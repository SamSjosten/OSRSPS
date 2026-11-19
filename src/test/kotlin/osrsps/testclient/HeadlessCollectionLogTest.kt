package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.collection.CollectionLog
import osrsps.game.collection.CollectionLogCatalog
import osrsps.game.collection.CollectionLogLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopTypes
import osrsps.game.skill.Agility
import osrsps.map.CollisionMap

/**
 * Collection log step 1 through the headless client: a graceful hood bought from Grace (the real shops.json entry) goes
 * in the log, with its counts, its page's completed varbit, the latest items, the chat message and the pop-up.
 */
class HeadlessCollectionLogTest {
    private val mark = ObjType(Agility.MARK_OF_GRACE, "Mark of Grace", stackable = true, cost = 1)
    private val hood = ObjType(HOOD, "Graceful hood", cost = 35)
    // Grace's whole stock, or the shop file's entry is left out.
    private val objs = ObjTypes.of(
        mark, hood, ObjType(11_854, "Graceful top", cost = 55), ObjType(11_856, "Graceful legs", cost = 60),
        ObjType(11_858, "Graceful gloves", cost = 30), ObjType(11_860, "Graceful boots", cost = 40),
        ObjType(11_852, "Graceful cape", cost = 40), ObjType(12_641, "Amylase pack", cost = 10),
    )
    private val grace = NpcType(GRACE, "Grace", ops = listOf("Talk-to", null, "Trade", "View Laps", "Toggle Counter"), internalName = "rooftops_grace")
    private val shops = ShopTypes.parse(
        ShopTypes::class.java.getResource(ShopTypes.RESOURCE)!!.readText(), NpcTypes.of(grace), objs,
    ).let { all -> ShopTypes(listOf(all["graces_graceful_clothing"]!!)) }

    // The Rooftop Agility page (struct 547) with two of its items, its completed varbit's id made up.
    private val catalog = CollectionLogCatalog(
        listOf(CollectionLogCatalog.Tab(4, 475, listOf(CollectionLogCatalog.Page(547, 4, 0, listOf(Agility.MARK_OF_GRACE, HOOD), completedVarbit = COMPLETED)))),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            objTypes = objs, npcSpawns = listOf(NpcSpawn(grace, 3221, 3220, source = "test")), shopTypes = shops,
            varTypes = VarTypes(
                6_000,
                mapOf(
                    ShopScreen.VARBIT_QUANTITY to VarbitType(5_100, 0, 2),
                    CollectionLog.VARBIT_NEW_ITEM_OPTION to VarbitType(5_101, 0, 1),
                    COMPLETED to VarbitType(5_102, 0, 0),
                    CollectionLog.VARBIT_CURRENT_RUNEDAY to VarbitType(5_103, 0, 15),
                ),
            ),
            collectionLogCatalog = catalog,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    @Test
    fun `a graceful hood bought from Grace is logged and announced in chat and by pop-up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.await("the log's size") { client.varp(CollectionLog.VARP_COUNT_MAX).takeIf { it == 2 } }
                assertEquals(-1, client.varp(CollectionLog.VARP_LATEST), "no latest item")
                client.command("varbit ${CollectionLog.VARBIT_NEW_ITEM_OPTION} 3")
                client.command("item ${Agility.MARK_OF_GRACE} 100")
                client.await("the marks") { if (client.held(Agility.MARK_OF_GRACE) == 100) Unit else null }
                val npc = client.await("Grace in view") { client.npcs().firstOrNull { it.id == GRACE } }
                client.npcOp(npc.index, TRADE)
                client.await("the shop") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.ifButton(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 2, 1, HOOD)

                val words = DialogueText.NONE
                client.awaitMessage("the new item message") { it == words[CollectionLogLines.NEW_ITEM, "item" to "Graceful hood"] }
                client.await("the count") { client.varp(CollectionLog.VARP_COUNT).takeIf { it == 1 } }
                assertEquals(1, client.varp(4621), "collection_count_other")
                assertEquals(HOOD, client.varp(CollectionLog.VARP_LATEST))
                assertEquals(0, client.varp(5_102), "half the page")
                client.await("the pop-up") { if (CollectionLog.NOTIFICATION_DISPLAY in client.openInterfaces()) Unit else null }
                val popup = client.await("its script") { client.clientScripts().lastOrNull { it.id == CollectionLog.NOTIFICATION_DISPLAY_INIT } }
                assertEquals(listOf<Any>(words[CollectionLogLines.POPUP_TITLE], words[CollectionLogLines.POPUP_TEXT, "item" to "Graceful hood"], -1), popup.args)

                // A second hood is counted, not announced again.
                val said = client.messages().size
                client.ifButton(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 2, 1, HOOD)
                client.await("the second hood") { if (client.held(HOOD) == 2) Unit else null }
                client.awaitTicks(2)
                assertEquals(0, client.messages().drop(said).count { it.text == words[CollectionLogLines.NEW_ITEM, "item" to "Graceful hood"] })
                assertEquals(1, client.varp(CollectionLog.VARP_COUNT))

                // The marks bought it with aren't a mark of grace logged; ::clog add logs one, which fills the page.
                client.command("clog add ${Agility.MARK_OF_GRACE}")
                client.await("the page done") { client.varp(5_102).takeIf { it == 1 } }
                assertEquals(Agility.MARK_OF_GRACE, client.varp(CollectionLog.VARP_LATEST))
                assertEquals(HOOD, client.varp(CollectionLog.VARP_LATEST + 2))
            }
        }
    }

    private companion object {
        const val GRACE = 5919
        const val TRADE = 3
        const val HOOD = 11_850
        const val COMPLETED = 11_990
    }
}
