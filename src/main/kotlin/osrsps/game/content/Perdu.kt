package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Perdu's lines; the words are in the dialogue file, from the Wiki's Transcript:Perdu (Standard dialogue), read 2026-10-25. */
object PerduLines : Lines("perdu", "Transcript:Perdu") {
    val GREETING = line("greeting", "Perdu's greeting, for a player with no experience to claim")
    val WHAT_TO_SAY = line("what_to_say", "the first menu's title")
    val WHY_PAY = line("why_pay", "option 1: why getting items back costs money")
    val SHOW_ITEMS = line("show_items", "option 2, and the first option of the menu after option 1: asking to see the items")
    val BREAKING = line("breaking", "option 3: items breaking on death")
    val NO_THANKS = line("no_thanks", "option 4, and the second option of the menu after option 1: saying no")
    val SAVES_A_TRIP = line("saves_a_trip", "option 1: Perdu's first answer")
    val TAKE_A_LOOK = line("take_a_look", "option 1: Perdu's second answer, asking whether to look")
    val RIVALS = line("rivals", "option 3: Perdu's answer about rivals and the Wilderness")
    val FIX = line("fix", "option 3, then the menu's option 1: fixing an item")
    val LOCK = line("lock", "option 3, then the menu's option 2: locking an item")
    val FIX_FEE = line("fix_fee", "option 3, fixing: Perdu's answer")
    val LOCK_EXPLAINED = line("lock_explained", "option 3, locking: Perdu's explanation")
    val PARCHMENT_MERCHANT = line("parchment_merchant", "option 3, locking, with no parchment: the message about the merchant")
    val GO_TO_FEROX = line("go_to_ferox", "option 3, locking, with no parchment: Perdu's directions")
    val HAVE_PARCHMENT = line("have_parchment", "option 3, locking, with a parchment: what the player says")
    val SHOW_ME = line("show_me", "option 3, locking, with a parchment: Perdu's answer")
}

/**
 * Perdu at Lumbridge Castle's gate, who sells back lost items: spawned as multi-NPC 7457, which every player sees as
 * 7456 (varbit 5401 is 0), the id clicks come in on. From the Wiki: the
 * conversation, and the parchment check. Not built yet: the Lost Property Shop that "show me the items" and Trade
 * open (the player says the line and nothing opens), claiming lost experience (the branch for a player owed some), and
 * using items on him. Guessed: every chathead expression.
 */
object Perdu {
    /** lost_property_merchant_model: the Perdu players see. */
    const val ID = 7456
    const val TALK_TO = 1

    /** trouver_parchment. */
    const val TROUVER_PARCHMENT = 24187

    internal suspend fun Dialogue.talk() = with(PerduLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (choice(WHY_PAY, SHOW_ITEMS, BREAKING, NO_THANKS, title = text(WHAT_TO_SAY))) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, WHY_PAY)
                chatNpc(ChatAnim.NEUTRAL, SAVES_A_TRIP)
                chatNpc(ChatAnim.NEUTRAL, TAKE_A_LOOK)
                if (choice(SHOW_ITEMS, NO_THANKS) == 1) chatPlayer(ChatAnim.NEUTRAL, SHOW_ITEMS) else chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
            }
            2 -> chatPlayer(ChatAnim.NEUTRAL, SHOW_ITEMS)
            3 -> {
                chatPlayer(ChatAnim.QUIZ, BREAKING)
                chatNpc(ChatAnim.NEUTRAL, RIVALS)
                if (choice(FIX, LOCK) == 1) {
                    chatPlayer(ChatAnim.QUIZ, FIX)
                    chatNpc(ChatAnim.NEUTRAL, FIX_FEE)
                } else {
                    chatPlayer(ChatAnim.QUIZ, LOCK)
                    chatNpc(ChatAnim.NEUTRAL, LOCK_EXPLAINED)
                    if (player.inventory.count(TROUVER_PARCHMENT) > 0) {
                        chatPlayer(ChatAnim.HAPPY, HAVE_PARCHMENT)
                        chatNpc(ChatAnim.NEUTRAL, SHOW_ME)
                    } else {
                        mesbox(PARCHMENT_MERCHANT)
                        chatNpc(ChatAnim.NEUTRAL, GO_TO_FEROX)
                    }
                }
            }
            else -> chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
        }
    }
}

/** Perdu's Talk-to. Trade does nothing yet. */
fun registerPerdu(world: World) {
    world.handlers.onNpc(Perdu.ID, Perdu.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Perdu) { talk() } }
    }
}
