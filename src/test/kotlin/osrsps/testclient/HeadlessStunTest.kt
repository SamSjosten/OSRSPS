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
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Stuns through the headless client: a stunned player's walk click is dropped and one after the stun walks, and a stun
 * ends a fight, the player's attack XP standing still until auto retaliate takes the fight up again after it; run
 * energy still comes back while stunned. The world is paused and stepped, so the checks count game ticks; a stun typed
 * while paused after tick n lasts to the end of tick n + ticks - 1, as "this tick" has already run. The rl-client script
 * test-runner/examples/stun.json does the same in the real client.
 */
class HeadlessStunTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3224, 3218, source = "test")), npcCombat = NpcCombat.load(types, SeqNames.NONE),
            npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.attackXp() = stat(Skill.ATTACK.id)?.xp ?: 0

    /**
     * Runs [ticks] ticks of a paused world and waits for all of their updates, so the tests count game ticks and not
     * test-thread time. As HeadlessCastleTest's step: the run ends with "Paused after tick n", then its tick end.
     */
    private fun HeadlessClient.step(ticks: Int) {
        val from = messages().size
        command("step $ticks")
        val done = await("the steps to finish") { messages().drop(from).firstOrNull { it.text.matches(STEPPED) } }
        await("the last stepped tick's end") { if (ticksSeen > done.tick) Unit else null }
    }

    private fun HeadlessClient.pause() = commandReply("pause") { it.startsWith("Paused after tick") }

    @Test
    fun `a stunned player can't walk, and walks once the stun ends`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val start = client.await("the login rebuild") { client.position }
                client.pause()
                client.commandReply("stun 5") { it.startsWith("Stunned for 5 more ticks") }
                // Held for the next tick, which the stun covers.
                client.walk(start.x, start.z + 3)
                client.step(5)
                assertEquals(start, client.position, "the click while stunned went nowhere")
                client.commandReply("stun") { it == "Not stunned." }
                client.walk(start.x, start.z + 3)
                client.step(3)
                client.await("walked north") { client.position?.takeIf { it.z == start.z + 3 } }
            }
        }
    }

    @Test
    fun `run energy comes back while stunned`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                val start = client.await("the login rebuild") { client.position }
                // A ctrl-click runs, which drains energy.
                client.walk(start.x, start.z + 8, keyCombination = 1)
                client.await("ran north") { client.position?.takeIf { it.z == start.z + 8 } }
                client.await("energy drained") { client.runEnergyHundredths.takeIf { it in 0 until 10_000 } }
                client.pause()
                client.commandReply("stun 8") { it.startsWith("Stunned for 8 more ticks") }
                val energy = client.runEnergyHundredths
                client.step(4)
                assertTrue(client.runEnergyHundredths > energy, "energy recovered while stunned")
            }
        }
    }

    @Test
    fun `a stun ends a fight, the player doesn't hit back while stunned, and auto retaliate takes it up again after`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val npc = client.await("the man in view") { client.npcs().firstOrNull { it.id == man.id } }
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                // Each hit that does damage gives attack XP (accurate punches), so XP going up means the player is swinging.
                client.npcOp(npc.index, 2)
                client.await("a first hit", timeoutMillis = 10_000) { client.attackXp().takeIf { it > 0 } }
                client.pause()
                client.commandReply("stun 12") { it.startsWith("Stunned for 12 more ticks") }
                client.step(1)
                val xp = client.attackXp()
                // He hits every 4 ticks all the while; stunned, the player neither swings nor retaliates.
                client.step(10)
                assertEquals(xp, client.attackXp(), "no swing while stunned")
                client.commandReply("resume") { it.startsWith("Resumed") }
                client.await("retaliating once the stun ends", timeoutMillis = 15_000) { client.attackXp().takeIf { it > xp } }
            }
        }
    }

    private companion object {
        /** What a finished ::step says; the ::pause reply has more after the number. */
        val STEPPED = Regex("Paused after tick \\d+")
    }
}
