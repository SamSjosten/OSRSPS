package osrsps.testclient

import java.nio.file.Path
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

    private companion object {
        /** The client's login response for "Invalid username or password". */
        const val INVALID_USERNAME_OR_PASSWORD = 3

        /** "Your account is already logged in." */
        const val ALREADY_LOGGED_IN = 5
    }
}
