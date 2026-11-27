package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals

class TaiBwoEventsTest {
    @Test
    fun `the tuber goes from 1 in 100 at level 10 to 1 in 33 at 99, and the table adds to 99`() {
        assertEquals(0.01, TaiBwoEvents.tuberChance(10), 1e-12)
        assertEquals(0.01, TaiBwoEvents.tuberChance(1), 1e-12, "below 10 as at 10")
        assertEquals(1.0 / 33, TaiBwoEvents.tuberChance(99), 1e-12)
        assertEquals((0.01 + 1.0 / 33) / 2, TaiBwoEvents.tuberChance(54) + (TaiBwoEvents.tuberChance(55) - TaiBwoEvents.tuberChance(54)) / 2, 1e-12)
        assertEquals(TaiBwoEvents.TABLE_TOTAL, TaiBwoEvents.TABLE.sumOf { it.second })
        assertEquals(listOf(22, 21, 21, 8, 7, 6), TaiBwoEvents.Creature.entries.map { it.weight }, "spider, tribesman, snake, mosquito, the two swarms")
    }

    @Test
    fun `a hack cycle is 16 ticks at 10 to 19, a tick less each ten levels, 8 from 90`() {
        assertEquals(16, TaiBwoCleanup.cycle(10))
        assertEquals(16, TaiBwoCleanup.cycle(19))
        assertEquals(15, TaiBwoCleanup.cycle(20))
        assertEquals(9, TaiBwoCleanup.cycle(89))
        assertEquals(8, TaiBwoCleanup.cycle(99))
    }
}
