package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals

class SlayerSuperiorsTest {
    @Test
    fun `the unique tables' rate is the Wiki's, which gives its chance of any unique`() {
        // 1 / (200 - (10 + 55)² / 125) for a Slayer 10 superior: 1/166.2 a table, and any unique 9/8 of that, the
        // Wiki's 1/147.7 for the chasm crawler and 1/137.8 for the giant rockslug (Slayer 20).
        assertEquals(20_775, SlayerSuperiors.uniqueOdds(10))
        assertEquals(1477, anyUniqueTenths(10))
        assertEquals(19_375, SlayerSuperiors.uniqueOdds(20))
        assertEquals(1378, anyUniqueTenths(20))
    }

    @Test
    fun `either unique table is as likely as the other`() {
        val tables = (0 until SlayerSuperiors.uniqueOdds(10)).groupingBy { SlayerSuperiors.uniqueTable(it) }.eachCount()
        assertEquals(125, tables[1])
        assertEquals(125, tables[2])
    }

    /** 1 in this many tenths of kills give any unique: the table's odds over 125, times 8/9. */
    private fun anyUniqueTenths(level: Int) = Math.round(SlayerSuperiors.uniqueOdds(level) * 8.0 / 9 / 125 * 10).toInt()
}
