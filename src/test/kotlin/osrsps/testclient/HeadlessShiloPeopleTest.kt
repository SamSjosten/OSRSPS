package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.dialogue.Chatbox
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.ShiloPeople
import osrsps.map.LocShape
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Smithing
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Karamja step K4.3b through the headless client: Kaleb Paramaya's sale menu striking through what the player can't pay
 * for and selling a jug of wine and a Paramaya ticket, Seravel's ship ticket, Yohnus's furnace refusing a player with no
 * coins, his coffer taking coins in twenties, and the furnace taking 20 coins a bar from it. test-runner/examples/
 * shilo-village-people.json does the same on the real map.
 */
class HeadlessShiloPeopleTest {
    private fun npc(id: Int, name: String, ops: List<String?>) = NpcType(id, name, ops = ops, internalName = "npc_$id")
    private val talk = listOf("Talk-to", null, null, null, null)
    private val kaleb = npc(ShiloPeople.KALEB, "Kaleb Paramaya", talk)
    private val seravel = npc(ShiloPeople.SERAVEL, "Seravel", talk)
    private val yohnus = npc(ShiloPeople.YOHNUS, "Yohnus", listOf("Talk to", null, "Coffer", null, null))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        val ids = listOf(COINS, ShiloPeople.JUG_OF_WINE, ShiloPeople.BEER, ShiloPeople.PARAMAYA_TICKET, ShiloPeople.SHIP_TICKET, COPPER, TIN, Smithing.BRONZE.obj) +
            Smithing.BARS.flatMap { bar -> bar.ores.map { it.first } + bar.obj }
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        for (id in ids) objs[id] = ObjType(id, "Obj $id", stackable = id == COINS)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(kaleb, seravel, yohnus),
            npcSpawns = listOf(
                NpcSpawn(kaleb, 3223, 3218, wander = 0, source = "test"),
                NpcSpawn(seravel, 3221, 3218, wander = 0, source = "test"),
                NpcSpawn(yohnus, 3222, 3220, wander = 0, source = "test"),
            ),
            locTypes = LocTypes(
                mapOf(Smithing.SHILO_FURNACE to LocType(Smithing.SHILO_FURNACE, "Furnace", listOf(null, "Smelt", "Coffer", null, null), LocCollisionType(width = 1, length = 1))),
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(Smithing.SHILO_FURNACE, LocShape.CENTREPIECE_STRAIGHT, 0, FURNACE_X, FURNACE_Z, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.give(id: Int, count: Int) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.chat(key: String) =
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }.also { continueDialogue() }

    private fun HeadlessClient.menu(): DialogueView.Menu = awaitDialogue("a menu") { it is DialogueView.Menu } as DialogueView.Menu

    private fun HeadlessClient.box(key: String) =
        awaitDialogue(key) { it is DialogueView.Message && it.text == "[$key]" }.also { continueDialogue() }

    private fun HeadlessClient.closed() = await("the dialogue to close") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.npcIndex(id: Int) = await("NPC $id in view") { npcs().firstOrNull { it.id == id } }.index

    @Test
    fun `Kaleb and Seravel sell for coins, and Yohnus's coffer pays the furnace 20 a bar`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }

                // Kaleb, with no coins: every sale struck through, and refused.
                client.npcOp(client.npcIndex(ShiloPeople.KALEB), ShiloPeople.TALK_TO)
                client.chat("kaleb.hello")
                client.menu()
                client.chooseOption(2)
                client.chat("kaleb.for_sale")
                val struck = client.menu()
                assertEquals("<str>[kaleb.buy_wine]</str>", struck.options[0])
                assertEquals("[kaleb.cancel]", struck.options[4])
                client.chooseOption(1)
                client.chat("kaleb.not_enough")
                client.menu()
                client.chooseOption(5)
                client.chat("kaleb.fine")
                client.closed()

                // With 36 coins: a jug of wine and a night's rest.
                client.give(COINS, 36)
                client.npcOp(client.npcIndex(ShiloPeople.KALEB), ShiloPeople.TALK_TO)
                client.chat("kaleb.hello")
                client.menu()
                client.chooseOption(2)
                client.chat("kaleb.for_sale")
                assertEquals("[kaleb.buy_wine]", client.menu().options[0])
                client.chooseOption(1)
                client.box("kaleb.bought_wine")
                assertEquals("<str>[kaleb.buy_pack]</str>", client.menu().options[3], "35 coins left")
                client.chooseOption(3)
                client.box("kaleb.bought_rest")
                client.menu()
                client.chooseOption(5)
                client.chat("kaleb.fine")
                client.closed()
                assertEquals(1L, client.count(ShiloPeople.JUG_OF_WINE))
                assertEquals(1L, client.count(ShiloPeople.PARAMAYA_TICKET))
                assertEquals(0L, client.count(COINS))

                // Seravel: no coins, then a ticket for 25.
                client.npcOp(client.npcIndex(ShiloPeople.SERAVEL), ShiloPeople.TALK_TO)
                client.chat("seravel.hello")
                client.chat("seravel.ticket")
                client.menu()
                client.chooseOption(1)
                client.chat("seravel.yes")
                client.chat("seravel.not_enough")
                client.closed()
                client.give(COINS, 25)
                client.npcOp(client.npcIndex(ShiloPeople.SERAVEL), ShiloPeople.TALK_TO)
                client.chat("seravel.hello")
                client.chat("seravel.ticket")
                client.menu()
                client.chooseOption(1)
                client.chat("seravel.yes")
                client.chat("seravel.great")
                client.closed()
                assertEquals(1L, client.count(ShiloPeople.SHIP_TICKET))
                assertEquals(0L, client.count(COINS))

                // The furnace with no coins: Yohnus's fee line.
                client.give(COPPER, 2)
                client.give(TIN, 2)
                client.locOp(Smithing.SHILO_FURNACE, FURNACE_X, FURNACE_Z, 2)
                client.chat("yohnus.fee")
                client.closed()

                // 50 coins into the coffer: 40 taken, in twenties.
                client.give(COINS, 50)
                client.npcOp(client.npcIndex(ShiloPeople.YOHNUS), ShiloPeople.COFFER)
                assertEquals("[yohnus.coffer_title coins=0]", client.menu().title)
                client.chooseOption(1)
                client.await("the count prompt") { client.clientScripts().lastOrNull { it.id == osrsps.game.script.ScriptContext.MESLAYER_MODE7 } }
                client.resumeCount(50)
                assertEquals("[yohnus.coffer_title coins=40]", client.menu().title)
                client.chooseOption(3)
                client.closed()
                assertEquals(10L, client.count(COINS))

                // Two bronze bars: 40 coins out of the coffer, none from the backpack.
                client.locOp(Smithing.SHILO_FURNACE, FURNACE_X, FURNACE_Z, 2)
                client.await("the Make-X box") { client.clientScripts().firstOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + Smithing.BARS.indexOf(Smithing.BRONZE), 2)
                client.await("two bars", timeoutMillis = 20_000) { if (client.count(Smithing.BRONZE.obj) == 2L) Unit else null }
                assertEquals(10L, client.count(COINS))
                assertEquals(0, server.world.onlinePlayers.single().charges[ShiloPeople.COFFER_CHARGE])

                // The coffer emptied when there's nothing in it.
                client.locOp(Smithing.SHILO_FURNACE, FURNACE_X, FURNACE_Z, ShiloPeople.COFFER)
                client.menu()
                client.chooseOption(2)
                client.box("yohnus.already_empty")
                assertTrue(client.menu().title.contains("coins=0"))
                client.chooseOption(3)
                client.closed()
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val COPPER = 436
        const val TIN = 438
        const val FURNACE_X = 3224
        const val FURNACE_Z = 3220
    }
}
