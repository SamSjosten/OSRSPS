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
import osrsps.game.content.AlKharidPalace
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Al Kharid step 5 through the headless client: a Keris bought from Osman through his Sophanem talk takes 20,000 coins,
 * and with too few coins he sells nothing. The rl-client script test-runner/examples/al-kharid-palace.json does the
 * same in the real client.
 */
class HeadlessOsmanTest {
    private val coins = ObjType(COINS, "Coins", stackable = true)
    private val keris = ObjType(AlKharidPalace.KERIS, "Keris")
    private val objs = ObjTypes.of(coins, keris)
    private val osman = NpcType(AlKharidPalace.OSMAN, "Osman", ops = listOf("Talk-to", null, null, null, null), internalName = "osman")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs, npcTypes = NpcTypes.of(osman),
            npcSpawns = listOf(NpcSpawn(osman, 3221, 3220, source = "test")), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    /** Waits for the chat line with key [key] and clicks on past it. */
    private fun HeadlessClient.line(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.choose(option: Int) {
        awaitDialogue("a menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    /** Talks to Osman through to taking a Keris. */
    private fun HeadlessClient.askForKeris() {
        val npc = await("Osman in view") { npcs().firstOrNull { it.id == osman.id } }
        npcOp(npc.index, AlKharidPalace.TALK_TO)
        line("osman.hello_again")
        choose(2)
        line("osman.sophanem")
        line("osman.sophanem_reply")
        choose(1)
        line("osman.daggers")
        line("osman.dagger_offer")
        choose(1)
        line("osman.take_one")
    }

    @Test
    fun `a Keris from Osman costs 20,000 coins`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $COINS 25000")
                client.await("the coins") { if (client.held(COINS) == 25_000) Unit else null }
                client.askForKeris()
                client.await("the Keris for 20,000") { if (client.held(AlKharidPalace.KERIS) == 1 && client.held(COINS) == 5_000) Unit else null }
            }
        }
    }

    @Test
    fun `with too few coins Osman sells nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $COINS 19999")
                client.await("the coins") { if (client.held(COINS) == 19_999) Unit else null }
                client.askForKeris()
                client.awaitDialogue("not enough coins") { it is DialogueView.Chat && it.text == "[osman.no_coins]" }
                assertEquals(0, client.held(AlKharidPalace.KERIS))
                assertEquals(19_999, client.held(COINS))
            }
        }
    }

    private companion object {
        const val COINS = 995
    }
}
