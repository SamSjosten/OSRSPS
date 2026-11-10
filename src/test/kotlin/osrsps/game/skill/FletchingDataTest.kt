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
    fun `stringing, arrows and darts match the Wiki`() {
        assertEquals(12, data.stringing.size)
        assertEquals(Triple(841, 5, 50), data.stringing.getValue(50).let { Triple(it.product, it.level, it.xpTenths) })
        assertEquals(Triple(859, 85, 915), data.stringing.getValue(70).let { Triple(it.product, it.level, it.xpTenths) })
        assertTrue(data.stringing.values.all { it.first == 2 && it.interval == 2 && it.inputs.last() == ObjCount(1777, 1) })
        val headless = data.headless!!
        assertEquals(listOf(ObjCount(52, 1), ObjCount(314, 1)), headless.inputs)
        assertTrue(headless.batch && headless.amount == 15 && headless.xpTenths == 10)
        val arrows = data.arrows.mapValues { (_, it) -> Triple(it.product, it.level, it.xpTenths) }
        assertEquals(Triple(882, 1, 13), arrows[39])
        assertEquals(Triple(892, 75, 125), arrows[44])
        assertEquals(Triple(11212, 90, 150), arrows[11237])
        val darts = data.darts.mapValues { (_, it) -> Triple(it.product, it.level, it.xpTenths) }
        assertEquals(Triple(806, 10, 18), darts[819])
        assertEquals(Triple(811, 81, 188), darts[824])
        assertEquals(Triple(11230, 95, 250), darts[11232])
        assertTrue(data.darts.values.all { it.batch && it.amount == 10 })
    }

    @Test
    fun `a batch counts short sets, and a recipe counts what the inventory can make, and removeObj takes from several slots`() {
        val inventory = Inventory(93, 28)
        val logs = ObjType(1511, "Logs")
        val recipe = product(1511, 50)
        inventory.add(logs, 3)
        assertEquals(0, recipe.times(inventory), "no knife")
        inventory.add(ObjType(946, "Knife"), 1)
        assertEquals(3, recipe.times(inventory))
        Making.removeObj(inventory, 1511, 2)
        assertEquals(1L, inventory.count(1511))
        val sets = data.headless!!
        inventory.add(ObjType(52, "Arrow shaft", stackable = true), 20)
        inventory.add(ObjType(314, "Feather", stackable = true), 31)
        assertEquals(2, sets.times(inventory), "15 and 5")
    }

    @Test
    fun `crossbows match the Wiki's table, each stock's own limbs, a hammer, then the string, at the stock's level`() {
        // Template:Table/Fletching/Crossbows (read 2026-11-10): level, limbs XP, string XP.
        val expected = mapOf(9454 to Triple(9, 120, 60), 9456 to Triple(24, 320, 160), 9457 to Triple(39, 440, 220),
            9459 to Triple(46, 540, 270), 9461 to Triple(54, 640, 320), 9463 to Triple(61, 820, 410), 9465 to Triple(69, 1000, 500),
            21921 to Triple(78, 1350, 700))
        assertEquals(expected.keys, data.limbs.map { it.product }.toSet())
        for (limbs in data.limbs) {
            val strung = data.crossbows.getValue(limbs.product)
            assertEquals(expected.getValue(limbs.product), Triple(limbs.level, limbs.xpTenths, strung.xpTenths), "crossbow ${limbs.product}")
            assertEquals(limbs.level, strung.level)
            assertEquals(listOf(2347), limbs.tools)
            assertEquals(ObjCount(9438, 1), strung.inputs[1])
            assertTrue(listOf(limbs, strung).all { it.first == 1 && it.interval == 2 && it.anim >= 0 })
        }
        // The stock a crossbow takes is the log tier with the same level: the stock's own level in knifeOnLogs.
        val stockLevels = data.knifeOnLogs.values.flatten().filter { it.key.contains("stock") }.associate { it.product to it.level }
        assertTrue(data.limbs.all { stockLevels.getValue(it.inputs[0].obj) == it.level }, "stock levels")
        assertEquals(listOf(ObjCount(9440, 1), ObjCount(9420, 1)), data.limbs.first().inputs)
        assertEquals(9174, data.crossbows.getValue(9454).product)
        assertEquals(21902, data.crossbows.getValue(21921).product)
    }

    @Test
    fun `bolts, tipped bolts, bolt tips and javelins match the Wiki`() {
        // Template:Table/Fletching/Bolts: XP per bolt, 10 to a click.
        val bolts = data.bolts.mapValues { (_, it) -> Triple(it.product, it.level, it.xpTenths) }
        assertEquals(10, bolts.size)
        assertEquals(Triple(877, 9, 5), bolts[9375])
        assertEquals(Triple(9145, 43, 25), bolts[9382])
        assertEquals(Triple(11875, 55, 30), bolts[11876])
        assertEquals(Triple(9144, 69, 100), bolts[9381])
        assertEquals(Triple(21905, 84, 120), bolts[21930])
        assertTrue(data.bolts.values.all { it.batch && it.amount == 10 && it.inputs[1] == ObjCount(314, 1) })
        // Template:Table/Fletching/Tipped bolts and Tipped dragon bolts: XP per bolt, 2 ticks a set of 10.
        fun tipped(tips: Int, bolts: Int) = data.tippedBolts.single { it.inputs == listOf(ObjCount(bolts, 1), ObjCount(tips, 1)) }
        assertEquals(22, data.tippedBolts.size)
        assertEquals(Triple(879, 11, 16), tipped(45, 877).let { Triple(it.product, it.level, it.xpTenths) })
        assertEquals(Triple(881, 51, 95), tipped(47, 877).let { Triple(it.product, it.level, it.xpTenths) })
        assertEquals(Triple(9342, 73, 94), tipped(9194, 9144).let { Triple(it.product, it.level, it.xpTenths) })
        assertEquals(Triple(21961, 84, 40), tipped(9188, 21905).let { Triple(it.product, it.level, it.xpTenths) })
        assertEquals(Triple(9336, 48, 39), tipped(9188, 9141).let { Triple(it.product, it.level, it.xpTenths) })
        assertEquals(10, data.tippedBolts.count { it.inputs[0].obj == 21905 }, "dragon bolts take every gem tip")
        assertTrue(data.tippedBolts.all { it.batch && it.amount == 10 && it.first == 2 && it.interval == 2 })
        // Template:Table/Fletching/Bolt tips: 12 a gem, 24 from an onyx or oyster pearls, 6 from an oyster pearl; 5 ticks.
        val tips = data.boltTips.mapValues { (_, it) -> listOf(it.product, it.amount, it.level, it.xpTenths) }
        assertEquals(11, tips.size)
        assertEquals(listOf(45, 12, 11, 15), tips[1609])
        assertEquals(listOf(46, 6, 41, 32), tips[411])
        assertEquals(listOf(46, 24, 41, 32), tips[413])
        assertEquals(listOf(9189, 12, 56, 40), tips[1607])
        assertEquals(listOf(9194, 24, 73, 94), tips[6573])
        assertTrue(data.boltTips.values.all { it.tools == listOf(1755) && it.interval == 5 && !it.batch })
        // Template:Table/Fletching/Javelins: XP per javelin, 15 at a time, 1 tick a set.
        val javelins = data.javelins.mapValues { (_, it) -> Triple(it.product, it.level, it.xpTenths) }
        assertEquals(8, javelins.size)
        assertEquals(Triple(825, 3, 10), javelins[19570])
        assertEquals(Triple(830, 77, 124), javelins[19580])
        assertEquals(Triple(21318, 84, 135), javelins[21352])
        assertEquals(Triple(19484, 92, 150), javelins[19582])
        assertTrue(data.javelins.values.all { it.batch && it.amount == 15 && it.interval == 1 && it.inputs[0] == ObjCount(19584, 1) })
    }

    @Test
    fun `20 bolts and 7 tips make one set of 7, and a gem needs the chisel`() {
        val inventory = Inventory(93, 28)
        val opal = data.tippedBolts.first()
        inventory.add(ObjType(877, "Bronze bolts", stackable = true), 20)
        inventory.add(ObjType(45, "Opal bolt tips", stackable = true), 7)
        assertEquals(1, opal.times(inventory))
        assertEquals(7L, opal.units(inventory))
        val cut = data.boltTips.getValue(1609)
        inventory.add(ObjType(1609, "Opal"), 1)
        assertEquals(0, cut.times(inventory), "no chisel")
        inventory.add(ObjType(1755, "Chisel"), 1)
        assertEquals(1, cut.times(inventory))
    }
}
