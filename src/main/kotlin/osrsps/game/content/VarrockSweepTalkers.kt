package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Toby's lines with no diary tier claimed, from the Wiki's Transcript:Toby, read 2026-11-11. */
object TobyLines : Lines("toby", "Transcript:Toby") {
    val HELLO = line("hello", "his greeting")
    val WHO = line("who", "top menu option 2, which the player then says")
    val WHO_1 = line("who_1", "who: he is the taskmaster")
    val WHO_2 = line("who_2", "who: the player asks about the diary (no menu)")
    val DIARY_1 = line("diary_1", "what the diary is: it tracks achievements (also the question menu's first answer)")
    val DIARY_2 = line("diary_2", "what the diary is: the side panel")
    val QUESTION = line("question", "top menu option 3: a question about the diary")
    val QUESTION_SAID = line("question_said", "the player's box after option 3, near the option's words")
    val WHAT_IS = line("what_is", "question menu option 1, which the player then says")
    val REWARDS = line("rewards", "question menu option 2 (not said)")
    val REWARDS_1 = line("rewards_1", "rewards: four armours, one per difficulty")
    val EASY = line("easy", "rewards menu option 1")
    val EASY_ASK = line("easy_ask", "easy: the player asks for the details")
    val EASY_1 = line("easy_1", "easy: Toby's answer")
    val MEDIUM = line("medium", "rewards menu option 2")
    val MEDIUM_ASK = line("medium_ask", "medium: the player asks for the details")
    val MEDIUM_1 = line("medium_1", "medium: Toby's answer")
    val HARD = line("hard", "rewards menu option 3")
    val HARD_ASK = line("hard_ask", "hard: the player asks for the details")
    val HARD_1 = line("hard_1", "hard: Toby's answer")
    val ELITE = line("elite", "rewards menu option 4")
    val ELITE_ASK = line("elite_ask", "elite: the player asks for the details")
    val ELITE_1 = line("elite_1", "elite: Toby's answer")
    val THANKS = line("thanks", "after any tier: the player's brief thanks")
    val CLAIM = line("claim", "question menu option 3, which the player then says")
    val CLAIM_1 = line("claim_1", "claim: complete the tasks, then come back")
    val BYE = line("bye", "either menu's last option, which the player then says")
    val BYE_1 = line("bye_1", "his farewell")
}

/** James's lines, from the Wiki's Transcript:James, read 2026-11-11. */
object JamesLines : Lines("james_bonds", "Transcript:James") {
    val WHO = line("who", "the player's opening question")
    val WHO_1 = line("who_1", "his introduction")
    val WHAT = line("what", "menu option 1 (not said)")
    val WHAT_1 = line("what_1", "what a bond is: membership")
    val WHAT_2 = line("what_2", "what a bond is: a free name change")
    val BUY = line("buy", "menu option 2 (not said)")
    val BUY_1 = line("buy_1", "buying one: from players or the Grand Exchange")
    val REDEEM = line("redeem", "menu option 3 (not said)")
    val REDEEM_1 = line("redeem_1", "redeeming one")
    val MORE = line("more", "menu option 4, the second menu")
    val HOW_MUCH = line("how_much", "second menu option 1 (not said)")
    val HOW_MUCH_1 = line("how_much_1", "how much membership: 14 days")
    val ELSE = line("else", "second menu option 2 (not said)")
    val ELSE_1 = line("else_1", "anything else: membership and name changes only")
    val GIVE = line("give", "second menu option 3 (not said)")
    val GIVE_1 = line("give_1", "giving or selling one: the Bond Pouch")
}

/** Meredith's lines, from the Wiki's Transcript:Meredith, read 2026-11-11 (the non-Deadman version). */
object MeredithLines : Lines("meredith", "Transcript:Meredith") {
    val GREETING = line("greeting", "her greeting to the Games Zone")
    val HOW = line("how", "option 1, which the player then says")
    val HOW_1 = line("how_1", "how: challenge anyone by right-click")
    val HOW_2 = line("how_2", "how: pick the game and its options")
    val HOW_3 = line("how_3", "how: accepting takes both to a table")
    val HOW_4 = line("how_4", "how: replay any time; pub tables too")
    val WHICH = line("which", "option 2, which the player then says")
    val WHICH_1 = line("which_1", "which: the four games")
    val DRAUGHTS = line("draughts", "games menu option 1, which the player then says")
    val DRAUGHTS_1 = line("draughts_1", "draughts: the rules")
    val RUNELINK = line("runelink", "games menu option 2, which the player then says")
    val RUNELINK_1 = line("runelink_1", "runelink: four in a row")
    val RUNESQUARES = line("runesquares", "games menu option 3, which the player then says")
    val RUNESQUARES_1 = line("runesquares_1", "runesquares: adding lines")
    val RUNESQUARES_2 = line("runesquares_2", "runesquares: a square earns another turn")
    val RUNEVERSI = line("runeversi", "games menu option 4, which the player then says")
    val RUNEVERSI_1 = line("runeversi_1", "runeversi: the goal")
    val RUNEVERSI_2 = line("runeversi_2", "runeversi: capturing")
    val DRINK = line("drink", "option 3, which the player then says")
    val DRINK_1 = line("drink_1", "drink: none sold here")
    val THANKS = line("thanks", "option 4, which the player then says")
}

/**
 * More of Varrock's people the Misthalin sweep found unspawned, as far as their talk goes without a system that isn't
 * built; spawned in varrock.json on mejrs's tiles:
 *
 * - Toby (5525), the Varrock diary's taskmaster: his menus and the rewards' descriptions. There are no diaries, so
 *   nothing is claimable: the platebody option is left out of his first menu (guessed: the Wiki doesn't say when it
 *   shows), and "Who are you?" doesn't open the diary tab.
 * - James (`bond_james_bond` 5885, shown as `bond_james_visible` 8193): every answer returns to his menu, which has no
 *   way out but closing it, as the Wiki has it.
 * - Meredith (10733) in the Games Zone: her answers; the board games themselves aren't built. Her games menu has no
 *   way back either.
 * - The Weaponsmaster (5211), upstairs in the Phoenix Gang's house: a fight only (the Wiki's stats, npc-combat.json);
 *   his talk is left out, as the Wiki's transcript couldn't be read.
 *
 * Left out: the Castle Wars recruiters, the clan Clerk, Marlo (Mahogany Homes), the Estate agent (player-owned
 * houses) and the Assistant Serf (balloons): their talk leads straight into a system that isn't built. Guessed: every
 * chathead expression.
 */
object VarrockSweepTalkers {
    const val TOBY = 5525
    const val JAMES = 8193
    const val MEREDITH = 10733
    const val TALK_TO = 1

    internal suspend fun Dialogue.toby() = with(TobyLines) {
        chatNpc(ChatAnim.HAPPY, HELLO)
        while (true) {
            when (choice(WHO, QUESTION, BYE)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHO)
                    chatNpc(ChatAnim.HAPPY, WHO_1)
                    chatPlayer(ChatAnim.QUIZ, WHO_2)
                    chatNpc(ChatAnim.NEUTRAL, DIARY_1)
                    chatNpc(ChatAnim.NEUTRAL, DIARY_2)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, QUESTION_SAID)
                    when (choice(WHAT_IS, REWARDS, CLAIM, BYE)) {
                        1 -> {
                            chatPlayer(ChatAnim.QUIZ, WHAT_IS)
                            chatNpc(ChatAnim.NEUTRAL, DIARY_1)
                            chatNpc(ChatAnim.NEUTRAL, DIARY_2)
                        }
                        2 -> {
                            chatNpc(ChatAnim.NEUTRAL, REWARDS_1)
                            val (ask, answer) = when (choice(EASY, MEDIUM, HARD, ELITE)) {
                                1 -> EASY_ASK to EASY_1
                                2 -> MEDIUM_ASK to MEDIUM_1
                                3 -> HARD_ASK to HARD_1
                                else -> ELITE_ASK to ELITE_1
                            }
                            chatPlayer(ChatAnim.QUIZ, ask)
                            chatNpc(ChatAnim.NEUTRAL, answer)
                            chatPlayer(ChatAnim.HAPPY, THANKS)
                            return@with
                        }
                        3 -> {
                            chatPlayer(ChatAnim.QUIZ, CLAIM)
                            chatNpc(ChatAnim.NEUTRAL, CLAIM_1)
                        }
                        else -> return@with bye()
                    }
                }
                else -> return@with bye()
            }
        }
    }

    private suspend fun Dialogue.bye() = with(TobyLines) {
        chatPlayer(ChatAnim.HAPPY, BYE)
        chatNpc(ChatAnim.HAPPY, BYE_1)
    }

    internal suspend fun Dialogue.james() = with(JamesLines) {
        chatPlayer(ChatAnim.QUIZ, WHO)
        chatNpc(ChatAnim.HAPPY, WHO_1)
        while (true) {
            when (choice(WHAT, BUY, REDEEM, MORE)) {
                1 -> {
                    chatNpc(ChatAnim.NEUTRAL, WHAT_1)
                    chatNpc(ChatAnim.NEUTRAL, WHAT_2)
                }
                2 -> chatNpc(ChatAnim.NEUTRAL, BUY_1)
                3 -> chatNpc(ChatAnim.NEUTRAL, REDEEM_1)
                else -> when (choice(HOW_MUCH, ELSE, GIVE)) {
                    1 -> chatNpc(ChatAnim.NEUTRAL, HOW_MUCH_1)
                    2 -> chatNpc(ChatAnim.NEUTRAL, ELSE_1)
                    else -> chatNpc(ChatAnim.NEUTRAL, GIVE_1)
                }
            }
        }
    }

    internal suspend fun Dialogue.meredith() = with(MeredithLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        while (true) {
            when (choice(HOW, WHICH, DRINK, THANKS)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, HOW)
                    chatNpc(ChatAnim.NEUTRAL, HOW_1)
                    chatNpc(ChatAnim.NEUTRAL, HOW_2)
                    chatNpc(ChatAnim.NEUTRAL, HOW_3)
                    chatNpc(ChatAnim.NEUTRAL, HOW_4)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, WHICH)
                    chatNpc(ChatAnim.NEUTRAL, WHICH_1)
                    while (true) {
                        when (choice(DRAUGHTS, RUNELINK, RUNESQUARES, RUNEVERSI)) {
                            1 -> {
                                chatPlayer(ChatAnim.QUIZ, DRAUGHTS)
                                chatNpc(ChatAnim.NEUTRAL, DRAUGHTS_1)
                            }
                            2 -> {
                                chatPlayer(ChatAnim.QUIZ, RUNELINK)
                                chatNpc(ChatAnim.NEUTRAL, RUNELINK_1)
                            }
                            3 -> {
                                chatPlayer(ChatAnim.QUIZ, RUNESQUARES)
                                chatNpc(ChatAnim.NEUTRAL, RUNESQUARES_1)
                                chatNpc(ChatAnim.NEUTRAL, RUNESQUARES_2)
                            }
                            else -> {
                                chatPlayer(ChatAnim.QUIZ, RUNEVERSI)
                                chatNpc(ChatAnim.NEUTRAL, RUNEVERSI_1)
                                chatNpc(ChatAnim.NEUTRAL, RUNEVERSI_2)
                            }
                        }
                    }
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, DRINK)
                    chatNpc(ChatAnim.NEUTRAL, DRINK_1)
                    return@with
                }
                else -> {
                    chatPlayer(ChatAnim.HAPPY, THANKS)
                    return@with
                }
            }
        }
    }
}

fun registerVarrockSweepTalkers(world: World) {
    with(VarrockSweepTalkers) {
        fun talk(id: Int, block: suspend Dialogue.() -> Unit) =
            world.handlers.onNpc(id, TALK_TO) { op -> world.dialogues.start(this, op.npc) { block() } }
        talk(TOBY) { toby() }
        talk(JAMES) { james() }
        talk(MEREDITH) { meredith() }
    }
}
