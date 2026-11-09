package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/**
 * The Cook's lines after Recipe for Disaster, with the Cooking tips he took over when 242.1 removed the Cooking tutor;
 * the words are in the dialogue file, from the Wiki's Transcript:Cook (Lumbridge), read 2026-11-08.
 */
object CookLines : Lines("cook", "Transcript:Cook (Lumbridge)") {
    val GREETING = line("greeting", "After Recipe for Disaster: the Cook's first line")
    val OTHER_QUESTS = line("other_quests", "the other quests option")
    val NOTHING_TO_DO = line("nothing_to_do", "After Cook's Assistant: the Cook's answer to option 1 on free-to-play worlds")
    val ADVICE = line("advice", "After Recipe for Disaster: option 2, which the player then says")
    val ADVICE_1 = line("advice_1", "advice: lots to cook, but he keeps it simple")
    val IMAGINE = line("imagine", "advice: the player's reply")
    val ADVICE_2 = line("advice_2", "advice: meat, bought or gathered")
    val ADVICE_3 = line("advice_3", "advice: shrimp south of the graveyard, and cows")
    val FISH_HINT = line("fish_hint", "advice: the fishing spot icon's box (item 5088)")
    val ADVICE_4 = line("advice_4", "advice: a fire or, better, a range")
    val RANGE_HINT = line("range_hint", "advice: the range icon's box (item 5090)")
    val ADVICE_5 = line("advice_5", "advice: bread or a cake")
    val ADVICE_6 = line("advice_6", "advice: bread dough, and water")
    val WATER_HINT = line("water_hint", "advice: the water source icon's box (item 22563)")
    val ADVICE_7 = line("advice_7", "advice: stews, pies, pizzas and more")
    val GUIDE_HINT = line("guide_hint", "advice: the Skills panel box")
    val INFO_THANKS = line("info_thanks", "advice: the player's thanks")
    val HAPPY_TO_HELP = line("happy_to_help", "advice: the Cook's last line")
    val RANGE = line("range", "the range option, which the player then says")
    val RANGE_YES = line("range_yes", "the range option: the Cook's first line")
    val RANGE_NAME = line("range_name", "the range option: the Cook's second line, naming the range")
    val BURN_LESS = line("burn_less", "the range option: the player's question about burning")
    val SALESMAN = line("salesman", "the range option: the Cook's answer")
    val THANKS = line("thanks", "the range option: the player's last line")
}

/**
 * The Cook (NPC 4626) in Lumbridge Castle's kitchen, as he is once Cook's Assistant and Recipe for Disaster are done,
 * which every account here has: other quests, the cooking advice (242.1) and the range. From the Wiki: the advice goes
 * back to the menu; the other two end the talk. Its picture boxes show the icon items the Wiki names (5088, 5090,
 * 22563); its Skills panel sprite box is a message box here. Guessed: every chathead expression (the Wiki notes none),
 * and option 1. On members' worlds the Wiki has option 1 lead into Recipe for Disaster's start, and gives nothing for
 * after the quest, so he answers as on a free-to-play world, straight after the option with no line from the player.
 */
object Cook {
    const val ID = 4626
    const val TALK_TO = 1

    /** The icon items the advice's boxes show: a fishing spot, a range and a water source. */
    const val FISHING_ICON = 5088
    const val RANGE_ICON = 5090
    const val WATER_ICON = 22563

    internal suspend fun Dialogue.talk() = with(CookLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        while (true) {
            when (choice(OTHER_QUESTS, ADVICE, RANGE)) {
                1 -> {
                    chatNpc(ChatAnim.SAD, NOTHING_TO_DO)
                    return@with
                }
                2 -> advice()
                else -> {
                    chatPlayer(ChatAnim.QUIZ, RANGE)
                    chatNpc(ChatAnim.HAPPY, RANGE_YES)
                    chatNpc(ChatAnim.HAPPY, RANGE_NAME)
                    chatPlayer(ChatAnim.QUIZ, BURN_LESS)
                    chatNpc(ChatAnim.SHIFTY, SALESMAN)
                    chatPlayer(ChatAnim.CONFUSED, THANKS)
                    return@with
                }
            }
        }
    }

    private suspend fun Dialogue.advice() = with(CookLines) {
        chatPlayer(ChatAnim.QUIZ, ADVICE)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_1)
        chatPlayer(ChatAnim.SHIFTY, IMAGINE)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_2)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_3)
        objbox(FISHING_ICON, FISH_HINT)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_4)
        objbox(RANGE_ICON, RANGE_HINT)
        chatNpc(ChatAnim.HAPPY, ADVICE_5)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_6)
        objbox(WATER_ICON, WATER_HINT)
        chatNpc(ChatAnim.NEUTRAL, ADVICE_7)
        mesbox(GUIDE_HINT)
        chatPlayer(ChatAnim.HAPPY, INFO_THANKS)
        chatNpc(ChatAnim.HAPPY, HAPPY_TO_HELP)
    }
}

/** The Cook's Talk-to. */
fun registerCook(world: World) {
    world.handlers.onNpc(Cook.ID, Cook.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Cook) { talk() } }
    }
}
