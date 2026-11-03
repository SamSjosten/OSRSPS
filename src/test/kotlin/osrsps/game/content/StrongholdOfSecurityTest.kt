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
    private fun leaves(list: List<String>): List<ZoneLoc> = list.joinToString(", ").split(", ").map { entry ->
        val (id, x, z, angle) = entry.split(' ').map(String::toInt)
        ZoneLoc(id, LocShape.WALL_STRAIGHT, angle, x, z, 0)
    }
    private val leaves = leaves(DOORS)
    private val famineLeaves = leaves(FAMINE_DOORS)
    private val pestLeaves = leaves(PEST_DOORS)
    private val deathLeaves = leaves(DEATH_DOORS)
    private var byTile = leaves.associateBy { it.x to it.z }

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
        assertEquals(76, famineLeaves.size)
        oneAskingSide(leaves)
        byTile = famineLeaves.associateBy { it.x to it.z }
        oneAskingSide(famineLeaves)
        assertEquals(60, pestLeaves.size)
        byTile = pestLeaves.associateBy { it.x to it.z }
        oneAskingSide(pestLeaves)
        assertEquals(72, deathLeaves.size)
        byTile = deathLeaves.associateBy { it.x to it.z }
        oneAskingSide(deathLeaves)
    }

    @Test
    fun `a pair of a west and an east edge three tiles apart asks only from its passage`() {
        byTile = famineLeaves.associateBy { it.x to it.z }
        // 1997,5215 on its west edge and 1999,5215 on its east edge: the passage is x 1997 to 1999.
        val west = byTile.getValue(1997 to 5215)
        val east = byTile.getValue(1999 to 5215)
        assertFalse(inPassage(west, 1996, 5215))
        assertTrue(inPassage(west, 1997, 5215))
        assertTrue(inPassage(east, 1999, 5215))
        assertFalse(inPassage(east, 2000, 5215))
        assertEquals(Tile(2000, 5215), through(east, 1999, 5215))
        assertEquals(Tile(1999, 5216), through(east, 2000, 5216))
    }

    private fun oneAskingSide(all: List<ZoneLoc>) {
        for (door in all) {
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

    @Test
    fun `each floor's reward, portal and doors are its own`() {
        val (war, famine, pit, death) = StrongholdOfSecurity.FLOORS
        assertEquals(StrongholdOfSecurity.VARBIT_FLAP, war.varbit)
        assertEquals(StrongholdOfSecurity.VARBIT_SLAP_HEAD, famine.varbit)
        assertEquals(StrongholdOfSecurity.VARBIT_IDEA, pit.varbit)
        assertEquals(StrongholdOfSecurity.VARBIT_STAMP, death.varbit)
        // The Wiki: each portal at 26, 51 and 76 "even if the reward hasn't been claimed before"; the fourth's only once
        // the Cradle is searched.
        assertEquals(listOf(26, 51, 76, null), StrongholdOfSecurity.FLOORS.map { it.portalLevel })
        // 10,000 coins in all, the Cradle giving boots instead.
        assertEquals(listOf(2000, 3000, 5000, 0), StrongholdOfSecurity.FLOORS.map { it.coins })
        assertEquals(10_000, StrongholdOfSecurity.FLOORS.sumOf { it.coins })
        assertEquals(listOf(false, false, true, false), StrongholdOfSecurity.FLOORS.map { it.restoresAll })
        assertEquals(listOf(false, false, false, true), StrongholdOfSecurity.FLOORS.map { it.boots })
        assertTrue(death.contains(0, 2344, 5214))
        assertFalse(death.contains(0, 2144, 5280))
        assertTrue(death.doors.intersect(war.doors + famine.doors + pit.doors).isEmpty())
        assertEquals(setOf(StrongholdOfSecurity.PORTAL_OF_DEATH_LEFT, StrongholdOfSecurity.PORTAL_OF_DEATH_RIGHT), deathLeaves.map { it.id }.toSet())
        assertTrue(famine.contains(0, 2021, 5215))
        assertFalse(famine.contains(0, 1907, 5222))
        assertTrue(pit.contains(0, 2144, 5280))
        assertFalse(pit.contains(0, 2021, 5215))
        assertTrue(war.doors.intersect(famine.doors).isEmpty())
        assertTrue(pit.doors.intersect(war.doors + famine.doors).isEmpty())
        assertEquals(setOf(StrongholdOfSecurity.OOZING_BARRIER_LEFT, StrongholdOfSecurity.OOZING_BARRIER_RIGHT), pestLeaves.map { it.id }.toSet())
    }

    private companion object {
        /** Portal of Death leaves (id, x, z, angle), as the 241 cache's map square 36,81 places them. */
        val DEATH_DOORS = listOf(
        "23727 2308 5225 0, 23727 2309 5205 0, 23727 2311 5225 0, 23727 2312 5205 0, 23727 2315 5187 0, 23727 2318 5187 0",
        "23727 2320 5212 1, 23727 2320 5215 1, 23727 2323 5188 3, 23727 2323 5191 3, 23727 2323 5240 3, 23727 2323 5243 3",
        "23727 2333 5194 0, 23727 2333 5227 0, 23727 2333 5238 0, 23727 2336 5194 0, 23727 2336 5227 0, 23727 2336 5238 0",
        "23727 2341 5221 1, 23727 2341 5224 1, 23727 2344 5188 0, 23727 2347 5188 0, 23727 2352 5245 2, 23727 2355 5245 2",
        "23727 2356 5218 1, 23727 2356 5221 1, 23727 2359 5189 0, 23727 2359 5232 3, 23727 2359 5235 3, 23727 2360 5194 0",
        "23727 2361 5204 3, 23727 2361 5207 3, 23727 2362 5189 0, 23727 2363 5194 0, 23727 2366 5218 1, 23727 2366 5221 1",
        "23728 2308 5224 0, 23728 2309 5204 0, 23728 2311 5224 0, 23728 2312 5204 0, 23728 2315 5186 0, 23728 2318 5186 0",
        "23728 2319 5212 1, 23728 2319 5215 1, 23728 2324 5188 3, 23728 2324 5191 3, 23728 2324 5240 3, 23728 2324 5243 3",
        "23728 2333 5193 0, 23728 2333 5226 0, 23728 2333 5237 0, 23728 2336 5193 0, 23728 2336 5226 0, 23728 2336 5237 0",
        "23728 2340 5221 1, 23728 2340 5224 1, 23728 2344 5187 0, 23728 2347 5187 0, 23728 2352 5246 2, 23728 2355 5218 1",
        "23728 2355 5221 1, 23728 2355 5246 2, 23728 2359 5188 0, 23728 2360 5193 0, 23728 2360 5232 3, 23728 2360 5235 3",
        "23728 2362 5188 0, 23728 2362 5204 3, 23728 2362 5207 3, 23728 2363 5193 0, 23728 2365 5218 1, 23728 2365 5221 1"
        )

        /** Oozing barrier leaves (id, x, z, angle), as the 241 cache's map square 33,82 places them. */
        val PEST_DOORS = listOf(
        "23653 2124 5288 0, 23653 2127 5288 0, 23653 2130 5293 3, 23653 2130 5296 3, 23653 2132 5257 3, 23653 2132 5260 3",
        "23653 2133 5278 1, 23653 2133 5281 1, 23653 2137 5262 2, 23653 2138 5295 0, 23653 2140 5262 2, 23653 2141 5295 0",
        "23653 2148 5299 3, 23653 2148 5302 3, 23653 2149 5292 0, 23653 2152 5291 2, 23653 2153 5263 2, 23653 2155 5286 3",
        "23653 2155 5289 3, 23653 2156 5263 2, 23653 2162 5287 3, 23653 2162 5290 3, 23653 2163 5275 3, 23653 2163 5278 3",
        "23653 2166 5262 3, 23653 2167 5259 1, 23653 2167 5271 2, 23653 2167 5294 3, 23653 2167 5297 3, 23653 2170 5271 2",
        "23654 2124 5287 0, 23654 2127 5287 0, 23654 2131 5293 3, 23654 2131 5296 3, 23654 2132 5278 1, 23654 2132 5281 1",
        "23654 2133 5257 3, 23654 2133 5260 3, 23654 2137 5263 2, 23654 2138 5294 0, 23654 2140 5263 2, 23654 2141 5294 0",
        "23654 2149 5291 0, 23654 2149 5299 3, 23654 2149 5302 3, 23654 2152 5292 2, 23654 2153 5264 2, 23654 2156 5264 2",
        "23654 2156 5286 3, 23654 2156 5289 3, 23654 2163 5287 3, 23654 2163 5290 3, 23654 2164 5275 3, 23654 2164 5278 3",
        "23654 2166 5259 1, 23654 2167 5262 3, 23654 2167 5272 2, 23654 2168 5294 3, 23654 2168 5297 3, 23654 2170 5272 2"
        )

        /** Rickety door leaves (id, x, z, angle), as the 241 cache's map square 31,81 places them. */
        val FAMINE_DOORS = listOf(
        "17009 1994 5196 1, 17009 1995 5194 3, 17009 1997 5215 0, 17009 1999 5216 2, 17009 2004 5194 1, 17009 2005 5192 3",
        "17009 2005 5237 1, 17009 2006 5215 0, 17009 2006 5235 3, 17009 2008 5216 2, 17009 2013 5242 1, 17009 2014 5240 3",
        "17009 2016 5227 0, 17009 2018 5228 2, 17009 2019 5242 1, 17009 2020 5202 1, 17009 2020 5240 3, 17009 2021 5200 3",
        "17009 2026 5241 1, 17009 2027 5239 3, 17009 2031 5198 1, 17009 2031 5227 1, 17009 2032 5196 3, 17009 2032 5225 3",
        "17009 2033 5210 1, 17009 2034 5185 0, 17009 2034 5208 3, 17009 2036 5186 2, 17009 2036 5203 1, 17009 2037 5201 3",
        "17009 2037 5244 0, 17009 2039 5245 2, 17009 2040 5222 0, 17009 2042 5223 2, 17009 2044 5239 1, 17009 2045 5197 1",
        "17009 2045 5237 3, 17009 2046 5195 3, 17100 1994 5194 3, 17100 1995 5196 1, 17100 1997 5216 0, 17100 1999 5215 2",
        "17100 2004 5192 3, 17100 2005 5194 1, 17100 2005 5235 3, 17100 2006 5216 0, 17100 2006 5237 1, 17100 2008 5215 2",
        "17100 2013 5240 3, 17100 2014 5242 1, 17100 2016 5228 0, 17100 2018 5227 2, 17100 2019 5240 3, 17100 2020 5200 3",
        "17100 2020 5242 1, 17100 2021 5202 1, 17100 2026 5239 3, 17100 2027 5241 1, 17100 2031 5196 3, 17100 2031 5225 3",
        "17100 2032 5198 1, 17100 2032 5227 1, 17100 2033 5208 3, 17100 2034 5186 0, 17100 2034 5210 1, 17100 2036 5185 2",
        "17100 2036 5201 3, 17100 2037 5203 1, 17100 2037 5245 0, 17100 2039 5244 2, 17100 2040 5223 0, 17100 2042 5222 2",
        "17100 2044 5237 3, 17100 2045 5195 3, 17100 2045 5239 1, 17100 2046 5197 1"
        )

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
