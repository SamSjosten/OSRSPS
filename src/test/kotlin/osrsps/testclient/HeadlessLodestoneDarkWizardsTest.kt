package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.EquipmentTab
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.content.CustomLocs
import osrsps.game.content.CustomVars
import osrsps.game.content.LodestoneMap
import osrsps.game.content.Lodestones
import osrsps.game.loc.LocTypes
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * The 8 Nov nightly's lodestone-network failure on a low-level account, through the headless client with the real
 * NPC combat file. The young dark wizards of the stone circle south of Varrock (aggressive, hunt range 5, going for
 * players up to twice their level) spawn four tiles from the script's old tele tile 3219,3376, so a new account put
 * there is cast at; a lodestone cast within [World.LOGOUT_COMBAT_TICKS] of a hit is refused as in combat, wherever
 * the player has gone since, and goes through once the window has passed. The script's tile since PR #494, 3210,3376
 * west of the stone, lies outside every tile the circle can wander to, so the same account activates the stone and
 * casts untouched. test-runner/examples/lodestone-network.json walks that path in the real client.
 */
class HeadlessLodestoneDarkWizardsTest {
    private val varrock = Lodestones.VARROCK
    private val lumbridge = Lodestones.LUMBRIDGE

    /** Our vars after the test world's (none): the stones' loc types name the varbits, so the world gets the same. */
    private val vars = CustomVars.after(VarTypes.NONE)

    /** The circle's two kinds: level 7 (5087) and level 20 (5088), the combat file's young_dark_wizard groups. */
    private val young = NpcType(
        5087, "Dark wizard", ops = listOf(null, "Attack", null, null, null), combatLevel = 7, internalName = "qip_ds_young_dark_wizard2",
        stats = NpcStats(hitpoints = 12, magic = 6),
    )
    private val bearded = NpcType(
        5088, "Dark wizard", ops = listOf(null, "Attack", null, null, null), combatLevel = 20, internalName = "qip_ds_young_dark_wizard3",
        stats = NpcStats(attack = 17, strength = 17, defence = 14, hitpoints = 24, magic = 22),
    )

    /** The stones' map squares, open ground with our loc types, and the circle's six spawns from npc-spawns/varrock.json. */
    private fun server(dir: Path, wander: Int) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(young, bearded)
        World(
            crcs,
            CollisionMap().apply { for (site in Lodestones.SITES) markLoaded(site.x shr 6, site.z shr 6) },
            random = GameRandom(5), customVars = vars, locTypes = LocTypes.NONE.with(CustomLocs(vars).types), npcTypes = types,
            npcSpawns = listOf(
                NpcSpawn(bearded, 3223, 3367, wander = wander, source = "test"),
                NpcSpawn(young, 3223, 3372, wander = wander, source = "test"),
                NpcSpawn(bearded, 3224, 3370, wander = wander, source = "test"),
                NpcSpawn(young, 3225, 3365, wander = wander, source = "test"),
                NpcSpawn(young, 3225, 3374, wander = wander, source = "test"),
                NpcSpawn(bearded, 3228, 3373, wander = wander, source = "test"),
            ),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), cycleMillis = millis,
        )
    }

    /** Teleports in a paused world: commands answer at once, the move shows on the next stepped tick. */
    private fun HeadlessClient.teleStepped(to: Coord) {
        commandReply("tele ${to.x} ${to.z}") { it.startsWith("Teleported") }
        stepTicks(1)
        assertEquals(to, position)
    }

    /** Steps a tick at a time until [condition] holds, at most [ticks] times. */
    private fun HeadlessClient.stepUntil(ticks: Int, condition: () -> Boolean): Boolean {
        repeat(ticks) {
            if (condition()) return true
            stepTicks(1)
        }
        return condition()
    }

    @Test
    fun `at the script's old Varrock tile a new account is cast at, and a lodestone cast is refused until the combat window passes`(@TempDir dir: Path) {
        server(dir, wander = 0).use { server ->
            val world = server.world
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                val alice = world.playerNamed("alice")!!
                assertTrue(alice.skills.combatLevel <= 2 * young.combatLevel, "combat level ${alice.skills.combatLevel}, within the level 7s' rule")
                // The script's account never fights back; neither does she, so nothing but the wizards moves her.
                client.command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
                client.await("auto retaliate off") { if (client.varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1) Unit else null }
                client.commandReply("pause") { it.startsWith("Paused after tick") }
                client.teleStepped(OLD_TILE)
                assertTrue(client.stepUntil(12) { alice.attackedTick >= 0 }, "a wizard cast at her within 12 ticks of arriving")
                assertEquals(young.id, alice.attackedBy?.type?.id, "the level 7 at 3223, 3372, four tiles off")
                val hitAt = alice.attackedTick

                // On to the next stone as the script does, and the map pick from there.
                client.teleStepped(EDGEVILLE_TILE)
                val messagesBefore = client.messages().size
                val animsBefore = client.animationTicks().size
                client.command("lodestone lumbridge")
                client.stepTicks(2)
                assertTrue(world.tick - hitAt < World.LOGOUT_COMBAT_TICKS, "still inside the window at tick ${world.tick}, hit at $hitAt")
                assertTrue(client.messages().drop(messagesBefore).any { it.text == "[home_teleport.in_combat]" }, "refused as in combat: ${client.messages().drop(messagesBefore).map { it.text }}")
                client.stepTicks(Lodestones.CAST_TICKS)
                assertEquals(EDGEVILLE_TILE, client.position, "no cast")
                assertEquals(animsBefore, client.animationTicks().size, "no stage played")

                // The window is LOGOUT_COMBAT_TICKS from the hit; the script waits 17 ticks before its pick, so the cast goes through.
                assertTrue(world.tick - hitAt >= World.LOGOUT_COMBAT_TICKS)
                client.command("lodestone lumbridge")
                assertTrue(client.stepUntil(3) { client.animationTicks().size > animsBefore }, "the first stage")
                client.stepTicks(Lodestones.CAST_TICKS)
                client.await("the landing") { client.position?.takeIf { it == Coord(lumbridge.x + 1, lumbridge.z + 1) } }
                client.commandReply("resume") { it.startsWith("Resumed after tick") }
            }
        }
    }

    @Test
    fun `at the script's new tile west of the stone no wizard goes for a new account, and the pick casts`(@TempDir dir: Path) {
        server(dir, wander = 3).use { server ->
            val world = server.world
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                val alice = world.playerNamed("alice")!!
                client.commandReply("pause") { it.startsWith("Paused after tick") }
                client.teleStepped(NEW_TILE)
                client.stepTicks(5)
                assertTrue(world.npcs.all.none { it.target === alice }, "nobody hunts her at ${NEW_TILE.x}, ${NEW_TILE.z}")

                client.locOp(varrock.id, varrock.x, varrock.z, Lodestones.ACTIVATE)
                val messagesBefore = client.messages().size
                assertTrue(
                    client.stepUntil(12) { client.messages().drop(messagesBefore).any { it.text == "[lodestone.activated name=[lodestone.varrock]]" } },
                    "the activation: ${client.messages().drop(messagesBefore).map { it.text }}",
                )
                assertTrue(alice.x <= varrock.x - 1, "activated from the stone's west side, at ${alice.x}, ${alice.z}")
                assertEquals(1 or (1 shl varrock.index), client.varp(vars.lodestonesVarp), "Lumbridge's and Varrock's bits")

                // The script's wait before its pick, then the map and Lumbridge's row.
                client.stepTicks(17)
                client.command("lodestone")
                client.stepTicks(1)
                client.await("the lodestone map") { if (MAP in client.openInterfaces() && client.opEnabled(MAP, LodestoneMap.CLOSE, -1, LodestoneMap.CLOSE_OP)) Unit else null }
                val animsBefore = client.animationTicks().size
                client.ifButton(MAP, LodestoneMap.row(lumbridge), LodestoneMap.TELEPORT)
                assertTrue(client.stepUntil(3) { client.animationTicks().size > animsBefore }, "the first stage on the pick")
                client.stepTicks(Lodestones.CAST_TICKS)
                client.await("the landing") { client.position?.takeIf { it == Coord(lumbridge.x + 1, lumbridge.z + 1) } }
                assertTrue(alice.attackedTick < 0, "never cast at")
                assertNull(alice.attackedBy)
                client.commandReply("resume") { it.startsWith("Resumed after tick") }
            }
        }
    }

    private companion object {
        const val MAP = LodestoneMap.INTERFACE

        /** lodestone-network.json's Varrock tele tile before PR #494, four tiles from the level 7 at 3223, 3372. */
        val OLD_TILE = Coord(3219, 3376)

        /** Its tile since: west of the stone, eight tiles from the nearest tile the circle wanders to. */
        val NEW_TILE = Coord(3210, 3376)

        /** The script's Edgeville tele tile, where the map pick of the failing run was made. */
        val EDGEVILLE_TILE = Coord(3076, 3505)
    }
}
