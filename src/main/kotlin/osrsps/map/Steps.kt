package osrsps.map

import osrsps.map.CollisionFlag.BLOCK_WALK
import osrsps.map.CollisionFlag.WALL_EAST
import osrsps.map.CollisionFlag.WALL_NORTH
import osrsps.map.CollisionFlag.WALL_NORTH_EAST
import osrsps.map.CollisionFlag.WALL_NORTH_WEST
import osrsps.map.CollisionFlag.WALL_SOUTH
import osrsps.map.CollisionFlag.WALL_SOUTH_EAST
import osrsps.map.CollisionFlag.WALL_SOUTH_WEST
import osrsps.map.CollisionFlag.WALL_WEST

/** Whether a single step is allowed: the checks the route finder makes, for one tile or a larger square. */
object Steps {
    /**
     * One step of ([dx], [dz]), each -1 to 1, from [x], [z] by something one tile big. [block] is what stops a tile
     * being stepped onto besides its walls, and every tile the step crosses must also have all of [need]: NPCs that
     * keep to water ([osrsps.game.npc.MoveRestrict.BLOCKED]) step only where the floor is blocked.
     */
    fun canStep(collision: CollisionMap, level: Int, x: Int, z: Int, dx: Int, dz: Int, block: Int = BLOCK_WALK, need: Int = 0): Boolean {
        fun open(ox: Int, oz: Int, walls: Int): Boolean {
            val flags = collision[x + ox, z + oz, level]
            return flags and (block or walls) == 0 && flags and need == need
        }
        val westEast = when (dx) {
            -1 -> open(-1, 0, WALL_EAST)
            1 -> open(1, 0, WALL_WEST)
            else -> true
        }
        val southNorth = when (dz) {
            -1 -> open(0, -1, WALL_NORTH)
            1 -> open(0, 1, WALL_SOUTH)
            else -> true
        }
        if (dx == 0 || dz == 0) return westEast && southNorth && (dx != 0 || dz != 0)
        val corner = when {
            dx < 0 && dz < 0 -> WALL_NORTH or WALL_NORTH_EAST or WALL_EAST
            dx > 0 && dz < 0 -> WALL_NORTH_WEST or WALL_NORTH or WALL_WEST
            dx < 0 -> WALL_EAST or WALL_SOUTH_EAST or WALL_SOUTH
            else -> WALL_SOUTH or WALL_SOUTH_WEST or WALL_WEST
        }
        return westEast && southNorth && open(dx, dz, corner)
    }

    /**
     * One step by something [size] tiles square whose south-west tile is [x], [z], with [block] and [need] as for one
     * tile.
     *
     * Inferred, not checked against live: the square moves as its tiles would one by one. Each tile on its leading
     * edge must be able to take the step on its own, and no wall may cut across the tiles it moves onto. That reads the
     * same as the client's rules for one tile and matches what large NPCs are seen to do (a cow can't straddle a
     * fence), but the corner cases with pillars haven't been compared with live.
     */
    fun canStep(collision: CollisionMap, level: Int, x: Int, z: Int, dx: Int, dz: Int, size: Int, block: Int = BLOCK_WALK, need: Int = 0): Boolean {
        if (size <= 1) return canStep(collision, level, x, z, dx, dz, block, need)
        if (dx == 0 && dz == 0) return false
        fun inOld(tx: Int, tz: Int) = tx in x until x + size && tz in z until z + size
        for (tx in x until x + size) {
            for (tz in z until z + size) {
                // Only the leading tiles step onto ground the square doesn't already cover.
                if (inOld(tx + dx, tz + dz)) continue
                if (!canStep(collision, level, tx, tz, dx, dz, block, need)) return false
            }
        }
        // Walls between two tiles the square moves onto, at least one of them new to it. Walls are flagged on both
        // tiles they separate, so the far tile's flag is enough.
        val newX = x + dx
        val newZ = z + dz
        for (tx in newX until newX + size) {
            for (tz in newZ until newZ + size) {
                val eastNew = !(inOld(tx, tz) && inOld(tx + 1, tz))
                if (tx + 1 < newX + size && eastNew && collision[tx + 1, tz, level] and WALL_WEST != 0) return false
                val northNew = !(inOld(tx, tz) && inOld(tx, tz + 1))
                if (tz + 1 < newZ + size && northNew && collision[tx, tz + 1, level] and WALL_SOUTH != 0) return false
            }
        }
        return true
    }
}
