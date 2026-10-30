package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.AxeLogs
import osrsps.game.content.Bankers
import osrsps.game.content.DraynorSearchLines
import osrsps.game.content.DraynorVillage
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Draynor piece step 1 through the headless client: the pig pen's gate, Morgan's and the Wise Old Man's stairs and
 * Morgan's garlic cupboard, placed as the 241 cache has them (types, sizes, angles and options), on open ground. The
 * rl-client script test-runner/examples/draynor-village.json does the same on the real map.
 */
class HeadlessDraynorTest {
    private fun type(id: Int, name: String, width: Int, length: Int, vararg ops: String?, internal: String? = null) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = width, length = length), internalName = internal)

    private val types = LocTypes(
        listOf(
            type(GATE_L, "Gate", 1, 1, "Open", internal = "farming_fencegate_l"),
            type(GATE_R, "Gate", 1, 1, "Open", internal = "farming_fencegate_r"),
            type(GATE_L_OPEN, "Gate", 1, 1, "Close", internal = "farming_openfencegate_l"),
            type(GATE_R_OPEN, "Gate", 1, 1, "Close", internal = "farming_openfencegate_r"),
            type(DraynorVillage.MORGAN_STAIRS, "Staircase", 2, 3, "Climb-up"),
            type(DraynorVillage.MORGAN_STAIRS_TOP, "Staircase", 2, 2, "Climb-down"),
            type(DraynorVillage.NARROW_STAIRS, "Staircase", 1, 3, "Climb-up"),
            type(DraynorVillage.NARROW_STAIRS_TOP, "Staircase", 1, 2, "Climb-down"),
            type(DraynorVillage.GARLIC_CUPBOARD, "Cupboard", 1, 2, "Open"),
            type(DraynorVillage.GARLIC_CUPBOARD_OPEN, "Cupboard", 1, 2, "Search", "Close"),
            type(AxeLogs.WITH_AXE, "Logs", 1, 1, "Take-axe"),
            type(AxeLogs.WITHOUT_AXE, "Logs", 1, 1),
        ).associateBy { it.id },
    )

    private val locs = listOf(
        ZoneLoc(GATE_L, LocShape.WALL_STRAIGHT, 1, 3077, 3258, 0),
        ZoneLoc(GATE_R, LocShape.WALL_STRAIGHT, 1, 3078, 3258, 0),
        ZoneLoc(DraynorVillage.MORGAN_STAIRS, LocShape.CENTREPIECE_STRAIGHT, 1, 3099, 3266, 0),
        ZoneLoc(DraynorVillage.MORGAN_STAIRS_TOP, LocShape.CENTREPIECE_STRAIGHT, 1, 3100, 3266, 1),
        ZoneLoc(DraynorVillage.NARROW_STAIRS, LocShape.CENTREPIECE_STRAIGHT, 3, 3090, 3251, 0),
        ZoneLoc(DraynorVillage.NARROW_STAIRS_TOP, LocShape.CENTREPIECE_STRAIGHT, 3, 3091, 3251, 1),
        ZoneLoc(DraynorVillage.GARLIC_CUPBOARD, LocShape.CENTREPIECE_STRAIGHT, 0, 3096, 3269, 1),
        ZoneLoc(AxeLogs.WITH_AXE, LocShape.CENTREPIECE_STRAIGHT, 2, 3097, 3239, 0),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(DraynorVillage.GARLIC + 1)
        objs[AxeLogs.BRONZE_AXE] = ObjType(AxeLogs.BRONZE_AXE, "Bronze axe", iops = listOf(null, null, null, null, "Drop"))
        objs[DraynorVillage.GARLIC] = ObjType(DraynorVillage.GARLIC, "Garlic", iops = listOf(null, null, null, null, "Drop"))
        val map = CollisionMap().apply {
            markLoaded(3077 shr 6, 3258 shr 6)
            markLoaded(3096 shr 6, 3269 shr 6)
            markLoaded(3097 shr 6, 3239 shr 6)
        }
        World(crcs, map, objTypes = ObjTypes(objs), locTypes = types, staticLocs = StaticLocs().apply { locs.forEach(::add) }, cycleMillis = millis)
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int) =
        assertEquals(Coord(x, z, level), await("$x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } })

    @Test
    fun `the pig pen's gate opens both leaves and shuts them again`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3077, 3257, 0)
                client.locOp(GATE_R, 3078, 3258, 1)
                client.await("both leaves open") {
                    val world = server.world
                    if (world.locs.find(GATE_L_OPEN, 3077, 3259, 0) != null && world.locs.find(GATE_R_OPEN, 3077, 3260, 0) != null) Unit else null
                }
                assertNull(server.world.locs.find(GATE_L, 3077, 3258, 0))
                client.locOp(GATE_L_OPEN, 3077, 3259, 1)
                client.await("both leaves shut") {
                    val world = server.world
                    if (world.locs.find(GATE_L, 3077, 3258, 0) != null && world.locs.find(GATE_R, 3078, 3258, 0) != null) Unit else null
                }
            }
        }
    }

    @Test
    fun `the straight stairs take the player off their far ends`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3098, 3267, 0)
                client.locOp(DraynorVillage.MORGAN_STAIRS, 3099, 3266, 1)
                client.awaitAt(3102, 3266, 1)
                client.locOp(DraynorVillage.MORGAN_STAIRS_TOP, 3100, 3266, 1)
                client.awaitAt(3098, 3266, 0)

                client.tele(3089, 3250, 0)
                client.locOp(DraynorVillage.NARROW_STAIRS, 3090, 3251, 1)
                client.awaitAt(3093, 3251, 1)
                client.locOp(DraynorVillage.NARROW_STAIRS_TOP, 3091, 3251, 1)
                client.awaitAt(3089, 3251, 0)
            }
        }
    }

    @Test
    fun `Morgan's cupboard opens, gives garlic each search and shuts`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3097, 3269, 1)
                client.locOp(DraynorVillage.GARLIC_CUPBOARD, 3096, 3269, 1)
                client.await("the cupboard open") { server.world.locs.find(DraynorVillage.GARLIC_CUPBOARD_OPEN, 3096, 3269, 1) }
                val found = DialogueText.NONE[DraynorSearchLines.GARLIC]
                for (n in 1..2) {
                    val before = client.messages().count { it.text == found }
                    client.locOp(DraynorVillage.GARLIC_CUPBOARD_OPEN, 3096, 3269, 1)
                    client.await("garlic $n") { if (client.messages().count { it.text == found } > before) Unit else null }
                    client.await("$n garlic in the backpack") {
                        if (client.inventory(BACKPACK).count { it?.id == DraynorVillage.GARLIC } == n) Unit else null
                    }
                }
                client.locOp(DraynorVillage.GARLIC_CUPBOARD_OPEN, 3096, 3269, 2)
                client.await("the cupboard shut") { server.world.locs.find(DraynorVillage.GARLIC_CUPBOARD, 3096, 3269, 1) }
            }
        }
    }

    @Test
    fun `Take-axe on the logs by the willows gives a bronze axe and leaves plain logs until it is back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dan").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3097, 3238, 0)
                client.locOp(AxeLogs.WITH_AXE, 3097, 3239, AxeLogs.TAKE_AXE)
                client.await("a bronze axe") { if (client.inventory(BACKPACK).count { it?.id == AxeLogs.BRONZE_AXE } == 1) Unit else null }
                assertNotNull(server.world.locs.find(AxeLogs.WITHOUT_AXE, 3097, 3239, 0))
                client.await("the axe back on the logs", timeoutMillis = 60_000) { server.world.locs.find(AxeLogs.WITH_AXE, 3097, 3239, 0) }
            }
        }
    }

    @Test
    fun `Draynor's bankers talk and open the bank as the castle's does`(@TempDir dir: Path) {
        server(dir).use { server ->
            for (banker in Bankers.DRAYNOR) {
                assertNotNull(server.world.handlers.find(Op(OpTarget.NPC, banker, Bankers.TALK_TO)), "talk to $banker")
                assertNotNull(server.world.handlers.find(Op(OpTarget.NPC, banker, Bankers.BANK)), "bank with $banker")
            }
        }
    }

    private companion object {
        const val GATE_L = 1561
        const val GATE_R = 1562
        const val GATE_L_OPEN = 1563
        const val GATE_R_OPEN = 1564
        const val BACKPACK = 93
    }
}
