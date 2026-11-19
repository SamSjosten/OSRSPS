package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.content.CairnIsle
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Karamja step K4.1 through the headless client: Cairn Isle's rock slide refusing below 15 Agility and climbed across
 * to the promontory, and the bridge's balance check on stepping onto it, which ends across with the balance message or
 * back on the shore with the fall's. test-runner/examples/cairn-isle.json does the same on the real map.
 */
class HeadlessCairnIsleTest {
    /** The rock slide's six tiles, as the 242 map has them. */
    private val locs = listOf(2978, 2979, 2980).flatMap { z -> listOf(ZoneLoc(ROCKS, 22, 2, 2792, z, 0), ZoneLoc(ROCKS, 22, 0, 2794, z, 0)) }

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply { markLoaded(2794 shr 6, 2979 shr 6); markLoaded(2770 shr 6, 2979 shr 6) },
            locTypes = LocTypes(mapOf(ROCKS to LocType(ROCKS, "Rocks", listOf("Climb", null, null, null, null), LocCollisionType(width = 1, length = 1, interactType = 0)))),
            staticLocs = StaticLocs().apply { locs.forEach(::add) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.ready() {
        await("the login rebuild") { position }
        commandReply("keys on") { it == "Dialogue shows keys" }
    }

    @Test
    fun `the rock slide wants 15 Agility and climbs across to the promontory`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.ready()
                client.tele(2795, 2979)
                client.locOp(ROCKS, 2794, 2979, 1)
                client.await("the level message") { client.messages().firstOrNull { it.text == "[agility.cairn_rocks_level level=15]" } }
                client.command("setlevel agility 99")
                client.await("level 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                val xp = client.stat(AGILITY)!!.xp
                client.locOp(ROCKS, 2794, 2979, 1)
                client.await("on the promontory") { client.position?.takeIf { it == Coord(2791, 2979, 0) } }
                client.await("1 XP") { client.stat(AGILITY)?.takeIf { it.xp >= xp + 1 } }
                client.locOp(ROCKS, 2792, 2979, 1)
                client.await("back down") { client.position?.takeIf { it == Coord(2795, 2979, 0) } }
            }
        }
    }

    @Test
    fun `stepping onto the bridge checks the player's balance, across or washed back to the shore`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ben").use { client ->
                client.ready()
                client.command("setlevel agility 99")
                client.await("level 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                client.tele(CairnIsle.EAST_END + 1, CairnIsle.Z)
                client.walk(CairnIsle.EAST_END - 1, CairnIsle.Z)
                val text = client.await("the bridge's end") {
                    client.messages().firstOrNull { it.text == "[cairn_isle.balance]" || it.text == "[cairn_isle.fell]" }?.text
                }
                val expected = if (text == "[cairn_isle.balance]") Coord(CairnIsle.WEST_END - 1, CairnIsle.Z, 0) else Coord(CairnIsle.SHORE.x, CairnIsle.SHORE.z, 0)
                assertEquals(expected, client.await("where it ends") { client.position?.takeIf { it == expected } })
            }
        }
    }

    @Test
    fun `a run onto the bridge stops on its end, where the check starts`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cal").use { client ->
                client.ready()
                client.command("setlevel agility 99")
                client.await("level 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                client.tele(CairnIsle.EAST_END + 1, CairnIsle.Z)
                client.awaitTicks(2)
                val player = server.world.playerNamed("cal")!!
                player.runEnabled = true
                client.commandReply("pause") { it.contains("aused after tick") }
                client.walk(CairnIsle.EAST_END - 3, CairnIsle.Z)
                var tick = 0
                while (player.x == CairnIsle.EAST_END + 1 && tick++ < 5) client.stepTicks(1)
                assertEquals(CairnIsle.EAST_END, player.x, "the run's first tile is the bridge's end, and it stops there")
                client.commandReply("resume") { it.startsWith("Resumed") }
                client.await("the bridge's end") { client.messages().firstOrNull { it.text == "[cairn_isle.balance]" || it.text == "[cairn_isle.fell]" } }
            }
        }
    }

    private companion object {
        const val ROCKS = 2231
        const val AGILITY = 16
    }
}
