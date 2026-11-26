package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.VarrockArmour
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Mining
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Gathering 2, step G4 through the headless client: the Desert Quarry's granite and sandstone rocks, reached by ::tele,
 * on open ground (no cache collision in tests). The rl-client script test-runner/examples/mining-g4.json does the same
 * in the real client.
 */
class HeadlessQuarryTest {
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(maxOf(PIECES.keys.max(), VarrockArmour.PIECES.max()) + 1)
        for (armour in VarrockArmour.PIECES) objs[armour] = ObjType(armour, "Varrock armour")
        objs[RUNE_PICKAXE] = ObjType(RUNE_PICKAXE, "Rune pickaxe")
        for ((obj, name) in PIECES) objs[obj] = ObjType(obj, name)
        val mine = listOf("Mine", null, null, "hidden", null)
        fun rock(id: Int, name: String) = LocType(id, name, mine, LocCollisionType(width = 1, length = 1))
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(GRANITE_X shr 6, GRANITE_Z shr 6)
            },
            objTypes = ObjTypes(objs),
            random = GameRandom(3),
            locTypes = LocTypes(
                listOf(
                    rock(GRANITE, "Granite rocks"), rock(SANDSTONE, "Sandstone rocks"),
                    rock(Mining.ROCKS1, "Rocks"), rock(Mining.ROCKS2, "Rocks"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(GRANITE, LocShape.CENTREPIECE_STRAIGHT, 0, GRANITE_X, GRANITE_Z, 0))
                add(ZoneLoc(SANDSTONE, LocShape.CENTREPIECE_STRAIGHT, 0, SANDSTONE_X, SANDSTONE_Z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.pieces() = inventory(BACKPACK).filterNotNull().filter { it.id in PIECES }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z")
        await("at $x, $z") { position?.takeIf { it.x == x && it.z == z } }
    }

    private fun HeadlessClient.miner(level: Int) {
        await("the login rebuild") { position }
        command("item $RUNE_PICKAXE")
        command("setlevel mining $level")
        await("level $level") { stat(MINING)?.takeIf { it.level == level } }
    }

    /** Mines the rock until one piece comes, and checks its XP, the message and the empty rock; the piece's obj. */
    private fun HeadlessClient.quarry(locId: Int, x: Int, z: Int, key: String, empty: Int): Int {
        val start = stat(MINING)!!.xp
        tele(x - 1, z)
        locOp(locId, x, z, 1)
        seen("[mining.swing]")
        val piece = await("a piece of $key", timeoutMillis = 30_000) { pieces().firstOrNull() }.id
        seen("[mining.quarry ore=$key]")
        val xp = Mining.ROCKS.getValue(locId).type.sizes.first { it.obj == piece }.xpTenths / 10
        assertEquals(start + xp, await("$key XP") { stat(MINING)?.takeIf { it.xp > start } }.xp)
        await("the empty rock") { if (locAt(x, z) == empty) Unit else null }
        return piece
    }

    @Test
    fun `granite needs 45 and sandstone 35`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.miner(34)
                client.tele(SANDSTONE_X - 1, SANDSTONE_Z)
                client.locOp(SANDSTONE, SANDSTONE_X, SANDSTONE_Z, 1)
                client.seen("[mining.level level=35]")
                client.tele(GRANITE_X - 1, GRANITE_Z)
                client.locOp(GRANITE, GRANITE_X, GRANITE_Z, 1)
                client.seen("[mining.level level=45]")
            }
        }
    }

    @Test
    fun `granite gives one piece with its size's XP, empties and is back in 9 ticks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.miner(99)
                client.quarry(GRANITE, GRANITE_X, GRANITE_Z, "granite", Mining.ROCKS2)
                // 5.4 seconds: 9 ticks of 40 ms here; the rock comes back as itself.
                client.await("the granite back", timeoutMillis = 5_000) { if (client.locAt(GRANITE_X, GRANITE_Z) == GRANITE) Unit else null }
            }
        }
    }

    @Test
    fun `sandstone gives one piece with its size's XP and empties`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.miner(99)
                client.quarry(SANDSTONE, SANDSTONE_X, SANDSTONE_Z, "sandstone", Mining.ROCKS1)
            }
        }
    }

    /** Mines granite [swings] times, each after the rock is back, and gives how many swings gave two pieces. */
    private fun HeadlessClient.graniteSwings(swings: Int): Int {
        tele(GRANITE_X - 1, GRANITE_Z)
        var doubles = 0
        repeat(swings) {
            await("the granite", timeoutMillis = 10_000) { if (locAt(GRANITE_X, GRANITE_Z).let { it == null || it == GRANITE }) Unit else null }
            val before = pieces().size
            val xp = stat(MINING)!!.xp
            locOp(GRANITE, GRANITE_X, GRANITE_Z, 1)
            await("the empty rock", timeoutMillis = 10_000) { if (locAt(GRANITE_X, GRANITE_Z) == Mining.ROCKS2) Unit else null }
            val got = pieces().drop(before)
            assertTrue(got.size in 1..2, "${got.size} pieces from one swing")
            // Two of the same size, with its XP for each.
            if (got.size == 2) {
                doubles++
                assertEquals(got[0].id, got[1].id)
            }
            val tenths = Mining.ROCKS.getValue(GRANITE).type.sizes.first { it.obj == got[0].id }.xpTenths * got.size
            assertEquals(xp + tenths / 10, await("granite XP") { stat(MINING)?.takeIf { it.xp >= xp + tenths / 10 } }.xp)
            if (pieces().size > 24) {
                command("empty")
                await("an empty backpack") { if (pieces().isEmpty()) Unit else null }
                command("item $RUNE_PICKAXE")
            }
        }
        return doubles
    }

    @Test
    fun `Varrock armour 2 mines a second piece of granite now and then, armour 1 never`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.miner(99)
                val dave = server.world.playerNamed("dave")!!
                dave.equipment[WearPos.BODY] = InvObj(VarrockArmour.PIECES[1], 1)
                val doubles = client.graniteSwings(SWINGS)
                assertTrue(doubles in 1 until SWINGS, "$doubles doubles in $SWINGS swings on armour 2")
                // The near miss: armour 1, which the Wiki leaves out of the quarry.
                dave.equipment[WearPos.BODY] = InvObj(VarrockArmour.PIECES[0], 1)
                assertEquals(0, client.graniteSwings(SWINGS), "armour 1")
            }
        }
    }

    private companion object {
        const val SWINGS = 30
        /** enakh_granite_rocks and enakh_sandstone_rocks in the Desert Quarry (OSRS Wiki, Granite rocks, Sandstone rocks). */
        const val GRANITE = 11387
        const val GRANITE_X = 3172
        const val GRANITE_Z = 2920
        const val SANDSTONE = 11386
        const val SANDSTONE_X = 3169
        const val SANDSTONE_Z = 2922
        const val RUNE_PICKAXE = 1275
        const val BACKPACK = 93
        const val MINING = 14
        val PIECES = mapOf(
            6983 to "Granite (5kg)", 6981 to "Granite (2kg)", 6979 to "Granite (500g)",
            6977 to "Sandstone (10kg)", 6975 to "Sandstone (5kg)", 6973 to "Sandstone (2kg)", 6971 to "Sandstone (1kg)",
        )
    }
}
