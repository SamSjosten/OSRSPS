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
import osrsps.game.content.VarrockShopkeepers
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopStock
import osrsps.game.shop.ShopType
import osrsps.game.shop.ShopTypes
import osrsps.map.CollisionMap

/**
 * Varrock step 4 through the headless client: each weapon and armour keeper's Talk-to opens their shop from the menu,
 * and turning them down ends with their parting lines. The rl-client script test-runner/examples/varrock-shops.json
 * trades at every shop in Varrock itself.
 */
class HeadlessVarrockShopsTest {
    private val sword = ObjType(BRONZE_SWORD, "Bronze sword", cost = 26, tradeable = true)
    private val objs = ObjTypes.of(sword)

    private val trade = listOf("Talk-to", null, "Trade", null, null)
    private val keeper = NpcType(VarrockShopkeepers.SWORDSHOP_KEEPER, "Shop keeper", ops = trade, internalName = "swordshop1")
    private val assistant = NpcType(VarrockShopkeepers.SWORDSHOP_ASSISTANT, "Shop assistant", ops = trade, internalName = "swordshop2")
    private val horvik = NpcType(VarrockShopkeepers.HORVIK, "Horvik", ops = trade, internalName = "horvik_the_armourer")
    private val lowe = NpcType(VarrockShopkeepers.LOWE, "Lowe", ops = trade, internalName = "lowe")
    private val zaff = NpcType(VarrockShopkeepers.ZAFF, "Zaff", ops = trade, internalName = "zaff")

    private fun shop(key: String, invId: Int, vararg npcs: NpcType) = ShopType(
        key, key, invId = invId, size = 1, general = false, sellAt = 1000, buyAt = 600, changePer = 20,
        stock = listOf(ShopStock(sword, 1, 100)), npcs = npcs.map { it.id },
    )

    private val shops = ShopTypes(
        listOf(
            shop("varrock_swordshop", 6, keeper, assistant),
            shop("horviks_armour_shop", 2, horvik),
            shop("lowes_archery_emporium", 7, lowe),
            shop("zaffs_superior_staffs", 51, zaff),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs,
            npcTypes = NpcTypes.of(keeper, assistant, horvik, lowe, zaff),
            npcSpawns = listOf(keeper, assistant, horvik, lowe, zaff).mapIndexed { i, type -> NpcSpawn(type, 3219 + i * 2, 3221, source = "test") },
            shopTypes = shops, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.awaitShop() = await("the shop") { if (ShopScreen.MAIN in openInterfaces() && opEnabled(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 27, 2)) Unit else null }

    private fun HeadlessClient.talk(id: Int, greeting: String, menu: List<String>) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, VarrockShopkeepers.TALK_TO)
        awaitDialogue(greeting) { it is DialogueView.Chat && it.text == greeting }
        continueDialogue()
        awaitDialogue("the menu") { it is DialogueView.Menu && it.options == menu }
    }

    /** Talks to [id], picks the shop option, and waits for the shop; then closes it. */
    private fun HeadlessClient.talkToShop(id: Int, prefix: String, menu: List<String>, option: Int) {
        talk(id, "[$prefix.greeting]", menu.map { "[$prefix.$it]" })
        chooseOption(option)
        awaitShop()
        closeModal()
        await("the shop closed") { if (ShopScreen.MAIN !in openInterfaces()) Unit else null }
    }

    /** Picks the other option, then reads [lines] (each said by whoever says it) to the end. */
    private fun HeadlessClient.decline(id: Int, prefix: String, menu: List<String>, option: Int, lines: List<String>) {
        talk(id, "[$prefix.greeting]", menu.map { "[$prefix.$it]" })
        chooseOption(option)
        for (line in lines) {
            awaitDialogue(line) { it is DialogueView.Chat && it.text == "[$prefix.$line]" }
            continueDialogue()
        }
        await("the talk over") { if (dialogueView == null) Unit else null }
    }

    @Test
    fun `each keeper's talk opens their shop`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.talkToShop(keeper.id, "varrock_swordshop", listOf("yes", "no"), 1)
                client.talkToShop(assistant.id, "varrock_swordshop", listOf("yes", "no"), 1)
                client.talkToShop(horvik.id, "horvik", listOf("browsing", "trade"), 2)
                client.talkToShop(lowe.id, "lowe", listOf("yes", "no"), 1)
                client.talkToShop(zaff.id, "zaff", listOf("yes", "no"), 1)
            }
        }
    }

    @Test
    fun `turning each keeper down ends the talk with no shop`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.decline(keeper.id, "varrock_swordshop", listOf("yes", "no"), 2, listOf("no", "bye"))
                client.decline(horvik.id, "horvik", listOf("browsing", "trade"), 1, listOf("browsing", "come_back"))
                client.decline(lowe.id, "lowe", listOf("yes", "no"), 2, listOf("no", "suit_yourself"))
                client.decline(zaff.id, "zaff", listOf("yes", "no"), 2, listOf("no", "pun", "joke"))
                assertTrue(ShopScreen.MAIN !in client.openInterfaces())
            }
        }
    }

    private companion object {
        const val BRONZE_SWORD = 1277
    }
}
