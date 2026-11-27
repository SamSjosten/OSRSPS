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
 * Attack is slot 2, given only while the player is inside the Wilderness ([ATTACK_TEXT]; World's Wilderness update) and
 * cleared on leaving. The client orders it by the player's attack priority setting itself. Inferred: slot 2 and no
 * priority flag (RuneLite names slot 2's menu action PLAYER_SECOND_OPTION, the one Attack uses). Report opens the
 * report screen ([osrsps.game.social.Reports]).
 */
object PlayerOps {
    const val ATTACK = 2
    const val ATTACK_TEXT = "Attack"
    const val FOLLOW = 3
    const val TRADE = 4
    const val REPORT = 8
    const val SLOT_5 = 5

    /** Slot to text; null clears the slot. */
    val AT_LOGIN: List<Pair<Int, String?>> = listOf(
        ATTACK to null,
        FOLLOW to "Follow",
        TRADE to "Trade with",
        SLOT_5 to null,
        REPORT to "Report",
    )

    const val VARBIT_TRADE_HIDDEN = 6580
    const val VARBIT_REPORT_HIDDEN = 1334

    /**
     * Sends the options [vars]' settings leave on through [queue], with Attack while [attack] (the player is inside the
     * Wilderness) and [slot5] in slot 5 (the rat pits' Challenge), so a settings toggle that sends them all again keeps them.
     */
    fun send(vars: PlayerVars, queue: (OutgoingGameMessage) -> Unit, attack: Boolean = false, slot5: String? = null) {
        for ((slot, text) in AT_LOGIN) {
            val shown = when (slot) {
                ATTACK -> ATTACK_TEXT.takeIf { attack }
                SLOT_5 -> slot5
                else -> text?.takeIf { shown(vars, slot) }
            }
            queue(SetPlayerOp(slot, false, shown))
        }
    }

    /** Whether the option in [slot] is on the menu, by the settings that can hide it. */
    fun shown(vars: PlayerVars, slot: Int): Boolean = when (slot) {
        TRADE -> !hidden(vars, VARBIT_TRADE_HIDDEN)
        REPORT -> !hidden(vars, VARBIT_REPORT_HIDDEN)
        else -> true
    }

    private fun hidden(vars: PlayerVars, varbit: Int) = vars.types.varbit(varbit) != null && vars.getVarbit(varbit) == 1
}
