package osrsps.game.content

import kotlin.test.Test
import osrsps.game.content.EmirsArena.dalbur
import osrsps.game.content.EmirsArena.duelGuide
import osrsps.game.content.EmirsArena.fronk
import osrsps.game.content.EmirsArena.tournamentGuide

/** Al Kharid step 9: Captain Dalbur, the Arena's Guides and Arena Guard Fronk talk; the glider doesn't fly yet. */
class EmirsArenaTest {
    private val h = DialogueHarness()
    private val dalburMenu = arrayOf("dalbur.glider", "dalbur.ali", "dalbur.better", "dalbur.nothing")

    @Test
    fun `Captain Dalbur's glider answer ends at the stub, his other answers as the Wiki has them`() {
        h.talk { dalbur() }
        h.menu(*dalburMenu)
        h.pick(1)
        h.expect("dalbur.glider", "dalbur.of_course", "arena_stub.glider_closed")
        h.ends()

        h.talk { dalbur() }
        h.menu(*dalburMenu)
        h.pick(2)
        h.expect("dalbur.ali", "dalbur.ali_1", "dalbur.magic_carpet", "dalbur.ali_2", "dalbur.ali_3")
        h.ends()

        h.talk { dalbur() }
        h.menu(*dalburMenu)
        h.pick(3)
        h.expect("dalbur.better", "dalbur.better_1", "dalbur.better_2", "dalbur.network", "dalbur.secret")
        h.ends()

        h.talk { dalbur() }
        h.menu(*dalburMenu)
        h.pick(4)
        h.expect("dalbur.nothing")
        h.ends()
    }

    @Test
    fun `the Duel Guide loops until the player leaves, and organising changes the last option`() {
        val first = arrayOf("duel_guide.what", "duel_guide.ranked", "duel_guide.invited", "duel_guide.organise", "duel_guide.okay")
        val after = arrayOf("duel_guide.what", "duel_guide.ranked", "duel_guide.invited", "duel_guide.goodbye")
        h.talk { duelGuide() }
        h.expect("duel_guide.welcome")
        h.menu(*first)
        h.pick(1)
        h.expect("duel_guide.what", "duel_guide.what_1")
        h.menu(*first)
        h.pick(3)
        h.expect(
            "duel_guide.invited", "duel_guide.invited_1", "duel_guide.tutorial_1", "duel_guide.tutorial_2", "duel_guide.tutorial_3",
            "duel_guide.invited_2", "duel_guide.invited_3", "duel_guide.challenge_1", "duel_guide.challenge_2",
        )
        h.menu(*first)
        h.pick(4)
        h.expect("duel_guide.organise", "duel_guide.organise_1", "duel_guide.challenge_1", "duel_guide.challenge_2")
        h.menu(*after)
        h.pick(2)
        h.expect("duel_guide.ranked", "duel_guide.ranked_1", "duel_guide.ranked_2", "duel_guide.ranked_3", "duel_guide.ranked_4")
        h.menu(*after)
        h.pick(4)
        h.expect("duel_guide.goodbye")
        h.ends()

        h.talk { duelGuide() }
        h.expect("duel_guide.welcome")
        h.menu(*first)
        h.pick(5)
        h.expect("duel_guide.okay")
        h.ends()
    }

    @Test
    fun `the 1v1 Tournament Guide's talk`() {
        val first = arrayOf(
            "tournament_guide.what", "tournament_guide.ranked", "tournament_guide.invited", "tournament_guide.organise", "tournament_guide.okay",
        )
        val after = arrayOf("tournament_guide.what", "tournament_guide.ranked", "tournament_guide.invited", "tournament_guide.goodbye")
        h.talk { tournamentGuide() }
        h.expect("tournament_guide.welcome")
        h.menu(*first)
        h.pick(1)
        h.expect("tournament_guide.what", "tournament_guide.what_1", "tournament_guide.what_2")
        h.menu(*first)
        h.pick(3)
        h.expect(
            "tournament_guide.invited", "tournament_guide.invited_1", "tournament_guide.tutorial_1", "tournament_guide.tutorial_2",
            "tournament_guide.tutorial_3", "tournament_guide.invited_2",
        )
        h.menu(*first)
        h.pick(4)
        h.expect("tournament_guide.organise", "tournament_guide.organise_1", "tournament_guide.organise_tutorial_1", "tournament_guide.organise_tutorial_2")
        h.menu(*after)
        h.pick(4)
        h.expect("tournament_guide.goodbye")
        h.ends()
    }

    @Test
    fun `Fronk's duties open the Coven's history, which he tells again when asked`() {
        val first = arrayOf("fronk.place", "fronk.do_here", "fronk.work_out", "fronk.fine")
        val coven = arrayOf("fronk.coven_1", "fronk.coven_2", "fronk.coven_3", "fronk.coven_4", "fronk.coven_5")
        h.talk { fronk() }
        h.expect("fronk.greeting")
        h.menu(*first)
        h.pick(1)
        h.expect("fronk.place", "fronk.place_1", "fronk.place_2")
        h.menu(*first)
        h.pick(2)
        h.expect("fronk.do_here", "fronk.do_here_1")
        h.menu("fronk.coven", "fronk.place", "fronk.work_out", "fronk.fine")
        h.pick(1)
        h.expect("fronk.coven", *coven)
        h.menu("fronk.coven_again", "fronk.place", "fronk.work_out", "fronk.fine")
        h.pick(1)
        h.expect("fronk.coven_again", *coven)
        h.menu("fronk.coven_again", "fronk.place", "fronk.work_out", "fronk.fine")
        h.pick(3)
        h.expect("fronk.work_out", "fronk.work_out_1")
        h.menu("fronk.coven_again", "fronk.place", "fronk.work_out", "fronk.fine")
        h.pick(4)
        h.expect("fronk.fine")
        h.ends()
    }
}
