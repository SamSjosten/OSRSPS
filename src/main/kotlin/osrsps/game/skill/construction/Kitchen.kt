package osrsps.game.skill.construction

import osrsps.game.GameLines
import osrsps.game.World
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/**
 * Construction step CON4 (the kitchen): what the built kitchen furniture does, from the Wiki's Kitchen page and each
 * piece's page (read 2026-11-22).
 *
 * - Larders and shelves: Search gives their supplies, free and without end, in or out of building mode, in the owner's
 *   house or anyone's. Which supplies each gives is the Wiki's. GUESSED: they are picked in the Make-X box, as many as
 *   the backpack takes (the Wiki says only that the player selects from the list); the box's title is held.
 * - The firepits cook as a fire and the fancy range as a range ([KitchenStoves], joined to [osrsps.game.skill.Cooking]'s
 *   lists); the sinks fill buckets, jugs and bowls ([osrsps.game.content.WaterSources] by their names).
 *
 * Left out for now: the ovens' and steel range's cooking (the Wiki gives each a partial food list, no exact one); the
 * kettles and pots on stoves, and tea; the barrels' drinks (the Wiki says they can't leave the house, which needs a check
 * on leaving); the spice rack's Store, Take and Check; pets on their beds.
 */
class Kitchen(private val world: World, private val houses: Houses) {
    fun register() {
        for ((loc, supplies) in SUPPLIES) {
            world.handlers.onLoc(loc, SEARCH) { op ->
                arriveDelay()
                search(this, op.loc ?: return@onLoc, supplies)
            }
        }
    }

    private suspend fun search(context: ScriptContext, loc: ZoneLoc, supplies: List<Int>) {
        val player = context.player
        if (houses.houseAt(loc.x, loc.z) == null) return world.message(player, GameLines.NOTHING_INTERESTING)
        if (player.inventory.freeSlots == 0) return world.message(player, GameLines.NO_SPACE)
        var pick: Pair<Int, Int>? = null
        world.dialogues.start(context) { pick = makeX(ConstructionLines.TAKE_SUPPLIES, supplies, player.inventory.freeSlots, 1) }
        val (index, quantity) = pick ?: return
        if (houses.houseAt(loc.x, loc.z) == null || !world.locs.exists(loc)) return
        val type = world.objTypes[supplies.getOrNull(index) ?: return] ?: return
        val added = player.inventory.add(type, minOf(quantity, player.inventory.freeSlots).coerceAtLeast(1))
        if (added == 0) world.message(player, GameLines.NO_SPACE)
    }

    companion object {
        /** The larders' and shelves' Search. */
        const val SEARCH = 1

        // Supplies by gameval name: poh_tea_leaves, bucket_milk, egg, pot_flour, potato, garlic, onion, cheese;
        // poh_kettle_empty, the clay, porcelain and gilt porcelain teapots and cups (poh_teapot_*, poh_claycup_empty,
        // poh_chinacup_empty, poh_giltchinacup_empty), poh_beer_glass, cake_tin, bowl_empty, piedish, pot_empty, chefs_hat.
        private const val TEA_LEAVES = 7738
        private const val MILK = 1927
        private const val EGG = 1944
        private const val FLOUR = 1933
        private const val POTATO = 1942
        private const val GARLIC = 1550
        private const val ONION = 1957
        private const val CHEESE = 1985
        private const val KETTLE = 7688
        private const val CLAY_TEAPOT = 7702
        private const val PORCELAIN_TEAPOT = 7714
        private const val GILT_TEAPOT = 7726
        private const val CLAY_CUP = 7728
        private const val PORCELAIN_CUP = 7732
        private const val GILT_CUP = 7735
        private const val BEER_GLASS = 7742
        private const val CAKE_TIN = 1887
        private const val BOWL = 1923
        private const val PIE_DISH = 2313
        private const val POT = 1931
        private const val CHEFS_HAT = 1949

        private val W1 = listOf(KETTLE, CLAY_TEAPOT, CLAY_CUP)
        private val W2 = W1 + BEER_GLASS
        private val W3 = listOf(KETTLE, PORCELAIN_TEAPOT, PORCELAIN_CUP, BEER_GLASS, CAKE_TIN)
        private val O1 = W2 + listOf(CAKE_TIN, BOWL)
        private val O2 = W3 + listOf(BOWL, PIE_DISH)
        private val T1 = O2 + POT
        private val T2 = listOf(KETTLE, GILT_TEAPOT, GILT_CUP, BEER_GLASS, CAKE_TIN, BOWL, PIE_DISH, POT, CHEFS_HAT)

        /**
         * The supplies that stay in the house: taken from a player as they leave it, by any way, or log out inside. Tea
         * leaves are the Wiki's ("cannot be taken outside a player-owned house"); the rest are INFERRED from their
         * house-only gameval names (poh_kettle_empty, poh_teapot_*, poh_*cup_empty, poh_beer_glass). Milk, eggs, flour
         * and the rest of the ordinary ingredients and tools leave with the player.
         */
        val HOUSE_ONLY: Set<Int> = setOf(TEA_LEAVES, KETTLE, CLAY_TEAPOT, PORCELAIN_TEAPOT, GILT_TEAPOT, CLAY_CUP, PORCELAIN_CUP, GILT_CUP, BEER_GLASS)

        /** What each larder (poh_larder_1 to 3) and each shelves (poh_kitchen_shelves_1 to 7, and their crockery) gives. */
        val SUPPLIES: Map<Int, List<Int>> = buildMap {
            put(13565, listOf(TEA_LEAVES, MILK))
            put(13566, listOf(TEA_LEAVES, MILK, EGG, FLOUR))
            put(13567, listOf(TEA_LEAVES, MILK, EGG, FLOUR, POTATO, GARLIC, ONION, CHEESE))
            listOf(W1, W2, W3, O1, O2, T1, T2).forEachIndexed { i, items ->
                put(13545 + i, items)
                put(13552 + i, items)
            }
        }
    }
}

/** The built stoves Cooking cooks on: the three firepits as fires, the fancy range (as it is, or with a kettle or pot) as a range. */
object KitchenStoves {
    /** poh_stove_1, _2 and _2_kettle, _3 and _3_kettle. */
    val FIREPITS = setOf(13528, 13529, 13530, 13531, 13532)

    /** poh_stove_7, _7_kettle, _7_pot. */
    val FANCY_RANGES = setOf(13542, 13543, 13544)
}
