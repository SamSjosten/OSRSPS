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
import osrsps.game.script.ScriptContext
import osrsps.game.social.FriendChats

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
        // The Chat-channel tab's friends chat panel, in side_channels' contents as the journal's tabs sit in theirs, and
        // its Setup button. Guessed: the other three channel tabs (clans, grouping) aren't built, so this one stays.
        send(player, IfOpenSub(FriendChats.SIDE_CHANNELS, FriendChats.SIDE_CHANNELS_CONTENTS, FriendChats.CURRENT, OVERLAY))
        send(player, IfSetEventsV2(FriendChats.CURRENT, FriendChats.CURRENT_SETUP, -1, -1, 0, IF_EVENT_OP1))
        // Lets the client send clicks on the "Game client layout" dropdown's three options.
        send(player, IfSetEventsV2(GameFrame.SETTINGS_SIDE, GameFrame.SETTINGS_CLIENT_LAYOUT_BUTTONS, 1, 3, 0, IF_EVENT_OP1))
        // And on the attack priority dropdowns' options: client-made children 1 to n of their buttons components.
        // Inferred from settings_side_dropdown_open; RSMod sends the same ranges.
        send(player, IfSetEventsV2(SettingsSide.INTERFACE, SettingsSide.ATTACK_PRIORITY_PLAYER_BUTTONS, 1, SettingsSide.PLAYER_PRIORITY_OPTIONS, 0, IF_EVENT_OP1))
        send(player, IfSetEventsV2(SettingsSide.INTERFACE, SettingsSide.ATTACK_PRIORITY_NPC_BUTTONS, 1, SettingsSide.NPC_PRIORITY_OPTIONS, 0, IF_EVENT_OP1))
        // And the volume sliders' IF_SCRIPT_TRIGGER, which each names its bobble (settings_side_*_volume_pos).
        for (volume in SettingsSide.Volume.entries) {
            send(player, IfSetEventsV2(SettingsSide.INTERFACE, volume.slider, -1, -1, IF_EVENT_SCRIPT_TRIGGER, 0))
        }
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

    /** Shows side panel tab [tab] ([GameFrame.SIDE_TAB_COMBAT] and so on), as a click on its stone does. */
    fun switchSideTab(player: Player, tab: Int) {
        send(player, RunClientScript(GameFrame.SIDEBUTTON_SWITCH_SCRIPT, listOf<Any>(tab)))
    }

    /** Opens [interfaceId] in side panel [slot] in place of what is there, as an overlay like the side panels. */
    fun openSide(player: Player, slot: FrameSlot, interfaceId: Int) {
        val frame = player.frame
        send(player, IfOpenSub(frame.interfaceId, frame.component(slot), interfaceId, OVERLAY))
    }

    /** Enables [events] on slots [from] to [to] of a component: an interface's events go when it closes, so after opening it. */
    fun setEvents(player: Player, interfaceId: Int, component: Int, from: Int, to: Int, events: Int, scriptEvents: Int = 0) {
        send(player, IfSetEventsV2(interfaceId, component, from, to, scriptEvents, events))
    }

    /**
     * Opens [interfaceId] in the frame's floater as an overlay, as RSMod opens the All Settings window: it moves with
     * the frame and stays open until the server closes it, walking and other modals leaving it alone (inferred).
     */
    fun openFloater(player: Player, interfaceId: Int) {
        player.floater = interfaceId
        val frame = player.frame
        player.closedWhileAway -= frame.interfaceId to frame.component(FrameSlot.FLOATER)
        send(player, IfOpenSub(frame.interfaceId, frame.component(FrameSlot.FLOATER), interfaceId, OVERLAY))
    }

    /** Opens [interfaceId] as an overlay inside component [component] of [parent], an interface that is open. */
    fun openInside(player: Player, parent: Int, component: Int, interfaceId: Int) {
        player.closedWhileAway -= parent to component
        send(player, IfOpenSub(parent, component, interfaceId, OVERLAY))
    }

    /** Closes what [openInside] opened in [component] of [parent]. */
    fun closeInside(player: Player, parent: Int, component: Int) {
        sendClose(player, parent, component)
    }

    /**
     * Sends a close of [component] of [parent]. One sent while the connection is lost may never arrive, so it is kept
     * for [resendClosesWhileAway].
     */
    private fun sendClose(player: Player, parent: Int, component: Int) {
        send(player, IfCloseSub(parent, component))
        if (player.connectionLost || player.connectionLostTick >= 0) player.closedWhileAway += parent to component
    }

    /**
     * After a reconnect, closes again what was closed while the connection was lost (a trade ended by the drop, say),
     * so the client doesn't keep showing an interface the server has shut. Opened again since, it is left alone.
     */
    fun resendClosesWhileAway(player: Player) {
        for ((parent, component) in player.closedWhileAway) send(player, IfCloseSub(parent, component))
        player.closedWhileAway.clear()
    }

    /**
     * After a reconnect, closes every modal slot the server has empty, and the chatbox's dialogue if none is open: a
     * close sent before the server noticed the drop never arrived, and isn't in [Player.closedWhileAway].
     */
    fun closeEmptyModalSlots(player: Player) {
        val frame = player.frame
        for (slot in MODAL_SLOTS) if (slot !in player.modals) send(player, IfCloseSub(frame.interfaceId, frame.component(slot)))
        if (player.chatModal < 0) send(player, IfCloseSub(Chatbox.CHATBOX, Chatbox.CHATMODAL))
    }

    /** Runs client script [id] with [args] (ints, and strings in the script's order). */
    fun runClientScript(player: Player, id: Int, args: List<Any>) {
        send(player, RunClientScript(id, args))
    }

    /** Closes what [openFloater] opened, if anything. */
    fun closeFloater(player: Player) {
        if (player.floater < 0) return
        val frame = player.frame
        sendClose(player, frame.interfaceId, frame.component(FrameSlot.FLOATER))
        player.floater = -1
    }

    /** Opens [interfaceId] as a modal in [slot], replacing whatever modal was there. */
    fun openModal(player: Player, slot: FrameSlot, interfaceId: Int) {
        val replaced = player.modals.put(slot, interfaceId)
        // The one it replaces is gone from the client too, so whatever it held is let go as on a close.
        if (replaced != null && replaced != interfaceId && replaced !in player.modals.values) closeHooks[replaced]?.invoke(player)
        val frame = player.frame
        player.closedWhileAway -= frame.interfaceId to frame.component(slot)
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
        player.closedWhileAway -= Chatbox.CHATBOX to Chatbox.CHATMODAL
        send(player, IfOpenSub(Chatbox.CHATBOX, Chatbox.CHATMODAL, interfaceId, MODAL))
    }

    /** Closes the dialogue in the chatbox, if any; a script waiting on it is dropped. */
    fun closeChatModal(player: Player) {
        if (player.chatModal >= 0) {
            sendClose(player, Chatbox.CHATBOX, Chatbox.CHATMODAL)
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
        // Guessed, after RSMod closing the bank's search prompt: an amount prompt closes with the interfaces it was for.
        dropCount(player)
        if (player.scripts.dropTyping()) send(player, RunClientScript(ScriptContext.MESLAYER_CLOSE, listOf(ScriptContext.MESLAYER_STRING)))
        if (player.modals.isEmpty()) return
        val frame = player.frame
        for (slot in player.modals.keys) sendClose(player, frame.interfaceId, frame.component(slot))
        val closed = player.modals.values.toList()
        player.modals.clear()
        for (interfaceId in closed) closeHooks[interfaceId]?.invoke(player)
    }

    /**
     * Drops the script waiting on an amount, if any: the chatbox prompt closes, a late answer does nothing, and the
     * [onCountDropped] hooks run.
     */
    fun dropCount(player: Player) {
        if (!player.scripts.dropCount()) return
        send(player, RunClientScript(ScriptContext.MESLAYER_CLOSE, listOf(ScriptContext.MESLAYER_COUNT)))
        for (hook in countDropHooks) hook(player)
    }

    private val closeHooks = HashMap<Int, (Player) -> Unit>()
    private val countDropHooks = ArrayList<(Player) -> Unit>()

    /** Runs [hook] whenever closing the interfaces drops a script waiting on an amount: for what asked for it. */
    fun onCountDropped(hook: (Player) -> Unit) {
        countDropHooks += hook
    }

    /** Runs [hook] whenever modal [interfaceId] closes, after it has: for what the client keeps open past it. */
    fun onClose(interfaceId: Int, hook: (Player) -> Unit) {
        check(interfaceId !in closeHooks) { "Interface $interfaceId already has a close hook" }
        closeHooks[interfaceId] = hook
    }

    /**
     * A dialogue closed before the player answered it: its script never carries on. RSMod tells the client with
     * trigger_ondialogabort, which runs the chatbox's dialogue-abort hooks; inferred from RSMod, not seen on live by us.
     */
    private fun dropDialogueScript(player: Player) {
        if (player.scripts.dropPaused()) send(player, TriggerOnDialogAbort)
    }

    /**
     * Sets varbit [id] and sends its varp now, ahead of the end-of-cycle flush (which resends it), for an interface
     * opening in the same cycle that reads it as it loads. Does nothing when the cache has no such varbit (tests).
     */
    fun setVarbitNow(player: Player, id: Int, value: Int) {
        val varbit = player.vars.types.varbit(id) ?: return
        player.vars.setVarbit(id, value)
        sendVarp(player, varbit.baseVar, player.vars[varbit.baseVar])
    }

    /** Sends varp [id] now, ahead of the end-of-cycle flush, for an interface opening in the same cycle that reads it. */
    fun sendVarpNow(player: Player, id: Int) = sendVarp(player, id, player.vars[id])

    private fun sendVarp(player: Player, id: Int, value: Int) {
        send(player, if (value in Byte.MIN_VALUE..Byte.MAX_VALUE) VarpSmall(id, value) else VarpLarge(id, value))
    }

    companion object {
        /** The slots [openModal] opens into. */
        val MODAL_SLOTS = listOf(FrameSlot.MAINMODAL, FrameSlot.SIDEMODAL)

        const val MODAL = 0
        const val OVERLAY = 1

        /**
         * Op 1's bit in IF_SETEVENTS_V2's second word, where op n is bit n - 1 (RSMod's IfEvent, Op1 to Op32). Bits 1 to
         * 10 of the first word are the old op bits, deprecated there: the VM's 241 client sent no autocast menu click
         * while op 1 was set only in the first word.
         */
        const val IF_EVENT_OP1 = 1 shl 0
        const val IF_EVENT_OP2 = 1 shl 1

        /**
         * IF_SETEVENTS_V2 first word: the component may send IF_SCRIPT_TRIGGER (cs2 `if_runscript`). Without it the 241
         * client runs the script's other commands and sends nothing. Found on the VM (2026-10-29) by clicking the
         * master volume slider with each bit set alone: bit 23 sent it, none of bits 0 to 22 or 24 to 30 did.
         */
        const val IF_EVENT_SCRIPT_TRIGGER = 1 shl 23
    }
}

/** A frame change waiting for its tick. */
class FrameMove(val from: GameFrame, val dest: GameFrame, val intermediate: GameFrame?, val dueTick: Long)
