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

/** A route: the turning points to walk through in order, ending at the tile actually reached. */
data class Route(val waypoints: List<Tile>, val alternative: Boolean) {
    val destination: Tile? get() = waypoints.lastOrNull()
}

data class Tile(val x: Int, val z: Int)

/**
 * The client's breadth-first route finder for a 1x1 mover walking to a tile, written from the client's
 * documented behaviour. It searches a 128x128 area centred on the start. Neighbours are tried west, east,
 * south, north, then south-west, south-east, north-west, north-east, so ties between equal-length paths go
 * the same way the client's do. When the tile can't be reached it picks the closest reached tile within
 * [ALTERNATIVE_RANGE] of it, nearest by straight-line distance and then by path length, the way clicking on
 * water walks you to the shore.
 */
class RouteFinder(private val collision: CollisionMap) {
    private val directions = IntArray(SIZE * SIZE)
    private val distances = IntArray(SIZE * SIZE)
    private val queueX = IntArray(SIZE * SIZE)
    private val queueZ = IntArray(SIZE * SIZE)

    fun find(level: Int, srcX: Int, srcZ: Int, destX: Int, destZ: Int): Route {
        val baseX = srcX - SIZE / 2
        val baseZ = srcZ - SIZE / 2
        directions.fill(0)
        distances.fill(UNVISITED)
        val startX = srcX - baseX
        val startZ = srcZ - baseZ
        val targetX = destX - baseX
        val targetZ = destZ - baseZ

        var endX = targetX
        var endZ = targetZ
        var alternative = false
        if (!search(level, baseX, baseZ, startX, startZ, targetX, targetZ)) {
            val closest = closestReached(targetX, targetZ) ?: return Route(emptyList(), true)
            endX = closest shr 16
            endZ = closest and 0xFFFF
            alternative = true
        }
        if (endX == startX && endZ == startZ) return Route(emptyList(), alternative)
        return Route(backtrack(baseX, baseZ, startX, startZ, endX, endZ), alternative)
    }

    private fun search(level: Int, baseX: Int, baseZ: Int, startX: Int, startZ: Int, targetX: Int, targetZ: Int): Boolean {
        var head = 0
        var tail = 0
        visit(startX, startZ, START, 0)
        queueX[tail] = startX
        queueZ[tail++] = startZ
        while (head != tail) {
            val x = queueX[head]
            val z = queueZ[head++]
            if (x == targetX && z == targetZ) return true
            val absX = baseX + x
            val absZ = baseZ + z
            val next = distances[index(x, z)] + 1
            fun open(dx: Int, dz: Int, mask: Int) = collision[absX + dx, absZ + dz, level] and mask == 0
            fun step(nx: Int, nz: Int, came: Int) {
                visit(nx, nz, came, next)
                queueX[tail] = nx
                queueZ[tail++] = nz
            }
            val west = x > 0 && open(-1, 0, BLOCK_WALK or WALL_EAST)
            val east = x < SIZE - 1 && open(1, 0, BLOCK_WALK or WALL_WEST)
            val south = z > 0 && open(0, -1, BLOCK_WALK or WALL_NORTH)
            val north = z < SIZE - 1 && open(0, 1, BLOCK_WALK or WALL_SOUTH)
            if (west && unvisited(x - 1, z)) step(x - 1, z, FROM_EAST)
            if (east && unvisited(x + 1, z)) step(x + 1, z, FROM_WEST)
            if (south && unvisited(x, z - 1)) step(x, z - 1, FROM_NORTH)
            if (north && unvisited(x, z + 1)) step(x, z + 1, FROM_SOUTH)
            if (west && south && unvisited(x - 1, z - 1) &&
                open(-1, -1, BLOCK_WALK or WALL_NORTH or WALL_NORTH_EAST or WALL_EAST)
            ) step(x - 1, z - 1, FROM_NORTH or FROM_EAST)
            if (east && south && unvisited(x + 1, z - 1) &&
                open(1, -1, BLOCK_WALK or WALL_NORTH_WEST or WALL_NORTH or WALL_WEST)
            ) step(x + 1, z - 1, FROM_NORTH or FROM_WEST)
            if (west && north && unvisited(x - 1, z + 1) &&
                open(-1, 1, BLOCK_WALK or WALL_EAST or WALL_SOUTH_EAST or WALL_SOUTH)
            ) step(x - 1, z + 1, FROM_SOUTH or FROM_EAST)
            if (east && north && unvisited(x + 1, z + 1) &&
                open(1, 1, BLOCK_WALK or WALL_SOUTH or WALL_SOUTH_WEST or WALL_WEST)
            ) step(x + 1, z + 1, FROM_SOUTH or FROM_WEST)
        }
        return false
    }

    /** Packs the closest reached tile as `x shl 16 or z`, or null when nothing near the target was reached. */
    private fun closestReached(targetX: Int, targetZ: Int): Int? {
        var best: Int? = null
        var bestDistance = Int.MAX_VALUE
        var bestCost = Int.MAX_VALUE
        for (x in targetX - ALTERNATIVE_RANGE..targetX + ALTERNATIVE_RANGE) {
            for (z in targetZ - ALTERNATIVE_RANGE..targetZ + ALTERNATIVE_RANGE) {
                if (x !in 0 until SIZE || z !in 0 until SIZE) continue
                val cost = distances[index(x, z)]
                if (cost >= MAX_ALTERNATIVE_COST) continue
                val dx = x - targetX
                val dz = z - targetZ
                val distance = dx * dx + dz * dz
                if (distance < bestDistance || (distance == bestDistance && cost < bestCost)) {
                    best = (x shl 16) or z
                    bestDistance = distance
                    bestCost = cost
                }
            }
        }
        return best
    }

    /** Walks back from the end to the start, keeping each tile where the direction changes. */
    private fun backtrack(baseX: Int, baseZ: Int, startX: Int, startZ: Int, endX: Int, endZ: Int): List<Tile> {
        val turns = ArrayList<Tile>()
        var x = endX
        var z = endZ
        turns.add(Tile(baseX + x, baseZ + z))
        var direction = directions[index(x, z)]
        var next = direction
        while (x != startX || z != startZ) {
            if (next != direction) {
                direction = next
                turns.add(Tile(baseX + x, baseZ + z))
            }
            if (next and FROM_EAST != 0) x++ else if (next and FROM_WEST != 0) x--
            if (next and FROM_NORTH != 0) z++ else if (next and FROM_SOUTH != 0) z--
            next = directions[index(x, z)]
        }
        turns.reverse()
        return if (turns.size > MAX_WAYPOINTS) turns.subList(0, MAX_WAYPOINTS).toList() else turns
    }

    private fun visit(x: Int, z: Int, came: Int, distance: Int) {
        directions[index(x, z)] = came
        distances[index(x, z)] = distance
    }

    private fun unvisited(x: Int, z: Int) = directions[index(x, z)] == 0

    private fun index(x: Int, z: Int) = x * SIZE + z

    companion object {
        const val SIZE = 128
        const val ALTERNATIVE_RANGE = 10
        const val MAX_ALTERNATIVE_COST = 100

        /** Longer routes are cut to their first 25 turns. Guessed: 25 is the client's checkpoint buffer, but which end it keeps is not checked. */
        const val MAX_WAYPOINTS = 25

        private const val UNVISITED = 99_999_999

        // Where a tile was reached from; the client's own encoding. FROM_EAST means the step went west.
        private const val FROM_NORTH = 1
        private const val FROM_EAST = 2
        private const val FROM_SOUTH = 4
        private const val FROM_WEST = 8
        private const val START = 99
    }
}
