package osrsps.game

import net.rsprot.protocol.game.outgoing.interfaces.IfCloseSub
import net.rsprot.protocol.game.outgoing.interfaces.IfMoveSub
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenTop
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.misc.player.TriggerOnDialogAbort
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.dialogue.Chatbox

/**
 * Opens, moves and closes interfaces in a player's game frame.
 *
 * Modals (the bank, a shop) are kept by [FrameSlot], so they follow the player through a frame change and close
 * on walking or when the client asks. Dialogues are modals too, opened into the chatbox's chatmodal, which moves with
 * the chatbox. Overlays (the side panels, chatbox, orbs) stay open for the whole session.
 *
 * The frame change sequence copies RSMod's, which it emulates from live packet captures. None of it has been
 * checked against live by us.
 */
class Interfaces(private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) }) {
    /** Opens [Player.frame] and its overlays on login. */
    fun openGameFrame(player: Player) {
        val frame = player.frame
        send(player, IfOpenTop(frame.interfaceId))
        for (overlay in GameFrame.overlays) {
            send(player, IfOpenSub(frame.interfaceId, frame.component(overlay.slot), overlay.interfaceId, OVERLAY))
        }
        openJournalTab(player, JournalTab.forValue(journalTabVarbit(player)?.let { player.vars.getVarbit(it) } ?: 0))
        // Lets the client send clicks on the "Game client layout" dropdown's three options.
        send(player, IfSetEventsV2(GameFrame.SETTINGS_SIDE, GameFrame.SETTINGS_CLIENT_LAYOUT_BUTTONS, 1, 3, IF_EVENT_OP1, 0))
        // Inferred: the client's scroll-wheel zoom clamps to varcs camera_zoom_small/big_min/max, which start at 0.
        // In the 241 cache only script 626 (no arguments, 128 to 896) or 603/604 with explicit limits set them, and
        // no script or main game interface calls 626, so the server has to. Not checked against a live login.
        send(player, RunClientScript(GameFrame.ZOOM_LIMITS_DEFAULT_SCRIPT, emptyList()))
    }

    /**
     * The player picked [dest] in the "Game client layout" dropdown. A change between fixed and resizable first
     * switches the client's window mode and moves the interfaces two ticks later; a change between the two
     * resizable layouts moves them next tick (RSMod's delays).
     */
    fun requestFrame(player: Player, dest: GameFrame, tick: Long) {
        // A choice made while an earlier one waits for its tick replaces it; the client's window mode already
        // follows the earlier choice, so compare with that.
        val pending = player.pendingFrameMove
        val chosen = pending?.dest ?: player.frame
        if (dest == chosen) return
        if (dest.resizable != chosen.resizable) send(player, RunClientScript(GameFrame.CLIENT_MODE_SCRIPT, listOf(dest.clientMode)))
        val current = player.frame
        if (dest == current) {
            player.pendingFrameMove = null
            return
        }
        // RSMod: going from fixed to a resizable layout whose stone arrangement isn't the current one goes through
        // the resizable layout that matches the current arrangement first.
        val intermediate = if (dest.resizable && !current.resizable && player.stoneArrangement != dest.stoneArrangement) {
            GameFrame.entries.first { it.resizable && it.stoneArrangement == player.stoneArrangement }
        } else {
            null
        }
        val dueTick = tick + if (dest.resizable != current.resizable) 2 else 1
        player.pendingFrameMove = FrameMove(current, dest, intermediate, maxOf(dueTick, pending?.dueTick ?: 0))
    }

    /** Moves the interfaces into the frame the player asked for once its tick comes. */
    fun processFrameMove(player: Player, tick: Long) {
        val move = player.pendingFrameMove ?: return
        if (tick < move.dueTick) return
        player.pendingFrameMove = null
        if (move.dest.resizable && player.stoneArrangement != move.dest.stoneArrangement) {
            player.stoneArrangement = move.dest.stoneArrangement
            // Sent now rather than with the end-of-cycle var flush so it reaches the client before the frame moves. The
            // flush then resends the same value.
            sendVarp(player, GameFrame.STONE_ARRANGEMENT_VARP, player.vars[GameFrame.STONE_ARRANGEMENT_VARP])
        }
        if (move.intermediate != null) {
            moveFrame(player, move.from, move.intermediate)
            moveFrame(player, move.intermediate, move.dest)
        } else {
            moveFrame(player, move.from, move.dest)
        }
        reopenOrbs(player)
    }

    private fun moveFrame(player: Player, from: GameFrame, dest: GameFrame) {
        player.frame = dest
        send(player, IfOpenTop(dest.interfaceId))
        for (slot in FrameSlot.entries) {
            send(player, IfMoveSub(from.interfaceId, from.component(slot), dest.interfaceId, dest.component(slot)))
            when (slot) {
                FrameSlot.XP_DROPS -> reopenOrbs(player)
                FrameSlot.TLI_LISTENER -> {
                    send(player, RunClientScript(GameFrame.SETTINGS_INTERFACE_SCALING_SCRIPT, emptyList()))
                    send(player, RunClientScript(GameFrame.BUFF_BAR_LAYOUT_REDRAW_SCRIPT, emptyList()))
                }
                // RSMod also sets op1 events on toplevel_osrs_stretch's side stones after each side moves. Left out
                // until a side tab fails to open after a layout change.
                else -> Unit
            }
        }
    }

    private fun reopenOrbs(player: Player) {
        val frame = player.frame
        send(player, IfOpenSub(frame.interfaceId, frame.component(FrameSlot.ORBS), GameFrame.ORBS, OVERLAY))
    }

    /**
     * Shows [tab] inside the quest tab. The sub-interface sits in side_journal, not the top-level, so it moves with
     * side_journal on a frame change and is only opened at login and when the player picks another tab.
     */
    fun openJournalTab(player: Player, tab: JournalTab) {
        journalTabVarbit(player)?.let { player.vars.setVarbit(it, tab.value) }
        send(player, IfOpenSub(JournalTab.SIDE_JOURNAL, JournalTab.TAB_CONTAINER, tab.interfaceId, OVERLAY))
    }

    /** Null when the cache has no such varbit (tests); the tab then still opens but isn't remembered. */
    private fun journalTabVarbit(player: Player): Int? =
        JournalTab.VARBIT_SIDE_JOURNAL_TAB.takeIf { player.vars.types.varbit(it) != null }

    /** Opens [interfaceId] as a modal in [slot], replacing whatever modal was there. */
    fun openModal(player: Player, slot: FrameSlot, interfaceId: Int) {
        player.modals[slot] = interfaceId
        val frame = player.frame
        send(player, IfOpenSub(frame.interfaceId, frame.component(slot), interfaceId, MODAL))
    }

    /**
     * Opens [interfaceId] in the chatbox's chatmodal, replacing the dialogue there, as RSMod's captures of live have it:
     * varbit chatmodal_unclamp set to [unclamp], the background reset script, then the sub. A null [unclamp] opens the
     * sub alone, which RSMod does for a messagebox.
     */
    fun openChatModal(player: Player, interfaceId: Int, unclamp: Int?) {
        if (unclamp != null) {
            val varbit = player.vars.types.varbit(Chatbox.VARBIT_CHATMODAL_UNCLAMP)
            if (varbit != null) {
                player.vars.setVarbit(Chatbox.VARBIT_CHATMODAL_UNCLAMP, unclamp)
                // Sent now, ahead of the sub, rather than with the end-of-cycle flush, which then resends it.
                sendVarp(player, varbit.baseVar, player.vars[varbit.baseVar])
            }
            send(player, RunClientScript(Chatbox.RESET_BACKGROUND_SCRIPT, emptyList()))
        }
        player.chatModal = interfaceId
        player.chatModalOpens++
        send(player, IfOpenSub(Chatbox.CHATBOX, Chatbox.CHATMODAL, interfaceId, MODAL))
    }

    /** Closes the dialogue in the chatbox, if any; a script waiting on it is dropped. */
    fun closeChatModal(player: Player) {
        if (player.chatModal >= 0) {
            send(player, IfCloseSub(Chatbox.CHATBOX, Chatbox.CHATMODAL))
            player.chatModal = -1
        }
        dropDialogueScript(player)
    }

    /**
     * Closes every open modal, a dialogue included. Walking, the client's close (Esc or a close button) and logging out
     * do this.
     */
    fun closeModals(player: Player) {
        closeChatModal(player)
        if (player.modals.isEmpty()) return
        val frame = player.frame
        for (slot in player.modals.keys) send(player, IfCloseSub(frame.interfaceId, frame.component(slot)))
        player.modals.clear()
    }

    /**
     * A dialogue closed before the player answered it: its script never carries on. RSMod tells the client with
     * trigger_ondialogabort, which runs the chatbox's dialogue-abort hooks; inferred from RSMod, not seen on live by us.
     */
    private fun dropDialogueScript(player: Player) {
        if (player.scripts.dropPaused()) send(player, TriggerOnDialogAbort)
    }

    private fun sendVarp(player: Player, id: Int, value: Int) {
        send(player, if (value in Byte.MIN_VALUE..Byte.MAX_VALUE) VarpSmall(id, value) else VarpLarge(id, value))
    }

    companion object {
        const val MODAL = 0
        const val OVERLAY = 1

        /** The first op's bit in an interface event mask. */
        const val IF_EVENT_OP1 = 1 shl 1
    }
}

/** A frame change waiting for its tick. */
class FrameMove(val from: GameFrame, val dest: GameFrame, val intermediate: GameFrame?, val dueTick: Long)
