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
import osrsps.game.skill.Cooking
import osrsps.game.skill.Firemaking
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Gathering step 6 through the headless client: food used on a range or fire, the Make-X box, the 4-tick cooking clock
 * and the checks before it. The rl-client script test-runner/examples/cooking-lumbridge.json cooks in the castle kitchen.
 */
class HeadlessCookingTest {
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Cooking.SHRIMPS.burnt + 1)
        for (food in Cooking.FOODS) {
            for (id in listOf(food.raw, food.cooked, food.burnt)) objs[id] = ObjType(id, "Food $id")
        }
        val cook = listOf("Cook", null, null, null, null)
        val none = listOf<String?>(null, null, null, null, null)
        fun loc(id: Int, name: String, ops: List<String?>) = LocType(id, name, ops, LocCollisionType(width = 1, length = 1))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(7),
            locTypes = LocTypes(
                listOf(loc(Cooking.RANGE, "Range", cook), loc(Cooking.LUMBRIDGE_RANGE, "Cooking range", cook), loc(Firemaking.FIRE, "Fire", none))
                    .associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Cooking.RANGE, LocShape.CENTREPIECE_STRAIGHT, 0, RANGE_X, RANGE_Z, 0))
                add(ZoneLoc(Firemaking.FIRE, LocShape.CENTREPIECE_STRAIGHT, 0, FIRE_X, FIRE_Z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.slotOf(id: Int) = inventory(BACKPACK).indexOfFirst { it?.id == id }

    private fun HeadlessClient.cooked() = messages().filter { it.text.startsWith("[cooking.cooked_") || it.text.startsWith("[cooking.burnt_") }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    @Test
    fun `one raw shrimp on a range cooks straight away, 3 ticks on, for 30 XP at 99`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel cooking 99")
                client.await("level 99") { client.stat(COOKING)?.takeIf { it.level == 99 } }
                client.give(Cooking.SHRIMPS.raw)
                val used = client.ticksSeen
                client.locTarget(Cooking.RANGE, RANGE_X, RANGE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(Cooking.SHRIMPS.raw), Cooking.SHRIMPS.raw)
                val cooked = client.seen("[cooking.cooked_shrimps]")
                assertTrue(client.clientScripts().none { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }, "no Make-X box for one shrimp")
                client.await("the shrimps") { if (client.count(Cooking.SHRIMPS.cooked) == 1) Unit else null }
                assertEquals(0, client.count(Cooking.SHRIMPS.raw))
                assertEquals(30, client.await("Cooking XP") { client.stat(COOKING)?.takeIf { it.xp > 13_034_431 } }.xp - 13_034_431)
                assertTrue(cooked.tick - used in 3..6, "cooked ${cooked.tick - used} ticks after the use")
            }
        }
    }

    @Test
    fun `the Make-X box over five shrimps cooks the three chosen, one every 4 ticks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel cooking 99")
                client.give(Cooking.SHRIMPS.raw, 5)
                client.locTarget(Cooking.RANGE, RANGE_X, RANGE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(Cooking.SHRIMPS.raw), Cooking.SHRIMPS.raw)
                val setup = client.await("the Make-X box") { client.clientScripts().firstOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }
                assertTrue(Cooking.SHRIMPS.raw in setup.args, "the box shows the raw shrimps: ${setup.args}")
                assertTrue(5 in setup.args, "up to 5: ${setup.args}")
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 3)
                val cooks = client.await("three cooked", timeoutMillis = 30_000) { client.cooked().takeIf { it.size >= 3 } }
                assertEquals(listOf(4, 4), cooks.zipWithNext { a, b -> b.tick - a.tick })
                client.await("12 ticks on") { if (client.ticksSeen >= cooks.last().tick + 12) Unit else null }
                assertEquals(3, client.cooked().size)
                assertEquals(2, client.count(Cooking.SHRIMPS.raw))
                assertEquals(3, client.count(Cooking.SHRIMPS.cooked))
            }
        }
    }

    @Test
    fun `bread dough won't cook on a fire, trout needs 15, and level 1 burns some until the level-up ends it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(Cooking.BREAD.raw)
                client.locTarget(Firemaking.FIRE, FIRE_X, FIRE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(Cooking.BREAD.raw), Cooking.BREAD.raw)
                client.seen("[cooking.range_only]")
                client.give(Cooking.TROUT.raw)
                client.locTarget(Firemaking.FIRE, FIRE_X, FIRE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(Cooking.TROUT.raw), Cooking.TROUT.raw)
                client.seen("[cooking.level level=15]")

                // Level 1 on a fire: about half burn, and the third shrimp cooked (90 XP) levels up, which ends the job.
                client.give(Cooking.SHRIMPS.raw, 10)
                client.locTarget(Firemaking.FIRE, FIRE_X, FIRE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(Cooking.SHRIMPS.raw), Cooking.SHRIMPS.raw)
                client.await("the Make-X box") { client.clientScripts().firstOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 10)
                client.seen("[levelup.message skill=Cooking level=2]")
                val stopped = client.ticksSeen
                client.await("12 ticks on") { if (client.ticksSeen >= stopped + 12) Unit else null }
                val cooked = client.count(Cooking.SHRIMPS.cooked)
                val burnt = client.count(Cooking.SHRIMPS.burnt)
                assertEquals(3, cooked)
                assertEquals(cooked + burnt, client.cooked().size, "nothing cooked after the level-up")
                assertEquals(10 - cooked - burnt, client.count(Cooking.SHRIMPS.raw))
                assertTrue(burnt > 0, "some burn at level 1")
            }
        }
    }

    @Test
    fun `Cook on a range offers every raw food held and cooks the one picked`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel cooking 99")
                client.give(Cooking.SHRIMPS.raw, 2)
                client.give(Cooking.SARDINE.raw, 2)
                client.locOp(Cooking.RANGE, RANGE_X, RANGE_Z, 1)
                val setup = client.await("the Make-X box") { client.clientScripts().firstOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }
                assertTrue(Cooking.SHRIMPS.raw in setup.args && Cooking.SARDINE.raw in setup.args, "both foods: ${setup.args}")
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 1, 2)
                client.await("two sardines", timeoutMillis = 30_000) { if (client.count(Cooking.SARDINE.cooked) == 2) Unit else null }
                assertEquals(2, client.count(Cooking.SHRIMPS.raw))
            }
        }
    }

    private companion object {
        const val RANGE_X = 3224
        const val RANGE_Z = 3218
        const val FIRE_X = 3220
        const val FIRE_Z = 3218
        const val BACKPACK = 93
        const val COOKING = 7
    }
}
