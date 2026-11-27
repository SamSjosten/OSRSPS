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
import osrsps.game.content.Revenants
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Wilderness step W5.2 through the headless client: a revenant imp in the Revenant Caves takes on a player by itself,
 * freezes them with its magic, and below half its hitpoints heals 20 as it attacks, then waits out its cooldown.
 */
class HeadlessRevenantsTest {
    private val imp = NpcType(
        7881, "Revenant imp", ops = listOf(null, "Attack", null, null, null), combatLevel = 7, internalName = "wild_cave_imp",
        stats = NpcStats(attack = 5, strength = 5, defence = 4, hitpoints = 10, ranged = 5, magic = 9),
    )
    private val types = NpcTypes.of(imp)

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3197 shr 6, 10070 shr 6) }, npcTypes = types, random = GameRandom(7),
            npcSpawns = listOf(NpcSpawn(imp, 3197, 10070, wander = 0, source = "test")), npcCombat = NpcCombat.load(types, SeqNames.NONE),
            cycleMillis = millis,
        )
    }

    @Test
    fun `it takes a player on, freezes them with its magic, and heals 20 below half`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel hitpoints 99") { true }
                client.command("varp 172 1")
                client.command("tele 3199 10072 0")
                client.keepAlive()
                val abe = server.world.playerNamed("abe")!!
                val npc = server.world.npcs.all.first { it.type.id == Revenants.KINDS[0].id }
                client.await("its attack", timeoutMillis = 20_000) { Unit.takeIf { npc.target === abe } }
                client.keepAlive()
                client.await("frozen", timeoutMillis = 40_000) { Unit.takeIf { abe.freezeTicks > 0 } }

                // The count first: the imp can heal as soon as the hit lands (Codex review on #1013).
                val heals = Revenants.heals(server.world, npc)
                val before = heals.left
                client.commandReply("hit 6 ${npc.index}") { it.startsWith("Hit Revenant imp") }
                client.await("its heal", timeoutMillis = 20_000) { Unit.takeIf { heals.left == before - 1 } }
                assertEquals(10, npc.hitpoints, "20 heals the imp's 10 whole")
                assertTrue(heals.readyTick > server.world.tick, "a cooldown after")
                assertTrue(before in Revenants.HEALS_MIN..Revenants.HEALS_MAX)
            }
        }
    }
}
