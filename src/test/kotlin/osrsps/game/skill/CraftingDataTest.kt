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

    @Test
    fun `gems match the Wiki, and the crush pairs give each page's chart`() {
        assertEquals(10, data.gems.size)
        assertEquals(Triple(1607, 20, 500), data.gems.getValue(1623).let { Triple(it.product, it.level, it.xpTenths) })
        assertEquals(Triple(19493, 89, 2000), data.gems.getValue(19496).let { Triple(it.product, it.level, it.xpTenths) })
        assertTrue(data.gems.values.all { it.tools == listOf(1755) && it.first == 2 && it.interval == 2 })
        assertEquals(setOf(1625, 1627, 1629), data.gems.filterValues { it.chance != null }.keys)
        // The charts' points, in 256ths: opal 1, 50, 99; jade 1, 13, 50, 99; red topaz 1, 2, 16, 49, 50, 99.
        val points = mapOf(
            1625 to mapOf(1 to 129, 50 to 190, 99 to 251),
            1627 to mapOf(1 to 101, 13 to 119, 50 to 174, 99 to 246),
            1629 to mapOf(1 to 91, 2 to 93, 16 to 114, 49 to 164, 50 to 166, 99 to 241),
        )
        for ((uncut, chart) in points) {
            val chance = data.gems.getValue(uncut).chance!!
            for ((level, expected) in chart) assertEquals(expected, SuccessRoll.chance(chance.low, chance.high, level), "gem $uncut at $level")
        }
        assertEquals(listOf(38, 50, 63), listOf(1625, 1627, 1629).map { data.gems.getValue(it).chance!!.failedXpTenths })
        assertTrue(data.gems.values.mapNotNull { it.chance }.all { it.failed == 1633 })
    }

    @Test
    fun `gold and silver jewellery match the Wiki, by their buttons`() {
        val (gold, silver) = data.jewellery
        assertEquals(446 to 32, gold.id to gold.items.size)
        assertEquals(6 to 16, silver.id to silver.items.size)
        fun of(furnace: JewelleryInterface, component: Int) = furnace.items.getValue(component).let { Triple(it.product, it.level, it.xpTenths) }
        assertEquals(Triple(1635, 5, 150), of(gold, 8))
        assertEquals(Triple(19538, 89, 1500), of(gold, 15))
        assertEquals(Triple(1662, 56, 900), of(gold, 27))
        assertEquals(Triple(1683, 80, 1500), of(gold, 42))
        assertEquals(Triple(11069, 7, 250), of(gold, 50))
        assertEquals(Triple(19532, 95, 1800), of(gold, 58))
        assertEquals(Triple(21081, 1, 100), of(silver, 7))
        assertEquals(Triple(21105, 45, 800), of(silver, 17))
        assertEquals(Triple(5525, 23, 525), of(silver, 28))
        assertEquals(listOf(ObjCount(2357, 1), ObjCount(1607, 1)), gold.items.getValue(9).inputs)
        assertEquals(listOf(1592), gold.items.getValue(9).tools)
        assertEquals(11, data.amulets.size)
        assertTrue(data.amulets.values.all { it.level == 1 && it.xpTenths == 40 && it.inputs.last() == ObjCount(1759, 1) })
    }
}
