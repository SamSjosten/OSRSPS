package osrsps.game.skill

import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Line
import osrsps.game.script.ScriptContext

/**
 * The dairy churn (Fishing and Cooking 2 step FC5b): Churn, or a bucket of milk, pot of cream or pat of butter used on
 * it, opens the Make-X box over what the held dairy can become (cream from milk; butter from milk or cream; cheese from
 * any), then churns them one by one.
 *
 * Sources: the OSRS Wiki's Dairy churn, Pot of cream, Pat of butter and Cheese pages (read 2026-11-10): levels 21, 38 and
 * 48; XP 18, 40.5 and 64 from milk, 22.5 and 46 from cream, 23.5 from butter; the milk's bucket back. The ticks (10 a
 * step, 19 for two, 26 for three) are the Wiki's Dairy churn. Inferred from the seq names: the animations
 * player_churns_milk_short, _medium and _long for one, two and three steps. Guessed: the box showing only what can be
 * made, each product from the held input nearest it (butter before cream before milk), no pot back from cream, and the
 * message with nothing to churn.
 */
class Churn(private val world: World) {
    fun register() {
        for (loc in CHURNS) {
            world.handlers.onLoc(loc, 1) { choose() }
            for (input in listOf(MILK, CREAM, BUTTER)) world.handlers.onObjOnLoc(loc, input) { choose() }
        }
    }

    private suspend fun ScriptContext.choose() {
        val recipes = PRODUCTS.mapNotNull { product -> RECIPES.firstOrNull { it.product == product && it.times(player.inventory) > 0 } }
        if (recipes.isEmpty()) {
            player.message(world.textFor(player)[CookingMixLines.CHURN_NOTHING])
            return
        }
        world.making.choose(this, recipes)
    }

    companion object {
        /** The Dairy churn locs: the wooden and metal ones, and the two in Kourend. */
        val CHURNS = listOf(11695, 11696, 25720, 35937)

        const val MILK = CookingMixes.BUCKET_OF_MILK
        const val CREAM = 2130
        const val BUTTER = 6697
        const val CHEESE = 1985
        private const val BUCKET = 1925
        private val PRODUCTS = listOf(CREAM, BUTTER, CHEESE)

        private const val SHORT = 2793
        private const val MEDIUM = 2794
        private const val LONG = 2795

        private fun churn(key: String, from: Int, product: Int, level: Int, xpTenths: Int, ticks: Int, anim: Int, made: Line) = MakeRecipe(
            key, Skill.COOKING, product, 1, level, xpTenths, listOf(ObjCount(from, 1)), emptyList(), anim, ticks, ticks, made,
            CookingMixLines.LEVEL, byproducts = if (from == MILK) listOf(ObjCount(BUCKET, 1)) else emptyList(),
        )

        /** Each way to churn, nearest input first for each product. */
        val RECIPES = listOf(
            churn("churn_milk_cream", MILK, CREAM, 21, 180, 10, SHORT, CookingMixLines.CHURN_CREAM),
            churn("churn_cream_butter", CREAM, BUTTER, 38, 225, 10, SHORT, CookingMixLines.CHURN_BUTTER),
            churn("churn_milk_butter", MILK, BUTTER, 38, 405, 19, MEDIUM, CookingMixLines.CHURN_BUTTER),
            churn("churn_butter_cheese", BUTTER, CHEESE, 48, 235, 10, SHORT, CookingMixLines.CHURN_CHEESE),
            churn("churn_cream_cheese", CREAM, CHEESE, 48, 460, 19, MEDIUM, CookingMixLines.CHURN_CHEESE),
            churn("churn_milk_cheese", MILK, CHEESE, 48, 640, 26, LONG, CookingMixLines.CHURN_CHEESE),
        )
    }
}
