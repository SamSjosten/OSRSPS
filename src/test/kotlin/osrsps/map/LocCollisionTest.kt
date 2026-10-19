package osrsps.map

import kotlin.test.Test
import kotlin.test.assertEquals

class LocCollisionTest {
    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val locs = LocCollision(map)

    @Test
    fun `a straight wall blocks both sides`() {
        locs.addLoc(LocCollisionType(), 3210, 3210, 0, LocShape.WALL_STRAIGHT, 1)
        val proj = CollisionFlag.WALL_NORTH shl CollisionFlag.PROJ_SHIFT
        assertEquals(CollisionFlag.WALL_NORTH or proj, map[3210, 3210, 0])
        assertEquals(CollisionFlag.WALL_SOUTH, map[3210, 3211, 0] and 0xFF)
    }

    @Test
    fun `a wall that lets projectiles through only blocks walking`() {
        locs.addLoc(LocCollisionType(blocksProjectile = false), 3210, 3210, 0, LocShape.WALL_L, 2)
        assertEquals(CollisionFlag.WALL_SOUTH or CollisionFlag.WALL_EAST, map[3210, 3210, 0])
        assertEquals(CollisionFlag.WALL_WEST, map[3211, 3210, 0])
        assertEquals(CollisionFlag.WALL_NORTH, map[3210, 3209, 0])
    }

    @Test
    fun `a rotated loc swaps its footprint`() {
        locs.addLoc(LocCollisionType(width = 3, length = 1, interactType = 1, blocksProjectile = false), 3210, 3210, 0, LocShape.CENTREPIECE_STRAIGHT, 1)
        for (z in 3210..3212) assertEquals(CollisionFlag.LOC, map[3210, z, 0])
        assertEquals(0, map[3211, 3210, 0])
    }

    @Test
    fun `walk-through locs and wall decorations add nothing`() {
        locs.addLoc(LocCollisionType(interactType = 0), 3210, 3210, 0, LocShape.CENTREPIECE_STRAIGHT, 0)
        locs.addLoc(LocCollisionType(), 3211, 3210, 0, 4, 0)
        assertEquals(0, map[3210, 3210, 0])
        assertEquals(0, map[3211, 3210, 0])
    }

    @Test
    fun `ground decoration blocks only when its interact type is 1`() {
        locs.addLoc(LocCollisionType(), 3210, 3210, 0, LocShape.GROUND_DECOR, 0)
        locs.addLoc(LocCollisionType(interactType = 1), 3211, 3210, 0, LocShape.GROUND_DECOR, 0)
        assertEquals(0, map[3210, 3210, 0])
        assertEquals(CollisionFlag.FLOOR_DECORATION, map[3211, 3210, 0])
    }

    @Test
    fun `tiles outside loaded map squares are blocked`() {
        assertEquals(CollisionFlag.UNLOADED, map[3100, 3210, 0])
    }
}
