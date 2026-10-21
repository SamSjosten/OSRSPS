package osrsps.map

import osrsps.map.CollisionFlag.BLOCK_WALK
import osrsps.map.CollisionFlag.WALL_EAST
import osrsps.map.CollisionFlag.WALL_NORTH
import osrsps.map.CollisionFlag.WALL_SOUTH
import osrsps.map.CollisionFlag.WALL_WEST

/**
 * Where a route should end: on a tile, or anywhere a loc can be used from.
 *
 * [x] and [z] are the south-west tile. [width] and [length] are the footprint as placed, already turned by [angle].
 * [shape] is the loc's shape, or [TILE] for a plain tile. [accessBlock] is the sides the loc can't be used from, also
 * already turned: 1 north, 2 east, 4 south, 8 west.
 */
data class RouteTarget(
    val x: Int,
    val z: Int,
    val width: Int = 1,
    val length: Int = 1,
    val shape: Int = TILE,
    val angle: Int = 0,
    val accessBlock: Int = 0,
) {
    companion object {
        const val TILE = -1

        fun tile(x: Int, z: Int) = RouteTarget(x, z)

        /**
         * A loc of [shape] and [angle] at [x], [z]. Ground locs and ground decorations (shapes 10, 11 and 22) use their
         * type's [width], [length] and [accessBlock], turned by the angle as the client turns them; walls and wall
         * decorations take up one tile.
         */
        fun loc(x: Int, z: Int, shape: Int, angle: Int, width: Int, length: Int, accessBlock: Int): RouteTarget {
            if (shape != LocShape.CENTREPIECE_STRAIGHT && shape != LocShape.CENTREPIECE_DIAGONAL && shape != LocShape.GROUND_DECOR) {
                return RouteTarget(x, z, 1, 1, shape, angle)
            }
            val turned = angle and 1 == 1
            // The client turns the four side bits left by the angle, so north becomes east after a quarter turn. Its
            // sum, `(flags << angle & 15) + (flags >> 4 - angle)`, doesn't mask off the bits above the fourth first, so
            // they can fall back in and carry (29, a common value, has bit 16 set); kept as the client has it.
            val access = if (angle == 0) accessBlock else ((accessBlock shl angle) and 0xF) + (accessBlock shr (4 - angle))
            return RouteTarget(x, z, if (turned) length else width, if (turned) width else length, shape, angle, access)
        }
    }
}

/**
 * Whether a 1x1 mover standing on a tile has reached a [RouteTarget], following the client's reach rules: the same
 * checks its route finder makes for each tile it visits. Written from the client's behaviour as open-source route
 * finders document it; not checked against live yet, so mark any surprise against a recording here.
 *
 * - A tile is reached only by standing on it.
 * - A wall (shapes 0 to 3, and 9) is reached from its own tile or the tile on its far side, or from a tile beside it
 *   with no wall in between.
 * - A wall decoration (4 to 8) is reached from its own tile; the diagonal and the all-round ones (6 to 8) also from
 *   the neighbours on their open sides.
 * - A ground loc or ground decoration is reached from inside its footprint or from any tile along one of its sides
 *   that the loc doesn't block access from, with no wall between.
 * - Roofs and other shapes are reached only from their own tile.
 */
object Reach {
    private const val ACCESS_NORTH = 0x1
    private const val ACCESS_EAST = 0x2
    private const val ACCESS_SOUTH = 0x4
    private const val ACCESS_WEST = 0x8

    fun reached(collision: CollisionMap, level: Int, srcX: Int, srcZ: Int, target: RouteTarget): Boolean {
        val shape = target.shape
        return when {
            shape == RouteTarget.TILE -> srcX == target.x && srcZ == target.z
            shape in LocShape.WALL_STRAIGHT..LocShape.WALL_SQUARE_CORNER || shape == LocShape.WALL_DIAGONAL ->
                reachWall(collision[srcX, srcZ, level], srcX - target.x, srcZ - target.z, shape, target.angle)
            shape in 4..8 -> reachWallDecoration(collision[srcX, srcZ, level], srcX - target.x, srcZ - target.z, shape, target.angle)
            shape == LocShape.CENTREPIECE_STRAIGHT || shape == LocShape.CENTREPIECE_DIAGONAL || shape == LocShape.GROUND_DECOR ->
                reachRectangle(collision[srcX, srcZ, level], srcX, srcZ, target)
            else -> srcX == target.x && srcZ == target.z
        }
    }

    /** [flags] are the mover's own tile; [dx] and [dz] are its offset from the wall's tile. */
    private fun reachWall(flags: Int, dx: Int, dz: Int, shape: Int, angle: Int): Boolean {
        if (dx == 0 && dz == 0) return true
        fun open(wall: Int) = flags and (BLOCK_WALK or wall) == 0
        val west = dx == -1 && dz == 0
        val east = dx == 1 && dz == 0
        val south = dx == 0 && dz == -1
        val north = dx == 0 && dz == 1
        return when (shape) {
            LocShape.WALL_STRAIGHT -> when (angle) {
                // The wall is on the west side of its tile: reached from the west, or from north or south of the tile.
                0 -> west || (north && open(WALL_SOUTH)) || (south && open(WALL_NORTH))
                1 -> north || (west && open(WALL_EAST)) || (east && open(WALL_WEST))
                2 -> east || (north && open(WALL_SOUTH)) || (south && open(WALL_NORTH))
                else -> south || (west && open(WALL_EAST)) || (east && open(WALL_WEST))
            }
            LocShape.WALL_L -> when (angle) {
                // Walls on the west and north sides.
                0 -> west || north || (east && open(WALL_WEST)) || (south && open(WALL_NORTH))
                1 -> north || east || (west && open(WALL_EAST)) || (south && open(WALL_NORTH))
                2 -> east || south || (west && open(WALL_EAST)) || (north && open(WALL_SOUTH))
                else -> south || west || (east && open(WALL_WEST)) || (north && open(WALL_SOUTH))
            }
            LocShape.WALL_DIAGONAL -> (north && open(WALL_SOUTH)) || (south && open(WALL_NORTH)) ||
                (west && open(WALL_EAST)) || (east && open(WALL_WEST))
            // Corners only from their own tile.
            else -> false
        }
    }

    private fun reachWallDecoration(flags: Int, dx: Int, dz: Int, shape: Int, angle: Int): Boolean {
        if (dx == 0 && dz == 0) return true
        fun open(wall: Int) = flags and (BLOCK_WALK or wall) == 0
        val west = dx == -1 && dz == 0 && open(WALL_EAST)
        val east = dx == 1 && dz == 0 && open(WALL_WEST)
        val south = dx == 0 && dz == -1 && open(WALL_NORTH)
        val north = dx == 0 && dz == 1 && open(WALL_SOUTH)
        return when (shape) {
            6, 7 -> when (if (shape == 7) (angle + 2) and 3 else angle) {
                0 -> east || south
                1 -> west || south
                2 -> west || north
                else -> east || north
            }
            8 -> west || east || south || north
            else -> false
        }
    }

    private fun reachRectangle(flags: Int, srcX: Int, srcZ: Int, target: RouteTarget): Boolean {
        val eastX = target.x + target.width - 1
        val northZ = target.z + target.length - 1
        if (srcX in target.x..eastX && srcZ in target.z..northZ) return true
        val alongZ = srcZ in target.z..northZ
        val alongX = srcX in target.x..eastX
        val access = target.accessBlock
        return (srcX == target.x - 1 && alongZ && flags and WALL_EAST == 0 && access and ACCESS_WEST == 0) ||
            (srcX == eastX + 1 && alongZ && flags and WALL_WEST == 0 && access and ACCESS_EAST == 0) ||
            (srcZ == target.z - 1 && alongX && flags and WALL_NORTH == 0 && access and ACCESS_SOUTH == 0) ||
            (srcZ == northZ + 1 && alongX && flags and WALL_SOUTH == 0 && access and ACCESS_NORTH == 0)
    }
}
