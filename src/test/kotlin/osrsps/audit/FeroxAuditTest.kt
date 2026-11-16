package osrsps.audit

import osrsps.game.Wilderness
import osrsps.map.Steps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks Ferox Enclave's safe area ([Wilderness.FEROX_ROWS]) against the real map (see [RealMap]; skipped without it,
 * as in CI). Exploit review round 37 found the old box reached past the town's east wall, one step from the level 13
 * obelisk, so a Tele Blocked player walked in with no barrier. Run it in the cloud after a change to the rows.
 */
class FeroxAuditTest {
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

    @Test
    fun `the safe area is the town's rectangle less every tile walked to from outside without a barrier`() {
        val outside = walk(3105 to 3600, 3100..3175, 3595..3665)
        val expected = HashSet<Pair<Int, Int>>()
        for (x in Wilderness.FEROX_WEST..Wilderness.FEROX_EAST) for (z in Wilderness.FEROX_SOUTH..Wilderness.FEROX_NORTH) {
            if ((x to z) !in outside) expected += x to z
        }
        val actual = Wilderness.FEROX_ROWS.flatMap { (z, runs) -> runs.flatMap { run -> run.map { it to z } } }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `everywhere walked to from the middle of the town is safe, so the rectangle holds all of it`() {
        val town = walk(3140 to 3630, 3100..3175, 3595..3665)
        assertTrue(town.size in 100..2000, "the town is walled in: ${town.size} tiles")
        val unsafe = town.filterNot { (x, z) -> Wilderness.inFerox(x, z) }
        assertEquals(emptyList(), unsafe)
    }
}
