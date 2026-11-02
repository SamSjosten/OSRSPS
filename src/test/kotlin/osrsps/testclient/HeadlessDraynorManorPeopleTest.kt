package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.DraynorManorPeople
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopStock
import osrsps.game.shop.ShopType
import osrsps.game.shop.ShopTypes
import osrsps.map.CollisionMap

/**
 * Draynor piece step 12b through the headless client: Talk-to on Oddenstein, the witch and Ava with the real NPC ids, as
 * the registered handlers run them; Ava's "stuff for sale" and her Trade open her shop, heading off doesn't. The rl-client
 * script test-runner/examples/draynor-manor-people.json does the same in the manor itself.
 */
class HeadlessDraynorManorPeopleTest {
    private val feather = ObjType(FEATHER, "Feather", tradeable = true, stackable = true)
    private val objs = ObjTypes.of(feather)

    private val shop = ShopType(
        "avas_odds_and_ends", "Ava's Odds and Ends", invId = AVA_INV, size = 6, general = false, sellAt = 1300, buyAt = 400,
        changePer = 30, stock = listOf(ShopStock(feather, 1000, 100)), npcs = listOf(DraynorManorPeople.AVA),
    )

    private val talk = listOf("Talk-to", null, null, null, null)
    private val spawns = listOf(
        NpcSpawn(NpcType(DraynorManorPeople.ODDENSTEIN, "Professor Oddenstein", ops = talk, internalName = "professor_oddenstein"), 3221, 3220, source = "test"),
        NpcSpawn(NpcType(DraynorManorPeople.WITCH, "Witch", ops = talk, internalName = "anma_witch"), 3223, 3220, source = "test"),
        NpcSpawn(
            NpcType(DraynorManorPeople.AVA, "Ava", ops = listOf("Talk-to", null, "Trade", "Devices", null), internalName = "anma_assistant"),
            3221, 3216, source = "test",
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs, npcSpawns = spawns,
            shopTypes = ShopTypes(listOf(shop)), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.op(id: Int, op: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, op)
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

    private fun HeadlessClient.awaitShop() {
        await("the shop") { if (ShopScreen.MAIN in openInterfaces()) Unit else null }
        await("its stock") { if (inventory(AVA_INV) == listOf(InvObj(FEATHER, 1000))) Unit else null }
    }

    private fun HeadlessClient.awaitEnd() = await("the talk to end") { if (dialogueView == null) Unit else null }

    @Test
    fun `Oddenstein and the witch talk`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.op(DraynorManorPeople.ODDENSTEIN, DraynorManorPeople.TALK_TO)
                client.page("oddenstein.careful")
                client.pick(2)
                client.page("oddenstein.house")
                client.page("oddenstein.tenants")
                client.awaitEnd()
                client.op(DraynorManorPeople.WITCH, DraynorManorPeople.TALK_TO)
                for (key in listOf("ickle", "wondered", "clearly_not")) client.page("manor_witch.$key")
                client.awaitEnd()
            }
        }
    }

    @Test
    fun `Ava's stuff for sale and her Trade open her shop, and heading off doesn't`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.op(DraynorManorPeople.AVA, DraynorManorPeople.TALK_TO)
                client.page("ava.hello")
                client.pick(4)
                client.pick(2)
                client.page("ava.head_off")
                client.page("ava.bed")
                client.awaitEnd()
                client.awaitTicks(2)
                assertTrue(ShopScreen.MAIN !in client.openInterfaces())
                client.op(DraynorManorPeople.AVA, DraynorManorPeople.TALK_TO)
                client.page("ava.hello")
                client.pick(4)
                client.pick(1)
                client.page("ava.stuff")
                client.page("ava.shop")
                client.awaitShop()
            }
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.op(DraynorManorPeople.AVA, TRADE)
                client.awaitShop()
            }
        }
    }

    private companion object {
        const val FEATHER = 314
        const val AVA_INV = 489
        const val TRADE = 3
    }
}
