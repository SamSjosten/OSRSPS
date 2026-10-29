package osrsps.testclient

import java.nio.file.Path
import osrsps.game.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/** What /status.json reads from the world, over the real login: whether it is up, who is in, and if it takes logins. */
class HeadlessWorldStatusTest {
    @Test
    fun `the world counts who is in and is up until it stops`(@TempDir dir: Path) {
        val server = TestServer(dir)
        val world = server.world
        try {
            assertTrue(world.isUp())
            assertTrue(world.acceptingLogins)
            assertEquals(0, world.playerCount)
            server.login("bob").use { bob ->
                server.login("alice").use { alice ->
                    // The count is taken as a cycle ends: two tick ends after her login, its cycle is over.
                    alice.awaitTicks(2)
                    assertEquals(2, world.playerCount)
                    alice.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                    alice.await("the logout") { alice.packets().firstOrNull { it.prot.name == "LOGOUT" } }
                }
                // She leaves the world in the cycle after her logout; three tick ends on, that cycle is over too.
                bob.awaitTicks(3)
                assertEquals(1, world.playerCount)
            }
            // A game thread that ends no cycle for more than 10 s counts as down, though nothing else has stopped. A second
            // over, so a cycle ending while this line runs can't bring it back under.
            assertFalse(world.isUp(System.currentTimeMillis() + World.STALL_MILLIS + 1_000))
            assertTrue(world.isUp())
        } finally {
            server.close()
        }
        assertFalse(world.isUp())
        assertFalse(world.acceptingLogins)
    }
}
