package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.ItemPrices
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.map.CollisionMap

/**
 * Grand Exchange step 1 through the headless client: `::geoffer` buys and sells against the guide-price market over
 * real ticks, the coins and items move, and the offers come back after a logout. The rl-client script
 * test-runner/examples/ge-offers-engine.json does the same against 214's price file.
 */
class HeadlessGrandExchangeTest {
    private val objs = ObjTypes.of(
        ObjType(COINS, "Coins", stackable = true, tradeable = true),
        ObjType(FEATHER, "Feather", stackable = true, tradeable = true, internalName = "feather"),
    )
    private val prices = ItemPrices(mapOf(FEATHER to ItemPrices.Entry("Feather", 3, 30_000)))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs, itemPrices = prices, cycleMillis = millis)
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.awaitHeld(id: Int, count: Int) =
        await("the backpack to hold $count of $id") { if (held(id) == count) Unit else null }

    @Test
    fun `buy, collect, sell and abort, and offers kept over a logout`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $COINS 1000")
                client.awaitHeld(COINS, 1000)
                client.commandReply("geoffer buy feather 100 5") { it.startsWith("Placed") }
                client.awaitHeld(COINS, 500)
                // Filled on a later tick at the guide price, 3: 200 back to collect.
                client.await("the buy to fill") {
                    client.commandReply("geoffer list") { it.startsWith("Slot 0") }.takeIf { "100/100 done for 300" in it }
                }
                assertEquals("Collected from slot 0.", client.commandReply("geoffer collect 0") { it.startsWith("Collected") || it.startsWith("Nothing") })
                client.awaitHeld(FEATHER, 100)
                client.awaitHeld(COINS, 700)

                // A sell over the guide price waits; it is still there after a logout.
                client.commandReply("geoffer sell feather 50 10") { it.startsWith("Placed") }
                client.awaitHeld(FEATHER, 50)
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.loginAgain("alice").use { client ->
                client.await("the login rebuild") { client.position }
                assertEquals(
                    "Slot 0: sell 50 x Feather (314) at 10, 0/50 done for 0, to collect 0 items and 0 coins",
                    client.commandReply("geoffer list") { it.startsWith("Slot 0") },
                )
                client.commandReply("geoffer abort 0") { it.startsWith("Aborted") }
                client.commandReply("geoffer collect 0") { it.startsWith("Collected") }
                client.awaitHeld(FEATHER, 100)
                assertEquals("No offers.", client.commandReply("geoffer list") { it == "No offers." || it.startsWith("Slot") })
            }
        }
    }

    /** Logs in once the server has let the last login of [name] go: until then a login is refused as still on. */
    private fun TestServer.loginAgain(name: String): HeadlessClient {
        repeat(50) {
            runCatching { login(name) }.onSuccess { return it }
            Thread.sleep(100)
        }
        return login(name)
    }

    private companion object {
        const val COINS = 995
        const val FEATHER = 314
    }
}
