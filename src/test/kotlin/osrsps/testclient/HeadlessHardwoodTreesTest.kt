package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Gathering 2, step G2 through the headless client: the Chompy Marsh teak, reached by ::tele as live has it, and a
 * redwood section by its Cut option, on open ground (no cache collision in tests). The rl-client script
 * test-runner/examples/woodcutting-g2.json does the same in the real client.
 */
class HeadlessHardwoodTreesTest {
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(REDWOOD_LOGS + 1)
        objs[TEAK_LOGS] = ObjType(TEAK_LOGS, "Teak logs")
        objs[REDWOOD_LOGS] = ObjType(REDWOOD_LOGS, "Redwood logs")
        objs[DRAGON_AXE] = ObjType(DRAGON_AXE, "Dragon axe")
        val none = List<String?>(5) { null }
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(TEAK_X shr 6, TEAK_Z shr 6)
                markLoaded(REDWOOD_X shr 6, REDWOOD_Z shr 6)
            },
            objTypes = ObjTypes(objs),
            random = GameRandom(4),
            locTypes = LocTypes(
                mapOf(
                    TEAK to LocType(TEAK, "Teak tree", listOf("Chop down", null, null, null, null), LocCollisionType(width = 1, length = 1)),
                    TEAK_STUMP to LocType(TEAK_STUMP, "Tree stump", none, LocCollisionType(width = 1, length = 1)),
                    // The Cut option second, so only the option lookup finds it (live has it first).
                    REDWOOD to LocType(REDWOOD, "Redwood tree", listOf(null, "Cut", null, null, null), LocCollisionType(width = 1, length = 2)),
                    REDWOOD_CUT to LocType(REDWOOD_CUT, "Redwood tree", none, LocCollisionType(width = 1, length = 2)),
                ),
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(TEAK, LocShape.CENTREPIECE_STRAIGHT, 0, TEAK_X, TEAK_Z, 0))
                add(ZoneLoc(REDWOOD, LocShape.CENTREPIECE_STRAIGHT, 0, REDWOOD_X, REDWOOD_Z, 1))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).count { it?.id == id }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("at $x, $z, $level") { position?.takeIf { it.x == x && it.z == z } }
    }

    @Test
    fun `a teak needs 35, then gives logs until its 50-tick timer fells it, and grows back in 15 ticks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $DRAGON_AXE")
                client.command("setlevel woodcutting 34")
                client.await("level 34") { client.stat(WOODCUTTING)?.takeIf { it.level == 34 } }
                client.tele(TEAK_X - 1, TEAK_Z, 0)
                client.locOp(TEAK, TEAK_X, TEAK_Z, 1)
                client.seen("[woodcutting.level level=35]")

                client.command("setlevel woodcutting 99")
                val start = client.await("level 99") { client.stat(WOODCUTTING)?.takeIf { it.level == 99 } }.xp
                client.locOp(TEAK, TEAK_X, TEAK_Z, 1)
                client.seen("[woodcutting.swing]")
                client.await("the teak stump", timeoutMillis = 30_000) { if (client.locAt(TEAK_X, TEAK_Z) == TEAK_STUMP) Unit else null }
                val logs = client.count(TEAK_LOGS)
                // A roll every 4 ticks over a 50-tick timer: 13 at most, and more than one.
                assertTrue(logs in 2..13, "$logs teak logs")
                assertEquals(start + 85 * logs, client.await("teak XP") { client.stat(WOODCUTTING)?.takeIf { it.xp >= start + 85 * logs } }.xp)
                client.await("the teak back") { if (client.locAt(TEAK_X, TEAK_Z) == TEAK) Unit else null }
            }
        }
    }

    @Test
    fun `a redwood section is cut by its Cut option and gives redwood logs`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $DRAGON_AXE")
                client.command("setlevel woodcutting 99")
                val start = client.await("level 99") { client.stat(WOODCUTTING)?.takeIf { it.level == 99 } }.xp
                client.tele(REDWOOD_X + 1, REDWOOD_Z, 1)
                client.locOp(REDWOOD, REDWOOD_X, REDWOOD_Z, 2)
                client.await("a redwood log", timeoutMillis = 60_000) { if (client.count(REDWOOD_LOGS) >= 1) Unit else null }
                client.seen("[woodcutting.get_logs logs=redwood logs]")
                assertEquals(start + 380, client.await("redwood XP") { client.stat(WOODCUTTING)?.takeIf { it.xp > start } }.xp)
            }
        }
    }

    private companion object {
        /** teaktree in Chompy Marsh (OSRS Wiki, Teak tree). */
        const val TEAK = 9036
        const val TEAK_STUMP = 9037
        const val TEAK_X = 2335
        const val TEAK_Z = 3048
        /** redwoodtree_l, a section on the Woodcutting Guild's first scaffold level. */
        const val REDWOOD = 29668
        const val REDWOOD_CUT = 29669
        const val REDWOOD_X = 1568
        const val REDWOOD_Z = 3493
        const val TEAK_LOGS = 6333
        const val REDWOOD_LOGS = 19669
        const val DRAGON_AXE = 6739
        const val BACKPACK = 93
        const val WOODCUTTING = 8
    }
}
