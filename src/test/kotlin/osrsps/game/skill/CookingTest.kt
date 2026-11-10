package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom

class CookingTest {
    /** Points from the Wiki's burn charts (read 2026-10-29), as 256ths cooked: on a fire or range, then on the Lumbridge range. */
    private val charts = mapOf(
        Cooking.SHRIMPS to (mapOf(1 to 129, 10 to 164, 20 to 203, 30 to 243, 32 to 250, 33 to 254, 34 to 256) to
            mapOf(1 to 139, 10 to 175, 20 to 215, 25 to 235, 26 to 240, 29 to 252, 30 to 256)),
        Cooking.SARDINE to (mapOf(1 to 119, 6 to 138, 11 to 157, 35 to 249, 36 to 253, 37 to 256) to
            mapOf(1 to 129, 6 to 149, 11 to 168, 32 to 250, 33 to 254, 34 to 256)),
        Cooking.HERRING to (mapOf(1 to 109, 5 to 124, 10 to 142, 15 to 161, 39 to 250, 40 to 254, 41 to 256) to
            mapOf(1 to 119, 5 to 134, 10 to 153, 15 to 172, 35 to 249, 36 to 253, 37 to 256)),
        Cooking.TROUT to (mapOf(1 to 89, 6 to 107, 11 to 124, 15 to 138, 20 to 156, 25 to 173, 47 to 250, 48 to 254, 49 to 256) to
            mapOf(1 to 99, 6 to 117, 11 to 135, 15 to 150, 20 to 168, 25 to 186, 43 to 251, 44 to 254, 45 to 256)),
        Cooking.PIKE to (mapOf(1 to 79, 6 to 96, 11 to 113, 20 to 144, 25 to 161, 30 to 178, 51 to 249, 52 to 253, 53 to 256) to
            mapOf(1 to 89, 6 to 107, 11 to 124, 20 to 156, 25 to 173, 30 to 191, 47 to 250, 48 to 254, 49 to 256)),
        Cooking.SALMON to (mapOf(1 to 69, 6 to 86, 11 to 102, 25 to 148, 30 to 165, 35 to 181, 56 to 251, 57 to 254, 58 to 256) to
            mapOf(1 to 79, 6 to 96, 11 to 112, 25 to 158, 30 to 175, 35 to 191, 53 to 251, 54 to 254, 55 to 256)),
    )

    @Test
    fun `each food's pairs give the Wiki's burn charts`() {
        for ((food, both) in charts) {
            val (fire, lumbridge) = both
            for ((level, chance) in fire) {
                assertEquals(chance, SuccessRoll.chance(food.low, food.high, level).coerceAtMost(256), "${food.raw} on a fire at $level")
            }
            for ((level, chance) in lumbridge) {
                assertEquals(chance, SuccessRoll.chance(food.lumbridgeLow, food.lumbridgeHigh, level).coerceAtMost(256), "${food.raw} at Lumbridge at $level")
            }
        }
    }

    @Test
    fun `every raw food is listed once, and only bread needs a range`() {
        // Karambwan alone cooks two ways from one raw fish.
        assertEquals(Cooking.FOODS.size - 1, Cooking.FOODS.map { it.raw }.toSet().size)
        assertEquals(listOf(Cooking.KARAMBWAN, Cooking.POISON_KARAMBWAN), Cooking.FOODS.filter { it.raw == 3142 })
        assertEquals(Cooking.FOODS.size, Cooking.FOODS.map { it.raw to it.cooked }.toSet().size)
        assertEquals(listOf(Cooking.BREAD), Cooking.FOODS.filter { it.rangeOnly })
        for (food in Cooking.FOODS) assertTrue(food.raw != food.cooked && food.cooked != food.burnt, "${food.raw}")
    }

    @Test
    fun `lobster, tuna, swordfish and shark have the Wiki's levels, XP and burn pairs, fire and range`() {
        // The Wiki's Lobster, Tuna, Swordfish and Shark pages (read 2026-11-10): their charts' pairs, fire then range.
        val foods = listOf(Cooking.LOBSTER, Cooking.TUNA, Cooking.SWORDFISH, Cooking.SHARK)
        assertEquals(listOf(40, 30, 45, 80), foods.map { it.level })
        assertEquals(listOf(1200, 1000, 1400, 2100), foods.map { it.xpTenths })
        assertEquals(listOf(38 to 332, 58 to 372, 18 to 292, 1 to 202), foods.map { it.low to it.high })
        assertEquals(listOf(38 to 332, 58 to 372, 30 to 310, 1 to 232), foods.map { it.rangeLow to it.rangeHigh })
        // Not on the Lumbridge range's list: it cooks them as a range does.
        assertEquals(foods.map { it.rangeLow to it.rangeHigh }, foods.map { it.lumbridgeLow to it.lumbridgeHigh })
        assertEquals(listOf(381, 367, 375, 387), foods.map { it.burnt })
        // The Wiki's stop-burn levels: lobster 74 and tuna 63 anywhere; swordfish 86 on a fire and 80 on a range; shark never.
        fun stops(low: Int, high: Int) = (1..99).firstOrNull { SuccessRoll.chance(low, high, it) >= 256 }
        assertEquals(listOf(74, 63, 86, null), foods.map { stops(it.low, it.high) })
        assertEquals(80, stops(Cooking.SWORDFISH.rangeLow, Cooking.SWORDFISH.rangeHigh))
        assertEquals(null, stops(Cooking.SHARK.rangeLow, Cooking.SHARK.rangeHigh))
    }

    @Test
    fun `seeded cooking burns lobsters and sharks as often as the charts say, a range less than a fire`() {
        val random = GameRandom(99)
        for ((low, high, level) in listOf(Triple(38, 332, 40), Triple(1, 202, 80), Triple(1, 232, 80), Triple(18, 292, 60), Triple(30, 310, 60))) {
            val rolls = 100_000
            val cooked = (1..rolls).count { SuccessRoll.roll(random, low, high, level) }
            val expected = SuccessRoll.chance(low, high, level) / 256.0
            assertTrue(kotlin.math.abs(cooked.toDouble() / rolls - expected) < 0.006, "$low/$high at $level: ${cooked.toDouble() / rolls}, the chart's $expected")
        }
    }

    @Test
    fun `mackerel, cod and bass have the Wiki's levels, XP and burn pairs, mackerel and cod helped by the Lumbridge range`() {
        val foods = listOf(Cooking.MACKEREL, Cooking.COD, Cooking.BASS)
        assertEquals(listOf(10, 18, 43), foods.map { it.level })
        assertEquals(listOf(600, 750, 1300), foods.map { it.xpTenths })
        assertEquals(listOf(98 to 452, 83 to 422, 33 to 312), foods.map { it.low to it.high })
        assertEquals(listOf(98 to 452, 88 to 432, 33 to 312), foods.map { it.rangeLow to it.rangeHigh })
        assertEquals(listOf(108 to 472, 93 to 442, 33 to 312), foods.map { it.lumbridgeLow to it.lumbridgeHigh })
        assertEquals(listOf(357, 343, 367), foods.map { it.burnt })
        // The Wiki's stop-burn levels: mackerel 45 (41 at Lumbridge), cod 51 on a fire and 49 on a range. Its text says
        // bass 80 and cod 46 at Lumbridge, a level off from where its own charts' pairs reach 256 (79 and 47): the
        // pairs are what the charts plot, so they win, as for the Lumbridge range's other foods.
        fun stops(low: Int, high: Int) = (1..99).first { SuccessRoll.chance(low, high, it) >= 256 }
        assertEquals(listOf(45, 51, 79), foods.map { stops(it.low, it.high) })
        assertEquals(49, stops(Cooking.COD.rangeLow, Cooking.COD.rangeHigh))
        assertEquals(listOf(41, 47), listOf(Cooking.MACKEREL, Cooking.COD).map { stops(it.lumbridgeLow, it.lumbridgeHigh) })
    }

    @Test
    fun `karambwan cooks thoroughly from 30 and poorly from 1, both by the Wiki's one chart, burning to burnt karambwan`() {
        val ways = listOf(Cooking.KARAMBWAN, Cooking.POISON_KARAMBWAN)
        assertEquals(listOf(3144, 3146), ways.map { it.cooked })
        assertEquals(listOf(3148, 3148), ways.map { it.burnt })
        assertEquals(listOf(30, 1), ways.map { it.level })
        assertEquals(listOf(1900, 800), ways.map { it.xpTenths })
        for (food in ways) {
            assertEquals(70 to 255, food.low to food.high)
            assertEquals(70 to 255, food.rangeLow to food.rangeHigh)
            assertEquals(70 to 255, food.lumbridgeLow to food.lumbridgeHigh)
        }
        // The Wiki: karambwan stops burning only at 99.
        assertEquals(254, SuccessRoll.chance(70, 255, 98))
        assertEquals(256, SuccessRoll.chance(70, 255, 99))
        assertEquals(71, SuccessRoll.chance(70, 255, 1))
    }

    @Test
    fun `seeded cooking burns karambwan as often as its chart says`() {
        val random = GameRandom(7)
        val tries = 100_000
        for (level in listOf(1, 30, 65)) {
            val cooked = (1..tries).count { SuccessRoll.roll(random, 70, 255, level) }
            val expected = SuccessRoll.chance(70, 255, level) / 256.0
            assertTrue(kotlin.math.abs(cooked.toDouble() / tries - expected) < 0.006, "level $level: ${cooked.toDouble() / tries}, chart $expected")
        }
    }
}
