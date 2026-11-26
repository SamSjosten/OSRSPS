package osrsps.game.content.randomevents

import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.content.message
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape
import osrsps.map.Tile

/** Postie Pete's lines for the Evil twin; the words are in the dialogue file, from his transcript (read 2026-11-19). */
object PostiePeteTwinLines : Lines("evil_twin_postie", "Transcript:Postie Pete") {
    val MESSAGE_FOR_YOU = line("message_for_you", "Evil twin, overhead: upon spawning in", "name")
    val HELLO = line("hello", "Evil twin, overhead: dialogue 1", "name")
    val URGENT = line("urgent", "Evil twin, overhead: dialogue 2", "name")
    val SOMEONE = line("someone", "Evil twin, overhead: dialogue 3", "name")
    val ACCEPT = line("accept", "Evil twin, overhead: dialogue 4", "name")
    val MAYBE_LATER = line("maybe_later", "Evil twin, overhead: if the player does not respond in time")
    val NEEDS_HELP = line("needs_help", "Evil twin: his question", "name")
    val SURE = line("sure", "Evil twin: the first option, and the player's words")
    val STUFFED = line("stuffed", "Evil twin: the second option, and the player's words")
    val NO_MESSAGES = line("no_messages", "Evil twin: if he is interested in another player")
}

/** Molly's lines; the words are in the dialogue file, from her transcript (read 2026-11-19). */
object MollyLines : Lines("molly", "Transcript:Molly") {
    val WAIT = line("wait", "Walking through the door without talking to her: her question")
    val YES_I_KNOW_OPTION = line("yes_i_know_option", "The door: the first option")
    val YES_I_KNOW = line("yes_i_know", "The door: the first option, the player's words")
    val GOOD_LUCK_THEN = line("good_luck_then", "The door: the first option, her answer")
    val NO_I_DONT = line("no_i_dont", "The door: the second option, and the player's words")
    val COME_OVER = line("come_over", "The door: the second option, her answer")
    val WANT_TO_LEAVE = line("want_to_leave", "The third option, and the player's words")
    val THERE_I_WAS = line("there_i_was", "Leaving: her answer")
    val THANKS_FOR_COMING = line("thanks_for_coming", "Talking after first arriving: her first line")
    val WHATS_THE_PROBLEM = line("whats_the_problem", "Talking after first arriving: the player's question")
    val EVIL_SISTER = line("evil_sister", "Talking after first arriving: her second line")
    val TO_DO_WITH_ME = line("to_do_with_me", "Talking after first arriving: the player's second line")
    val GLAD_YOU_ASKED = line("glad_you_asked", "Talking after first arriving: her third line")
    val THROUGH_THAT_DOOR = line("through_that_door", "Talking after first arriving: the room and the claw")
    val LURED = line("lured", "Talking after first arriving: her sister and the civilians")
    val WHAT_DO_I_DO = line("what_do_i_do", "The player's question: what to do")
    val CATCH_MY_SISTER = line("catch_my_sister", "Her answer: catch her sister")
    val NO_MORE_BOTHER = line("no_more_bother", "Talking after first arriving: once she's in prison")
    val EASY_ENOUGH = line("easy_enough", "The player's answer: easy enough")
    val TAKE_A_LOOK = line("take_a_look", "Her line: take a good long look")
    val TWO_ATTEMPTS = line("two_attempts", "Her line: two attempts")
    val DO_MY_BEST = line("do_my_best", "The player's answer: I'll do my best")
    val CONTROLS = line("controls", "Her question: the controls")
    val YES_PLEASE_OPTION = line("yes_please_option", "The controls: the first option")
    val YES_PLEASE = line("yes_please", "The controls: the first option, the player's words")
    val GLOWING_MARK = line("glowing_mark", "The controls: her first line")
    val CLICK_DIRECTION = line("click_direction", "The controls: her second line")
    val LEVER_POINTS = line("lever_points", "The controls: her third line")
    val HIT_THE_BUTTON = line("hit_the_button", "The controls: her fourth line")
    val BE_CAREFUL = line("be_careful", "The controls: her fifth line")
    val MY_SISTER = line("my_sister", "The controls: her sixth line")
    val DOES_THAT_HELP = line("does_that_help", "The controls: her question")
    val COVERS_EVERYTHING = line("covers_everything", "The controls: the player's answer")
    val GOOD_LUCK = line("good_luck", "The controls: her last line")
    val NO_THANKS_OPTION = line("no_thanks_option", "The controls: the second option")
    val NO_THANKS = line("no_thanks", "The controls: the second option, the player's words")
    val AGAIN = line("again", "Talking again before entering the other room: the player's question")
    val ONE_MORE_TIME = line("one_more_time", "Talking again: her first line")
    val THROUGH_THAT_DOOR_AGAIN = line("through_that_door_again", "Talking again: the room and the claw")
    val LURED_AGAIN = line("lured_again", "Talking again: her sister and the civilians")
    val NO_MORE_BOTHER_AGAIN = line("no_more_bother_again", "Talking again: once she's in prison")
    val FINISH_FIRST = line("finish_first", "Walking back through the door before catching the twin")
    val OKAY = line("okay", "Walking back: the first option, and the player's words")
    val BUT_LEAVE = line("but_leave", "Walking back: the second option, and the player's words")
    val USE_THE_PANEL = line("use_the_panel", "Clicking Grab on the civilians")
    val TRIED_YOUR_BEST = line("tried_your_best", "After grabbing two innocent civilians")
    val SORRY = line("sorry", "Talking after failing: the player's line")
    val INCOMPETENCE = line("incompetence", "Talking after failing: her first line")
    val OUT_OF_MY_SIGHT = line("out_of_my_sight", "Talking after failing: her last line")
    val WELL_DONE = line("well_done", "After grabbing the evil twin: her first line")
    val COME_NEXT_DOOR = line("come_next_door", "After grabbing the evil twin: her second line")
    val IN_THE_CAGE = line("in_the_cage", "Talking after capturing the twin: the player's line")
    val FANTASTIC = line("fantastic", "Talking after capturing the twin: her first line")
    val TAKE_THIS = line("take_this", "Talking after capturing the twin: her last line")
    val GIVEN_SAPPHIRES = line("given_sapphires", "The reward box, uncut sapphires", "count")
    val GIVEN_EMERALDS = line("given_emeralds", "The reward box, uncut emeralds", "count")
    val GIVEN_RUBIES = line("given_rubies", "The reward box, uncut rubies", "count")
    val GIVEN_DIAMONDS = line("given_diamonds", "The reward box, uncut diamonds", "count")
    val OVER_THE_CAGE = line("over_the_cage", "Evil twin page trivia: the message on lowering the claw over the cage")
}

/**
 * The Evil twin (OSRS Wiki, Evil twin, Molly, Suspect, Control panel (Evil twin), Evil claw and Postie Pete pages and the
 * transcripts of Molly and Postie Pete, read 2026-11-19). Postie Pete comes as NPC 6738 (grab_postman; Talk-to,
 * Dismiss) with a message from Molly; yes takes the player to her rooms (map square 29,80, [EventAreas]). Molly comes
 * in one of 20 looks (five builds, grab_good_twin_one to _five, each in red, green, blue or yellow); her twin is the
 * suspect (grab_evil_twin_*) of the same look, penned with others below the control room. The control panel (20813)
 * opens the claw's controls (macro_evil_twin) beside the screen: each arrow moves the claw (20811, its glowing mark
 * 20812 under it) a tile, and Grab drops it on the mark. Two catches are allowed: the twin, and Molly sends the player
 * home with 2 to 4 noted uncut gems of one kind (sapphires, emeralds, rubies or diamonds, a quarter each); two
 * civilians, and she sends them to one of the Wiki's teleport spots (the shared and members ones). The door between
 * her room and the control room checks the player through; leaving (LEAVE) gives nothing.
 *
 * Guessed: the four others in the pen (other looks, at random, all different); where everyone stands and how far the
 * suspects wander; where a caught suspect is put (inside the cage); the controls opening in the side modal; that an
 * empty grab takes no attempt; the claw's range (the pen's tiles, the cage included). Inferred: crossing the door
 * once the player knows what they are doing, or once the catching is over; the number of gems from the Wiki's drop
 * table (2 to 4), where Molly's page and transcript say 1 to 5. Left out: the claw's lowering and lifting (the
 * grab_evil_twin_with_claw_* forms), the dark room until the catching is done, the jingle, the tries shown on the
 * controls, and the prayer pause.
 */
object EvilTwin : RandomEvent("evil_twin", 6738) {
    const val PANEL = 20813
    const val CLAW = 20811
    const val MARK = 20812
    const val DOOR = 20817
    const val USE = 1
    const val OPEN = 1
    const val GRAB_OP = 1
    const val ATTEMPTS = 2

    const val INTERFACE = 277
    const val GRAB = 3
    const val MOVE_RIGHT = 7
    const val MOVE_UP = 8
    const val MOVE_LEFT = 9
    const val MOVE_DOWN = 10

    enum class Colour { RED, GREEN, BLUE, YELLOW }

    /** Molly's ids by build (one to five) then colour, and the suspects' likewise. */
    val MOLLY = listOf(
        listOf(342, 5464, 5474, 352), listOf(5478, 5471, 356, 5476), listOf(5480, 5467, 5485, 5486),
        listOf(361, 362, 363, 5487), listOf(364, 365, 366, 367),
    )
    val SUSPECTS = listOf(
        listOf(5468, 5469, 5470, 357), listOf(349, 350, 351, 358), listOf(346, 347, 5482, 5483),
        listOf(5484, 5465, 343, 344), listOf(345, 354, 355, 359),
    )
    val MOLLIES = MOLLY.flatten()
    val ALL_SUSPECTS = SUSPECTS.flatten()
    const val PEN_SIZE = 5

    /** The reward gems, noted: sapphire, emerald, ruby, diamond. */
    val GEMS = listOf(1624, 1622, 1620, 1618)
    private val GIVEN = with(MollyLines) { listOf(GIVEN_SAPPHIRES, GIVEN_EMERALDS, GIVEN_RUBIES, GIVEN_DIAMONDS) }
    const val GEMS_MIN = 2
    const val GEMS_MAX = 4

    /** The Wiki's teleport spots for a failed catch, the shared and the members ones. */
    val FAIL_SPOTS = listOf(
        Triple(3279, 3500, 1), Triple(3097, 3266, 1), Triple(2954, 3388, 3), Triple(3221, 3474, 3), Triple(3229, 3223, 1),
        Triple(3097, 9567, 0), Triple(3287, 3190, 2), Triple(2964, 3213, 1), Triple(2519, 3426, 1), Triple(2700, 3403, 2),
        Triple(2536, 3076, 1), Triple(2434, 3346, 2), Triple(2787, 3515, 0), Triple(2616, 3292, 1), Triple(2898, 3423, 1),
        Triple(2708, 3472, 1), Triple(2663, 3111, 0), Triple(2651, 3163, 1), Triple(2772, 3235, 0), Triple(2613, 3208, 1),
        Triple(2207, 4934, 1),
    )

    /**
     * A failed catch's spot: one of [FAIL_SPOTS] at random, else, when that one is blocked, the next one along that
     * isn't; null when none is free, and the player goes back where they came from instead. Exploit review R51 C-3:
     * the spots are entered by hand, so a wrong or blocked one mustn't strand them. Guessed: the fallbacks.
     */
    fun failSpot(world: World): Triple<Int, Int, Int>? {
        val start = world.random.nextInt(FAIL_SPOTS.size)
        for (i in FAIL_SPOTS.indices) {
            val spot = FAIL_SPOTS[(start + i) % FAIL_SPOTS.size]
            if (world.canStand(spot.first, spot.second, spot.third)) return spot
        }
        return null
    }

    /** The zones copied: Molly's rooms and the pen, of map square 29,80. */
    const val FROM_X = 1856
    const val FROM_Z = 5120
    const val WIDTH_ZONES = 3
    const val HEIGHT_ZONES = 3

    val ARRIVE = Tile(1862, 5136)
    val MOLLY_AT = Tile(1860, 5137)
    /** The door's x. Its wall is on the east side of this tile (rotation 2), so the tile itself is in Molly's room. */
    const val DOOR_X = 1864
    val MOLLY_SIDE = Tile(1863, 5137)
    val PANEL_SIDE = Tile(1865, 5137)
    val CLAW_START = Tile(1870, 5132)
    val CLAW_X = 1865..1875
    val CLAW_Z = 5123..5132
    val CAGE_X = 1865..1867
    val CAGE_Z = 5123..5125
    val CAGE_AT = Tile(1866, 5124)
    val SUSPECTS_AT = listOf(Tile(1869, 5127), Tile(1872, 5129), Tile(1874, 5126), Tile(1870, 5130), Tile(1873, 5131))
    const val SUSPECT_WANDER = 3

    /** A stay in Molly's rooms: her look, the pen, the claw, and how the catching went. */
    class Rooms(val molly: Npc?, val build: Int, val colour: Colour, val suspects: List<Npc>) {
        val twinId: Int get() = SUSPECTS[build][colour.ordinal]
        var claw = CLAW_START
        var briefed = false
        var caught = 0
        var gotTwin = false
        val over: Boolean get() = gotTwin || caught >= ATTEMPTS
    }

    override val teleports = true
    override val greeting = PostiePeteTwinLines.MESSAGE_FOR_YOU
    override val nags = with(PostiePeteTwinLines) { listOf(HELLO, URGENT, SOMEONE, ACCEPT) }
    override val leaving = PostiePeteTwinLines.MAYBE_LATER
    override val notYours = PostiePeteTwinLines.NO_MESSAGES

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean = with(PostiePeteTwinLines) {
        chatNpc(ChatAnim.NEUTRAL, NEEDS_HELP, "name" to player.username)
        if (choice(SURE, STUFFED) != 1) {
            chatPlayer(ChatAnim.ANGRY, STUFFED)
            return true
        }
        chatPlayer(ChatAnim.HAPPY, SURE)
        val away = events.areas.takeAway(player, this@EvilTwin, FROM_X, FROM_Z, WIDTH_ZONES, HEIGHT_ZONES, ARRIVE) ?: return true
        val build = world.random.nextInt(MOLLY.size)
        val colour = Colour.entries[world.random.nextInt(Colour.entries.size)]
        val others = ALL_SUSPECTS.filter { it != SUSPECTS[build][colour.ordinal] }.toMutableList()
        val pen = mutableListOf(SUSPECTS[build][colour.ordinal])
        while (pen.size < PEN_SIZE) pen += others.removeAt(world.random.nextInt(others.size))
        for (i in pen.size - 1 downTo 1) {
            val j = world.random.nextInt(i + 1)
            pen[i] = pen[j].also { pen[j] = pen[i] }
        }
        val suspects = pen.withIndex().mapNotNull { (i, id) -> spawn(world, away, SUSPECTS_AT[i], id, SUSPECT_WANDER) }
        away.state = Rooms(spawn(world, away, MOLLY_AT, MOLLY[build][colour.ordinal], 0), build, colour, suspects)
        true
    }

    private fun spawn(world: World, away: EventAreas.Away, at: Tile, id: Int, wander: Int): Npc? {
        val type = world.npcTypes[id] ?: return null
        val tile = away.inside(at.x, at.z)
        return world.spawnNpc(NpcSpawn(type, tile.x, tile.z, 0, wander = wander, source = "evil twin for ${away.owner}", respawns = false))
    }

    /** The stay [player] is in and its rooms, if they are in them. */
    fun roomsOf(world: World, player: Player): Pair<EventAreas.Away, Rooms>? {
        val away = world.randomEvents.areas.awayOf(player) ?: return null
        val rooms = away.state as? Rooms ?: return null
        return away to rooms
    }

    /** Talk-to on Molly. */
    suspend fun ScriptContext.talkToMolly(world: World, npc: Npc) {
        val (away, rooms) = roomsOf(world, player) ?: return
        if (npc !== rooms.molly) return
        world.dialogues.start(this, npc) {
            with(MollyLines) {
                when {
                    rooms.gotTwin -> {
                        // Back first, then the gems (what doesn't fit goes on the ground there, not in the rooms that
                        // are about to end), then the talk, so shutting it neither loses nor repeats them. Guessed: the
                        // talk comes after the teleport.
                        world.randomEvents.areas.bringBack(player, away)
                        val gem = world.random.nextInt(GEMS.size)
                        val count = GEMS_MIN + world.random.nextInt(GEMS_MAX - GEMS_MIN + 1)
                        world.randomEvents.reward(player, GEMS[gem], count)
                        chatPlayer(ChatAnim.HAPPY, IN_THE_CAGE)
                        chatNpc(ChatAnim.HAPPY, FANTASTIC)
                        chatNpc(ChatAnim.HAPPY, TAKE_THIS)
                        objbox(GEMS[gem], text(GIVEN[gem], "count" to count))
                    }
                    rooms.caught >= ATTEMPTS -> {
                        // Sent off first, as the gems above, so shutting the talk can't keep them in the rooms.
                        // Guessed: the talk comes after the teleport.
                        val spot = failSpot(world)
                        if (spot == null) world.randomEvents.areas.bringBack(player, away)
                        else world.teleport(player, spot.first, spot.second, spot.third)
                        chatPlayer(ChatAnim.SAD, SORRY)
                        chatNpc(ChatAnim.ANGRY, INCOMPETENCE)
                        chatNpc(ChatAnim.ANGRY, OUT_OF_MY_SIGHT)
                    }
                    rooms.briefed -> {
                        chatPlayer(ChatAnim.QUIZ, AGAIN)
                        chatNpc(ChatAnim.NEUTRAL, ONE_MORE_TIME)
                        chatNpc(ChatAnim.NEUTRAL, THROUGH_THAT_DOOR_AGAIN)
                        chatNpc(ChatAnim.NEUTRAL, LURED_AGAIN)
                        brief(world, away, NO_MORE_BOTHER_AGAIN)
                    }
                    else -> {
                        chatNpc(ChatAnim.HAPPY, THANKS_FOR_COMING)
                        chatPlayer(ChatAnim.QUIZ, WHATS_THE_PROBLEM)
                        chatNpc(ChatAnim.ANGRY, EVIL_SISTER)
                        chatPlayer(ChatAnim.QUIZ, TO_DO_WITH_ME)
                        chatNpc(ChatAnim.HAPPY, GLAD_YOU_ASKED)
                        chatNpc(ChatAnim.NEUTRAL, THROUGH_THAT_DOOR)
                        chatNpc(ChatAnim.NEUTRAL, LURED)
                        rooms.briefed = true
                        brief(world, away, NO_MORE_BOTHER)
                    }
                }
            }
        }
    }

    /** The rest of her briefing, from the player asking what to do, and the controls if wanted. */
    private suspend fun Dialogue.brief(world: World, away: EventAreas.Away, noMoreBother: osrsps.game.dialogue.Line) = with(MollyLines) {
        chatPlayer(ChatAnim.QUIZ, WHAT_DO_I_DO)
        chatNpc(ChatAnim.NEUTRAL, CATCH_MY_SISTER)
        chatNpc(ChatAnim.HAPPY, noMoreBother)
        chatPlayer(ChatAnim.NEUTRAL, EASY_ENOUGH)
        chatNpc(ChatAnim.NEUTRAL, TAKE_A_LOOK)
        chatNpc(ChatAnim.NEUTRAL, TWO_ATTEMPTS)
        chatPlayer(ChatAnim.NEUTRAL, DO_MY_BEST)
        chatNpc(ChatAnim.QUIZ, CONTROLS)
        when (choice(YES_PLEASE_OPTION, NO_THANKS_OPTION, WANT_TO_LEAVE)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, YES_PLEASE)
                chatNpc(ChatAnim.NEUTRAL, GLOWING_MARK)
                chatNpc(ChatAnim.NEUTRAL, CLICK_DIRECTION)
                chatNpc(ChatAnim.NEUTRAL, LEVER_POINTS)
                chatNpc(ChatAnim.NEUTRAL, HIT_THE_BUTTON)
                chatNpc(ChatAnim.NEUTRAL, BE_CAREFUL)
                chatNpc(ChatAnim.NEUTRAL, MY_SISTER)
                chatNpc(ChatAnim.QUIZ, DOES_THAT_HELP)
                chatPlayer(ChatAnim.HAPPY, COVERS_EVERYTHING)
                chatNpc(ChatAnim.HAPPY, GOOD_LUCK)
            }
            2 -> {
                chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
                chatNpc(ChatAnim.HAPPY, GOOD_LUCK)
            }
            else -> leave(world, away)
        }
    }

    /** Leaving: her answer, and home with nothing. */
    private suspend fun Dialogue.leave(world: World, away: EventAreas.Away) {
        chatPlayer(ChatAnim.NEUTRAL, MollyLines.WANT_TO_LEAVE)
        chatNpc(ChatAnim.ANGRY, MollyLines.THERE_I_WAS)
        world.randomEvents.areas.bringBack(player, away)
    }

    /** Open on the door: through it, or her word first. */
    suspend fun ScriptContext.door(world: World) {
        val (away, rooms) = roomsOf(world, player) ?: return
        val molly = rooms.molly ?: return
        val inMollysRoom = away.outside(player.x, player.z).x <= DOOR_X
        fun cross(to: Tile) {
            val inside = away.inside(to.x, to.z)
            world.teleport(player, inside.x, inside.z, 0)
        }
        if (rooms.over) return if (inMollysRoom) cross(PANEL_SIDE) else cross(MOLLY_SIDE)
        if (!inMollysRoom) {
            world.dialogues.start(this, molly) {
                with(MollyLines) {
                    chatNpc(ChatAnim.NEUTRAL, FINISH_FIRST)
                    if (choice(OKAY, BUT_LEAVE) == 1) return@start chatPlayer(ChatAnim.NEUTRAL, OKAY)
                    chatPlayer(ChatAnim.NEUTRAL, BUT_LEAVE)
                    chatNpc(ChatAnim.ANGRY, THERE_I_WAS)
                    world.randomEvents.areas.bringBack(player, away)
                }
            }
            return
        }
        if (rooms.briefed) return cross(PANEL_SIDE)
        world.dialogues.start(this, molly) {
            with(MollyLines) {
                chatNpc(ChatAnim.SHOCKED, WAIT)
                when (choice(YES_I_KNOW_OPTION, NO_I_DONT, WANT_TO_LEAVE)) {
                    1 -> {
                        chatPlayer(ChatAnim.NEUTRAL, YES_I_KNOW)
                        chatNpc(ChatAnim.HAPPY, GOOD_LUCK_THEN)
                        rooms.briefed = true
                        cross(PANEL_SIDE)
                    }
                    2 -> {
                        chatPlayer(ChatAnim.CONFUSED, NO_I_DONT)
                        chatNpc(ChatAnim.NEUTRAL, COME_OVER)
                    }
                    else -> leave(world, away)
                }
            }
        }
    }

    /** Use on the control panel: the claw's controls. */
    fun usePanel(world: World, player: Player) {
        val (_, rooms) = roomsOf(world, player) ?: return
        if (rooms.over) return
        world.interfaces.openModal(player, FrameSlot.SIDEMODAL, INTERFACE)
        for (component in listOf(GRAB, MOVE_RIGHT, MOVE_UP, MOVE_LEFT, MOVE_DOWN)) {
            player.session.queue(IfSetEventsV2(INTERFACE, component, -1, -1, 0, BankScreen.opMask(1..1)))
        }
    }

    /** An arrow: the claw and its mark a tile that way, within the pen. */
    private fun controlsOpen(player: Player) = INTERFACE in player.modals.values

    fun move(world: World, player: Player, dx: Int, dz: Int) {
        val (away, rooms) = roomsOf(world, player) ?: return
        if (rooms.over) return
        val to = Tile(rooms.claw.x + dx, rooms.claw.z + dz)
        if (to.x !in CLAW_X || to.z !in CLAW_Z) return
        val from = away.inside(rooms.claw.x, rooms.claw.z)
        for (loc in world.locs.at(from.x, from.z, 0)) if (loc.id == CLAW || loc.id == MARK) world.locs.remove(loc)
        val inside = away.inside(to.x, to.z)
        world.locs.add(ZoneLoc(CLAW, LocShape.CENTREPIECE_STRAIGHT, 0, inside.x, inside.z, 0))
        world.locs.add(ZoneLoc(MARK, LocShape.GROUND_DECOR, 0, inside.x, inside.z, 0))
        rooms.claw = to
    }

    /** Grab: whoever stands on the mark goes into the cage, and Molly has her say. */
    suspend fun ScriptContext.grab(world: World) {
        val (away, rooms) = roomsOf(world, player) ?: return
        if (rooms.over) return
        if (rooms.claw.x in CAGE_X && rooms.claw.z in CAGE_Z) return world.message(player, MollyLines.OVER_THE_CAGE)
        val mark = away.inside(rooms.claw.x, rooms.claw.z)
        val caught = rooms.suspects.firstOrNull { it.x == mark.x && it.z == mark.z && away.outside(it.x, it.z).let { t -> t.x !in CAGE_X || t.z !in CAGE_Z } }
            ?: return
        rooms.caught++
        val cage = away.inside(CAGE_AT.x, CAGE_AT.z)
        world.npcs.teleport(caught, cage.x, cage.z)
        world.npcs.freeze(caught, CAGED_TICKS)
        val molly = rooms.molly ?: return
        if (caught.spawn.type.id == rooms.twinId) rooms.gotTwin = true
        if (rooms.over) world.interfaces.closeModals(player)
        world.dialogues.start(this, molly) {
            with(MollyLines) {
                when {
                    rooms.gotTwin -> {
                        chatNpc(ChatAnim.HAPPY, WELL_DONE)
                        chatNpc(ChatAnim.HAPPY, COME_NEXT_DOOR)
                    }
                    rooms.caught >= ATTEMPTS -> chatNpc(ChatAnim.SAD, TRIED_YOUR_BEST)
                }
            }
        }
    }

    /** Long enough that a caught suspect stays in the cage until the rooms end. */
    private const val CAGED_TICKS = 100_000

    /** Grab on a suspect: the controls are the way. */
    suspend fun ScriptContext.grabByHand(world: World) {
        val (_, rooms) = roomsOf(world, player) ?: return
        val molly = rooms.molly ?: return
        world.dialogues.start(this, molly) { chatNpc(ChatAnim.ANGRY, MollyLines.USE_THE_PANEL) }
    }

    /** The rooms' locs whose handlers work in their copies. */
    val INSTANCE_LOCS: Set<Int> = setOf(PANEL, DOOR)

    fun register(world: World) {
        for (id in MOLLIES) world.handlers.onNpc(id, RandomEvents.TALK_TO) { op -> talkToMolly(world, op.npc ?: return@onNpc) }
        // From where the player stands: the suspects are fenced in the pen, so walking over to them would never get there.
        for (id in ALL_SUSPECTS) world.npcInteractions.fromAfar(id, GRAB_OP) { player, _ ->
            if (roomsOf(world, player) == null) return@fromAfar false
            player.waypoints.clear()
            world.scripts.start(player) { grabByHand(world) }
            true
        }
        world.handlers.onLoc(PANEL, USE) { usePanel(world, player) }
        world.handlers.onLoc(DOOR, OPEN) { door(world) }
        // Only while the claw's controls are open: a button sent without them is ignored.
        world.handlers.onButton(INTERFACE, MOVE_RIGHT) { if (controlsOpen(player)) move(world, player, 1, 0) }
        world.handlers.onButton(INTERFACE, MOVE_LEFT) { if (controlsOpen(player)) move(world, player, -1, 0) }
        world.handlers.onButton(INTERFACE, MOVE_UP) { if (controlsOpen(player)) move(world, player, 0, 1) }
        world.handlers.onButton(INTERFACE, MOVE_DOWN) { if (controlsOpen(player)) move(world, player, 0, -1) }
        world.handlers.onButton(INTERFACE, GRAB) { if (controlsOpen(player)) grab(world) }
    }

    /** Interface and component names in the rev 242 cache, for [osrsps.cache.GameFrameCheck]. */
    val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
        get() = mapOf(
            INTERFACE to (
                "macro_evil_twin" to mapOf(
                    GRAB to "grab", MOVE_RIGHT to "move_right", MOVE_UP to "move_up", MOVE_LEFT to "move_left", MOVE_DOWN to "move_down",
                )
                ),
        )
}
