package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.combat.FoodLines
import osrsps.game.content.AlKharidTraders
import osrsps.game.dialogue.DialogueText
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Al Kharid step 4a through the headless client: a kebab bought through Karim's talk and eaten from the backpack (a
 * second one clicked at once waits for the eat delay), and silk haggled down to 2 gp from the Silk trader. The rl-client
 * script test-runner/examples/al-kharid-traders.json does the same in the real client.
 */
class HeadlessAlKharidTradersTest {
    private val coins = ObjType(COINS, "Coins", stackable = true)
    private val kebab = ObjType(AlKharidTraders.KEBAB, "Kebab", iops = listOf("Eat", null, null, null, "Drop"), internalName = "kebab")
    private val silk = ObjType(AlKharidTraders.SILK, "Silk", tradeable = true)
    private val objs = ObjTypes.of(coins, kebab, silk)

    private val talk = listOf("Talk-to", null, null, null, null)
    private val karim = NpcType(AlKharidTraders.KARIM, "Karim", ops = talk, internalName = "kebab_seller")
    private val silkTrader = NpcType(AlKharidTraders.SILK_TRADER, "Silk trader", ops = talk, internalName = "silk_trader")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs, npcTypes = NpcTypes.of(karim, silkTrader),
            npcSpawns = listOf(NpcSpawn(karim, 3221, 3220, source = "test"), NpcSpawn(silkTrader, 3225, 3220, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.talkTo(id: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, AlKharidTraders.TALK_TO)
    }

    /** Waits for the chat line with key [key] and clicks on past it. */
    private fun HeadlessClient.line(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.choose(option: Int) {
        awaitDialogue("a menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    @Test
    fun `a kebab bought from Karim is eaten, and a second waits for the eat delay`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $COINS 5")
                client.command("item ${AlKharidTraders.KEBAB}")
                client.await("the coins and a kebab") { if (client.held(COINS) == 5 && client.held(AlKharidTraders.KEBAB) == 1) Unit else null }
                client.talkTo(karim.id)
                client.line("karim.offer")
                client.choose(2)
                client.line("karim.yes_please")
                client.await("the kebab paid for") { if (client.held(COINS) == 4 && client.held(AlKharidTraders.KEBAB) == 2) Unit else null }

                val slots = client.inventory(Inventory.INV).withIndex().filter { it.value?.id == AlKharidTraders.KEBAB }.map { it.index }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, EAT_BUTTON_OP, slots[0], AlKharidTraders.KEBAB)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, EAT_BUTTON_OP, slots[1], AlKharidTraders.KEBAB)
                client.awaitMessage("the kebab eaten") { it == DialogueText.NONE[FoodLines.EAT, "food" to "kebab"] }
                client.awaitTicks(1)
                assertEquals(1, client.held(AlKharidTraders.KEBAB), "the second click came inside the eat delay")
            }
        }
    }

    @Test
    fun `silk haggled down to 2 gp from the Silk trader`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $COINS 5")
                client.await("the coins") { if (client.held(COINS) == 5) Unit else null }
                client.talkTo(silkTrader.id)
                client.line("silk_trader.offer")
                client.choose(1)
                client.line("silk_trader.how_much")
                client.line("silk_trader.three_gp")
                client.choose(1)
                client.line("silk_trader.too_much")
                client.line("silk_trader.two_gp")
                client.line("silk_trader.varrock")
                client.choose(1)
                client.line("silk_trader.two_gp_good")
                client.await("the silk for 2 gp") { if (client.held(COINS) == 3 && client.held(AlKharidTraders.SILK) == 1) Unit else null }
            }
        }
    }

    private companion object {
        const val COINS = 995

        /** Eat is iop1, which the inventory sends as button op 2. */
        const val EAT_BUTTON_OP = 2
    }
}
