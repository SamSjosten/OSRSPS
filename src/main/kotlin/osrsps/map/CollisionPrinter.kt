package osrsps.map

import osrsps.map.CollisionFlag.BLOCK_WALK
import osrsps.map.CollisionFlag.WALL_EAST
import osrsps.map.CollisionFlag.WALL_NORTH
import osrsps.map.CollisionFlag.WALL_SOUTH
import osrsps.map.CollisionFlag.WALL_WEST

/**
 * Draws collision as text, north up. Each tile is three characters: `###` blocked, `[` / `]` a wall on the
 * west / east side, `^` / `_` a wall on the north / south side (shown in the middle), `@` the centre tile.
 * Diagonal walls are shown as `x`.
 */
object CollisionPrinter {
    fun render(map: CollisionMap, centreX: Int, centreZ: Int, level: Int, radius: Int): String = buildString {
        appendLine("Collision around $centreX, $centreZ, level $level (north up, x ${centreX - radius}..${centreX + radius})")
        for (z in centreZ + radius downTo centreZ - radius) {
            append(z).append(' ')
            for (x in centreX - radius..centreX + radius) append(tile(map[x, z, level], x == centreX && z == centreZ))
            appendLine()
        }
    }

    private fun tile(flags: Int, centre: Boolean): String {
        if (flags and BLOCK_WALK != 0) return "###"
        val west = if (flags and WALL_WEST != 0) '[' else ' '
        val east = if (flags and WALL_EAST != 0) ']' else ' '
        val middle = when {
            centre -> '@'
            flags and WALL_NORTH != 0 && flags and WALL_SOUTH != 0 -> '='
            flags and WALL_NORTH != 0 -> '^'
            flags and WALL_SOUTH != 0 -> '_'
            flags and 0x55 != 0 -> 'x'
            else -> '.'
        }
        return "$west$middle$east"
    }
}
