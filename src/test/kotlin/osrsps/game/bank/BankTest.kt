package osrsps.game.bank

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes

class BankTest {
    private val types = ObjTypes(
        arrayOfNulls<ObjType>(PLACEHOLDER + 1).also {
            it[LOGS] = ObjType(LOGS, "Logs", certlink = LOGS_NOTE, placeholderlink = PLACEHOLDER)
            it[LOGS_NOTE] = ObjType(LOGS_NOTE, "Logs", stackable = true, certlink = LOGS, certtemplate = 799)
            it[PLACEHOLDER] = ObjType(PLACEHOLDER, "Logs", placeholderlink = LOGS, placeholdertemplate = 14401)
            it[COINS] = ObjType(COINS, "Coins", stackable = true)
            it[ORB] = ObjType(ORB, "Orb", params = mapOf(Bank.PARAM_NO_BANK to 1))
            for (id in SWORD until SWORD + 6) it[id] = ObjType(id, "Sword")
        },
    )
    private val bank = Bank(types)
    private val vault = Inventory(Inventory.BANK, Inventory.BANK_CAPACITY)
    private val pack = Inventory(Inventory.INV, Inventory.INV_CAPACITY)
    private val tabs = Tabs()

    /** Tab sizes and the tab being viewed, as the varbits keep them. */
    private class Tabs : BankTabs {
        val sizes = IntArray(Bank.TABS + 1)
        override fun get(tab: Int) = sizes[tab]
        override fun set(tab: Int, size: Int) {
            sizes[tab] = size
        }
        override var current = 0
    }

    private fun ids() = vault.contents().map { it.second.id }

    private fun sizes() = (1..Bank.TABS).map { tabs[it] }.takeWhile { it > 0 }

    private fun Inventory.fill(id: Int, count: Int) = types[id]!!.let { type -> add(type, count) }

    @Test
    fun `a deposit stacks every item, taking more from other slots, and notes go in as their item`() {
        pack.fill(LOGS, 3)
        pack.fill(LOGS_NOTE, 10)
        assertEquals(Bank.Deposit.Done(2), bank.deposit(vault, tabs, pack, 1, 2))
        assertEquals(InvObj(LOGS, 2), vault[0])
        // Slot 1 went first, then slot 0.
        assertNull(pack[0])
        assertNull(pack[1])
        assertEquals(InvObj(LOGS, 1), pack[2])
        assertEquals(Bank.Deposit.Done(10), bank.deposit(vault, tabs, pack, 3, Int.MAX_VALUE))
        assertEquals(InvObj(LOGS, 12), vault[0])
        assertEquals(1, vault.capacity - vault.freeSlots)
    }

    @Test
    fun `a deposit of what the bank refuses, into a full bank, or onto a full stack doesn't happen`() {
        pack.fill(ORB, 1)
        assertEquals(Bank.Deposit.NotBankable, bank.deposit(vault, tabs, pack, 0, 1))
        pack.fill(COINS, 5)
        vault[0] = InvObj(COINS, Inventory.MAX_STACK - 2)
        assertEquals(Bank.Deposit.Partly(2), bank.deposit(vault, tabs, pack, 1, 5))
        assertEquals(InvObj(COINS, 3), pack[1])
        assertEquals(Bank.Deposit.FullStack, bank.deposit(vault, tabs, pack, 1, 5))
        for (slot in 1 until Bank.DEFAULT_CAPACITY) vault[slot] = InvObj(SWORD, 1)
        pack.fill(LOGS, 1)
        assertEquals(Bank.Deposit.BankFull, bank.deposit(vault, tabs, pack, 2, 1))
    }

    @Test
    fun `a withdrawal takes one item a slot, or a stack of notes, and the bank closes up behind it`() {
        vault[0] = InvObj(SWORD, 2)
        vault[1] = InvObj(LOGS, 30)
        vault[2] = InvObj(COINS, 100)
        assertEquals(Bank.Withdrawal(2), bank.withdraw(vault, tabs, pack, 0, 5, asNote = false, placeholder = false))
        assertEquals(InvObj(LOGS, 30), vault[0])
        assertEquals(InvObj(COINS, 100), vault[1])
        assertNull(vault[2])
        // 26 free slots for 30 logs.
        assertEquals(Bank.Withdrawal(26, short = true), bank.withdraw(vault, tabs, pack, 0, 30, asNote = false, placeholder = false))
        assertEquals(InvObj(LOGS, 4), vault[0])
        assertEquals(Bank.Withdrawal.NoSpace, bank.withdraw(vault, tabs, pack, 0, 1, asNote = false, placeholder = false))
    }

    @Test
    fun `notes come out as one stack, and an item without a note comes out as itself`() {
        vault[0] = InvObj(LOGS, 30)
        vault[1] = InvObj(COINS, 100)
        assertEquals(Bank.Withdrawal(30), bank.withdraw(vault, tabs, pack, 0, 30, asNote = true, placeholder = false))
        assertEquals(InvObj(LOGS_NOTE, 30), pack[0])
        assertEquals(Bank.Withdrawal(100, notNoted = true), bank.withdraw(vault, tabs, pack, 0, 100, asNote = true, placeholder = false))
        assertEquals(InvObj(COINS, 100), pack[1])
        assertTrue(vault.isEmpty())
    }

    @Test
    fun `a placeholder keeps the slot, takes the next deposit, and can be released`() {
        vault[0] = InvObj(LOGS, 1)
        vault[1] = InvObj(COINS, 5)
        bank.withdraw(vault, tabs, pack, 0, 1, asNote = false, placeholder = true)
        assertEquals(PLACEHOLDER, vault[0]?.id)
        assertEquals(Bank.Withdrawal.Nothing, bank.withdraw(vault, tabs, pack, 0, 1, asNote = false, placeholder = false))
        assertEquals(Bank.Deposit.Done(1), bank.deposit(vault, tabs, pack, 0, 1))
        assertEquals(InvObj(LOGS, 1), vault[0])
        bank.withdraw(vault, tabs, pack, 0, 1, asNote = false, placeholder = true)
        assertFalse(bank.discard(vault, tabs, 1))
        assertTrue(bank.discard(vault, tabs, 0))
        assertEquals(InvObj(COINS, 5), vault[0])
        assertEquals(1, vault.capacity - vault.freeSlots)
    }

    @Test
    fun `a drag swaps, or with insert moves the ones between along, and past the end goes to the end`() {
        for (slot in 0 until 4) vault[slot] = InvObj(SWORD + slot, 1)
        assertTrue(bank.move(vault, tabs, 0, 2, insert = false))
        assertEquals(listOf(SWORD + 2, SWORD + 1, SWORD, SWORD + 3), (0 until 4).map { vault[it]?.id })
        assertTrue(bank.move(vault, tabs, 0, 3, insert = true))
        assertEquals(listOf(SWORD + 1, SWORD, SWORD + 3, SWORD + 2), (0 until 4).map { vault[it]?.id })
        assertTrue(bank.move(vault, tabs, 3, 0, insert = true))
        assertEquals(listOf(SWORD + 2, SWORD + 1, SWORD, SWORD + 3), (0 until 4).map { vault[it]?.id })
        assertTrue(bank.move(vault, tabs, 0, 50, insert = false))
        assertEquals(listOf(SWORD + 1, SWORD, SWORD + 3, SWORD + 2), (0 until 4).map { vault[it]?.id })
        assertFalse(bank.move(vault, tabs, 4, 0, insert = false))
    }

    @Test
    fun `deposit all takes what the bank will hold`() {
        pack.fill(LOGS, 2)
        pack.fill(ORB, 1)
        pack.fill(COINS, 50)
        val result = bank.depositAll(vault, tabs, pack)
        assertEquals(2, result.deposited)
        assertEquals(1, result.notBankable)
        assertEquals(listOf(InvObj(LOGS, 2), InvObj(COINS, 50)), listOf(vault[0], vault[1]))
        assertEquals(listOf(2 to InvObj(ORB, 1)), pack.contents())
    }

    /** Swords SWORD to SWORD + n - 1 in slots 0 up, with [sizes] as tabs 1 up and the rest the main tab. */
    private fun swords(n: Int, vararg sizes: Int) {
        for (slot in 0 until n) vault[slot] = InvObj(SWORD + slot, 1)
        sizes.forEachIndexed { i, size -> tabs[i + 1] = size }
    }

    @Test
    fun `a new item goes at the end of the tab being viewed, or of the main tab when viewing all`() {
        swords(3, 1, 1)
        pack.fill(COINS, 5)
        pack.fill(LOGS, 1)
        tabs.current = 1
        bank.deposit(vault, tabs, pack, 0, 5)
        assertEquals(listOf(SWORD, COINS, SWORD + 1, SWORD + 2), ids())
        assertEquals(listOf(2, 1), sizes())
        tabs.current = 0
        bank.deposit(vault, tabs, pack, 1, 1)
        assertEquals(listOf(SWORD, COINS, SWORD + 1, SWORD + 2, LOGS), ids())
        assertEquals(listOf(2, 1), sizes())
    }

    @Test
    fun `taking a tab's last item takes the tab away, and the view follows its tab`() {
        swords(4, 1, 1, 1)
        tabs.current = 3
        bank.withdraw(vault, tabs, pack, 0, 1, asNote = false, placeholder = false)
        assertEquals(listOf(SWORD + 1, SWORD + 2, SWORD + 3), ids())
        assertEquals(listOf(1, 1), sizes())
        assertEquals(2, tabs.current)
        bank.withdraw(vault, tabs, pack, 1, 1, asNote = false, placeholder = false)
        assertEquals(listOf(1), sizes())
        assertEquals(0, tabs.current)
    }

    @Test
    fun `dragging onto a tab or past its end moves the item to that tab's end, and onto the next tab makes a new one`() {
        swords(5, 2)
        assertTrue(bank.moveToTab(vault, tabs, 4, 1))
        assertEquals(listOf(SWORD, SWORD + 1, SWORD + 4, SWORD + 2, SWORD + 3), ids())
        assertEquals(listOf(3), sizes())
        assertFalse(bank.moveToTab(vault, tabs, 0, 1))
        assertTrue(bank.moveToTab(vault, tabs, 0, 2))
        assertEquals(listOf(SWORD + 1, SWORD + 4, SWORD, SWORD + 2, SWORD + 3), ids())
        assertEquals(listOf(2, 1), sizes())
        // A tab past the next free one is the next free one.
        assertTrue(bank.moveToTab(vault, tabs, 4, 9))
        assertEquals(listOf(2, 1, 1), sizes())
        assertEquals(listOf(SWORD + 1, SWORD + 4, SWORD, SWORD + 3, SWORD + 2), ids())
    }

    @Test
    fun `an insert into another tab goes in front of the item dragged onto, and a swap leaves the tabs as they are`() {
        swords(4, 2, 2)
        assertTrue(bank.move(vault, tabs, 0, 3, insert = true))
        assertEquals(listOf(SWORD + 1, SWORD + 2, SWORD, SWORD + 3), ids())
        assertEquals(listOf(1, 3), sizes())
        assertTrue(bank.move(vault, tabs, 3, 0, insert = true))
        assertEquals(listOf(SWORD + 3, SWORD + 1, SWORD + 2, SWORD), ids())
        assertEquals(listOf(2, 2), sizes())
        assertTrue(bank.move(vault, tabs, 0, 3, insert = false))
        assertEquals(listOf(SWORD, SWORD + 1, SWORD + 2, SWORD + 3), ids())
        assertEquals(listOf(2, 2), sizes())
    }

    @Test
    fun `a tab dragged onto another takes its place, and a collapsed tab goes to the end of the main tab`() {
        swords(6, 1, 2, 1)
        tabs.current = 1
        assertTrue(bank.moveTab(vault, tabs, 1, 3))
        assertEquals(listOf(SWORD + 1, SWORD + 2, SWORD + 3, SWORD, SWORD + 4, SWORD + 5), ids())
        assertEquals(listOf(2, 1, 1), sizes())
        assertEquals(3, tabs.current)
        assertFalse(bank.moveTab(vault, tabs, 1, 4))
        assertTrue(bank.collapse(vault, tabs, 1))
        assertEquals(listOf(SWORD + 3, SWORD, SWORD + 4, SWORD + 5, SWORD + 1, SWORD + 2), ids())
        assertEquals(listOf(1, 1), sizes())
        assertEquals(2, tabs.current)
    }

    @Test
    fun `the view follows its own tab when other tabs hold the same items`() {
        // A filler in each of tabs 1 to 3, viewing tab 3: collapsing tab 1 leaves it as tab 2.
        for (slot in 0 until 3) vault[slot] = InvObj(Bank.FILLER, 1)
        for (tab in 1..3) tabs[tab] = 1
        tabs.current = 3
        assertTrue(bank.collapse(vault, tabs, 1))
        assertEquals(listOf(1, 1), sizes())
        assertEquals(2, tabs.current)
    }

    @Test
    fun `placeholders go by the tab or all at once, and fillers fill the free space and clear`() {
        vault[0] = InvObj(PLACEHOLDER, 1)
        vault[1] = InvObj(SWORD, 1)
        vault[2] = InvObj(PLACEHOLDER, 1)
        tabs[1] = 1
        assertEquals(1, bank.releasePlaceholders(vault, tabs, 0))
        assertEquals(listOf(PLACEHOLDER, SWORD), ids())
        assertEquals(0, bank.releasePlaceholders(vault, tabs, 0))
        assertEquals(1, bank.releasePlaceholders(vault, tabs, null))
        assertEquals(listOf(SWORD), ids())
        assertEquals(emptyList(), sizes())

        assertEquals(10, bank.addFillers(vault, tabs, 10))
        assertEquals(Bank.DEFAULT_CAPACITY - 11, bank.addFillers(vault, tabs, Int.MAX_VALUE))
        assertEquals(0, bank.addFillers(vault, tabs, 1))
        assertEquals(Bank.Withdrawal.Nothing, bank.withdraw(vault, tabs, pack, 1, 1, asNote = false, placeholder = false))
        assertTrue(bank.discard(vault, tabs, 1))
        assertFalse(bank.discard(vault, tabs, 0))
        assertEquals(Bank.DEFAULT_CAPACITY - 2, bank.clearFillers(vault, tabs))
        assertEquals(listOf(SWORD), ids())
    }

    @Test
    fun `a gap left in a save is closed by the next change, and an item past it comes out once`() {
        vault[0] = InvObj(SWORD, 1)
        vault[5] = InvObj(COINS, 100)
        assertEquals(Bank.Withdrawal(100), bank.withdraw(vault, tabs, pack, 5, 100, asNote = false, placeholder = false))
        assertEquals(listOf(0 to InvObj(SWORD, 1)), vault.contents())
        vault[3] = InvObj(LOGS, 2)
        assertTrue(bank.move(vault, tabs, 3, 0, insert = true))
        assertEquals(listOf(0 to InvObj(LOGS, 2), 1 to InvObj(SWORD, 1)), vault.contents())
    }

    private companion object {
        const val LOGS = 1511
        const val LOGS_NOTE = 1512
        const val COINS = 995
        const val ORB = 50
        const val SWORD = 1277
        const val PLACEHOLDER = 14401
    }
}
