package osrsps.game.command

import osrsps.game.Wilderness
import osrsps.game.World

/**
 * `::wildy <level>` jumps to that Wilderness level on the line north of Edgeville (x 3090), on the first tile of the
 * level's 8 rows that can be stood on, looking a few tiles either side when the line itself is blocked there. Level 1
 * lands north of the ditch, inside.
 */
fun CommandRegistry.registerWildernessCommands(world: World) {
    register("wildy", "::wildy level") {
        val level = args.singleOrNull()?.toIntOrNull() ?: fail("Usage: ::wildy level (1 to ${WildernessJumps.MAX_LEVEL})")
        if (level !in 1..WildernessJumps.MAX_LEVEL) fail("Usage: ::wildy level (1 to ${WildernessJumps.MAX_LEVEL})")
        val (x, z) = WildernessJumps.tileFor(level) { x, z -> world.hasMap(x, z) && world.canStand(x, z, 0) }
            ?: fail("Nowhere to stand at level $level near x ${WildernessJumps.LINE_X}")
        // As a click does: whatever the player had open closes and its script is dropped.
        world.scripts.interrupt(player)
        world.teleport(player, x, z, 0)
        reply("Jumped to $x, $z: level $level Wilderness")
    }
}

internal object WildernessJumps {
    const val LINE_X = 3090
    const val MAX_LEVEL = 56
    private const val SEARCH = 16

    /** The tile [level] jumps to: rows south to north, the line first and then out either side; null when none [fits]. */
    fun tileFor(level: Int, fits: (x: Int, z: Int) -> Boolean): Pair<Int, Int>? {
        val south = maxOf(Wilderness.DITCH_NORTH + 1, 3520 + (level - 1) * 8)
        val north = 3520 + level * 8 - 1
        for (z in south..north) {
            for (offset in 0..SEARCH) {
                for (x in listOf(LINE_X + offset, LINE_X - offset).distinct()) {
                    if (Wilderness.level(x, z) == level && Wilderness.inside(x, z) && fits(x, z)) return x to z
                }
            }
        }
        return null
    }
}
