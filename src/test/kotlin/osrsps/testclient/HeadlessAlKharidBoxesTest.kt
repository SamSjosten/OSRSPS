package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.content.AlKharid
import osrsps.game.content.SearchLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Al Kharid step 7 through the headless client: Search on the boxes by Ali Morrisane's stall finds nothing. The rl-client
 * script test-runner/examples/al-kharid-mine-fish.json searches them in the real client.
 */
class HeadlessAlKharidBoxesTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            locTypes = LocTypes(
                mapOf(AlKharid.BOXES to LocType(AlKharid.BOXES, "Boxes", listOf("Search", null, null, null, null), LocCollisionType(width = 1, length = 1), internalName = "boxes3")),
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(AlKharid.BOXES, LocShape.CENTREPIECE_STRAIGHT, 0, 3223, 3218, 0)) },
            cycleMillis = millis,
        )
    }

    @Test
    fun `Search on the boxes finds nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(AlKharid.BOXES, 3223, 3218, 1)
                client.awaitMessage("the empty boxes") { it == DialogueText.NONE[SearchLines.BOXES_EMPTY] }
            }
        }
    }
}
