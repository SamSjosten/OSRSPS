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
import osrsps.game.content.VarrockFitzharmons
import osrsps.game.dialogue.Chatbox
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Varrock step 8b through the headless client: Dimintheis's talk every way, handing back family gauntlets only to a
 * player without a pair; Johnathon's talk and his Gauntlets option, turning a pair into chaos gauntlets for 25,000
 * coins, not for less and not without a pair; Old Man Yarlo's talk. The rl-client script
 * test-runner/examples/varrock-fitzharmons.json talks to them in the real city.
 */
class HeadlessVarrockFitzharmonsTest {
    private val coins = ObjType(COINS, "Coins", stackable = true)
    private val gauntlets = VarrockFitzharmons.ALL_GAUNTLETS.map { ObjType(it, "Gauntlets $it", tradeable = false) }

    private val dimintheis = NpcType(VarrockFitzharmons.DIMINTHEIS, "Dimintheis", ops = listOf("Talk-to", null, null, null, null), internalName = "dimintheis")
    private val johnathon = NpcType(
        VarrockFitzharmons.JOHNATHON, "Johnathon", ops = listOf("Talk-to", null, "Gauntlets", null, null), internalName = "johnathon_fitzharmon_2ops",
    )
    private val yarlo = NpcType(VarrockFitzharmons.YARLO, "Old Man Yarlo", ops = listOf("Talk-to", null, null, null, null), internalName = "daddyshome_daddy")
    private val all = listOf(dimintheis, johnathon, yarlo)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(coins, *gauntlets.toTypedArray()),
            npcTypes = NpcTypes.of(*all.toTypedArray()),
            npcSpawns = all.mapIndexed { i, type -> NpcSpawn(type, 3216 + i * 2, 3215, wander = 0, source = "test") },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.op(id: Int, option: Int = VarrockFitzharmons.TALK_TO) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, option)
    }

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.message(key: String) {
        awaitDialogue(key) { it is DialogueView.Message && it.text == "[$key]" }
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

    /** Reads on from each of [steps], a key under [prefix], "n:key1,key2" to pick option n, or "!key" for a message box. */
    private fun HeadlessClient.walk(id: Int, prefix: String, vararg steps: String, option: Int = VarrockFitzharmons.TALK_TO) {
        op(id, option)
        for (step in steps) {
            val menu = step.substringBefore(':', "")
            when {
                step.startsWith("!") -> message("$prefix.${step.drop(1)}")
                step.startsWith("@") -> objbox("$prefix.${step.drop(1)}")
                menu.isEmpty() -> chat("$prefix.$step")
                else -> pick(menu.toInt(), *step.substringAfter(':').split(',').map { "$prefix.$it" }.toTypedArray())
            }
        }
        over()
    }

    private fun HeadlessClient.holding(vararg items: Pair<Int, Int>) {
        command("empty")
        for ((id, count) in items) command("item $id $count")
        await("holding ${items.toList()}") { if (items.all { (id, count) -> held(id) == count }) Unit else null }
    }

    @Test
    fun `Dimintheis talks every way and hands back gauntlets only to a player without a pair`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val quests = arrayOf("1:quests,other", "quests", "!murmur", "no_trouble", "insist", "content")
                client.walk(VarrockFitzharmons.DIMINTHEIS, "dimintheis", *quests, "1:money,help", "money", "pardon", "nothing")
                client.walk(VarrockFitzharmons.DIMINTHEIS, "dimintheis", *quests, "2:money,help", "help", "sons", "straight_there", "!rewards")

                val other = arrayOf("2:quests,other", "greeting", "gauntlets")
                client.walk(VarrockFitzharmons.DIMINTHEIS, "dimintheis", *other, "returned", "2:yes_please,not_now", "not_now", "any_time")
                assertEquals(0, client.held(VarrockFitzharmons.FAMILY_GAUNTLETS))
                client.walk(VarrockFitzharmons.DIMINTHEIS, "dimintheis", *other, "returned", "1:yes_please,not_now", "yes_please", "@hands_over")
                client.await("the gauntlets") { if (client.held(VarrockFitzharmons.FAMILY_GAUNTLETS) == 1) Unit else null }

                // Any pair owned, his sons' too: he hasn't got them.
                for (pair in VarrockFitzharmons.ALL_GAUNTLETS) {
                    client.holding(pair to 1)
                    client.walk(VarrockFitzharmons.DIMINTHEIS, "dimintheis", *other, "have_them")
                    assertEquals(1, client.held(pair))
                }
            }
        }
    }

    @Test
    fun `Johnathon turns a pair into chaos gauntlets for 25,000 coins, and Yarlo talks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val opening = arrayOf("hero", "agree", "anything")
                client.walk(VarrockFitzharmons.JOHNATHON, "johnathon", *opening, "2:change,no_thanks", "no_thanks")

                // Without a pair to change.
                client.holding(COINS to 25_000)
                client.walk(VarrockFitzharmons.JOHNATHON, "johnathon", *opening, "1:change,no_thanks", "change", "no_gauntlets")
                client.holding(COINS to 25_000, VarrockFitzharmons.CHAOS_GAUNTLETS to 1)
                client.walk(VarrockFitzharmons.JOHNATHON, "johnathon", "no_gauntlets", option = VarrockFitzharmons.GAUNTLETS_OP)

                // Declined, and too poor.
                client.holding(COINS to 24_999, VarrockFitzharmons.COOKING_GAUNTLETS to 1)
                client.walk(VarrockFitzharmons.JOHNATHON, "johnathon", "price price=25,000", "2:accept,decline", "decline", option = VarrockFitzharmons.GAUNTLETS_OP)
                client.walk(VarrockFitzharmons.JOHNATHON, "johnathon", "price price=25,000", "1:accept,decline", "accept", "no_coins", option = VarrockFitzharmons.GAUNTLETS_OP)
                assertEquals(24_999, client.held(COINS))
                assertEquals(1, client.held(VarrockFitzharmons.COOKING_GAUNTLETS))

                // Changed, through the talk and through the option.
                for ((pair, viaTalk) in listOf(VarrockFitzharmons.GOLDSMITH_GAUNTLETS to true, VarrockFitzharmons.FAMILY_GAUNTLETS to false)) {
                    client.holding(COINS to 25_000, pair to 1)
                    val steps = arrayOf("price price=25,000", "1:accept,decline", "accept", "@chants")
                    if (viaTalk) {
                        client.walk(VarrockFitzharmons.JOHNATHON, "johnathon", *opening, "1:change,no_thanks", "change", *steps)
                    } else {
                        client.walk(VarrockFitzharmons.JOHNATHON, "johnathon", *steps, option = VarrockFitzharmons.GAUNTLETS_OP)
                    }
                    client.await("chaos gauntlets") { if (client.held(VarrockFitzharmons.CHAOS_GAUNTLETS) == 1) Unit else null }
                    assertEquals(0, client.held(pair))
                    assertEquals(0, client.held(COINS))
                }

                client.walk(VarrockFitzharmons.YARLO, "yarlo", "happier", "1:good_news,construction", "good_news")
                client.walk(VarrockFitzharmons.YARLO, "yarlo", "happier", "2:good_news,construction", "construction", "thanks")
            }
        }
    }

    private companion object {
        const val COINS = 995
    }
}
