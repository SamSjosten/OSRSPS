package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.MuseumCleaning
import osrsps.game.content.VarrockMuseum
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * The museum's finds cleaning through the headless client: the tool rack gives the three tools, the rocks a find, a
 * table won't clean without leather gloves and boots, then cleans every find one by one (each crumbles or turns into
 * something), the storage crate takes an artefact for a reward, and a wall map gives the museum map. The real places
 * are the 241 cache's; here the locs stand by the spawn.
 */
class HeadlessMuseumCleaningTest {
    private fun loc(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, listOf(*ops) + List(5 - ops.size) { null }, LocCollisionType(width = 1, length = 1))

    private fun wearable(id: Int, name: String, pos: Int) = ObjType(id, name, iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = pos)

    /** Every item the activity hands out, so none is dropped for want of a type. */
    private val objs = ObjTypes.of(
        wearable(MuseumCleaning.LEATHER_GLOVES, "Leather gloves", WearPos.HANDS), wearable(MuseumCleaning.LEATHER_BOOTS, "Leather boots", WearPos.FEET),
        *(MuseumCleaning.TOOL_IDS + MuseumCleaning.COMMON + MuseumCleaning.UNIQUE_FOUR + listOf(MuseumCleaning.UNCLEANED_FIND, MuseumCleaning.CLEAN_NECKLACE, VarrockMuseum.MUSEUM_MAP))
            .map { ObjType(it, "Item $it") }.toTypedArray(),
        *(MuseumCleaning.UNREMARKABLE + MuseumCleaning.CRATE_REWARDS).map { it.obj }.distinct()
            .map { ObjType(it, "Reward $it", stackable = it in STACKABLE) }.toTypedArray(),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs,
            locTypes = LocTypes(
                listOf(
                    loc(MuseumCleaning.TOOLS, "Tools", "Take"), loc(MuseumCleaning.ROCKS.first(), "Dig Site specimen rocks", "Take"),
                    loc(MuseumCleaning.TABLE, "Specimen table", "Clean"), loc(MuseumCleaning.CRATE, "Storage crate", "Add finds"),
                    loc(MuseumCleaning.WALL_MAPS.first(), "Map", "Look-at", "Take"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(MuseumCleaning.TOOLS, LocShape.CENTREPIECE_STRAIGHT, 0, 3224, 3218, 0))
                add(ZoneLoc(MuseumCleaning.ROCKS.first(), LocShape.CENTREPIECE_STRAIGHT, 0, 3226, 3218, 0))
                add(ZoneLoc(MuseumCleaning.TABLE, LocShape.CENTREPIECE_STRAIGHT, 0, 3228, 3218, 0))
                add(ZoneLoc(MuseumCleaning.CRATE, LocShape.CENTREPIECE_STRAIGHT, 0, 3222, 3220, 0))
                add(ZoneLoc(MuseumCleaning.WALL_MAPS.first(), LocShape.CENTREPIECE_STRAIGHT, 0, 3220, 3218, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.awaitHeld(id: Int, count: Long) = await("$count of $id") { if (held(id) == count) Unit else null }

    private fun HeadlessClient.wear(id: Int) {
        command("item $id 1")
        val slot = await("item $id in the backpack") { inventory(Inventory.INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }
        ifButton(INVENTORY, INVENTORY_ITEMS, WEAR_BUTTON, slot, id)
        await("item $id worn") { inventory(WORN).firstOrNull { it?.id == id } }
    }

    private fun HeadlessClient.count(text: String) = messages().count { it.text.startsWith(text) }

    @Test
    fun `tools, a find, no cleaning without the gear, then every find cleaned`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(MuseumCleaning.TOOLS, 3224, 3218, 1)
                for (tool in MuseumCleaning.TOOL_IDS) client.awaitHeld(tool, 1)
                client.locOp(MuseumCleaning.ROCKS.first(), 3226, 3218, 1)
                client.awaitHeld(MuseumCleaning.UNCLEANED_FIND, 1)
                client.awaitTicks(2)
                client.locOp(MuseumCleaning.ROCKS.first(), 3226, 3218, 1)
                client.awaitHeld(MuseumCleaning.UNCLEANED_FIND, 2)

                client.locOp(MuseumCleaning.TABLE, 3228, 3218, 1)
                client.awaitMessage("no gear") { it == "[museum_cleaning.need_gear]" }
                client.wear(MuseumCleaning.LEATHER_GLOVES)
                client.wear(MuseumCleaning.LEATHER_BOOTS)
                client.locOp(MuseumCleaning.TABLE, 3228, 3218, 1)
                client.awaitHeld(MuseumCleaning.UNCLEANED_FIND, 0)
                client.await("two outcomes") {
                    if (client.count("[museum_cleaning.crumble]") + client.count("[museum_cleaning.cleaned") == 2) Unit else null
                }
            }
        }
    }

    @Test
    fun `the crate takes an artefact for a reward, and a wall map gives the museum map`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(MuseumCleaning.CRATE, 3222, 3220, 1)
                client.awaitMessage("nothing to add") { it == "[museum_cleaning.no_artefacts]" }
                client.command("item ${MuseumCleaning.POTTERY} 1")
                client.awaitHeld(MuseumCleaning.POTTERY, 1)
                client.locOp(MuseumCleaning.CRATE, 3222, 3220, 1)
                val reward = client.await("the reward") { client.messages().lastOrNull { it.text.startsWith("[museum_cleaning.reward") } }
                assertEquals(0L, client.held(MuseumCleaning.POTTERY))
                assertTrue("Reward" in reward.text, reward.text)

                client.locOp(MuseumCleaning.WALL_MAPS.first(), 3220, 3218, 2)
                client.awaitHeld(VarrockMuseum.MUSEUM_MAP, 1)
                client.locOp(MuseumCleaning.WALL_MAPS.first(), 3220, 3218, 1)
                client.await("the map") { if (MuseumCleaning.MAP_INTERFACE in client.openInterfaces()) Unit else null }
                assertEquals(1L, client.held(VarrockMuseum.MUSEUM_MAP))
            }
        }
    }

    private companion object {
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val WORN = 94
        const val WEAR_BUTTON = 3
        val STACKABLE = setOf(995, 9140, 807, 40)
    }
}
