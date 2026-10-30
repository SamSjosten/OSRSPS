package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.NpcType
import osrsps.game.ObjType
import osrsps.game.content.ShantayPass.buyPass
import osrsps.game.content.ShantayPass.cellDoor
import osrsps.game.content.ShantayPass.gateGuard
import osrsps.game.content.ShantayPass.gateSouth
import osrsps.game.content.ShantayPass.shantay
import osrsps.game.shop.ShopScreen

/** Al Kharid step 8: Shantay's talk, the jail and its fine, the pass, and the gate guard. */
class ShantayPassTest {
    private val h = DialogueHarness()
    private val coins = ObjType(ShopScreen.COINS, "Coins", stackable = true)
    private val guard = NpcType(ShantayPass.GATE_GUARD, "Shantay Guard")
    private val shantayType = NpcType(ShantayPass.SHANTAY, "Shantay")
    private val done = mutableListOf<String>()
    private val actions = object : ShantayActions {
        override fun openShop() { done += "shop" }
        override fun give(obj: Int) { done += "give $obj" }
        override fun jail() { done += "jail" }
        override fun release() { done += "release" }
        override fun toPrison() { done += "prison" }
        override fun openBank() { done += "bank" }
    }
    private val mainMenu = arrayOf("shantay.what_is", "shantay.sell", "shantay.must_go", "shantay.buy_pass")

    @Test
    fun `the first talk has his welcome, later ones greet the player again`() {
        h.talk { shantay(actions) }
        h.expect("shantay.hello", "shantay.see_you")
        h.menu(*mainMenu)
        h.pick(3)
        h.expect("shantay.must_go", "shantay.so_long")
        h.ends()

        h.talk { shantay(actions) }
        h.expect("shantay.hello_again")
        h.menu(*mainMenu)
        h.pick(2)
        h.expect("shantay.sell", "shantay.absolutely")
        h.next()
        assertEquals(listOf("shop"), done)
    }

    @Test
    fun `a pass costs five coins`() {
        h.talk { buyPass(actions) }
        h.expect("shantay.buy_pass", "shantay.no_coins")
        h.ends()

        h.player.inventory.add(coins, 7)
        h.talk { buyPass(actions) }
        h.expect("shantay.buy_pass", "shantay.bought")
        h.ends()
        assertEquals(listOf("give ${ShantayPass.PASS}"), done)
        assertEquals(2L, h.player.inventory.count(ShopScreen.COINS))
    }

    @Test
    fun `the outlaw is jailed, and pays the fine to be let out`() {
        h.player.charges[ShantayPass.TALKED] = 1
        h.player.inventory.add(coins, 5)
        h.talk { shantay(actions) }
        h.expect("shantay.hello_again")
        h.menu(*mainMenu)
        h.pick(1)
        h.expect("shantay.what_is", "shantay.this_is", "shantay.my_men")
        h.menu("shantay.outlaw", "shantay.novice", "shantay.adventurer")
        h.pick(1)
        h.expect("shantay.outlaw", "shantay.very_funny", "shantay.arrest", "shantay.arrested", "shantay.pay_now")
        h.menu("shantay.yes_okay", "shantay.not_having")
        h.pick(1)
        h.expect("shantay.yes_okay", "shantay.senses", "shantay.hands_over", "shantay.keep_peace", "shantay.unlocks")
        h.ends()
        assertEquals(listOf("jail", "release"), done)
        assertEquals(0L, h.player.inventory.count(ShopScreen.COINS))
    }

    @Test
    fun `refusing twice at the cell door sends the player to Port Sarim`() {
        h.talk { cellDoor(shantayType, actions) }
        h.expect("shantay.locked", "shantay.walks_over", "shantay.jail_pay")
        h.menu("shantay.yes_okay", "shantay.not_having")
        h.pick(2)
        h.expect("shantay.not_having", "shantay.choice", "shantay.will_you")
        h.menu("shantay.yes_okay", "shantay.do_your_worst")
        h.pick(2)
        h.expect("shantay.do_your_worst", "shantay.transported", "shantay.lesson", "shantay.prison")
        h.ends()
        assertEquals(listOf("prison"), done)
    }

    @Test
    fun `with coins only in the bank, the fine can be paid from it`() {
        h.player.bank.add(coins, 100)
        h.talk { cellDoor(shantayType, actions) }
        h.expect("shantay.locked", "shantay.walks_over", "shantay.jail_pay")
        h.menu("shantay.yes_okay", "shantay.not_having")
        h.pick(1)
        h.expect("shantay.yes_okay", "shantay.senses", "shantay.no_cash", "shantay.your_bank", "shantay.which")
        h.menu("shantay.pay_fine", "shantay.not_having")
        h.pick(1)
        h.expect("shantay.pay_fine", "shantay.need_bank")
        h.next()
        assertEquals(listOf("bank"), done)
    }

    @Test
    fun `the gate guard wants a pass, and with one the desert is closed for now`() {
        h.talk { gateGuard { gateSouth(guard) } }
        h.expect("shantay_guard.hello")
        h.menu("shantay_guard.desert", "shantay_guard.nothing")
        h.pick(1)
        h.expect("shantay_guard.desert", "shantay_guard.need_pass")
        h.ends()

        h.player.inventory.add(ObjType(ShantayPass.PASS, "Shantay pass"), 1)
        h.talk { gateSouth(guard) }
        h.expect("shantay_gate.desert_closed")
        h.ends()
        assertEquals(1L, h.player.inventory.count(ShantayPass.PASS))
    }

    @Test
    fun `Pick-lock's chance rises over four attempts and with level`() {
        assertEquals(0, ShantayPass.pickChance(1, 1))
        assertEquals(82, ShantayPass.pickChance(1, 4))
        assertEquals(82, ShantayPass.pickChance(1, 9))
        assertEquals(39, ShantayPass.pickChance(99, 1))
        assertEquals(127, ShantayPass.pickChance(99, 4))
        assertTrue(ShantayPass.pickChance(50, 2) in 20..60)
    }
}
