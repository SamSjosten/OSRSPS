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
import osrsps.game.skill.CookingMixes
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Fishing and Cooking 2 step FC5a through the headless client: flour and water into dough through the Make-X box, a pie
 * shell filled and baked, a garden pie's three fillings, a pizza from base to topping, and a cake that gives its tin back.
 * The rl-client script test-runner/examples/lumbridge-pies-pizza-cake.json does the same in the real client.
 */
class HeadlessMultiStepFoodTest {
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 20) { crcs, millis ->
        val ids = CookingMixes.PAIRS.flatMap { (a, b, recipes) ->
            listOf(a, b) + recipes.flatMap { r -> r.inputs.map { it.obj } + r.product + r.byproducts.map { it.obj } }
        } + Cooking.FOODS.flatMap { listOf(it.raw, it.cooked, it.burnt) } + Cooking.CAKE_TIN
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        for (id in ids) objs[id] = ObjType(id, NAMES[id] ?: "Item $id")
        World(
            crcs, CollisionMap().apply { markLoaded(RANGE_X shr 6, RANGE_Z shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(11),
            locTypes = LocTypes(
                mapOf(Cooking.RANGE to LocType(Cooking.RANGE, "Range", listOf("Cook", null, null, null, null), LocCollisionType(width = 1, length = 1))),
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(Cooking.RANGE, LocShape.CENTREPIECE_STRAIGHT, 0, RANGE_X, RANGE_Z, 0)) },
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

    /** Uses [id] on [target] and waits for the Make-X box that opens. */
    private fun HeadlessClient.useForBox(id: Int, target: Int): List<Any> {
        val before = boxes()
        use(id, target)
        return await("the Make-X box") { clientScripts().filter { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }.drop(before).lastOrNull() }.args
    }

    /** Uses [id] on [target], which makes one straight away, and waits for [product]. */
    private fun HeadlessClient.make(id: Int, target: Int, product: Int) {
        val before = count(product)
        use(id, target)
        await("item $product") { if (count(product) == before + 1) Unit else null }
    }

    private fun HeadlessClient.bake(raw: Int, product: Int) {
        val before = count(product)
        locTarget(Cooking.RANGE, RANGE_X, RANGE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(raw), raw)
        await("item $product", 30_000) { if (count(product) == before + 1) Unit else null }
    }

    private fun HeadlessClient.xp() = stat(COOKING)!!.xp

    @Test
    fun `flour and water ask which dough, pizza and pitta dough need their levels, and the pot and bucket come back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(FLOUR, 2)
                client.give(BUCKET_OF_WATER, 2)
                val box = client.useForBox(FLOUR, BUCKET_OF_WATER)
                for (dough in listOf(CookingMixes.BREAD_DOUGH, CookingMixes.PASTRY_DOUGH, CookingMixes.PIZZA_BASE, CookingMixes.PITTA_DOUGH)) {
                    assertTrue(dough in box, "$dough in the box")
                }
                // Pizza base, the third, at level 1.
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 2, 1)
                client.seen("[cooking_mix.level level=35 item=pizza base]")
                assertEquals(0, client.count(CookingMixes.PIZZA_BASE))

                // Pastry dough, the second: flour and water used, the pot and bucket back, no XP.
                client.useForBox(FLOUR, BUCKET_OF_WATER)
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 1, 2)
                client.await("two pastry dough") { if (client.count(CookingMixes.PASTRY_DOUGH) == 2) Unit else null }
                client.seen("[cooking_mix.pastry_dough]")
                assertEquals(0, client.count(FLOUR))
                assertEquals(0, client.count(BUCKET_OF_WATER))
                assertEquals(2, client.count(POT))
                assertEquals(2, client.count(BUCKET))
                assertEquals(0, client.xp())
            }
        }
    }

    @Test
    fun `pastry in a pie dish, a cooking apple from 30, baked on the range for 130 XP, and a garden pie takes its three fillings`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele ${RANGE_X - 1} $RANGE_Z")
                client.await("the teleport") { client.position?.takeIf { it.x == RANGE_X - 1 } }
                // Two of each ask how many; one of each makes one at once, without the box.
                client.give(CookingMixes.PASTRY_DOUGH)
                client.give(CookingMixes.PIE_DISH)
                client.make(CookingMixes.PASTRY_DOUGH, CookingMixes.PIE_DISH, CookingMixes.PIE_SHELL)
                client.seen("[cooking_mix.pie_shell]")
                client.give(CookingMixes.PASTRY_DOUGH, 2)
                client.give(CookingMixes.PIE_DISH, 2)
                client.useForBox(CookingMixes.PASTRY_DOUGH, CookingMixes.PIE_DISH)
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.await("a second pie shell") { if (client.count(CookingMixes.PIE_SHELL) == 2) Unit else null }

                client.give(COOKING_APPLE)
                client.level(29)
                client.use(COOKING_APPLE, CookingMixes.PIE_SHELL)
                client.seen("[cooking_mix.level level=30 item=uncooked apple pie]")
                client.level(99)
                client.make(COOKING_APPLE, CookingMixes.PIE_SHELL, Cooking.APPLE_PIE.raw)
                client.seen("[cooking_mix.apple_pie]")
                val before = client.xp()
                client.bake(Cooking.APPLE_PIE.raw, Cooking.APPLE_PIE.cooked)
                client.seen("[cooking.cooked_pie item=apple pie]")
                assertEquals(130, client.await("Cooking XP") { client.xp().takeIf { it > before } } - before)

                // A garden pie: tomato, onion, cabbage, one at a time, then baked.
                client.give(TOMATO)
                client.give(ONION)
                client.give(CABBAGE)
                client.make(TOMATO, CookingMixes.PIE_SHELL, GARDEN_PIE_1)
                client.make(ONION, GARDEN_PIE_1, GARDEN_PIE_2)
                client.make(CABBAGE, GARDEN_PIE_2, Cooking.GARDEN_PIE.raw)
                client.seen("[cooking_mix.garden_pie_3]")
                client.bake(Cooking.GARDEN_PIE.raw, Cooking.GARDEN_PIE.cooked)
                client.seen("[cooking.cooked_pie item=garden pie]")
            }
        }
    }

    @Test
    fun `a pizza from base to anchovies, and a cake that gives its tin back before the chocolate`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele ${RANGE_X - 1} $RANGE_Z")
                client.await("the teleport") { client.position?.takeIf { it.x == RANGE_X - 1 } }
                client.level(99)
                client.give(CookingMixes.PIZZA_BASE)
                client.give(TOMATO)
                client.give(CHEESE)
                client.give(ANCHOVIES)
                val start = client.xp()
                client.make(TOMATO, CookingMixes.PIZZA_BASE, CookingMixes.INCOMPLETE_PIZZA)
                client.make(CHEESE, CookingMixes.INCOMPLETE_PIZZA, CookingMixes.UNCOOKED_PIZZA)
                assertEquals(start, client.xp(), "the tomato and cheese give no XP")
                client.bake(CookingMixes.UNCOOKED_PIZZA, CookingMixes.PLAIN_PIZZA)
                client.seen("[cooking.cooked_pizza]")
                // The bake's 143 XP can reach the client after the pizza and its message; wait for it before the topping.
                val beforeTopping = client.await("the bake's XP") { client.xp().takeIf { it == start + 143 } }
                client.make(ANCHOVIES, CookingMixes.PLAIN_PIZZA, ANCHOVY_PIZZA)
                client.seen("[cooking_mix.anchovy_pizza]")
                assertEquals(39, client.await("topping XP") { client.xp().takeIf { it > beforeTopping } } - beforeTopping)

                // The cake: an egg on the tin mixes the egg, flour and milk; the pot and bucket come back, and the tin
                // comes back from the range.
                client.give(Cooking.CAKE_TIN)
                client.give(CookingMixes.EGG)
                client.give(FLOUR)
                client.give(CookingMixes.BUCKET_OF_MILK)
                client.make(CookingMixes.EGG, Cooking.CAKE_TIN, CookingMixes.UNCOOKED_CAKE)
                client.seen("[cooking_mix.uncooked_cake]")
                assertEquals(listOf(0, 0, 0, 1, 1), listOf(Cooking.CAKE_TIN, CookingMixes.EGG, FLOUR, POT, BUCKET).map { client.count(it) })
                val beforeCake = client.xp()
                client.bake(CookingMixes.UNCOOKED_CAKE, CookingMixes.CAKE)
                client.seen("[cooking.cooked_cake]")
                assertEquals(180, client.await("Cooking XP") { client.xp().takeIf { it > beforeCake } } - beforeCake)
                client.await("the cake tin back") { if (client.count(Cooking.CAKE_TIN) == 1) Unit else null }

                client.give(CHOCOLATE_BAR)
                val beforeChocolate = client.xp()
                client.make(CHOCOLATE_BAR, CookingMixes.CAKE, CHOCOLATE_CAKE)
                assertEquals(30, client.await("chocolate XP") { client.xp().takeIf { it > beforeChocolate } } - beforeChocolate)
            }
        }
    }

    private companion object {
        const val RANGE_X = 3224
        const val RANGE_Z = 3218
        const val BACKPACK = 93
        const val COOKING = 7

        const val FLOUR = 1933
        const val BUCKET_OF_WATER = 1929
        const val POT = 1931
        const val BUCKET = 1925
        const val COOKING_APPLE = 1955
        const val TOMATO = 1982
        const val ONION = 1957
        const val CABBAGE = 1965
        const val CHEESE = 1985
        const val ANCHOVIES = 319
        const val ANCHOVY_PIZZA = 2297
        const val CHOCOLATE_BAR = 1973
        const val CHOCOLATE_CAKE = 1897
        const val GARDEN_PIE_1 = 7172
        const val GARDEN_PIE_2 = 7174

        val NAMES = mapOf(
            CookingMixes.PIZZA_BASE to "Pizza base", Cooking.APPLE_PIE.raw to "Uncooked apple pie", Cooking.APPLE_PIE.cooked to "Apple pie",
            Cooking.GARDEN_PIE.cooked to "Garden pie",
        )
    }
}
