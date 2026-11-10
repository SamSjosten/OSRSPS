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
import osrsps.game.bank.BankScreen
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Churn
import osrsps.game.skill.Cooking
import osrsps.game.skill.CookingMixes
import osrsps.game.skill.Wine
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Fishing and Cooking 2 step FC5b through the headless client: a stew and a curry mixed and cooked on a range, wine that
 * ferments 20 ticks after the last jug is made (in the backpack and the bank), and the dairy churn. The rl-client
 * script test-runner/examples/lumbridge-stew-wine-churn.json does the same in the real client.
 */
class HeadlessStewWineChurnTest {
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 20) { crcs, millis ->
        val ids = CookingMixes.PAIRS.flatMap { (a, b, recipes) ->
            listOf(a, b) + recipes.flatMap { r -> r.inputs.map { it.obj } + r.product + r.byproducts.map { it.obj } }
        } + Cooking.FOODS.flatMap { listOf(it.raw, it.cooked, it.burnt) } + Churn.RECIPES.flatMap { r ->
            r.inputs.map { it.obj } + r.product + r.byproducts.map { it.obj }
        } + listOf(Wine.JUG_OF_WINE, Wine.BAD_WINE)
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        for (id in ids) objs[id] = ObjType(id, NAMES[id] ?: "Item $id")
        val box = LocCollisionType(width = 1, length = 1)
        World(
            crcs, CollisionMap().apply { markLoaded(RANGE_X shr 6, RANGE_Z shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(17),
            locTypes = LocTypes(
                mapOf(
                    Cooking.RANGE to LocType(Cooking.RANGE, "Range", listOf("Cook", null, null, null, null), box),
                    CHURN to LocType(CHURN, "Dairy churn", listOf("Churn", null, null, null, null), box),
                ),
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Cooking.RANGE, LocShape.CENTREPIECE_STRAIGHT, 0, RANGE_X, RANGE_Z, 0))
                add(ZoneLoc(CHURN, LocShape.CENTREPIECE_STRAIGHT, 0, CHURN_X, CHURN_Z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().lastOrNull { it.text == text } }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.level(level: Int) {
        command("setlevel cooking $level")
        await("Cooking $level") { stat(COOKING)?.takeIf { it.level == level } }
    }

    private fun HeadlessClient.use(id: Int, target: Int) =
        ifButtonT(InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(id), id, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(target), target)

    private fun HeadlessClient.boxes() = clientScripts().count { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }

    private fun HeadlessClient.awaitBox(before: Int): List<Any> =
        await("the Make-X box") { clientScripts().filter { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }.drop(before).lastOrNull() }.args

    /** Uses [id] on [target], which makes one straight away, and waits for [product]. */
    private fun HeadlessClient.make(id: Int, target: Int, product: Int) {
        val before = count(product)
        use(id, target)
        await("item $product") { if (count(product) == before + 1) Unit else null }
    }

    private fun HeadlessClient.xp() = stat(COOKING)!!.xp

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z")
        await("the teleport") { position?.takeIf { it.x == x && it.z == z } }
    }

    @Test
    fun `a potato and cooked meat in a bowl of water make a stew, spice from 60 makes it a curry, and the range cooks it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(RANGE_X - 1, RANGE_Z)
                client.level(99)
                client.give(BOWL_OF_WATER, 2)
                client.give(POTATO, 2)
                client.give(COOKED_MEAT, 2)
                client.give(SPICE)
                val start = client.xp()
                // Two of each ask how many; take one at a time here.
                val before = client.boxes()
                client.use(POTATO, BOWL_OF_WATER)
                client.awaitBox(before)
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                client.await("two potato stews") { if (client.count(CookingMixes.INCOMPLETE_STEW_POTATO) == 2) Unit else null }
                client.seen("[cooking_mix.stew_potato]")
                val second = client.boxes()
                client.use(COOKED_MEAT, CookingMixes.INCOMPLETE_STEW_POTATO)
                client.awaitBox(second)
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                client.await("two uncooked stews") { if (client.count(CookingMixes.UNCOOKED_STEW) == 2) Unit else null }
                client.seen("[cooking_mix.stew_add_meat]")
                assertEquals(start, client.xp(), "mixing a stew gives no XP")

                client.level(59)
                client.use(SPICE, CookingMixes.UNCOOKED_STEW)
                client.seen("[cooking_mix.level level=60 item=uncooked curry]")
                client.level(99)
                client.make(SPICE, CookingMixes.UNCOOKED_STEW, CookingMixes.UNCOOKED_CURRY)
                client.seen("[cooking_mix.curry_spice]")

                // The stew and the curry on the range: 117 XP and 280 XP, neither burning at 99.
                val beforeStew = client.xp()
                client.locTarget(Cooking.RANGE, RANGE_X, RANGE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(Cooking.STEW.raw), Cooking.STEW.raw)
                client.await("the stew", 30_000) { if (client.count(Cooking.STEW.cooked) == 1) Unit else null }
                client.seen("[cooking.cooked_stew]")
                assertEquals(117, client.await("stew XP") { client.xp().takeIf { it > beforeStew } } - beforeStew)
                val beforeCurry = client.xp()
                client.locTarget(Cooking.RANGE, RANGE_X, RANGE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(Cooking.CURRY.raw), Cooking.CURRY.raw)
                client.await("the curry", 30_000) { if (client.count(Cooking.CURRY.cooked) == 1) Unit else null }
                client.seen("[cooking.cooked_curry]")
                assertEquals(280, client.await("curry XP") { client.xp().takeIf { it > beforeCurry } } - beforeCurry)
            }
        }
    }

    @Test
    fun `wine ferments 20 ticks after the last jug is made, each new jug starting the wait again, the bank's too`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val player = server.world.playerNamed("bob")!!
                // Twenty unfermented wines from before, put in the bank.
                client.give(Wine.UNFERMENTED, 20)
                client.command("openbank")
                client.await("the bank") {
                    client.openInterfaces().takeIf { BankScreen.SIDE in it && client.opEnabled(BankScreen.SIDE, BankScreen.SIDE_ITEMS, 0, 8) }
                }
                client.ifButton(BankScreen.SIDE, BankScreen.SIDE_ITEMS, 8, client.slotOf(Wine.UNFERMENTED), Wine.UNFERMENTED)
                client.await("twenty banked") { if (player.bank.count(Wine.UNFERMENTED) == 20L && client.count(Wine.UNFERMENTED) == 0) Unit else null }
                client.closeModal()
                client.await("the bank closed") { client.openInterfaces().takeIf { BankScreen.MAIN !in it } }

                // At 99 every wine ferments.
                client.level(99)
                client.give(GRAPES, 2)
                client.give(JUG_OF_WATER, 2)
                val start = client.xp()
                val before = client.boxes()
                client.use(GRAPES, JUG_OF_WATER)
                client.awaitBox(before)
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.await("one jug") { if (client.count(Wine.UNFERMENTED) == 1) Unit else null }
                client.seen("[cooking_mix.wine]")
                client.awaitTicks(10)
                assertEquals(1, client.count(Wine.UNFERMENTED), "still fermenting after 10 ticks")
                client.make(GRAPES, JUG_OF_WATER, Wine.UNFERMENTED)
                client.awaitTicks(15)
                // 25 ticks after the first jug, but only 15 after the second.
                assertEquals(2, client.count(Wine.UNFERMENTED), "the second jug started the wait again")
                assertEquals(20L, player.bank.count(Wine.UNFERMENTED))
                client.await("the wine", 30_000) { if (client.count(Wine.JUG_OF_WINE) == 2) Unit else null }
                client.await("the bank's wine") { if (player.bank.count(Wine.JUG_OF_WINE) == 20L) Unit else null }
                assertEquals(0L, player.bank.count(Wine.UNFERMENTED))
                assertEquals(0, client.count(Wine.BAD_WINE))
                assertEquals(22 * 200, client.await("wine XP") { client.xp().takeIf { it > start } } - start)
            }
        }
    }

    @Test
    fun `a first jug made with no other wine held still ferments`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                // At 99 every wine ferments; the grapes' first jug comes 3 ticks after the use.
                client.level(99)
                client.give(GRAPES)
                client.give(JUG_OF_WATER)
                val start = client.xp()
                client.make(GRAPES, JUG_OF_WATER, Wine.UNFERMENTED)
                client.await("the wine", 30_000) { if (client.count(Wine.JUG_OF_WINE) == 1) Unit else null }
                assertEquals(0, client.count(Wine.UNFERMENTED))
                assertEquals(200, client.await("wine XP") { client.xp().takeIf { it > start } } - start)
            }
        }
    }

    @Test
    fun `the churn asks which of cream, butter and cheese, takes the nearest input, and gives the milk's bucket back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(CHURN_X - 1, CHURN_Z)
                client.locOp(CHURN, CHURN_X, CHURN_Z, 1)
                client.seen("[cooking_mix.churn_nothing]")
                client.level(99)
                client.give(Churn.MILK, 2)
                val start = client.xp()
                val before = client.boxes()
                client.locOp(CHURN, CHURN_X, CHURN_Z, 1)
                val box = client.awaitBox(before)
                for (product in listOf(Churn.CREAM, Churn.BUTTER, Churn.CHEESE)) assertTrue(product in box, "$product in the box")
                // Cheese, the third, from milk: 26 ticks, 64 XP, the bucket back.
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 2, 1)
                client.await("the cheese", 30_000) { if (client.count(Churn.CHEESE) == 1) Unit else null }
                client.seen("[cooking_mix.churn_cheese]")
                assertEquals(1, client.count(BUCKET))
                assertEquals(64, client.await("cheese XP") { client.xp().takeIf { it > start } } - start)

                // With milk and cream held, cheese comes from the cream: 46 XP, the milk kept.
                client.give(Churn.CREAM)
                val beforeCream = client.xp()
                val second = client.boxes()
                client.locTarget(CHURN, CHURN_X, CHURN_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(Churn.CREAM), Churn.CREAM)
                client.awaitBox(second)
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 2, 1)
                client.await("the second cheese", 30_000) { if (client.count(Churn.CHEESE) == 2) Unit else null }
                assertEquals(46, client.await("cream XP") { client.xp().takeIf { it > beforeCream } } - beforeCream)
                assertEquals(listOf(0, 1), listOf(Churn.CREAM, Churn.MILK).map { client.count(it) })
            }
        }
    }

    private companion object {
        const val RANGE_X = 3224
        const val RANGE_Z = 3218
        const val CHURN = 11695
        const val CHURN_X = 3228
        const val CHURN_Z = 3214
        const val BACKPACK = 93
        const val COOKING = 7

        const val BOWL_OF_WATER = 1921
        const val POTATO = 1942
        const val COOKED_MEAT = 2142
        const val SPICE = 2007
        const val GRAPES = 1987
        const val JUG_OF_WATER = 1937
        const val BUCKET = 1925

        val NAMES = mapOf(CookingMixes.UNCOOKED_CURRY to "Uncooked curry")
    }
}
