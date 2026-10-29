package osrsps.game.bank

import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetHide
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.Player

/**
 * The Bank PIN Settings screen (bankpin_settings, 14), which a banker's "PIN settings" option opens: here always in its
 * no-PIN state, since PINs aren't built (setting one, its 7-day wait, the keypad, asking for it at the bank).
 *
 * The screen's layers start hidden in the cache; the server shows the outer one and the no-PIN buttons (Set a PIN,
 * Change your recovery delay), which are pause buttons, and fills in the status lines. Guessed, not seen on live: the
 * three status lines' words (the Wiki gives none; 7 days is its wait for a new PIN) and that nothing shows in the
 * message box. The buttons say PINs aren't available, which is ours.
 */
class BankPinSettings(
    private val openModal: (Player, FrameSlot, Int) -> Unit,
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == INTERFACE

    fun open(player: Player) {
        openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        send(player, IfSetHide(INTERFACE, SETTINGS, false))
        send(player, IfSetHide(INTERFACE, BUTTONS_NOPIN, false))
        send(player, IfSetText(INTERFACE, STATUS, NO_PIN))
        send(player, IfSetText(INTERFACE, DELAY, DEFAULT_DELAY))
        send(player, IfSetText(INTERFACE, LOGOUT, DEFAULT_LOGOUT))
        // A player's click sends resume_pausebutton; op 1 lets a click as the test runner makes it (an if_button) through too.
        for (button in BUTTONS) send(player, IfSetEventsV2(INTERFACE, button, -1, -1, EVENT_PAUSE_BUTTON, BankScreen.opMask(1..1)))
    }

    /** A click on one of the screen's buttons (a resume_pausebutton or an if_button). Returns false when it isn't one. */
    fun resume(player: Player, interfaceId: Int, componentId: Int): Boolean {
        if (interfaceId != INTERFACE || componentId !in BUTTONS) return false
        if (isOpen(player)) send(player, MessageGame(CHATTYPE_GAMEMESSAGE, NOT_AVAILABLE))
        return true
    }

    companion object {
        const val INTERFACE = 14
        const val SETTINGS = 0
        const val STATUS = 6
        const val DELAY = 8
        const val LOGOUT = 10
        const val BUTTONS_NOPIN = 18
        const val SET = 19
        const val DELAY_NOPIN = 20
        val BUTTONS = setOf(SET, DELAY_NOPIN)

        const val NO_PIN = "No PIN set"
        const val DEFAULT_DELAY = "7 days"
        const val DEFAULT_LOGOUT = "Stay unlocked"
        const val NOT_AVAILABLE = "Bank PINs aren't available on this world."

        private const val EVENT_PAUSE_BUTTON = 1
        private const val CHATTYPE_GAMEMESSAGE = 0

        /** Every id above with its gameval name in the 241 cache, for [osrsps.cache.GameFrameCheck]. */
        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            INTERFACE to (
                "bankpin_settings" to mapOf(
                    SETTINGS to "settings", STATUS to "statusoutput", DELAY to "delayoutput", LOGOUT to "logoutoutput",
                    BUTTONS_NOPIN to "buttons_nopin", SET to "set", DELAY_NOPIN to "delay0",
                )
                ),
        )
    }
}
