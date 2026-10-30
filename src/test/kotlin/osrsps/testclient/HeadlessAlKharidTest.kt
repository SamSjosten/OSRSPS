package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.content.AlKharid
import osrsps.game.content.Bankers
import osrsps.game.content.TollGate
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollision
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
            wall(AlKharid.CURTAIN, "Curtain", "Open"),
            wall(AlKharid.CURTAIN_OPEN, "Curtain", "Close"),
            LocType(BOOTH, "Bank booth", ops = listOf(null, "Bank", "Collect", null, null), collision = LocCollisionType(blocksProjectile = false)),
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
        // The closed curtain upstairs in the palace.
        ZoneLoc(AlKharid.CURTAIN, LocShape.WALL_STRAIGHT, 0, 3288, 3173, 1),
    )

    /** One of the bank's booths, with a banker behind it, as the map and 2004Scape's spawns have them. */
    private val booth = ZoneLoc(BOOTH, LocShape.CENTREPIECE_STRAIGHT, 1, 3268, 3166, 0)

    private val banker = NpcType(Bankers.AL_KHARID_BANKER_2, "Banker", ops = listOf("Talk-to", null, "Bank", "Collect", null), internalName = "kharidbanker2")

    private val guard = NpcType(TollGate.BORDER_GUARD_WEST, "Border Guard", ops = listOf("Talk-to", null, null, null, null), internalName = "borderguard1")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(TollGate.X shr 6, TollGate.Z_LEFT shr 6)
            markLoaded(3287 shr 6, 3171 shr 6)
            markLoaded(3268 shr 6, 3166 shr 6)
            // The booth blocks walking but not reaching across, as the map's booths do; static locs add no collision here.
            LocCollision(this).addLoc(LocCollisionType(blocksProjectile = false), 3268, 3166, 0, LocShape.CENTREPIECE_STRAIGHT, 1)
        }
        World(
            crcs, map, locTypes = types, staticLocs = StaticLocs().apply { (gate + palace + booth).forEach(::add) },
            npcTypes = NpcTypes.of(guard, banker),
            npcSpawns = listOf(NpcSpawn(guard, 3267, 3226, source = "test"), NpcSpawn(banker, 3267, 3166, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.awaitBank() =
        await("the bank") { openInterfaces().takeIf { BankScreen.MAIN in it } }

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

    @Test
    fun `a curtain opens and closes as a door does`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gina").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 3289 3173 1")
                client.await("the teleport upstairs") { client.position?.takeIf { it == Coord(3289, 3173, 1) } }
                client.locOp(AlKharid.CURTAIN, 3288, 3173, 1)
                val world = server.world
                client.await("the curtain open") { world.locs[3287, 3173, 1, 0]?.takeIf { it.id == AlKharid.CURTAIN_OPEN } }
                client.locOp(AlKharid.CURTAIN_OPEN, 3287, 3173, 1)
                client.await("the curtain shut") { world.locs[3288, 3173, 1, 0]?.takeIf { it.id == AlKharid.CURTAIN } }
                assertNull(world.locs[3287, 3173, 1, 0])
            }
        }
    }

    @Test
    fun `an Al Kharid banker banks across the booth, by Bank and through the banker's talk`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3269, 3166)
                val npc = client.await("the banker in view") { client.npcs().firstOrNull { it.id == Bankers.AL_KHARID_BANKER_2 } }
                client.npcOp(npc.index, Bankers.BANK)
                client.awaitBank()
                // The player stays on their side of the booth.
                assertEquals(Coord(3269, 3166, 0), client.position)
            }
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3269, 3166)
                val npc = client.await("the banker in view") { client.npcs().firstOrNull { it.id == Bankers.AL_KHARID_BANKER_2 } }
                client.npcOp(npc.index, Bankers.TALK_TO)
                client.awaitDialogue("banker.greeting") { it is DialogueView.Chat && it.text == "[banker.greeting]" }
                client.continueDialogue()
                client.awaitDialogue("the banker's menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.awaitBank()
            }
        }
    }

    @Test
    fun `a bank booth's Bank opens the bank`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("frank").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3269, 3166)
                client.locOp(BOOTH, 3268, 3166, 2)
                client.awaitBank()
            }
        }
    }

    private companion object {
        /** bankbooth: the booths the 241 map places in Al Kharid's bank (Bank is op2, Collect op3). */
        const val BOOTH = 10355
    }
}
