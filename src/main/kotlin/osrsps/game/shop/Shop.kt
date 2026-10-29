package osrsps.game.shop

/** One of a shop's slots: [count] of [obj]. An item the shop starts with stays at 0 when sold out; others go. */
data class ShopSlot(val obj: Int, val count: Int) {
    init {
        require(obj >= 0 && count >= 0) { "Bad shop slot $obj x $count" }
    }
}

/**
 * A shop's stock now, the same for every player (the Wiki: shops share their stock on a world), and its prices.
 *
 * The shop starts with its [ShopType.stock] in order. What players sell goes onto the item's slot, or (a general store
 * only) into the first free one. Every tick [restock] moves each item one step back towards its start: one more of a
 * sold item, one fewer of an overstocked one, each on its own timer. An item the shop doesn't start with drops by one
 * every [NON_STOCK_TICKS] and its slot goes when it reaches 0, the items after it moving up.
 *
 * Guessed, not recorded: the restock steps fall on ticks that are a multiple of the time (as RSMod and 2004Scape have
 * it), [NON_STOCK_TICKS], and that the items after a gone one move up (2004Scape leaves the slot empty).
 */
class Shop(val type: ShopType) {
    private val slots = arrayOfNulls<ShopSlot>(type.size)

    /** Goes up on every change, so each viewer can tell its copy is old. */
    var version = 0
        private set

    init {
        type.stock.forEachIndexed { i, stock -> slots[i] = ShopSlot(stock.type.id, stock.count) }
    }

    val size: Int get() = slots.size

    operator fun get(slot: Int): ShopSlot? = slots.getOrNull(slot)

    fun contents(): List<ShopSlot?> = slots.toList()

    /** How many of [obj] the shop holds now. */
    fun count(obj: Int): Int = slots.firstOrNull { it?.obj == obj }?.count ?: 0

    /** Whether there is a slot for [obj], or room for one. */
    fun hasRoom(obj: Int): Boolean = slots.any { it?.obj == obj } || slots.any { it == null }

    /** Takes [count] of what is in [slot], which holds at least that many. */
    fun take(slot: Int, count: Int) {
        val held = requireNotNull(slots[slot]) { "Nothing in shop slot $slot" }
        require(count in 1..held.count) { "Can't take $count of $held" }
        set(slot, held.count - count)
    }

    /** Puts [count] of [obj] in, onto its slot or the first free one. Returns false when there is no room. */
    fun put(obj: Int, count: Int): Boolean {
        require(count >= 1) { "Can't put $count of $obj" }
        val slot = slots.indexOfFirst { it?.obj == obj }.takeIf { it >= 0 } ?: slots.indexOfFirst { it == null }
        if (slot < 0) return false
        val have = slots[slot]?.count ?: 0
        slots[slot] = ShopSlot(obj, (have.toLong() + count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        version++
        return true
    }

    /** Moves each item one step back towards its starting count, on ticks when its time comes round. */
    fun restock(tick: Long) {
        for (slot in slots.indices.reversed()) {
            val held = slots[slot] ?: continue
            val start = type.stock.firstOrNull { it.type.id == held.obj }
            val base = start?.count ?: 0
            if (held.count == base) continue
            val every = start?.restockTicks ?: NON_STOCK_TICKS
            if (tick % every != 0L) continue
            set(slot, if (held.count > base) held.count - 1 else held.count + 1)
        }
    }

    private fun set(slot: Int, count: Int) {
        val held = slots[slot]!!
        if (count == 0 && !type.stocks(held.obj)) {
            for (i in slot until slots.size - 1) slots[i] = slots[i + 1]
            slots[slots.size - 1] = null
        } else {
            slots[slot] = held.copy(count = count)
        }
        version++
    }

    /** What the shop charges for one [obj] of value [cost] while it holds [stock]: at least 1 coin. */
    fun buyPrice(obj: Int, cost: Int, stock: Int): Int = maxOf(1, price(cost, type.sellAt, type.changePer, stock - type.baseStock(obj)))

    /** What the shop pays for one [obj] of value [cost] while it holds [stock]. */
    fun sellPrice(obj: Int, cost: Int, stock: Int): Int = price(cost, type.buyAt, type.changePer, stock - type.baseStock(obj))

    companion object {
        /** How often an item the shop doesn't start with goes down by one. Guessed (RSMod's), until recorded. */
        const val NON_STOCK_TICKS = 100

        /**
         * One item's price: [percent] (in tenths) of [cost], less [changePer] for each item the shop holds over its
         * starting stock ([overStock], negative when it holds fewer). The change is held between -500% and +100%, and
         * the percentage is never under 10%. 2004Scape's calc_shop_value (RS2), which gives every price on the Wiki's
         * Lumbridge General Store and Bob's Brilliant Axes pages and its worked example (a steel axe sold to Bob
         * with 4 in stock: 116), and the Wiki's 10% least a shop pays.
         */
        fun price(cost: Int, percent: Int, changePer: Int, overStock: Int): Int {
            val change = (overStock.toLong() * changePer).coerceIn(-5000L, 1000L)
            val scaled = maxOf(100L, percent - change)
            return (cost.toLong() * scaled / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }
}
