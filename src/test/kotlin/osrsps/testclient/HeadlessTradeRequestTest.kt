package osrsps.testclient

import java.nio.file.Path
import osrsps.game.EquipmentTab
import osrsps.game.World
import osrsps.game.social.FriendLists
import osrsps.game.trade.Trades
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/** Trade requests over the real protocol: asking, accepting, busy, filters, declining and leaving. */
class HeadlessTradeRequestTest {
    private fun HeadlessClient.trading() = openInterfaces().let { Trades.MAIN in it && Trades.SIDE in it }

    @Test
    fun `asking walks up and sends the request, and asking back opens the trade for both`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    bob.walk(3226, 3218)
                    bob.await("bob's walk") { bob.position?.takeIf { it.x == 3226 } }
                    alice.playerOp(bob.index, 4)
                    alice.awaitMessage("sending") { it == "[trade.sending]" }
                    // She walked up beside him (her last step can reach the client after the message).
                    alice.await("alice beside bob") { alice.position?.takeIf { it.x == 3225 } }
                    val request = bob.await("the request") { bob.messages().firstOrNull { it.type == Trades.CHATTYPE_TRADEREQ } }
                    assertEquals("alice", request.name)
                    assertEquals("[trade.request name=alice]", request.text)
                    assertFalse(bob.trading())
                    // Accept trade on the request is Trade with on her.
                    bob.playerOp(alice.index, 4)
                    alice.await("alice's screen") { alice.trading().takeIf { it } }
                    bob.await("bob's screen") { bob.trading().takeIf { it } }
                    // Decline: both screens shut, and she's told.
                    bob.ifButton(Trades.MAIN, Trades.MAIN_DECLINE, 1)
                    alice.awaitMessage("declined") { it == "[trade.declined]" }
                    alice.await("alice's screen shut") { (!alice.trading()).takeIf { it } }
                    bob.await("bob's screen shut") { (!bob.trading()).takeIf { it } }
                    assertTrue(server.world.playerNamed("bob")!!.trade == null && server.world.playerNamed("alice")!!.trade == null)
                }
            }
        }
    }

    @Test
    fun `a player with a screen open is busy, and walking away from the trade declines it`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    server.login("carol").use { carol ->
                        bob.walk(3226, 3218)
                        bob.await("bob's walk") { bob.position?.takeIf { it.x == 3226 } }
                        alice.playerOp(bob.index, 4)
                        bob.await("the request") { bob.messages().firstOrNull { it.type == Trades.CHATTYPE_TRADEREQ } }
                        bob.playerOp(alice.index, 4)
                        bob.await("trading") { bob.trading().takeIf { it } }
                        carol.playerOp(bob.index, 4)
                        carol.awaitMessage("busy") { it == "[trade.busy]" }
                        assertTrue(bob.messages().none { it.type == Trades.CHATTYPE_TRADEREQ && it.name == "carol" })
                        // Walking closes the screen, which declines.
                        alice.walk(3220, 3218)
                        bob.awaitMessage("declined") { it == "[trade.declined]" }
                        bob.await("bob's screen shut") { (!bob.trading()).takeIf { it } }
                    }
                }
            }
        }
    }

    @Test
    fun `a trade filter off, friends only, or an ignore keeps the request from showing`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    bob.walk(3226, 3218)
                    bob.await("bob's walk") { bob.position?.takeIf { it.x == 3226 } }
                    bob.setChatFilters(0, 0, 2)
                    bob.awaitTicks(1)
                    alice.playerOp(bob.index, 4)
                    alice.awaitMessage("sending") { it == "[trade.sending]" }
                    bob.setChatFilters(0, 0, FriendLists.PRIVATE_FRIENDS)
                    bob.awaitTicks(1)
                    alice.playerOp(bob.index, 4)
                    alice.await("sent twice") { alice.messages().filter { it.text == "[trade.sending]" }.takeIf { it.size == 2 } }
                    bob.setChatFilters(0, 0, 0)
                    bob.addIgnore("alice")
                    bob.awaitTicks(2)
                    alice.playerOp(bob.index, 4)
                    alice.await("sent three times") { alice.messages().filter { it.text == "[trade.sending]" }.takeIf { it.size == 3 } }
                    bob.awaitTicks(2)
                    assertTrue(bob.messages().none { it.type == Trades.CHATTYPE_TRADEREQ })
                    // Friends only, and she is his friend: it shows.
                    bob.deleteIgnore("alice")
                    bob.addFriend("alice")
                    bob.setChatFilters(0, 0, FriendLists.PRIVATE_FRIENDS)
                    bob.awaitTicks(2)
                    alice.playerOp(bob.index, 4)
                    bob.await("the request") { bob.messages().firstOrNull { it.type == Trades.CHATTYPE_TRADEREQ } }
                }
            }
        }
    }

    @Test
    fun `a request from a player who then died can't be accepted while they lie dead`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    bob.walk(3226, 3218)
                    bob.await("bob's walk") { bob.position?.takeIf { it.x == 3226 } }
                    alice.playerOp(bob.index, 4)
                    bob.await("the request") { bob.messages().firstOrNull { it.type == Trades.CHATTYPE_TRADEREQ } }
                    alice.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                    alice.await("[combat.dead]") { alice.messages().firstOrNull { it.text == "[combat.dead]" } }
                    assertTrue(server.world.playerNamed("alice")!!.dead)
                    bob.playerOp(alice.index, 4)
                    bob.awaitTicks(3)
                    assertFalse(bob.trading())
                    assertFalse(alice.trading())
                    assertTrue(server.world.playerNamed("bob")!!.trade == null)
                }
            }
        }
    }

    @Test
    fun `another screen's button during the trade declines it, and a player out of view can't be asked`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    bob.walk(3226, 3218)
                    bob.await("bob's walk") { bob.position?.takeIf { it.x == 3226 } }
                    alice.playerOp(bob.index, 4)
                    bob.await("the request") { bob.messages().firstOrNull { it.type == Trades.CHATTYPE_TRADEREQ } }
                    bob.playerOp(alice.index, 4)
                    alice.await("trading") { alice.trading().takeIf { it } }
                    // Equipment Stats (a queued or forged click: the trade covers the tab) would replace the trade's screens.
                    alice.ifButton(EquipmentTab.WORNITEMS, EquipmentTab.WORNITEMS_VIEW_STATS, 1)
                    bob.awaitMessage("declined") { it == "[trade.declined]" }
                    bob.await("bob's screen shut") { (!bob.trading()).takeIf { it } }
                    assertTrue(server.world.playerNamed("alice")!!.trade == null)
                    // Bob goes out of view; a forged Trade with on his index goes nowhere.
                    bob.command("tele 3250 3218 0")
                    bob.await("bob away") { bob.position?.takeIf { it.x == 3250 } }
                    val before = alice.position!!
                    alice.playerOp(bob.index, 4)
                    alice.awaitTicks(3)
                    assertEquals(before, alice.position)
                    assertTrue(alice.messages().count { it.text == "[trade.sending]" } == 1)
                }
            }
        }
    }

    @Test
    fun `logging out mid-trade declines it for the partner`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    bob.walk(3226, 3218)
                    bob.await("bob's walk") { bob.position?.takeIf { it.x == 3226 } }
                    alice.playerOp(bob.index, 4)
                    bob.await("the request") { bob.messages().firstOrNull { it.type == Trades.CHATTYPE_TRADEREQ } }
                    bob.playerOp(alice.index, 4)
                    alice.await("trading") { alice.trading().takeIf { it } }
                    bob.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                    alice.awaitMessage("declined") { it == "[trade.declined]" }
                    alice.await("alice's screen shut") { (!alice.trading()).takeIf { it } }
                }
            }
        }
    }
}
