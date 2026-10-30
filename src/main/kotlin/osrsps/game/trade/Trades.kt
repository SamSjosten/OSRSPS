package osrsps.game.trade

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.abs
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import osrsps.game.Follows
import osrsps.game.ge.GrandExchange
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.script.ScriptContext
import osrsps.game.FrameSlot
import osrsps.game.Interaction
import osrsps.game.Movement
import osrsps.game.Player

import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.social.FriendLists
import osrsps.game.social.FriendLists.Companion.sameName
import osrsps.game.social.IgnoreLists
import osrsps.map.CollisionMap
import osrsps.map.Reach
import osrsps.map.RouteTarget
import osrsps.map.Tile

private val logger = KotlinLogging.logger {}

/**
 * A trade between two players, from the moment both screens open until it ends. Each player's offer is held here,
 * out of their inventory: an item is in one place or the other, never both, and whatever ends the trade short of
 * completing it puts every offered item back ([Trades.decline]). A save made meanwhile counts the offer as still in the
 * inventory ([offeredBy]).
 */
class Trade(val first: Player, val second: Player) {
    /** One player's side: what they offer and whether they have accepted this screen. */
    class Side(val player: Player) {
        val offer = Inventory(Trades.TRADEOFFER, Inventory.INV_CAPACITY)
        /** The offered items that stack, so a save can put the offer back as the inventory would hold it. */
        val stackable = HashSet<Int>()
        var accepted = false
        /** Ticks until this player may accept the first screen again after a change (the Accept trade delay). */
        var acceptDelay = 0
        /** The offer or the partner's changed since the screens' texts and inventories were last sent. */
        var dirty = true
    }

    val firstSide = Side(first)
    val secondSide = Side(second)

    /** Over: declined, closed or completed. Nothing may touch it after. */
    var over = false
        internal set

    /** On the second screen, tradeconfirm: the offers can't change any more. */
    var confirming = false
        internal set

    fun partnerOf(player: Player): Player = if (player === first) second else first

    fun sideOf(player: Player): Side = if (player === first) firstSide else secondSide

    fun partnerSideOf(player: Player): Side = if (player === first) secondSide else firstSide
}

/**
 * What [player] has offered in their trade, if they are in one, each with whether it stacks: their save counts it as
 * still in the inventory.
 */
fun offeredBy(player: Player): List<Pair<InvObj, Boolean>> {
    val trade = player.trade ?: return emptyList()
    if (trade.over) return emptyList()
    val side = trade.sideOf(player)
    return side.offer.contents().map { (_, obj) -> obj to (obj.id in side.stackable) }
}

/** "Trade with" clicked on [target]: the player walks up to them, then asks. */
class TradeInteraction(val target: Player, private val trades: Trades) : Interaction {
    /** Where the target stood when the route to them was last made. */
    internal var routedTo: Tile? = null

    override fun tryInteract(player: Player): Boolean = trades.approach(player, this)
}

/**
 * Trading between players: the request, then the trade screens.
 *
 * Trade with walks the player up beside the other player, as an NPC option does (along a side, not a corner), and then
 * asks: the asker is told they are sending an offer and the other player gets a trade request in their chat, which
 * they accept by clicking it (the chatbox's Accept trade runs Trade with on the asker, client script chat_op_core) or
 * by using Trade with on the asker themselves. A request is answered when the asker's request is still standing: it
 * stands until they ask someone else, trade, or leave (guessed; live's lifetime for a request isn't on the Wiki).
 *
 * A player with an interface open (a bank, the equipment stats, another trade) is busy (OSRS Wiki, Trading). A player
 * whose trade filter is off, or on Friends with the asker not their friend, or who ignores the asker, never sees the
 * request, but the asker is told the offer is on its way as usual, so they can't tell (inferred).
 */
class Trades(
    private val collision: CollisionMap,
    private val movement: Movement,
    /** Whether [Player] is still in the world. */
    private val inWorld: (Player) -> Boolean,
    private val ignores: IgnoreLists,
    /** Each player's words for the game messages. */
    private val words: (Player) -> DialogueText,
    /** Opens a modal interface. */
    private val openModal: (Player, FrameSlot, Int) -> Unit,
    /** Closes every modal the player has open. */
    private val closeModals: (Player) -> Unit,
    private val objTypes: ObjTypes = ObjTypes.NONE,
    /** The Grand Exchange price of an item, for the offers' values; null when it has none. */
    private val price: (ObjType) -> Long? = { null },
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
    /** Starts a script for the player, for Offer-X's amount prompt. */
    private val startScript: (Player, suspend ScriptContext.() -> Unit) -> Unit = { _, _ -> },
    /** Examine on an item in either offer, as in the inventory. */
    private val examine: (Player, ObjType, Int) -> Unit = { _, _, _ -> },
    /** Where an offered item goes that can't go back into its owner's inventory; logged, never expected. */
    private val overflow: (Player, InvObj) -> Unit = { _, _ -> },
    /** Sets a varbit for the player; nothing when the cache lacks it (tests). */
    private val setVarbit: (Player, Int, Int) -> Unit = { _, _, _ -> },
    /** A varbit of the player's; 0 when the cache lacks it. */
    private val varbit: (Player, Int) -> Int = { _, _ -> 0 },
    /**
     * Completes a trade: runs the swap, then saves both players together (World.commitTrade, the trade journal). Returns
     * false, after running the undo, when the trade couldn't be made safe to save.
     */
    private val commit: (Player, Player, swap: () -> Unit, undo: () -> Unit) -> Boolean = { _, _, swap, _ -> swap(); true },
) {
    /** A click on Trade with [target]. */
    fun click(player: Player, target: Player, invertRun: Boolean) {
        if (target === player) return
        // The client only shows players in view, so one further off is a forged click (as for Follow and NPCs).
        if (!inView(player, target)) {
            logger.debug { "${player.username} clicked Trade with on ${target.username}, who isn't in view" }
            return
        }
        val interaction = TradeInteraction(target, this)
        player.interaction = interaction
        route(player, interaction, invertRun)
    }

    /** Walks [player] on toward [interaction]'s target, and asks once beside them. Returns true once it's over. */
    internal fun approach(player: Player, interaction: TradeInteraction): Boolean {
        val target = interaction.target
        if (!inWorld(target) || target.dead || !inView(player, target)) {
            // Guessed, as for an NPC that goes away: the player finishes their walk and gives up.
            player.interaction = null
            return true
        }
        if (!Reach.reachedEntity(collision, player.level, player.x, player.z, 1, target.x, target.z, 1, 1)) {
            // The target walked: a new route to where they are now, as for a moving NPC.
            if (interaction.routedTo != Tile(target.x, target.z)) route(player, interaction, invertRun = false)
            return false
        }
        player.interaction = null
        // Arrived: the rest of a route to where they stood would walk past them (as on reaching an NPC).
        player.waypoints.clear()
        request(player, target)
        return true
    }

    private fun inView(player: Player, target: Player): Boolean =
        target.level == player.level && abs(target.x - player.x) <= Follows.RANGE && abs(target.z - player.z) <= Follows.RANGE

    private fun route(player: Player, interaction: TradeInteraction, invertRun: Boolean) {
        val target = interaction.target
        interaction.routedTo = Tile(target.x, target.z)
        movement.request(player, RouteTarget.entity(target.x, target.z, 1), invertRun)
    }

    /** [player], beside [target], asks to trade. */
    fun request(player: Player, target: Player) {
        if (player.trade != null) return
        // Guessed: nobody trades with a player lying dead, who couldn't decline (their clicks are dropped until the respawn).
        if (player.dead || target.dead) return
        // Nor with one whose connection has dropped (waiting for a reconnect) or who is leaving: they couldn't see the
        // screens. Guessed, as live's lingering player isn't known to refuse; nothing is said.
        if (gone(player) || gone(target)) return
        // A delayed player (part way up a ladder) is busy too, or the climb would carry them off mid-trade.
        if (target.trade != null || target.modalOpen || target.scripts.delayed) {
            message(player, TradeLines.BUSY)
            return
        }
        if (target.tradeRequestTo === player) {
            open(player, target)
            return
        }
        player.tradeRequestTo = target
        message(player, TradeLines.SENDING)
        if (!seesRequestsFrom(target, player)) {
            logger.debug { "${target.username} doesn't see trade requests from ${player.username}" }
            return
        }
        val text = words(target).get(TradeLines.REQUEST, "name" to player.username)
        target.session.queue(MessageGame(CHATTYPE_TRADEREQ, player.username, text))
    }

    /** Whether [player]'s trade filter and ignore list let a request from [asker] through. */
    private fun seesRequestsFrom(player: Player, asker: Player): Boolean {
        if (ignores.ignores(player, asker)) return false
        return when (player.chatFilters.trade) {
            FriendLists.PRIVATE_ON -> true
            FriendLists.PRIVATE_FRIENDS -> player.friends.any { sameName(it, asker.username) }
            else -> false
        }
    }

    /**
     * Opens the trade screens for both players: trademain with both offers (the partner's as a mirror of the same
     * inventory, a combined id under -70000, RSProt's UpdateInvFull) and tradeside with the inventory and its Offer
     * options (interface_inv_init, as the shops' side does). The ops' words are guessed from the Wiki's list.
     */
    private fun open(player: Player, partner: Player) {
        player.tradeRequestTo = null
        partner.tradeRequestTo = null
        val trade = Trade(partner, player)
        for (side in listOf(player, partner)) {
            closeModals(side)
            // A walk or an approach started before the trade would carry on under the screens: both stop.
            side.interaction = null
            side.waypoints.clear()
            side.trade = trade
            setVarbit(side, VARBIT_THIS_REMOVED, 0)
            setVarbit(side, VARBIT_OTHER_REMOVED, 0)
            setVarbit(side, VARBIT_ACCEPT_DELAY, 0)
            openModal(side, FrameSlot.MAINMODAL, MAIN)
            openModal(side, FrameSlot.SIDEMODAL, SIDE)
            send(side, RunClientScript(TRADE_PARTNER_SET_SCRIPT, listOf(trade.partnerOf(side).username)))
            send(side, IfSetText(MAIN, MAIN_TITLE, words(side).get(TradeLines.TITLE, "name" to trade.partnerOf(side).username)))
            send(side, IfSetText(MAIN, MAIN_STATUS, ""))
            send(side, IfSetEventsV2(MAIN, MAIN_YOUR_OFFER, 0, Inventory.INV_CAPACITY - 1, 0, OFFER_OPS))
            send(side, IfSetEventsV2(MAIN, MAIN_OTHER_OFFER, 0, Inventory.INV_CAPACITY - 1, 0, EXAMINE_OP))
            send(
                side,
                RunClientScript(
                    INTERFACE_INV_INIT_SCRIPT,
                    listOf(SIDE shl 16 or SIDE_ITEMS, Inventory.INV, 4, 7, 0, -1, "Offer$OP_COLOUR", "Offer-5$OP_COLOUR", "Offer-10$OP_COLOUR", "Offer-All$OP_COLOUR", "Offer-X$OP_COLOUR"),
                ),
            )
            send(side, IfSetEventsV2(SIDE, SIDE_ITEMS, 0, Inventory.INV_CAPACITY - 1, 0, OFFER_OPS))
        }
        logger.info { "${partner.username} and ${player.username} are trading" }
    }

    /**
     * Sends each side of [player]'s trade that changed: the offers (theirs as tradeoffer, the partner's mirrored), the
     * headers with each offer's value, the partner's free slots and the accept status. Run at the end of every cycle;
     * the Accept trade delay counts down only at the end of a game tick ([tickEnd]), not on a paused world's cycles.
     */
    fun flush(player: Player, tickEnd: Boolean = true) {
        val trade = player.trade ?: return
        if (trade.over) return
        val mine = trade.sideOf(player)
        val theirs = trade.partnerSideOf(player)
        if (tickEnd && mine.acceptDelay > 0) {
            mine.acceptDelay--
            setVarbit(player, VARBIT_ACCEPT_DELAY, mine.acceptDelay)
        }
        if (!mine.dirty && !theirs.dirty) return
        val partner = theirs.player
        val text = words(player)
        send(player, UpdateInvFull(TRADEOFFER, Inventory.INV_CAPACITY, objects(mine.offer)))
        send(player, UpdateInvFull(MIRROR, TRADEOFFER, Inventory.INV_CAPACITY, objects(theirs.offer)))
        if (trade.confirming) {
            val status = when {
                mine.accepted -> text[TradeLines.WAITING]
                theirs.accepted -> text[TradeLines.OTHER_ACCEPTED]
                else -> text[TradeLines.ARE_YOU_SURE]
            }
            send(player, IfSetText(CONFIRM, CONFIRM_TITLE, status))
            return
        }
        send(player, IfSetText(MAIN, MAIN_YOUR_HEADER, text.get(TradeLines.YOUR_OFFER, "value" to format(value(mine.offer)))))
        send(player, IfSetText(MAIN, MAIN_OTHER_HEADER, text.get(TradeLines.OTHER_OFFER, "name" to partner.username, "value" to format(value(theirs.offer)))))
        val free = partner.inventory.freeSlots
        send(player, IfSetText(MAIN, MAIN_FREE_SPACE, text.get(TradeLines.FREE_SLOTS, "name" to partner.username, "count" to free)))
        val status = when {
            mine.accepted -> text[TradeLines.WAITING]
            theirs.accepted -> text[TradeLines.OTHER_ACCEPTED]
            else -> ""
        }
        send(player, IfSetText(MAIN, MAIN_STATUS, status))
    }

    /** Clears the dirty marks once both sides have been sent; run after every player's [flush]. */
    fun flushed(player: Player) {
        val trade = player.trade ?: return
        if (player === trade.second || trade.second.trade !== trade) {
            trade.firstSide.dirty = false
            trade.secondSide.dirty = false
        }
    }

    private fun objects(inv: Inventory): UpdateInvFull.ObjectProvider = UpdateInvFull.ObjectProvider { slot ->
        inv[slot]?.let { InventoryObject(slot, it.id, it.count) } ?: InventoryObject.NULL
    }

    /** An offer's worth at Grand Exchange prices, items without one counting nothing. */
    private fun value(offer: Inventory): Long =
        offer.contents().sumOf { (_, obj) -> (unitValue(obj.id) ?: 0L) * obj.count }

    /**
     * Whether players can trade [type]. The cache's flag (opcode 65) is the Grand Exchange's, which coins and platinum
     * tokens lack though players trade them (OSRS Wiki, Coins and Platinum token).
     */
    private fun tradeable(type: ObjType): Boolean =
        type.tradeable || type.id == GrandExchange.COINS || type.id == GrandExchange.PLATINUM_TOKEN

    /** Coins and platinum tokens aren't on the Grand Exchange: 1 and 1,000 coins each (OSRS Wiki, Platinum token). */
    private fun unitValue(id: Int): Long? = when (id) {
        GrandExchange.COINS -> 1L
        GrandExchange.PLATINUM_TOKEN -> 1_000L
        else -> objTypes[id]?.let(price)
    }

    /** Inferred, from the Wiki's 10,435,672 in full on the second screen: whole numbers with commas. */
    private fun format(value: Long): String = "%,d".format(value)

    /**
     * Offer (op 1), Offer-5, Offer-10, Offer-All and Offer-X (ops 2 to 5) on inventory [slot] holding [obj]: as many of
     * that item as asked (up to what the inventory has) go from the inventory to the offer, the clicked stack first.
     */
    private fun offer(player: Player, slot: Int, obj: Int, count: Int) {
        val trade = player.trade ?: return
        // An Offer-X prompt left open while both accepted answers after the second screen is up: nothing may change.
        if (trade.over || trade.confirming || count <= 0 || slot !in 0 until Inventory.INV_CAPACITY) return
        val held = player.inventory[slot]?.takeIf { it.id == obj } ?: return
        val type = objTypes[held.id] ?: return
        if (!tradeable(type)) {
            message(player, TradeLines.UNTRADEABLE)
            return
        }
        val side = trade.sideOf(player)
        if (type.stackable) side.stackable += type.id
        var left = count
        // The clicked slot first, then the rest of the item's slots, as one stack can only be in one.
        val slots = listOf(slot) + (0 until Inventory.INV_CAPACITY).filter { it != slot && player.inventory[it]?.id == held.id }
        for (from in slots) {
            if (left == 0) break
            val have = player.inventory[from]?.takeIf { it.id == held.id } ?: continue
            val taking = minOf(left, have.count)
            val added = side.offer.add(type, taking)
            if (added == 0) break
            player.inventory.remove(from, added)
            left -= added
        }
        if (left != count) changed(trade, removed = null)
    }

    /** Remove (op 1), Remove-5, Remove-10, Remove-All and Remove-X (ops 2 to 5) on [slot] of the player's offer. */
    private fun remove(player: Player, slot: Int, obj: Int, count: Int) {
        val trade = player.trade ?: return
        if (trade.over || trade.confirming || count <= 0 || slot !in 0 until Inventory.INV_CAPACITY) return
        val side = trade.sideOf(player)
        val held = side.offer[slot]?.takeIf { it.id == obj } ?: return
        val type = objTypes[held.id] ?: return
        var left = count
        val slots = listOf(slot) + (0 until Inventory.INV_CAPACITY).filter { it != slot && side.offer[it]?.id == held.id }
        val emptied = ArrayList<Int>()
        for (from in slots) {
            if (left == 0) break
            val have = side.offer[from]?.takeIf { it.id == held.id } ?: continue
            val taking = minOf(left, have.count)
            val added = player.inventory.add(type, taking)
            if (added == 0) break
            side.offer.remove(from, added)
            emptied += from
            left -= added
        }
        if (left != count) changed(trade, removed = player to emptied)
    }

    /**
     * An offer changed: both accepts are off (live's rule that a change means the trade is checked again; inferred from
     * the Wiki's Trading page), and a removal marks the slot red on both screens: varbits trade_this_player_removed and
     * trade_other_player_removed, and the client's flasher (trade_slot_changed, 0 for your own offer, 1 for the other's).
     */
    private fun changed(trade: Trade, removed: Pair<Player, List<Int>>?) {
        for (side in listOf(trade.firstSide, trade.secondSide)) {
            side.accepted = false
            side.dirty = true
            // The Accept trade delay (OSRS Wiki, Settings): after a change, Accept waits 3 seconds, unless the player
            // turned it off (varbit trade_delay_disabled). The client counts it down from varbit trade_accept_delay,
            // showing "Check offer!" (trade_main_drawitems). Inferred: it counts in ticks, as the script converts it to
            // seconds, and a change by either player starts it for both.
            if (varbit(side.player, VARBIT_DELAY_DISABLED) == 0) {
                side.acceptDelay = ACCEPT_DELAY_TICKS
                setVarbit(side.player, VARBIT_ACCEPT_DELAY, ACCEPT_DELAY_TICKS)
            }
        }
        val (remover, slots) = removed ?: return
        val partner = trade.partnerOf(remover)
        setVarbit(remover, VARBIT_THIS_REMOVED, 1)
        setVarbit(partner, VARBIT_OTHER_REMOVED, 1)
        for (slot in slots) {
            send(remover, RunClientScript(TRADE_SLOT_CHANGED_SCRIPT, listOf(0, slot)))
            send(partner, RunClientScript(TRADE_SLOT_CHANGED_SCRIPT, listOf(1, slot)))
        }
    }

    /**
     * Accept, on either screen. Both accepting the first moves both to the second, once each has room for what they
     * get; both accepting the second completes the trade.
     */
    private fun accept(player: Player, confirming: Boolean) {
        val trade = player.trade ?: return
        if (trade.over || trade.confirming != confirming) return
        val side = trade.sideOf(player)
        if (side.accepted) return
        if (!confirming && side.acceptDelay > 0) return
        side.accepted = true
        side.dirty = true
        val partnerSide = trade.partnerSideOf(player)
        partnerSide.dirty = true
        if (!partnerSide.accepted) return
        if (!roomFor(trade)) return
        if (confirming) complete(trade) else confirm(trade)
    }

    /**
     * Whether each player has room for what the other offers, counting what they offer themselves as gone. One without
     * is told so, the partner that the other has no room, and both accepts go off (OSRS Wiki, Trading; words guessed).
     */
    private fun roomFor(trade: Trade): Boolean {
        for (side in listOf(trade.firstSide, trade.secondSide)) {
            val other = if (side === trade.firstSide) trade.secondSide else trade.firstSide
            if (receive(side.player.inventory, other) != null) continue
            message(side.player, TradeLines.NO_ROOM)
            message(other.player, TradeLines.OTHER_NO_ROOM)
            for (each in listOf(trade.firstSide, trade.secondSide)) {
                each.accepted = false
                each.dirty = true
            }
            return false
        }
        return true
    }

    /** [inventory] with [from]'s offer added, as a new inventory; null when it doesn't all fit. */
    private fun receive(inventory: Inventory, from: Trade.Side): Inventory? {
        val result = Inventory(inventory.type, inventory.capacity)
        for ((slot, obj) in inventory.contents()) result[slot] = obj
        for ((_, obj) in from.offer.contents()) {
            val type = objTypes[obj.id] ?: return null
            if (result.add(type, obj.count) < obj.count) return null
        }
        return result
    }

    /**
     * The second screen, tradeconfirm, in the first's place: both offers listed (the client draws them from the same
     * two inventories), each side's value, and Accept again. The side panel's inventory stays, but its Offer options
     * do nothing from here (inferred: nothing may change once both have accepted).
     */
    private fun confirm(trade: Trade) {
        trade.confirming = true
        for (side in listOf(trade.firstSide, trade.secondSide)) {
            side.accepted = false
            side.dirty = true
        }
        for (side in listOf(trade.first, trade.second)) {
            val partner = trade.partnerOf(side)
            val text = words(side)
            openModal(side, FrameSlot.MAINMODAL, CONFIRM)
            send(side, IfSetText(CONFIRM, CONFIRM_OPPONENT, text.get(TradeLines.CONFIRM_OPPONENT, "name" to partner.username)))
            send(side, IfSetText(CONFIRM, CONFIRM_GIVE, text.get(TradeLines.GIVE, "value" to format(value(trade.sideOf(side).offer)))))
            send(side, IfSetText(CONFIRM, CONFIRM_RECEIVE, text.get(TradeLines.RECEIVE, "value" to format(value(trade.partnerSideOf(side).offer)))))
        }
    }

    /**
     * Both accepted the second screen: each offer goes to the other player in one go, inside this click, and both are
     * saved together through [commit]. Room was checked as they accepted and nothing can have changed since; it is
     * checked again, and a trade that no longer fits ends as a decline would, everything given back.
     */
    private fun complete(trade: Trade) {
        val firstGets = receive(trade.first.inventory, trade.secondSide)
        val secondGets = receive(trade.second.inventory, trade.firstSide)
        if (firstGets == null || secondGets == null) {
            logger.error { "Trade between ${trade.first.username} and ${trade.second.username} no longer fits; ending it" }
            decline(trade.first)
            return
        }
        trade.over = true
        val inventories = listOf(trade.first, trade.second).map { player -> player to (0 until player.inventory.capacity).map { player.inventory[it] } }
        val offers = listOf(trade.firstSide, trade.secondSide).map { side -> side to (0 until side.offer.capacity).map { side.offer[it] } }
        val committed = commit(trade.first, trade.second, {
            for ((player, gets) in listOf(trade.first to firstGets, trade.second to secondGets)) {
                for (slot in 0 until player.inventory.capacity) player.inventory[slot] = gets[slot]
            }
            trade.firstSide.offer.clear()
            trade.secondSide.offer.clear()
            trade.first.trade = null
            trade.second.trade = null
        }) {
            for ((player, held) in inventories) held.forEachIndexed { slot, obj -> player.inventory[slot] = obj }
            for ((side, held) in offers) held.forEachIndexed { slot, obj -> side.offer[slot] = obj }
            trade.first.trade = trade
            trade.second.trade = trade
        }
        if (!committed) {
            // The swap was undone: the trade ends as a decline would, everything given back.
            logger.error { "Trade between ${trade.first.username} and ${trade.second.username} couldn't be saved safely; ending it" }
            trade.over = false
            decline(trade.first)
            return
        }
        for (player in listOf(trade.first, trade.second)) {
            closeModals(player)
            message(player, TradeLines.ACCEPTED)
        }
        logger.info { "${trade.first.username} and ${trade.second.username} traded" }
    }

    /** Puts every offered item back where it came from. */
    private fun giveBack(side: Trade.Side) {
        for ((slot, obj) in side.offer.contents()) {
            side.offer[slot] = null
            val type = objTypes[obj.id]
            val added = if (type == null) 0 else side.player.inventory.add(type, obj.count)
            if (added < obj.count) {
                logger.error { "${side.player.username}'s offered ${obj.id} x ${obj.count - added} had no room back in the inventory" }
                overflow(side.player, InvObj(obj.id, obj.count - added))
            }
        }
    }

    /** [player] declined, closed the screen or left: the trade ends for both, and the partner is told. */
    fun decline(player: Player) {
        val trade = player.trade ?: return
        if (trade.over) return
        trade.over = true
        giveBack(trade.firstSide)
        giveBack(trade.secondSide)
        val partner = trade.partnerOf(player)
        player.trade = null
        partner.trade = null
        closeModals(player)
        if (inWorld(partner)) {
            closeModals(partner)
            message(partner, TradeLines.DECLINED)
        }
        logger.info { "${player.username} ended their trade with ${partner.username}" }
    }

    /** A click on the trade screens. Returns false for one that isn't the trade's. */
    fun click(player: Player, interfaceId: Int, componentId: Int, sub: Int, obj: Int, op: Int): Boolean {
        if (interfaceId != MAIN && interfaceId != SIDE && interfaceId != CONFIRM) return false
        val trade = player.trade ?: return true
        when {
            interfaceId == MAIN && componentId == MAIN_DECLINE -> decline(player)
            interfaceId == MAIN && componentId == MAIN_ACCEPT -> accept(player, confirming = false)
            interfaceId == CONFIRM && componentId == CONFIRM_ACCEPT -> accept(player, confirming = true)
            interfaceId == CONFIRM && componentId == CONFIRM_DECLINE -> decline(player)
            trade.confirming && op != EXAMINE -> {}
            interfaceId == SIDE && componentId == SIDE_ITEMS -> when (op) {
                EXAMINE -> player.inventory.getOrNull(sub)?.let { examineObj(player, it) }
                5 -> startScript(player) {
                    val amount = countDialog()
                    if (player.trade === trade) offer(player, sub, obj, amount)
                }
                else -> offer(player, sub, obj, amountFor(op, player.inventory.count(obj)))
            }
            interfaceId == MAIN && componentId == MAIN_YOUR_OFFER -> when (op) {
                EXAMINE -> trade.sideOf(player).offer.getOrNull(sub)?.let { examineObj(player, it) }
                5 -> startScript(player) {
                    val amount = countDialog()
                    if (player.trade === trade) remove(player, sub, obj, amount)
                }
                else -> remove(player, sub, obj, amountFor(op, trade.sideOf(player).offer.count(obj)))
            }
            interfaceId == MAIN && componentId == MAIN_OTHER_OFFER && op == EXAMINE ->
                trade.partnerSideOf(player).offer.getOrNull(sub)?.let { examineObj(player, it) }
        }
        return true
    }

    /** How many ops 1 to 4 move: 1, 5, 10, or everything ([all]). */
    private fun amountFor(op: Int, all: Long): Int = when (op) {
        1 -> 1
        2 -> 5
        3 -> 10
        4 -> all.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        else -> 0
    }

    private fun examineObj(player: Player, obj: InvObj) {
        val type = objTypes[obj.id] ?: return
        examine(player, type, obj.count)
    }

    private fun Inventory.getOrNull(slot: Int): InvObj? = if (slot in 0 until capacity) this[slot] else null

    /** Anything else the player does in the world (a walk, a click elsewhere, an admin command) leaves their trade. */
    fun interrupt(player: Player) {
        if (player.trade != null) closeModals(player)
        // closeModals declines through the screen's close hook; this is for a trade whose screen was already gone.
        decline(player)
    }

    /**
     * The first screen shut. Opening the second screen over it shuts it too, which leaves the trade standing; any other
     * close (Escape, the close button, another screen) declines.
     */
    fun mainClosed(player: Player) {
        if (player.trade?.confirming == true) return
        decline(player)
    }

    private fun gone(player: Player) = player.loggedOut || player.connectionLost || player.connectionLostTick >= 0

    /** [player] is leaving the world: their trade ends and their standing request goes. */
    fun leave(player: Player) {
        decline(player)
        player.tradeRequestTo = null
    }

    private fun message(player: Player, line: Line) = player.message(words(player)[line])

    companion object {
        /** The trade screen, trademain, and the inventory beside it, tradeside. */
        const val MAIN = 335
        const val SIDE = 336

        /** The second screen, tradeconfirm: accept, decline, the opponent's name and the two headers. */
        const val CONFIRM = 334
        const val CONFIRM_TITLE = 4
        const val CONFIRM_ACCEPT = 13
        const val CONFIRM_DECLINE = 14
        const val CONFIRM_GIVE = 23
        const val CONFIRM_RECEIVE = 24
        const val CONFIRM_OPPONENT = 30

        /** trademain's components: accept, decline, the two offers, their headers, free space, status and title. */
        const val MAIN_ACCEPT = 10
        const val MAIN_DECLINE = 13
        const val MAIN_FREE_SPACE = 9
        const val MAIN_YOUR_HEADER = 24
        const val MAIN_YOUR_OFFER = 25
        const val MAIN_OTHER_HEADER = 27
        const val MAIN_OTHER_OFFER = 28
        const val MAIN_STATUS = 30
        const val MAIN_TITLE = 31

        /** tradeside's layer, which interface_inv_init fills with the inventory. */
        const val SIDE_ITEMS = 0

        /** The inventory both offers are (tradeoffer); the partner's goes as a mirror, any combined id under -70000. */
        const val TRADEOFFER = 90
        const val MIRROR = -70_001

        /** Varbits trade_this_player_removed and trade_other_player_removed, which mark a removal on the screens. */
        const val VARBIT_THIS_REMOVED = 4374
        const val VARBIT_OTHER_REMOVED = 4375

        /** Varbits trade_accept_delay (the ticks left, counted down) and trade_delay_disabled (the setting, 1 for off). */
        const val VARBIT_ACCEPT_DELAY = 13141
        const val VARBIT_DELAY_DISABLED = 13130

        /** OSRS Wiki, Settings: "you will have to wait 3 seconds after a change is made". */
        const val ACCEPT_DELAY_TICKS = 5

        /** Client scripts: trade_partner_set (the trade chat tab's partner), trade_slot_changed, interface_inv_init. */
        const val TRADE_PARTNER_SET_SCRIPT = 209
        const val TRADE_SLOT_CHANGED_SCRIPT = 765
        const val INTERFACE_INV_INIT_SCRIPT = 149

        /** Examine is op 10 on an inventory item. */
        const val EXAMINE = 10

        /** Ops 1 to 5 and 10 (op n is bit n - 1), and Examine alone. */
        const val EXAMINE_OP = 1 shl (EXAMINE - 1)
        const val OFFER_OPS = 0b1_1111 or EXAMINE_OP

        private const val OP_COLOUR = "<col=ff9040>"

        /** The chat type of a trade request, chattype_tradereq: shown with Accept trade on it. */
        const val CHATTYPE_TRADEREQ = 101
    }
}

/** Trading's game messages; words from the OSRS Wiki's Trading page. */
object TradeLines : Lines("trade", "Trading") {
    val SENDING = line("sending", "the asker's line when Trade with reaches the other player")
    val REQUEST = line("request", "the other player's trade request line in their chat", "name")
    val BUSY = line("busy", "asking a player who has a bank, the equipment stats or a trade open")
    val DECLINED = line("declined", "the partner declined or closed the trade screen, or left")
    val TITLE = line("title", "the trade screen's title", "name")
    val YOUR_OFFER = line("your_offer", "the header over your offer, with its value; layout guessed", "value")
    val OTHER_OFFER = line("other_offer", "the header over the partner's offer, with its value; layout guessed", "name", "value")
    val FREE_SLOTS = line("free_slots", "the partner's free inventory slots under the offers; words guessed", "name", "count")
    val WAITING = line("waiting", "the status line after you accept")
    val OTHER_ACCEPTED = line("other_accepted", "the status line after the partner accepts")
    val UNTRADEABLE = line("untradeable", "offering an item that can't be traded; words guessed")
    val NO_ROOM = line("no_room", "accepting without room for the partner's offer; words guessed")
    val OTHER_NO_ROOM = line("other_no_room", "the partner accepted without room for your offer; words guessed")
    val ARE_YOU_SURE = line("are_you_sure", "the second screen's title until someone accepts")
    val CONFIRM_OPPONENT = line("confirm_opponent", "the second screen's line naming the partner", "name")
    val GIVE = line("give", "the second screen's header over what you give, with its value; layout guessed", "value")
    val RECEIVE = line("receive", "the second screen's header over what you get, with its value; layout guessed", "value")
    val ACCEPTED = line("accepted", "the trade completed; words guessed")
}
