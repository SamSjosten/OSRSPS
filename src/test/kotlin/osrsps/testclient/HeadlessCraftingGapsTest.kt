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
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Crafting
import osrsps.game.skill.CraftingData
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Artisan 2 step A3 through the headless client: an orb on a battlestaff, a chisel on a snail shell and on amethyst, and
 * a loom's weaving. The rl-client script test-runner/examples/crafting-gaps.json does the same in the real client.
 */
class HeadlessCraftingGapsTest {
    private val data = CraftingData.load()

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val recipes = data.battlestaves.values + data.snelms.values + data.amethystCuts + data.weaving
        val ids = recipes.flatMap { r -> r.inputs.map { it.obj } + r.product + r.tools }
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        val stacks = data.amethystCuts.map { it.product }.toSet()
        for (id in ids) objs[id] = ObjType(id, "Item $id", stackable = id in stacks, tradeable = true)
        objs[WATER_BATTLESTAFF] = ObjType(WATER_BATTLESTAFF, "Water battlestaff", tradeable = true)
        objs[AMETHYST_BOLT_TIPS] = ObjType(AMETHYST_BOLT_TIPS, "Amethyst bolt tips", stackable = true, tradeable = true)
        objs[STRIP_OF_CLOTH] = ObjType(STRIP_OF_CLOTH, "Strip of cloth", tradeable = true)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(5),
            locTypes = LocTypes(
                mapOf(LOOM to LocType(LOOM, "Loom", listOf(Crafting.WEAVE, null, null, null, null), LocCollisionType(width = 1, length = 1))),
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(LOOM, LocShape.CENTREPIECE_STRAIGHT, 0, LOOM_X, LOOM_Z, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.made(prefix: String) = messages().filter { it.text.startsWith("[crafting.$prefix") }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.use(id: Int, target: Int) =
        ifButtonT(InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(id), id, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(target), target)

    /** The Make-X box, once more than [after] have opened. */
    private fun HeadlessClient.box(after: Int = 0) = await("the Make-X box") {
        clientScripts().filter { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }.takeIf { it.size > after }?.last()
    }

    private fun HeadlessClient.boxes() = clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }

    private fun HeadlessClient.ready(level: Int = 99) {
        await("the login rebuild") { position }
        command("setlevel crafting $level")
        await("level $level") { stat(CRAFTING)?.takeIf { it.level == level } }
    }

    private fun HeadlessClient.xpOver(base: Int) = (stat(CRAFTING)?.xp ?: 0) - base

    @Test
    fun `a water orb on a battlestaff needs 54, then makes water battlestaves 2 ticks apart for 100 XP each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready(53)
                client.give(BATTLESTAFF, 2)
                client.give(WATER_ORB, 2)
                client.use(WATER_ORB, BATTLESTAFF)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.seen("[crafting.level level=54 item=water battlestaff]")
                assertEquals(0, client.count(WATER_BATTLESTAFF))

                client.command("setlevel crafting 99")
                client.await("level 99") { client.stat(CRAFTING)?.takeIf { it.level == 99 } }
                client.use(BATTLESTAFF, WATER_ORB)
                client.box(after = 1)
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                val made = client.await("two staves") { client.made("battlestaff").takeIf { it.size >= 2 } }
                assertEquals(List(2) { "[crafting.battlestaff item=water battlestaff]" }, made.map { it.text })
                assertEquals(2, made[1].tick - made[0].tick)
                assertEquals(200, client.await("200 XP") { client.xpOver(XP_99).takeIf { it >= 200 } })
                assertEquals(2, client.count(WATER_BATTLESTAFF))
            }
        }
    }

    @Test
    fun `a chisel on round and pointed myre shells makes both snelms, 2 ticks each, 32,5 XP each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.give(CHISEL)
                client.give(MYRE_SHELL)
                client.give(POINTED_MYRE_SHELL)
                client.use(CHISEL, MYRE_SHELL)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.await("the myre snelm") { if (client.count(MYRE_SNELM) == 1) Unit else null }
                client.use(POINTED_MYRE_SHELL, CHISEL)
                client.box(after = 1)
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.await("the pointed myre snelm") { if (client.count(POINTED_MYRE_SNELM) == 1) Unit else null }
                assertEquals(65, client.await("65 XP") { client.xpOver(XP_99).takeIf { it >= 65 } })
                assertEquals(1, client.count(CHISEL), "the chisel is kept")
            }
        }
    }

    @Test
    fun `a chisel on amethyst offers the four cuts and makes 15 bolt tips a piece, 2 ticks apart`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                client.give(CHISEL)
                client.give(AMETHYST, 2)
                client.use(CHISEL, AMETHYST)
                val setup = client.box()
                assertEquals(4, data.amethystCuts.count { it.product in setup.args }, "the box: ${setup.args}")
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                val made = client.await("two cuts") { client.made("amethyst").takeIf { it.size >= 2 } }
                assertEquals(List(2) { "[crafting.amethyst amount=15 item=amethyst bolt tips]" }, made.map { it.text })
                assertEquals(2, made[1].tick - made[0].tick)
                assertEquals(30, client.count(AMETHYST_BOLT_TIPS))
                assertEquals(120, client.await("120 XP") { client.xpOver(XP_99).takeIf { it >= 120 } })
            }
        }
    }

    @Test
    fun `Weave with nothing does nothing, then wool weaves strips of cloth 3 ticks apart and jute offers a sack or a drift net`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready()
                client.locOp(LOOM, LOOM_X, LOOM_Z, 1)
                client.seen("[game.nothing_interesting]")
                client.give(BALL_OF_WOOL, 8)
                client.locOp(LOOM, LOOM_X, LOOM_Z, 1)
                val setup = client.box()
                assertTrue(STRIP_OF_CLOTH in setup.args, "the box: ${setup.args}")
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                val made = client.await("two strips") { client.made("weave").takeIf { it.size >= 2 } }
                assertEquals(List(2) { "[crafting.weave item=strip of cloth]" }, made.map { it.text })
                assertEquals(3, made[1].tick - made[0].tick)
                assertEquals(2, client.count(STRIP_OF_CLOTH))
                assertEquals(0, client.count(BALL_OF_WOOL))
                assertEquals(24, client.await("24 XP") { client.xpOver(XP_99).takeIf { it >= 24 } })

                client.give(JUTE, 4)
                val boxes = client.boxes()
                client.locTarget(LOOM, LOOM_X, LOOM_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(JUTE), JUTE)
                val jute = client.box(after = boxes)
                assertTrue(EMPTY_SACK in jute.args && DRIFT_NET in jute.args, "the jute box: ${jute.args}")
                assertTrue(STRIP_OF_CLOTH !in jute.args, "only what jute makes: ${jute.args}")
            }
        }
    }

    private companion object {
        const val CRAFTING = 12
        const val XP_99 = 13_034_431
        const val BACKPACK = 93
        const val LOOM = 8717
        const val LOOM_X = 3224
        const val LOOM_Z = 3218
        const val BATTLESTAFF = 1391
        const val WATER_ORB = 571
        const val WATER_BATTLESTAFF = 1395
        const val CHISEL = 1755
        const val MYRE_SHELL = 3345
        const val POINTED_MYRE_SHELL = 3355
        const val MYRE_SNELM = 3327
        const val POINTED_MYRE_SNELM = 3337
        const val AMETHYST = 21347
        const val AMETHYST_BOLT_TIPS = 21338
        const val BALL_OF_WOOL = 1759
        const val STRIP_OF_CLOTH = 3224
        const val JUTE = 5931
        const val EMPTY_SACK = 5418
        const val DRIFT_NET = 21652
    }
}
