package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.DraynorMarket
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopStock
import osrsps.game.shop.ShopType
import osrsps.game.shop.ShopTypes
import osrsps.map.CollisionMap

/**
 * Draynor piece step 3 through the headless client: Talk-to on Fortunato, Olivia and Diango, the options that open
 * their shops (and one that doesn't), with the real NPC ids, as the registered handlers run them. The rl-client
 * script test-runner/examples/draynor-market.json does the same in the real market.
 */
class HeadlessDraynorMarketTest {
    private val jug = ObjType(JUG, "Jug", tradeable = true)
    private val objs = ObjTypes.of(jug)

    private fun shop(key: String, inv: Int, npc: Int) =
        ShopType(key, key, invId = inv, size = 1, general = false, sellAt = 1000, buyAt = 600, changePer = 20, stock = listOf(ShopStock(jug, 3, 100)), npcs = listOf(npc))

    private val talk = listOf("Talk-to", null, "Trade", null, null)
    private val spawns = listOf(
        NpcSpawn(NpcType(DraynorMarket.FORTUNATO, "Fortunato", ops = talk, internalName = "rag_wine_merchant"), 3221, 3220, source = "test"),
        NpcSpawn(NpcType(DraynorMarket.OLIVIA, "Olivia", ops = talk, internalName = "seed_merchant"), 3223, 3220, source = "test"),
        NpcSpawn(NpcType(DraynorMarket.DIANGO, "Diango", ops = talk, internalName = "aprilfoolshorsesalesman"), 3221, 3216, source = "test"),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs, npcSpawns = spawns,
            shopTypes = ShopTypes(listOf(shop("wine", WINE_INV, DraynorMarket.FORTUNATO), shop("seeds", SEED_INV, DraynorMarket.OLIVIA), shop("toys", TOY_INV, DraynorMarket.DIANGO))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.talkTo(id: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, DraynorMarket.TALK_TO)
    }

    /** Waits for a page showing [key] and continues past it. */
    private fun HeadlessClient.page(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.pick(option: Int) {
        awaitDialogue("the menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    private fun HeadlessClient.awaitShop(inv: Int) {
        await("the shop") { if (ShopScreen.MAIN in openInterfaces()) Unit else null }
        await("its stock") { if (inventory(inv) == listOf(InvObj(JUG, 3))) Unit else null }
    }

    @Test
    fun `Fortunato's yes opens his shop`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.talkTo(DraynorMarket.FORTUNATO)
                for (key in listOf("help", "back", "please", "just_looking", "wonderful", "stock", "something")) client.page("fortunato.$key")
                client.pick(1)
                client.page("fortunato.yes")
                client.awaitShop(WINE_INV)
            }
        }
    }

    @Test
    fun `Olivia's yes opens her shop, and her no doesn't`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.talkTo(DraynorMarket.OLIVIA)
                client.page("olivia.seeds")
                client.pick(2)
                client.page("olivia.no_thanks")
                client.await("the talk to end") { if (client.dialogueView == null) Unit else null }
                client.awaitTicks(2)
                assertTrue(ShopScreen.MAIN !in client.openInterfaces())
                client.talkTo(DraynorMarket.OLIVIA)
                client.page("olivia.seeds")
                client.pick(1)
                client.awaitShop(SEED_INV)
            }
        }
    }

    @Test
    fun `Diango's plates and selling options both open his shop`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.talkTo(DraynorMarket.DIANGO)
                client.page("diango.howdy")
                client.pick(3)
                client.page("diango.selling")
                client.awaitShop(TOY_INV)
            }
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.talkTo(DraynorMarket.DIANGO)
                client.page("diango.howdy")
                client.pick(1)
                for (key in listOf("plates", "thieves", "plates_2")) client.page("diango.$key")
                client.awaitShop(TOY_INV)
            }
        }
    }

    private companion object {
        const val JUG = 1935
        const val WINE_INV = 384
        const val SEED_INV = 273
        const val TOY_INV = 137
    }
}
