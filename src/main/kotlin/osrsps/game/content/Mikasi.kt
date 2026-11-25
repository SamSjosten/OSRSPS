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
    val ANOTHER_RING = line("another_ring", "first menu, option 1 once a ring is earned and the highest earned isn't carried, worn or banked: which the player then says")
    val ANOTHER_RING_GIVEN = line("another_ring_given", "after another ring: the box beside the ring")
    val CLAIM_EASY = line("claim_easy", "a claim: the player has done every easy task (\"Achievement Diary\" capitalised)")
    val CLAIM_MEDIUM = line("claim_medium", "a claim: every medium task (\"achievement diary\" in lower case, as for hard and elite)")
    val CLAIM_HARD = line("claim_hard", "a claim: every hard task")
    val CLAIM_ELITE = line("claim_elite", "a claim: every elite task")
    val WANT_REWARD = line("want_reward", "a claim (easy to hard): her answer, a reward then")
    val YES_PLEASE = line("yes_please", "a claim (easy to hard): the player's answer")
    val ELITE_DONE = line("elite_done", "the elite claim: her answer, every task in the diary done")
    val RING_EASY = line("ring_easy", "the easy claim, after the ring: what it does")
    val RING_MEDIUM = line("ring_medium", "the medium claim: the ring upgraded, what it does")
    val RING_HARD = line("ring_hard", "the hard claim: the ring upgraded, what it does")
    val RING_ELITE = line("ring_elite", "the elite claim: the ring upgraded, what it does (two boxes on live)")
    val WOW_THANKS = line("wow_thanks", "a claim: the player's thanks")
    val LOSE_RING = line("lose_ring", "the easy claim, last: come back if the ring is lost")
}

/**
 * Mikasi outside Lumbridge Castle's gate (NPC 16804, mikasi_lumbridge_diary), the former Magic tutor, who runs the
 * Lumbridge & Draynor Diary since 242.1 moved Hatius Cosaintus to the Falador pub (Wiki update notes, 3 November 2026;
 * Falador isn't built, so he is left out). From the Wiki: her menus, and claiming each tier's reward (Explorer's ring 1
 * to 4 with the tier's antique lamp, the lamp from the diary's page, as her transcript leaves it out), the tiers in
 * order. She says nothing before the first menu. Guessed: every chathead expression; that a claim runs as soon as
 * Talk-to is clicked, in place of her silence, and her first menu follows; that a tier whose earlier tiers aren't all
 * done can't be claimed and says nothing (the Wiki has no words for it); that "Can I have another ring?" shows once a
 * ring is earned and the highest tier earned isn't carried, worn or banked, and gives that tier; that a full inventory doesn't
 * stop a claim (the ring and lamp land on the floor; Toby's transcript checks for space, hers doesn't); and that the
 * diary menu comes back after "what is the diary". Goodbye brings her first menu back, as the Wiki's transcript has
 * it after "See you later." (from either menu), so the talk ends only when the player walks away.
 */
object Mikasi {
    const val ID = 16804
    const val TALK_TO = 1

    private val AREA = DiaryArea.LUMBRIDGE

    internal suspend fun Dialogue.talk(diaries: Diaries) = with(MikasiLines) {
        claim(diaries)
        while (true) {
            val another = diaries.highestClaimed(player, AREA) != null && !diaries.ownsReward(player, AREA)
            val options = listOfNotNull(ANOTHER_RING.takeIf { another }, WHO_ARE_YOU, MY_DIARY, FAREWELL)
            when (options[choice(*options.toTypedArray()) - 1]) {
                ANOTHER_RING -> {
                    chatPlayer(ChatAnim.QUIZ, ANOTHER_RING)
                    diaries.replace(player, AREA)
                    objbox(AREA.rewards[diaries.highestClaimed(player, AREA)!!.ordinal], ANOTHER_RING_GIVEN)
                }
                WHO_ARE_YOU -> {
                    chatPlayer(ChatAnim.QUIZ, WHO_ARE_YOU)
                    chatNpc(ChatAnim.NEUTRAL, TASKMASTER)
                    chatPlayer(ChatAnim.QUIZ, WHAT_DIARY)
                    explain()
                }
                MY_DIARY -> {
                    chatPlayer(ChatAnim.QUIZ, MY_DIARY_SAID)
                    if (!diaryMenu()) return@with
                }
                else -> farewell()
            }
        }
    }

    /** The lowest tier ready to claim, if its earlier tiers are done: the ring and lamp, and her words for them. */
    private suspend fun Dialogue.claim(diaries: Diaries) = with(MikasiLines) {
        val tier = diaries.claimable(player, AREA)?.takeIf { diaries.previousDone(player, AREA, it) } ?: return@with
        chatPlayer(ChatAnim.HAPPY, listOf(CLAIM_EASY, CLAIM_MEDIUM, CLAIM_HARD, CLAIM_ELITE)[tier.ordinal])
        if (tier == DiaryTier.ELITE) {
            chatNpc(ChatAnim.HAPPY, ELITE_DONE)
        } else {
            chatNpc(ChatAnim.NEUTRAL, WANT_REWARD)
            chatPlayer(ChatAnim.HAPPY, YES_PLEASE)
        }
        diaries.claim(player, AREA, tier)
        chatNpc(ChatAnim.HAPPY, listOf(RING_EASY, RING_MEDIUM, RING_HARD, RING_ELITE)[tier.ordinal])
        chatPlayer(ChatAnim.HAPPY, WOW_THANKS)
        if (tier == DiaryTier.EASY) chatNpc(ChatAnim.NEUTRAL, LOSE_RING)
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
        world.dialogues.start(this, op.npc) { with(Mikasi) { talk(world.diaries) } }
    }
}
