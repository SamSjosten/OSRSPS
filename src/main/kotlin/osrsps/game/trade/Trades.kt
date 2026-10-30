package osrsps.game.trade

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
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

/** A trade between two players, from the moment both screens open until it ends. */
class Trade(val first: Player, val second: Player) {
    /** Over: declined, closed or completed. Nothing may touch it after. */
    var over = false
        internal set

    fun partnerOf(player: Player): Player = if (player === first) second else first
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
    /** Turns a player to face another, or stops them facing anyone. */
    private val facePlayer: (Player, Player?) -> Unit,
) {
    /** A click on Trade with [target]. */
    fun click(player: Player, target: Player, invertRun: Boolean) {
        if (target === player) return
        val interaction = TradeInteraction(target, this)
        player.interaction = interaction
        facePlayer(player, target)
        route(player, interaction, invertRun)
    }

    /** Walks [player] on toward [interaction]'s target, and asks once beside them. Returns true once it's over. */
    internal fun approach(player: Player, interaction: TradeInteraction): Boolean {
        val target = interaction.target
        if (!inWorld(target) || target.dead || target.level != player.level) {
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
        request(player, target)
        return true
    }

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
        if (target.trade != null || target.modalOpen) {
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

    /** Opens the trade screens for both players. */
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
            facePlayer(side, null)
            openModal(side, FrameSlot.MAINMODAL, MAIN)
            openModal(side, FrameSlot.SIDEMODAL, SIDE)
        }
        logger.info { "${partner.username} and ${player.username} are trading" }
    }

    /** [player] declined, closed the screen or left: the trade ends for both, and the partner is told. */
    fun decline(player: Player) {
        val trade = player.trade ?: return
        if (trade.over) return
        trade.over = true
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

    /** A click on the trade screen. Returns false for one that isn't the trade's. */
    fun click(player: Player, interfaceId: Int, componentId: Int): Boolean {
        if (interfaceId != MAIN) return false
        if (player.trade == null) return true
        when (componentId) {
            MAIN_DECLINE -> decline(player)
        }
        return true
    }

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

        /** trademain:decline. */
        const val MAIN_DECLINE = 13

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
}
