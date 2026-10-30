package osrsps.game.shop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.bank.BankScreen
import osrsps.game.bank.getVarbitIfKnown
import osrsps.game.bank.setVarbitIfKnown
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/**
 * A shop's messages, 2004Scape's (RS2) except the value checks', which are RSMod's; not checked against live. `{item}`
 * is the item's name and `{price}` the coins, written as 1,000.
 */
object ShopLines : Lines("shop", "Shop") {
    val COSTS_ONE = line("costs_one", "Value on a shop's item that costs 1 coin", "item")
    val COSTS = line("costs", "Value on a shop's item at any other price", "item", "price")
    val BUYS_FOR_ONE = line("buys_for_one", "Value on an inventory item the shop would buy for 1 coin", "item")
    val BUYS_FOR = line("buys_for", "Value on an inventory item at any other price", "item", "price")
    val OUT_OF_STOCK = line("out_of_stock", "buying when the stock has run out")
    val NOT_ENOUGH_COINS = line("not_enough_coins", "buying without the coins")
    val NOT_ENOUGH_SPACE = line("not_enough_space", "buying, or selling for coins, with no room in the inventory")
    val NO_ROOM = line("no_room", "selling to a shop whose slots are all taken")
    val CANT_SELL_COINS = line("cant_sell_coins", "Value or Sell on coins")
    val CANT_SELL = line("cant_sell", "Value or Sell on an untradeable item")
    val WRONG_SHOP = line("wrong_shop", "Value or Sell on an item a specialist shop doesn't stock")
    val ALREADY_HAVE = line("already_have", "buying an item a player may own one of (Diango's Chronicle) with one held or banked", "item")
}

/** The shop a player has open, and the [Shop.version] their client last got. */
class OpenShop(val shop: Shop) {
    internal var sentVersion = -1
}

/**
 * Shops as the player sees them: shopmain (300) over the game view and shopside (301), the inventory beside it, as
 * RSMod and 2004Scape open them. Every shop's stock lives here, one [Shop] each, shared by everyone on the world.
 *
 * The 241 client draws the shop itself: shop_main_init (script 1074, which the server runs with the shop's inv and
 * title) makes a slot per item, one sub past its index (sub 0 is the highlight), with Buy 1, Buy 5, Buy 10 and Buy 50
 * as ops 2 to 5 and Examine as op 10. Op 1 is Value, or the amount the Quantity buttons along the bottom pick (varbit
 * shop_quantity, which the client sets before telling the server; op 6 is then Value). The inventory beside it is
 * drawn by interface_inv_init (149) with the ops the server gives: Value and Sell 1, 5, 10 and 50, as RSMod has them.
 *
 * Messages and checks are 2004Scape's (RS2), except the value checks, which say "coins" as RSMod's do where 2004Scape
 * says "gp". Guessed, not checked against live: that the inventory's options stay Value and Sell 1 to 50 whatever the
 * Quantity buttons pick, and every message's words.
 */
class ShopScreen(
    types: ShopTypes,
    private val objTypes: ObjTypes,
    private val openModal: (Player, FrameSlot, Int) -> Unit,
    /** Shows an item's examine text and values, as the inventory's Examine does. */
    private val examine: (Player, ObjType, Int) -> Unit,
    /** The words of [ShopLines] for a player. */
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    private val shops: Map<String, Shop> = types.all.associate { it.key to Shop(it) }

    /** The stock of the shop called [key] now, for tests and commands. */
    operator fun get(key: String): Shop? = shops[key]

    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == MAIN && player.openShop != null

    /** Opens [type]'s shop, as its keepers' Trade does. */
    fun open(player: Player, type: ShopType) {
        val shop = shops.getValue(type.key)
        openModal(player, FrameSlot.MAINMODAL, MAIN)
        openModal(player, FrameSlot.SIDEMODAL, SIDE)
        player.openShop = OpenShop(shop)
        sendStock(player)
        send(player, RunClientScript(SHOP_MAIN_INIT_SCRIPT, listOf(type.invId, type.title, -1, 0, 1)))
        send(player, IfSetEventsV2(MAIN, MAIN_ITEMS, 1, type.size, 0, BankScreen.opMask(1..6) or BankScreen.opMask(10..10)))
        send(
            player,
            RunClientScript(
                INTERFACE_INV_INIT_SCRIPT,
                listOf(SIDE shl 16 or SIDE_ITEMS, Inventory.INV, 4, 7, 0, -1, VALUE_OP, "Sell 1$OP_COLOUR", "Sell 5$OP_COLOUR", "Sell 10$OP_COLOUR", "Sell 50$OP_COLOUR"),
            ),
        )
        send(player, IfSetEventsV2(SIDE, SIDE_ITEMS, 0, Inventory.INV_CAPACITY - 1, 0, BankScreen.opMask(1..5) or BankScreen.opMask(10..10)))
    }

    /** The shop closed: the player stops getting its stock. */
    fun closed(player: Player) {
        player.openShop = null
    }

    /** Moves every shop's stock a step back to its start. Run once a tick. */
    fun restock(tick: Long) {
        for (shop in shops.values) shop.restock(tick)
    }

    /** Sends the open shop's stock when it has changed. Run at the end of every cycle, after the cycle's clicks. */
    fun flush(player: Player) {
        val open = player.openShop ?: return
        if (!isOpen(player)) {
            player.openShop = null
            return
        }
        if (open.sentVersion != open.shop.version) sendStock(player)
    }

    private fun sendStock(player: Player) {
        val open = player.openShop ?: return
        val slots = open.shop.contents()
        val count = slots.indexOfLast { it != null } + 1
        // An item sold out shows at 0, as the shop still stocks it.
        send(player, UpdateInvFull(open.shop.type.invId, count) { slot -> slots[slot]?.let { InventoryObject(slot, it.obj, it.count) } ?: InventoryObject.NULL })
        open.sentVersion = open.shop.version
    }

    /** A click on the shop or its inventory. Returns false when the click isn't on either. */
    fun click(player: Player, interfaceId: Int, component: Int, sub: Int, obj: Int, op: Int): Boolean {
        if (interfaceId != MAIN && interfaceId != SIDE) return false
        if (!isOpen(player)) return true
        val shop = player.openShop!!.shop
        if (interfaceId == SIDE) {
            if (component == SIDE_ITEMS) clickInventory(player, shop, sub, obj, op)
            return true
        }
        when (component) {
            MAIN_ITEMS -> clickShop(player, shop, sub - 1, obj, op)
            VALUE -> setQuantity(player, QUANTITY_VALUE)
            QUANTITY_1 -> setQuantity(player, QUANTITY_BUY_1)
            QUANTITY_5 -> setQuantity(player, QUANTITY_BUY_5)
            QUANTITY_10 -> setQuantity(player, QUANTITY_BUY_10)
            QUANTITY_50 -> setQuantity(player, QUANTITY_BUY_50)
            else -> logger.info { "${player.username} clicked shop component $component op $op (not built yet)" }
        }
        return true
    }

    private fun setQuantity(player: Player, quantity: Int) = player.vars.setVarbitIfKnown(VARBIT_QUANTITY, quantity)

    private fun clickShop(player: Player, shop: Shop, slot: Int, obj: Int, op: Int) {
        val held = shop[slot]
        if (held == null || held.obj != obj) return
        val type = objTypes[held.obj] ?: return
        val quantity = player.vars.getVarbitIfKnown(VARBIT_QUANTITY)
        when (op) {
            1 -> if (quantity == QUANTITY_VALUE) valueInShop(player, shop, type) else buy(player, shop, type, BUY_AMOUNTS.getOrElse(quantity) { 1 })
            2 -> buy(player, shop, type, 1)
            3 -> buy(player, shop, type, 5)
            4 -> buy(player, shop, type, 10)
            5 -> buy(player, shop, type, 50)
            6 -> valueInShop(player, shop, type)
            EXAMINE -> examine(player, type, held.count)
            else -> logger.info { "${player.username} picked shop op $op on $type (not built yet)" }
        }
    }

    private fun clickInventory(player: Player, shop: Shop, slot: Int, obj: Int, op: Int) {
        val held = player.inventory.takeIf { slot in 0 until Inventory.INV_CAPACITY }?.get(slot)
        if (held == null || held.id != obj) return
        val type = objTypes[held.id] ?: return
        when (op) {
            1 -> valueInInventory(player, shop, type)
            2 -> sell(player, shop, slot, type, 1)
            3 -> sell(player, shop, slot, type, 5)
            4 -> sell(player, shop, slot, type, 10)
            5 -> sell(player, shop, slot, type, 50)
            EXAMINE -> examine(player, type, held.count)
            else -> logger.info { "${player.username} picked shop inventory op $op on $type (not built yet)" }
        }
    }

    private fun valueInShop(player: Player, shop: Shop, type: ObjType) {
        val price = shop.buyPrice(type.id, type.cost, shop.count(type.id))
        value(player, ShopLines.COSTS_ONE, ShopLines.COSTS, type.name, price)
    }

    private fun valueInInventory(player: Player, shop: Shop, type: ObjType) {
        val base = unnoted(type)
        if (!canSell(player, shop, base)) return
        val price = shop.sellPrice(base.id, base.cost, shop.count(base.id))
        value(player, ShopLines.BUYS_FOR_ONE, ShopLines.BUYS_FOR, base.name, price)
    }

    /**
     * Buys up to [amount] of [type], one at a time as 2004Scape does, each at the price its stock then gives, until
     * the coins, the stock or the inventory space run out.
     */
    private fun buy(player: Player, shop: Shop, type: ObjType, amount: Int) {
        if (player.scripts.delayed) return
        val inv = player.inventory
        repeat(amount) {
            val stock = shop.count(type.id)
            if (stock <= 0) return message(player, ShopLines.OUT_OF_STOCK)
            if (shop.type.onePerPlayer(type.id) && inv.count(type.id) + player.bank.count(type.id) > 0) {
                return message(player, ShopLines.ALREADY_HAVE, "item" to type.name)
            }
            val price = shop.buyPrice(type.id, type.cost, stock)
            val coinSlot = inv.slotOf(COINS)
            if (coinSlot < 0 || inv[coinSlot]!!.count < price) return message(player, ShopLines.NOT_ENOUGH_COINS)
            // Paying with the last coins frees their slot for the item, as 2004Scape lets it.
            inv.remove(coinSlot, price)
            if (inv.add(type, 1) == 0) {
                coinsBack(player, price)
                return message(player, ShopLines.NOT_ENOUGH_SPACE)
            }
            shop.take(shop.slotOf(type.id), 1)
        }
    }

    /**
     * Sells up to [amount] of the item in [slot] (all of that kind the player holds, as 2004Scape counts them), one at a
     * time at the price the shop's stock then gives. A note sells as its item.
     */
    private fun sell(player: Player, shop: Shop, slot: Int, type: ObjType, amount: Int) {
        if (player.scripts.delayed) return
        val base = unnoted(type)
        if (!canSell(player, shop, base)) return
        val inv = player.inventory
        repeat(amount) {
            val from = if (inv[slot]?.id == type.id) slot else inv.slotOf(type.id)
            if (from < 0) return
            if (!shop.hasRoom(base.id)) return message(player, ShopLines.NO_ROOM)
            val price = shop.sellPrice(base.id, base.cost, shop.count(base.id))
            val freesSlot = !type.stackable && !type.isCert || inv[from]!!.count == 1
            if (price > 0 && inv.slotOf(COINS) < 0 && inv.freeSlots == 0 && !freesSlot) return message(player, ShopLines.NOT_ENOUGH_SPACE)
            if (price > 0 && inv.count(COINS) + price > Inventory.MAX_STACK) return message(player, ShopLines.NOT_ENOUGH_SPACE)
            inv.remove(from, 1)
            shop.put(base.id, 1)
            if (price > 0) coinsBack(player, price)
        }
    }

    private fun canSell(player: Player, shop: Shop, base: ObjType): Boolean {
        val refusal = when {
            base.id == COINS -> ShopLines.CANT_SELL_COINS
            !base.tradeable -> ShopLines.CANT_SELL
            !shop.type.general && !shop.type.stocks(base.id) -> ShopLines.WRONG_SHOP
            else -> null
        }
        refusal?.let { message(player, it) }
        return refusal == null
    }

    private fun coinsBack(player: Player, count: Int) {
        val coins = objTypes[COINS] ?: ObjType(COINS, "Coins", stackable = true)
        player.inventory.add(coins, count)
    }

    /** What a note stands for, else [type]; only when the item links back, so a one-way cache link can't sell as another. */
    private fun unnoted(type: ObjType): ObjType =
        if (type.isCert) objTypes[type.certlink]?.takeIf { !it.isCert && it.certlink == type.id } ?: type else type

    /** A value check's message: [one] for a price of 1 coin, else [many] with the price written as 1,000 and so on. */
    private fun value(player: Player, one: Line, many: Line, item: String, price: Int) =
        if (price == 1) message(player, one, "item" to item) else message(player, many, "item" to item, "price" to "%,d".format(price))

    private fun message(player: Player, line: Line, vararg values: Pair<String, Any>) =
        send(player, MessageGame(CHATTYPE_GAMEMESSAGE, words(player).get(line, *values)))

    private fun Inventory.slotOf(id: Int): Int = contents().firstOrNull { it.second.id == id }?.first ?: -1

    private fun Shop.slotOf(obj: Int): Int = contents().indexOfFirst { it?.obj == obj }

    companion object {
        const val MAIN = 300
        const val SIDE = 301

        /** shopmain:items, one sub per item past its index. */
        const val MAIN_ITEMS = 16
        const val VALUE = 5
        const val QUANTITY_1 = 8
        const val QUANTITY_5 = 10
        const val QUANTITY_10 = 12
        const val QUANTITY_50 = 14

        /** shopside:items, one sub per inventory slot. */
        const val SIDE_ITEMS = 0
        const val EXAMINE = 10

        /** shop_quantity: what op 1 does, as shop_get_buy_quantity reads it. */
        const val VARBIT_QUANTITY = 6348
        const val QUANTITY_VALUE = 0
        const val QUANTITY_BUY_1 = 1
        const val QUANTITY_BUY_5 = 2
        const val QUANTITY_BUY_10 = 3
        const val QUANTITY_BUY_50 = 4
        private val BUY_AMOUNTS = listOf(0, 1, 5, 10, 50)

        const val SHOP_MAIN_INIT_SCRIPT = 1074
        const val INTERFACE_INV_INIT_SCRIPT = 149
        private const val OP_COLOUR = "<col=ff9040>"
        private const val VALUE_OP = "Value$OP_COLOUR"

        const val COINS = 995

        private const val CHATTYPE_GAMEMESSAGE = 0

        /** Every id above with its gameval name in the 241 cache, for [osrsps.cache.GameFrameCheck]. */
        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            MAIN to (
                "shopmain" to mapOf(
                    MAIN_ITEMS to "items", VALUE to "value", QUANTITY_1 to "quantity1", QUANTITY_5 to "quantity5",
                    QUANTITY_10 to "quantity10", QUANTITY_50 to "quantity50",
                )
                ),
            SIDE to ("shopside" to mapOf(SIDE_ITEMS to "items")),
        )
    }
}
