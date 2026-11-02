package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.content.DraynorSewers
import osrsps.game.content.LumbridgeCastle
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Draynor piece step 10 through the headless client and the registered handlers: both sewer entrances' trapdoors and
 * ladders, placed as the 241 cache has them (types, angles and options) on open ground, and Ruantun's talk. The
 * rl-client script test-runner/examples/draynor-sewers.json does the same on the real map.
 */
class HeadlessDraynorSewersTest {
    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val types = LocTypes(
        listOf(
            type(DraynorSewers.TRAPDOOR, "Trapdoor", "Open"),
            type(DraynorSewers.TRAPDOOR_OPEN, "Trapdoor", "Climb-down"),
            type(DraynorSewers.EAST_LADDER, "Ladder", "Climb-up"),
            type(LumbridgeCastle.CELLAR_LADDER, "Ladder", "Climb-up"),
        ).associateBy { it.id },
    )

    private val locs = listOf(
        ZoneLoc(DraynorSewers.TRAPDOOR, LocShape.CENTREPIECE_STRAIGHT, 0, 3118, 3244, 0),
        ZoneLoc(DraynorSewers.EAST_LADDER, LocShape.CENTREPIECE_STRAIGHT, 2, 3118, 9643, 0),
        ZoneLoc(DraynorSewers.TRAPDOOR, LocShape.CENTREPIECE_STRAIGHT, 0, 3084, 3272, 0),
        ZoneLoc(LumbridgeCastle.CELLAR_LADDER, LocShape.CENTREPIECE_STRAIGHT, 1, 3084, 9672, 0),
        // Made up: the trapdoor and ladder types anywhere but the entrances (the exploit review's round 11).
        ZoneLoc(DraynorSewers.TRAPDOOR, LocShape.CENTREPIECE_STRAIGHT, 0, STRAY_X, 3244, 0),
        ZoneLoc(DraynorSewers.EAST_LADDER, LocShape.CENTREPIECE_STRAIGHT, 2, STRAY_X, 9643, 0),
    )

    private val ruantun = NpcType(DraynorSewers.RUANTUN, "Ruantun", ops = listOf("Talk-to", null, null, null, null), internalName = "malak")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(3118 shr 6, 3244 shr 6)
            markLoaded(3118 shr 6, 9643 shr 6)
            markLoaded(3084 shr 6, 3272 shr 6)
            markLoaded(3084 shr 6, 9672 shr 6)
            markLoaded(3112 shr 6, 9690 shr 6)
        }
        World(
            crcs, map, locTypes = types, staticLocs = StaticLocs().apply { locs.forEach(::add) },
            npcTypes = NpcTypes.of(ruantun), npcSpawns = listOf(NpcSpawn(ruantun, 3112, 9690, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int) =
        assertEquals(Coord(x, z, level), await("$x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } })

    @Test
    fun `the east trapdoor opens, goes down beside the ladder below, and the ladder climbs back up beside the trapdoor`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3119, 3244, 0)
                client.locOp(DraynorSewers.TRAPDOOR, 3118, 3244, DraynorSewers.OPEN)
                client.await("the trapdoor open") { server.world.locs.find(DraynorSewers.TRAPDOOR_OPEN, 3118, 3244, 0) }
                client.locOp(DraynorSewers.TRAPDOOR_OPEN, 3118, 3244, DraynorSewers.CLIMB)
                // The ladder is a tile south of the dungeon's 6400: east of the trapdoor is east of the ladder.
                client.awaitAt(3119, 9643, 0)
                client.tele(3118, 9644, 0)
                client.locOp(DraynorSewers.EAST_LADDER, 3118, 9643, DraynorSewers.CLIMB)
                // North of the ladder is north of the trapdoor, not on it.
                client.awaitAt(3118, 3245, 0)
            }
        }
    }

    @Test
    fun `the trapdoor and ladder types anywhere else climb nowhere`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dan").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(STRAY_X + 1, 3244, 0)
                client.locOp(DraynorSewers.TRAPDOOR, STRAY_X, 3244, DraynorSewers.OPEN)
                client.await("the trapdoor open") { server.world.locs.find(DraynorSewers.TRAPDOOR_OPEN, STRAY_X, 3244, 0) }
                client.locOp(DraynorSewers.TRAPDOOR_OPEN, STRAY_X, 3244, DraynorSewers.CLIMB)
                client.seen("[game.nothing_interesting]")
                client.awaitTicks(3)
                assertEquals(Coord(STRAY_X + 1, 3244, 0), client.position)
                client.tele(STRAY_X + 1, 9643, 0)
                client.locOp(DraynorSewers.EAST_LADDER, STRAY_X, 9643, DraynorSewers.CLIMB)
                client.await("a second nothing interesting") { client.messages().count { it.text == "[game.nothing_interesting]" }.takeIf { it == 2 } }
                client.awaitTicks(3)
                assertEquals(Coord(STRAY_X + 1, 9643, 0), client.position)
            }
        }
    }

    @Test
    fun `the west trapdoor goes down by the cellar ladder's type, which climbs back up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3085, 3272, 0)
                client.locOp(DraynorSewers.TRAPDOOR, 3084, 3272, DraynorSewers.OPEN)
                client.await("the trapdoor open") { server.world.locs.find(DraynorSewers.TRAPDOOR_OPEN, 3084, 3272, 0) }
                client.locOp(DraynorSewers.TRAPDOOR_OPEN, 3084, 3272, DraynorSewers.CLIMB)
                client.awaitAt(3085, 9672, 0)
                client.locOp(LumbridgeCastle.CELLAR_LADDER, 3084, 9672, 1)
                client.awaitAt(3085, 3272, 0)
            }
        }
    }

    /** Continues each page that follows, checking it shows key ruantun.key. */
    private fun HeadlessClient.pages(vararg keys: String) {
        for (key in keys) {
            awaitDialogue("ruantun.$key") { it is DialogueView.Chat && it.text == "[ruantun.$key]" }
            continueDialogue()
        }
    }

    @Test
    fun `Ruantun answers each of his three questions`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3111, 9690, 0)
                for ((option, keys) in listOf(
                    1 to listOf("who", "who_serve", "who_okay"),
                    2 to listOf("why", "why_dark", "why_okay"),
                    3 to listOf("anvil", "anvil_yes", "anvil_thanks"),
                )) {
                    val npc = client.await("Ruantun in view") { client.npcs().firstOrNull { it.id == DraynorSewers.RUANTUN } }
                    client.npcOp(npc.index, DraynorSewers.TALK_TO)
                    client.pages("hello", "go_away")
                    client.awaitDialogue("the menu") { it is DialogueView.Menu }
                    client.chooseOption(option)
                    client.pages(*keys.toTypedArray())
                    client.await("the talk to end") { if (client.dialogueView == null) Unit else null }
                }
            }
        }
    }

    private companion object {
        const val STRAY_X = 3110
    }
}
