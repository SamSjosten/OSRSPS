package osrsps.game.content.randomevents

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.map.Tile

/** The frogs' lines; the words are in the dialogue file, from Transcript:Kiss the frog (read 2026-11-19). */
object KissTheFrogLines : Lines("kiss_the_frog", "Transcript:Kiss the frog") {
    val GREETING = line("greeting", "Overhead dialogue: upon spawning in (the princess form, see KissTheFrog)", "name")
    val TALK_TO = line("talk_to", "Overhead dialogue: dialogue 1 (princess)", "name")
    val NEEDS_HELP = line("needs_help", "Overhead dialogue: dialogue 2 (princess)", "name")
    val RESPOND = line("respond", "Overhead dialogue: dialogue 3 (princess)", "name")
    val MAYBE_LATER = line("maybe_later", "Overhead dialogue: if the player does not respond in time")
    val PLEASE_HELP = line("please_help", "Crowned frog: her or his question")

    /** The touch answers the transcript gives without doubt (7 to 10; it asks whether 1 to 6 are still in the game). */
    val POSITIVE = listOf(
        line("positive_7", "Crowned frog: positive dialogue 7, option and the player's words"),
        line("positive_8", "Crowned frog: positive dialogue 8, option and the player's words"),
        line("positive_9", "Crowned frog: positive dialogue 9, option and the player's words"),
        line("positive_10", "Crowned frog: positive dialogue 10, option and the player's words"),
    )

    /** The answers that make the player a frog (8 and 9; the transcript asks whether 1 to 7 are still in the game). */
    val TO_FROGLAND = listOf(
        line("frog_too_8", "Crowned frog: negative-teleport dialogue 8, option and the player's words"),
        line("frog_too_9", "Crowned frog: negative-teleport dialogue 9, option and the player's words"),
    )

    val POLITE = listOf(
        line("polite_1", "Crowned frog: negative-polite dialogue 1, option and the player's words"),
        line("polite_2", "Crowned frog: negative-polite dialogue 2, option and the player's words"),
    )

    val KISS = listOf(
        line("kiss_1", "Crowned frog: kiss option 1, and the player's words"),
        line("kiss_2", "Crowned frog: kiss option 2, and the player's words"),
        line("kiss_3", "Crowned frog: kiss option 3, and the player's words"),
    )

    val YES = line("yes", "Crowned frog: the kiss option, her or his answer")
    val THANK_YOU = line("thank_you", "Crowned frog: the positive or kiss option, the royal's thanks", "name")
    val HURRAH_PRINCE = line("hurrah_prince", "Other frog 1 (the prince)")
    val HURRAH_PRINCESS = line("hurrah_princess", "Other frog 1 (the princess)")
    val SPEAK_TO_PRINCE = line("speak_to_prince", "Other frog 2 (the prince)")
    val SPEAK_TO_PRINCESS = line("speak_to_princess", "Other frog 2 (the princess)")
    val NOT_NEEDED = line("not_needed", "Other player's frog 1")
    val JUST_A_FROG = line("just_a_frog", "Other player's frog 2")
    val READY_TO_LEAVE = line("ready_to_leave", "Frogland: the frog's question")
    val LEAVE_NOW = line("leave_now", "Frogland: the first option, and the player's words")
    val RATHER_STAY = line("rather_stay", "Frogland: the second option, and the player's words")
}

/**
 * Kiss the frog (OSRS Wiki, Kiss the frog, Frog (Kiss the frog), Frog Prince, Frog Princess, Frogland and Frog token
 * pages and Transcript:Kiss the frog, read 2026-11-19). A frog in a pointy hat (5429, macro_frog_crier) comes for the
 * player; Talk-to takes them to the frogs' swamp (map square 38,74, the place the Kiss the frog page's map shows, through
 * [EventAreas]), where the frog with the crown (5431, macro_frog_sulking) asks to be touched. A touch or a kiss turns it
 * into the Frog Prince (13445) or Frog Princess (13446), one at random since 26 June 2024, who gives a frog token (6183)
 * and sends the player home. A polite no ends it with nothing; asking to be a frog sends the player to Frogland, the
 * same square on the Wiki's map, where the frog there (5432, macro_frog_noncombat) asks whether they are ready to leave.
 *
 * The overheads name a princess: the Wiki's trivia says they take the opposite of the player's pronouns, and this
 * server's text follows the male forms throughout (the project's rule), so every player gets the princess lines. The crowned frog's own gender is picked at random, as the trivia
 * says. The options show one touch and one refusal in a random order (the transcript), then a kiss.
 *
 * Guessed: Talk-to on the pointy-hat frog taking the player straight to the swamp with no words (the transcript has
 * none for it); where the player lands and where the eight frogs stand (a ring, still), and which hats they wear; the
 * kiss always following the other two options; a refusal picked from the polite and the frog ones with equal odds; the
 * timing of the kiss (giant_frog_kiss with human_frog_kiss, then frog_change_into_human) and of turning into a frog
 * (human_change_into_frog); that a polite refusal sends the player home (the Wiki says events that teleport can now be
 * left by talking to the NPC). Left out: the player's frog look and its four side buttons in Frogland, and the Frogland
 * music track.
 */
object KissTheFrog : RandomEvent("kiss_the_frog", 5429) {
    const val CROWNED = 5431
    const val TOP_HAT = 5430
    const val HATLESS = 312
    const val FROGLAND_FROG = 5432
    const val PRINCE = 13445
    const val PRINCESS = 13446
    const val TOKEN = 6183

    /** giant_frog_kiss, frog_change_into_human, human_frog_kiss and human_change_into_frog. */
    const val FROG_KISS = 2374
    const val FROG_TO_HUMAN = 2375
    const val PLAYER_KISS = 2376
    const val PLAYER_TO_FROG = 2377

    /** Guessed: the kiss, then the change, then the royal standing there before the player goes home. */
    const val KISS_TICKS = 2
    const val CHANGE_TICKS = 2
    const val ROYAL_TICKS = 1

    /** The zones copied: the swamp of map square 38,74. */
    const val FROM_X = 2448
    const val FROM_Z = 4768
    const val WIDTH_ZONES = 4
    const val HEIGHT_ZONES = 3

    val ARRIVE = Tile(2464, 4773)

    /** Guessed: the ring of frogs around where the player lands. */
    val RING = listOf(
        Tile(2461, 4773), Tile(2467, 4773), Tile(2462, 4775), Tile(2466, 4775),
        Tile(2462, 4771), Tile(2466, 4771), Tile(2464, 4776), Tile(2464, 4770),
    )

    /** Guessed: the hats of the other seven frogs, in turn. */
    val PLAIN = listOf(TOP_HAT, HATLESS, 5429)

    /** A stay in the swamp. */
    class Swamp(val princess: Boolean, val crowned: Npc?) {
        var frogland = false
        var frogs: List<Npc> = emptyList()
        var froglandFrog: Npc? = null
        /** Touched or kissed: the royal is there and the token is theirs. */
        var won = false
    }

    override val teleports = true
    override val greeting = KissTheFrogLines.GREETING
    override val nags = with(KissTheFrogLines) { listOf(TALK_TO, NEEDS_HELP, RESPOND) }
    override val leaving = KissTheFrogLines.MAYBE_LATER
    override val notYours = KissTheFrogLines.NOT_NEEDED
    override val notYoursAlso = listOf(KissTheFrogLines.JUST_A_FROG)

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean {
        val away = events.areas.takeAway(player, this@KissTheFrog, FROM_X, FROM_Z, WIDTH_ZONES, HEIGHT_ZONES, ARRIVE) ?: return true
        val crownedAt = world.random.nextInt(RING.size)
        val swamp = Swamp(world.random.nextInt(2) == 0, spawn(world, away, CROWNED, RING[crownedAt]))
        away.state = swamp
        swamp.frogs = RING.withIndex().filter { it.index != crownedAt }.mapIndexedNotNull { i, (_, at) -> spawn(world, away, PLAIN[i % PLAIN.size], at) }
        return true
    }

    private fun spawn(world: World, away: EventAreas.Away, id: Int, at: Tile): Npc? {
        val type = world.npcTypes[id] ?: return null
        val tile = away.inside(at.x, at.z)
        return world.spawnNpc(NpcSpawn(type, tile.x, tile.z, 0, source = "frog swamp for ${away.owner}", respawns = false))
    }

    fun swampOf(world: World, player: Player): Pair<EventAreas.Away, Swamp>? {
        val away = world.randomEvents.areas.awayOf(player) ?: return null
        val swamp = away.state as? Swamp ?: return null
        return away to swamp
    }

    /** Talk-to on the crowned frog: the question, then a touch, a kiss, a refusal or Frogland. */
    suspend fun ScriptContext.talkToCrowned(world: World, npc: Npc) {
        val (away, swamp) = swampOf(world, player) ?: return
        if (npc !== swamp.crowned || swamp.frogland) return
        world.dialogues.start(this, npc) {
            with(KissTheFrogLines) {
                chatNpc(ChatAnim.SAD, PLEASE_HELP)
                val touch = POSITIVE[world.random.nextInt(POSITIVE.size)]
                val refusals = POLITE + TO_FROGLAND
                val refusal = refusals[world.random.nextInt(refusals.size)]
                val kiss = KISS[world.random.nextInt(KISS.size)]
                val touchFirst = world.random.nextInt(2) == 0
                val first = if (touchFirst) touch else refusal
                val second = if (touchFirst) refusal else touch
                val picked = when (choice(text(first), text(second), text(kiss))) {
                    1 -> first
                    2 -> second
                    else -> kiss
                }
                chatPlayer(ChatAnim.NEUTRAL, picked)
                when (picked) {
                    touch, kiss -> {
                        if (picked == kiss) chatNpc(ChatAnim.HAPPY, YES)
                        player.anim(PLAYER_KISS)
                        npc.effects.anim(FROG_KISS)
                        delay(KISS_TICKS)
                        npc.effects.anim(FROG_TO_HUMAN)
                        delay(CHANGE_TICKS)
                        val royal = royal(world, away, swamp, npc) ?: return@start world.randomEvents.areas.bringBack(player, away)
                        swamp.won = true
                        // The royal stands there for a tick, so the client draws them before the player goes. Guessed:
                        // how long. A talk to them gives what this would have, should anything cut it short.
                        delay(ROYAL_TICKS)
                        thank(world, away, royal)
                    }
                    in TO_FROGLAND -> toFrogland(world, away, swamp)
                    else -> world.randomEvents.areas.bringBack(player, away)
                }
            }
        }
    }

    /** Home, the token, and the royal's thanks. */
    private suspend fun Dialogue.thank(world: World, away: EventAreas.Away, royal: Npc) = with(KissTheFrogLines) {
        // Back first, then the token (on the ground there if it doesn't fit, not in the swamp that is about to end), then
        // the thanks, so shutting it neither loses nor repeats it. Guessed: the thanks comes after the teleport.
        world.randomEvents.areas.bringBack(player, away)
        world.randomEvents.reward(player, TOKEN)
        chatNpc(royal, ChatAnim.HAPPY, text(THANK_YOU, "name" to player.username))
    }

    /** Talk-to on the prince or princess, if the tick they stand there was cut short. */
    suspend fun ScriptContext.talkToRoyal(world: World, npc: Npc) {
        val (away, swamp) = swampOf(world, player) ?: return
        if (!swamp.won) return
        world.dialogues.start(this, npc) { thank(world, away, npc) }
    }

    /** The crowned frog's place taken by the prince or princess. */
    private fun royal(world: World, away: EventAreas.Away, swamp: Swamp, frog: Npc): Npc? {
        val at = away.outside(frog.x, frog.z)
        world.removeNpc(frog)
        return spawn(world, away, if (swamp.princess) PRINCESS else PRINCE, at)
    }

    /** Asking to be a frog: the swamp's frogs go and the Frogland frog stands where the crowned one did. */
    private fun ScriptContext.toFrogland(world: World, away: EventAreas.Away, swamp: Swamp) {
        player.anim(PLAYER_TO_FROG)
        val crowned = swamp.crowned
        val at = if (crowned != null) away.outside(crowned.x, crowned.z) else RING[0]
        for (frog in swamp.frogs + listOfNotNull(crowned)) world.removeNpc(frog)
        swamp.frogs = emptyList()
        swamp.frogland = true
        swamp.froglandFrog = spawn(world, away, FROGLAND_FROG, at)
    }

    /** Talk-to on the Frogland frog: home, or stay. */
    suspend fun ScriptContext.talkInFrogland(world: World, npc: Npc) {
        val (away, swamp) = swampOf(world, player) ?: return
        if (!swamp.frogland) return
        world.dialogues.start(this, npc) {
            with(KissTheFrogLines) {
                chatNpc(ChatAnim.NEUTRAL, READY_TO_LEAVE)
                if (choice(LEAVE_NOW, RATHER_STAY) != 1) return@start chatPlayer(ChatAnim.NEUTRAL, RATHER_STAY)
                chatPlayer(ChatAnim.HAPPY, LEAVE_NOW)
                world.randomEvents.areas.bringBack(player, away)
            }
        }
    }

    /** Talk-to on one of the other frogs in the swamp: one of their two lines, for the royal it is. */
    suspend fun ScriptContext.talkToOther(world: World, npc: Npc) {
        val (_, swamp) = swampOf(world, player) ?: return
        if (npc !in swamp.frogs) return
        world.dialogues.start(this, npc) {
            with(KissTheFrogLines) {
                val lines = if (swamp.princess) listOf(HURRAH_PRINCESS, SPEAK_TO_PRINCESS) else listOf(HURRAH_PRINCE, SPEAK_TO_PRINCE)
                chatNpc(ChatAnim.NEUTRAL, lines[world.random.nextInt(lines.size)])
            }
        }
    }

    /** Talk-to on a pointy-hat frog in the swamp, which is no event's NPC there. */
    override suspend fun ScriptContext.talkElsewhere(world: World, npc: Npc) = talkToOther(world, npc)

    fun register(world: World) {
        world.handlers.onNpc(CROWNED, RandomEvents.TALK_TO) { op -> talkToCrowned(world, op.npc ?: return@onNpc) }
        for (id in listOf(PRINCE, PRINCESS)) world.handlers.onNpc(id, RandomEvents.TALK_TO) { op -> talkToRoyal(world, op.npc ?: return@onNpc) }
        world.handlers.onNpc(FROGLAND_FROG, RandomEvents.TALK_TO) { op -> talkInFrogland(world, op.npc ?: return@onNpc) }
        for (id in listOf(TOP_HAT, HATLESS)) world.handlers.onNpc(id, RandomEvents.TALK_TO) { op -> talkToOther(world, op.npc ?: return@onNpc) }
    }
}
