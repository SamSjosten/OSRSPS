package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** herblore.json read back against the OSRS Wiki's numbers (read 2026-10-30). */
class HerbloreDataTest {
    private val data = HerbloreData.load()

    @Test
    fun `fifteen herbs clean at the Wiki's levels and XP, each with its unfinished potion`() {
        assertEquals(15, data.cleaning.size)
        assertEquals(GrimyHerb(199, 249, 3, 25), data.cleaning[199])
        assertEquals(GrimyHerb(207, 257, 25, 75), data.cleaning[207])
        assertEquals(GrimyHerb(30094, 30097, 58, 118), data.cleaning[30094])
        assertEquals(GrimyHerb(219, 269, 75, 150), data.cleaning[219])
        assertEquals(data.cleaning.values.map { it.clean }.toSet(), data.unfinished.keys)
        for (herb in data.cleaning.values) assertTrue(data.unfinished.getValue(herb.clean).level >= herb.level, "herb ${herb.clean}")
        assertEquals(91 to 3, data.unfinished.getValue(249).let { it.product to it.level })
        assertEquals(99 to 30, data.unfinished.getValue(257).let { it.product to it.level })
        assertEquals(111 to 78, data.unfinished.getValue(269).let { it.product to it.level })
        assertTrue(data.unfinished.values.all { it.xpTenths == 0 && it.first == 1 && it.interval == 1 && it.inputs[1] == ObjCount(227, 1) })
    }

    @Test
    fun `grinding is 0, then 2, then 3 ticks with the pestle kept, and a vial fills in 1`() {
        assertEquals(setOf(237, 1973, 10109, 5075, 9735, 243), data.grinding.keys)
        assertTrue(data.grinding.values.all { it.first == 0 && it.second == 2 && it.interval == 3 && it.tools == listOf(233) })
        assertEquals(235, data.grinding.getValue(237).product)
        assertEquals(listOf(ObjCount(229, 1)), data.fillVial.inputs)
        assertEquals(227 to 1, data.fillVial.product to data.fillVial.first)
        assertTrue("Well" !in data.waterSources)
    }

    @Test
    fun `23 finished potions from an unfinished potion and a secondary, a (3) dose each, at the Wiki's levels and XP`() {
        assertEquals(23, data.potions.size)
        val attack = data.potions.getValue(91 to 221)
        assertEquals(Triple(121, 3, 250), Triple(attack.product, attack.level, attack.xpTenths))
        assertEquals(listOf(ObjCount(221, 1), ObjCount(91, 1)), attack.inputs)
        assertEquals(Triple(3026, 63, 1425), data.potions.getValue(3004 to 223).let { Triple(it.product, it.level, it.xpTenths) })
        assertEquals(Triple(6687, 81, 1800), data.potions.getValue(3002 to 6693).let { Triple(it.product, it.level, it.xpTenths) })
        assertEquals(1063, data.potions.getValue(101 to 235).xpTenths)
        val unf = data.unfinished.values.map { it.product }.toSet()
        for ((pair, recipe) in data.potions) {
            assertTrue(pair.first in unf, "${recipe.key} starts from an unfinished potion")
            assertTrue(recipe.level >= data.unfinished.values.single { it.product == pair.first }.level, recipe.key)
            assertTrue(recipe.first == 2 && recipe.interval == 2 && recipe.anim == 363, recipe.key)
        }
    }
}
