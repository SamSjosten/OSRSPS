package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.GrandExchangeExperts
import osrsps.game.content.JewelleryDecant
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.map.CollisionMap

/**
 * Murky Matt through the headless client: Decant combines two games necklace(1)s into a (2) and two glory(1)s into a
 * glory(2) and an uncharged glory, and says when there is nothing to combine; his talk's fourth option enchants the ruby
 * rings asked for at 250 coins each; his talk has the Lunar Diplomacy lines. The rl-client script
 * test-runner/examples/misthalin-fixes-11.json does the same on the live cache.
 */
class HeadlessMurkyMattTest {
    private val matt = NpcType(GrandExchangeExperts.MURKY_MATT, "Murky Matt (runes)", ops = listOf("Talk-to", null, "Prices", "Decant", null), internalName = "ge_expert_runes")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = listOf(
            ObjType(GAMES_1, "Games necklace(1)"), ObjType(GAMES_2, "Games necklace(2)"), ObjType(GAMES_1 + 1, "Games necklace(1)", stackable = true),
            ObjType(GLORY_1, "Amulet of glory(1)"), ObjType(GLORY_2, "Amulet of glory(2)"), ObjType(GLORY, "Amulet of glory"),
            ObjType(JewelleryDecant.RUBY_RING, "Ruby ring"), ObjType(JewelleryDecant.RING_OF_FORGING, "Ring of forging"),
            ObjType(JewelleryDecant.COINS, "Coins", stackable = true),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(*objs.toTypedArray()),
            npcTypes = NpcTypes.of(matt), npcSpawns = listOf(NpcSpawn(matt, 3224, 3218, wander = 0, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("item $id") { if (count(id) >= before + count) Unit else null }
    }

    private fun HeadlessClient.says(text: String) = awaitDialogue(text) { it is DialogueView.Chat && it.text == text }.also { continueDialogue() }

    @Test
    fun `Decant combines charges, a glory's leftover coming back uncharged, and says when there is nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("Matt in view") { client.npcs().firstOrNull { it.id == matt.id } }
                client.npcOp(npc.index, GrandExchangeExperts.DECANT)
                client.says("[murky_matt.nothing_to_combine]")
                client.give(GAMES_1)
                client.give(GAMES_1)
                client.give(GLORY_1)
                client.give(GLORY_1)
                client.npcOp(npc.index, GrandExchangeExperts.DECANT)
                client.says("[murky_matt.all_done]")
                assertEquals(listOf(0, 1, 0, 1, 1), listOf(GAMES_1, GAMES_2, GLORY_1, GLORY_2, GLORY).map { client.count(it) })
                client.npcOp(npc.index, GrandExchangeExperts.DECANT)
                client.says("[murky_matt.nothing_to_combine]")
            }
        }
    }

    @Test
    fun `a huge noted stack is counted, not listed, and too many pieces back leaves it whole`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                val npc = client.await("Matt in view") { client.npcs().firstOrNull { it.id == matt.id } }
                // Two billion one-charge necklaces would come back as 250 million: no backpack holds them.
                client.give(GAMES_1 + 1, 2_000_000_000)
                client.npcOp(npc.index, GrandExchangeExperts.DECANT)
                client.says("[murky_matt.no_space]")
                assertEquals(2_000_000_000, client.count(GAMES_1 + 1))
            }
        }
    }

    @Test
    fun `his talk has the Lunar lines and enchants ruby rings for 250 coins each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val npc = client.await("Matt in view") { client.npcs().firstOrNull { it.id == matt.id } }
                fun menu(option: Int) {
                    client.awaitDialogue("a menu") { it is DialogueView.Menu }
                    client.chooseOption(option)
                }
                client.npcOp(npc.index, GrandExchangeExperts.TALK_TO)
                client.says("[murky_matt.greeting]")
                menu(1)
                for (key in listOf("pirate") + (1..7).map { "pirate_$it" } + (1..6).map { "lunar_$it" }) client.says("[murky_matt.$key]")
                menu(3)
                for (key in listOf("swab") + (1..5).map { "swab_$it" }) client.says("[murky_matt.$key]")
                client.awaitTicks(2)
                client.npcOp(npc.index, GrandExchangeExperts.TALK_TO)
                client.says("[murky_matt.greeting]")
                menu(4)
                client.says("[murky_matt.forging]")
                client.says("[murky_matt.bring_rubies]")

                client.give(JewelleryDecant.RUBY_RING)
                client.give(JewelleryDecant.RUBY_RING)
                client.give(JewelleryDecant.RUBY_RING)
                client.give(JewelleryDecant.COINS, 600)
                client.npcOp(npc.index, GrandExchangeExperts.TALK_TO)
                client.says("[murky_matt.greeting]")
                menu(4)
                client.says("[murky_matt.forging]")
                client.says("[murky_matt.how_many]")
                client.await("the count prompt") {
                    client.clientScripts().lastOrNull { it.id == ScriptContext.MESLAYER_MODE7 }?.takeIf { it.args == listOf("[murky_matt.how_many_prompt]") }
                }
                client.resumeCount(3)
                client.says("[murky_matt.pleasure]")
                // 600 coins pay for two of the three.
                assertEquals(listOf(1, 2, 100), listOf(JewelleryDecant.RUBY_RING, JewelleryDecant.RING_OF_FORGING, JewelleryDecant.COINS).map { client.count(it) })
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val GAMES_1 = 3867
        const val GAMES_2 = 3865
        const val GLORY_1 = 1706
        const val GLORY_2 = 1708
        const val GLORY = 1704
    }
}
