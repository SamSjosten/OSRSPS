package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/** Hatius Cosaintus's lines; the words are in the dialogue file, from the Wiki's Transcript:Hatius Cosaintus (Standard dialogue), read 2026-10-25. */
object HatiusLines : Lines("hatius", "Transcript:Hatius Cosaintus") {
    val WHO_ARE_YOU = line("who_are_you", "first menu, option 2: who he is, which the player then says")
    val TASKMASTER = line("taskmaster", "who he is: his answer")
    val WHAT_DIARY = line("what_diary", "who he is: the player's next question (also the diary menu's option 1)")
    val EXPLAIN = line("explain", "what the diary is: his first line, how it tracks achievements")
    val EXPLORE = line("explore", "what the diary is: his second line, the rewards")
    val SIDE_PANEL = line("side_panel", "what the diary is: his third line, the task list")
    val MY_DIARY = line("my_diary", "first menu, option 3: about the diary, which the player then says")
    val REWARDS = line("rewards", "diary menu, option 2: what the rewards are, which the player then says")
    val CLAIM = line("claim", "diary menu, option 3: how to claim, which the player then says")
    val FAREWELL = line("farewell", "first and diary menus, last option: goodbye, which the player then says")
    val GOODBYE = line("goodbye", "after goodbye: his answer")
    val FOUR_TIERS = line("four_tiers", "the rewards: his first line")
    val WHICH_TIER = line("which_tier", "the rewards: his second line")
    val EASY = line("easy", "the rewards, option 1: the easy tier, which the player then says")
    val MEDIUM = line("medium", "the rewards, option 2: the medium tier, which the player then says")
    val HARD = line("hard", "the rewards, option 3: the hard tier, which the player then says")
    val ELITE = line("elite", "the rewards, option 4: the elite tier, which the player then says")
    val EASY_REWARDS = line("easy_rewards", "the easy tier: his answer")
    val MEDIUM_REWARDS = line("medium_rewards", "the medium tier: his answer")
    val HARD_REWARDS = line("hard_rewards", "the hard tier: his answer")
    val ELITE_REWARDS = line("elite_rewards", "the elite tier: his answer")
    val THANKS = line("thanks", "after a tier: the player's thanks")
    val FINISH_TASKS = line("finish_tasks", "how to claim: his answer")
}

/**
 * Hatius Cosaintus outside Lumbridge Castle's gate (NPC 5523), who runs the Lumbridge & Draynor Diary. From the Wiki:
 * Talk-to for a player who has done no diary tasks (there are no diaries here yet). He says nothing before the first
 * menu. Guessed: every chathead expression; leaving out the Wiki's "another ring" option, which a player who has
 * earned no ring can't need and for which the Wiki names no ring; and that the diary menu comes back after "what is the
 * diary" (the Wiki doesn't say).
 */
object Hatius {
    const val ID = 5523
    const val TALK_TO = 1

    internal suspend fun Dialogue.talk() = with(HatiusLines) {
        while (true) {
            when (choice(WHO_ARE_YOU, MY_DIARY, FAREWELL)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHO_ARE_YOU)
                    chatNpc(ChatAnim.NEUTRAL, TASKMASTER)
                    chatPlayer(ChatAnim.QUIZ, WHAT_DIARY)
                    explain()
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, MY_DIARY)
                    if (!diaryMenu()) return@with
                }
                else -> {
                    farewell()
                    return@with
                }
            }
        }
    }

    /** The diary menu; true to go back to the first menu (after "how to claim"), false when the talk has ended. */
    private suspend fun Dialogue.diaryMenu(): Boolean {
        val lines = HatiusLines
        while (true) {
            when (choice(lines.WHAT_DIARY, lines.REWARDS, lines.CLAIM, lines.FAREWELL)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, lines.WHAT_DIARY)
                    explain()
                }
                2 -> {
                    rewards()
                    return false
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, lines.CLAIM)
                    chatNpc(ChatAnim.NEUTRAL, lines.FINISH_TASKS)
                    return true
                }
                else -> {
                    farewell()
                    return false
                }
            }
        }
    }

    private suspend fun Dialogue.explain() = with(HatiusLines) {
        chatNpc(ChatAnim.NEUTRAL, EXPLAIN)
        chatNpc(ChatAnim.NEUTRAL, EXPLORE)
        chatNpc(ChatAnim.NEUTRAL, SIDE_PANEL)
    }

    private suspend fun Dialogue.rewards() = with(HatiusLines) {
        chatPlayer(ChatAnim.QUIZ, REWARDS)
        chatNpc(ChatAnim.NEUTRAL, FOUR_TIERS)
        chatNpc(ChatAnim.QUIZ, WHICH_TIER)
        val tiers: List<Pair<Line, Line>> = listOf(EASY to EASY_REWARDS, MEDIUM to MEDIUM_REWARDS, HARD to HARD_REWARDS, ELITE to ELITE_REWARDS)
        val (asked, answer) = tiers[choice(EASY, MEDIUM, HARD, ELITE) - 1]
        chatPlayer(ChatAnim.QUIZ, asked)
        chatNpc(ChatAnim.HAPPY, answer)
        chatPlayer(ChatAnim.HAPPY, THANKS)
    }

    private suspend fun Dialogue.farewell() = with(HatiusLines) {
        chatPlayer(ChatAnim.HAPPY, FAREWELL)
        chatNpc(ChatAnim.HAPPY, GOODBYE)
    }
}

/** Hatius Cosaintus's Talk-to. */
fun registerHatius(world: World) {
    world.handlers.onNpc(Hatius.ID, Hatius.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Hatius) { talk() } }
    }
}
