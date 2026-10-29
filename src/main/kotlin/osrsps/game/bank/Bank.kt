package osrsps.game.bank

import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes

/**
 * The bank's rules, on a player's bank inventory (inv 95) and its tabs: what a deposit, a withdrawal and a rearrange do
 * to them. No messages or interfaces here; [BankScreen] says what happened.
 *
 * The bank holds tabs 1 to 9 first, in order, then the main tab, as the client reads them (bank_gettabrange): tab 1 is
 * slots 0 up to its size, tab 2 the next ones, and the main tab everything after tab 9. Only the numbered tabs' sizes
 * are kept ([BankTabs]); the main tab runs to the last item. Every change writes the whole bank back with no gaps
 * (taking a slot's last item moves the ones after it up a slot), so a gap only comes from a save edited by hand, and the
 * next change closes it. A tab that empties goes, the tabs after it moving down one. Placeholders and bank fillers hold
 * their slot as an item does.
 *
 * Everything stacks in the bank, notes go in as the item they stand for, and a bank slot holds at most
 * [Inventory.MAX_STACK]. From RSMod (rev 233), whose bank emulates live; how full stacks and a full bank share out a
 * deposit is RSMod's too, not checked on live. RSMod's main tab can have gaps (an item dragged to an empty slot past the
 * end stays there, which the client draws too) until the next withdrawal closes them; ours moves the item to the end at
 * once.
 */
class Bank(private val objTypes: ObjTypes) {
    /** How many slots the bank holds: [DEFAULT_CAPACITY] for every account until bank space can be bought. */
    fun capacity(): Int = DEFAULT_CAPACITY

    /** Slots in use, placeholders and fillers included. */
    fun used(bank: Inventory): Int = bank.capacity - bank.freeSlots

    /**
     * Puts up to [count] of [from]'s item in [slot] into [bank], taking more of the same item from [from]'s other slots
     * once that one is used up (a Deposit-5 of logs takes 5 wherever they are), and returns what happened. A note goes
     * in as its item; a placeholder for the item is filled in. An item the bank doesn't have yet goes at the end of
     * the tab being viewed, or of the main tab when that is all items.
     */
    fun deposit(bank: Inventory, tabs: BankTabs, from: Inventory, slot: Int, count: Int): Deposit {
        val held = from[slot] ?: return Deposit.Nothing
        val type = objTypes[held.id] ?: return Deposit.Nothing
        if (type.intParam(PARAM_NO_BANK) != 0) return Deposit.NotBankable
        val item = banked(type)
        val existing = existingSlot(bank, item)
        if (existing == null && used(bank) >= capacity()) return Deposit.BankFull
        val have = existing?.let { bank[it] }?.takeIf { it.id == item.id }?.count ?: 0
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
        val obj = InvObj(item.id, have + moved)
        if (existing != null) {
            bank[existing] = obj
        } else {
            val layout = layout(bank, tabs)
            val into = tabs.current.takeIf { it in 1..TABS && layout[it].isNotEmpty() } ?: MAIN_TAB
            layout[into] += obj
            store(bank, tabs, layout)
        }
        return if (moved < wanted) Deposit.Partly(moved) else Deposit.Done(moved)
    }

    /**
     * Puts everything in [from] into [bank], item by item in slot order. Returns how many items went in, how many
     * didn't all fit because the bank or its stack is full, and how many can't be banked at all.
     */
    fun depositAll(bank: Inventory, tabs: BankTabs, from: Inventory): DepositAll {
        var deposited = 0
        var noRoom = 0
        var notBankable = 0
        for ((slot, _) in from.contents()) {
            // An earlier slot of the same item took this one with it.
            if (from[slot] == null) continue
            when (deposit(bank, tabs, from, slot, Int.MAX_VALUE)) {
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
     * anything else one per free slot. Placeholders and fillers can't be withdrawn.
     */
    fun withdraw(
        bank: Inventory,
        tabs: BankTabs,
        inventory: Inventory,
        slot: Int,
        count: Int,
        asNote: Boolean,
        placeholder: Boolean,
    ): Withdrawal {
        val held = bank[slot] ?: return Withdrawal.Nothing
        val type = objTypes[held.id] ?: return Withdrawal.Nothing
        if (type.isPlaceholder || held.id == FILLER || count <= 0) return Withdrawal.Nothing
        val note = if (asNote) noteOf(type) else null
        val into = note ?: type
        val wanted = minOf(count, held.count)
        val fits = if (into.stackable) wanted else minOf(wanted, inventory.freeSlots)
        if (fits <= 0) return Withdrawal.NoSpace
        val added = inventory.add(into, fits)
        if (added <= 0) return Withdrawal.NoSpace
        if (added < held.count) {
            bank[slot] = held.copy(count = held.count - added)
        } else if (placeholder && placeholderOf(type) != null) {
            // Guessed: the placeholder is kept with a count of 1; the client draws it with none.
            bank[slot] = InvObj(type.placeholderlink, 1)
        } else {
            removeSlot(bank, tabs, slot)
        }
        return Withdrawal(added, added < wanted, asNote && note == null)
    }

    /**
     * Takes away what is in [slot] when it is a placeholder or a filler (their Release and Clear), or anything at all
     * when [any] (the incinerator), closing the gap. Returns false when nothing went.
     */
    fun discard(bank: Inventory, tabs: BankTabs, slot: Int, any: Boolean = false): Boolean {
        val held = bank.takeIf { slot in 0 until it.capacity }?.get(slot) ?: return false
        if (!any && !isPlaceholderOrFiller(held.id)) return false
        removeSlot(bank, tabs, slot)
        return true
    }

    /**
     * Moves bank [from] to [to], as a drag between two bank slots does: swapping the two, or with [insert] taking it
     * out and putting it in at [to], moving the ones between along. Into another tab an insert goes in front of the item
     * at [to] and the tabs' sizes follow; a swap leaves them. A drag past the last item moves it to the end of the main
     * tab. Returns false when [from] is empty.
     */
    fun move(bank: Inventory, tabs: BankTabs, from: Int, to: Int, insert: Boolean): Boolean {
        if (!holds(bank, from) || to !in 0 until bank.capacity || from == to) return false
        val target = bank[to]
        if (!insert && target != null) {
            bank.swap(from, to)
            return true
        }
        val layout = layout(bank, tabs)
        val (fromTab, fromIndex) = position(bank, tabs, from)
        // An empty slot is past the end of its tab (only the main tab has them).
        val (toTab, toIndex) = if (target != null) position(bank, tabs, to) else tabOf(tabs, to) to Int.MAX_VALUE
        val obj = layout[fromTab].removeAt(fromIndex)
        layout[toTab].add(minOf(toIndex, layout[toTab].size), obj)
        store(bank, tabs, layout)
        return true
    }

    /**
     * Moves bank [from] to the end of [tab]: dragged onto the tab or into the space after its last item. A tab with
     * nothing in it becomes a new tab after the last one. Returns false when [from] is empty or already in [tab].
     */
    fun moveToTab(bank: Inventory, tabs: BankTabs, from: Int, tab: Int): Boolean {
        if (!holds(bank, from) || tab !in 0..TABS) return false
        val (fromTab, fromIndex) = position(bank, tabs, from)
        if (fromTab == tab) return false
        val layout = layout(bank, tabs)
        layout[tab] += layout[fromTab].removeAt(fromIndex)
        store(bank, tabs, layout)
        return true
    }

    /**
     * Moves numbered tab [from] to [to], as dragging its icon onto another does: the tabs between move along one.
     * Returns false unless both are tabs with items in.
     */
    fun moveTab(bank: Inventory, tabs: BankTabs, from: Int, to: Int): Boolean {
        if (from !in 1..TABS || to !in 1..TABS || from == to || tabs[from] == 0 || tabs[to] == 0) return false
        val layout = layout(bank, tabs)
        val numbered = layout.subList(1, TABS + 1)
        numbered.add(to - 1, numbered.removeAt(from - 1))
        store(bank, tabs, layout)
        return true
    }

    /** Puts numbered [tab]'s items at the end of the main tab, which takes the tab away. */
    fun collapse(bank: Inventory, tabs: BankTabs, tab: Int): Boolean {
        if (tab !in 1..TABS || tabs[tab] == 0) return false
        val layout = layout(bank, tabs)
        layout[MAIN_TAB] += layout[tab]
        layout[tab].clear()
        store(bank, tabs, layout)
        return true
    }

    /** Takes the placeholders out of [tab] (0 the main tab), or of every tab when [tab] is null. Returns how many went. */
    fun releasePlaceholders(bank: Inventory, tabs: BankTabs, tab: Int?): Int =
        removeAll(bank, tabs, tab) { objTypes[it.id]?.isPlaceholder == true }

    /** Takes every bank filler out. Returns how many went. */
    fun clearFillers(bank: Inventory, tabs: BankTabs): Int = removeAll(bank, tabs, null) { it.id == FILLER }

    /** Adds up to [count] bank fillers at the end of the main tab, as far as the bank has room. Returns how many. */
    fun addFillers(bank: Inventory, tabs: BankTabs, count: Int): Int {
        val adding = minOf(count, capacity() - used(bank)).coerceAtLeast(0)
        if (adding == 0) return 0
        val layout = layout(bank, tabs)
        repeat(adding) { layout[MAIN_TAB] += InvObj(FILLER, 1) }
        store(bank, tabs, layout)
        return adding
    }

    /** Whether there is a placeholder anywhere in the bank. */
    fun hasPlaceholders(bank: Inventory): Boolean = bank.contents().any { (_, obj) -> objTypes[obj.id]?.isPlaceholder == true }

    fun isPlaceholderOrFiller(id: Int): Boolean = id == FILLER || objTypes[id]?.isPlaceholder == true

    /** The tab whose slots take in [slot]: 1 to 9, or 0 for the main tab. */
    fun tabOf(tabs: BankTabs, slot: Int): Int {
        var end = 0
        for (tab in 1..TABS) {
            end += tabs[tab]
            if (slot < end) return tab
        }
        return MAIN_TAB
    }

    /** Empties the bank and its tabs, for `::scenario`. */
    fun clear(bank: Inventory, tabs: BankTabs) {
        bank.clear()
        for (tab in 1..TABS) tabs[tab] = 0
        tabs.current = MAIN_TAB
    }

    private fun holds(bank: Inventory, slot: Int): Boolean = slot in 0 until bank.capacity && bank[slot] != null

    /** [slot]'s tab and its place among the tab's items, as [layout] has them. [slot] must hold an item. */
    private fun position(bank: Inventory, tabs: BankTabs, slot: Int): Pair<Int, Int> {
        val tab = tabOf(tabs, slot)
        val start = (1 until (if (tab == MAIN_TAB) TABS + 1 else tab)).sumOf { tabs[it] }
        return tab to (start until slot).count { bank[it] != null }
    }

    /**
     * The bank's items by tab: index 0 the main tab, 1 to 9 the numbered ones, each in slot order. The tab sizes say
     * which slots are whose; an empty slot (a gap in a save edited by hand) is left out, so [store] closes it.
     */
    private fun layout(bank: Inventory, tabs: BankTabs): Layout {
        val lists = MutableList(TABS + 1) { mutableListOf<InvObj>() }
        for (slot in 0 until bank.capacity) bank[slot]?.let { lists[tabOf(tabs, slot)] += it }
        return Layout(lists, lists.getOrNull(tabs.current)?.takeIf { tabs.current in 1..TABS && it.isNotEmpty() })
    }

    /**
     * The bank's items by tab, and the list of the numbered tab being viewed. Changes edit the lists or move them
     * about, so [store] finds where the viewed tab went by the list itself: two tabs can hold equal items (fillers).
     */
    private class Layout(lists: MutableList<MutableList<InvObj>>, val viewed: List<InvObj>?) : MutableList<MutableList<InvObj>> by lists

    /**
     * Writes [layout] back: the numbered tabs that still have items, moved down over any that emptied, then the main
     * tab. The tab being viewed follows its tab, or goes to the main tab when its tab emptied.
     */
    private fun store(bank: Inventory, tabs: BankTabs, layout: Layout) {
        val numbered = (1..TABS).filter { layout[it].isNotEmpty() }
        // Where the viewed tab's list is now, if it still has items: its place may have changed.
        val viewedNow = layout.viewed?.let { viewed ->
            numbered.indexOfFirst { layout[it] === viewed }.let { if (it < 0) MAIN_TAB else it + 1 }
        }
        val items = numbered.flatMap { layout[it] } + layout[MAIN_TAB]
        // Every slot, so nothing is left behind past a gap. Slots that don't change aren't sent again.
        for (slot in 0 until bank.capacity) bank[slot] = items.getOrNull(slot)
        for (tab in 1..TABS) tabs[tab] = numbered.getOrNull(tab - 1)?.let { layout[it].size } ?: 0
        if (viewedNow != null) tabs.current = viewedNow
    }

    private fun removeSlot(bank: Inventory, tabs: BankTabs, slot: Int) {
        val layout = layout(bank, tabs)
        val (tab, index) = position(bank, tabs, slot)
        layout[tab].removeAt(index)
        store(bank, tabs, layout)
    }

    private fun removeAll(bank: Inventory, tabs: BankTabs, tab: Int?, which: (InvObj) -> Boolean): Int {
        val layout = layout(bank, tabs)
        var removed = 0
        for (t in 0..TABS) {
            if (tab != null && t != tab) continue
            val before = layout[t].size
            layout[t].removeAll(which)
            removed += before - layout[t].size
        }
        if (removed > 0) store(bank, tabs, layout)
        return removed
    }

    /**
     * The item that goes in the bank for [type]: what a note stands for, else the item itself. Here and below a link is
     * followed only when the other end links back, so a one-way link in the cache can't turn one item into another.
     */
    private fun banked(type: ObjType): ObjType {
        if (!type.isCert) return type
        return objTypes[type.certlink]?.takeIf { !it.isCert && it.certlink == type.id } ?: type
    }

    /** The note of [type], or null when it has none (stackable items, quest items). */
    private fun noteOf(type: ObjType): ObjType? = objTypes[type.certlink]?.takeIf { it.isCert && it.certlink == type.id }

    /** The placeholder of [type], or null when it has none. */
    private fun placeholderOf(type: ObjType): ObjType? =
        if (type.isPlaceholder) null else objTypes[type.placeholderlink]?.takeIf { it.isPlaceholder && it.placeholderlink == type.id }

    /** The slot holding [item]'s stack or placeholder, if the bank has either. */
    private fun existingSlot(bank: Inventory, item: ObjType): Int? {
        val placeholder = placeholderOf(item)?.id
        return bank.contents().firstOrNull { (_, obj) -> obj.id == item.id || obj.id == placeholder }?.first
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
         * can't be bought yet. The Wiki's changelog also has members getting 100 more on 2025-11-05 while its text
         * still says 900; left at 900 until checked on live.
         */
        const val DEFAULT_CAPACITY = 900

        /** Param no_bank in RSMod's rev 233 symbol table: not 0 on items a bank refuses. Not checked in 241. */
        const val PARAM_NO_BANK = 59

        /** bank_filler in the 241 cache. */
        const val FILLER = 20594

        /** The numbered tabs, 1 to [TABS]; tab 0 is the main tab. */
        const val TABS = 9
        const val MAIN_TAB = 0
    }
}

/** The sizes of a bank's numbered tabs and the tab being viewed: the bank_tab_1 to 9 and bank_currenttab varbits. */
interface BankTabs {
    /** How many slots numbered tab [tab] (1 to 9) holds. */
    operator fun get(tab: Int): Int

    operator fun set(tab: Int, size: Int)

    /** The tab being viewed: 1 to 9, or 0 for the main tab, which shows every item. 15 is potion storage. */
    var current: Int
}
