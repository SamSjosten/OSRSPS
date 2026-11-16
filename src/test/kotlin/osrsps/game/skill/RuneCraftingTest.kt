package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals

class RuneCraftingTest {
    private fun recipe(key: String) = RuneCrafting.RECIPES.first { it.key == key }

    @Test
    fun `runes per essence follow the Wiki's Multiple runes table`() {
        assertEquals(1, recipe("air").perEssence(10))
        assertEquals(2, recipe("air").perEssence(11))
        assertEquals(10, recipe("air").perEssence(99))
        assertEquals(8, recipe("mind").perEssence(98))
        assertEquals(4, recipe("earth").perEssence(99))
        assertEquals(5, recipe("earth").perEssence(104))
        assertEquals(3, recipe("fire").perEssence(70))
        assertEquals(2, recipe("astral").perEssence(82))
        assertEquals(1, recipe("death").perEssence(98))
        assertEquals(2, recipe("death").perEssence(99))
        assertEquals(1, recipe("wrath").perEssence(99))
        assertEquals(1, recipe("blood").perEssence(99))
    }

    @Test
    fun `only the six altars of the first runes take rune essence`() {
        assertEquals(listOf("air", "mind", "water", "earth", "fire", "body"), RuneCrafting.RECIPES.filter { !it.pureOnly }.map { it.key })
    }

    @Test
    fun `every altar has one plain tiara recipe, and one gold tiara recipe for the elemental or catalytic tiara`() {
        assertEquals(26, RuneCrafting.TIARAS.size)
        val keys = RuneCrafting.TIARAS.map { it.altar to it.talisman }
        assertEquals(keys.size, keys.toSet().size)
    }
}
