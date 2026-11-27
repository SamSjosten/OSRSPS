package osrsps.game.content.randomevents

import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.content.XpLamp
import osrsps.game.content.XpLampLines
import osrsps.game.content.message
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext
import osrsps.map.Tile

/** The dunce's and Mr. Mordaut's lines; the words are in the dialogue file, from their transcripts (read 2026-11-19). */
object SurpriseExamLines : Lines("surprise_exam", "Transcript:Dunce and Transcript:Mr. Mordaut") {
    val SURPRISE = line("surprise", "Dunce, overhead: upon spawning in, and dialogue 1 while waiting", "name")
    val COME_TO_SCHOOL = line("come_to_school", "Dunce, overhead: dialogue 2", "name")
    val GETTING_TESTED = line("getting_tested", "Dunce, overhead: dialogue 3", "name")
    val TEACHER_WANTS = line("teacher_wants", "Dunce, overhead: dialogue 4", "name")
    val KEPT_WAITING = line("kept_waiting", "Dunce, overhead: dialogue 5", "name")
    val MAYBE_LATER = line("maybe_later", "Dunce, overhead: if the player does not respond in time")
    val INVITE = line("invite", "Dunce, standard dialogue: his words", "name")
    val SURE = line("sure", "Dunce, standard dialogue: the first option, and the player's words")
    val DONT_WANT = line("dont_want", "Dunce, standard dialogue: the second option, and the player's words")
    val NOT_THE_ONE = line("not_the_one", "Other people's Dunce")
    val SLACKING = line("slacking", "Mr. Mordaut, if the player accepts: his first words", "name")
    val TWO_TYPES = line("two_types", "Mr. Mordaut, if the player accepts: his second words")
    val FIFTEEN_CARDS = line("fifteen_cards", "Mr. Mordaut, if the player accepts: his third words")
    val PICK_NEXT = line("pick_next", "Mr. Mordaut, question 1: his words")
    val WRONG = listOf(
        line("wrong_1", "Mr. Mordaut, answered incorrectly: dialogue 1"),
        line("wrong_2", "Mr. Mordaut, answered incorrectly: dialogue 2"),
        line("wrong_3", "Mr. Mordaut, answered incorrectly: dialogue 3"),
    )
    val REMEMBER = line("remember", "Mr. Mordaut, answered incorrectly: if it's the 1st incorrect answer")
    val FAILED = line("failed", "Mr. Mordaut, the 3rd incorrect answer: his first words")
    val RIGHT = listOf(
        line("right_1", "Mr. Mordaut, answered correctly: dialogue 1"),
        line("right_2", "Mr. Mordaut, answered correctly: dialogue 2 (the male form)"),
        line("right_3", "Mr. Mordaut, answered correctly: dialogue 3"),
    )
    val WELL_DONE_BOOK = line("well_done_book", "Mr. Mordaut, the 3rd correct answer, without a Book of Knowledge")
    val WELL_DONE = line("well_done", "Mr. Mordaut, the 3rd correct answer, with a Book of Knowledge: his first words")
    val ALREADY_HAVE = line("already_have", "Mr. Mordaut, the 3rd correct answer, with a Book of Knowledge: his second words")
    val TO_EXIT = line("to_exit", "Mr. Mordaut: to exit (after passing or failing)")
}

/**
 * The Surprise Exam (OSRS Wiki, Surprise Exam, Mr. Mordaut, Dunce, Door (Surprise Exam) and Book of Knowledge pages and
 * the dunce's and Mr. Mordaut's transcripts, read 2026-11-19; the RuneScape Wiki's Surprise Exam page for the question
 * types). The dunce comes as NPC 6749 (pattern_invitation; Talk-to, Dismiss) and takes his player to the classroom
 * (map square 29,78, through [EventAreas]), where Mr. Mordaut (5504, pattern_teacher, size 5, at his desk) explains the
 * exam and asks what comes next: three objects of a theme in pattern_next (103) and four to pick from, one of the
 * same theme. Three right pass; three wrong fail; a wrong answer gets the same question again, a right one a new one.
 * Any of the four doors (9316 to 9319) lets the player out at any time; after passing, they get a Book of Knowledge
 * (11640), or, with one already owned, one of the Wiki's other rewards. Reading the book opens the experience window,
 * for 15 times the chosen skill's level.
 *
 * Guessed: which card models make each theme (sorted by eye from untextured renders of the models in enum_1922, which
 * the cards' client script reads for each model's zoom); the models drawn through client script pattern_cards_setmodel
 * in pattern_next too; where the player lands; the exam being asked straight after the welcome and again on Talk-to if
 * the window is shut; the REMEMBER line after the first wrong answer whichever of the three wrong lines came first;
 * which other reward comes when a book is owned (each as likely; the Wiki says its list may be incomplete); the book's
 * window title and message being the lamp's. Left out: the fifteen-card question (pattern_cards), until its hints are
 * recorded (the one hint on the Wiki can't make a question on its own); the classmates (the giant, the goblins, the
 * zombie, the mummy, the rat); the book's Destroy and its refusal at the bank; School's Out and the jingles.
 */
object SurpriseExam : RandomEvent("surprise_exam", 6749) {
    const val TEACHER = 5504
    const val DUNCE = 5503
    val DOORS = listOf(9316, 9317, 9318, 9319)
    const val OPEN = 1

    const val INTERFACE = 103
    const val CLOSE = 5
    val SHOWN = listOf(10, 11, 12)
    val OPTIONS = listOf(15, 17, 19, 21)

    /** pattern_cards_setmodel. */
    const val SETMODEL_SCRIPT = 52

    const val TO_PASS = 3
    const val TO_FAIL = 3

    const val BOOK = 11640
    const val READ = 1
    const val BOOK_TIMES = 15

    /** The zones copied: the classroom, in map square 29,78. */
    const val FROM_X = 1872
    const val FROM_Z = 5008
    const val WIDTH_ZONES = 3
    const val HEIGHT_ZONES = 4

    /** Guessed: where the player lands. Mr. Mordaut's south-west tile (the Wiki pins his centre) and the dunce's stool. */
    val ARRIVE = Tile(1886, 5028)
    val TEACHER_AT = Tile(1884, 5020)
    val DUNCE_AT = Tile(1890, 5018)

    /** The themes (guessed, see the class comment): fish, food, drink and headgear. */
    val THEMES: List<List<Int>> = listOf(
        listOf(41163, 41173, 41180, 41193, 41204, 41206, 41209),
        listOf(41161, 41191, 41202, 41214, 41226, 41230),
        listOf(41149, 41152, 41162, 41175, 41219, 41225),
        listOf(41148, 41164, 41179, 41187, 41189),
    )

    /** The other rewards when a book is owned (the Surprise Exam page): coins, talisman, uncut gems and the key halves. */
    val OTHER_REWARDS: List<Pair<Int, Int>> = listOf(
        995 to 20, 995 to 40, 995 to 80, 995 to 160, 995 to 320, 995 to 640, 1454 to 1,
        1623 to 1, 1621 to 1, 1619 to 1, 1617 to 1, 987 to 1, 985 to 1,
    )

    /** One question: the three shown, the four to pick from, and which of those is right. */
    class Question(val shown: List<Int>, val options: List<Int>, val right: Int)

    /** A stay in the classroom. */
    class Class(val teacher: Npc?) {
        var right = 0
        var wrong = 0
        var question: Question? = null
        val passed get() = right >= TO_PASS
        val failed get() = wrong >= TO_FAIL
        val over get() = passed || failed
        var left = false
    }

    private const val EXAM_SCRIPT = "surprise_exam"

    override val teleports = true
    override val greeting = SurpriseExamLines.SURPRISE
    override val nags = with(SurpriseExamLines) { listOf(SURPRISE, COME_TO_SCHOOL, GETTING_TESTED, TEACHER_WANTS, KEPT_WAITING) }
    override val leaving = SurpriseExamLines.MAYBE_LATER
    override val notYours = SurpriseExamLines.NOT_THE_ONE

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean = with(SurpriseExamLines) {
        chatNpc(ChatAnim.NEUTRAL, INVITE, "name" to player.username)
        if (choice(SURE, DONT_WANT) != 1) {
            chatPlayer(ChatAnim.NEUTRAL, DONT_WANT)
            return true
        }
        chatPlayer(ChatAnim.HAPPY, SURE)
        val away = events.areas.takeAway(player, this@SurpriseExam, FROM_X, FROM_Z, WIDTH_ZONES, HEIGHT_ZONES, ARRIVE) ?: return true
        val room = Class(spawn(world, away, TEACHER, TEACHER_AT))
        spawn(world, away, DUNCE, DUNCE_AT)
        away.state = room
        world.scripts.queue(player, EXAM_SCRIPT, QueueType.NORMAL, 0) { exam(world, welcome = true) }
        true
    }

    private fun spawn(world: World, away: EventAreas.Away, id: Int, at: Tile): Npc? {
        val type = world.npcTypes[id] ?: return null
        val tile = away.inside(at.x, at.z)
        return world.spawnNpc(NpcSpawn(type, tile.x, tile.z, 0, source = "classroom for ${away.owner}", respawns = false))
    }

    fun classOf(world: World, player: Player): Pair<EventAreas.Away, Class>? {
        val away = world.randomEvents.areas.awayOf(player) ?: return null
        val room = away.state as? Class ?: return null
        return away to room
    }

    /** The exam: the welcome the first time, then questions until it is passed or failed; afterwards, the way out. */
    private suspend fun ScriptContext.exam(world: World, welcome: Boolean) {
        val (_, room) = classOf(world, player) ?: return
        world.dialogues.start(this, room.teacher) {
            with(SurpriseExamLines) {
                suspend fun say(anim: ChatAnim, text: String) {
                    val teacher = room.teacher
                    if (teacher != null) chatNpc(teacher, anim, text) else world.npcTypes[TEACHER]?.let { chatNpc(it, it.name, anim, text) }
                }
                if (room.over) return@start say(ChatAnim.NEUTRAL, text(TO_EXIT))
                if (welcome) {
                    say(ChatAnim.NEUTRAL, text(SLACKING, "name" to player.username))
                    say(ChatAnim.NEUTRAL, text(TWO_TYPES))
                    say(ChatAnim.NEUTRAL, text(FIFTEEN_CARDS))
                }
                while (!room.over) {
                    val question = room.question ?: newQuestion(world).also { room.question = it }
                    say(ChatAnim.NEUTRAL, text(PICK_NEXT))
                    val picked = ask(world, this@start, question)
                    if (picked == question.right) {
                        room.right++
                        room.question = null
                        if (room.passed) break
                        say(ChatAnim.HAPPY, text(RIGHT[world.random.nextInt(RIGHT.size)]))
                    } else {
                        room.wrong++
                        if (room.failed) break
                        say(ChatAnim.ANGRY, text(WRONG[world.random.nextInt(WRONG.size)]))
                        if (room.wrong == 1) say(ChatAnim.NEUTRAL, text(REMEMBER))
                    }
                }
                when {
                    room.failed -> say(ChatAnim.ANGRY, text(FAILED))
                    ownsBook(player) -> {
                        say(ChatAnim.HAPPY, text(WELL_DONE))
                        say(ChatAnim.NEUTRAL, text(ALREADY_HAVE))
                    }
                    else -> say(ChatAnim.HAPPY, text(WELL_DONE_BOOK))
                }
                say(ChatAnim.NEUTRAL, text(TO_EXIT))
            }
        }
    }

    /** Three of a theme shown, and four to pick from: a fourth of the theme and three of the others, in a random order. */
    private fun newQuestion(world: World): Question {
        val theme = world.random.nextInt(THEMES.size)
        val own = THEMES[theme].shuffledBy(world)
        val others = THEMES.withIndex().filter { it.index != theme }.flatMap { it.value }.shuffledBy(world)
        val options = (listOf(own[3]) + others.take(3)).shuffledBy(world)
        return Question(own.take(3), options, options.indexOf(own[3]))
    }

    private fun <T> List<T>.shuffledBy(world: World): List<T> {
        val list = toMutableList()
        for (i in list.indices.reversed()) {
            val j = world.random.nextInt(i + 1)
            list[i] = list[j].also { list[j] = list[i] }
        }
        return list
    }

    /** The window for [question]; returns the option picked (0 to 3). */
    private suspend fun ask(world: World, dialogue: Dialogue, question: Question): Int {
        val player = dialogue.player
        // A tick first: the Continue that got here closes the chatbox as it returns, which would drop a window opened now.
        dialogue.delay(1)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        for (button in OPTIONS + CLOSE) player.session.queue(IfSetEventsV2(INTERFACE, button, -1, -1, 0, 1))
        for ((i, model) in question.shown.withIndex()) player.session.queue(RunClientScript(SETMODEL_SCRIPT, listOf((INTERFACE shl 16) or SHOWN[i], model)))
        for ((i, model) in question.options.withIndex()) player.session.queue(RunClientScript(SETMODEL_SCRIPT, listOf((INTERFACE shl 16) or OPTIONS[i], model)))
        val (component, _) = dialogue.context.pauseButtons(INTERFACE, OPTIONS, -1..-1)
        world.interfaces.closeModals(player)
        return OPTIONS.indexOf(component)
    }

    private fun ownsBook(player: Player) = player.inventory.count(BOOK) > 0 || player.bank.count(BOOK) > 0

    /** Talk-to on Mr. Mordaut: the exam where it was left, or the way out. */
    suspend fun ScriptContext.talkToTeacher(world: World, npc: Npc) {
        val (_, room) = classOf(world, player) ?: return
        if (npc !== room.teacher) return
        exam(world, welcome = false)
    }

    /** Open on a door: home, with the reward once passed. */
    fun leave(world: World, player: Player) {
        val (away, room) = classOf(world, player) ?: return
        if (room.left) return
        room.left = true
        val passed = room.passed
        world.interfaces.closeModals(player)
        world.randomEvents.areas.bringBack(player, away)
        if (!passed) return
        if (!ownsBook(player)) return run { world.randomEvents.reward(player, BOOK) }
        val (id, count) = OTHER_REWARDS[world.random.nextInt(OTHER_REWARDS.size)]
        world.randomEvents.reward(player, id, count)
    }

    /** Read on the book: the experience window, then 15 times the chosen skill's level, if the book is still there. */
    suspend fun ScriptContext.read(world: World, slot: Int) {
        val skill = with(XpLamp) { pick(world, world.textFor(player)[XpLampLines.TITLE]) }
        world.interfaces.closeModals(player)
        val at = if (player.inventory[slot]?.id == BOOK) slot else (0 until player.inventory.capacity).firstOrNull { player.inventory[it]?.id == BOOK } ?: return
        player.inventory.remove(at, 1)
        val tenths = XpLamp.tenths(player, skill, BOOK_TIMES)
        player.skills.addExactTenths(skill, tenths)
        world.message(player, XpLampLines.GRANTED, "xp" to tenths / 10, "skill" to skill.displayName)
    }

    /** The interface's gameval names, checked against the cache. */
    val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
        get() = mapOf(
            INTERFACE to ("pattern_next" to mapOf(
                CLOSE to "com_5", 10 to "0", 11 to "1", 12 to "2", 15 to "select_0", 17 to "select_1", 19 to "select_2", 21 to "select_3",
            )),
        )

    /** The classroom's locs whose handlers work in its copies. */
    val INSTANCE_LOCS: Set<Int> = DOORS.toSet()

    fun register(world: World) {
        for (door in DOORS) world.handlers.onLoc(door, OPEN) { leave(world, player) }
        world.handlers.onNpc(TEACHER, RandomEvents.TALK_TO) { op -> talkToTeacher(world, op.npc ?: return@onNpc) }
        world.handlers.onButton(INTERFACE, CLOSE) { world.interfaces.closeModals(player) }
        world.handlers.onObj(BOOK, READ) { op -> read(world, op.sub) }
    }
}
