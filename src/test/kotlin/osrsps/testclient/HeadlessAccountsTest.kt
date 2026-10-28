package osrsps.testclient

import java.nio.file.Path
import osrsps.game.Accounts
import osrsps.game.MemorySaveStore
import osrsps.game.World
import osrsps.map.CollisionMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.io.TempDir

/** Name claiming over the real login: a wrong password gets the client's "Invalid username or password". */
class HeadlessAccountsTest {
    @Test
    fun `a claimed name refuses another password and takes its own`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice", "first").use { alice ->
                alice.awaitTicks(1)
                val wrong = assertFailsWith<LoginFailedException> { server.login("Alice", "second") }
                assertEquals(INVALID_USERNAME_OR_PASSWORD, wrong.code)
                // The right password gets past the check, to "already logged in" as alice is still in the world.
                val right = assertFailsWith<LoginFailedException> { server.login("Alice", "first") }
                assertEquals(ALREADY_LOGGED_IN, right.code)
            }
        }
    }

    @Test
    fun `a wrong password doesn't count as already logged in`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("bob", "mine").use { bob ->
                bob.awaitTicks(1)
                // Refused for the password, not with "already logged in", so it can't tell a stranger bob is online.
                val refused = assertFailsWith<LoginFailedException> { server.login("bob", "guess") }
                assertEquals(INVALID_USERNAME_OR_PASSWORD, refused.code)
            }
        }
    }

    @Test
    fun `five wrong passwords lock the name, even against the right one`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("carol", "mine").close()
            repeat(5) {
                val wrong = assertFailsWith<LoginFailedException> { server.login("carol", "guess$it") }
                assertEquals(INVALID_USERNAME_OR_PASSWORD, wrong.code)
            }
            val locked = assertFailsWith<LoginFailedException> { server.login("carol", "mine") }
            assertEquals(TOO_MANY_ATTEMPTS, locked.code)
            // Another name isn't touched.
            server.login("dave", "his").close()
        }
    }

    @Test
    fun `reclaiming a reset name clears its wrong passwords`(@TempDir dir: Path) {
        val store = MemorySaveStore()
        val world = { crcs: IntArray, millis: Long ->
            World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, accounts = Accounts(store, iterations = 1_000), cycleMillis = millis)
        }
        TestServer(dir, world = world).use { server ->
            server.login("erin", "old").close()
            repeat(4) { assertFailsWith<LoginFailedException> { server.login("erin", "guess$it") } }
            // A reset deletes the account file; the next login claims the name again. Each login above has had its
            // answer, so the login thread isn't using the store.
            store.saves.remove("erin")
            // Claimed with the new password, then refused only because erin's last session still lingers.
            assertEquals(ALREADY_LOGGED_IN, assertFailsWith<LoginFailedException> { server.login("erin", "new") }.code)
            val wrong = assertFailsWith<LoginFailedException> { server.login("erin", "typo") }
            assertEquals(INVALID_USERNAME_OR_PASSWORD, wrong.code)
            // Not locked: the new password gets past the check, to "already logged in" as erin still lingers.
            val right = assertFailsWith<LoginFailedException> { server.login("erin", "new") }
            assertEquals(ALREADY_LOGGED_IN, right.code)
        }
    }

    private companion object {
        /** The client's login response for "Invalid username or password". */
        const val INVALID_USERNAME_OR_PASSWORD = 3

        /** "Your account is already logged in." */
        const val ALREADY_LOGGED_IN = 5

        /** "Too many login attempts. Please wait a few minutes before trying again." */
        const val TOO_MANY_ATTEMPTS = 16
    }
}
