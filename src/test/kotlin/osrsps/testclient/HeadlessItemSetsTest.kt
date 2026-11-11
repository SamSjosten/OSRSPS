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

    private val types = obj(SET, "Test armour set") + obj(HELM, "Test helm") + obj(BODY, "Test body") + obj(LEGS, "Test legs") + obj(JUNK, "Junk") +
        obj(PAGE_SET, "Holy book page set") + obj(PAGE_1, "Saradomin page 1") + obj(PAGE_2, "Saradomin page 2")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(*types.toTypedArray()),
            npcTypes = NpcTypes.of(clerk), npcSpawns = listOf(NpcSpawn(clerk, 3224, 3218, source = "test")),
            itemSets = ItemSets(listOf(ItemSet(0, SET, listOf(HELM, BODY, LEGS)), ItemSet(1, PAGE_SET, listOf(PAGE_1, PAGE_2)))),
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
    fun `a god page set comes out noted from unnoted pages`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $PAGE_1 1")
                client.command("item $PAGE_2 1")
                client.await("the pages") { if (client.held(PAGE_2) == 1L) Unit else null }
                client.openSets()
                client.ifButton(ItemSetsScreen.MAIN, ItemSetsScreen.ITEMLIST, 1, 1)
                client.await("the noted page set") { if (client.held(PAGE_SET + 1) == 1L) Unit else null }
                assertEquals(0L, client.held(PAGE_SET) + client.held(PAGE_1) + client.held(PAGE_2))
            }
        }
    }

    @Test
    fun `a set whose cache data would make more of it, or nothing, is neither packed nor unpacked`(@TempDir dir: Path) {
        // Exploit review round 27, R27B-1: the window's data comes from the cache unchecked. Index 0 lists its own noted
        // form, index 1 itself, index 2 nothing; indexes 3 and 4 share one item with different pieces.
        val bad = types + obj(LOOP, "Loop set") + obj(SELF, "Self set") + obj(EMPTY, "Empty set") + obj(TWICE, "Twice set")
        val sets = ItemSets(
            listOf(
                ItemSet(0, LOOP, listOf(LOOP + 1, HELM)), ItemSet(1, SELF, listOf(SELF, BODY)), ItemSet(2, EMPTY, emptyList()),
                ItemSet(3, TWICE, listOf(HELM)), ItemSet(4, TWICE, listOf(HELM, BODY, LEGS)),
            ),
        )
        // The noted form needs the items to see; the rest the data alone shows.
        assertEquals(listOf(true, false, false, false, false), sets.all.map { sets.usable(it) })
        TestServer(dir) { crcs, millis ->
            World(
                crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(*bad.toTypedArray()),
                npcTypes = NpcTypes.of(clerk), npcSpawns = listOf(NpcSpawn(clerk, 3224, 3218, source = "test")),
                itemSets = sets, cycleMillis = millis,
            )
        }.use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                for (id in listOf(LOOP, SELF, EMPTY, TWICE, HELM)) client.command("item $id 1")
                client.await("the sets") { if (client.held(HELM) == 1L) Unit else null }
                client.openSets()
                for (id in listOf(LOOP, SELF, EMPTY, TWICE)) client.ifButton(ItemSetsScreen.SIDE, ItemSetsScreen.SIDE_ITEMS, 1, client.slotOf(id), id)
                // Pack on the set of one helm, whose item another set shares.
                client.ifButton(ItemSetsScreen.MAIN, ItemSetsScreen.ITEMLIST, 1, 3)
                client.awaitTicks(3)
                assertEquals(listOf(1L, 1L, 1L, 1L, 1L), listOf(LOOP, SELF, EMPTY, TWICE, HELM).map { client.held(it) })
                assertEquals(0L, client.held(LOOP + 1) + client.held(BODY) + client.held(LEGS))
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
        const val PAGE_SET = 150
        const val PAGE_1 = 160
        const val PAGE_2 = 170
        const val LOOP = 180
        const val SELF = 190
        const val EMPTY = 200
        const val TWICE = 210
    }
}
