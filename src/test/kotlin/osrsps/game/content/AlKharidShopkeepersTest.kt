package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.content.AlKharidShopkeepers.dommik
import osrsps.game.content.AlKharidShopkeepers.gemTrader
import osrsps.game.content.AlKharidShopkeepers.keeper
import osrsps.game.content.AlKharidShopkeepers.louieLegs
import osrsps.game.content.AlKharidShopkeepers.ranael
import osrsps.game.content.AlKharidShopkeepers.zeke

/** Al Kharid step 3: the shop keepers' talk, with no dialogue file, so every line shows its key. */
class AlKharidShopkeepersTest {
    private val h = DialogueHarness()

    @Test
    fun `a General Store keeper opens the shop, says no thanks, or talks about Ali Morrisane and asks again`() {
        for ((lines, prefix) in listOf(AlKharidShopKeeperLines to "al_kharid_shop_keeper", AlKharidShopAssistantLines to "al_kharid_shop_assistant")) {
            val menu = arrayOf("$prefix.selling", "$prefix.no_thanks", "$prefix.ali")
            var trade = true
            h.talk { trade = keeper(lines) }
            h.expect("$prefix.help")
            h.menu(*menu)
            h.pick(3)
            h.expect("$prefix.ali", "$prefix.ali_1", "$prefix.ali_2", "$prefix.ali_3", "$prefix.what_do", "$prefix.usual", "$prefix.anything")
            h.menu(*menu)
            h.pick(2)
            h.expect("$prefix.no_thanks")
            h.ends()
            assertFalse(trade)

            h.talk { trade = keeper(lines) }
            h.expect("$prefix.help")
            h.menu(*menu)
            h.pick(1)
            assertTrue(trade)
        }
    }

    @Test
    fun `Zeke offers his stock after the dragon scimitar or Ali Morrisane, and only there`() {
        val menu = arrayOf("zeke.trade", "zeke.cloak", "zeke.dragon", "zeke.ali")
        var trade = true
        h.talk { trade = zeke() }
        h.expect("zeke.greeting")
        h.menu(*menu)
        h.pick(1)
        h.expect("zeke.trade", "zeke.scimitars")
        h.ends()
        assertFalse(trade)

        h.talk { trade = zeke() }
        h.expect("zeke.greeting")
        h.menu(*menu)
        h.pick(2)
        h.expect("zeke.cloak", "zeke.thank_you")
        h.ends()
        assertFalse(trade)

        h.talk { trade = zeke() }
        h.expect("zeke.greeting")
        h.menu(*menu)
        h.pick(3)
        h.expect("zeke.dragon", "zeke.a_dragon", "zeke.only_place", "zeke.hmmm", "zeke.perhaps")
        h.menu("zeke.yes_please", "zeke.not_today")
        h.pick(1)
        assertTrue(trade)

        h.talk { trade = zeke() }
        h.expect("zeke.greeting")
        h.menu(*menu)
        h.pick(4)
        h.expect("zeke.ali", "zeke.ali_1", "zeke.ali_2", "zeke.ali_3", "zeke.threats", "zeke.wares", "zeke.perhaps")
        h.menu("zeke.yes_please", "zeke.not_today")
        h.pick(2)
        assertFalse(trade)
    }

    @Test
    fun `Dommik shows his stock, says farewell, or talks about Ali Morrisane and asks again`() {
        val menu = arrayOf("dommik.no_thanks", "dommik.see", "dommik.ali")
        var trade = true
        h.talk { trade = dommik() }
        h.expect("dommik.greeting")
        h.menu(*menu)
        h.pick(3)
        h.expect("dommik.ali", "dommik.ali_1", "dommik.ali_2", "dommik.what_then", "dommik.as_any")
        h.menu(*menu)
        h.pick(1)
        h.expect("dommik.no_thanks", "dommik.fare_well")
        h.ends()
        assertFalse(trade)

        h.talk { trade = dommik() }
        h.expect("dommik.greeting")
        h.menu(*menu)
        h.pick(2)
        h.expect("dommik.see")
        h.next()
        assertTrue(trade)
    }

    @Test
    fun `Louie Legs, Ranael and the Gem trader each open their shop on yes`() {
        var trade = false
        h.talk { trade = louieLegs() }
        h.expect("louie_legs.greeting")
        h.menu("louie_legs.what_got", "louie_legs.no_thank_you")
        h.pick(1)
        h.expect("louie_legs.what_got", "louie_legs.legs")
        h.next()
        assertTrue(trade)
        h.talk { trade = louieLegs() }
        h.expect("louie_legs.greeting")
        h.menu("louie_legs.what_got", "louie_legs.no_thank_you")
        h.pick(2)
        h.expect("louie_legs.no_thank_you")
        h.ends()
        assertFalse(trade)

        h.talk { trade = ranael() }
        h.expect("ranael.greeting")
        h.menu("ranael.yes_please", "ranael.not_my_scene")
        h.pick(1)
        assertTrue(trade)
        h.talk { trade = ranael() }
        h.expect("ranael.greeting")
        h.menu("ranael.yes_please", "ranael.not_my_scene")
        h.pick(2)
        h.expect("ranael.not_my_scene")
        h.ends()
        assertFalse(trade)

        h.talk { trade = gemTrader() }
        h.expect("gem_trader.greeting")
        h.menu("gem_trader.yes_please", "gem_trader.no_thank_you")
        h.pick(1)
        assertTrue(trade)
        h.talk { trade = gemTrader() }
        h.expect("gem_trader.greeting")
        h.menu("gem_trader.yes_please", "gem_trader.no_thank_you")
        h.pick(2)
        h.expect("gem_trader.no_thank_you", "gem_trader.suit_yourself")
        h.ends()
        assertFalse(trade)
    }

    @Test
    fun `every Al Kharid shop keeper has an examine`() {
        for (id in listOf(2817, 2818, 2875, 2879, 2876, 2878, 2874, 3533)) assertEquals("examine.npc_$id", Examines.npc(id)?.key)
    }
}
