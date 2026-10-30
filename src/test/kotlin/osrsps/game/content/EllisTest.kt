package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.dialogue.DialogueText
import osrsps.game.shop.ShopScreen

/** Al Kharid step 4b: Ellis's talk and tanning. */
class EllisTest {
    private val h = DialogueHarness()
    private val coins = ObjType(ShopScreen.COINS, "Coins", stackable = true)
    private val cowhide = ObjType(COWHIDE, "Cowhide")
    private val objs = ObjTypes.of(coins, cowhide, ObjType(LEATHER, "Leather"), ObjType(HARD_LEATHER, "Hard leather"))

    @Test
    fun `with no hides Ellis has his two topics and Ali Morrisane`() {
        val menu = arrayOf("ellis.buy_leather", "ellis.weak", "ellis.ali")
        var tan = true
        h.talk { tan = with(Ellis) { talk() } }
        h.expect("ellis.greeting")
        h.menu(*menu)
        h.pick(1)
        h.expect("ellis.buy_leather", "ellis.how_it_works")
        h.ends()
        assertFalse(tan)

        h.talk { tan = with(Ellis) { talk() } }
        h.expect("ellis.greeting")
        h.menu(*menu)
        h.pick(2)
        h.expect("ellis.weak", "ellis.normal", "ellis.alternatively", "ellis.dragonhides", "ellis.bear_in_mind")
        h.ends()

        h.talk { tan = with(Ellis) { talk() } }
        h.expect("ellis.greeting")
        h.menu(*menu)
        h.pick(3)
        h.expect("ellis.ali", "ellis.ali_1", "ellis.ali_2", "ellis.ali_3", "ellis.threats", "ellis.protected", "ellis.armour")
        h.ends()
        assertFalse(tan)
    }

    @Test
    fun `with hides he offers to tan one or several`() {
        h.player.inventory.add(cowhide, 1)
        var tan = false
        h.talk { tan = with(Ellis) { talk() } }
        h.expect("ellis.greeting", "ellis.one_hide")
        h.menu("ellis.yes_please", "ellis.no_thanks")
        h.pick(1)
        assertTrue(tan)

        h.player.inventory.add(cowhide, 1)
        h.talk { tan = with(Ellis) { talk() } }
        h.expect("ellis.greeting", "ellis.some_hides")
        h.menu("ellis.yes_please", "ellis.no_thanks")
        h.pick(2)
        h.expect("ellis.no_thanks", "ellis.as_you_wish")
        h.ends()
        assertFalse(tan)
    }

    private fun tanner(hides: Int, gold: Int) = Player(2, "Tanner", 3276, 3192, 0).apply {
        if (hides > 0) inventory.add(cowhide, hides)
        if (gold > 0) inventory.add(coins, gold)
    }

    private val messages = mutableListOf<String>()

    private fun tan(player: Player, index: Int, wanted: Int): Int {
        val count = Ellis.tan(player, index, wanted, DialogueText.NONE, objs) { messages += it }
        return count
    }

    @Test
    fun `tanning takes as many hides as asked, held and paid for, and changes them into leather`() {
        tanner(5, 10).let { p ->
            assertEquals(5, tan(p, 0, Int.MAX_VALUE))
            assertEquals(0L, p.inventory.count(COWHIDE))
            assertEquals(5L, p.inventory.count(LEATHER))
            assertEquals(5L, p.inventory.count(ShopScreen.COINS))
            assertEquals("[tanning.tanned_some count=5 hide=cowhides]", messages.last())
        }
        // Hard leather at 3 coins a hide: 10 coins pay for three of five.
        tanner(5, 10).let { p ->
            assertEquals(3, tan(p, 1, 5))
            assertEquals(2L, p.inventory.count(COWHIDE))
            assertEquals(3L, p.inventory.count(HARD_LEATHER))
            assertEquals(1L, p.inventory.count(ShopScreen.COINS))
            // Two hides left and too few coins for another.
            assertEquals("[tanning.no_more_coins leather=hard leather]", messages.last())
        }
        tanner(5, 10).let { p -> assertEquals(1, tan(p, 0, 1)) }
        // None held, or no coins for one: nothing changes.
        messages.clear()
        tanner(0, 10).let { p -> assertEquals(0, tan(p, 0, 5)) }
        assertEquals(listOf("[tanning.no_hides hide=cowhides]"), messages)
        tanner(3, 2).let { p ->
            assertEquals(0, tan(p, 1, 5))
            assertEquals(3L, p.inventory.count(COWHIDE))
        }
    }

    @Test
    fun `the Tan buttons map to their slots and amounts`() {
        assertEquals(0 to Int.MAX_VALUE, Ellis.button(124))
        assertEquals(7 to -1, Ellis.button(139))
        assertEquals(2 to 5, Ellis.button(142))
        assertEquals(7 to 1, Ellis.button(155))
        assertNull(Ellis.button(123))
        assertEquals(listOf(1, 3, 15, 20, 20, 20, 20, 20), Ellis.TANNINGS.map { it.price })
        assertEquals("examine.npc_3231", Examines.npc(Ellis.ID)?.key)
    }

    private companion object {
        const val COWHIDE = 1739
        const val LEATHER = 1741
        const val HARD_LEATHER = 1743
    }
}
