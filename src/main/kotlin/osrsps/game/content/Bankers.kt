package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** A banker's lines; the words are in the dialogue file, from the Wiki's Transcript:Banker (Standard dialogue), read 2026-10-25. */
object BankerLines : Lines("banker", "Transcript:Banker") {
    val GREETING = line("greeting", "the banker's first line")
    val OPEN_BANK = line("open_bank", "option 1: open the bank")
    val PIN = line("pin", "option 2: PIN settings")
    val COLLECT = line("collect", "option 3: collect items")
    val BUY_SLOTS = line("buy_slots", "option 4: buy more bank space, which the player then says")
    val WHAT_PLACE = line("what_place", "option 5: what is this place, which the player then says")
    val THIS_BRANCH = line("this_branch", "option 5: the banker's answer")
    val YOUR_JOB = line("your_job", "option 5: the player's second question")
    val SAFEKEEPING = line("safekeeping", "option 5: the banker's last line")
}

/**
 * The bankers on the castle's top floor: the Banker (multi-NPC 6520, which players see as 2897 while varbit 4153 is 0)
 * and, where the Banker tutor stood until 242.1 replaced her with a normal banker, a second Banker (2897). From the
 * Wiki: the conversation; asking for the bank opens it, asking for PIN settings
 * opens [osrsps.game.bank.BankPinSettings], asking to collect (and Collect, op4) opens the Grand Exchange collection
 * box. Not built yet: buying bank space, so that option ends the conversation. Al Kharid's five bankers (3090 and
 * 3091) talk and bank the same way: the Wiki's Banker transcript is every banker's. So do Varrock's west and east bank
 * bankers (2897 and 2898) and the Grand Exchange's (1633, 1634 and 3089).
 * Guessed: every chathead expression.
 */
object Bankers {
    /** banker1_new: the banker players see. */
    const val BANKER = 2897

    /** banker1 and banker2: Draynor Village's bankers, who say what the castle's does (the Wiki's Banker page). */
    val DRAYNOR = listOf(1613, 1618)

    /** Al Kharid's bankers (kharidbanker1 and 2), who say what the castle's banker says. */
    const val AL_KHARID_BANKER_1 = 3090
    const val AL_KHARID_BANKER_2 = 3091
    val AL_KHARID = listOf(AL_KHARID_BANKER_1, AL_KHARID_BANKER_2)

    /**
     * banker2_new: the other look of Varrock's west and east bank bankers, who stand beside [BANKER]s and say what the
     * castle's banker says.
     */
    const val VARROCK_BANKER_2 = 2898

    /** banker1_west, banker1_east and banker2_east: the Grand Exchange's bankers. */
    val GRAND_EXCHANGE = listOf(1633, 1634, 3089)

    /** Every banker with the standard conversation. */
    val STANDARD = listOf(BANKER, VARROCK_BANKER_2) + DRAYNOR + AL_KHARID + GRAND_EXCHANGE
    const val TALK_TO = 1

    /** The NPCs' Bank option (op3 in the 241 cache). */
    const val BANK = 3

    /** The NPCs' Collect option (op4): the Grand Exchange collection box. */
    const val COLLECT = 4

    /** What a conversation ends in: nothing more, the bank, the PIN settings screen or the collection box. */
    enum class Ask { NOTHING, BANK, PIN, COLLECT }

    internal suspend fun Dialogue.banker(): Ask = with(BankerLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (choice(OPEN_BANK, PIN, COLLECT, BUY_SLOTS, WHAT_PLACE)) {
            1 -> return Ask.BANK
            2 -> return Ask.PIN
            3 -> return Ask.COLLECT
            4 -> chatPlayer(ChatAnim.QUIZ, BUY_SLOTS)
            5 -> {
                chatPlayer(ChatAnim.QUIZ, WHAT_PLACE)
                chatNpc(ChatAnim.HAPPY, THIS_BRANCH)
                chatPlayer(ChatAnim.QUIZ, YOUR_JOB)
                chatNpc(ChatAnim.HAPPY, SAFEKEEPING)
            }
        }
        Ask.NOTHING
    }
}

/** The bankers' Talk-to, Bank and Collect, from across their booths. */
fun registerBankers(world: World) {
    for (banker in Bankers.STANDARD) world.npcInteractions.reachOverCounter(banker)
    fun ScriptContext.answer(ask: Bankers.Ask) = when (ask) {
        Bankers.Ask.BANK -> world.bankScreen.open(player)
        Bankers.Ask.PIN -> world.bankPinSettings.open(player)
        Bankers.Ask.COLLECT -> world.grandExchangeScreen.openCollectionBox(player)
        Bankers.Ask.NOTHING -> Unit
    }
    for (banker in Bankers.STANDARD) {
        world.handlers.onNpc(banker, Bankers.TALK_TO) { op ->
            var ask = Bankers.Ask.NOTHING
            world.dialogues.start(this, op.npc) { ask = with(Bankers) { banker() } }
            answer(ask)
        }
        world.handlers.onNpc(banker, Bankers.BANK) { world.bankScreen.open(player) }
        world.handlers.onNpc(banker, Bankers.COLLECT) { world.grandExchangeScreen.openCollectionBox(player) }
    }
}
