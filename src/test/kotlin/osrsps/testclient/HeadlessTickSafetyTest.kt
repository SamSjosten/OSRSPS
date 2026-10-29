package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.content.LumbridgeCastle.LADDER_BOTTOM
import osrsps.game.content.LumbridgeCastle.LADDER_TOP
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * One player can't stop the tick for everyone. The exploit review of 29 Oct 2026 found a teleport during a ladder's
 * one-tick wait climbed to level 4, and the movement phase then threw every tick, so nobody else got another update.
 */
class HeadlessTickSafetyTest {
    private fun ladders(vararg ids: Int) = LocTypes(
        ids.map { LocType(it, "Ladder", ops = listOf(if (it == LADDER_TOP) "Climb-down" else "Climb-up", null, null, null, null), collision = LocCollisionType(width = 1, length = 1)) }
            .associateBy { it.id },
    )

    private fun server(dir: Path, ladder: ZoneLoc, cycleMillis: Long? = null) = TestServer(dir, cycleMillis = cycleMillis ?: 100) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(World.SPAWN_X shr 6, World.SPAWN_Z shr 6) },
            locTypes = ladders(ladder.id), staticLocs = StaticLocs().apply { add(ladder) }, cycleMillis = millis,
        )
    }

    @Test
    fun `a teleport during a ladder's wait doesn't change where the ladder goes`(@TempDir dir: Path) {
        // Where the 241 cache has the castle's ladder (HeadlessCastleTest). Live-length ticks, so the ::tele lands inside the wait.
        val ladder = ZoneLoc(LADDER_BOTTOM, LocShape.CENTREPIECE_STRAIGHT, 0, 3229, 3213, 0)
        server(dir, ladder, cycleMillis = 600).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.await("the login rebuild") { alice.position }
                    bob.await("the login rebuild") { bob.position }
                    alice.command("tele 3227 3213 0")
                    alice.await("at 3227, 3213") { alice.position?.takeIf { it == Coord(3227, 3213) } }
                    // One step to the tile beside the ladder, so the handler runs on the arrival tick and waits a tick.
                    alice.locOp(LADDER_BOTTOM, ladder.x, ladder.z, 1)
                    alice.await("beside the ladder", timeoutMillis = 10_000) { alice.position?.takeIf { it.x == 3228 } }
                    alice.command("tele 3222 3218 3")
                    // The climb goes from the ladder's level and the tile she stood on: level 1 beside it, not level 4.
                    val up = alice.await("the climb", timeoutMillis = 10_000) { server.world.playerNamed("alice")!!.takeIf { it.level == 1 } }
                    assertEquals(3228 to 3213, up.x to up.z)
                    val ticks = bob.ticksSeen
                    bob.await("the world still ticking", timeoutMillis = 10_000) { if (bob.ticksSeen >= ticks + 3) Unit else null }
                }
            }
        }
    }

    @Test
    fun `a ladder that would leave the map is refused, and the world keeps ticking`(@TempDir dir: Path) {
        // A ladder top on level 0 by hand: climbing down from it would be level -1.
        val ladder = ZoneLoc(LADDER_TOP, LocShape.CENTREPIECE_STRAIGHT, 0, World.SPAWN_X + 2, World.SPAWN_Z, 0)
        server(dir, ladder).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.await("the login rebuild") { alice.position }
                    bob.await("the login rebuild") { bob.position }
                    alice.locOp(LADDER_TOP, ladder.x, ladder.z, 1)
                    alice.await("beside the ladder", timeoutMillis = 10_000) { alice.position?.takeIf { it.x == World.SPAWN_X + 1 } }
                    alice.awaitTicks(5)
                    assertEquals(0, server.world.playerNamed("alice")!!.level)
                    val ticks = bob.ticksSeen
                    bob.await("the world still ticking") { if (bob.ticksSeen >= ticks + 5) Unit else null }
                }
            }
        }
    }

    @Test
    fun `teleports off the map are refused`(@TempDir dir: Path) {
        val ladder = ZoneLoc(LADDER_BOTTOM, LocShape.CENTREPIECE_STRAIGHT, 0, 3229, 3213, 0)
        server(dir, ladder).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                val player = server.world.playerNamed("alice")!!
                for ((x, z, level) in listOf(Triple(3222, 3218, 4), Triple(3222, 3218, -1), Triple(-1, 3218, 0), Triple(3222, 16384, 0))) {
                    assertFalse(server.world.teleport(player, x, z, level), "teleport to $x, $z, $level")
                }
                assertEquals(Triple(World.SPAWN_X, World.SPAWN_Z, 0), Triple(player.x, player.z, player.level))
            }
        }
    }

    @Test
    fun `a player whose part of the tick fails is kicked, and everyone else carries on`(@TempDir dir: Path) {
        val ladder = ZoneLoc(LADDER_BOTTOM, LocShape.CENTREPIECE_STRAIGHT, 0, 3229, 3213, 0)
        server(dir, ladder).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.await("the login rebuild") { alice.position }
                    bob.await("the login rebuild") { bob.position }
                    // Stands in for any state that makes one player's movement phase throw: RSProt refuses level 4.
                    server.world.playerNamed("alice")!!.level = 4
                    bob.await("alice kicked") { if (server.world.playerNamed("alice") == null) Unit else null }
                    val ticks = bob.ticksSeen
                    bob.await("the world still ticking") { if (bob.ticksSeen >= ticks + 5) Unit else null }
                    assertNull(server.world.playerNamed("alice"))
                    assertTrue(bob.position != null)
                    // Saved at the spawn, not at level 4, so she can log back in.
                    server.login("alice").use { again ->
                        assertEquals(Coord(World.SPAWN_X, World.SPAWN_Z, World.SPAWN_LEVEL), again.await("alice back") { again.position })
                    }
                }
            }
        }
    }
}
