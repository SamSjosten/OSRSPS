package osrsps.game.content

import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** The Cooking tutor's lines; the words are in the dialogue file, from the Wiki's Transcript:Cooking tutor, read 2026-10-29. */
object CookingTutorLines : Lines("cooking_tutor", "Transcript:Cooking tutor") {
    val HELLO = line("hello", "Members' worlds: the player's greeting")
    val GREETING = line("greeting", "the tutor's first line")
    val TRAIN = line("train", "first menu, option 1, which the player then says")
    val KINDS = line("kinds", "first menu, option 2, which the player then says")
    val NO_THANKS = line("no_thanks", "first menu, option 3, which the player then says")
    val COME_BACK = line("come_back", "option 3: the tutor's answer (end of dialogue)")

    val LOW_1 = line("low_1", "training, Cooking 1-25: first line, the simplest thing")
    val LOW_2 = line("low_2", "training, 1-25: second line, alternatively")
    val RANGE_HINT = line("range_hint", "training, 1-25: the range icon's box (item 5090)")
    val LOW_3 = line("low_3", "training, 1-25: third line, his own range")
    val COOKS_RANGE = line("cooks_range", "training, 1-25, after Cook's Assistant: the castle cook's range")
    val LOW_4 = line("low_4", "training, 1-25: alternatively, your own fire")
    val LOW_5 = line("low_5", "training, 1-25: once you've found your range")
    val LOW_6 = line("low_6", "training, 1-25: when you have a full inventory")
    val BANK_HINT = line("bank_hint", "training, 1-25: the bank icon's box (item 5080)")
    val MID_1 = line("mid_1", "training, Cooking 26-35 (free-to-play worlds; the members' transcript is missing): using a range")
    val MID_2 = line("mid_2", "training, 26-35: the river")
    val MID_3 = line("mid_3", "training, 26-35: as you increase your level")
    val MID_4 = line("mid_4", "training, 26-35: those foods may heal")
    val MID_5 = line("mid_5", "training, 26-35: to make a stew")
    val MASTER = line("master", "training, Cooking 99: first line")
    val HIGH = line("high", "training, Cooking 36-98: first line")
    val PIZZA = line("pizza", "training, 36-99: if you feel up to it, pizza")
    val POTATOES = line("potatoes", "training, 36-99: potatoes")
    val CURRY = line("curry", "training, 36-99, members' worlds: curry or baked potato")
    val ANYTHING_ELSE = line("anything_else", "training: the tutor asking if there's anything else")

    val MANY = line("many", "what to cook: the tutor's first line")
    val WHICH = line("which", "what to cook: the tutor's question before the food menu")
    val FISH = line("fish", "food menu, option 1, which the player then says")
    val FISH_HINT = line("fish_hint", "fish and meat: the box with raw beef and cooked meat")
    val FISH_1 = line("fish_1", "fish and meat: you might find")
    val FISH_2 = line("fish_2", "fish and meat: some foods have hidden properties (members: anglerfish)")
    val FISH_3 = line("fish_3", "fish and meat: most of the time")
    val FISH_MORE = line("fish_more", "fish and meat: would you like to hear more")
    val BREWING = line("brewing", "food menu, option 2, which the player then says")
    val BREWING_HINT = line("brewing_hint", "brewing: the box with Dragon bitter and beer")
    val BREWING_1 = line("brewing_1", "brewing, members' worlds: the recipe")
    val BREWING_2 = line("brewing_2", "brewing, members' worlds: turn the tap")
    val BREWING_MORE = line("brewing_more", "brewing: would you like to hear more")
    val VEGETABLES = line("vegetables", "food menu, option 3, which the player then says")
    val VEGETABLES_HINT = line("vegetables_hint", "vegetables: the box with a potato and a tuna potato")
    val CHURN = line("churn", "vegetables, members' worlds: churning")
    val DAIRY_HINT = line("dairy_hint", "vegetables: the box with butter and cheese, the dairy cow")
    val TOPPING = line("topping", "vegetables, members' worlds: now you will need")
    val VEGETABLES_MORE = line("vegetables_more", "vegetables: would you like to hear more")
    val PIES = line("pies", "food menu, option 4, which the player then says")
    val DOUGH_HINT = line("dough_hint", "pies: the box with a bucket of water and a pot of flour")
    val PIE_SHELL_HINT = line("pie_shell_hint", "pies: the box with a pie dish and pastry dough")
    val PIES_1 = line("pies_1", "pies: finally cook your pie")
    val PIES_2 = line("pies_2", "pies, members' worlds: some pies boost skills")
    val PIZZA_BASE = line("pizza_base", "pies: next, we have pizza")
    val PIZZA_HINT = line("pizza_hint", "pies: the box with a tomato and cheese")
    val TOPPINGS = line("toppings", "pies: if you want to, a topping")
    val PIES_MORE = line("pies_more", "pies: would you like to hear more")
}

/**
 * The Cooking tutor (3219) south of Bob's. From the Wiki: the conversation as on a members' world (every account counts
 * as a member here) once Cook's Assistant is done, the level ranges and the hint boxes' items. The Smithing apprentice
 * and the Woodsman tutor became Feoras ([Feoras]) and Wilfred ([Wilfred]) in 242.1.
 *
 * Expressions guessed (RSMod hasn't the Cooking tutor). Guessed: the 26-35 lines from the free-to-play transcript (the
 * members' one is missing), and that "Shows other options" after a food topic is the first menu again.
 */
object LumbridgeTutors {
    const val COOKING_TUTOR = 3219
    const val TALK_TO = 1

    const val BANK_ICON = 5080
    const val RANGE_ICON = 5090

    const val RAW_BEEF = 2132
    const val COOKED_MEAT = 2142
    const val DRAGON_BITTER = 1911
    const val BEER_OBJ = 1917
    const val POTATO = 1942
    const val TUNA_POTATO = 7060
    const val PAT_OF_BUTTER = 6697
    const val CHEESE = 1985
    const val BUCKET_OF_WATER = 1929
    const val POT_OF_FLOUR = 1933
    const val PIE_DISH = 2313
    const val PASTRY_DOUGH = 1953
    const val TOMATO = 1982

    const val MAX_LEVEL = 99

    internal suspend fun Dialogue.cookingTutor() = with(CookingTutorLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        chatNpc(ChatAnim.HAPPY, GREETING)
        while (true) {
            when (choice(TRAIN, KINDS, NO_THANKS)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, TRAIN)
                    cookingTraining(player.skills.baseLevel(Skill.COOKING))
                    chatNpc(ChatAnim.HAPPY, ANYTHING_ELSE)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, KINDS)
                    chatNpc(ChatAnim.HAPPY, MANY)
                    chatNpc(ChatAnim.QUIZ, WHICH)
                    foods()
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
                    chatNpc(ChatAnim.HAPPY, COME_BACK)
                    return@with
                }
            }
        }
    }

    private suspend fun Dialogue.cookingTraining(level: Int) = with(CookingTutorLines) {
        when {
            level <= 25 -> {
                chatNpc(ChatAnim.NEUTRAL, LOW_1)
                chatNpc(ChatAnim.NEUTRAL, LOW_2)
                objbox(RANGE_ICON, RANGE_HINT)
                chatNpc(ChatAnim.HAPPY, LOW_3)
                chatNpc(ChatAnim.NEUTRAL, COOKS_RANGE)
                for (line in listOf(LOW_4, LOW_5, LOW_6)) chatNpc(ChatAnim.NEUTRAL, line)
                objbox(BANK_ICON, BANK_HINT)
            }
            level <= 35 -> for (line in listOf(MID_1, MID_2, MID_3, MID_4, MID_5)) chatNpc(ChatAnim.NEUTRAL, line)
            else -> {
                if (level >= MAX_LEVEL) chatNpc(ChatAnim.LAUGH, MASTER) else chatNpc(ChatAnim.HAPPY, HIGH)
                for (line in listOf(PIZZA, POTATOES, CURRY)) chatNpc(ChatAnim.NEUTRAL, line)
            }
        }
    }

    /** The food menu: one topic, then back to the first menu. */
    private suspend fun Dialogue.foods() = with(CookingTutorLines) {
        when (choice(FISH, BREWING, VEGETABLES, PIES)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, FISH)
                doubleobjbox(RAW_BEEF, COOKED_MEAT, text(FISH_HINT))
                for (line in listOf(FISH_1, FISH_2, FISH_3)) chatNpc(ChatAnim.NEUTRAL, line)
                chatNpc(ChatAnim.HAPPY, FISH_MORE)
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, BREWING)
                doubleobjbox(DRAGON_BITTER, BEER_OBJ, text(BREWING_HINT))
                chatNpc(ChatAnim.NEUTRAL, BREWING_1)
                chatNpc(ChatAnim.NEUTRAL, BREWING_2)
                chatNpc(ChatAnim.HAPPY, BREWING_MORE)
            }
            3 -> {
                chatPlayer(ChatAnim.QUIZ, VEGETABLES)
                doubleobjbox(POTATO, TUNA_POTATO, text(VEGETABLES_HINT))
                chatNpc(ChatAnim.NEUTRAL, CHURN)
                doubleobjbox(PAT_OF_BUTTER, CHEESE, text(DAIRY_HINT))
                chatNpc(ChatAnim.NEUTRAL, TOPPING)
                chatNpc(ChatAnim.HAPPY, VEGETABLES_MORE)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, PIES)
                doubleobjbox(BUCKET_OF_WATER, POT_OF_FLOUR, text(DOUGH_HINT))
                doubleobjbox(PIE_DISH, PASTRY_DOUGH, text(PIE_SHELL_HINT))
                for (line in listOf(PIES_1, PIES_2, PIZZA_BASE)) chatNpc(ChatAnim.NEUTRAL, line)
                doubleobjbox(TOMATO, CHEESE, text(PIZZA_HINT))
                chatNpc(ChatAnim.NEUTRAL, TOPPINGS)
                chatNpc(ChatAnim.HAPPY, PIES_MORE)
            }
        }
    }

}

/** The Cooking tutor's Talk-to. */
fun registerLumbridgeTutors(world: World) {
    world.handlers.onNpc(LumbridgeTutors.COOKING_TUTOR, LumbridgeTutors.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(LumbridgeTutors) { cookingTutor() } }
    }
}
