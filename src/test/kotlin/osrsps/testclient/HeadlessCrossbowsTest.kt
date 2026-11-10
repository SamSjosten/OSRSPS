package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.AllSettings
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.skill.Fletching
import osrsps.game.skill.FletchingData
import osrsps.map.CollisionMap

/**
 * Artisan 2 step A1 through the headless client: crossbows from a stock, limbs and a string; bolts from unfinished bolts
 * and feathers; gem bolt tips cut with a chisel and attached to bolts; javelins from tips and shafts.
 */
class HeadlessCrossbowsTest {
    private val data = FletchingData.load()

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val recipes = data.limbs + data.crossbows.values + data.bolts.values + data.tippedBolts + data.boltTips.values + data.javelins.values
        val ids = recipes.flatMap { r -> r.inputs.map { it.obj } + r.tools + r.product } + data.hammer + data.chisel + data.crossbowString
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        val stacks = (data.bolts.values + data.tippedBolts + data.javelins.values).flatMap { r -> r.inputs.map { it.obj } + r.product }.toSet() +
            data.boltTips.values.map { it.product }
        for (id in ids) objs[id] = ObjType(id, "Item $id", stackable = id in stacks, tradeable = true)
        objs[BRONZE_XBOW_U] = ObjType(BRONZE_XBOW_U, "Bronze crossbow (u)", tradeable = true)
        objs[BRONZE_XBOW] = ObjType(BRONZE_XBOW, "Bronze crossbow", tradeable = true)
        objs[BRONZE_BOLTS] = ObjType(BRONZE_BOLTS, "Bronze bolts", stackable = true, tradeable = true)
        objs[OPAL_BOLTS] = ObjType(OPAL_BOLTS, "Opal bolts", stackable = true, tradeable = true)
        objs[OPAL_TIPS] = ObjType(OPAL_TIPS, "Opal bolt tips", stackable = true, tradeable = true)
        objs[BRONZE_JAVELIN] = ObjType(BRONZE_JAVELIN, "Bronze javelin", stackable = true, tradeable = true)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(3),
            cycleMillis = millis, varTypes = VarTypes(6_000, AllSettings.VARBITS),
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.made() = messages().filter { it.text.startsWith("[fletching.") && !it.text.startsWith("[fletching.level") }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.use(id: Int, target: Int) =
        ifButtonT(InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(id), id, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(target), target)

    private fun HeadlessClient.box() = await("the Make-X box") { clientScripts().lastOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }

    private fun HeadlessClient.ready(level: Int = 99) {
        await("the login rebuild") { position }
        command("setlevel fletching $level")
        await("level $level") { stat(FLETCHING)?.takeIf { it.level == level } }
    }

    private fun HeadlessClient.xpOver(base: Int) = (stat(FLETCHING)?.xp ?: 0) - base

    @Test
    fun `bronze limbs on a wooden stock need a hammer, then make the crossbow (u) for 12 XP, and a string makes the crossbow for 6`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.give(WOODEN_STOCK, 2)
                client.give(BRONZE_LIMBS, 2)
                client.use(BRONZE_LIMBS, WOODEN_STOCK)
                client.awaitTicks(4)
                assertTrue(client.clientScripts().none { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }, "no box without a hammer")
                assertEquals(0, client.count(BRONZE_XBOW_U))
                client.give(data.hammer)
                client.use(WOODEN_STOCK, BRONZE_LIMBS)
                val setup = client.box()
                assertTrue(BRONZE_XBOW_U in setup.args && 2 in setup.args, "the crossbow (u), up to 2: ${setup.args}")
                val chose = client.ticksSeen
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                val limbs = client.await("two crossbows (u)") { client.made().takeIf { it.size >= 2 } }
                assertEquals("[fletching.limbs item=bronze crossbow (u)]", limbs[0].text)
                assertTrue(limbs[0].tick - chose in 1..3, "the first ${limbs[0].tick - chose} ticks after the choice")
                assertEquals(2, limbs[1].tick - limbs[0].tick)
                client.await("two crossbows (u)") { if (client.count(BRONZE_XBOW_U) == 2) Unit else null }
                assertEquals(1, client.count(data.hammer), "the hammer is kept")
                assertEquals(24, client.await("24 XP") { client.xpOver(XP_99).takeIf { it >= 24 } })

                client.give(data.crossbowString)
                val boxes = client.clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }
                client.use(data.crossbowString, BRONZE_XBOW_U)
                client.await("a second box") { client.clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }.takeIf { it > boxes } }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.seen("[fletching.crossbow_string item=bronze crossbow]")
                client.awaitTicks(4)
                assertEquals(1, client.count(BRONZE_XBOW), "one string strings one crossbow")
                assertEquals(1, client.count(BRONZE_XBOW_U))
                assertEquals(30, client.await("30 XP") { client.xpOver(XP_99).takeIf { it >= 30 } })
            }
        }
    }

    @Test
    fun `a feather on unfinished bolts makes 10 bolts a click with no box, and bronze bolts need 9`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready(level = 8)
                client.give(BRONZE_UNF, 25)
                client.give(FEATHER, 25)
                client.use(FEATHER, BRONZE_UNF)
                client.seen("[fletching.level level=9 item=bronze bolts]")
                client.ready(level = 99)
                for (expected in listOf(10, 20, 25)) {
                    client.use(FEATHER, BRONZE_UNF)
                    client.await("$expected bolts") { if (client.count(BRONZE_BOLTS) == expected) Unit else null }
                }
                assertTrue(client.clientScripts().none { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }, "no Make-X box for bolts")
                assertEquals(listOf(10, 10, 5).map { "[fletching.bolts amount=$it item=bronze bolts]" }, client.made().map { it.text })
                // 0.5 XP a bolt: 12.5, which the client shows as 12.
                assertEquals(12, client.await("12 XP") { client.xpOver(XP_99).takeIf { it >= 12 } })
            }
        }
    }

    @Test
    fun `with the Make-X for darts and bolts setting on, feathering bolts opens the box`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                client.command("varbit ${Fletching.VARBIT_DARTS_MAKEX} 1")
                client.give(BRONZE_UNF, 30)
                client.give(FEATHER, 30)
                client.use(FEATHER, BRONZE_UNF)
                val setup = client.box()
                assertTrue("[fletching.bolt_sets_title]" in setup.args, "the bolts' title: ${setup.args}")
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                client.await("20 bolts") { if (client.count(BRONZE_BOLTS) == 20) Unit else null }
                client.awaitTicks(6)
                assertEquals(20, client.count(BRONZE_BOLTS), "made more sets than asked")
            }
        }
    }

    @Test
    fun `a chisel on an opal cuts 12 opal bolt tips for 1,5 XP, 5 ticks a gem, and needs 11`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready(level = 10)
                client.give(data.chisel)
                client.give(OPAL, 2)
                client.use(data.chisel, OPAL)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                client.seen("[fletching.level level=11 item=opal bolt tips]")
                client.ready()
                val boxes = client.clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }
                client.use(OPAL, data.chisel)
                client.await("a second box") { client.clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }.takeIf { it > boxes } }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                val cuts = client.await("two opals cut") { client.made().takeIf { it.size >= 2 } }
                assertEquals("[fletching.bolt_tips amount=12 item=opal bolt tips]", cuts[0].text)
                assertEquals(5, cuts[1].tick - cuts[0].tick)
                client.await("24 tips") { if (client.count(OPAL_TIPS) == 24) Unit else null }
                assertEquals(0, client.count(OPAL))
                assertEquals(3, client.await("3 XP") { client.xpOver(XP_99).takeIf { it >= 3 } })
            }
        }
    }

    @Test
    fun `opal tips on 20 bronze bolts make opal bolts in sets of 10, 2 ticks apart, 1,6 XP each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.ready()
                client.give(BRONZE_BOLTS, 20)
                client.give(OPAL_TIPS, 20)
                client.use(OPAL_TIPS, BRONZE_BOLTS)
                val setup = client.box()
                assertTrue(OPAL_BOLTS in setup.args && 2 in setup.args, "opal bolts, up to 2 sets: ${setup.args}")
                assertTrue("[fletching.bolt_sets_title]" in setup.args, "the bolts' title: ${setup.args}")
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                val sets = client.await("two sets") { client.made().takeIf { it.size >= 2 } }
                assertEquals(listOf(10, 10).map { "[fletching.tipped_bolts amount=$it item=opal bolts]" }, sets.map { it.text })
                assertEquals(2, sets[1].tick - sets[0].tick)
                client.await("20 opal bolts") { if (client.count(OPAL_BOLTS) == 20) Unit else null }
                assertEquals(0, client.count(BRONZE_BOLTS))
                assertEquals(32, client.await("32 XP") { client.xpOver(XP_99).takeIf { it >= 32 } })
            }
        }
    }

    @Test
    fun `bronze javelin tips on 20 shafts make javelins 15 then 5, a tick apart, 1 XP each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fred").use { client ->
                client.ready()
                client.give(data.javelinShaft, 20)
                client.give(BRONZE_JAVELIN_TIPS, 20)
                client.use(BRONZE_JAVELIN_TIPS, data.javelinShaft)
                val setup = client.box()
                assertTrue("[fletching.sets_title]" in setup.args, "the sets of 15 title: ${setup.args}")
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                val sets = client.await("two sets") { client.made().takeIf { it.size >= 2 } }
                assertEquals(listOf(15, 5).map { "[fletching.javelins amount=$it item=bronze javelin]" }, sets.map { it.text })
                assertEquals(1, sets[1].tick - sets[0].tick)
                client.await("20 javelins") { if (client.count(BRONZE_JAVELIN) == 20) Unit else null }
                assertEquals(20, client.await("20 XP") { client.xpOver(XP_99).takeIf { it >= 20 } })
            }
        }
    }

    private companion object {
        const val WOODEN_STOCK = 9440
        const val BRONZE_LIMBS = 9420
        const val BRONZE_XBOW_U = 9454
        const val BRONZE_XBOW = 9174
        const val FEATHER = 314
        const val BRONZE_UNF = 9375
        const val BRONZE_BOLTS = 877
        const val OPAL = 1609
        const val OPAL_TIPS = 45
        const val OPAL_BOLTS = 879
        const val BRONZE_JAVELIN_TIPS = 19570
        const val BRONZE_JAVELIN = 825
        const val BACKPACK = 93
        const val FLETCHING = 9
        const val XP_99 = 13_034_431
    }
}
