package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TickClockTest {
    private val clock = TickClock()

    private fun cycles(n: Int) = (1..n).map { clock.advance() }

    @Test
    fun `running, every cycle is a tick`() {
        assertEquals(listOf(true, true, true), cycles(3))
    }

    @Test
    fun `paused, no cycle is a tick`() {
        clock.pause()
        assertEquals(listOf(false, false, false), cycles(3))
    }

    @Test
    fun `step runs one tick a cycle, then stays paused`() {
        clock.pause()
        clock.step(2)
        assertEquals(listOf(true, true, false, false), cycles(4))
        assertTrue(clock.paused)
    }

    @Test
    fun `steps add up`() {
        clock.pause()
        clock.step(1)
        clock.step(2)
        assertEquals(3, clock.stepsLeft)
        assertEquals(listOf(true, true, true, false), cycles(4))
    }

    @Test
    fun `pause drops queued steps and says how many`() {
        clock.pause()
        clock.step(5)
        clock.advance()
        assertEquals(4, clock.pause())
        assertEquals(listOf(false), cycles(1))
    }

    @Test
    fun `resume runs every cycle again and drops queued steps`() {
        clock.pause()
        clock.step(5)
        clock.resume()
        assertFalse(clock.paused)
        assertEquals(0, clock.stepsLeft)
        assertEquals(listOf(true, true), cycles(2))
    }

    @Test
    fun `step needs a paused clock and a positive count`() {
        assertFailsWith<IllegalStateException> { clock.step(1) }
        clock.pause()
        assertFailsWith<IllegalArgumentException> { clock.step(0) }
    }
}
