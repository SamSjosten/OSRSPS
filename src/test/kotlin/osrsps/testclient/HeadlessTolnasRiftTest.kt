package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.content.TolnasRift
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Misthalin piece 7 step 6b through the headless client: Tolna's rift's rope down into the dungeon's lobby, the rope
 * back up beside Tolna, and a hole without the rope sending the player to it. The rl-client script
 * test-runner/examples/tolnas-rift.json does the same on the real map, with the rooms' monsters.
 */
class HeadlessTolnasRiftTest {
    private fun type(id: Int, name: String, op: String) =
        LocType(id, name, ops = listOf(op, null, null, null, null), collision = LocCollisionType(width = 1, length = 1, interactType = 0))

    private val rope = ZoneLoc(TolnasRift.ROPE_RIFT, LocShape.CENTREPIECE_STRAIGHT, 2, 3310, 3452, 0)
    private val ropeShown = ZoneLoc(TolnasRift.ROPE_RIFT_SHOWN, LocShape.CENTREPIECE_STRAIGHT, 2, 3310, 3448, 0)
    private val hole = ZoneLoc(HOLE, LocShape.CENTREPIECE_STRAIGHT, 1, 3306, 3450, 0)
    private val ropeUp = ZoneLoc(TolnasRift.ROPE_UP, LocShape.CENTREPIECE_STRAIGHT, 0, 3105, 5279, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(TolnasRift.TOP.x shr 6, TolnasRift.TOP.z shr 6)
                markLoaded(TolnasRift.LOBBY.x shr 6, TolnasRift.LOBBY.z shr 6)
            },
            locTypes = LocTypes(
                listOf(
                    type(TolnasRift.ROPE_RIFT, "Rift", "Enter"),
                    type(TolnasRift.ROPE_RIFT_SHOWN, "Rift", "Enter"),
                    type(HOLE, "Rift", "Enter"),
                    type(TolnasRift.ROPE_UP, "Rope", "Climb-up"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(rope, ropeShown, hole, ropeUp).forEach(::add) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready() = await("the login rebuild") { position }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.awaitAt(what: String, x: Int, z: Int) = await(what) { position?.takeIf { it == Coord(x, z, 0) } }

    @Test
    fun `Enter on the rope climbs down to the lobby and Climb-up comes back out beside Tolna`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.tele(3308, 3452)
                client.locOp(rope.id, rope.x, rope.z, TolnasRift.ENTER)
                client.awaitAt("in the lobby", TolnasRift.LOBBY.x, TolnasRift.LOBBY.z)

                client.locOp(ropeUp.id, ropeUp.x, ropeUp.z, TolnasRift.CLIMB_UP)
                client.awaitAt("back at the top", TolnasRift.TOP.x, TolnasRift.TOP.z)

                // The rope as the multiloc shows it after the quest goes down too.
                client.tele(3309, 3448)
                client.locOp(ropeShown.id, ropeShown.x, ropeShown.z, TolnasRift.ENTER)
                client.awaitAt("in the lobby again", TolnasRift.LOBBY.x, TolnasRift.LOBBY.z)
            }
        }
    }

    @Test
    fun `a hole without the rope sends the player to the rope and leaves them where they were`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.tele(3305, 3450)
                client.locOp(hole.id, hole.x, hole.z, TolnasRift.ENTER)
                client.await("the rope line") { if (client.messages().any { it.text == "[tolna_rift.rope_only]" }) Unit else null }
                client.awaitAt("still at the top", 3305, 3450)
            }
        }
    }

    private companion object {
        /** `soulbane_falloff2`, the rope multi's ropeless form, placed on its own round the rift. */
        const val HOLE = 13969
    }
}
