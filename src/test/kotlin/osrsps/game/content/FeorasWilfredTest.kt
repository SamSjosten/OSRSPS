package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.content.Feoras.talk
import osrsps.game.content.Wilfred.talk

/** Lumbridge 242 step 2: Feoras and Wilfred, the Smithing apprentice and Woodsman tutor renamed in 242.1. Keys show. */
class FeorasWilfredTest {
    private val h = DialogueHarness()
    private val given = mutableListOf<Int>()
    private val give: (Player, Int) -> Unit = { player, obj ->
        given += obj
        player.inventory[player.inventory.contents().size] = InvObj(obj, 1)
    }
    private val feorasMenu = arrayOf("feoras.for_sale", "feoras.advice", "feoras.hammer", "feoras.nothing")
    private val wilfredMenu = arrayOf("wilfred.chopping", "wilfred.fires", "wilfred.tools", "wilfred.cape", "wilfred.leave")

    @Test
    fun `asking Feoras what he sells ends the talk by opening the shop`() {
        var trade = false
        h.talk { trade = with(Feoras) { talk(give) } }
        h.expect("feoras.greeting")
        h.menu(*feorasMenu)
        h.pick(1)
        h.expect("feoras.for_sale", "feoras.take_a_look")
        h.ends()
        assertTrue(trade)
    }

    @Test
    fun `Feoras's advice shows the mine, furnace and anvil icons and comes back to his menu`() {
        h.talk { with(Feoras) { talk(give) } }
        h.expect("feoras.greeting")
        h.menu(*feorasMenu)
        h.pick(2)
        h.expect(
            "feoras.advice", "feoras.advice_1", "feoras.advice_2", "feoras.mine_hint", "feoras.advice_3", "feoras.furnace_hint",
            "feoras.advice_4", "feoras.anvil_hint", "feoras.advice_5", "feoras.advice_6", "feoras.advice_7", "feoras.guide_hint",
            "feoras.thanks", "feoras.anything_else",
        )
        assertEquals(listOf(Feoras.MINING_SITE_ICON, Feoras.FURNACE_ICON, Feoras.ANVIL_ICON), h.boxObjs)
        h.menu(*feorasMenu)
    }

    @Test
    fun `Feoras gives a hammer once, not without room, and the talk ends after it`() {
        var trade = true
        h.talk { trade = with(Feoras) { talk(give) } }
        h.expect("feoras.greeting")
        h.menu(*feorasMenu)
        h.pick(3)
        h.expect("feoras.hammer", "feoras.spare", "feoras.gives_hammer")
        h.ends()
        assertFalse(trade)
        assertEquals(listOf(Feoras.HAMMER_OBJ), given)

        h.talk { with(Feoras) { talk(give) } }
        h.expect("feoras.greeting")
        h.menu(*feorasMenu)
        h.pick(3)
        h.expect("feoras.hammer", "feoras.has_hammer")
        h.ends()

        h.player.inventory.clear()
        h.player.inventory.add(ObjType(526, "Bones"), h.player.inventory.capacity)
        h.talk { with(Feoras) { talk(give) } }
        h.expect("feoras.greeting")
        h.menu(*feorasMenu)
        h.pick(3)
        h.expect("feoras.hammer", "feoras.no_room")
        h.ends()
        assertEquals(1, given.size)
    }

    @Test
    fun `Wilfred's chopping and fires advice come back to his menu`() {
        h.talk { with(Wilfred) { talk(give) } }
        h.expect("wilfred.greeting")
        h.menu(*wilfredMenu)
        h.pick(1)
        h.expect(
            "wilfred.chopping", "wilfred.chop_1", "wilfred.chop_2", "wilfred.chop_3", "wilfred.tree_hint", "wilfred.thats_it",
            "wilfred.chop_4", "wilfred.wc_guide_hint",
        )
        assertEquals(listOf(Wilfred.TREE_ICON), h.boxObjs)
        h.menu(*wilfredMenu)
        h.pick(2)
        h.expect("wilfred.fires", "wilfred.fires_1", "wilfred.fires_2", "wilfred.fm_guide_hint")
        h.menu(*wilfredMenu)
        h.pick(5)
        h.expect("wilfred.leave", "wilfred.leave_reply")
        h.ends()
    }

    @Test
    fun `Wilfred offers both tools, then only the missing one, then nothing`() {
        h.talk { with(Wilfred) { talk(give) } }
        h.expect("wilfred.greeting")
        h.menu(*wilfredMenu)
        h.pick(3)
        h.expect("wilfred.tools", "wilfred.offer")
        h.menu("wilfred.axe_please", "wilfred.tinderbox_please", "wilfred.both_please", "wilfred.neither")
        h.pick(1)
        h.expect("wilfred.axe_please", "wilfred.gives_axe", "wilfred.thanks", "wilfred.dont_mention_it")
        h.menu(*wilfredMenu)
        h.pick(3)
        h.expect("wilfred.tools", "wilfred.has_axe")
        h.menu("wilfred.yes_please", "wilfred.no_thanks")
        h.pick(1)
        h.expect("wilfred.yes_please", "wilfred.gives_tinderbox", "wilfred.thanks", "wilfred.dont_mention_it")
        assertEquals(listOf(Wilfred.BRONZE_AXE, Wilfred.TINDERBOX), given)
        h.menu(*wilfredMenu)
        h.pick(3)
        h.expect("wilfred.tools", "wilfred.has_both")
        h.menu(*wilfredMenu)
        assertEquals(2, given.size)
    }

    @Test
    fun `Wilfred counts banked tools, and gives both at once to a player with neither`() {
        h.player.bank[0] = InvObj(Wilfred.TINDERBOX, 1)
        h.talk { with(Wilfred) { talk(give) } }
        h.expect("wilfred.greeting")
        h.menu(*wilfredMenu)
        h.pick(3)
        h.expect("wilfred.tools", "wilfred.has_tinderbox")
        h.menu("wilfred.yes_please", "wilfred.no_thanks")
        h.pick(2)
        h.expect("wilfred.no_thanks", "wilfred.fair_enough")
        h.menu(*wilfredMenu)
        assertTrue(given.isEmpty())

        h.player.bank.clear()
        h.pick(3)
        h.expect("wilfred.tools", "wilfred.offer")
        h.menu("wilfred.axe_please", "wilfred.tinderbox_please", "wilfred.both_please", "wilfred.neither")
        h.pick(3)
        h.expect("wilfred.both_please", "wilfred.gives_both", "wilfred.thanks", "wilfred.dont_mention_it")
        assertEquals(listOf(Wilfred.BRONZE_AXE, Wilfred.TINDERBOX), given)
        assertEquals(listOf(Wilfred.BRONZE_AXE, Wilfred.TINDERBOX), h.boxObjs)
    }

    @Test
    fun `without room Wilfred gives nothing, and both tools need two free slots`() {
        h.player.inventory.add(ObjType(526, "Bones"), h.player.inventory.capacity - 1)
        h.talk { with(Wilfred) { talk(give) } }
        h.expect("wilfred.greeting")
        h.menu(*wilfredMenu)
        h.pick(3)
        h.expect("wilfred.tools", "wilfred.offer")
        h.menu("wilfred.axe_please", "wilfred.tinderbox_please", "wilfred.both_please", "wilfred.neither")
        h.pick(3)
        h.expect("wilfred.both_please", "wilfred.no_room_both")
        h.menu(*wilfredMenu)
        assertTrue(given.isEmpty())

        h.player.inventory.add(ObjType(526, "Bones"), 1)
        h.pick(3)
        h.expect("wilfred.tools", "wilfred.offer")
        h.menu("wilfred.axe_please", "wilfred.tinderbox_please", "wilfred.both_please", "wilfred.neither")
        h.pick(1)
        h.expect("wilfred.axe_please", "wilfred.no_room")
        h.menu(*wilfredMenu)
        assertTrue(given.isEmpty())
    }

    @Test
    fun `at 99 Woodcutting Wilfred sells the cape and hood for 99,000 coins`() {
        h.player.skills.setLevel(Skill.WOODCUTTING, 99)
        h.player.inventory.add(ObjType(Wilfred.COINS, "Coins", stackable = true), 99_500)
        h.talk { with(Wilfred) { talk(give) } }
        h.expect("wilfred.greeting")
        h.menu(*wilfredMenu)
        h.pick(4)
        h.expect("wilfred.cape", "wilfred.cape_what")
        h.menu("wilfred.buy_cape", "wilfred.hood")
        h.pick(1)
        h.expect("wilfred.buy_cape", "wilfred.price")
        h.menu("wilfred.accept", "wilfred.too_much")
        h.pick(1)
        h.expect("wilfred.accept", "wilfred.sold")
        assertEquals(listOf(Wilfred.WOODCUTTING_CAPE, Wilfred.WOODCUTTING_HOOD), given)
        assertEquals(500, h.player.inventory.count(Wilfred.COINS))
        h.menu(*wilfredMenu)
    }
}
