package osrsps.game.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
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
    private val types = LocTypes(mapOf(WALL to LocType(WALL, "Wall"), BOX to LocType(BOX, "Box", collision = LocCollisionType(width = 2, length = 1))))
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
    fun `replacing a zone takes its walls' flags off the next zone, and puts back the next zone's walls that reach in`() {
        // A wall on the east edge of the first zone (it flags 3216's west side too), and one on the west edge of the zone
        // east of it (it flags 3215's east side): the first zone is then replaced by open ground.
        val east = ZoneLoc(WALL, LocShape.WALL_STRAIGHT, 2, 3215, 3212, 0)
        val west = ZoneLoc(WALL, LocShape.WALL_STRAIGHT, 0, 3216, 3213, 0)
        for (loc in listOf(east, west)) {
            static.add(loc)
            LocCollision(map).addLoc(LocCollisionType(), loc.x, loc.z, 0, loc.shape, loc.angle)
        }
        val instance = create()
        val dx = instance.baseX - 3208
        val dz = instance.baseZ - 3208
        assertTrue(map[3216 + dx, 3212 + dz, 0] and CollisionFlag.WALL_WEST != 0, "the copied wall's other side")
        assertTrue(map[3215 + dx, 3213 + dz, 0] and CollisionFlag.WALL_EAST != 0, "the next zone's wall's other side")
        instances.replace(instance, InstancePart(3224, 3224, 0, 1, 1, 0, 0, 0))
        assertEquals(0, map[3216 + dx, 3212 + dz, 0] and CollisionFlag.WALL_WEST, "the gone wall's other side is open")
        assertTrue(map[3215 + dx, 3213 + dz, 0] and CollisionFlag.WALL_EAST != 0, "the next zone's wall still reaches in")
        assertEquals(listOf(west.copy(x = 3216 + dx, z = 3213 + dz)), static.at(3216 + dx, 3213 + dz, 0))
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

    @Test
    fun `a zone turned a quarter clockwise turns its terrain, its walls and its locs, and the client is told so`() {
        // A 2 by 1 box at 3209, 3214 (1, 6 in the zone), whose footprint stands upright once turned.
        val box = ZoneLoc(BOX, LocShape.CENTREPIECE_STRAIGHT, 0, 3209, 3214, 0)
        static.add(box)
        LocCollision(map).addLoc(LocCollisionType(width = 2, length = 1), box.x, box.z, 0, box.shape, box.angle)
        val instance = assertNotNull(instances.create(1, 1, listOf(InstancePart(3208, 3208, 0, 1, 1, 0, 0, 0, rotation = 1)), 3222, 3218, 0))
        val x = instance.baseX
        val z = instance.baseZ
        // Tile 2, 2's west wall is 2, 5's north wall; the water at 4, 3 is at 3, 3.
        assertEquals(listOf(wall.copy(x = x + 2, z = z + 5, angle = 1)), static.at(x + 2, z + 5, 0))
        assertTrue(map[x + 2, z + 5, 0] and CollisionFlag.WALL_NORTH != 0, "the wall turned north")
        assertEquals(CollisionFlag.FLOOR, map[x + 3, z + 3, 0], "the water")
        assertEquals(0, map[x + 4, z + 3, 0], "open ground where the water was")
        // The box's south-west tile 1, 6 goes to 6, 5 (its width moving it), turned once: 1 by 2 there.
        assertEquals(listOf(box.copy(x = x + 6, z = z + 5, angle = 1)), static.at(x + 6, z + 5, 0))
        assertTrue(map[x + 6, z + 5, 0] and CollisionFlag.LOC != 0 && map[x + 6, z + 6, 0] and CollisionFlag.LOC != 0, "its footprint")
        assertEquals(0, map[x + 7, z + 5, 0] and CollisionFlag.LOC, "nothing beside it")
        assertEquals(1, instances.referenceZone(x shr 3, z shr 3, 0)?.rotation)
    }

    @Test
    fun `the turn sums put a footprint's south-west tile where the client draws it, each way round`() {
        // A 3 by 2 footprint at 1, 2: clockwise once to 2, 4, twice to 4, 4, three times to 4, 1; and back after four.
        assertEquals(1 to 2, Instances.rotateX(1, 2, 0, 3, 2) to Instances.rotateZ(1, 2, 0, 3, 2))
        assertEquals(2 to 4, Instances.rotateX(1, 2, 1, 3, 2) to Instances.rotateZ(1, 2, 1, 3, 2))
        assertEquals(4 to 4, Instances.rotateX(1, 2, 2, 3, 2) to Instances.rotateZ(1, 2, 2, 3, 2))
        assertEquals(4 to 1, Instances.rotateX(1, 2, 3, 3, 2) to Instances.rotateZ(1, 2, 3, 3, 2))
        assertFails { InstancePart(3208, 3208, 0, 2, 1, 0, 0, 0, rotation = 1) }
    }

    private companion object {
        const val WALL = 1
        const val BOX = 2
    }

    @Test
    fun `a part takes zones from one level onto another, and the client draws them from the level they came from`() {
        map.add(3212, 3212, 1, CollisionFlag.FLOOR)
        val upstairs = ZoneLoc(WALL, LocShape.WALL_STRAIGHT, 1, 3211, 3213, 1)
        static.add(upstairs)
        val instance = assertNotNull(instances.create(2, 2, listOf(InstancePart(3208, 3208, 1, 2, 2, 0, 0, 0)), 3222, 3218, 0))
        val dx = instance.baseX - 3208
        val dz = instance.baseZ - 3208
        assertEquals(CollisionFlag.FLOOR, map[3212 + dx, 3212 + dz, 0], "level 1's water, on level 0")
        assertEquals(0, map[3212 + dx, 3211 + dz, 0], "not level 0's")
        assertEquals(listOf(upstairs.copy(x = 3211 + dx, z = 3213 + dz, level = 0)), static.at(3211 + dx, 3213 + dz, 0))
        assertTrue(static.at(3210 + dx, 3210 + dz, 0).isEmpty(), "level 0's wall isn't copied")
        assertEquals(4, instance.copiedZones)
        val drawn = assertNotNull(instances.referenceZone(instance.baseX shr 3, instance.baseZ shr 3, 0))
        assertEquals(Triple(3208 shr 3, 3208 shr 3, 1), Triple(drawn.zoneX, drawn.zoneZ, drawn.level))
        assertNull(instances.referenceZone(instance.baseX shr 3, instance.baseZ shr 3, 1), "nothing put on level 1")
    }

    @Test
    fun `a part laid over an instance's zones replaces their collision and locs`() {
        map.add(3212, 3212, 1, CollisionFlag.FLOOR)
        val upstairs = ZoneLoc(WALL, LocShape.WALL_STRAIGHT, 1, 3211, 3213, 1)
        static.add(upstairs)
        val instance = create()
        val dx = instance.baseX - 3208
        val dz = instance.baseZ - 3208
        instances.replace(instance, InstancePart(3208, 3208, 1, 2, 2, 0, 0, 0))
        assertEquals(CollisionFlag.FLOOR, map[3212 + dx, 3212 + dz, 0], "level 1's water")
        assertEquals(0, map[3212 + dx, 3211 + dz, 0], "level 0's water gone")
        assertEquals(0, map[3210 + dx, 3210 + dz, 0] and CollisionFlag.WALL_WEST, "level 0's wall gone")
        assertTrue(static.at(3210 + dx, 3210 + dz, 0).isEmpty())
        assertEquals(listOf(upstairs.copy(x = 3211 + dx, z = 3213 + dz, level = 0)), static.at(3211 + dx, 3213 + dz, 0))
        assertEquals(16, instance.copiedZones, "the same zones, level 0's drawn from level 1 now")
        assertEquals(1, instances.referenceZone(instance.baseX shr 3, instance.baseZ shr 3, 0)?.level)
        // And back.
        instances.replace(instance, InstancePart(3208, 3208, 0, 2, 2, 0, 0, 0))
        assertEquals(CollisionFlag.FLOOR, map[3212 + dx, 3211 + dz, 0])
        assertTrue(map[3210 + dx, 3210 + dz, 0] and CollisionFlag.WALL_WEST != 0)
        instances.destroy(instance)
        assertEquals(CollisionFlag.UNLOADED, map[3212 + dx, 3212 + dz, 0])
    }
}
