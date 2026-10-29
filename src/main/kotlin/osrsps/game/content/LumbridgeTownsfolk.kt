package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Abigaila's lines; the words are in the dialogue file, from the Wiki's Transcript:Abigaila, read 2026-10-29. */
object AbigailaLines : Lines("abigaila", "Transcript:Abigaila") {
    val HELP = line("help", "Abigaila's first line")
    val WHO = line("who", "option 1, which the player then says")
    val STORY_1 = line("story_1", "option 1: her first line, from Morytania")
    val STORY_2 = line("story_2", "option 1: second line, she tried for years")
    val STORY_3 = line("story_3", "option 1: third line, the journey")
    val STORY_4 = line("story_4", "option 1: fourth line, it was dark")
    val STORY_5 = line("story_5", "option 1: fifth line, eventually")
    val STORY_6 = line("story_6", "option 1: sixth line, the sun was rising")
    val SOB = line("sob", "option 1: seventh line, a sob")
    val STORY_7 = line("story_7", "option 1: eighth line, she's done alright")
    val STORY_8 = line("story_8", "option 1: ninth line, she's able to")
    val FREE = line("free", "option 1: tenth line, she is free")
    val STORY_9 = line("story_9", "option 1: eleventh line, she hasn't forgotten")
    val HUSBAND = line("husband", "option 1: twelfth line, her husband")
    val PASS_TIME = line("pass_time", "option 1: last line (the first menu comes back)")
    val SCRY = line("scry", "option 2, which the player then says")
    val EMPTY_FIRST = line("empty_first", "option 2, poisoned, diseased or carrying anything: her answer")
    val SPECIFIC = line("specific", "option 2's menu: Observe a specific party")
    val RECENT = line("recent", "option 2's menu: Observe recent party")
    val CANCEL = line("cancel", "option 2's menu: Cancel")
    val LEAVE = line("leave", "option 3, which the player then says")
}

/** The Ironman tutor's lines; the words are in the dialogue file, from the Wiki's Transcript:Ironman tutor (without Ironman status), read 2026-10-29. */
object IronmanTutorLines : Lines("ironman_tutor", "Transcript:Ironman tutor") {
    val GREETING = line("greeting", "the tutor's first line, with the player's name", "name")
    val TELL_ME = line("tell_me", "option 1, which the player then says")
    val ABOUT_1 = line("about_1", "option 1: first line")
    val ABOUT_2 = line("about_2", "option 1: second line")
    val ABOUT_3 = line("about_3", "option 1: third line")
    val ABOUT_4 = line("about_4", "option 1: fourth line")
    val ABOUT_5 = line("about_5", "option 1: fifth line, stats frozen")
    val ABOUT_6 = line("about_6", "option 1: sixth line")
    val ABOUT_7 = line("about_7", "option 1: seventh line")
    val ABOUT_8 = line("about_8", "option 1: eighth line")
    val ABOUT_9 = line("about_9", "option 1: ninth line, the ultimate challenge")
    val ABOUT_10 = line("about_10", "option 1: tenth line")
    val ABOUT_11 = line("about_11", "option 1: eleventh line (a menu follows)")
    val CHANGE = line("change", "after option 1: change my mode (the Wiki's \"same as below\")")
    val REVIEW = line("review", "option 2: review my mode, which the player then says (the talk ends)")
    val ARMOUR = line("armour", "option 3, which the player then says; also the menu after option 1")
    val NOT_IRONMAN = line("not_ironman", "option 3: the tutor's answer, also Armour's")
    val FINE = line("fine", "option 4, which the player then says")
}

/**
 * Abigaila (8368), upstairs in the General Store, and the Ironman tutor (311), east of the castle. From the Wiki: both
 * conversations for a player who isn't an Ironman (none is here), that Abigaila won't let a player who carries or
 * wears anything look into her ball, and that the Ironman tutor's armour is for Ironmen only. Not built: the Theatre
 * of Blood, which her ball shows, and reviewing or changing the account mode, so those options end the talk. Guessed:
 * every chathead expression (the Wiki notes none, RSMod has neither), that Scry goes straight to the ball's menu, that
 * "same as below" after the Ironman tutor's first answer means the review option (the account mode isn't built, so
 * it ends the talk), that Armour gives option 3's answer with no line from the player, and that being poisoned or
 * diseased is left out until either exists.
 */
object LumbridgeTownsfolk {
    const val ABIGAILA = 8368
    const val IRONMAN_TUTOR = 311
    const val TALK_TO = 1
    const val SCRY = 3
    const val ARMOUR = 3

    internal suspend fun Dialogue.abigaila() = with(AbigailaLines) {
        chatNpc(ChatAnim.NEUTRAL, HELP)
        while (true) {
            when (choice(WHO, SCRY, LEAVE)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHO)
                    for (line in listOf(STORY_1, STORY_2, STORY_3, STORY_4, STORY_5, STORY_6)) chatNpc(ChatAnim.SAD, line)
                    chatNpc(ChatAnim.SAD, SOB)
                    for (line in listOf(STORY_7, STORY_8)) chatNpc(ChatAnim.NEUTRAL, line)
                    chatNpc(ChatAnim.HAPPY, FREE)
                    for (line in listOf(STORY_9, HUSBAND, PASS_TIME)) chatNpc(ChatAnim.SAD, line)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, SCRY)
                    scry()
                    return@with
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, LEAVE)
                    return@with
                }
            }
        }
    }

    /** The scrying ball: refused while carrying or wearing anything, else its menu, whose parties aren't built. */
    internal suspend fun Dialogue.scry() = with(AbigailaLines) {
        if (!player.inventory.isEmpty() || !player.equipment.isEmpty()) {
            chatNpc(ChatAnim.NEUTRAL, EMPTY_FIRST)
            return@with
        }
        if (choice(SPECIFIC, RECENT, CANCEL) != 3) player.message("The Theatre of Blood isn't in this game yet.")
    }

    internal suspend fun Dialogue.ironmanTutor() = with(IronmanTutorLines) {
        chatNpc(ChatAnim.HAPPY, GREETING, "name" to player.username)
        when (choice(TELL_ME, REVIEW, ARMOUR, FINE)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, TELL_ME)
                for (line in listOf(ABOUT_1, ABOUT_2, ABOUT_3, ABOUT_4, ABOUT_5, ABOUT_6, ABOUT_7, ABOUT_8, ABOUT_9, ABOUT_10, ABOUT_11)) {
                    chatNpc(ChatAnim.NEUTRAL, line)
                }
                when (choice(CHANGE, ARMOUR, FINE)) {
                    1 -> chatPlayer(ChatAnim.NEUTRAL, CHANGE)
                    2 -> armour(said = true)
                    else -> chatPlayer(ChatAnim.NEUTRAL, FINE)
                }
            }
            2 -> chatPlayer(ChatAnim.NEUTRAL, REVIEW)
            3 -> armour(said = true)
            else -> chatPlayer(ChatAnim.NEUTRAL, FINE)
        }
    }

    internal suspend fun Dialogue.armour(said: Boolean) = with(IronmanTutorLines) {
        if (said) chatPlayer(ChatAnim.QUIZ, ARMOUR)
        chatNpc(ChatAnim.NEUTRAL, NOT_IRONMAN)
    }
}

/** Abigaila's Talk-to and Scry, and the Ironman tutor's Talk-to and Armour. His Setup does nothing yet. */
fun registerLumbridgeTownsfolk(world: World) {
    world.handlers.onNpc(LumbridgeTownsfolk.ABIGAILA, LumbridgeTownsfolk.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(LumbridgeTownsfolk) { abigaila() } }
    }
    world.handlers.onNpc(LumbridgeTownsfolk.ABIGAILA, LumbridgeTownsfolk.SCRY) { op ->
        world.dialogues.start(this, op.npc) { with(LumbridgeTownsfolk) { scry() } }
    }
    world.handlers.onNpc(LumbridgeTownsfolk.IRONMAN_TUTOR, LumbridgeTownsfolk.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(LumbridgeTownsfolk) { ironmanTutor() } }
    }
    world.handlers.onNpc(LumbridgeTownsfolk.IRONMAN_TUTOR, LumbridgeTownsfolk.ARMOUR) { op ->
        world.dialogues.start(this, op.npc) { with(LumbridgeTownsfolk) { armour(said = false) } }
    }
}
