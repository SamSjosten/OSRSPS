package osrsps.game.skill.construction

import kotlin.test.Test
import kotlin.test.assertEquals

class StudyTest {
    @Test
    fun `every study piece builds on its own place, with the Wiki's XP`() {
        val xp = mapOf(
            5769 to 60, 5770 to 120, 5771 to 120, 5772 to 180, 5773 to 180, 5774 to 580, 5775 to 580, 5776 to 1800,
            5777 to 180, 5778 to 270, 5779 to 570, 5780 to 570, 5781 to 960, 5782 to 1320, 5783 to 1420,
            5784 to 280, 5785 to 580, 5786 to 890, 5787 to 30, 5788 to 45, 5789 to 60, 5790 to 16, 5791 to 121, 5792 to 181, 5793 to 281,
        )
        assertEquals(xp, xp.keys.associateWith { FurnitureBuilds.BUILDS.getValue(it).xp })
        val places = mapOf(15420 to 5769..5776, 15421 to 5777..5783, 15422 to 5784..5786, 15423 to 5787..5790, 15424 to 5791..5793, 15425 to 5529..5531)
        for ((hotspot, rows) in places) for (row in rows) assertEquals(true, FurnitureBuilds.buildsAt(hotspot, row, RoomType.STUDY), "row $row on $hotspot")
    }

    @Test
    fun `the crystal balls know every elemental staff, battlestaff and mystic staff by element and kind`() {
        assertEquals(12, Study.STAVES.size)
        assertEquals(Study.Element.FIRE to 2, Study.STAVES[1401])
        assertEquals(Study.Element.AIR to 1, Study.STAVES[1397])
        assertEquals(listOf(0, 100, 1000), Study.RUNE_COST)
    }
}
