package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.Bankers
import osrsps.game.content.GrandExchangeClerks
import osrsps.game.ge.GrandExchangeScreen
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Varrock step 3 through the headless client: a Grand Exchange clerk's Exchange, History and Talk-to, a banker's
 * Collect, and a Grand Exchange booth's Exchange and Collect, each opening its screen. Types and options are the 241
 * cache's; the clerk, banker and booth stand on open ground. The rl-client script
 * test-runner/examples/varrock-grand-exchange.json does the same in the Grand Exchange itself.
 */
class HeadlessGrandExchangeBuildingTest {
    private val clerk = NpcType(CLERK, "Grand Exchange Clerk", ops = listOf("Talk-to", null, "Exchange", "History", "Sets"), internalName = "ge_clerk_1")
    private val banker = NpcType(BANKER, "Banker", ops = listOf("Talk-to", null, "Bank", "Collect", null), internalName = "banker1_west")

    private val booth = LocType(
        EXCHANGE_BOOTH, "Grand Exchange booth", ops = listOf("Exchange", null, "Collect", null, null),
        collision = LocCollisionType(width = 1, length = 1),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(arrayOfNulls(1)),
            npcTypes = NpcTypes.of(clerk, banker),
            npcSpawns = listOf(NpcSpawn(clerk, 3224, 3218, source = "test"), NpcSpawn(banker, 3224, 3222, source = "test")),
            locTypes = LocTypes(mapOf(EXCHANGE_BOOTH to booth)),
            staticLocs = StaticLocs().apply { add(ZoneLoc(EXCHANGE_BOOTH, LocShape.CENTREPIECE_STRAIGHT, 0, 3220, 3220, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.awaitOpen(id: Int, what: String) = await(what) { if (id in openInterfaces()) Unit else null }

    private fun HeadlessClient.awaitClosed(id: Int) {
        closeModal()
        await("$id closed") { if (id !in openInterfaces()) Unit else null }
    }

    @Test
    fun `a clerk's Exchange, History and Talk-to open the offers screen and the history`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the clerk in view") { client.npcs().firstOrNull { it.id == CLERK } }
                client.await("the login rebuild") { client.position }
                client.npcOp(npc.index, GrandExchangeClerks.EXCHANGE)
                client.awaitOpen(GrandExchangeScreen.MAIN, "the offers screen")
                client.awaitClosed(GrandExchangeScreen.MAIN)
                client.npcOp(npc.index, GrandExchangeClerks.HISTORY)
                client.awaitOpen(GrandExchangeScreen.HISTORY, "the trade history")
                client.awaitClosed(GrandExchangeScreen.HISTORY)

                client.npcOp(npc.index, GrandExchangeClerks.TALK_TO)
                val greeting = client.awaitDialogue("the greeting") { it is DialogueView.Chat } as DialogueView.Chat
                assertEquals("[ge_clerk.greeting]", greeting.text)
                client.continueDialogue()
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[ge_clerk.how_to_use]", "[ge_clerk.trade]", "[ge_clerk.sets]", "[ge_clerk.fine]"), menu.options)
                // How to use it: the player asks, seven pages and the question again, then the menu.
                client.chooseOption(1)
                client.awaitDialogue("the player asks") { it is DialogueView.Chat && it.text == "[ge_clerk.how_to_use]" }
                client.continueDialogue()
                for (page in 1..7) {
                    client.awaitDialogue("page $page") { it is DialogueView.Chat && it.text == "[ge_clerk.how_$page]" }
                    client.continueDialogue()
                }
                client.awaitDialogue("asked again") { it is DialogueView.Chat && it.text == "[ge_clerk.again]" }
                client.continueDialogue()
                client.awaitDialogue("the menu again") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.awaitOpen(GrandExchangeScreen.MAIN, "the offers screen from the talk")
            }
        }
    }

    @Test
    fun `a banker's Collect and a booth's Exchange and Collect open their screens`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val npc = client.await("the banker in view") { client.npcs().firstOrNull { it.id == BANKER } }
                client.await("the login rebuild") { client.position }
                client.npcOp(npc.index, Bankers.COLLECT)
                client.awaitOpen(GrandExchangeScreen.COLLECTION_BOX, "the collection box")
                client.awaitClosed(GrandExchangeScreen.COLLECTION_BOX)
                client.locOp(EXCHANGE_BOOTH, 3220, 3220, 1)
                client.awaitOpen(GrandExchangeScreen.MAIN, "the offers screen from the booth")
                client.awaitClosed(GrandExchangeScreen.MAIN)
                client.locOp(EXCHANGE_BOOTH, 3220, 3220, 3)
                client.awaitOpen(GrandExchangeScreen.COLLECTION_BOX, "the collection box from the booth")
            }
        }
    }

    private companion object {
        const val CLERK = 2148
        const val BANKER = 1633

        /** exchange_bank_wall_exchange. */
        const val EXCHANGE_BOOTH = 10061
    }
}
