package osrsps.game.script

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.FrameSlot
import osrsps.game.Player

class ScriptRunnerTest {
    private var tick = 0L
    private var modalsClosed = 0
    private val handlers = Handlers()
    private val runner = ScriptRunner(handlers, { it.modals.clear(); modalsClosed++ }) { tick }
    private val player = Player(1, "test", 3222, 3218, 0)

    /** What ran, as "name@tick". */
    private val ran = mutableListOf<String>()

    private fun record(name: String): Script = { ran += "$name@$tick" }

    /** Runs the script phase of the next [n] ticks. */
    private fun ticks(n: Int = 1) = repeat(n) {
        tick++
        runner.process(player)
    }

    private fun openModal() {
        player.modals[FrameSlot.MAINMODAL] = 12
    }

    private fun queue(name: String, type: QueueType = QueueType.NORMAL, delay: Int = 0) =
        runner.queue(player, name, type, delay, record(name))

    @Test
    fun `a queued script runs once its delay has counted down`() {
        tick = 10
        // Queued in tick 10's input phase, before its script phase.
        queue("now", delay = 0)
        queue("next", delay = 1)
        queue("later", delay = 3)
        tick--
        ticks(5)
        assertEquals(listOf("now@10", "next@11", "later@13"), ran)
        assertTrue(player.scripts.queued.isEmpty())
    }

    @Test
    fun `scripts in one queue run in the order they were queued`() {
        queue("a", QueueType.STRONG)
        queue("b", QueueType.NORMAL)
        queue("c", QueueType.SOFT)
        queue("w", QueueType.WEAK)
        ticks()
        // The strong script removed the weak one before anything ran.
        assertEquals(listOf("a@1", "b@1", "c@1"), ran)
    }

    @Test
    fun `normal and weak scripts wait while a modal is open, counting down meanwhile`() {
        openModal()
        queue("normal", delay = 2)
        queue("weak", QueueType.WEAK, delay = 2)
        ticks(4)
        assertEquals(emptyList(), ran)
        player.modals.clear()
        ticks()
        assertEquals(listOf("normal@5", "weak@5"), ran)
    }

    @Test
    fun `a strong script closes modals and removes weak scripts before anything runs, even before it is due`() {
        openModal()
        queue("weak", QueueType.WEAK)
        queue("strong", QueueType.STRONG, delay = 2)
        ticks()
        assertTrue(player.modals.isEmpty())
        assertTrue(player.scripts.queued.none { it.type == QueueType.WEAK })
        ticks(2)
        assertEquals(listOf("strong@3"), ran)
    }

    @Test
    fun `a soft script runs on time with a modal open, and closes it`() {
        openModal()
        queue("soft", QueueType.SOFT, delay = 1)
        ticks(2)
        assertEquals(listOf("soft@2"), ran)
        assertTrue(player.modals.isEmpty())
    }

    @Test
    fun `a soft script with no modal open leaves weak scripts alone`() {
        queue("soft", QueueType.SOFT)
        queue("weak", QueueType.WEAK)
        ticks()
        assertEquals(listOf("soft@1", "weak@1"), ran)
        assertEquals(0, modalsClosed)
    }

    @Test
    fun `a strong script queued during the phase removes weak scripts before they run`() {
        runner.queue(player, "queues strong", QueueType.NORMAL, 0) {
            queue("strong", QueueType.STRONG, delay = 2, script = record("strong"))
        }
        queue("weak", QueueType.WEAK)
        ticks(3)
        assertEquals(listOf("strong@3"), ran)
    }

    @Test
    fun `interrupting removes weak scripts and closes modals but keeps the rest`() {
        openModal()
        queue("weak", QueueType.WEAK)
        queue("normal")
        runner.interrupt(player)
        assertTrue(player.modals.isEmpty())
        ticks()
        assertEquals(listOf("normal@1"), ran)
    }

    @Test
    fun `delay carries the script on the given number of ticks later`() {
        tick = 5
        runner.start(player) {
            ran += "start@$tick"
            delay(3)
            ran += "end@$tick"
        }
        assertTrue(player.scripts.delayed)
        assertEquals(8L, player.scripts.delayedUntil)
        ticks(2)
        assertEquals(listOf("start@5"), ran)
        ticks()
        assertEquals(listOf("start@5", "end@8"), ran)
        assertFalse(player.scripts.delayed)
    }

    @Test
    fun `while delayed only soft scripts run, the rest wait for the delay to end`() {
        runner.start(player) { delay(2) }
        queue("normal")
        queue("strong", QueueType.STRONG)
        queue("soft", QueueType.SOFT)
        queue("weak", QueueType.WEAK)
        ticks(2)
        // The strong script removed the weak one on tick 1.
        assertEquals(listOf("soft@1", "normal@2", "strong@2"), ran)
    }

    @Test
    fun `a delayed script carries on before the queue runs, so a delay ending frees the player that tick`() {
        runner.start(player) {
            delay(1)
            ran += "resumed@$tick"
        }
        queue("normal")
        ticks()
        assertEquals(listOf("resumed@1", "normal@1"), ran)
    }

    @Test
    fun `a script that delays stops later scripts in the same phase`() {
        runner.queue(player, "delays", QueueType.NORMAL, 0) {
            ran += "delays@$tick"
            delay(2)
        }
        queue("after")
        ticks(3)
        assertEquals(listOf("delays@1", "after@3"), ran)
    }

    @Test
    fun `a soft script can't delay a player who is already delayed`() {
        runner.start(player) { delay(5) }
        runner.queue(player, "soft", QueueType.SOFT, 0) {
            ran += "soft@$tick"
            delay(1)
            ran += "never"
        }
        ticks()
        assertEquals(listOf("soft@1"), ran)
        assertEquals(5L, player.scripts.delayedUntil)
    }

    @Test
    fun `a script queued with delay 0 by a script in the queue runs in the same phase`() {
        runner.queue(player, "first", QueueType.NORMAL, 0) {
            ran += "first@$tick"
            queue("second") { ran += "second@$tick" }
            queue("third", delay = 1) { ran += "third@$tick" }
        }
        ticks(2)
        assertEquals(listOf("first@1", "second@1", "third@2"), ran)
    }

    @Test
    fun `a script that keeps queueing itself is stopped at the per-tick bound`() {
        var runs = 0
        lateinit var again: Script
        again = {
            runs++
            queue("again", script = again)
        }
        runner.queue(player, "again", QueueType.NORMAL, 0, again)
        ticks()
        assertEquals(ScriptRunner.MAX_RUNS_PER_PASS, runs)
        ticks()
        assertEquals(2 * ScriptRunner.MAX_RUNS_PER_PASS, runs)
    }

    @Test
    fun `a failing script doesn't stop the ones after it`() {
        runner.queue(player, "fails", QueueType.NORMAL, 0) { error("boom") }
        runner.queue(player, "fails later", QueueType.NORMAL, 0) {
            delay(1)
            error("boom")
        }
        queue("after", delay = 1)
        ticks(2)
        assertEquals(listOf("after@2"), ran)
        assertFalse(player.scripts.delayed)
    }

    @Test
    fun `clearQueue removes scripts by name`() {
        queue("a")
        queue("b", QueueType.WEAK)
        queue("a", QueueType.WEAK)
        runner.clearQueue(player, "a")
        ticks()
        assertEquals(listOf("b@1"), ran)
    }

    @Test
    fun `a timer fires every interval ticks, the first time interval - 1 ticks after it was set`() {
        // Set in tick 1's input phase, before its timer phase.
        tick = 1
        runner.setTimer(player, "t", 3, TimerType.NORMAL, record("t"))
        tick = 0
        ticks(9)
        assertEquals(listOf("t@3", "t@6", "t@9"), ran)
    }

    @Test
    fun `a timer set from a timer first fires a full interval later`() {
        runner.setTimer(player, "setter", 1, TimerType.NORMAL) {
            clearTimer("setter")
            setTimer("t", 2, script = record("t"))
        }
        ticks(5)
        assertEquals(listOf("t@3", "t@5"), ran)
    }

    @Test
    fun `a normal timer waits while busy and fires on the first free tick, a soft one fires on time`() {
        runner.setTimer(player, "normal", 2, TimerType.NORMAL, record("normal"))
        runner.setTimer(player, "soft", 2, TimerType.SOFT, record("soft"))
        openModal()
        ticks(4)
        player.modals.clear()
        ticks()
        assertEquals(listOf("soft@2", "soft@4", "normal@5"), ran)
        ticks(2)
        assertEquals(listOf("soft@2", "soft@4", "normal@5", "soft@6", "normal@7"), ran)
    }

    @Test
    fun `setting a timer again replaces it and restarts its clock`() {
        runner.setTimer(player, "t", 2, TimerType.NORMAL, record("old"))
        ticks()
        runner.setTimer(player, "t", 3, TimerType.NORMAL, record("new"))
        ticks(3)
        assertEquals(listOf("new@4"), ran)
        assertEquals(1, player.scripts.activeTimers.size)
    }

    @Test
    fun `within a tick the delay ends first, then the queue, the weak queue, timers and soft timers`() {
        runner.setTimer(player, "soft timer", 1, TimerType.SOFT, record("soft timer"))
        runner.setTimer(player, "timer", 1, TimerType.NORMAL, record("timer"))
        queue("weak", QueueType.WEAK)
        queue("normal")
        runner.start(player) {
            delay(1)
            ran += "resumed@$tick"
        }
        ticks()
        assertEquals(listOf("resumed@1", "normal@1", "weak@1", "timer@1", "soft timer@1"), ran)
    }

    @Test
    fun `trigger runs the handler for a click now, and drops clicks while delayed`() {
        handlers.onButton(149, 0) { op -> ran += "slot ${op.sub} op ${op.option}@$tick" }
        assertTrue(runner.trigger(player, Op.button(149, 0, 2, sub = 5)))
        assertFalse(runner.trigger(player, Op.button(149, 1, 1)))
        runner.start(player) { delay(1) }
        assertTrue(runner.trigger(player, Op.button(149, 0, 1, sub = 6)))
        assertEquals(listOf("slot 5 op 2@0"), ran)
    }

    @Test
    fun `modals close only for strong and soft scripts`() {
        queue("normal")
        ticks()
        assertEquals(0, modalsClosed)
        queue("strong", QueueType.STRONG)
        ticks()
        // Once for being in the queue and once more as it runs.
        assertEquals(2, modalsClosed)
    }
}
