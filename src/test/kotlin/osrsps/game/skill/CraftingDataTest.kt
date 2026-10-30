package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** crafting.json read back against the OSRS Wiki's numbers (read 2026-10-30). */
class CraftingDataTest {
    private val data = CraftingData.load()

    private fun leather(hide: Int, obj: Int) = data.leather.getValue(hide).single { it.product == obj }

    @Test
    fun `spinning matches the Wiki`() {
        assertEquals(listOf(1759 to 25, 1777 to 150), data.spinning.map { it.product to it.xpTenths })
        assertEquals(listOf(1, 10), data.spinning.map { it.level })
        assertTrue(data.spinning.all { it.first == 2 && it.interval == 3 })
    }

    @Test
    fun `leather, hard leather and dragon leather match the Wiki, with thread lasting 5`() {
        assertEquals(listOf(1741, 1743, 1745, 2505, 2507, 2509), data.leather.keys.toList())
        assertEquals(Triple(1, 138, 1), leather(1741, 1059).let { Triple(it.level, it.xpTenths, it.inputs[0].count) })
        assertEquals(Triple(7, 163, 1), leather(1741, 1061).let { Triple(it.level, it.xpTenths, it.inputs[0].count) })
        assertEquals(Triple(38, 370, 1), leather(1741, 1169).let { Triple(it.level, it.xpTenths, it.inputs[0].count) })
        assertEquals(Triple(28, 350, 2), leather(1743, 1131).let { Triple(it.level, it.xpTenths, it.interval) })
        assertEquals(Triple(63, 1860, 3), leather(1745, 1135).let { Triple(it.level, it.xpTenths, it.inputs[0].count) })
        assertEquals(Triple(68, 1400, 2), leather(2505, 2493).let { Triple(it.level, it.xpTenths, it.inputs[0].count) })
        assertEquals(Triple(84, 2580, 3), leather(2509, 2503).let { Triple(it.level, it.xpTenths, it.inputs[0].count) })
        val recipes = data.leather.values.flatten()
        assertTrue(recipes.all { it.tools == listOf(1733) && it.wears == WearingMaterial(1734, 5, CraftingData.THREAD_CHARGE, CraftingLines.THREAD_USED) })
        assertEquals(Triple(1133, 41, 400), data.studded.getValue(1129).let { Triple(it.product, it.level, it.xpTenths) })
        assertEquals(Triple(1097, 44, 420), data.studded.getValue(1095).let { Triple(it.product, it.level, it.xpTenths) })
    }
}
