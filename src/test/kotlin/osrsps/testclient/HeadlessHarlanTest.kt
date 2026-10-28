package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameFrame
import osrsps.game.NpcType
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.Harlan
import osrsps.game.dialogue.Chatbox
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/** Harlan, the melee combat tutor, through the headless client (combat step 7). No dialogue file: lines show their keys. */
class HeadlessHarlanTest {
    private val harlan = NpcSpawn(NpcType(Harlan.ID, "Melee combat tutor", ops = listOf("Talk-to", null, null, null, null)), 3221, 3220, source = "test")
    private val root = listOf("[harlan.melee]", "[harlan.weapon_types]", "[harlan.skillcapes]", "[harlan.training_kit]", "[harlan.goodbye]")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = arrayOfNulls<ObjType>(Harlan.TRAINING_SHIELD + 1)
        types[Harlan.TRAINING_SWORD] = ObjType(Harlan.TRAINING_SWORD, "Training sword")
        types[Harlan.TRAINING_SHIELD] = ObjType(Harlan.TRAINING_SHIELD, "Training shield")
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
    fun `Harlan hands over a training sword and shield once, and the melee talk shows the worn and combat tabs`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                val npc = client.await("Harlan in view") { client.npcs().firstOrNull { it.id == Harlan.ID } }
                client.npcOp(npc.index, Harlan.TALK_TO)
                client.says("Melee combat tutor" to "[harlan.greeting]")
                client.menu(root)
                client.chooseOption(4)
                client.says("dave" to "[harlan.training_kit]")
                assertEquals(DialogueView.Other(Chatbox.OBJECTBOX_DOUBLE), client.awaitDialogue("the items box") { it is DialogueView.Other })
                assertEquals(
                    listOf(InvObj(Harlan.TRAINING_SWORD, 1), InvObj(Harlan.TRAINING_SHIELD, 1)),
                    client.await("the kit in the bag") { client.inventory(BACKPACK).filterNotNull().takeIf { it.size == 2 } },
                )
                client.resumePause(Chatbox.OBJECTBOX_DOUBLE, Chatbox.OBJECTBOX_DOUBLE_PAUSEBUTTON)
                client.says("Melee combat tutor" to "[harlan.there_you_go]", "Melee combat tutor" to "[harlan.kit_anything_else]")
                client.menu(root)
                client.chooseOption(4)
                client.says("dave" to "[harlan.training_kit]", "Melee combat tutor" to "[harlan.has_both]", "Melee combat tutor" to "[harlan.kit_anything_else]")
                assertEquals(2, client.inventory(BACKPACK).count { it != null })

                client.menu(root)
                client.chooseOption(1)
                client.says("dave" to "[harlan.melee]", "Melee combat tutor" to "[harlan.melee_1]", "Melee combat tutor" to "[harlan.melee_2]")
                client.awaitDialogue("melee_3") { it is DialogueView.Chat && it.text == "[harlan.melee_3]" }
                val switches = client.clientScripts().filter { it.id == GameFrame.SIDEBUTTON_SWITCH_SCRIPT }.map { it.args }
                assertEquals(listOf(listOf<Any>(GameFrame.SIDE_TAB_WORN), listOf<Any>(GameFrame.SIDE_TAB_COMBAT)), switches)
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
    }
}
