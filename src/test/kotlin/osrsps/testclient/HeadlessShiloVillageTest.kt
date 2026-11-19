package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.ShiloVillage
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopStock
import osrsps.game.shop.ShopType
import osrsps.game.shop.ShopTypes
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Karamja step K4.3 through the headless client: Mosol Rei's talk and Follow taking the player into Shilo Village, the
 * broken cart climbed over from either side, the metal and wooden gates walked through, Obli's talk opening his store
 * and Fernahei's turned down. test-runner/examples/shilo-village.json does the same on the real map.
 */
class HeadlessShiloVillageTest {
    private val mosol = NpcType(ShiloVillage.MOSOL_REI, "Mosol Rei", ops = listOf("Follow", null, "Talk-to", null, null), internalName = "mosol_rei_complete")
    private val shopOps = listOf("Talk-to", null, "Trade", null, null)
    private val obli = NpcType(ShiloVillage.OBLI, "Obli", ops = shopOps, internalName = "shilogeneralstore")
    private val fernahei = NpcType(ShiloVillage.FERNAHEI, "Fernahei", ops = shopOps, internalName = "shilofishowner")
    private val machete = ObjType(MACHETE, "Machete", cost = 40, tradeable = true)
    private val store = ShopType(
        "oblis_general_store", "Obli's General Store.", invId = 138, size = 40, general = true,
        sellAt = 1500, buyAt = 500, changePer = 20, stock = listOf(ShopStock(machete, 50, 200)), npcs = listOf(ShiloVillage.OBLI),
    )

    /** As the 242 map has them: the cart east of the metal gate, and both gates' two leaves. */
    private val locs = listOf(
        ZoneLoc(ShiloVillage.BROKEN_CART, 10, 0, 2877, 2951, 0),
        ZoneLoc(ShiloVillage.METAL_GATE[0], 0, 0, 2875, 2952, 0),
        ZoneLoc(ShiloVillage.METAL_GATE[1], 0, 0, 2875, 2953, 0),
        ZoneLoc(ShiloVillage.WOODEN_GATE[0], 0, 2, 2867, 2953, 0),
        ZoneLoc(ShiloVillage.WOODEN_GATE[1], 0, 2, 2867, 2952, 0),
    )

    private fun type(id: Int, name: String, op: Int, option: String, size: Int = 1) =
        LocType(id, name, List(5) { if (it == op - 1) option else null }, LocCollisionType(width = size, length = size, interactType = 0))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(2870 shr 6, 2952 shr 6); markLoaded(2882 shr 6, 2952 shr 6) },
            objTypes = ObjTypes.of(ObjType(ShopScreen.COINS, "Coins", stackable = true), machete),
            npcTypes = NpcTypes.of(mosol, obli, fernahei),
            npcSpawns = listOf(
                NpcSpawn(mosol, 2883, 2951, wander = 0, source = "test"),
                NpcSpawn(obli, 2824, 2959, wander = 0, source = "test"),
                NpcSpawn(fernahei, 2826, 2959, wander = 0, source = "test"),
            ),
            locTypes = LocTypes(
                mapOf(ShiloVillage.BROKEN_CART to type(ShiloVillage.BROKEN_CART, "Broken cart", ShiloVillage.CLIMB_OVER, "Climb over", size = 3)) +
                    ShiloVillage.METAL_GATE.associateWith { type(it, "Metal gate", ShiloVillage.OPEN, "Open") } +
                    ShiloVillage.WOODEN_GATE.associateWith { type(it, "Wooden gate", ShiloVillage.OPEN, "Open") },
            ),
            staticLocs = StaticLocs().apply { locs.forEach(::add) },
            shopTypes = ShopTypes(listOf(store)), varTypes = VARS, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.at(x: Int, z: Int) = await("at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }

    private fun HeadlessClient.chat(key: String) =
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }.also { continueDialogue() }

    private fun HeadlessClient.message(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.closed() = await("the dialogue to close") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.npcIndex(id: Int) = await("NPC $id in view") { npcs().firstOrNull { it.id == id } }.index

    @Test
    fun `Mosol Rei shows the player in, the cart and gates are passed both ways, and Obli opens his store`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }

                // Talk-to: turned down, then taken up.
                client.tele(2882, 2951)
                val mosol = client.npcIndex(ShiloVillage.MOSOL_REI)
                client.npcOp(mosol, ShiloVillage.MOSOL_TALK_TO)
                client.chat("mosol_rei.greetings")
                client.chat("mosol_rei.threat_removed")
                client.chat("mosol_rei.show_the_way")
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals("[mosol_rei.enter_title]", menu.title)
                client.chooseOption(2)
                client.message("[mosol_rei.decide_not]")
                client.closed()
                client.npcOp(mosol, ShiloVillage.MOSOL_TALK_TO)
                client.chat("mosol_rei.greetings")
                client.chat("mosol_rei.threat_removed")
                client.chat("mosol_rei.show_the_way")
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.awaitDialogue("led in") { it is DialogueView.Message && it.text == "[mosol_rei.leads]" }
                client.continueDialogue()
                client.at(ShiloVillage.INSIDE_X, ShiloVillage.INSIDE_Z)
                client.message("[mosol_rei.leaves]")

                // Follow: straight in.
                client.tele(2882, 2952)
                client.npcOp(client.npcIndex(ShiloVillage.MOSOL_REI), ShiloVillage.FOLLOW)
                client.at(ShiloVillage.INSIDE_X, ShiloVillage.INSIDE_Z)

                // Over the cart from the east, and back from the west.
                client.tele(2880, 2952)
                client.locOp(ShiloVillage.BROKEN_CART, 2877, 2951, ShiloVillage.CLIMB_OVER)
                client.message("[shilo_cart.from_east]")
                client.at(ShiloVillage.CART_WEST_X, 2952)
                client.locOp(ShiloVillage.BROKEN_CART, 2877, 2951, ShiloVillage.CLIMB_OVER)
                client.message("[shilo_cart.from_west_1]")
                client.at(ShiloVillage.CART_EAST_X, 2952)
                client.message("[shilo_cart.from_west_2]")

                // Through the metal gate westward and back, then the wooden gate.
                client.tele(2875, 2953)
                client.locOp(ShiloVillage.METAL_GATE[1], 2875, 2953, ShiloVillage.OPEN)
                client.at(2874, 2953)
                client.locOp(ShiloVillage.METAL_GATE[1], 2875, 2953, ShiloVillage.OPEN)
                client.at(2875, 2953)
                client.tele(2868, 2952)
                client.locOp(ShiloVillage.WOODEN_GATE[1], 2867, 2952, ShiloVillage.OPEN)
                client.at(2867, 2952)

                // Obli's store from his talk; Fernahei turned down.
                client.tele(2825, 2958)
                client.npcOp(client.npcIndex(ShiloVillage.OBLI), ShiloVillage.TALK_TO)
                client.chat("obli.welcome")
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("obli.yes_please")
                client.await("the store") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.command("tele 2826 2958 0")
                client.await("the store to close") { if (ShopScreen.MAIN !in client.openInterfaces()) Unit else null }
                client.npcOp(client.npcIndex(ShiloVillage.FERNAHEI), ShiloVillage.TALK_TO)
                client.chat("fernahei.welcome")
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.chat("fernahei.no_thanks")
                client.chat("fernahei.thats_fine")
                client.closed()
                assertEquals(false, ShopScreen.MAIN in client.openInterfaces())
            }
        }
    }

    private companion object {
        const val MACHETE = 975

        /** shop_quantity sits alone on a spare varp here, as in HeadlessShopTest. */
        val VARS = VarTypes(6_000, mapOf(ShopScreen.VARBIT_QUANTITY to VarbitType(5_100, 0, 2)))
    }
}
