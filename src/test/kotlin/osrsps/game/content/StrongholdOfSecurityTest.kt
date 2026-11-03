package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Edgeville step 12: where a Gate of War takes a player, which door of a pair is the second (it may ask), and the
 * security questions' shape. The door list is every Gate of War leaf in the 241 cache's Vault of War (map square 29,81).
 */
class StrongholdOfSecurityTest {
    private val leaves: List<ZoneLoc> = DOORS.joinToString(", ").split(", ").map { entry ->
        val (id, x, z, angle) = entry.split(' ').map(String::toInt)
        ZoneLoc(id, LocShape.WALL_STRAIGHT, angle, x, z, 0)
    }
    private val byTile = leaves.associateBy { it.x to it.z }

    private fun leafAt(door: ZoneLoc, across: Int, along: Int): ZoneLoc? {
        val alongX = door.angle == 1 || door.angle == 3
        val tile = if (alongX) along to across else across to along
        return byTile[tile]?.takeIf { (it.angle and 1) == (door.angle and 1) }
    }

    private fun inPassage(door: ZoneLoc, x: Int, z: Int) =
        StrongholdOfSecurity.inPassage(door, x, z) { across, along -> leafAt(door, across, along)?.let(StrongholdOfSecurity::crossing) }

    private fun through(door: ZoneLoc, x: Int, z: Int): Tile? {
        val alongX = door.angle == 1 || door.angle == 3
        val doorAcross = if (alongX) door.z else door.x
        return StrongholdOfSecurity.through(door, x, z) { along -> leafAt(door, doorAcross, along)?.angle == door.angle }
    }

    @Test
    fun `every leaf in the cache is the second door of its pair from exactly one side`() {
        assertEquals(72, leaves.size)
        for (door in leaves) {
            val line = StrongholdOfSecurity.crossing(door)
            val alongX = door.angle == 1 || door.angle == 3
            val sides = listOf(line - 1, line).map { across ->
                if (alongX) inPassage(door, door.x, across) else inPassage(door, across, door.z)
            }
            assertEquals(1, sides.count { it }, "$door: $sides")
        }
    }

    @Test
    fun `a pair east to west asks only from the passage between its walls`() {
        val first = byTile.getValue(1904 to 5203)
        val second = byTile.getValue(1907 to 5203)
        assertFalse(inPassage(first, 1903, 5203))
        assertTrue(inPassage(first, 1904, 5203))
        assertTrue(inPassage(second, 1906, 5203))
        assertFalse(inPassage(second, 1907, 5203))
        assertEquals(Tile(1904, 5203), through(first, 1903, 5203))
        assertEquals(Tile(1903, 5203), through(first, 1904, 5203))
        assertEquals(Tile(1907, 5203), through(second, 1906, 5203))
    }

    @Test
    fun `a pair north to south of a south and a north edge, four tiles apart, asks only from its passage`() {
        // 1890,5208 on its south edge and 1890,5211 on its north edge: the passage is z 5208 to 5211.
        val south = byTile.getValue(1890 to 5208)
        val north = byTile.getValue(1890 to 5211)
        assertTrue(inPassage(south, 1890, 5208))
        assertFalse(inPassage(south, 1890, 5207))
        assertTrue(inPassage(north, 1890, 5211))
        assertFalse(inPassage(north, 1890, 5212))
        assertEquals(Tile(1890, 5207), through(south, 1890, 5208))
        assertEquals(Tile(1890, 5212), through(north, 1890, 5211))
        assertEquals(Tile(1890, 5211), through(north, 1890, 5212))
    }

    @Test
    fun `a door takes a player in the other leaf's row through that leaf, and nobody from further off`() {
        val left = byTile.getValue(1904 to 5203)
        // From the right leaf's row (5204): through in that row.
        assertEquals(Tile(1904, 5204), through(left, 1903, 5204))
        // From the row south of the pair, where no leaf is: through the clicked leaf's row.
        assertEquals(Tile(1904, 5203), through(left, 1903, 5202))
        assertNull(through(left, 1902, 5203))
        assertNull(through(left, 1903, 5205))
    }

    @Test
    fun `the questions are the Wiki's 33, eight of them with two answers, each with a reply per answer`() {
        val questions = StrongholdLines.QUESTIONS
        assertEquals(33, questions.size)
        assertEquals(8, questions.count { it.options.size == 2 })
        for (question in questions) {
            assertEquals(question.options.size, question.replies.size)
            assertTrue(question.right in 1..question.options.size, "question ${question.number}")
        }
        assertEquals("stronghold_of_security.q12_reply3", questions[11].replies[2].key)
    }

    @Test
    fun `the floor's locs work only where the cache has them`() {
        assertTrue(StrongholdOfSecurity.isAt(ZoneLoc(StrongholdOfSecurity.GIFT_OF_PEACE, LocShape.CENTREPIECE_STRAIGHT, 0, 1907, 5222, 0), StrongholdOfSecurity.GIFT_OF_PEACE_TILE))
        assertFalse(StrongholdOfSecurity.isAt(ZoneLoc(StrongholdOfSecurity.GIFT_OF_PEACE, LocShape.CENTREPIECE_STRAIGHT, 0, 1907, 5222, 1), StrongholdOfSecurity.GIFT_OF_PEACE_TILE))
        assertTrue(StrongholdOfSecurity.inVaultOfWar(0, 1859, 5244))
        assertFalse(StrongholdOfSecurity.inVaultOfWar(0, 1859, 5300))
    }

    private companion object {
        /** Gate of War leaves (id, x, z, angle), as the 241 cache's map square 29,81 places them. */
        val DOORS = listOf(
        "19206 1858 5235 1, 19206 1858 5238 1, 19206 1860 5195 1, 19206 1860 5198 1, 19206 1860 5209 1, 19206 1860 5212 1",
        "19206 1865 5226 0, 19206 1867 5217 0, 19206 1868 5226 0, 19206 1870 5217 0, 19206 1874 5204 1, 19206 1874 5207 1",
        "19206 1876 5239 0, 19206 1877 5192 3, 19206 1877 5195 3, 19206 1879 5188 0, 19206 1879 5223 3, 19206 1879 5226 3",
        "19206 1879 5239 0, 19206 1882 5188 0, 19206 1884 5243 0, 19206 1886 5235 0, 19206 1887 5243 0, 19206 1889 5211 1",
        "19206 1889 5235 0, 19206 1890 5208 3, 19206 1894 5212 0, 19206 1897 5212 0, 19206 1904 5203 0, 19206 1904 5230 1",
        "19206 1904 5233 1, 19206 1904 5242 0, 19206 1907 5203 0, 19206 1908 5242 0, 19206 1911 5206 1, 19206 1911 5209 1",
        "19207 1859 5235 1, 19207 1859 5238 1, 19207 1861 5195 1, 19207 1861 5198 1, 19207 1861 5209 1, 19207 1861 5212 1",
        "19207 1865 5227 0, 19207 1867 5218 0, 19207 1868 5227 0, 19207 1870 5218 0, 19207 1875 5204 1, 19207 1875 5207 1",
        "19207 1876 5192 3, 19207 1876 5195 3, 19207 1876 5240 0, 19207 1878 5223 3, 19207 1878 5226 3, 19207 1879 5189 0",
        "19207 1879 5240 0, 19207 1882 5189 0, 19207 1884 5244 0, 19207 1886 5236 0, 19207 1887 5244 0, 19207 1889 5208 3",
        "19207 1889 5236 0, 19207 1890 5211 1, 19207 1894 5213 0, 19207 1897 5213 0, 19207 1904 5204 0, 19207 1904 5243 0",
        "19207 1905 5230 1, 19207 1905 5233 1, 19207 1907 5204 0, 19207 1908 5243 0, 19207 1912 5206 1, 19207 1912 5209 1"
        )
    }
}
