package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.Ellis
import osrsps.game.content.TanningLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Al Kharid step 4b through the headless client: Ellis's talk, with cowhides carried, opens the tanning screen, and Tan
 * All there turns both hides into soft leather for a coin each; Trade opens it too, and Tan 1 makes hard leather for 3.
 * The rl-client script test-runner/examples/al-kharid-tanning.json does the same in the real client.
 */
class HeadlessEllisTest {
    private val coins = ObjType(COINS, "Coins", stackable = true)
    private val cowhide = ObjType(COWHIDE, "Cowhide")
    private val soft = ObjType(SOFT_LEATHER, "Leather")
    private val hard = ObjType(HARD_LEATHER, "Hard leather")
    private val objs = ObjTypes.of(coins, cowhide, soft, hard)

    private val ellis = NpcType(Ellis.ID, "Ellis", ops = listOf("Talk-to", null, "Trade", null, null), internalName = "ellis_tanner")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs, npcTypes = NpcTypes.of(ellis),
            npcSpawns = listOf(NpcSpawn(ellis, 3221, 3220, source = "test")), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.awaitTanning() = await("the tanning screen") { if (Ellis.INTERFACE in openInterfaces()) Unit else null }

    private fun HeadlessClient.ellis() = await("Ellis in view") { npcs().firstOrNull { it.id == Ellis.ID } }

    @Test
    fun `Ellis's talk opens the tanning screen and Tan All tans both hides`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $COINS 10")
                client.command("item $COWHIDE 2")
                client.await("the coins and hides") { if (client.held(COINS) == 10 && client.held(COWHIDE) == 2) Unit else null }
                client.npcOp(client.ellis().index, Ellis.TALK_TO)
                client.awaitDialogue("his greeting") { it is DialogueView.Chat && it.text == "[ellis.greeting]" }
                client.continueDialogue()
                client.awaitDialogue("his offer") { it is DialogueView.Chat && it.text == "[ellis.some_hides]" }
                client.continueDialogue()
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.awaitTanning()
                client.ifButton(Ellis.INTERFACE, TAN_ALL, 1)
                client.awaitMessage("the tanning") { it == DialogueText.NONE[TanningLines.TANNED_SOME, "count" to 2, "hide" to "cowhides"] }
                client.await("2 soft leather for 2 coins") {
                    if (client.held(SOFT_LEATHER) == 2 && client.held(COWHIDE) == 0 && client.held(COINS) == 8) Unit else null
                }
            }
        }
    }

    @Test
    fun `Trade opens the tanning screen and Tan 1 makes hard leather for 3 coins`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $COINS 10")
                client.command("item $COWHIDE 2")
                client.await("the coins and hides") { if (client.held(COINS) == 10 && client.held(COWHIDE) == 2) Unit else null }
                client.npcOp(client.ellis().index, Ellis.TRADE)
                client.awaitTanning()
                client.ifButton(Ellis.INTERFACE, TAN_1_HARD, 1)
                client.await("1 hard leather for 3 coins") {
                    if (client.held(HARD_LEATHER) == 1 && client.held(COWHIDE) == 1 && client.held(COINS) == 7) Unit else null
                }
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val COWHIDE = 1739
        const val SOFT_LEATHER = 1741
        const val HARD_LEATHER = 1743

        /** Slot a's Tan All, and slot b's Tan 1 (components 124 and 149 of the 241 cache's tanner). */
        const val TAN_ALL = 124
        const val TAN_1_HARD = 149
    }
}
