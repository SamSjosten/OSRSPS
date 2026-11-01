package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** A Grand Exchange Clerk's lines; the words are in the dialogue file, from the Wiki's Transcript:Grand Exchange Clerk. */
object GeClerkLines : Lines("ge_clerk", "Transcript:Grand Exchange Clerk") {
    val GREETING = line("greeting", "the clerk's first line: trade, or item sets?")
    val HOW_TO_USE = line("how_to_use", "menu option 1: how to use the Grand Exchange, which the player then says")
    val HOW_1 = line("how_1", "how to use it: the clerk's first line")
    val HOW_2 = line("how_2", "how to use it: second line")
    val HOW_3 = line("how_3", "how to use it: third line")
    val HOW_4 = line("how_4", "how to use it: fourth line")
    val HOW_5 = line("how_5", "how to use it: fifth line")
    val HOW_6 = line("how_6", "how to use it: sixth line")
    val HOW_7 = line("how_7", "how to use it: seventh line")
    val AGAIN = line("again", "after the explanation: the clerk asks again, trade or item sets?")
    val TRADE = line("trade", "menu option 2: set up trade offers (opens the offers screen)")
    val SETS = line("sets", "menu option 3: item sets (not built: ends the talk)")
    val FINE = line("fine", "menu option 4: the player is fine, which they then say")
}

/**
 * The Grand Exchange's four clerks (ge_clerk_1 to 4, 2148 to 2151), across their booths: Exchange opens the offers
 * screen, History the trade history, and Talk-to the clerk's menu, which explains the Exchange, opens the offers screen
 * or ends. From the Wiki's transcript; the explanation's seven pages are its count. Left out: Sets and the menu's item
 * sets (there is no item sets screen yet), the combat path voucher and the collection reminders options. Guessed:
 * every chathead expression, and that the player says the options they pick except setting up offers.
 */
object GrandExchangeClerks {
    val CLERKS = listOf(2148, 2149, 2150, 2151)
    const val TALK_TO = 1
    const val EXCHANGE = 3
    const val HISTORY = 4

    /** Whether a talk ends at the offers screen. */
    internal suspend fun Dialogue.clerk(): Boolean = with(GeClerkLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        // The explanation asks again and brings the menu back; every other choice ends the talk.
        var picked = choice(HOW_TO_USE, TRADE, SETS, FINE)
        while (picked == 1) {
            chatPlayer(ChatAnim.QUIZ, HOW_TO_USE)
            for (line in listOf(HOW_1, HOW_2, HOW_3, HOW_4, HOW_5, HOW_6, HOW_7)) chatNpc(ChatAnim.NEUTRAL, line)
            chatNpc(ChatAnim.HAPPY, AGAIN)
            picked = choice(HOW_TO_USE, TRADE, SETS, FINE)
        }
        if (picked == 4) chatPlayer(ChatAnim.HAPPY, FINE)
        picked == 2
    }
}

fun registerGrandExchangeClerks(world: World) {
    for (clerk in GrandExchangeClerks.CLERKS) {
        world.npcInteractions.reachOverCounter(clerk)
        world.handlers.onNpc(clerk, GrandExchangeClerks.TALK_TO) { op ->
            var trade = false
            world.dialogues.start(this, op.npc) { trade = with(GrandExchangeClerks) { clerk() } }
            if (trade) world.grandExchangeScreen.open(player)
        }
        world.handlers.onNpc(clerk, GrandExchangeClerks.EXCHANGE) { world.grandExchangeScreen.open(player) }
        world.handlers.onNpc(clerk, GrandExchangeClerks.HISTORY) { world.grandExchangeScreen.openHistory(player) }
    }
}
