package osrsps.game.skill

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom

/** Gathering 2, step G4: the Desert Quarry's granite and sandstone rocks against the Wiki. */
class MiningG4Test {
    private fun rock(locId: Int) = Mining.ROCKS.getValue(locId)

    /** The chance any size succeeds, as the Wiki's comment works it out: 1 minus the chance every size misses. */
    private fun anySize(sizes: List<RockSize>, level: Int): Double =
        1 - sizes.fold(1.0) { miss, size -> miss * (1 - SuccessRoll.chance(size.low, size.high, level) / 256.0) }

    private fun percent(p: Double) = (p * 1000).roundToInt() / 10.0

    @Test
    fun `granite and sandstone give the Wiki's chance of any piece at their level and at 99`() {
        val granite = rock(GRANITE).type
        assertEquals(42.0, percent(anySize(granite.sizes, 45)))
        assertEquals(68.2, percent(anySize(granite.sizes, 99)))
        val sandstone = rock(SANDSTONE).type
        assertEquals(56.5, percent(anySize(sandstone.sizes, 35)))
        assertEquals(92.7, percent(anySize(sandstone.sizes, 99)))
    }

    @Test
    fun `levels, sizes, experience and respawns are the Wiki's`() {
        val granite = rock(GRANITE)
        assertEquals(listOf(45, 9), listOf(granite.type.level, granite.type.respawn))
        assertEquals(
            listOf(listOf(6983, 750, 6, 64), listOf(6981, 600, 8, 75), listOf(6979, 500, 16, 100)),
            granite.type.sizes.map { listOf(it.obj, it.xpTenths, it.low, it.high) },
        )
        val sandstone = rock(SANDSTONE)
        assertEquals(listOf(35, 9), listOf(sandstone.type.level, sandstone.type.respawn))
        assertEquals(
            listOf(listOf(6977, 600, 4, 50), listOf(6975, 500, 8, 75), listOf(6973, 400, 16, 100), listOf(6971, 300, 25, 200)),
            sandstone.type.sizes.map { listOf(it.obj, it.xpTenths, it.low, it.high) },
        )
        // The empty rock shares each rock's model: granite's 1391 is rocks2's, sandstone's 1390 rocks1's.
        assertEquals(Mining.ROCKS2, granite.empty)
        assertEquals(Mining.ROCKS1, sandstone.empty)
        // The quarry is outside the Mining Guild and the Wilderness: 5.4 seconds.
        assertEquals(9, Mining.respawn(granite.type, 3172, 2920, 0))
    }

    @Test
    fun `a seeded quarry run gives each size at its cascade share`() {
        val sizes = rock(SANDSTONE).type.sizes
        val random = GameRandom(5)
        val rolls = 100_000
        val counts = HashMap<Int, Int>()
        repeat(rolls) { Mining.quarrySize(random, sizes, LEVEL)?.let { counts.merge(it.obj, 1, Int::plus) } }
        // A size is given when it succeeds and every larger one missed.
        var larger = 1.0
        for (size in sizes) {
            val p = SuccessRoll.chance(size.low, size.high, LEVEL) / 256.0
            val share = larger * p
            larger *= 1 - p
            val expected = rolls * share
            val sd = Math.sqrt(expected * (1 - share))
            assertTrue(abs(counts.getValue(size.obj) - expected) < 4 * sd, "size ${size.obj}: ${counts[size.obj]} vs $expected")
        }
    }

    private companion object {
        const val GRANITE = 11387
        const val SANDSTONE = 11386
        const val LEVEL = 60
    }
}
