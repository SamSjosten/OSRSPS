package osrsps.game.skill.hunter

import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape

/**
 * A net trap set on a young tree of one colour: the [tree] it is set on (Set-trap), the tree bending while it is set
 * ([setting]), bent over the net once set ([treeSet], [netSet]), and back up beside the fallen net once it fails
 * ([treeReset], [netReset], both with Dismantle and Reset). A catch or a miss shows one 1 by 2 loc over the tree and
 * the net (a creature's [Creature.catching] and [Creature.full], and [failing]). The net lies on the tile beside the
 * tree the player set it from; that is the trap's own tile, where the creature has to step.
 */
class NetTrapKind(
    name: String,
    val tree: Int,
    val setting: Int,
    val treeSet: Int,
    netSet: Int,
    val treeReset: Int,
    netReset: Int,
    failing: Int,
    creature: Creature,
) : TrapKind(name, NetTraps.NET_TRAP, netSet, failing, netReset, NetTraps.SET_ANIM, listOf(creature), HunterLines.INVESTIGATE_NET) {
    /** The Hunter level Set-trap needs: the salamander's. */
    override val level: Int get() = creatures.single().level

    override val laidFromItem: Boolean get() = false
    override val setLocs: List<Int> get() = listOf(treeSet, set)
    override val brokenLocs: List<Int> get() = listOf(treeReset, broken)

    override fun look(trap: Trap, state: TrapState, locId: Int): TrapLook {
        val tree = trap.anchor ?: error("A net trap is set on a tree")
        val net = ZoneLoc(locId, LocShape.CENTREPIECE_STRAIGHT, trap.rotation, trap.x, trap.z, trap.level)
        return when (state) {
            TrapState.SET -> TrapLook(listOf(tree.copy(id = treeSet), net.copy(id = set)))
            TrapState.BROKEN -> TrapLook(listOf(tree.copy(id = treeReset), net.copy(id = broken)))
            else -> {
                // Turned north or east (0, 1) the 1 by 2 loc starts on the tree's tile and takes its place; turned south or
                // west, on the net's, and the tree has to go while it stands.
                val fromTree = trap.rotation <= 1
                val over = ZoneLoc(locId, LocShape.CENTREPIECE_STRAIGHT, trap.rotation, if (fromTree) tree.x else trap.x, if (fromTree) tree.z else trap.z, trap.level)
                TrapLook(listOf(over), hidden = if (fromTree) emptyList() else listOf(tree))
            }
        }
    }

    /** Only the ground loc layer matters: the net lies among grass and flowers, and beside fences. */
    override fun fits(locs: List<ZoneLoc>): Boolean = locs.none { it.layer == GROUND_LAYER }

    companion object {
        private val GROUND_LAYER = osrsps.game.zone.ZoneLoc.layerOf(LocShape.CENTREPIECE_STRAIGHT)

        /** The rotation for a net [dx], [dz] off its tree: 0 north, 1 east, 2 south, 3 west; null unless square to a side. */
        fun sideOf(dx: Int, dz: Int): Int? = when {
            dx == 0 && dz == 1 -> 0
            dx == 1 && dz == 0 -> 1
            dx == 0 && dz == -1 -> 2
            dx == -1 && dz == 0 -> 3
            else -> null
        }
    }
}

/**
 * Net trapping (Hunter step 3): a net trap set on a young tree near swamp lizards, orange salamanders and red
 * salamanders.
 *
 * Sources: the OSRS Wiki's Net trap page (read 2026-11-15) for how it works: Set-trap on a young tree with a net trap
 * in the backpack; a creature passing over the net may be caught, never while the player stands on it; a miss lets the
 * tree spring back and leaves the net, to Reset or Dismantle (the failed state of 7 October 2026); about a minute
 * later the trap falls back to its item, which goes a minute after. Each creature's page (Swamp lizard (Hunter),
 * Orange salamander (Hunter), Red salamander (Hunter)) for its level, XP, catch pair (the Skilling success chart, Mod
 * Ash's 23 March 2022 figures: green 20% to 141%, orange 6% to 113%, red 0% to 94%), drop (the salamander) and where it
 * lives; the Net trap (item) page for the item. Ids and shapes from the rev 242 cache: the item net_trap, the trees and
 * nets hunting_sapling_up/setting/set/net_set/up_reset/net_reset_<colour> (1 by 1, the nets walkable), the
 * catching/full/failing locs (1 by 2, walkable), the player's hunting_setting_sapling_trap. RuneLite's Hunter plugin for
 * where the net goes: on the tile the player stood on, turned to the tree, the 1 by 2 locs starting on the tree's tile
 * when the net is north or east of it and on the net's when south or west.
 *
 * Guessed: Set-trap needs the salamander's level (the Net trap (item) page says only that using one needs it); the
 * player must stand square to a side of the tree, and steps off the net after (west, else east, south, north, as for
 * the other traps); the tree bends for the 4 ticks of laying. Left out: black salamanders (the Wilderness thread), tecu
 * salamanders (Varlamore), baiting with tar and smoking, the claws (only on a Hunters' Rumour), the salamanders as
 * weapons (combat), Release, and making a net trap from a rope and a small fishing net.
 */
object NetTraps {
    const val NET_TRAP = 34631 // net_trap

    /** hunting_setting_sapling_trap. */
    const val SET_ANIM = 5215

    const val SWAMP_LIZARD = 2906 // salamander_green
    const val ORANGE_SALAMANDER = 2903 // salamander_orange
    const val RED_SALAMANDER = 2904 // salamander_red

    const val SWAMP_LIZARD_ITEM = 10149 // green_salamander
    const val ORANGE_SALAMANDER_ITEM = 10146 // orange_salamander
    const val RED_SALAMANDER_ITEM = 10147 // red_salamander

    /** Each colour: up, setting, set, net_set, up_reset, net_reset, failing; its creature with catching and full. */
    val SWAMP = NetTrapKind(
        "net trap", tree = 9341, setting = 9342, treeSet = 9257, netSet = 9343, treeReset = 62528, netReset = 62529, failing = 9005,
        Creature(SWAMP_LIZARD, 29, 1520, 52, 360, 9003, 9004, listOf(CreatureLoot(SWAMP_LIZARD_ITEM, 1))),
    )
    val ORANGE = NetTrapKind(
        "net trap", tree = 8732, setting = 8733, treeSet = 8730, netSet = 8731, treeReset = 62522, netReset = 62523, failing = 8974,
        Creature(ORANGE_SALAMANDER, 47, 2240, 16, 288, 8972, 8734, listOf(CreatureLoot(ORANGE_SALAMANDER_ITEM, 1))),
    )
    val RED = NetTrapKind(
        "net trap", tree = 8990, setting = 8991, treeSet = 8989, netSet = 8992, treeReset = 62524, netReset = 62525, failing = 8987,
        Creature(RED_SALAMANDER, 59, 2720, 0, 240, 8985, 8986, listOf(CreatureLoot(RED_SALAMANDER_ITEM, 1))),
    )

    val KINDS = listOf(SWAMP, ORANGE, RED)
}
