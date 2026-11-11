package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ItemPrices
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.GrandExchangeExperts
import osrsps.game.ge.PriceChecker
import osrsps.game.ge.PriceGuides
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * The Grand Exchange's market experts through the headless client: an expert's Prices and the talk's prices option
 * open the price list with the guide's items at their guide prices (an item with no price left out); Brugsen Bursen's
 * Prices opens the guide prices window; Bob Barter's Prices opens his list. Types and options are the 241 cache's; the
 * items are the logs guide's first three, with Achey tree logs given no price.
 */
class HeadlessGrandExchangeExpertsTest {
    private val relobo = NpcType(GrandExchangeExperts.RELOBO, "Relobo Blinyo (logs)", ops = listOf("Talk-to", null, "Prices", null, null), internalName = "ge_expert_logs")
    private val brugsen = NpcType(GrandExchangeExperts.BRUGSEN, "Brugsen Bursen", ops = listOf("Talk-to", null, "Prices", null, null), internalName = "ge_boss")
    private val bob = NpcType(GrandExchangeExperts.BOB_BARTER, "Bob Barter (herbs)", ops = listOf("Talk-to", null, "Prices", "Decant", null), internalName = "ge_expert_herbs")

    private val logs = ObjType(LOGS, "Logs", tradeable = true)
    private val achey = ObjType(ACHEY, "Achey tree logs", tradeable = true)
    private val oak = ObjType(OAK, "Oak logs", tradeable = true)
    private val vial = ObjType(VIAL, "Vial", tradeable = true)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(logs, achey, oak, vial),
            npcTypes = NpcTypes.of(relobo, brugsen, bob),
            npcSpawns = listOf(
                NpcSpawn(relobo, 3224, 3218, wander = 0, source = "test"),
                NpcSpawn(brugsen, 3220, 3218, wander = 0, source = "test"),
                NpcSpawn(bob, 3222, 3221, wander = 0, source = "test"),
            ),
            itemPrices = ItemPrices(
                mapOf(
                    LOGS to ItemPrices.Entry("Logs", 19, 15_000),
                    OAK to ItemPrices.Entry("Oak logs", 29, 15_000),
                    VIAL to ItemPrices.Entry("Vial", 3, 10_000),
                ),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.awaitOpen(id: Int, what: String) = await(what) { if (id in openInterfaces()) Unit else null }

    private fun HeadlessClient.awaitClosed(id: Int) {
        closeModal()
        await("$id closed") { if (id !in openInterfaces()) Unit else null }
    }

    @Test
    fun `Prices and the talk open the expert's list at guide prices`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val npc = client.await("Relobo in view") { client.npcs().firstOrNull { it.id == GrandExchangeExperts.RELOBO } }
                client.npcOp(npc.index, GrandExchangeExperts.PRICES)
                client.awaitOpen(PriceGuides.MAIN, "the logs list")
                val list = client.await("the list's items") { client.inventory(PriceGuides.INV).filterNotNull().takeIf { it.isNotEmpty() } }
                assertEquals(listOf(LOGS to 19, OAK to 29), list.map { it.id to it.count })
                client.awaitClosed(PriceGuides.MAIN)

                client.npcOp(npc.index, GrandExchangeExperts.TALK_TO)
                client.awaitDialogue("the greeting") { it is DialogueView.Chat && it.text == "[relobo.greeting]" }
                client.continueDialogue()
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[relobo.travelled]", "[relobo.prices]", "[relobo.leave]"), menu.options)
                client.chooseOption(2)
                client.awaitDialogue("the player asks") { it is DialogueView.Chat && it.text == "[relobo.prices]" }
                client.continueDialogue()
                client.awaitOpen(PriceGuides.MAIN, "the logs list from the talk")
            }
        }
    }

    @Test
    fun `Brugsen's Prices opens the guide prices window and Bob Barter's his list`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val brugsen = client.await("Brugsen in view") { client.npcs().firstOrNull { it.id == GrandExchangeExperts.BRUGSEN } }
                client.npcOp(brugsen.index, GrandExchangeExperts.PRICES)
                client.awaitOpen(PriceChecker.MAIN, "the guide prices window")
                client.awaitClosed(PriceChecker.MAIN)
                val bob = client.await("Bob in view") { client.npcs().firstOrNull { it.id == GrandExchangeExperts.BOB_BARTER } }
                client.npcOp(bob.index, GrandExchangeExperts.PRICES)
                client.awaitOpen(PriceGuides.MAIN, "the herbs list")
                assertEquals(listOf(VIAL to 3), client.await("the herbs list's items") { client.inventory(PriceGuides.INV).filterNotNull().takeIf { it.isNotEmpty() } }.map { it.id to it.count })
            }
        }
    }

    private companion object {
        const val LOGS = 1511
        const val ACHEY = 2862
        const val OAK = 1521
        const val VIAL = 229
    }
}
