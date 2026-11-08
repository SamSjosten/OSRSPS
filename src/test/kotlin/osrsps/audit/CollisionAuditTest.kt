package osrsps.audit

import osrsps.game.loc.Doors
import osrsps.game.loc.Gates
import osrsps.game.loc.Locs
import osrsps.game.zone.Zones
import osrsps.map.LocShape
import osrsps.map.Steps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the world's collision as the server builds it from the real 241 cache, when given a checkout of
 * github.com/abextm/osrs-cache at a rev 241 tag (2.flatcache, 5.flatcache and 24.flatcache) in OSRSPS_FLATCACHE. Not in
 * CI, which has no cache: run it in the cloud after a change to map loading, loc collision or doors.
 */
class CollisionAuditTest {
    @Test
    fun `every loc type and map square loads`() {
        val world = RealMap.load()
        assertEquals(0, world.failedTypes, "loc types that didn't decode add no collision")
        assertTrue(world.squares.size > 2900, "map squares found: ${world.squares.size}")
        // Group 25287 is a short non-terrain group the loader skips on purpose.
        val withTerrain = world.squares.count { (it.files.terrain()?.size ?: 0) >= 4 * 64 * 64 * 2 }
        assertEquals(withTerrain, world.loadedSquares, "every square with terrain loads")
    }

    /**
     * Each wall a town's map files place blocks a step across its edge, both ways: the loader, the wall shapes and the
     * step rules agree on real data. Lumbridge, Draynor, Varrock, Edgeville and Al Kharid.
     */
    @Test
    fun `walls in the towns block a step across them`() {
        val world = RealMap.load().map
        val towns = mapOf(
            "Lumbridge" to (3200..3240 to 3200..3240),
            "Draynor" to (3080..3110 to 3240..3280),
            "Varrock" to (3170..3270 to 3380..3500),
            "Edgeville" to (3080..3110 to 3480..3510),
            "Al Kharid" to (3265..3320 to 3150..3200),
        )
        for ((town, area) in towns) {
            var checked = 0
            for (x in area.first) for (z in area.second) for (level in 0..2) {
                for (wall in world.locs.at(x, z, level)) {
                    if (wall.shape != LocShape.WALL_STRAIGHT) continue
                    if (world.locTypes[wall.id]?.collision?.interactType == 0) continue
                    val (dx, dz) = OUTWARD[wall.angle]
                    assertTrue(!Steps.canStep(world.collision, level, x, z, dx, dz), "$town: step out through ${wall.id} at $x, $z, $level")
                    assertTrue(!Steps.canStep(world.collision, level, x + dx, z + dz, -dx, -dz), "$town: step in through ${wall.id} at $x, $z, $level")
                    checked++
                }
            }
            assertTrue(checked > 50, "$town: only $checked walls checked")
        }
    }

    /** Opening and closing every door and gate the server pairs puts the collision back exactly as it was. */
    @Test
    fun `doors and gates leave no holes once closed again`() {
        val loaded = RealMap.load()
        val world = loaded.map
        val map = world.collision
        val locs = Locs(world.locTypes, world.locs, map, Zones()) { 0L }
        val doors = Doors(locs)
        val gates = Gates(locs)
        var toggled = 0
        val holes = ArrayList<String>()
        for (square in loaded.squares) {
            val baseX = square.x shl 6
            val baseZ = square.z shl 6
            for (level in 0..3) for (x in baseX until baseX + 64) for (z in baseZ until baseZ + 64) {
                val door = world.locs.at(x, z, level, 0) ?: continue
                if (door.shape != LocShape.WALL_STRAIGHT || !locs.exists(door)) continue
                val open = world.locTypes[door.id]?.op(Doors.OPEN) ?: 0
                if (open == 0) continue
                val before = IntArray(AREA * AREA) { map[x - 3 + it % AREA, z - 3 + it / AREA, level] }
                val opened = doors.toggle(door, open) ?: gates.toggle(door, open) ?: continue
                toggled++
                val close = world.locTypes[opened.id]?.op(Doors.CLOSE) ?: 0
                doors.toggle(opened, close) ?: gates.toggle(opened, close)
                val after = IntArray(AREA * AREA) { map[x - 3 + it % AREA, z - 3 + it / AREA, level] }
                if (!before.contentEquals(after)) holes += "${door.id} at $x, $z, $level"
                for (i in 0 until AREA * AREA) locs.restore(x - 3 + i % AREA, z - 3 + i / AREA, level, 0)
            }
        }
        assertTrue(toggled > 1000, "doors toggled: $toggled")
        assertEquals(emptyList(), holes)
    }

    private companion object {
        const val AREA = 7

        /** By a straight wall's angle (0 west edge to 3 south): the step that crosses it from its own tile. */
        val OUTWARD = listOf(-1 to 0, 0 to 1, 1 to 0, 0 to -1)
    }
}
