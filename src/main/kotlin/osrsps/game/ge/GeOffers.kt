package osrsps.game.ge

/** Whether an offer buys or sells. */
enum class OfferType { BUY, SELL }

/**
 * One Grand Exchange offer: [quantity] of [item] (never a note) at [price] coins each. [completed] of them have changed
 * hands so far, for [exchanged] coins in all before tax. What the player can take back out of the slot waits in
 * [collectItems] and [collectCoins]: bought items, refunds, sale coins, and whatever an abort returned.
 *
 * An offer stays in its slot until it is [finished] and everything has been collected, as on live.
 */
data class GeOffer(
    val type: OfferType,
    val item: Int,
    val quantity: Int,
    val price: Long,
    val completed: Int = 0,
    val exchanged: Long = 0,
    val aborted: Boolean = false,
    val collectItems: Int = 0,
    val collectCoins: Long = 0,
    /** The tick it was placed on, so it first fills on the tick after; not saved, so a loaded offer can fill at once. */
    val placedTick: Long = -1,
) {
    init {
        require(quantity >= 1 && price >= 1 && completed in 0..quantity) { "Bad offer $this" }
        require(exchanged >= 0 && collectItems >= 0 && collectCoins >= 0) { "Bad offer $this" }
    }

    /** Whether nothing more can change hands: every one has, or the offer was aborted. */
    val finished: Boolean get() = aborted || completed == quantity

    /** Whether the slot can be emptied: finished, with nothing left to collect. */
    val done: Boolean get() = finished && collectItems == 0 && collectCoins == 0L

    val remaining: Int get() = quantity - completed
}

/** How much of [item] a player has bought in the buy limit's window, which started at [windowStart] (ms since 1970). */
data class BuyLimitUse(val item: Int, val windowStart: Long, val bought: Int)

/** A player's Grand Exchange: their [SLOTS] offer slots and what they've bought against each item's buy limit. */
class GeAccount {
    private val offers = arrayOfNulls<GeOffer>(SLOTS)

    /** By item id; entries whose window has passed are dropped when next looked at. */
    val limits = LinkedHashMap<Int, BuyLimitUse>()

    /** Offers collected to nothing since the screen last flushed, by slot; see GrandExchange.collect. Not saved. */
    val collected = arrayOfNulls<GeOffer>(SLOTS)

    operator fun get(slot: Int): GeOffer? = offers[checkSlot(slot)]

    operator fun set(slot: Int, offer: GeOffer?) {
        offers[checkSlot(slot)] = offer
    }

    /** The first empty slot, -1 when all are taken. */
    fun freeSlot(): Int = offers.indexOfFirst { it == null }

    /** The filled slots, in slot order. */
    fun contents(): List<Pair<Int, GeOffer>> = offers.withIndex().mapNotNull { (slot, offer) -> offer?.let { slot to it } }

    fun clear() {
        offers.fill(null)
        collected.fill(null)
        limits.clear()
    }

    private fun checkSlot(slot: Int): Int {
        require(slot in 0 until SLOTS) { "The Grand Exchange has slots 0 to ${SLOTS - 1}, not $slot" }
        return slot
    }

    companion object {
        /** A member's slots (the Wiki); every account here counts as a member. */
        const val SLOTS = 8
    }
}
