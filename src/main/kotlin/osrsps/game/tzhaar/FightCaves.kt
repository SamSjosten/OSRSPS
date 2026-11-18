package osrsps.game.tzhaar

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.DeathPlace
import osrsps.game.content.message
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Lines
import osrsps.game.instance.Instance
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.map.Tile

private val logger = KotlinLogging.logger {}

/** The Fight Cave's words (keys only; the words are in data/dialogue.txt). */
object FightCaveLines : Lines("fight_cave", "TzHaar Fight Cave") {
    val WAVE = line("wave", "Each wave as it starts, in red, with its number (the Wiki's TzHaar Fight Cave: the chatbox shows the wave)", "wave")
    val LOGOUT_PAUSE = line("logout_pause", "The logout button in a wave, in red: the run will pause when the wave ends (the Wiki's Strategies page: logging out)")
    val LOGOUT_REPEAT = line("logout_repeat", "Straight after logout_pause, in red: logging out before the wave ends repeats it")
    val TRIED = line("tried", "TzHaar-Mej-Jal after a death or leaving in the first wave, with no TokKul (Transcript:TzHaar-Mej-Jal)")
    val REWARD = line("reward", "TzHaar-Mej-Jal after a death or leaving in a later wave, as TokKul is handed over (Transcript:TzHaar-Mej-Jal)")
    val VICTORIOUS = line("victorious", "TzHaar-Mej-Jal after TzTok-Jad's death, handing over the fire cape (Transcript:TzHaar-Mej-Jal)")
    val NO_ROOM = line("no_room", "Enter on the cave entrance when no cave can be made (ours: the server's instances are all taken)")
}

/**
 * The TzHaar Fight Cave (Fight Caves step FC1; the Wiki's TzHaar Fight Cave, its Strategies and Rotations pages, read
 * 2026-11-17). Enter on the cave entrance in Mor Ul Rek (`tzhaar_fightcave_wall_entrance` 11833 at 2437,5166) makes a
 * cave of the player's own, a copy of map square 37_79 ([Instance]), and the waves of [FightCaveWaves] come one after
 * another, each when the last one's monsters are all dead, the monsters going for the player wherever they stand.
 * Wave 63 is TzTok-Jad. The cave is multi-way.
 *
 * Every way out pays Tokkul for the waves done ([FightCaveWaves.tokkul]): Leave or Escape on the cave's exit
 * (`tzhaar_fightcave_wall_exit` 11834), and a death, which is safe (nothing lost, stats back) and puts the player
 * outside the entrance. Killing TzTok-Jad pays for all 63 and his own 4,000 and hands over a fire cape. TzHaar-Mej-Jal
 * says a line each time.
 *
 * Logging out: the logout button during a wave says the run will pause when the wave ends, and once it does the player
 * is logged out, to start at the next wave when they log in. A logout any other way (the button again, which the combat
 * lock usually refuses, or a lost connection) brings them back to the start of the wave they left. Either way the run
 * waits in the varp [osrsps.game.content.CustomVars.fightCaveVarp], and a login puts them in a new cave in its
 * middle with a short wait before the wave (the Wiki: "you spawn in the middle of the room and have a short window to
 * reposition and swap prayers"). `::fightcave` starts any wave of any rotation; the rotation is otherwise picked from
 * [World.random], so `::seed` replays one.
 *
 * GUESSED until recorded: the rotation picked at random (live picks it from the minute the player goes in, which the
 * Wiki doesn't map to rotations); the five spots' tiles ([FightCaveSpot]); where the player comes in ([ARRIVAL], south of
 * the exit) and comes out ([OUTSIDE], north of the entrance); the waits before the first wave ([FIRST_WAVE_TICKS]),
 * between waves ([WAVE_GAP_TICKS]) and after a login ([RESUME_TICKS]); Leave and Escape alike, with no question first;
 * Tokkul into the backpack (on the floor when it is full); the second monster of a kind in a wave being the cache's
 * `_b` type; TzHaar-Mej-Jal's lines.
 */
class FightCaves(private val world: World) {
    /** A player's run in their cave. */
    class Run internal constructor(
        /** [Player.saveKey] of whose it is. */
        val owner: String,
        val instance: Instance,
        /** The rotation, 1 to 15 ([FightCaveWaves.ROTATION_STARTS]). */
        val rotation: Int,
        /** The wave being fought, or coming next while [nextWaveTick] is set. */
        wave: Int,
    ) {
        var wave: Int = wave
            internal set

        /** The tick the next wave comes in on; -1 while one is being fought. */
        var nextWaveTick: Long = -1
            internal set

        /** The wave's monsters still in the world, the dead ones until their bodies go. */
        val monsters: MutableList<Npc> = ArrayList()

        /** The logout button was clicked in this wave: the player is logged out as it ends. */
        var pausing: Boolean = false
            internal set

        /** They died in the cave, and are paid once they are out. */
        var died: Boolean = false
            internal set

        /** Where tile [tile] of the real cave is in this one. */
        fun at(tile: Tile): Tile = Tile(instance.baseX + tile.x - CAVE_X, instance.baseZ + tile.z - CAVE_Z)

        override fun toString() = "fight cave run of $owner: wave $wave, rotation $rotation"
    }

    /** What the varp holds of a run left part way: the wave a login starts at, and the rotation. */
    data class Saved(val wave: Int, val rotation: Int)

    private val runs = LinkedHashMap<String, Run>()

    /** Players logged in with a run waiting, put in their cave in the next tick. */
    private val resuming = LinkedHashSet<String>()

    /** [player]'s run, if they have one going. */
    fun runOf(player: Player): Run? = runs[player.saveKey]

    fun register() {
        world.handlers.onLoc(ENTRANCE, ENTER) { enter(player) }
        world.handlers.onLoc(EXIT, LEAVE) { leave(player) }
        world.handlers.onLoc(EXIT, ESCAPE) { leave(player) }
        val multi = world.multiCombat.dynamic
        world.multiCombat.dynamic = { level, x, z -> runAt(x, z) != null || multi(level, x, z) }
    }

    /** The run whose cave holds tile [x], [z], if any. */
    fun runAt(x: Int, z: Int): Run? = runs.values.firstOrNull { it.instance.contains(x, z) }

    /** Enter on the cave entrance: a new cave from wave 1, in a rotation picked at random. */
    fun enter(player: Player) {
        if (runs.containsKey(player.saveKey)) return
        start(player, 1, randomRotation(), ARRIVAL, FIRST_WAVE_TICKS)
    }

    /** A rotation from 1 to 15, from the world's random (guessed: live goes by the time the player goes in). */
    fun randomRotation(): Int = world.random.between(1, FightCaveWaves.ROTATIONS)

    /**
     * Puts [player] in a cave of their own at [tile] (the real cave's), wave [wave] of [rotation] coming in [delay] ticks;
     * false, with them told, when no cave can be made. Any run they had ends first.
     */
    fun start(player: Player, wave: Int, rotation: Int, tile: Tile = ARRIVAL, delay: Int = FIRST_WAVE_TICKS): Boolean {
        require(wave in 1..FightCaveWaves.WAVES) { "No wave $wave" }
        require(rotation in 1..FightCaveWaves.ROTATIONS) { "No rotation $rotation" }
        require(delay >= 1) { "A wave can't come in $delay ticks" }
        runs[player.saveKey]?.let(::end)
        val instance = world.instances.create(CAVE_X, CAVE_Z, CAVE_ZONES, CAVE_ZONES, OUTSIDE.x, OUTSIDE.z, 0)
        if (instance == null) {
            world.message(player, FightCaveLines.NO_ROOM)
            return false
        }
        val run = Run(player.saveKey, instance, rotation, wave)
        run.nextWaveTick = world.tick + delay
        runs[player.saveKey] = run
        save(player, run.wave, rotation)
        world.scripts.interrupt(player)
        val at = run.at(tile)
        world.teleport(player, at.x, at.z, 0)
        logger.info { "${player.username} went into a fight cave: wave $wave, rotation $rotation" }
        return true
    }

    /**
     * [player] has just come into the world: TokKul a stop's settling couldn't give them comes now, and a run they left
     * waits for them, put in by the next tick's [process].
     */
    fun login(player: Player) {
        val owed = owed(player)
        if (owed > 0) setOwed(player, owed - give(player, owed))
        if (saved(player) != null) resuming += player.saveKey
    }

    /**
     * TokKul [player] is owed: what the server's stop settled for a death but neither their backpack nor bank had room
     * for, kept in the cave's varp until a login has the room. Guessed: live has no such case.
     */
    fun owed(player: Player): Int = (player.vars[world.customVars.fightCaveVarp] ushr OWED_SHIFT) and OWED_MASK

    private fun setOwed(player: Player, tokkul: Int) {
        val value = player.vars[world.customVars.fightCaveVarp] and ((1 shl OWED_SHIFT) - 1)
        player.vars[world.customVars.fightCaveVarp] = value or (tokkul.coerceIn(0, OWED_MASK) shl OWED_SHIFT)
    }

    /** Puts up to [tokkul] in [player]'s backpack, then their bank; how much went in. */
    private fun give(player: Player, tokkul: Int): Int {
        val type = world.objTypes[TOKKUL] ?: return 0
        val added = player.inventory.add(type, tokkul)
        val left = tokkul - added
        return added + if (left > 0) world.bankItems(player, TOKKUL, left) else 0
    }

    /** What [player]'s save holds of a run they left, if anything. */
    fun saved(player: Player): Saved? {
        val value = player.vars[world.customVars.fightCaveVarp]
        val wave = value and WAVE_MASK
        val rotation = (value shr ROTATION_SHIFT) and ROTATION_MASK
        if (wave !in 1..FightCaveWaves.WAVES || rotation !in 1..FightCaveWaves.ROTATIONS) return null
        return Saved(wave, rotation)
    }

    private fun save(player: Player, wave: Int, rotation: Int) {
        val owed = player.vars[world.customVars.fightCaveVarp] and (OWED_MASK shl OWED_SHIFT)
        player.vars[world.customVars.fightCaveVarp] = wave or (rotation shl ROTATION_SHIFT) or owed
    }

    /** Clears the saved run, keeping any TokKul [owed]. */
    private fun forget(player: Player) {
        player.vars[world.customVars.fightCaveVarp] = player.vars[world.customVars.fightCaveVarp] and (OWED_MASK shl OWED_SHIFT)
    }

    /** The cave's part of a tick, in the NPC phase after the fights: logins, waves in, waves done, runs ended. */
    fun process() {
        if (resuming.isNotEmpty()) resume()
        if (runs.isEmpty()) return
        for (run in runs.values.toList()) {
            val player = world.onlinePlayers.firstOrNull { it.saveKey == run.owner }
            if (player == null) {
                // Gone without leaving() (it saves them first), as a test's world can.
                end(run)
                continue
            }
            if (player.dead) continue
            if (!run.instance.contains(player.x, player.z)) {
                left(player, run)
                continue
            }
            run.monsters.removeAll { world.npcs[it.index] !== it }
            if (run.nextWaveTick >= 0) {
                if (world.tick >= run.nextWaveTick) spawnWave(player, run)
            } else if (run.monsters.none { !it.dead }) {
                waveDone(player, run)
            }
        }
    }

    /** Puts each player logged in with a run waiting in a new cave: the middle, the wave they left at coming soon after. */
    private fun resume() {
        for (key in resuming.toList()) {
            resuming -= key
            val player = world.onlinePlayers.firstOrNull { it.saveKey == key } ?: continue
            if (player.dead || runs.containsKey(key) || world.instances.at(player.x, player.z) != null) continue
            val saved = saved(player) ?: continue
            start(player, saved.wave, saved.rotation, MIDDLE, RESUME_TICKS)
        }
    }

    private fun spawnWave(player: Player, run: Run) {
        run.nextWaveTick = -1
        run.pausing = false
        world.message(player, FightCaveLines.WAVE, "wave" to run.wave)
        val monsters = FightCaveWaves.monsters(run.wave)
        val spots = FightCaveWaves.spots(run.wave, run.rotation)
        val seen = HashSet<FightCaveMonster>()
        for ((monster, spot) in monsters.zip(spots)) {
            val typeId = if (seen.add(monster)) monster.first else monster.second
            if (spawn(player, run, typeId, run.at(spot.tile)) == null) {
                // A monster that couldn't come in (the world full of NPCs) isn't a kill: the wave goes and comes again.
                logger.warn { "${run.owner}: fight cave wave ${run.wave} couldn't spawn ${typeId}; trying again" }
                for (npc in run.monsters) if (world.npcs[npc.index] === npc) world.removeNpc(npc)
                run.monsters.clear()
                run.nextWaveTick = world.tick + WAVE_GAP_TICKS
                return
            }
        }
        logger.debug { "${run.owner}: wave ${run.wave} in, ${monsters.joinToString()} at ${spots.joinToString()}" }
    }

    /**
     * Puts a monster of [typeId] in [run]'s cave on [tile] (already the cave's own), going for [player] from the next
     * tick, wherever in the cave they are; null when it can't be. Fight Caves step FC2's Tz-Kek split comes in this way.
     */
    fun spawn(player: Player, run: Run, typeId: Int, tile: Tile): Npc? {
        val type = world.npcTypes[typeId] ?: return null.also { logger.warn { "The cache has no NPC $typeId for the fight cave" } }
        val npc = world.spawnNpc(NpcSpawn(type, tile.x, tile.z, 0, source = SOURCE, respawns = false)) ?: return null
        npc.unleashed = true
        npc.huntsAnywhere = true
        run.monsters += npc
        world.meleeCombat.aggress(npc, player, world.tick)
        return npc
    }

    private fun waveDone(player: Player, run: Run) {
        if (run.wave == FightCaveWaves.WAVES) return victory(player, run)
        run.wave++
        save(player, run.wave, run.rotation)
        if (run.pausing) {
            // Paused: out at the wave's end, to start the next at their next login (the Wiki's Strategies page).
            run.pausing = false
            player.logoutForced = true
            return
        }
        run.nextWaveTick = world.tick + WAVE_GAP_TICKS
    }

    private fun victory(player: Player, run: Run) {
        end(run)
        forget(player)
        world.teleport(player, OUTSIDE.x, OUTSIDE.z, 0)
        pay(player, FightCaveWaves.tokkul(FightCaveWaves.WAVES) + JAD_TOKKUL)
        world.objTypes[FIRE_CAPE]?.let { world.groundObjs.give(player, it, 1) }
        mejJal(player, FightCaveLines.VICTORIOUS)
        logger.info { "${player.username} beat the fight cave (rotation ${run.rotation})" }
    }

    /**
     * The logout button, clicked in a cave: the first click in a wave pauses the run at its end and is taken (true); any
     * other is the ordinary logout's (false), which the combat lock usually refuses.
     */
    fun logoutClicked(player: Player): Boolean {
        val run = runs[player.saveKey] ?: return false
        if (run.pausing || run.nextWaveTick >= 0 || !run.instance.contains(player.x, player.z)) return false
        run.pausing = true
        world.message(player, FightCaveLines.LOGOUT_PAUSE)
        world.message(player, FightCaveLines.LOGOUT_REPEAT)
        return true
    }

    /**
     * [player] is leaving the world, saved next: a run left mid-wave starts that wave again at their next login, one
     * paused at a wave's end the next (its varp already says which); a death's Tokkul is paid now.
     */
    fun leaving(player: Player, shuttingDown: Boolean = false) {
        resuming -= player.saveKey
        val run = runs[player.saveKey] ?: return
        if (run.died) return paidOut(player, run, bankOverflow = shuttingDown)
        end(run)
    }

    /**
     * Where [player], dying in their cave, comes back: outside the entrance with everything they had (the Wiki: "Dying
     * respawns you outside the caves with your equipment and inventory"); null when they aren't in one.
     */
    fun deathPlace(player: Player): DeathPlace? {
        val run = runs[player.saveKey]?.takeIf { it.instance.contains(player.x, player.z) } ?: return null
        run.died = true
        return DeathPlace(keepItems = true, OUTSIDE.x, OUTSIDE.z, 0, OUTSIDE.x, OUTSIDE.z, 0, safe = true)
    }

    /** Leave or Escape on the exit (and `::fightcave leave`): out with the Tokkul for the waves done. */
    fun leave(player: Player) {
        val run = runs[player.saveKey] ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        world.teleport(player, OUTSIDE.x, OUTSIDE.z, 0)
        paidOut(player, run)
    }

    /** [player] is out of their cave while their run went on: paid for a death or a walk out, else just ended. */
    private fun left(player: Player, run: Run) {
        if (run.died) return paidOut(player, run)
        // Out some other way (::tele, ::reset): no Tokkul, and nothing to come back to.
        end(run)
        forget(player)
    }

    /** Pays [run]'s TokKul: the transcript has one line for a run out in the first wave and one for a later wave, death or not. */
    private fun paidOut(player: Player, run: Run, bankOverflow: Boolean = false) {
        end(run)
        forget(player)
        val tokkul = FightCaveWaves.tokkul(run.wave - 1)
        if (tokkul > 0) pay(player, tokkul, bankOverflow)
        if (!player.loggedOut) mejJal(player, if (tokkul > 0) FightCaveLines.REWARD else FightCaveLines.TRIED)
    }

    /**
     * Gives [tokkul], what won't fit on the ground; or with [bankOverflow] (the server stopping) into the backpack and
     * bank, what neither takes [owed] for the next login.
     */
    private fun pay(player: Player, tokkul: Int, bankOverflow: Boolean = false) {
        val type = world.objTypes[TOKKUL] ?: return
        if (!bankOverflow) return world.groundObjs.give(player, type, tokkul)
        val left = tokkul - give(player, tokkul)
        if (left > 0) setOwed(player, owed(player) + left)
    }

    /** TzHaar-Mej-Jal says [line], his head beside it, as he does as a run ends. */
    private fun mejJal(player: Player, line: osrsps.game.dialogue.Line) {
        val type = world.npcTypes[MEJ_JAL] ?: return
        world.scripts.start(player) {
            world.dialogues.start(this) { chatNpc(type, type.name, ChatAnim.NEUTRAL, line) }
        }
    }

    /** Ends [run]: its monsters go, and its cave once nobody is in it (the world ends empty instances). */
    internal fun end(run: Run) {
        if (runs[run.owner] === run) runs.remove(run.owner)
        // A shot on its way would land outside after the run is over, where a death is no longer safe: every shot from
        // the cave goes, a monster's whose body has already gone too.
        world.meleeCombat.dropShotsFromWithin { x, z -> run.instance.contains(x, z) }
        for (npc in run.monsters) if (world.npcs[npc.index] === npc) world.removeNpc(npc)
        run.monsters.clear()
    }

    companion object {
        const val ENTRANCE = 11833
        const val EXIT = 11834
        const val ENTER = 1
        const val LEAVE = 1
        const val ESCAPE = 2

        /** TzHaar-Mej-Jal, who pays out (`tzhaar_fightcave_master`). */
        const val MEJ_JAL = 2180

        const val TOKKUL = 6529
        const val FIRE_CAPE = 6570

        /** TzTok-Jad's own Tokkul (the Wiki: 8,032 for a full run, of which 4,032 are the 63 waves'). */
        const val JAD_TOKKUL = 4000

        /** The cave's locs whose handlers work in a copy of it. */
        val INSTANCE_LOCS: Set<Int> = setOf(EXIT)

        /** The cave is map square 37_79, all of it copied. */
        const val CAVE_X = 37 shl 6
        const val CAVE_Z = 79 shl 6
        const val CAVE_ZONES = 8

        /** Where a player comes in: south of the exit (2412,5118, 3 by 2). Guessed. */
        val ARRIVAL = Tile(2413, 5117)

        /** Where a login puts a player back: the middle of the cave. Guessed tile. */
        val MIDDLE = Tile(2400, 5088)

        /** Where a player comes out: north of the entrance (2437,5166, 3 by 2). Guessed. */
        val OUTSIDE = Tile(2438, 5168)

        /** Ticks from going in to wave 1. Guessed. */
        const val FIRST_WAVE_TICKS = 4

        /** Ticks from a wave's last monster dying to the next wave. Guessed. */
        const val WAVE_GAP_TICKS = 8

        /** Ticks from a login back into the cave to the wave: the Wiki's "short window". Guessed. */
        const val RESUME_TICKS = 10

        /** The spawn source of the cave's monsters, for `::npcs`. */
        const val SOURCE = "fight cave"

        private const val WAVE_MASK = 0x7f
        private const val ROTATION_SHIFT = 7
        private const val ROTATION_MASK = 0xf
        private const val OWED_SHIFT = 11

        /** Room for 1..62's 3,906 TokKul several times over; the varp's sign bit stays clear. */
        private const val OWED_MASK = 0xfffff
    }
}
