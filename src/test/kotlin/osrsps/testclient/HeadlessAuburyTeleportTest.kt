package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.content.EssenceMine
import osrsps.game.content.VarrockStores
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Aubury's Teleport through the headless client: his talk's fourth option and his Teleport each send the player to the
 * rune essence mine, and a portal there brings them back to his shop, not beside Sedridor. A player Sedridor sent still
 * comes back beside Sedridor. The rl-client script test-runner/examples/varrock-aubury-teleport.json does the same in
 * Varrock.
 */
class HeadlessAuburyTeleportTest {
    private val aubury = NpcType(AUBURY, "Aubury", ops = listOf("Talk-to", null, "Trade", "Teleport", null), internalName = "aubury_3op")
    private val sedridor = NpcType(EssenceMine.SEDRIDOR, "Archmage Sedridor", ops = listOf("Talk-to", null, "Teleport", null, null))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(3253 shr 6, 3401 shr 6)
            markLoaded(3103 shr 6, 9571 shr 6)
            markLoaded(2880 shr 6, 4800 shr 6)
        }
        World(
            crcs, map,
            locTypes = LocTypes(
                listOf(LocType(EssenceMine.PORTAL, "Portal", listOf("Use", null, null, null, null), LocCollisionType())).associateBy { it.id },
            ),
            npcTypes = NpcTypes.of(aubury, sedridor),
            npcSpawns = listOf(NpcSpawn(aubury, 3253, 3403, wander = 0, source = "test"), NpcSpawn(sedridor, 3103, 9571, wander = 0, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.inMine() = await("the mine") { position?.takeIf { it.x in 2880..2943 && it.z in 4800..4863 && it.level == 0 } }

    /** Through the portal by the first rock; where the player comes out, within [radius] of [x], [z]. */
    private fun HeadlessClient.portalBackTo(x: Int, z: Int, radius: Int) {
        tele(2886, 4850, 0)
        locOp(EssenceMine.PORTAL, 2885, 4850, 1)
        val back = await("out of the mine") { position?.takeIf { it.x !in 2880..2943 || it.z !in 4800..4863 } }
        assertTrue(maxOf(kotlin.math.abs(back.x - x), kotlin.math.abs(back.z - z)) <= radius, "came out at $back")
    }

    private fun HeadlessClient.npcIndex(server: TestServer, id: Int) = server.world.npcs.all.first { it.type.id == id }.index

    @Test
    fun `Aubury's fourth option sends the player to the mine, and the portal brings them back to his shop`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3253, 3401, 0)
                client.npcOp(client.npcIndex(server, AUBURY), VarrockStores.TALK_TO)
                client.awaitDialogue("his greeting") { it is DialogueView.Chat && it.text == "[aubury.greeting]" }
                client.continueDialogue()
                client.awaitDialogue("the menu") { it is DialogueView.Menu && it.options.size == 4 }
                client.chooseOption(4)
                client.awaitDialogue("the ask") { it is DialogueView.Chat && it.text == "[aubury.teleport]" }
                client.continueDialogue()
                client.awaitDialogue("his answer") { it is DialogueView.Chat && it.text == "[aubury.teleport_reply]" }
                client.continueDialogue()
                client.inMine()
                client.portalBackTo(VarrockStores.AUBURY_RETURN.x, VarrockStores.AUBURY_RETURN.z, 2)
            }
        }
    }

    @Test
    fun `Aubury's Teleport sends the player at once, and whoever sent them is who they come back to`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3253, 3401, 0)
                client.npcOp(client.npcIndex(server, AUBURY), VarrockStores.TELEPORT_OP)
                client.inMine()
                client.portalBackTo(VarrockStores.AUBURY_RETURN.x, VarrockStores.AUBURY_RETURN.z, 2)
                // Sent by Sedridor this time: back beside him, not Aubury.
                client.tele(3104, 9571, 0)
                client.npcOp(client.npcIndex(server, EssenceMine.SEDRIDOR), EssenceMine.TELEPORT_OP)
                client.inMine()
                client.portalBackTo(EssenceMine.TO_SEDRIDOR.x, EssenceMine.TO_SEDRIDOR.z, 2)
            }
        }
    }

    private companion object {
        const val AUBURY = 11435
    }
}
