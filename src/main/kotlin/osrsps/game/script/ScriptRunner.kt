package osrsps.game.script

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

private val logger = KotlinLogging.logger {}

/**
 * Runs content scripts: handlers for clicks, the queues, delays and timers. Each tick's script phase calls [process]
 * for every player, in index order, after their input and before movement.
 *
 * A player's script phase, in the order of the 2004Scape project's RS2 world cycle (not checked against OSRS):
 * 1. The delayed script carries on if its tick has come.
 * 2. The queue (normal, strong, soft), then the weak queue.
 * 3. Normal timers, then soft timers.
 *
 * The player is busy while delayed or with a modal open. Busy players don't run normal or weak scripts or normal
 * timers, and their clicks are ignored while delayed.
 *
 * Only touched on the game thread. Scripts run on it straight away and are never resumed anywhere else.
 */
class ScriptRunner(
    private val handlers: Handlers,
    /** Closes every modal the player has open. */
    private val closeModals: (Player) -> Unit,
    private val currentTick: () -> Long,
) {
    val tick: Long get() = currentTick()

    /** Counts script phases, so a queue entry is counted down once a phase even if a script moves the queue about. */
    private var passes = 0L

    fun busy(player: Player): Boolean = player.scripts.delayed || player.modals.isNotEmpty()

    /** Runs [script] for [player] now, until it finishes or delays. */
    fun start(player: Player, script: Script) {
        script.startCoroutine(ScriptContext(player, this), Completion(player))
    }

    /**
     * Runs the handler for [op] now, if there is one. Returns false when there isn't. A click while the player is
     * delayed is dropped; inferred from live, where clicks do nothing during an agility obstacle.
     */
    fun trigger(player: Player, op: Op): Boolean {
        val handler = handlers.find(op) ?: return false
        if (player.scripts.delayed) {
            logger.debug { "Dropping $op for ${player.username}: delayed until tick ${player.scripts.delayedUntil}" }
            return true
        }
        start(player) { handler(op) }
        return true
    }

    /**
     * Queues [script] for [player]. Every script phase counts [delay] down by one, busy or not, and the script runs in
     * the first phase that finds it at 0 or less and the player able to run it. So 0 runs in this tick's script phase
     * when queued before it (from a click, a command or an earlier script in the phase), 1 runs in the next tick's.
     * Inferred from the 2004Scape world cycle; not measured on live.
     */
    fun queue(player: Player, name: String, type: QueueType, delay: Int, script: Script) {
        require(delay >= 0) { "Can't queue $name with delay $delay" }
        val entry = QueuedScript(name, type, delay, script)
        if (type == QueueType.WEAK) player.scripts.weakQueue += entry else player.scripts.queue += entry
        // Removed now rather than at the next script phase; nothing could run them in between.
        if (type == QueueType.STRONG) player.scripts.weakQueue.clear()
    }

    /** Removes every queued script called [name], weak ones included. */
    fun clearQueue(player: Player, name: String) {
        player.scripts.queue.removeAll { it.name == name }
        player.scripts.weakQueue.removeAll { it.name == name }
    }

    /**
     * Starts [player]'s timer called [name], replacing one already set. It fires every [interval] ticks until cleared.
     * Each timer phase counts it down before checking, so set before this tick's timer phase it first fires
     * `interval - 1` ticks later, and set after (from a timer) `interval` ticks later. Inferred from the 2004Scape
     * world cycle; not measured on live.
     */
    fun setTimer(player: Player, name: String, interval: Int, type: TimerType, script: Script) {
        require(interval > 0) { "Timer $name needs an interval of at least 1, not $interval" }
        player.scripts.timers.remove(name)
        player.scripts.timers[name] = ScriptTimer(name, type, interval, script)
    }

    fun clearTimer(player: Player, name: String) {
        player.scripts.timers.remove(name)
    }

    /**
     * The player did something that interrupts what they were doing: walked, or closed an interface. Closes their
     * modals and removes their weak scripts.
     */
    fun interrupt(player: Player) {
        closeModals(player)
        player.scripts.weakQueue.clear()
    }

    /**
     * Drops everything [player] had going: modals, every queue, timers, a delay (whose script never carries on) and
     * clicks held while the clock is paused. For `::scenario` and `::reset`, which start from a clean slate; not
     * something live does.
     */
    fun clearAll(player: Player) {
        closeModals(player)
        player.heldActions.clear()
        val state = player.scripts
        state.queue.clear()
        state.weakQueue.clear()
        state.timers.clear()
        state.suspended = null
        state.delayedUntil = 0
    }

    /** Runs [player]'s part of this tick's script phase. */
    fun process(player: Player) {
        resumeDelayed(player)
        val state = player.scripts
        if (hasStrong(player)) interrupt(player)
        processQueue(player, state.queue)
        processQueue(player, state.weakQueue)
        processTimers(player, TimerType.NORMAL)
        processTimers(player, TimerType.SOFT)
    }

    private fun resumeDelayed(player: Player) {
        val state = player.scripts
        val continuation = state.suspended ?: return
        if (tick < state.delayedUntil) return
        state.suspended = null
        continuation.resume(Unit)
    }

    private fun processQueue(player: Player, queue: MutableList<QueuedScript>) {
        val pass = ++passes
        var runs = 0
        var i = 0
        while (i < queue.size) {
            val entry = queue[i]
            entry.lastPass = pass
            val due = entry.delay-- <= 0
            if (!due || !canRun(player, entry.type)) {
                i++
                continue
            }
            queue.removeAt(i)
            // Strong closing modals before it runs is from 2004Scape; soft doing the same is a guess. Closing a modal
            // removes weak scripts, but a soft script with none open leaves them alone.
            val closesModals = entry.type == QueueType.STRONG || (entry.type == QueueType.SOFT && player.modals.isNotEmpty())
            if (closesModals) interrupt(player)
            start(player, entry.script)
            // Guessed: a script queued during the phase with delay 0 runs in it too. The bound is ours, so a script that
            // keeps queueing itself can't hang the game thread.
            if (++runs >= MAX_RUNS_PER_PASS) {
                logger.warn { "Stopping ${player.username}'s queue after $runs scripts in one tick" }
                return
            }
            // The script may have removed or added entries, so carry on from the first one this phase hasn't seen.
            i = queue.indexOfFirst { it.lastPass != pass }.let { if (it < 0) queue.size else it }
        }
    }

    private fun canRun(player: Player, type: QueueType): Boolean = when (type) {
        QueueType.SOFT -> true
        QueueType.STRONG, QueueType.NORMAL -> !busy(player)
        // However a weak script got queued, it never runs while a strong one waits.
        QueueType.WEAK -> !busy(player) && !hasStrong(player)
    }

    private fun hasStrong(player: Player) = player.scripts.queue.any { it.type == QueueType.STRONG }

    private fun processTimers(player: Player, type: TimerType) {
        val timers = player.scripts.timers
        for (timer in timers.values.filter { it.type == type }) {
            // An earlier timer's script may have cleared or replaced this one.
            if (timers[timer.name] !== timer) continue
            if (--timer.clock > 0) continue
            if (type == TimerType.NORMAL && busy(player)) continue
            timer.clock = timer.interval
            start(player, timer.script)
        }
    }

    internal suspend fun delay(player: Player, ticks: Int) {
        require(ticks > 0) { "Can't delay for $ticks ticks" }
        val state = player.scripts
        // Only a soft script or soft timer can run while the player is delayed, and it can't add a second delay.
        check(state.suspended == null) { "${player.username} is already delayed until tick ${state.delayedUntil}" }
        suspendCoroutine { continuation ->
            state.suspended = continuation
            state.delayedUntil = tick + ticks
        }
    }

    /** Where a script ends up when it finishes or throws. One bad script shouldn't cost everyone the tick. */
    private class Completion(private val player: Player) : Continuation<Unit> {
        override val context: CoroutineContext get() = EmptyCoroutineContext

        override fun resumeWith(result: Result<Unit>) {
            result.onFailure { logger.error(it) { "A script for ${player.username} failed" } }
        }
    }

    companion object {
        /** Scripts one queue may run for one player in one tick. Ours, not live's. */
        const val MAX_RUNS_PER_PASS = 100
    }
}
