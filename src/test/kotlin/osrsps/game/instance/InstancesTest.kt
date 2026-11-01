package osrsps.game.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.LocCollision
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/** Instances: copying zones of the map, collision and locs, into a block of the dynamic area, and ending them. */
class InstancesTest {
    private val types = LocTypes(mapOf(WALL to LocType(WALL, "Wall")))
    private val map = CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }
    private val static = StaticLocs()
    private val instances = Instances(map, static, types)

    // A wall on the west edge of 3210, 3210 and a tile of water (floor flag) on 3212, 3211, as the map loader leaves them.
    private val wall = ZoneLoc(WALL, LocShape.WALL_STRAIGHT, 0, 3210, 3210, 0)

    init {
        static.add(wall)
        LocCollision(map).addLoc(LocCollisionType(), wall.x, wall.z, 0, wall.shape, wall.angle)
        map.add(3212, 3211, 0, CollisionFlag.FLOOR)
    }

    /** Copies the 2 x 2 zones from 3208, 3208. */
    private fun create(): Instance = assertNotNull(instances.create(3208, 3208, 2, 2, 3222, 3218, 0))

    @Test
    fun `an instance copies its zones' collision and cache locs into the dynamic area, laid out as they are`() {
        val instance = create()
        assertTrue(instance.baseX >= Instances.FIRST_SQUARE_X shl 6, "out past the cache's maps")
        assertSame(instance, instances.at(instance.baseX, instance.baseZ))
        val dx = instance.baseX - 3208
        val dz = instance.baseZ - 3208
        assertEquals(map[3210, 3210, 0], map[3210 + dx, 3210 + dz, 0], "the wall's flags")
        assertTrue(map[3210 + dx, 3210 + dz, 0] and CollisionFlag.WALL_WEST != 0)
        assertEquals(CollisionFlag.FLOOR, map[3212 + dx, 3211 + dz, 0], "the water")
        assertEquals(0, map[3214 + dx, 3214 + dz, 0], "open ground")
        assertEquals(listOf(wall.copy(x = 3210 + dx, z = 3210 + dz)), static.at(3210 + dx, 3210 + dz, 0))
        // 2 x 2 zones on each of the 4 levels.
        assertEquals(16, instance.copiedZones)
    }

    @Test
    fun `a wall on a zone's east edge keeps its other side, in the next zone, in the copy`() {
        // On the east edge of 3215, 3212: the last tile of the zone from 3208; its other side is 3216's west edge.
        val east = ZoneLoc(WALL, LocShape.WALL_STRAIGHT, 2, 3215, 3212, 0)
        static.add(east)
        LocCollision(map).addLoc(LocCollisionType(), east.x, east.z, 0, east.shape, east.angle)
        assertTrue(map[3216, 3212, 0] and CollisionFlag.WALL_WEST != 0, "as the map loader leaves it")
        val instance = create()
        val dx = instance.baseX - 3208
        val dz = instance.baseZ - 3208
        assertEquals(map[3215, 3212, 0], map[3215 + dx, 3212 + dz, 0])
        assertEquals(map[3216, 3212, 0], map[3216 + dx, 3212 + dz, 0], "its other side")
    }

    @Test
    fun `the rest of its block, and the margin round it, are off the map`() {
        val instance = create()
        assertEquals(CollisionFlag.UNLOADED, map[instance.baseX + 16, instance.baseZ, 0], "the zone east of the copy")
        assertEquals(CollisionFlag.UNLOADED, map[instance.baseX - 1, instance.baseZ, 0], "west of the block")
        assertSame(instance, instances.at(instance.baseX + 16, instance.baseZ), "still its block, with nothing copied there")
    }

    @Test
    fun `the client is told to draw each copied zone as its original, and nothing elsewhere`() {
        val instance = create()
        val zone = instances.referenceZone((instance.baseX shr 3) + 1, instance.baseZ shr 3, 2)
        assertNotNull(zone)
        assertEquals(3208 / 8 + 1, zone.zoneX)
        assertEquals(3208 / 8, zone.zoneZ)
        assertEquals(2, zone.level)
        assertEquals(0, zone.rotation)
        assertNull(instances.referenceZone((instance.baseX shr 3) + 2, instance.baseZ shr 3, 0), "east of the copy")
        assertNull(instances.referenceZone(3208 shr 3, 3208 shr 3, 0), "out in the world")
    }

    @Test
    fun `two instances are at least a map square apart, so nobody in one sees into the other`() {
        val first = create()
        val second = create()
        assertFalse(first.contains(second.baseX, second.baseZ))
        val gapX = maxOf(second.baseX - first.maxX, first.baseX - second.maxX)
        val gapZ = maxOf(second.baseZ - first.maxZ, first.baseZ - second.maxZ)
        assertTrue(maxOf(gapX, gapZ) > 64, "gap $gapX, $gapZ")
        assertEquals(listOf(first, second), instances.all.toList())
    }

    @Test
    fun `an ended instance takes its zones, collision and locs with it, and its block is used again`() {
        val instance = create()
        val dx = instance.baseX - 3208
        val dz = instance.baseZ - 3208
        instances.destroy(instance)
        assertNull(instances.at(instance.baseX, instance.baseZ))
        assertEquals(CollisionFlag.UNLOADED, map[3210 + dx, 3210 + dz, 0])
        assertEquals(0, map.stored(3210 + dx, 3210 + dz, 0), "no flags kept")
        assertEquals(emptyList(), static.at(3210 + dx, 3210 + dz, 0))
        assertNull(instances.referenceZone(instance.baseX shr 3, instance.baseZ shr 3, 0))
        // The original is untouched.
        assertEquals(listOf(wall), static.at(3210, 3210, 0))
        assertEquals(CollisionFlag.FLOOR, map[3212, 3211, 0])
        val next = create()
        assertEquals(instance.baseX, next.baseX)
        assertEquals(instance.baseZ, next.baseZ)
    }

    @Test
    fun `zones off the cache's maps aren't copied`() {
        // 3192 is in map square 49, which this map doesn't have; 3200 is in 50, which it does.
        val instance = assertNotNull(instances.create(3192, 3208, 2, 1, 3222, 3218, 0))
        assertEquals(CollisionFlag.UNLOADED, map[instance.baseX, instance.baseZ, 0])
        assertEquals(0, map[instance.baseX + 8, instance.baseZ, 0])
        assertEquals(4, instance.copiedZones)
    }

    @Test
    fun `there is room for many instances, and then none`() {
        val made = generateSequence { instances.create(3208, 3208, 64, 64, 3222, 3218, 0) }.take(10_000).count()
        // 8 x 8 square blocks a square apart in the 100 x 200 square dynamic area.
        assertEquals(11 * 22, made)
        assertNull(instances.create(3208, 3208, 1, 1, 3222, 3218, 0))
    }

    private companion object {
        const val WALL = 1
    }
}
