package osrsps.game.content.randomevents

import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.content.XpLamp
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext
import osrsps.map.Tile

/** The Quiz Master's lines; the words are in the dialogue file, from his transcript (read 2026-11-19). */
object QuizMasterLines : Lines("quiz_master", "Transcript:Quiz Master") {
    val LUCKY_DAY = line("lucky_day", "Overhead dialogue: upon spawning in, and dialogue 4 while waiting", "name")
    val WAKEY = line("wakey", "Overhead dialogue: while waiting, dialogue 1", "name")
    val TREAT = line("treat", "Overhead dialogue: while waiting, dialogue 2", "name")
    val COME_ALONG = line("come_along", "Overhead dialogue: while waiting, dialogue 3", "name")
    val MAYBE_LATER = line("maybe_later", "Overhead dialogue: if the player does not respond in time")
    val INVITE = line("invite", "Standard dialogue: his question", "name")
    val YES = line("yes", "Standard dialogue: the first option, and the player's words")
    val STUPID = line("stupid", "Standard dialogue: the second option")
    val WELCOME = line("welcome", "Standard dialogue: the first option, his first words")
    val ODD_ONE_OUT = line("odd_one_out", "Standard dialogue: the first option, his second words (the letters coloured)")
    val CONTESTANT = line("contestant", "Standard dialogue: the first option, his last words (the name coloured)", "name")
    val NOT_YOURS = line("not_yours", "If the event is not yours", "name")
    val WRONG = (1..9).map { line("wrong_$it", "Getting a question wrong: dialogue $it") }
    val RIGHT = (1..6).map { line("right_$it", "Getting a question correct: dialogue $it") }
    val WINNER = line("winner", "Getting a fourth question correct: his words (the words coloured)")
    val COINS = line("coins", "Getting a fourth question correct: the first option")
    val BOX = line("box", "Getting a fourth question correct: the second option")
}

/** The mystery box's messages; the words are in the dialogue file, from its transcript (read 2026-11-19). */
object MysteryBoxLines : Lines("mystery_box", "Transcript:Mystery box") {
    val BAGUETTE = line("baguette", "For the stale baguette")
    val BODY_RUNE = line("body_rune", "For body rune")
    val BOOT = line("boot", "For boot")
    val BUCKET = line("bucket", "For bucket")
    val CABBAGE = line("cabbage", "For cabbage")
    val FLYER = line("flyer", "For flyer")
    val ONION = line("onion", "For onion")
    val CASKET = line("casket", "For casket")
    val LAMP = line("lamp", "For a lamp")
    val DIAMOND = line("diamond", "For diamond")
    val SCIMITAR = line("scimitar", "For mithril scimitar")
    val NATURE_RUNES = line("nature_runes", "For nature runes")
    val STEEL_PLATE = line("steel_plate", "For steel plate")
    val NOTHING = line("nothing", "If nothing is rolled on the rare drop table or the clue scroll rolled for is already owned")
}

/**
 * The Quiz Master (OSRS Wiki, Quiz Master and Mystery box pages and their transcripts, read 2026-11-19). He comes as
 * NPC 6755 (macro_magneson_invitation; Talk-to, Dismiss) and asks his player to play; yes takes them to his studio, in
 * the black chair (macro_mind_chair, 1952,4764 on level 1 of map square 30,74, through [EventAreas]) facing him (379,
 * macro_magneson, at 1952,4768 where the Quiz Master page's map pins him). Odd one out: the window macro_quizshow
 * (191, in the chatbox) shows three spinning items, its client script quiz_init making them from the models the server
 * gives; Select on the one that isn't like the others is right. Four right in a row win 1000 coins or a mystery box,
 * the player's pick, and send them back; a wrong one starts the count again, so nobody can fail. The items are the
 * cache's macro_quiz objs, two to a kind (food, weapon, armour, tool, jewel).
 *
 * The mystery box (6199) opens to one of the Wiki's members table: a stale baguette (1/256) first, then the twelve items
 * at 1/15 each, with a message box for each.
 *
 * Guessed: a question showing both items of one kind and one of another, in a random order; the models drawn at the
 * objs' 2D zoom; four right in a row (the Wiki says four in succession); which line each answer gets, at random as the
 * transcript has them; that a quiz dropped (Esc, a click elsewhere) is asked again a moment later, as nothing else is
 * left to open it; the sitting animation (quiz_sit) held for the whole show; his chathead expressions. Left out: the
 * clue scroll (2/15) and rare drop table (1/15) rolls of the mystery box, which give its empty box until clue
 * scrolls and the rare drop table exist; the box's Entrana table; the Victory! jingle and the answer sounds.
 */
object QuizMaster : RandomEvent("quiz_master", 6755) {
    const val HOST = 379
    const val INTERFACE = 191
    const val BUTTONS = 1

    /** quiz_init. */
    const val INIT_SCRIPT = 3799

    /** quiz_sit. */
    const val SIT = 2378

    const val TO_WIN = 4
    const val COINS = 995
    const val COINS_PRIZE = 1000
    const val MYSTERY_BOX = 6199
    const val OPEN = 1

    /** The zones copied: the studio, in map square 30,74. */
    const val FROM_X = 1944
    const val FROM_Z = 4760
    const val WIDTH_ZONES = 2
    const val HEIGHT_ZONES = 2
    const val LEVEL = 1

    val CHAIR = Tile(1952, 4764)
    val HOST_AT = Tile(1952, 4768)

    /** Guessed: how often the studio checks its quiz is still being asked. */
    const val WATCH_TICKS = 3

    /** One of the quiz's items: its obj (for the record), model and 2D zoom (the cache's macro_quiz objs). */
    class Item(val obj: Int, val model: Int, val zoom: Int)

    /** The kinds, two items each: food, weapon, armour, tool, jewel. */
    val KINDS: List<List<Item>> = listOf(
        listOf(Item(6189, 8829, 1370), Item(6190, 8830, 1340)),
        listOf(Item(6191, 8836, 1040), Item(6192, 8828, 1180)),
        listOf(Item(6193, 8833, 640), Item(6194, 8832, 1560)),
        listOf(Item(6195, 8835, 710), Item(6196, 8837, 2030)),
        listOf(Item(6197, 8834, 830), Item(6198, 8831, 620)),
    )

    /** The mystery box's twelve items at 1/15 each, with their messages; the baguette comes first at 1/256. */
    val BOX_TABLE: List<Triple<Int, Int, osrsps.game.dialogue.Line>> = with(MysteryBoxLines) {
        listOf(
            Triple(XpLamp.LAMP, 1, LAMP), Triple(1965, 1, CABBAGE), Triple(1601, 1, DIAMOND), Triple(1925, 1, BUCKET),
            Triple(956, 1, FLYER), Triple(685, 1, BOOT), Triple(559, 1, BODY_RUNE), Triple(1957, 1, ONION),
            Triple(1329, 1, SCIMITAR), Triple(405, 1, CASKET), Triple(1119, 1, STEEL_PLATE), Triple(561, 20, NATURE_RUNES),
        )
    }
    const val BOX_ROLL = 15
    const val BAGUETTE = 20590
    const val BAGUETTE_ROLL = 256

    /** A show: the right answers in a row, and whether it is over. */
    class Show(val host: Npc?) {
        var streak = 0
        var welcomed = false
        var done = false

        /** The question on screen until it is answered, so one dropped with Esc comes back the same (exploit review R64C-4). */
        var asking: Question? = null
    }

    /** One question: the three items shown, and where the odd one is among them. */
    class Question(val shown: List<Item>, val oddAt: Int)

    private const val QUIZ_SCRIPT = "quiz_master_show"

    override val teleports = true
    override val greeting = QuizMasterLines.LUCKY_DAY
    override val nags = with(QuizMasterLines) { listOf(WAKEY, TREAT, COME_ALONG, LUCKY_DAY) }
    override val leaving = QuizMasterLines.MAYBE_LATER
    override val notYours = QuizMasterLines.NOT_YOURS

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean = with(QuizMasterLines) {
        chatNpc(ChatAnim.HAPPY, INVITE, "name" to player.username)
        if (choice(YES, STUPID) != 1) return true
        chatPlayer(ChatAnim.HAPPY, YES)
        val away = events.areas.takeAway(player, this@QuizMaster, FROM_X, FROM_Z, WIDTH_ZONES, HEIGHT_ZONES, CHAIR, LEVEL) ?: return true
        val show = Show(spawnHost(world, away))
        away.state = show
        // Seated facing the host, whichever way they turned to the invitation (the teleport clears facing; Codex on #995).
        show.host?.let { world.npcInteractions.face(player, it) }
        player.anim(SIT)
        ask(world, player)
        watch(world, player, away, show)
        true
    }

    private fun spawnHost(world: World, away: EventAreas.Away): Npc? {
        val type = world.npcTypes[HOST] ?: return null
        val tile = away.inside(HOST_AT.x, HOST_AT.z)
        return world.spawnNpc(NpcSpawn(type, tile.x, tile.z, LEVEL, source = "quiz show for ${away.owner}", respawns = false))
    }

    fun showOf(world: World, player: Player): Pair<EventAreas.Away, Show>? {
        val away = world.randomEvents.areas.awayOf(player) ?: return null
        val show = away.state as? Show ?: return null
        return away to show
    }

    /** Queues the show's talk: the welcome the first time, then questions until four are right. */
    private fun ask(world: World, player: Player) {
        world.scripts.queue(player, QUIZ_SCRIPT, QueueType.NORMAL, 0) { run(world) }
    }

    private suspend fun ScriptContext.run(world: World) {
        val (away, show) = showOf(world, player) ?: return
        if (show.done) return
        world.dialogues.start(this, show.host) {
            with(QuizMasterLines) {
                val host = show.host
                suspend fun say(anim: ChatAnim, text: String) {
                    if (host != null) chatNpc(host, anim, text) else world.npcTypes[HOST]?.let { chatNpc(it, it.name, anim, text) }
                }
                if (!show.welcomed) {
                    say(ChatAnim.HAPPY, text(WELCOME))
                    say(ChatAnim.HAPPY, text(ODD_ONE_OUT))
                    say(ChatAnim.HAPPY, text(CONTESTANT, "name" to player.username))
                    show.welcomed = true
                }
                while (show.streak < TO_WIN) {
                    val right = question(world, this@start, show)
                    if (right) {
                        show.streak++
                        if (show.streak < TO_WIN) say(ChatAnim.HAPPY, text(RIGHT[world.random.nextInt(RIGHT.size)]))
                    } else {
                        show.streak = 0
                        say(ChatAnim.ANGRY, text(WRONG[world.random.nextInt(WRONG.size)]))
                    }
                }
                say(ChatAnim.HAPPY, text(WINNER))
                val coins = choice(COINS, BOX) == 1
                show.done = true
                player.anim(-1)
                world.randomEvents.areas.bringBack(player, away)
                if (coins) world.randomEvents.reward(player, QuizMaster.COINS, COINS_PRIZE) else world.randomEvents.reward(player, MYSTERY_BOX)
            }
        }
    }

    /** Both items of one kind and one of another, in a random order. */
    private fun newQuestion(world: World): Question {
        val pair = world.random.nextInt(KINDS.size)
        val oddKind = (pair + 1 + world.random.nextInt(KINDS.size - 1)) % KINDS.size
        val odd = KINDS[oddKind][world.random.nextInt(2)]
        val shown = KINDS[pair].toMutableList()
        val oddAt = world.random.nextInt(3)
        shown.add(oddAt, odd)
        return Question(shown, oddAt)
    }

    /** The question on screen, or a new one; true when the odd one is picked. Dropped with Esc, it is kept for next time. */
    private suspend fun question(world: World, dialogue: Dialogue, show: Show): Boolean {
        val player = dialogue.player
        val q = show.asking ?: newQuestion(world).also { show.asking = it }
        world.interfaces.openChatModal(player, INTERFACE, unclamp = null)
        player.session.queue(IfSetEventsV2(INTERFACE, BUTTONS, 0, 2, 0, 1))
        player.session.queue(RunClientScript(INIT_SCRIPT, listOf((INTERFACE shl 16) or BUTTONS) + q.shown.flatMap { listOf(it.model, it.zoom) }))
        val (_, sub) = dialogue.context.pauseButtons(INTERFACE, listOf(BUTTONS), 0..2)
        world.interfaces.closeChatModal(player)
        show.asking = null
        return sub == q.oddAt
    }

    /** The studio's check: a quiz dropped is asked again; a player gone some other way leaves the show behind. */
    private fun watch(world: World, player: Player, away: EventAreas.Away, show: Show) {
        world.npcScripts.later(WATCH_TICKS) {
            if (show.done) return@later
            if (world.randomEvents.areas.awayOf(player) !== away) {
                show.done = true
                return@later
            }
            val scripts = player.scripts
            if (!scripts.waitingForDialogue && !scripts.delayed && scripts.queue.none { it.name == QUIZ_SCRIPT }) ask(world, player)
            watch(world, player, away, show)
        }
    }

    /** Open on a mystery box: one roll of its table, and its message. */
    suspend fun ScriptContext.openBox(world: World, slot: Int) {
        if (player.inventory[slot]?.id != MYSTERY_BOX) return
        player.inventory.remove(slot, 1)
        val (id, count, line) = if (world.random.nextInt(BAGUETTE_ROLL) == 0) Triple(BAGUETTE, 1, MysteryBoxLines.BAGUETTE) else {
            BOX_TABLE.getOrNull(world.random.nextInt(BOX_ROLL)) ?: Triple(-1, 0, MysteryBoxLines.NOTHING)
        }
        if (id >= 0) world.randomEvents.reward(player, id, count)
        world.dialogues.start(this) { mesbox(line) }
    }

    /** The interface's gameval names, checked against the cache. */
    val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
        get() = mapOf(INTERFACE to ("macro_quizshow" to mapOf(0 to "background", BUTTONS to "buttons", 2 to "com_2")))

    fun register(world: World) {
        world.handlers.onObj(MYSTERY_BOX, OPEN) { op -> openBox(world, op.sub) }
    }
}
