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
 * Artisan step 3 onwards through the headless client: Crafting's spinning wheel. The rl-client script
 * test-runner/examples/crafting-spinning.json spins on Lumbridge Castle's wheel.
 */
class HeadlessCraftingTest {
    private val data = CraftingData.load()

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val ids = data.spinning.flatMap { r -> r.inputs.map { it.obj } + r.product }
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        for (id in ids) objs[id] = ObjType(id, "Item $id", tradeable = true)
        objs[WOOL] = ObjType(WOOL, "Wool", tradeable = true)
        objs[BALL_OF_WOOL] = ObjType(BALL_OF_WOOL, "Ball of wool", tradeable = true)
        objs[BOW_STRING] = ObjType(BOW_STRING, "Bow string", tradeable = true)
        val spin = listOf(null, Crafting.SPIN, null, null, null)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(5),
            locTypes = LocTypes(
                mapOf(Crafting.LUMBRIDGE_WHEEL to LocType(Crafting.LUMBRIDGE_WHEEL, "Spinning wheel", spin, LocCollisionType(width = 1, length = 1))),
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(Crafting.LUMBRIDGE_WHEEL, LocShape.CENTREPIECE_STRAIGHT, 0, WHEEL_X, WHEEL_Z, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.spun() = messages().filter { it.text.startsWith("[crafting.spin") }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.box() = await("the Make-X box") { clientScripts().lastOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }

    @Test
    fun `Spin with nothing to spin does nothing, then offers wool and flax and spins three balls 3 ticks apart`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel crafting 99")
                client.locOp(Crafting.LUMBRIDGE_WHEEL, WHEEL_X, WHEEL_Z, 2)
                client.seen("[game.nothing_interesting]")
                client.give(WOOL, 3)
                client.give(FLAX, 1)
                client.locOp(Crafting.LUMBRIDGE_WHEEL, WHEEL_X, WHEEL_Z, 2)
                val setup = client.box()
                assertTrue(BALL_OF_WOOL in setup.args && BOW_STRING in setup.args, "both: ${setup.args}")
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 3)
                val balls = client.await("three balls", timeoutMillis = 20_000) { client.spun().takeIf { it.size >= 3 } }
                assertEquals("[crafting.spin item=ball of wool]", balls[0].text)
                assertEquals(listOf(3, 3), balls.zipWithNext { a, b -> b.tick - a.tick })
                client.await("three balls held") { if (client.count(BALL_OF_WOOL) == 3) Unit else null }
                assertEquals(1, client.count(FLAX))
                assertEquals(7, client.await("Crafting XP") { client.stat(CRAFTING)?.takeIf { it.xp > 13_034_431 } }.xp - 13_034_431)
            }
        }
    }

    @Test
    fun `flax used on the wheel at level 1 needs 10`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(FLAX, 2)
                client.locTarget(Crafting.LUMBRIDGE_WHEEL, WHEEL_X, WHEEL_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(FLAX), FLAX)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                client.seen("[crafting.level level=10 item=bow string]")
                client.awaitTicks(6)
                assertEquals(2, client.count(FLAX))
            }
        }
    }

    private companion object {
        const val WHEEL_X = 3224
        const val WHEEL_Z = 3218
        const val WOOL = 1737
        const val BALL_OF_WOOL = 1759
        const val FLAX = 1779
        const val BOW_STRING = 1777
        const val BACKPACK = 93
        const val CRAFTING = 12
    }
}
