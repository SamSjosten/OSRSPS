package osrsps.game.content

import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Our own line for the Farming options of the gardeners and Tool Leprechauns, which wait for the Farming skill thread. */
object FarmsRoadsLines : Lines("farms_roads", "Farming") {
    val FARMING_LATER = line("farming_later", "a gardener's Pay or Trade, a Tool Leprechaun's Exchange or Deposit-all, or their talk's Farming options: our own line, as Farming isn't built")
}

/** The patch gardeners' menu, from the Wiki's Transcript:Dreven (the same for every gardener, inferred). */
object GardenerLines : Lines("gardener", "Transcript:Dreven") {
    val LOOK_AFTER = line("look_after", "Talk-to's menu, option 1, which the player then says")
    val ADVICE = line("advice", "Talk-to's menu, option 2, which the player then says (guessed: the Wiki has no members' answer)")
    val SELL = line("sell", "Talk-to's menu, option 3, which the player then says (guessed, as above)")
    val ANOTHER_TIME = line("another_time", "Talk-to's menu, option 4, which the player then says")
}

/** The Tool Leprechauns' lines, from the Wiki's Transcript:Tool Leprechaun. */
object ToolLeprechaunLines : Lines("tool_leprechaun", "Transcript:Tool Leprechaun") {
    val GREETING = line("greeting", "Talk-to, his first line, asking whether to store the player's tools")
    val YES_PLEASE = line("yes_please", "his menu, option 1 (the store, which waits for Farming)")
    val WHAT_STORE = line("what_store", "his menu, option 2, which the player then says")
    val WHAT_DO = line("what_do", "his menu, option 3")
    val NO_THANKS = line("no_thanks", "his menu, option 4, which the player then says")
    val STORE_TOOLS = line("store_tools", "what he stores: the tools")
    val STORE_BUCKETS = line("store_buckets", "what he stores: buckets, composts and plant cure")
    val STORE_NOTES = line("store_notes", "what he stores: produce changed into banknotes")
    val STORE_ASK = line("store_ask", "after what he stores or his shed: whether to use the store")
    val WHAT_DO_ASK = line("what_do_ask", "option 3 as the player says it, about his pockets")
    val SHED = line("shed", "his magic shed")
    val DAFTER = line("dafter", "after no thanks: his last line")
}

/** Tarquin's lines; from the Wiki's Transcript:Tarquin. */
object TarquinLines : Lines("tarquin", "Transcript:Tarquin") {
    val HELLO = line("hello", "the player's greeting")
    val OLD_BEAN = line("old_bean", "his greeting back")
    val WHO = line("who", "first menu, option 1: who he is, which the player then says")
    val TEACH = line("teach", "first menu, option 2: about canoeing")
    val TEACH_SAID = line("teach_said", "option 2 as the player says it")
    val NAME = line("name", "who he is: his name")
    val HEARD = line("heard", "who he is: the player should have heard of him")
    val WHY_HEARD = line("why_heard", "who he is: the player asks why")
    val PRONOUNCED = line("pronounced", "who he is: how his name is said")
    val ROYAL = line("royal", "who he is: the royal family of Misthalin")
    val ROALD = line("roald", "who he is: the player asks about King Roald")
    val CLOSELY = line("closely", "who he is: quite closely")
    val COUSIN = line("cousin", "who he is: fourth cousin")
    val DOING = line("doing", "who he is: the player asks what he does here")
    val CANOEING = line("canoeing", "who he is: canoeing, and his offer to explain")
    val YES = line("yes", "that offer's menu: yes")
    val NO = line("no", "that offer's menu: no")
    val NO_THANKS = line("no_thanks", "after no: the player's last line")
    val TEACH_ME = line("teach_me", "after yes: the player asks to be taught about canoes")
    val NO_SKILL = line("no_skill", "below level 12 Woodcutting: he says the player lacks the skill")
    val LEVEL_12 = line("level_12", "below level 12: the level needed")
    val QUICKER = line("quicker", "below level 12: canoes make travel quicker")
    val CHOP = line("chop", "level 12 or more: chop the tree on the bank")
    val SHAPE = line("shape", "level 12 or more: shape the log with an axe")
    val FAVOURITE = line("favourite", "level 12 or more: the Stable Dugout, his favourite")
    val LENGTH = line("length", "level 12 or more: the length of the Lum")
    val OTHERS = line("others", "level 12 or more: other canoes")
    val LOG_FOOL = line("log_fool", "levels 12 to 26: the fool on a log near Barbarian Village")
    val LOG_ONLY = line("log_only", "levels 12 to 26: only a log, one stop")
    val SATISFY = line("satisfy", "levels 12 to 26: it will satisfy the player")
    val MEANING = line("meaning", "levels 12 to 26: the player asks what he means")
    val PROFANE = line("profane", "levels 12 to 26: his reply")
    val PFFT = line("pfft", "levels 12 to 26: the player's last line")
    val HANDY = line("handy", "levels 27 to 41: handy with an axe")
    val DUGOUT = line("dugout", "levels 27 to 41: the Dugout, two stops")
    val LIMITED = line("limited", "levels 27 to 41: limited means")
    val WHAT_LIMITED = line("what_limited", "levels 27 to 41: the player asks what he means")
    val ITINERANT = line("itinerant", "levels 27 to 41: an itinerant adventurer")
    val CLUTTER = line("clutter", "levels 27 to 41: cluttering up his river")
    val STABLE = line("stable", "levels 42 to 56: the Stable Dugout, any civilised place")
    val PEDIGREE = line("pedigree", "levels 42 to 56: his boat club")
    val CHARMING = line("charming", "levels 42 to 56: the player's reply")
    val BE_OFF = line("be_off", "levels 42 to 56: his last line")
    val WAKA = line("waka", "level 57 or more: the player can make a Waka")
    val WHATS_WAKA = line("whats_waka", "level 57 or more: the player asks what a Waka is")
    val ONE_MAN = line("one_man", "level 57 or more: the man near Edgeville")
    val ROUTE_BACK = line("route_back", "level 57 or more: born in the Wilderness")
    val TRUE = line("true", "level 57 or more: the player asks if it is true")
    val CONSORT = line("consort", "level 57 or more: his last line")
}

/** Rat Burgiss's lines; from the Wiki's Transcript:Rat Burgiss, its standard (members') dialogue. */
object RatBurgissLines : Lines("rat_burgiss", "Transcript:Rat Burgiss") {
    val HELLO = line("hello", "the player's greeting")
    val BUSY = line("busy", "his reply: too busy to chat")
    val SORRY = line("sorry", "the player's apology")
    val FAREWELL = line("farewell", "his last line")
}

/** Seth Groats's line; from the Wiki's Transcript:Seth Groats. */
object SethGroatsLines : Lines("seth_groats", "Transcript:Seth Groats") {
    val MORNING = line("morning", "Talk-to: his only line")
}

/**
 * Misthalin piece 7 step 5: the farms and roads between Lumbridge, Draynor and Varrock. The NPCs are in
 * misthalin-farms-roads.json; this file gives the ones with options something to say.
 *
 * - The patch gardeners Dreven (bush patch south of the Champions' Guild, 2674), Vasquen (hops patch north of Lumbridge,
 *   2672), Fayeth (tree patch west of Lumbridge Castle, 2681) and Treznor (tree patch outside Varrock Palace, 2680).
 *   Talk-to shows the Wiki's four options (Transcript:Dreven, read 2026-11-10; the menu is taken to be every gardener's,
 *   inferred). Looking after crops, advice and the Farming Supplies shop belong to the Farming skill thread (Sam, 2 Nov:
 *   a town piece only places a skill's NPCs), so those options, Pay and Trade give [FarmsRoadsLines.FARMING_LATER].
 * - The Tool Leprechauns (`farming_tools_leprechaun`, 0) by each of those patches and Draynor Manor's belladonna patch:
 *   Talk-to follows Transcript:Tool Leprechaun (read 2026-11-10); its store, Exchange and Deposit-all wait for Farming.
 * - Tarquin (1323) at the canoe station by the Champions' Guild: Talk-to follows Transcript:Tarquin (read 2026-11-10),
 *   by Woodcutting level as Barfy Bill's does. Store-axe waits for canoeing (step 11), as Barfy Bill's does.
 * - Rat Burgiss (4158) on the Lumbridge to Varrock road and Seth Groats (1351) north of Groats' farm: their only lines.
 *
 * Guessed: every chathead expression, and that a gardener's player repeats the chosen option before the refusal.
 */
object FarmsRoads {
    const val DREVEN = 2674
    const val VASQUEN = 2672
    const val FAYETH = 2681
    const val TREZNOR = 2680
    val GARDENERS = listOf(DREVEN, VASQUEN, FAYETH, TREZNOR)
    const val TOOL_LEPRECHAUN = 0
    const val TARQUIN = 1323
    const val RAT_BURGISS = 4158
    const val SETH_GROATS = 1351

    const val TALK_TO = 1
    const val PAY = 3
    const val TRADE = 5
    const val EXCHANGE = 3
    const val DEPOSIT_ALL = 4

    internal suspend fun Dialogue.gardener() = with(GardenerLines) {
        when (choice(LOOK_AFTER, ADVICE, SELL, ANOTHER_TIME)) {
            1 -> farmingLater(LOOK_AFTER)
            2 -> farmingLater(ADVICE)
            3 -> farmingLater(SELL)
            else -> chatPlayer(ChatAnim.NEUTRAL, ANOTHER_TIME)
        }
    }

    private suspend fun Dialogue.farmingLater(said: osrsps.game.dialogue.Line) {
        chatPlayer(ChatAnim.QUIZ, said)
        mesbox(FarmsRoadsLines.FARMING_LATER)
    }

    internal suspend fun Dialogue.leprechaun() = with(ToolLeprechaunLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        while (true) {
            when (choice(YES_PLEASE, WHAT_STORE, WHAT_DO, NO_THANKS)) {
                1 -> return@with mesbox(FarmsRoadsLines.FARMING_LATER)
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, WHAT_STORE)
                    chatNpc(ChatAnim.HAPPY, STORE_TOOLS)
                    chatNpc(ChatAnim.HAPPY, STORE_BUCKETS)
                    chatNpc(ChatAnim.HAPPY, STORE_NOTES)
                    chatNpc(ChatAnim.QUIZ, STORE_ASK)
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, WHAT_DO_ASK)
                    chatNpc(ChatAnim.HAPPY, SHED)
                    chatNpc(ChatAnim.QUIZ, STORE_ASK)
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
                    chatNpc(ChatAnim.LAUGH, DAFTER)
                    return@with
                }
            }
        }
    }

    internal suspend fun Dialogue.tarquin() = with(TarquinLines) {
        chatPlayer(ChatAnim.NEUTRAL, HELLO)
        chatNpc(ChatAnim.HAPPY, OLD_BEAN)
        if (choice(WHO, TEACH) == 1) tarquinWho() else {
            chatPlayer(ChatAnim.QUIZ, TEACH_SAID)
            tarquinTeach()
        }
    }

    private suspend fun Dialogue.tarquinWho() = with(TarquinLines) {
        chatPlayer(ChatAnim.QUIZ, WHO)
        chatNpc(ChatAnim.HAPPY, NAME)
        chatNpc(ChatAnim.HAPPY, HEARD)
        chatPlayer(ChatAnim.QUIZ, WHY_HEARD)
        chatNpc(ChatAnim.ANGRY, PRONOUNCED)
        chatNpc(ChatAnim.HAPPY, ROYAL)
        chatPlayer(ChatAnim.QUIZ, ROALD)
        chatNpc(ChatAnim.HAPPY, CLOSELY)
        chatNpc(ChatAnim.HAPPY, COUSIN)
        chatPlayer(ChatAnim.CONFUSED, DOING)
        chatNpc(ChatAnim.HAPPY, CANOEING)
        if (choice(YES, NO) == 1) {
            chatPlayer(ChatAnim.QUIZ, TEACH_ME)
            tarquinTeach()
        } else {
            chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
        }
    }

    private suspend fun Dialogue.tarquinTeach() = with(TarquinLines) {
        val level = player.skills.baseLevel(Skill.WOODCUTTING)
        if (level < BarfyBill.LOG_LEVEL) {
            chatNpc(ChatAnim.NEUTRAL, NO_SKILL)
            chatNpc(ChatAnim.NEUTRAL, LEVEL_12)
            chatNpc(ChatAnim.NEUTRAL, QUICKER)
            return@with
        }
        chatNpc(ChatAnim.HAPPY, CHOP)
        chatNpc(ChatAnim.HAPPY, SHAPE)
        chatNpc(ChatAnim.HAPPY, FAVOURITE)
        chatNpc(ChatAnim.HAPPY, LENGTH)
        chatNpc(ChatAnim.NEUTRAL, OTHERS)
        when {
            level < BarfyBill.DUGOUT_LEVEL -> {
                chatNpc(ChatAnim.LAUGH, LOG_FOOL)
                chatNpc(ChatAnim.NEUTRAL, LOG_ONLY)
                chatNpc(ChatAnim.NEUTRAL, SATISFY)
                chatPlayer(ChatAnim.ANGRY, MEANING)
                chatNpc(ChatAnim.ANGRY, PROFANE)
                chatPlayer(ChatAnim.ANGRY, PFFT)
            }
            level < BarfyBill.STABLE_DUGOUT_LEVEL -> {
                chatNpc(ChatAnim.HAPPY, HANDY)
                chatNpc(ChatAnim.NEUTRAL, DUGOUT)
                chatNpc(ChatAnim.NEUTRAL, LIMITED)
                chatPlayer(ChatAnim.ANGRY, WHAT_LIMITED)
                chatNpc(ChatAnim.NEUTRAL, ITINERANT)
                chatNpc(ChatAnim.ANGRY, CLUTTER)
            }
            level < BarfyBill.WAKA_LEVEL -> {
                chatNpc(ChatAnim.HAPPY, STABLE)
                chatNpc(ChatAnim.NEUTRAL, PEDIGREE)
                chatPlayer(ChatAnim.ANGRY, CHARMING)
                chatNpc(ChatAnim.ANGRY, BE_OFF)
            }
            else -> {
                chatNpc(ChatAnim.CONFUSED, WAKA)
                chatPlayer(ChatAnim.QUIZ, WHATS_WAKA)
                chatNpc(ChatAnim.NEUTRAL, ONE_MAN)
                chatNpc(ChatAnim.NEUTRAL, ROUTE_BACK)
                chatPlayer(ChatAnim.SHOCKED, TRUE)
                chatNpc(ChatAnim.ANGRY, CONSORT)
            }
        }
    }

    internal suspend fun Dialogue.ratBurgiss() = with(RatBurgissLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        chatNpc(ChatAnim.NEUTRAL, BUSY)
        chatPlayer(ChatAnim.NEUTRAL, SORRY)
        chatNpc(ChatAnim.HAPPY, FAREWELL)
    }
}

/** The farms' and roads' Talk-to, and the Farming refusals. */
fun registerFarmsRoads(world: World) {
    with(FarmsRoads) {
        for (gardener in GARDENERS) {
            world.handlers.onNpc(gardener, TALK_TO) { op -> world.dialogues.start(this, op.npc) { gardener() } }
            world.handlers.onNpc(gardener, PAY) { world.message(player, FarmsRoadsLines.FARMING_LATER) }
            world.handlers.onNpc(gardener, TRADE) { world.message(player, FarmsRoadsLines.FARMING_LATER) }
        }
        world.handlers.onNpc(TOOL_LEPRECHAUN, TALK_TO) { op -> world.dialogues.start(this, op.npc) { leprechaun() } }
        world.handlers.onNpc(TOOL_LEPRECHAUN, EXCHANGE) { world.message(player, FarmsRoadsLines.FARMING_LATER) }
        world.handlers.onNpc(TOOL_LEPRECHAUN, DEPOSIT_ALL) { world.message(player, FarmsRoadsLines.FARMING_LATER) }
        world.handlers.onNpc(TARQUIN, TALK_TO) { op -> world.dialogues.start(this, op.npc) { tarquin() } }
        world.handlers.onNpc(RAT_BURGISS, TALK_TO) { op -> world.dialogues.start(this, op.npc) { ratBurgiss() } }
        world.handlers.onNpc(SETH_GROATS, TALK_TO) { op ->
            world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.HAPPY, SethGroatsLines.MORNING) }
        }
    }
}
