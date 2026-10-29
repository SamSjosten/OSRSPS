package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginGuardTest {
    private var now = 0L
    private val guard = LoginGuard(now = { now })

    @Test
    fun `the fifth wrong password in a row locks the name for five minutes`() {
        repeat(4) { guard.wrongPassword("alice", HOME) }
        assertFalse(guard.locked("alice", HOME))
        guard.wrongPassword("alice", HOME)
        assertTrue(guard.locked("alice", HOME))
        now += LoginGuard.LOCK_MILLIS - 1
        assertTrue(guard.locked("alice", HOME))
        now += 1
        assertFalse(guard.locked("alice", HOME))
        // The count starts again after a lock.
        guard.wrongPassword("alice", HOME)
        assertFalse(guard.locked("alice", HOME))
    }

    @Test
    fun `the right password clears the count`() {
        repeat(4) { guard.wrongPassword("alice", HOME) }
        guard.rightPassword("alice", HOME)
        repeat(4) { guard.wrongPassword("alice", HOME) }
        assertFalse(guard.locked("alice", HOME))
    }

    @Test
    fun `each name is counted on its own`() {
        repeat(5) { guard.wrongPassword("alice", HOME) }
        assertTrue(guard.locked("alice", HOME))
        assertFalse(guard.locked("bob", HOME))
    }

    @Test
    fun `a stranger's guesses lock the name only from the stranger's address`() {
        repeat(5) { guard.wrongPassword("alice", STRANGER) }
        assertTrue(guard.locked("alice", STRANGER))
        assertFalse(guard.locked("alice", HOME))
        // Alice logging in doesn't unlock the stranger.
        guard.rightPassword("alice", HOME)
        assertTrue(guard.locked("alice", STRANGER))
    }

    @Test
    fun `forgetting a name clears its counts from every address`() {
        repeat(5) { guard.wrongPassword("alice", STRANGER) }
        repeat(3) { guard.wrongPassword("alice", HOME) }
        guard.forget("alice")
        assertFalse(guard.locked("alice", STRANGER))
        assertEquals(0, guard.size)
    }

    @Test
    fun `old counts are dropped once there are many`() {
        repeat(4096) { guard.wrongPassword("alice", "10.0.${it / 256}.${it % 256}") }
        now += LoginGuard.LOCK_MILLIS
        guard.wrongPassword("alice", STRANGER)
        assertEquals(1, guard.size)
    }

    private companion object {
        const val HOME = "198.51.100.9"
        const val STRANGER = "203.0.113.7"
    }
}
