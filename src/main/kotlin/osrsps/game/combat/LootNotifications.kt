package osrsps.game.combat

import osrsps.game.AllSettings
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Lines
import osrsps.game.ge.GrandExchange

/** The loot notifications' chat messages, in the dialogue file. */
object LootLines : Lines("loot", "Settings") {
    val VALUABLE = line("valuable", "the chat message for a drop worth the loot notification value or more", "item", "value")
    val UNTRADEABLE = line("untradeable", "the chat message for an untradeable drop, with untradeable loot notifications on", "item")
}

/**
 * The All Settings "Loot drop notifications" (varbit option_lootnotification_on) with its "Minimum item value needed
 * for loot notification" (option_lootnotification_value), and "Untradeable loot notifications"
 * (option_lootnotification_untradeables), which only works with the first on. Their descriptions in the cache: a
 * chatbox message for a drop "above the threshold", and one "when you receive an untradeable item as a drop".
 *
 * Guessed, not from a capture: the value is the whole stack's at the Grand Exchange price, else high alchemy, and a
 * stack worth exactly the threshold counts; the item reads "N x Name" for more than one. Coins and platinum tokens
 * aren't on the Grand Exchange but are tradeable, at 1 and 1,000 coins each, as in [osrsps.game.trade.Trades].
 */
class LootNotifications(
    /** The Grand Exchange price ([osrsps.game.ItemPrices]), null for none. */
    private val price: (ObjType) -> Long?,
    private val words: (Player) -> DialogueText,
    private val message: (Player, String) -> Unit = { player, text -> player.message(text) },
) {
    /** Tells [player], whose loot it is, about [count] of [type] dropped for them, if their settings ask for it. */
    fun dropped(player: Player, type: ObjType, count: Int) {
        if (varbit(player, VARBIT_ON) != 1) return
        val item = if (count > 1) "$count x ${type.name}" else type.name
        val unit = currencyValue(type.id)
        if (unit == null && !type.tradeable) {
            if (varbit(player, VARBIT_UNTRADEABLE) == 1) message(player, words(player)[LootLines.UNTRADEABLE, "item" to item])
            return
        }
        val threshold = varbit(player, VARBIT_VALUE)
        val value = (unit ?: price(type) ?: type.highAlch.toLong()) * count
        if (threshold > 0 && value >= threshold) {
            message(player, words(player)[LootLines.VALUABLE, "item" to item, "value" to "%,d".format(value)])
        }
    }

    private fun currencyValue(id: Int): Long? = when (id) {
        GrandExchange.COINS -> 1L
        GrandExchange.PLATINUM_TOKEN -> 1_000L
        else -> null
    }

    private fun varbit(player: Player, id: Int): Int = if (player.vars.types.varbit(id) != null) player.vars.getVarbit(id) else 0

    companion object {
        const val VARBIT_ON = AllSettings.VARBIT_LOOT_NOTIFICATION
        const val VARBIT_VALUE = AllSettings.VARBIT_LOOT_NOTIFICATION_VALUE
        const val VARBIT_UNTRADEABLE = 5402
    }
}
