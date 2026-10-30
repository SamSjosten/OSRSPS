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
import osrsps.game.ge.GrandExchangeScreen.Companion.MAIN
import osrsps.game.ge.GrandExchangeScreen.Companion.SETUP
import osrsps.map.CollisionMap

/**
 * Grand Exchange step 3 through the headless client: a buy set up from an empty slot's Buy box and the chatbox's item
 * search, a sell from Offer on an inventory item, the quantity and price buttons as the client's own scripts change
 * them, the typed amounts, and Confirm. The rl-client script test-runner/examples/ge-offers-setup.json does the same in
 * the real client.
 */
class HeadlessGrandExchangeSetupTest {
    private val objs = ObjTypes.of(
        ObjType(COINS, "Coins", stackable = true, tradeable = true),
        ObjType(FEATHER, "Feather", stackable = true, tradeable = true, internalName = "feather"),
        ObjType(LOGS, "Logs", tradeable = true, internalName = "logs"),
    )
    private val prices = ItemPrices(
        mapOf(FEATHER to ItemPrices.Entry("Feather", 3, 30_000), LOGS to ItemPrices.Entry("Logs", 1_000, 15_000)),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs, itemPrices = prices, varTypes = VARS,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.selectedSlot() = varp(VARP) and 0xF

    private fun HeadlessClient.offerType() = (varp(VARP) shr 4) and 1

    private fun HeadlessClient.quantity() = varp(VARP_QUANTITY)

    private fun HeadlessClient.price() = longVarp(GrandExchangeScreen.VARP_PRICE)

    private fun HeadlessClient.setup(sub: Int, op: Int = 1) = ifButton(MAIN, SETUP, op, sub)

    private fun HeadlessClient.awaitSetup(what: String, quantity: Int, price: Long) =
        await(what) { if (quantity() == quantity && price() == price) Unit else null }

    @Test
    fun `a buy from the item search, a sell from the inventory, the buttons and Confirm`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("gemarket off")
                client.command("item $COINS 1000")
                client.command("item $LOGS 3")
                client.command("ge")
                client.await("the offers screen") { if (MAIN in client.openInterfaces()) Unit else null }

                // Buy on slot 0: the item search opens, and picking feathers sets them up at their guide price.
                client.ifButton(MAIN, GrandExchangeScreen.INDEX_FIRST, 1, GrandExchangeScreen.SUB_BUY)
                val search = client.await("the item search") { client.clientScripts().firstOrNull { it.id == GrandExchangeScreen.MESLAYER_MODE14 } }
                assertEquals(listOf<Any>("[ge.what_to_buy]", 1, -1, 1), search.args)
                assertEquals(1, client.selectedSlot())
                assertEquals(GrandExchangeScreen.TYPE_BUY, client.offerType())
                client.resumeObj(FEATHER)
                client.await("feathers set up") { if (client.varp(GrandExchangeScreen.VARP_SEARCH) == FEATHER) Unit else null }
                client.awaitSetup("1 at the guide price", 1, 3)
                assertEquals(FEATHER, client.varp(GrandExchangeScreen.VARP_LAST_SEARCHED))
                assertEquals(20, client.varp(VARP_TAX))

                // The quantity buttons, as ge_offers_setup_changequantity has them: +10 from 1 makes 10.
                client.setup(GrandExchangeScreen.SETUP_QUANTITY_10)
                client.awaitSetup("+10", 10, 3)
                client.setup(GrandExchangeScreen.SETUP_QUANTITY_100)
                client.awaitSetup("+100", 110, 3)
                client.setup(GrandExchangeScreen.SETUP_QUANTITY_MINUS_1)
                client.awaitSetup("-1", 109, 3)
                client.setup(GrandExchangeScreen.SETUP_QUANTITY_ALL)
                client.awaitSetup("a buy's +1K", 1_109, 3)
                // The price buttons: 5% of 3 rounds to nothing, so a coin.
                client.setup(GrandExchangeScreen.SETUP_PRICE_PLUS_5)
                client.awaitSetup("+5%", 1_109, 4)
                client.setup(GrandExchangeScreen.SETUP_PRICE_GUIDE)
                client.awaitSetup("the guide price", 1_109, 3)
                client.setup(GrandExchangeScreen.SETUP_PRICE_ENTER)
                client.await("the price prompt") { client.clientScripts().lastOrNull()?.takeIf { it.args == listOf<Any>("[ge.set_price]") } }
                client.resumeCount(5)
                client.awaitSetup("a typed price", 1_109, 5)
                // A buy's All: as many as 1,000 coins pay for at 5.
                client.setup(GrandExchangeScreen.SETUP_QUANTITY_ENTER, op = 2)
                client.awaitSetup("all the coins buy", 200, 5)
                client.setup(GrandExchangeScreen.SETUP_QUANTITY_ENTER)
                client.await("the quantity prompt") { client.clientScripts().lastOrNull()?.takeIf { it.args == listOf<Any>("[ge.how_many_buy]") } }
                client.resumeCount(100)
                client.awaitSetup("a typed quantity", 100, 5)

                // Confirm places it in slot 0 and goes back to the index.
                client.ifButton(MAIN, GrandExchangeScreen.CONFIRM, 1)
                val bought = client.await("the buy offer") { client.stockMarketSlot(0) }
                assertEquals(StockMarketSlot(GrandExchangeScreen.STATUS_ACTIVE, FEATHER, 5, 100, 0, 0), bought)
                client.await("the index") { if (client.selectedSlot() == 0 && client.varp(GrandExchangeScreen.VARP_SEARCH) == -1) Unit else null }
                assertEquals(listOf(InvObj(COINS, 500), InvObj(LOGS, 1)), client.inventory(INV).filterNotNull().take(2))

                // Offer on the logs (inventory slot 1): a sell in the first free slot, 1 at the guide price.
                client.ifButton(GrandExchangeScreen.SIDE, GrandExchangeScreen.SIDE_ITEMS, 1, 1)
                client.await("logs set up to sell") { if (client.varp(GrandExchangeScreen.VARP_SEARCH) == LOGS) Unit else null }
                assertEquals(2, client.selectedSlot())
                assertEquals(GrandExchangeScreen.TYPE_SELL, client.offerType())
                client.awaitSetup("1 log at the guide price", 1, 1_000)
                // A sell's All: the 3 logs held. Then -5%, and a custom -10%.
                client.setup(GrandExchangeScreen.SETUP_QUANTITY_ALL)
                client.awaitSetup("all the logs", 3, 1_000)
                client.setup(GrandExchangeScreen.SETUP_PRICE_MINUS_5)
                client.awaitSetup("-5%", 3, 950)
                client.setup(GrandExchangeScreen.SETUP_CUSTOM_MINUS, op = 2)
                client.await("the percent prompt") { client.clientScripts().lastOrNull()?.takeIf { it.args == listOf<Any>("[ge.set_percent]") } }
                client.resumeCount(10)
                client.await("10%") { if (client.varp(VARP_CUSTOM) == 10) Unit else null }
                client.setup(GrandExchangeScreen.SETUP_CUSTOM_MINUS)
                client.awaitSetup("-10%", 3, 855)
                // +100 goes no further than the logs held.
                client.setup(GrandExchangeScreen.SETUP_QUANTITY_100)
                client.awaitSetup("still 3", 3, 855)
                client.ifButton(MAIN, GrandExchangeScreen.CONFIRM, 1)
                val sold = client.await("the sell offer") { client.stockMarketSlot(1) }
                assertEquals(StockMarketSlot(GrandExchangeScreen.STATUS_ACTIVE or GrandExchangeScreen.STATUS_SELLING, LOGS, 855, 3, 0, 0), sold)
                client.await("the logs gone") { if (client.inventory(INV).filterNotNull().none { it.id == LOGS }) Unit else null }

                // A buy worth more than the coins held is refused, and stays set up.
                client.ifButton(MAIN, GrandExchangeScreen.INDEX_FIRST + 2, 1, GrandExchangeScreen.SUB_BUY)
                client.await("the second search") { client.clientScripts().count { it.id == GrandExchangeScreen.MESLAYER_MODE14 }.takeIf { it == 2 } }
                client.resumeObj(FEATHER)
                client.awaitSetup("feathers again", 1, 3)
                client.setup(GrandExchangeScreen.SETUP_QUANTITY_ENTER)
                client.await("the quantity prompt again") {
                    client.clientScripts().count { it.args == listOf<Any>("[ge.how_many_buy]") }.takeIf { it == 2 }
                }
                client.resumeCount(1_000)
                client.awaitSetup("1,000 feathers", 1_000, 3)
                client.ifButton(MAIN, GrandExchangeScreen.CONFIRM, 1)
                client.await("the refusal") { client.messages().firstOrNull { it.text == "[ge.not_enough_coins]" } }
                assertEquals(3, client.selectedSlot())
                assertTrue(client.stockMarketSlot(2) == null)
                client.command("gemarket on")
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val FEATHER = 314
        const val LOGS = 1511
        const val INV = 93

        /** Made-up varps for the varbits; the cache's aren't in the tests. */
        const val VARP = 1_000
        const val VARP_QUANTITY = 1_001
        const val VARP_CUSTOM = 1_002
        const val VARP_TAX = 1_003
        val VARS = VarTypes(
            6_000,
            mapOf(
                GrandExchangeScreen.VARBIT_SELECTED_SLOT to VarbitType(VARP, 0, 3),
                GrandExchangeScreen.VARBIT_NEWOFFER_TYPE to VarbitType(VARP, 4, 4),
                GrandExchangeScreen.VARBIT_QUANTITY to VarbitType(VARP_QUANTITY, 0, 31),
                GrandExchangeScreen.VARBIT_PRICE_CUSTOM to VarbitType(VARP_CUSTOM, 0, 9),
                GrandExchangeScreen.VARBIT_TAX_RATE to VarbitType(VARP_TAX, 0, 9),
            ),
        )
    }
}
