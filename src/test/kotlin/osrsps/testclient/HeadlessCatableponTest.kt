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
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Edgeville step 13b: the Pit of Pestilence's catablepon, with the real NPC combat file, through the headless client.
 * It comes for the player unprovoked and its Weaken glare lowers their Strength 4 a cast from 99 (5% of the level),
 * to 87: past 8% + 3 drained (10) it only swings, which 20 stepped game ticks check. ::god keeps its swings from
 * killing them.
 */
class HeadlessCatableponTest {
    private val catablepon = NpcType(
        2474, "Catablepon", ops = listOf(null, "Attack", null, null, null), size = 2, combatLevel = 49, internalName = "sos_pest_catablepon",
        stats = NpcStats(attack = 45, defence = 40, strength = 40, hitpoints = 40, magic = 60),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(catablepon)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(7), npcTypes = types,
            npcSpawns = listOf(NpcSpawn(catablepon, 3223, 3218, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), cycleMillis = millis,
        )
    }

    @Test
    fun `a catablepon's Weaken takes Strength from 99 to 87 and no further`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.await("the catablepon in view") { client.npcs().firstOrNull { it.id == catablepon.id } }
                client.command("god on")
                client.command("setlevel strength 99")
                client.await("Strength 99") { client.stat(Skill.STRENGTH.id)?.takeIf { it.level == 99 } }
                var lowest = 99
                val seen = sortedSetOf<Int>()
                client.await("Strength glared down to 87", timeoutMillis = 60_000) {
                    client.stat(Skill.STRENGTH.id)?.level?.let { lowest = minOf(lowest, it); seen += it }
                    Unit.takeIf { lowest <= 87 }
                }
                // Each glare takes 4 from where it is: 95, 91, 87.
                assertTrue(seen.all { it in setOf(87, 91, 95, 99) }, "levels seen: $seen")
                // Past 10 drained it only swings: paused and stepped a tick at a time, 20 game ticks (five of its attacks)
                // leave Strength at 87, or 88 if the once-a-minute restore comes round.
                client.commandReply("pause") { it.startsWith("Paused after tick") }
                repeat(20) {
                    client.stepTicks(1)
                    val level = client.stat(Skill.STRENGTH.id)?.level
                    assertTrue(level != null && level in 87..88, "Strength $level after ${it + 1} more ticks")
                }
                client.commandReply("resume") { it.startsWith("Resumed") }
            }
        }
    }
}
