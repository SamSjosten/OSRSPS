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

    @Test
    fun `held at its leash, it fires out to 10 tiles, so no spot just past its range is safe`(@TempDir dir: Path) {
        val server = TestServer(dir, cycleMillis = 30) { crcs, millis ->
            World(
                crcs, CollisionMap().apply { markLoaded(3170 shr 6, 10070 shr 6) }, npcTypes = types, random = GameRandom(9),
                npcSpawns = listOf(NpcSpawn(imp, 3170, 10070, wander = 0, source = "test")), npcCombat = NpcCombat.load(types, SeqNames.NONE),
                cycleMillis = millis,
            )
        }
        server.use {
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel hitpoints 99") { true }
                // 16 tiles east of its spawn: it can come only 7 of them, and stops 9 away (exploit review R65B-2).
                client.command("tele 3186 10070 0")
                client.await("there") { client.position?.takeIf { it == Coord(3186, 10070, 0) } }
                val abe = server.world.playerNamed("abe")!!
                val npc = server.world.npcs.all.first { it.type.id == Revenants.KINDS[0].id }
                client.commandReply("hit 1 ${npc.index}") { it.startsWith("Hit Revenant imp") }
                client.keepAlive()
                client.await("its shot from the leash", timeoutMillis = 30_000) { Unit.takeIf { abe.hitpoints < 99 || abe.freezeTicks > 0 } }
                assertTrue(kotlin.math.abs(npc.x - 3170) <= 7, "it stayed on its leash: ${npc.x}")
                assertTrue(3186 - npc.x > Revenants.RANGE, "fired from past its range: ${npc.x}")
                // And again each time its attack is due, not a tick late (Codex review on #1031).
                client.keepAlive()
                val first = npc.nextAttackTick
                client.await("its next shot", timeoutMillis = 20_000) { Unit.takeIf { npc.nextAttackTick > first } }
                val second = npc.nextAttackTick
                client.keepAlive()
                client.await("the one after", timeoutMillis = 20_000) { Unit.takeIf { npc.nextAttackTick > second } }
                assertEquals(second - first, npc.nextAttackTick - second, "a steady cadence")
                assertTrue(second - first <= 5, "every ${second - first} ticks")
            }
        }
    }
}
