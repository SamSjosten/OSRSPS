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
import osrsps.game.skill.FletchingData
import osrsps.map.CollisionMap

/**
 * Artisan step 1 through the headless client: a knife on logs, the Make-X box over what they make, and the shared make
 * loop's timing and stops. The rl-client script test-runner/examples/fletching-logs.json does the same in the real
 * client.
 */
class HeadlessFletchingTest {
    private val data = FletchingData.load()

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val recipes = data.knifeOnLogs.values.flatten() + data.stringing.values + listOfNotNull(data.headless) + data.arrows.values + data.darts.values
        val ids = recipes.flatMap { r -> r.inputs.map { it.obj } + r.product } + data.knife
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        val stacks = (data.arrows.values + data.darts.values + listOfNotNull(data.headless)).flatMap { r -> r.inputs.map { it.obj } + r.product }.toSet()
        for (id in ids) objs[id] = ObjType(id, "Item $id", stackable = id in stacks, tradeable = true)
        objs[SHAFTS] = ObjType(SHAFTS, "Arrow shaft", stackable = true, tradeable = true)
        objs[JAVELIN_SHAFTS] = ObjType(JAVELIN_SHAFTS, "Javelin shaft", stackable = true, tradeable = true)
        objs[SHORTBOW_U] = ObjType(SHORTBOW_U, "Shortbow (u)", tradeable = true)
        objs[LONGBOW_U] = ObjType(LONGBOW_U, "Longbow (u)", tradeable = true)
        objs[SHORTBOW] = ObjType(SHORTBOW, "Shortbow", tradeable = true)
        objs[BRONZE_ARROW] = ObjType(BRONZE_ARROW, "Bronze arrow", stackable = true, tradeable = true)
        objs[BRONZE_DART] = ObjType(BRONZE_DART, "Bronze dart", stackable = true, tradeable = true)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(3),
            cycleMillis = millis,
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

    private fun HeadlessClient.ready(name: String, level: Int = 99) {
        await("the login rebuild") { position }
        if (level > 1) {
            command("setlevel fletching $level")
            await("level $level") { stat(FLETCHING)?.takeIf { it.level == level } }
        }
        give(data.knife)
    }

    @Test
    fun `a knife on logs offers all five products and cuts the two shortbows chosen, 3 ticks apart, 5 XP each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready("alice")
                client.give(LOGS, 3)
                client.use(data.knife, LOGS)
                val setup = client.box()
                for (product in listOf(SHAFTS, JAVELIN_SHAFTS, SHORTBOW_U, LONGBOW_U, WOODEN_STOCK)) {
                    assertTrue(product in setup.args, "the box shows $product: ${setup.args}")
                }
                assertTrue(3 in setup.args, "up to 3: ${setup.args}")
                val chose = client.ticksSeen
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 2, 2)
                val cuts = client.await("two bows") { client.made().takeIf { it.size >= 2 } }
                assertEquals("[fletching.bow item=shortbow (u)]", cuts[0].text)
                assertTrue(cuts[0].tick - chose in 2..4, "the first ${cuts[0].tick - chose} ticks after the choice")
                assertEquals(3, cuts[1].tick - cuts[0].tick)
                client.awaitTicks(8)
                assertEquals(2, client.made().size)
                assertEquals(2, client.count(SHORTBOW_U))
                assertEquals(1, client.count(LOGS))
                assertEquals(10, client.await("Fletching XP") { client.stat(FLETCHING)?.takeIf { it.xp > 13_034_431 } }.xp - 13_034_431)
            }
        }
    }

    @Test
    fun `logs on a knife work too, and a longbow at level 1 needs 10`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready("bob", level = 1)
                client.give(LOGS)
                client.use(LOGS, data.knife)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 3, 1)
                client.seen("[fletching.level level=10 item=longbow (u)]")
                client.awaitTicks(6)
                assertEquals(1, client.count(LOGS))
                assertEquals(0, client.made().size)
            }
        }
    }

    @Test
    fun `arrow shafts come 15 a log, and the level-up at 17 logs ends the job`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready("carol", level = 1)
                client.give(LOGS, 20)
                client.use(data.knife, LOGS)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 20)
                // 5 XP a log: the 17th reaches the 83 that level 2 needs.
                client.seen("[levelup.message skill=Fletching level=2]")
                assertTrue(client.made().all { it.text == "[fletching.shafts amount=15]" })
                client.awaitTicks(10)
                assertEquals(17, client.made().size)
                assertEquals(17 * 15, client.count(SHAFTS))
                assertEquals(3, client.count(LOGS))
            }
        }
    }

    @Test
    fun `walking away stops the cutting`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready("dave")
                client.give(LOGS, 10)
                client.use(data.knife, LOGS)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 2, 10)
                client.await("the first bow") { client.made().takeIf { it.isNotEmpty() } }
                val at = client.position!!
                client.walk(at.x + 3, at.z)
                client.awaitTicks(10)
                val cut = client.made().size
                assertTrue(cut in 1..2, "stopped after $cut")
                assertEquals(10 - cut, client.count(LOGS))
            }
        }
    }

    @Test
    fun `using one item on another ends the cutting`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ivy").use { client ->
                client.ready("ivy")
                client.give(LOGS, 10)
                client.use(data.knife, LOGS)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 2, 10)
                client.await("the first bow") { client.made().takeIf { it.isNotEmpty() } }
                client.use(LOGS, SHORTBOW_U)
                client.awaitTicks(10)
                val cut = client.made().size
                assertTrue(cut in 1..2, "stopped after $cut")
                assertEquals(10 - cut, client.count(LOGS))
            }
        }
    }

    @Test
    fun `shafts that won't fit on a full stack leave the log, with a message`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("jack").use { client ->
                client.ready("jack")
                client.give(SHAFTS, Int.MAX_VALUE - 5)
                client.give(LOGS)
                client.use(data.knife, LOGS)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.seen("[make.no_space]")
                client.awaitTicks(4)
                assertEquals(1, client.count(LOGS))
                assertEquals(Int.MAX_VALUE - 5, client.count(SHAFTS))
                assertEquals(0, client.made().size)
            }
        }
    }

    private fun HeadlessClient.xpOver99() = (stat(FLETCHING)?.xp ?: 0) - 13_034_431

    @Test
    fun `a bow string on two shortbows (u) strings both, 2 ticks apart, 5 XP each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.ready("erin")
                client.give(SHORTBOW_U, 2)
                client.give(BOW_STRING, 2)
                client.use(BOW_STRING, SHORTBOW_U)
                val setup = client.box()
                assertTrue(SHORTBOW in setup.args && 2 in setup.args, "the shortbow, up to 2: ${setup.args}")
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                val strung = client.await("two strung") { client.made().takeIf { it.size >= 2 } }
                assertEquals("[fletching.string item=shortbow]", strung[0].text)
                assertEquals(2, strung[1].tick - strung[0].tick)
                client.await("two shortbows") { if (client.count(SHORTBOW) == 2) Unit else null }
                assertEquals(0, client.count(BOW_STRING))
                assertEquals(10, client.await("10 XP") { client.xpOver99().takeIf { it >= 10 } })
            }
        }
    }

    @Test
    fun `feathers on 20 shafts make headless arrows 15 then 5, in sets, 1 XP each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fred").use { client ->
                client.ready("fred")
                client.give(SHAFTS, 20)
                client.give(FEATHER, 20)
                client.use(FEATHER, SHAFTS)
                val setup = client.box()
                assertTrue(HEADLESS in setup.args && 2 in setup.args, "headless arrows, up to 2 sets: ${setup.args}")
                assertTrue(setup.args.any { it == "[fletching.sets_title]" }, "the sets title: ${setup.args}")
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                val sets = client.await("two sets") { client.made().takeIf { it.size >= 2 } }
                assertEquals(listOf("[fletching.headless amount=15]", "[fletching.headless amount=5]"), sets.map { it.text })
                assertEquals(2, sets[1].tick - sets[0].tick)
                client.await("20 headless") { if (client.count(HEADLESS) == 20) Unit else null }
                assertEquals(0, client.count(SHAFTS))
                assertEquals(0, client.count(FEATHER))
                assertEquals(20, client.await("20 XP") { client.xpOver99().takeIf { it >= 20 } })
            }
        }
    }

    @Test
    fun `bronze arrowtips on 15 headless arrows make 15 bronze arrows for 19,5 XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gina").use { client ->
                client.ready("gina")
                client.give(HEADLESS, 15)
                client.give(BRONZE_TIPS, 15)
                client.use(BRONZE_TIPS, HEADLESS)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.seen("[fletching.arrows amount=15 item=bronze arrow]")
                client.await("15 arrows") { if (client.count(BRONZE_ARROW) == 15) Unit else null }
                // The client sees whole XP: 19.5 shows as 19.
                assertEquals(19, client.await("19 XP") { client.xpOver99().takeIf { it >= 19 } })
            }
        }
    }

    @Test
    fun `a feather on dart tips makes 10 darts a click with no box, and bronze darts need 10`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hank").use { client ->
                client.ready("hank", level = 1)
                client.give(BRONZE_DART_TIPS, 25)
                client.give(FEATHER, 25)
                client.use(FEATHER, BRONZE_DART_TIPS)
                client.seen("[fletching.level level=10 item=bronze dart]")
                client.command("setlevel fletching 99")
                client.await("level 99") { client.stat(FLETCHING)?.takeIf { it.level == 99 } }
                for (expected in listOf(10, 20, 25)) {
                    client.use(FEATHER, BRONZE_DART_TIPS)
                    client.await("$expected darts") { if (client.count(BRONZE_DART) == expected) Unit else null }
                }
                assertTrue(client.clientScripts().none { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }, "no Make-X box for darts")
                assertEquals(listOf(10, 10, 5).map { "[fletching.darts amount=$it item=bronze dart]" }, client.made().map { it.text })
                assertEquals(45, client.await("45 XP") { client.xpOver99().takeIf { it >= 45 } })
            }
        }
    }

    private companion object {
        const val SHORTBOW = 841
        const val BOW_STRING = 1777
        const val FEATHER = 314
        const val HEADLESS = 53
        const val BRONZE_TIPS = 39
        const val BRONZE_ARROW = 882
        const val BRONZE_DART_TIPS = 819
        const val BRONZE_DART = 806
        const val LOGS = 1511
        const val SHAFTS = 52
        const val JAVELIN_SHAFTS = 19584
        const val SHORTBOW_U = 50
        const val LONGBOW_U = 48
        const val WOODEN_STOCK = 9440
        const val BACKPACK = 93
        const val FLETCHING = 9
    }
}
