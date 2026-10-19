package osrsps.map

import osrsps.map.CollisionFlag.FLOOR
import osrsps.map.CollisionFlag.FLOOR_DECORATION
import osrsps.map.CollisionFlag.LOC
import osrsps.map.CollisionFlag.LOC_PROJ
import osrsps.map.CollisionFlag.PROJ_SHIFT
import osrsps.map.CollisionFlag.WALL_EAST
import osrsps.map.CollisionFlag.WALL_NORTH
import osrsps.map.CollisionFlag.WALL_NORTH_EAST
import osrsps.map.CollisionFlag.WALL_NORTH_WEST
import osrsps.map.CollisionFlag.WALL_SOUTH
import osrsps.map.CollisionFlag.WALL_SOUTH_EAST
import osrsps.map.CollisionFlag.WALL_SOUTH_WEST
import osrsps.map.CollisionFlag.WALL_WEST

/** The collision a loc type adds: its footprint and how it blocks. */
data class LocCollisionType(
    val width: Int = 1,
    val length: Int = 1,
    /** 0 = walk through it, 1 = blocks, 2 = blocks (the default; also used for interaction). */
    val interactType: Int = 2,
    val blocksProjectile: Boolean = true,
)

/** Loc shapes, as stored in the map files. */
object LocShape {
    const val WALL_STRAIGHT = 0
    const val WALL_DIAGONAL_CORNER = 1
    const val WALL_L = 2
    const val WALL_SQUARE_CORNER = 3
    const val WALL_DIAGONAL = 9
    const val CENTREPIECE_STRAIGHT = 10
    const val CENTREPIECE_DIAGONAL = 11
    const val GROUND_DECOR = 22
}

/** Adds terrain and loc collision the way the client does when it builds the scene. */
class LocCollision(private val map: CollisionMap) {
    fun addFloor(x: Int, z: Int, level: Int) = map.add(x, z, level, FLOOR)

    fun addLoc(type: LocCollisionType, x: Int, z: Int, level: Int, shape: Int, rotation: Int) {
        when (shape) {
            LocShape.GROUND_DECOR -> if (type.interactType == 1) map.add(x, z, level, FLOOR_DECORATION)
            in LocShape.WALL_STRAIGHT..LocShape.WALL_SQUARE_CORNER ->
                if (type.interactType != 0) addWall(x, z, level, shape, rotation, type.blocksProjectile)
            // Wall decorations (4 to 8) never block.
            in 4..8 -> {}
            else -> if (type.interactType != 0) {
                // A loc rotated a quarter turn swaps its footprint.
                val (width, length) = if (rotation and 1 == 1) type.length to type.width else type.width to type.length
                addSolid(x, z, level, width, length, type.blocksProjectile)
            }
        }
    }

    private fun addSolid(x: Int, z: Int, level: Int, width: Int, length: Int, blocksProjectile: Boolean) {
        val mask = if (blocksProjectile) LOC or LOC_PROJ else LOC
        for (dx in 0 until width) for (dz in 0 until length) map.add(x + dx, z + dz, level, mask)
    }

    private fun addWall(x: Int, z: Int, level: Int, shape: Int, rotation: Int, blocksProjectile: Boolean) {
        val add = { tx: Int, tz: Int, mask: Int ->
            map.add(tx, tz, level, if (blocksProjectile) mask or (mask shl PROJ_SHIFT) else mask)
        }
        when (shape) {
            LocShape.WALL_STRAIGHT -> when (rotation) {
                0 -> { add(x, z, WALL_WEST); add(x - 1, z, WALL_EAST) }
                1 -> { add(x, z, WALL_NORTH); add(x, z + 1, WALL_SOUTH) }
                2 -> { add(x, z, WALL_EAST); add(x + 1, z, WALL_WEST) }
                else -> { add(x, z, WALL_SOUTH); add(x, z - 1, WALL_NORTH) }
            }
            LocShape.WALL_DIAGONAL_CORNER, LocShape.WALL_SQUARE_CORNER -> when (rotation) {
                0 -> { add(x, z, WALL_NORTH_WEST); add(x - 1, z + 1, WALL_SOUTH_EAST) }
                1 -> { add(x, z, WALL_NORTH_EAST); add(x + 1, z + 1, WALL_SOUTH_WEST) }
                2 -> { add(x, z, WALL_SOUTH_EAST); add(x + 1, z - 1, WALL_NORTH_WEST) }
                else -> { add(x, z, WALL_SOUTH_WEST); add(x - 1, z - 1, WALL_NORTH_EAST) }
            }
            LocShape.WALL_L -> when (rotation) {
                0 -> { add(x, z, WALL_NORTH or WALL_WEST); add(x - 1, z, WALL_EAST); add(x, z + 1, WALL_SOUTH) }
                1 -> { add(x, z, WALL_NORTH or WALL_EAST); add(x, z + 1, WALL_SOUTH); add(x + 1, z, WALL_WEST) }
                2 -> { add(x, z, WALL_SOUTH or WALL_EAST); add(x + 1, z, WALL_WEST); add(x, z - 1, WALL_NORTH) }
                else -> { add(x, z, WALL_SOUTH or WALL_WEST); add(x, z - 1, WALL_NORTH); add(x - 1, z, WALL_EAST) }
            }
        }
    }
}
