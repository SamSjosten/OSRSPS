package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.CombatLines
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.map.Tile

/** Peeve's words in the Wilderness Slayer Cave (the Wiki's Transcript:Peeve). */
object PeeveLines : Lines("peeve", "Transcript:Peeve") {
    val WOO = line("woo", "his, to a player without a ghostspeak amulet attacking a task-only monster off their task")
    val SNEAKY = line("sneaky", "his, to a player with a ghostspeak amulet attacking a task-only monster off their task")
}

/**
 * The Wilderness Slayer Cave, levels 17 to 32 (Wilderness step W6.2; the Wiki's Wilderness Slayer Cave, Crevice
 * (Wilderness Slayer Cave), Peeve, Transcript:Peeve and each monster's page, read 2026-11-15). Members only, all
 * multi-way combat (multi-combat.tsv).
 *
 * - **Two ways in and out**: the level 18 stairs (`wild_slayer_cave_south_entrance` 40388 at 3259,3664, Walk-down) to
 *   the southern opening (40389 at 3384,10050, Exit), and the level 29 stairs (40390 at 3293,3746) to the northern
 *   opening (40391 at 3405,10146).
 * - **The monsters** (npc-spawns/wilderness-slayer-cave.json): the cave's own versions of thirteen kinds, where the
 *   Wiki's maps pin them, with their stronger numbers and drops (npc-combat.json, npc-drops.json); their drops roll the
 *   Wiki's "Wilderness Slayer Cave drop table" of Justine's stuff beside the main table, at each kind's chance.
 * - **Task only**: "Abyssal demons, Nechryaels, Dust devils, and Jellies can only be killed while on Slayer task ... If
 *   a player attempts to kill these monsters while not on the corresponding task, they will be stopped by Peeve", a
 *   ghost who wanders the cave ([TASK_ONLY], [peeve]).
 * - **The crevices** (53259, 77 Agility, 10 XP) are obstacles in skills/agility.json.
 *
 * Where the ways in and out put the player is from RuneLite's Shortest Path plugin's transports (read 2026-11-15).
 * GUESSED: where Peeve wanders; his words as a chat with him wherever he is in the cave. Not built: the cannon, the
 * 15% better Larran's key chance on a Wilderness task (Larran's keys aren't built), the crevices' message.
 */
object WildernessSlayerCave {
    const val SOUTH_STAIRS = 40388
    const val SOUTH_EXIT = 40389
    const val NORTH_STAIRS = 40390
    const val NORTH_EXIT = 40391
    const val OP = 1

    val SOUTH_ARRIVAL = Tile(3385, 10052)
    val NORTH_ARRIVAL = Tile(3406, 10145)
    val SOUTH_OUT = Tile(3259, 3663)
    val NORTH_OUT = Tile(3294, 3749)

    const val PEEVE = 11237
    const val GHOSTSPEAK_AMULET = 552

    /** The cave's abyssal demon, dust devil, Greater Nechryael and five jellies. */
    val TASK_ONLY = setOf(11239, 11238, 11240, 11241, 11242, 11243, 11244, 11245)

    /** The cave: map squares 52,157 to 53,158 on the ground level, which hold nothing else (the 242 cache's map). */
    fun inCave(x: Int, z: Int): Boolean = x in 3328..3455 && z in 10048..10175

    /** Whether [npc] is one [player] may not attack: task-only, and not their task. */
    fun guarded(world: World, player: Player, npc: Npc): Boolean = npc.type.id in TASK_ONLY && !world.slayer.onTask(player, npc)

    /** Peeve, if he is in the world. */
    fun peeve(world: World): Npc? = world.npcs.all.firstOrNull { it.type.id == PEEVE && !it.outOfPlay }

    /** Peeve stops [player]: his woo, or with a ghostspeak amulet worn, his words. */
    fun stop(world: World, player: Player) {
        val peeve = peeve(world)
        val worn = player.equipment.count(GHOSTSPEAK_AMULET) > 0
        val line = if (worn) PeeveLines.SNEAKY else PeeveLines.WOO
        if (peeve == null) return world.message(player, CombatLines.TASK_ONLY)
        world.scripts.start(player) { world.dialogues.start(this, peeve) { chatNpc(ChatAnim.NEUTRAL, line) } }
    }
}

fun registerWildernessSlayerCave(world: World) {
    with(WildernessSlayerCave) {
        for ((loc, to) in listOf(SOUTH_STAIRS to SOUTH_ARRIVAL, NORTH_STAIRS to NORTH_ARRIVAL, SOUTH_EXIT to SOUTH_OUT, NORTH_EXIT to NORTH_OUT)) {
            world.handlers.onLoc(loc, OP) {
                arriveDelay()
                world.teleport(player, to.x, to.z, 0)
            }
        }
        val taskOnly = world.combat.taskOnly
        world.combat.taskOnly = { player, npc -> guarded(world, player, npc) || taskOnly(player, npc) }
        val tell = world.combat.tellBarred
        world.combat.tellBarred = { player, npc, line ->
            if (line == CombatLines.TASK_ONLY && npc.type.id in TASK_ONLY) {
                stop(world, player)
                true
            } else {
                tell(player, npc, line)
            }
        }
    }
}
