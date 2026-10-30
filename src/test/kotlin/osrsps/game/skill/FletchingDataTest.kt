package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.dialogue.Chatbox

/** fletching.json read back against the OSRS Wiki's numbers (read 2026-10-30), and the make recipe's arithmetic. */
class FletchingDataTest {
    private val data = FletchingData.load()

    private fun product(log: Int, obj: Int) = data.knifeOnLogs.getValue(log).single { it.product == obj }

    @Test
    fun `every log tier is there, each box fits, and no log makes the same thing twice`() {
        assertEquals(listOf(1511, 1521, 1519, 6333, 1517, 6332, 1515, 1513, 19669), data.knifeOnLogs.keys.toList())
        for ((log, recipes) in data.knifeOnLogs) {
            assertTrue(recipes.size in 1..Chatbox.SKILLMULTI_MAX_ITEMS, "log $log")
            assertEquals(recipes.size, recipes.map { it.product }.toSet().size, "log $log")
            assertTrue(recipes.all { it.inputs == listOf(ObjCount(log, 1)) && it.tools == listOf(946) }, "log $log")
        }
    }

    @Test
    fun `shafts are 15 a log and 15 more a tier, with the Wiki's levels and XP`() {
        val shafts = listOf(1511 to Triple(15, 1, 50), 1521 to Triple(30, 15, 100), 1519 to Triple(45, 30, 150), 1517 to Triple(60, 45, 200),
            1515 to Triple(75, 60, 250), 1513 to Triple(90, 75, 300), 19669 to Triple(105, 90, 350))
        for ((log, expected) in shafts) {
            val recipe = product(log, 52)
            assertEquals(expected, Triple(recipe.amount, recipe.level, recipe.xpTenths), "log $log")
        }
    }

    @Test
    fun `bows and stocks match the Wiki`() {
        assertEquals(5 to 50, product(1511, 50).let { it.level to it.xpTenths })
        assertEquals(10 to 100, product(1511, 48).let { it.level to it.xpTenths })
        assertEquals(35 to 333, product(1519, 60).let { it.level to it.xpTenths })
        assertEquals(85 to 915, product(1513, 70).let { it.level to it.xpTenths })
        assertEquals(9 to 60, product(1511, 9440).let { it.level to it.xpTenths })
        assertEquals(46 to 270, product(6333, 9446).let { it.level to it.xpTenths })
        assertEquals(3 to 50, product(1511, 19584).let { it.level to it.xpTenths })
        assertTrue(data.knifeOnLogs.values.flatten().all { it.first == 2 && it.interval == 3 && it.anim == 1248 })
    }

    @Test
    fun `a recipe counts what the inventory can make, and removeObj takes from several slots`() {
        val inventory = Inventory(93, 28)
        val logs = ObjType(1511, "Logs")
        val recipe = product(1511, 50)
        inventory.add(logs, 3)
        assertEquals(0, recipe.times(inventory), "no knife")
        inventory.add(ObjType(946, "Knife"), 1)
        assertEquals(3, recipe.times(inventory))
        Making.removeObj(inventory, 1511, 2)
        assertEquals(1L, inventory.count(1511))
    }
}
