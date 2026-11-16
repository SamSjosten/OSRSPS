package osrsps.audit

import osrsps.game.content.ResourceArea
import osrsps.game.skill.ThievingLocks
import osrsps.map.Steps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the Resource Area's box ([ResourceArea.inside]), its dark crab spots and runite golems, and the Pirates' Hideout
 * doors' sides against the real map (see [RealMap]; skipped without it, as in CI), with every door and gate shut, as
 * their locs stand in the cache. Exploit review round 38 (R38A-2, R38A-3, R38A-5).
 */
class ResourceAreaAuditTest {
    /** Every tile reachable on foot from [from] inside [xs] by [zs], on the surface. */
    private fun walk(from: Pair<Int, Int>, xs: IntRange, zs: IntRange): Set<Pair<Int, Int>> {
        val collision = RealMap.load().map.collision
        val seen = hashSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val (x, z) = queue.removeFirst()
            for (dx in -1..1) for (dz in -1..1) {
                val next = (x + dx) to (z + dz)
                if ((dx == 0 && dz == 0) || next.first !in xs || next.second !in zs || next in seen) continue
                if (Steps.canStep(collision, 0, x, z, dx, dz)) {
                    seen += next
                    queue += next
                }
            }
        }
        return seen
    }

    private val xs = 3150..3220
    private val zs = 3900..3975

    @Test
    fun `the area walled in by its gate is all inside the box, and nothing outside its walls is`() {
        val inArea = walk(PILES, xs, zs)
        println("Resource Area: ${inArea.size} tiles walked to from Piles, x ${inArea.minOf { it.first }}..${inArea.maxOf { it.first }}, z ${inArea.minOf { it.second }}..${inArea.maxOf { it.second }}")
        assertTrue(inArea.size in 50..600, "the area is walled in: ${inArea.size} tiles")
        assertEquals(emptyList<Pair<Int, Int>>(), inArea.filterNot { (x, z) -> ResourceArea.inside(x, z, 0) }.sortedWith(compareBy({ it.first }, { it.second })), "walked to inside, but outside the box")
        val outside = walk(OUTSIDE, xs, zs)
        assertTrue(outside.size > inArea.size, "outside is the open Wilderness: ${outside.size} tiles")
        val boxedOutside = outside.filter { (x, z) -> ResourceArea.inside(x, z, 0) }.sortedWith(compareBy({ it.first }, { it.second }))
        println("In the box but walked to from outside: $boxedOutside")
        assertEquals(emptyList<Pair<Int, Int>>(), boxedOutside, "in the box, but reached from outside without the gate")
    }

    @Test
    fun `no dark crab spot or golem can be reached from outside the walls`() {
        val outside = walk(OUTSIDE, xs, zs)
        for ((x, z) in CRAB_SPOTS + GOLEMS) {
            val beside = (-1..1).flatMap { dx -> (-1..1).map { dz -> (x + dx) to (z + dz) } }.filter { it in outside }
            assertEquals(emptyList<Pair<Int, Int>>(), beside, "beside $x,$z from outside")
        }
    }

    @Test
    fun `each Pirates' Hideout door's own tile is inside the hideout`() {
        val locs = RealMap.load().map.locs
        val doors = (3020..3060).flatMap { x -> (3940..3970).mapNotNull { z -> locs.at(x, z, 0, 0)?.takeIf { it.id == ThievingLocks.DOOR_PIRATES } } }
        println("Pirates' Hideout doors: ${doors.map { "${it.x},${it.z} angle ${it.angle}" }}")
        assertEquals(3, doors.size)
        val outside = walk(PIRATES_OUTSIDE, 3000..3080, 3930..3990)
        for (door in doors) {
            assertTrue((door.x to door.z) !in outside, "door ${door.x},${door.z}'s own tile is not reached from outside")
            val (ox, oz) = when (door.angle) { 0 -> door.x - 1 to door.z; 1 -> door.x to door.z + 1; 2 -> door.x + 1 to door.z; else -> door.x to door.z - 1 }
            assertTrue((ox to oz) in outside, "door ${door.x},${door.z}: the tile through its wall, $ox,$oz, is outside")
            assertTrue(ThievingLocks.inside(door, door.x, door.z, ownTileInside = true))
            assertTrue(ThievingLocks.outside(door, ox, oz, ownTileInside = true))
        }
    }

    private companion object {
        val PILES = 3185 to 3933
        val OUTSIDE = 3176 to 3950
        val PIRATES_OUTSIDE = 3045 to 3940
        val CRAB_SPOTS = listOf(3181 to 3926, 3183 to 3926, 3185 to 3926, 3187 to 3927)
        val GOLEMS = listOf(3178 to 3938, 3191 to 3934)
    }
}
