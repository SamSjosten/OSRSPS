package osrsps.game.bank

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.Inventory
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.PlayerVars
import osrsps.game.sendInventory
import osrsps.game.script.ScriptContext
import osrsps.game.script.ScriptRunner

private val logger = KotlinLogging.logger {}

/**
 * The bank as the player sees it: bankmain (12) over the game view and bankside (15), the inventory beside it, as
 * RSMod opens them. Withdrawing, depositing, the amount prompt, notes, rearranging, depositing the inventory and worn
 * items, and the buttons along the bottom.
 *
 * Interface, component, varp and varbit ids are the 241 cache's (gameval names in [CACHE_NAMES] and the comments). The
 * menus are the client's own: script669 builds a bank slot's (Withdraw-1, -5, -10, -X, -All, -All-but-1,
 * Placeholder, then Examine as op 11) and bankside_drawitem an inventory slot's (Deposit-1 as op 3 up to Deposit-All
 * as op 8, Examine as op 10), from the varbits set here. The buttons carry their ops in the cache (transmitop), so they
 * need no events; the slots get theirs on opening, as RSMod sends them.
 *
 * Not built yet (the plan's step 3): bank tabs, the worn items view, search, the deposit box, bank fillers and the
 * menu's other settings. Their buttons do nothing.
 */
class BankScreen(
    private val bank: Bank,
    private val objTypes: ObjTypes,
    private val scripts: ScriptRunner,
    private val openModal: (Player, FrameSlot, Int) -> Unit,
    /** Shows an item's examine text and values, as the inventory's Examine does. */
    private val examine: (Player, Int, Int) -> Unit,
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == MAIN

    /** Opens the bank, as a booth's Bank or a banker's do. */
    fun open(player: Player) {
        send(player, RunClientScript(MAINMODAL_BACKGROUND_SCRIPT, listOf(-1, -2)))
        // RSMod's emulation: every opening starts withdrawing items, not notes. Not checked against live.
        player.vars.setVarbitIfKnown(VARBIT_WITHDRAW_NOTES, 0)
        openModal(player, FrameSlot.MAINMODAL, MAIN)
        openModal(player, FrameSlot.SIDEMODAL, SIDE)
        send(player, IfSetEventsV2(MAIN, MAIN_ITEMS, 0, Inventory.BANK_CAPACITY - 1, EVENTS_DRAG_DEPTH2 or EVENTS_DRAG_TARGET, opMask(1..11)))
        send(player, IfSetEventsV2(SIDE, SIDE_ITEMS, 0, Inventory.INV_CAPACITY - 1, EVENTS_DRAG_DEPTH1 or EVENTS_DRAG_TARGET, opMask(1..10)))
        send(player, IfSetText(MAIN, MAIN_CAPACITY, bank.capacity().toString()))
        player.bank.resendAll()
    }

    /** Sends the bank's changes while it is open. Run at the end of every cycle, after the cycle's clicks. */
    fun flush(player: Player) {
        if (isOpen(player)) sendInventory(player, player.bank, send)
    }

    /** A click on the bank or its inventory. Returns false when the click isn't on either. */
    fun click(player: Player, interfaceId: Int, component: Int, sub: Int, obj: Int, op: Int): Boolean {
        if (interfaceId == SIDE) {
            if (component != SIDE_ITEMS) return false
            if (isOpen(player)) clickInventory(player, sub, obj, op)
            return true
        }
        if (interfaceId != MAIN) return false
        if (!isOpen(player)) return true
        when (component) {
            MAIN_ITEMS -> clickBank(player, sub, obj, op)
            SWAP_INSERT -> toggle(player, VARBIT_INSERT_MODE)
            NOTE -> toggle(player, VARBIT_WITHDRAW_NOTES)
            PLACEHOLDER -> toggle(player, VARBIT_LEAVE_PLACEHOLDERS)
            QUANTITY_1 -> setQuantityType(player, QUANTITY_TYPE_1)
            QUANTITY_5 -> setQuantityType(player, QUANTITY_TYPE_5)
            QUANTITY_10 -> setQuantityType(player, QUANTITY_TYPE_10)
            QUANTITY_ALL -> setQuantityType(player, QUANTITY_TYPE_ALL)
            QUANTITY_X -> if (op == 2) {
                start(player) {
                    val amount = countDialog()
                    if (amount > 0 && isOpen(player)) {
                        player.vars.setVarbitIfKnown(VARBIT_REQUESTED_QUANTITY, amount)
                        setQuantityType(player, QUANTITY_TYPE_X)
                    }
                }
            } else {
                setQuantityType(player, QUANTITY_TYPE_X)
            }
            DEPOSIT_INVENTORY -> depositAll(player, player.inventory)
            DEPOSIT_WORN -> depositAll(player, player.equipment)
            else -> logger.info { "${player.username} clicked bank component $component op $op (not built yet)" }
        }
        return true
    }

    /** The player dragged a bank or inventory slot onto another of the same. Returns false for any other drag. */
    fun drag(player: Player, interfaceId: Int, component: Int, from: Int, fromObj: Int, toInterfaceId: Int, toComponent: Int, to: Int): Boolean {
        val main = interfaceId == MAIN && component == MAIN_ITEMS
        val side = interfaceId == SIDE && component == SIDE_ITEMS
        if (!main && !side) return false
        if (!isOpen(player) || toInterfaceId != interfaceId || toComponent != component) return true
        val inv = if (main) player.bank else player.inventory
        val held = inv.takeIf { from in 0 until it.capacity }?.get(from)
        if (held == null || held.id != fromObj || player.scripts.delayed) {
            // The client may have moved it already; put both slots back as they are.
            if (from in 0 until inv.capacity) inv.resend(from)
            if (to in 0 until inv.capacity) inv.resend(to)
            return true
        }
        if (main) {
            val insert = player.vars.getVarbitIfKnown(VARBIT_INSERT_MODE) == 1
            if (!bank.move(inv, from, to, insert)) inv.resend(from)
        } else if (to in 0 until Inventory.INV_CAPACITY && to != from) {
            inv.swap(from, to)
        }
        return true
    }

    private fun clickBank(player: Player, slot: Int, obj: Int, op: Int) {
        val held = player.bank.takeIf { slot in 0 until Inventory.BANK_CAPACITY }?.get(slot)
        if (held == null || held.id != obj) {
            logger.debug { "${player.username} clicked bank slot $slot as $obj, but it holds $held" }
            return
        }
        val type = objTypes[held.id] ?: return
        if (op == MAIN_EXAMINE) {
            examine(player, held.id, held.count)
            return
        }
        if (player.scripts.delayed) return
        if (type.isPlaceholder) {
            if (op == MAIN_RELEASE) bank.releasePlaceholder(player.bank, slot)
            return
        }
        val leave = player.vars.getVarbitIfKnown(VARBIT_LEAVE_PLACEHOLDERS) == 1
        when (op) {
            1 -> withdraw(player, slot, held.id, defaultQuantity(player, held.count), leave)
            2 -> withdraw(player, slot, held.id, 1, leave)
            3 -> withdraw(player, slot, held.id, 5, leave)
            4 -> withdraw(player, slot, held.id, 10, leave)
            5 -> withdraw(player, slot, held.id, requested(player), leave)
            6 -> start(player) {
                val amount = askAmount()
                if (amount > 0) withdraw(player, slot, held.id, amount, leave)
            }
            7 -> withdraw(player, slot, held.id, held.count, leave)
            8 -> if (held.count > 1) withdraw(player, slot, held.id, held.count - 1, leave)
            10 -> withdraw(player, slot, held.id, held.count, placeholder = true)
            else -> logger.info { "${player.username} picked bank op $op on $type (not built yet)" }
        }
    }

    private fun clickInventory(player: Player, slot: Int, obj: Int, op: Int) {
        val held = player.inventory.takeIf { slot in 0 until Inventory.INV_CAPACITY }?.get(slot)
        if (held == null || held.id != obj) return
        if (op == SIDE_EXAMINE) {
            examine(player, held.id, held.count)
            return
        }
        if (player.scripts.delayed) return
        when (op) {
            2 -> deposit(player, slot, held.id, defaultQuantity(player, Int.MAX_VALUE))
            3 -> deposit(player, slot, held.id, 1)
            4 -> deposit(player, slot, held.id, 5)
            5 -> deposit(player, slot, held.id, 10)
            6 -> deposit(player, slot, held.id, requested(player))
            7 -> start(player) {
                val amount = askAmount()
                if (amount > 0) deposit(player, slot, held.id, amount)
            }
            8 -> deposit(player, slot, held.id, Int.MAX_VALUE)
            else -> logger.info { "${player.username} picked bank inventory op $op on ${held.id} (not built yet)" }
        }
    }

    /**
     * Withdraws from [slot], if it still holds [id]: the click may have waited on an amount prompt. The messages are
     * RSMod's, as it has them from live.
     */
    private fun withdraw(player: Player, slot: Int, id: Int, count: Int, placeholder: Boolean) {
        if (!isOpen(player) || player.bank[slot]?.id != id) return
        val notes = player.vars.getVarbitIfKnown(VARBIT_WITHDRAW_NOTES) == 1
        val result = bank.withdraw(player.bank, player.inventory, slot, count, notes, placeholder)
        if (result.count == 0) {
            if (result.short) message(player, NO_INVENTORY_SPACE)
            return
        }
        if (result.notNoted) message(player, CANNOT_NOTE)
        if (result.short) message(player, NOT_ENOUGH_SPACE_FOR_ALL)
    }

    private fun deposit(player: Player, slot: Int, id: Int, count: Int) {
        if (!isOpen(player) || player.inventory[slot]?.id != id) return
        when (bank.deposit(player.bank, player.inventory, slot, count)) {
            Bank.Deposit.NotBankable -> message(player, NOT_BANKABLE)
            Bank.Deposit.BankFull -> message(player, BANK_FULL)
            Bank.Deposit.FullStack, is Bank.Deposit.Partly -> message(player, FULL_STACK)
            is Bank.Deposit.Done, Bank.Deposit.Nothing -> Unit
        }
    }

    private fun depositAll(player: Player, from: Inventory) {
        if (player.scripts.delayed) return
        if (from.isEmpty()) {
            message(player, NOTHING_TO_DEPOSIT)
            return
        }
        val result = bank.depositAll(player.bank, from)
        when {
            result.noRoom > 0 -> message(player, CANNOT_HOLD)
            result.notBankable > 0 && result.deposited == 0 -> message(player, NONE_BANKABLE)
            result.notBankable > 0 -> message(player, SOME_NOT_BANKABLE)
        }
    }

    /** What the left-click option takes: the quantity type's amount, All being [all]. */
    private fun defaultQuantity(player: Player, all: Int): Int = when (player.vars.getVarbitIfKnown(VARBIT_QUANTITY_TYPE)) {
        QUANTITY_TYPE_5 -> 5
        QUANTITY_TYPE_10 -> 10
        QUANTITY_TYPE_X -> requested(player)
        QUANTITY_TYPE_ALL -> all
        else -> 1
    }

    /** The last amount typed, at least 1, as the client's own Withdraw-X label has it (script2579). */
    private fun requested(player: Player): Int = maxOf(1, player.vars.getVarbitIfKnown(VARBIT_REQUESTED_QUANTITY))

    /** Asks for an amount, which becomes the requested quantity the client offers next to Withdraw-X (op 5). */
    private suspend fun ScriptContext.askAmount(): Int {
        val amount = countDialog()
        if (amount > 0) player.vars.setVarbitIfKnown(VARBIT_REQUESTED_QUANTITY, amount)
        return amount
    }

    private fun start(player: Player, script: suspend ScriptContext.() -> Unit) {
        if (!player.scripts.delayed) scripts.start(player, script)
    }

    private fun toggle(player: Player, varbit: Int) {
        player.vars.setVarbitIfKnown(varbit, 1 - player.vars.getVarbitIfKnown(varbit))
    }

    private fun setQuantityType(player: Player, type: Int) = player.vars.setVarbitIfKnown(VARBIT_QUANTITY_TYPE, type)

    private fun message(player: Player, text: String) = send(player, MessageGame(CHATTYPE_GAMEMESSAGE, text))

    companion object {
        /** bankmain and its components. */
        const val MAIN = 12
        const val MAIN_CAPACITY = 8
        const val MAIN_ITEMS = 12
        const val SWAP_INSERT = 23
        const val NOTE = 25
        const val QUANTITY_1 = 29
        const val QUANTITY_5 = 31
        const val QUANTITY_10 = 33
        const val QUANTITY_X = 35
        const val QUANTITY_ALL = 37
        const val PLACEHOLDER = 40
        const val DEPOSIT_INVENTORY = 47
        const val DEPOSIT_WORN = 49

        /** bankside and its inventory. */
        const val SIDE = 15
        const val SIDE_ITEMS = 3

        /** A bank slot's Examine (op 11 in 241; 10 in RSMod's rev 233) and a placeholder's Release. */
        const val MAIN_EXAMINE = 11
        const val MAIN_RELEASE = 8
        const val SIDE_EXAMINE = 10

        /** toplevel_mainmodal_background, which RSMod runs with -1, -2 before the bank opens. */
        const val MAINMODAL_BACKGROUND_SCRIPT = 917

        const val VARBIT_LEAVE_PLACEHOLDERS = 3755
        const val VARBIT_WITHDRAW_NOTES = 3958
        const val VARBIT_INSERT_MODE = 3959
        const val VARBIT_REQUESTED_QUANTITY = 3960
        const val VARBIT_QUANTITY_TYPE = 6590

        /** bank_quantity_type's values, as script2579 reads them. */
        const val QUANTITY_TYPE_1 = 0
        const val QUANTITY_TYPE_5 = 1
        const val QUANTITY_TYPE_10 = 2
        const val QUANTITY_TYPE_X = 3
        const val QUANTITY_TYPE_ALL = 4

        /** Event bits as RSMod's IfEvent has them: drag depth in bits 17 to 19, drag target bit 20, op n in the high word's bit n - 1. */
        const val EVENTS_DRAG_DEPTH1 = 1 shl 17
        const val EVENTS_DRAG_DEPTH2 = 2 shl 17
        const val EVENTS_DRAG_TARGET = 1 shl 20

        fun opMask(ops: IntRange): Int = ops.fold(0) { mask, op -> mask or (1 shl (op - 1)) }

        const val NO_INVENTORY_SPACE = "You don't have enough inventory space."
        const val NOT_ENOUGH_SPACE_FOR_ALL = "You don't have enough inventory space to withdraw that many."
        const val CANNOT_NOTE = "This item cannot be withdrawn as a note."
        const val NOT_BANKABLE = "A magical force prevents you from banking this item!"
        const val BANK_FULL = "You don't have enough space in your bank account."
        const val FULL_STACK = "You already have a full stack of that item in the bank."
        const val NOTHING_TO_DEPOSIT = "You have nothing to deposit."
        const val CANNOT_HOLD = "Your bank cannot hold your items."
        const val NONE_BANKABLE = "Your items cannot be stored in the bank."
        const val SOME_NOT_BANKABLE = "Some of your items cannot be stored in the bank."
        private const val CHATTYPE_GAMEMESSAGE = 0

        /** Every id above with its gameval name in the 241 cache, for [osrsps.cache.GameFrameCheck]. */
        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            MAIN to (
                "bankmain" to mapOf(
                    MAIN_CAPACITY to "capacity", MAIN_ITEMS to "items", SWAP_INSERT to "swap_insert", NOTE to "note",
                    QUANTITY_1 to "quantity1", QUANTITY_5 to "quantity5", QUANTITY_10 to "quantity10", QUANTITY_X to "quantityx",
                    QUANTITY_ALL to "quantityall", PLACEHOLDER to "placeholder", DEPOSIT_INVENTORY to "depositinv",
                    DEPOSIT_WORN to "depositworn",
                )
                ),
            SIDE to ("bankside" to mapOf(SIDE_ITEMS to "items")),
        )
    }
}

/** The varbit's value, 0 when the cache has no such varbit (tests). */
private fun PlayerVars.getVarbitIfKnown(id: Int): Int = if (types.varbit(id) == null) 0 else getVarbit(id)

/** Sets the varbit; does nothing when the cache has no such varbit (tests). */
private fun PlayerVars.setVarbitIfKnown(id: Int, value: Int) {
    if (types.varbit(id) != null) setVarbit(id, value)
}
