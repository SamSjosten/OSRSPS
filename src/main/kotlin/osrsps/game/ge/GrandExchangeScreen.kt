package osrsps.game.ge

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStockMarketSlotV2
import net.rsprot.protocol.game.outgoing.varp.VarpLong
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.bank.BankScreen
import osrsps.game.bank.getVarbitIfKnown
import osrsps.game.bank.setVarbitIfKnown
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptRunner

private val logger = KotlinLogging.logger {}

/** The Grand Exchange screens' prompts and messages. Every one's words guessed unless it says otherwise. */
object GrandExchangeLines : Lines("ge", "Grand Exchange") {
    val WHAT_TO_BUY = line("what_to_buy", "the item search's prompt over the chatbox after an empty slot's Buy")
    val HOW_MANY_BUY = line("how_many_buy", "Enter quantity on a buy offer's setup")
    val HOW_MANY_SELL = line("how_many_sell", "Enter quantity on a sell offer's setup")
    val SET_PRICE = line("set_price", "Enter price on an offer's setup")
    val SET_PERCENT = line("set_percent", "Customise on the setup's -X% or +X% button")
    val NO_FREE_SLOT = line("no_free_slot", "Offer on an inventory item with every slot taken")
    val NOT_TRADEABLE = line("not_tradeable", "Offer on, or a search for, an item that can't be traded on the Exchange", "item")
    val CHOOSE_ITEM = line("choose_item", "Confirm before an item is chosen")
    val NOT_ENOUGH_COINS = line("not_enough_coins", "Confirm on a buy offer worth more than the coins and platinum tokens held")
    val NOT_ENOUGH_ITEMS = line("not_enough_items", "Confirm on a sell offer for more than the inventory holds")
    val TOO_MUCH_MONEY = line("too_much_money", "Confirm on an offer worth more than the most an offer can be")
    val NO_ROOM_FOR_CHANGE = line("no_room_for_change", "Confirm when paying with platinum tokens leaves change and no room for it")

    // An offer's progress, sent as it fills or is aborted, whether or not a screen is open.
    val BOUGHT = line("bought", "a buy offer part-filled: {done} of {total} bought", "done", "total", "item")
    val SOLD = line("sold", "a sell offer part-filled: {done} of {total} sold", "done", "total", "item")
    val FINISHED_BUYING = line("finished_buying", "a buy offer completely filled", "total", "item")
    val FINISHED_SELLING = line("finished_selling", "a sell offer completely filled", "total", "item")
    val ABORTED = line("aborted", "Abort offer acknowledged")
    val COLLECT_WAITING = line("collect_waiting", "logging in with something in the collection box")
    val NO_ROOM_TO_COLLECT = line("no_room_to_collect", "collecting to an inventory with no room for it")
}

/**
 * The Grand Exchange's offers screen as the 241 client draws it: ge_offers (465) over the game view with its eight
 * slots, and ge_offers_side (467), the inventory beside it. `::ge` opens it until Varrock's clerks exist.
 *
 * The client draws the slots itself from UPDATE_STOCKMARKET_SLOT ([slotUpdate]) and each slot's collection box from its
 * inv (tradingpost_sell_0 to 5, ge_collect_6 and 7: the invs script816 redraws on, [COLLECT_INVS]): the item in slot 0,
 * the coins in slot 1. Read from the rev 241 client scripts (Joshua-F/osrs-dumps), not checked against live:
 * - ge_offers_index_initslot makes each index_N's children; sub 2 is the slot's face (View offer, Abort offer, Modify
 *   offer as ops 1 to 3), subs 3 and 4 an empty slot's Buy and Sell boxes (op 1). script805 sets varbit ge_selectedslot
 *   (slot + 1, 0 for the index) and ge_newoffer_type in the client; the server sets the same here.
 * - The Back button (465:4) puts ge_selectedslot to 0.
 *
 * Live sends every slot at login (RuneLite's Grand Exchange plugin reads them then), so [flush] sends changed slots
 * whether or not the screen is open.
 *
 * Setting up an offer (ge_offers:setup, 465:26) works on the vars the client draws it from: tradingpost_search (the
 * item), ge_newoffer_quantity, ge_newoffer_price (a long varp, kept in [GeSetup]) and ge_transmit_taxrate. The client's
 * own scripts change the quantity and price as their buttons are clicked (ge_offers_setup_changequantity and
 * changeprice); the server makes the same change from the same click, so both agree. A buy chooses its item from the
 * chatbox item search (meslayer_mode14, which answers with resume_p_objdialog), a sell from ge_offers_side's Offer.
 * Confirm (465:30) places the offer in the selected slot. Left for later: the warning on a sell under a tenth of the
 * guide price, and Modify offer.
 *
 * Collecting: an offer's two boxes on its details (ge_offers:details_collect subs 2 and 3, as script816 makes them),
 * Collect to inventory or bank over the index (ge_offers:collectall sub 0, ge_offers_index_inittop), and the
 * collection box ge_collect (402, `::gecollect` until Varrock's clerks), whose slots' boxes are subs 3 and 4 of
 * collect_0 to 7 (ge_collect_initslot). The ops are as ge_collect_drawitem and script9909 label them: for an item
 * with a note, Collect-item and Collect-note for one, Collect-notes and Collect-items for more; else Collect (op 2);
 * then Bank (op 3) and Examine (op 10).
 */
class GrandExchangeScreen(
    private val grandExchange: GrandExchange,
    private val openModal: (Player, FrameSlot, Int) -> Unit,
    private val scripts: ScriptRunner? = null,
    /** Shows an item's examine text and values, as the inventory's Examine does. */
    private val examine: (Player, Int, Int) -> Unit = { _, _, _ -> },
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    /** Puts a count of an item into the bank; returns how many went in. */
    private val toBank: ((Player, Int, Int) -> Int)? = null,
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == MAIN

    /** Opens the offers screen at its index of eight slots. */
    fun open(player: Player) {
        toIndex(player)
        openModal(player, FrameSlot.MAINMODAL, MAIN)
        openModal(player, FrameSlot.SIDEMODAL, SIDE)
        for (slot in 0 until GeAccount.SLOTS) {
            send(player, IfSetEventsV2(MAIN, INDEX_FIRST + slot, SUB_FACE, SUB_SELL, 0, BankScreen.opMask(1..3)))
        }
        send(player, IfSetEventsV2(MAIN, BACK, -1, -1, 0, BankScreen.opMask(1..1)))
        send(player, IfSetEventsV2(MAIN, SETUP, SETUP_CHOOSE, SETUP_CUSTOM_PLUS, 0, BankScreen.opMask(1..2)))
        send(player, IfSetEventsV2(MAIN, CONFIRM, -1, -1, 0, BankScreen.opMask(1..1)))
        send(player, IfSetEventsV2(MAIN, COLLECT_ALL, 0, 0, 0, BankScreen.opMask(1..2)))
        send(player, IfSetEventsV2(MAIN, DETAILS_COLLECT, DETAILS_BOX_ITEMS, DETAILS_BOX_COINS, 0, COLLECT_OPS))
        send(player, IfSetEventsV2(SIDE, SIDE_ITEMS, 0, Inventory.INV_CAPACITY - 1, 0, BankScreen.opMask(1..1) or BankScreen.opMask(10..10)))
    }

    /** Opens the collection box, ge_collect: every slot's two boxes and Collect to inventory or bank. */
    fun openCollectionBox(player: Player) {
        openModal(player, FrameSlot.MAINMODAL, COLLECTION_BOX)
        for (slot in 0 until GeAccount.SLOTS) {
            send(player, IfSetEventsV2(COLLECTION_BOX, COLLECTION_SLOT_FIRST + slot, COLLECTION_BOX_ITEMS, COLLECTION_BOX_COINS, 0, COLLECT_OPS))
        }
        send(player, IfSetEventsV2(COLLECTION_BOX, COLLECTION_TO_INVENTORY, -1, -1, 0, BankScreen.opMask(1..1)))
        send(player, IfSetEventsV2(COLLECTION_BOX, COLLECTION_TO_BANK, -1, -1, 0, BankScreen.opMask(1..1)))
    }

    /** The screen closed: back to the index for the next opening. */
    fun closed(player: Player) {
        toIndex(player)
    }

    /** Back to the index of eight slots, with nothing set up. */
    private fun toIndex(player: Player) {
        player.vars.setVarbitIfKnown(VARBIT_SELECTED_SLOT, 0)
        clearSetup(player)
    }

    /** Clears the offer being set up: no item, quantity or price. */
    private fun clearSetup(player: Player) {
        player.vars[VARP_SEARCH] = NO_ITEM
        player.vars.setVarbitIfKnown(VARBIT_QUANTITY, 0)
        player.vars.setVarbitIfKnown(VARBIT_TAX_RATE, 0)
        player.geSetup.price = 0
        player.geSetup.searching = false
        player.geSetup.generation++
    }

    /** A click on the offers screen or the collection box. Returns false when the click isn't on either. */
    fun click(player: Player, interfaceId: Int, component: Int, sub: Int, op: Int, obj: Int = -1): Boolean {
        if (interfaceId == COLLECTION_BOX) {
            if (player.modals[FrameSlot.MAINMODAL] == COLLECTION_BOX) clickCollectionBox(player, component, sub, op)
            return true
        }
        if (interfaceId != MAIN && interfaceId != SIDE) return false
        if (!isOpen(player)) return true
        if (interfaceId == SIDE) {
            if (component == SIDE_ITEMS) clickInventory(player, sub, op, obj)
            return true
        }
        when (component) {
            BACK -> toIndex(player)
            in INDEX_FIRST until INDEX_FIRST + GeAccount.SLOTS -> clickSlot(player, component - INDEX_FIRST, sub, op)
            SETUP -> clickSetup(player, sub, op)
            CONFIRM -> confirm(player)
            COLLECT_ALL -> if (sub == 0) collectAll(player, bank = op == 2)
            DETAILS_COLLECT -> {
                val slot = player.vars.getVarbitIfKnown(VARBIT_SELECTED_SLOT) - 1
                if (slot in 0 until GeAccount.SLOTS) clickBox(player, slot, sub == DETAILS_BOX_COINS, op)
            }
            else -> logger.info { "${player.username} clicked Grand Exchange component $component op $op (not built yet)" }
        }
        return true
    }

    private fun clickSlot(player: Player, slot: Int, sub: Int, op: Int) {
        val offer = player.ge[slot]
        when {
            sub == SUB_FACE && offer != null && op == 1 -> player.vars.setVarbitIfKnown(VARBIT_SELECTED_SLOT, slot + 1)
            sub == SUB_FACE && offer != null && op == 2 -> grandExchange.abort(player, slot)
            (sub == SUB_BUY || sub == SUB_SELL) && offer == null && op == 1 -> {
                clearSetup(player)
                player.vars.setVarbitIfKnown(VARBIT_NEWOFFER_TYPE, if (sub == SUB_SELL) TYPE_SELL else TYPE_BUY)
                player.vars.setVarbitIfKnown(VARBIT_SELECTED_SLOT, slot + 1)
                // A buy asks for its item straight away; a sell waits for one from the inventory beside.
                if (sub == SUB_BUY) openSearch(player)
            }
            else -> logger.info { "${player.username} picked Grand Exchange slot $slot sub $sub op $op (not built yet)" }
        }
    }

    /** The game message for an offer that went from [before] to [after]: filled further, or aborted. */
    private fun progress(player: Player, before: GeOffer?, after: GeOffer?) {
        if (before == null || after == null || before.item != after.item || before.type != after.type) return
        val item = grandExchange.itemName(after.item)
        val buying = after.type == OfferType.BUY
        when {
            after.completed > before.completed && after.completed == after.quantity -> message(
                player, if (buying) GrandExchangeLines.FINISHED_BUYING else GrandExchangeLines.FINISHED_SELLING,
                "total" to number(after.quantity.toLong()), "item" to item,
            )
            after.completed > before.completed -> message(
                player, if (buying) GrandExchangeLines.BOUGHT else GrandExchangeLines.SOLD,
                "done" to number(after.completed.toLong()), "total" to number(after.quantity.toLong()), "item" to item,
            )
            after.aborted && !before.aborted -> message(player, GrandExchangeLines.ABORTED)
        }
    }

    private fun number(n: Long): String = String.format(java.util.Locale.ROOT, "%,d", n)

    /** The slot being set up, or -1 when the screen shows the index or an offer already placed. */
    private fun setupSlot(player: Player): Int {
        val slot = player.vars.getVarbitIfKnown(VARBIT_SELECTED_SLOT) - 1
        return if (slot in 0 until GeAccount.SLOTS && player.ge[slot] == null) slot else -1
    }

    private fun type(player: Player): Int = player.vars.getVarbitIfKnown(VARBIT_NEWOFFER_TYPE)

    private fun item(player: Player): Int = player.vars[VARP_SEARCH]

    private fun quantity(player: Player): Int = player.vars.getVarbitIfKnown(VARBIT_QUANTITY)

    private fun setQuantity(player: Player, quantity: Int) {
        val mask = player.vars.types.varbit(VARBIT_QUANTITY)?.mask ?: return
        player.vars.setVarbit(VARBIT_QUANTITY, if (mask == -1) quantity else quantity.coerceIn(0, mask))
    }

    /** The chatbox's item search, over the tradeable items, showing the last item searched for (meslayer_mode14). */
    private fun openSearch(player: Player) {
        player.geSetup.searching = true
        send(player, RunClientScript(MESLAYER_MODE14, listOf(words(player)[GrandExchangeLines.WHAT_TO_BUY], 1, -1, 1)))
    }

    /**
     * The client picked [obj] in the item search (resume_p_objdialog). Returns false when no search waits for one: the
     * packet is then someone else's, or old.
     */
    fun chooseItem(player: Player, obj: Int): Boolean {
        if (!player.geSetup.searching) return false
        player.geSetup.searching = false
        if (!isOpen(player) || setupSlot(player) < 0 || type(player) != TYPE_BUY) return true
        val price = grandExchange.guidePrice(obj)
        if (price == null) {
            message(player, GrandExchangeLines.NOT_TRADEABLE, "item" to (grandExchange.itemName(obj)))
            return true
        }
        player.vars[VARP_LAST_SEARCHED] = obj
        setItem(player, obj, price)
        return true
    }

    /**
     * The item an offer is being set up for, at its guide price (the Wiki: "the initial price is set to a guide value").
     * GUESSED: the quantity starts at 1, as the setup's +1, +10 and +100 buttons for a sell suggest.
     */
    private fun setItem(player: Player, item: Int, price: Long) {
        player.vars[VARP_SEARCH] = item
        player.geSetup.generation++
        setQuantity(player, 1)
        player.geSetup.price = price
        player.vars.setVarbitIfKnown(VARBIT_TAX_RATE, grandExchange.taxRate(item))
    }

    /** Offer (op 1) on an inventory item beside the screen: a sell offer for it, in the slot set up or the first free. */
    private fun clickInventory(player: Player, invSlot: Int, op: Int, clicked: Int) {
        if (invSlot !in 0 until Inventory.INV_CAPACITY) return
        // A click sent before the slot changed is for an item no longer there.
        val obj = player.inventory[invSlot]?.takeIf { it.id == clicked } ?: return
        if (op == 10) {
            examine(player, obj.id, obj.count)
            return
        }
        if (op != 1) return
        val selected = player.vars.getVarbitIfKnown(VARBIT_SELECTED_SLOT) - 1
        // As ge_offer_side_draw offers it: at the index, on an empty slot set up to sell, or beside an offer placed.
        val slot = when {
            selected !in 0 until GeAccount.SLOTS || player.ge[selected] != null -> player.ge.freeSlot()
            type(player) == TYPE_SELL -> selected
            else -> return
        }
        if (slot < 0) {
            message(player, GrandExchangeLines.NO_FREE_SLOT)
            return
        }
        val item = grandExchange.offerItem(obj.id)
        val price = grandExchange.guidePrice(item)
        if (price == null) {
            message(player, GrandExchangeLines.NOT_TRADEABLE, "item" to grandExchange.itemName(item))
            return
        }
        clearSetup(player)
        player.vars.setVarbitIfKnown(VARBIT_NEWOFFER_TYPE, TYPE_SELL)
        player.vars.setVarbitIfKnown(VARBIT_SELECTED_SLOT, slot + 1)
        setItem(player, item, price)
    }

    /** A click on the setup panel's children, as ge_offers_setup_init makes them. */
    private fun clickSetup(player: Player, sub: Int, op: Int) {
        if (setupSlot(player) < 0) return
        if (sub == SETUP_CHOOSE) {
            if (op == 1 && type(player) == TYPE_BUY) openSearch(player)
            return
        }
        // Every other button works on the chosen item, as the client's scripts return without one.
        if (item(player) == NO_ITEM) return
        val selling = type(player) == TYPE_SELL
        when (sub) {
            SETUP_QUANTITY_MINUS_1 -> if (op == 1) changeQuantity(player, -1)
            SETUP_QUANTITY_PLUS_1, SETUP_QUANTITY_1 -> if (op == 1) changeQuantity(player, 1)
            SETUP_QUANTITY_10 -> if (op == 1) changeQuantity(player, 10)
            SETUP_QUANTITY_100 -> if (op == 1) changeQuantity(player, 100)
            // All for a sell, +1K for a buy.
            SETUP_QUANTITY_ALL -> if (op == 1) changeQuantity(player, Int.MAX_VALUE)
            SETUP_QUANTITY_ENTER -> when (op) {
                1 -> ask(player, if (selling) GrandExchangeLines.HOW_MANY_SELL else GrandExchangeLines.HOW_MANY_BUY) { amount ->
                    if (amount > 0) setQuantity(player, amount)
                }
                // A buy's All: as many as the coins and platinum tokens held pay for (script5156).
                2 -> if (!selling) affordAll(player)
            }
            SETUP_PRICE_MINUS_1 -> if (op == 1) changePrice(player, -1)
            SETUP_PRICE_PLUS_1 -> if (op == 1) changePrice(player, 1)
            SETUP_PRICE_MINUS_5 -> if (op == 1) changePrice(player, -5)
            SETUP_PRICE_PLUS_5 -> if (op == 1) changePrice(player, 5)
            SETUP_PRICE_GUIDE -> if (op == 1) grandExchange.guidePrice(item(player))?.let { player.geSetup.price = it }
            SETUP_PRICE_ENTER -> if (op == 1) {
                ask(player, GrandExchangeLines.SET_PRICE) { amount -> if (amount > 0) player.geSetup.price = amount.toLong() }
            }
            SETUP_CUSTOM_MINUS, SETUP_CUSTOM_PLUS -> when (op) {
                1 -> changePriceByCustom(player, up = sub == SETUP_CUSTOM_PLUS)
                2 -> ask(player, GrandExchangeLines.SET_PERCENT) { amount ->
                    val mask = player.vars.types.varbit(VARBIT_PRICE_CUSTOM)?.mask ?: return@ask
                    player.vars.setVarbit(VARBIT_PRICE_CUSTOM, if (mask == -1) amount else amount.coerceIn(0, mask))
                }
            }
        }
    }

    /** ge_offers_setup_changequantity: by [by], or [Int.MAX_VALUE] for a sell's All or a buy's +1K. */
    private fun changeQuantity(player: Player, by: Int) {
        var change = by
        val quantity = quantity(player)
        if (item(player) == BOND) {
            setQuantity(player, 1)
            return
        }
        val most = if (type(player) == TYPE_SELL) {
            // ge_offers_checkavailable: the item and its note in the inventory, at least 1.
            grandExchange.available(player.inventory, item(player)).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
        } else {
            if (change == Int.MAX_VALUE) change = 1000
            Int.MAX_VALUE
        }
        val next = when {
            change == Int.MAX_VALUE -> most
            change > 0 -> {
                if (change > 1 && quantity == 1) change -= 1
                if (most - change < quantity) most else quantity + change
            }
            quantity <= -change -> 1
            else -> quantity + change
        }
        setQuantity(player, next)
    }

    /** ge_offers_setup_changeprice: 1 or -1 coin, or 5 or -5 for 5% (a twentieth, at least 1 coin). */
    private fun changePrice(player: Player, by: Int) {
        val price = player.geSetup.price
        player.geSetup.price = when (by) {
            1 -> if (price < GrandExchange.MAX_TOTAL) price + 1 else price
            -1 -> if (price > 1) price - 1 else price
            5 -> {
                val step = maxOf(1, price / 20)
                if (GrandExchange.MAX_TOTAL - step < price) GrandExchange.MAX_TOTAL else price + step
            }
            else -> {
                val step = maxOf(1, price / 20)
                if (step >= price) 1 else price - step
            }
        }
    }

    /**
     * The -X% and +X% buttons: the price by varbit ge_price_custom percent. The client's script5154 only plays a sound,
     * so GUESSED: as its 5% buttons do, rounded down and at least 1 coin.
     */
    private fun changePriceByCustom(player: Player, up: Boolean) {
        val percent = player.vars.getVarbitIfKnown(VARBIT_PRICE_CUSTOM)
        if (percent <= 0) return
        val price = player.geSetup.price
        val step = maxOf(1, price * percent / 100)
        player.geSetup.price = if (up) minOf(GrandExchange.MAX_TOTAL, price + step) else maxOf(1, price - step)
    }

    /** script5156: a buy's quantity to as many as the coins and platinum tokens held pay for, at least 1. */
    private fun affordAll(player: Player) {
        val price = player.geSetup.price
        if (price <= 0) return
        val spendable = grandExchange.spendable(player.inventory)
        setQuantity(player, (spendable / price).coerceIn(1, Int.MAX_VALUE.toLong()).toInt())
    }

    /** Asks for an amount with [prompt], then hands it to [then] if the same offer is still being set up. */
    private fun ask(player: Player, prompt: Line, then: (Int) -> Unit) {
        val runner = scripts ?: return
        if (player.scripts.delayed) return
        val generation = player.geSetup.generation
        runner.start(player) {
            val amount = countDialog(words(player)[prompt])
            if (isOpen(player) && player.geSetup.generation == generation && item(player) != NO_ITEM) then(amount)
        }
    }

    /** Confirm: places the offer set up in its slot, then back to the index; or says why not. */
    private fun confirm(player: Player) {
        val slot = setupSlot(player)
        if (slot < 0) return
        val item = item(player)
        if (item == NO_ITEM) {
            message(player, GrandExchangeLines.CHOOSE_ITEM)
            return
        }
        val quantity = quantity(player)
        val price = player.geSetup.price
        val result = if (type(player) == TYPE_SELL) {
            grandExchange.sell(player, item, quantity, price, slot)
        } else {
            grandExchange.buy(player, item, quantity, price, slot)
        }
        when (result) {
            PlaceResult.PLACED -> toIndex(player)
            PlaceResult.NOT_ENOUGH_COINS -> message(player, GrandExchangeLines.NOT_ENOUGH_COINS)
            PlaceResult.NOT_ENOUGH_ITEMS -> message(player, GrandExchangeLines.NOT_ENOUGH_ITEMS)
            PlaceResult.TOO_EXPENSIVE -> message(player, GrandExchangeLines.TOO_MUCH_MONEY)
            PlaceResult.NO_ROOM_FOR_CHANGE -> message(player, GrandExchangeLines.NO_ROOM_FOR_CHANGE)
            PlaceResult.NOT_TRADEABLE, PlaceResult.NOT_ON_EXCHANGE ->
                message(player, GrandExchangeLines.NOT_TRADEABLE, "item" to grandExchange.itemName(item))
            // The client draws Confirm greyed out for these; a forged click gets nothing.
            PlaceResult.NO_FREE_SLOT, PlaceResult.BAD_AMOUNT -> {}
        }
    }

    private fun clickCollectionBox(player: Player, component: Int, sub: Int, op: Int) {
        when (component) {
            COLLECTION_TO_INVENTORY -> collectAll(player, bank = false)
            COLLECTION_TO_BANK -> collectAll(player, bank = true)
            in COLLECTION_SLOT_FIRST until COLLECTION_SLOT_FIRST + GeAccount.SLOTS ->
                if (sub == COLLECTION_BOX_ITEMS || sub == COLLECTION_BOX_COINS) {
                    clickBox(player, component - COLLECTION_SLOT_FIRST, sub == COLLECTION_BOX_COINS, op)
                }
        }
    }

    /** A click on one of [slot]'s two boxes: its items, or its coins (and platinum tokens). */
    private fun clickBox(player: Player, slot: Int, coinsBox: Boolean, op: Int) {
        val offer = player.ge[slot] ?: return
        val count = if (coinsBox) offer.collectCoins else offer.collectItems.toLong()
        if (count <= 0) return
        when (op) {
            10 -> {
                val id = if (coinsBox) GrandExchange.COINS else offer.item
                examine(player, id, minOf(count, Int.MAX_VALUE.toLong()).toInt())
            }
            3 -> collect(player, pending = true) { grandExchange.collect(player, slot, items = !coinsBox, coins = coinsBox, toBank = toBank ?: return@collect false) }
            1, 2 -> {
                // Op 1 is Collect-item for one and Collect-notes for more; op 2 the other. Coins have only op 2.
                if (coinsBox && op == 1) return
                val notes = if (op == 1) count > 1 else count == 1L
                collect(player, pending = true) { grandExchange.collect(player, slot, notes = notes, items = !coinsBox, coins = coinsBox) }
            }
        }
    }

    private fun collectAll(player: Player, bank: Boolean) {
        val toBank = if (bank) toBank ?: return else null
        val pending = player.ge.contents().any { (_, offer) -> offer.collectItems > 0 || offer.collectCoins > 0 }
        collect(player, pending) { grandExchange.collectAll(player, toBank) }
    }

    /** Runs [collect]; when nothing moved though something was [pending], the inventory or bank had no room. */
    private fun collect(player: Player, pending: Boolean, collect: () -> Boolean) {
        if (!collect() && pending) message(player, GrandExchangeLines.NO_ROOM_TO_COLLECT)
    }

    private fun message(player: Player, line: Line, vararg values: Pair<String, Any>) =
        send(player, MessageGame(CHATTYPE_GAMEMESSAGE, words(player).get(line, *values)))

    /**
     * Sends the slots and collection boxes that changed since last sent, with a game message for each offer that filled
     * further or was aborted, and at login one for anything waiting to be collected. Run at the end of every cycle.
     */
    fun flush(player: Player) {
        val sent = player.geSent
        if (!sent.sentOnce && !sent.resync && player.ge.contents().any { (_, offer) -> offer.collectItems > 0 || offer.collectCoins > 0 }) {
            message(player, GrandExchangeLines.COLLECT_WAITING)
        }
        for (slot in 0 until GeAccount.SLOTS) {
            val offer = player.ge[slot]
            if (sent.sentOnce && sent.offers[slot] == offer) continue
            if (!sent.sentOnce && !sent.resync && offer == null) {
                // At login only the slots in use go: the client starts with every slot empty. After a reconnect
                // every slot goes, since a reset queued for the old connection may never have arrived.
                sent.offers[slot] = null
                continue
            }
            if (sent.sentOnce) progress(player, sent.offers[slot], offer)
            send(player, slotUpdate(slot, offer))
            val items = offer?.takeIf { it.collectItems > 0 }?.let { InventoryObject(0, it.item, it.collectItems) } ?: InventoryObject.NULL
            val (coinCount, tokenCount) = GrandExchange.payout(offer?.collectCoins ?: 0)
            val coins = if (coinCount > 0) InventoryObject(1, GrandExchange.COINS, coinCount.toInt()) else InventoryObject.NULL
            val tokens = if (tokenCount > 0) InventoryObject(2, GrandExchange.PLATINUM_TOKEN, tokenCount.toInt()) else InventoryObject.NULL
            send(player, UpdateInvFull(COLLECT_INVS[slot], 3) { i -> if (i == 0) items else if (i == 1) coins else tokens })
            sent.offers[slot] = offer
        }
        sent.sentOnce = true
        sent.resync = false
        // ge_newoffer_price is a long varp, which PlayerVars doesn't hold; sent here like the others, when it changes.
        if (sent.price != player.geSetup.price) {
            send(player, VarpLong(VARP_PRICE, player.geSetup.price))
            sent.price = player.geSetup.price
        }
    }

    companion object {
        const val MAIN = 465
        const val SIDE = 467

        /** ge_offers:back. */
        const val BACK = 4

        /** ge_offers:index_0, the first of eight in a row. */
        const val INDEX_FIRST = 7

        /** The children ge_offers_index_initslot makes in each index_N: the face, then an empty slot's Buy and Sell. */
        const val SUB_FACE = 2
        const val SUB_BUY = 3
        const val SUB_SELL = 4

        /** ge_offers:setup, whose children ge_offers_setup_init makes: the item, then the quantity and price buttons. */
        const val SETUP = 26
        const val SETUP_CHOOSE = 0
        const val SETUP_QUANTITY_MINUS_1 = 1
        const val SETUP_QUANTITY_PLUS_1 = 2
        const val SETUP_QUANTITY_1 = 3
        const val SETUP_QUANTITY_10 = 4
        const val SETUP_QUANTITY_100 = 5
        const val SETUP_QUANTITY_ALL = 6
        const val SETUP_QUANTITY_ENTER = 7
        const val SETUP_PRICE_MINUS_1 = 8
        const val SETUP_PRICE_PLUS_1 = 9
        const val SETUP_PRICE_MINUS_5 = 10
        const val SETUP_PRICE_GUIDE = 11
        const val SETUP_PRICE_ENTER = 12
        const val SETUP_PRICE_PLUS_5 = 13
        const val SETUP_CUSTOM_MINUS = 14
        const val SETUP_CUSTOM_PLUS = 15

        /** ge_offers:setup_confirm. */
        const val CONFIRM = 30

        /** ge_offers:collectall, whose sub 0 is Collect to inventory (op 1) or bank (op 2). */
        const val COLLECT_ALL = 6

        /** ge_offers:details_collect, the details' two boxes as script816 makes them. */
        const val DETAILS_COLLECT = 24
        const val DETAILS_BOX_ITEMS = 2
        const val DETAILS_BOX_COINS = 3

        /** ge_collect, the collection box: Collect to inventory and to bank, then collect_0 to 7 with two boxes each. */
        const val COLLECTION_BOX = 402
        const val COLLECTION_TO_INVENTORY = 3
        const val COLLECTION_TO_BANK = 4
        const val COLLECTION_SLOT_FIRST = 5
        const val COLLECTION_BOX_ITEMS = 3
        const val COLLECTION_BOX_COINS = 4

        /** Collect (ops 1 and 2), Bank (3) and Examine (10) on a box. */
        private val COLLECT_OPS = BankScreen.opMask(1..3) or BankScreen.opMask(10..10)

        /** ge_offers_side:items, the inventory beside the screen, one sub per slot. */
        const val SIDE_ITEMS = 0

        const val VARBIT_SELECTED_SLOT = 4439
        const val VARBIT_NEWOFFER_TYPE = 4397
        const val VARBIT_QUANTITY = 4396
        const val VARBIT_PRICE_CUSTOM = 4284
        const val VARBIT_TAX_RATE = 13139
        const val TYPE_BUY = 0
        const val TYPE_SELL = 1

        /** tradingpost_search, the item set up (an obj varp, so -1 for none), and ge_last_searched. */
        const val VARP_SEARCH = 1151
        const val VARP_LAST_SEARCHED = 2674
        const val NO_ITEM = -1

        /** ge_newoffer_price, a long varp. */
        const val VARP_PRICE = 5753

        /** meslayer_mode14 in the 241 cache: the chatbox's item search. */
        const val MESLAYER_MODE14 = 750

        /** Old school bond, which the setup holds at a quantity of 1. */
        const val BOND = 13190

        private const val CHATTYPE_GAMEMESSAGE = 0

        /** Each slot's collection box: tradingpost_sell_0 to 5, then ge_collect_6 and 7. */
        val COLLECT_INVS = listOf(518, 519, 520, 521, 522, 523, 539, 540)

        /**
         * A slot's status byte. Inferred from RuneLite's RSGrandExchangeOffer, which reads the low three bits as the state
         * (2 active, 5 finished, completed or aborted) and bit 3 as selling.
         */
        const val STATUS_ACTIVE = 2
        const val STATUS_FINISHED = 5
        const val STATUS_SELLING = 8

        /** The client's view of [slot]: empty for no offer. */
        fun slotUpdate(slot: Int, offer: GeOffer?): UpdateStockMarketSlotV2 {
            if (offer == null) return UpdateStockMarketSlotV2(slot, UpdateStockMarketSlotV2.ResetStockMarketSlot)
            val state = if (offer.finished) STATUS_FINISHED else STATUS_ACTIVE
            val status = state or if (offer.type == OfferType.SELL) STATUS_SELLING else 0
            return UpdateStockMarketSlotV2(
                slot,
                UpdateStockMarketSlotV2.SetStockMarketSlot(status, offer.item, offer.price, offer.quantity, offer.completed, offer.exchanged),
            )
        }

        /** Every id above with its gameval name in the 241 cache, for [osrsps.cache.GameFrameCheck]. */
        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            MAIN to (
                "ge_offers" to mapOf(
                    BACK to "back", COLLECT_ALL to "collectall", DETAILS_COLLECT to "details_collect", SETUP to "setup", CONFIRM to "setup_confirm",
                ) + (0 until GeAccount.SLOTS).associate { INDEX_FIRST + it to "index_$it" }
                ),
            SIDE to ("ge_offers_side" to mapOf(SIDE_ITEMS to "items")),
            COLLECTION_BOX to (
                "ge_collect" to mapOf(COLLECTION_TO_INVENTORY to "collect_inv", COLLECTION_TO_BANK to "collect_bank") +
                    (0 until GeAccount.SLOTS).associate { COLLECTION_SLOT_FIRST + it to "collect_$it" }
                ),
        )
    }
}

/** What the client was last sent of a player's Grand Exchange slots; see [GrandExchangeScreen.flush]. */
class GeSent {
    val offers = arrayOfNulls<GeOffer>(GeAccount.SLOTS)
    var sentOnce = false
    var resync = false

    /** The ge_newoffer_price last sent; the client starts at 0. */
    var price = 0L

    /** Sends every slot again, empty ones included, as after a reconnect. */
    fun resendAll() {
        offers.fill(null)
        sentOnce = false
        resync = true
        price = -1
    }
}

/** The offer a player is setting up, past what its varps hold. Not saved: an offer set up and not confirmed is gone. */
class GeSetup {
    /** ge_newoffer_price. */
    var price = 0L

    /** Whether the chatbox's item search is open for a buy, waiting on resume_p_objdialog. */
    var searching = false

    /** Counts each new setup, so an amount asked for one is never applied to the next. */
    var generation = 0
}
