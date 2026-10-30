package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmithingTest {
    private fun item(bar: Bar, obj: Int) = AnvilItems.BY_BAR.getValue(bar.obj).first { it.obj == obj }

    @Test
    fun `the anvil items match the Wiki's bronze and iron tables`() {
        // Smithing page (read 2026-10-29): level offsets from the bar's 1 or 15, bars, and how many are made.
        val bronze = mapOf(1205 to Triple(1, 1, 1), 1351 to Triple(1, 1, 1), 1277 to Triple(4, 1, 1), 4819 to Triple(4, 1, 15),
            819 to Triple(4, 1, 10), 1321 to Triple(5, 2, 1), 39 to Triple(5, 1, 15), 864 to Triple(7, 1, 5), 1117 to Triple(18, 5, 1),
            1307 to Triple(14, 3, 1), 1087 to Triple(16, 3, 1))
        for ((obj, want) in bronze) {
            val item = item(Smithing.BRONZE, obj)
            assertEquals(want, Triple(item.level, item.bars, item.count), "bronze $obj")
        }
        assertEquals(Triple(15, 1, 1), item(Smithing.IRON, 1203).let { Triple(it.level, it.bars, it.count) })
        assertEquals(Triple(33, 5, 1), item(Smithing.IRON, 1115).let { Triple(it.level, it.bars, it.count) })
    }

    @Test
    fun `smithing XP is 12_5 a bar a tier, so a bronze platebody is 62_5`() {
        assertEquals(625, Smithing.BRONZE.smithXpTenths * item(Smithing.BRONZE, 1117).bars)
        assertEquals(listOf(125, 250, 375, 500, 625, 750), Smithing.BARS.filter { it.anvilType > 0 }.map { it.smithXpTenths })
    }

    @Test
    fun `every anvil bar has its type and items, each on its own component`() {
        val anvil = Smithing.BARS.filter { it.anvilType > 0 }
        assertEquals((1..6).toList(), anvil.map { it.anvilType })
        assertEquals(anvil.map { it.obj }.toSet(), AnvilItems.BY_BAR.keys)
        for ((bar, items) in AnvilItems.BY_BAR) {
            assertEquals(items.size, items.map { it.component }.toSet().size, "bar $bar")
            assertTrue(items.all { it.component in 9..38 && it.level in 1..99 && it.bars in 1..5 }, "bar $bar")
        }
    }
}
