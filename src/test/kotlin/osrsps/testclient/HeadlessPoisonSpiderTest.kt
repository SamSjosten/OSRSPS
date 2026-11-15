package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.Poison
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Wilderness step W2.1: the Edgeville Dungeon's poison spider, with the real NPC combat file, through the headless
 * client. It comes for the player unprovoked, and its hits poison them starting at 6 (the client's poison varp at 30,
 * five times the damage). Its stats here are the Wiki's level 64's.
 */
class HeadlessPoisonSpiderTest {
    private val spider = NpcType(
        3023, "Poison spider", ops = listOf(null, "Attack", null, null, null), combatLevel = 64, internalName = "poisonspider",
        stats = NpcStats(attack = 50, defence = 50, strength = 50, hitpoints = 64),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(spider)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(11), npcTypes = types,
            npcSpawns = listOf(NpcSpawn(spider, 3223, 3218, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), cycleMillis = millis,
        )
    }

    @Test
    fun `a poison spider attacks unprovoked and poisons the player at 6`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.await("the spider in view") { client.npcs().firstOrNull { it.id == spider.id } }
                client.commandReply("setlevel hitpoints 99") { it.contains("set to level 99") }
                client.await("poisoned by the spider", timeoutMillis = 60_000) { client.varp(Poison.VARP).takeIf { it > 0 } }
                // Its first poison starts at 6: five times that in the varp (a tick of it may have passed, 29).
                val varp = client.varp(Poison.VARP)
                assertEquals(true, varp in 29..30, "poison varp $varp")
                assertEquals(true, (client.stat(Skill.HITPOINTS.id)?.level ?: 99) < 99, "the spider hit")
            }
        }
    }
}
