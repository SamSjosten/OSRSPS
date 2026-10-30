package osrsps.game.script

import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import osrsps.game.Player

/**
 * A piece of content code: what a click, a queue entry or a timer runs. It runs straight away and can [ScriptContext.delay]
 * to carry on in a later tick, like RuneScript's `p_delay`.
 */
typealias Script = suspend ScriptContext.() -> Unit

/**
 * The four kinds of queued script. Normal, strong and soft share one queue in the order they were queued; weak scripts
 * have their own, run after it.
 *
 * The rules come from the 2004Scape project's write-up of the RS2 world cycle (Jagex's engine of 2004, which OSRS
 * grew from), not from OSRS sources, and the soft rules are guessed. Task 21 checks them against live recordings.
 */
enum class QueueType {
    /**
     * Waits while the player is busy (delayed, or a modal open). Interrupting the player removes it: walking, closing an
     * interface, or a strong script in the queue.
     */
    WEAK,

    /** Waits while the player is busy. */
    NORMAL,

    /**
     * Removes the player's weak scripts when queued, and every script phase it is in the queue closes their modals and
     * removes weak scripts before anything runs; no weak script runs while it waits. It runs once it is due and the
     * player isn't delayed.
     */
    STRONG,

    /** Guessed: runs as soon as it is due, even while the player is busy, closing their modals first. */
    SOFT,
}

enum class TimerType {
    /** Doesn't fire while the player is busy; once due it fires on the first tick they aren't. */
    NORMAL,

    /** Fires on time whatever the player is doing. Stat regeneration is one. */
    SOFT,
}

/** What a running script sees: whose it is, the tick, and the scripting calls. */
class ScriptContext internal constructor(val player: Player, private val runner: ScriptRunner) {
    /** The current game tick. */
    val tick: Long get() = runner.tick

    /**
     * Stops the script here and carries on [ticks] ticks later, at the start of that tick's script phase. The player is
     * delayed until then: busy for queues and timers, and their clicks are ignored. RuneScript's `p_delay(n)` is
     * `delay(n + 1)`.
     */
    suspend fun delay(ticks: Int = 1) = runner.delay(player, ticks)

    /**
     * Stops the script until the player clicks component [componentId] of [interfaceId] with a resume_pausebutton (a
     * dialogue's continue, a chat menu's options) on a slot in [subs], and returns that slot. The player isn't delayed
     * meanwhile; anything that closes the dialogue drops the script. See [ScriptRunner.resumePauseButton].
     */
    suspend fun pauseButton(interfaceId: Int, componentId: Int, subs: IntRange = -1..-1): Int =
        runner.pauseButton(player, Op.packButton(interfaceId, componentId), subs)

    /**
     * As [pauseButton], for a click on any of [componentIds] of [interfaceId]: returns the component clicked and the slot,
     * as a Make-X box's item buttons send them (the slot is the quantity).
     */
    suspend fun pauseButtons(interfaceId: Int, componentIds: Collection<Int>, subs: IntRange): Pair<Int, Int> {
        val (packed, sub) = runner.pauseButtons(player, componentIds.map { Op.packButton(interfaceId, it) }.toSet(), subs)
        return (packed and 0xFFFF) to sub
    }

    /**
     * Asks the player for an amount, with [prompt] over the chatbox's input line (client script meslayer_mode7, as RSMod
     * sends it), and stops the script until they enter one, which it returns: never negative. The player isn't delayed
     * meanwhile; closing their interfaces (walking, Esc) closes the prompt and drops the script. See
     * [ScriptRunner.resumeCount].
     */
    suspend fun countDialog(prompt: String = ENTER_AMOUNT): Int {
        player.session.queue(RunClientScript(MESLAYER_MODE7, listOf(prompt)))
        return runner.countDialog(player).coerceAtLeast(0)
    }

    /**
     * Asks the player for a line of text, with [prompt] over the chatbox's input line (client script meslayer_mode9,
     * layer mode 9), and stops the script until they enter it, which it returns. Closing their interfaces closes the
     * prompt and drops the script, as [countDialog]'s.
     */
    suspend fun stringDialog(prompt: String): String {
        player.session.queue(RunClientScript(MESLAYER_MODE9, listOf(prompt, 0)))
        return runner.stringDialog(player)
    }

    /**
     * Stops the script until the client sends a resume_p_countdialog, without opening a prompt: for an interface whose
     * own buttons send one, as popupoverlay's two buttons send 0 and 1. Dropped like [countDialog]'s.
     */
    suspend fun waitForCount(): Int = runner.countDialog(player)

    /** Queues [script] for this player; see [ScriptRunner.queue]. */
    fun queue(name: String, type: QueueType = QueueType.NORMAL, delay: Int = 0, script: Script) =
        runner.queue(player, name, type, delay, script)

    /** Starts or replaces this player's timer called [name]; see [ScriptRunner.setTimer]. */
    fun setTimer(name: String, interval: Int, type: TimerType = TimerType.NORMAL, script: Script) =
        runner.setTimer(player, name, interval, type, script)

    fun clearTimer(name: String) = runner.clearTimer(player, name)

    companion object {
        /** meslayer_mode7 in the 241 cache: a prompt for a number. */
        const val MESLAYER_MODE7 = 108

        /** meslayer_mode9: a prompt for a line of text, answered with resume_p_stringdialog. */
        const val MESLAYER_MODE9 = 110
        const val MESLAYER_STRING = 9

        /** meslayer_close, which closes the prompt of the layer mode it is given (7 for a number) or any (0). */
        const val MESLAYER_CLOSE = 101
        const val MESLAYER_COUNT = 7

        const val ENTER_AMOUNT = "Enter amount:"
    }
}
