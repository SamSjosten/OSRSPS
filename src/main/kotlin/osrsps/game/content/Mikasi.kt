package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/**
 * Mikasi's lines, as the Lumbridge & Draynor Diary's taskmaster since 242.1; the words are in the dialogue file, from
 * the Wiki's Transcript:Mikasi (Standard dialogue), read 2026-11-08.
 */
object MikasiLines : Lines("mikasi", "Transcript:Mikasi") {
    val WHO_ARE_YOU = line("who_are_you", "first menu, option 2: who she is, which the player then says")
    val TASKMASTER = line("taskmaster", "who she is: her answer")
    val WHAT_DIARY = line("what_diary", "who she is: the player's next question (also the diary menu's option 1)")
    val EXPLAIN = line("explain", "what the diary is: her first line, how it tracks achievements and the rewards")
    val SIDE_PANEL = line("side_panel", "what the diary is: her second line, the task list")
    val MY_DIARY = line("my_diary", "first menu, option 3: about the diary")
    val MY_DIARY_SAID = line("my_diary_said", "about the diary: what the player says (its capitals and stop differ from the option)")
    val REWARDS = line("rewards", "diary menu, option 2: what the rewards are (she answers straight away)")
    val CLAIM = line("claim", "diary menu, option 3: how to claim, which the player then says")
    val FAREWELL = line("farewell", "first and diary menus, last option: goodbye, which the player then says")
    val GOODBYE = line("goodbye", "after goodbye: her answer")
    val FOUR_TIERS = line("four_tiers", "the rewards: the four rings, and which tier to hear about")
    val EASY = line("easy", "the rewards, option 1: the easy tier")
    val MEDIUM = line("medium", "the rewards, option 2: the medium tier")
    val HARD = line("hard", "the rewards, option 3: the hard tier")
    val ELITE = line("elite", "the rewards, option 4: the elite tier")
    val EASY_SAID = line("easy_said", "the easy tier: what the player says")
    val MEDIUM_SAID = line("medium_said", "the medium tier: what the player says")
    val HARD_SAID = line("hard_said", "the hard tier: what the player says")
    val ELITE_SAID = line("elite_said", "the elite tier: what the player says")
    val EASY_REWARDS = line("easy_rewards", "the easy tier: her answer")
    val MEDIUM_REWARDS = line("medium_rewards", "the medium tier: her answer")
    val HARD_REWARDS = line("hard_rewards", "the hard tier: her answer")
    val ELITE_REWARDS = line("elite_rewards", "the elite tier: her answer")
    val THANKS = line("thanks", "after a tier: the player's thanks")
    val FINISH_TASKS = line("finish_tasks", "how to claim: her answer")
}

/**
 * Mikasi outside Lumbridge Castle's gate (NPC 16804, mikasi_lumbridge_diary), the former Magic tutor, who runs the
 * Lumbridge & Draynor Diary since 242.1 moved Hatius Cosaintus to the Falador pub (Wiki update notes, 3 November 2026;
 * Falador isn't built, so he is left out). From the Wiki: Talk-to for a player who has done no diary tasks (there are
 * no diaries here yet). She says nothing before the first menu. Guessed: every chathead expression; leaving out the
 * Wiki's "another ring" option, which a player who has earned no ring can't need; and that the diary menu comes back
 * after "what is the diary" (the Wiki doesn't say). Goodbye brings her first menu back, as the Wiki's transcript has
 * it after "See you later." (from either menu), so the talk ends only when the player walks away.
 */
object Mikasi {
    const val ID = 16804
    const val TALK_TO = 1

    internal suspend fun Dialogue.talk() = with(MikasiLines) {
        while (true) {
            when (choice(WHO_ARE_YOU, MY_DIARY, FAREWELL)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHO_ARE_YOU)
                    chatNpc(ChatAnim.NEUTRAL, TASKMASTER)
                    chatPlayer(ChatAnim.QUIZ, WHAT_DIARY)
                    explain()
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, MY_DIARY_SAID)
                    if (!diaryMenu()) return@with
                }
                else -> farewell()
            }
        }
    }

    /** The diary menu; true to go back to the first menu (after "how to claim" or goodbye), false when the talk has ended. */
    private suspend fun Dialogue.diaryMenu(): Boolean {
        val lines = MikasiLines
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
                    return true
                }
            }
        }
    }

    private suspend fun Dialogue.explain() = with(MikasiLines) {
        chatNpc(ChatAnim.NEUTRAL, EXPLAIN)
        chatNpc(ChatAnim.NEUTRAL, SIDE_PANEL)
    }

    private suspend fun Dialogue.rewards() = with(MikasiLines) {
        chatNpc(ChatAnim.QUIZ, FOUR_TIERS)
        val tiers: List<Pair<Line, Line>> = listOf(
            EASY_SAID to EASY_REWARDS, MEDIUM_SAID to MEDIUM_REWARDS, HARD_SAID to HARD_REWARDS, ELITE_SAID to ELITE_REWARDS,
        )
        val (asked, answer) = tiers[choice(EASY, MEDIUM, HARD, ELITE) - 1]
        chatPlayer(ChatAnim.QUIZ, asked)
        chatNpc(ChatAnim.HAPPY, answer)
        chatPlayer(ChatAnim.HAPPY, THANKS)
    }

    private suspend fun Dialogue.farewell() = with(MikasiLines) {
        chatPlayer(ChatAnim.HAPPY, FAREWELL)
        chatNpc(ChatAnim.HAPPY, GOODBYE)
    }
}

/** Mikasi's Talk-to. */
fun registerMikasi(world: World) {
    world.handlers.onNpc(Mikasi.ID, Mikasi.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Mikasi) { talk() } }
    }
}
