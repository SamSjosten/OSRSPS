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
import osrsps.game.content.Ned
import osrsps.game.content.Rick
import osrsps.game.content.RickLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.map.CollisionMap

/**
 * Draynor piece step 15d through the headless client and the registered handlers: Rick's talk about himself, his
 * crystals' places and the refusal, turning away a player without 75,000 coins, and Buy-crystals selling as many as the
 * coins allow. He stands on open ground. The rl-client script test-runner/examples/rick.json does the same on the real
 * map.
 */
class HeadlessRickTest {
    private val rick = NpcType(Rick.RICK, "Rick", ops = listOf("Talk-to", null, "Buy-crystals", null, null))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Rick.ESCAPE_CRYSTAL + 1)
        objs[Ned.COINS] = ObjType(Ned.COINS, "Coins", stackable = true)
        objs[Rick.ESCAPE_CRYSTAL] = ObjType(Rick.ESCAPE_CRYSTAL, "Escape crystal")
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(rick), npcSpawns = listOf(NpcSpawn(rick, 3220, 3216, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.op(server: TestServer, op: Int) {
        await("the login rebuild") { position }
        npcOp(server.world.npcs.all.first { it.type.id == Rick.RICK }.index, op)
    }

    private fun HeadlessClient.sees(vararg lines: Line) {
        for (line in lines) {
            awaitDialogue(line.key) { it is DialogueView.Chat && it.text == DialogueText.NONE[line] }
            continueDialogue()
        }
    }

    private fun HeadlessClient.pick(option: Int) {
        awaitDialogue("a menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    private fun HeadlessClient.count(id: Int): Long = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    @Test
    fun `his talk about himself goes to the shorter menu, and where the crystals work ends in the refusal`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                with(RickLines) {
                    client.op(server, Rick.TALK_TO)
                    client.sees(HEY, HELLO)
                    client.pick(1)
                    client.sees(WHO, NAME, WHY_NOT, PUMPKIN, LUCKILY, LUCKY)
                    client.pick(1)
                    client.sees(DOING, STUDENT, ARDOUGNE, FIELDS, PUMPKIN_FIELDS)
                    client.pick(3)
                    client.sees(GOODBYE, BYE)

                    client.op(server, Rick.TALK_TO)
                    client.sees(HEY, HELLO)
                    client.pick(3)
                    client.sees(WHICH, PLACES, GAUNTLET, BUY_ONE)
                    client.pick(2)
                    client.sees(TOO_EXPENSIVE, LOT_GOES_INTO)
                }
            }
        }
    }

    @Test
    fun `he turns away a player without the coins, then sells as many as the coins allow`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                with(RickLines) {
                    client.op(server, Rick.BUY_CRYSTALS)
                    client.sees(CANT_AFFORD)

                    client.command("item ${Ned.COINS} 160000")
                    client.await("the coins") { if (client.count(Ned.COINS) == 160_000L) Unit else null }
                    client.op(server, Rick.BUY_CRYSTALS)
                    client.pick(1)
                    client.await("the amount prompt") {
                        client.clientScripts().lastOrNull { it.id == ScriptContext.MESLAYER_MODE7 }
                    }
                    client.resumeCount(5)
                    client.await("two crystals") { if (client.count(Rick.ESCAPE_CRYSTAL) == 2L) Unit else null }
                    assertEquals(10_000L, client.count(Ned.COINS))
                }
            }
        }
    }

    @Test
    fun `with a full backpack he still sells one when it takes every coin`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                with(RickLines) {
                    client.command("item ${Ned.COINS} 75000")
                    client.command("item ${Rick.ESCAPE_CRYSTAL} 27")
                    client.await("a full backpack") { if (client.inventory(BACKPACK).count { it != null } == 28) Unit else null }
                    client.op(server, Rick.BUY_CRYSTALS)
                    client.pick(1)
                    client.await("the amount prompt") {
                        client.clientScripts().lastOrNull { it.id == ScriptContext.MESLAYER_MODE7 }
                    }
                    client.resumeCount(1)
                    client.await("the 28th crystal") { if (client.count(Rick.ESCAPE_CRYSTAL) == 28L) Unit else null }
                    assertEquals(0L, client.count(Ned.COINS))
                }
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
    }
}
