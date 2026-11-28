package osrsps.game.skill.construction

import kotlin.test.Test
import kotlin.test.assertEquals

class GamesRoomTest {
    @Test
    fun `a hoop lands 76 times in 256 at Ranged 1 and 241 at 99, the Hoop and stick page's 29_7 and 94_1 percent`() {
        assertEquals(76, GamesRoom.landChance(1))
        assertEquals(241, GamesRoom.landChance(99))
        assertEquals(158, GamesRoom.landChance(50))
        assertEquals(241, GamesRoom.landChance(120), "boosted past 99 stays at 99's")
    }

    @Test
    fun `every games room piece builds on its own place, with the Wiki's XP`() {
        val xp = mapOf(5618 to 360, 5619 to 800, 5620 to 1200, 5621 to 240, 5622 to 660, 5623 to 860, 5624 to 100, 5625 to 200, 5626 to 2000, 5627 to 176, 5628 to 252, 5629 to 356, 5630 to 120, 5631 to 290, 5632 to 600)
        assertEquals(xp, xp.keys.associateWith { FurnitureBuilds.BUILDS.getValue(it).xp })
        for ((hotspot, rows) in mapOf(15342 to 5618..5620, 15343 to 5621..5623, 15344 to 5624..5626, 15345 to 5627..5629, 15346 to 5630..5632)) {
            for (row in rows) assertEquals(true, FurnitureBuilds.buildsAt(hotspot, row, RoomType.GAMES_ROOM), "row $row on $hotspot")
        }
    }
}
