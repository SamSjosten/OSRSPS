package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.content.SearchLines
import osrsps.game.content.WizardsTower
import osrsps.game.content.WizardsTowerLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Draynor piece step 14 through the headless client and the registered handlers: the spiral staircase up through the
 * three floors and back down, the ladder to the basement and back, a bookcase's three boxes and the first floor's
 * boxes. Locs are placed as the 241 cache has them (types, shapes, angles, sizes and options) on open ground. The
 * rl-client script test-runner/examples/wizards-tower.json does the same on the real map, with a wizard's fight and the
 * caged demon.
 */
class HeadlessWizardsTowerTest {
    private fun type(id: Int, name: String, vararg ops: String?, width: Int = 1, length: Int = 1) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = width, length = length))

    private val types = LocTypes(
        listOf(
            type(WizardsTower.STAIRS_BOTTOM, "Staircase", "Climb-up", width = 2, length = 2),
            type(WizardsTower.STAIRS_MIDDLE, "Staircase", "Climb", "Climb-up", "Climb-down", width = 2, length = 2),
            type(WizardsTower.STAIRS_TOP, "Staircase", "Climb-down"),
            type(WizardsTower.LADDER_DOWN, "Ladder", "Climb-down"),
            type(WizardsTower.LADDER_UP, "Ladder", "Climb-up"),
            type(WizardsTower.BOOKCASES[0], "Bookcase", "Search", length = 2),
            type(WizardsTower.BOXES, "Boxes", "Search"),
        ).associateBy { it.id },
    )

    private val locs = listOf(
        ZoneLoc(WizardsTower.STAIRS_BOTTOM, LocShape.CENTREPIECE_STRAIGHT, 3, 3103, 3159, 0),
        ZoneLoc(WizardsTower.STAIRS_MIDDLE, LocShape.CENTREPIECE_STRAIGHT, 3, 3103, 3159, 1),
        ZoneLoc(WizardsTower.STAIRS_TOP, LocShape.CENTREPIECE_STRAIGHT, 3, 3104, 3160, 2),
        ZoneLoc(WizardsTower.LADDER_DOWN, LocShape.CENTREPIECE_STRAIGHT, 3, 3104, 3162, 0),
        ZoneLoc(WizardsTower.LADDER_UP, LocShape.CENTREPIECE_STRAIGHT, 3, 3103, 9576, 0),
        ZoneLoc(WizardsTower.BOOKCASES[0], LocShape.CENTREPIECE_STRAIGHT, 2, 3113, 3158, 0),
        ZoneLoc(WizardsTower.BOXES, LocShape.CENTREPIECE_STRAIGHT, 1, 3109, 3157, 1),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(3104 shr 6, 3160 shr 6)
            markLoaded(3103 shr 6, 9576 shr 6)
        }
        World(crcs, map, locTypes = types, staticLocs = StaticLocs().apply { locs.forEach(::add) }, cycleMillis = millis)
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int) =
        assertEquals(Coord(x, z, level), await("$x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } })

    @Test
    fun `the spiral staircase climbs to the second floor and back down, with the middle's menu`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3105, 3161, 0)
                client.locOp(WizardsTower.STAIRS_BOTTOM, 3103, 3159, WizardsTower.CLIMB)
                client.awaitAt(3104, 3161, 1)
                client.locOp(WizardsTower.STAIRS_MIDDLE, 3103, 3159, WizardsTower.CLIMB_UP)
                client.awaitAt(3104, 3161, 2)
                client.locOp(WizardsTower.STAIRS_TOP, 3104, 3160, WizardsTower.CLIMB)
                client.awaitAt(3105, 3160, 1)
                client.locOp(WizardsTower.STAIRS_MIDDLE, 3103, 3159, WizardsTower.CLIMB_DOWN)
                client.awaitAt(3105, 3160, 0)
                // The middle's Climb asks which way.
                client.tele(3105, 3160, 1)
                client.locOp(WizardsTower.STAIRS_MIDDLE, 3103, 3159, WizardsTower.CLIMB)
                client.awaitDialogue("the climb menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.awaitAt(3104, 3161, 2)
            }
        }
    }

    @Test
    fun `the ladder goes down to the basement and back up beside the ladder`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3105, 3162, 0)
                client.locOp(WizardsTower.LADDER_DOWN, 3104, 3162, WizardsTower.CLIMB)
                client.awaitAt(WizardsTower.LADDER_DOWN_TO.x, WizardsTower.LADDER_DOWN_TO.z, 0)
                client.locOp(WizardsTower.LADDER_UP, 3103, 9576, WizardsTower.CLIMB)
                client.awaitAt(WizardsTower.LADDER_UP_TO.x, WizardsTower.LADDER_UP_TO.z, 0)
            }
        }
    }

    @Test
    fun `a bookcase shows its opening box, one of the eight books and the last box, and the boxes hold nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3112, 3158, 0)
                client.locOp(WizardsTower.BOOKCASES[0], 3113, 3158, WizardsTower.SEARCH)
                val books = WizardsTowerLines.BOOKS.map { DialogueText.NONE[it] }.toSet()
                client.awaitDialogue("the opening box") { it is DialogueView.Message && it.text == DialogueText.NONE[WizardsTowerLines.BOOKCASE] }
                client.continueDialogue()
                val book = client.awaitDialogue("a book") { it is DialogueView.Message && it.text in books } as DialogueView.Message
                assertTrue(book.text in books)
                client.continueDialogue()
                client.awaitDialogue("the last box") { it is DialogueView.Message && it.text == DialogueText.NONE[WizardsTowerLines.INTERESTING] }
                client.continueDialogue()

                client.tele(3110, 3157, 1)
                client.locOp(WizardsTower.BOXES, 3109, 3157, WizardsTower.SEARCH)
                val text = DialogueText.NONE[SearchLines.BOXES_EMPTY]
                client.await(text) { client.messages().firstOrNull { it.text == text } }
            }
        }
    }
}
