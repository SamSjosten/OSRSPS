package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopStock
import osrsps.game.shop.ShopType
import osrsps.game.shop.ShopTypes
import osrsps.map.CollisionMap

/**
 * Rest of Lumbridge step 4 through the headless client: Trade on a shop keeper opens the shop, Buy and Sell move the
 * stock and the coins at the prices it gives, Value says them, the Quantity buttons change what op 1 buys, and the
 * stock comes back. The rl-client script test-runner/examples/lumbridge-shops.json does the same in the real client.
 */
class HeadlessShopTest {
    private val coins = ObjType(COINS, "Coins", stackable = true)
    private val pot = ObjType(POT, "Pot", tradeable = true)
    private val axe = ObjType(AXE, "Bronze axe", cost = 16, tradeable = true)
    // A note that claims to note the axe, which doesn't link back: a bad cache link, not anything live has.
    private val looseNote = ObjType(LOOSE_NOTE, "Pot", stackable = true, tradeable = true, certlink = AXE, certtemplate = 799)
    private val objs = ObjTypes.of(coins, pot, axe, looseNote)

    private val store = ShopType(
        "store", "Test General Store", invId = STORE_INV, size = 40, general = true, sellAt = 1300, buyAt = 400, changePer = 30,
        stock = listOf(ShopStock(pot, 5, 10)), npcs = listOf(KEEPER),
    )
    private val axes = ShopType(
        "axes", "Test Axes", invId = AXE_INV, size = 1, general = false, sellAt = 1000, buyAt = 600, changePer = 20,
        stock = listOf(ShopStock(axe, 10, 100)), npcs = listOf(BOB),
    )

    private val trade = listOf("Talk-to", null, "Trade", null, null)
    private val keeper = NpcSpawn(NpcType(KEEPER, "Shop keeper", ops = trade), 3221, 3220, source = "test")
    private val bob = NpcSpawn(NpcType(BOB, "Bob", ops = listOf("Talk-to", null, "Trade", "Repair", null)), 3223, 3220, source = "test")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs, npcSpawns = listOf(keeper, bob),
            shopTypes = ShopTypes(listOf(store, axes)), varTypes = VARS, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.backpack() = inventory(Inventory.INV).filterNotNull()

    private fun HeadlessClient.held(id: Int) = backpack().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.slotOf(id: Int) = inventory(Inventory.INV).indexOfFirst { it?.id == id }

    private fun HeadlessClient.awaitHeld(id: Int, count: Int) =
        await("the backpack to hold $count of $id") { if (held(id) == count) Unit else null }

    private fun HeadlessClient.awaitStock(inv: Int, vararg slots: InvObj) =
        await("the shop to hold ${slots.toList()}") { if (inventory(inv) == slots.toList()) Unit else null }

    private fun HeadlessClient.open(npcId: Int) {
        val npc = await("$npcId in view") { npcs().firstOrNull { it.id == npcId } }
        npcOp(npc.index, TRADE)
        // The events come after the opens and the stock.
        await("the shop and Buy on its first item") {
            if (ShopScreen.MAIN in openInterfaces() && ShopScreen.SIDE in openInterfaces() && opEnabled(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 1, 2)) Unit else null
        }
        await("Sell on the inventory") { if (opEnabled(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 27, 2)) Unit else null }
    }

    @Test
    fun `a general store sells at its price, buys anything back, and restocks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $COINS 100")
                client.command("item $AXE")
                client.await("the items") { if (client.backpack().size == 2) Unit else null }
                client.open(KEEPER)
                client.awaitStock(STORE_INV, InvObj(POT, 5))
                val init = client.clientScripts().last { it.id == ShopScreen.SHOP_MAIN_INIT_SCRIPT }
                assertEquals(listOf<Any>(STORE_INV, "Test General Store", -1, 0, 1), init.args)
                val sideInit = client.clientScripts().last { it.id == ShopScreen.INTERFACE_INV_INIT_SCRIPT }
                assertEquals(listOf<Any>(ShopScreen.SIDE shl 16, Inventory.INV, 4, 7, 0, -1), sideInit.args.take(6))

                // Value (op 1 while the Quantity is Value), then Buy 1 (op 2) at 130% of 1, one coin.
                client.ifButton(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 1, 1, POT)
                client.awaitMessage("the pot's price") { it == "Pot: currently costs 1 coin." }
                client.ifButton(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 2, 1, POT)
                client.awaitHeld(POT, 1)
                client.awaitHeld(COINS, 99)
                client.awaitStock(STORE_INV, InvObj(POT, 4))

                // The 5 button makes op 1 Buy 5: four are left, so it buys them and says it ran out.
                client.ifButton(ShopScreen.MAIN, ShopScreen.QUANTITY_5, 1)
                client.await("Quantity 5") { if (client.varp(VARP_QUANTITY) == ShopScreen.QUANTITY_BUY_5) Unit else null }
                client.ifButton(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 1, 1, POT)
                client.awaitMessage("out of stock") { it == ShopScreen.OUT_OF_STOCK }
                client.awaitHeld(POT, 5)
                client.awaitHeld(COINS, 95)
                // Sold out, the pot stays at 0.
                client.awaitStock(STORE_INV, InvObj(POT, 0))

                // The store buys an axe it doesn't stock at 40% of 16, into the next slot.
                client.ifButton(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 1, client.slotOf(AXE), AXE)
                client.awaitMessage("the axe's price") { it == "Bronze axe: shop will buy for 6 coins." }
                client.ifButton(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 2, client.slotOf(AXE), AXE)
                client.awaitHeld(AXE, 0)
                client.awaitHeld(COINS, 101)
                // Coins can't be sold.
                client.ifButton(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 2, client.slotOf(COINS), COINS)
                client.awaitMessage("no selling coins") { it == ShopScreen.CANT_SELL_COINS }

                // Pots come back one every 10 ticks.
                client.await("a pot restocked", timeoutMillis = 30_000) { client.inventory(STORE_INV).firstOrNull()?.takeIf { it.id == POT && it.count >= 1 } }
            }
        }
    }

    @Test
    fun `a specialist shop buys only what it stocks, and asks for coins`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $POT")
                client.command("item $COINS 20")
                client.await("the items") { if (client.backpack().size == 2) Unit else null }
                client.open(BOB)
                client.awaitStock(AXE_INV, InvObj(AXE, 10))
                client.ifButton(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 2, client.slotOf(POT), POT)
                client.awaitMessage("not this shop") { it == ShopScreen.WRONG_SHOP }
                // Buy 5 at 16, 16 and more: 20 coins buy one.
                client.ifButton(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 3, 1, AXE)
                client.awaitMessage("not enough coins") { it == ShopScreen.NOT_ENOUGH_COINS }
                client.awaitHeld(AXE, 1)
                client.awaitHeld(COINS, 4)
                client.awaitStock(AXE_INV, InvObj(AXE, 9))
                // Sold back with one under its stock: 62% of 16, 9.
                client.ifButton(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 2, client.slotOf(AXE), AXE)
                client.awaitHeld(COINS, 13)
                client.awaitStock(AXE_INV, InvObj(AXE, 10))
            }
        }
    }

    @Test
    fun `talking to a keeper or Bob opens the shop when asked what they sell`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val keeperNpc = client.await("the keeper in view") { client.npcs().firstOrNull { it.id == KEEPER } }
                client.npcOp(keeperNpc.index, TALK_TO)
                client.awaitDialogue("the keeper's line") { it is DialogueView.Chat && it.text == "[lumbridge_shop_keeper.help]" }
                client.continueDialogue()
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.await("the store") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.awaitStock(STORE_INV, InvObj(POT, 5))
            }
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                val bobNpc = client.await("Bob in view") { client.npcs().firstOrNull { it.id == BOB } }
                client.npcOp(bobNpc.index, REPAIR)
                client.awaitDialogue("nothing to repair") { it is DialogueView.Chat && it.text == "[bob.nothing_to_repair]" }
                client.continueDialogue()
                client.await("the line to close") { if (client.dialogueView == null) Unit else null }
                client.npcOp(bobNpc.index, TALK_TO)
                client.awaitDialogue("Bob's menu") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.awaitDialogue("the player's question") { it is DialogueView.Chat && it.text == "[bob.sell]" }
                client.continueDialogue()
                client.awaitDialogue("Bob's answer") { it is DialogueView.Chat && it.text == "[bob.axes]" }
                client.continueDialogue()
                client.await("Bob's shop") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.awaitStock(AXE_INV, InvObj(AXE, 10))
            }
        }
    }

    @Test
    fun `a note whose item doesn't link back sells as itself, not as that item`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $LOOSE_NOTE 5")
                client.await("the note") { if (client.held(LOOSE_NOTE) == 5) Unit else null }
                client.open(BOB)
                client.awaitStock(AXE_INV, InvObj(AXE, 10))
                client.ifButton(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 2, client.slotOf(LOOSE_NOTE), LOOSE_NOTE)
                client.awaitMessage("not this shop") { it == ShopScreen.WRONG_SHOP }
                assertEquals(5, client.held(LOOSE_NOTE))
                assertEquals(0, client.held(COINS))
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val POT = 1931
        const val AXE = 1351
        const val LOOSE_NOTE = 1932
        const val STORE_INV = 3
        const val AXE_INV = 1
        const val KEEPER = 2813
        const val BOB = 10619
        const val TRADE = 3
        const val TALK_TO = 1
        const val REPAIR = 4

        /** shop_quantity sits alone on a spare varp here, so the varp is the quantity. */
        const val VARP_QUANTITY = 5_100

        val VARS = VarTypes(6_000, mapOf(ShopScreen.VARBIT_QUANTITY to VarbitType(VARP_QUANTITY, 0, 2)))
    }
}
