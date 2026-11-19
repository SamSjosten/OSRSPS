package osrsps.game.tzhaar

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.DeathPlace
import osrsps.game.content.message
import osrsps.game.dialogue.Lines
import osrsps.game.instance.Instance
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.map.Tile

private val logger = KotlinLogging.logger {}

/** The Inferno's words (keys only; the words are in data/dialogue.txt). */
object InfernoLines : Lines("inferno", "The Inferno") {
    val WAVE = line("wave", "Each wave as it starts, in red, with its number (as the Fight Cave's fight_cave.wave)", "wave")
    val LOGOUT_PAUSE = line("logout_pause", "The logout button in a wave, in red: the run will pause when the wave ends (the Wiki's Inferno: paused by logging out between waves)")
    val LOGOUT_REPEAT = line("logout_repeat", "Straight after logout_pause, in red: logging out before the wave ends repeats it")
    val NO_ROOM = line("no_room", "Jump-in when no Inferno can be made (ours: the server's instances are all taken)")
    val IN_SCENARIO = line("in_scenario", "Jump-in in a test setup (::scenario, the Leagues Tutor's kits): ours, as its kit would earn real rewards; ::reset first")
}

/**
 * The Inferno (Inferno step IN1; the Wiki's Inferno, Rocky support, Jal-Nib and TzHaar-Ket-Keh pages, read
 * 2026-11-17). Jump-in on the Inferno's entrance in Mor Ul Rek (the multiloc `inferno_entrance` 30352 at 2493,5124,
 * which shows Jump-in once `inferno_sacrificed_firecape`, varbit 5646, is 2 or more: TzHaar-Ket-Keh sets it as he takes
 * a fire cape, [KetKeh]) makes an Inferno of the player's own, a copy of map square 35_83 ([Instance]), and the 69 waves
 * of [InfernoWaves] come one after another, each when the last one's monsters are all dead, the monsters going for the
 * player wherever they stand. Nobody else gets in; it is multi-way.
 *
 * Waves 1 to 66 bring Jal-Nibs, which go for the three rocky supports ([InfernoPillars]); those still standing fall as
 * wave 66 ends. A death is safe (nothing lost, "having to restart from the first wave"), puts the player outside, and
 * pays TokKul for the waves done; so does Exit or Quick-exit on the cave exit (`inferno_exit` 30283). Wave 69 is
 * TzKal-Zuk; his death pays [WIN_TOKKUL] (the Wiki's 16,440) and the infernal cape, with TzHaar-Ket-Keh's lines.
 *
 * Logging out: as in the Fight Cave ([FightCaves]), the logout button during a wave pauses the run at its end, and the
 * next login starts the next wave; any other logout repeats the wave. The run waits in the varp
 * [osrsps.game.content.CustomVars.infernoVarp] and the supports' damage in their own varbits.
 *
 * Built in IN1: the waves and their monsters with their stats and ordinary attacks (npc-combat.json); the supports and
 * Nibblers; entry, exit, death and logout. IN2: each monster's own mechanics ([InfernoMonsters]: Jal-Ak's split,
 * Jal-ImKot's dig, Jal-Zek's revive, ...). JalTok-Jad's come in IN3, TzKal-Zuk's shield, sets and healers with the pet
 * in IN4.
 *
 * GUESSED until recorded: the monsters' spots ([InfernoSpot]) and which monster takes which (World.random); where the
 * player comes in ([ARRIVAL], north of the exit), comes back after a login ([MIDDLE]), and comes out ([OUTSIDE], south of
 * the entrance); the waits before the first wave, between waves and after a login (the Fight Cave's); Exit and
 * Quick-exit alike, with no question first; TokKul for a run cut short ([InfernoWaves.tokkul]); where TzKal-Zuk stands
 * ([ZUK_TILE]); that a run left by ::tele pays nothing.
 */
class Inferno(private val world: World) {
    /** A player's run in their Inferno. */
    class Run internal constructor(
        /** [Player.saveKey] of whose it is. */
        val owner: String,
        val instance: Instance,
        wave: Int,
    ) {
        var wave: Int = wave
            internal set

        /** The tick the next wave comes in on; -1 while one is being fought. */
        var nextWaveTick: Long = -1
            internal set

        /** The wave's monsters still in the world, the dead ones until their bodies go. */
        val monsters: MutableList<Npc> = ArrayList()

        /** The tick the wave being fought came in on. */
        var waveStartTick: Long = 0
            internal set

        /** A Jal-Ak's smaller selves that couldn't come in yet (the world full of NPCs), by type and tile: the wave waits. */
        internal val splitsOwed: MutableList<Pair<Int, Tile>> = ArrayList()

        /** The wave's monsters that died and a Jal-Zek can bring back, by type, first dead first ([InfernoMonsters]). */
        val slain: MutableList<Int> = ArrayList()

        /** The rocky supports still standing. */
        val pillars: MutableList<InfernoPillars.Pillar> = ArrayList()

        /** The supports (0 to 2) still standing that couldn't come in for want of room yet ([InfernoPillars.raise]). */
        internal val pillarsOwed: MutableSet<Int> = sortedSetOf()

        /** The logout button was clicked in this wave: the player is logged out as it ends. */
        var pausing: Boolean = false
            internal set

        /** They died in the Inferno, and are paid once they are out. */
        var died: Boolean = false
            internal set

        /** Where tile [tile] of the real Inferno is in this one. */
        fun at(tile: Tile): Tile = Tile(instance.baseX + tile.x - CAVE_X, instance.baseZ + tile.z - CAVE_Z)

        override fun toString() = "inferno run of $owner: wave $wave"
    }

    private val runs = LinkedHashMap<String, Run>()

    /** Players logged in with a run waiting, put in their Inferno in the next tick. */
    private val resuming = LinkedHashSet<String>()

    /** Every run going on now, for tests and `::inferno`. */
    val all: Collection<Run> get() = runs.values

    /** [player]'s run, if they have one going. */
    fun runOf(player: Player): Run? = runs[player.saveKey]

    val pillars = InfernoPillars(world)

    /** The monsters' own mechanics (Inferno step IN2). */
    val monsterMechanics = InfernoMonsters(world)

    fun register() {
        pillars.register(this)
        monsterMechanics.register(this)
        KetKeh.register(world, this)
        world.handlers.onLoc(ENTRANCE, JUMP_IN) { enter(player) }
        world.handlers.onLoc(EXIT, LEAVE) { leave(player) }
        world.handlers.onLoc(EXIT, QUICK_EXIT) { leave(player) }
        world.npcScripts.onDeath(listOf(InfernoMonster.ZUK.type)) { zuk ->
            val run = runAt(zuk.x, zuk.z) ?: return@onDeath
            // A shot landing after they died isn't a win: their death stays the safe one (Codex review on #829).
            world.onlinePlayers.firstOrNull { it.saveKey == run.owner }
                ?.takeIf { !it.dead && it.hitpoints > 0 && !run.died && run.instance.contains(it.x, it.z) }?.let { victory(it, run) }
        }
        val multi = world.multiCombat.dynamic
        world.multiCombat.dynamic = { level, x, z -> runAt(x, z) != null || multi(level, x, z) }
    }

    /** The run whose Inferno holds tile [x], [z], if any. */
    fun runAt(x: Int, z: Int): Run? = runs.values.firstOrNull { it.instance.contains(x, z) }

    /** Whether [player] has given TzHaar-Ket-Keh a fire cape, so the entrance shows Jump-in. */
    fun sacrificed(player: Player): Boolean =
        player.vars.types.varbit(VARBIT_SACRIFICED) != null && player.vars.getVarbit(VARBIT_SACRIFICED) >= SACRIFICED

    /** Records [player]'s fire cape given to TzHaar-Ket-Keh: the entrance shows Jump-in from now on. */
    fun sacrifice(player: Player) {
        if (player.vars.types.varbit(VARBIT_SACRIFICED) != null) player.vars.setVarbit(VARBIT_SACRIFICED, SACRIFICED)
    }

    /** Jump-in on the entrance: a new Inferno from wave 1, once the fire cape has been given. */
    fun enter(player: Player) {
        if (runs.containsKey(player.saveKey) || !sacrificed(player)) return
        // A test setup's kit (99s, the best gear) would earn a real infernal cape and TokKul that outlive putting it back.
        if (player.scenario != null) return world.message(player, InfernoLines.IN_SCENARIO)
        start(player, 1)
    }

    /**
     * Puts [player] in an Inferno of their own at [tile] (the real one's), wave [wave] coming in [delay] ticks; false,
     * with them told, when none can be made. Any run they had ends first. A run from wave 1 has all three supports; a
     * later one ([fresh] false, a login or `::inferno`) the ones the player's varbits say are standing.
     */
    fun start(player: Player, wave: Int, tile: Tile = ARRIVAL, delay: Int = FIRST_WAVE_TICKS, fresh: Boolean = wave == 1): Boolean {
        require(wave in 1..InfernoWaves.WAVES) { "No wave $wave" }
        require(delay >= 1) { "A wave can't come in $delay ticks" }
        // The new Inferno first: with none to be had, a run already going stays as it was, its player in it.
        val instance = world.instances.create(CAVE_X, CAVE_Z, CAVE_ZONES, CAVE_ZONES, OUTSIDE.x, OUTSIDE.z, 0)
        if (instance == null) {
            world.message(player, InfernoLines.NO_ROOM)
            return false
        }
        runs[player.saveKey]?.let(::end)
        val run = Run(player.saveKey, instance, wave)
        run.nextWaveTick = world.tick + delay
        runs[player.saveKey] = run
        save(player, wave)
        if (fresh) pillars.reset(player)
        if (wave <= InfernoWaves.LAST_PILLAR_WAVE) pillars.raise(run, player)
        world.scripts.interrupt(player)
        val at = run.at(tile)
        world.teleport(player, at.x, at.z, 0)
        logger.info { "${player.username} went into an inferno: wave $wave" }
        return true
    }

    /** [player] has just come into the world: a run they left waits for them, put in by the next tick's [process]. */
    fun login(player: Player) {
        if (saved(player) != null) resuming += player.saveKey
    }

    /** The wave a login starts [player]'s left run at, if their save holds one. */
    fun saved(player: Player): Int? = (player.vars[world.customVars.infernoVarp] and WAVE_MASK).takeIf { it in 1..InfernoWaves.WAVES }

    private fun save(player: Player, wave: Int) {
        player.vars[world.customVars.infernoVarp] = wave
    }

    private fun forget(player: Player) {
        player.vars[world.customVars.infernoVarp] = 0
    }

    /** The Inferno's part of a tick, in the NPC phase after the fights: logins, waves in, waves done, runs ended. */
    fun process() {
        pillars.process()
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
            pillars.tick(run, player)
            for (npc in run.monsters.toList()) {
                if (monsterMechanics.scripted(npc.type.id)) monsterMechanics.tick(this, run, npc, player)
            }
            if (run.nextWaveTick >= 0) {
                if (world.tick >= run.nextWaveTick) spawnWave(player, run)
            } else if (run.splitsOwed.isNotEmpty()) {
                for (owed in run.splitsOwed.toList()) {
                    if (spawn(player, run, owed.first, owed.second) != null) run.splitsOwed.remove(owed)
                }
            } else if (run.monsters.none { !it.dead }) {
                waveDone(player, run)
            }
        }
    }

    /** Puts each player logged in with a run waiting in a new Inferno: the middle, the wave they left at coming soon after. */
    private fun resume() {
        for (key in resuming.toList()) {
            resuming -= key
            val player = world.onlinePlayers.firstOrNull { it.saveKey == key } ?: continue
            if (player.dead || runs.containsKey(key) || world.instances.at(player.x, player.z) != null) continue
            val wave = saved(player) ?: continue
            start(player, wave, MIDDLE, RESUME_TICKS, fresh = false)
        }
    }

    private fun spawnWave(player: Player, run: Run) {
        run.nextWaveTick = -1
        run.pausing = false
        run.waveStartTick = world.tick
        run.slain.clear()
        world.message(player, InfernoLines.WAVE, "wave" to run.wave)
        val monsters = InfernoWaves.monsters(run.wave)
        val spots = InfernoSpot.entries.toMutableList()
        var nibblers = 0
        for (monster in monsters) {
            val tile = when (monster) {
                InfernoMonster.NIBBLER -> nibblerTile(nibblers++)
                InfernoMonster.ZUK -> ZUK_TILE
                else -> spots.removeAt(world.random.nextInt(spots.size)).tile
            }
            // Nibblers go for the supports while any stand ([InfernoPillars]); the rest for the player.
            if (spawn(player, run, monster.type, run.at(tile), fight = monster != InfernoMonster.NIBBLER || (run.pillars.isEmpty() && run.pillarsOwed.isEmpty())) == null) {
                // A monster that couldn't come in (the world full of NPCs) isn't a kill: the wave goes and comes again.
                logger.warn { "${run.owner}: inferno wave ${run.wave} couldn't spawn ${monster.type}; trying again" }
                for (npc in run.monsters) if (world.npcs[npc.index] === npc) world.removeNpc(npc)
                run.monsters.clear()
                run.nextWaveTick = world.tick + WAVE_GAP_TICKS
                return
            }
        }
        logger.debug { "${run.owner}: inferno wave ${run.wave} in, ${monsters.joinToString()}" }
    }

    /** The [n]th Nibbler's tile: the middle, then round it. */
    private fun nibblerTile(n: Int): Tile {
        val (dx, dz) = NIBBLER_OFFSETS[n % NIBBLER_OFFSETS.size]
        return Tile(InfernoWaves.NIBBLER_TILE.x + dx, InfernoWaves.NIBBLER_TILE.z + dz)
    }

    /**
     * Puts a monster of [typeId] in [run]'s Inferno on [tile] (already the copy's own), going for [player] from the next
     * tick unless [fight] is false; null when it can't be.
     */
    fun spawn(player: Player, run: Run, typeId: Int, tile: Tile, fight: Boolean = true): Npc? {
        val type = world.npcTypes[typeId] ?: return null.also { logger.warn { "The cache has no NPC $typeId for the inferno" } }
        val npc = world.spawnNpc(NpcSpawn(type, tile.x, tile.z, 0, source = SOURCE, respawns = false)) ?: return null
        npc.unleashed = true
        npc.huntsAnywhere = true
        // A Nibbler's swings are its own ([InfernoPillars]): at a support, or, with none left, at the player. TzKal-Zuk
        // stands still and holds his attack until his fight is built (Inferno step IN4).
        npc.scriptedAttacks = typeId == InfernoMonster.NIBBLER.type || typeId == InfernoMonster.ZUK.type || monsterMechanics.scripted(typeId)
        run.monsters += npc
        if (fight) world.meleeCombat.aggress(npc, player, world.tick)
        return npc
    }

    private fun waveDone(player: Player, run: Run) {
        if (run.wave == InfernoWaves.ZUK_WAVE) return
        // "The supports automatically collapse after wave 66 has been cleared".
        if (run.wave == InfernoWaves.LAST_PILLAR_WAVE) pillars.collapseAll(run)
        run.wave++
        save(player, run.wave)
        if (run.pausing) {
            run.pausing = false
            player.logoutForced = true
            return
        }
        run.nextWaveTick = world.tick + WAVE_GAP_TICKS
    }

    /** TzKal-Zuk is dead: out with the infernal cape and the TokKul, and TzHaar-Ket-Keh's thanks. */
    private fun victory(player: Player, run: Run) {
        end(run)
        forget(player)
        world.teleport(player, OUTSIDE.x, OUTSIDE.z, 0)
        world.objTypes[INFERNAL_CAPE]?.let { world.groundObjs.give(player, it, 1) }
        pay(player, WIN_TOKKUL)
        if (player.vars.types.varpCount > VARP_ZUK_KILLS) player.vars[VARP_ZUK_KILLS] = player.vars[VARP_ZUK_KILLS] + 1
        KetKeh.victory(world, player, WIN_TOKKUL)
        logger.info { "${player.username} beat the inferno" }
    }

    /**
     * The logout button, clicked in an Inferno: the first click in a wave pauses the run at its end and is taken (true);
     * any other is the ordinary logout's (false).
     */
    fun logoutClicked(player: Player): Boolean {
        val run = runs[player.saveKey] ?: return false
        if (run.pausing || run.nextWaveTick >= 0 || !run.instance.contains(player.x, player.z)) return false
        run.pausing = true
        world.message(player, InfernoLines.LOGOUT_PAUSE)
        world.message(player, InfernoLines.LOGOUT_REPEAT)
        return true
    }

    /** [player] is leaving the world, saved next: a run left waits in the varp; a death's TokKul is paid now. */
    fun leaving(player: Player) {
        resuming -= player.saveKey
        val run = runs[player.saveKey] ?: return
        // Gone from the world, they can't pick up a drop: what won't fit goes to the bank, then owed.
        if (run.died) return paidOut(player, run, bankOverflow = true)
        pillars.save(run, player)
        end(run)
    }

    /** Where [player], dying in their Inferno, comes back: outside, with everything they had; null when not in one. */
    fun deathPlace(player: Player): DeathPlace? {
        val run = runs[player.saveKey]?.takeIf { it.instance.contains(player.x, player.z) } ?: return null
        run.died = true
        return DeathPlace(keepItems = true, OUTSIDE.x, OUTSIDE.z, 0, OUTSIDE.x, OUTSIDE.z, 0, safe = true)
    }

    /** Exit or Quick-exit (and `::inferno leave`): out with the TokKul for the waves done. */
    fun leave(player: Player) {
        val run = runs[player.saveKey] ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        world.teleport(player, OUTSIDE.x, OUTSIDE.z, 0)
        paidOut(player, run)
    }

    /** [player] is out of their Inferno while their run went on: paid for a death or a walk out, else just ended. */
    private fun left(player: Player, run: Run) {
        if (run.died) return paidOut(player, run)
        end(run)
        forget(player)
    }

    private fun paidOut(player: Player, run: Run, bankOverflow: Boolean = false) {
        end(run)
        forget(player)
        val tokkul = InfernoWaves.tokkul(run.wave - 1)
        if (tokkul > 0) pay(player, tokkul, bankOverflow)
    }

    /** As the Fight Cave pays ([FightCaves.pay]): its owed TokKul is the cave's, paid at the next login. */
    private fun pay(player: Player, tokkul: Int, bankOverflow: Boolean = false) = world.fightCaves.pay(player, tokkul, bankOverflow)

    /** Ends [run]: its monsters and supports go, and its Inferno once nobody is in it (the world ends empty instances). */
    internal fun end(run: Run) {
        if (runs[run.owner] === run) runs.remove(run.owner)
        // A shot on its way would land outside after the run is over, where a death is no longer safe.
        world.meleeCombat.dropShotsFromWithin { x, z -> run.instance.contains(x, z) }
        for (npc in run.monsters) if (world.npcs[npc.index] === npc) world.removeNpc(npc)
        run.monsters.clear()
        for (pillar in run.pillars) {
            if (world.npcs[pillar.npc.index] === pillar.npc) world.removeNpc(pillar.npc)
            world.locs.remove(pillar.loc)
        }
        run.pillars.clear()
        run.pillarsOwed.clear()
    }

    companion object {
        /** `inferno_entrance`, the multiloc in Mor Ul Rek. */
        const val ENTRANCE = 30352
        const val JUMP_IN = 1

        /** `inferno_exit`, in the Inferno's south wall (2269,5325). */
        const val EXIT = 30283
        const val LEAVE = 1
        const val QUICK_EXIT = 2

        /** `inferno_sacrificed_firecape`: 2 or more shows the entrance's Jump-in (its multiloc shows nothing for 0 and 1). */
        const val VARBIT_SACRIFICED = 5646
        const val SACRIFICED = 2

        /** `total_zuk_kills`, which gives TzHaar-Ket-Keh his Exchange option (his multinpc). */
        const val VARP_ZUK_KILLS = 1585

        const val INFERNAL_CAPE = 21295

        /** The most TokKul the Inferno pays (the Wiki's Inferno, Rewards), for TzKal-Zuk's death. */
        const val WIN_TOKKUL = 16_440

        /** The Inferno's locs whose handlers work in a copy of it. */
        val INSTANCE_LOCS: Set<Int> = setOf(EXIT)

        /** The Inferno is map square 35_83, all of it copied. */
        const val CAVE_X = 35 shl 6
        const val CAVE_Z = 83 shl 6
        const val CAVE_ZONES = 8

        /** Where a player comes in: north of the exit (2269,5325, 5 by 4). Guessed. */
        val ARRIVAL = Tile(2271, 5329)

        /** Where a login puts a player back: the middle, south of the Nibblers. Guessed. */
        val MIDDLE = Tile(2271, 5340)

        /** Where a player comes out: south of the entrance (2493,5124, 6 by 6), the Wiki's Inferno map pin. Guessed. */
        val OUTSIDE = Tile(2496, 5122)

        /** TzKal-Zuk's south-west tile, behind the arena's north wall. Guessed. */
        val ZUK_TILE = Tile(2268, 5361)

        /** Round the Nibblers' middle tile, for the second Nibbler on. */
        private val NIBBLER_OFFSETS = listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1)

        const val FIRST_WAVE_TICKS = FightCaves.FIRST_WAVE_TICKS
        const val WAVE_GAP_TICKS = FightCaves.WAVE_GAP_TICKS
        const val RESUME_TICKS = FightCaves.RESUME_TICKS

        /** The spawn source of the Inferno's monsters and supports, for `::npcs`. */
        const val SOURCE = "inferno"

        private const val WAVE_MASK = 0x7f
    }
}
