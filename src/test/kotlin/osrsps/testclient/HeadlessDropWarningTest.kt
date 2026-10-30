package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.AllSettings
import osrsps.game.DropLines
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.dialogue.DialogueText
import osrsps.map.CollisionMap

/**
 * The All Settings drop item warning: a new account warns before dropping an item whose high alchemy value is 30,000
 * coins or more, No keeps it, Yes drops it, and with the warning off it drops at once. The rl-client script
 * test-runner/examples/settings-drop-warning.json clicks the real client.
 */
class HeadlessDropWarningTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(CHEAP + 1)
        // 50,000 coins of value is 30,000 at high alchemy: just at the default threshold.
        objs[VALUABLE] = ObjType(VALUABLE, "Shiny thing", cost = 50_000, tradeable = true)
        objs[CHEAP] = ObjType(CHEAP, "Dull thing", cost = 49_998, tradeable = true)
        World(
            crcs, CollisionMap().apply { markLoaded(World.SPAWN_X shr 6, World.SPAWN_Z shr 6) }, objTypes = ObjTypes(objs),
            varTypes = VarTypes(6_000, AllSettings.VARBITS), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.drop(id: Int) = ifButton(INVENTORY, INVENTORY_ITEMS, DROP, slotOf(id), id)

    private fun HeadlessClient.held(id: Int) = inventory(BACKPACK).any { it?.id == id }

    @Test
    fun `a valuable item asks first, No keeps it, Yes drops it, and a cheaper one drops at once`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ivy").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $VALUABLE")
                client.command("item $CHEAP")
                client.slotOf(CHEAP)

                client.drop(VALUABLE)
                val menu = client.awaitDialogue("the warning") { it is DialogueView.Menu }
                val words = DialogueText.NONE
                assertEquals(
                    DialogueView.Menu(words[DropLines.TITLE, "item" to "Shiny thing", "value" to "30,000"], listOf(words[DropLines.YES], words[DropLines.NO])),
                    menu,
                )
                client.chooseOption(2)
                client.awaitTicks(2)
                assertTrue(client.held(VALUABLE), "No dropped it")

                client.drop(VALUABLE)
                client.awaitDialogue("the warning again") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.await("the valuable dropped") { if (!client.held(VALUABLE)) Unit else null }

                // 29,998 at high alchemy: under the threshold, so no question.
                client.drop(CHEAP)
                client.await("the cheap one dropped") { if (!client.held(CHEAP)) Unit else null }
            }
        }
    }

    @Test
    fun `with the warning off a valuable item drops at once`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("jack").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("varbit ${AllSettings.VARBIT_DROP_WARNING} 0")
                client.command("item $VALUABLE")
                client.drop(VALUABLE)
                client.await("the valuable dropped") { if (!client.held(VALUABLE)) Unit else null }
            }
        }
    }

    private companion object {
        const val VALUABLE = 1
        const val CHEAP = 2
        const val BACKPACK = 93
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val DROP = 7
    }
}
