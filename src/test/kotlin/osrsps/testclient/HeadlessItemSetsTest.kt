package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.GrandExchangeClerks
import osrsps.game.ge.ItemSet
import osrsps.game.ge.ItemSets
import osrsps.game.ge.ItemSetsScreen
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * The item sets window through the headless client: a clerk's Sets and the talk's Item sets open it; Pack turns one of
 * each piece into the set, unnoted or noted; Unpack turns a set back; a full backpack changes nothing and says so; and
 * Contents on a set the player can't make names its pieces. A made-up three-piece set stands in for the cache's.
 */
class HeadlessItemSetsTest {
    private val clerk = NpcType(CLERK, "Grand Exchange Clerk", ops = listOf("Talk-to", null, "Exchange", "History", "Sets"), internalName = "ge_clerk_1")

    private fun obj(id: Int, name: String) = listOf(
        ObjType(id, name, tradeable = true, certlink = id + 1),
        ObjType(id + 1, name, stackable = true, tradeable = true, certlink = id, certtemplate = 799),
    )

    private val types = obj(SET, "Test armour set") + obj(HELM, "Test helm") + obj(BODY, "Test body") + obj(LEGS, "Test legs") + obj(JUNK, "Junk")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(*types.toTypedArray()),
            npcTypes = NpcTypes.of(clerk), npcSpawns = listOf(NpcSpawn(clerk, 3224, 3218, source = "test")),
            itemSets = ItemSets(listOf(ItemSet(0, SET, listOf(HELM, BODY, LEGS)))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(Inventory.INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.openSets() {
        val npc = await("the clerk in view") { npcs().firstOrNull { it.id == CLERK } }
        npcOp(npc.index, GrandExchangeClerks.SETS)
        await("the item sets window") { if (ItemSetsScreen.MAIN in openInterfaces() && ItemSetsScreen.SIDE in openInterfaces()) Unit else null }
    }

    @Test
    fun `Pack makes a set from unnoted or noted pieces and Unpack takes it apart`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                for (id in listOf(HELM, BODY, LEGS)) client.command("item $id 1")
                for (id in listOf(HELM, BODY, LEGS)) client.command("item ${id + 1} 2")
                client.await("the pieces") { if (client.held(LEGS + 1) == 2L) Unit else null }
                client.openSets()

                // Unnoted pieces first: an unnoted set.
                client.ifButton(ItemSetsScreen.MAIN, ItemSetsScreen.ITEMLIST, 1, 0)
                client.await("the set") { if (client.held(SET) == 1L) Unit else null }
                assertEquals(0L, client.held(HELM) + client.held(BODY) + client.held(LEGS))
                // Then the notes: a noted set.
                client.ifButton(ItemSetsScreen.MAIN, ItemSetsScreen.ITEMLIST, 1, 0)
                client.await("the noted set") { if (client.held(SET + 1) == 1L) Unit else null }
                assertEquals(listOf(1L, 1L, 1L), listOf(HELM + 1, BODY + 1, LEGS + 1).map { client.held(it) })

                // Unpack the unnoted set from the backpack beside the window.
                client.ifButton(ItemSetsScreen.SIDE, ItemSetsScreen.SIDE_ITEMS, 1, client.slotOf(SET), SET)
                client.await("the pieces back") { if (client.held(SET) == 0L && client.held(LEGS) == 1L) Unit else null }
                assertEquals(listOf(1L, 1L), listOf(HELM, BODY).map { client.held(it) })
                // And the noted one into notes.
                client.ifButton(ItemSetsScreen.SIDE, ItemSetsScreen.SIDE_ITEMS, 1, client.slotOf(SET + 1), SET + 1)
                client.await("the notes back") { if (client.held(SET + 1) == 0L && client.held(LEGS + 1) == 2L) Unit else null }
            }
        }
    }

    @Test
    fun `a full backpack keeps the set whole, and Contents names the pieces`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.openSets()
                client.ifButton(ItemSetsScreen.MAIN, ItemSetsScreen.ITEMLIST, 1, 0)
                val contents = client.await("the contents") { client.messages().lastOrNull { it.text.startsWith("[itemsets.contents") } }
                assertTrue("Test helm, Test body, Test legs" in contents.text, contents.text)

                client.command("item $SET 1")
                client.command("item $JUNK 27")
                client.await("a full backpack") { if (client.inventory(Inventory.INV).count { it != null } == 28) Unit else null }
                client.ifButton(ItemSetsScreen.SIDE, ItemSetsScreen.SIDE_ITEMS, 1, client.slotOf(SET), SET)
                client.await("no room") { client.messages().lastOrNull { it.text == "[itemsets.no_room]" } }
                assertEquals(1L, client.held(SET))
                assertEquals(0L, client.held(HELM))
            }
        }
    }

    @Test
    fun `Item sets in a clerk's talk opens the window`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val npc = client.await("the clerk in view") { client.npcs().firstOrNull { it.id == CLERK } }
                client.npcOp(npc.index, GrandExchangeClerks.TALK_TO)
                client.awaitDialogue("the greeting") { it is DialogueView.Chat }
                client.continueDialogue()
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(3)
                client.await("the item sets window") { if (ItemSetsScreen.MAIN in client.openInterfaces()) Unit else null }
            }
        }
    }

    private companion object {
        const val CLERK = 2148
        const val SET = 100
        const val HELM = 110
        const val BODY = 120
        const val LEGS = 130
        const val JUNK = 140
    }
}
