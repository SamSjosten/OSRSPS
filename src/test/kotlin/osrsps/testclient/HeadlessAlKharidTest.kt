package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.content.AlKharid
import osrsps.game.content.TollGate
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Al Kharid step 1 through the headless client: the toll gate placed as the 241 map has it (multilocs 44598 and 44599
 * on the west edge of 3268, 3227 and 3268, 3228), Open from either side, a Border Guard's Talk-to, and the palace's
 * double doors. The rl-client script test-runner/examples/al-kharid-toll-gate.json does the same in the real client.
 */
class HeadlessAlKharidTest {
    private fun wall(id: Int, name: String, vararg ops: String, multiloc: Boolean = false) = LocType(
        id, name, ops = ops.toList() + List(5 - ops.size) { null },
        collision = LocCollisionType(), multiloc = multiloc,
    )

    private val types = LocTypes(
        listOf(
            // The map's multilocs have no options of their own; the player's varp 273 shows them as 44050/44051 (Open).
            wall(TollGate.LEFT, "null", multiloc = true),
            wall(TollGate.RIGHT, "null", multiloc = true),
            wall(TollGate.LEFT_OPEN, "null"),
            wall(TollGate.RIGHT_OPEN, "null"),
            wall(AlKharid.PALACE_DOOR_LEFT, "Large door", "Open"),
            wall(AlKharid.PALACE_DOOR_LEFT_OPEN, "Large door", "Close"),
            wall(AlKharid.PALACE_DOOR_RIGHT, "Large door", "Open"),
            wall(AlKharid.PALACE_DOOR_RIGHT_OPEN, "Large door", "Close"),
        ).associateBy { it.id },
    )

    private val gate = listOf(
        ZoneLoc(TollGate.LEFT, LocShape.WALL_STRAIGHT, 0, TollGate.X, TollGate.Z_LEFT, 0),
        ZoneLoc(TollGate.RIGHT, LocShape.WALL_STRAIGHT, 0, TollGate.X, TollGate.Z_RIGHT, 0),
    )

    /** The palace's west doors, as the map has them. */
    private val palace = listOf(
        ZoneLoc(AlKharid.PALACE_DOOR_LEFT, LocShape.WALL_STRAIGHT, 0, 3287, 3171, 0),
        ZoneLoc(AlKharid.PALACE_DOOR_RIGHT, LocShape.WALL_STRAIGHT, 0, 3287, 3172, 0),
    )

    private val guard = NpcType(TollGate.BORDER_GUARD_WEST, "Border Guard", ops = listOf("Talk-to", null, null, null, null), internalName = "borderguard1")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(TollGate.X shr 6, TollGate.Z_LEFT shr 6)
            markLoaded(3287 shr 6, 3171 shr 6)
        }
        World(
            crcs, map, locTypes = types, staticLocs = StaticLocs().apply { (gate + palace).forEach(::add) },
            npcTypes = NpcTypes.of(guard), npcSpawns = listOf(NpcSpawn(guard, 3267, 3226, source = "test")), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int) =
        assertEquals(Coord(x, z, 0), await("$x, $z") { position?.takeIf { it == Coord(x, z, 0) } })

    @Test
    fun `Open takes the player through the toll gate free, both ways, the leaves swinging open and shutting again`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3265, 3227)
                client.locOp(TollGate.LEFT, TollGate.X, TollGate.Z_LEFT, TollGate.OPEN)
                client.awaitAt(TollGate.X, TollGate.Z_LEFT)
                // Both leaves stand open on the Lumbridge side's tiles for a moment, then shut.
                val world = server.world
                assertEquals(TollGate.LEFT_OPEN, world.locs[TollGate.X - 1, TollGate.Z_LEFT, 0, 0]?.id)
                assertEquals(TollGate.RIGHT_OPEN, world.locs[TollGate.X - 1, TollGate.Z_RIGHT, 0, 0]?.id)
                client.await("the gate to shut") { world.locs[TollGate.X, TollGate.Z_LEFT, 0, 0]?.takeIf { it.id == TollGate.LEFT } }
                assertEquals(TollGate.RIGHT, world.locs[TollGate.X, TollGate.Z_RIGHT, 0, 0]?.id)
                assertNull(world.locs[TollGate.X - 1, TollGate.Z_LEFT, 0, 0])

                // And back west through the right leaf, from the Al Kharid side.
                client.tele(3270, 3228)
                client.locOp(TollGate.RIGHT, TollGate.X, TollGate.Z_RIGHT, TollGate.OPEN)
                client.awaitAt(TollGate.X - 1, TollGate.Z_RIGHT)
                assertEquals(0, client.messages().count { it.text.contains("coins", ignoreCase = true) })
            }
        }
    }

    @Test
    fun `a Border Guard's two lines, then the player is through the gate`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3265, 3226)
                val npc = client.await("the Border Guard in view") { client.npcs().firstOrNull { it.id == TollGate.BORDER_GUARD_WEST } }
                client.npcOp(npc.index, TollGate.TALK_TO)
                for ((name, key) in listOf("bob" to "border_guard.come_through", "Border Guard" to "border_guard.friend")) {
                    val page = client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" } as DialogueView.Chat
                    assertEquals(name, page.name)
                    client.continueDialogue()
                }
                client.awaitAt(TollGate.X, TollGate.Z_LEFT)
            }
        }
    }

    @Test
    fun `the palace's large doors open together`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3285, 3171)
                client.locOp(AlKharid.PALACE_DOOR_LEFT, 3287, 3171, 1)
                val world = server.world
                client.await("the left leaf open") { world.locs[3286, 3171, 0, 0]?.takeIf { it.id == AlKharid.PALACE_DOOR_LEFT_OPEN } }
                assertEquals(AlKharid.PALACE_DOOR_RIGHT_OPEN, world.locs[3286, 3172, 0, 0]?.id)
            }
        }
    }
}
