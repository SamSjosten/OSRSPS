package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.collection.CollectionLogCatalog
import osrsps.game.collection.CollectionLogWindow
import osrsps.map.CollisionMap

/**
 * Collection log step 2 through the headless client: `::clog open` opens the log on its first page with what the player
 * has of it and its kill count, a page clicked is sent and drawn, the burger menu opens the overview, its Other button
 * the log's Other tab, and the account summary's row the overview. A burst of search clicks searches at most once a tick.
 */
class HeadlessCollectionLogWindowTest {
    private val objs = ObjTypes.of(ObjType(CLUB, "Hill giant club"), ObjType(PICKAXE, "Dragon pickaxe"), ObjType(MARK, "Mark of grace", stackable = true))

    private val catalog = CollectionLogCatalog(
        listOf(
            CollectionLogCatalog.Tab(0, 471, listOf(CollectionLogCatalog.Page(478, 0, 0, listOf(CLUB), null), CollectionLogCatalog.Page(491, 0, 1, listOf(PICKAXE), null))),
            CollectionLogCatalog.Tab(4, 475, listOf(CollectionLogCatalog.Page(547, 4, 0, listOf(MARK), null))),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            objTypes = objs,
            varTypes = VarTypes(
                6_000,
                mapOf(
                    CollectionLogWindow.VARBIT_LAST_TAB to VarbitType(5_100, 0, 2),
                    CollectionLogWindow.VARBIT_LAST_PAGE to VarbitType(5_100, 3, 10),
                ),
            ),
            collectionLogCatalog = catalog,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.page(): List<Pair<Int, Int>> =
        inventory(CollectionLogWindow.TRANSMIT_INV).filterNotNull().map { it.id to it.count }

    @Test
    fun `the log opens on a page with its items and kill count, and moves between pages, tabs and the overview`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("clog add $PICKAXE 2")
                client.command("varp 1514 31")
                client.command("clog open 0 1")
                client.await("the log") { if (CollectionLogWindow.LOG in client.openInterfaces()) Unit else null }
                val draw = client.await("its draw") { client.clientScripts().lastOrNull { it.id == CollectionLogWindow.DRAW } }
                assertEquals(listOf<Any>(0, 621 shl 16 or 10, 621 shl 16 or 11, 621 shl 16 or 12, 621 shl 16 or 13, 471, 1), draw.args)
                assertEquals(listOf(PICKAXE to 2), client.page())
                assertEquals(31, client.varp(2048), "King Black Dragon kills")

                // The first page: nothing of it yet, and Bryophyta's kill count.
                client.ifButton(CollectionLogWindow.LOG, 11, 1, sub = 0)
                client.await("Bryophyta's page") { client.clientScripts().lastOrNull { it.id == CollectionLogWindow.DRAW_LIST }?.takeIf { it.args[6] == 0 } }
                assertEquals(emptyList(), client.page())
                assertEquals(0, client.varp(2048))

                // The burger menu's Overview, then the overview's Other button.
                client.ifButton(CollectionLogWindow.LOG, CollectionLogWindow.BURGER_FRAME, 1, sub = CollectionLogWindow.VIEW_OVERVIEW)
                client.await("the overview") { if (CollectionLogWindow.OVERVIEW in client.openInterfaces()) Unit else null }
                client.ifButton(CollectionLogWindow.OVERVIEW, CollectionLogWindow.OVERVIEW_TAB_BUTTONS, 1, sub = 4)
                client.await("the log on Other") {
                    client.clientScripts().lastOrNull { it.id == CollectionLogWindow.DRAW }?.takeIf { it.args[5] == 475 && CollectionLogWindow.LOG in client.openInterfaces() }
                }

                // Closed, then the account summary's row, op2, opens the overview.
                client.ifButton(CollectionLogWindow.LOG, CollectionLogWindow.CLOSE, 1)
                client.await("closed") { if (CollectionLogWindow.LOG !in client.openInterfaces()) Unit else null }
                client.ifButton(CollectionLogWindow.SUMMARY, CollectionLogWindow.SUMMARY_CLICK_LAYER, 2, sub = CollectionLogWindow.SUMMARY_ROW)
                client.await("the overview from the summary") { if (CollectionLogWindow.OVERVIEW in client.openInterfaces()) Unit else null }
            }
        }
    }

    @Test
    fun `a burst of search clicks sends the search results at most once a tick, and only for op1`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("clog add $PICKAXE 1")
                client.command("clog add $CLUB 1")
                client.command("clog open 0 1")
                client.await("the log") { if (CollectionLogWindow.LOG in client.openInterfaces()) Unit else null }
                fun transmits() = client.clientScripts().count { it.id == CollectionLogWindow.DELAYED_TRANSMIT }

                // One search: a script for each of the two logged items.
                client.ifButton(CollectionLogWindow.LOG, CollectionLogWindow.SEARCH_TOGGLE, 1)
                client.await("the first search's results") { transmits().takeIf { it == 2 } }
                client.awaitTicks(1)

                // Ten clicks sent at once land in one tick, or two at most if a tick ends between them: never ten
                // searches' worth (exploit review 44, R44B-4).
                repeat(10) { client.ifButton(CollectionLogWindow.LOG, CollectionLogWindow.SEARCHBAR, 1) }
                client.await("the burst's results") { transmits().takeIf { it > 2 } }
                client.awaitTicks(2)
                val afterBurst = transmits()
                assert(afterBurst <= 2 + 2 * 2) { "a burst of ten searches sent ${afterBurst - 2} scripts" }

                // Another op on the search toggle sends nothing.
                client.ifButton(CollectionLogWindow.LOG, CollectionLogWindow.SEARCH_TOGGLE, 2)
                client.awaitTicks(2)
                assertEquals(afterBurst, transmits())

                // A later tick's click searches again, as after typing.
                client.ifButton(CollectionLogWindow.LOG, CollectionLogWindow.SEARCH_TOGGLE, 1)
                client.await("the next search's results") { transmits().takeIf { it == afterBurst + 2 } }
            }
        }
    }

    private companion object {
        const val CLUB = 20756
        const val PICKAXE = 11920
        const val MARK = 11849
    }
}
