package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

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
 * The Banker tutor's lines; the words are in the dialogue file, from the Wiki's Transcript:Banker tutor (Standard
 * dialogue), read 2026-10-25.
 */
object BankerTutorLines : Lines("banker_tutor", "Transcript:Banker tutor") {
    val GREETING = line("greeting", "the tutor's first line")
    val HOW_IT_WORKS = line("how_it_works", "first menu, option 1: how banking works")
    val ACCESS = line("access", "first menu, option 2: access the account")
    val PIN = line("pin", "first menu, option 3: PIN settings")
    val COLLECT = line("collect", "first menu, option 4: collect items")
    val BUY_SLOTS = line("buy_slots", "first menu, option 5: more bank space, which the player then says")
    val USING = line("using", "topic menu, option 1: using the bank")
    val USING_SAID = line("using_said", "topic menu, option 1: what the player then says (longer than the option)")
    val USING_1 = line("using_1", "using the bank: the tutor's first line")
    val USING_2 = line("using_2", "using the bank: second line")
    val USING_3 = line("using_3", "using the bank: third line, right-click")
    val USING_4 = line("using_4", "using the bank: fourth line, rearranging")
    val USING_5 = line("using_5", "using the bank: fifth line, notes")
    val USING_6 = line("using_6", "using the bank: sixth line, the logs example")
    val USING_7 = line("using_7", "using the bank: seventh line")
    val DEPOSIT_BOXES = line("deposit_boxes", "topic menu, option 2: deposit boxes")
    val DEPOSIT_BOXES_SAID = line("deposit_boxes_said", "topic menu, option 2: what the player then says")
    val DEPOSIT_1 = line("deposit_1", "deposit boxes: the tutor's first line")
    val DEPOSIT_2 = line("deposit_2", "deposit boxes: second line")
    val WHAT_PIN = line("what_pin", "topic menu, option 3: what a PIN is")
    val WHAT_PIN_SAID = line("what_pin_said", "topic menu, option 3: what the player then says")
    val PIN_1 = line("pin_1", "PIN: the tutor's first line")
    val PIN_2 = line("pin_2", "PIN: second line")
    val PIN_3 = line("pin_3", "PIN: third line")
    val SET_PIN = line("set_pin", "PIN menu, option 1: setting one, which the player then says")
    val REMOVE_PIN = line("remove_pin", "PIN menu, option 2: removing one, which the player then says")
    val FORGOT_PIN = line("forgot_pin", "PIN menu, option 3: forgetting it, which the player then says")
    val OTHER_TOPICS = line("other_topics", "PIN menu, option 4: back to the other topics")
    val GOODBYE = line("goodbye", "PIN menu, option 5: goodbye, which the player then says")
    val SET_1 = line("set_1", "setting a PIN: the tutor's first line")
    val SET_2 = line("set_2", "setting a PIN: second line")
    val SET_3 = line("set_3", "setting a PIN: third line, the recovery delay")
    val SET_4 = line("set_4", "setting a PIN: fourth line")
    val SET_5 = line("set_5", "setting a PIN: fifth line")
    val REMOVE_1 = line("remove_1", "removing a PIN: the tutor's first line")
    val REMOVE_2 = line("remove_2", "removing a PIN: second line")
    val FORGOT_1 = line("forgot_1", "a forgotten PIN: the tutor's first line")
    val FORGOT_2 = line("forgot_2", "a forgotten PIN: second line")
}

/**
 * The bankers on the castle's top floor: the Banker (multi-NPC 6520, which players see as 2897 while varbit 4153 is 0)
 * and the Banker tutor (3227). From the Wiki: both conversations; asking for the bank opens it. Not built yet: PIN
 * settings, the collection box and buying bank space, so those options end the conversation, and Collect does nothing.
 * Guessed: every chathead expression; that the tutor's topic menu comes back after each topic (the Wiki doesn't say
 * where they lead).
 */
object Bankers {
    /** banker1_new: the banker players see. */
    const val BANKER = 2897
    const val BANKER_TUTOR = 3227
    const val TALK_TO = 1

    /** The NPCs' Bank option (op3 in the 241 cache). */
    const val BANK = 3

    /** Returns true when the player asked for their bank. */
    internal suspend fun Dialogue.banker(): Boolean = with(BankerLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (choice(OPEN_BANK, PIN, COLLECT, BUY_SLOTS, WHAT_PLACE)) {
            1 -> return true
            4 -> chatPlayer(ChatAnim.QUIZ, BUY_SLOTS)
            5 -> {
                chatPlayer(ChatAnim.QUIZ, WHAT_PLACE)
                chatNpc(ChatAnim.HAPPY, THIS_BRANCH)
                chatPlayer(ChatAnim.QUIZ, YOUR_JOB)
                chatNpc(ChatAnim.HAPPY, SAFEKEEPING)
            }
        }
        false
    }

    /** Returns true when the player asked for their bank. */
    internal suspend fun Dialogue.tutor(): Boolean = with(BankerTutorLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (choice(HOW_IT_WORKS, ACCESS, PIN, COLLECT, BUY_SLOTS)) {
            1 -> tutorTopics()
            2 -> return true
            5 -> chatPlayer(ChatAnim.QUIZ, BUY_SLOTS)
        }
        false
    }

    /** The topic menu, again after each topic, and the PIN menu after the PIN topic, until the player says goodbye. */
    private suspend fun Dialogue.tutorTopics() = with(BankerTutorLines) {
        var pinMenu = false
        while (true) {
            if (!pinMenu) {
                when (choice(USING, DEPOSIT_BOXES, WHAT_PIN)) {
                    1 -> {
                        chatPlayer(ChatAnim.QUIZ, USING_SAID)
                        for (line in listOf(USING_1, USING_2, USING_3, USING_4, USING_5, USING_6, USING_7)) chatNpc(ChatAnim.NEUTRAL, line)
                    }
                    2 -> {
                        chatPlayer(ChatAnim.QUIZ, DEPOSIT_BOXES_SAID)
                        chatNpc(ChatAnim.NEUTRAL, DEPOSIT_1)
                        chatNpc(ChatAnim.NEUTRAL, DEPOSIT_2)
                    }
                    else -> {
                        chatPlayer(ChatAnim.QUIZ, WHAT_PIN_SAID)
                        for (line in listOf(PIN_1, PIN_2, PIN_3)) chatNpc(ChatAnim.NEUTRAL, line)
                        pinMenu = true
                    }
                }
                continue
            }
            val (asked, answer) = when (choice(SET_PIN, REMOVE_PIN, FORGOT_PIN, OTHER_TOPICS, GOODBYE)) {
                1 -> SET_PIN to listOf(SET_1, SET_2, SET_3, SET_4, SET_5)
                2 -> REMOVE_PIN to listOf(REMOVE_1, REMOVE_2)
                3 -> FORGOT_PIN to listOf(FORGOT_1, FORGOT_2)
                4 -> {
                    pinMenu = false
                    continue
                }
                else -> {
                    chatPlayer(ChatAnim.HAPPY, GOODBYE)
                    return@with
                }
            }
            chatPlayer(ChatAnim.QUIZ, asked)
            for (line in answer) chatNpc(ChatAnim.NEUTRAL, line)
        }
    }
}

/** The bankers' Talk-to and Bank, from across their booths. Collect does nothing yet. */
fun registerBankers(world: World) {
    world.npcInteractions.reachOverCounter(Bankers.BANKER)
    world.npcInteractions.reachOverCounter(Bankers.BANKER_TUTOR)
    world.handlers.onNpc(Bankers.BANKER, Bankers.TALK_TO) { op ->
        var bank = false
        world.dialogues.start(this, op.npc) { bank = with(Bankers) { banker() } }
        if (bank) world.bankScreen.open(player)
    }
    world.handlers.onNpc(Bankers.BANKER_TUTOR, Bankers.TALK_TO) { op ->
        var bank = false
        world.dialogues.start(this, op.npc) { bank = with(Bankers) { tutor() } }
        if (bank) world.bankScreen.open(player)
    }
    for (banker in listOf(Bankers.BANKER, Bankers.BANKER_TUTOR)) {
        world.handlers.onNpc(banker, Bankers.BANK) { world.bankScreen.open(player) }
    }
}
