package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.skill.Firemaking
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

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
        objs[Firemaking.TINDERBOX] = ObjType(Firemaking.TINDERBOX, "Tinderbox", tradeable = true)
        objs[Firemaking.ASHES] = ObjType(Firemaking.ASHES, "Ashes", tradeable = true)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            random = GameRandom(1),
            locTypes = LocTypes(mapOf(Firemaking.FIRE to LocType(Firemaking.FIRE, "Fire", collision = LocCollisionType(width = 1, length = 1)))),
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

    private companion object {
        const val LOGS = 1511
        const val OAK_LOGS = 1521
        const val BACKPACK = 93
        const val FIREMAKING = 11
        const val LIGHT = 4

        /** iop5 (Drop) is button op 7 (see InventoryTab.click). */
        const val DROP_BUTTON_OP = 7
    }
}
