package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunecraftTest {
    @Test
    fun `Locate's eight ways are 45-degree slices centred on their compass points`() {
        assertEquals(Pull.NORTH, Runecraft.pull(0, 10))
        assertEquals(Pull.NORTH, Runecraft.pull(4, 10)) // 21.8 degrees
        assertEquals(Pull.NORTH_EAST, Runecraft.pull(5, 10)) // 26.6 degrees
        assertEquals(Pull.EAST, Runecraft.pull(10, 0))
        assertEquals(Pull.SOUTH_EAST, Runecraft.pull(10, -10))
        assertEquals(Pull.SOUTH, Runecraft.pull(0, -1))
        assertEquals(Pull.SOUTH_WEST, Runecraft.pull(-10, -10))
        assertEquals(Pull.WEST, Runecraft.pull(-237, 74))
        assertEquals(Pull.NORTH_WEST, Runecraft.pull(-10, 10))
        assertEquals(Pull.NORTH, Runecraft.pull(-4, 10)) // 338.2 degrees
    }

    @Test
    fun `every altar's ids are unique, and the elemental group is air, water, earth and fire`() {
        val altars = Runecraft.ALTARS
        assertEquals(13, altars.size)
        for (ids in listOf(altars.map { it.ruins }, altars.map { it.varbit }, altars.map { it.altar }, altars.map { it.portal }, altars.map { it.talisman }, altars.map { it.tiara })) {
            assertEquals(ids.size, ids.toSet().size, "duplicates in $ids")
        }
        assertEquals(listOf("air", "water", "earth", "fire"), altars.filter { it.elemental }.map { it.key })
        assertTrue(altars.all { it.inside.isNotEmpty() })
    }
}
