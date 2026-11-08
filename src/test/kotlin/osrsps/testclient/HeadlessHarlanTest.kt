package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.Harlan
import osrsps.game.dialogue.Chatbox
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/** Captain Harlan through the headless client (Lumbridge 242 step 1). No dialogue file: lines show their keys. */
class HeadlessHarlanTest {
    private val harlan = NpcSpawn(
        NpcType(Harlan.ID, "Captain Harlan", ops = listOf("Talk-to", null, "Claim-weapons", null, null)), 3221, 3220, source = "test",
    )
    private val root = listOf(
        "[captain_harlan.guards]", "[captain_harlan.advice]", "[captain_harlan.weapons]", "[captain_harlan.cape]", "[captain_harlan.leave]",
    )
    private val kinds = listOf(
        "[captain_harlan.ask_melee]", "[captain_harlan.ask_ranged]", "[captain_harlan.ask_magic]", "[captain_harlan.something_else]",
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val ids = listOf(
            Harlan.BRONZE_SHORTSWORD to "Bronze shortsword", Harlan.WOODEN_SHIELD to "Wooden shield", Harlan.SHORTBOW to "Shortbow",
        )
        val types = arrayOfNulls<ObjType>(Harlan.BRONZE_SHORTSWORD + 1)
        for ((id, name) in ids) types[id] = ObjType(id, name)
        types[Harlan.BRONZE_ARROW] = ObjType(Harlan.BRONZE_ARROW, "Bronze arrow", stackable = true)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(types), npcSpawns = listOf(harlan),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.says(vararg expected: Pair<String, String>) {
        for ((name, text) in expected) {
            val page = awaitDialogue("$name saying $text") { it is DialogueView.Chat && it.text == text } as DialogueView.Chat
            assertEquals(name, page.name)
            continueDialogue()
        }
    }

    private fun HeadlessClient.menu(options: List<String>) =
        assertEquals(options, (awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu).options)

    @Test
    fun `Captain Harlan hands over a shortsword and shield, then refuses for 30 minutes, also through Claim-weapons`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                val npc = client.await("Captain Harlan in view") { client.npcs().firstOrNull { it.id == Harlan.ID } }
                client.npcOp(npc.index, Harlan.TALK_TO)
                client.says("Captain Harlan" to "[captain_harlan.greeting]")
                client.menu(root)
                client.chooseOption(3)
                client.says("dave" to "[captain_harlan.weapons]", "Captain Harlan" to "[captain_harlan.offer_all]")
                client.menu(kinds)
                client.chooseOption(1)
                client.says("dave" to "[captain_harlan.ask_melee]")
                assertEquals(DialogueView.Other(Chatbox.OBJECTBOX_DOUBLE), client.awaitDialogue("the items box") { it is DialogueView.Other })
                assertEquals(
                    listOf(InvObj(Harlan.BRONZE_SHORTSWORD, 1), InvObj(Harlan.WOODEN_SHIELD, 1)),
                    client.await("the weapons in the bag") { client.inventory(BACKPACK).filterNotNull().takeIf { it.size == 2 } },
                )
                client.resumePause(Chatbox.OBJECTBOX_DOUBLE, Chatbox.OBJECTBOX_DOUBLE_PAUSEBUTTON)
                client.says("Captain Harlan" to "[captain_harlan.anything_else]")
                client.menu(kinds)
                client.chooseOption(4)
                client.says("dave" to "[captain_harlan.something_else]")
                client.menu(root)
                client.chooseOption(5)
                client.says("dave" to "[captain_harlan.leave]", "Captain Harlan" to "[captain_harlan.leave_reply]")

                client.npcOp(npc.index, Harlan.CLAIM_WEAPONS)
                client.menu(
                    listOf(
                        "[captain_harlan.claim_melee]", "[captain_harlan.claim_ranged]", "[captain_harlan.claim_magic]", "[captain_harlan.cancel]",
                    ),
                )
                client.chooseOption(2)
                client.says("Captain Harlan" to "[captain_harlan.too_soon_all minutes=30]")
                assertEquals(2, client.inventory(BACKPACK).count { it != null })
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
    }
}
