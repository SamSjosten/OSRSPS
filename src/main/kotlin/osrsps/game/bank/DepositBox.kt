package osrsps.game.bank

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.WearPos

private val logger = KotlinLogging.logger {}

/**
 * The bank deposit box (bank_depositbox, 192): the inventory and the worn items, put into the bank without opening it.
 *
 * The client draws it all from the inventory and worn inventories it already has (bank_depositbox_update): each item's
 * options come from varbit depositbox_mode, which its quantity buttons set on the client before telling the server, as
 * the bank's do. Op 1 is the mode's amount (in mode 0, Deposit-1, it isn't offered and op 2 is the first option), op 2
 * to 6 Deposit-1, -5, -10, -X and -All, op 9 the bank's extra option (Wear) and op 10 Examine. The worn slots are the
 * bank's worn items view's (Remove, Bank, Examine), and the three buttons deposit the inventory, the worn items and
 * containers (there are none yet). Its menu's toggle hides the worn items button, on the client alone; the server keeps
 * the varbit in step.
 *
 * Inferred, not checked against live: Deposit-X sets the amount the X button then offers, as the bank's does; an item
 * used on the box asks for an amount when the player has more than one (the Wiki says they "select or enter" one;
 * this enters it). Not built: deposit locks (the lock menu) and containers.
 */
class DepositBox(
    private val bankScreen: BankScreen,
    private val openModal: (Player, FrameSlot, Int) -> Unit,
    /** Shows an item's examine text and values, as the inventory's Examine does. */
    private val examine: (Player, Int, Int) -> Unit,
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == INTERFACE

    /** Opens the deposit box, as a box's Deposit does. */
    fun open(player: Player) {
        openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        send(
            player,
            IfSetEventsV2(INTERFACE, ITEMS, 0, Inventory.INV_CAPACITY - 1, BankScreen.EVENTS_DRAG_DEPTH1 or BankScreen.EVENTS_DRAG_TARGET, BankScreen.opMask(1..6) or BankScreen.opMask(9..10)),
        )
        bankScreen.setExtraOps(player)
    }

    /** Keeps the extra option's bits up while the box is open. Run every cycle before the varps are sent. */
    fun updateVars(player: Player) {
        if (isOpen(player)) bankScreen.setExtraOps(player)
    }

    /** A click on the deposit box. Returns false when the click isn't on it. */
    fun click(player: Player, interfaceId: Int, component: Int, sub: Int, obj: Int, op: Int): Boolean {
        if (interfaceId != INTERFACE) return false
        if (!isOpen(player)) return true
        when (component) {
            ITEMS -> clickItem(player, sub, obj, op)
            in WORN_SLOTS -> bankScreen.clickWorn(player, WORN_SLOT_WEARPOS.getValue(component), op)
            DEPOSIT_INVENTORY -> if (!player.scripts.delayed) bankScreen.depositAll(player, player.inventory)
            DEPOSIT_WORN -> if (!player.scripts.delayed) bankScreen.depositAll(player, player.equipment)
            QUANTITY_1 -> setMode(player, MODE_1)
            QUANTITY_5 -> setMode(player, MODE_5)
            QUANTITY_10 -> setMode(player, MODE_10)
            QUANTITY_ALL -> setMode(player, MODE_ALL)
            QUANTITY_X -> if (op == 2) {
                bankScreen.start(player) {
                    val amount = countDialog()
                    if (amount > 0 && isOpen(player)) {
                        player.vars[VARP_REQUESTED_QUANTITY] = amount
                        setMode(player, MODE_X)
                    }
                }
            } else {
                setMode(player, MODE_X)
            }
            DEPOSITWORN_TOGGLE -> player.vars.setVarbitIfKnown(VARBIT_HIDE_DEPOSITWORN, 1 - player.vars.getVarbitIfKnown(VARBIT_HIDE_DEPOSITWORN))
            else -> logger.info { "${player.username} clicked deposit box component $component op $op (not built yet)" }
        }
        return true
    }

    /** The player dragged an item onto another slot of the box: the two swap, as the inventory's own drag does. */
    fun drag(player: Player, interfaceId: Int, component: Int, from: Int, fromObj: Int, toInterfaceId: Int, toComponent: Int, to: Int): Boolean {
        if (interfaceId != INTERFACE || component != ITEMS) return false
        if (!isOpen(player) || toInterfaceId != INTERFACE || toComponent != ITEMS) return true
        val inv = player.inventory
        if (from !in 0 until Inventory.INV_CAPACITY || to !in 0 until Inventory.INV_CAPACITY) return true
        if (inv[from]?.id != fromObj || player.scripts.delayed) {
            inv.resend(from)
            inv.resend(to)
            return true
        }
        if (from != to) inv.swap(from, to)
        return true
    }

    /** An item used on a deposit box: all of it when there is one, else as many as the player asks for. */
    fun useItem(player: Player, slot: Int, id: Int) {
        val held = player.inventory.takeIf { slot in 0 until Inventory.INV_CAPACITY }?.get(slot)
        if (held == null || held.id != id) return
        if (player.inventory.count(id) <= 1) {
            bankScreen.depositFrom(player, player.inventory, slot, id, Int.MAX_VALUE)
            return
        }
        bankScreen.start(player) {
            val amount = countDialog()
            if (amount > 0) bankScreen.depositFrom(player, player.inventory, slot, id, amount)
        }
    }

    private fun clickItem(player: Player, slot: Int, obj: Int, op: Int) {
        val held = player.inventory.takeIf { slot in 0 until Inventory.INV_CAPACITY }?.get(slot)
        if (held == null || held.id != obj) return
        if (op == EXAMINE) {
            examine(player, held.id, held.count)
            return
        }
        if (player.scripts.delayed) return
        when (op) {
            1 -> deposit(player, slot, held.id, modeAmount(player))
            2 -> deposit(player, slot, held.id, 1)
            3 -> deposit(player, slot, held.id, 5)
            4 -> deposit(player, slot, held.id, 10)
            5 -> bankScreen.start(player) {
                val amount = countDialog()
                if (amount > 0) {
                    player.vars[VARP_REQUESTED_QUANTITY] = amount
                    deposit(player, slot, held.id, amount)
                }
            }
            6 -> deposit(player, slot, held.id, Int.MAX_VALUE)
            BankScreen.SIDE_EXTRAOP -> bankScreen.clickExtraOp(player, slot, held.id)
            else -> logger.info { "${player.username} picked deposit box op $op on ${held.id} (not built yet)" }
        }
    }

    private fun deposit(player: Player, slot: Int, id: Int, count: Int) {
        if (isOpen(player)) bankScreen.depositFrom(player, player.inventory, slot, id, count)
    }

    /** Op 1's amount: what the mode's button says, X being the last amount typed (at least 1, as the client shows it). */
    private fun modeAmount(player: Player): Int = when (player.vars.getVarbitIfKnown(VARBIT_MODE)) {
        MODE_5 -> 5
        MODE_10 -> 10
        MODE_X -> maxOf(1, player.vars[VARP_REQUESTED_QUANTITY])
        MODE_ALL -> Int.MAX_VALUE
        else -> 1
    }

    private fun setMode(player: Player, mode: Int) = player.vars.setVarbitIfKnown(VARBIT_MODE, mode)

    companion object {
        const val INTERFACE = 192

        /** The inventory's items, one sub per slot. */
        const val ITEMS = 6
        const val EXAMINE = 10

        /** The worn slots, slot0 to slot13, by wearpos (the number in the name). */
        val WORN_SLOT_WEARPOS = mapOf(
            10 to WearPos.HEAD, 11 to WearPos.CAPE, 12 to WearPos.NECK, 13 to WearPos.WEAPON, 14 to WearPos.BODY,
            15 to WearPos.SHIELD, 16 to WearPos.LEGS, 17 to WearPos.HANDS, 18 to WearPos.FEET, 19 to WearPos.RING,
            20 to WearPos.AMMO,
        )
        val WORN_SLOTS = WORN_SLOT_WEARPOS.keys

        const val DEPOSIT_WORN = 30
        const val DEPOSIT_INVENTORY = 31
        const val DEPOSIT_CONTAINERS = 32
        const val LOCK_MENU = 34
        const val QUANTITY_1 = 35
        const val QUANTITY_5 = 36
        const val QUANTITY_10 = 37
        const val QUANTITY_X = 38
        const val QUANTITY_ALL = 39
        const val DEPOSITWORN_TOGGLE = 42

        /** depositbox_mode: which amount op 1 deposits, as storage_create_quantitybuttons numbers the buttons. */
        const val VARBIT_MODE = 4430
        const val MODE_1 = 0
        const val MODE_5 = 1
        const val MODE_ALL = 2
        const val MODE_X = 3
        const val MODE_10 = 4

        /** depositbox_requestedquantity, a varp: the X button's amount. */
        const val VARP_REQUESTED_QUANTITY = 1794

        /** depositbox_hidedepositworn, the menu's toggle. */
        const val VARBIT_HIDE_DEPOSITWORN = 20095

        /** Every id above with its gameval name in the 241 cache, for [osrsps.cache.GameFrameCheck]. */
        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            INTERFACE to (
                "bank_depositbox" to mapOf(
                    ITEMS to "inventory", DEPOSIT_WORN to "deposit_worn", DEPOSIT_INVENTORY to "deposit_inv",
                    DEPOSIT_CONTAINERS to "deposit_lootingbag", LOCK_MENU to "lock_menu", QUANTITY_1 to "1", QUANTITY_5 to "5",
                    QUANTITY_10 to "10", QUANTITY_X to "x", QUANTITY_ALL to "all", DEPOSITWORN_TOGGLE to "depositworn_toggle",
                ) + WORN_SLOT_WEARPOS.mapValues { (_, wearpos) -> "slot$wearpos" }
                ),
        )
    }
}
