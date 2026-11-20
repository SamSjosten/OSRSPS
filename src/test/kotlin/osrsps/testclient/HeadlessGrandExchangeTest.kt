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
import osrsps.game.shop.ShopStock
import osrsps.game.shop.ShopType
import osrsps.game.shop.ShopTypes
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
        ObjType(LOGS, "Logs", tradeable = true, internalName = "logs"),
    )
    private val prices = ItemPrices(mapOf(FEATHER to ItemPrices.Entry("Feather", 3, 30_000), LOGS to ItemPrices.Entry("Logs", 40, 10)))

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

    @Test
    fun `the market buys a sell only up to the item's limit, and still not after a logout`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $LOGS 15")
                client.awaitHeld(LOGS, 15)
                client.commandReply("geoffer sell logs 15 1") { it.startsWith("Placed") }
                // Ten, the logs' limit, sell at the guide price, 40 (under 50, so no tax); the other five wait for the four hours to pass.
                client.await("the sale to fill to the limit") {
                    client.commandReply("geoffer list") { it.startsWith("Slot 0") }.takeIf { "10/15 done for 400" in it }
                }
                client.awaitTicks(5)
                assertEquals(
                    "Slot 0: sell 15 x Logs ($LOGS) at 1, 10/15 done for 400, to collect 0 items and 400 coins",
                    client.commandReply("geoffer list") { it.startsWith("Slot 0") },
                )
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.loginAgain("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.awaitTicks(5)
                assertEquals(
                    "Slot 0: sell 15 x Logs ($LOGS) at 1, 10/15 done for 400, to collect 0 items and 400 coins",
                    client.commandReply("geoffer list") { it.startsWith("Slot 0") },
                )
                client.commandReply("geoffer abort 0") { it.startsWith("Aborted") }
                client.commandReply("geoffer collect 0") { it.startsWith("Collected") }
                client.awaitHeld(LOGS, 5)
                client.awaitHeld(COINS, 400)
            }
        }
    }

    @Test
    fun `an item an NPC sells below its guide price sells to the market for no more than the NPC's price`(@TempDir dir: Path) {
        // Exploit review 44, R44A-3. Beer is the Sheared Ram's and the Blue Moon's at 2 coins (their own registrations,
        // made as the world starts); the logs are this test's shop's at 5. Their guides are 72 and 40. Rope is free from
        // Ned (exploit review 45, R45A-1), so the market never buys it.
        val objs = ObjTypes.of(
            ObjType(COINS, "Coins", stackable = true, tradeable = true),
            ObjType(BEER, "Beer", tradeable = true, internalName = "beer"),
            ObjType(LOGS, "Logs", tradeable = true, internalName = "logs"),
            ObjType(ROPE, "Rope", tradeable = true, internalName = "rope"),
        )
        val prices = ItemPrices(
            mapOf(BEER to ItemPrices.Entry("Beer", 72, 2_000), LOGS to ItemPrices.Entry("Logs", 40, 10), ROPE to ItemPrices.Entry("Rope", 16, 1_000)),
        )
        val shops = ShopTypes(listOf(ShopType("test", "Test shop", 1, 1, false, 1000, 0, 0, listOf(ShopStock(objs[LOGS]!!, 5, 100, fixedPrice = 5)), emptyList())))
        TestServer(dir) { crcs, millis ->
            World(
                crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
                objTypes = objs, itemPrices = prices, shopTypes = shops, cycleMillis = millis,
            )
        }.use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $BEER 10")
                client.awaitHeld(BEER, 10)
                // Under the guide's 72 but over the bartenders' 2: it waits.
                client.commandReply("geoffer sell beer 10 50") { it.startsWith("Placed") }
                client.awaitTicks(5)
                assertEquals(
                    "Slot 0: sell 10 x Beer ($BEER) at 50, 0/10 done for 0, to collect 0 items and 0 coins",
                    client.commandReply("geoffer list") { it.startsWith("Slot 0") },
                )
                client.commandReply("geoffer abort 0") { it.startsWith("Aborted") }
                client.commandReply("geoffer collect 0") { it.startsWith("Collected") }
                client.awaitHeld(BEER, 10)

                // At 2 it fills at 2, not 72: 20 coins for the ten.
                client.commandReply("geoffer sell beer 10 2") { it.startsWith("Placed") }
                client.await("the beer to sell") {
                    client.commandReply("geoffer list") { it.startsWith("Slot 0") }.takeIf { "10/10 done for 20," in it }
                }
                client.commandReply("geoffer collect 0") { it.startsWith("Collected") }
                client.awaitHeld(COINS, 20)

                // The shop's logs at its 5, not the guide's 40.
                client.command("item $LOGS 4")
                client.awaitHeld(LOGS, 4)
                client.commandReply("geoffer sell logs 4 1") { it.startsWith("Placed") }
                client.await("the logs to sell") {
                    client.commandReply("geoffer list") { it.startsWith("Slot 0") }.takeIf { "4/4 done for 20," in it }
                }
                client.commandReply("geoffer collect 0") { it.startsWith("Collected") }
                client.awaitHeld(COINS, 40)

                // Ned's rope, free with the yak hair talk: not even 1 coin fills.
                client.command("item $ROPE 1")
                client.awaitHeld(ROPE, 1)
                client.commandReply("geoffer sell rope 1 1") { it.startsWith("Placed") }
                client.awaitTicks(5)
                assertEquals(
                    "Slot 0: sell 1 x Rope ($ROPE) at 1, 0/1 done for 0, to collect 0 items and 0 coins",
                    client.commandReply("geoffer list") { it.startsWith("Slot 0") },
                )
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
        const val LOGS = 1511
        const val BEER = 1917
        const val ROPE = 954
    }
}
