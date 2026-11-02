package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

/**
 * Several players logged in beside each other, each seeing all the others. On Netty 4.2.2's io_uring transport, with
 * assertions on (as tests run), a client's connection was shut down by an AssertionError in IoUringIoHandler.iovArray
 * once several connections flushed in the same pass. The assertion was wrong, not the writes, so a server running
 * without -ea was unaffected; Netty 4.2.13 moved it, and we run a later 4.2.
 */
class HeadlessCrowdTest {
    @Test
    fun `six players beside each other all stay connected and all see one another`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            val clients = mutableListOf<HeadlessClient>()
            var failure: Throwable? = null
            try {
                repeat(PLAYERS) { clients += server.login("crowd${it + 1}") }
                for (client in clients) {
                    client.await("all ${PLAYERS - 1} others") { client.otherPlayers().takeIf { it.size == PLAYERS - 1 } }
                }
                // Everyone keeps getting updates: a few more ticks, and each still sees everyone else.
                clients.forEach { it.awaitTicks(5) }
                for (client in clients) assertEquals(PLAYERS - 1, client.otherPlayers().size)
                assertEquals(PLAYERS, server.world.onlinePlayers.size)
            } catch (e: Throwable) {
                failure = e
            }
            // Closing rethrows anything a client's reader failed on, so close every one and keep the first failure.
            for (client in clients) {
                try {
                    client.close()
                } catch (e: Throwable) {
                    failure?.addSuppressed(e) ?: run { failure = e }
                }
            }
            failure?.let { throw it }
        }
    }

    private companion object {
        const val PLAYERS = 6
    }
}
