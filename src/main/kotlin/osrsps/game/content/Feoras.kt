package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/** Feoras's lines; the words are in the dialogue file, from the Wiki's Transcript:Feoras, read 2026-11-08. */
object FeorasLines : Lines("feoras", "Transcript:Feoras") {
    val GREETING = line("greeting", "Standard dialogue: his greeting")
    val FOR_SALE = line("for_sale", "first menu: what he has for sale, which the player then says")
    val TAKE_A_LOOK = line("take_a_look", "for sale: his answer, before the shop opens")
    val ADVICE = line("advice", "first menu: smithing advice, which the player then says")
    val ADVICE_1 = line("advice_1", "smithing advice: what Smithing is")
    val ADVICE_2 = line("advice_2", "smithing advice: getting ore, the mine south of Lumbridge")
    val MINE_HINT = line("mine_hint", "smithing advice: the mining site icon's box (item 5082)")
    val ADVICE_3 = line("advice_3", "smithing advice: smelting at a furnace like his")
    val FURNACE_HINT = line("furnace_hint", "smithing advice: the furnace icon's box (item 5084)")
    val ADVICE_4 = line("advice_4", "smithing advice: hammer, bars and an anvil")
    val ANVIL_HINT = line("anvil_hint", "smithing advice: the Smithing icon's box (an anvil icon, item 5086, stands in)")
    val ADVICE_5 = line("advice_5", "smithing advice: his furnace and old anvil, bronze only")
    val ADVICE_6 = line("advice_6", "smithing advice: a better anvil, Varrock")
    val ADVICE_7 = line("advice_7", "smithing advice: that covers everything")
    val GUIDE_HINT = line("guide_hint", "smithing advice: the Skills panel box")
    val THANKS = line("thanks", "smithing advice: the player's thanks")
    val ANYTHING_ELSE = line("anything_else", "smithing advice: his line, anything else")
    val HAMMER = line("hammer", "first menu: a hammer, which the player then says")
    val SPARE = line("spare", "a hammer, with room: his line")
    val GIVES_HAMMER = line("gives_hammer", "a hammer, with room: the box with the hammer")
    val NO_ROOM = line("no_room", "a hammer, no room: his line")
    val HAS_HAMMER = line("has_hammer", "a hammer, owning one: his line (missing)")
    val NOTHING = line("nothing", "first menu: nothing, which the player then says")
    val FAIR_ENOUGH = line("fair_enough", "nothing: his answer (end of dialogue)")
}

/**
 * Feoras (NPC 16806, lumbridge_blacksmith), the Smithing apprentice renamed in 242.1, in the smithy north of the
 * Sheared Ram, with the new Lumbridge Blacksmith shop (inv 1032; [registerShops] opens it from Trade).
 *
 * From the Wiki: the menu, the advice with its boxes, the shop opening after "for sale", and a hammer for a player
 * with room. Guessed: every chathead expression; that the boxes show the icon items the old tutors used (5082, 5084;
 * the anvil icon item 5086 for the Wiki's Smithing icon) and the Skills panel one is a plain message box; that "has a
 * hammer" counts carried, worn and banked (the Wiki: "only if unowned"), with a line of its own the Wiki lacks.
 */
object Feoras {
    const val ID = 16806
    const val TALK_TO = 1

    const val HAMMER_OBJ = 2347
    const val MINING_SITE_ICON = 5082
    const val FURNACE_ICON = 5084
    const val ANVIL_ICON = 5086

    /** The talk; true when it ends by opening the shop. */
    internal suspend fun Dialogue.talk(give: (Player, Int) -> Unit): Boolean {
        with(FeorasLines) {
            chatNpc(ChatAnim.HAPPY, GREETING)
            while (true) {
                when (choice(FOR_SALE, ADVICE, HAMMER, NOTHING)) {
                    1 -> {
                        chatPlayer(ChatAnim.QUIZ, FOR_SALE)
                        chatNpc(ChatAnim.HAPPY, TAKE_A_LOOK)
                        return true
                    }
                    2 -> advice()
                    3 -> {
                        chatPlayer(ChatAnim.QUIZ, HAMMER)
                        when {
                            owns(player, HAMMER_OBJ) -> chatNpc(ChatAnim.NEUTRAL, HAS_HAMMER)
                            player.inventory.freeSlots == 0 -> chatNpc(ChatAnim.NEUTRAL, NO_ROOM)
                            else -> {
                                chatNpc(ChatAnim.HAPPY, SPARE)
                                give(player, HAMMER_OBJ)
                                objbox(HAMMER_OBJ, GIVES_HAMMER)
                            }
                        }
                        return false
                    }
                    else -> {
                        chatPlayer(ChatAnim.NEUTRAL, NOTHING)
                        chatNpc(ChatAnim.NEUTRAL, FAIR_ENOUGH)
                        return false
                    }
                }
            }
        }
    }

    private suspend fun Dialogue.advice() = with(FeorasLines) {
        chatPlayer(ChatAnim.QUIZ, ADVICE)
        chatNpc(ChatAnim.HAPPY, ADVICE_1)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_2)
        objbox(MINING_SITE_ICON, MINE_HINT)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_3)
        objbox(FURNACE_ICON, FURNACE_HINT)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_4)
        objbox(ANVIL_ICON, ANVIL_HINT)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_5)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_6)
        chatNpc(ChatAnim.HAPPY, ADVICE_7)
        mesbox(GUIDE_HINT)
        chatPlayer(ChatAnim.HAPPY, THANKS)
        chatNpc(ChatAnim.HAPPY, ANYTHING_ELSE)
    }

    /** Carried, worn or banked. */
    fun owns(player: Player, obj: Int): Boolean =
        player.inventory.count(obj) > 0 || player.equipment.count(obj) > 0 || player.bank.count(obj) > 0
}

/** Feoras's Talk-to; his Trade is [registerShops]'s. */
fun registerFeoras(world: World) {
    val give: (Player, Int) -> Unit = { player, obj ->
        world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
    }
    world.handlers.onNpc(Feoras.ID, Feoras.TALK_TO) { op ->
        var trade = false
        world.dialogues.start(this, op.npc) { trade = with(Feoras) { talk(give) } }
        if (trade) world.shopTypes.ofNpc(Feoras.ID)?.let { world.shopScreen.open(player, it) }
    }
}
