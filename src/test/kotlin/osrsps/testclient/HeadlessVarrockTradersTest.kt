package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.VarrockTraders
import osrsps.game.dialogue.Chatbox
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Varrock step 5b through the headless client: Benny's newspaper bought and not, Baraek's fur bought at 20 and at 18
 * and not at all, furs sold to him, and the Apothecary's talk through a topic and back to his menu. The rl-client
 * script test-runner/examples/varrock-traders.json does the same in Varrock's square.
 */
class HeadlessVarrockTradersTest {
    private val coins = ObjType(COINS, "Coins", stackable = true)
    private val fur = ObjType(VarrockTraders.FUR, "Fur", cost = 20, tradeable = true)
    private val bearFur = ObjType(VarrockTraders.BEAR_FUR, "Bear fur", cost = 10, tradeable = true)
    private val greyWolfFur = ObjType(VarrockTraders.GREY_WOLF_FUR, "Grey wolf fur", cost = 50, tradeable = true)
    private val newspaper = ObjType(VarrockTraders.NEWSPAPER, "Newspaper", cost = 50, tradeable = true)

    private val talk = listOf("Talk-to", null, null, null, null)
    private val apothecary = NpcType(VarrockTraders.APOTHECARY, "Apothecary", ops = listOf("Talk-to", null, "Potions", null, null), internalName = "apothecary")
    private val baraek = NpcType(VarrockTraders.BARAEK, "Baraek", ops = talk, internalName = "baraek")
    private val benny = NpcType(VarrockTraders.BENNY, "Benny", ops = talk, internalName = "qip_soa_newspaperseller")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(coins, fur, bearFur, greyWolfFur, newspaper),
            npcTypes = NpcTypes.of(apothecary, baraek, benny),
            npcSpawns = listOf(apothecary, baraek, benny).mapIndexed { i, type -> NpcSpawn(type, 3219 + i * 3, 3221, source = "test") },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.hold(id: Int, count: Int) {
        command("item $id $count")
        await("$count of $id") { if (held(id) == count) Unit else null }
    }

    private fun HeadlessClient.talkTo(id: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, VarrockTraders.TALK_TO)
    }

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    /** An item box, whose text the headless client doesn't read: [key] names it for the failure message. */
    private fun HeadlessClient.objbox(key: String) {
        awaitDialogue(key) { it is DialogueView.Other && it.interfaceId == Chatbox.OBJECTBOX }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
    }

    private fun HeadlessClient.pick(option: Int, vararg keys: String) {
        val texts = keys.map { "[$it]" }
        awaitDialogue("the menu $texts") { it is DialogueView.Menu && it.options == texts }
        chooseOption(option)
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    @Test
    fun `Benny sells the Varrock Herald for 50 coins, and not to a player with less`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.hold(COINS, 60)
                client.talkTo(benny.id)
                client.pick(1, "benny.can_i", "benny.how_much", "benny.herald", "benny.anything")
                client.chat("benny.can_i")
                client.chat("benny.certainly")
                client.pick(1, "benny.sure", "benny.no_thanks")
                client.chat("benny.sure")
                client.await("the newspaper") { if (client.held(VarrockTraders.NEWSPAPER) == 1) Unit else null }
                assertEquals(10, client.held(COINS))

                client.talkTo(benny.id)
                client.pick(3, "benny.can_i", "benny.how_much", "benny.herald", "benny.anything")
                client.chat("benny.herald")
                client.chat("benny.illiterate")
                client.chat("benny.captivating")
                client.chat("benny.maybe")
                client.chat("benny.no_cash")
                client.over()
                assertEquals(10, client.held(COINS))
                assertEquals(1, client.held(VarrockTraders.NEWSPAPER))
            }
        }
    }

    @Test
    fun `Baraek sells a fur for 20, or 18 to a player short of 20, and none for less`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                // Once the player holds a fur, the menu offers to sell it too.
                val first = arrayOf("baraek.sell_me", "baraek.quest")
                val buy = arrayOf("baraek.sell_me", "baraek.quest", "baraek.sell_fur")
                client.hold(COINS, 39)
                client.talkTo(baraek.id)
                client.pick(1, *first)
                client.chat("baraek.sell_me")
                client.chat("baraek.twenty")
                client.pick(1, "baraek.yes", "baraek.outrage")
                client.chat("baraek.yes")
                client.objbox("baraek.sold")
                client.over()
                assertEquals(19, client.held(COINS))
                assertEquals(1, client.held(VarrockTraders.FUR))

                client.talkTo(baraek.id)
                client.pick(1, *buy)
                client.chat("baraek.sell_me")
                client.chat("baraek.twenty")
                client.pick(1, "baraek.yes", "baraek.outrage")
                client.chat("baraek.not_enough")
                client.chat("baraek.best_price")
                client.pick(1, "baraek.ok_here", "baraek.no_leave")
                client.chat("baraek.ok_here")
                client.objbox("baraek.sold")
                client.over()
                assertEquals(1, client.held(COINS))
                assertEquals(2, client.held(VarrockTraders.FUR))

                client.talkTo(baraek.id)
                client.pick(1, *buy)
                client.chat("baraek.sell_me")
                client.chat("baraek.twenty")
                client.pick(1, "baraek.yes", "baraek.outrage")
                client.chat("baraek.not_enough")
                client.chat("baraek.best_price")
                client.pick(1, "baraek.ok_here", "baraek.no_leave")
                client.chat("baraek.ok_here")
                client.chat("baraek.not_either")
                client.chat("baraek.no_cheaper")
                client.chat("baraek.never_mind")
                client.over()
                assertEquals(1, client.held(COINS))
                assertEquals(2, client.held(VarrockTraders.FUR))

                client.talkTo(baraek.id)
                client.pick(2, *buy)
                client.chat("baraek.quest")
                client.chat("baraek.kiddo")
                client.over()
            }
        }
    }

    @Test
    fun `Baraek buys fur and bear fur at 12, and grey wolf fur at 120`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.hold(VarrockTraders.FUR, 2)
                client.hold(VarrockTraders.BEAR_FUR, 1)
                client.talkTo(baraek.id)
                client.pick(3, "baraek.sell_me", "baraek.quest", "baraek.sell_fur")
                client.chat("baraek.sell_fur")
                client.chat("baraek.look")
                client.objbox("baraek.hand_fur")
                client.chat("baraek.condition")
                client.pick(1, "baraek.thatll_do", "baraek.keep")
                client.chat("baraek.thatll_do")
                client.chat("baraek.thanks")
                client.over()
                assertEquals(36, client.held(COINS))
                assertEquals(0, client.held(VarrockTraders.FUR) + client.held(VarrockTraders.BEAR_FUR))

                client.hold(VarrockTraders.GREY_WOLF_FUR, 2)
                client.talkTo(baraek.id)
                client.pick(3, "baraek.sell_me", "baraek.quest", "baraek.sell_grey")
                client.chat("baraek.sell_grey")
                client.chat("baraek.grey_1")
                client.chat("baraek.grey_2")
                client.chat("baraek.grey_3")
                client.pick(1, "baraek.yep", "baraek.throat")
                client.chat("baraek.yep")
                client.chat("baraek.thanks")
                client.over()
                assertEquals(276, client.held(COINS))
                assertEquals(0, client.held(VarrockTraders.GREY_WOLF_FUR))
            }
        }
    }

    @Test
    fun `the Apothecary's topics bring his menu back, and no thanks ends the talk`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                val first = arrayOf("apothecary.something_else", "apothecary.no_thanks")
                val topics = arrayOf("apothecary.gossip", "apothecary.hair", "apothecary.free", "apothecary.no_thanks")
                client.talkTo(apothecary.id)
                client.chat("apothecary.greeting")
                client.pick(1, *first)
                client.pick(1, *topics)
                for (key in listOf("gossip", "gossip_1", "gossip_2", "gossip_3", "gossip_4")) client.chat("apothecary.$key")
                client.pick(1, *first)
                client.pick(2, *topics)
                for (key in listOf("hair", "hair_1", "hair_2")) client.chat("apothecary.$key")
                client.pick(1, *first)
                client.pick(3, *topics)
                client.chat("apothecary.free")
                client.chat("apothecary.charity")
                client.pick(2, *first)
                client.chat("apothecary.no_thanks")
                client.over()
            }
        }
    }

    private companion object {
        const val COINS = 995
    }
}
