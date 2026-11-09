package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.content.SwampTutors.finlay
import osrsps.game.content.SwampTutors.monlum

/** Lumbridge 242 step 2: Monlum and Finlay, the swamp's tutors since 242.1. No dialogue file: every line shows its key. */
class SwampTutorsTest {
    private val h = DialogueHarness()

    /** The items handed over, in order. */
    private val given = mutableListOf<Int>()
    private val give: (Player, Int) -> Unit = { player, obj ->
        given += obj
        player.inventory[player.inventory.contents().size] = InvObj(obj, 1)
    }
    private val monlumMenu = arrayOf("monlum.advice", "monlum.pickaxe", "monlum.leave")
    private val finlayMenu = arrayOf("finlay.advice", "finlay.tools", "finlay.leave")

    @Test
    fun `Monlum's advice shows the mining site icon and comes back to his menu`() {
        h.talk { monlum(give) }
        h.expect("monlum.greeting")
        h.menu(*monlumMenu)
        h.pick(1)
        h.expect(
            "monlum.advice", "monlum.advice_1", "monlum.mine_hint", "monlum.advice_2", "monlum.advice_3", "monlum.guide_hint",
            "monlum.thanks",
        )
        assertEquals(listOf(SwampTutors.MINING_SITE_ICON), h.boxObjs)
        h.menu(*monlumMenu)
        h.pick(3)
        h.expect("monlum.leave")
        h.ends()
    }

    @Test
    fun `Monlum gives a bronze pickaxe once, and a banked pickaxe counts`() {
        h.talk { monlum(give) }
        h.expect("monlum.greeting")
        h.menu(*monlumMenu)
        h.pick(2)
        h.expect("monlum.pickaxe", "monlum.spare", "monlum.gives_pickaxe", "monlum.thanks", "monlum.no_problem")
        assertEquals(listOf(SwampTutors.BRONZE_PICKAXE), given)
        h.menu(*monlumMenu)
        h.pick(2)
        h.expect("monlum.pickaxe", "monlum.has_one", "monlum.so_i_do")
        assertEquals(1, given.size)

        h.player.inventory.clear()
        h.player.bank[0] = InvObj(1275, 1)
        h.menu(*monlumMenu)
        h.pick(2)
        h.expect("monlum.pickaxe", "monlum.has_one", "monlum.so_i_do")
        assertEquals(1, given.size)
    }

    @Test
    fun `with a full backpack Monlum and Finlay give nothing, so a gift can't pile up at the player's feet`() {
        h.player.inventory.add(ObjType(526, "Bones"), h.player.inventory.capacity)
        h.talk { monlum(give) }
        h.expect("monlum.greeting")
        h.menu(*monlumMenu)
        h.pick(2)
        h.expect("monlum.pickaxe", "monlum.no_room")
        h.menu(*monlumMenu)
        h.pick(3)
        h.expect("monlum.leave")
        h.ends()

        h.talk { finlay(give) }
        h.expect("finlay.greeting")
        h.menu(*finlayMenu)
        h.pick(2)
        h.expect("finlay.tools", "finlay.no_room")
        h.menu(*finlayMenu)
        assertTrue(given.isEmpty())
    }

    @Test
    fun `Finlay's advice has Percy the parrot and the fishing spot icon`() {
        h.talk { finlay(give) }
        h.expect("finlay.greeting")
        h.menu(*finlayMenu)
        h.pick(1)
        h.expect(
            "finlay.advice", "finlay.advice_1", "finlay.what_parrot", "finlay.percy", "finlay.sure", "finlay.advice_2",
            "finlay.advice_3", "finlay.spot_hint", "finlay.advice_4", "finlay.advice_5", "finlay.guide_hint", "finlay.thanks",
            "finlay.dont_mention_it",
        )
        assertEquals(listOf(SwampTutors.FISHING_SPOT_ICON), h.boxObjs)
        h.menu(*finlayMenu)
        h.pick(3)
        h.expect("finlay.leave")
        h.ends()
    }

    @Test
    fun `Finlay gives a small net to a player without one, and nothing to one with`() {
        h.talk { finlay(give) }
        h.expect("finlay.greeting")
        h.menu(*finlayMenu)
        h.pick(2)
        h.expect("finlay.tools", "finlay.only_a_net", "finlay.gives_net", "finlay.thanks", "finlay.happy_to_help")
        assertEquals(listOf(SwampTutors.SMALL_FISHING_NET), given)
        h.menu(*finlayMenu)
        h.pick(2)
        h.expect("finlay.tools", "finlay.has_net", "finlay.i_do")
        assertTrue(given.size == 1)
    }
}
