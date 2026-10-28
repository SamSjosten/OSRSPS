package osrsps.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LineOfSightTest {
    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val locs = LocCollision(map)

    private fun clear(fromX: Int, fromZ: Int, toX: Int, toZ: Int, srcSize: Int = 1, destSize: Int = 1) =
        LineOfSight.clear(map, 0, fromX, fromZ, srcSize, toX, toZ, destSize, destSize)

    @Test
    fun `open ground is clear both ways and at every angle`() {
        for (dx in -7..7) for (dz in -7..7) {
            if (dx == 0 && dz == 0) continue
            assertTrue(clear(3210, 3210, 3210 + dx, 3210 + dz), "to $dx, $dz")
            assertTrue(clear(3210 + dx, 3210 + dz, 3210, 3210), "from $dx, $dz")
        }
    }

    @Test
    fun `a solid loc that blocks projectiles blocks the line, one that doesn't leaves it clear`() {
        // A 1x1 loc two tiles east, in the way of a straight line east.
        locs.addLoc(LocCollisionType(blocksProjectile = true), 3212, 3210, 0, LocShape.CENTREPIECE_STRAIGHT, 0)
        assertFalse(clear(3210, 3210, 3215, 3210))
        assertFalse(clear(3215, 3210, 3210, 3210))
        // A line north of it passes.
        assertTrue(clear(3210, 3211, 3215, 3211))

        val other = CollisionMap().apply { markLoaded(50, 50) }
        LocCollision(other).addLoc(LocCollisionType(blocksProjectile = false), 3212, 3210, 0, LocShape.CENTREPIECE_STRAIGHT, 0)
        assertTrue(LineOfSight.clear(other, 0, 3210, 3210, 1, 3215, 3210))
    }

    @Test
    fun `a wall blocks the line coming through it from either side`() {
        // A wall on the west side of 3212, 3210 (angle 0 is west for a straight wall).
        locs.addLoc(LocCollisionType(), 3212, 3210, 0, LocShape.WALL_STRAIGHT, 0)
        assertFalse(clear(3210, 3210, 3214, 3210))
        assertFalse(clear(3214, 3210, 3210, 3210))
        // Running alongside it, north to south past the wall's tile, is clear.
        assertTrue(clear(3212, 3214, 3212, 3206))
    }

    @Test
    fun `a diagonal line is blocked by a loc on its path, not by one beside it`() {
        // From 3210, 3210 to 3214, 3212 the line steps east each tile and moves north at about the halfway points.
        // Blocking every tile it could use in column 3212 stops it; blocking a tile off the line doesn't.
        locs.addLoc(LocCollisionType(blocksProjectile = true), 3212, 3215, 0, LocShape.CENTREPIECE_STRAIGHT, 0)
        assertTrue(clear(3210, 3210, 3214, 3212))
        locs.addLoc(LocCollisionType(blocksProjectile = true), 3212, 3211, 0, LocShape.CENTREPIECE_STRAIGHT, 0)
        assertFalse(clear(3210, 3210, 3214, 3212))
    }

    @Test
    fun `the start tile itself isn't checked, the end tile is`() {
        locs.addLoc(LocCollisionType(blocksProjectile = true), 3210, 3210, 0, LocShape.CENTREPIECE_STRAIGHT, 0)
        assertTrue(LineOfSight.trace(map, 0, 3210, 3210, 3214, 3210))
        assertFalse(LineOfSight.trace(map, 0, 3214, 3210, 3210, 3210))
    }

    @Test
    fun `a big target is seen through its closest tile`() {
        // A 3x3 NPC at 3215..3217, 3210..3212, with a loc in front of its south-west corner only.
        locs.addLoc(LocCollisionType(blocksProjectile = true), 3214, 3210, 0, LocShape.CENTREPIECE_STRAIGHT, 0)
        // Straight west of the corner: the line to the closest tile goes through the loc.
        assertFalse(clear(3210, 3210, 3215, 3210, destSize = 3))
        // West of its middle row the closest tile is 3215, 3211, and that line is open.
        assertTrue(clear(3210, 3211, 3215, 3210, destSize = 3))
    }

    @Test
    fun `nothing sees what it stands under`() {
        assertFalse(clear(3216, 3211, 3215, 3210, destSize = 3))
        assertFalse(clear(3210, 3210, 3210, 3210))
    }

    @Test
    fun `distance is Chebyshev and edge to edge`() {
        assertEquals(1, LineOfSight.distance(3210, 3210, 1, 3211, 3211))
        assertEquals(7, LineOfSight.distance(3210, 3210, 1, 3217, 3204))
        // A 3x3 at 3215..3217: from 3210 its west edge is 5 away, from 3220 its east edge 3.
        assertEquals(5, LineOfSight.distance(3210, 3211, 1, 3215, 3210, 3, 3))
        assertEquals(3, LineOfSight.distance(3220, 3211, 1, 3215, 3210, 3, 3))
        assertEquals(0, LineOfSight.distance(3216, 3211, 1, 3215, 3210, 3, 3))
        // A 2x2 source to a 1x1 target beside its north-east corner.
        assertEquals(1, LineOfSight.distance(3210, 3210, 2, 3212, 3212))
    }

    @Test
    fun `in range needs both the distance and the line`() {
        assertTrue(LineOfSight.inRange(map, 0, 3210, 3210, 1, 3217, 3210, 1, 1, 7))
        assertFalse(LineOfSight.inRange(map, 0, 3210, 3210, 1, 3218, 3210, 1, 1, 7))
        locs.addLoc(LocCollisionType(blocksProjectile = true), 3213, 3210, 0, LocShape.CENTREPIECE_STRAIGHT, 0)
        assertFalse(LineOfSight.inRange(map, 0, 3210, 3210, 1, 3217, 3210, 1, 1, 7))
    }
}
