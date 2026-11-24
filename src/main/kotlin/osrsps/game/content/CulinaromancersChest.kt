package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.World

/**
 * The Culinaromancer's Chest in Lumbridge Castle's cellar, as Recipe for Disaster leaves it (post-quest content step
 * 5). From the OSRS Wiki's Culinaromancer's Chest page (read 2026-11-19); the tile and types from the 242 cache's map.
 *
 * The map places multiloc `hundred_goodchest` (12308) at 3219,9623 on varbit 1850, which shows the chest
 * (`hundred_goodchest_base` 12309: Bank, Collect, Buy-food, Buy-items) once the quest is far enough on; the bank loc
 * rule ([registerBanking]) sees only the shown type, so the map's id gets its options here:
 *
 * - **Bank** opens the bank and **Collect** the Grand Exchange collection box, as every bank booth's do.
 * - **Buy-food** opens the food store and **Buy-items** the items store (shops.json's `culinaromancers_chest_food` and
 *   `culinaromancers_chest_items`): the Wiki's tables for a finished quest, every pair of gloves from hardleather to
 *   barrows and every utensil, at 130%/40%/2%, the gloves not bought back.
 *
 * Left out: the Elite Lumbridge & Draynor Diary's 20% off (diaries aren't built), the bank-tile rule for bank-only item
 * options.
 */
object CulinaromancersChest {
    const val CHEST = 12308
    const val BANK = 1
    const val COLLECT = 3
    const val BUY_FOOD = 4
    const val BUY_ITEMS = 5
    const val FOOD_SHOP = "culinaromancers_chest_food"
    const val ITEMS_SHOP = "culinaromancers_chest_items"
}

fun registerCulinaromancersChest(world: World) = with(CulinaromancersChest) {
    world.handlers.onLoc(CHEST, BANK) { world.bankScreen.open(player) }
    world.handlers.onLoc(CHEST, COLLECT) { world.grandExchangeScreen.openCollectionBox(player) }
    for ((option, key) in listOf(BUY_FOOD to FOOD_SHOP, BUY_ITEMS to ITEMS_SHOP)) {
        world.handlers.onLoc(CHEST, option) {
            val shop = world.shopTypes[key] ?: return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            world.shopScreen.open(player, shop)
        }
    }
}
