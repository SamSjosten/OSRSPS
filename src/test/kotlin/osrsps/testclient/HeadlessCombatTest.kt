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
import osrsps.game.combat.NpcCombat
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/** Combat through the headless client, with the real NPC combat file. */
class HeadlessCombatTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3224, 3218, source = "test")), npcCombat = NpcCombat.load(types, SeqNames.NONE),
            cycleMillis = millis,
        )
    }

    /** Waits for [text] among every message so far: a command's later lines can arrive in the same tick as its first. */
    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `combat gives a new account's numbers against a man, and follows the style button`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                assertEquals("Punch (accurate, crush), speed 4: attack roll 768, max hit 1", client.commandReply("combat") { it.startsWith("Punch") })
                client.seen("vs Man #1 (level 2, 7 hp): defence roll 430, you hit 71.9%")
                client.seen("It: attack roll 640 vs your 576, hits 54.9%, max hit 1")
                client.command("varp 43 3")
                assertEquals("Block (defensive, crush), speed 4: attack roll 576, max hit 1", client.commandReply("combat man") { it.startsWith("Block") })
                client.seen("It: attack roll 640 vs your 768, hits 41.6%, max hit 1")
            }
        }
    }

    @Test
    fun `attacking a man punches him to death, with XP for every point, and he comes back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                client.npcOp(npc.index, 2)
                // Two tiles away: a step, then the first swing.
                client.awaitTicks(3)
                val fighting = client.commandReply("fight", timeoutMillis = 5_000) { it.startsWith("You are fighting") }
                assertTrue(fighting.startsWith("You are fighting Man #${npc.index} ("), fighting)
                // Every hit is 0 or 1, so the man takes seven of them however the dice fall: about 20 swings.
                client.await("the man's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == man.id }) Unit else null }
                assertEquals(7 * 4, client.stat(ATTACK)!!.xp)
                assertEquals(1_154 + 9, client.stat(HITPOINTS)!!.xp)
                client.await("the man's return", timeoutMillis = 10_000) { client.npcs().firstOrNull { it.id == man.id } }
                assertEquals("You are not fighting anything.", client.commandReply("fight") { it.startsWith("You are") })
            }
        }
    }

    companion object {
        /** Skill ids as the stat packet carries them. */
        private const val ATTACK = 0
        private const val HITPOINTS = 3
    }
}
