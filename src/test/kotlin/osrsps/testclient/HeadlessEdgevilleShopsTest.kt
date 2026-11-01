package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.EdgevilleShops
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopStock
import osrsps.game.shop.ShopType
import osrsps.game.shop.ShopTypes
import osrsps.map.CollisionMap

/**
 * Edgeville step 2 through the headless client: the General Store assistant's, Oziach's and Richard's talk each open
 * their own shop from the option that buys, and Oziach's other options don't. The rl-client script
 * test-runner/examples/edgeville-shops.json trades at each shop in the real client.
 */
class HeadlessEdgevilleShopsTest {
    private val pot = ObjType(POT, "Pot", cost = 1, tradeable = true)
    private val shield = ObjType(SHIELD, "Anti-dragon shield", cost = 20, tradeable = true)
    private val cape = ObjType(CAPE, "Team-6 cape", cost = 50, tradeable = true)
    private val objs = ObjTypes.of(pot, shield, cape)

    private val trade = listOf("Talk-to", null, "Trade", null, null)
    private val assistant = NpcType(EdgevilleShops.SHOP_ASSISTANT, "Shop assistant", ops = trade, internalName = "generalassistant5")
    private val oziach = NpcType(EdgevilleShops.OZIACH, "Oziach", ops = trade, internalName = "oziach")
    private val richard = NpcType(EdgevilleShops.RICHARD, "Richard", ops = trade, internalName = "wilderness_capeseller_6")

    private val shops = ShopTypes(
        listOf(
            ShopType("edgeville_general_store", "Edgeville General Store", invId = STORE_INV, size = 40, general = true, sellAt = 1300, buyAt = 400, changePer = 30, stock = listOf(ShopStock(pot, 5, 100)), npcs = listOf(assistant.id)),
            ShopType("oziachs_armour", "Oziach's Armour", invId = OZIACH_INV, size = 3, general = false, sellAt = 1300, buyAt = 400, changePer = 30, stock = listOf(ShopStock(shield, 35, 1)), npcs = listOf(oziach.id)),
            ShopType("richards_wilderness_cape_shop", "Richard's Wilderness Cape Shop", invId = RICHARD_INV, size = 40, general = false, sellAt = 1000, buyAt = 600, changePer = 10, stock = listOf(ShopStock(cape, 100, 100)), npcs = listOf(richard.id)),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs, npcTypes = NpcTypes.of(assistant, oziach, richard),
            npcSpawns = listOf(NpcSpawn(assistant, 3221, 3220, source = "test"), NpcSpawn(oziach, 3223, 3220, source = "test"), NpcSpawn(richard, 3225, 3220, source = "test")),
            shopTypes = shops, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.awaitShop(inv: Int, stock: InvObj) {
        await("the shop") { if (ShopScreen.MAIN in openInterfaces() && opEnabled(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 27, 2)) Unit else null }
        await("its stock") { inventory(inv).takeIf { it == listOf(stock) } }
    }

    private fun HeadlessClient.talkTo(id: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, EdgevilleShops.TALK_TO)
    }

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    @Test
    fun `the General Store assistant's first option opens the store`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.talkTo(assistant.id)
                client.chat("edgeville_shop_assistant.help")
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.awaitShop(STORE_INV, InvObj(POT, 5))
            }
        }
    }

    @Test
    fun `Oziach answers his other options without his shop, and his buy option opens it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.talkTo(oziach.id)
                client.chat("oziach.greeting")
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(3)
                client.chat("oziach.nice_day")
                client.chat("oziach.nice_day_reply")
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
                assertTrue(ShopScreen.MAIN !in client.openInterfaces(), "no shop from a nice day")

                client.talkTo(oziach.id)
                client.chat("oziach.greeting")
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("oziach.buy")
                client.awaitShop(OZIACH_INV, InvObj(SHIELD, 35))
            }
        }
    }

    @Test
    fun `Richard's yes opens his cape shop`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.talkTo(richard.id)
                client.chat("richard.greeting")
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.awaitShop(RICHARD_INV, InvObj(CAPE, 100))
            }
        }
    }

    private companion object {
        const val POT = 1931
        const val SHIELD = 1540
        const val CAPE = 4325
        const val STORE_INV = 24
        const val OZIACH_INV = 35
        const val RICHARD_INV = 241
    }
}
