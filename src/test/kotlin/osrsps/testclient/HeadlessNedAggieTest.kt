package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.Aggie
import osrsps.game.content.Ned
import osrsps.game.dialogue.Chatbox
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Draynor piece step 4 through the headless client: Ned's rope for 15 coins (and the free one the yak hair talk ends
 * with), and Aggie's Dyes option making red dye, as the registered handlers run them. The rl-client script
 * test-runner/examples/draynor-ned-aggie.json does the same in Draynor.
 */
class HeadlessNedAggieTest {
    private val spawns = listOf(
        NpcSpawn(NpcType(Ned.ID, "Ned", ops = listOf("Talk-to", null, "Trade", null, null)), 3221, 3220, source = "test"),
        NpcSpawn(NpcType(Aggie.AFTER_MET, "Aggie", ops = listOf("Talk-to", null, "Dyes", null, null)), 3223, 3220, source = "test"),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = arrayOfNulls<ObjType>(REDBERRIES + 1)
        types[Ned.COINS] = ObjType(Ned.COINS, "Coins", stackable = true)
        types[Ned.ROPE_ITEM] = ObjType(Ned.ROPE_ITEM, "Rope")
        types[REDBERRIES] = ObjType(REDBERRIES, "Redberries")
        types[RED_DYE] = ObjType(RED_DYE, "Red dye")
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(types), npcSpawns = spawns,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.opNpc(id: Int, op: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, op)
    }

    /** Continues each page that follows, checking it shows key [ns].key. */
    private fun HeadlessClient.pages(ns: String, vararg keys: String) {
        for (key in keys) {
            awaitDialogue("$ns.$key") { it is DialogueView.Chat && it.text == "[$ns.$key]" }
            continueDialogue()
        }
    }

    private fun HeadlessClient.pick(option: Int) {
        awaitDialogue("the menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    private fun HeadlessClient.objbox() {
        awaitDialogue("the item box") { it is DialogueView.Other }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    @Test
    fun `Ned sells a rope for 15 coins and gives one free after the yak hair talk`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${Ned.COINS} 20")
                client.await("the coins") { if (client.count(Ned.COINS) == 20) Unit else null }
                client.opNpc(Ned.ID, Ned.TALK_TO)
                client.pages("ned", "greeting")
                client.pick(2)
                client.pages("ned", "rope", "price", "rope_2", "rope_3", "rope_4", "rope_5")
                client.pick(1)
                client.pages("ned", "sell", "here")
                client.await("the bought rope") { if (client.count(Ned.ROPE_ITEM) == 1) Unit else null }
                assertEquals(5, client.count(Ned.COINS))
                client.pages(
                    "ned", "yak_smell", "yak_what", "yak_isles", "yak_well", "yak_trader", "yak_so", "yak_accuse", "yak_confess",
                    "yak_brand", "yak_accept", "yak_sample", "yak_secret",
                )
                client.objbox()
                assertEquals(2, client.count(Ned.ROPE_ITEM))
                client.pages("ned", "yak_jofridr", "yak_bye")
            }
        }
    }

    @Test
    fun `Aggie's Dyes makes red dye from 3 redberries and 5 coins, and refuses with too few`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${Ned.COINS} 10")
                client.command("item $REDBERRIES 2")
                client.await("the items") { if (client.count(REDBERRIES) == 2) Unit else null }
                client.opNpc(Aggie.AFTER_MET, Aggie.DYES)
                client.pages("aggie", "which")
                client.pick(1)
                client.pages("aggie", "red", "red_cost")
                client.pick(1)
                client.pages("aggie", "make_red")
                client.awaitDialogue("the no-redberries box") { it is DialogueView.Message && it.text == "[aggie.no_redberries]" }
                assertEquals(10, client.count(Ned.COINS))
                client.continueDialogue()
                client.await("the talk to end") { if (client.dialogueView == null) Unit else null }

                client.command("item $REDBERRIES 1")
                client.await("the third redberry") { if (client.count(REDBERRIES) == 3) Unit else null }
                client.opNpc(Aggie.AFTER_MET, Aggie.DYES)
                client.pages("aggie", "which")
                client.pick(1)
                client.pages("aggie", "red", "red_cost")
                client.pick(1)
                client.pages("aggie", "make_red")
                client.objbox()
                assertEquals(1, client.count(RED_DYE))
                assertEquals(0, client.count(REDBERRIES))
                assertEquals(5, client.count(Ned.COINS))
            }
        }
    }

    private companion object {
        const val REDBERRIES = 1951
        const val RED_DYE = 1763
        const val BACKPACK = 93
    }
}
