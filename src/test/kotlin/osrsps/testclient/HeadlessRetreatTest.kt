package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Retreat through the headless client, with the real NPC combat file: a unicorn on 4 of its 19 hitpoints, at or below
 * the quarter it retreats at, walks away from the player who attacks it instead of fighting back. The rl-client script
 * test-runner/examples/retreat.json does the same in the real client.
 */
class HeadlessRetreatTest {
    private val unicorn = NpcType(2837, "Unicorn", ops = listOf(null, "Attack", null, null, null), combatLevel = 15, size = 2, internalName = "unicorn", stats = NpcStats(attack = 10, defence = 10, strength = 13, hitpoints = 19))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(unicorn)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(unicorn, 3225, 3218, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    @Test
    fun `a unicorn low on hitpoints retreats from the player who attacks it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the unicorn in view") { client.npcs().firstOrNull { it.id == unicorn.id } }
                client.await("the login rebuild") { client.position }
                client.commandReply("npchp 4 ${npc.index}") { it.contains("now on 4 hitpoints") }
                client.npcOp(npc.index, 2)
                val line = client.await("the retreat in ::npcs", timeoutMillis = 15_000) {
                    val from = client.messages().size
                    client.command("npcs 15")
                    client.await("the unicorn's line") { client.messages().drop(from).firstOrNull { it.text.startsWith("#${npc.index} Unicorn") } }
                        .text.takeIf { it.endsWith("; retreating from alice") }
                }
                assertTrue(" at 3225, 3218;" !in line, "it walked off: $line")
            }
        }
    }
}
