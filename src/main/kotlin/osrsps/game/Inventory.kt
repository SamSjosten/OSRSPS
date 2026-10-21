package osrsps.game

/** One slot's item: [id] is the obj type, [count] at least 1. */
data class InvObj(val id: Int, val count: Int) {
    init {
        require(id >= 0 && count >= 1) { "Bad inventory item $id x $count" }
    }
}

/**
 * An inventory: [capacity] slots, each empty or holding one [InvObj]. Changed slots are sent once a tick by [flush].
 *
 * Only the always-stack rule of the main inventory is built: a stackable item (and every note) goes on one stack per
 * inventory, everything else one per slot. Banks stack everything and come later.
 */
class Inventory(
    /** The inv type id the client knows it by; 93 is the player's inventory. */
    val type: Int,
    val capacity: Int,
) {
    private val slots = arrayOfNulls<InvObj>(capacity)
    private val changed = sortedSetOf<Int>()

    /** Nothing has been sent yet: the first flush sends every slot. */
    private var sentOnce = false

    operator fun get(slot: Int): InvObj? = slots[checkSlot(slot)]

    /** Puts [obj] in [slot], replacing what was there. For loading saves and scenarios; content uses [add]. */
    operator fun set(slot: Int, obj: InvObj?) {
        checkSlot(slot)
        if (slots[slot] == obj) return
        slots[slot] = obj
        changed += slot
    }

    val freeSlots: Int get() = slots.count { it == null }

    fun isEmpty(): Boolean = slots.all { it == null }

    /** How many of item [id] the inventory holds, over all its slots. */
    fun count(id: Int): Long = slots.sumOf { if (it?.id == id) it.count.toLong() else 0L }

    /** The slots that hold something, in slot order. */
    fun contents(): List<Pair<Int, InvObj>> = slots.withIndex().mapNotNull { (slot, obj) -> obj?.let { slot to it } }

    /**
     * Adds up to [count] of [type]: onto its stack (or the first free slot) if it stacks, one per free slot if not.
     * Returns how many went in, which is less than [count] when the inventory fills up or a stack would pass
     * [MAX_STACK]. Inferred: live's inv_add refuses the whole add instead; ::item wants as many as fit.
     */
    fun add(type: ObjType, count: Int): Int {
        require(count >= 1) { "Can't add $count of $type" }
        if (type.stackable) {
            val slot = slots.indexOfFirst { it?.id == type.id }.takeIf { it >= 0 } ?: slots.indexOfFirst { it == null }
            if (slot < 0) return 0
            val have = slots[slot]?.count ?: 0
            val added = minOf(count.toLong(), MAX_STACK.toLong() - have).toInt()
            if (added > 0) set(slot, InvObj(type.id, have + added))
            return added
        }
        var added = 0
        for (slot in slots.indices) {
            if (added == count) break
            if (slots[slot] != null) continue
            set(slot, InvObj(type.id, 1))
            added++
        }
        return added
    }

    /** Takes up to [count] from [slot], returning how many came off. */
    fun remove(slot: Int, count: Int = Int.MAX_VALUE): Int {
        require(count >= 1) { "Can't remove $count" }
        val obj = get(slot) ?: return 0
        val removed = minOf(count, obj.count)
        set(slot, if (removed == obj.count) null else obj.copy(count = obj.count - removed))
        return removed
    }

    /** Swaps two slots, as dragging in the inventory does. */
    fun swap(from: Int, to: Int) {
        val a = get(from)
        val b = get(to)
        set(from, b)
        set(to, a)
    }

    fun clear() {
        for (slot in slots.indices) set(slot, null)
    }

    /** Marks [slot] as changed, so its contents are sent again: for a client that may have shown something else. */
    fun resend(slot: Int) {
        changed += checkSlot(slot)
    }

    /** Whether the next [flush] sends anything. */
    val hasChanges: Boolean get() = !sentOnce || changed.isNotEmpty()

    /** Makes the next [flush] send every slot again, as after a reconnect. */
    fun resendAll() {
        sentOnce = false
    }

    /**
     * Hands what the client needs to [full] (every slot, the first time) or [partial] (the changed slots, after that).
     * Nothing is sent when nothing changed. When live picks full or partial updates isn't known; this is a guess.
     */
    fun flush(full: (slots: List<InvObj?>) -> Unit, partial: (changed: List<Pair<Int, InvObj?>>) -> Unit) {
        if (!sentOnce) {
            sentOnce = true
            changed.clear()
            full(slots.toList())
            return
        }
        if (changed.isEmpty()) return
        val update = changed.map { it to slots[it] }
        changed.clear()
        partial(update)
    }

    private fun checkSlot(slot: Int): Int {
        require(slot in 0 until capacity) { "Inventory $type has slots 0 to ${capacity - 1}, not $slot" }
        return slot
    }

    companion object {
        /** The largest stack the client shows: a signed 32-bit int, 2,147,483,647. */
        const val MAX_STACK = Int.MAX_VALUE

        /** inv in the cache's gameval index, 28 slots. */
        const val INV = 93
        const val INV_CAPACITY = 28

        /** worn in the cache's gameval index: the equipment, one slot per [WearPos]. */
        const val WORN = 94
    }
}
