package osrsps.game.shop

import net.rsprot.protocol.game.incoming.buttons.IfScriptTrigger.ParameterTypes
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.Inventory
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.bank.BankScreen
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.sendInventory
import java.util.WeakHashMap

/**
 * What runs an omnishop for a player: their balance of its currency, their stock (the inv the client counts from), and
 * what a purchase hands over. The interface knows none of it.
 */
interface OmnishopOwner {
    fun balance(player: Player): Int

    fun setBalance(player: Player, value: Int)

    /** [player]'s stock of the shop, in the shop's inv. */
    fun stock(player: Player): Inventory
}

/**
 * Jagex's shared reward-shop interface, `omnishop_main` (819) with `omnishop_side` (806): the client draws the whole
 * window from the shop's db rows ([OmnishopType]), and the server opens it, keeps the selection varp and the stock inv,
 * and sells. Buying only: no shop on it here sells back yet.
 *
 * - **Open** as the tsps notes have it from rsprox captures of other omnishops (docs/omnishop.md: Mixology Rewards, the
 *   Boat Emporium): varps `omnishop_lastshop` (3874) to the shop's row and `omnishop_selected_id` (3869) to -1, script
 *   `toplevel_mainmodal_background` (917) [-1, -3], the two modals, `omnishop_main_init` (7140) and
 *   `omnishop_side_init` (7246), and events on the list. Guessed: passing the shop's inv to `omnishop_main_init`
 *   (the captures pass -1), so that the client redraws the list as its stock changes.
 * - **The list** (`omnishop_main:list`, 819:38): each entry is twelve components, its options on the first, so a click's
 *   slot is the entry times twelve (osrs-dumps, `omnishop_main_list_item_setup`). Option 1 selects it, or unselects the
 *   one selected; 2 to 5 buy the row's four [OmnishopType.amounts]; 10 examines.
 * - **The buttons** (`button_1` to `button_4`, 819:26-29, op 1 in the cache) buy those amounts of the entry selected.
 * - **Buying**: each purchase takes the price from the balance and hands over the entry's multiplier, one at a time
 *   till the amount, the stock, the balance or the room runs out; then `omnishop_main_currency_redraw` (501), as the
 *   captures have it. Refusals, as the captures have them: "You don't have enough {currency}." with the currency's
 *   plural name, and "You don't have enough inventory space." (the shop's lines).
 * - The client's own script triggers on the window (`trigger_buy`, `trigger_request_info`, `trigger_examine`) come
 *   with the clicks above and do nothing more: the info panel draws an item's examine text itself.
 */
class OmnishopScreen(
    private val objTypes: ObjTypes,
    private val openModal: (Player, FrameSlot, Int) -> Unit,
    private val closeModals: (Player) -> Unit,
    private val setVarpNow: (Player, Int, Int) -> Unit,
    private val examine: (Player, Int, Int) -> Unit = { _, _, _ -> },
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    private class Open(val shop: OmnishopType, val owner: OmnishopOwner) {
        var selected = -1
    }

    private val open = WeakHashMap<Player, Open>()

    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == MAIN && open[player] != null

    /** The shop [player] has open, or null. */
    fun shopOf(player: Player): OmnishopType? = open[player]?.takeIf { isOpen(player) }?.shop

    fun open(player: Player, shop: OmnishopType, owner: OmnishopOwner) {
        closeModals(player)
        setVarpNow(player, LASTSHOP_VARP, shop.row)
        setVarpNow(player, SELECTED_VARP, -1)
        send(player, RunClientScript(MAINMODAL_BACKGROUND_SCRIPT, listOf(-1, -3)))
        openModal(player, FrameSlot.MAINMODAL, MAIN)
        openModal(player, FrameSlot.SIDEMODAL, SIDE)
        open[player] = Open(shop, owner)
        // The stock before the scripts that count it.
        val stock = owner.stock(player)
        stock.resendAll()
        sendInventory(player, stock, send)
        send(player, RunClientScript(MAIN_INIT_SCRIPT, listOf(shop.row, shop.inv, -1)))
        send(player, RunClientScript(SIDE_INIT_SCRIPT, listOf(shop.row)))
        val last = shop.stock.size * ENTRY_COMPONENTS - 1
        if (last >= 0) send(player, IfSetEventsV2(MAIN, LIST, 0, last, 0, BankScreen.opMask(1..5) or BankScreen.opMask(10..10)))
        send(player, IfSetEventsV2(SIDE, SIDE_ITEMS, 0, Inventory.INV_CAPACITY - 1, 0, BankScreen.opMask(10..10)))
    }

    /** A reconnect: the open shop's stock goes again in full with the next [flush], as an update may have been lost. */
    fun resync(player: Player) {
        open[player]?.let { it.owner.stock(player).resendAll() }
    }

    /** The window closed. */
    fun closed(player: Player) {
        open.remove(player)
    }

    /** Sends the open shop's stock when it has changed. Run at the end of every cycle. */
    fun flush(player: Player) {
        val shop = open[player] ?: return
        if (!isOpen(player)) {
            open.remove(player)
            return
        }
        sendInventory(player, shop.owner.stock(player), send)
    }

    /** A click on the window. Returns false when it isn't on it. */
    fun click(player: Player, interfaceId: Int, component: Int, sub: Int, op: Int, obj: Int = -1): Boolean {
        if (interfaceId != MAIN && interfaceId != SIDE) return false
        val shop = open[player]?.takeIf { isOpen(player) } ?: return true
        if (player.scripts.delayed) return true
        if (interfaceId == SIDE) {
            if (component == SIDE_ITEMS && op == 10 && sub in 0 until Inventory.INV_CAPACITY) player.inventory[sub]?.takeIf { it.id == obj }?.let { examine(player, it.id, it.count) }
            return true
        }
        when (component) {
            LIST -> {
                if (sub < 0 || sub % ENTRY_COMPONENTS != 0) return true
                val entry = sub / ENTRY_COMPONENTS
                val stock = shop.shop.stock.getOrNull(entry) ?: return true
                when (op) {
                    1 -> select(player, shop, if (shop.selected == entry) -1 else entry)
                    in 2..5 -> buy(player, shop, entry, shop.shop.amounts[op - 2])
                    10 -> examine(player, stock.obj, 1)
                }
            }
            in BUTTONS -> if (op == 1 && shop.selected >= 0) buy(player, shop, shop.selected, shop.shop.amounts[component - BUTTONS.first])
        }
        return true
    }

    private fun select(player: Player, shop: Open, entry: Int) {
        shop.selected = entry
        setVarpNow(player, SELECTED_VARP, entry)
    }

    private fun buy(player: Player, shop: Open, entry: Int, amount: Int) {
        val stock = shop.shop.stock.getOrNull(entry) ?: return
        if (amount <= 0 || !stock.buyable || stock.viewOnly) return
        val type = objTypes[stock.obj] ?: return
        val owner = shop.owner
        val inv = owner.stock(player)
        val have = if (shop.shop.inv >= 0) inv.count(stock.obj).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else amount
        val units = maxOf(1, stock.multiplier)
        var bought = 0
        while (bought < minOf(amount, have)) {
            if (owner.balance(player) < stock.cost) {
                message(player, ShopLines.NOT_ENOUGH_CURRENCY, "currency" to (shop.shop.currency?.plural ?: ""))
                break
            }
            val room = if (type.stackable) player.inventory.count(type.id) > 0 || player.inventory.freeSlots > 0 else player.inventory.freeSlots >= units
            if (!room) {
                message(player, ShopLines.NOT_ENOUGH_SPACE)
                break
            }
            owner.setBalance(player, owner.balance(player) - stock.cost)
            player.inventory.add(type, units)
            if (shop.shop.inv >= 0) takeStock(inv, stock.obj)
            bought++
        }
        if (bought > 0) send(player, RunClientScript(CURRENCY_REDRAW_SCRIPT, listOf(shop.shop.row)))
    }

    private fun takeStock(inv: Inventory, obj: Int) {
        val slot = inv.contents().firstOrNull { it.second.id == obj }?.first ?: return
        inv.remove(slot, 1)
    }

    private fun message(player: Player, line: Line, vararg values: Pair<String, Any>) =
        send(player, MessageGame(CHATTYPE_GAMEMESSAGE, words(player).get(line, *values)))

    companion object {
        /** omnishop_main and omnishop_side. */
        const val MAIN = 819
        const val SIDE = 806

        /** omnishop_main:list, button_1 to button_4; omnishop_side:items. */
        const val LIST = 38
        val BUTTONS = 26..29
        const val SIDE_ITEMS = 0

        /** The components each list entry is made of. */
        const val ENTRY_COMPONENTS = 12

        /** omnishop_lastshop and omnishop_selected_id. */
        const val LASTSHOP_VARP = 3874
        const val SELECTED_VARP = 3869

        const val MAINMODAL_BACKGROUND_SCRIPT = 917
        const val MAIN_INIT_SCRIPT = 7140
        const val SIDE_INIT_SCRIPT = 7246
        const val CURRENCY_REDRAW_SCRIPT = 501

        /**
         * The window's script triggers, by the id the client sends (the ifscript_ number in osrs-dumps), and their
         * arguments: `trigger_buy` (shop, entry, amount code), `trigger_request_info` (shop, entry), `trigger_examine` (obj).
         */
        val SCRIPT_TRIGGERS: Map<Int, ParameterTypes> = mapOf(
            407_137_374L.toInt() to ParameterTypes.of(ParameterTypes.INT, ParameterTypes.INT, ParameterTypes.INT),
            1_754_529_215L.toInt() to ParameterTypes.of(ParameterTypes.INT, ParameterTypes.INT),
            3_336_794_041L.toInt() to ParameterTypes.of(ParameterTypes.INT),
        )

        private const val CHATTYPE_GAMEMESSAGE = 0
    }
}
