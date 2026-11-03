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
import osrsps.game.content.Jalarast
import osrsps.game.content.JalarastLines
import osrsps.game.content.Ned
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Draynor piece step 15c through the headless client and the registered handlers: Wizard Jalarast turning away a player
 * without a piece's bark, fine cloth or coins, making a splitbark body from his talk and gauntlets from Trade, and his
 * talk's explanation branch. He stands on open ground. The rl-client script test-runner/examples/jalarast.json does the
 * same on the real map.
 */
class HeadlessJalarastTest {
    private val jalarast = NpcType(Jalarast.JALARAST, "Wizard Jalarast", ops = listOf("Talk-to", null, "Trade", null, null))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Jalarast.FINE_CLOTH + 1)
        objs[Jalarast.BARK] = ObjType(Jalarast.BARK, "Bark")
        objs[Jalarast.FINE_CLOTH] = ObjType(Jalarast.FINE_CLOTH, "Fine cloth")
        objs[Ned.COINS] = ObjType(Ned.COINS, "Coins", stackable = true)
        for (piece in Jalarast.Piece.entries) objs[piece.obj] = ObjType(piece.obj, piece.name)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(jalarast), npcSpawns = listOf(NpcSpawn(jalarast, 3220, 3216, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.op(server: TestServer, op: Int) {
        await("the login rebuild") { position }
        npcOp(server.world.npcs.all.first { it.type.id == Jalarast.JALARAST }.index, op)
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
    fun `he turns away a player without the materials, then makes a body from his talk and gauntlets from Trade`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                with(JalarastLines) {
                    client.op(server, Jalarast.TRADE)
                    client.pick(4)
                    client.sees(NEED_GAUNTLETS)

                    client.command("item ${Jalarast.BARK} 5")
                    client.command("item ${Jalarast.FINE_CLOTH} 5")
                    client.command("item ${Ned.COINS} 40000")
                    client.await("the materials") { if (client.count(Ned.COINS) == 40_000L && client.count(Jalarast.FINE_CLOTH) == 5L) Unit else null }
                    client.op(server, Jalarast.TALK_TO)
                    client.sees(HELLO)
                    client.pick(3)
                    client.sees(MAKE_ARMOUR, CERTAINLY)
                    client.pick(2)
                    client.sees(THERE_YOU_GO)
                    assertEquals(1L, client.count(Jalarast.Piece.BODY.obj))
                    assertEquals(1L, client.count(Jalarast.BARK))
                    assertEquals(1L, client.count(Jalarast.FINE_CLOTH))
                    assertEquals(3_000L, client.count(Ned.COINS))

                    client.op(server, Jalarast.TRADE)
                    client.pick(4)
                    client.sees(THERE_YOU_GO)
                    assertEquals(1L, client.count(Jalarast.Piece.GAUNTLETS.obj))
                    assertEquals(2_000L, client.count(Ned.COINS))
                    // No bark or cloth left for boots.
                    client.op(server, Jalarast.TRADE)
                    client.pick(5)
                    client.sees(NEED_BOOTS)
                }
            }
        }
    }

    @Test
    fun `his explanation, then how much he needs`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                with(JalarastLines) {
                    client.op(server, Jalarast.TALK_TO)
                    client.sees(HELLO)
                    client.pick(1)
                    client.sees(WHAT_DO, STUDYING)
                    client.pick(1)
                    client.sees(WHATS_THAT, SPECIAL, EASY)
                    client.pick(2)
                    client.sees(MAKE_SOME, NEED_BARK, DISCOUNT)
                    client.pick(2)
                    client.sees(HOW_MUCH, PIECES, PRICES, GO_LOOKING)
                }
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
    }
}
