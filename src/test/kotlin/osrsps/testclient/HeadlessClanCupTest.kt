package osrsps.testclient

import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.content.ClanCup
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import java.nio.file.Path
import kotlin.test.Test

/**
 * The Clan Cup portals through the headless client: Use upstairs in Edgeville goes into the Challenge area, and Use on
 * the Challenge area's portal back. The rl-client script test-runner/examples/clan-cup-portal.json does the same on
 * the real map.
 */
class HeadlessClanCupTest {
    private fun portal(id: Int) = LocType(id, "Clan Cup portal", ops = listOf("Use", null, null, null, null), collision = LocCollisionType(width = 1, length = 1))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(EDGE_X shr 6, EDGE_Z shr 6)
                markLoaded(AREA_PORTAL_X shr 6, AREA_PORTAL_Z shr 6)
            },
            locTypes = LocTypes(listOf(portal(ClanCup.EDGEVILLE_PORTAL), portal(ClanCup.PORTAL)).associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(ClanCup.EDGEVILLE_PORTAL, LocShape.CENTREPIECE_STRAIGHT, 0, EDGE_X, EDGE_Z, 1))
                add(ZoneLoc(ClanCup.PORTAL, LocShape.CENTREPIECE_STRAIGHT, 0, AREA_PORTAL_X, AREA_PORTAL_Z, 0))
            },
            cycleMillis = millis,
        )
    }

    @Test
    fun `Use goes into the Challenge area from Edgeville, and back out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele ${EDGE_X} ${EDGE_Z + 1} 1")
                client.await("upstairs in Edgeville") { client.position?.takeIf { it == Coord(EDGE_X, EDGE_Z + 1, 1) } }
                client.locOp(ClanCup.EDGEVILLE_PORTAL, EDGE_X, EDGE_Z, ClanCup.USE)
                client.await("in the Challenge area") { client.position?.takeIf { it == Coord(ClanCup.AREA_X, ClanCup.AREA_Z, 0) } }
                client.locOp(ClanCup.PORTAL, AREA_PORTAL_X, AREA_PORTAL_Z, ClanCup.USE)
                client.await("back in Edgeville") { client.position?.takeIf { it == Coord(ClanCup.EDGEVILLE_X, ClanCup.EDGEVILLE_Z, ClanCup.EDGEVILLE_LEVEL) } }
            }
        }
    }

    private companion object {
        const val EDGE_X = 3097
        const val EDGE_Z = 3507
        const val AREA_PORTAL_X = 3157
        const val AREA_PORTAL_Z = 9817
    }
}
