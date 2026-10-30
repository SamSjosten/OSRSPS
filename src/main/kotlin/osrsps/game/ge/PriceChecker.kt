package osrsps.game.ge

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.bank.BankScreen
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptRunner
import osrsps.game.sendInventory
import java.util.Locale

private val logger = KotlinLogging.logger {}

/** The price checker's prompt and texts. Every one's words guessed unless it says otherwise. */
object PriceCheckerLines : Lines("pricechecker", "Grand Exchange guide prices") {
    val TOTAL = line("total", "the total under the items checked, {total} the guide price of them all", "total")
    val SEARCH = line("search", "the item search's prompt after Search for item")
    val OTHER = line("other", "the guide price of an item searched for, {item} its name and {price} its price", "item", "price")
    val NOT_TRADEABLE = line("not_tradeable", "Add on an item that can't be traded on the Grand Exchange")
}

/**
 * The Grand Exchange guide prices screen, ge_pricechecker (464) with ge_pricechecker_side (238) beside it, opened from
 * the equipment tab's Guide prices button (wornitems:pricechecker). As on live, the items checked leave the inventory
 * for inv tradeoffer while the screen is open and come back when it closes; a save taken meanwhile counts them as in
 * the inventory ([saved]).
 *
 * The client draws each item's price from script ge_pricechecker_prices, one long a slot (rev 241 client scripts,
 * osrs-dumps); the total and a searched item's price are the server's texts. Search for item uses the chatbox's item
 * search, as a Grand Exchange buy does.
 */
class PriceChecker(
    private val grandExchange: GrandExchange,
    private val objTypes: ObjTypes,
    private val openModal: (Player, FrameSlot, Int) -> Unit,
    private val closeModals: (Player) -> Unit,
    private val scripts: ScriptRunner? = null,
    private val examine: (Player, Int, Int) -> Unit = { _, _, _ -> },
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    /** Drops a count of an item under the player. */
    private val drop: (Player, ObjType, Int) -> Unit = { _, _, _ -> },
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == MAIN

    fun open(player: Player) {
        closeModals(player)
        openModal(player, FrameSlot.MAINMODAL, MAIN)
        openModal(player, FrameSlot.SIDEMODAL, SIDE)
        send(
            player,
            RunClientScript(
                INTERFACE_INV_INIT_SCRIPT,
                listOf(SIDE shl 16 or SIDE_ITEMS, Inventory.INV, 4, 7, 0, -1, "Add", "Add-5", "Add-10", "Add-All", "Add-X"),
            ),
        )
        send(player, IfSetEventsV2(SIDE, SIDE_ITEMS, 0, Inventory.INV_CAPACITY - 1, 0, OPS))
        send(player, IfSetEventsV2(MAIN, ITEMS, 0, Inventory.INV_CAPACITY - 1, 0, OPS))
        send(player, IfSetEventsV2(MAIN, OTHER, -1, -1, 0, BankScreen.opMask(1..1)))
        send(player, IfSetEventsV2(MAIN, ALL, -1, -1, 0, BankScreen.opMask(1..1)))
        player.priceChecker.resendAll()
        player.priceCheckSearching = false
        update(player)
    }

    /**
     * The screen closed: the items checked go back to the inventory. Not on a death, which closes it: they stay here for
     * the death to sort with the rest of what the player carries ([osrsps.game.combat.Deaths.respawn]).
     */
    fun closed(player: Player) {
        player.priceCheckSearching = false
        if (player.dead) return
        giveBack(player, player.inventory)
    }

    /** Sends the items checked while the screen is open. Run at the end of every cycle. */
    fun flush(player: Player) {
        if (isOpen(player)) sendInventory(player, player.priceChecker, send)
    }

    /** A click on the screen or the inventory beside it. Returns false when the click isn't on either. */
    fun click(player: Player, interfaceId: Int, component: Int, sub: Int, op: Int, obj: Int = -1): Boolean {
        if (interfaceId != MAIN && interfaceId != SIDE) return false
        if (!isOpen(player)) return true
        when {
            interfaceId == SIDE && component == SIDE_ITEMS -> clickItem(player, player.inventory, player.priceChecker, sub, op, obj, adding = true)
            interfaceId == MAIN && component == ITEMS -> clickItem(player, player.priceChecker, player.inventory, sub, op, obj, adding = false)
            interfaceId == MAIN && component == ALL -> addAll(player)
            interfaceId == MAIN && component == OTHER -> {
                player.priceCheckSearching = true
                send(player, RunClientScript(GrandExchangeScreen.MESLAYER_MODE14, listOf(words(player)[PriceCheckerLines.SEARCH], 1, -1, 1)))
            }
        }
        return true
    }

    /** The item picked in the item search after Search for item; false when no search of ours waits for one. */
    fun chooseItem(player: Player, obj: Int): Boolean {
        if (!player.priceCheckSearching) return false
        player.priceCheckSearching = false
        if (!isOpen(player)) return true
        val price = grandExchange.guidePrice(obj)
        if (price == null) {
            message(player, PriceCheckerLines.NOT_TRADEABLE)
            return true
        }
        send(player, IfSetObject(MAIN, OTHER_ITEM, obj, 1))
        send(player, IfSetText(MAIN, OUTPUT, words(player)[PriceCheckerLines.OTHER, "item" to grandExchange.itemName(obj), "price" to number(price)]))
        return true
    }

    /** Add (from the inventory) or Remove (back to it), ops 1 to 5 for 1, 5, 10, all and X; op 10 examines. */
    private fun clickItem(player: Player, from: Inventory, to: Inventory, slot: Int, op: Int, clicked: Int, adding: Boolean) {
        if (slot !in 0 until from.capacity) return
        // A click sent before the slot changed is for an item no longer there.
        val obj = from[slot]?.takeIf { it.id == clicked } ?: return
        when (op) {
            10 -> examine(player, obj.id, obj.count)
            1 -> move(player, from, to, obj.id, 1, adding)
            2 -> move(player, from, to, obj.id, 5, adding)
            3 -> move(player, from, to, obj.id, 10, adding)
            4 -> move(player, from, to, obj.id, Int.MAX_VALUE, adding)
            5 -> {
                val runner = scripts ?: return
                if (player.scripts.delayed) return
                runner.start(player) {
                    val amount = countDialog()
                    if (amount > 0 && isOpen(player)) move(player, from, to, obj.id, amount, adding)
                }
            }
        }
    }

    /** Moves up to [count] of [id] from [from] to [to], as many as there are and fit. */
    private fun move(player: Player, from: Inventory, to: Inventory, id: Int, count: Int, adding: Boolean) {
        if (adding && grandExchange.guidePrice(grandExchange.offerItem(id)) == null) {
            message(player, PriceCheckerLines.NOT_TRADEABLE)
            return
        }
        val type = objTypes[id] ?: return
        var left = minOf(count.toLong(), from.count(id)).toInt()
        while (left > 0) {
            val slot = from.contents().firstOrNull { it.second.id == id }?.first ?: break
            val take = minOf(left, from[slot]!!.count)
            val added = to.add(type, take)
            if (added <= 0) break
            from.remove(slot, added)
            left -= added
        }
        update(player)
    }

    private fun addAll(player: Player) {
        for ((_, obj) in player.inventory.contents().toList()) {
            if (player.inventory.count(obj.id) == 0L) continue
            if (grandExchange.guidePrice(grandExchange.offerItem(obj.id)) == null) continue
            move(player, player.inventory, player.priceChecker, obj.id, Int.MAX_VALUE, adding = true)
        }
    }

    /** Sends each slot's guide price and the total under them. */
    private fun update(player: Player) {
        val prices = (0 until Inventory.INV_CAPACITY).map { slot ->
            player.priceChecker[slot]?.let { grandExchange.guidePrice(grandExchange.offerItem(it.id)) } ?: 0L
        }
        send(player, RunClientScript(PRICES_SCRIPT, prices))
        val total = player.priceChecker.contents().sumOf { (slot, obj) -> prices[slot] * obj.count }
        send(player, IfSetText(MAIN, OUTPUT, words(player)[PriceCheckerLines.TOTAL, "total" to number(total)]))
    }

    /**
     * Puts the items checked back into [into], emptying the checker. If the backpack filled up meanwhile (an item
     * given, or one taken off), what doesn't fit goes to the floor under the player, never to the bank: banking it let
     * a player bank from anywhere, and on death kept it out of the grave (fourth review, 30 Oct 2026).
     * GUESSED: live may keep the backpack's room for them; nothing checked is ever lost here.
     */
    private fun giveBack(player: Player, into: Inventory) {
        for ((slot, obj) in player.priceChecker.contents()) {
            val type = objTypes[obj.id] ?: continue
            player.priceChecker[slot] = null
            val left = obj.count - into.add(type, obj.count)
            if (left > 0) drop(player, type, left)
        }
    }

    /**
     * What a save takes as [player]'s inventory and bank: theirs, with the items being checked back in the inventory,
     * and what doesn't fit there in the bank, unnoted. Closing the screen drops that on the floor instead, which a save
     * can't; the bank only matters if the server stops with the screen open.
     */
    fun saved(player: Player): Pair<Map<Int, InvObj>, Map<Int, InvObj>> {
        val bank = player.bank.contents().toMap()
        if (player.priceChecker.contents().isEmpty()) return player.inventory.contents().toMap() to bank
        val copy = Inventory(Inventory.INV, Inventory.INV_CAPACITY)
        for ((slot, obj) in player.inventory.contents()) copy[slot] = obj
        val bankCopy = Inventory(player.bank.type, player.bank.capacity)
        for ((slot, obj) in bank) bankCopy[slot] = obj
        for ((_, obj) in player.priceChecker.contents()) {
            val type = objTypes[obj.id] ?: continue
            val left = obj.count - copy.add(type, obj.count)
            if (left <= 0) continue
            val banked = if (type.isCert) objTypes[type.certlink] ?: type else type
            val stack = bankCopy.contents().firstOrNull { it.second.id == banked.id }
            when {
                stack != null -> bankCopy[stack.first] = InvObj(banked.id, (stack.second.count.toLong() + left).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                else -> (0 until bankCopy.capacity).firstOrNull { bankCopy[it] == null }?.let { bankCopy[it] = InvObj(banked.id, left) }
                    ?: logger.warn { "${player.username}: $left x ${obj.id} from the price checker fit neither backpack nor bank in a save" }
            }
        }
        return copy.contents().toMap() to bankCopy.contents().toMap()
    }

    private fun message(player: Player, line: Line) = send(player, MessageGame(CHATTYPE_GAMEMESSAGE, words(player)[line]))

    private fun number(n: Long): String = String.format(Locale.ROOT, "%,d", n)

    companion object {
        const val MAIN = 464
        const val SIDE = 238

        /** ge_pricechecker's items, Search for item, the item found, Add all and the text under them. */
        const val ITEMS = 2
        const val OTHER = 5
        const val OTHER_ITEM = 8
        const val ALL = 10
        const val OUTPUT = 12

        /** ge_pricechecker_side:items. */
        const val SIDE_ITEMS = 0

        /** tradeoffer, the inv the items checked sit in. */
        const val INV = 90

        /** wornitems:pricechecker, the equipment tab's Guide prices button. */
        const val WORNITEMS_BUTTON = 3

        /** ge_pricechecker_prices: each slot's price as a long. */
        const val PRICES_SCRIPT = 785
        private const val INTERFACE_INV_INIT_SCRIPT = 149

        private val OPS = BankScreen.opMask(1..5) or BankScreen.opMask(10..10)
        private const val CHATTYPE_GAMEMESSAGE = 0

        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            MAIN to ("ge_pricechecker" to mapOf(ITEMS to "items", OTHER to "other", OTHER_ITEM to "otheritem", ALL to "all", OUTPUT to "output")),
            SIDE to ("ge_pricechecker_side" to mapOf(SIDE_ITEMS to "items")),
        )
    }
}
