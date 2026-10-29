package osrsps.testclient

import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.skill.BirdNests
import osrsps.map.CollisionMap

/**
 * Gathering step 2b through the headless client: a bird nest falling next to the player (as `::nest` drops one, the way
 * a log's 1/256 roll does), taking it and searching it. The rl-client script test-runner/examples/woodcutting-nests.json
 * does the same in the real client.
 */
class HeadlessBirdNestTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val search = listOf("Search", null, null, null, "Drop")
        val objs = arrayOfNulls<ObjType>(BirdNests.SEEDS + 100)
        for (nest in BirdNests.CONTENTS.keys) objs[nest] = ObjType(nest, "Bird nest", iops = search, tradeable = true)
        objs[BirdNests.EMPTY_NEST] = ObjType(BirdNests.EMPTY_NEST, "Bird nest")
        for (seed in BirdNests.CONTENTS.getValue(BirdNests.SEEDS)) objs[seed.obj] = ObjType(seed.obj, "Seed ${seed.obj}", stackable = true)
        for (ring in BirdNests.CONTENTS.getValue(BirdNests.RING)) objs[ring.obj] = ObjType(ring.obj, "Ring ${ring.obj}")
        for (egg in listOf(5076, 5077, 5078)) objs[egg] = ObjType(egg, "Egg $egg")
        objs[POT] = ObjType(POT, "Pot")
        World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), cycleMillis = millis)
    }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `a seed nest falls next to the player, and searching it leaves an empty nest and a seed`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val at = client.await("the login rebuild") { client.position }
                client.command("nest seeds")
                client.seen("[birdnest.falls]")
                val nest = client.await("the nest") { client.groundObjs().firstOrNull { it.id == BirdNests.SEEDS } }
                val dx = nest.coord.x - at.x
                val dz = nest.coord.z - at.z
                assertTrue(abs(dx) <= 1 && abs(dz) <= 1 && (dx != 0 || dz != 0), "the nest landed at $dx, $dz from the player")

                client.objOp(BirdNests.SEEDS, nest.coord.x, nest.coord.z, TAKE)
                val slot = client.slotOf(BirdNests.SEEDS)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, SEARCH_BUTTON_OP, slot, BirdNests.SEEDS)
                client.await("the empty nest in its slot") { if (client.inventory(BACKPACK)[slot]?.id == BirdNests.EMPTY_NEST) Unit else null }
                val seeds = BirdNests.CONTENTS.getValue(BirdNests.SEEDS).map { it.obj }.toSet()
                val seed = client.await("a seed") { client.inventory(BACKPACK).firstOrNull { it != null && it.id in seeds } }
                assertEquals(1, seed.count)
                client.seen("[birdnest.search item=seed ${seed.id}]")
            }
        }
    }

    @Test
    fun `searching a ring nest with a full backpack says so and keeps the nest`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${BirdNests.RING}")
                client.command("item $POT 27")
                client.await("a full backpack") { if (client.inventory(BACKPACK).count { it != null } == 28) Unit else null }
                val slot = client.slotOf(BirdNests.RING)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, SEARCH_BUTTON_OP, slot, BirdNests.RING)
                client.seen("[birdnest.inventory_full]")
                client.awaitTicks(2)
                assertEquals(BirdNests.RING, client.inventory(BACKPACK)[slot]?.id)
            }
        }
    }

    private companion object {
        const val POT = 1931
        const val BACKPACK = 93
        const val TAKE = 3

        /** iop1 is button op 2 (see InventoryTab.click). */
        const val SEARCH_BUTTON_OP = 2
    }
}
