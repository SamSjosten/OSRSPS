package osrsps.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReachTest {
    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val walls = LocCollision(map)

    private fun reached(x: Int, z: Int, target: RouteTarget) = Reach.reached(map, 0, x, z, target)

    /** The tiles around (and on) [target]'s tile that reach it, as offsets from it. */
    private fun reachedFrom(target: RouteTarget): Set<Pair<Int, Int>> {
        val found = HashSet<Pair<Int, Int>>()
        for (dx in -2..3) for (dz in -2..3) if (reached(target.x + dx, target.z + dz, target)) found += dx to dz
        return found
    }

    @Test
    fun `a tile is reached only by standing on it`() {
        assertEquals(setOf(0 to 0), reachedFrom(RouteTarget.tile(3210, 3210)))
    }

    @Test
    fun `a door on the east edge is reached from both sides and from beside it`() {
        // A closed door, angle 2: the wall is on the east side of 3210, 3210.
        walls.addLoc(LocCollisionType(), 3210, 3210, 0, LocShape.WALL_STRAIGHT, 2)
        val door = RouteTarget.loc(3210, 3210, LocShape.WALL_STRAIGHT, 2, 1, 1, 0)
        assertEquals(setOf(0 to 0, 1 to 0, 0 to 1, 0 to -1), reachedFrom(door))
    }

    @Test
    fun `a wall between the mover and the door's tile blocks reaching it from the side`() {
        walls.addLoc(LocCollisionType(), 3210, 3210, 0, LocShape.WALL_STRAIGHT, 2)
        // A wall along the north edge of the door's tile: from the north the door can't be reached any more.
        walls.addLoc(LocCollisionType(), 3210, 3210, 0, LocShape.WALL_STRAIGHT, 1)
        val door = RouteTarget.loc(3210, 3210, LocShape.WALL_STRAIGHT, 2, 1, 1, 0)
        assertFalse(reached(3210, 3211, door))
        assertTrue(reached(3210, 3209, door))
    }

    @Test
    fun `an L-shaped wall is reached across both its sides`() {
        val wall = RouteTarget.loc(3210, 3210, LocShape.WALL_L, 0, 1, 1, 0)
        assertEquals(setOf(0 to 0, -1 to 0, 0 to 1, 1 to 0, 0 to -1), reachedFrom(wall))
    }

    @Test
    fun `a ground loc is reached from any tile along its sides but not its corners`() {
        // A 2x3 table, unturned.
        val table = RouteTarget.loc(3210, 3210, LocShape.CENTREPIECE_STRAIGHT, 0, 2, 3, 0)
        val reached = reachedFrom(table)
        assertTrue(-1 to 0 in reached && -1 to 2 in reached, "west side")
        assertTrue(2 to 1 in reached, "east side")
        assertTrue(0 to -1 in reached && 1 to -1 in reached, "south side")
        assertTrue(1 to 3 in reached, "north side")
        assertFalse(-1 to -1 in reached, "corner")
        assertFalse(3 to 1 in reached, "two tiles away")
        assertTrue(1 to 2 in reached, "inside")
    }

    @Test
    fun `a quarter turn swaps a ground loc's footprint and turns its blocked sides`() {
        // 1 wide, 2 long, usable only from the south before turning (north, east and west blocked: 1 + 2 + 8).
        val loc = RouteTarget.loc(3210, 3210, LocShape.CENTREPIECE_STRAIGHT, 1, 1, 2, 11)
        assertEquals(2, loc.width)
        assertEquals(1, loc.length)
        // Turned a quarter, south becomes west.
        assertEquals(setOf(0 to 0, 1 to 0, -1 to 0), reachedFrom(loc))
    }

    @Test
    fun `turning keeps the client's quirk of letting high access bits fall back in`() {
        // 29 is 16 + 8 + 4 + 1. Turned once, the client gets (29 shl 1 and 15) + (29 shr 3) = 10 + 3 = 13.
        assertEquals(13, RouteTarget.loc(0, 0, LocShape.CENTREPIECE_STRAIGHT, 1, 1, 1, 29).accessBlock)
    }

    @Test
    fun `a wall beside a ground loc stops it being reached from that side`() {
        val loc = RouteTarget.loc(3210, 3210, LocShape.CENTREPIECE_STRAIGHT, 0, 1, 1, 0)
        // A wall on the east edge of the tile west of the loc.
        walls.addLoc(LocCollisionType(), 3209, 3210, 0, LocShape.WALL_STRAIGHT, 2)
        assertFalse(reached(3209, 3210, loc))
        assertTrue(reached(3211, 3210, loc))
    }

    @Test
    fun `wall decorations are reached from their tile, and diagonal ones from their open sides`() {
        assertEquals(setOf(0 to 0), reachedFrom(RouteTarget.loc(3210, 3210, 4, 0, 1, 1, 0)))
        assertEquals(setOf(0 to 0, 1 to 0, 0 to -1), reachedFrom(RouteTarget.loc(3210, 3210, 6, 0, 1, 1, 0)))
        // Shape 7 is shape 6 turned half round.
        assertEquals(setOf(0 to 0, -1 to 0, 0 to 1), reachedFrom(RouteTarget.loc(3210, 3210, 7, 0, 1, 1, 0)))
        assertEquals(setOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1), reachedFrom(RouteTarget.loc(3210, 3210, 8, 0, 1, 1, 0)))
    }
}
