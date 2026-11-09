package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/**
 * The Lumbridge Guide's lines as since 242.1, which made his talk "more in-universe"; the words are in the dialogue
 * file, from the Wiki's Transcript:Lumbridge Guide (Standard dialogue), read 2026-11-08. The quest answer is missing
 * on the Wiki ("probably mostly the same as original dialogue"), so it keeps the old talk's line for a player with every
 * quest done.
 */
object LumbridgeGuideLines : Lines("lumbridge_guide", "Transcript:Lumbridge Guide") {
    val GREETING_FIRST = line("greeting_first", "the first time: Phileas introduces himself")
    val HELLO = line("hello", "later times: his first line")
    val GOALS = line("goals", "first menu, option 1: goals, which the player then says")
    val GOALS_ANSWER = line("goals_answer", "goals: his advice isn't useful any more")
    val QUEST_TO_GO_ON = line("quest_to_go_on", "first menu, option 2: a quest, which the player then says (the Wiki is missing what follows)")
    val NO_MORE_QUESTS = line("no_more_quests", "quest, with every quest done: his answer (the old talk's words, inferred)")
    val COMBAT = line("combat", "first menu, option 3: getting better at combat (he answers straight away)")
    val COMBAT_HARLAN = line("combat_harlan", "combat: talk to Captain Harlan")
    val SKILLS = line("skills", "first menu, option 4: improving skills, which the player then says")
    val SKILLS_1 = line("skills_1", "skills: practice makes perfect")
    val SKILLS_2 = line("skills_2", "skills: a few tips for the common ones")
    val SKILLS_MINING = line("skills_mining", "skills: mining south of Lumbridge")
    val HINT_MINING = line("hint_mining", "skills: the mining site icon's box (item 5082)")
    val SKILLS_SMITHING = line("skills_smithing", "skills: smelting and smithing")
    val HINT_FURNACE = line("hint_furnace", "skills: the furnace icon's box (item 5084)")
    val HINT_ANVIL = line("hint_anvil", "skills: the Smithing icon's box (a message box here)")
    val SKILLS_FISHING = line("skills_fishing", "skills: fishing and cooking")
    val HINT_FISHING = line("hint_fishing", "skills: the fishing spot icon's box (item 5088)")
    val HINT_RANGE = line("hint_range", "skills: the range icon's box (item 5090)")
    val SKILLS_WOODCUTTING = line("skills_woodcutting", "skills: woodcutting")
    val HINT_TREES = line("hint_trees", "skills: the tree icon's box (item 9713)")
    val SKILLS_3 = line("skills_3", "skills: I could keep going")
    val SKILLS_4 = line("skills_4", "skills: gathering or producing")
    val SKILLS_5 = line("skills_5", "skills: more tips from people around here")
    val HINT_GUIDE = line("hint_guide", "skills: the Skills panel box (a message box here)")
    val MORE_OPTIONS = line("more_options", "first menu, option 5: more options")
    val MAKE_MONEY = line("make_money", "more options, option 1: making money, which the player then says")
    val MONEY_1 = line("money_1", "money: selling things, goblins")
    val MONEY_2 = line("money_2", "money: making or gathering items")
    val MONEY_3 = line("money_3", "money: General Stores")
    val MONEY_4 = line("money_4", "money: specialist stores")
    val MONEY_5 = line("money_5", "money: the Grand Exchange")
    val MONEY_6 = line("money_6", "money: the trek to Varrock")
    val MONEY_7 = line("money_7", "money: the bank")
    val FIND_BANK = line("find_bank", "more options, option 2: a bank, which the player then says")
    val BANK_ROOF = line("bank_roof", "bank: on the roof of the castle")
    val HINT_BANK = line("hint_bank", "bank: the bank icon's box (item 5080)")
    val BE_OFF = line("be_off", "more options: leaving, which the player then says")
    val UNTIL_NEXT = line("until_next", "leaving: his last line")
    val PREVIOUS_OPTIONS = line("previous_options", "more options: back to the first menu")
}

/**
 * The Lumbridge Guide (306) outside Lumbridge Castle's gate, as since 242.1. From the Wiki: his first-time greeting
 * (varbit lumbridge_guide_met, 16839, then set) or later hello; the goals, quest, combat and skills answers and the more
 * options menu (money, bank, leaving), each back to the menu it was picked from; the icon boxes.
 *
 * Not built: owed experience (the 242.1 quest XP the Guide and Perdu hand out), the lost Amulet of the Eye (there is no
 * way to lose it here), the Tarnished locket, his Treasure Trails and Astral Contacts parts. Guessed: every chathead
 * expression; that the goals answer, which the Wiki gives under a condition it doesn't name, is always given (no other
 * goals answer is on the page); and that the quest answer (missing on the Wiki) keeps the old talk's line for a player
 * with every quest done.
 */
object LumbridgeGuide {
    const val ID = 306
    const val TALK_TO = 1

    /** lumbridge_guide_met: the player has heard his first greeting. */
    const val VARBIT_MET = 16839

    /** The items shown in his boxes, with their cache names. */
    const val MINING_ICON_OBJ = 5082 // mining_site_icon_dummy
    const val FURNACE_ICON_OBJ = 5084 // furnace_icon_dummy
    const val FISHING_ICON_OBJ = 5088 // fishing_spot_icon_dummy
    const val RANGE_ICON_OBJ = 5090 // range_icon_dummy
    const val TREE_ICON_OBJ = 9713
    const val BANK_ICON_OBJ = 5080 // bank_icon_dummy

    internal suspend fun Dialogue.talk() = with(LumbridgeGuideLines) {
        if (player.vars.types.varbit(VARBIT_MET) != null && player.vars.getVarbit(VARBIT_MET) == 0) {
            player.vars.setVarbit(VARBIT_MET, 1)
            chatNpc(ChatAnim.HAPPY, GREETING_FIRST)
        } else {
            chatNpc(ChatAnim.HAPPY, HELLO)
        }
        while (true) {
            when (choice(GOALS, QUEST_TO_GO_ON, COMBAT, SKILLS, MORE_OPTIONS)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, GOALS)
                    // Guessed: always this answer; the Wiki gives it under a condition it doesn't name.
                    chatNpc(ChatAnim.NEUTRAL, GOALS_ANSWER)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, QUEST_TO_GO_ON)
                    // Guessed: the Wiki is missing this answer; the old talk's line for a player with every quest done.
                    chatNpc(ChatAnim.NEUTRAL, NO_MORE_QUESTS)
                }
                3 -> chatNpc(ChatAnim.NEUTRAL, COMBAT_HARLAN)
                4 -> skills()
                else -> if (!moreOptions()) return@with
            }
        }
    }

    private suspend fun Dialogue.skills() = with(LumbridgeGuideLines) {
        chatPlayer(ChatAnim.QUIZ, SKILLS)
        chatNpc(ChatAnim.NEUTRAL, SKILLS_1)
        chatNpc(ChatAnim.NEUTRAL, SKILLS_2)
        chatNpc(ChatAnim.NEUTRAL, SKILLS_MINING)
        objbox(MINING_ICON_OBJ, HINT_MINING)
        chatNpc(ChatAnim.NEUTRAL, SKILLS_SMITHING)
        objbox(FURNACE_ICON_OBJ, HINT_FURNACE)
        mesbox(HINT_ANVIL)
        chatNpc(ChatAnim.NEUTRAL, SKILLS_FISHING)
        objbox(FISHING_ICON_OBJ, HINT_FISHING)
        objbox(RANGE_ICON_OBJ, HINT_RANGE)
        chatNpc(ChatAnim.NEUTRAL, SKILLS_WOODCUTTING)
        objbox(TREE_ICON_OBJ, HINT_TREES)
        chatNpc(ChatAnim.NEUTRAL, SKILLS_3)
        chatNpc(ChatAnim.NEUTRAL, SKILLS_4)
        chatNpc(ChatAnim.HAPPY, SKILLS_5)
        mesbox(HINT_GUIDE)
    }

    /** The more options menu, again after each answer; false once the player leaves, true to go back to the first menu. */
    private suspend fun Dialogue.moreOptions(): Boolean {
        val l = LumbridgeGuideLines
        while (true) {
            when (choice(l.MAKE_MONEY, l.FIND_BANK, l.BE_OFF, l.PREVIOUS_OPTIONS)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, l.MAKE_MONEY)
                    for (line in listOf(l.MONEY_1, l.MONEY_2, l.MONEY_3, l.MONEY_4, l.MONEY_5, l.MONEY_6, l.MONEY_7)) chatNpc(ChatAnim.NEUTRAL, line)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, l.FIND_BANK)
                    chatNpc(ChatAnim.NEUTRAL, l.BANK_ROOF)
                    objbox(BANK_ICON_OBJ, l.HINT_BANK)
                }
                3 -> {
                    chatPlayer(ChatAnim.NEUTRAL, l.BE_OFF)
                    chatNpc(ChatAnim.HAPPY, l.UNTIL_NEXT)
                    return false
                }
                else -> return true
            }
        }
    }
}

/** The Lumbridge Guide's Talk-to. */
fun registerLumbridgeGuide(world: World) {
    world.handlers.onNpc(LumbridgeGuide.ID, LumbridgeGuide.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(LumbridgeGuide) { talk() } }
    }
}
