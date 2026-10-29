package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.Player

class SkillClockTest {
    private val alice = Player(1, "alice", 3222, 3218, 0)

    private fun steps(from: Long, ticks: Int, cycle: Int) = (from until from + ticks).map { SkillClock.step(alice, it, cycle) }

    @Test
    fun `a 4-tick action rolls on the fourth tick and every 4 after`() {
        val run = steps(100, 12, 4)
        val due = run.withIndex().filter { it.value == ClockStep.DUE }.map { it.index }
        assertEquals(listOf(3, 7, 11), due)
        assertEquals(ClockStep.STARTED, run[0])
        assertEquals(ClockStep.STARTED, run[4])
    }

    @Test
    fun `a clock still running isn't set again, so a new click keeps the rolls on the same ticks`() {
        SkillClock.step(alice, 100, 4)
        // A click on another tree two ticks in: still the tick-103 roll.
        assertEquals(ClockStep.WAITING, SkillClock.step(alice, 102, 4))
        assertEquals(ClockStep.DUE, SkillClock.step(alice, 103, 4))
    }

    @Test
    fun `a clock that ran out long ago starts a fresh cycle`() {
        SkillClock.step(alice, 100, 4)
        assertEquals(ClockStep.STARTED, SkillClock.step(alice, 500, 4))
        assertEquals(503L, alice.actionDelay)
    }

    @Test
    fun `a one-tick action rolls every tick`() {
        assertEquals(List(5) { ClockStep.DUE }, steps(10, 5, 1))
    }

    @Test
    fun `a pickaxe's cycle of 3 rolls every 3 ticks`() {
        val due = steps(0, 9, 3).withIndex().filter { it.value == ClockStep.DUE }.map { it.index }
        assertEquals(listOf(2, 5, 8), due)
    }
}
