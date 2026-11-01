package osrsps.game.content

import kotlin.test.Test

/** Draynor step 5: Leela's and Morgan's talks, with no dialogue file, so every line shows its key. */
class DraynorTalkTest {
    private val h = DialogueHarness()

    @Test
    fun `Leela thanks the player once the prince is free`() {
        h.talk { with(DraynorJail) { leela() } }
        h.expect("leela.friend", "leela.here", "leela.point", "leela.luck")
        h.ends()
    }

    @Test
    fun `Morgan talks of the vampyre the first time after the quest, and briefly after`() {
        h.talk { with(DraynorVillage) { morganFirst() } }
        h.expect("morgan.no_more", "morgan.hero", "morgan.helped")
        h.ends()
        h.talk { with(DraynorVillage) { morganLater() } }
        h.expect("morgan.once", "morgan.dont")
        h.ends()
    }
}
