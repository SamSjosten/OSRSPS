package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.content.Climbing
import osrsps.game.content.LumbridgeCastle.CELLAR_LADDER
import osrsps.game.content.LumbridgeCastle.CHEST
import osrsps.game.content.LumbridgeCastle.CHEST_OPEN
import osrsps.game.content.LumbridgeCastle.CRATE
import osrsps.game.content.LumbridgeCastle.DRAWERS
import osrsps.game.content.LumbridgeCastle.DRAWERS_OPEN
import osrsps.game.content.LumbridgeCastle.CLIMB
import osrsps.game.content.LumbridgeCastle.CLIMB_DOWN
import osrsps.game.content.LumbridgeCastle.DOOR_OPEN
import osrsps.game.content.LumbridgeCastle.KITCHEN_DOOR
import osrsps.game.content.LumbridgeCastle.KITCHEN_TRAPDOOR
import osrsps.game.content.LumbridgeCastle.LARGE_DOOR_LEFT
import osrsps.game.content.LumbridgeCastle.LARGE_DOOR_LEFT_OPEN
import osrsps.game.content.LumbridgeCastle.LARGE_DOOR_RIGHT
import osrsps.game.content.LumbridgeCastle.LARGE_DOOR_RIGHT_OPEN
import osrsps.game.content.LumbridgeCastle.LADDER_BOTTOM
import osrsps.game.content.LumbridgeCastle.LADDER_MIDDLE
import osrsps.game.content.LumbridgeCastle.LADDER_TOP
import osrsps.game.content.LumbridgeCastle.SACKS
import osrsps.game.content.LumbridgeCastle.SKIP_FLOOR
import osrsps.game.content.LumbridgeCastle.STAIRS_BOTTOM
import osrsps.game.content.LumbridgeCastle.STAIRS_MIDDLE
import osrsps.game.content.LumbridgeCastle.STAIRS_TOP
import osrsps.game.content.Searching
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Lumbridge Castle through the headless client: its stairs, ladders, kitchen trapdoor, cellar ladder, doors and
 * searchable furniture, placed as the 241 cache has them (types, sizes and options too), on open ground.
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
            type(KITCHEN_DOOR, "Door", 1, "Open"),
            type(DOOR_OPEN, "Door", 1, "Close"),
            type(LARGE_DOOR_LEFT, "Large door", 1, "Open"),
            type(LARGE_DOOR_LEFT_OPEN, "Large door", 1, "Close"),
            type(LARGE_DOOR_RIGHT, "Large door", 1, "Open"),
            type(LARGE_DOOR_RIGHT_OPEN, "Large door", 1, "Close"),
            type(CRATE, "Crate", 1, "Search"),
            type(SACKS, "Sacks", 1, "Search"),
            type(CHEST, "Closed chest", 1, "Open"),
            LocType(CHEST_OPEN, "Open chest", ops = listOf(null, "Search", "Shut", null, null), collision = LocCollisionType()),
            type(DRAWERS, "Drawers", 1, "Open"),
            LocType(DRAWERS_OPEN, "Drawers", ops = listOf(null, "Search", "Shut", null, null), collision = LocCollisionType()),
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
        ZoneLoc(KITCHEN_DOOR, LocShape.WALL_STRAIGHT, 1, 3207, 3217, 0),
        ZoneLoc(LARGE_DOOR_LEFT, LocShape.WALL_STRAIGHT, 0, 3213, 3221, 0),
        ZoneLoc(LARGE_DOOR_RIGHT, LocShape.WALL_STRAIGHT, 0, 3213, 3222, 0),
        ZoneLoc(CHEST, LocShape.CENTREPIECE_STRAIGHT, 0, 3209, 3218, 1),
        ZoneLoc(CRATE, LocShape.CENTREPIECE_STRAIGHT, 0, 3210, 3218, 1),
        ZoneLoc(DRAWERS, LocShape.CENTREPIECE_STRAIGHT, 2, 3213, 3216, 1),
        ZoneLoc(SACKS, LocShape.CENTREPIECE_STRAIGHT, 0, 3205, 3218, 2),
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

    /**
     * Runs one tick of a paused world and waits for all of its updates. The tick ends with "Paused after tick n" and
     * then its tick end; waiting on the message, not just a tick end, keeps a late tick end from the tick that paused
     * the world from passing for this one.
     */
    private fun HeadlessClient.step() {
        val from = messages().size
        command("step")
        val done = await("the step to finish") { messages().drop(from).firstOrNull { it.text.matches(STEPPED) } }
        await("the stepped tick's end") { if (ticksSeen > done.tick) Unit else null }
    }

    private companion object {
        /** What a finished ::step says; the ::pause reply has more after the number. */
        val STEPPED = Regex("Paused after tick \\d+")
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
    fun `the kitchen door and both leaves of the large doors open and close`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("frank").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3207, 3218, 0)
                client.locOp(KITCHEN_DOOR, 3207, 3217, 1)
                val kitchenOpen = LocChange(DOOR_OPEN, LocShape.WALL_STRAIGHT, 2, Coord(3207, 3218))
                client.await("the kitchen door open") { client.locChanges().takeIf { kitchenOpen in it } }
                client.locOp(DOOR_OPEN, 3207, 3218, 1)
                client.await("the kitchen door shut") { client.locChanges().takeIf { kitchenOpen !in it } }

                client.tele(3214, 3221, 0)
                client.locOp(LARGE_DOOR_LEFT, 3213, 3221, 1)
                val leftOpen = LocChange(LARGE_DOOR_LEFT_OPEN, LocShape.WALL_STRAIGHT, 3, Coord(3212, 3221))
                val rightOpen = LocChange(LARGE_DOOR_RIGHT_OPEN, LocShape.WALL_STRAIGHT, 1, Coord(3212, 3222))
                client.await("both large doors open") { client.locChanges().takeIf { it.containsAll(listOf(leftOpen, rightOpen)) } }
                client.locOp(LARGE_DOOR_RIGHT_OPEN, 3212, 3222, 1)
                client.await("both large doors shut") { client.locChanges().takeIf { leftOpen !in it && rightOpen !in it } }
            }
        }
    }

    @Test
    fun `crates and sacks are empty, and the chest and drawers open, are empty and shut`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gina").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3208, 3218, 1)
                client.locOp(CRATE, 3210, 3218, 1)
                client.awaitMessage("the crate searched") { it == Searching.CRATE_EMPTY }

                val chestOpen = LocChange(CHEST_OPEN, LocShape.CENTREPIECE_STRAIGHT, 0, Coord(3209, 3218, 1))
                client.locOp(CHEST, 3209, 3218, 1)
                client.await("the chest open") { client.locChanges().takeIf { chestOpen in it } }
                client.locOp(CHEST_OPEN, 3209, 3218, Searching.SEARCH)
                client.awaitMessage("the chest searched") { it == Searching.CHEST_EMPTY }
                client.locOp(CHEST_OPEN, 3209, 3218, Searching.SHUT)
                client.await("the chest shut") { client.locChanges().takeIf { chestOpen !in it } }

                client.tele(3212, 3216, 1)
                val drawersOpen = LocChange(DRAWERS_OPEN, LocShape.CENTREPIECE_STRAIGHT, 2, Coord(3213, 3216, 1))
                client.locOp(DRAWERS, 3213, 3216, 1)
                client.await("the drawers open") { client.locChanges().takeIf { drawersOpen in it } }
                client.locOp(DRAWERS_OPEN, 3213, 3216, Searching.SEARCH)
                client.awaitMessage("the drawers searched") { it == Searching.DRAWERS_EMPTY }

                client.tele(3206, 3218, 2)
                client.locOp(SACKS, 3205, 3218, 1)
                client.awaitMessage("the sacks searched") { it == Searching.SACKS_EMPTY }
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
