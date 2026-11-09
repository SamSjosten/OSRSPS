package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.LumbridgeSwampCaves
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.LightSources
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Misthalin piece 7 step 2b through the headless client: the dark hole wants a rope once, the climbing rope climbs back
 * up, the dark warns then bites a player with no light, one beside the rope climbs out instead, the gas flares and
 * explodes a naked flame, and a fall from a stepping stone puts the lights out. The rl-client script
 * test-runner/examples/swamp-caves.json does the same in the real caves.
 */
class HeadlessSwampCavesTest {
    private fun type(id: Int, name: String, op: String, width: Int = 1) =
        LocType(id, name, ops = listOf(op, null, null, null, null), collision = LocCollisionType(width = width, length = width, interactType = 0))

    private val hole = ZoneLoc(LumbridgeSwampCaves.DARK_HOLE, LocShape.CENTREPIECE_STRAIGHT, 0, 3169, 3172, 0)
    private val rope = ZoneLoc(LumbridgeSwampCaves.CLIMBING_ROPE, LocShape.CENTREPIECE_STRAIGHT, 0, 3169, 9572, 0)
    private val stone = ZoneLoc(STONE_NORTH, LocShape.GROUND_DECOR, 1, 3206, 9572, 0)

    private val objs = listOf(
        ObjType(LumbridgeSwampCaves.ROPE, "Rope", tradeable = true),
        ObjType(LightSources.CANDLE, "Candle", tradeable = true),
        ObjType(LightSources.LIT_CANDLE, "Candle", iops = listOf(LightSources.EXTINGUISH, null, null, null, null)),
        ObjType(LightSources.OIL_LANTERN, "Oil lantern", tradeable = true),
        ObjType(LightSources.OIL_LANTERN_LIT, "Oil lantern", iops = listOf(null, LightSources.EXTINGUISH, null, null, null)),
    )

    // Fast ticks: the dark takes 30 of them.
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 60) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3169 shr 6, 3172 shr 6)
                for (x in listOf(3155, 3169, 3206)) markLoaded(x shr 6, 9572 shr 6)
                markLoaded(3160 shr 6, 9590 shr 6)
            },
            // swamp_caves_roped_entrance, as the 242 cache has it.
            varTypes = VarTypes(6_000, mapOf(LumbridgeSwampCaves.ROPED_VARBIT to VarbitType(425, 3, 3))),
            random = GameRandom(5), objTypes = ObjTypes.of(*objs.toTypedArray()),
            locTypes = LocTypes(
                listOf(
                    type(LumbridgeSwampCaves.DARK_HOLE, "Dark hole", "Climb-down"),
                    type(LumbridgeSwampCaves.CLIMBING_ROPE, "Climbing rope", "Climb"),
                    type(STONE_NORTH, "Stepping stone", "Jump-across"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(hole, rope, stone).forEach(::add) }, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, timeoutMillis: Long = 10_000) =
        assertEquals(Coord(x, z, 0), await("$x, $z", timeoutMillis) { position?.takeIf { it == Coord(x, z, 0) } })

    private fun HeadlessClient.seen(text: String, timeoutMillis: Long = 10_000) =
        await(text, timeoutMillis) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.hitpoints() = stat(HITPOINTS)?.level

    private fun HeadlessClient.light(lit: Int) {
        command("item $lit")
        slotOf(lit)
    }

    @Test
    fun `the dark hole wants a rope tied once, then goes down, and the rope climbs back up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3168, 3172)
                client.locOp(hole.id, hole.x, hole.z, 1)
                client.seen("[swamp_caves.need_rope]")
                assertEquals(Coord(3168, 3172, 0), client.position)

                client.command("item ${LumbridgeSwampCaves.ROPE}")
                client.locTarget(hole.id, hole.x, hole.z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(LumbridgeSwampCaves.ROPE), LumbridgeSwampCaves.ROPE)
                client.seen("[swamp_caves.rope_tied]")
                client.await("the rope used up") { if (client.inventory(BACKPACK).none { it?.id == LumbridgeSwampCaves.ROPE }) Unit else null }

                client.locOp(hole.id, hole.x, hole.z, 1)
                client.awaitAt(3168, 9572)
                client.locOp(rope.id, rope.x, rope.z, 1)
                client.awaitAt(3168, 3172)
                // Tied for good: down again with no rope.
                client.locOp(hole.id, hole.x, hole.z, 1)
                client.awaitAt(3168, 9572)
            }
        }
    }

    @Test
    fun `with no light the insects warn at 15 ticks and bite every tick from 30, and a light stops them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hitpoints 99")
                client.await("99 Hitpoints") { client.hitpoints()?.takeIf { it == 99 } }
                client.tele(FAR_X, FAR_Z)
                client.seen("[swamp_caves.insects_warn]")
                assertEquals(99, client.hitpoints())
                client.seen("[swamp_caves.insects_swarm]")
                client.await("three bites") { client.hitpoints()?.takeIf { it <= 96 } }

                client.light(LightSources.LIT_CANDLE)
                client.awaitTicks(2)
                val lit = client.hitpoints()!!
                client.awaitTicks(5)
                assertEquals(lit, client.hitpoints())
                assertEquals(1, client.messages().count { it.text == "[swamp_caves.insects_swarm]" })
            }
        }
    }

    @Test
    fun `with no light beside the climbing rope the player climbs out instead`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3170, 9572)
                client.awaitAt(3168, 3172, timeoutMillis = 20_000)
                assertTrue(client.messages().none { it.text == "[swamp_caves.insects_swarm]" })
            }
        }
    }

    @Test
    fun `a candle flares in the gas and explodes for a quarter of the Hitpoints level, and a lantern doesn't`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hitpoints 40")
                client.await("40 Hitpoints") { client.hitpoints()?.takeIf { it == 40 } }
                client.light(LightSources.OIL_LANTERN_LIT)
                client.light(LightSources.LIT_CANDLE)
                client.tele(GAS_X, GAS_Z)
                client.seen("[swamp_caves.gas_flare item=candle]")
                client.seen("[swamp_caves.gas_explode]")
                client.await("the candle out") { if (client.inventory(BACKPACK).any { it?.id == LightSources.CANDLE }) Unit else null }
                client.await("10 damage") { client.hitpoints()?.takeIf { it == 30 } }
                // The lantern still burns, so no bites, and it is no naked flame, so no second flare.
                client.awaitTicks(10)
                assertEquals(30, client.hitpoints())
                assertEquals(1, client.messages().count { it.text.startsWith("[swamp_caves.gas_flare") })
                assertTrue(client.inventory(BACKPACK).any { it?.id == LightSources.OIL_LANTERN_LIT })
            }
        }
    }

    @Test
    fun `a fall from a stepping stone puts the lights out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.light(LightSources.OIL_LANTERN_LIT)
                client.tele(3204, 9572)
                // 51/256 to cross at level 1: jump until a fall, which lands back on the west side.
                fun out() = client.inventory(BACKPACK).any { it?.id == LightSources.OIL_LANTERN }
                var jumps = 0
                while (!out() && jumps < 30) {
                    client.locOp(stone.id, stone.x, stone.z, 1)
                    // A jump takes 4 ticks either way, then the player is on a bank again.
                    client.awaitTicks(7)
                    jumps++
                }
                // Each fall lands where the jump started: a bank tile, never the stone.
                assertTrue(client.position!!.x == 3204 || client.position!!.x == 3208, "${client.position}")
                assertTrue(client.inventory(BACKPACK).any { it?.id == LightSources.OIL_LANTERN }, "${client.inventory(BACKPACK)}")
            }
        }
    }

    private companion object {
        const val STONE_NORTH = 5948
        const val BACKPACK = 93
        const val HITPOINTS = 3

        /** In the dark, out of the gas and more than 3 tiles from the rope. */
        const val FAR_X = 3188
        const val FAR_Z = 9565

        /** In the north-west gas. */
        const val GAS_X = 3160
        const val GAS_Z = 9590
    }
}
