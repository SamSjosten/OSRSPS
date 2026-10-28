package osrsps.game

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginGuardTest {
    private var now = 0L
    private val guard = LoginGuard(now = { now })

    @Test
    fun `the fifth wrong password in a row locks the name for five minutes`() {
        repeat(4) { guard.wrongPassword("alice") }
        assertFalse(guard.locked("alice"))
        guard.wrongPassword("alice")
        assertTrue(guard.locked("alice"))
        now += LoginGuard.LOCK_MILLIS - 1
        assertTrue(guard.locked("alice"))
        now += 1
        assertFalse(guard.locked("alice"))
        // The count starts again after a lock.
        guard.wrongPassword("alice")
        assertFalse(guard.locked("alice"))
    }

    @Test
    fun `the right password clears the count`() {
        repeat(4) { guard.wrongPassword("alice") }
        guard.rightPassword("alice")
        repeat(4) { guard.wrongPassword("alice") }
        assertFalse(guard.locked("alice"))
    }

    @Test
    fun `each name is counted on its own`() {
        repeat(5) { guard.wrongPassword("alice") }
        assertTrue(guard.locked("alice"))
        assertFalse(guard.locked("bob"))
    }
}
