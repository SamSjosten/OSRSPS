package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.Skill

/** Rest of Lumbridge step 8: Barfy Bill's talk, with no dialogue file, so every line shows its key. */
class BarfyBillTest {
    private val h = DialogueHarness()

    private fun start() {
        h.talk { with(BarfyBill) { talk() } }
        h.expect("barfy_bill.hello", "barfy_bill.oh_hello")
        h.menu("barfy_bill.who", "barfy_bill.teach")
    }

    /** Asks to be taught at Woodcutting level [level] and checks what follows is [keys]. */
    private fun taught(level: Int, vararg keys: String) {
        h.player.skills.setLevel(Skill.WOODCUTTING, level)
        start()
        h.pick(2)
        h.expect("barfy_bill.teach_me", *keys)
        h.ends()
    }

    @Test
    fun `who he is ends in his offer, and no lets the player go`() {
        val who = arrayOf(
            "barfy_bill.who", "barfy_bill.name", "barfy_bill.ex_captain", "barfy_bill.ship", "barfy_bill.why_not",
            "barfy_bill.sea_sick", "barfy_bill.saved_money", "barfy_bill.now", "barfy_bill.fire", "barfy_bill.canoeing",
            "barfy_bill.river_sick", "barfy_bill.know_how",
        )
        start()
        h.pick(1)
        h.expect(*who)
        h.menu("barfy_bill.yes", "barfy_bill.no")
        h.pick(2)
        h.expect("barfy_bill.no_thanks")
        h.ends()

        // Yes goes on to the teaching, here below level 12.
        start()
        h.pick(1)
        h.expect(*who)
        h.menu("barfy_bill.yes", "barfy_bill.no")
        h.pick(1)
        h.expect("barfy_bill.teach_me", "barfy_bill.no_skill", "barfy_bill.level_12", "barfy_bill.quicker")
        h.ends()
    }

    @Test
    fun `what he teaches goes by the Woodcutting level, one band for each canoe`() {
        assertEquals(1, h.player.skills.baseLevel(Skill.WOODCUTTING))
        taught(11, "barfy_bill.no_skill", "barfy_bill.level_12", "barfy_bill.quicker")
        val shape = arrayOf("barfy_bill.chop", "barfy_bill.shape")
        val log = arrayOf(*shape, "barfy_bill.no_talent", "barfy_bill.mean", "barfy_bill.callouses", "barfy_bill.log")
        taught(12, *log)
        taught(26, *log)
        val dugout = arrayOf(*shape, "barfy_bill.dugout", "barfy_bill.how_far", "barfy_bill.two_stops")
        taught(27, *dugout)
        taught(41, *dugout)
        val stable = arrayOf(*shape, "barfy_bill.stable", "barfy_bill.any_place", "barfy_bill.wilderness", "barfy_bill.waka_man", "barfy_bill.hellish")
        taught(42, *stable)
        taught(56, *stable)
        val waka = arrayOf(*shape, "barfy_bill.axe", "barfy_bill.waka", "barfy_bill.evil", "barfy_bill.warning")
        taught(57, *waka)
        taught(99, *waka)
    }

    @Test
    fun `Barfy Bill, the goblins and the giant spider have examines, the eight goblins sharing one`() {
        assertEquals("examine.npc_1326", Examines.npc(BarfyBill.ID)?.key)
        for (id in 3029..3036) assertEquals("examine.npc_3029", Examines.npc(id)?.key, "goblin $id")
        assertEquals("examine.npc_3017", Examines.npc(3017)?.key)
    }
}
