package osrsps.game.content

import osrsps.game.GameFrame
import osrsps.game.JournalTab
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Toby's lines, from the Wiki's Transcript:Toby, read 2026-11-11 (the claims 2026-11-19). */
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
    val ANOTHER_PLATEBODY = line("another_platebody", "top menu option 1 once an armour is earned and none is carried, worn or banked: which the player then says")
    val ANOTHER_PLATEBODY_GIVEN = line("another_platebody_given", "after another platebody: the box")
    val CLAIM_EASY = line("claim_easy", "a claim: the player has done every easy task (\"Achievement Diary\" capitalised)")
    val CLAIM_MEDIUM = line("claim_medium", "a claim: every medium task (\"achievement diary\" in lower case, as for hard and elite)")
    val CLAIM_HARD = line("claim_hard", "a claim: every hard task")
    val CLAIM_ELITE = line("claim_elite", "a claim: every elite task")
    val WELL_DONE = line("well_done", "a claim (easy to hard): his answer, a reward then")
    val YES_PLEASE = line("yes_please", "a claim (easy to hard): the player's answer")
    val ELITE_DONE = line("elite_done", "the elite claim: his answer, every task in the Varrock area done, here's the reward")
    val ELITE_EARLIER = line("elite_earlier", "the elite claim with an earlier tier not done: complete the easy, medium and hard tasks too")
    val NO_SPACE = line("no_space", "a claim without two free inventory spaces")
    val ARMOUR_EASY = line("armour_easy", "the easy claim: the armour, what it does (two boxes on live)")
    val ARMOUR_MEDIUM = line("armour_medium", "the medium claim: the armour upgraded, what it does")
    val ARMOUR_HARD = line("armour_hard", "the hard claim: the armour upgraded, what it does")
    val ARMOUR_ELITE = line("armour_elite", "the elite claim: the armour upgraded, what it does")
    val WOW_THANKS = line("wow_thanks", "a claim: the player's thanks")
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
 * - Toby (5525), the Varrock diary's taskmaster: his menus, and claiming each tier (Varrock armour 1 to 4 and the
 *   tier's antique lamp, with two free inventory spaces, the tiers in order); "Who are you?" opens the Achievement
 *   Diaries tab, as the Wiki has it. Guessed: a claim runs as soon as Talk-to is clicked, in place of his Hello, and his
 *   first menu follows; a medium or hard claim with an earlier tier not done says nothing (the Wiki's words are
 *   missing), while an elite one gets his "easy, medium and hard tasks too" line; "Can I have another Varrock
 *   platebody?" shows once an armour is earned and none is carried, worn or banked, and gives the highest tier earned.
 * - James (`bond_james_bond` 5885, shown as `bond_james_visible` 8193): every answer returns to his menu, which has no
 *   way out but closing it, as the Wiki has it.
 * - Meredith (10733) in the Games Zone: her answers; the board games themselves aren't built. Her games menu has no
 *   way back either.
 * - The Weaponsmaster (5211), upstairs in the Phoenix Gang's house: his fight is the Wiki's stats (npc-combat.json);
 *   his talk to a gang member is [PhoenixGang]'s (post-quest step 2).
 *
 * Left out: the Castle Wars recruiters, Marlo (Mahogany Homes), the Estate agent (player-owned
 * houses) and the Assistant Serf (balloons): their talk leads straight into a system that isn't built. Guessed: every
 * chathead expression.
 */
object VarrockSweepTalkers {
    const val TOBY = 5525
    const val JAMES = 8193
    const val MEREDITH = 10733
    const val TALK_TO = 1

    private val VARROCK = DiaryArea.VARROCK

    internal suspend fun Dialogue.toby(world: World) = with(TobyLines) {
        val diaries = world.diaries
        when (tobyClaim(diaries)) {
            null -> return@with
            false -> chatNpc(ChatAnim.HAPPY, HELLO)
            true -> Unit
        }
        while (true) {
            val another = diaries.highestClaimed(player, VARROCK) != null && !diaries.ownsReward(player, VARROCK)
            val options = listOfNotNull(ANOTHER_PLATEBODY.takeIf { another }, WHO, QUESTION, BYE)
            when (options[choice(*options.toTypedArray()) - 1]) {
                ANOTHER_PLATEBODY -> {
                    chatPlayer(ChatAnim.QUIZ, ANOTHER_PLATEBODY)
                    diaries.replace(player, VARROCK)
                    objbox(VARROCK.rewards[diaries.highestClaimed(player, VARROCK)!!.ordinal], ANOTHER_PLATEBODY_GIVEN)
                }
                WHO -> {
                    chatPlayer(ChatAnim.QUIZ, WHO)
                    chatNpc(ChatAnim.HAPPY, WHO_1)
                    chatPlayer(ChatAnim.QUIZ, WHO_2)
                    chatNpc(ChatAnim.NEUTRAL, DIARY_1)
                    world.interfaces.switchSideTab(player, GameFrame.SIDE_TAB_QUEST)
                    world.interfaces.openJournalTab(player, JournalTab.DIARIES)
                    chatNpc(ChatAnim.NEUTRAL, DIARY_2)
                }
                QUESTION -> {
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

    /**
     * The lowest tier ready to claim: true when Toby spoke (a claim made, or the elite refused for an earlier tier), so
     * his Hello is skipped and his menu follows; null when he turned it down for space, which ends the talk (the Wiki's
     * "end"); false when there was nothing to claim.
     */
    private suspend fun Dialogue.tobyClaim(diaries: Diaries): Boolean? = with(TobyLines) {
        val tier = diaries.claimable(player, VARROCK) ?: return false
        val earlierDone = diaries.previousDone(player, VARROCK, tier)
        if (!earlierDone && tier != DiaryTier.ELITE) return false
        chatPlayer(ChatAnim.HAPPY, listOf(CLAIM_EASY, CLAIM_MEDIUM, CLAIM_HARD, CLAIM_ELITE)[tier.ordinal])
        if (!earlierDone) {
            chatNpc(ChatAnim.NEUTRAL, ELITE_EARLIER)
            return true
        }
        if (tier == DiaryTier.ELITE) {
            chatNpc(ChatAnim.HAPPY, ELITE_DONE)
        } else {
            chatNpc(ChatAnim.HAPPY, WELL_DONE)
            chatPlayer(ChatAnim.HAPPY, YES_PLEASE)
        }
        if (player.inventory.freeSlots < 2) {
            chatNpc(ChatAnim.NEUTRAL, NO_SPACE)
            return null
        }
        diaries.claim(player, VARROCK, tier)
        chatNpc(ChatAnim.HAPPY, listOf(ARMOUR_EASY, ARMOUR_MEDIUM, ARMOUR_HARD, ARMOUR_ELITE)[tier.ordinal])
        chatPlayer(ChatAnim.HAPPY, WOW_THANKS)
        true
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
        talk(TOBY) { toby(world) }
        talk(JAMES) { james() }
        talk(MEREDITH) { meredith() }
    }
}
