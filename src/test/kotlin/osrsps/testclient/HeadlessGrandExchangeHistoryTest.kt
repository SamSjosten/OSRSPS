package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.EquipmentTab
import osrsps.game.ItemPrices
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.ge.GrandExchangeScreen
import osrsps.game.ge.PriceChecker
import osrsps.map.CollisionMap

/**
 * Grand Exchange step 5 through the headless client: the trade history drawn by its client scripts and its Buy-offer,
 * and the guide prices screen from the equipment tab, with items going in and out of it, their prices, a searched
 * item, the items coming back when it closes, and a save taken while it is open. The rl-client script
 * test-runner/examples/ge-history-prices.json does the same in the real client.
 */
class HeadlessGrandExchangeHistoryTest {
    private val objs = ObjTypes.of(
        ObjType(COINS, "Coins", stackable = true, tradeable = true),
        ObjType(FEATHER, "Feather", stackable = true, tradeable = true, internalName = "feather"),
        ObjType(LOGS, "Logs", tradeable = true, internalName = "logs"),
        ObjType(QUEST_ITEM, "Quest item", internalName = "quest_item"),
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

    private fun HeadlessClient.held(id: Int) = inventory(INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    @Test
    fun `the history and its Buy-offer`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("gemarket off")
                client.command("item $COINS 1000")
                client.command("item $LOGS 2")
                client.commandReply("geoffer buy feather 100 5") { it.startsWith("Placed") }
                client.command("gefill 0")
                client.commandReply("geoffer sell logs 2 1000") { it.startsWith("Placed") }
                client.command("gefill 1")
                client.command("ge")
                client.await("the offers screen") { if (GrandExchangeScreen.MAIN in client.openInterfaces()) Unit else null }

                client.ifButton(GrandExchangeScreen.MAIN, GrandExchangeScreen.HISTORY_BUTTON, 1)
                val finish = client.await("the history drawn") { client.clientScripts().firstOrNull { it.id == GrandExchangeScreen.HISTORY_FINISH_SCRIPT } }
                assertTrue(GrandExchangeScreen.HISTORY in client.openInterfaces())
                val scripts = client.clientScripts()
                val lines = scripts.subList(scripts.indexOfLast { it.id == GrandExchangeScreen.HISTORY_INIT_SCRIPT } + 1, scripts.lastIndexOf(finish))
                // Newest first: the logs sold for 2,000 less 20 tax each, then the feathers bought for 500.
                assertEquals(
                    listOf(
                        listOf<Any>(0, LOGS, 1, 2, 2_000L, 40L),
                        listOf<Any>(1, FEATHER, 0, 100, 500L, 0L),
                    ),
                    lines.map { it.args },
                )

                // Buy-offer on the feathers' row (sub 6, the second row's first child): a buy set up in the first free slot.
                client.ifButton(GrandExchangeScreen.HISTORY, GrandExchangeScreen.HISTORY_LIST, 1, GrandExchangeScreen.HISTORY_ROW_SUBS)
                client.await("the offers screen again") { if (GrandExchangeScreen.MAIN in client.openInterfaces()) Unit else null }
                client.await("feathers set up in slot 2") {
                    if (client.varp(GrandExchangeScreen.VARP_SEARCH) == FEATHER && client.varp(VARP) and 0xF == 3) Unit else null
                }
                assertEquals(3L, client.longVarp(GrandExchangeScreen.VARP_PRICE))
                client.command("gemarket on")
            }
        }
    }

    @Test
    fun `the guide prices screen`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $FEATHER 100")
                client.command("item $LOGS 3")
                client.command("item $QUEST_ITEM 1")
                client.await("the items") { if (client.held(LOGS) == 3) Unit else null }

                client.ifButton(EquipmentTab.WORNITEMS, PriceChecker.WORNITEMS_BUTTON, 1)
                client.await("the guide prices screen") {
                    if (PriceChecker.MAIN in client.openInterfaces() && PriceChecker.SIDE in client.openInterfaces()) Unit else null
                }
                client.await("an empty total") { client.text(PriceChecker.MAIN, PriceChecker.OUTPUT)?.takeIf { it == "[pricechecker.total total=0]" } }

                // Add-10 on the feathers (slot 0), Add on a log (slot 1), Add-All on the logs.
                client.ifButton(PriceChecker.SIDE, PriceChecker.SIDE_ITEMS, 3, 0, obj = FEATHER)
                client.await("10 feathers in") { if (client.held(FEATHER) == 90) Unit else null }
                client.ifButton(PriceChecker.SIDE, PriceChecker.SIDE_ITEMS, 1, 1, obj = LOGS)
                client.ifButton(PriceChecker.SIDE, PriceChecker.SIDE_ITEMS, 4, 2, obj = LOGS)
                client.await("the logs in") { if (client.held(LOGS) == 0) Unit else null }
                assertEquals(listOf(InvObj(FEATHER, 10), InvObj(LOGS, 1), InvObj(LOGS, 1), InvObj(LOGS, 1)), client.inventory(PriceChecker.INV).filterNotNull())
                client.await("the total") { client.text(PriceChecker.MAIN, PriceChecker.OUTPUT)?.takeIf { it == "[pricechecker.total total=3,030]" } }
                val prices = client.clientScripts().last { it.id == PriceChecker.PRICES_SCRIPT }.args
                assertEquals(listOf<Any>(3L, 1_000L, 1_000L, 1_000L) + List(24) { 0L }, prices)

                // Something that can't be traded (the quest item, slot 4) stays out.
                client.ifButton(PriceChecker.SIDE, PriceChecker.SIDE_ITEMS, 1, 4, obj = QUEST_ITEM)
                client.await("the refusal") { client.messages().firstOrNull { it.text == "[pricechecker.not_tradeable]" } }

                // Remove-5 of the feathers.
                client.ifButton(PriceChecker.MAIN, PriceChecker.ITEMS, 2, 0, obj = FEATHER)
                client.await("5 feathers back") { if (client.held(FEATHER) == 95) Unit else null }

                // A save meanwhile counts the items in the screen as in the inventory.
                val saved = server.world.snapshot(server.world.playerNamed("alice")!!).inventory.values
                assertEquals(100L, saved.filter { it.id == FEATHER }.sumOf { it.count.toLong() })
                assertEquals(3, saved.count { it.id == LOGS })

                // Search for item: the guide price of logs.
                client.ifButton(PriceChecker.MAIN, PriceChecker.OTHER, 1)
                client.await("the item search") { client.clientScripts().lastOrNull()?.takeIf { it.id == GrandExchangeScreen.MESLAYER_MODE14 } }
                client.resumeObj(LOGS)
                client.await("the logs' price") { client.text(PriceChecker.MAIN, PriceChecker.OUTPUT)?.takeIf { it == "[pricechecker.other item=Logs price=1,000]" } }

                // Closing gives everything back.
                client.closeModal()
                client.await("the items back") { if (client.held(FEATHER) == 100 && client.held(LOGS) == 3) Unit else null }
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val FEATHER = 314
        const val LOGS = 1511
        const val QUEST_ITEM = 2000
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
