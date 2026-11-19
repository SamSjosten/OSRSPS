package osrsps.testclient

import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.content.PollBooths
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The poll booths through the headless client: Use opens the ballot with an empty poll history, and its close button
 * shuts it. The rl-client script test-runner/examples/poll-booth.json does the same in Varrock's west bank.
 */
class HeadlessPollBoothTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply { markLoaded(BOOTH_X shr 6, BOOTH_Z shr 6) },
            locTypes = LocTypes(
                mapOf(BOOTH to LocType(BOOTH, "Poll booth", ops = listOf("Use", null, null, null, null), collision = LocCollisionType(width = 1, length = 1))),
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(BOOTH, LocShape.CENTREPIECE_STRAIGHT, 0, BOOTH_X, BOOTH_Z, 0)) },
            cycleMillis = millis,
        )
    }

    @Test
    fun `Use opens the ballot with no polls in it, and its X closes it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele ${BOOTH_X - 1} $BOOTH_Z 0")
                client.await("beside the booth") { client.position?.takeIf { it == Coord(BOOTH_X - 1, BOOTH_Z, 0) } }
                client.locOp(BOOTH, BOOTH_X, BOOTH_Z, PollBooths.USE)
                client.await("the ballot") { if (PollBooths.BALLOT in client.openInterfaces()) Unit else null }
                val history = client.await("the poll history") { client.clientScripts().firstOrNull { it.id == PollBooths.SCRIPT_LIST_BUILD } }
                assertEquals(listOf(PollBooths.thisYear(), 0, emptyList<String>(), emptyList<String>(), emptyList<Int>(), emptyList<Int>()), history.args)

                client.ifButton(PollBooths.BALLOT, PollBooths.CLOSE, 1)
                client.await("the ballot closed") { if (PollBooths.BALLOT in client.openInterfaces()) null else Unit }
                assertTrue(server.world.playerNamed("alice")!!.floater < 0)
            }
        }
    }

    private companion object {
        /** pollbooth_brown, the multiloc in Varrock's west bank. */
        const val BOOTH = 26813
        const val BOOTH_X = 3180
        const val BOOTH_Z = 3438
    }
}
