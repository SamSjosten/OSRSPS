package osrsps.game.content

import osrsps.game.World

/** The NPC option that opens a shop: Trade, op 3 on every shop keeper in the 241 cache so far. */
private const val TRADE = 3

/**
 * Trade on every shop keeper in the shop file opens their shop. Their Talk-to comes with each town's NPCs. A keeper can
 * be traded with across a counter, as a banker can: many shops have one, and where none stands the rule changes
 * nothing.
 */
fun registerShops(world: World) {
    for (type in world.shopTypes.all) {
        for (npc in type.npcs) {
            world.npcInteractions.reachOverCounter(npc)
            world.handlers.onNpc(npc, TRADE) { world.shopScreen.open(player, type) }
        }
    }
}
