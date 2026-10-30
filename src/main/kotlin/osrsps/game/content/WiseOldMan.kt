package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/** One line of a fixed run of talk: the Wise Old Man's ([npc]) or the player's. */
class Said(val npc: Boolean, val line: Line)

/**
 * The Wise Old Man's lines once every quest is done (the Wiki's Transcript:Wise_Old_Man, "Subsequent Dialogue", read
 * 2026-10-30). Each topic's run of lines is declared by [said] as `<topic>_1`, `<topic>_2`…, with W for his lines and P
 * for the player's, in the Wiki's order with every quest's extra lines in.
 */
object WomLines : Lines("wise_old_man", "Transcript:Wise_Old_Man") {
    val GREETINGS = line("greetings", "his first line, with the player's name", "name")
    val HOOD = line("hood", "main menu, option 1, which the player then says: another Quest point hood")
    val HOOD_GIVEN = line("hood_given", "option 1: the item box, the hood")
    val HOOD_NO_ROOM = line("hood_no_room", "option 1 with a full inventory: his answer")
    val CAPE = line("cape", "main menu, option 2, which the player then says: buying a Quest point cape")
    val CAPE_PRICE = line("cape_price", "option 2: his answer, the price")
    val CAPE_OKAY = line("cape_okay", "the price menu, option 1, which the player then says")
    val CAPE_GIVEN = line("cape_given", "option 1: the item box, the hood and the cape")
    val CAPE_NO_ROOM = line("cape_no_room", "option 1 with under 2 free slots: his answer")
    val CAPE_NO_COINS = line("cape_no_coins", "option 1 with under 99,000 coins: the player's line")
    val CAPE_NO_THANKS = line("cape_no_thanks", "the price menu, option 2, which the player then says")
    val MORE = line("more", "main menu, option 3")
    val ANYTHING = line("anything", "the More menu: anything to do for him, which the player then says")
    val NOTHING_NOW = line("nothing_now", "that option: his answer (the free-to-play one, with no tasks built)")
    val ASK = line("ask", "the More menu: asking him something, which the player then says")
    val PLEASE_DO = line("please_do", "that option: his answer, before the topics")

    val DISTANT = line("distant", "the topics menu, option 1")
    val BEASTS = line("beasts", "the topics menu, option 2")
    val DAYS = line("days", "the topics menu, option 3")
    val GODS = line("gods", "the topics menu, option 4")
    val YOU = line("you", "the topics menu, option 5")

    val CLOSE = line("close", "after each topic: whether there's anything else")
    val YES = line("yes", "the closing menu, option 1, which the player then says")
    val THANKS = line("thanks", "the closing menu, option 2, which the player then says")
    val FAREWELL = line("farewell", "option 2: his last line, with the player's name", "name")

    val WILDERNESS = line("wilderness", "Distant lands, option 1")
    val JUNGLES = line("jungles", "Distant lands, option 2")
    val UNDERGROUND = line("underground", "Distant lands, option 3")
    val REALMS = line("realms", "Distant lands, option 4")
    val WILDERNESS_TALK = said("wilderness", "PWWWWWW")
    val JUNGLES_TALK = said("jungles", "PWWWWPWPW")
    val UNDERGROUND_TALK = said("underground", "PWWWWWW")
    val REALMS_TALK = said("realms", "PWWPWP")

    val BIGGEST = line("biggest", "Strange beasts, option 1")
    val POISON = line("poison", "Strange beasts, option 2")
    val WEALTH = line("wealth", "Strange beasts, option 3")
    val RANDOM = line("random", "Strange beasts, option 4")
    val BIGGEST_TALK = said("biggest", "PWWWWWP")
    val POISON_TALK = said("poison", "PWWWW")
    val WEALTH_TALK = said("wealth", "PWWW")
    val RANDOM_TALK = said("random", "PWWW")

    val HEROES = line("heroes", "Days gone by, option 1")
    val MAGIC = line("magic", "Days gone by, option 2")
    val SETTLEMENTS = line("settlements", "Days gone by, option 3")
    val HISTORY = line("history", "Days gone by, option 4: the Wise Old Man of Draynor Village")
    val HEROES_TALK = said("heroes", "PWWWWWWWW")
    val MAGIC_TALK = said("magic", "PWWWWWP")
    val SETTLEMENTS_TALK = said("settlements", "PWWWPWWWWPW")
    /** Ends on his own closing question ("…know?"), not [CLOSE]. */
    val HISTORY_TALK = said("history", "PWWWWWPWPWPWPWWPWWWPWWW")

    val THREE_GODS = line("three_gods", "Gods and demons, option 1")
    val WARS = line("wars", "Gods and demons, option 2")
    val MAHJARRAT = line("mahjarrat", "Gods and demons, option 3")
    val WIELDING = line("wielding", "Gods and demons, option 4")
    /** Ends the talk: Desert Treasure I's lines stop it. */
    val THREE_GODS_TALK = said("three_gods", "PWWWWPWPWPWPW")
    val WARS_TALK = said("wars", "PWWPW")
    /** Ends the talk, as [THREE_GODS_TALK] does. */
    val MAHJARRAT_TALK = said("mahjarrat", "PWWWPWPWPW")
    val WIELDING_TALK = said("wielding", "PW")

    val HAT = line("hat", "Something to do with you, option 1: his hat")
    val QUEST = line("quest", "Something to do with you, option 2, after Swan Song: the quest done together")
    val RETIREMENT = line("retirement", "Something to do with you, option 3, after Swan Song")
    val HAT_TALK = said("hat", "PW")
    /** Not having seen the robbery's recording. Ends on his "…else?" question, before the hat menu. */
    val HAT_UNSEEN_TALK = said("hat_unseen", "PWPWPWWWPWPW")
    /** Having seen the recording. Ends as [HAT_UNSEEN_TALK] does. */
    val HAT_SEEN_TALK = said("hat_seen", "PPWPWPWWPWPWWWWPWPW")
    val HOW_HAT = line("how_hat", "the hat menu: how to get a hat like his, which the player then says")
    val HOW_HAT_TALK = said("how_hat", "WPW")
    val GIVE_BACK = line("give_back", "the hat menu after how_hat: he should give it back, which the player then says")
    val GIVE_BACK_TALK = said("give_back", "WPW")
    val QUEST_TALK = said("quest", "PWPWP")
    /** Ends on his own closing question ("…ask?"), not [CLOSE]. */
    val RETIREMENT_TALK = said("retirement", "PWWPWPWPWPWWWWWPWWWPW")

    val TELESCOPE = said("telescope", "PW")
    val TELESCOPE_UNSEEN = said("telescope_unseen", "PWPWP")
    val TELESCOPE_SEEN = said("telescope_seen", "PWP")

    private fun said(topic: String, speakers: String): List<Said> = speakers.mapIndexed { i, who ->
        val npc = who == 'W'
        Said(npc, line("${topic}_${i + 1}", "$topic, line ${i + 1}: ${if (npc) "his" else "the player's"}"))
    }
}

/** Miss Schism's lines (the Wiki's Transcript:Miss_Schism, read 2026-10-30). */
object MissSchismLines : Lines("miss_schism", "Transcript:Miss_Schism") {
    val NEWS_OPENER = line("news_opener", "her first line")
    val NEWS = line("news", "menu option 1, hearing the news")
    val WHO = line("who", "menu option 2, which the player then says")
    val HORRIBLE = line("horrible", "menu option 3, which the player then says")
    val OOOH = line("oooh", "option 3: her one-word answer")
    val WHO_TALK = said("who", "WWWP")
    /** Not having seen the robbery's recording; ends the talk. */
    val UNSEEN_TALK = said("unseen", "PWPWPWPWP")
    /** Having seen it, up to her menu. */
    val SEEN_TALK = said("seen", "PWPWPWW")
    val NEW = line("new", "her menu, option 1: quite new here, which the player then says")
    val NEW_ANSWER = line("new_answer", "option 1: her answer")
    val AGES = line("ages", "her menu, option 2: around for ages, which the player then says")
    val AGES_ANSWER = line("ages_answer", "option 2: her answer")
    /** After options 1 and 2, from the player's "…him." on; ends the talk. Guessed: the two pictures she shows aren't. */
    val STORY_TALK = said("story", "PWWWPWWWPWPWWPW")
    val ENOUGH = line("enough", "her menu, option 3, which the player then says")
    val ENOUGH_ANSWER = line("enough_answer", "option 3: her answer")

    private fun said(topic: String, speakers: String): List<Said> = speakers.mapIndexed { i, who ->
        val npc = who == 'W'
        Said(npc, line("${topic}_${i + 1}", "$topic, line ${i + 1}: ${if (npc) "hers" else "the player's"}"))
    }
}

/** The Draynor bank guard's lines after the robbery (the Wiki's Transcript:Bank_guard, read 2026-10-30). */
object BankGuardLines : Lines("wom_bankguard", "Transcript:Bank_guard") {
    val YES_Q = line("yes_q", "his first line")
    val OK = line("ok", "his one-word answer to a goodbye")
    val FIRST_TALK = said("first", "PWPW")
    val SEE = line("see", "menu option: seeing the recording, which the player then says")
    val SEE_LONG = line("see_long", "that option: his answer")
    val SHOW = line("show", "the recording menu, option 1, which the player then says")
    val SHOW_HOW = line("show_how", "that option: his answer, before the recording")
    val ANOTHER_DAY = line("another_day", "the recording menu's no, which the player then says")
    val ROBBER = line("robber", "the first menu, option 2: who the robber was, which the player then says")
    val ROBBER_ANSWER = line("robber_answer", "that option: his answer")
    val STOP = line("stop", "the robber menu, option 2, which the player then says")
    val GOODBYE = line("goodbye", "that option: his answer (sir or madam; the words file has one, as the body type isn't kept)")
    val EYES = line("eyes", "the recording: the player closes their eyes (message box)")
    val PLAYS = line("plays", "the recording plays (message box); the recording's own scene isn't built")
    val END = line("end", "the recording ends (message box)")

    val FAMILIAR = line("familiar", "after the recording, option 1: the robber looked familiar, which the player then says")
    val FAMILIAR_ANSWER = line("familiar_answer", "that option: his answer")
    val NOT_CONFRONTED_TALK = said("not_confronted", "PW")
    val CONFRONTED_TALK = said("confronted", "PW")
    val ARREST_TALK = said("arrest", "PWP")
    val REPLAY = line("replay", "after the recording, option 2: seeing it again, which the player then says")
    val FIFTY = line("fifty", "that option: his answer, 50 coins first")
    val PAY = line("pay", "with 50 coins held: paying, the menu's option 1")
    val NOT_ENOUGH = line("not_enough", "under 50 coins held: the player's line")
    val NO_BANK = line("no_bank", "under 50 coins held and banked: his answer")
    val FROM_BANK = line("from_bank", "under 50 held, 50 banked: his offer to take them from the bank")
    val PAY_BANK = line("pay_bank", "that menu, option 1")
    val SORRY = line("sorry", "after the recording, option 3, which the player then says")

    private fun said(topic: String, speakers: String): List<Said> = speakers.mapIndexed { i, who ->
        val npc = who == 'W'
        Said(npc, line("${topic}_${i + 1}", "$topic, line ${i + 1}: ${if (npc) "his" else "the player's"}"))
    }
}

/**
 * The Wise Old Man's house north of Draynor's bank, his neighbour Miss Schism and the bank guard who keeps the robbery's
 * recording (Draynor piece step 6), with every quest done: Swan Song shows him (`wom_multi` 2109 shows `wise_old_man`
 * 2108 once varbit 2098 is past 189), and every account may have the Quest point cape.
 *
 * - The Wise Old Man: the hood and the cape (99,000 coins), and every topic of "I'd just like to ask you something", as
 *   the Wiki's transcript has them with every quest's lines in. Guessed sides where a quest lets the player choose:
 *   Ceril in Hazeel Cult and the Guardians of Armadyl in Temple of Ikov. Desert Treasure I's lines end "Three gods?" and
 *   "The Mahjarrat", as the Wiki has them.
 * - Seeing the robbery's recording, which the bank guard shows, sets varbit 960 (`wom_bankjob`) to [SEEN]; asking the
 *   Wise Old Man about his hat after it sets [CONFRONTED]. Both are guessed uses of that varbit. The Wise Old Man's
 *   hat talk, his telescope (`Observe`, loc 7092), Miss Schism's news and the guard's own talk change with it.
 * - Left out: his tasks ("What did you ask me to do?", and "anything I can do" gives the free-to-play answer), the junk
 *   check (the Recycling Centre), items used on him, and the scenes the recording and Miss Schism's pictures show (the
 *   message boxes and lines are kept). The bed's Kick ([WOM_BED]) is a task's and does nothing yet.
 *
 * Every chathead expression is guessed.
 */
object WiseOldMan {
    const val ID = 2108
    const val MISS_SCHISM = 2116
    const val BANK_GUARD = 2123
    const val WOM_BED = 2114
    const val TALK_TO = 1

    const val TELESCOPE = 7092
    const val OBSERVE = 1

    const val COINS = 995
    const val CAPE_COINS = 99_000
    const val QUEST_CAPE = 9813
    const val QUEST_HOOD = 9814
    const val RECORDING_PRICE = 50

    /** wom_bankjob: 0 before the recording, then [SEEN], then [CONFRONTED]. Guessed values. */
    const val BANKJOB_VARBIT = 960
    const val SEEN = 1
    const val CONFRONTED = 2

    fun bankjob(player: Player): Int =
        if (player.vars.types.varbit(BANKJOB_VARBIT) != null) player.vars.getVarbit(BANKJOB_VARBIT) else 0

    private fun setBankjob(player: Player, value: Int) {
        if (player.vars.types.varbit(BANKJOB_VARBIT) != null && player.vars.getVarbit(BANKJOB_VARBIT) < value) {
            player.vars.setVarbit(BANKJOB_VARBIT, value)
        } else if (player.vars.types.varbit(BANKJOB_VARBIT) == null) {
            logger.warn { "No varbit $BANKJOB_VARBIT in the cache" }
        }
    }

    /** Says a fixed run of talk, the Wise Old Man's lines (or [npc]'s) and the player's. */
    internal suspend fun Dialogue.say(talk: List<Said>, anim: ChatAnim = ChatAnim.NEUTRAL) {
        for (said in talk) if (said.npc) chatNpc(anim, said.line) else chatPlayer(anim, said.line)
    }

    internal suspend fun Dialogue.wom(give: (Player, Int) -> Unit): Unit = with(WomLines) {
        chatNpc(ChatAnim.HAPPY, GREETINGS, "name" to player.username)
        while (true) {
            when (choice(HOOD, CAPE, MORE)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, HOOD)
                    if (player.inventory.freeSlots == 0) {
                        chatNpc(ChatAnim.NEUTRAL, HOOD_NO_ROOM)
                    } else {
                        give(player, QUEST_HOOD)
                        objbox(QUEST_HOOD, HOOD_GIVEN)
                    }
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, CAPE)
                    chatNpc(ChatAnim.HAPPY, CAPE_PRICE)
                    if (choice(CAPE_OKAY, CAPE_NO_THANKS) == 1) {
                        chatPlayer(ChatAnim.HAPPY, CAPE_OKAY)
                        when {
                            player.inventory.freeSlots < 2 -> chatNpc(ChatAnim.NEUTRAL, CAPE_NO_ROOM)
                            player.inventory.count(COINS) < CAPE_COINS -> chatPlayer(ChatAnim.SAD, CAPE_NO_COINS)
                            else -> {
                                Ned.take(player.inventory, COINS, CAPE_COINS)
                                give(player, QUEST_CAPE)
                                give(player, QUEST_HOOD)
                                doubleobjbox(QUEST_HOOD, QUEST_CAPE, text(CAPE_GIVEN))
                            }
                        }
                    } else {
                        chatPlayer(ChatAnim.NEUTRAL, CAPE_NO_THANKS)
                    }
                }
                else -> {
                    if (choice(ANYTHING, ASK) == 1) {
                        chatPlayer(ChatAnim.QUIZ, ANYTHING)
                        chatNpc(ChatAnim.NEUTRAL, NOTHING_NOW)
                    } else {
                        chatPlayer(ChatAnim.QUIZ, ASK)
                        chatNpc(ChatAnim.HAPPY, PLEASE_DO)
                        topics()
                    }
                    return@with
                }
            }
        }
    }

    /** The five topics, until the player thanks him or a topic ends the talk. */
    private suspend fun Dialogue.topics(): Unit = with(WomLines) {
        while (true) {
            val more = when (choice(DISTANT, BEASTS, DAYS, GODS, YOU)) {
                1 -> when (choice(WILDERNESS, JUNGLES, UNDERGROUND, REALMS)) {
                    1 -> topic(WILDERNESS_TALK)
                    2 -> topic(JUNGLES_TALK)
                    3 -> topic(UNDERGROUND_TALK)
                    else -> topic(REALMS_TALK)
                }
                2 -> when (choice(BIGGEST, POISON, WEALTH, RANDOM)) {
                    1 -> topic(BIGGEST_TALK)
                    2 -> topic(POISON_TALK)
                    3 -> topic(WEALTH_TALK)
                    else -> topic(RANDOM_TALK)
                }
                3 -> when (choice(HEROES, MAGIC, SETTLEMENTS, HISTORY)) {
                    1 -> topic(HEROES_TALK)
                    2 -> topic(MAGIC_TALK)
                    3 -> topic(SETTLEMENTS_TALK)
                    else -> topic(HISTORY_TALK, asks = false)
                }
                4 -> when (choice(THREE_GODS, WARS, MAHJARRAT, WIELDING)) {
                    1 -> say(THREE_GODS_TALK).let { false }
                    2 -> topic(WARS_TALK)
                    3 -> say(MAHJARRAT_TALK).let { false }
                    else -> topic(WIELDING_TALK)
                }
                else -> when (choice(HAT, QUEST, RETIREMENT)) {
                    1 -> hat()
                    2 -> topic(QUEST_TALK)
                    else -> topic(RETIREMENT_TALK, asks = false, farewell = false)
                }
            }
            if (!more) return@with
        }
    }

    /**
     * A topic's [talk], then his closing question ([WomLines.CLOSE], unless the talk [asks] its own) and the menu: true
     * for "Yes please", back to the topics.
     */
    private suspend fun Dialogue.topic(talk: List<Said>, asks: Boolean = true, farewell: Boolean = true): Boolean {
        say(talk)
        if (asks) chatNpc(ChatAnim.QUIZ, WomLines.CLOSE)
        return closing(farewell)
    }

    private suspend fun Dialogue.closing(farewell: Boolean): Boolean = with(WomLines) {
        if (choice(YES, THANKS) == 1) {
            chatPlayer(ChatAnim.HAPPY, YES)
            return@with true
        }
        chatPlayer(ChatAnim.NEUTRAL, THANKS)
        if (farewell) chatNpc(ChatAnim.HAPPY, FAREWELL, "name" to player.username)
        false
    }

    /** "Your hat!": the robbery talk, then the hat menu until the player picks Yes please (true) or Thanks. */
    private suspend fun Dialogue.hat(): Boolean {
        with(WomLines) {
        say(HAT_TALK)
        val seen = bankjob(player) >= SEEN
        if (seen) {
            say(HAT_SEEN_TALK)
            setBankjob(player, CONFRONTED)
        } else {
            say(HAT_UNSEEN_TALK)
        }
        // The Wiki's hat menu with the recording unseen has no Thanks until How has been asked.
        var options = if (seen) listOf(HOW_HAT, YES, THANKS) else listOf(HOW_HAT, YES)
        while (true) {
            val picked = options[choice(*options.toTypedArray()) - 1]
            when (picked) {
                HOW_HAT -> {
                    chatPlayer(ChatAnim.QUIZ, HOW_HAT)
                    say(HOW_HAT_TALK)
                    options = listOf(GIVE_BACK, YES, THANKS)
                }
                GIVE_BACK -> {
                    chatPlayer(ChatAnim.NEUTRAL, GIVE_BACK)
                    say(GIVE_BACK_TALK)
                    options = listOf(HOW_HAT, YES, THANKS)
                }
                YES -> {
                    chatPlayer(ChatAnim.HAPPY, YES)
                    return true
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, THANKS)
                    chatNpc(ChatAnim.HAPPY, FAREWELL, "name" to player.username)
                    return false
                }
            }
        }
        }
    }

    /** The telescope upstairs: the player and the Wise Old Man, his chathead shown as [wom]'s type. */
    internal suspend fun Dialogue.telescope(womSays: suspend Dialogue.(Line) -> Unit): Unit = with(WomLines) {
        val talk = TELESCOPE + if (bankjob(player) >= SEEN) TELESCOPE_SEEN else TELESCOPE_UNSEEN
        for (said in talk) if (said.npc) womSays(said.line) else chatPlayer(ChatAnim.NEUTRAL, said.line)
    }

    internal suspend fun Dialogue.missSchism(): Unit = with(MissSchismLines) {
        chatNpc(ChatAnim.HAPPY, NEWS_OPENER)
        while (true) {
            when (choice(NEWS, WHO, HORRIBLE)) {
                1 -> {
                    if (bankjob(player) < SEEN) {
                        say(UNSEEN_TALK)
                        return@with
                    }
                    say(SEEN_TALK)
                    when (choice(NEW, AGES, ENOUGH)) {
                        1 -> {
                            chatPlayer(ChatAnim.NEUTRAL, NEW)
                            chatNpc(ChatAnim.HAPPY, NEW_ANSWER)
                            say(STORY_TALK)
                        }
                        2 -> {
                            chatPlayer(ChatAnim.NEUTRAL, AGES)
                            chatNpc(ChatAnim.HAPPY, AGES_ANSWER)
                            say(STORY_TALK)
                        }
                        else -> {
                            chatPlayer(ChatAnim.ANGRY, ENOUGH)
                            chatNpc(ChatAnim.NEUTRAL, ENOUGH_ANSWER)
                        }
                    }
                    return@with
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, WHO)
                    say(WHO_TALK)
                }
                else -> {
                    chatPlayer(ChatAnim.ANGRY, HORRIBLE)
                    chatNpc(ChatAnim.NEUTRAL, OOOH)
                    return@with
                }
            }
        }
    }

    internal suspend fun Dialogue.bankGuard(): Unit = with(BankGuardLines) {
        chatNpc(ChatAnim.NEUTRAL, YES_Q)
        if (bankjob(player) < SEEN) firstTalk() else laterTalk()
    }

    private suspend fun Dialogue.firstTalk(): Unit = with(BankGuardLines) {
        say(FIRST_TALK)
        if (choice(SEE, ROBBER) == 2) {
            chatPlayer(ChatAnim.QUIZ, ROBBER)
            chatNpc(ChatAnim.NEUTRAL, ROBBER_ANSWER)
            if (choice(SEE, STOP) == 2) {
                chatPlayer(ChatAnim.NEUTRAL, STOP)
                chatNpc(ChatAnim.NEUTRAL, GOODBYE)
                return@with
            }
        }
        chatPlayer(ChatAnim.QUIZ, SEE)
        chatNpc(ChatAnim.NEUTRAL, SEE_LONG)
        if (choice(SHOW, ANOTHER_DAY) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, SHOW)
            chatNpc(ChatAnim.NEUTRAL, SHOW_HOW)
            recording()
        } else {
            anotherDay()
        }
    }

    private suspend fun Dialogue.laterTalk(): Unit = with(BankGuardLines) {
        when (choice(FAMILIAR, REPLAY, SORRY)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, FAMILIAR)
                chatNpc(ChatAnim.NEUTRAL, FAMILIAR_ANSWER)
                say(if (bankjob(player) >= CONFRONTED) CONFRONTED_TALK else NOT_CONFRONTED_TALK)
                say(ARREST_TALK)
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, REPLAY)
                chatNpc(ChatAnim.NEUTRAL, FIFTY)
                if (player.inventory.count(COINS) >= RECORDING_PRICE) {
                    if (choice(PAY, ANOTHER_DAY) == 1) {
                        Ned.take(player.inventory, COINS, RECORDING_PRICE)
                        recording()
                    } else {
                        anotherDay()
                    }
                    return@with
                }
                chatPlayer(ChatAnim.SAD, NOT_ENOUGH)
                if (player.bank.count(COINS) < RECORDING_PRICE) {
                    chatNpc(ChatAnim.NEUTRAL, NO_BANK)
                    return@with
                }
                chatNpc(ChatAnim.NEUTRAL, FROM_BANK)
                if (choice(PAY_BANK, ANOTHER_DAY) == 1) {
                    Ned.take(player.bank, COINS, RECORDING_PRICE)
                    recording()
                } else {
                    anotherDay()
                }
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, SORRY)
                chatNpc(ChatAnim.NEUTRAL, OK)
            }
        }
    }

    private suspend fun Dialogue.anotherDay(): Unit = with(BankGuardLines) {
        chatPlayer(ChatAnim.NEUTRAL, ANOTHER_DAY)
        chatNpc(ChatAnim.NEUTRAL, OK)
    }

    /** The recording: its three message boxes (the scene itself isn't built), and the player has seen it. */
    private suspend fun Dialogue.recording(): Unit = with(BankGuardLines) {
        mesbox(EYES)
        mesbox(PLAYS)
        mesbox(END)
        setBankjob(player, SEEN)
    }
}

/** The Wise Old Man's Talk-to and telescope, Miss Schism's and the bank guard's Talk-to. */
fun registerWiseOldMan(world: World) {
    val give: (Player, Int) -> Unit = { player, obj ->
        world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
    }
    with(WiseOldMan) {
        world.handlers.onNpc(ID, TALK_TO) { op -> world.dialogues.start(this, op.npc) { wom(give) } }
        world.handlers.onNpc(MISS_SCHISM, TALK_TO) { op -> world.dialogues.start(this, op.npc) { missSchism() } }
        world.handlers.onNpc(BANK_GUARD, TALK_TO) { op -> world.dialogues.start(this, op.npc) { bankGuard() } }
        world.handlers.onLoc(TELESCOPE, OBSERVE) {
            val wom = world.npcTypes[ID] ?: return@onLoc
            world.dialogues.start(this) {
                telescope { line -> chatNpc(wom, wom.name, ChatAnim.NEUTRAL, line) }
            }
        }
    }
}
