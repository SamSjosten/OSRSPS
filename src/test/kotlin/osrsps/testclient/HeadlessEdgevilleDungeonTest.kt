package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.content.EdgevilleDungeon
import osrsps.game.content.EdgevilleDungeonLines
import osrsps.game.content.LumbridgeCastle
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.combat.NpcDrops
import osrsps.game.combat.DropTestItems
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Edgeville piece step 10 through the headless client and the registered handlers: the trapdoor down and the ladder
 * back up, a metal gate's two leaves, the odd looking wall, the north gate's refusal, and a level 18 zombie fought with
 * the real combat and drop files. The locs are placed as the 241 cache has them (types, shapes, angles and options), on
 * open ground. The rl-client script test-runner/examples/edgeville-dungeon.json does the same on the real map.
 */
class HeadlessEdgevilleDungeonTest {
    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val types = LocTypes(
        listOf(
            type(EdgevilleDungeon.TRAPDOOR, "Trapdoor", "Open"),
            type(EdgevilleDungeon.TRAPDOOR_OPEN, "Trapdoor", "Climb-down", "Close"),
            type(LumbridgeCastle.CELLAR_LADDER, "Ladder", "Climb-up"),
            type(EdgevilleDungeon.GATE_LEFT, "Gate", "Open"),
            type(EdgevilleDungeon.GATE_RIGHT, "Gate", "Open"),
            type(EdgevilleDungeon.GATE_LEFT_OPEN, "Gate", "Close"),
            type(EdgevilleDungeon.GATE_RIGHT_OPEN, "Gate", "Close"),
            type(EdgevilleDungeon.WALL_LEFT, "Odd looking wall", "Push"),
            type(EdgevilleDungeon.WALL_RIGHT, "Odd looking wall", "Push"),
            type(EdgevilleDungeon.WALL_LEFT_OPEN, "Odd looking wall"),
            type(EdgevilleDungeon.WALL_RIGHT_OPEN, "Odd looking wall"),
            type(EdgevilleDungeon.NORTH_GATE_LEFT, "Gate", "Open"),
            type(EdgevilleDungeon.NORTH_GATE_RIGHT, "Gate", "Open"),
        ).associateBy { it.id },
    )

    private val locs = listOf(
        ZoneLoc(EdgevilleDungeon.TRAPDOOR, LocShape.GROUND_DECOR, 2, 3097, 3468, 0),
        ZoneLoc(LumbridgeCastle.CELLAR_LADDER, LocShape.CENTREPIECE_STRAIGHT, 3, 3097, 9867, 0),
        ZoneLoc(EdgevilleDungeon.GATE_RIGHT, LocShape.WALL_STRAIGHT, 2, 3103, 9909, 0),
        ZoneLoc(EdgevilleDungeon.GATE_LEFT, LocShape.WALL_STRAIGHT, 2, 3103, 9910, 0),
        ZoneLoc(EdgevilleDungeon.WALL_LEFT, LocShape.WALL_STRAIGHT, 0, 3094, 9895, 0),
        ZoneLoc(EdgevilleDungeon.WALL_RIGHT, LocShape.WALL_STRAIGHT, 0, 3094, 9896, 0),
        ZoneLoc(EdgevilleDungeon.NORTH_GATE_LEFT, LocShape.WALL_STRAIGHT, 1, 3131, 9917, 0),
        ZoneLoc(EdgevilleDungeon.NORTH_GATE_RIGHT, LocShape.WALL_STRAIGHT, 1, 3132, 9917, 0),
        // Made up: the trapdoor type somewhere else, where it climbs nowhere, and the gate type outside the middle.
        ZoneLoc(EdgevilleDungeon.TRAPDOOR, LocShape.GROUND_DECOR, 0, STRAY_X, 3468, 0),
        ZoneLoc(EdgevilleDungeon.GATE_LEFT, LocShape.WALL_STRAIGHT, 1, 3105, 9944, 0),
    )

    private val zombie = NpcType(
        ZOMBIE, "Zombie", ops = listOf(null, "Attack", null, null, null), combatLevel = 18,
        internalName = "zombie2", stats = NpcStats(attack = 13, defence = 18, strength = 13, hitpoints = 24),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(3097 shr 6, 3468 shr 6)
            for (x in listOf(3094, 3131, 3145)) markLoaded(x shr 6, 9867 shr 6)
            markLoaded(3131 shr 6, 9917 shr 6)
            markLoaded(3105 shr 6, 9944 shr 6)
        }
        val npcTypes = NpcTypes.of(zombie)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, map, random = GameRandom(10), objTypes = objTypes, locTypes = types, staticLocs = StaticLocs().apply { locs.forEach(::add) },
            npcTypes = npcTypes, npcSpawns = listOf(NpcSpawn(zombie, ZOMBIE_X, ZOMBIE_Z, wander = 0, source = "test")),
            npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), npcDrops = NpcDrops.load(npcTypes, objTypes), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int) =
        assertEquals(Coord(x, z, level), await("$x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } })

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `the trapdoor opens, climbs down beside the ladder, closes, and the ladder climbs back up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3097, 3467, 0)
                client.locOp(EdgevilleDungeon.TRAPDOOR, 3097, 3468, EdgevilleDungeon.OPEN)
                client.await("the trapdoor open") { server.world.locs.find(EdgevilleDungeon.TRAPDOOR_OPEN, 3097, 3468, 0) }
                client.locOp(EdgevilleDungeon.TRAPDOOR_OPEN, 3097, 3468, EdgevilleDungeon.CLIMB_DOWN)
                // North of the ladder, in the passage, wherever the player stood by the trapdoor.
                client.awaitAt(3097, 9868, 0)
                client.locOp(LumbridgeCastle.CELLAR_LADDER, 3097, 9867, 1)
                client.awaitAt(3097, 3468, 0)
                client.tele(3097, 3467, 0)
                client.locOp(EdgevilleDungeon.TRAPDOOR_OPEN, 3097, 3468, EdgevilleDungeon.CLOSE)
                client.await("the trapdoor shut") { server.world.locs.find(EdgevilleDungeon.TRAPDOOR, 3097, 3468, 0) }
            }
        }
    }

    @Test
    fun `the trapdoor's type anywhere else climbs nowhere`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(STRAY_X, 3467, 0)
                client.locOp(EdgevilleDungeon.TRAPDOOR, STRAY_X, 3468, EdgevilleDungeon.OPEN)
                client.await("the trapdoor open") { server.world.locs.find(EdgevilleDungeon.TRAPDOOR_OPEN, STRAY_X, 3468, 0) }
                client.locOp(EdgevilleDungeon.TRAPDOOR_OPEN, STRAY_X, 3468, EdgevilleDungeon.CLIMB_DOWN)
                client.seen("[game.nothing_interesting]")
                client.awaitTicks(3)
                assertEquals(Coord(STRAY_X, 3467, 0), client.position)
            }
        }
    }

    private fun World.typeNear(id: Int, x: Int, z: Int): ZoneLoc? =
        (x - 2..x + 2).flatMap { dx -> (z - 2..z + 2).mapNotNull { dz -> locs.find(id, dx, dz, 0) } }.firstOrNull()

    @Test
    fun `the metal gate opens both leaves together and closes them again`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3104, 9910, 0)
                client.locOp(EdgevilleDungeon.GATE_LEFT, 3103, 9910, 1)
                val world = server.world
                val leftOpen = client.await("both leaves open") {
                    val left = world.typeNear(EdgevilleDungeon.GATE_LEFT_OPEN, 3103, 9910)
                    val right = world.typeNear(EdgevilleDungeon.GATE_RIGHT_OPEN, 3103, 9909)
                    if (left != null && right != null) left else null
                }
                assertNull(world.locs.find(EdgevilleDungeon.GATE_LEFT, 3103, 9910, 0))
                assertNull(world.locs.find(EdgevilleDungeon.GATE_RIGHT, 3103, 9909, 0))
                client.locOp(EdgevilleDungeon.GATE_LEFT_OPEN, leftOpen.x, leftOpen.z, 1)
                client.await("both leaves shut") {
                    if (world.locs.find(EdgevilleDungeon.GATE_LEFT, 3103, 9910, 0) != null &&
                        world.locs.find(EdgevilleDungeon.GATE_RIGHT, 3103, 9909, 0) != null
                    ) Unit else null
                }

                // The same gate type north of the north gate stays shut.
                client.tele(3105, 9943, 0)
                client.locOp(EdgevilleDungeon.GATE_LEFT, 3105, 9944, 1)
                client.seen("[game.nothing_interesting]")
                assertNotNull(world.locs.find(EdgevilleDungeon.GATE_LEFT, 3105, 9944, 0))
            }
        }
    }

    @Test
    fun `the odd looking wall opens and lets the player through both ways, and the north gate stays shut`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dan").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3094, 9895, 0)
                client.locOp(EdgevilleDungeon.WALL_LEFT, 3094, 9895, EdgevilleDungeon.PUSH)
                client.awaitAt(3093, 9895, 0)
                client.await("the wall shut again") { server.world.locs.find(EdgevilleDungeon.WALL_LEFT, 3094, 9895, 0) }
                assertNotNull(server.world.locs.find(EdgevilleDungeon.WALL_RIGHT, 3094, 9896, 0))
                client.tele(3093, 9896, 0)
                client.locOp(EdgevilleDungeon.WALL_RIGHT, 3094, 9896, EdgevilleDungeon.PUSH)
                client.awaitAt(3094, 9896, 0)

                client.tele(3131, 9916, 0)
                client.locOp(EdgevilleDungeon.NORTH_GATE_LEFT, 3131, 9917, EdgevilleDungeon.OPEN)
                client.seen(DialogueText.NONE[EdgevilleDungeonLines.GATE_CLOSED])
                client.awaitTicks(2)
                // The gate is on its tiles' north edge: the player walks up to it and stays south of it.
                assertEquals(Coord(3131, 9917, 0), client.position)
                assertNotNull(server.world.locs.find(EdgevilleDungeon.NORTH_GATE_LEFT, 3131, 9917, 0))
            }
        }
    }

    @Test
    fun `a level 18 zombie killed drops its bones`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(ZOMBIE_X - 1, ZOMBIE_Z, 0)
                client.commandReply("god") { it.startsWith("You can") }
                client.command("setlevel attack 99")
                client.command("setlevel strength 99")
                val npc = client.await("the zombie in view") { client.npcs().firstOrNull { it.id == ZOMBIE } }
                client.npcOp(npc.index, 2)
                client.await("the zombie's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == ZOMBIE }) Unit else null }
                val bones = client.await("the bones") { client.groundObjs().firstOrNull { it.id == BONES } }
                assertEquals(1, bones.count)
            }
        }
    }

    private companion object {
        const val STRAY_X = 3110
        const val ZOMBIE = 42
        const val ZOMBIE_X = 3139
        const val ZOMBIE_Z = 9885
        const val BONES = 526
    }
}
