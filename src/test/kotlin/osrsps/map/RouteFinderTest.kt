package osrsps.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteFinderTest {
    /** One open 64x64 map square at 3200..3263, 3200..3263; everything else is off the map. */
    private fun openSquare() = CollisionMap().apply { markLoaded(3200 shr 6, 3200 shr 6) }

    private fun steps(route: Route, fromX: Int, fromZ: Int): Int {
        var x = fromX
        var z = fromZ
        var count = 0
        for (point in route.waypoints) {
            while (x != point.x || z != point.z) {
                x += Integer.signum(point.x - x)
                z += Integer.signum(point.z - z)
                count++
            }
        }
        return count
    }

    @Test
    fun `walks a straight line as one waypoint`() {
        val route = RouteFinder(openSquare()).find(0, 3210, 3210, 3215, 3210)
        assertEquals(listOf(Tile(3215, 3210)), route.waypoints)
        assertFalse(route.alternative)
    }

    @Test
    fun `walks straight first and finishes on the diagonal`() {
        val route = RouteFinder(openSquare()).find(0, 3210, 3210, 3215, 3212)
        assertEquals(Tile(3215, 3212), route.destination)
        assertEquals(5, steps(route, 3210, 3210), "Chebyshev distance")
        // The neighbour order makes the straight leg come first, as on live (inferred from the client's search order).
        assertEquals(listOf(Tile(3213, 3210), Tile(3215, 3212)), route.waypoints)
    }

    @Test
    fun `goes around a wall instead of through it`() {
        val map = openSquare()
        // A wall along the west side of 3212, z 3205..3215.
        val locs = LocCollision(map)
        for (z in 3205..3215) locs.addLoc(LocCollisionType(), 3212, z, 0, LocShape.WALL_STRAIGHT, 0)
        val route = RouteFinder(map).find(0, 3210, 3210, 3214, 3210)
        assertEquals(Tile(3214, 3210), route.destination)
        assertTrue(steps(route, 3210, 3210) > 4, "must detour around the wall")
        // Never steps from x 3211 to x 3212 inside the wall's span.
        var x = 3210
        var z = 3210
        for (point in route.waypoints) while (x != point.x || z != point.z) {
            val nx = x + Integer.signum(point.x - x)
            val nz = z + Integer.signum(point.z - z)
            assertFalse(x == 3211 && nx == 3212 && nz in 3205..3215, "crossed the wall at $x,$z")
            x = nx
            z = nz
        }
    }

    @Test
    fun `does not cut a corner past a solid loc`() {
        val map = openSquare()
        map.add(3211, 3210, 0, CollisionFlag.LOC)
        val route = RouteFinder(map).find(0, 3210, 3210, 3211, 3211)
        // North-east is blocked by the loc to the east, so the route goes north then east.
        assertEquals(listOf(Tile(3210, 3211), Tile(3211, 3211)), route.waypoints)
    }

    @Test
    fun `walks to the closest reachable tile when the target is blocked`() {
        val map = openSquare()
        // A 3x3 pond centred on 3220, 3220.
        for (x in 3219..3221) for (z in 3219..3221) map.add(x, z, 0, CollisionFlag.FLOOR)
        val route = RouteFinder(map).find(0, 3210, 3220, 3220, 3220)
        assertTrue(route.alternative)
        assertEquals(Tile(3218, 3220), route.destination)
    }

    @Test
    fun `stays put when nothing near the target is reachable`() {
        val map = openSquare()
        // Box the player in.
        for (x in 3209..3211) for (z in 3209..3211) if (x != 3210 || z != 3210) map.add(x, z, 0, CollisionFlag.LOC)
        val route = RouteFinder(map).find(0, 3210, 3210, 3240, 3240)
        assertTrue(route.waypoints.isEmpty())
    }

    @Test
    fun `does not walk off the loaded map`() {
        val route = RouteFinder(openSquare()).find(0, 3202, 3210, 3190, 3210)
        assertEquals(Tile(3200, 3210), route.destination)
        assertTrue(route.alternative)
    }
}
