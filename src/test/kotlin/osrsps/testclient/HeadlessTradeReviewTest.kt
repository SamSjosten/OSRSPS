package osrsps.testclient

import java.nio.file.Path
import osrsps.game.trade.Trades
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * Repros from the adversarial trade review (2026-10-30). Each test states what should hold; one that fails on the code
 * it was written against is a finding.
 */
class HeadlessTradeReviewTest {
    /**
     * A request stands through the asker's dropped connection: the drop declines a trade (World.lingerOver) but leaves
     * Player.tradeRequestTo set, and Trades.request never checks the target's connection. So the other player can still
     * accept it and is put in a trade with a player who isn't there, which the drop's own decline was meant to stop, and
     * which the asker's client never sees if they reconnect (the screens were sent to the dead connection, and opening
     * them took them off closedWhileAway).
     */
    @Test
    fun `a standing request from a player whose connection dropped can't be accepted`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("bob").use { bob ->
                val alice = server.login("alice")
                bob.walk(3223, 3218)
                bob.await("bob's walk") { bob.position?.takeIf { it.x == 3223 } }
                alice.playerOp(bob.index, 4)
                bob.await("the request") { bob.messages().firstOrNull { it.type == Trades.CHATTYPE_TRADEREQ } }
                val p = server.world.playerNamed("alice")!!
                // Her connection drops; she stays in the world for a reconnect.
                alice.close()
                bob.await("the drop noticed") { p.connectionLostTick.takeIf { it >= 0 } }
                assertTrue(server.world.playerNamed("alice") === p, "she lingers")
                // Bob clicks Accept trade on her request (Trade with on her).
                bob.playerOp(p.index, 4)
                bob.awaitTicks(5)
                val b = server.world.playerNamed("bob")!!
                assertNull(b.trade, "bob was put in a trade with a disconnected player")
                assertTrue(Trades.MAIN !in bob.openInterfaces(), "bob's trade screen opened on a disconnected player")
            }
        }
    }
}
