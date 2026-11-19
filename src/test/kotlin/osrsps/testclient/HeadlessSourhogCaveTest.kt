package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.SourhogCave
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Post-quest content step 8 through the headless client: the strange hole climbed down into the Sourhog Cave and its
 * exit rope climbed back up. The rl-client script test-runner/examples/sourhog-cave.json does the same in the real
 * client, and crosses the blockage.
 */
class HeadlessSourhogCaveTest {
    private val hole = ZoneLoc(SourhogCave.HOLE, LocShape.CENTREPIECE_STRAIGHT, 0, 3150, 3347, 0)
    private val rope = ZoneLoc(SourhogCave.ROPE, LocShape.CENTREPIECE_STRAIGHT, 0, 3157, 9714, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3150 shr 6, 3347 shr 6); markLoaded(3157 shr 6, 9714 shr 6) },
            varTypes = VarTypes(6_000, mapOf(HOLE_VARBIT to VarbitType(HOLE_VARP, 0, 5)), 21_000),
            locTypes = LocTypes(
                listOf(
                    LocType(
                        SourhogCave.HOLE, "null", List(5) { null }, LocCollisionType(width = 2, length = 2), multiloc = true,
                        multiVarbit = HOLE_VARBIT, multiLocs = listOf(HOLE_ROPED, HOLE_ROPED),
                    ),
                    LocType(HOLE_ROPED, "Strange hole", listOf("Climb-down", null, null, null, null), LocCollisionType(width = 2, length = 2)),
                    LocType(SourhogCave.ROPE, "Rope", listOf("Climb-up", null, null, null, null), LocCollisionType(width = 1, length = 1)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { add(hole); add(rope) },
            random = GameRandom(3),
            cycleMillis = millis,
        )
    }

    @Test
    fun `the hole goes down below the rope, and the rope comes back up beside the hole`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 3150 3345 0")
                client.await("the teleport") { client.position?.takeIf { it == Coord(3150, 3345, 0) } }
                client.locOp(hole.id, hole.x, hole.z, SourhogCave.CLIMB)
                client.await("the cave") { client.position?.takeIf { it == Coord(SourhogCave.CAVE.x, SourhogCave.CAVE.z, 0) } }
                client.locOp(rope.id, rope.x, rope.z, SourhogCave.CLIMB)
                client.await("the surface") { client.position?.takeIf { it == Coord(SourhogCave.SURFACE.x, SourhogCave.SURFACE.z, 0) } }
            }
        }
    }

    private companion object {
        const val HOLE_VARBIT = 10582
        const val HOLE_VARP = 2748
        const val HOLE_ROPED = 40309
    }
}
