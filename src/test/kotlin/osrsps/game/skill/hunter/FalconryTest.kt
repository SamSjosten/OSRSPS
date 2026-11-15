package osrsps.game.skill.hunter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.skill.SuccessRoll

/** Hunter step 7's data against the Wiki: levels, XP, Mod Ash's catch rates, drops, and the guessed flight and area. */
class FalconryTest {
    @Test
    fun `levels, XP and drops are the Falconry and kebbit pages'`() {
        assertEquals(listOf(43 to 1040, 57 to 1320, 69 to 1560), Falconry.KEBBITS.map { it.level to it.xpTenths })
        assertEquals(listOf(2, 2, 3), Falconry.KEBBITS.map { it.drops.size })
        assertTrue(Falconry.KEBBITS.all { it.drops.first() == Falconry.BONES })
    }

    @Test
    fun `the catch pairs are the Skilling success charts', giving Mod Ash's rates at 1 and 99`() {
        assertEquals(listOf(26 to 310, 0 to 253, 0 to 205), Falconry.KEBBITS.map { it.pair })
        // Spotted 10% to 121%, dark 0% to 99%, dashing 0% to 80%.
        val expected = listOf(10 to 121, 0 to 99, 0 to 80)
        for ((kebbit, rates) in Falconry.KEBBITS.zip(expected)) {
            // The chart's pairs at levels 1 and 99, before SuccessRoll caps a chance at 256.
            val low = (1 + kebbit.pair.first) * 100 / SuccessRoll.DENOMINATOR
            val high = (1 + kebbit.pair.second) * 100 / SuccessRoll.DENOMINATOR
            assertEquals(rates, low to high, "kebbit ${kebbit.npc}")
        }
    }

    @Test
    fun `the falcon flies a tile in three, at least one tick, and the area is the fence's box`() {
        assertEquals(1, Falconry.flightTicks(2370, 3590, 2371, 3590))
        assertEquals(3, Falconry.flightTicks(2370, 3590, 2376, 3594))
        assertTrue(Falconry.inArea(2376, 3606, 0))
        assertFalse(Falconry.inArea(2376, 3606, 1))
        assertFalse(Falconry.inArea(2363, 3590, 0))
    }
}
