package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.instance.Instance
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.map.Tile

/** Brutus's pen's lines (keys only; the words are in data/dialogue.txt). */
object BrutusLines : Lines("brutus", "Brutus's pen") {
    val RELEASE_TITLE = line("release_title", "Release on the pen's gate: the menu's title (no source yet)")
    val RELEASE_YES = line("release_yes", "Release's menu: go in (no source yet)")
    val RELEASE_YES_ALWAYS = line("release_yes_always", "Release's menu: go in, and don't ask again (named in the Wiki's 4 March 2026 change; words not found)")
    val RELEASE_NO = line("release_no", "Release's menu: stay out (no source yet)")
    val NO_ROOM = line("no_room", "Release when no instance can be made (ours)")
    val NOT_WANTED = line("not_wanted", "an item used on Brutus (OSRS Wiki, Brutus, Trivia)")
    val NOT_THE_BUCKET = line("not_the_bucket", "an empty bucket used on Brutus (OSRS Wiki, Brutus, Trivia)")
    val DYING = line("dying", "Brutus's overhead as he dies (Transcript:Brutus, Upon death)")
    val GROWLS = line("growls", "Brutus's overhead as he readies his charge (Transcript:Brutus, When charging)")
    val SNORTS = line("snorts", "Brutus's overhead as he readies his stomps (Transcript:Brutus, When stomping)")
    val KILL_COUNT = line("kill_count", "a kill of Brutus: the kill count (the boss kill-count message; words not recorded yet)", "count")
    val HUFF = line("huff", "Brutus's overhead as he comes for a player out of his reach (Transcript:Brutus, Switching into smart-pathing)")
}

/**
 * Brutus, the cow boss of the Lumbridge cow field (Brutus step 1: the way in and out and Brutus standing in his field;
 * the OSRS Wiki's Brutus, Brutus/Strategies, Warning Sign (Brutus) and The Ides of Milk, read 2026-11-19; ids, ops and
 * places from the 242 cache). The Ides of Milk counts as done, as every quest does, so the post-quest Brutus is open to
 * everyone.
 *
 * - **The pen's gate** (`fencegate_l_cowboss_start` 60763 and `fencegate_r_cowboss_start` 60760, at 3262 and
 *   3263,3294 on the pen's south side): Release. "By attempting to release him, the player is teleported into an empty,
 *   instanced version of the field, where Brutus will spawn" (Brutus). Release first asks, with a "Yes and don't ask
 *   again" (the Wiki's 4 March 2026 change), which sets the cache's `cowboss_regular_dontaskagain` (varbit 20160) so
 *   the gate takes the player straight in from then on. The field is an [Instance] of its zones ([FIELD_X], [FIELD_Z],
 *   [FIELD_WIDTH_ZONES] by [FIELD_HEIGHT_ZONES]), with no cows in it ("No other cows can be fought in this area"); the
 *   dairy cows are locs, so they come along and can be milked, as the Wiki says.
 * - **The way out**: in the instance the pen's gate is `fencegate_l_cowboss_exit` 65 and `_r_cowboss_exit` 64 (Leave,
 *   Quick-escape), which put the player back outside the real gate ([OUTSIDE]). Logging out or dying inside leaves by
 *   the instance's exit, as every instance does: the grave goes outside the gate, the player back to Lumbridge.
 * - **Brutus** (`cowboss` 15626, 3 by 3, 58 hitpoints) stands in the field at [SPAWN] and comes back 36 ticks after a
 *   kill (the Wiki's respawn; npc-combat.json). This step gives him his basic attack only; his charge and stomp are
 *   the next step. As he dies he says his last words (Transcript:Brutus). Items used on him are refused with the Wiki's
 *   two messages (an empty bucket has its own).
 * - **His loot** (step 3; the Wiki's Brutus, Drops, and Bull bones, read 2026-11-22): npc-drops.json's table, on his
 *   tile nearest the player ("Brutus' drops appear on the his tile closest to the player"). A kill counts for the
 *   player whose field it is, in the cache's `total_cowboss_kills` ([KILL_COUNT_VARP]), with the kill-count message,
 *   and the collection log's Brutus page shows it. Bull bones can't be taken, only buried where they lie, for 40
 *   Prayer XP (no confirmation yet, as giant bones). The steaks cook and eat in Cooking and food.json.
 *
 * GUESSED until recorded: where Release puts the player ([ENTRY], just south of the gate) and where Brutus stands
 * ([SPAWN]: in the field south of the pen, from the Wiki's safespot-cycle tiles near "his respawn point", 3261,3293,
 * 3259,3295 and 3258,3297, and its note that standing east of his spawn he never charges, the field's east fence
 * being at x 3266); the instance's extent (the field from 3240,3256 to 3271,3303, the dairy cows and the troughs in
 * it); the teleport's [SPOTANIM_TELEPORT] (`vfx_human_cowboss_teleport01`) and its [TELEPORT_TICKS]; Leave and
 * Quick-escape doing the same, with no menu; the menu's words and order. Left out: Brutus seen standing in his pen on
 * the real map (the cache has no NPC for that, so how live shows him is unknown), and the Ides of Milk quest's bull.
 */
class BrutusPens(private val world: World) {
    /** One player's field: the instance, and who it was made for. */
    class Run(val instance: Instance, val owner: String, val demonic: Boolean = false) {
        var boss: Npc? = null

        /** The tick of the player's first attack on this Brutus, for the scoreboard's times; -1 before it. */
        var fightStart = -1L

        fun inside(x: Int, z: Int) = Tile(instance.baseX + x - Brutus.FIELD_X, instance.baseZ + z - Brutus.FIELD_Z)
    }

    private val runs = HashMap<Int, Run>()

    fun runOf(player: Player): Run? = runAt(player.x, player.z)

    fun runAt(x: Int, z: Int): Run? {
        val instance = world.instances.at(x, z) ?: return null
        return runs[instance.id]?.takeIf { it.instance === instance }
    }

    /** Release on the pen's gate: the menu unless the player said not to ask again, then into a field of their own. */
    suspend fun ScriptContext.release() {
        arriveDelay()
        // With an abyssal potato, the offer to feed it to him first: fed, it's Demonic Brutus (step 6).
        var demonic = false
        if (player.inventory.count(DemonicBrutus.POTATO) > 0) {
            var pick = 0
            world.dialogues.start(this) { pick = choice(DemonicLines.FEED_YES, DemonicLines.FEED_NO, title = text(DemonicLines.FEED_TITLE)) }
            demonic = pick == 1
        }
        if (demonic) return enter(demonic = true)
        val askable = player.vars.types.varbit(Brutus.DONT_ASK_VARBIT) != null
        if (!askable || player.vars.getVarbit(Brutus.DONT_ASK_VARBIT) == 0) {
            var pick = 0
            world.dialogues.start(this) {
                pick = choice(BrutusLines.RELEASE_YES, BrutusLines.RELEASE_YES_ALWAYS, BrutusLines.RELEASE_NO, title = text(BrutusLines.RELEASE_TITLE))
            }
            if (pick != 1 && pick != 2) return
            if (pick == 2 && askable) player.vars.setVarbit(Brutus.DONT_ASK_VARBIT, 1)
        }
        enter(demonic = false)
    }

    private suspend fun ScriptContext.enter(demonic: Boolean) {
        if (demonic) {
            // Fed as he is released: the potato is gone before the fight begins.
            val slot = player.inventory.contents().firstOrNull { it.second.id == DemonicBrutus.POTATO }?.first ?: return
            player.inventory.remove(slot, 1)
        }
        player.spotanim(Brutus.SPOTANIM_TELEPORT)
        delay(Brutus.TELEPORT_TICKS)
        // Made only now, as the player goes in: an instance with nobody in it is ended at the end of the tick.
        val run = open(player, demonic) ?: return world.message(player, BrutusLines.NO_ROOM)
        val to = run.inside(Brutus.ENTRY.x, Brutus.ENTRY.z)
        world.teleport(player, to.x, to.z, 0)
    }

    /** A new field for [player], the way out in place of the gate in and Brutus in it; null when the dynamic area has no room. */
    fun open(player: Player, demonic: Boolean = false): Run? {
        // Ended fields are let go of here, as nothing else tells this when an instance ends.
        runs.values.removeAll { run -> world.instances.all.none { it === run.instance } }
        val instance = world.instances.create(
            Brutus.FIELD_X, Brutus.FIELD_Z, Brutus.FIELD_WIDTH_ZONES, Brutus.FIELD_HEIGHT_ZONES, Brutus.OUTSIDE.x, Brutus.OUTSIDE.z, 0,
        ) ?: return null
        val run = Run(instance, player.saveKey, demonic)
        runs[instance.id] = run
        for ((start, exit) in Brutus.GATE_SWAPS) {
            for (x in Brutus.GATE_X) {
                val at = run.inside(x, Brutus.GATE_Z)
                val loc = world.locs.find(start, at.x, at.z, 0) ?: continue
                world.locs.replace(loc, exit)
            }
        }
        val type = world.npcTypes[if (demonic) DemonicBrutus.BOSS else Brutus.BOSS]
        if (type != null) {
            val at = run.inside(Brutus.SPAWN.x, Brutus.SPAWN.z)
            // Demonic Brutus is for the one fight the potato bought: he doesn't come back.
            run.boss = world.spawnNpc(NpcSpawn(type, at.x, at.z, 0, source = "Brutus's field", respawns = !demonic))
        }
        return run
    }

    /** Leave or Quick-escape on the gate out: back outside the real gate. */
    suspend fun ScriptContext.leave() {
        arriveDelay()
        if (runOf(player) == null) return world.message(player, GameLines.NOTHING_INTERESTING)
        world.scripts.interrupt(player)
        world.teleport(player, Brutus.OUTSIDE.x, Brutus.OUTSIDE.z, 0)
    }
}

object Brutus {
    const val BOSS = 15626

    const val GATE_LEFT = 60763
    const val GATE_RIGHT = 60760
    const val EXIT_LEFT = 60765
    const val EXIT_RIGHT = 60764
    const val RELEASE = 1
    const val LEAVE = 1
    const val QUICK_ESCAPE = 2

    /** Each gate leaf of the real map, and the leaf of the way out that takes its place in a field. */
    val GATE_SWAPS = listOf(GATE_LEFT to EXIT_LEFT, GATE_RIGHT to EXIT_RIGHT)
    val GATE_X = listOf(3262, 3263)
    const val GATE_Z = 3294

    /** The locs whose handlers work in the fields, the instances: the way out's leaves and the dairy cows. */
    val INSTANCE_LOCS: Set<Int> = setOf(EXIT_LEFT, EXIT_RIGHT) + GroatsFarm.DAIRY_COWS +
        // The scoreboard west of the pen is inside the copied field: Read works there too.
        setOf(BrutusScoreboard.SCOREBOARD_MULTI, BrutusScoreboard.SCOREBOARD, BrutusScoreboard.SCOREBOARD_DEMONIC)

    /**
     * The tick [player] launched their attack on [boss] (as they swing, fire or cast, before it lands), or null when they
     * haven't attacked this Brutus. Their later attacks move it on, so it is read every tick until noted.
     */
    fun firstAttack(player: Player, boss: Npc): Long? =
        player.launchedAtNpcTick.takeIf { player.launchedAtNpc === boss && it >= boss.spawnTick }

    /** The cache's `cowboss_regular_dontaskagain`: Release goes straight in. */
    const val DONT_ASK_VARBIT = 20160

    /** `vfx_human_cowboss_teleport01`, and the ticks from Release to the field. Guessed: both. */
    const val SPOTANIM_TELEPORT = 3603
    const val TELEPORT_TICKS = 2

    const val BUCKET = 1925

    /** `bull_bones`: buried where they fall, never taken (OSRS Wiki, Bull bones), for 40 Prayer XP. */
    const val BULL_BONES = 33115
    const val BURY = 3
    const val BULL_BONES_XP_TENTHS = 400

    /** The cache's `total_cowboss_kills` and `total_cowboss_hardmode_kills`. */
    const val KILL_COUNT_VARP = 5407
    const val HARDMODE_KILL_COUNT_VARP = 5408

    /** His tile nearest [x], [z], his south-west tile on [bossX], [bossZ]: where his drop goes (OSRS Wiki, Brutus, Trivia). */
    fun nearestTile(bossX: Int, bossZ: Int, size: Int, x: Int, z: Int): Tile =
        Tile(x.coerceIn(bossX, bossX + size - 1), z.coerceIn(bossZ, bossZ + size - 1))

    /** The field's map: four by six zones from here, the pen and the eastern field with Gillie's dairy cows. */
    const val FIELD_X = 3240
    const val FIELD_Z = 3256
    const val FIELD_WIDTH_ZONES = 4
    const val FIELD_HEIGHT_ZONES = 6

    /** Outside the real gate, where leaving puts the player; inside a field, where Release does; Brutus's south-west tile. */
    val OUTSIDE = Tile(3262, 3293)
    val ENTRY = Tile(3262, 3293)
    val SPAWN = Tile(3261, 3288)
}

fun registerBrutus(world: World, pens: BrutusPens) {
    with(Brutus) {
        for (leaf in listOf(GATE_LEFT, GATE_RIGHT)) {
            world.handlers.onLoc(leaf, RELEASE) { with(pens) { release() } }
        }
        for (leaf in listOf(EXIT_LEFT, EXIT_RIGHT)) {
            world.handlers.onLoc(leaf, LEAVE) { with(pens) { leave() } }
            world.handlers.onLoc(leaf, QUICK_ESCAPE) { with(pens) { leave() } }
        }
        world.handlers.onObjOnNpc(BOSS) { world.message(player, BrutusLines.NOT_WANTED) }
        world.handlers.onObjOnNpc(BOSS, BUCKET) { world.message(player, BrutusLines.NOT_THE_BUCKET) }
        world.npcScripts.onDeath(listOf(BOSS)) { boss ->
            boss.effects.say(world.dialogueText[BrutusLines.DYING])
            // Guessed: the kill counts for the player whose field it is (the Wiki doesn't say; the field is theirs alone).
            val run = pens.runAt(boss.x, boss.z) ?: return@onDeath
            val player = world.onlinePlayers.firstOrNull { it.saveKey == run.owner && !it.loggedOut }
            // A kill on the first attack dies before his fight's next tick could note it: the attack's tick is the start.
            val start = run.fightStart.takeIf { it >= 0 } ?: player?.let { firstAttack(it, boss) }
            val ticks = start?.let { world.tick - it }
            run.fightStart = -1
            if (player == null) return@onDeath
            if (KILL_COUNT_VARP < player.vars.types.varpCount) {
                player.vars[KILL_COUNT_VARP] = player.vars[KILL_COUNT_VARP] + 1
                world.message(player, BrutusLines.KILL_COUNT, "count" to player.vars[KILL_COUNT_VARP])
            }
            BrutusScoreboard.killed(world, player, ticks)
        }
        // "Brutus' drops appear on the his tile closest to the player, rather than under his centre or south-west tile".
        world.dropTiles[BOSS] = { boss, killer ->
            if (killer == null) Tile(boss.x, boss.z) else nearestTile(boss.x, boss.z, boss.type.size, killer.x, killer.z)
        }
        // Bull bones: Bury on the floor, as giant bones (no confirmation yet), and no Take.
        world.handlers.onGroundObj(BULL_BONES, BURY) { op ->
            val obj = op.groundObj!!
            world.message(player, BonesLines.DIG)
            player.anim(Bones.ANIM_BURY)
            player.waypoints.clear()
            delay(1)
            if (!world.groundObjs.remove(obj)) return@onGroundObj
            player.skills.addTenths(Skill.PRAYER, BULL_BONES_XP_TENTHS)
            world.message(player, BonesLines.BURIED)
        }
    }
}
