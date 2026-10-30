package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.AlKharidShopkeepers
import osrsps.game.dialogue.DialogueText
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopLines
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopStock
import osrsps.game.shop.ShopType
import osrsps.game.shop.ShopTypes
import osrsps.map.CollisionMap

/**
 * Al Kharid step 3 through the headless client: the Gem trader's talk opens his shop, the General Store won't buy
 * silk, and Ali Morrisane sells junk once his basic stock closes. The rl-client script
 * test-runner/examples/al-kharid-shops.json trades at every shop in the real client.
 */
class HeadlessAlKharidShopsTest {
    private val coins = ObjType(COINS, "Coins", stackable = true)
    private val silk = ObjType(SILK, "Silk", cost = 3, tradeable = true)
    private val pot = ObjType(POT, "Pot", cost = 1, tradeable = true)
    private val sapphire = ObjType(SAPPHIRE, "Uncut sapphire", cost = 25, tradeable = true)
    private val junk = AlKharidShopkeepers.JUNK.map { ObjType(it, "Junk $it", tradeable = true) }
    private val objs = ObjTypes.of(coins, silk, pot, sapphire, *junk.toTypedArray())

    private val trade = listOf("Talk-to", null, "Trade", null, null)
    private val gemTrader = NpcType(AlKharidShopkeepers.GEM_TRADER, "Gem trader", ops = trade, internalName = "gem_trader")
    private val keeper = NpcType(AlKharidShopkeepers.SHOP_KEEPER, "Shop keeper", ops = trade, internalName = "generalshopkeeper3")
    private val ali = NpcType(ALI, "Ali Morrisane", ops = trade, internalName = "feud_ali_m")

    private val shops = ShopTypes(
        listOf(
            ShopType("gem_trader", "Gem Trader.", invId = 26, size = 1, general = false, sellAt = 1000, buyAt = 700, changePer = 30, stock = listOf(ShopStock(sapphire, 1, 100)), npcs = listOf(gemTrader.id)),
            ShopType(
                "al_kharid_general_store", "Al Kharid General Store", invId = 13, size = 40, general = true, sellAt = 1300, buyAt = 400, changePer = 30,
                stock = listOf(ShopStock(pot, 5, 100)), npcs = listOf(keeper.id), refuses = setOf(SILK),
            ),
            ShopType(AlKharidShopkeepers.ALIS_WARES, "Ali's Discount Wares", invId = 252, size = 1, general = false, sellAt = 1000, buyAt = 550, changePer = 10, stock = listOf(ShopStock(pot, 5, 100)), npcs = listOf(ali.id)),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs, npcTypes = NpcTypes.of(gemTrader, keeper, ali),
            npcSpawns = listOf(NpcSpawn(gemTrader, 3221, 3220, source = "test"), NpcSpawn(keeper, 3223, 3220, source = "test"), NpcSpawn(ali, 3225, 3220, source = "test")),
            shopTypes = shops, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.awaitShop() = await("the shop") { if (ShopScreen.MAIN in openInterfaces() && opEnabled(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 27, 2)) Unit else null }

    private fun HeadlessClient.trade(id: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, TRADE)
        awaitShop()
    }

    @Test
    fun `the Gem trader's talk opens his shop`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val npc = client.await("the Gem trader in view") { client.npcs().firstOrNull { it.id == gemTrader.id } }
                client.npcOp(npc.index, AlKharidShopkeepers.TALK_TO)
                client.awaitDialogue("his greeting") { it is DialogueView.Chat && it.text == "[gem_trader.greeting]" }
                client.continueDialogue()
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.awaitShop()
                client.await("his stock") { client.inventory(26).takeIf { it == listOf(InvObj(SAPPHIRE, 1)) } }
            }
        }
    }

    @Test
    fun `the General Store won't buy silk but buys a pot`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $SILK")
                client.command("item $POT")
                client.await("the items") { if (client.held(SILK) == 1 && client.held(POT) == 1) Unit else null }
                client.trade(keeper.id)
                client.ifButton(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 2, client.inventory(Inventory.INV).indexOfFirst { it?.id == SILK }, SILK)
                client.awaitMessage("not this shop") { it == DialogueText.NONE[ShopLines.WRONG_SHOP] }
                client.ifButton(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 2, client.inventory(Inventory.INV).indexOfFirst { it?.id == POT }, POT)
                client.await("the pot sold") { if (client.held(POT) == 0) Unit else null }
                assertEquals(1, client.held(SILK))
            }
        }
    }

    @Test
    fun `leaving Ali's basic stock with 10 coins buys a piece of junk`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $COINS 25")
                client.await("the coins") { if (client.held(COINS) == 25) Unit else null }
                client.trade(ali.id)
                // Walking away closes the shop.
                client.walk(3222, 3222)
                client.awaitMessage("the junk") { it == "[ali_morrisane.junk]" }
                client.await("15 coins left") { if (client.held(COINS) == 15) Unit else null }
                assertTrue(AlKharidShopkeepers.JUNK.any { client.held(it) == 1 })
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val SILK = 950
        const val POT = 1931
        const val SAPPHIRE = 1623
        const val ALI = 3533
        const val TRADE = 3
    }
}
