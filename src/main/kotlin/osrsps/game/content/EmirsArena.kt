package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Captain Dalbur's lines; the words are in the dialogue file, from the Wiki's Transcript:Captain Dalbur, read 2026-10-30. */
object DalburLines : Lines("dalbur", "Transcript:Captain Dalbur") {
    val GLIDER = line("glider", "after The Grand Tree: menu option 1, which the player then says")
    val ALI = line("ali", "menu option 2, which the player then says")
    val BETTER = line("better", "menu option 3, which the player then says")
    val NOTHING = line("nothing", "menu option 4, which the player then says; the talk ends")
    val OF_COURSE = line("of_course", "his answer to the glider, before the map opens")
    val ALI_1 = line("ali_1", "Ali Morrisane: his first answer")
    val MAGIC_CARPET = line("magic_carpet", "Ali Morrisane: the player asks what business")
    val ALI_2 = line("ali_2", "Ali Morrisane: his second")
    val ALI_3 = line("ali_3", "Ali Morrisane: his last")
    val BETTER_1 = line("better_1", "gliders: his first answer")
    val BETTER_2 = line("better_2", "gliders: his second")
    val NETWORK = line("network", "gliders: the player's question")
    val SECRET = line("secret", "gliders: his last")
}

/** The Duel Guide's lines; the words are in the dialogue file, from the Wiki's Transcript:Duel Guide, read 2026-10-30. */
object DuelGuideLines : Lines("duel_guide", "Transcript:Duel Guide") {
    val WELCOME = line("welcome", "his greeting")
    val WHAT = line("what", "option 1, which the player then says")
    val RANKED = line("ranked", "option 2, which the player then says")
    val INVITED = line("invited", "option 3, which the player then says")
    val ORGANISE = line("organise", "option 4, which the player then says")
    val OKAY = line("okay", "option 5, which the player then says; the talk ends")
    val GOODBYE = line("goodbye", "after organising: option 4, which the player then says; the talk ends")
    val WHAT_1 = line("what_1", "what's a Duel: his answer")
    val RANKED_1 = line("ranked_1", "ranked or unranked: his first line")
    val RANKED_2 = line("ranked_2", "ranked or unranked: second")
    val RANKED_3 = line("ranked_3", "ranked or unranked: third")
    val RANKED_4 = line("ranked_4", "ranked or unranked: fourth")
    val INVITED_1 = line("invited_1", "getting invited: his first line")
    val TUTORIAL_1 = line("tutorial_1", "getting invited: the first instruction box (the Grouping tab)")
    val TUTORIAL_2 = line("tutorial_2", "getting invited: the second instruction box (Find)")
    val TUTORIAL_3 = line("tutorial_3", "getting invited: the third instruction box (Sign up)")
    val INVITED_2 = line("invited_2", "getting invited: his line after the boxes")
    val INVITED_3 = line("invited_3", "getting invited: his last line")
    val CHALLENGE_1 = line("challenge_1", "the instruction box on Challenge")
    val CHALLENGE_2 = line("challenge_2", "the instruction box on which worlds")
    val ORGANISE_1 = line("organise_1", "organising: his answer")
}

/** The 1v1 Tournament Guide's lines; the words are in the dialogue file, from the Wiki's Transcript:1v1 Tournament Guide, read 2026-10-30. */
object TournamentGuideLines : Lines("tournament_guide", "Transcript:1v1 Tournament Guide") {
    val WELCOME = line("welcome", "her greeting")
    val WHAT = line("what", "option 1, which the player then says")
    val RANKED = line("ranked", "option 2, which the player then says")
    val INVITED = line("invited", "option 3, which the player then says")
    val ORGANISE = line("organise", "option 4, which the player then says")
    val OKAY = line("okay", "option 5, which the player then says; the talk ends")
    val GOODBYE = line("goodbye", "after organising: option 4, which the player then says; the talk ends")
    val WHAT_1 = line("what_1", "what's a Tournament: the first answer")
    val WHAT_2 = line("what_2", "what's a Tournament: the second")
    val RANKED_1 = line("ranked_1", "ranked or unranked: first line")
    val RANKED_2 = line("ranked_2", "ranked or unranked: second")
    val RANKED_3 = line("ranked_3", "ranked or unranked: third")
    val RANKED_4 = line("ranked_4", "ranked or unranked: fourth")
    val INVITED_1 = line("invited_1", "getting invited: the first line")
    val TUTORIAL_1 = line("tutorial_1", "getting invited: the first instruction box")
    val TUTORIAL_2 = line("tutorial_2", "getting invited: the second instruction box")
    val TUTORIAL_3 = line("tutorial_3", "getting invited: the third instruction box")
    val INVITED_2 = line("invited_2", "getting invited: the last line")
    val ORGANISE_1 = line("organise_1", "organising: the answer")
    val ORGANISE_TUTORIAL_1 = line("organise_tutorial_1", "organising: the first instruction box")
    val ORGANISE_TUTORIAL_2 = line("organise_tutorial_2", "organising: the second instruction box")
}

/** Arena Guard Fronk's lines; the words are in the dialogue file, from the Wiki's Transcript:Arena Guard Fronk, read 2026-10-30. */
object FronkLines : Lines("fronk", "Transcript:Arena Guard Fronk") {
    val GREETING = line("greeting", "his greeting (live says sir or madam by the player's look; ours always the one form until appearance is built)")
    val PLACE = line("place", "option: what is this place, which the player then says")
    val DO_HERE = line("do_here", "option: what he does here, which the player then says")
    val WORK_OUT = line("work_out", "option: does he work out, which the player then says")
    val FINE = line("fine", "option: the player is fine; the talk ends")
    val COVEN = line("coven", "option after what he does: the Calamity Coven, which the player then says")
    val COVEN_AGAIN = line("coven_again", "the same option once heard, which the player then says")
    val PLACE_1 = line("place_1", "the place: his first answer")
    val PLACE_2 = line("place_2", "the place: his second, pointing to the Guides")
    val DO_HERE_1 = line("do_here_1", "what he does: his answer")
    val COVEN_1 = line("coven_1", "the Coven: first line")
    val COVEN_2 = line("coven_2", "the Coven: Maoma")
    val COVEN_3 = line("coven_3", "the Coven: Saika")
    val COVEN_4 = line("coven_4", "the Coven: Koriff")
    val COVEN_5 = line("coven_5", "the Coven: last line")
    val WORK_OUT_1 = line("work_out_1", "working out: his answer")
}

/** What the Arena's and the glider's stubs say: ours, not Jagex's, until the Arena and the gliders are built. */
object ArenaStubLines : Lines("arena_stub", "ours") {
    val ARENA_CLOSED = line("arena_closed", "ours: the Arena's fights aren't open here yet")
    val GLIDER_CLOSED = line("glider_closed", "ours: the gliders don't fly anywhere built yet")
}

/**
 * The Emir's Arena and the gnome glider as stubs (the Al Kharid plan's step 9): the people talk, nothing fights or
 * flies. From the Wiki's transcripts, read 2026-10-30.
 *
 * - Captain Dalbur (`pilot_al_kharid` 6089, which varbit 9584 shows as one of 10452 to 10458, as after The Grand
 *   Tree): his talk; Glider, Glider-to and the glider in his talk say the gliders don't fly yet (ours) in place of the map.
 * - The Duel Guide (11673) and the 1v1 Tournament Guide (11672): their talks, the instruction boxes as boxes.
 * - Arena Guard Fronk (11664): his talk, the Coven's history looping as the Wiki has it.
 * - The Arena's recruitment boards, staging area steps and scoreboard: ours, the Arena isn't open yet. Its gates open
 *   as gates.
 * - The fire altar's ruins stay as they are (Runecrafting isn't built).
 *
 * Left out: the five other guards' talks (one transcript each, a later step), Joby and Beni (Arena worlds only),
 * Chris and his Lost Property shop. Guessed: every tile in the Arena (no data from live yet), the expressions, that
 * Fronk's menus show both Coven options as the transcript lists them.
 */
object EmirsArena {
    const val DALBUR = 6089
    val DALBURS = (10452..10458).toList()
    const val GLIDER_OP = 1
    const val DALBUR_TALK = 3
    const val GLIDER_TO = 4

    const val DUEL_GUIDE = 11673
    const val TOURNAMENT_GUIDE = 11672
    const val FRONK = 11664
    const val TALK_TO = 1

    const val BOARD = 44918
    const val BOARD_2 = 44919
    const val STEPS = 44924
    const val STEPS_2 = 44925
    const val SCOREBOARD = 44930

    internal suspend fun Dialogue.dalbur() = with(DalburLines) {
        when (choice(GLIDER, ALI, BETTER, NOTHING)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, GLIDER)
                chatNpc(ChatAnim.HAPPY, OF_COURSE)
                mesbox(ArenaStubLines.GLIDER_CLOSED)
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, ALI)
                chatNpc(ChatAnim.NEUTRAL, ALI_1)
                chatPlayer(ChatAnim.QUIZ, MAGIC_CARPET)
                chatNpc(ChatAnim.NEUTRAL, ALI_2)
                chatNpc(ChatAnim.ANGRY, ALI_3)
            }
            3 -> {
                chatPlayer(ChatAnim.QUIZ, BETTER)
                chatNpc(ChatAnim.HAPPY, BETTER_1)
                chatNpc(ChatAnim.HAPPY, BETTER_2)
                chatPlayer(ChatAnim.QUIZ, NETWORK)
                chatNpc(ChatAnim.SHIFTY, SECRET)
            }
            else -> chatPlayer(ChatAnim.NEUTRAL, NOTHING)
        }
    }

    internal suspend fun Dialogue.duelGuide() = with(DuelGuideLines) {
        chatNpc(ChatAnim.HAPPY, WELCOME)
        var organised = false
        while (true) {
            val pick = if (organised) choice(WHAT, RANKED, INVITED, GOODBYE) else choice(WHAT, RANKED, INVITED, ORGANISE, OKAY)
            when {
                pick == 1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHAT)
                    chatNpc(ChatAnim.NEUTRAL, WHAT_1)
                }
                pick == 2 -> {
                    chatPlayer(ChatAnim.QUIZ, RANKED)
                    for (line in listOf(RANKED_1, RANKED_2, RANKED_3, RANKED_4)) chatNpc(ChatAnim.NEUTRAL, line)
                }
                pick == 3 -> {
                    chatPlayer(ChatAnim.QUIZ, INVITED)
                    chatNpc(ChatAnim.NEUTRAL, INVITED_1)
                    for (line in listOf(TUTORIAL_1, TUTORIAL_2, TUTORIAL_3)) mesbox(line)
                    chatNpc(ChatAnim.NEUTRAL, INVITED_2)
                    chatNpc(ChatAnim.NEUTRAL, INVITED_3)
                    mesbox(CHALLENGE_1)
                    mesbox(CHALLENGE_2)
                }
                pick == 4 && !organised -> {
                    chatPlayer(ChatAnim.NEUTRAL, ORGANISE)
                    chatNpc(ChatAnim.NEUTRAL, ORGANISE_1)
                    mesbox(CHALLENGE_1)
                    mesbox(CHALLENGE_2)
                    organised = true
                }
                organised -> return@with chatPlayer(ChatAnim.NEUTRAL, GOODBYE)
                else -> return@with chatPlayer(ChatAnim.NEUTRAL, OKAY)
            }
        }
    }

    internal suspend fun Dialogue.tournamentGuide() = with(TournamentGuideLines) {
        chatNpc(ChatAnim.HAPPY, WELCOME)
        var organised = false
        while (true) {
            val pick = if (organised) choice(WHAT, RANKED, INVITED, GOODBYE) else choice(WHAT, RANKED, INVITED, ORGANISE, OKAY)
            when {
                pick == 1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHAT)
                    chatNpc(ChatAnim.NEUTRAL, WHAT_1)
                    chatNpc(ChatAnim.NEUTRAL, WHAT_2)
                }
                pick == 2 -> {
                    chatPlayer(ChatAnim.QUIZ, RANKED)
                    for (line in listOf(RANKED_1, RANKED_2, RANKED_3, RANKED_4)) chatNpc(ChatAnim.NEUTRAL, line)
                }
                pick == 3 -> {
                    chatPlayer(ChatAnim.QUIZ, INVITED)
                    chatNpc(ChatAnim.NEUTRAL, INVITED_1)
                    for (line in listOf(TUTORIAL_1, TUTORIAL_2, TUTORIAL_3)) mesbox(line)
                    chatNpc(ChatAnim.NEUTRAL, INVITED_2)
                }
                pick == 4 && !organised -> {
                    chatPlayer(ChatAnim.NEUTRAL, ORGANISE)
                    chatNpc(ChatAnim.NEUTRAL, ORGANISE_1)
                    mesbox(ORGANISE_TUTORIAL_1)
                    mesbox(ORGANISE_TUTORIAL_2)
                    organised = true
                }
                organised -> return@with chatPlayer(ChatAnim.NEUTRAL, GOODBYE)
                else -> return@with chatPlayer(ChatAnim.NEUTRAL, OKAY)
            }
        }
    }

    internal suspend fun Dialogue.fronk() = with(FronkLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        var heardCoven = false
        var afterDuties = false
        while (true) {
            val options = buildList {
                if (afterDuties) add(if (heardCoven) COVEN_AGAIN else COVEN)
                add(PLACE)
                if (!afterDuties) add(DO_HERE)
                add(WORK_OUT)
                add(FINE)
            }
            when (options[choice(*options.toTypedArray()) - 1]) {
                COVEN, COVEN_AGAIN -> {
                    chatPlayer(ChatAnim.QUIZ, if (heardCoven) COVEN_AGAIN else COVEN)
                    for (line in listOf(COVEN_1, COVEN_2, COVEN_3, COVEN_4, COVEN_5)) chatNpc(ChatAnim.NEUTRAL, line)
                    heardCoven = true
                }
                PLACE -> {
                    chatPlayer(ChatAnim.QUIZ, PLACE)
                    chatNpc(ChatAnim.NEUTRAL, PLACE_1)
                    chatNpc(ChatAnim.NEUTRAL, PLACE_2)
                }
                DO_HERE -> {
                    chatPlayer(ChatAnim.QUIZ, DO_HERE)
                    chatNpc(ChatAnim.NEUTRAL, DO_HERE_1)
                    afterDuties = true
                }
                WORK_OUT -> {
                    chatPlayer(ChatAnim.QUIZ, WORK_OUT)
                    chatNpc(ChatAnim.LAUGH, WORK_OUT_1)
                }
                else -> return@with chatPlayer(ChatAnim.NEUTRAL, FINE)
            }
        }
    }
}

fun registerEmirsArena(world: World) {
    with(EmirsArena) {
        for (dalbur in DALBURS) {
            world.handlers.onNpc(dalbur, DALBUR_TALK) { op -> world.dialogues.start(this, op.npc) { dalbur() } }
            for (op in listOf(GLIDER_OP, GLIDER_TO)) {
                world.handlers.onNpc(dalbur, op) { world.dialogues.start(this) { mesbox(ArenaStubLines.GLIDER_CLOSED) } }
            }
        }
        world.handlers.onNpc(DUEL_GUIDE, TALK_TO) { op -> world.dialogues.start(this, op.npc) { duelGuide() } }
        world.handlers.onNpc(TOURNAMENT_GUIDE, TALK_TO) { op -> world.dialogues.start(this, op.npc) { tournamentGuide() } }
        world.handlers.onNpc(FRONK, TALK_TO) { op -> world.dialogues.start(this, op.npc) { fronk() } }
        for (loc in listOf(BOARD, BOARD_2)) for (op in 1..3) world.handlers.onLoc(loc, op) { world.message(player, ArenaStubLines.ARENA_CLOSED) }
        for (loc in listOf(STEPS, STEPS_2)) world.handlers.onLoc(loc, 1) { world.message(player, ArenaStubLines.ARENA_CLOSED) }
        world.handlers.onLoc(SCOREBOARD, 1) { world.message(player, ArenaStubLines.ARENA_CLOSED) }
    }
}
