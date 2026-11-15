package osrsps.game.skill.hunter

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom
import osrsps.game.skill.SuccessRoll

/** Hunter step 1's data against the Wiki: each bird's catch chance at levels 1 and 99, its XP, and the trap count. */
class BirdSnaresTest {
    /** Each bird page's "catch rate is A% at lvl 1 and B% at lvl 99" (Mod Ash), with its level and XP. */
    private val wiki = mapOf(
        BirdSnares.CRIMSON_SWIFT to listOf(39, 164, 1, 340),
        BirdSnares.GOLDEN_WARBLER to listOf(36, 157, 5, 470),
        BirdSnares.COPPER_LONGTAIL to listOf(33, 152, 9, 612),
        BirdSnares.CERULEAN_TWITCH to listOf(32, 149, 11, 645),
        BirdSnares.TROPICAL_WAGTAIL to listOf(29, 144, 19, 952),
    )

    @Test
    fun `each bird's pair gives the Wiki's catch chance at 1 and 99, its level and its XP`() {
        assertEquals(wiki.keys, BirdSnares.BIRDS.map { it.npc }.toSet())
        for (bird in BirdSnares.BIRDS) {
            val (at1, at99, level, xp) = wiki.getValue(bird.npc)
            // The Wiki's percentages are the unclamped formula, (1 + low) / 256 and (1 + high) / 256, to within a point
            // (Mod Ash's figures round some down and some to nearest).
            val got1 = (1 + bird.low) * 100.0 / 256
            val got99 = (1 + bird.high) * 100.0 / 256
            assertTrue(abs(got1 - at1) < 1, "${bird.npc} at 1: $got1% against $at1%")
            assertTrue(abs(got99 - at99) < 1, "${bird.npc} at 99: $got99% against $at99%")
            assertEquals(level, bird.level)
            assertEquals(xp, bird.xpTenths)
        }
    }

    @Test
    fun `a crimson swift is caught about 39 times in 100 at level 1, and every time at 99`() {
        val random = GameRandom(42)
        val swift = BirdSnares.BIRDS.first { it.npc == BirdSnares.CRIMSON_SWIFT }
        val caught = (1..20_000).count { SuccessRoll.roll(random, swift.low, swift.high, 1) }
        assertTrue(caught in 7_600..8_200, "$caught of 20000")
        assertEquals(256, SuccessRoll.chance(swift.low, swift.high, 99))
    }

    @Test
    fun `two traps from level 1, three at 40, four at 60, five at 80`() {
        val counts = listOf(1, 39, 40, 59, 60, 79, 80, 99).map { Traps.maxTrapsAt(it) }
        assertEquals(listOf(2, 2, 3, 3, 4, 4, 5, 5), counts)
    }

    @Test
    fun `every bird has its own struggling and caught snare, and the kind owns them all`() {
        val locs = BirdSnares.BIRDS.flatMap { listOf(it.catching, it.full) }
        assertEquals(10, locs.toSet().size)
        assertTrue(locs.all(BirdSnares.KIND::owns))
        assertTrue(listOf(BirdSnares.SET, BirdSnares.FAILING, BirdSnares.BROKEN).all(BirdSnares.KIND::owns))
    }
}
