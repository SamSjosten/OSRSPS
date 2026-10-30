package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertEquals(Cooking.FOODS.size, Cooking.FOODS.map { it.raw }.toSet().size)
        assertEquals(listOf(Cooking.BREAD), Cooking.FOODS.filter { it.rangeOnly })
        for (food in Cooking.FOODS) assertTrue(food.raw != food.cooked && food.cooked != food.burnt, "${food.raw}")
    }
}
