package osrsps.game.loc

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.zone.payload.LocAddChangeV2
import net.rsprot.protocol.game.outgoing.zone.payload.LocDel
import osrsps.game.Player
import osrsps.game.zone.ZoneLoc
import osrsps.game.zone.Zones
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.LocCollision
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

class LocsTest {
    private var tick = 0L
    private val types = LocTypes(
        mapOf(
            WALL to LocType(WALL, "Wall"),
            TABLE to LocType(TABLE, "Table", collision = LocCollisionType(width = 2)),
            STUMP to LocType(STUMP, "Stump", collision = LocCollisionType(interactType = 0)),
        ),
    )
    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val static = StaticLocs()
    private val zones = Zones()
    private val locs = Locs(types, static, map, zones) { tick }
    private val watcher = Player(1, "watcher", 3215, 3215, 0)

    private val wall = ZoneLoc(WALL, LocShape.WALL_STRAIGHT, 2, 3210, 3210, 0)
    private val table = ZoneLoc(TABLE, LocShape.CENTREPIECE_STRAIGHT, 0, 3212, 3210, 0)

    init {
        // As the map loader does: the cache's loc and its collision.
        static.add(wall)
        LocCollision(map).addLoc(LocCollisionType(), wall.x, wall.z, 0, wall.shape, wall.angle)
        zones.collect(watcher)
        zones.endCycle()
    }

    @AfterTest
    fun release() = zones.endCycle()

    /** The loc payloads [watcher] gets this cycle, as "add id" / "del". */
    private fun sent(): List<String> {
        val result = zones.collect(watcher).flatMap { it.shared + it.own }.map {
            when (it) {
                is LocAddChangeV2 -> "add ${it.id}"
                is LocDel -> "del"
                else -> it.toString()
            }
        }
        zones.endCycle()
        return result
    }

    @Test
    fun `finds the cache's locs`() {
        assertEquals(wall, locs[3210, 3210, 0, 0])
        assertEquals(listOf(wall), locs.at(3210, 3210, 0))
        assertEquals(wall, locs.find(WALL, 3210, 3210, 0))
        assertNull(locs.find(TABLE, 3210, 3210, 0))
        assertTrue(locs.exists(wall))
    }

    @Test
    fun `removing a loc clears its collision and tells watchers`() {
        assertTrue(locs.remove(wall))
        assertEquals(0, map[3210, 3210, 0])
        assertEquals(0, map[3211, 3210, 0])
        assertNull(locs[3210, 3210, 0, 0])
        assertEquals(listOf("del"), sent())
        assertFalse(locs.remove(wall), "already gone")
    }

    @Test
    fun `adding a loc blocks its footprint and tells watchers`() {
        locs.add(table)
        assertEquals(CollisionFlag.LOC or CollisionFlag.LOC_PROJ, map[3212, 3210, 0])
        assertEquals(CollisionFlag.LOC or CollisionFlag.LOC_PROJ, map[3213, 3210, 0])
        assertEquals(listOf("add $TABLE"), sent())
        assertEquals(1, locs.changed)
    }

    @Test
    fun `adding a loc replaces the one on its layer, collision and all`() {
        locs.add(wall.copy(id = STUMP))
        assertEquals(0, map[3210, 3210, 0], "a walk-through loc in place of the wall")
        assertEquals(listOf(wall.copy(id = STUMP)), locs.at(3210, 3210, 0))
    }

    @Test
    fun `replace keeps the tile, shape and angle`() {
        val stump = locs.replace(wall, STUMP)
        assertEquals(wall.copy(id = STUMP), stump)
        assertNull(locs.replace(wall, STUMP), "the wall is gone now")
    }

    @Test
    fun `restoring a tile puts the cache's loc back and leaves nothing to send later`() {
        locs.remove(wall)
        sent()
        locs.restore(3210, 3210, 0, 0)
        assertEquals(CollisionFlag.WALL_EAST, map[3210, 3210, 0] and 0xFF)
        assertEquals(listOf("add $WALL"), sent())
        assertEquals(0, locs.changed)
        // A player arriving later gets the zone wiped with no loc changes in it.
        val late = Player(2, "late", 3215, 3215, 0)
        assertTrue(zones.collect(late).all { it.own.isEmpty() && it.shared.isEmpty() })
    }

    @Test
    fun `a timed change puts the tile back once its time is up`() {
        tick = 100
        locs.replace(wall, STUMP, revertAfter = 3)
        tick = 102
        locs.process()
        assertEquals(STUMP, locs[3210, 3210, 0, 0]?.id)
        tick = 103
        locs.process()
        assertEquals(wall, locs[3210, 3210, 0, 0])
        assertEquals(0, locs.changed)
    }

    @Test
    fun `a later change cancels an earlier timer on the same tile`() {
        tick = 100
        locs.remove(wall, revertAfter = 2)
        locs.add(table.copy(x = 3210, z = 3210, shape = LocShape.WALL_STRAIGHT))
        tick = 105
        locs.process()
        assertEquals(TABLE, locs[3210, 3210, 0, 0]?.id)
    }

    private companion object {
        const val WALL = 1
        const val TABLE = 2
        const val STUMP = 3
    }
}
