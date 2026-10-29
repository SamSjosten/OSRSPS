package osrsps.game.skill

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom

class BirdNestsTest {
    @Test
    fun `a nest is 1 in 256 a log, and 10 percent more with the cape`() {
        assertEquals(1.0 / 256, BirdNests.CHANCE.toDouble() / BirdNests.CHANCE_DENOMINATOR)
        assertEquals(1.1 / 256, BirdNests.CAPE_CHANCE.toDouble() / BirdNests.CHANCE_DENOMINATOR, 1e-12)
    }

    @Test
    fun `the tables add up to the Wiki's totals`() {
        assertEquals(100, BirdNests.NEST_TYPES.sumOf { it.weight })
        assertEquals(65, BirdNests.NEST_TYPES.single { it.obj == BirdNests.SEEDS }.weight)
        assertEquals(32, BirdNests.NEST_TYPES.single { it.obj == BirdNests.RING }.weight)
        assertEquals(1011, BirdNests.CONTENTS.getValue(BirdNests.SEEDS).sumOf { it.weight })
        assertEquals(100, BirdNests.CONTENTS.getValue(BirdNests.RING).sumOf { it.weight })
        // Every nest a tree drops can be searched, and nothing is listed twice.
        assertEquals(BirdNests.NEST_TYPES.map { it.obj }.toSet(), BirdNests.CONTENTS.keys)
        for (table in BirdNests.CONTENTS.values) assertEquals(table.size, table.map { it.obj }.toSet().size)
    }

    @Test
    fun `pick draws each entry by its weight`() {
        val random = GameRandom(7)
        val table = BirdNests.CONTENTS.getValue(BirdNests.SEEDS)
        val draws = 202_200
        val counts = HashMap<Int, Int>()
        repeat(draws) { counts.merge(BirdNests.pick(random, table), 1, Int::plus) }
        for (entry in table) {
            val expected = draws.toDouble() * entry.weight / 1011
            val got = counts[entry.obj] ?: 0
            // Well inside five standard deviations.
            assertTrue(abs(got - expected) < 5 * Math.sqrt(expected) + 5, "${entry.obj}: $got, expected about $expected")
        }
    }
}
