package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.EquipmentTab
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
 * Draynor piece step 9 through the headless client, with the real NPC combat file: a dark wizard (512, aggressive)
 * goes for a player four tiles off unprovoked and hurts them with its spells from where it stands, never walking up
 * (the player has auto retaliate off, so neither moves).
 * The rl-client script test-runner/examples/draynor-dark-wizards.json does the same in Draynor.
 */
class HeadlessDarkWizardTest {
    private val wizard = NpcType(
        512, "Dark wizard", ops = listOf(null, "Attack", null, null, null), combatLevel = 7, internalName = "young_dark_wizard",
        stats = NpcStats(hitpoints = 12, magic = 6),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(wizard)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), npcTypes = types,
            npcSpawns = listOf(NpcSpawn(wizard, WIZARD_X, Z, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), cycleMillis = millis,
        )
    }

    @Test
    fun `a dark wizard casts at a player four tiles off without walking up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele $PLAYER_X $Z 0")
                client.await("the teleport") { client.position?.takeIf { it == Coord(PLAYER_X, Z, 0) } }
                // Auto retaliate off, so the player stays put while the wizard casts.
                client.command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
                client.await("auto retaliate off") { if (client.varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1) Unit else null }
                val hitpoints = client.await("hitpoints") { client.stat(Skill.HITPOINTS.id) }.level
                client.await("a spell's damage", timeoutMillis = 60_000) {
                    client.stat(Skill.HITPOINTS.id)?.takeIf { it.level < hitpoints }
                }
                val view = client.npcs().first { it.id == wizard.id }
                assertEquals(Coord(WIZARD_X, Z, 0), view.coord)
                assertEquals(Coord(PLAYER_X, Z, 0), client.position)
                assertTrue(WIZARD_X - PLAYER_X > 1)
            }
        }
    }

    private companion object {
        const val PLAYER_X = 3222
        const val WIZARD_X = 3226
        const val Z = 3218
    }
}
