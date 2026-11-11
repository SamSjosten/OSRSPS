package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.content.DigsiteDigs.Site

/** Misthalin piece 7 step 9b's tables and site maps against the OSRS Wiki (read 2026-11-11). */
class DigsiteDigsTest {
    private fun List<Find>.total() = sumOf { it.weight }

    private fun List<Find>.weightOf(obj: Int) = filter { it.obj == obj }.sumOf { it.weight }

    @Test
    fun `each site gives the Wiki's XP and its finds table adds up to the Wiki's denominator`() {
        assertEquals(listOf(50, 60, 70, 80), Site.entries.map { it.xpTenths })
        assertEquals(listOf(13, 12, 16, 20), Site.entries.map { it.finds.total() })
        assertEquals(listOf(8, 2, 3, 1), Site.entries.map { it.finds.weightOf(-1) })
        // Level 3's ancient talisman is the quest's: 3/20 coins (10 each) take its place.
        val coins = Site.LEVEL_3.finds.single { it.obj == DigsiteDigs.COINS }
        assertEquals(listOf(3, 10, 10), listOf(coins.weight, coins.min, coins.max))
        assertEquals(2, Site.LEVEL_2.finds.weightOf(DigsiteDigs.BONES))
    }

    @Test
    fun `the specimen tray is out of 11 and the muddy panning tray out of 40, with the special cup turned to nothing`() {
        assertEquals(11, DigsiteDigs.TRAY_FINDS.total())
        assertEquals(2, DigsiteDigs.TRAY_FINDS.weightOf(-1))
        assertEquals(40, DigsiteDigs.PANNING_FINDS.total())
        assertEquals(23, DigsiteDigs.PANNING_FINDS.weightOf(-1))
        val coins = DigsiteDigs.PANNING_FINDS.single { it.obj == DigsiteDigs.COINS }
        assertEquals(listOf(4, 1, 10), listOf(coins.weight, coins.min, coins.max))
        assertEquals(listOf(4, 3, 3, 3), listOf(680, 407, 1625, 1627).map { DigsiteDigs.PANNING_FINDS.weightOf(it) })
    }

    @Test
    fun `the site under the player follows the Wiki's maps, on the ground floor only`() {
        assertEquals(Site.TRAINING, DigsiteDigs.siteAt(3368, 3398, 0))
        assertEquals(Site.TRAINING, DigsiteDigs.siteAt(3355, 3398, 0))
        assertEquals(Site.LEVEL_1, DigsiteDigs.siteAt(3368, 3405, 0))
        assertEquals(Site.LEVEL_1, DigsiteDigs.siteAt(3362, 3410, 0))
        assertEquals(Site.LEVEL_2, DigsiteDigs.siteAt(3355, 3428, 0))
        assertEquals(Site.LEVEL_2, DigsiteDigs.siteAt(3368, 3425, 0))
        assertEquals(Site.LEVEL_3, DigsiteDigs.siteAt(3354, 3408, 0))
        assertEquals(Site.LEVEL_3, DigsiteDigs.siteAt(3372, 3439, 0))
        assertEquals(null, DigsiteDigs.siteAt(3383, 3418, 0))
        assertEquals(null, DigsiteDigs.siteAt(3368, 3398, 1))
    }
}
