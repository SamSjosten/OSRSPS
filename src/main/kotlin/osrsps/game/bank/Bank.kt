package osrsps.game.bank

import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes

/**
 * The bank's rules, on a player's bank inventory (inv 95): what a deposit, a withdrawal and a rearrange do to it. No
 * messages or interfaces here; [BankScreen] says what happened.
 *
 * Until bank tabs come (the plan's step 3) the bank is one main tab, kept in order with no gaps: the items fill slots 0
 * up, and taking a slot's last item moves the ones after it up a slot. Placeholders hold their slot as an item does.
 * Everything stacks in the bank, notes go in as the item they stand for, and a bank slot holds at most
 * [Inventory.MAX_STACK]. From RSMod (rev 233), whose bank emulates live; how full stacks and a full bank share out a
 * deposit is RSMod's too, not checked on live.
 */
class Bank(private val objTypes: ObjTypes) {
    /** How many slots the bank holds: [DEFAULT_CAPACITY] for every account until bank space can be bought. */
    fun capacity(): Int = DEFAULT_CAPACITY

    /** Slots in use, placeholders included. */
    fun used(bank: Inventory): Int = bank.capacity - bank.freeSlots

    /**
     * Puts up to [count] of [from]'s item in [slot] into [bank], taking more of the same item from [from]'s other slots
     * once that one is used up (a Deposit-5 of logs takes 5 wherever they are), and returns what happened. A note goes
     * in as its item; a placeholder for the item is filled in.
     */
    fun deposit(bank: Inventory, from: Inventory, slot: Int, count: Int): Deposit {
        val held = from[slot] ?: return Deposit.Nothing
        val type = objTypes[held.id] ?: return Deposit.Nothing
        if (type.intParam(PARAM_NO_BANK) != 0) return Deposit.NotBankable
        val item = banked(type)
        val bankSlot = slotFor(bank, item) ?: return Deposit.BankFull
        val have = bank[bankSlot]?.takeIf { it.id == item.id }?.count ?: 0
        val room = Inventory.MAX_STACK - have
        if (room <= 0) return Deposit.FullStack
        val wanted = minOf(count.toLong(), from.count(held.id)).toInt()
        // The clicked slot first, then the others in slot order.
        val slots = listOf(slot) + from.contents().map { it.first }.filter { it != slot && from[it]?.id == held.id }
        var moved = 0
        for (fromSlot in slots) {
            val take = minOf(wanted - moved, room - moved)
            if (take <= 0) break
            moved += from.remove(fromSlot, take)
        }
        bank[bankSlot] = InvObj(item.id, have + moved)
        return if (moved < wanted) Deposit.Partly(moved) else Deposit.Done(moved)
    }

    /**
     * Puts everything in [from] into [bank], item by item in slot order. Returns how many items went in, how many
     * didn't all fit because the bank or its stack is full, and how many can't be banked at all.
     */
    fun depositAll(bank: Inventory, from: Inventory): DepositAll {
        var deposited = 0
        var noRoom = 0
        var notBankable = 0
        for ((slot, _) in from.contents()) {
            // An earlier slot of the same item took this one with it.
            if (from[slot] == null) continue
            when (deposit(bank, from, slot, Int.MAX_VALUE)) {
                is Deposit.Done -> deposited++
                is Deposit.Partly, Deposit.BankFull, Deposit.FullStack -> noRoom++
                Deposit.NotBankable -> notBankable++
                Deposit.Nothing -> Unit
            }
        }
        return DepositAll(deposited, noRoom, notBankable)
    }

    /**
     * Takes up to [count] of what is in bank [slot] into [inventory]: as a note when [asNote] and the item has one,
     * leaving a placeholder when [placeholder] and the slot empties. A stack goes in as far as one stack can take,
     * anything else one per free slot.
     */
    fun withdraw(bank: Inventory, inventory: Inventory, slot: Int, count: Int, asNote: Boolean, placeholder: Boolean): Withdrawal {
        val held = bank[slot] ?: return Withdrawal.Nothing
        val type = objTypes[held.id] ?: return Withdrawal.Nothing
        if (type.isPlaceholder || count <= 0) return Withdrawal.Nothing
        val note = if (asNote) noteOf(type) else null
        val into = note ?: type
        val wanted = minOf(count, held.count)
        val fits = if (into.stackable) wanted else minOf(wanted, inventory.freeSlots)
        if (fits <= 0) return Withdrawal.NoSpace
        val added = inventory.add(into, fits)
        if (added <= 0) return Withdrawal.NoSpace
        if (added < held.count) {
            bank[slot] = held.copy(count = held.count - added)
        } else if (placeholder && type.placeholderlink != -1) {
            // Guessed: the placeholder is kept with a count of 1; the client draws it with none.
            bank[slot] = InvObj(type.placeholderlink, 1)
        } else {
            removeSlot(bank, slot)
        }
        return Withdrawal(added, added < wanted, asNote && note == null)
    }

    /** Takes away the placeholder in [slot], closing the gap. Returns false when the slot holds no placeholder. */
    fun releasePlaceholder(bank: Inventory, slot: Int): Boolean {
        val held = bank[slot] ?: return false
        if (objTypes[held.id]?.isPlaceholder != true) return false
        removeSlot(bank, slot)
        return true
    }

    /**
     * Moves bank [from] to [to], as a drag does: swapping the two, or with [insert] taking it out and putting it in at
     * [to], moving the ones between along. A drag past the last item moves it to the end. Returns false when [from] is
     * empty.
     */
    fun move(bank: Inventory, from: Int, to: Int, insert: Boolean): Boolean {
        val size = size(bank)
        if (from !in 0 until size || to < 0 || from == to) return false
        val target = minOf(to, size - 1)
        if (target == from) return false
        if (!insert && to < size) {
            bank.swap(from, target)
            return true
        }
        val moving = bank[from]
        if (from < target) {
            for (slot in from until target) bank[slot] = bank[slot + 1]
        } else {
            for (slot in from downTo target + 1) bank[slot] = bank[slot - 1]
        }
        bank[target] = moving
        return true
    }

    /** The slots in use, from 0: the bank has no gaps. */
    fun size(bank: Inventory): Int = (0 until bank.capacity).firstOrNull { bank[it] == null } ?: bank.capacity

    /** The item that goes in the bank for [type]: what a note stands for, else the item itself. */
    private fun banked(type: ObjType): ObjType = if (type.isCert) objTypes[type.certlink] ?: type else type

    /** The note of [type], or null when it has none (stackable items, quest items). */
    private fun noteOf(type: ObjType): ObjType? = objTypes[type.certlink]?.takeIf { it.isCert }

    /** The slot [item] goes in: its stack or placeholder, else the next free slot while the bank has room. */
    private fun slotFor(bank: Inventory, item: ObjType): Int? {
        val placeholder = item.placeholderlink.takeIf { it != -1 && !item.isPlaceholder }
        val existing = bank.contents().firstOrNull { (_, obj) -> obj.id == item.id || obj.id == placeholder }
        if (existing != null) return existing.first
        if (used(bank) >= capacity()) return null
        return size(bank).takeIf { it < bank.capacity }
    }

    private fun removeSlot(bank: Inventory, slot: Int) {
        val size = size(bank)
        for (i in slot until size - 1) bank[i] = bank[i + 1]
        bank[size - 1] = null
    }

    /** What a deposit did. */
    sealed interface Deposit {
        data class Done(val count: Int) : Deposit

        /** Only [count] went in: the bank's stack is full. */
        data class Partly(val count: Int) : Deposit

        /** The slot was empty, or held an item the cache doesn't have. */
        data object Nothing : Deposit
        data object NotBankable : Deposit
        data object BankFull : Deposit
        data object FullStack : Deposit
    }

    class DepositAll(val deposited: Int, val noRoom: Int, val notBankable: Int)

    /**
     * What a withdrawal did: [count] came out, fewer than asked for when [short] (the inventory filled), and as items
     * though notes were asked for when [notNoted].
     */
    data class Withdrawal(val count: Int, val short: Boolean = false, val notNoted: Boolean = false) {
        companion object {
            val Nothing = Withdrawal(0)
            val NoSpace = Withdrawal(0, short = true)
        }
    }

    companion object {
        /**
         * A members' bank holds 900 (OSRS Wiki, Bank, read 2026-10-29): every account counts as members, and bank space
         * can't be bought yet.
         */
        const val DEFAULT_CAPACITY = 900

        /** Param no_bank in RSMod's rev 233 symbol table: not 0 on items a bank refuses. Not checked in 241. */
        const val PARAM_NO_BANK = 59
    }
}
