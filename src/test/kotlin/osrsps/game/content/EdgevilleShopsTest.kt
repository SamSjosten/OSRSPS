package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.content.EdgevilleShops.oziach
import osrsps.game.content.EdgevilleShops.richard
import osrsps.game.content.LumbridgeShopkeepers.keeper

/** Edgeville step 2: the shop keepers' talk, with no dialogue file, so every line shows its key. */
class EdgevilleShopsTest {
    private val h = DialogueHarness()

    @Test
    fun `a General Store keeper opens the shop or says no thanks`() {
        for ((lines, prefix) in listOf(EdgevilleShopKeeperLines to "edgeville_shop_keeper", EdgevilleShopAssistantLines to "edgeville_shop_assistant")) {
            var trade = true
            h.talk { trade = keeper(lines) }
            h.expect("$prefix.help")
            h.menu("$prefix.selling", "$prefix.no_thanks")
            h.pick(2)
            h.expect("$prefix.no_thanks")
            h.ends()
            assertFalse(trade)

            h.talk { trade = keeper(lines) }
            h.expect("$prefix.help")
            h.menu("$prefix.selling", "$prefix.no_thanks")
            h.pick(1)
            assertTrue(trade)
        }
    }

    @Test
    fun `Oziach sells only from the buy option, and answers the other three`() {
        val menu = arrayOf("oziach.buy", "oziach.not_friend", "oziach.nice_day", "oziach.maze_key")
        for ((pick, said) in listOf(2 to "not_friend", 3 to "nice_day", 4 to "maze_key")) {
            var trade = true
            h.talk { trade = oziach() }
            h.expect("oziach.greeting")
            h.menu(*menu)
            h.pick(pick)
            h.expect("oziach.$said", "oziach.${said}_reply")
            h.ends()
            assertFalse(trade)
        }
        var trade = false
        h.talk { trade = oziach() }
        h.expect("oziach.greeting")
        h.menu(*menu)
        h.pick(1)
        h.expect("oziach.buy")
        h.ends()
        assertTrue(trade)
    }

    @Test
    fun `Richard explains the capes and asks again, then sells or is turned down`() {
        val menu = arrayOf("richard.what_for", "richard.yes", "richard.no")
        var trade = true
        h.talk { trade = richard() }
        h.expect("richard.greeting")
        h.menu(*menu)
        h.pick(1)
        h.expect("richard.what_for", "richard.explain_1", "richard.explain_2", "richard.want_one")
        h.menu(*menu)
        h.pick(3)
        h.expect("richard.no")
        h.ends()
        assertFalse(trade)

        h.talk { trade = richard() }
        h.expect("richard.greeting")
        h.menu(*menu)
        h.pick(2)
        // No line follows: the shop opens straight from the menu.
        assertEquals(-1, h.player.chatModal)
        assertTrue(trade)
    }
}
