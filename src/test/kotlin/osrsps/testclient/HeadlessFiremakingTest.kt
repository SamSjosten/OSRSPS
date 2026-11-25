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
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Firemaking
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Gathering step 3 through the headless client: lighting logs from the backpack and on the ground. The rl-client script
 * test-runner/examples/firemaking-lumbridge.json lights logs in the real client.
 */
class HeadlessFiremakingTest {
    // Fast ticks: a fire burns up to 199 of them, and the connection times out after 15 s without a packet from the client.
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(OAK_LOGS + 1)
        val ground = listOf(null, null, "Take", "Light", null)
        objs[LOGS] = ObjType(LOGS, "Logs", ops = ground, tradeable = true)
        objs[OAK_LOGS] = ObjType(OAK_LOGS, "Oak logs", ops = ground, tradeable = true)
        objs[YEW_LOGS] = ObjType(YEW_LOGS, "Yew logs", ops = ground, tradeable = true)
        objs[Firemaking.TINDERBOX] = ObjType(Firemaking.TINDERBOX, "Tinderbox", tradeable = true)
        objs[Firemaking.ASHES] = ObjType(Firemaking.ASHES, "Ashes", tradeable = true)
        objs[RUNE_AXE] = ObjType(RUNE_AXE, "Rune axe", tradeable = true)
        World(
            crcs, CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(SQUARE_X shr 6, SQUARE_Z shr 6)
                markLoaded(CHURCH_X shr 6, CHURCH_Z shr 6)
            },
            objTypes = ObjTypes(objs),
            random = GameRandom(1),
            locTypes = LocTypes(
                mapOf(
                    Firemaking.FIRE to LocType(Firemaking.FIRE, "Fire", collision = LocCollisionType(width = 1, length = 1)),
                    YEW to LocType(YEW, "Yew tree", listOf("Chop down", null, null, null, null), LocCollisionType(width = 1, length = 1)),
                    YEW_STUMP to LocType(YEW_STUMP, "Tree stump", listOf(null, null, null, null, null), LocCollisionType(width = 1, length = 1)),
                ),
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(YEW, LocShape.CENTREPIECE_STRAIGHT, 0, YEW_X, YEW_Z, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String, timeoutMillis: Long = 10_000) =
        await(text, timeoutMillis) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    private fun HeadlessClient.use(id: Int, target: Int) =
        ifButtonT(InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(id), id, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(target), target)

    @Test
    fun `a tinderbox on logs lights them where the player stood, steps them west, and the fire leaves ashes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val at = client.await("the login rebuild") { client.position }
                client.command("item ${Firemaking.TINDERBOX}")
                client.command("item $LOGS")
                client.use(Firemaking.TINDERBOX, LOGS)
                client.seen("[firemaking.attempt]")
                client.await("the logs on the ground") { client.groundObjs().firstOrNull { it.id == LOGS && it.coord == at } }
                // 65/256 a roll at level 1, a roll every 4 ticks.
                client.seen("[firemaking.catches]", timeoutMillis = 30_000)
                // Failed rolls strike again without a new message: one attempt message however many rolls it took.
                assertEquals(1, client.messages().count { it.text == "[firemaking.attempt]" })
                client.await("the fire") { if (client.locAt(at.x, at.z) == Firemaking.FIRE) Unit else null }
                client.await("a step west") { client.position?.takeIf { it.x == at.x - 1 && it.z == at.z } }
                assertEquals(40, client.await("Firemaking XP") { client.stat(FIREMAKING)?.takeIf { it.xp > 0 } }.xp)
                assertEquals(0, client.inventory(BACKPACK).count { it?.id == LOGS })
                // 100 to 199 ticks later it goes out, leaving ashes.
                client.await("ashes", timeoutMillis = 40_000) { client.groundObjs().firstOrNull { it.id == Firemaking.ASHES && it.coord == at } }
                assertEquals(-1, client.locAt(at.x, at.z) ?: -1)
            }
        }
    }

    @Test
    fun `oak logs need level 15, and Light on logs needs a tinderbox`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val at = client.await("the login rebuild") { client.position }
                client.command("item ${Firemaking.TINDERBOX}")
                client.command("item $OAK_LOGS")
                client.use(Firemaking.TINDERBOX, OAK_LOGS)
                client.seen("[firemaking.level level=15 logs=oak logs]")
                assertEquals(1, client.inventory(BACKPACK).count { it?.id == OAK_LOGS })

                client.command("groundobj $LOGS")
                client.await("the logs") { client.groundObjs().firstOrNull { it.id == LOGS && it.coord == at } }
                // Drop the tinderbox: with none, Light says so.
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, DROP_BUTTON_OP, client.slotOf(Firemaking.TINDERBOX), Firemaking.TINDERBOX)
                client.await("no tinderbox") { if (client.inventory(BACKPACK).none { it?.id == Firemaking.TINDERBOX }) Unit else null }
                client.objOp(LOGS, at.x, at.z, LIGHT)
                client.seen("[firemaking.no_tinderbox]")
            }
        }
    }

    @Test
    fun `Light on logs on the ground lights them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                val at = client.await("the login rebuild") { client.position }
                client.command("item ${Firemaking.TINDERBOX}")
                client.command("setlevel firemaking 43")
                client.command("groundobj $LOGS")
                client.await("the logs") { client.groundObjs().firstOrNull { it.id == LOGS && it.coord == at } }
                client.objOp(LOGS, at.x, at.z, LIGHT)
                client.seen("[firemaking.attempt]")
                // Sure from level 43: lit on the first roll, 3 ticks after the strike.
                client.seen("[firemaking.catches]")
                client.await("the fire") { if (client.locAt(at.x, at.z) == Firemaking.FIRE) Unit else null }
            }
        }
    }

    @Test
    fun `logs lit while another skill's clock still runs strike and say so at once`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${Firemaking.TINDERBOX}")
                client.command("item $LOGS")
                client.command("setlevel firemaking 43")
                client.slotOf(LOGS)
                // As if chopping the logs had set the shared skill clock a moment ago: its roll is still to come.
                server.world.playerNamed("dave")!!.actionDelay = server.world.tick + 30
                val before = client.ticksSeen
                client.use(Firemaking.TINDERBOX, LOGS)
                // Before the fix, nothing was said until the clock ran out (the first roll, sure at 43, lit them).
                val attempt = client.seen("[firemaking.attempt]")
                assertTrue(attempt.tick - before < 5, "attempt after ${attempt.tick - before} ticks")
                client.seen("[firemaking.catches]", timeoutMillis = 30_000)
                assertEquals(1, client.messages().count { it.text == "[firemaking.attempt]" })
            }
        }
    }

    @Test
    fun `oak logs lit in Lumbridge do the easy diary task, and plain logs don't`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${Firemaking.TINDERBOX}")
                client.command("setlevel firemaking 43")
                client.command("item $LOGS")
                client.slotOf(LOGS)
                client.use(Firemaking.TINDERBOX, LOGS)
                client.seen("[firemaking.catches]", timeoutMillis = 30_000)
                assertEquals(0, client.messages().count { it.text.startsWith("[diary.task_done") }, "plain logs do no task")
                client.command("item $OAK_LOGS")
                client.slotOf(OAK_LOGS)
                client.use(Firemaking.TINDERBOX, OAK_LOGS)
                client.await("the diary task", 30_000) { client.messages().firstOrNull { it.text.startsWith("[diary.task_done") } }
            }
        }
    }

    @Test
    fun `yew logs chopped in Varrock and lit on the church's top floor do the hard diary task, and bought ones or the square don't`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fern").use { client ->
                client.await("the login rebuild") { client.position }
                fun diaryTasksDone() = client.messages().count { it.text.startsWith("[diary.task_done") }
                fun light(x: Int, z: Int, level: Int) {
                    client.command("tele $x $z $level")
                    client.await("at $x, $z, $level") { client.position?.takeIf { it.x == x && it.z == z && it.level == level } }
                    client.slotOf(YEW_LOGS)
                    val catches = client.messages().count { it.text == "[firemaking.catches]" }
                    client.use(Firemaking.TINDERBOX, YEW_LOGS)
                    client.await("the fire", 30_000) { Unit.takeIf { client.messages().count { it.text == "[firemaking.catches]" } > catches } }
                    client.awaitTicks(2)
                }
                client.command("item ${Firemaking.TINDERBOX}")
                client.command("setlevel firemaking 60")
                client.command("setlevel woodcutting 60")
                // Logs from anywhere but a Varrock yew (::item here) on the top floor: not the task (Codex on #952).
                client.command("item $YEW_LOGS")
                light(CHURCH_X + 3, CHURCH_Z, CHURCH_TOP)
                assertEquals(0, diaryTasksDone(), "logs not chopped in Varrock")
                client.command("item $RUNE_AXE")
                client.command("tele ${YEW_X + 1} $YEW_Z 0")
                client.await("by the yew") { client.position?.takeIf { it.x == YEW_X + 1 && it.z == YEW_Z } }
                client.locOp(YEW, YEW_X, YEW_Z, 1)
                client.await("yew logs", 30_000) { client.inventory(BACKPACK).indexOfFirst { it?.id == YEW_LOGS }.takeIf { it >= 0 } }
                client.seen("[diary.stage stage=1]")
                // Chopped, but lit in the square or on the church's first floor: still not the task.
                light(SQUARE_X, SQUARE_Z, 0)
                assertEquals(0, diaryTasksDone(), "outside the church")
                client.command("item $YEW_LOGS")
                light(CHURCH_X, CHURCH_Z, 1)
                assertEquals(0, diaryTasksDone(), "the church's first floor")
                client.command("item $YEW_LOGS")
                light(CHURCH_X, CHURCH_Z, CHURCH_TOP)
                client.await("the diary task") { Unit.takeIf { diaryTasksDone() == 1 } }
            }
        }
    }

    private companion object {
        const val LOGS = 1511
        const val OAK_LOGS = 1521
        const val YEW_LOGS = 1515
        const val SQUARE_X = 3222
        const val SQUARE_Z = 3430
        /** Inside the diary's Varrock church box, whose top floor is level 3. */
        const val CHURCH_X = 3255
        const val CHURCH_Z = 3480
        const val CHURCH_TOP = 3
        const val RUNE_AXE = 1359
        const val YEW = 10822
        const val YEW_STUMP = 9714
        /** By the church, inside Varrock. */
        const val YEW_X = 3249
        const val YEW_Z = 3470
        const val BACKPACK = 93
        const val FIREMAKING = 11
        const val LIGHT = 4

        /** iop5 (Drop) is button op 7 (see InventoryTab.click). */
        const val DROP_BUTTON_OP = 7
    }
}
