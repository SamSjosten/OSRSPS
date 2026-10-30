package osrsps.game.ge

import osrsps.game.Inventory
import osrsps.game.ItemPrices
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player

/** Why an offer couldn't be placed, or [PLACED]. */
enum class PlaceResult { PLACED, NO_FREE_SLOT, NOT_TRADEABLE, NOT_ON_EXCHANGE, BAD_AMOUNT, TOO_EXPENSIVE, NOT_ENOUGH_COINS, NOT_ENOUGH_ITEMS, NO_ROOM_FOR_CHANGE }

/**
 * The Grand Exchange's offers: placing, filling, aborting and collecting, with buy limits and tax as live has them
 * (the OSRS Wiki's Grand Exchange page, read 2026-10-30). The screens come later; for now `::geoffer` drives this.
 *
 * There are no other traders, so a bot market stands in for them. GUESSED, Sam's pick from the plan (option A,
 * 2026-10-30): a buy at or above the guide price fills on the tick after it is placed, at the guide price, refunding
 * the difference; a sell at or below the guide price fills the same way at the guide price. Anything else waits until
 * the price file changes or the offer is aborted. A buy fills only as far as its buy limit lets it; the rest fills once
 * the limit's window has passed. An item with no guide price never fills. Offers fill only while their owner is logged
 * in (inferred: on live they fill while logged out, which here would show the same on the first tick after login).
 */
class GrandExchange(
    private val objTypes: ObjTypes,
    private val prices: () -> ItemPrices,
    /** The tick now, for filling on the tick after an offer is placed. */
    private val currentTick: () -> Long,
    /** Wall time in ms, for the buy limits' four hours, which run on through logouts. */
    private val now: () -> Long = System::currentTimeMillis,
    /** Items with no tax, by id. */
    private val taxExempt: Set<Int> = emptySet(),
) {
    /** Whether the bot market fills offers; `::gemarket off` stops it, so tests can hold offers open. */
    var marketOpen: Boolean = true

    /**
     * Places a buy offer for [quantity] of [item] at [price] each in [slot], the first free one unless given, taking the coins now: coins
     * first, then platinum tokens, from the inventory. Inferred from the Wiki ("prioritising inventory"): the bank is
     * drawn on after the inventory; not yet, as the bank isn't reachable from the commands.
     */
    fun buy(player: Player, item: Int, quantity: Int, price: Long, slot: Int = player.ge.freeSlot()): PlaceResult {
        val type = objTypes[item]
        val check = checkPlace(player, slot, type, quantity, price)
        if (check != PlaceResult.PLACED) return check
        val pay = pay(player.inventory, quantity * price)
        if (pay != PlaceResult.PLACED) return pay
        player.ge[slot] = GeOffer(OfferType.BUY, type!!.id, quantity, price, placedTick = currentTick())
        return PlaceResult.PLACED
    }

    /** Places a sell offer in [slot], taking the items now from the inventory, unnoted ones first and then notes. */
    fun sell(player: Player, item: Int, quantity: Int, price: Long, slot: Int = player.ge.freeSlot()): PlaceResult {
        val type = objTypes[item]?.let { unnoted(it) }
        val check = checkPlace(player, slot, type, quantity, price)
        if (check != PlaceResult.PLACED) return check
        val ids = listOfNotNull(type!!.id, noteOf(type)?.id)
        if (ids.sumOf { player.inventory.count(it) } < quantity) return PlaceResult.NOT_ENOUGH_ITEMS
        var left = quantity
        for (id in ids) {
            for ((slot, obj) in player.inventory.contents()) {
                if (left == 0) break
                if (obj.id == id) left -= player.inventory.remove(slot, left)
            }
        }
        player.ge[slot] = GeOffer(OfferType.SELL, type.id, quantity, price, placedTick = currentTick())
        return PlaceResult.PLACED
    }

    private fun checkPlace(player: Player, slot: Int, type: ObjType?, quantity: Int, price: Long): PlaceResult = when {
        slot !in 0 until GeAccount.SLOTS || player.ge[slot] != null -> PlaceResult.NO_FREE_SLOT
        type == null || type.isCert || !type.tradeable -> PlaceResult.NOT_TRADEABLE
        // Only items with a guide price are on the Exchange here; the Wiki's snapshot has every one live lists.
        prices().price(type) == null -> PlaceResult.NOT_ON_EXCHANGE
        quantity < 1 || price < 1 -> PlaceResult.BAD_AMOUNT
        price > MAX_TOTAL / quantity -> PlaceResult.TOO_EXPENSIVE
        else -> PlaceResult.PLACED
    }

    /** Aborts [slot]'s offer: what hadn't changed hands goes to the slot to collect. False if there is nothing to abort. */
    fun abort(player: Player, slot: Int): Boolean {
        val offer = player.ge[slot] ?: return false
        if (offer.finished) return false
        val aborted = when (offer.type) {
            OfferType.BUY -> offer.copy(aborted = true, collectCoins = offer.collectCoins + offer.remaining * offer.price)
            OfferType.SELL -> offer.copy(aborted = true, collectItems = offer.collectItems + offer.remaining)
        }
        player.ge[slot] = aborted
        record(player, aborted)
        return true
    }

    /**
     * Moves what [slot] holds into the inventory, as far as it fits: items as notes when there is more than one of an
     * item that doesn't stack and [notes] is set (live's Collect-notes), coins onto the coin stack. [items] and [coins]
     * pick the parts, as the screen's two boxes take one each. With [toBank], the parts go to the bank instead: it is
     * handed an item and a count and returns how many went in. The slot empties once the offer is finished and nothing
     * is left. Returns whether anything moved. GUESSED: that a part that doesn't fit stays in the slot rather than the
     * collect failing whole.
     */
    fun collect(
        player: Player,
        slot: Int,
        notes: Boolean? = null,
        items: Boolean = true,
        coins: Boolean = true,
        toBank: ((Player, Int, Int) -> Int)? = null,
    ): Boolean {
        var offer = player.ge[slot] ?: return false
        var moved = false
        if (items && offer.collectItems > 0) {
            val type = objTypes[offer.item]!!
            val added = if (toBank != null) {
                toBank(player, offer.item, offer.collectItems)
            } else {
                // Notes when asked for (Collect-note, Collect-notes); left to itself, notes only for more than one.
                val giveAs = if (!type.stackable && (notes ?: (offer.collectItems > 1))) noteOf(type) ?: type else type
                player.inventory.add(giveAs, offer.collectItems)
            }
            if (added > 0) moved = true
            offer = offer.copy(collectItems = offer.collectItems - added)
        }
        if (coins && offer.collectCoins > 0) {
            val (coinPart, tokenPart) = payout(offer.collectCoins)
            var paid = 0L
            if (tokenPart > 0) {
                val added = give(player, PLATINUM_TOKEN, tokenPart.toInt(), toBank)
                paid += added * TOKEN_VALUE
            }
            // The coins go whether or not every token fit: each part takes what room it finds.
            if (coinPart > 0) paid += give(player, COINS, coinPart.toInt(), toBank)
            if (paid > 0) moved = true
            offer = offer.copy(collectCoins = offer.collectCoins - paid)
        }
        if (offer.done) {
            // Kept for the screen's flush, so an abort collected in the same tick still gets its message.
            player.ge.collected[slot] = offer
            player.ge[slot] = null
        } else {
            player.ge[slot] = offer
        }
        return moved
    }

    /** Collects every slot, as Collect to inventory or to bank does. Returns whether anything moved. */
    fun collectAll(player: Player, toBank: ((Player, Int, Int) -> Int)? = null): Boolean {
        var moved = false
        for (slot in 0 until GeAccount.SLOTS) {
            if (collect(player, slot, toBank = toBank)) moved = true
        }
        return moved
    }

    private fun give(player: Player, id: Int, count: Int, toBank: ((Player, Int, Int) -> Int)?): Int =
        toBank?.invoke(player, id, count) ?: player.inventory.add(objTypes[id] ?: ObjType(id, stackable = true), count)

        /**
     * Adds a finished offer to the history, if anything changed hands. GUESSED: that an offer goes in when it finishes,
     * whole, rather than for each part that fills.
     */
    private fun record(player: Player, offer: GeOffer) {
        if (offer.completed > 0) player.ge.record(GeTrade(offer.type, offer.item, offer.completed, offer.exchanged, offer.taxPaid))
    }

    /** Fills [player]'s offers the bot market takes, once a tick. */
    fun process(player: Player) {
        if (!marketOpen) return
        val tick = currentTick()
        for ((slot, offer) in player.ge.contents()) {
            if (offer.finished || offer.placedTick >= tick) continue
            val type = objTypes[offer.item] ?: continue
            val guide = prices().price(type) ?: continue
            when (offer.type) {
                OfferType.BUY -> if (offer.price >= guide) fill(player, slot, minOf(offer.remaining, limitLeft(player, type)), guide)
                OfferType.SELL -> if (offer.price <= guide) fill(player, slot, offer.remaining, guide)
            }
        }
    }

    /**
     * Fills [amount] of [slot]'s offer at [at] coins each (the offer's own price by default), as `::gefill` does: the
     * buy limit counts the items but doesn't stop them. Returns how many filled.
     */
    fun fill(player: Player, slot: Int, amount: Int, at: Long? = null): Int {
        val offer = player.ge[slot] ?: return 0
        val count = minOf(amount, offer.remaining)
        if (offer.finished || count <= 0) return 0
        val each = at ?: offer.price
        player.ge[slot] = when (offer.type) {
            OfferType.BUY -> {
                recordBought(player, limitKey(offer.item), count)
                offer.copy(
                    completed = offer.completed + count, exchanged = offer.exchanged + count * each,
                    collectItems = offer.collectItems + count, collectCoins = offer.collectCoins + count * (offer.price - each),
                )
            }
            OfferType.SELL -> offer.copy(
                completed = offer.completed + count, exchanged = offer.exchanged + count * each,
                collectCoins = offer.collectCoins + count * (each - tax(offer.item, each)), taxPaid = offer.taxPaid + count * tax(offer.item, each),
            )
        }
        player.ge[slot]?.takeIf { it.finished }?.let { record(player, it) }
        return count
    }

    /**
     * The tax on selling one of [item] for [price]: 2% rounded down, at most 5,000,000, none for the exempt items (the
     * Wiki: a sale under 50 coins pays none, one at 100 pays 2). The client works out the same from varp
     * ge_transmit_taxrate ([TAX_PER_MILLE]).
     */
    fun tax(item: Int, price: Long): Long = if (item in taxExempt) 0 else minOf(MAX_TAX, price * TAX_PER_MILLE / 1000)

    /**
     * How many more of [type] the buy limit lets [player] buy now, counting what they bought of its other doses (see
     * [limitKey]); no limit in the snapshot means no limit.
     */
    fun limitLeft(player: Player, type: ObjType): Int {
        val limit = prices().limit(type) ?: return Int.MAX_VALUE
        val use = currentUse(player, limitKey(type.id)) ?: return limit
        return (limit - use.bought).coerceAtLeast(0)
    }

    /**
     * The item whose buy limit [item] counts against: the first id of the items named like it with another dose count,
     * so Prayer potion(4) and (3) share one (the Wiki, Buying limit: "if the player has reached the limit on 4-dose
     * Prayer potions, they cannot buy Prayer potions of fewer doses"). GUESSED: that every item named with a 1 to 4 in
     * brackets shares its limit this way; the Wiki names only prayer potions.
     */
    fun limitKey(item: Int): Int {
        val name = objTypes[item]?.name ?: return item
        val base = DOSES.find(name)?.groupValues?.get(1) ?: return item
        return doseGroups[base.lowercase()] ?: item
    }

    /** Each dosed name without its count, to the first unnoted item id with it. */
    private val doseGroups: Map<String, Int> by lazy {
        val groups = HashMap<String, Int>()
        for (id in 0 until objTypes.size) {
            val type = objTypes[id] ?: continue
            if (type.isCert || type.isPlaceholder) continue
            val base = DOSES.find(type.name)?.groupValues?.get(1) ?: continue
            groups.putIfAbsent(base.lowercase(), id)
        }
        groups
    }

    /**
     * The buy limit's use of [item], null once its window has passed: the window starts at the first item bought and
     * the limit resets [LIMIT_WINDOW_MILLIS] later (the Wiki, Buying limit: "This timer is based on the first item
     * bought in the 4-hour slot").
     */
    private fun currentUse(player: Player, item: Int): BuyLimitUse? {
        val use = player.ge.limits[item] ?: return null
        if (now() - use.windowStart < LIMIT_WINDOW_MILLIS) return use
        player.ge.limits.remove(item)
        return null
    }

    private fun recordBought(player: Player, item: Int, count: Int) {
        val use = currentUse(player, item) ?: BuyLimitUse(item, now(), 0)
        player.ge.limits[item] = use.copy(bought = (use.bought.toLong() + count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    /**
     * Takes [total] coins from [inv]: coins first, then whole platinum tokens, with the change from the last token put
     * back as coins (the Wiki, 30 September 2026). Takes nothing when there isn't enough, or no room for the change.
     */
    private fun pay(inv: Inventory, total: Long): PlaceResult {
        val coins = inv.count(COINS)
        val tokens = inv.count(PLATINUM_TOKEN)
        if (coins + tokens * TOKEN_VALUE < total) return PlaceResult.NOT_ENOUGH_COINS
        val fromCoins = minOf(coins, total)
        val short = total - fromCoins
        val tokensUsed = (short + TOKEN_VALUE - 1) / TOKEN_VALUE
        val change = tokensUsed * TOKEN_VALUE - short
        // The coins are all spent when tokens are used, so their slot is free for the change unless there were none.
        if (change > 0 && coins == 0L && inv.freeSlots == 0 && tokensUsed < tokens) return PlaceResult.NO_ROOM_FOR_CHANGE
        take(inv, COINS, fromCoins)
        take(inv, PLATINUM_TOKEN, tokensUsed)
        if (change > 0) inv.add(objTypes[COINS] ?: ObjType(COINS, stackable = true), change.toInt())
        return PlaceResult.PLACED
    }

    private fun take(inv: Inventory, id: Int, amount: Long) {
        var left = amount
        for ((slot, obj) in inv.contents()) {
            if (left == 0L) break
            if (obj.id == id) left -= inv.remove(slot, minOf(left, Int.MAX_VALUE.toLong()).toInt())
        }
    }

    /** [item]'s guide price, null when it isn't on the Exchange: noted, untradeable or with no price. */
    fun guidePrice(item: Int): Long? {
        val type = objTypes[item]?.takeIf { !it.isCert && it.tradeable } ?: return null
        return prices().price(type)
    }

    /** [item]'s name, for messages. */
    fun itemName(item: Int): String = objTypes[item]?.name ?: "item $item"

    /** The item an offer for [item] trades: its unnoted form. */
    fun offerItem(item: Int): Int = objTypes[item]?.let { unnoted(it).id } ?: item

    /** What [item] and its note add up to in [inv], as many as a sell offer can take. */
    fun available(inv: Inventory, item: Int): Long {
        val type = objTypes[item]?.let { unnoted(it) } ?: return 0
        return listOfNotNull(type.id, noteOf(type)?.id).sumOf { inv.count(it) }
    }

    /** The coins a buy offer can draw on in [inv]: its coins and platinum tokens. */
    fun spendable(inv: Inventory): Long = inv.count(COINS) + inv.count(PLATINUM_TOKEN) * TOKEN_VALUE

    /** The tax per mille on selling [item], as varbit ge_transmit_taxrate tells the client: none for the exempt items. */
    fun taxRate(item: Int): Int = if (item in taxExempt) 0 else TAX_PER_MILLE.toInt()

    private fun unnoted(type: ObjType): ObjType =
        if (type.isCert) objTypes[type.certlink]?.takeIf { !it.isCert } ?: type else type

    private fun noteOf(type: ObjType): ObjType? = objTypes[type.certlink]?.takeIf { it.isCert && it.certlink == type.id }

    companion object {
        /**
         * How [coins] to collect are paid: as coins while they fit in a stack, else as platinum tokens for the
         * thousands and coins for the rest. The collection box's third slot holds the tokens (script818 and
         * script9910 draw coins and tokens as one, "coins_and_platinum"). GUESSED: where the split falls.
         */
        fun payout(coins: Long): Pair<Long, Long> {
            if (coins <= Int.MAX_VALUE) return coins to 0L
            // A stack of tokens tops out at 2,147,483,647 too; MAX_TOTAL is exactly a full stack of each. A sell can
            // take in more (it fills at the guide price, above what was asked); the rest waits in the box for later.
            val tokens = minOf(coins / TOKEN_VALUE, Int.MAX_VALUE.toLong())
            return minOf(coins - tokens * TOKEN_VALUE, Int.MAX_VALUE.toLong()) to tokens
        }

        const val COINS = 995
        const val PLATINUM_TOKEN = 13204
        const val TOKEN_VALUE = 1000L

        /** The most an offer can be worth since 30 September 2026 (the Wiki), coins and tokens together. */
        const val MAX_TOTAL = 2_149_631_130_647L

        /** 2% since 29 May 2025, in the client's per mille (varp ge_transmit_taxrate). */
        const val TAX_PER_MILLE = 20L
        const val MAX_TAX = 5_000_000L

        const val LIMIT_WINDOW_MILLIS = 4 * 60 * 60 * 1000L

        /** A name with a dose count: "Prayer potion(4)" is "Prayer potion" and 4. */
        private val DOSES = Regex("""^(.*)\(([1-4])\)$""")

        const val TAX_EXEMPT_RESOURCE = "/ge-tax-exempt.txt"

        /**
         * The ids [TAX_EXEMPT_RESOURCE] names: every unnoted item whose name is a listed one, or a listed one followed by
         * a dose or charge count in brackets.
         */
        fun taxExempt(objTypes: ObjTypes, text: String = GrandExchange::class.java.getResource(TAX_EXEMPT_RESOURCE)!!.readText()): Set<Int> {
            val names = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { it.lowercase() }.toSet()
            val counted = Regex("""^(.*)\(\d+\)$""")
            val ids = HashSet<Int>()
            for (id in 0 until objTypes.size) {
                val type = objTypes[id] ?: continue
                if (type.isCert || type.isPlaceholder) continue
                val name = type.name.lowercase()
                if (name in names || counted.find(name)?.groupValues?.get(1) in names) ids += id
            }
            return ids
        }
    }
}

