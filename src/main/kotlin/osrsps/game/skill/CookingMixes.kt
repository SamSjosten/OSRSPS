package osrsps.game.skill

import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/** The words for Cooking's mixing (doughs, pie fillings, pizza toppings, cakes), in the dialogue file. */
object CookingMixLines : Lines("cooking_mix", "Cooking") {
    val LEVEL = line("level", "the message when mixing something needs a higher Cooking level", "level", "item")
    val BREAD_DOUGH = line("bread_dough", "Bread dough: the message on mixing flour and water into it")
    val PASTRY_DOUGH = line("pastry_dough", "Pastry dough: the message on mixing flour and water into it")
    val PIZZA_BASE = line("pizza_base", "Pizza base: the message on mixing flour and water into it")
    val PITTA_DOUGH = line("pitta_dough", "Pitta dough: the message on mixing flour and water into it")
    val PIE_SHELL = line("pie_shell", "Pie shell: the message on putting pastry dough in a pie dish")
    val REDBERRY_PIE = line("redberry_pie", "Uncooked berry pie: the message on filling a pie shell with redberries")
    val MEAT_PIE = line("meat_pie", "Uncooked meat pie: the message on filling a pie shell with cooked meat or chicken")
    val APPLE_PIE = line("apple_pie", "Uncooked apple pie: the message on filling a pie shell with a cooking apple")
    val BOTANICAL_PIE = line("botanical_pie", "Uncooked botanical pie: the message on filling a pie shell with a golovanova fruit top")
    val MUSHROOM_PIE = line("mushroom_pie", "Uncooked mushroom pie: the message on filling a pie shell with a sulliuscep cap")
    val DRAGONFRUIT_PIE = line("dragonfruit_pie", "Uncooked dragonfruit pie: the message on filling a pie shell with a dragonfruit")
    val MUD_PIE_1 = line("mud_pie_1", "Part mud pie: the message on putting compost in a pie shell")
    val MUD_PIE_2 = line("mud_pie_2", "Part mud pie: the message on adding a bucket of water")
    val MUD_PIE_3 = line("mud_pie_3", "Raw mud pie: the message on adding clay")
    val GARDEN_PIE_1 = line("garden_pie_1", "Part garden pie: the message on putting a tomato in a pie shell")
    val GARDEN_PIE_2 = line("garden_pie_2", "Part garden pie: the message on adding an onion")
    val GARDEN_PIE_3 = line("garden_pie_3", "Raw garden pie: the message on adding a cabbage")
    val FISH_PIE_1 = line("fish_pie_1", "Part fish pie: the message on putting a trout in a pie shell")
    val FISH_PIE_2 = line("fish_pie_2", "Part fish pie: the message on adding a cod")
    val FISH_PIE_3 = line("fish_pie_3", "Raw fish pie: the message on adding a potato")
    val ADMIRAL_PIE_1 = line("admiral_pie_1", "Part admiral pie: the message on putting a salmon in a pie shell")
    val ADMIRAL_PIE_2 = line("admiral_pie_2", "Part admiral pie: the message on adding a tuna")
    val ADMIRAL_PIE_3 = line("admiral_pie_3", "Raw admiral pie: the message on adding a potato")
    val WILD_PIE_1 = line("wild_pie_1", "Part wild pie: the message on putting raw bear meat in a pie shell")
    val WILD_PIE_2 = line("wild_pie_2", "Part wild pie: the message on adding a raw chompy")
    val WILD_PIE_3 = line("wild_pie_3", "Raw wild pie: the message on adding a raw rabbit")
    val SUMMER_PIE_1 = line("summer_pie_1", "Part summer pie: the message on putting a strawberry in a pie shell")
    val SUMMER_PIE_2 = line("summer_pie_2", "Part summer pie: the message on adding a watermelon")
    val SUMMER_PIE_3 = line("summer_pie_3", "Raw summer pie: the message on adding a cooking apple")
    val INCOMPLETE_PIZZA = line("incomplete_pizza", "Incomplete pizza: the message on adding a tomato to a pizza base")
    val UNCOOKED_PIZZA = line("uncooked_pizza", "Uncooked pizza: the message on adding cheese")
    val MEAT_PIZZA = line("meat_pizza", "Meat pizza: the message on adding cooked meat or chicken to a plain pizza")
    val ANCHOVY_PIZZA = line("anchovy_pizza", "Anchovy pizza: the message on adding anchovies to a plain pizza")
    val PINEAPPLE_PIZZA = line("pineapple_pizza", "Pineapple pizza: the message on adding pineapple to a plain pizza")
    val UNCOOKED_CAKE = line("uncooked_cake", "Uncooked cake: the message on mixing an egg, milk and flour in a cake tin")
    val CHOCOLATE_CAKE = line("chocolate_cake", "Chocolate cake: the message on adding chocolate to a cake")
}

/**
 * Cooking's mixing (step FC5a): flour and water into the four doughs, pie shells and every pie's filling (one at a time
 * for the three-stage pies), the pizza's tomato, cheese and toppings, the cake's mix and its chocolate. Each pair of items
 * used on each other opens the Make-X box over what it makes ([Making], live's skillmulti), or with only one recipe and
 * one to make, makes it straight away. The baking is [Cooking]'s.
 *
 * Sources: the OSRS Wiki's item pages (read 2026-11-10): each step's level, XP (none but the pizza toppings and
 * chocolate cake), materials, what comes back (the pot and the water's container from the doughs, the bucket from the mud
 * pie's compost and water, the pot and bucket from the cake mix) and its ticks (1 for a dough, 2 for the rest).
 * Guessed: the first product a tick after the choice, a single recipe with one to make skipping the box, the words, the
 * flour and water's box listing the doughs in the Wiki's order (bread, pastry, pizza, pitta), and no animation.
 * Out: stews, curries, wine, dairy, potatoes with toppings and kebabs (step FC5b), the Hosidius range.
 */
object CookingMixes {
    private const val POT_OF_FLOUR = 1933
    private const val BUCKET_OF_WATER = 1929
    private const val BOWL_OF_WATER = 1921
    private const val JUG_OF_WATER = 1937
    private const val POT = 1931
    private const val BUCKET = 1925
    private const val BOWL = 1923
    private const val JUG = 1935

    const val BREAD_DOUGH = 2307
    const val PASTRY_DOUGH = 1953
    const val PIZZA_BASE = 2283
    const val PITTA_DOUGH = 1863
    const val PIE_DISH = 2313
    const val PIE_SHELL = 2315
    const val INCOMPLETE_PIZZA = 2285
    const val UNCOOKED_PIZZA = 2287
    const val PLAIN_PIZZA = 2289
    const val EGG = 1944
    const val BUCKET_OF_MILK = 1927
    const val UNCOOKED_CAKE = 1889
    const val CAKE = 1891

    private const val COOKED_MEAT = 2142
    private const val COOKED_CHICKEN = 2140
    private const val TOMATO = 1982
    private const val CHEESE = 1985
    private const val COOKING_APPLE = 1955
    private const val POTATO = 1942

    /** One thing a pair of items makes, at [level] for [xpTenths], [ticks] apart. */
    private fun mix(
        key: String, product: Int, level: Int, xpTenths: Int, inputs: List<Int>, made: Line,
        byproducts: List<Int> = emptyList(), ticks: Int = 2,
    ) = MakeRecipe(
        key, Skill.COOKING, product, 1, level, xpTenths, inputs.map { ObjCount(it, 1) }, emptyList(), -1, 1, ticks, made,
        CookingMixLines.LEVEL, byproducts = byproducts.map { ObjCount(it, 1) },
    )

    /** Flour and each water: the four doughs, the pot and the water's container back. */
    private fun doughs(water: Int, container: Int) = listOf(
        mix("bread_dough", BREAD_DOUGH, 1, 0, listOf(POT_OF_FLOUR, water), CookingMixLines.BREAD_DOUGH, listOf(POT, container), 1),
        mix("pastry_dough", PASTRY_DOUGH, 1, 0, listOf(POT_OF_FLOUR, water), CookingMixLines.PASTRY_DOUGH, listOf(POT, container), 1),
        mix("pizza_base", PIZZA_BASE, 35, 0, listOf(POT_OF_FLOUR, water), CookingMixLines.PIZZA_BASE, listOf(POT, container), 1),
        mix("pitta_dough", PITTA_DOUGH, 58, 0, listOf(POT_OF_FLOUR, water), CookingMixLines.PITTA_DOUGH, listOf(POT, container), 1),
    )

    /** One step of a pie: [onto] (a pie shell or a part pie) and [add] make [product]. */
    private fun pie(key: String, product: Int, level: Int, onto: Int, add: Int, made: Line, byproducts: List<Int> = emptyList()) =
        mix(key, product, level, 0, listOf(onto, add), made, byproducts)

    private val CAKE_MIX = mix(
        "uncooked_cake", UNCOOKED_CAKE, 40, 0, listOf(Cooking.CAKE_TIN, EGG, POT_OF_FLOUR, BUCKET_OF_MILK),
        CookingMixLines.UNCOOKED_CAKE, listOf(POT, BUCKET),
    )

    /** Each pair of items and what it makes, in the box's order. */
    val PAIRS: List<Triple<Int, Int, List<MakeRecipe>>> = listOf(
        Triple(POT_OF_FLOUR, BUCKET_OF_WATER, doughs(BUCKET_OF_WATER, BUCKET)),
        Triple(POT_OF_FLOUR, BOWL_OF_WATER, doughs(BOWL_OF_WATER, BOWL)),
        Triple(POT_OF_FLOUR, JUG_OF_WATER, doughs(JUG_OF_WATER, JUG)),
        Triple(PASTRY_DOUGH, PIE_DISH, listOf(pie("pie_shell", PIE_SHELL, 1, PASTRY_DOUGH, PIE_DISH, CookingMixLines.PIE_SHELL))),
        // One-filling pies.
        Triple(PIE_SHELL, 1951, listOf(pie("redberry_pie", 2321, 10, PIE_SHELL, 1951, CookingMixLines.REDBERRY_PIE))),
        Triple(PIE_SHELL, COOKED_MEAT, listOf(pie("meat_pie", 2319, 20, PIE_SHELL, COOKED_MEAT, CookingMixLines.MEAT_PIE))),
        Triple(PIE_SHELL, COOKED_CHICKEN, listOf(pie("meat_pie_chicken", 2319, 20, PIE_SHELL, COOKED_CHICKEN, CookingMixLines.MEAT_PIE))),
        Triple(PIE_SHELL, COOKING_APPLE, listOf(pie("apple_pie", 2317, 30, PIE_SHELL, COOKING_APPLE, CookingMixLines.APPLE_PIE))),
        Triple(PIE_SHELL, 19653, listOf(pie("botanical_pie", 19656, 52, PIE_SHELL, 19653, CookingMixLines.BOTANICAL_PIE))),
        Triple(PIE_SHELL, 21626, listOf(pie("mushroom_pie", 21684, 60, PIE_SHELL, 21626, CookingMixLines.MUSHROOM_PIE))),
        Triple(PIE_SHELL, 22929, listOf(pie("dragonfruit_pie", 22789, 73, PIE_SHELL, 22929, CookingMixLines.DRAGONFRUIT_PIE))),
        // Three-filling pies: the shell and the first, then each part pie and the next.
        Triple(PIE_SHELL, 6032, listOf(pie("mud_pie_1", 7164, 29, PIE_SHELL, 6032, CookingMixLines.MUD_PIE_1, listOf(BUCKET)))),
        Triple(7164, BUCKET_OF_WATER, listOf(pie("mud_pie_2", 7166, 29, 7164, BUCKET_OF_WATER, CookingMixLines.MUD_PIE_2, listOf(BUCKET)))),
        Triple(7166, 434, listOf(pie("mud_pie_3", 7168, 29, 7166, 434, CookingMixLines.MUD_PIE_3))),
        Triple(PIE_SHELL, TOMATO, listOf(pie("garden_pie_1", 7172, 34, PIE_SHELL, TOMATO, CookingMixLines.GARDEN_PIE_1))),
        Triple(7172, 1957, listOf(pie("garden_pie_2", 7174, 34, 7172, 1957, CookingMixLines.GARDEN_PIE_2))),
        Triple(7174, 1965, listOf(pie("garden_pie_3", 7176, 34, 7174, 1965, CookingMixLines.GARDEN_PIE_3))),
        Triple(PIE_SHELL, 333, listOf(pie("fish_pie_1", 7182, 47, PIE_SHELL, 333, CookingMixLines.FISH_PIE_1))),
        Triple(7182, 339, listOf(pie("fish_pie_2", 7184, 47, 7182, 339, CookingMixLines.FISH_PIE_2))),
        Triple(7184, POTATO, listOf(pie("fish_pie_3", 7186, 47, 7184, POTATO, CookingMixLines.FISH_PIE_3))),
        Triple(PIE_SHELL, 329, listOf(pie("admiral_pie_1", 7192, 70, PIE_SHELL, 329, CookingMixLines.ADMIRAL_PIE_1))),
        Triple(7192, 361, listOf(pie("admiral_pie_2", 7194, 70, 7192, 361, CookingMixLines.ADMIRAL_PIE_2))),
        Triple(7194, POTATO, listOf(pie("admiral_pie_3", 7196, 70, 7194, POTATO, CookingMixLines.ADMIRAL_PIE_3))),
        Triple(PIE_SHELL, 2136, listOf(pie("wild_pie_1", 7202, 85, PIE_SHELL, 2136, CookingMixLines.WILD_PIE_1))),
        Triple(7202, 2876, listOf(pie("wild_pie_2", 7204, 85, 7202, 2876, CookingMixLines.WILD_PIE_2))),
        Triple(7204, 3226, listOf(pie("wild_pie_3", 7206, 85, 7204, 3226, CookingMixLines.WILD_PIE_3))),
        Triple(PIE_SHELL, 5504, listOf(pie("summer_pie_1", 7212, 95, PIE_SHELL, 5504, CookingMixLines.SUMMER_PIE_1))),
        Triple(7212, 5982, listOf(pie("summer_pie_2", 7214, 95, 7212, 5982, CookingMixLines.SUMMER_PIE_2))),
        Triple(7214, COOKING_APPLE, listOf(pie("summer_pie_3", 7216, 95, 7214, COOKING_APPLE, CookingMixLines.SUMMER_PIE_3))),
        // Pizza: tomato, cheese, then baked (Cooking), then a topping.
        Triple(PIZZA_BASE, TOMATO, listOf(mix("incomplete_pizza", INCOMPLETE_PIZZA, 35, 0, listOf(PIZZA_BASE, TOMATO), CookingMixLines.INCOMPLETE_PIZZA))),
        Triple(INCOMPLETE_PIZZA, CHEESE, listOf(mix("uncooked_pizza", UNCOOKED_PIZZA, 35, 0, listOf(INCOMPLETE_PIZZA, CHEESE), CookingMixLines.UNCOOKED_PIZZA))),
        Triple(PLAIN_PIZZA, COOKED_MEAT, listOf(mix("meat_pizza", 2293, 45, 260, listOf(PLAIN_PIZZA, COOKED_MEAT), CookingMixLines.MEAT_PIZZA))),
        Triple(PLAIN_PIZZA, COOKED_CHICKEN, listOf(mix("meat_pizza_chicken", 2293, 45, 260, listOf(PLAIN_PIZZA, COOKED_CHICKEN), CookingMixLines.MEAT_PIZZA))),
        Triple(PLAIN_PIZZA, 319, listOf(mix("anchovy_pizza", 2297, 55, 390, listOf(PLAIN_PIZZA, 319), CookingMixLines.ANCHOVY_PIZZA))),
        Triple(PLAIN_PIZZA, 2118, listOf(mix("pineapple_pizza", 2301, 65, 450, listOf(PLAIN_PIZZA, 2118), CookingMixLines.PINEAPPLE_PIZZA))),
        Triple(PLAIN_PIZZA, 2116, listOf(mix("pineapple_pizza_chunks", 2301, 65, 450, listOf(PLAIN_PIZZA, 2116), CookingMixLines.PINEAPPLE_PIZZA))),
        // Cake: an egg, milk or flour on the tin mixes all three; chocolate on the baked cake.
        Triple(Cooking.CAKE_TIN, EGG, listOf(CAKE_MIX)),
        Triple(Cooking.CAKE_TIN, POT_OF_FLOUR, listOf(CAKE_MIX)),
        Triple(Cooking.CAKE_TIN, BUCKET_OF_MILK, listOf(CAKE_MIX)),
        Triple(CAKE, 1973, listOf(mix("chocolate_cake", 1897, 50, 300, listOf(CAKE, 1973), CookingMixLines.CHOCOLATE_CAKE))),
        Triple(CAKE, 1975, listOf(mix("chocolate_cake_dust", 1897, 50, 300, listOf(CAKE, 1975), CookingMixLines.CHOCOLATE_CAKE))),
    )

    fun register(world: World) {
        for ((a, b, recipes) in PAIRS) {
            world.handlers.onObjOnObj(a, b) { world.making.choose(this, recipes, alwaysAsk = recipes.size > 1) }
        }
    }
}
