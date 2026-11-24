package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.skill.Mining
import osrsps.game.skill.ShootingStars

/** Shooting stars step 2: Dusuri's talk, with no dialogue file, so every line shows its key; and the celestial ring. */
class DusuriTest {
    private val varTypes = VarTypes(
        6_000,
        mapOf(
            ShootingStars.VARBIT_MINED to VarbitType(STAR_TRANSMIT, 0, 0),
            Dusuri.VARBIT_MET to VarbitType(STAR_TRANSMIT, 1, 1),
        ),
    )

    private fun met(h: DialogueHarness) = h.player.vars.getVarbit(Dusuri.VARBIT_MET)

    @Test
    fun `never touched a star, he explains them, gains Trade, and the player bears it in mind`() {
        val h = DialogueHarness("Dusuri", varTypes)
        var trade = true
        h.talk { trade = with(Dusuri) { talk() } }
        h.expect(
            "dusuri.hello", "dusuri.what_do_you_do", "dusuri.i_specialise", "dusuri.where_from", "dusuri.in_stars",
            "dusuri.shooting_stars", "dusuri.they_fall", "dusuri.happy_to_trade", "dusuri.bear_in_mind", "dusuri.good_luck",
        )
        h.ends()
        assertFalse(trade)
        assertEquals(1, met(h))
    }

    @Test
    fun `touched a star and carrying stardust, he offers to trade at once`() {
        val h = DialogueHarness("Dusuri", varTypes)
        h.player.vars.setVarbit(ShootingStars.VARBIT_MINED, 1)
        h.player.inventory[0] = InvObj(ShootingStars.STARDUST, 10)
        var trade = false
        h.talk { trade = with(Dusuri) { talk() } }
        h.expect("dusuri.hello", "dusuri.what_do_you_do", "dusuri.i_specialise", "dusuri.where_from", "dusuri.in_stars", "dusuri.seen_them", "dusuri.wonderful")
        h.menu("dusuri.have_some", "dusuri.bear_in_mind")
        h.pick(1)
        h.expect("dusuri.have_some", "dusuri.of_course")
        h.ends()
        assertTrue(trade)
        assertEquals(1, met(h))
    }

    @Test
    fun `touched a star with no stardust, no offer and the goodbye`() {
        val h = DialogueHarness("Dusuri", varTypes)
        h.player.vars.setVarbit(ShootingStars.VARBIT_MINED, 1)
        var trade = true
        h.talk { trade = with(Dusuri) { talk() } }
        h.expect(
            "dusuri.hello", "dusuri.what_do_you_do", "dusuri.i_specialise", "dusuri.where_from", "dusuri.in_stars",
            "dusuri.seen_them", "dusuri.wonderful", "dusuri.bear_in_mind", "dusuri.good_luck",
        )
        h.ends()
        assertFalse(trade)
    }

    @Test
    fun `after meeting him, trade, what he sells, about stars, or leave`() {
        val h = DialogueHarness("Dusuri", varTypes)
        h.player.vars.setVarbit(Dusuri.VARBIT_MET, 1)
        val menu = arrayOf("dusuri.can_we_trade", "dusuri.what_do_you_sell", "dusuri.about_stars", "dusuri.im_off")
        var trade = false
        h.talk { trade = with(Dusuri) { talk() } }
        h.expect("dusuri.hello")
        h.menu(*menu)
        h.pick(1)
        h.expect("dusuri.can_we_trade", "dusuri.of_course")
        h.ends()
        assertTrue(trade)

        h.talk { trade = with(Dusuri) { talk() } }
        h.expect("dusuri.hello")
        h.menu(*menu)
        h.pick(2)
        h.expect("dusuri.what_do_you_sell", "dusuri.packs", "dusuri.ring")
        h.ends()
        assertFalse(trade)

        h.talk { trade = with(Dusuri) { talk() } }
        h.expect("dusuri.hello")
        h.menu(*menu)
        h.pick(3)
        h.expect("dusuri.about_stars", "dusuri.not_a_lot", "dusuri.observatory")
        h.ends()

        h.talk { trade = with(Dusuri) { talk() } }
        h.expect("dusuri.hello")
        h.menu(*menu)
        h.pick(4)
        h.expect("dusuri.im_off")
        h.ends()
        assertFalse(trade)
    }

    @Test
    fun `a celestial ring or signet, charged or not, gives an invisible +4 to mining rolls`() {
        val player = Player(1, "Zezima", 3222, 3219, 0, VarTypes.NONE)
        assertEquals(0, Mining.celestialBoost(player))
        for (ring in listOf(25539, 25541, 25543, 25545)) {
            player.equipment[WearPos.RING] = InvObj(ring, 1)
            assertEquals(4, Mining.celestialBoost(player))
        }
        player.equipment[WearPos.RING] = InvObj(1635, 1) // gold_ring
        assertEquals(0, Mining.celestialBoost(player))
    }

    @Test
    fun `a pack fits onto a stack or into a slot, the opened pack's own slot counting`() {
        val inv = Inventory(Inventory.INV, 2)
        val noted = osrsps.game.ObjType(1762, "Soft clay", stackable = true)
        inv[0] = InvObj(Dusuri.SOFT_CLAY_PACK, 1)
        inv[1] = InvObj(995, 5)
        assertFalse(Dusuri.fits(inv, mapOf(noted to 100), freedSlot = false))
        assertTrue(Dusuri.fits(inv, mapOf(noted to 100), freedSlot = true))
        inv[1] = InvObj(1762, Inventory.MAX_STACK - 50)
        assertFalse(Dusuri.fits(inv, mapOf(noted to 100), freedSlot = true))
    }

    private companion object {
        const val STAR_TRANSMIT = 2924
    }
}
