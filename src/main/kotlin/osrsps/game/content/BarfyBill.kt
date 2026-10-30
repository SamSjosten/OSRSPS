package osrsps.game.content

import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Barfy Bill's lines; the words are in the dialogue file, from the Wiki's Transcript:Barfy Bill, read 2026-10-30. */
object BarfyBillLines : Lines("barfy_bill", "Transcript:Barfy Bill") {
    val HELLO = line("hello", "the player's greeting")
    val OH_HELLO = line("oh_hello", "his greeting back")
    val WHO = line("who", "first menu, option 1: who he is, which the player then says")
    val TEACH = line("teach", "first menu, option 2: about canoeing")
    val NAME = line("name", "who he is: his name")
    val EX_CAPTAIN = line("ex_captain", "who he is: the player asks about the sea captain")
    val SHIP = line("ship", "who he is: the ship he bought")
    val WHY_NOT = line("why_not", "who he is: the player asks why he stopped sailing")
    val SEA_SICK = line("sea_sick", "who he is: his sea sickness")
    val SAVED_MONEY = line("saved_money", "who he is: the money he could have saved")
    val NOW = line("now", "who he is: the player asks what he does now")
    val FIRE = line("fire", "who he is: the ship's fire")
    val CANOEING = line("canoeing", "who he is: why he canoes")
    val RIVER_SICK = line("river_sick", "who he is: he isn't river sick")
    val KNOW_HOW = line("know_how", "who he is: his offer to explain canoes")
    val YES = line("yes", "that offer's menu: yes")
    val NO = line("no", "that offer's menu: no")
    val NO_THANKS = line("no_thanks", "after no: the player's last line")
    val TEACH_ME = line("teach_me", "the player asks to be taught about canoes")
    val NO_SKILL = line("no_skill", "below level 12 Woodcutting: he says the player lacks the skill")
    val LEVEL_12 = line("level_12", "below level 12: the level needed")
    val QUICKER = line("quicker", "below level 12: canoes make travel quicker")
    val CHOP = line("chop", "level 12 or more: chop the tree on the bank")
    val SHAPE = line("shape", "level 12 or more: shape the log with an axe")
    val NO_TALENT = line("no_talent", "levels 12 to 26: no talent in woodcutting")
    val MEAN = line("mean", "levels 12 to 26: the player asks what he means")
    val CALLOUSES = line("callouses", "levels 12 to 26: no callouses, no splinters")
    val LOG = line("log", "levels 12 to 26: only a log canoe, one stop")
    val DUGOUT = line("dugout", "levels 27 to 41: the dugout")
    val HOW_FAR = line("how_far", "levels 27 to 41: the player asks how far")
    val TWO_STOPS = line("two_stops", "levels 27 to 41: two stops")
    val STABLE = line("stable", "levels 42 to 56: the stable dugout")
    val ANY_PLACE = line("any_place", "levels 42 to 56: any place on the river")
    val WILDERNESS = line("wilderness", "levels 42 to 56: the player asks about the Wilderness")
    val WAKA_MAN = line("waka_man", "levels 42 to 56: the man near Edgeville with a waka")
    val HELLISH = line("hellish", "levels 42 to 56: why anyone would go there")
    val AXE = line("axe", "level 57 or more: he sees the player knows an axe")
    val WAKA = line("waka", "level 57 or more: the waka, and the Wilderness")
    val EVIL = line("evil", "level 57 or more: the evil there")
    val WARNING = line("warning", "level 57 or more: the player's thanks")
}

/**
 * Barfy Bill at the canoe station north of the castle bridge (NPC 1326), one of the River Lum's canoe instructors.
 * Talk-to follows RSMod's script, which is the Wiki's transcript; what he says about canoes goes by the Woodcutting
 * level, as the transcript's bands have it. Guessed: RSMod's chathead expressions, with scared for its "worried",
 * which the 241 client has no expression for. Left out: canoes themselves and his Store-axe (added 1 April 2026, with
 * no transcript yet), which wait for canoeing; Store-axe says nothing yet.
 */
object BarfyBill {
    const val ID = 1326
    const val TALK_TO = 1

    internal suspend fun Dialogue.talk() = with(BarfyBillLines) {
        chatPlayer(ChatAnim.NEUTRAL, HELLO)
        chatNpc(ChatAnim.NEUTRAL, OH_HELLO)
        if (choice(WHO, TEACH) == 1) who() else teach()
    }

    private suspend fun Dialogue.who() = with(BarfyBillLines) {
        chatPlayer(ChatAnim.NEUTRAL, WHO)
        chatNpc(ChatAnim.NEUTRAL, NAME)
        chatPlayer(ChatAnim.QUIZ, EX_CAPTAIN)
        chatNpc(ChatAnim.SAD, SHIP)
        chatPlayer(ChatAnim.QUIZ, WHY_NOT)
        chatNpc(ChatAnim.SAD, SEA_SICK)
        chatNpc(ChatAnim.NEUTRAL, SAVED_MONEY)
        chatPlayer(ChatAnim.CONFUSED, NOW)
        chatNpc(ChatAnim.SHIFTY, FIRE)
        chatNpc(ChatAnim.NEUTRAL, CANOEING)
        chatNpc(ChatAnim.HAPPY, RIVER_SICK)
        chatNpc(ChatAnim.QUIZ, KNOW_HOW)
        if (choice(YES, NO) == 1) teach() else chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
    }

    private suspend fun Dialogue.teach() = with(BarfyBillLines) {
        chatPlayer(ChatAnim.QUIZ, TEACH_ME)
        val level = player.skills.baseLevel(Skill.WOODCUTTING)
        if (level < LOG_LEVEL) {
            chatNpc(ChatAnim.NEUTRAL, NO_SKILL)
            chatNpc(ChatAnim.NEUTRAL, LEVEL_12)
            chatNpc(ChatAnim.NEUTRAL, QUICKER)
            return@with
        }
        chatNpc(ChatAnim.NEUTRAL, CHOP)
        chatNpc(ChatAnim.NEUTRAL, SHAPE)
        when {
            level < DUGOUT_LEVEL -> {
                chatNpc(ChatAnim.HAPPY, NO_TALENT)
                chatPlayer(ChatAnim.QUIZ, MEAN)
                chatNpc(ChatAnim.HAPPY, CALLOUSES)
                chatNpc(ChatAnim.HAPPY, LOG)
            }
            level < STABLE_DUGOUT_LEVEL -> {
                chatNpc(ChatAnim.HAPPY, DUGOUT)
                chatPlayer(ChatAnim.QUIZ, HOW_FAR)
                chatNpc(ChatAnim.NEUTRAL, TWO_STOPS)
            }
            level < WAKA_LEVEL -> {
                chatNpc(ChatAnim.HAPPY, STABLE)
                chatNpc(ChatAnim.HAPPY, ANY_PLACE)
                chatPlayer(ChatAnim.QUIZ, WILDERNESS)
                chatNpc(ChatAnim.SCARED, WAKA_MAN)
                chatNpc(ChatAnim.NEUTRAL, HELLISH)
            }
            else -> {
                chatNpc(ChatAnim.HAPPY, AXE)
                chatNpc(ChatAnim.NEUTRAL, WAKA)
                chatNpc(ChatAnim.SCARED, EVIL)
                chatPlayer(ChatAnim.NEUTRAL, WARNING)
            }
        }
    }

    /** The Woodcutting levels each canoe needs (OSRS Wiki, Canoeing). */
    const val LOG_LEVEL = 12
    const val DUGOUT_LEVEL = 27
    const val STABLE_DUGOUT_LEVEL = 42
    const val WAKA_LEVEL = 57
}

/** Barfy Bill's Talk-to. */
fun registerBarfyBill(world: World) {
    world.handlers.onNpc(BarfyBill.ID, BarfyBill.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(BarfyBill) { talk() } }
    }
}
