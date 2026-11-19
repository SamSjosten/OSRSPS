package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.collection.CollectionLogCatalog
import osrsps.game.collection.CollectionLogWindow
import osrsps.game.collection.TheCollector
import osrsps.game.content.Examines
import osrsps.game.dialogue.Chatbox
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Collection log step 3 through the headless client: Get-log on The Collector hands over a log, whose Read and
 * Collections overview open the windows and whose Destroy, answered Yes, takes it; Create-book on a staff makes its
 * ranked log.
 */
class HeadlessCollectorTest {
    private val logOps = listOf(null, "Wield", "Read", "Collections overview", "Destroy")
    private val objs = ObjTypes.of(
        ObjType(TheCollector.LOG, "Collection log", iops = logOps, wearpos1 = WearPos.SHIELD),
        ObjType(TheCollector.BOOKS[0], "Collection log", iops = logOps, wearpos1 = WearPos.SHIELD),
        ObjType(TheCollector.STAVES[0], "Bronze staff of collection", iops = listOf(null, "Wield", "Create-book", null, "Drop"), wearpos1 = WearPos.WEAPON),
    )
    private val collector = NpcType(TheCollector.ID, "The Collector", ops = listOf("Talk-to", null, "Get-log", "Claim-log-rewards", null), internalName = "vm_collector")
    private val catalog = CollectionLogCatalog(listOf(CollectionLogCatalog.Tab(0, 471, listOf(CollectionLogCatalog.Page(478, 0, 0, listOf(22372), null)))))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            objTypes = objs, npcSpawns = listOf(NpcSpawn(collector, 3221, 3220, source = "test")),
            collectionLogCatalog = catalog,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slotOf(id: Int) = inventory(Inventory.INV).indexOfFirst { it?.id == id }

    @Test
    fun `Get-log hands over a log, which opens the windows and can be destroyed, and a staff makes its book`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val npc = client.await("The Collector in view") { client.npcs().firstOrNull { it.id == TheCollector.ID } }
                client.npcOp(npc.index, TheCollector.GET_LOG)
                client.await("the log") { client.slotOf(TheCollector.LOG).takeIf { it >= 0 } }
                client.await("the item box") { if (Chatbox.OBJECTBOX in client.openInterfaces()) Unit else null }
                // Clicking an item closes the talk.

                val slot = client.slotOf(TheCollector.LOG)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, READ_BUTTON_OP, slot, TheCollector.LOG)
                client.await("the log window") { if (CollectionLogWindow.LOG in client.openInterfaces()) Unit else null }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, OVERVIEW_BUTTON_OP, slot, TheCollector.LOG)
                client.await("the overview") { if (CollectionLogWindow.OVERVIEW in client.openInterfaces()) Unit else null }

                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, DESTROY_BUTTON_OP, slot, TheCollector.LOG)
                val init = client.await("the destroy check") { client.clientScripts().lastOrNull { it.id == Chatbox.CONFIRM_DESTROY_INIT } }
                assertEquals(TheCollector.LOG, init.args[0])
                client.resumePause(Chatbox.CONFIRM_DESTROY, Chatbox.CONFIRM_DESTROY_UNIVERSE, 1)
                client.await("the log gone") { if (client.slotOf(TheCollector.LOG) < 0) Unit else null }

                client.command("item ${TheCollector.STAVES[0]}")
                val staff = client.await("the staff") { client.slotOf(TheCollector.STAVES[0]).takeIf { it >= 0 } }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, READ_BUTTON_OP, staff, TheCollector.STAVES[0])
                client.await("the bronze log") { client.slotOf(TheCollector.BOOKS[0]).takeIf { it >= 0 } }
                assertEquals("examine.npc_${TheCollector.ID}", Examines.npc(TheCollector.ID)?.key)
            }
        }
    }

    private companion object {
        /** The inventory's button ops for iop3, iop4 and iop5 (InventoryTab.IOP_BY_BUTTON_OP). */
        const val READ_BUTTON_OP = 4
        const val OVERVIEW_BUTTON_OP = 6
        const val DESTROY_BUTTON_OP = 7
    }
}
