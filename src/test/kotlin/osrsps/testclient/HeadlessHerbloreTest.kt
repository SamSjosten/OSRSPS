package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Herblore
import osrsps.game.skill.HerbloreData
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Artisan step 8 through the headless client: cleaning herbs, unfinished potions, grinding and filling vials. The rl-client
 * script test-runner/examples/herblore-basics.json does the same in Lumbridge Castle's kitchen.
 */
class HeadlessHerbloreTest {
    private val data = HerbloreData.load()

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val ids = data.cleaning.values.flatMap { listOf(it.grimy, it.clean) } +
            (data.unfinished.values + data.grinding.values + data.fillVial).flatMap { r -> r.inputs.map { it.obj } + r.product + r.tools }
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        for (id in ids) objs[id] = ObjType(id, "Item $id", tradeable = true)
        for (herb in data.cleaning.values) objs[herb.grimy] = ObjType(herb.grimy, "Grimy herb ${herb.grimy}", iops = CLEAN_IOPS, tradeable = true)
        objs[GRIMY_GUAM] = ObjType(GRIMY_GUAM, "Grimy guam leaf", iops = CLEAN_IOPS, tradeable = true)
        objs[GUAM] = ObjType(GUAM, "Guam leaf", tradeable = true)
        objs[UNICORN_HORN] = ObjType(UNICORN_HORN, "Unicorn horn", tradeable = true)
        World(
            crcs, CollisionMap().apply { markLoaded(SINK_X shr 6, SINK_Z shr 6) }, objTypes = ObjTypes(objs),
            locTypes = LocTypes(
                mapOf(Herblore.LUMBRIDGE_SINK to LocType(Herblore.LUMBRIDGE_SINK, "Sink", listOf(null, null, null, null, null), LocCollisionType(width = 1, length = 1))),
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(Herblore.LUMBRIDGE_SINK, LocShape.CENTREPIECE_STRAIGHT, 0, SINK_X, SINK_Z, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.use(id: Int, target: Int) =
        ifButtonT(InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(id), id, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(target), target)

    private fun HeadlessClient.box() = await("the Make-X box") { clientScripts().lastOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }

    private fun HeadlessClient.said(prefix: String) = messages().filter { it.text.startsWith(prefix) }

    private fun HeadlessClient.xp() = stat(HERBLORE)?.xp ?: 0

    @Test
    fun `Clean needs level 3, then cleans the clicked herb at once for 2_5 XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(GRIMY_GUAM, 2)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, CLEAN_BUTTON_OP, client.slotOf(GRIMY_GUAM), GRIMY_GUAM)
                client.seen("[herblore.clean_level level=3]")
                assertEquals(2, client.count(GRIMY_GUAM))
                client.command("setlevel herblore 3")
                val start = client.await("level 3") { client.xp().takeIf { it >= LEVEL_3_XP } }
                val second = client.inventory(BACKPACK).indexOfLast { it?.id == GRIMY_GUAM }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, CLEAN_BUTTON_OP, second, GRIMY_GUAM)
                client.seen("[herblore.clean input=Grimy guam leaf]")
                client.await("the clean one in its slot") { client.inventory(BACKPACK)[second]?.takeIf { it.id == GUAM } }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, CLEAN_BUTTON_OP, client.slotOf(GRIMY_GUAM), GRIMY_GUAM)
                client.await("two clean") { if (client.count(GUAM) == 2) Unit else null }
                assertEquals(5, client.await("5 XP") { (client.xp() - start).takeIf { it >= 5 } })
            }
        }
    }

    @Test
    fun `a guam and a vial of water make unfinished potions a tick apart, for no XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel herblore 3")
                client.give(GUAM, 3)
                client.give(VIAL_OF_WATER, 3)
                val start = client.await("level 3") { client.xp().takeIf { it >= LEVEL_3_XP } }
                client.use(VIAL_OF_WATER, GUAM)
                assertTrue(GUAM_UNF in client.box().args)
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 3)
                val made = client.await("three made", timeoutMillis = 20_000) { client.said("[herblore.unfinished").takeIf { it.size >= 3 } }
                assertEquals("[herblore.unfinished input=Guam leaf]", made[0].text)
                assertEquals(listOf(1, 1), made.zipWithNext { a, b -> b.tick - a.tick })
                assertEquals(3, client.count(GUAM_UNF))
                assertEquals(0, client.count(VIAL_OF_WATER))
                assertEquals(start, client.xp())
            }
        }
    }

    @Test
    fun `a pestle grinds the first horn as it is chosen, the next 2 ticks later and the rest every 3`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(PESTLE)
                client.give(UNICORN_HORN, 4)
                client.use(PESTLE, UNICORN_HORN)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 4)
                val ground = client.await("four ground", timeoutMillis = 20_000) { client.said("[herblore.grind").takeIf { it.size >= 4 } }
                assertEquals("[herblore.grind input=Unicorn horn]", ground[0].text)
                assertEquals(listOf(2, 3, 3), ground.zipWithNext { a, b -> b.tick - a.tick })
                client.await("four dust") { if (client.count(UNICORN_HORN_DUST) == 4) Unit else null }
                assertEquals(1, client.count(PESTLE))
            }
        }
    }

    @Test
    fun `a vial on the sink fills every vial held, a tick apart, with no box`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(VIAL, 3)
                client.locTarget(Herblore.LUMBRIDGE_SINK, SINK_X, SINK_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(VIAL), VIAL)
                val filled = client.await("three filled", timeoutMillis = 20_000) { client.said("[herblore.fill").takeIf { it.size >= 3 } }
                assertEquals(listOf(1, 1), filled.zipWithNext { a, b -> b.tick - a.tick })
                // The use happens on arrival, in the movement phase; the first vial fills the tick after.
                val arrived = client.moves().last { it.tick <= filled[0].tick }.tick
                assertEquals(1, filled[0].tick - arrived, "the first fill after arriving at the sink")
                client.await("three vials of water") { if (client.count(VIAL_OF_WATER) == 3) Unit else null }
                assertTrue(client.clientScripts().none { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT })
            }
        }
    }

    companion object {
        const val BACKPACK = 93
        const val HERBLORE = 15
        const val CLEAN_BUTTON_OP = 2
        const val LEVEL_3_XP = 174
        val CLEAN_IOPS = listOf(Herblore.CLEAN, null, null, null, "Drop")
        const val GRIMY_GUAM = 199
        const val GUAM = 249
        const val GUAM_UNF = 91
        const val VIAL = 229
        const val VIAL_OF_WATER = 227
        const val PESTLE = 233
        const val UNICORN_HORN = 237
        const val UNICORN_HORN_DUST = 235

        /** Lumbridge Castle's kitchen sink. */
        const val SINK_X = 3205
        const val SINK_Z = 3215
    }
}
