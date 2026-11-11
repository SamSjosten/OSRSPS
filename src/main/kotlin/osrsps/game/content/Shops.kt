package osrsps.game.content

import osrsps.game.World

/** The NPC option that opens a shop: Trade, op 3 on almost every shop keeper. */
private const val TRADE = 3

/**
 * Which of [npc]'s options is Trade: op 3 unless its type puts Trade elsewhere, as the Sawmill operator's op 4 does
 * (his op 3 is Buy-plank).
 */
private fun tradeOp(world: World, npc: Int): Int {
    val index = world.npcTypes[npc]?.ops?.indexOfFirst { it.equals("Trade", ignoreCase = true) } ?: -1
    return if (index >= 0) index + 1 else TRADE
}

/**
 * Trade on every shop keeper in the shop file opens their shop. Their Talk-to comes with each town's NPCs. A keeper can
 * be traded with across a counter, as a banker can: many shops have one, and where none stands the rule changes
 * nothing.
 */
fun registerShops(world: World) {
    for (type in world.shopTypes.all) {
        for (npc in type.npcs) {
            world.npcInteractions.reachOverCounter(npc)
            world.handlers.onNpc(npc, tradeOp(world, npc)) { op ->
                if (!world.thieving.refuses(player, op.npc!!, tick)) world.shopScreen.open(player, type)
            }
        }
    }
}
