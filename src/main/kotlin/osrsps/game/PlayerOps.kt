package osrsps.game

import net.rsprot.protocol.game.outgoing.misc.player.SetPlayerOp
import net.rsprot.protocol.message.OutgoingGameMessage

/**
 * The options on other players' right-click menus, which the server gives the client at login. The slots and words are
 * RSMod's login script (rev 233): Follow 3, Trade with 4, Report 8, slots 2 and 5 cleared. Not checked against a live
 * login. None has priority, so a left click on a player still walks, as on live.
 *
 * The All Settings toggles "Player Trade options" and "Player Report options" (varbits tradeoption_disabled and
 * rightclick_report_abuse_disabled, 1 for hidden; the rows show them inverted) clear those slots, at login and as they
 * are clicked. Inferred: the client has no script reading them, so the server must leave the options out.
 *
 * Not built: Attack (slot 2 in the Wilderness and on PvP worlds), and Report.
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
    const val REPORT_NOT_BUILT = "Reporting isn't built on this server yet."

    const val VARBIT_TRADE_HIDDEN = 6580
    const val VARBIT_REPORT_HIDDEN = 1334

    /** Sends the options [vars]' settings leave on through [queue]. */
    fun send(vars: PlayerVars, queue: (OutgoingGameMessage) -> Unit) {
        for ((slot, text) in AT_LOGIN) queue(SetPlayerOp(slot, false, text?.takeIf { shown(vars, slot) }))
    }

    /** Whether the option in [slot] is on the menu, by the settings that can hide it. */
    fun shown(vars: PlayerVars, slot: Int): Boolean = when (slot) {
        TRADE -> !hidden(vars, VARBIT_TRADE_HIDDEN)
        REPORT -> !hidden(vars, VARBIT_REPORT_HIDDEN)
        else -> true
    }

    private fun hidden(vars: PlayerVars, varbit: Int) = vars.types.varbit(varbit) != null && vars.getVarbit(varbit) == 1
}
