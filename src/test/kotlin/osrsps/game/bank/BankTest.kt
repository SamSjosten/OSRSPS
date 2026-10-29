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
            it[SWORD] = ObjType(SWORD, "Sword")
        },
    )
    private val bank = Bank(types)
    private val vault = Inventory(Inventory.BANK, Inventory.BANK_CAPACITY)
    private val pack = Inventory(Inventory.INV, Inventory.INV_CAPACITY)

    private fun Inventory.fill(id: Int, count: Int) = types[id]!!.let { type -> add(type, count) }

    @Test
    fun `a deposit stacks every item, taking more from other slots, and notes go in as their item`() {
        pack.fill(LOGS, 3)
        pack.fill(LOGS_NOTE, 10)
        assertEquals(Bank.Deposit.Done(2), bank.deposit(vault, pack, 1, 2))
        assertEquals(InvObj(LOGS, 2), vault[0])
        // Slot 1 went first, then slot 0.
        assertNull(pack[0])
        assertNull(pack[1])
        assertEquals(InvObj(LOGS, 1), pack[2])
        assertEquals(Bank.Deposit.Done(10), bank.deposit(vault, pack, 3, Int.MAX_VALUE))
        assertEquals(InvObj(LOGS, 12), vault[0])
        assertEquals(1, bank.size(vault))
    }

    @Test
    fun `a deposit of what the bank refuses, into a full bank, or onto a full stack doesn't happen`() {
        pack.fill(ORB, 1)
        assertEquals(Bank.Deposit.NotBankable, bank.deposit(vault, pack, 0, 1))
        pack.fill(COINS, 5)
        vault[0] = InvObj(COINS, Inventory.MAX_STACK - 2)
        assertEquals(Bank.Deposit.Partly(2), bank.deposit(vault, pack, 1, 5))
        assertEquals(InvObj(COINS, 3), pack[1])
        assertEquals(Bank.Deposit.FullStack, bank.deposit(vault, pack, 1, 5))
        for (slot in 1 until Bank.DEFAULT_CAPACITY) vault[slot] = InvObj(SWORD, 1)
        pack.fill(LOGS, 1)
        assertEquals(Bank.Deposit.BankFull, bank.deposit(vault, pack, 2, 1))
    }

    @Test
    fun `a withdrawal takes one item a slot, or a stack of notes, and the bank closes up behind it`() {
        vault[0] = InvObj(SWORD, 2)
        vault[1] = InvObj(LOGS, 30)
        vault[2] = InvObj(COINS, 100)
        assertEquals(Bank.Withdrawal(2), bank.withdraw(vault, pack, 0, 5, asNote = false, placeholder = false))
        assertEquals(InvObj(LOGS, 30), vault[0])
        assertEquals(InvObj(COINS, 100), vault[1])
        assertNull(vault[2])
        // 26 free slots for 30 logs.
        assertEquals(Bank.Withdrawal(26, short = true), bank.withdraw(vault, pack, 0, 30, asNote = false, placeholder = false))
        assertEquals(InvObj(LOGS, 4), vault[0])
        assertEquals(Bank.Withdrawal.NoSpace, bank.withdraw(vault, pack, 0, 1, asNote = false, placeholder = false))
    }

    @Test
    fun `notes come out as one stack, and an item without a note comes out as itself`() {
        vault[0] = InvObj(LOGS, 30)
        vault[1] = InvObj(COINS, 100)
        assertEquals(Bank.Withdrawal(30), bank.withdraw(vault, pack, 0, 30, asNote = true, placeholder = false))
        assertEquals(InvObj(LOGS_NOTE, 30), pack[0])
        assertEquals(Bank.Withdrawal(100, notNoted = true), bank.withdraw(vault, pack, 0, 100, asNote = true, placeholder = false))
        assertEquals(InvObj(COINS, 100), pack[1])
        assertTrue(vault.isEmpty())
    }

    @Test
    fun `a placeholder keeps the slot, takes the next deposit, and can be released`() {
        vault[0] = InvObj(LOGS, 1)
        vault[1] = InvObj(COINS, 5)
        bank.withdraw(vault, pack, 0, 1, asNote = false, placeholder = true)
        assertEquals(PLACEHOLDER, vault[0]?.id)
        assertEquals(Bank.Withdrawal.Nothing, bank.withdraw(vault, pack, 0, 1, asNote = false, placeholder = false))
        assertEquals(Bank.Deposit.Done(1), bank.deposit(vault, pack, 0, 1))
        assertEquals(InvObj(LOGS, 1), vault[0])
        bank.withdraw(vault, pack, 0, 1, asNote = false, placeholder = true)
        assertFalse(bank.releasePlaceholder(vault, 1))
        assertTrue(bank.releasePlaceholder(vault, 0))
        assertEquals(InvObj(COINS, 5), vault[0])
        assertEquals(1, bank.size(vault))
    }

    @Test
    fun `a drag swaps, or with insert moves the ones between along, and past the end goes to the end`() {
        for (slot in 0 until 4) vault[slot] = InvObj(SWORD + slot, 1)
        assertTrue(bank.move(vault, 0, 2, insert = false))
        assertEquals(listOf(SWORD + 2, SWORD + 1, SWORD, SWORD + 3), (0 until 4).map { vault[it]?.id })
        assertTrue(bank.move(vault, 0, 3, insert = true))
        assertEquals(listOf(SWORD + 1, SWORD, SWORD + 3, SWORD + 2), (0 until 4).map { vault[it]?.id })
        assertTrue(bank.move(vault, 3, 0, insert = true))
        assertEquals(listOf(SWORD + 2, SWORD + 1, SWORD, SWORD + 3), (0 until 4).map { vault[it]?.id })
        assertTrue(bank.move(vault, 0, 50, insert = false))
        assertEquals(listOf(SWORD + 1, SWORD, SWORD + 3, SWORD + 2), (0 until 4).map { vault[it]?.id })
        assertFalse(bank.move(vault, 4, 0, insert = false))
    }

    @Test
    fun `deposit all takes what the bank will hold`() {
        pack.fill(LOGS, 2)
        pack.fill(ORB, 1)
        pack.fill(COINS, 50)
        val result = bank.depositAll(vault, pack)
        assertEquals(2, result.deposited)
        assertEquals(1, result.notBankable)
        assertEquals(listOf(InvObj(LOGS, 2), InvObj(COINS, 50)), listOf(vault[0], vault[1]))
        assertEquals(listOf(2 to InvObj(ORB, 1)), pack.contents())
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
