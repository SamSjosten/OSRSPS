package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocShape

/**
 * Revision 242 added packet forms whose loc ids are 3 bytes: OPLOC1-5_V3, OPLOCT_V2 and OPLOC6_V2 from the client, and
 * LOC_ADD_CHANGE_V3 from the server. No loc in the cache is past 65535 yet, so a made-up one checks that every form
 * carries the whole id both ways.
 */
class HeadlessLongLocIdTest {
    private val clicks = ConcurrentLinkedQueue<String>()

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(POT + 1)
        objs[POT] = ObjType(POT, "Pot", iops = listOf(null, null, null, null, "Drop"))
        val locs = mapOf(LONG to LocType(LONG, "Long", ops = listOf("One", "Two", "Three", "Four", "Five")))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            locTypes = LocTypes(locs),
            staticLocs = StaticLocs().apply { add(ZoneLoc(LONG, LocShape.CENTREPIECE_STRAIGHT, 0, X, Z, 0)) },
            cycleMillis = millis,
        ).also { world ->
            for (option in 1..5) {
                world.handlers.onLoc(LONG, option) { op ->
                    clicks += "op ${op.id} ${op.option} at ${op.loc?.x}, ${op.loc?.z}"
                    // A changed loc past 65535 goes out in the zone's updates.
                    if (option == 1) world.locs.add(ZoneLoc(LONG + 1, LocShape.CENTREPIECE_STRAIGHT, 1, X, Z + 2, 0))
                }
            }
            world.handlers.onObjOnLoc(LONG, POT) { op -> clicks += "obj ${op.obj} on ${op.id} at ${op.loc?.x}, ${op.loc?.z}" }
        }
    }

    private fun HeadlessClient.next(expected: String) {
        val seen = await(expected) { clicks.poll() }
        assertEquals(expected, seen)
        // The handlers end at once, but the click after one walk is read only once the arrive delay is over.
        awaitTicks(2)
    }

    @Test
    fun `a loc id past 65535 reaches the server whole in every op form, and comes back whole in zone updates`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                for (option in 1..5) {
                    client.locOp(LONG, X, Z, option, longId = true)
                    client.next("op $LONG $option at $X, $Z")
                }
                val change = client.await("the long loc's add") { client.locChanges().firstOrNull { it.id == LONG + 1 } }
                assertEquals(listOf(10, 1, Coord(X, Z + 2)), listOf(change.shape, change.angle, change.coord))

                client.command("item $POT")
                val slot = client.await("a pot") { client.inventory(BACKPACK).indexOfFirst { it?.id == POT }.takeIf { it >= 0 } }
                client.locTarget(LONG, X, Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, POT, longId = true)
                client.next("obj $POT on $LONG at $X, $Z")
            }
        }
    }

    private companion object {
        /** The first id the 2-byte forms can't carry, and more besides: 70000 has bits in all three bytes. */
        const val LONG = 70_000
        const val POT = 1931
        const val X = 3224
        const val Z = 3218
        const val BACKPACK = 93
    }
}
