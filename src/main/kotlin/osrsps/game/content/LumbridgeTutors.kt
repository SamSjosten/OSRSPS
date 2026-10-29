package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

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

/** The Smithing apprentice's lines; the words are in the dialogue file, from the Wiki's Transcript:Smithing apprentice, read 2026-10-29. */
object SmithingApprenticeLines : Lines("smithing_apprentice", "Transcript:Smithing apprentice") {
    val TEACH = line("teach", "Smithing 1-14: the player's question")
    val FURNACE_HINT = line("furnace_hint", "1-14: the furnace icon's box (item 5084)")
    val NEED_ORE = line("need_ore", "1-14, with no ore: the apprentice's line")
    val MINING_HINT = line("mining_hint", "1-14, with no ore: the mining tutor icon's box (item 22571)")
    val HAVE_ORE = line("have_ore", "1-14, with ore: first line")
    val CLICK_FURNACE = line("click_furnace", "1-14, with ore: click on the furnace")
    val FULL_INVENTORY = line("full_inventory", "1-14, with ore: when you have a full inventory")
    val BANK_HINT = line("bank_hint", "1-14, with ore: the bank icon's box (item 5080)")
    val HAMMER = line("hammer", "1-14, with ore: if you have a hammer")
    val RUSTED = line("rusted", "1-14, with ore: the weather has rusted it")
    val VARROCK = line("varrock", "1-14, with ore: alternatively, Varrock")
    val KNOW_BASICS = line("know_basics", "Smithing 16-28: the player's line")
    val STOCKPILE = line("stockpile", "16-28: first line")
    val RARELY_WRONG = line("rarely_wrong", "16-28: second line")
    val LOSE_IRON = line("lose_iron", "16-28: third line, losing iron")
    val KELDAGRIM = line("keldagrim", "16-28: fourth line, Keldagrim")
    val WHAT_NEXT = line("what_next", "Smithing 29-99: the player's question")
    val SMITHING_TUTOR = line("smithing_tutor", "29-99: first line")
    val SMITHING_TUTOR_HINT = line("smithing_tutor_hint", "29-99: the Smithing tutor icon's box (item 22574)")
    val SMELT = line("smelt", "29-99: last line")
}

/** The Woodsman tutor's lines; the words are in the dialogue file, from the Wiki's Transcript:Woodsman tutor (members' worlds), read 2026-10-29. */
object WoodsmanTutorLines : Lines("woodsman_tutor", "Transcript:Woodsman tutor") {
    val BASICS = line("basics", "Woodcutting 1-18: option, which the player then says")
    val KNOW_A_BIT = line("know_a_bit", "Woodcutting 20-28: option, which the player then says")
    val ADVICE = line("advice", "Woodcutting 29-98: option, which the player then says")
    val TREES = line("trees", "Woodcutting 19-98: tell me about different trees and axes, which the player then says")
    val CAPE = line("cape", "every level: what is that cape, which the player then says")
    val GOODBYE = line("goodbye", "every level: goodbye, which the player then says")
    val SKILLCAPE = line("skillcape", "the cape: the tutor's answer")

    val TREE_HINT = line("tree_hint", "basics: the tree icon's box (item 9713)")
    val NO_ROOM_FOR_AXE = line("no_room_for_axe", "with no axe and a full inventory: the tutor's line")
    val HAVE_AN_AXE = line("have_an_axe", "with no axe: the tutor's line as he hands one over")
    val LIKELY_TREE = line("likely_tree", "basics: when you see a likely tree")
    val FULL_INVENTORY = line("full_inventory", "basics: when you have a full inventory")
    val BANK_HINT = line("bank_hint", "basics: the bank icon's box (item 5080)")
    val LOST_TINDERBOX = line("lost_tinderbox", "basics, with no tinderbox: the tutor's line")
    val CLICK_TINDERBOX = line("click_tinderbox", "basics: the last line, using a tinderbox")
    val CHOOSE = line("choose", "20-28: choose carefully where")
    val HANG_ON = line("hang_on", "20-28: make sure you hang on")
    val WATCH_OUT = line("watch_out", "20-28: watch out for")
    val DONT_FORGET = line("dont_forget", "20-28: don't forget")
    val LOTS = line("lots", "20-28: yes, you'll need lots")
    val BETTER = line("better", "29-98: as you get better")
    val ALSO = line("also", "29-98: of course, they're also")
    val LOOK_OUT = line("look_out", "29-98: also, look out for")

    val TREES_TITLE = line("trees_title", "the trees menu's title (RSMod's; the Wiki gives none)")
    val OAK_WILLOW = line("oak_willow", "trees menu: Oak and Willow")
    val OAK_HINT = line("oak_hint", "Oak and Willow: the box with logs and oak logs")
    val WILLOW_HINT = line("willow_hint", "Oak and Willow: the box with willow logs")
    val MAPLE_YEW = line("maple_yew", "trees menu: Maple and Yew")
    val MAPLE_HINT = line("maple_hint", "Maple and Yew: the box with maple logs")
    val YEW_HINT = line("yew_hint", "Maple and Yew: the box with yew logs and the tree icon")
    val MAGIC = line("magic", "trees menu, Woodcutting 29-98: Magic and other trees")
    val MAGIC_HINT = line("magic_hint", "Magic and other trees: the box with magic logs")
    val HOLLOW = line("hollow", "Magic and other trees: the tutor's line, hollow trees")
    val AXES = line("axes", "trees menu: Axes")
    val BRONZE_HINT = line("bronze_hint", "Axes: the box with a bronze axe")
    val PROGRESS = line("progress", "Axes: the tutor's line")
    val IRON_STEEL_HINT = line("iron_steel_hint", "Axes: the box with iron and steel axes")
    val RUNE_HINT = line("rune_hint", "Axes: the box with a rune axe")
    val GO_BACK = line("go_back", "trees menu: Go back to teaching")

    val MASTER_1 = line("master_1", "99 Woodcutting: the tutor's first line")
    val MASTER_2 = line("master_2", "99 Woodcutting: the tutor's second line")
    val NO_THANKS = line("no_thanks", "99: No, thanks., which the player then says")
    val COME_BACK = line("come_back", "99, no thanks: the tutor's answer")
    val YES_PLEASE = line("yes_please", "99: Yes, please, which the player then says")
    val BUY_SKILLCAPE = line("buy_skillcape", "99, owning a Woodcutting cape: the Skillcape option")
    val HOOD = line("hood", "99, owning a Woodcutting cape: the Hood option")
    val PRICE = line("price", "99, buying: the tutor's line, the price")
    val TOO_RICH = line("too_rich", "99, buying: too rich for me, which the player then says")
    val CHOP = line("chop", "99, too rich: the tutor's answer")
    val NO_PROBLEM = line("no_problem", "99, buying: no problem, which the player then says")
    val NO_MONEY = line("no_money", "99, no problem, without 99000 coins: the player's line")
    val NO_MONEY_REPLY = line("no_money_reply", "99, without 99000 coins: the tutor's answer")
    val SOLD = line("sold", "99, with 99000 coins: the tutor's line as the cape is handed over")
    val NO_SPACE = line("no_space", "99, with the coins but no inventory space: the tutor's line")
    val ANOTHER_HOOD = line("another_hood", "99, Hood: the player's question")
    val NO_SPACE_HOOD = line("no_space_hood", "99, Hood, with no inventory space: the tutor's line")
    val FREE_HOOD = line("free_hood", "99, Hood: the tutor's line")
    val HOOD_GIVEN = line("hood_given", "99, Hood: the box with the woodcutting hood")
}

/**
 * Lumbridge's skill tutors outside the castle: the Cooking tutor (3219) south of Bob's, the Smithing apprentice (3224)
 * and the Woodsman tutor (3226) north of the castle. From the Wiki: each conversation as on a members' world (every
 * account counts as a member here) once Cook's Assistant is done, the level ranges, the hint boxes' items, the bronze
 * axe and tinderbox handed over and the Woodcutting skillcape at 99,000 coins.
 *
 * Expressions are RSMod's (rev 233 scripts) for the apprentice and the Woodsman tutor and guessed for the Cooking
 * tutor, whom RSMod hasn't. Guessed: the levels the Wiki leaves out (Smithing 15 goes with 16-28, Woodcutting 19 with
 * 1-18), the Cooking tutor's 26-35 lines from the free-to-play transcript (the members' one is missing), that "Shows
 * other options" after a food topic is the first menu again, that the Woodsman tutor hands a tinderbox over with his
 * line (the Wiki notes only the axe), which items count as an axe or as ore, and that the skillcape is trimmed when
 * another skill is at 99 (as with Harlan's).
 */
object LumbridgeTutors {
    const val COOKING_TUTOR = 3219
    const val SMITHING_APPRENTICE = 3224
    const val WOODSMAN_TUTOR = 3226
    const val TALK_TO = 1

    const val BANK_ICON = 5080
    const val RANGE_ICON = 5090
    const val FURNACE_ICON = 5084
    const val MINING_TUTOR_ICON = 22571
    const val SMITHING_TUTOR_ICON = 22574
    const val TREE_ICON = 9713

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

    const val LOGS = 1511
    const val OAK_LOGS = 1521
    const val WILLOW_LOGS = 1519
    const val MAPLE_LOGS = 1517
    const val YEW_LOGS = 1515
    const val MAGIC_LOGS = 1513
    const val BRONZE_AXE = 1351
    const val IRON_AXE = 1349
    const val STEEL_AXE = 1353
    const val RUNE_AXE = 1359
    const val TINDERBOX = 590

    /** skillcape_woodcutting, its trimmed one and skillcape_woodcutting_hood. */
    const val WOODCUTTING_CAPE = 9807
    const val WOODCUTTING_CAPE_TRIMMED = 9808
    const val WOODCUTTING_HOOD = 9809

    const val COINS = 995
    const val CAPE_PRICE = 99_000
    const val MAX_LEVEL = 99

    /** Axes a woodcutter uses (bronze to dragon, 3rd age, infernal and crystal); guessed as the ones that count. */
    val WOODCUTTING_AXES = listOf(1351, 1349, 1353, 1361, 1355, 1357, 1359, 6739, 20011, 13241, 23673)

    /** Ores a furnace takes (copper to runite, and coal); guessed as the ones that count. */
    val ORES = listOf(436, 438, 440, 442, 444, 447, 449, 451, 453)

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

    internal suspend fun Dialogue.smithingApprentice() = with(SmithingApprenticeLines) {
        val level = player.skills.baseLevel(Skill.SMITHING)
        when {
            level <= 14 -> {
                chatPlayer(ChatAnim.QUIZ, TEACH)
                objbox(FURNACE_ICON, FURNACE_HINT)
                if (ORES.none { player.inventory.count(it) > 0 }) {
                    chatNpc(ChatAnim.SAD, NEED_ORE)
                    objbox(MINING_TUTOR_ICON, MINING_HINT)
                } else {
                    chatNpc(ChatAnim.HAPPY, HAVE_ORE)
                    chatNpc(ChatAnim.NEUTRAL, CLICK_FURNACE)
                    chatNpc(ChatAnim.HAPPY, FULL_INVENTORY)
                    objbox(BANK_ICON, BANK_HINT)
                    chatNpc(ChatAnim.HAPPY, HAMMER)
                    chatNpc(ChatAnim.SAD, RUSTED)
                    chatNpc(ChatAnim.HAPPY, VARROCK)
                }
            }
            level <= 28 -> {
                chatPlayer(ChatAnim.QUIZ, KNOW_BASICS)
                chatNpc(ChatAnim.HAPPY, STOCKPILE)
                chatNpc(ChatAnim.NEUTRAL, RARELY_WRONG)
                chatNpc(ChatAnim.NEUTRAL, LOSE_IRON)
                chatNpc(ChatAnim.HAPPY, KELDAGRIM)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, WHAT_NEXT)
                chatNpc(ChatAnim.LAUGH, SMITHING_TUTOR)
                objbox(SMITHING_TUTOR_ICON, SMITHING_TUTOR_HINT)
                chatNpc(ChatAnim.HAPPY, SMELT)
            }
        }
    }

    internal suspend fun Dialogue.woodsmanTutor(give: (Player, Int) -> Unit) = with(WoodsmanTutorLines) {
        val level = player.skills.baseLevel(Skill.WOODCUTTING)
        if (level >= MAX_LEVEL) {
            skillcape(give)
            return@with
        }
        while (true) {
            val options = when {
                level <= 19 -> listOf(BASICS, CAPE, GOODBYE)
                level <= 28 -> listOf(KNOW_A_BIT, TREES, CAPE, GOODBYE)
                else -> listOf(ADVICE, TREES, CAPE, GOODBYE)
            }
            when (val picked = options[choice(*options.toTypedArray()) - 1]) {
                BASICS -> {
                    chatPlayer(ChatAnim.QUIZ, BASICS)
                    objbox(TREE_ICON, TREE_HINT)
                    axeIfNeeded(give)
                    chatNpc(ChatAnim.NEUTRAL, LIKELY_TREE)
                    chatNpc(ChatAnim.HAPPY, FULL_INVENTORY)
                    objbox(BANK_ICON, BANK_HINT)
                    if (!owns(TINDERBOX)) {
                        give(player, TINDERBOX)
                        chatNpc(ChatAnim.NEUTRAL, LOST_TINDERBOX)
                    }
                    chatNpc(ChatAnim.HAPPY, CLICK_TINDERBOX)
                    return@with
                }
                KNOW_A_BIT -> {
                    chatPlayer(ChatAnim.QUIZ, KNOW_A_BIT)
                    axeIfNeeded(give)
                    chatNpc(ChatAnim.HAPPY, CHOOSE)
                    chatNpc(ChatAnim.HAPPY, HANG_ON)
                    chatNpc(ChatAnim.SHIFTY, WATCH_OUT)
                    chatNpc(ChatAnim.HAPPY, DONT_FORGET)
                    chatNpc(ChatAnim.HAPPY, LOTS)
                }
                ADVICE -> {
                    chatPlayer(ChatAnim.QUIZ, ADVICE)
                    axeIfNeeded(give)
                    chatNpc(ChatAnim.HAPPY, BETTER)
                    chatNpc(ChatAnim.HAPPY, ALSO)
                    chatNpc(ChatAnim.HAPPY, LOOK_OUT)
                }
                TREES -> {
                    chatPlayer(ChatAnim.QUIZ, TREES)
                    trees(level)
                }
                CAPE -> {
                    chatPlayer(ChatAnim.HAPPY, CAPE)
                    chatNpc(ChatAnim.NEUTRAL, SKILLCAPE)
                    return@with
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, picked)
                    return@with
                }
            }
        }
    }

    /** The trees menu, until Go back to teaching. */
    private suspend fun Dialogue.trees(level: Int) = with(WoodsmanTutorLines) {
        while (true) {
            val options = if (level >= 29) listOf(OAK_WILLOW, MAPLE_YEW, MAGIC, AXES, GO_BACK) else listOf(OAK_WILLOW, MAPLE_YEW, AXES, GO_BACK)
            when (options[choice(*options.toTypedArray(), title = text(TREES_TITLE)) - 1]) {
                OAK_WILLOW -> {
                    doubleobjbox(LOGS, OAK_LOGS, text(OAK_HINT))
                    objbox(WILLOW_LOGS, WILLOW_HINT)
                }
                MAPLE_YEW -> {
                    objbox(MAPLE_LOGS, MAPLE_HINT)
                    doubleobjbox(YEW_LOGS, TREE_ICON, text(YEW_HINT))
                }
                MAGIC -> {
                    objbox(MAGIC_LOGS, MAGIC_HINT)
                    chatNpc(ChatAnim.NEUTRAL, HOLLOW)
                }
                AXES -> {
                    objbox(BRONZE_AXE, BRONZE_HINT)
                    chatNpc(ChatAnim.HAPPY, PROGRESS)
                    doubleobjbox(IRON_AXE, STEEL_AXE, text(IRON_STEEL_HINT))
                    objbox(RUNE_AXE, RUNE_HINT)
                }
                else -> return@with
            }
        }
    }

    /**
     * Whether the player has [obj] carried, worn or banked. The Wiki's Woodsman tutor gives the axe and tinderbox "only if
     * unowned"; the bank counting is read from that, not seen on live. Without it the free axe sold to Bob for coins
     * from nothing (the second exploit review, 29 Oct 2026).
     */
    private fun Dialogue.owns(obj: Int): Boolean =
        player.inventory.count(obj) > 0 || player.equipment.count(obj) > 0 || player.bank.count(obj) > 0

    /** With no axe of their own, a bronze one, or a line saying there's no room for it. */
    private suspend fun Dialogue.axeIfNeeded(give: (Player, Int) -> Unit) = with(WoodsmanTutorLines) {
        if (WOODCUTTING_AXES.any { owns(it) }) return@with
        if (player.inventory.freeSlots == 0) {
            chatNpc(ChatAnim.SAD, NO_ROOM_FOR_AXE)
        } else {
            give(player, BRONZE_AXE)
            chatNpc(ChatAnim.HAPPY, HAVE_AN_AXE)
        }
    }

    private suspend fun Dialogue.skillcape(give: (Player, Int) -> Unit) = with(WoodsmanTutorLines) {
        chatNpc(ChatAnim.HAPPY, MASTER_1)
        chatNpc(ChatAnim.QUIZ, MASTER_2)
        if (choice(NO_THANKS, YES_PLEASE) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
            chatNpc(ChatAnim.NEUTRAL, COME_BACK)
            return@with
        }
        chatPlayer(ChatAnim.HAPPY, YES_PLEASE)
        val ownsCape = listOf(WOODCUTTING_CAPE, WOODCUTTING_CAPE_TRIMMED).any {
            player.inventory.count(it) > 0 || player.equipment.count(it) > 0 || player.bank.count(it) > 0
        }
        if (ownsCape && choice(BUY_SKILLCAPE, HOOD) == 2) {
            chatPlayer(ChatAnim.QUIZ, ANOTHER_HOOD)
            if (player.inventory.freeSlots == 0) {
                chatNpc(ChatAnim.NEUTRAL, NO_SPACE_HOOD)
                return@with
            }
            chatNpc(ChatAnim.HAPPY, FREE_HOOD)
            give(player, WOODCUTTING_HOOD)
            objbox(WOODCUTTING_HOOD, HOOD_GIVEN)
            return@with
        }
        chatNpc(ChatAnim.NEUTRAL, PRICE)
        if (choice(TOO_RICH, NO_PROBLEM) == 1) {
            chatPlayer(ChatAnim.SHOCKED, TOO_RICH)
            chatNpc(ChatAnim.NEUTRAL, CHOP)
            return@with
        }
        chatPlayer(ChatAnim.HAPPY, NO_PROBLEM)
        val coins = player.inventory.count(COINS)
        when {
            coins < CAPE_PRICE -> {
                chatPlayer(ChatAnim.SAD, NO_MONEY)
                chatNpc(ChatAnim.NEUTRAL, NO_MONEY_REPLY)
            }
            player.inventory.freeSlots + (if (coins == CAPE_PRICE.toLong()) 1 else 0) < 2 -> chatNpc(ChatAnim.NEUTRAL, NO_SPACE)
            else -> {
                takeCoins(player, CAPE_PRICE)
                val trimmed = Skill.entries.any { it != Skill.WOODCUTTING && player.skills.baseLevel(it) >= MAX_LEVEL }
                give(player, if (trimmed) WOODCUTTING_CAPE_TRIMMED else WOODCUTTING_CAPE)
                give(player, WOODCUTTING_HOOD)
                chatNpc(ChatAnim.HAPPY, SOLD)
            }
        }
    }

    private fun takeCoins(player: Player, count: Int) {
        var left = count
        for ((slot, held) in player.inventory.contents()) {
            if (left == 0) break
            if (held.id == COINS) left -= player.inventory.remove(slot, left)
        }
    }
}

/** The three tutors' Talk-to. */
fun registerLumbridgeTutors(world: World) {
    val give: (Player, Int) -> Unit = { player, obj ->
        world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
    }
    world.handlers.onNpc(LumbridgeTutors.COOKING_TUTOR, LumbridgeTutors.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(LumbridgeTutors) { cookingTutor() } }
    }
    world.handlers.onNpc(LumbridgeTutors.SMITHING_APPRENTICE, LumbridgeTutors.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(LumbridgeTutors) { smithingApprentice() } }
    }
    world.handlers.onNpc(LumbridgeTutors.WOODSMAN_TUTOR, LumbridgeTutors.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(LumbridgeTutors) { woodsmanTutor(give) } }
    }
}
