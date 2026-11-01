package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/** Doris's lines; the words are in the dialogue file, from the Wiki's Transcript:Doris. */
object DorisLines : Lines("doris", "Transcript:Doris") {
    val GREETING = line("greeting", "her first line: how the player is doing")
    val PRETTY_GOOD = line("pretty_good", "option 1, which the player then says")
    val PRETTY_GOOD_REPLY = line("pretty_good_reply", "option 1: her answer")
    val NOT_GOOD = line("not_good", "option 2, which the player then says")
    val NOT_GOOD_REPLY = line("not_good_reply", "option 2: her answer")
    val WILDERNESS = line("wilderness", "option 3: living by the Wilderness, which the player then says")
    val WILDERNESS_1 = line("wilderness_1", "option 3: her answer, first box (the warning signs)")
    val WILDERNESS_2 = line("wilderness_2", "option 3: second box (quieter than Lumbridge)")
    val WILDERNESS_3 = line("wilderness_3", "option 3: third box (Lumbridge's thieves)")
    val WILDERNESS_4 = line("wilderness_4", "option 3: fourth box (no guards)")
    val WILDERNESS_5 = line("wilderness_5", "option 3: fifth box (no Attack option)")
    val DAVE = line("dave", "option 4: how Dave became evil, which the player then says")
    val DAVE_1 = line("dave_1", "option 4: her answer, first box")
    val DAVE_2 = line("dave_2", "option 4: second box (wanting to be a monster)")
    val DAVE_3 = line("dave_3", "option 4: third box (the Tower of Fear)")
    val DAVE_4 = line("dave_4", "option 4: fourth box (hoping he'd move out)")
}

/**
 * Evil Dave's lines in the Basement of Doom, after Recipe for Disaster; from the Wiki's Transcript:Evil Dave. Each
 * exchange after a menu option is a list of boxes in order; whose box each is is in [EvilDave]'s speaker lists.
 */
object EvilDaveLines : Lines("evil_dave", "Transcript:Evil Dave") {
    val GREETING = line("greeting", "after Recipe for Disaster: his first line, welcoming the player to his basement")
    val NICE_BASEMENT = line("nice_basement", "menus: the basement, which the player then says")
    val IS_IT_NICE = line("is_it_nice", "the basement: he asks if it's nice")
    val EVIL = line("evil", "the basement: the player means evil")
    val PLANS = line("plans", "the basement: his plans to conquer the world")
    val SUMMONING = line("summoning", "basement menu, option 1: the demon-summoning, which the player then says")
    val SUMMONING_REPLIES = series("summoning", 4, "the demon-summoning, after the player's question (Dave, player, Dave, Dave)")
    val GIVE_UP = line("give_up", "menus: giving up evil, which the player then says")
    val GIVE_UP_REPLIES = series("give_up", 6, "giving up evil: his six boxes")
    val SEE_YOU = line("see_you", "menus: see you later, which the player then says")

    /** One of these, picked at random, ends the talk after the player's goodbye. */
    val FAREWELLS = series("farewell", 5, "goodbye: one of his five random farewells")

    val TIME_TRAVEL = line("time_travel", "first menu, option 3: explaining time travel again, which the player then says")
    val FROZEN = line("frozen", "time travel menu, option 1: he's still in the time freeze, which the player then says")
    val FROZEN_REPLIES = series("frozen", 9, "still in the time freeze, after the player's line (D, P, D, P, D, D, D, P, D)")
    val SAVED = line("saved", "time travel menu, option 2: saved from his point of view, which the player then says")
    val SAVED_REPLIES = series("saved", 5, "saved, after the player's line (D, P, D, P, D)")
    val FOOD = line("food", "time travel menu, option 3: taking the food back, which the player then says")
    val FOOD_REPLIES = series("food", 5, "the food, after the player's line (D, P, D, P, D), before the why menu")
    val ALREADY = line("already", "why menu, option 1: the player already has, which the player then says")
    val ALREADY_REPLIES = series("already", 3, "already, after the player's line (D, P, D)")
    val PARADOX = line("paradox", "why menu, option 2: a temporal paradox, which the player then says")
    val PARADOX_REPLIES = series("paradox", 3, "paradox, after the player's line (D, P, D)")
    val DONT_KNOW = line("dont_know", "why menu, option 3: the player doesn't know, which the player then says")
    val DONT_KNOW_REPLY = line("dont_know_reply", "don't know: his answer")
    val MAGIC = line("magic", "time travel menu, option 4: the magic requires it, which the player then says")
    val MAGIC_REPLY = line("magic_reply", "the magic: his complaint, which ends the talk")
    val NEVER_MIND = line("never_mind", "time travel menu, option 5: never mind, which the player then says")

    private fun series(prefix: String, count: Int, about: String) = (1..count).map { line("${prefix}_$it", "$about: box $it of $count") }
}

/** Hari's lines; from the Wiki's Transcript:Hari. */
object HariLines : Lines("hari", "Transcript:Hari") {
    val HELLO = line("hello", "the player's greeting")
    val HELLO_REPLY = line("hello_reply", "his greeting back")
    val WHO = line("who", "first menu, option 1: who he is, which the player then says")
    val TEACH = line("teach", "first menu, option 2: about canoeing")
    val NAME = line("name", "who he is: his name")
    val DOING = line("doing", "who he is: the player asks what he does here")
    val ADVENTURE = line("adventure", "who he is: Wilderness adventure")
    val RIVER = line("river", "who he is: the underground river north")
    val COMES_OUT = line("comes_out", "who he is: the player asks where it comes out")
    val POND = line("pond", "who he is: a pond deep in the Wilderness")
    val OFFER = line("offer", "who he is: a special canoe, and his offer to explain")
    val YES = line("yes", "that offer's menu: yes")
    val NO = line("no", "that offer's menu: no")
    val NO_THANKS = line("no_thanks", "after no: the player's last line")
    val TEACH_ME = line("teach_me", "the player asks to be taught about canoes")
    val NO_SKILL = line("no_skill", "below level 12 Woodcutting: he says the player lacks the skill")
    val LEVEL_12 = line("level_12", "below level 12: the level needed")
    val QUICKER = line("quicker", "below level 12: canoes make travel quicker")
    val CHOP = line("chop", "level 12 or more: chop the tree on the bank")
    val SHAPE = line("shape", "level 12 or more: shape the log with an axe")
    val LOG = line("log", "levels 12 to 26: only a log canoe")
    val GOOD = line("good", "levels 12 to 26: the player asks if that is good")
    val ONE_STOP = line("one_stop", "levels 12 to 26: one stop, not into the Wilderness")
    val DUGOUT = line("dugout", "levels 27 to 41: a dugout, two stops")
    val REACH_WILDERNESS = line("reach_wilderness", "levels 27 to 41: the player asks about the Wilderness")
    val NOT_SURVIVE = line("not_survive", "levels 27 to 41: the player wouldn't survive")
    val WONT_TRY = line("wont_try", "levels 27 to 41: the player won't try")
    val STABLE = line("stable", "levels 42 to 56: a stable dugout")
    val ANYWHERE = line("anywhere", "levels 42 to 56: anywhere but the Wilderness")
    val ONLY_WAKA = line("only_waka", "levels 42 to 56: only a waka goes there")
    val WHAT_WAKA = line("what_waka", "levels 42 and up: the player asks what a waka is")
    val COME_BACK = line("come_back", "levels 42 to 56: come back after more Woodcutting")
    val CAN_WAKA = line("can_waka", "level 57 or more: the player can make a waka")
    val WAKA = line("waka", "level 57 or more: a strong, fast canoe")
    val ANY_DESTINATION = line("any_destination", "level 57 or more: the player asks if it goes anywhere")
    val NORTH = line("north", "level 57 or more: north underground to a Wilderness pond")
    val LOST = line("lost", "level 57 or more: many have been lost there")
}

/** Lucien's lines in his house, after Temple of Ikov on his side; from the Wiki's Transcript:Lucien. */
object LucienLines : Lines("lucien", "Transcript:Lucien") {
    val WHAT = line("what", "after Temple of Ikov, sided with Lucien: what the player wants")
    val LOST_PENDANT = line("lost_pendant", "with no Pendant of Lucien: the player lost it")
    val NEW_PENDANT = line("new_pendant", "with no Pendant of Lucien: he gives another")
    val NOTHING = line("nothing", "the player says it's nothing")
    val LEAVE = line("leave", "he tells the player to leave")
}

/** Edgeville's lines for the places in step 3 that aren't built yet: ours, not live's. */
object EdgevilleClosedLines : Lines("edgeville_closed", "Edgeville") {
    val LEVER = line("lever", "Pull on the Wilderness lever: ours, the Wilderness isn't built yet")
    val FAIRY_RING = line("fairy_ring", "any option on the fairy ring: ours, the fairy rings aren't built yet")
    val SOUL_WARS = line("soul_wars", "Enter on the Soul Wars portal: ours, Soul Wars isn't built yet")
    val DEATHS_DOMAIN = line("deaths_domain", "Enter on Death's Domain: ours, Death's Domain isn't built yet")
    val CANOE = line("canoe", "the canoe station: ours, canoes aren't built yet")
}

/**
 * Edgeville's people (Edgeville piece step 3a): Doris in her house west of the bank, Evil Dave and his hell-rats in the
 * Basement of Doom below it, Hari by the canoe station and Lucien in the house east of the willows, with refusals on
 * the places not built yet. Every account has every quest done, so each talks as after its quest. From the 241 cache:
 * the NPC and loc types, the trapdoor's varbit and the basement's tiles; from the Wiki: each conversation. Expressions
 * are guessed, as the Wiki notes none.
 *
 * - The basement's trapdoor (`100_dave_celler_trapdoor`, 12266, at 3077,3493) is a multiloc on varbit 1888
 *   (`hundred_dave_trapdoor`): 0 shows the closed trapdoor (Open), 1 the open one (Go-down, Close). Each player opens
 *   their own. Guessed: the open and close animations (furniture's), and where the player lands at each end.
 * - The hell-rats and Hell-Rat Behemoths have no options in the cache: they wait for cats, which hunt them.
 * - Lucien (`ikov_lucien2`, 13644) is a multi-NPC on varbit 10753 (`ikov_lucien_vis`); 1 shows `ikov_lucien2_vis_noattack`
 *   (13608, Talk-to only). Inferred, not seen: that 1 is "after the quest, on his side". The account counts as having
 *   sided with Lucien, so he isn't attackable; the Guardians' side would be Sam's call.
 * - Hari's Store-axe waits for canoeing, as Barfy Bill's does.
 */
object EdgevillePeople {
    const val DORIS = 4808
    const val EVIL_DAVE = 4806
    const val HARI = 1325
    const val LUCIEN = 13644
    const val LUCIEN_SHOWN = 13608
    const val TALK_TO = 1

    /** ikov_lucien_vis: 1 shows Lucien in his house, Talk-to only. */
    const val VARBIT_LUCIEN = 10753

    /** ikov_pendantoflucien. */
    const val PENDANT_OF_LUCIEN = 86

    const val TRAPDOOR = 12266
    const val VARBIT_TRAPDOOR = 1888
    const val CELLAR_STAIRS = 12265

    /** Guessed: east of the cellar stairs, in the basement. */
    const val BASEMENT_X = 3077
    const val BASEMENT_Z = 9893

    /** Guessed: east of the trapdoor, in Doris's house. */
    const val HOUSE_X = 3078
    const val HOUSE_Z = 3493

    const val LEVER = 26761
    const val FAIRY_RING = 29495
    const val SOUL_WARS_PORTAL = 40474
    const val DEATHS_DOMAIN = 39547
    const val CANOE_STATION = 12166

    internal suspend fun Dialogue.doris() = with(DorisLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (choice(PRETTY_GOOD, NOT_GOOD, WILDERNESS, DAVE)) {
            1 -> {
                chatPlayer(ChatAnim.HAPPY, PRETTY_GOOD)
                chatNpc(ChatAnim.HAPPY, PRETTY_GOOD_REPLY)
            }
            2 -> {
                chatPlayer(ChatAnim.SAD, NOT_GOOD)
                chatNpc(ChatAnim.SAD, NOT_GOOD_REPLY)
            }
            3 -> {
                chatPlayer(ChatAnim.QUIZ, WILDERNESS)
                for (line in listOf(WILDERNESS_1, WILDERNESS_2, WILDERNESS_3, WILDERNESS_4, WILDERNESS_5)) chatNpc(ChatAnim.NEUTRAL, line)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, DAVE)
                for (line in listOf(DAVE_1, DAVE_2, DAVE_3, DAVE_4)) chatNpc(ChatAnim.NEUTRAL, line)
            }
        }
    }

    /** Who says each box of Evil Dave's exchanges, from the Wiki's transcript: true is Dave, false the player. */
    private val SUMMONING_SPEAKERS = listOf(true, false, true, true)
    private val FROZEN_SPEAKERS = listOf(true, false, true, false, true, true, true, false, true)
    private val ALTERNATING = listOf(true, false, true, false, true)

    /** Evil Dave's talk after Recipe for Disaster; [pick] chooses his farewell, from 0 until its argument. */
    internal suspend fun Dialogue.evilDave(pick: (Int) -> Int) = with(EvilDaveLines) {
        chatNpc(ChatAnim.LAUGH, GREETING)
        when (choice(NICE_BASEMENT, GIVE_UP, TIME_TRAVEL)) {
            1 -> basement(pick)
            2 -> giveUp(pick)
            else -> timeTravel()
        }
    }

    /** [lines] in order, Dave saying those [speakers] marks true and the player the others. */
    private suspend fun Dialogue.exchange(lines: List<Line>, speakers: List<Boolean>) {
        for ((i, line) in lines.withIndex()) {
            if (speakers[i]) chatNpc(ChatAnim.NEUTRAL, line) else chatPlayer(ChatAnim.NEUTRAL, line)
        }
    }

    private suspend fun Dialogue.basement(pick: (Int) -> Int): Unit = with(EvilDaveLines) {
        chatPlayer(ChatAnim.HAPPY, NICE_BASEMENT)
        chatNpc(ChatAnim.CONFUSED, IS_IT_NICE)
        chatPlayer(ChatAnim.NEUTRAL, EVIL)
        chatNpc(ChatAnim.LAUGH, PLANS)
        while (true) {
            when (choice(SUMMONING, GIVE_UP, SEE_YOU)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, SUMMONING)
                    exchange(SUMMONING_REPLIES, SUMMONING_SPEAKERS)
                }
                2 -> return@with giveUp(pick)
                else -> return@with farewell(pick)
            }
        }
    }

    private suspend fun Dialogue.giveUp(pick: (Int) -> Int): Unit = with(EvilDaveLines) {
        chatPlayer(ChatAnim.QUIZ, GIVE_UP)
        for (line in GIVE_UP_REPLIES) chatNpc(ChatAnim.NEUTRAL, line)
        if (choice(NICE_BASEMENT, SEE_YOU) == 1) basement(pick) else farewell(pick)
    }

    private suspend fun Dialogue.farewell(pick: (Int) -> Int) = with(EvilDaveLines) {
        chatPlayer(ChatAnim.NEUTRAL, SEE_YOU)
        chatNpc(ChatAnim.LAUGH, FAREWELLS[pick(FAREWELLS.size)])
    }

    private suspend fun Dialogue.timeTravel() = with(EvilDaveLines) {
        chatPlayer(ChatAnim.NEUTRAL, TIME_TRAVEL)
        while (true) {
            when (choice(FROZEN, SAVED, FOOD, MAGIC, NEVER_MIND)) {
                1 -> {
                    chatPlayer(ChatAnim.NEUTRAL, FROZEN)
                    exchange(FROZEN_REPLIES, FROZEN_SPEAKERS)
                }
                2 -> {
                    chatPlayer(ChatAnim.NEUTRAL, SAVED)
                    exchange(SAVED_REPLIES, ALTERNATING)
                }
                3 -> {
                    chatPlayer(ChatAnim.NEUTRAL, FOOD)
                    exchange(FOOD_REPLIES, ALTERNATING)
                    when (choice(ALREADY, PARADOX, DONT_KNOW)) {
                        1 -> {
                            chatPlayer(ChatAnim.NEUTRAL, ALREADY)
                            exchange(ALREADY_REPLIES, ALTERNATING)
                        }
                        2 -> {
                            chatPlayer(ChatAnim.SHOCKED, PARADOX)
                            exchange(PARADOX_REPLIES, ALTERNATING)
                        }
                        else -> {
                            chatPlayer(ChatAnim.CONFUSED, DONT_KNOW)
                            chatNpc(ChatAnim.NEUTRAL, DONT_KNOW_REPLY)
                        }
                    }
                }
                4 -> {
                    chatPlayer(ChatAnim.NEUTRAL, MAGIC)
                    chatNpc(ChatAnim.ANGRY, MAGIC_REPLY)
                    return@with
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, NEVER_MIND)
                    return@with
                }
            }
        }
    }

    internal suspend fun Dialogue.hari() = with(HariLines) {
        chatPlayer(ChatAnim.NEUTRAL, HELLO)
        chatNpc(ChatAnim.NEUTRAL, HELLO_REPLY)
        if (choice(WHO, TEACH) == 1) hariWho() else hariTeach()
    }

    private suspend fun Dialogue.hariWho() = with(HariLines) {
        chatPlayer(ChatAnim.QUIZ, WHO)
        chatNpc(ChatAnim.NEUTRAL, NAME)
        chatPlayer(ChatAnim.QUIZ, DOING)
        chatNpc(ChatAnim.NEUTRAL, ADVENTURE)
        chatNpc(ChatAnim.NEUTRAL, RIVER)
        chatPlayer(ChatAnim.QUIZ, COMES_OUT)
        chatNpc(ChatAnim.NEUTRAL, POND)
        chatNpc(ChatAnim.QUIZ, OFFER)
        if (choice(YES, NO) == 1) hariTeach() else chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
    }

    private suspend fun Dialogue.hariTeach() = with(HariLines) {
        chatPlayer(ChatAnim.QUIZ, TEACH_ME)
        val level = player.skills.baseLevel(Skill.WOODCUTTING)
        if (level < BarfyBill.LOG_LEVEL) {
            chatNpc(ChatAnim.NEUTRAL, NO_SKILL)
            chatNpc(ChatAnim.NEUTRAL, LEVEL_12)
            chatNpc(ChatAnim.NEUTRAL, QUICKER)
            return@with
        }
        chatNpc(ChatAnim.NEUTRAL, CHOP)
        chatNpc(ChatAnim.NEUTRAL, SHAPE)
        when {
            level < BarfyBill.DUGOUT_LEVEL -> {
                chatNpc(ChatAnim.NEUTRAL, LOG)
                chatPlayer(ChatAnim.QUIZ, GOOD)
                chatNpc(ChatAnim.NEUTRAL, ONE_STOP)
            }
            level < BarfyBill.STABLE_DUGOUT_LEVEL -> {
                chatNpc(ChatAnim.HAPPY, DUGOUT)
                chatPlayer(ChatAnim.QUIZ, REACH_WILDERNESS)
                chatNpc(ChatAnim.SCARED, NOT_SURVIVE)
                chatPlayer(ChatAnim.SCARED, WONT_TRY)
            }
            level < BarfyBill.WAKA_LEVEL -> {
                chatNpc(ChatAnim.HAPPY, STABLE)
                chatNpc(ChatAnim.NEUTRAL, ANYWHERE)
                chatNpc(ChatAnim.NEUTRAL, ONLY_WAKA)
                chatPlayer(ChatAnim.QUIZ, WHAT_WAKA)
                chatNpc(ChatAnim.NEUTRAL, COME_BACK)
            }
            else -> {
                chatNpc(ChatAnim.HAPPY, CAN_WAKA)
                chatPlayer(ChatAnim.QUIZ, WHAT_WAKA)
                chatNpc(ChatAnim.NEUTRAL, WAKA)
                chatPlayer(ChatAnim.QUIZ, ANY_DESTINATION)
                chatNpc(ChatAnim.NEUTRAL, NORTH)
                chatNpc(ChatAnim.SCARED, LOST)
            }
        }
    }

    /** Lucien's talk; [give] hands the player a new pendant when they own none (carried, worn or banked). */
    internal suspend fun Dialogue.lucien(give: (Player, Int) -> Unit) = with(LucienLines) {
        chatNpc(ChatAnim.ANGRY, WHAT)
        val owns = player.inventory.count(PENDANT_OF_LUCIEN) > 0 || player.equipment.count(PENDANT_OF_LUCIEN) > 0 ||
            player.bank.count(PENDANT_OF_LUCIEN) > 0
        if (!owns) {
            chatPlayer(ChatAnim.SAD, LOST_PENDANT)
            give(player, PENDANT_OF_LUCIEN)
            chatNpc(ChatAnim.ANGRY, NEW_PENDANT)
        }
        chatPlayer(ChatAnim.NEUTRAL, NOTHING)
        chatNpc(ChatAnim.ANGRY, LEAVE)
    }
}

private val logger = KotlinLogging.logger {}

fun registerEdgevillePeople(world: World) {
    with(EdgevillePeople) {
        world.handlers.onNpc(DORIS, TALK_TO) { op -> world.dialogues.start(this, op.npc) { doris() } }
        world.handlers.onNpc(EVIL_DAVE, TALK_TO) { op ->
            world.dialogues.start(this, op.npc) { evilDave { world.random.nextInt(it) } }
        }
        world.handlers.onNpc(HARI, TALK_TO) { op -> world.dialogues.start(this, op.npc) { hari() } }
        val give: (Player, Int) -> Unit = { player, obj ->
            world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
        }
        // A multi-NPC's options are the shown type's.
        world.handlers.onNpc(LUCIEN_SHOWN, TALK_TO) { op -> world.dialogues.start(this, op.npc) { lucien(give) } }

        // The trapdoor: op 1 is Open while closed and Go-down while open; op 2 is Close.
        world.handlers.onLoc(TRAPDOOR, 1) { op ->
            if (player.vars.getVarbit(VARBIT_TRAPDOOR) == 0) {
                arriveDelay()
                player.anim(Searching.ANIM_OPEN)
                player.vars.setVarbit(VARBIT_TRAPDOOR, 1)
            } else {
                arriveDelay()
                player.anim(Climbing.ANIM_PICKUP_FLOOR)
                delay(1)
                world.teleport(player, BASEMENT_X, BASEMENT_Z, op.loc!!.level)
            }
        }
        world.handlers.onLoc(TRAPDOOR, 2) {
            if (player.vars.getVarbit(VARBIT_TRAPDOOR) == 0) return@onLoc
            arriveDelay()
            player.anim(Searching.ANIM_SHUT)
            player.vars.setVarbit(VARBIT_TRAPDOOR, 0)
        }
        world.handlers.onLoc(CELLAR_STAIRS, 1) { op ->
            arriveDelay()
            delay(1)
            world.teleport(player, HOUSE_X, HOUSE_Z, op.loc!!.level)
        }

        world.handlers.onLoc(LEVER, 1) { world.message(player, EdgevilleClosedLines.LEVER) }
        for (option in 1..4) world.handlers.onLoc(FAIRY_RING, option) { world.message(player, EdgevilleClosedLines.FAIRY_RING) }
        world.handlers.onLoc(SOUL_WARS_PORTAL, 1) { world.message(player, EdgevilleClosedLines.SOUL_WARS) }
        world.handlers.onLoc(DEATHS_DOMAIN, 1) { world.message(player, EdgevilleClosedLines.DEATHS_DOMAIN) }
        world.handlers.onLoc(CANOE_STATION, 1) { world.message(player, EdgevilleClosedLines.CANOE) }
    }
}
