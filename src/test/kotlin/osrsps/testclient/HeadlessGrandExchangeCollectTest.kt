package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ItemPrices
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.ge.GrandExchangeScreen
import osrsps.game.ge.GrandExchangeScreen.Companion.MAIN
import osrsps.map.CollisionMap

/**
 * Grand Exchange step 4 through the headless client: the game messages as an offer fills, finishes and is aborted, an
 * offer's two boxes collected from its details, the collection box's Collect to bank, and the message at login for
 * what waits to be collected. The rl-client script test-runner/examples/ge-offers-collect.json does the same in the
 * real client.
 */
class HeadlessGrandExchangeCollectTest {
    private val objs = ObjTypes.of(
        ObjType(COINS, "Coins", stackable = true, tradeable = true),
        ObjType(FEATHER, "Feather", stackable = true, tradeable = true, internalName = "feather"),
    )
    private val prices = ItemPrices(mapOf(FEATHER to ItemPrices.Entry("Feather", 3, 30_000)))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs, itemPrices = prices, varTypes = VARS,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.awaitMessage(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.held(id: Int) = inventory(INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    @Test
    fun `messages, collecting from the details and the collection box, and the login notice`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("gemarket off")
                client.command("item $COINS 1000")
                client.commandReply("geoffer buy feather 100 5") { it.startsWith("Placed") }
                client.command("gefill 0 40")
                client.awaitMessage("[ge.bought done=40 total=100 item=Feather]")

                // The details of slot 0: Collect-notes on the 40 feathers, which stack, so feathers.
                client.command("ge")
                client.await("the offers screen") { if (MAIN in client.openInterfaces()) Unit else null }
                client.ifButton(MAIN, GrandExchangeScreen.INDEX_FIRST, 1, GrandExchangeScreen.SUB_FACE)
                client.await("slot 1's details") { if (client.varp(VARP) and 0xF == 1) Unit else null }
                client.ifButton(MAIN, GrandExchangeScreen.DETAILS_COLLECT, 1, GrandExchangeScreen.DETAILS_BOX_ITEMS)
                client.await("40 feathers") { if (client.held(FEATHER) == 40) Unit else null }

                // Abort, then Collect on the coins box: 500 paid, 300 back for the 60 not bought.
                client.ifButton(MAIN, GrandExchangeScreen.INDEX_FIRST, 2, GrandExchangeScreen.SUB_FACE)
                client.awaitMessage("[ge.aborted]")
                client.await("the coins to collect") { client.inventory(GrandExchangeScreen.COLLECT_INVS[0]).getOrNull(1) }
                client.ifButton(MAIN, GrandExchangeScreen.DETAILS_COLLECT, 2, GrandExchangeScreen.DETAILS_BOX_COINS)
                client.await("800 coins") { if (client.held(COINS) == 800) Unit else null }
                client.await("slot 0 emptied") { if (client.stockMarketSlot(0) == null) Unit else null }
                client.ifButton(MAIN, GrandExchangeScreen.BACK, 1)

                // A sell that finishes, collected to the bank from the collection box.
                client.commandReply("geoffer sell feather 40 3") { it.startsWith("Placed") }
                client.command("gefill 0")
                client.awaitMessage("[ge.finished_selling total=40 item=Feather]")
                client.command("gecollect")
                client.await("the collection box") { if (GrandExchangeScreen.COLLECTION_BOX in client.openInterfaces()) Unit else null }
                client.ifButton(GrandExchangeScreen.COLLECTION_BOX, GrandExchangeScreen.COLLECTION_TO_BANK, 1)
                client.await("slot 0 emptied again") { if (client.stockMarketSlot(0) == null) Unit else null }
                assertEquals(120L, server.world.playerNamed("alice")!!.bank.count(COINS))
                assertEquals(800, client.held(COINS))

                // Something left to collect at logout.
                client.commandReply("geoffer buy feather 10 5") { it.startsWith("Placed") }
                client.command("gefill 0 5")
                client.awaitMessage("[ge.bought done=5 total=10 item=Feather]")
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.loginAgain("alice").use { client ->
                client.awaitMessage("[ge.collect_waiting]")
                val box = client.await("slot 0's box") { client.inventory(GrandExchangeScreen.COLLECT_INVS[0]).takeIf { it.isNotEmpty() } }
                assertEquals(listOf(InvObj(FEATHER, 5), null, null), box)
                client.command("gemarket on")
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
        const val INV = 93

        /** A made-up varp holding both varbits; the cache's base varps aren't in the tests. */
        const val VARP = 1_000
        val VARS = VarTypes(
            6_000,
            mapOf(
                GrandExchangeScreen.VARBIT_SELECTED_SLOT to VarbitType(VARP, 0, 3),
                GrandExchangeScreen.VARBIT_NEWOFFER_TYPE to VarbitType(VARP, 4, 4),
            ),
        )
    }
}
