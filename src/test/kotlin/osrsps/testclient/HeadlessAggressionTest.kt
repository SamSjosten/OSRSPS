package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.Aggression
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Aggression through two headless players, with the real NPC combat file: a giant spider goes for the nearer of
 * them, and once that one has had their ten minutes, for the other. The rl-client script
 * test-runner/examples/aggro.json does the first part in the real client.
 */
class HeadlessAggressionTest {
    private val spider = NpcType(3017, "Giant spider", ops = listOf(null, "Attack", null, null, null), combatLevel = 2, internalName = "giantspider1", stats = NpcStats(hitpoints = 5))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(spider)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            // Far enough from the login tile that it leaves everyone alone until they are put beside it.
            npcSpawns = listOf(NpcSpawn(spider, 3245, 3218, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    /** The spider's line in `::npcs`. */
    private fun spiderLine(client: HeadlessClient): String {
        val from = client.messages().size
        client.command("npcs 30")
        return client.await("the spider's line") { client.messages().drop(from).firstOrNull { it.text.startsWith("#") && " Giant spider " in it.text } }.text
    }

    @Test
    fun `a giant spider goes for the nearer of two players, and for the other once the nearer is tolerated`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.await("the login rebuild") { alice.position }
                    bob.await("the login rebuild") { bob.position }
                    // Paused, so both stand by it before it looks.
                    alice.commandReply("pause") { it.startsWith("Paused after tick") }
                    alice.commandReply("tele 3248 3218") { it.startsWith("Teleported") }
                    bob.commandReply("tele 3246 3219") { it.startsWith("Teleported") }
                    alice.stepTicks(1)
                    assertTrue(spiderLine(alice).endsWith("; fighting bob"), "the nearer")
                    // Bob leaves, and after ten minutes by it he is tolerated anyway.
                    bob.commandReply("tele 3222 3218") { it.startsWith("Teleported") }
                    alice.stepTicks(1)
                    assertTrue(!spiderLine(alice).contains("fighting"), "it lets bob go once he is out of its range")
                    bob.commandReply("tele 3246 3219") { it.startsWith("Teleported") }
                    alice.stepTicks(1)
                    assertTrue(spiderLine(alice).endsWith("; fighting bob"), "bob is still the nearer")
                    bob.commandReply("tele 3222 3218") { it.startsWith("Teleported") }
                    alice.stepTicks(1)
                    bob.commandReply("tele 3246 3219") { it.startsWith("Teleported") }
                    assertEquals(
                        "${Aggression.TOLERANCE_TICKS} ticks in the areas around 3222, 3218 and 3246, 3219: aggressive monsters here are tolerant of you.",
                        bob.commandReply("aggro ${Aggression.TOLERANCE_TICKS}") { it.contains("ticks in the areas") },
                    )
                    alice.stepTicks(1)
                    assertTrue(spiderLine(alice).endsWith("; fighting alice"), "bob is tolerated, so alice")
                    alice.commandReply("resume") { it.startsWith("Resumed") }
                }
            }
        }
    }
}
