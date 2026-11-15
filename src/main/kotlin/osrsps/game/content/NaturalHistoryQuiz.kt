package osrsps.game.content

import osrsps.game.FrameSlot
import osrsps.game.Interfaces
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** The natural history quiz's words: the plaques' questions and answers, Orlando's remarks, the messages. */
object NaturalHistoryQuizLines : Lines("natural_history_quiz", "Natural history quiz") {
    /** A display's [questions], each with its three [Question.answers] in the Wiki's order, and its [name] message once done. */
    class DisplayLines(val name: Line, val questions: List<Question>)

    class Question(val question: Line, val answers: List<Line>)

    private fun display(animal: String): DisplayLines = DisplayLines(
        line("${animal}_name", "the $animal plaque once its quiz is done: the game message naming the animal"),
        (1..QUESTIONS).map { q ->
            Question(
                line("${animal}_q$q", "the $animal plaque: question $q of $QUESTIONS"),
                (1..3).map { a -> line("${animal}_q${q}_a$a", "the $animal plaque, question $q: answer $a of 3, in the Wiki's order") },
            )
        },
    )

    const val QUESTIONS = 6

    /** Every display's lines, by the animal's name in [NaturalHistoryQuiz.DISPLAYS]. */
    val DISPLAYS: Map<String, DisplayLines> = NaturalHistoryQuiz.ANIMALS.associateWith { display(it) }

    val NOT_STARTED = line("not_started", "a plaque studied before agreeing to Orlando's quiz: the game message")
    val RIGHT = line("right", "a right answer: Orlando's box")
    val WRONG = line("wrong", "a wrong answer: Orlando's box")
    val KUDOS = line("kudos", "a display's third right answer: the game message")
    val ALL_OF_THEM = line("all_of_them", "a display's third right answer: Orlando's second box")
    val FINISHED = line("finished", "the last display's third right answer: Orlando's third box")
    val COME_OVER = line("come_over", "the last display's third right answer: Orlando's fourth box")
    val XP = line("xp", "the quiz's reward from Orlando: the game message")
}

/**
 * The Varrock Museum's natural history quiz (the Natural History floor): fourteen displays, each with a plaque
 * (`vm_plaque_*`, locs 24605 to 24618, Study) that opens `vm_natural_history` (533) with one of the display's six
 * questions. Three right answers fill the display (`vm_nathis_*_display`, 0 to 3; the client's Kudos List counts it at 3)
 * for 2 Kudos. Orlando Smith starts it (`vm_nathis_agreed`) and, all fourteen done, gives 1,000 Slayer and Hunter XP
 * once (`vm_nathis_reward_given`).
 *
 * From the Wiki's Natural history quiz and Transcript:Orlando Smith (read 2026-11-14) and the 242 cache (osrs-dumps
 * 2026-11-11-rev242: the varbits, the plaques, the interface, enum 1741's order and `vm_kudos_get_status`'s 3).
 *
 * Guessed: which question a plaque asks (any of the six at random, but not the one just answered wrongly or rightly), the
 * answers in the Wiki's order, the interface closing on an answer with Orlando's remark as a chat box, the Kudos message
 * coming before his second box, the reward message's place and the
 * XP given exactly, whatever the XP rate. Left out: the animal's picture on the plaque (its model ids are set by the
 * server and unknown) and the wrong-answer marks (`vm_nathis_wrong_*`), the creature text layer, the natural
 * historians' lectures behind the displays' buttons, and `vm_nathis_got_spiel`.
 */
object NaturalHistoryQuiz {
    const val INTERFACE = 533
    const val DISPLAY_NUMBER = 25
    const val CREATURE_TEXT = 26
    const val QUIZ_TEXT = 27
    const val QUESTION = 28
    val ANSWERS = listOf(29, 30, 31)
    const val CLOSE = 32
    const val ANSWER_OP = 1
    const val STUDY = 1

    const val VARBIT_AGREED = 3671
    const val VARBIT_REWARD_GIVEN = 3688

    /** A right answer to each question needs this many: the display is done at 3. */
    const val DONE = 3
    const val KUDOS_EACH = 2
    const val XP = 1_000

    /** Unsaved: the display and question a player's open plaque asks, as display index * 8 + question + 1; 0 for none. */
    const val ASKING = "natural_history_asking"

    /** Unsaved: the question [display] asked last, plus 1, so the next one differs. */
    private fun lastKey(display: Display) = "natural_history_last_${display.animal}"

    /** A display: its plaque loc, varbit, display number and each question's right answer (0 to 2). */
    class Display(val animal: String, val plaque: Int, val varbit: Int, val number: Int, val right: List<Int>) {
        val lines: NaturalHistoryQuizLines.DisplayLines get() = NaturalHistoryQuizLines.DISPLAYS.getValue(animal)
    }

    /** In the client's Kudos List order (enum 1741 rows 5 to 18), with each display's number from the Wiki. */
    val DISPLAYS = listOf(
        Display("dragon", 24607, 3672, 1, listOf(1, 0, 2, 1, 1, 0)),
        Display("wyvern", 24608, 3681, 2, listOf(0, 0, 2, 1, 2, 1)),
        Display("tortoise", 24606, 3680, 3, listOf(1, 2, 0, 2, 1, 0)),
        Display("lizard", 24605, 3675, 4, listOf(2, 1, 1, 1, 0, 0)),
        Display("leech", 24610, 3685, 5, listOf(0, 2, 1, 0, 1, 2)),
        Display("camel", 24609, 3679, 6, listOf(1, 1, 2, 0, 0, 0)),
        Display("mole", 24611, 3678, 7, listOf(0, 0, 2, 2, 0, 1)),
        Display("penguin", 24612, 3673, 8, listOf(2, 0, 0, 0, 0, 1)),
        Display("terrorbird", 24617, 3683, 9, listOf(0, 1, 2, 1, 1, 2)),
        Display("kalphite", 24618, 3684, 10, listOf(1, 2, 1, 2, 0, 1)),
        Display("monkey", 24615, 3676, 11, listOf(0, 2, 2, 1, 0, 0)),
        Display("seaslug", 24616, 3682, 12, listOf(0, 1, 1, 0, 2, 2)),
        Display("snake", 24614, 3677, 13, listOf(1, 0, 2, 1, 0, 2)),
        Display("snail", 24613, 3674, 14, listOf(2, 1, 0, 1, 1, 0)),
    )

    /** The animals' names, for [NaturalHistoryQuizLines], which is built before [DISPLAYS] can be read. */
    val ANIMALS = listOf("dragon", "wyvern", "tortoise", "lizard", "leech", "camel", "mole", "penguin", "terrorbird", "kalphite", "monkey", "seaslug", "snake", "snail")

    fun agreed(player: Player) = player.vars.getVarbit(VARBIT_AGREED) == 1

    fun done(player: Player, display: Display) = player.vars.getVarbit(display.varbit) >= DONE

    fun doneCount(player: Player) = DISPLAYS.count { done(player, it) }

    fun allDone(player: Player) = doneCount(player) == DISPLAYS.size

    fun rewardGiven(player: Player) = player.vars.getVarbit(VARBIT_REWARD_GIVEN) == 1

    fun isOpen(player: Player) = player.modals[FrameSlot.MAINMODAL] == INTERFACE

    /** Study on [display]'s plaque. */
    fun study(world: World, player: Player, display: Display) {
        when {
            !agreed(player) -> return world.message(player, NaturalHistoryQuizLines.NOT_STARTED)
            done(player, display) -> return world.message(player, display.lines.name)
        }
        val last = player.sessionCharges[lastKey(display)] - 1
        val choices = (0 until NaturalHistoryQuizLines.QUESTIONS).filter { it != last }
        val question = choices[world.random.nextInt(choices.size)]
        ask(world, player, display, question)
    }

    /** Opens the plaque on [question] of [display]. The client keeps a component's text and hiding, so each open sets it all. */
    fun ask(world: World, player: Player, display: Display, question: Int) {
        val interfaces = world.interfaces
        val words = world.textFor(player)
        val lines = display.lines.questions[question]
        interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        interfaces.setText(player, INTERFACE, DISPLAY_NUMBER, display.number.toString())
        interfaces.setHidden(player, INTERFACE, CREATURE_TEXT, true)
        interfaces.setHidden(player, INTERFACE, QUIZ_TEXT, false)
        interfaces.setText(player, INTERFACE, QUESTION, words[lines.question])
        for ((i, component) in ANSWERS.withIndex()) {
            interfaces.setText(player, INTERFACE, component, words[lines.answers[i]])
            interfaces.setEvents(player, INTERFACE, component, -1, -1, Interfaces.IF_EVENT_OP1)
        }
        interfaces.setEvents(player, INTERFACE, CLOSE, -1, -1, Interfaces.IF_EVENT_OP1)
        player.sessionCharges[ASKING] = DISPLAYS.indexOf(display) * 8 + question + 1
        player.sessionCharges[lastKey(display)] = question + 1
    }

    /** What the open plaque asks: its display and question, or null with none open. */
    fun asking(player: Player): Pair<Display, Int>? {
        val asking = player.sessionCharges[ASKING]
        if (asking == 0 || !isOpen(player)) return null
        return DISPLAYS[(asking - 1) / 8] to (asking - 1) % 8
    }

    /**
     * Answer [answer] (0 to 2) clicked: the plaque closes and Orlando says whether it's right. A third right answer fills
     * the display for its Kudos; the last display's says the quiz is done.
     */
    suspend fun ScriptContext.answer(world: World, answer: Int) {
        val (display, question) = asking(player) ?: return
        player.sessionCharges[ASKING] = 0
        world.interfaces.closeModals(player)
        val orlando = world.npcTypes[VarrockMuseumPeople.ORLANDO_SMITH] ?: return
        val right = display.right[question] == answer
        if (right && !done(player, display)) {
            player.vars.setVarbit(display.varbit, player.vars.getVarbit(display.varbit) + 1)
            // `vm_nathis_display_alldone` (3686) is bits 2 to 29 of the same varp, every display at once, so it follows.
            if (done(player, display)) Kudos.refresh(player)
        }
        val filled = right && done(player, display)
        world.dialogues.start(this) {
            with(NaturalHistoryQuizLines) {
                if (!right) return@start chatNpc(orlando, orlando.name, ChatAnim.NEUTRAL, WRONG)
                chatNpc(orlando, orlando.name, ChatAnim.HAPPY, RIGHT)
                if (!filled) return@start
                world.message(player, KUDOS)
                chatNpc(orlando, orlando.name, ChatAnim.HAPPY, ALL_OF_THEM)
                if (!allDone(player)) return@start
                chatNpc(orlando, orlando.name, ChatAnim.HAPPY, FINISHED)
                chatNpc(orlando, orlando.name, ChatAnim.HAPPY, COME_OVER)
            }
        }
    }

    /** Orlando's reward, once: 1,000 Slayer and Hunter XP. False when it's given already or the quiz isn't done. */
    fun reward(world: World, player: Player): Boolean {
        if (!allDone(player) || rewardGiven(player)) return false
        player.vars.setVarbit(VARBIT_REWARD_GIVEN, 1)
        player.skills.addExactTenths(Skill.SLAYER, XP * 10)
        player.skills.addExactTenths(Skill.HUNTER, XP * 10)
        world.message(player, NaturalHistoryQuizLines.XP)
        return true
    }
}

fun registerNaturalHistoryQuiz(world: World) {
    with(NaturalHistoryQuiz) {
        for (display in DISPLAYS) world.handlers.onLoc(display.plaque, STUDY) { study(world, player, display) }
        for ((i, component) in ANSWERS.withIndex()) world.handlers.onButton(INTERFACE, component, ANSWER_OP) { answer(world, i) }
        world.handlers.onButton(INTERFACE, CLOSE) {
            player.sessionCharges[ASKING] = 0
            if (isOpen(player)) world.interfaces.closeModals(player)
        }
    }
}
