package osrsps.game.skill

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom

/** Gathering 2, step G1: runite, amethyst and gem rocks, the Mining Guild's area and the charged glory, against the Wiki. */
class MiningG1Test {
    private fun rock(key: String) = Mining.ROCKS.values.first { it.type.key == key }.type

    @Test
    fun `runite, amethyst and gem rocks give the Wiki's success charts`() {
        val runite = rock("runite")
        // Runite rocks: 17/256 at 85, 19/256 from 97.
        assertEquals(17, SuccessRoll.chance(runite.low, runite.high, 85))
        assertEquals(19, SuccessRoll.chance(runite.low, runite.high, 97))
        assertEquals(19, SuccessRoll.chance(runite.low, runite.high, 99))
        // Amethyst crystals: about 3.5% at 92 and 4.3% at 99.
        val amethyst = rock("amethyst")
        assertEquals(9, SuccessRoll.chance(amethyst.low, amethyst.high, 92))
        assertEquals(11, SuccessRoll.chance(amethyst.low, amethyst.high, 99))
        // Gem rocks: 28/70, and a charged glory's 84/210 is three times that.
        val gem = rock("gem")
        assertEquals(28 to 70, gem.low to gem.high)
        assertEquals(84 to 210, gem.low * Mining.GLORY_GEM_ROCK_MULTIPLIER to gem.high * Mining.GLORY_GEM_ROCK_MULTIPLIER)
    }

    @Test
    fun `levels, experience and respawns are the Wiki's`() {
        val runite = rock("runite")
        assertEquals(listOf(85, 1250, 451), listOf(runite.level, runite.xpTenths, runite.ore))
        val amethyst = rock("amethyst")
        assertEquals(listOf(92, 2400, 21347, 125, 2, 3), listOf(amethyst.level, amethyst.xpTenths, amethyst.ore, amethyst.respawn, amethyst.minYield, amethyst.maxYield))
        val gem = rock("gem")
        assertEquals(listOf(40, 650, 99), listOf(gem.level, gem.xpTenths, gem.respawn))
        assertTrue(gem.gemRock)
        // 12 minutes; 6 in the Mining Guild (3056, 9721) and the Lava Maze's Wilderness mine (3059, 3885).
        assertEquals(1200, Mining.respawn(runite, 2941, 9884, 0))
        assertEquals(600, Mining.respawn(runite, 3056, 9721, 0))
        assertEquals(600, Mining.respawn(runite, 3059, 3885, 0))
        // The guild halves every rock: its coal (3015, 9722) 25 ticks, but not the free area's coal (3039, 9738).
        val coal = rock("coal")
        assertEquals(25, Mining.respawn(coal, 3015, 9722, 0))
        assertEquals(50, Mining.respawn(coal, 3039, 9738, 0))
        // Amethyst grows only in the guild: the Wiki's 75 seconds is its time there.
        assertEquals(125, Mining.respawn(amethyst, 3017, 9701, 0))
    }

    @Test
    fun `the guild's members' area holds every members' rock the Wiki lists and none of the free area's`() {
        // Corners of the Wiki's rock tiles: amethyst 3001-3029 x 9698-9727, runite 3054-3056, adamantite 3030-3047 x 9713-9726.
        for ((x, z) in listOf(3001 to 9711, 3029 to 9701, 3023 to 9698, 3006 to 9727, 3056 to 9721, 3054 to 9725, 3015 to 9722, 3047 to 9718)) {
            assertTrue(Mining.inGuild(x, z, 0), "$x, $z")
        }
        // The free area starts at 9732; the Dwarven Mine is further north; the guild above ground is on another map.
        for ((x, z) in listOf(3034 to 9732, 3040 to 9736, 3046 to 9733, 3037 to 9763, 3030 to 3348)) {
            assertTrue(!Mining.inGuild(x, z, 0), "$x, $z")
        }
        assertTrue(!Mining.inGuild(3056, 9721, 1))
    }

    @Test
    fun `a gem rock's table is 128ths, as the Wiki's, and a seeded run lands on it`() {
        assertEquals(128, Mining.GEM_ROCK_TABLE.sumOf { it.weight })
        assertEquals(Mining.GEM_ROCK_TABLE.map { it.obj }.toSet(), Mining.GEM_ROCK_LINES.keys)
        val random = GameRandom(11)
        val picks = 128_000
        val counts = HashMap<Int, Int>()
        repeat(picks) { counts.merge(BirdNests.pick(random, Mining.GEM_ROCK_TABLE), 1, Int::plus) }
        for (entry in Mining.GEM_ROCK_TABLE) {
            val expected = picks * entry.weight / 128.0
            // Within 4 standard deviations of the Wiki's share.
            val sd = Math.sqrt(expected * (1 - entry.weight / 128.0))
            assertTrue(abs(counts.getValue(entry.obj) - expected) < 4 * sd, "gem ${entry.obj}: ${counts[entry.obj]} vs $expected")
        }
    }

    @Test
    fun `a seeded run of runite rolls at 99 lands on the Wiki's 19 in 256`() {
        val runite = rock("runite")
        val random = GameRandom(5)
        val rolls = 256_000
        val hits = (1..rolls).count { SuccessRoll.roll(random, runite.low, runite.high, 99) }
        val expected = rolls * 19 / 256.0
        assertTrue(abs(hits - expected) < 4 * Math.sqrt(expected), "$hits vs $expected")
    }

    @Test
    fun `charged glories are every charged amulet of glory, and not the uncharged one`() {
        assertEquals(13, Mining.CHARGED_GLORIES.size)
        assertTrue(1704 !in Mining.CHARGED_GLORIES)
        assertTrue(10362 !in Mining.CHARGED_GLORIES)
    }
}
