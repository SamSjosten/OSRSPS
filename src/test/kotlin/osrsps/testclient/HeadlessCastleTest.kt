package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.content.Climbing
import osrsps.game.content.LumbridgeCastle.CELLAR_LADDER
import osrsps.game.content.LumbridgeCastle.CLIMB
import osrsps.game.content.LumbridgeCastle.CLIMB_DOWN
import osrsps.game.content.LumbridgeCastle.KITCHEN_TRAPDOOR
import osrsps.game.content.LumbridgeCastle.LADDER_BOTTOM
import osrsps.game.content.LumbridgeCastle.LADDER_MIDDLE
import osrsps.game.content.LumbridgeCastle.LADDER_TOP
import osrsps.game.content.LumbridgeCastle.SKIP_FLOOR
import osrsps.game.content.LumbridgeCastle.STAIRS_BOTTOM
import osrsps.game.content.LumbridgeCastle.STAIRS_MIDDLE
import osrsps.game.content.LumbridgeCastle.STAIRS_TOP
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Getting about Lumbridge Castle through the headless client: its stairs, ladders, kitchen trapdoor and cellar ladder,
 * placed as the 241 cache has them (types, sizes and options too), on open ground.
 */
class HeadlessCastleTest {
    private fun type(id: Int, name: String, size: Int, vararg ops: String) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = size, length = size))

    private val types = LocTypes(
        listOf(
            type(STAIRS_BOTTOM, "Staircase", 2, "Climb-up", "Top-floor"),
            type(STAIRS_MIDDLE, "Staircase", 2, "Climb", "Climb-up", "Climb-down"),
            type(STAIRS_TOP, "Staircase", 1, "Climb-down", "Bottom-floor"),
            type(LADDER_BOTTOM, "Ladder", 1, "Climb-up"),
            type(LADDER_MIDDLE, "Ladder", 1, "Climb", "Climb-up", "Climb-down"),
            type(LADDER_TOP, "Ladder", 1, "Climb-down"),
            type(KITCHEN_TRAPDOOR, "Trapdoor", 1, "Climb-down"),
            type(CELLAR_LADDER, "Ladder", 1, "Climb-up"),
        ).associateBy { it.id },
    )

    private val locs = listOf(
        ZoneLoc(STAIRS_BOTTOM, LocShape.CENTREPIECE_STRAIGHT, 3, 3204, 3207, 0),
        ZoneLoc(STAIRS_MIDDLE, LocShape.CENTREPIECE_STRAIGHT, 3, 3204, 3207, 1),
        ZoneLoc(STAIRS_TOP, LocShape.CENTREPIECE_STRAIGHT, 3, 3205, 3208, 2),
        ZoneLoc(LADDER_BOTTOM, LocShape.CENTREPIECE_STRAIGHT, 0, 3229, 3213, 0),
        ZoneLoc(LADDER_MIDDLE, LocShape.CENTREPIECE_STRAIGHT, 2, 3229, 3213, 1),
        ZoneLoc(LADDER_TOP, LocShape.CENTREPIECE_STRAIGHT, 0, 3229, 3213, 2),
        ZoneLoc(KITCHEN_TRAPDOOR, LocShape.GROUND_DECOR, 0, 3209, 3216, 0),
        ZoneLoc(CELLAR_LADDER, LocShape.CENTREPIECE_STRAIGHT, 3, 3209, 9616, 0),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(3222 shr 6, 3218 shr 6)
            markLoaded(3209 shr 6, 9616 shr 6)
        }
        World(crcs, map, locTypes = types, staticLocs = StaticLocs().apply { locs.forEach(::add) }, cycleMillis = millis)
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int) =
        assertEquals(Coord(x, z, level), await("$x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } })

    /** Runs one tick of a paused world and waits for its updates. */
    private fun HeadlessClient.step() {
        val seen = ticksSeen
        command("step")
        await("the tick after $seen") { if (ticksSeen > seen) Unit else null }
    }

    @Test
    fun `the south-west stairs go up a floor at a time, and to the top and bottom in one`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3206, 3208, 0)
                client.locOp(STAIRS_BOTTOM, 3204, 3207, 1)
                client.awaitAt(3205, 3209, 1)

                client.locOp(STAIRS_MIDDLE, 3204, 3207, CLIMB)
                val menu = client.awaitDialogue("the stairs menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(DialogueView.Menu(Climbing.STAIRS_MENU_TITLE, listOf(Climbing.STAIRS_UP, Climbing.STAIRS_DOWN)), menu)
                client.chooseOption(1)
                client.awaitAt(3205, 3209, 2)

                client.locOp(STAIRS_TOP, 3205, 3208, SKIP_FLOOR)
                client.awaitAt(3206, 3208, 0)

                client.locOp(STAIRS_BOTTOM, 3204, 3207, SKIP_FLOOR)
                client.awaitAt(3205, 3209, 2)
            }
        }
    }

    @Test
    fun `the east tower ladders go up and down a floor, keeping the player's tile`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3228, 3213, 0)
                client.locOp(LADDER_BOTTOM, 3229, 3213, 1)
                client.awaitAt(3228, 3213, 1)

                client.locOp(LADDER_MIDDLE, 3229, 3213, CLIMB)
                val menu = client.awaitDialogue("the ladder menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(DialogueView.Menu(Climbing.LADDER_MENU_TITLE, listOf(Climbing.LADDER_UP, Climbing.LADDER_DOWN)), menu)
                client.chooseOption(1)
                client.awaitAt(3228, 3213, 2)

                client.locOp(LADDER_TOP, 3229, 3213, 1)
                client.awaitAt(3228, 3213, 1)
                client.locOp(LADDER_MIDDLE, 3229, 3213, CLIMB_DOWN)
                client.awaitAt(3228, 3213, 0)
            }
        }
    }

    @Test
    fun `the kitchen trapdoor goes down to the cellar and its ladder back up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3209, 3217, 0)
                client.locOp(KITCHEN_TRAPDOOR, 3209, 3216, 1)
                client.awaitAt(3209, 3217 + Climbing.DUNGEON_OFFSET, 0)
                client.locOp(CELLAR_LADDER, 3209, 9616, 1)
                client.awaitAt(3209, 3217, 0)
            }
        }
    }

    @Test
    fun `stairs move the player on the tick they are used, a ladder the tick after`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3206, 3208, 0)
                client.commandReply("pause") { it.startsWith("Paused after tick") }
                // Both clicks are held for the next tick, and used in it: the player is already beside each.
                client.locOp(STAIRS_BOTTOM, 3204, 3207, 1)
                client.step()
                assertEquals(Coord(3205, 3209, 1), client.position)

                client.command("tele 3228 3213 0")
                client.step()
                client.locOp(LADDER_BOTTOM, 3229, 3213, 1)
                client.step()
                assertEquals(Coord(3228, 3213, 0), client.position, "still climbing")
                client.step()
                assertEquals(Coord(3228, 3213, 1), client.position)
            }
        }
    }

    @Test
    fun `a ladder walked to waits a tick when the player arrives on the tick they use it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3227, 3213, 0)
                client.commandReply("pause") { it.startsWith("Paused after tick") }
                client.locOp(LADDER_BOTTOM, 3229, 3213, 1)
                client.step()
                assertEquals(Coord(3228, 3213, 0), client.position, "arrived beside the ladder")
                // Climbing straight away would have the player up on this tick.
                client.step()
                assertEquals(Coord(3228, 3213, 0), client.position, "climbing")
                client.step()
                assertEquals(Coord(3228, 3213, 1), client.position)
            }
        }
    }
}
