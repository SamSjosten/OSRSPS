package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.World
import osrsps.game.content.Hans
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.PatrolStop
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/** NPCs, loc and NPC clicks, and a whole dialogue, through the headless client and the real network stack. */
class HeadlessInteractionTest {
    private val hans = NpcSpawn(NpcType(Hans.ID, "Hans", ops = listOf("Talk-to", "Age", null, null, null)), 3221, 3220, source = "test")

    /** Has all five ops; each answers with its number, so a test can tell a misencoded op from the right one. */
    private val statue = NpcType(STATUE, "Statue", ops = listOf("One", "Two", "Three", "Four", "Five"))
    private val guard = NpcSpawn(NpcType(GUARD, "Guard"), 3226, 3214, patrol = listOf(PatrolStop(3226, 3214), PatrolStop(3229, 3214)), source = "test")

    private fun server(dir: Path, vararg npcs: NpcSpawn, locs: List<ZoneLoc> = emptyList()) = TestServer(dir) { crcs, millis ->
        val crate = LocType(CRATE, "Crate", ops = listOf("One", "Two", "Three", "Four", "Five"), collision = LocCollisionType(width = 1, length = 1))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, npcSpawns = npcs.toList(),
            locTypes = LocTypes(mapOf(CRATE to crate)), staticLocs = StaticLocs().apply { locs.forEach(::add) },
            cycleMillis = millis,
        ).apply {
            for (op in 1..5) {
                handlers.onNpc(STATUE, op) { player.message("npc op $op") }
                handlers.onLoc(CRATE, op) { player.message("loc op $op") }
            }
        }
    }

    @Test
    fun `sees NPCs with their type and tile, and one walking its patrol`(@TempDir dir: Path) {
        server(dir, hans, guard).use { server ->
            server.login("alice").use { client ->
                client.await("Hans in view") { client.npcs().firstOrNull { it.id == Hans.ID } }
                assertEquals(Coord(3221, 3220), client.npcs().single { it.id == Hans.ID }.coord)
                client.await("the guard at the east end of his patrol") {
                    client.npcs().firstOrNull { it.id == GUARD && it.coord == Coord(3229, 3214) }
                }
            }
        }
    }

    @Test
    fun `talks to Hans through a menu and out the other side`(@TempDir dir: Path) {
        server(dir, hans).use { server ->
            server.login("bob").use { client ->
                val npc = client.await("Hans in view") { client.npcs().firstOrNull { it.id == Hans.ID } }
                client.npcOp(npc.index, Hans.TALK_TO)

                assertEquals(
                    DialogueView.Chat(Chatbox.CHAT_LEFT, "Hans", "Hello. What are you doing here?"),
                    client.awaitDialogue("Hans's greeting"),
                )
                client.continueDialogue()
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(5, menu.options.size)
                assertEquals("I don't know. I'm lost. Where am I?", menu.options[2])
                client.chooseOption(3)

                val asked = client.awaitDialogue("the player asking") as DialogueView.Chat
                assertEquals(Chatbox.CHAT_RIGHT, asked.interfaceId)
                assertEquals("bob", asked.name)
                assertEquals("I don't know. I'm lost. Where am I?", asked.text)
                client.continueDialogue()

                val answer = client.awaitDialogue("Hans's answer") as DialogueView.Chat
                assertEquals("Hans", answer.name)
                assertEquals(true, answer.text.startsWith("You are in Lumbridge Castle"), answer.text)
                client.continueDialogue()
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
                assertNull(client.dialogueView)
            }
        }
    }

    @Test
    fun `each NPC op reaches the server`(@TempDir dir: Path) {
        val spawn = NpcSpawn(statue, 3224, 3218, source = "test")
        server(dir, spawn).use { server ->
            server.login("carol").use { client ->
                val npc = client.await("the statue in view") { client.npcs().firstOrNull { it.id == STATUE } }
                for (op in 1..5) {
                    client.npcOp(npc.index, op)
                    client.awaitMessage("npc op $op answered") { it == "npc op $op" }
                }
            }
        }
    }

    @Test
    fun `each loc op reaches the server`(@TempDir dir: Path) {
        server(dir, locs = listOf(ZoneLoc(CRATE, LocShape.CENTREPIECE_STRAIGHT, 0, 3225, 3218, 0))).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                for (op in 1..5) {
                    client.locOp(CRATE, 3225, 3218, op)
                    client.awaitMessage("loc op $op answered") { it == "loc op $op" }
                }
            }
        }
    }

    private companion object {
        const val STATUE = 9001
        const val GUARD = 9002
        const val CRATE = 9003
    }
}
