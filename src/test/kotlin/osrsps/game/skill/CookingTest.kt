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
        assertEquals(
            listOf(
                Cooking.BREAD, Cooking.REDBERRY_PIE, Cooking.MEAT_PIE, Cooking.MUD_PIE, Cooking.APPLE_PIE, Cooking.GARDEN_PIE,
                Cooking.FISH_PIE, Cooking.BOTANICAL_PIE, Cooking.MUSHROOM_PIE, Cooking.ADMIRAL_PIE, Cooking.DRAGONFRUIT_PIE,
                Cooking.WILD_PIE, Cooking.SUMMER_PIE, Cooking.PIZZA, Cooking.CAKE, Cooking.PITTA,
            ),
            Cooking.FOODS.filter { it.rangeOnly },
        )
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

    @Test
    fun `monkfish and anglerfish have the Wiki's levels, XP and burn pairs, a range burning less than a fire`() {
        val foods = listOf(Cooking.MONKFISH, Cooking.ANGLERFISH)
        assertEquals(listOf(7946 to 7948, 13441 to 13443), foods.map { it.cooked to it.burnt })
        assertEquals(listOf(62, 84), foods.map { it.level })
        assertEquals(listOf(1500, 2300), foods.map { it.xpTenths })
        assertEquals(listOf(11 to 275, 1 to 200), foods.map { it.low to it.high })
        assertEquals(listOf(13 to 280, 1 to 220), foods.map { it.rangeLow to it.rangeHigh })
        assertEquals(listOf(13 to 280, 1 to 220), foods.map { it.lumbridgeLow to it.lumbridgeHigh })
        // The Wiki's Monkfish page: it stops burning at 92 on a fire and 90 on a range. Anglerfish never stops (its high
        // pairs stay under 256).
        fun stops(low: Int, high: Int) = (1..99).firstOrNull { SuccessRoll.chance(low, high, it) >= 256 }
        assertEquals(92, stops(11, 275))
        assertEquals(90, stops(13, 280))
        assertEquals(null, stops(1, 200))
        assertEquals(null, stops(1, 220))
    }

    @Test
    fun `pies, pizza, cake and pitta bake from the Wiki's levels and XP, and stop burning where their charts do`() {
        val bakes = listOf(
            Cooking.REDBERRY_PIE, Cooking.MEAT_PIE, Cooking.MUD_PIE, Cooking.APPLE_PIE, Cooking.GARDEN_PIE, Cooking.FISH_PIE,
            Cooking.BOTANICAL_PIE, Cooking.MUSHROOM_PIE, Cooking.ADMIRAL_PIE, Cooking.DRAGONFRUIT_PIE, Cooking.WILD_PIE,
            Cooking.SUMMER_PIE, Cooking.PIZZA, Cooking.CAKE, Cooking.PITTA,
        )
        assertEquals(listOf(10, 20, 29, 30, 34, 47, 52, 60, 70, 73, 85, 95, 35, 40, 58), bakes.map { it.level })
        assertEquals(listOf(780, 1100, 1280, 1300, 1380, 1640, 1800, 2000, 2100, 2200, 2400, 2600, 1430, 1800, 400), bakes.map { it.xpTenths })
        assertEquals(List(12) { Cooking.BURNT_PIE } + listOf(2305, 1903, 1867), bakes.map { it.burnt })
        assertEquals(listOf(Cooking.CAKE), bakes.filter { it.returns == Cooking.CAKE_TIN })
        fun stops(low: Int, high: Int) = (1..99).firstOrNull { SuccessRoll.chance(low, high, it) >= 256 }
        // The pages' text: redberry 45, mud and apple 63, garden and pizza 68, fish and cake 74, botanical 84, admiral 94,
        // pitta 37; dragonfruit, wild and summer never. Meat pie's and mushroom pie's charts reach 256 a level before the
        // text's 54 and 89; the charts' pairs stand.
        assertEquals(
            listOf(45, 53, 63, 63, 68, 74, 84, 88, 94, null, null, null, 68, 74, 37),
            bakes.map { stops(it.rangeLow, it.rangeHigh) },
        )
        // The Lumbridge range helps the four on its list: redberry stops at 42 and meat at 49 there; botanical and
        // mushroom never burn there from the level they need.
        assertEquals(listOf(42, 49, 42, 38), listOf(Cooking.REDBERRY_PIE, Cooking.MEAT_PIE, Cooking.BOTANICAL_PIE, Cooking.MUSHROOM_PIE).map {
            stops(it.lumbridgeLow, it.lumbridgeHigh)
        })
        val helped = setOf(Cooking.REDBERRY_PIE, Cooking.MEAT_PIE, Cooking.BOTANICAL_PIE, Cooking.MUSHROOM_PIE)
        for (food in bakes - helped) assertEquals(food.rangeLow to food.rangeHigh, food.lumbridgeLow to food.lumbridgeHigh)
    }

    @Test
    fun `seeded baking burns pies as often as the charts say`() {
        val random = GameRandom(5)
        // Apple pie at 40, summer pie at 99, mushroom pie at 70 on a range and at Lumbridge.
        for ((low, high, level) in listOf(Triple(58, 372, 40), Triple(1, 212, 99), Triple(17, 285, 70), Triple(140, 450, 60))) {
            val rolls = 100_000
            val baked = (1..rolls).count { SuccessRoll.roll(random, low, high, level) }
            val expected = SuccessRoll.chance(low, high, level) / 256.0
            assertTrue(kotlin.math.abs(baked.toDouble() / rolls - expected) < 0.006, "$low/$high at $level: ${baked.toDouble() / rolls}, the chart's $expected")
        }
    }

    @Test
    fun `the mixes follow the Wiki's recipes, every pie chain ending in a pie that bakes`() {
        val recipes = CookingMixes.PAIRS.flatMap { it.third }.distinct()
        val byKey = recipes.associateBy { it.key }
        // Flour and water: four doughs in each box, the pot and the container back.
        val doughBoxes = CookingMixes.PAIRS.filter { it.first == 1933 && it.second in setOf(1929, 1921, 1937) }
        assertEquals(3, doughBoxes.size)
        for ((_, water, box) in doughBoxes) {
            assertEquals(listOf(CookingMixes.BREAD_DOUGH, CookingMixes.PASTRY_DOUGH, CookingMixes.PIZZA_BASE, CookingMixes.PITTA_DOUGH), box.map { it.product })
            assertEquals(listOf(1, 1, 35, 58), box.map { it.level })
            val container = mapOf(1929 to 1925, 1921 to 1923, 1937 to 1935).getValue(water)
            for (recipe in box) assertEquals(listOf(1931, container), recipe.byproducts.map { it.obj })
        }
        // Only the pizza toppings and the chocolate cake give XP.
        val xp = recipes.filter { it.xpTenths > 0 }.associate { it.key to (it.level to it.xpTenths) }
        assertEquals(
            mapOf(
                "meat_pizza" to (45 to 260), "meat_pizza_chicken" to (45 to 260), "anchovy_pizza" to (55 to 390),
                "pineapple_pizza" to (65 to 450), "pineapple_pizza_chunks" to (65 to 450),
                "chocolate_cake" to (50 to 300), "chocolate_cake_dust" to (50 to 300),
            ),
            xp,
        )
        // Each pie's raw form, from its fillings, is a food the range bakes at the same level.
        val raws = Cooking.FOODS.associateBy { it.raw }
        for (key in listOf("redberry_pie", "meat_pie", "apple_pie", "botanical_pie", "mushroom_pie", "dragonfruit_pie") +
            listOf("mud", "garden", "fish", "admiral", "wild", "summer").map { "${it}_pie_3" }) {
            val recipe = byKey.getValue(key)
            assertEquals(recipe.level, raws.getValue(recipe.product).level, key)
        }
        for (pie in listOf("mud", "garden", "fish", "admiral", "wild", "summer")) {
            val (one, two, three) = (1..3).map { byKey.getValue("${pie}_pie_$it") }
            assertEquals(CookingMixes.PIE_SHELL, one.inputs[0].obj, pie)
            assertEquals(one.product, two.inputs[0].obj, pie)
            assertEquals(two.product, three.inputs[0].obj, pie)
        }
        // The mud pie's compost and water give their buckets back; the cake mix its pot and bucket.
        assertEquals(listOf(1925), byKey.getValue("mud_pie_1").byproducts.map { it.obj })
        assertEquals(listOf(1925), byKey.getValue("mud_pie_2").byproducts.map { it.obj })
        assertEquals(listOf(1931, 1925), byKey.getValue("uncooked_cake").byproducts.map { it.obj })
        assertEquals(listOf(Cooking.CAKE_TIN, 1944, 1933, 1927), byKey.getValue("uncooked_cake").inputs.map { it.obj })
        // No pair is listed twice.
        assertEquals(CookingMixes.PAIRS.size, CookingMixes.PAIRS.map { setOf(it.first, it.second) }.toSet().size)
    }
}
