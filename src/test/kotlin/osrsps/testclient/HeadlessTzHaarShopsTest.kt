package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.TzHaarShops
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopStock
import osrsps.game.shop.ShopType
import osrsps.game.shop.ShopTypes
import osrsps.map.CollisionMap

/**
 * Karamja step K2.2 through the headless client: TzHaar-Hur-Tel's talk opening his shop, where Tokkul buys a
 * Toktz-xil-ul at 150% of its value, and TzHaar-Mej-Roh's talk naming the player and telling the castes. The rl-client
 * script test-runner/examples/tzhaar-shops.json does the same on the real map.
 */
class HeadlessTzHaarShopsTest {
    private val ops = listOf("Talk-to", null, "Trade", null, null)
    private val hurTel = NpcType(TzHaarShops.HUR_TEL, "TzHaar-Hur-Tel", ops = ops, internalName = "tzhaar_merchant_equipment")
    private val mejRoh = NpcType(TzHaarShops.MEJ_ROH, "TzHaar-Mej-Roh", ops = ops, internalName = "tzhaar_merchant_rune")
    private val tokkul = ObjType(TOKKUL, "Tokkul", stackable = true)
    private val ring = ObjType(XIL_UL, "Toktz-xil-ul", cost = 250, stackable = true, tradeable = true)
    private val airPack = ObjType(AIR_PACK, "Tzhaar air rune pack", iops = listOf("Open", null, null, null, "Drop"))
    private val airRune = ObjType(AIR_RUNE, "Air rune", stackable = true)
    private val equipment = ShopType(
        "tzhaar_hur_tels_equipment_store", "TzHaar-Hur-Tel's Equipment Store", invId = EQUIPMENT_INV, size = 8, general = false,
        sellAt = 1500, buyAt = 150, changePer = 20, stock = listOf(ShopStock(ring, 500, 10)), npcs = listOf(TzHaarShops.HUR_TEL),
        currency = TOKKUL,
    )

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(2470 shr 6, 5140 shr 6) }, objTypes = ObjTypes.of(tokkul, ring, airPack, airRune),
            npcTypes = NpcTypes.of(hurTel, mejRoh),
            npcSpawns = listOf(NpcSpawn(hurTel, 2478, 5146, wander = 0, source = "test"), NpcSpawn(mejRoh, 2474, 5146, wander = 0, source = "test")),
            shopTypes = ShopTypes(listOf(equipment)), varTypes = VARS, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.chat(key: String) =
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }.also { continueDialogue() }

    @Test
    fun `TzHaar-Hur-Tel's talk opens his shop, which sells for Tokkul`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.command("item $TOKKUL 1000")
                client.await("the Tokkul") { if (client.held(TOKKUL) == 1000L) Unit else null }
                client.command("tele 2476 5146 0")
                val npc = client.await("TzHaar-Hur-Tel in view") { client.npcs().firstOrNull { it.id == TzHaarShops.HUR_TEL } }
                client.npcOp(npc.index, TzHaarShops.TALK_TO)
                // A new account's skills are all 1: a tie, which goes to Mej.
                client.awaitDialogue("tzhaar_shop.greeting") { it is DialogueView.Chat && it.text == "[tzhaar_shop.greeting name=JalYt-Mej-ada]" }
                client.continueDialogue()
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.await("the shop") { if (ShopScreen.MAIN in client.openInterfaces() && client.opEnabled(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 1, 2)) Unit else null }
                // Buy 1 at 150% of 250 Tokkul.
                client.ifButton(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 2, 1, XIL_UL)
                client.await("the ring bought") { if (client.held(XIL_UL) == 1L) Unit else null }
                client.await("375 Tokkul paid") { if (client.held(TOKKUL) == 625L) Unit else null }
            }
        }
    }

    @Test
    fun `TzHaar-Mej-Roh names the player and tells the castes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ben").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.command("tele 2474 5144 0")
                val npc = client.await("TzHaar-Mej-Roh in view") { client.npcs().firstOrNull { it.id == TzHaarShops.MEJ_ROH } }
                client.npcOp(npc.index, TzHaarShops.TALK_TO)
                client.awaitDialogue("tzhaar_shop.greeting") { it is DialogueView.Chat && it.text == "[tzhaar_shop.greeting name=JalYt-Mej-ben]" }
                client.continueDialogue()
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.awaitDialogue("tzhaar_shop.are_you_not") { it is DialogueView.Chat && it.text == "[tzhaar_shop.are_you_not name=JalYt-Mej-ben]" }
                client.continueDialogue()
                client.awaitDialogue("tzhaar_shop.whats_caste") { it is DialogueView.Chat && it.text == "[tzhaar_shop.whats_caste caste=JalYt-Mej]" }
                client.continueDialogue()
                client.chat("tzhaar_shop.mej_is")
                client.chat("tzhaar_shop.suppose")
                client.awaitDialogue("tzhaar_shop.then_you") { it is DialogueView.Chat && it.text == "[tzhaar_shop.then_you caste=JalYt-Mej]" }
                client.continueDialogue()
                client.awaitDialogue("the caste menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("tzhaar_shop.what_are_you")
                client.chat("tzhaar_shop.i_am_mej")
                client.awaitDialogue("his menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("tzhaar_shop.other_types")
                client.chat("tzhaar_shop.castes_mej")
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
            }
        }
    }

    @Test
    fun `a Tzhaar air rune pack opens into 100 air runes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cy").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $AIR_RUNE 5")
                client.await("the runes") { if (client.held(AIR_RUNE) == 5L) Unit else null }
                client.command("item $AIR_PACK 2")
                client.await("the packs") { if (client.held(AIR_PACK) == 2L) Unit else null }
                val slot = client.inventory(Inventory.INV).indexOfFirst { it?.id == AIR_PACK }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, OPEN_BUTTON_OP, slot, AIR_PACK)
                client.await("100 more runes") { if (client.held(AIR_RUNE) == 105L) Unit else null }
                assertEquals(1L, client.held(AIR_PACK))
            }
        }
    }

    private companion object {
        const val AIR_PACK = 21698
        const val AIR_RUNE = 556
        /** The button op for an item's first option, Open. */
        const val OPEN_BUTTON_OP = 2
        const val TOKKUL = 6529
        const val XIL_UL = 6522
        const val EQUIPMENT_INV = 325

        /** shop_quantity sits alone on a spare varp here, as in HeadlessShopTest. */
        val VARS = VarTypes(6_000, mapOf(ShopScreen.VARBIT_QUANTITY to VarbitType(5_100, 0, 2)))
    }
}
