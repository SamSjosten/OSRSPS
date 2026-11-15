package osrsps.game.skill.hunter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Hunter step 5's data against the Wiki and the cache: levels, XP, drops, the 25 pits and their varbits, the fur guess. */
class PitfallsTest {
    @Test
    fun `levels and XP are the Pitfall page's`() {
        assertEquals(
            listOf(31 to 1800, 41 to 2400, 55 to 3000, 72 to 3800, 91 to 4500),
            Pitfalls.CREATURES.map { it.level to it.xpTenths },
        )
        // Antelopes are always caught; the big cats roll.
        assertNull(Pitfalls.SUNLIGHT.catchPair)
        assertNull(Pitfalls.MOONLIGHT.catchPair)
        assertTrue(listOf(Pitfalls.LARUPIA, Pitfalls.GRAAHK, Pitfalls.KYATT).all { it.catchPair != null && it.fur > 0 && it.tattyFur > 0 })
        assertEquals(listOf(5, 7, 7, 5, 5), Pitfalls.CREATURES.map { it.maxHit })
    }

    @Test
    fun `every creature leaves big bones`() {
        assertTrue(Pitfalls.CREATURES.all { c -> c.loot.first().obj == Pitfalls.BIG_BONES })
        val splinters = Pitfalls.SUNLIGHT.loot.first { it.obj == 28924 }
        assertEquals(2 to 6, splinters.count to splinters.countMax)
    }

    @Test
    fun `the map's 25 pits, each with its own varbit`() {
        val pits = Pitfalls.PITS.values
        assertEquals(25, pits.size)
        assertEquals(25, pits.map { it.varbit }.toSet().size)
        assertEquals(listOf(5, 5, 6, 5, 4), Pitfalls.CREATURES.map { c -> pits.count { it.creature === c } })
        assertEquals(2958, Pitfalls.PITS.getValue(19253).varbit)
        assertEquals(2973, Pitfalls.PITS.getValue(19268).varbit)
        assertEquals(9775, Pitfalls.PITS.getValue(51681).varbit)
    }

    @Test
    fun `the undamaged fur's chance rises from half to always`() {
        assertEquals(500, Pitfalls.perfectFurChance(31, 31))
        assertEquals(500, Pitfalls.perfectFurChance(31, 20))
        assertEquals(750, Pitfalls.perfectFurChance(31, 65))
        assertEquals(1000, Pitfalls.perfectFurChance(31, 99))
    }
}
