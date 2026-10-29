package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/**
 * The lines of a Lumbridge General Store keeper's Talk-to, from that keeper's transcript ("Standard dialogue", read
 * 2026-10-29). The shop keeper's and the assistant's pages are separate, so each has its own keys.
 */
abstract class GeneralStoreLines(prefix: String, page: String, who: String) : Lines(prefix, page) {
    val HELP = line("help", "the $who's first line")
    val SELLING = line("selling", "option 1: what they sell (the shop opens, with no line from the player)")
    val NO_THANKS = line("no_thanks", "option 2, which the player then says")
}

object ShopKeeperLines : GeneralStoreLines("lumbridge_shop_keeper", "Transcript:Shop keeper (Lumbridge)", "shop keeper")

object ShopAssistantLines : GeneralStoreLines("lumbridge_shop_assistant", "Transcript:Shop assistant (Lumbridge)", "shop assistant")

/** Bob's lines; the words are in the dialogue file, from the Wiki's Transcript:Bob, read 2026-10-29. */
object BobLines : Lines("bob", "Transcript:Bob") {
    val QUEST = line("quest", "Standard dialogue, option 1, which the player then says")
    val OWN = line("own", "option 1: Bob's answer")
    val SELL = line("sell", "option 2, which the player then says")
    val AXES = line("axes", "option 2: Bob's answer, before the shop opens")
    val REPAIR = line("repair", "option 3, which the player then says")
    val REPAIR_REPLY = line("repair_reply", "option 3: Bob's answer")
    val NOTHING_TO_REPAIR = line("nothing_to_repair", "Right click Repair, with no repairable equipment")
    val CANT_REPAIR = line("cant_repair", "Use an item on Bob, an item he can't repair")
}

/**
 * The Lumbridge General Store's Shop keeper (2813) and Shop assistant (2814), and Bob (10619) of Bob's Brilliant Axes.
 * From the Wiki: each conversation, and that asking what they sell opens the shop. Bob repairs Barrows equipment,
 * which isn't in the game yet, so Repair and items used on him always get the "nothing to repair" answers. Expressions
 * are RSMod's (rev 233 scripts), as the Wiki notes none.
 */
object LumbridgeShopkeepers {
    const val SHOP_KEEPER = 2813
    const val SHOP_ASSISTANT = 2814
    const val BOB = 10619
    const val TALK_TO = 1
    const val REPAIR = 4

    /** A general store keeper's talk; true when the player asked to see the shop. */
    internal suspend fun Dialogue.keeper(lines: GeneralStoreLines): Boolean = with(lines) {
        chatNpc(ChatAnim.HAPPY, HELP)
        if (choice(SELLING, NO_THANKS) == 1) return@with true
        chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
        false
    }

    /** Bob's talk, which opens with the menu; true when the player asked to see the shop. */
    internal suspend fun Dialogue.bob(): Boolean = with(BobLines) {
        when (choice(QUEST, SELL, REPAIR)) {
            1 -> {
                chatPlayer(ChatAnim.HAPPY, QUEST)
                chatNpc(ChatAnim.ANGRY, OWN)
                false
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, SELL)
                chatNpc(ChatAnim.HAPPY, AXES)
                true
            }
            else -> {
                chatPlayer(ChatAnim.SAD, REPAIR)
                chatNpc(ChatAnim.SHOCKED, REPAIR_REPLY)
                false
            }
        }
    }
}

/** The shop keepers' Talk-to, and Bob's Repair and items used on him. Trade is [registerShops]'s. */
fun registerLumbridgeShopkeepers(world: World) {
    fun openShop(player: Player, npc: Int) {
        world.shopTypes.ofNpc(npc)?.let { world.shopScreen.open(player, it) }
    }
    for ((id, lines) in listOf(LumbridgeShopkeepers.SHOP_KEEPER to ShopKeeperLines, LumbridgeShopkeepers.SHOP_ASSISTANT to ShopAssistantLines)) {
        world.handlers.onNpc(id, LumbridgeShopkeepers.TALK_TO) { op ->
            var trade = false
            world.dialogues.start(this, op.npc) { trade = with(LumbridgeShopkeepers) { keeper(lines) } }
            if (trade) openShop(player, id)
        }
    }
    world.handlers.onNpc(LumbridgeShopkeepers.BOB, LumbridgeShopkeepers.TALK_TO) { op ->
        var trade = false
        world.dialogues.start(this, op.npc) { trade = with(LumbridgeShopkeepers) { bob() } }
        if (trade) openShop(player, LumbridgeShopkeepers.BOB)
    }
    world.handlers.onNpc(LumbridgeShopkeepers.BOB, LumbridgeShopkeepers.REPAIR) { op ->
        world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.CONFUSED, BobLines.NOTHING_TO_REPAIR) }
    }
    world.handlers.onObjOnNpc(LumbridgeShopkeepers.BOB) { op ->
        world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.CONFUSED, BobLines.CANT_REPAIR) }
    }
}
