package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNull
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

/**
 * The logout button does nothing until 16 ticks after the player was last hit (OSRS Wiki, Logout button), so a losing
 * fight can't be left with every item. The exploit review of 29 Oct 2026 found it worked mid-fight.
 */
class HeadlessLogoutCombatTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    @Test
    fun `logout is refused in combat and works 16 ticks after the last hit`(@TempDir dir: Path) {
        TestServer(dir) { crcs, millis ->
            val types = NpcTypes.of(man)
            World(
                crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), npcTypes = types,
                npcSpawns = listOf(NpcSpawn(man, 3224, 3218, source = "test")), npcCombat = NpcCombat.load(types, SeqNames.NONE),
                cycleMillis = millis,
            )
        }.use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                client.npcOp(npc.index, ATTACK)
                client.await("the man's swing", timeoutMillis = 15_000) { server.world.playerNamed("alice")?.attackedTick?.takeIf { it >= 0 } }
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.awaitMessage("the refusal") { it == "[game.cant_log_out]" }
                assertNull(client.packets().firstOrNull { it.prot.name == "LOGOUT" })

                // The fight over, the lock runs out 16 ticks after the last hit.
                client.command("kill ${npc.index}")
                client.await("the man dead") { if (server.world.npcs[npc.index]?.dead != false) Unit else null }
                val lastHit = server.world.playerNamed("alice")!!.attackedTick
                client.await("16 ticks after the last hit", timeoutMillis = 10_000) { if (server.world.tick - lastHit >= World.LOGOUT_COMBAT_TICKS) Unit else null }
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
                assertTrue(server.world.tick - lastHit >= World.LOGOUT_COMBAT_TICKS)
            }
        }
    }

    @Test
    fun `a hit landing in the tick logout was clicked in cancels the logout`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val alice = server.world.playerNamed("alice")!!
                // Stands in for the input phase taking the click, then an NPC's first hit landing later in that tick.
                alice.attackedTick = server.world.tick
                alice.logoutRequested = true
                client.awaitMessage("the refusal") { it == "[game.cant_log_out]" }
                client.awaitTicks(3)
                assertNull(client.packets().firstOrNull { it.prot.name == "LOGOUT" })
                assertTrue(server.world.playerNamed("alice") != null)
            }
        }
    }

    private companion object {
        const val ATTACK = 2
    }
}
