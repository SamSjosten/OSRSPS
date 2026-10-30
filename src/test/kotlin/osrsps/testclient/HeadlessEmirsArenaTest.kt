package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.content.EmirsArena
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Al Kharid step 9 through the headless client: Captain Dalbur's Glider shows the stub, Arena Guard Fronk talks, and the
 * recruitment board says the Arena isn't open. The rl-client script test-runner/examples/al-kharid-arena-glider.json
 * does the same in the real client, with both Guides.
 */
class HeadlessEmirsArenaTest {
    private val types = LocTypes(
        listOf(LocType(EmirsArena.BOARD, "Arena recruitment board", ops = listOf("View", "Spectate", "Rank", null, null), collision = LocCollisionType(width = 2, length = 3))).associateBy { it.id },
    )

    // The type live shows after The Grand Tree; the spawn is the multi 6089, which shows it with varbit 9584 at 0.
    private val dalbur = NpcType(10453, "Captain Dalbur", ops = listOf("Glider", null, "Talk-to", "Glider-to Ta Quir Priw", null), internalName = "pilot_al_kharid_grandtree")
    private val fronk = NpcType(EmirsArena.FRONK, "Arena Guard Fronk", ops = listOf("Talk-to", null, null, null, null), internalName = "pvpa_guard_1")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3284 shr 6, 3212 shr 6); markLoaded(3362 shr 6, 3268 shr 6) }, locTypes = types,
            staticLocs = StaticLocs().apply { add(ZoneLoc(EmirsArena.BOARD, LocShape.CENTREPIECE_STRAIGHT, 1, 3362, 3268, 0)) },
            npcTypes = NpcTypes.of(dalbur, fronk),
            npcSpawns = listOf(NpcSpawn(dalbur, 3284, 3212, source = "test"), NpcSpawn(fronk, 3360, 3265, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    @Test
    fun `Captain Dalbur's Glider shows the stub and his Talk-to opens his menu`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3285, 3212)
                val npc = client.await("Dalbur in view") { client.npcs().firstOrNull { it.id == dalbur.id } }
                client.npcOp(npc.index, EmirsArena.GLIDER_OP)
                client.awaitDialogue("the glider stub") { it is DialogueView.Message && it.text == "[arena_stub.glider_closed]" }
                client.continueDialogue()
                client.npcOp(npc.index, EmirsArena.DALBUR_TALK)
                val menu = client.awaitDialogue("his menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[dalbur.glider]", "[dalbur.ali]", "[dalbur.better]", "[dalbur.nothing]"), menu.options)
            }
        }
    }

    @Test
    fun `Fronk greets the player and the recruitment board says the Arena isn't open`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3361, 3265)
                val npc = client.await("Fronk in view") { client.npcs().firstOrNull { it.id == EmirsArena.FRONK } }
                client.npcOp(npc.index, EmirsArena.TALK_TO)
                val page = client.awaitDialogue("his greeting") { it is DialogueView.Chat && it.text == "[fronk.greeting]" } as DialogueView.Chat
                assertEquals("Arena Guard Fronk", page.name)
                client.continueDialogue()
                client.awaitDialogue("his menu") { it is DialogueView.Menu }
                client.chooseOption(4)
                client.awaitDialogue("the goodbye") { it is DialogueView.Chat && it.text == "[fronk.fine]" }
                client.continueDialogue()
                client.locOp(EmirsArena.BOARD, 3362, 3268, 1)
                client.await("the stub message") { client.messages().firstOrNull { it.text == "[arena_stub.arena_closed]" } }
            }
        }
    }
}
