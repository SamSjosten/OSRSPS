package osrsps.testclient

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.content.Cook
import osrsps.map.CollisionMap

/** NPC and loc examines through the headless client: words from the dialogue file, or the key without them or with `::keys on`. */
class HeadlessExamineTest {
    @Test
    fun `examines come from the dialogue file`(@TempDir dir: Path) {
        val dialogue = dir.resolve("dialogue.txt")
        Files.writeString(dialogue, "examine.npc_${Cook.ID} = Our own words for the Cook.\n")
        TestServer(dir) { crcs, millis ->
            World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, cycleMillis = millis, dialogueFile = dialogue)
        }.use { server ->
            server.login("frank").use { client ->
                client.await("the login rebuild") { client.position }
                client.examineNpc(Cook.ID)
                client.await("the Cook's examine") { client.messages().firstOrNull { it.type == NPC_EXAMINE && it.text == "Our own words for the Cook." } }
                client.examineLoc(SACKS)
                client.await("the sacks' examine") { client.messages().firstOrNull { it.type == OBJ_EXAMINE && it.text == "[examine.loc_$SACKS]" } }
                // As test scripts do: with keys on, the Cook's examine shows its key though the file has words for it.
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.examineNpc(Cook.ID)
                client.await("the Cook's examine key") { client.messages().firstOrNull { it.type == NPC_EXAMINE && it.text == "[examine.npc_${Cook.ID}]" } }
            }
        }
    }

    private companion object {
        const val NPC_EXAMINE = 28
        const val OBJ_EXAMINE = 29

        /** sacks: one of the castle's. */
        const val SACKS = 365
    }
}
