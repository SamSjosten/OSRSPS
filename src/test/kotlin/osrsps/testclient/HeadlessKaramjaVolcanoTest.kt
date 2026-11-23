package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.content.KaramjaVolcano
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Karamja step K1.4 through the headless client: the volcano's rocks down into the dungeon and its rope back up, the
 * wall between the Karamja and Crandor sides opening both ways, and Crandor's hole down and its rope up.
 * test-runner/examples/karamja-volcano.json does the same on the real map.
 */
class HeadlessKaramjaVolcanoTest {
    private fun type(id: Int, name: String, op: String, size: Int = 1) =
        LocType(id, name, ops = listOf(op, null, null, null, null), collision = LocCollisionType(width = size, length = size))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(2856 shr 6, 3168 shr 6)
                markLoaded(2856 shr 6, 9569 shr 6)
                markLoaded(2836 shr 6, 9600 shr 6)
                markLoaded(2833 shr 6, 3255 shr 6)
            },
            locTypes = LocTypes(
                mapOf(
                    KaramjaVolcano.VOLCANO_ROCKS to type(KaramjaVolcano.VOLCANO_ROCKS, "Rocks", "Climb-down", size = 2),
                    KaramjaVolcano.VOLCANO_ROPE to type(KaramjaVolcano.VOLCANO_ROPE, "Climbing rope", "Climb"),
                    KaramjaVolcano.WALL to type(KaramjaVolcano.WALL, "Wall", "Open"),
                    KaramjaVolcano.CRANDOR_HOLE to type(KaramjaVolcano.CRANDOR_HOLE, "Hole", "Enter"),
                    KaramjaVolcano.CRANDOR_ROPE to type(KaramjaVolcano.CRANDOR_ROPE, "Climbing rope", "Climb"),
                ),
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(KaramjaVolcano.VOLCANO_ROCKS, LocShape.CENTREPIECE_STRAIGHT, 0, 2856, 3168, 0))
                add(ZoneLoc(KaramjaVolcano.VOLCANO_ROPE, LocShape.CENTREPIECE_STRAIGHT, 0, 2856, 9569, 0))
                add(ZoneLoc(KaramjaVolcano.WALL, LocShape.WALL_STRAIGHT, 3, KaramjaVolcano.WALL_X, KaramjaVolcano.WALL_Z, 0))
                add(ZoneLoc(KaramjaVolcano.CRANDOR_HOLE, LocShape.CENTREPIECE_STRAIGHT, 0, 2833, 3255, 0))
                add(ZoneLoc(KaramjaVolcano.CRANDOR_ROPE, LocShape.CENTREPIECE_STRAIGHT, 0, 2833, 9657, 0))
                // A copy of the volcano's rope on the surface, which the real map doesn't have: it must not climb.
                add(ZoneLoc(KaramjaVolcano.VOLCANO_ROPE, LocShape.CENTREPIECE_STRAIGHT, 0, STRAY_ROPE_X, 3168, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        arrive(x, z)
    }

    private fun HeadlessClient.arrive(x: Int, z: Int) {
        await("the player at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    @Test
    fun `the volcano's rocks go down into the dungeon and its rope climbs back up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(2856, 3167)
                client.locOp(KaramjaVolcano.VOLCANO_ROCKS, 2856, 3168, KaramjaVolcano.CLIMB_DOWN)
                client.arrive(KaramjaVolcano.VOLCANO_BELOW.x, KaramjaVolcano.VOLCANO_BELOW.z)
                client.locOp(KaramjaVolcano.VOLCANO_ROPE, 2856, 9569, KaramjaVolcano.CLIMB)
                client.arrive(KaramjaVolcano.VOLCANO_ABOVE.x, KaramjaVolcano.VOLCANO_ABOVE.z)
            }
        }
    }

    @Test
    fun `the wall opens from the Karamja side and from the Crandor side`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ben").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(KaramjaVolcano.WALL_X, KaramjaVolcano.WALL_Z - 1)
                client.locOp(KaramjaVolcano.WALL, KaramjaVolcano.WALL_X, KaramjaVolcano.WALL_Z, KaramjaVolcano.OPEN)
                client.arrive(KaramjaVolcano.WALL_X, KaramjaVolcano.WALL_Z)
                client.locOp(KaramjaVolcano.WALL, KaramjaVolcano.WALL_X, KaramjaVolcano.WALL_Z, KaramjaVolcano.OPEN)
                client.arrive(KaramjaVolcano.WALL_X, KaramjaVolcano.WALL_Z - 1)
            }
        }
    }

    @Test
    fun `Crandor's hole goes down and its rope climbs back up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cal").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(2834, 3254)
                client.locOp(KaramjaVolcano.CRANDOR_HOLE, 2833, 3255, KaramjaVolcano.ENTER)
                client.arrive(KaramjaVolcano.CRANDOR_BELOW.x, KaramjaVolcano.CRANDOR_BELOW.z)
                client.locOp(KaramjaVolcano.CRANDOR_ROPE, 2833, 9657, KaramjaVolcano.CLIMB)
                client.arrive(KaramjaVolcano.CRANDOR_ABOVE.x, KaramjaVolcano.CRANDOR_ABOVE.z)
            }
        }
    }

    @Test
    fun `a copy of a climb anywhere else does nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dee").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(STRAY_ROPE_X, 3167)
                client.locOp(KaramjaVolcano.VOLCANO_ROPE, STRAY_ROPE_X, 3168, KaramjaVolcano.CLIMB)
                client.awaitMessage("nothing interesting") { it == "[game.nothing_interesting]" }
                client.arrive(STRAY_ROPE_X, 3167)
            }
        }
    }

    private companion object {
        const val STRAY_ROPE_X = 2861
    }
}
