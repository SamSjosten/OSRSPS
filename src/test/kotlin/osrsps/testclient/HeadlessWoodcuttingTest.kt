package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.LevelUps
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Gathering step 2 through the headless client: chopping a regular tree and an oak in Lumbridge. The rl-client script
 * test-runner/examples/woodcutting-lumbridge.json chops the same trees in the real client.
 */
class HeadlessWoodcuttingTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(DRAGON_AXE + 1)
        objs[LOGS] = ObjType(LOGS, "Logs")
        objs[OAK_LOGS] = ObjType(OAK_LOGS, "Oak logs")
        objs[BRONZE_AXE] = ObjType(BRONZE_AXE, "Bronze axe")
        objs[DRAGON_AXE] = ObjType(DRAGON_AXE, "Dragon axe")
        objs[POT] = ObjType(POT, "Pot")
        val chop = listOf("Chop down", null, null, null, null)
        val stumpOps = List<String?>(5) { null }
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            random = GameRandom(1),
            locTypes = LocTypes(
                mapOf(
                    TREE to LocType(TREE, "Tree", chop, LocCollisionType(width = 2, length = 2)),
                    STUMP to LocType(STUMP, "Tree stump", stumpOps, LocCollisionType(width = 2, length = 2)),
                    OAK to LocType(OAK, "Oak tree", chop, LocCollisionType(width = 3, length = 3)),
                    OAK_STUMP to LocType(OAK_STUMP, "Tree stump", stumpOps, LocCollisionType(width = 3, length = 3)),
                ),
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(TREE, LocShape.CENTREPIECE_STRAIGHT, 0, 3225, 3218, 0))
                add(ZoneLoc(OAK, LocShape.CENTREPIECE_STRAIGHT, 0, 3215, 3218, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).count { it?.id == id }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    @Test
    fun `no axe, then a bronze axe fells a regular tree for one log, and it grows back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(TREE, 3225, 3218, 1)
                client.seen("[woodcutting.no_axe]")

                client.command("item $BRONZE_AXE")
                client.await("the axe") { if (client.count(BRONZE_AXE) == 1) Unit else null }
                client.locOp(TREE, 3225, 3218, 1)
                client.seen("[woodcutting.swing]")
                client.await("logs", timeoutMillis = 30_000) { if (client.count(LOGS) == 1) Unit else null }
                client.seen("[woodcutting.get_logs logs=logs]")
                assertEquals(25, client.await("Woodcutting XP") { client.stat(WOODCUTTING)?.takeIf { it.xp > 0 } }.xp)
                client.await("the stump") { if (client.locAt(3225, 3218) == STUMP) Unit else null }

                // A regular tree falls after one log: no more come.
                client.awaitTicks(8)
                assertEquals(1, client.count(LOGS))
                // 60 to 100 ticks later it grows back.
                client.await("the tree back", timeoutMillis = 30_000) { if (client.locAt(3225, 3218) == TREE) Unit else null }
            }
        }
    }

    @Test
    fun `an oak needs level 15 and a sure roll gives a log every 4 ticks until its 45-tick timer runs out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $DRAGON_AXE")
                client.await("the axe") { if (client.count(DRAGON_AXE) == 1) Unit else null }
                client.locOp(OAK, 3215, 3218, 1)
                client.seen("[woodcutting.level level=15]")

                // Level 99 with a dragon axe is a sure roll on an oak (375 high).
                client.command("setlevel woodcutting 99")
                client.locOp(OAK, 3215, 3218, 1)
                client.await("the oak stump", timeoutMillis = 30_000) { if (client.locAt(3215, 3218) == OAK_STUMP) Unit else null }
                // Rolls 3, 7, ... 43 ticks in leave the timer above 0; the roll at 47 fells it, with its log.
                assertEquals(12, client.await("12 oak logs") { client.count(OAK_LOGS).takeIf { it >= 12 } })
                // 12 x 37.5 XP.
                client.await("450 XP") { client.stat(WOODCUTTING)?.takeIf { it.xp == 13_034_431 + 450 } }
                client.awaitTicks(8)
                assertEquals(12, client.count(OAK_LOGS))
            }
        }
    }

    @Test
    fun `a level-up stops the chopping`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $DRAGON_AXE")
                // Level 61 (a dragon axe's) is 302,288 XP and 62 is 333,804: one oak log (37.5) is the level.
                client.command("setlevel woodcutting 61")
                client.command("addxp woodcutting 31500")
                client.await("333,788 XP") { client.stat(WOODCUTTING)?.takeIf { it.xp == 333_788 } }
                client.locOp(OAK, 3215, 3218, 1)
                client.await("the level-up box", timeoutMillis = 30_000) { if (LevelUps.INTERFACE in client.openInterfaces()) Unit else null }
                assertEquals(1, client.count(OAK_LOGS))
                client.awaitTicks(12)
                assertEquals(1, client.count(OAK_LOGS), "chopping carried on behind the level-up box")
                // Closing it doesn't start it again.
                client.resumePause(LevelUps.INTERFACE, LevelUps.CONTINUE)
                client.await("the box closed") { if (LevelUps.INTERFACE !in client.openInterfaces()) Unit else null }
                client.awaitTicks(8)
                assertEquals(1, client.count(OAK_LOGS))
            }
        }
    }

    @Test
    fun `a full backpack stops the chopping`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $BRONZE_AXE")
                client.command("item $POT 27")
                client.await("a full backpack") { if (client.inventory(BACKPACK).count { it != null } == 28) Unit else null }
                client.locOp(TREE, 3225, 3218, 1)
                client.seen("[woodcutting.inventory_full]")
            }
        }
    }

    private companion object {
        const val TREE = 1276
        const val STUMP = 1342
        const val OAK = 10820
        const val OAK_STUMP = 1356
        const val LOGS = 1511
        const val OAK_LOGS = 1521
        const val BRONZE_AXE = 1351
        const val DRAGON_AXE = 6739
        const val POT = 1931
        const val BACKPACK = 93
        const val WOODCUTTING = 8
    }
}
