package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Draynor step 3: the market traders' talks, with no dialogue file, so every line shows its key. */
class DraynorMarketTest {
    private val h = DialogueHarness()

    @Test
    fun `Fortunato greets a returning customer, then sells or sees the player off`() {
        val opening = arrayOf(
            "fortunato.help", "fortunato.back", "fortunato.please", "fortunato.just_looking", "fortunato.wonderful",
            "fortunato.stock", "fortunato.something",
        )
        var trade = false
        h.talk { trade = with(DraynorMarket) { fortunato() } }
        h.expect(*opening)
        h.menu("fortunato.yes", "fortunato.no")
        h.pick(1)
        h.expect("fortunato.yes")
        h.ends()
        assertTrue(trade)

        h.talk { trade = with(DraynorMarket) { fortunato() } }
        h.expect(*opening)
        h.menu("fortunato.yes", "fortunato.no")
        h.pick(2)
        h.expect("fortunato.no", "fortunato.well", "fortunato.thanks")
        h.ends()
        assertFalse(trade)
    }

    @Test
    fun `Olivia opens her shop at once, or says no thanks, or where her seeds come from`() {
        val trades = mutableListOf<Boolean>()
        for (option in 1..3) {
            h.talk { trades += with(DraynorMarket) { olivia() } }
            h.expect("olivia.seeds")
            h.menu("olivia.yes", "olivia.no", "olivia.where")
            h.pick(option)
            when (option) {
                // The shop opens straight from the menu, with no page after it.
                1 -> assertEquals(-1, h.player.chatModal)
                2 -> h.expect("olivia.no_thanks")
                else -> h.expect("olivia.where", "olivia.honest")
            }
            if (option != 1) h.ends()
        }
        assertEquals(listOf(true, false, false), trades)
    }

    @Test
    fun `Diango's spinning plates and selling options open the shop, the other two don't`() {
        val trades = mutableListOf<Boolean>()
        for (option in 1..4) {
            h.talk { trades += with(DraynorMarket) { diango() } }
            h.expect("diango.howdy")
            h.menu("diango.plates", "diango.holiday", "diango.selling", "diango.no_thanks")
            h.pick(option)
            when (option) {
                1 -> h.expect("diango.plates", "diango.thieves", "diango.plates_2")
                2 -> h.expect("diango.holiday", "diango.missing")
                3 -> h.expect("diango.selling")
                else -> h.expect("diango.no_thanks")
            }
            h.ends()
        }
        assertEquals(listOf(true, false, true, false), trades)
    }
}
