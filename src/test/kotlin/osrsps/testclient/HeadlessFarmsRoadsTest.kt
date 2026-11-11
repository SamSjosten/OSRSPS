package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.content.FarmsRoads
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Misthalin piece 7 step 5 through the headless client: a patch gardener's menu, whose Farming options, Pay and Trade
 * wait for the Farming skill; a Tool Leprechaun's talk, back to his menu after each question, and his store's
 * refusals; Tarquin's canoe talk by Woodcutting level; Rat Burgiss and Seth Groats. The rl-client script
 * test-runner/examples/farms-roads.json walks the real fields from Lumbridge to Varrock and talks to each of them.
 */
class HeadlessFarmsRoadsTest {
    private val gardener = NpcType(FarmsRoads.DREVEN, "Dreven", ops = listOf("Talk-to", null, "Pay", null, "Trade"), internalName = "farming_gardener_bush_1")
    private val leprechaun = NpcType(FarmsRoads.TOOL_LEPRECHAUN, "Tool Leprechaun", ops = listOf("Talk-to", null, "Exchange", "Deposit-all", null), internalName = "farming_tools_leprechaun")
    private val tarquin = NpcType(FarmsRoads.TARQUIN, "Tarquin", ops = listOf("Talk-to", null, "Store-axe", null, null), internalName = "canoeing_tarquin")
    private val rat = NpcType(FarmsRoads.RAT_BURGISS, "Rat Burgiss", ops = listOf("Talk-to", null, null, null, null), internalName = "surok_rat")
    private val seth = NpcType(FarmsRoads.SETH_GROATS, "Seth Groats", ops = listOf("Talk-to", null, null, null, null), internalName = "favour_seth_groats")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5),
            npcTypes = NpcTypes.of(gardener, leprechaun, tarquin, rat, seth),
            npcSpawns = listOf(
                NpcSpawn(gardener, 3224, 3218, source = "test"), NpcSpawn(leprechaun, 3220, 3218, source = "test"),
                NpcSpawn(tarquin, 3222, 3216, source = "test"), NpcSpawn(rat, 3222, 3220, source = "test"),
                NpcSpawn(seth, 3226, 3220, source = "test"),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.talkTo(id: Int, op: Int = FarmsRoads.TALK_TO) {
        await("the login rebuild") { position }
        val npc = await("NPC $id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, op)
    }

    /** Each of [keys] in turn, an NPC's or the player's line, continued past. */
    private fun HeadlessClient.chats(vararg keys: String) {
        for (key in keys) {
            awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
            continueDialogue()
        }
    }

    private fun HeadlessClient.menu(vararg keys: String) {
        val menu = awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
        assertEquals(keys.map { "[$it]" }, menu.options)
    }

    private fun HeadlessClient.awaitClosed() = await("the dialogue to close") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    @Test
    fun `a gardener offers his four options, and the Farming ones, Pay and Trade wait for Farming`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.talkTo(FarmsRoads.DREVEN)
                client.menu("gardener.look_after", "gardener.advice", "gardener.sell", "gardener.another_time")
                client.chooseOption(1)
                client.chats("gardener.look_after")
                client.awaitDialogue("the refusal") { it == DialogueView.Message(LATER) }
                client.continueDialogue()
                client.awaitClosed()

                client.talkTo(FarmsRoads.DREVEN)
                client.menu("gardener.look_after", "gardener.advice", "gardener.sell", "gardener.another_time")
                client.chooseOption(4)
                client.chats("gardener.another_time")
                client.awaitClosed()

                client.talkTo(FarmsRoads.DREVEN, FarmsRoads.PAY)
                client.await("Pay's refusal") { if (client.seen(LATER) == 1) Unit else null }
                client.talkTo(FarmsRoads.DREVEN, FarmsRoads.TRADE)
                client.await("Trade's refusal") { if (client.seen(LATER) == 2) Unit else null }
            }
        }
    }

    @Test
    fun `a Tool Leprechaun answers each question and asks again, and his store waits for Farming`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val options = arrayOf("tool_leprechaun.yes_please", "tool_leprechaun.what_store", "tool_leprechaun.what_do", "tool_leprechaun.no_thanks")
                client.talkTo(FarmsRoads.TOOL_LEPRECHAUN)
                client.chats("tool_leprechaun.greeting")
                client.menu(*options)
                client.chooseOption(2)
                client.chats(
                    "tool_leprechaun.what_store", "tool_leprechaun.store_tools", "tool_leprechaun.store_buckets",
                    "tool_leprechaun.store_notes", "tool_leprechaun.store_ask",
                )
                client.menu(*options)
                client.chooseOption(3)
                client.chats("tool_leprechaun.what_do_ask", "tool_leprechaun.shed", "tool_leprechaun.store_ask")
                client.menu(*options)
                client.chooseOption(4)
                client.chats("tool_leprechaun.no_thanks", "tool_leprechaun.dafter")
                client.awaitClosed()

                client.talkTo(FarmsRoads.TOOL_LEPRECHAUN)
                client.chats("tool_leprechaun.greeting")
                client.menu(*options)
                client.chooseOption(1)
                client.awaitDialogue("the store's refusal") { it == DialogueView.Message(LATER) }
                client.continueDialogue()
                client.awaitClosed()

                client.talkTo(FarmsRoads.TOOL_LEPRECHAUN, FarmsRoads.EXCHANGE)
                client.await("Exchange's refusal") { if (client.seen(LATER) == 1) Unit else null }
                client.talkTo(FarmsRoads.TOOL_LEPRECHAUN, FarmsRoads.DEPOSIT_ALL)
                client.await("Deposit-all's refusal") { if (client.seen(LATER) == 2) Unit else null }
            }
        }
    }

    @Test
    fun `Tarquin talks about canoes by the player's Woodcutting level`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                // A new account is below level 12.
                client.talkTo(FarmsRoads.TARQUIN)
                client.chats("tarquin.hello", "tarquin.old_bean")
                client.menu("tarquin.who", "tarquin.teach")
                client.chooseOption(2)
                client.chats("tarquin.teach_said", "tarquin.no_skill", "tarquin.level_12", "tarquin.quicker")
                client.awaitClosed()

                client.command("setlevel woodcutting 30")
                client.await("Woodcutting 30") { client.stat(WOODCUTTING)?.takeIf { it.level == 30 } }
                client.talkTo(FarmsRoads.TARQUIN)
                client.chats("tarquin.hello", "tarquin.old_bean")
                client.menu("tarquin.who", "tarquin.teach")
                client.chooseOption(2)
                client.chats(
                    "tarquin.teach_said", "tarquin.chop", "tarquin.shape", "tarquin.favourite", "tarquin.length", "tarquin.others",
                    "tarquin.handy", "tarquin.dugout", "tarquin.limited", "tarquin.what_limited", "tarquin.itinerant", "tarquin.clutter",
                )
                client.awaitClosed()

                client.command("setlevel woodcutting 60")
                client.await("Woodcutting 60") { client.stat(WOODCUTTING)?.takeIf { it.level == 60 } }
                client.talkTo(FarmsRoads.TARQUIN)
                client.chats("tarquin.hello", "tarquin.old_bean")
                client.menu("tarquin.who", "tarquin.teach")
                client.chooseOption(1)
                client.chats(
                    "tarquin.who", "tarquin.name", "tarquin.heard", "tarquin.why_heard", "tarquin.pronounced", "tarquin.royal",
                    "tarquin.roald", "tarquin.closely", "tarquin.cousin", "tarquin.doing", "tarquin.canoeing",
                )
                client.menu("tarquin.yes", "tarquin.no")
                client.chooseOption(1)
                client.chats(
                    "tarquin.teach_me", "tarquin.chop", "tarquin.shape", "tarquin.favourite", "tarquin.length", "tarquin.others",
                    "tarquin.waka", "tarquin.whats_waka", "tarquin.one_man", "tarquin.route_back", "tarquin.true", "tarquin.consort",
                )
                client.awaitClosed()
            }
        }
    }

    @Test
    fun `Rat Burgiss is too busy to chat and Seth Groats is off to milk his cows`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.talkTo(FarmsRoads.RAT_BURGISS)
                client.chats("rat_burgiss.hello", "rat_burgiss.busy", "rat_burgiss.sorry", "rat_burgiss.farewell")
                client.awaitClosed()
                client.talkTo(FarmsRoads.SETH_GROATS)
                val page = client.awaitDialogue("Seth's line") { it is DialogueView.Chat && it.text == "[seth_groats.morning]" } as DialogueView.Chat
                assertEquals("Seth Groats", page.name)
                client.continueDialogue()
                client.awaitClosed()
            }
        }
    }

    private companion object {
        const val LATER = "[farms_roads.farming_later]"
        const val WOODCUTTING = 8
    }
}
