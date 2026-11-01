package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjSpawn
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.DraynorManor
import osrsps.game.content.ManorGroundsLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Draynor piece step 12a through the headless client and the registered handlers: the manor's back door, closet and
 * whistle-room doors, both staircases, the bookcase passage and its lever, and the closet's rubber tube, placed as the
 * 241 cache has them (types, shapes, angles, sizes and options) on open ground. The rl-client script
 * test-runner/examples/draynor-manor-inside.json does the same on the real map.
 */
class HeadlessDraynorManorTest {
    private fun type(id: Int, name: String, vararg ops: String?, width: Int = 1, length: Int = 1) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = width, length = length))

    private val types = LocTypes(
        listOf(
            type(DraynorManor.BACK_DOOR, "Door", "Open"),
            type(DraynorManor.BACK_DOOR_OPEN, "null"),
            type(DraynorManor.CLOSET_DOOR, "Door", "Open"),
            type(DraynorManor.CLOSET_DOOR_OPEN, "null"),
            type(DraynorManor.WHISTLE_DOOR, "Door", "Open"),
            type(DraynorManor.WHISTLE_DOOR_OPEN, "Door", "Close"),
            type(DraynorManor.STAIRS_UP, "Staircase", "Climb-up", width = 2, length = 4),
            type(DraynorManor.STAIRS_DOWN, "Staircase", "Climb-down", width = 2, length = 2),
            type(DraynorManor.SPIRAL_UP, "Staircase", "Climb-up", width = 2, length = 2),
            type(DraynorManor.SPIRAL_DOWN, "Staircase", "Climb-down"),
            type(DraynorManor.BOOKCASE_L, "Bookcase", "Search"),
            type(DraynorManor.BOOKCASE_R, "Bookcase", "Search"),
            type(DraynorManor.BOOKCASE_OPEN, "Bookcase"),
            type(DraynorManor.INVISIBLE_WALL, "null"),
            type(DraynorManor.LEVER, "Lever", "Pull"),
            type(DraynorManor.LEVER_DOWN, "null"),
        ).associateBy { it.id },
    )

    private val locs = listOf(
        ZoneLoc(DraynorManor.BACK_DOOR, LocShape.WALL_STRAIGHT, 3, 3123, 3361, 0),
        ZoneLoc(DraynorManor.CLOSET_DOOR, LocShape.WALL_STRAIGHT, 2, 3107, 3367, 0),
        ZoneLoc(DraynorManor.WHISTLE_DOOR, LocShape.WALL_STRAIGHT, 1, 3106, 3361, 2),
        ZoneLoc(DraynorManor.STAIRS_UP, LocShape.CENTREPIECE_STRAIGHT, 0, 3108, 3362, 0),
        ZoneLoc(DraynorManor.STAIRS_DOWN, LocShape.CENTREPIECE_STRAIGHT, 0, 3108, 3364, 1),
        ZoneLoc(DraynorManor.SPIRAL_UP, LocShape.CENTREPIECE_STRAIGHT, 3, 3104, 3362, 1),
        ZoneLoc(DraynorManor.SPIRAL_DOWN, LocShape.CENTREPIECE_STRAIGHT, 3, 3105, 3363, 2),
        ZoneLoc(DraynorManor.BOOKCASE_L, LocShape.CENTREPIECE_STRAIGHT, 0, 3097, 3358, 0),
        ZoneLoc(DraynorManor.BOOKCASE_R, LocShape.CENTREPIECE_STRAIGHT, 0, 3097, 3359, 0),
        ZoneLoc(DraynorManor.LEVER, WALL_DECOR, 2, 3096, 3357, 0),
    )

    private val skeleton = NpcType(DraynorManor.SKELETON, "Skeleton", ops = listOf(null, "Attack", null, null, null))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(DraynorManor.RUBBER_TUBE + 1)
        objs[DraynorManor.CLOSET_KEY] = ObjType(DraynorManor.CLOSET_KEY, "Key", iops = listOf(null, null, null, null, "Drop"))
        objs[DraynorManor.RUBBER_TUBE] = ObjType(DraynorManor.RUBBER_TUBE, "Rubber tube", iops = listOf(null, null, null, null, "Drop"))
        val map = CollisionMap().apply { markLoaded(3108 shr 6, 3360 shr 6) }
        World(
            crcs, map, objTypes = ObjTypes(objs), locTypes = types, staticLocs = StaticLocs().apply { locs.forEach(::add) },
            npcTypes = NpcTypes.of(skeleton), npcSpawns = listOf(NpcSpawn(skeleton, 3110, 3367, source = "test")),
            cycleMillis = millis,
        ).also { it.groundObjs.addSpawn(ObjSpawn(DraynorManor.RUBBER_TUBE, 1, 3111, 3367, 0)) }
    }

    private val realSetOn = DraynorManor.setOn

    @AfterTest
    fun restore() {
        DraynorManor.setOn = realSetOn
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int) =
        assertEquals(Coord(x, z, level), await("$x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } })

    private fun HeadlessClient.seen(line: osrsps.game.dialogue.Line) = DialogueText.NONE[line].let { text ->
        await(text) { messages().firstOrNull { it.text == text } }
    }

    @Test
    fun `the back door lets the player out of the east room and won't open from outside`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3123, 3362, 0)
                client.locOp(DraynorManor.BACK_DOOR, 3123, 3361, DraynorManor.OPEN)
                client.seen(ManorGroundsLines.BACK_DOOR_LOCKED)
                client.tele(3123, 3360, 0)
                client.locOp(DraynorManor.BACK_DOOR, 3123, 3361, DraynorManor.OPEN)
                client.awaitAt(3123, 3361, 0)
                // It shuts again behind them.
                client.await("the door shut") { server.world.locs.find(DraynorManor.BACK_DOOR, 3123, 3361, 0) }
            }
        }
    }

    @Test
    fun `the main staircase and the spiral stairs take the player up to the top floor and back down`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3108, 3361, 0)
                client.locOp(DraynorManor.STAIRS_UP, 3108, 3362, DraynorManor.CLIMB)
                client.awaitAt(3108, 3366, 1)
                client.locOp(DraynorManor.STAIRS_DOWN, 3108, 3364, DraynorManor.CLIMB)
                client.awaitAt(3108, 3361, 0)
                client.tele(3104, 3361, 1)
                client.locOp(DraynorManor.SPIRAL_UP, 3104, 3362, DraynorManor.CLIMB)
                client.awaitAt(3105, 3364, 2)
                client.locOp(DraynorManor.SPIRAL_DOWN, 3105, 3363, DraynorManor.CLIMB)
                client.awaitAt(3106, 3363, 1)
            }
        }
    }

    @Test
    fun `the bookcases open into the room behind from the east, and its lever lets the player back out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3098, 3358, 0)
                client.locOp(DraynorManor.BOOKCASE_L, 3097, 3358, DraynorManor.SEARCH)
                client.seen(ManorGroundsLines.SECRET_DOOR)
                client.awaitAt(3096, 3358, 0)
                assertNotNull(server.world.locs.find(DraynorManor.INVISIBLE_WALL, 3097, 3358, 0))
                assertNotNull(server.world.locs.find(DraynorManor.BOOKCASE_OPEN, 3097, 3357, 0))
                assertNotNull(server.world.locs.find(DraynorManor.BOOKCASE_OPEN, 3097, 3360, 0))
                // From inside, the bookcases do nothing.
                client.await("the bookcases shut") { server.world.locs.find(DraynorManor.BOOKCASE_R, 3097, 3359, 0) }
                client.locOp(DraynorManor.BOOKCASE_R, 3097, 3359, DraynorManor.SEARCH)
                Thread.sleep(400) // four ticks at the test server's 100 ms
                assertEquals(3096, client.position!!.x)

                client.tele(3096, 3357, 0)
                client.locOp(DraynorManor.LEVER, 3096, 3357, DraynorManor.PULL)
                client.seen(ManorGroundsLines.LEVER_OPENS)
                client.awaitAt(3098, 3358, 0)
            }
        }
    }

    @Test
    fun `the closet is locked, its key lets the player through, and taking the tube sets the skeleton on them`(@TempDir dir: Path) {
        val setOn = CopyOnWriteArrayList<String>()
        DraynorManor.setOn = { _, npc, player -> setOn += "${npc.type.id} ${player.username}" }
        server(dir).use { server ->
            server.login("dan").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3107, 3367, 0)
                client.locOp(DraynorManor.CLOSET_DOOR, 3107, 3367, DraynorManor.OPEN)
                client.seen(ManorGroundsLines.CLOSET_LOCKED)
                client.command("item ${DraynorManor.CLOSET_KEY}")
                val slot = client.await("the key") { client.inventory(BACKPACK).indexOfFirst { it?.id == DraynorManor.CLOSET_KEY }.takeIf { it >= 0 } }
                client.locTarget(DraynorManor.CLOSET_DOOR, 3107, 3367, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, DraynorManor.CLOSET_KEY)
                client.seen(ManorGroundsLines.CLOSET_UNLOCK)
                client.seen(ManorGroundsLines.CLOSET_THROUGH)
                client.awaitAt(3108, 3367, 0)

                client.objOp(DraynorManor.RUBBER_TUBE, 3111, 3367, TAKE)
                client.await("the tube") { if (client.inventory(BACKPACK).any { it?.id == DraynorManor.RUBBER_TUBE }) Unit else null }
                client.await("the skeleton set on") { setOn.firstOrNull() }
                assertEquals(listOf("${DraynorManor.SKELETON} dan"), setOn.toList())
            }
        }
    }

    @Test
    fun `the top floor's south room door opens to let the player through either way`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3106, 3362, 2)
                client.locOp(DraynorManor.WHISTLE_DOOR, 3106, 3361, DraynorManor.OPEN)
                client.awaitAt(3106, 3361, 2)
                client.await("the door shut") { server.world.locs.find(DraynorManor.WHISTLE_DOOR, 3106, 3361, 2) }
                client.locOp(DraynorManor.WHISTLE_DOOR, 3106, 3361, DraynorManor.OPEN)
                client.awaitAt(3106, 3362, 2)
                assertTrue(client.position!!.level == 2)
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val TAKE = 3
        /** Shape 4, a straight wall decoration, as the lever is. */
        const val WALL_DECOR = 4
    }
}
