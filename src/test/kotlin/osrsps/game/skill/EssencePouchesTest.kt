package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EssencePouchesTest {
    private fun pouch(key: String) = EssencePouches.POUCHES.first { it.key == key }

    @Test
    fun `capacities follow the Wiki's degradation table`() {
        val giant = pouch("giant")
        assertEquals(listOf(12, 12, 9, 8, 7, 6, 5, 3, 0), listOf(0, 199, 200, 300, 400, 600, 800, 1000, 1200).map { giant.capacity(it) })
        assertTrue(giant.destroyed(1200))
        assertFalse(giant.destroyed(1199))
        assertEquals(3, pouch("small").capacity(5000))
        assertFalse(pouch("small").destroyed(5000))
        assertEquals(listOf(6, 3, 0), listOf(399, 400, 800).map { pouch("medium").capacity(it) })
        assertEquals(listOf(9, 7, 5, 3, 0), listOf(0, 400, 600, 800, 1000).map { pouch("large").capacity(it) })
    }

    @Test
    fun `the colossal pouch scales with base level`() {
        val colossal = pouch("colossal")
        assertEquals(listOf(8, 16, 27, 40), listOf(25, 50, 75, 85).map { EssencePouches.capacity(colossal, 0, it) })
        assertEquals(35, EssencePouches.capacity(colossal, 320, 99))
        assertTrue(colossal.destroyed(1021))
    }

    @Test
    fun `each pouch has its own vars`() {
        val vars = EssencePouches.POUCHES.flatMap { listOf(it.countVarbit, it.typeVarbit) }
        assertEquals(vars.size, vars.toSet().size)
    }
}
