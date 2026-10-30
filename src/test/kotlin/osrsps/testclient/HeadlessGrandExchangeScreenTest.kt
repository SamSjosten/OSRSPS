package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ItemPrices
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.ge.GrandExchangeScreen
import osrsps.map.CollisionMap

/**
 * Grand Exchange step 2 through the headless client: the slots reach the client as UPDATE_STOCKMARKET_SLOT whether the
 * screen is open or not, `::ge` opens it, View offer and Back move varbit ge_selectedslot, Abort offer finishes the
 * offer and fills its collection box, and the slots come again at the next login. The rl-client script
 * test-runner/examples/ge-offers-screen.json does the same in the real client.
 */
class HeadlessGrandExchangeScreenTest {
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

    private fun HeadlessClient.selectedSlot() = varp(VARP) and 0xF

    private fun HeadlessClient.newOfferType() = (varp(VARP) shr 4) and 1

    @Test
    fun `slots, view, back, abort and the next login`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("gemarket off")
                client.command("item $COINS 1000")
                client.commandReply("geoffer buy feather 100 5") { it.startsWith("Placed") }
                // Sent without the screen open, active and buying.
                val placed = client.await("slot 0") { client.stockMarketSlot(0) }
                assertEquals(StockMarketSlot(GrandExchangeScreen.STATUS_ACTIVE, FEATHER, 5, 100, 0, 0), placed)

                client.command("ge")
                client.await("the offers screen and its events") {
                    if (GrandExchangeScreen.MAIN in client.openInterfaces() && GrandExchangeScreen.SIDE in client.openInterfaces() &&
                        client.opEnabled(GrandExchangeScreen.MAIN, GrandExchangeScreen.INDEX_FIRST, GrandExchangeScreen.SUB_FACE, 2)
                    ) {
                        Unit
                    } else {
                        null
                    }
                }
                // View offer, then Back.
                client.ifButton(GrandExchangeScreen.MAIN, GrandExchangeScreen.INDEX_FIRST, 1, GrandExchangeScreen.SUB_FACE)
                client.await("slot 1 selected") { if (client.selectedSlot() == 1) Unit else null }
                client.ifButton(GrandExchangeScreen.MAIN, GrandExchangeScreen.BACK, 1)
                client.await("the index") { if (client.selectedSlot() == 0) Unit else null }

                // Abort offer: finished, the 500 coins back in the collection box's coin slot.
                client.ifButton(GrandExchangeScreen.MAIN, GrandExchangeScreen.INDEX_FIRST, 2, GrandExchangeScreen.SUB_FACE)
                val aborted = client.await("the abort") { client.stockMarketSlot(0)?.takeIf { it.status == GrandExchangeScreen.STATUS_FINISHED } }
                assertEquals(0, aborted.completed)
                client.await("the collection box") {
                    if (client.inventory(GrandExchangeScreen.COLLECT_INVS[0]) == listOf(null, InvObj(COINS, 500))) Unit else null
                }

                // An empty slot's Sell box: the setup panel's vars.
                client.ifButton(GrandExchangeScreen.MAIN, GrandExchangeScreen.INDEX_FIRST + 1, 1, GrandExchangeScreen.SUB_SELL)
                client.await("a sell offer set up in slot 2") { if (client.selectedSlot() == 2 && client.newOfferType() == 1) Unit else null }

                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.loginAgain("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val again = client.await("slot 0 at login") { client.stockMarketSlot(0) }
                assertEquals(GrandExchangeScreen.STATUS_FINISHED, again.status)
                assertTrue(client.stockMarketSlot(1) == null)
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

        /** A made-up varp holding both varbits; the cache's base varps aren't in the tests. */
        const val VARP = 1_000
        val VARS = VarTypes(
            2_000,
            mapOf(
                GrandExchangeScreen.VARBIT_SELECTED_SLOT to VarbitType(VARP, 0, 3),
                GrandExchangeScreen.VARBIT_NEWOFFER_TYPE to VarbitType(VARP, 4, 4),
            ),
        )
    }
}
