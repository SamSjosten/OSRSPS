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
        val ids = data.knifeOnLogs.flatMap { (log, recipes) -> listOf(log) + recipes.map { it.product } } + data.knife
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        for (id in ids) objs[id] = ObjType(id, "Item $id", tradeable = true)
        objs[SHAFTS] = ObjType(SHAFTS, "Arrow shaft", stackable = true, tradeable = true)
        objs[JAVELIN_SHAFTS] = ObjType(JAVELIN_SHAFTS, "Javelin shaft", stackable = true, tradeable = true)
        objs[SHORTBOW_U] = ObjType(SHORTBOW_U, "Shortbow (u)", tradeable = true)
        objs[LONGBOW_U] = ObjType(LONGBOW_U, "Longbow (u)", tradeable = true)
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

    private companion object {
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
