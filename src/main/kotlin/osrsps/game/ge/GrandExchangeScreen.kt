package osrsps.game.ge

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStockMarketSlotV2
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.bank.BankScreen
import osrsps.game.bank.setVarbitIfKnown

private val logger = KotlinLogging.logger {}

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
 * whether or not the screen is open. Setting up an offer, collecting and the history come in later steps.
 */
class GrandExchangeScreen(
    private val grandExchange: GrandExchange,
    private val openModal: (Player, FrameSlot, Int) -> Unit,
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == MAIN

    /** Opens the offers screen at its index of eight slots. */
    fun open(player: Player) {
        player.vars.setVarbitIfKnown(VARBIT_SELECTED_SLOT, 0)
        openModal(player, FrameSlot.MAINMODAL, MAIN)
        openModal(player, FrameSlot.SIDEMODAL, SIDE)
        for (slot in 0 until GeAccount.SLOTS) {
            send(player, IfSetEventsV2(MAIN, INDEX_FIRST + slot, SUB_FACE, SUB_SELL, 0, BankScreen.opMask(1..3)))
        }
        send(player, IfSetEventsV2(MAIN, BACK, -1, -1, 0, BankScreen.opMask(1..1)))
    }

    /** The screen closed: back to the index for the next opening. */
    fun closed(player: Player) {
        player.vars.setVarbitIfKnown(VARBIT_SELECTED_SLOT, 0)
    }

    /** A click on the offers screen. Returns false when the click isn't on it. */
    fun click(player: Player, interfaceId: Int, component: Int, sub: Int, op: Int): Boolean {
        if (interfaceId != MAIN && interfaceId != SIDE) return false
        if (!isOpen(player)) return true
        if (interfaceId == SIDE) return true
        when (component) {
            BACK -> player.vars.setVarbitIfKnown(VARBIT_SELECTED_SLOT, 0)
            in INDEX_FIRST until INDEX_FIRST + GeAccount.SLOTS -> clickSlot(player, component - INDEX_FIRST, sub, op)
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
                // The setup panel this opens is built in step 3; the vars are set as the client sets them.
                player.vars.setVarbitIfKnown(VARBIT_NEWOFFER_TYPE, if (sub == SUB_SELL) 1 else 0)
                player.vars.setVarbitIfKnown(VARBIT_SELECTED_SLOT, slot + 1)
            }
            else -> logger.info { "${player.username} picked Grand Exchange slot $slot sub $sub op $op (not built yet)" }
        }
    }

    /** Sends the slots and collection boxes that changed since last sent. Run at the end of every cycle. */
    fun flush(player: Player) {
        val sent = player.geSent
        for (slot in 0 until GeAccount.SLOTS) {
            val offer = player.ge[slot]
            if (sent.sentOnce && sent.offers[slot] == offer) continue
            if (!sent.sentOnce && !sent.resync && offer == null) {
                // At login only the slots in use go: the client starts with every slot empty. After a reconnect
                // every slot goes, since a reset queued for the old connection may never have arrived.
                sent.offers[slot] = null
                continue
            }
            send(player, slotUpdate(slot, offer))
            val items = offer?.takeIf { it.collectItems > 0 }?.let { InventoryObject(0, it.item, it.collectItems) } ?: InventoryObject.NULL
            val coins = offer?.takeIf { it.collectCoins > 0 }
                ?.let { InventoryObject(1, GrandExchange.COINS, minOf(it.collectCoins, Int.MAX_VALUE.toLong()).toInt()) } ?: InventoryObject.NULL
            send(player, UpdateInvFull(COLLECT_INVS[slot], 2) { i -> if (i == 0) items else coins })
            sent.offers[slot] = offer
        }
        sent.sentOnce = true
        sent.resync = false
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

        const val VARBIT_SELECTED_SLOT = 4439
        const val VARBIT_NEWOFFER_TYPE = 4397

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
                "ge_offers" to mapOf(BACK to "back") + (0 until GeAccount.SLOTS).associate { INDEX_FIRST + it to "index_$it" }
                ),
            SIDE to ("ge_offers_side" to mapOf(0 to "items")),
        )
    }
}

/** What the client was last sent of a player's Grand Exchange slots; see [GrandExchangeScreen.flush]. */
class GeSent {
    val offers = arrayOfNulls<GeOffer>(GeAccount.SLOTS)
    var sentOnce = false
    var resync = false

    /** Sends every slot again, empty ones included, as after a reconnect. */
    fun resendAll() {
        offers.fill(null)
        sentOnce = false
        resync = true
    }
}
