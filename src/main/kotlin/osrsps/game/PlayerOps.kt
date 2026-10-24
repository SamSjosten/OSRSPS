package osrsps.game

import net.rsprot.protocol.game.outgoing.misc.player.SetPlayerOp
import net.rsprot.protocol.message.OutgoingGameMessage

/**
 * The options on other players' right-click menus, which the server gives the client at login. The slots and words are
 * RSMod's login script (rev 233): Follow 3, Trade with 4, Report 8, slots 2 and 5 cleared. Not checked against a live
 * login. None has priority, so a left click on a player still walks, as on live.
 *
 * Not built: Attack (slot 2 in the Wilderness and on PvP worlds), and anything but Follow doing what it does on live.
 */
object PlayerOps {
    const val FOLLOW = 3
    const val TRADE = 4
    const val REPORT = 8

    /** Slot to text; null clears the slot. */
    val AT_LOGIN: List<Pair<Int, String?>> = listOf(
        2 to null,
        FOLLOW to "Follow",
        TRADE to "Trade with",
        5 to null,
        REPORT to "Report",
    )

    /** Not live's: tells the tester the option isn't built yet. */
    const val TRADE_NOT_BUILT = "Trading isn't built on this server yet."
    const val REPORT_NOT_BUILT = "Reporting isn't built on this server yet."

    /** Sends the login options through [queue]. */
    fun send(queue: (OutgoingGameMessage) -> Unit) {
        for ((slot, text) in AT_LOGIN) queue(SetPlayerOp(slot, false, text))
    }
}
