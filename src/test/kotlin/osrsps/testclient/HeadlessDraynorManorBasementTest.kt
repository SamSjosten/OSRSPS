package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.DraynorManorBasement
import osrsps.game.content.DraynorManorBasement.Door
import osrsps.game.content.ManorBasementLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Draynor piece step 13 through the headless client and the registered handlers: the ladder down resets the levers, a
 * lever's Pull and Inspect, the doors' varbits following the levers, a locked door refused and an open one walked
 * through, the ladder back up, and the coffin. Locs and varbits are placed as the 241 cache has them (ids, shapes,
 * angles, the varbits' varps 33 and 668) on open ground; the bits within each varp are this test's own. The rl-client
 * script test-runner/examples/draynor-manor-basement.json walks the Wiki's whole solution on the real map.
 */
class HeadlessDraynorManorBasementTest {
    private fun type(id: Int, name: String, vararg ops: String?, multiloc: Boolean = false) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(), multiloc = multiloc)

    private val types = LocTypes(
        (
            listOf(
                type(DraynorManorBasement.LADDER_DOWN, "Ladder", "Climb-down"),
                type(DraynorManorBasement.LADDER_UP, "Ladder", "Climb-up"),
                type(DraynorManorBasement.COFFIN, "Coffin", "Open"),
            ) + DraynorManorBasement.LEVERS.map { type(it, "null", multiloc = true) } +
                Door.entries.map { type(it.id, "null", multiloc = true) }
            ).associateBy { it.id },
    )

    private val locs = listOf(
        ZoneLoc(DraynorManorBasement.LADDER_DOWN, LocShape.CENTREPIECE_STRAIGHT, 0, 3092, 3362, 0),
        ZoneLoc(DraynorManorBasement.LADDER_UP, LocShape.CENTREPIECE_STRAIGHT, 0, 3117, 9754, 0),
        // Lever A where the cache has it, and the doors 4to7 and 1to2.
        ZoneLoc(DraynorManorBasement.LEVERS[0], WALL_DECOR, 3, 3108, 9745, 0),
        ZoneLoc(DraynorManorBasement.LEVERS[1], WALL_DECOR, 2, 3118, 9752, 0),
        ZoneLoc(Door.D4TO7.id, LocShape.CENTREPIECE_STRAIGHT, 3, 3102, 9758, 0),
        ZoneLoc(DraynorManorBasement.COFFIN, LocShape.CENTREPIECE_STRAIGHT, 3, 3077, 9775, 0),
    )

    private val vars = VarTypes(
        6_000,
        DraynorManorBasement.LEVER_VARBITS.withIndex().associate { (i, id) -> id to VarbitType(LEVER_VARP, i, i) } +
            Door.entries.withIndex().associate { (i, door) -> door.varbit to VarbitType(DOOR_VARP, i, i) },
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(3092 shr 6, 3362 shr 6)
            markLoaded(3104 shr 6, 9754 shr 6)
            markLoaded(3077 shr 6, 9775 shr 6)
        }
        World(crcs, map, locTypes = types, staticLocs = StaticLocs().apply { locs.forEach(::add) }, varTypes = vars, cycleMillis = millis)
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int) =
        assertEquals(Coord(x, z, level), await("$x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } })

    private fun HeadlessClient.seen(line: Line, vararg values: Pair<String, Any>) = DialogueText.NONE.get(line, *values).let { text ->
        await(text) { messages().firstOrNull { it.text == text } }
    }

    private fun HeadlessClient.awaitVarp(id: Int, value: Int) = await("varp $id at $value") { if (varp(id) == value) Unit else null }

    @Test
    fun `the levers open the doors they should, an open door lets the player through, and the ladders reset them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3092, 3361, 0)
                client.locOp(DraynorManorBasement.LADDER_DOWN, 3092, 3362, DraynorManorBasement.CLIMB)
                client.awaitAt(3116, 9754, 0)
                // B, then A, down: door 4to7 opens and only it.
                client.locOp(DraynorManorBasement.LEVERS[1], 3118, 9752, DraynorManorBasement.PULL)
                client.seen(ManorBasementLines.PULL, "lever" to "B", "dir" to DialogueText.NONE[ManorBasementLines.DOWN])
                client.seen(ManorBasementLines.CLUNK)
                client.awaitVarp(LEVER_VARP, 0b10)
                client.locOp(DraynorManorBasement.LEVERS[0], 3108, 9745, DraynorManorBasement.PULL)
                client.awaitVarp(LEVER_VARP, 0b11)
                client.awaitVarp(DOOR_VARP, 1 shl Door.D4TO7.ordinal)
                client.locOp(DraynorManorBasement.LEVERS[0], 3108, 9745, DraynorManorBasement.INSPECT)
                client.seen(ManorBasementLines.INSPECT, "dir" to DialogueText.NONE[ManorBasementLines.DOWN])
                // Through door 4to7 from its south, onto its tile: the test's map has no collision there (on the real
                // map the door's tile is blocked and the player goes one past it; DraynorManorBasementTest).
                client.tele(3102, 9757, 0)
                client.locOp(Door.D4TO7.id, 3102, 9758, DraynorManorBasement.OPEN)
                client.awaitAt(3102, 9758, 0)
                // A back up shuts it: the server refuses the door even though the client asks.
                client.locOp(DraynorManorBasement.LEVERS[0], 3108, 9745, DraynorManorBasement.PULL)
                client.seen(ManorBasementLines.PULL, "lever" to "A", "dir" to DialogueText.NONE[ManorBasementLines.UP])
                client.awaitVarp(DOOR_VARP, 0)
                client.tele(3102, 9757, 0)
                client.locOp(Door.D4TO7.id, 3102, 9758, DraynorManorBasement.OPEN)
                client.seen(ManorBasementLines.LOCKED)
                client.awaitAt(3102, 9757, 0)
                // The ladder up puts every lever back.
                client.tele(3117, 9755, 0)
                client.locOp(DraynorManorBasement.LADDER_UP, 3117, 9754, DraynorManorBasement.CLIMB)
                client.awaitAt(3092, 3361, 0)
                client.awaitVarp(LEVER_VARP, 0)
                client.tele(3077, 9774, 0)
                client.locOp(DraynorManorBasement.COFFIN, 3077, 9775, DraynorManorBasement.COFFIN_OPEN)
                client.seen(ManorBasementLines.COFFIN)
            }
        }
    }

    private companion object {
        const val LEVER_VARP = 33
        const val DOOR_VARP = 668
        const val WALL_DECOR = 4
    }
}
