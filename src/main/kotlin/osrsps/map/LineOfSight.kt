package osrsps.map

import osrsps.map.CollisionFlag.LOC_PROJ
import osrsps.map.CollisionFlag.PROJ_SHIFT
import osrsps.map.CollisionFlag.WALL_EAST
import osrsps.map.CollisionFlag.WALL_NORTH
import osrsps.map.CollisionFlag.WALL_SOUTH
import osrsps.map.CollisionFlag.WALL_WEST
import kotlin.math.abs

/**
 * Line of sight and attack distance between two entities, for ranged and magic attacks.
 *
 * OSRS Wiki, Line of sight: a ranged or magic attack needs the target in line of sight and in range, and for a target
 * bigger than one tile it is the target's closest tile that counts. Distance is Chebyshev and edge to edge (Wiki, Attack
 * range and Hit delay). The line itself is traced as the client traces it: a step at a time along the longer axis, the
 * other axis kept in 16.16 fixed point from the middle of the start tile, and a tile blocks when it has a projectile
 * wall on the side the line comes in by or a projectile-blocking loc (RuneLite's WorldArea.hasLineOfSightTo, which
 * mirrors the client). Inferred, not confirmed against live: that the server traces the same line, and that for a
 * source bigger than one tile (an NPC shooting) its closest tile to the target is where the line starts. Walls and locs
 * that block walking but not projectiles (a bank booth, a fence of some kinds) don't block it; water and other floor
 * that can't be walked on doesn't either.
 */
object LineOfSight {
    /**
     * Whether a [srcSize] entity at [srcX], [srcZ] can see a [destWidth] by [destLength] one at [destX], [destZ] on
     * [level]: false when they overlap, as nothing attacks from under its target.
     */
    fun clear(
        collision: CollisionMap,
        level: Int,
        srcX: Int,
        srcZ: Int,
        srcSize: Int,
        destX: Int,
        destZ: Int,
        destWidth: Int = 1,
        destLength: Int = 1,
    ): Boolean {
        if (overlaps(srcX, srcZ, srcSize, srcSize, destX, destZ, destWidth, destLength)) return false
        val fromX = closest(srcX, srcSize, destX)
        val fromZ = closest(srcZ, srcSize, destZ)
        val toX = closest(destX, destWidth, srcX)
        val toZ = closest(destZ, destLength, srcZ)
        return trace(collision, level, fromX, fromZ, toX, toZ)
    }

    /**
     * The Chebyshev distance between the edges of two entities: 1 when they touch (diagonally too), 0 when they overlap.
     * Attack range is measured this way (Wiki, Attack range).
     */
    fun distance(srcX: Int, srcZ: Int, srcSize: Int, destX: Int, destZ: Int, destWidth: Int = 1, destLength: Int = 1): Int {
        val dx = gap(srcX, srcSize, destX, destWidth)
        val dz = gap(srcZ, srcSize, destZ, destLength)
        return maxOf(dx, dz)
    }

    /** Whether the source is within [range] tiles of the target, edge to edge, and can see it. */
    fun inRange(
        collision: CollisionMap,
        level: Int,
        srcX: Int,
        srcZ: Int,
        srcSize: Int,
        destX: Int,
        destZ: Int,
        destWidth: Int,
        destLength: Int,
        range: Int,
    ): Boolean {
        val distance = distance(srcX, srcZ, srcSize, destX, destZ, destWidth, destLength)
        return distance in 1..range && clear(collision, level, srcX, srcZ, srcSize, destX, destZ, destWidth, destLength)
    }

    /** The line from one tile to another, the client's way; see the class note. Each tile entered is checked, not the start. */
    fun trace(collision: CollisionMap, level: Int, fromX: Int, fromZ: Int, toX: Int, toZ: Int): Boolean {
        if (fromX == toX && fromZ == toZ) return true
        val dx = toX - fromX
        val dz = toZ - fromZ
        // Coming in from the west the tile's west side must be open, and so on.
        val xBlock = LOC_PROJ or ((if (dx < 0) WALL_EAST else WALL_WEST) shl PROJ_SHIFT)
        val zBlock = LOC_PROJ or ((if (dz < 0) WALL_NORTH else WALL_SOUTH) shl PROJ_SHIFT)
        if (abs(dx) > abs(dz)) {
            val step = if (dx < 0) -1 else 1
            val slope = (dz shl 16) / abs(dx)
            // Half a tile in, and one under for a line going south so it rounds the way the client's does.
            var fixedZ = (fromZ shl 16) + HALF_TILE - (if (dz < 0) 1 else 0)
            var x = fromX
            while (x != toX) {
                x += step
                val z = fixedZ ushr 16
                if (collision[x, z, level] and xBlock != 0) return false
                fixedZ += slope
                val nextZ = fixedZ ushr 16
                if (nextZ != z && collision[x, nextZ, level] and zBlock != 0) return false
            }
        } else {
            val step = if (dz < 0) -1 else 1
            val slope = (dx shl 16) / abs(dz)
            var fixedX = (fromX shl 16) + HALF_TILE - (if (dx < 0) 1 else 0)
            var z = fromZ
            while (z != toZ) {
                z += step
                val x = fixedX ushr 16
                if (collision[x, z, level] and zBlock != 0) return false
                fixedX += slope
                val nextX = fixedX ushr 16
                if (nextX != x && collision[nextX, z, level] and xBlock != 0) return false
            }
        }
        return true
    }

    /** On one axis, the tile of the span starting at [start] ([size] long) closest to a span starting at [other]. */
    private fun closest(start: Int, size: Int, other: Int): Int = other.coerceIn(start, start + size - 1)

    /** On one axis, tiles from the end of one span to the start of the other: 0 when they overlap, 1 when they touch. */
    private fun gap(start: Int, size: Int, other: Int, otherSize: Int): Int = when {
        other > start + size - 1 -> other - (start + size - 1)
        start > other + otherSize - 1 -> start - (other + otherSize - 1)
        else -> 0
    }

    private fun overlaps(ax: Int, az: Int, aw: Int, al: Int, bx: Int, bz: Int, bw: Int, bl: Int): Boolean =
        ax < bx + bw && bx < ax + aw && az < bz + bl && bz < az + al

    private const val HALF_TILE = 0x8000
}
