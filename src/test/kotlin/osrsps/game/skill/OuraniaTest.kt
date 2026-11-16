package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OuraniaTest {
    @Test
    fun `each band's thresholds rise to 10000, one for each rune`() {
        assertEquals(11, Ourania.BANDS.size)
        for (band in Ourania.BANDS) {
            assertEquals(Ourania.RUNES.size, band.size)
            assertEquals(Ourania.RANGE, band.last())
            assertTrue(band.zipWithNext().all { (a, b) -> a < b }, "rising: $band")
        }
    }

    @Test
    fun `Mod Ash's level 20 example and the bands' edges`() {
        val band = Ourania.BANDS[Ourania.band(20)]
        // 0-7 a soul rune, 8-22 blood, 23-54 death, 55-109 law.
        assertEquals(listOf(8, 23, 55, 110), band.take(4))
        assertEquals(listOf(0, 0, 1, 9, 9, 10, 10), listOf(1, 9, 10, 98, 90, 99, 120).map { Ourania.band(it) })
    }
}
