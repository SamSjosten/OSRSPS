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
 * Varrock step 14 through the headless client, with the real NPC combat file: a level 20 dark wizard of the stone
 * circle (5088, aggressive) goes for a player four tiles off unprovoked, hurts them with Earth Strike and lowers their
 * Strength with Weaken, all from where it stands. The rl-client script test-runner/examples/varrock-dark-wizards.json
 * does the same at the stone circle.
 */
class HeadlessVarrockDarkWizardsTest {
    private val wizard = NpcType(
        WIZARD, "Dark wizard", ops = listOf(null, "Attack", null, null, null), combatLevel = 20, internalName = "qip_ds_young_dark_wizard3",
        stats = NpcStats(attack = 17, strength = 17, defence = 14, hitpoints = 24, magic = 22),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(wizard)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), npcTypes = types,
            npcSpawns = listOf(NpcSpawn(wizard, WIZARD_X, Z, wander = 0, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), cycleMillis = millis,
        )
    }

    @Test
    fun `a level 20 dark wizard hurts with Earth Strike and weakens with Weaken from four tiles off`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                // Strength 40 for Weaken's 5% to take 2; hitpoints 99 to outlast the strikes. Combat level 38, under twice 20.
                client.commandReply("setlevel strength 40") { it.contains("40") }
                client.commandReply("setlevel hitpoints 99") { it.contains("99") }
                val alice = server.world.playerNamed("alice")!!
                assertTrue(alice.skills.combatLevel <= 40, "combat level ${alice.skills.combatLevel}")
                client.command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
                client.await("auto retaliate off") { if (client.varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1) Unit else null }
                client.command("tele $PLAYER_X $Z 0")
                client.await("the teleport") { client.position?.takeIf { it == Coord(PLAYER_X, Z, 0) } }
                client.await("a strike's damage", timeoutMillis = 60_000) { client.stat(Skill.HITPOINTS.id)?.takeIf { it.level < 99 } }
                client.await("Weaken's drain", timeoutMillis = 60_000) { client.stat(Skill.STRENGTH.id)?.takeIf { it.level == 38 } }
                assertEquals(Coord(WIZARD_X, Z, 0), client.npcs().first { it.id == WIZARD }.coord)
                assertEquals(Coord(PLAYER_X, Z, 0), client.position)
            }
        }
    }

    private companion object {
        const val WIZARD = 5088
        const val PLAYER_X = 3222
        const val WIZARD_X = 3226
        const val Z = 3218
    }
}
