package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.TunnelOfChaos
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Misthalin fixes 14 through the headless client: after What Lies Below the statue east of Varrock is open, Enter goes
 * down into the Tunnel of Chaos and its stairs come back up; the portal does nothing without a chaos talisman and takes
 * the player to the Chaos Altar maze with one; the maze's portal comes back. The rl-client script
 * test-runner/examples/misthalin-fixes-14.json does the same on the live cache.
 */
class HeadlessTunnelOfChaosTest {
    private fun loc(id: Int, name: String, op: String) =
        LocType(id, name, ops = listOf(op, null, null, null, null), collision = LocCollisionType(width = 1, length = 1))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                for ((x, z) in listOf(3222 to 3218, 3283 to 3468, 3179 to 5191, 3152 to 5233, 2270 to 4836, 3987 to 4940)) markLoaded(x shr 6, z shr 6)
            },
            objTypes = ObjTypes.of(ObjType(TunnelOfChaos.CHAOS_TALISMAN, "Chaos talisman")),
            locTypes = LocTypes(
                listOf(
                    loc(TunnelOfChaos.STATUE, "Open statue", "Enter"), loc(TunnelOfChaos.STAIRS, "Stairs", "Climb"),
                    loc(TunnelOfChaos.PORTAL, "Portal", "Use"), loc(TunnelOfChaos.MAZE_PORTAL, "Portal", "Use"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(TunnelOfChaos.STATUE, LocShape.CENTREPIECE_STRAIGHT, 0, 3283, 3469, 0))
                add(ZoneLoc(TunnelOfChaos.STAIRS, LocShape.CENTREPIECE_STRAIGHT, 0, 3179, 5189, 0))
                add(ZoneLoc(TunnelOfChaos.PORTAL, LocShape.CENTREPIECE_STRAIGHT, 0, 3152, 5233, 0))
                add(ZoneLoc(TunnelOfChaos.MAZE_PORTAL, LocShape.CENTREPIECE_STRAIGHT, 0, 2270, 4836, 1))
                // The cache's second copy of the statue, far from Varrock.
                add(ZoneLoc(TunnelOfChaos.STATUE, LocShape.CENTREPIECE_STRAIGHT, 0, 3987, 4940, 0))
            },
            // surok_tunnelcheck and surok_portal on surok_main, as the 242 cache has them.
            varTypes = VarTypes(6_000, mapOf(TunnelOfChaos.VARBIT_TUNNEL_OPEN to VarbitType(992, 8, 8), TunnelOfChaos.VARBIT_MAZE_PORTAL to VarbitType(992, 15, 15))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.at(x: Int, z: Int, level: Int = 0) =
        await("at $x,$z,$level") { if (position == Coord(x, z, level)) Unit else null }

    @Test
    fun `the statue, the stairs and the portals`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                // The statue and the maze portal open after the quest; the vars can reach the client after the rebuild.
                client.await("the statue and the maze portal open") { if (client.varp(992) == 1 shl 8 or (1 shl 15)) Unit else null }

                client.command("tele 3283 3466")
                client.at(3283, 3466)
                client.awaitTicks(3)
                client.locOp(TunnelOfChaos.STATUE, 3283, 3469, TunnelOfChaos.OP)
                client.at(TunnelOfChaos.TUNNEL_LANDING.x, TunnelOfChaos.TUNNEL_LANDING.z)
                client.awaitTicks(3)
                client.locOp(TunnelOfChaos.STAIRS, 3179, 5189, TunnelOfChaos.OP)
                client.at(TunnelOfChaos.SURFACE_LANDING.x, TunnelOfChaos.SURFACE_LANDING.z)

                client.command("tele 3153 5233")
                client.at(3153, 5233)
                client.awaitTicks(3)
                client.locOp(TunnelOfChaos.PORTAL, 3152, 5233, TunnelOfChaos.OP)
                client.await("nothing interesting") { client.messages().firstOrNull { it.text == "[game.nothing_interesting]" } }
                assertEquals(Coord(3153, 5233), client.position)

                client.command("item ${TunnelOfChaos.CHAOS_TALISMAN}")
                client.await("the talisman") { if (client.inventory(BACKPACK).any { it?.id == TunnelOfChaos.CHAOS_TALISMAN }) Unit else null }
                client.locOp(TunnelOfChaos.PORTAL, 3152, 5233, TunnelOfChaos.OP)
                client.at(TunnelOfChaos.MAZE_LANDING.x, TunnelOfChaos.MAZE_LANDING.z, TunnelOfChaos.MAZE_LEVEL)
                client.awaitTicks(3)
                client.locOp(TunnelOfChaos.MAZE_PORTAL, 2270, 4836, TunnelOfChaos.OP)
                client.at(TunnelOfChaos.PORTAL_LANDING.x, TunnelOfChaos.PORTAL_LANDING.z)
            }
        }
    }

    @Test
    fun `the statue's other copy in the cache goes nowhere`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.await("the statue open") { if (client.varp(992) and (1 shl 8) != 0) Unit else null }
                client.command("tele 3987 4938")
                client.at(3987, 4938)
                client.awaitTicks(3)
                client.locOp(TunnelOfChaos.STATUE, 3987, 4940, TunnelOfChaos.OP)
                client.awaitTicks(8)
                assertEquals(Coord(3987, 4939), client.position, "walked up to the statue and stayed there")
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
    }
}
