package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.SearchLines
import osrsps.game.content.Varrock
import osrsps.game.content.WestBankDoorLines
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
 * Varrock piece step 1 through the headless client: two straight staircases (one climbing north, one south), a ladder
 * a chest of drawers and the West Bank's locked door with its banker, placed as the 241 cache has them (types, sizes, angles and options) on open ground. The
 * rl-client script test-runner/examples/varrock-city.json does the same on the real map.
 */
class HeadlessVarrockTest {
    private fun type(id: Int, name: String, width: Int, length: Int, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = width, length = length))

    private val types = LocTypes(
        listOf(
            type(STAIRS, "Staircase", 2, 3, "Climb-up"),
            type(STAIRS_TALLER, "Staircase", 2, 3, "Climb-up"),
            type(Varrock.STRAIGHT_STAIRS_TOP, "Staircase", 2, 2, "Climb-down"),
            type(Varrock.LADDER, "Ladder", 1, 1, "Climb-up"),
            type(Varrock.LADDER_TOP, "Ladder", 1, 1, "Climb-down"),
            type(Varrock.DRAWERS, "Drawers", 1, 1, "Open"),
            type(Varrock.DRAWERS_OPEN, "Drawers", 1, 1, "Search", "Close"),
            type(Varrock.WEST_BANK_LOCKED_DOOR, "Door", 1, 1, "Knock-at"),
        ).associateBy { it.id },
    )

    private val locs = listOf(
        // The house south-west of Varrock Square: up to the north.
        ZoneLoc(STAIRS, LocShape.CENTREPIECE_STRAIGHT, 0, 3188, 3389, 0),
        ZoneLoc(Varrock.STRAIGHT_STAIRS_TOP, LocShape.CENTREPIECE_STRAIGHT, 0, 3188, 3390, 1),
        // The house by the south gate: up to the south.
        ZoneLoc(STAIRS_TALLER, LocShape.CENTREPIECE_STRAIGHT, 2, 3230, 3383, 0),
        ZoneLoc(Varrock.STRAIGHT_STAIRS_TOP, LocShape.CENTREPIECE_STRAIGHT, 2, 3230, 3383, 1),
        ZoneLoc(Varrock.LADDER, LocShape.CENTREPIECE_STRAIGHT, 0, 3214, 3410, 0),
        ZoneLoc(Varrock.LADDER_TOP, LocShape.CENTREPIECE_STRAIGHT, 0, 3214, 3410, 1),
        ZoneLoc(Varrock.DRAWERS, LocShape.CENTREPIECE_STRAIGHT, 0, 3247, 3382, 0),
        // The West Bank's locked door, on its tile's west edge, with the banker behind it to the east.
        ZoneLoc(Varrock.WEST_BANK_LOCKED_DOOR, LocShape.WALL_STRAIGHT, 0, 3186, 3446, 0),
    )

    private val doorguard = NpcType(Varrock.WEST_BANK_DOORGUARD, "Banker", ops = listOf(null, null, null, null, null), internalName = "varrock_bank_doorguard")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(3188 shr 6, 3389 shr 6)
            markLoaded(3230 shr 6, 3383 shr 6)
            markLoaded(3214 shr 6, 3410 shr 6)
            markLoaded(3186 shr 6, 3446 shr 6)
        }
        World(
            crcs, map, objTypes = ObjTypes(arrayOfNulls(1)), locTypes = types, staticLocs = StaticLocs().apply { locs.forEach(::add) },
            npcTypes = NpcTypes.of(doorguard), npcSpawns = listOf(NpcSpawn(doorguard, 3187, 3446, wander = 0, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int) =
        assertEquals(Coord(x, z, level), await("$x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } })

    @Test
    fun `straight stairs take the player off their far ends whichever way they climb`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3189, 3388, 0)
                client.locOp(STAIRS, 3188, 3389, 1)
                client.awaitAt(3189, 3392, 1)
                client.locOp(Varrock.STRAIGHT_STAIRS_TOP, 3188, 3390, 1)
                client.awaitAt(3189, 3388, 0)

                client.tele(3230, 3386, 0)
                client.locOp(STAIRS_TALLER, 3230, 3383, 1)
                client.awaitAt(3230, 3382, 1)
                client.locOp(Varrock.STRAIGHT_STAIRS_TOP, 3230, 3383, 1)
                client.awaitAt(3230, 3386, 0)
            }
        }
    }

    @Test
    fun `a ladder goes up and down on the player's tile`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3215, 3410, 0)
                client.locOp(Varrock.LADDER, 3214, 3410, 1)
                client.awaitAt(3215, 3410, 1)
                client.locOp(Varrock.LADDER_TOP, 3214, 3410, 1)
                client.awaitAt(3215, 3410, 0)
            }
        }
    }

    @Test
    fun `drawers open, have nothing in them and close`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3248, 3382, 0)
                client.locOp(Varrock.DRAWERS, 3247, 3382, 1)
                client.await("the drawers open") { server.world.locs.find(Varrock.DRAWERS_OPEN, 3247, 3382, 0) }
                val empty = DialogueText.NONE[SearchLines.DRAWERS_EMPTY]
                client.locOp(Varrock.DRAWERS_OPEN, 3247, 3382, 1)
                client.await("nothing in them") { client.messages().firstOrNull { it.text == empty } }
                client.locOp(Varrock.DRAWERS_OPEN, 3247, 3382, 2)
                client.await("the drawers shut") { server.world.locs.find(Varrock.DRAWERS, 3247, 3382, 0) }
            }
        }
    }

    @Test
    fun `Knock-at on the West Bank's locked door has the banker behind it turn the player away`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3185, 3446, 0)
                client.locOp(Varrock.WEST_BANK_LOCKED_DOOR, 3186, 3446, Varrock.KNOCK_AT)
                val knock = DialogueText.NONE[WestBankDoorLines.KNOCK]
                val view = client.await("the banker's answer") { client.dialogueView as? DialogueView.Chat }
                assertEquals(knock, view.text)
                assertEquals("Banker", view.name)
                client.continueDialogue()
                client.await("the dialogue closed") { if (client.dialogueView == null) Unit else null }
                // The door stays shut: the player is still outside it.
                assertEquals(Coord(3185, 3446, 0), client.position)
            }
        }
    }

    private companion object {

        /** fai_varrock_stairs and fai_varrock_stairs_taller. */
        const val STAIRS = 11796
        const val STAIRS_TALLER = 11797
    }
}
