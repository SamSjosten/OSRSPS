package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.content.VarrockSweepTalkers
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Toby, James and Meredith from the Misthalin sweep through the headless client: Toby's rewards menu and his goodbye,
 * James's menus coming back round, and Meredith's games menu. James is spawned as the type he shows
 * (`bond_james_visible`). Types and options are the 241 cache's.
 */
class HeadlessVarrockSweepTalkersTest {
    private fun talker(id: Int, name: String) = NpcType(id, name, ops = listOf("Talk-to", null, null, null, null))

    private val toby = talker(VarrockSweepTalkers.TOBY, "Toby")
    private val james = talker(VarrockSweepTalkers.JAMES, "James")
    private val meredith = talker(VarrockSweepTalkers.MEREDITH, "Meredith")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            npcTypes = NpcTypes.of(toby, james, meredith),
            npcSpawns = listOf(
                NpcSpawn(toby, 3224, 3218, wander = 0, source = "test"),
                NpcSpawn(james, 3220, 3218, wander = 0, source = "test"),
                NpcSpawn(meredith, 3222, 3221, wander = 0, source = "test"),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.talk(id: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, VarrockSweepTalkers.TALK_TO)
    }

    private fun HeadlessClient.chat(text: String) {
        awaitDialogue(text) { it is DialogueView.Chat && it.text == text }
        continueDialogue()
    }

    private fun HeadlessClient.menu(vararg options: String) {
        val menu = awaitDialogue("the menu ${options.first()}") { it is DialogueView.Menu && (it as DialogueView.Menu).options.first() == options.first() } as DialogueView.Menu
        assertEquals(options.toList(), menu.options)
    }

    @Test
    fun `Toby describes a tier's rewards and says goodbye`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.talk(VarrockSweepTalkers.TOBY)
                client.chat("[toby.hello]")
                client.menu("[toby.who]", "[toby.question]", "[toby.bye]")
                client.chooseOption(2)
                client.chat("[toby.question_said]")
                client.menu("[toby.what_is]", "[toby.rewards]", "[toby.claim]", "[toby.bye]")
                client.chooseOption(2)
                client.chat("[toby.rewards_1]")
                client.menu("[toby.easy]", "[toby.medium]", "[toby.hard]", "[toby.elite]")
                client.chooseOption(3)
                client.chat("[toby.hard_ask]")
                client.chat("[toby.hard_1]")
                client.chat("[toby.thanks]")

                client.talk(VarrockSweepTalkers.TOBY)
                client.chat("[toby.hello]")
                client.menu("[toby.who]", "[toby.question]", "[toby.bye]")
                client.chooseOption(3)
                client.chat("[toby.bye]")
                client.chat("[toby.bye_1]")
            }
        }
    }

    @Test
    fun `James's answers come back to his menu, and Meredith's games menu`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.talk(VarrockSweepTalkers.JAMES)
                client.chat("[james_bonds.who]")
                client.chat("[james_bonds.who_1]")
                client.menu("[james_bonds.what]", "[james_bonds.buy]", "[james_bonds.redeem]", "[james_bonds.more]")
                client.chooseOption(4)
                client.menu("[james_bonds.how_much]", "[james_bonds.else]", "[james_bonds.give]")
                client.chooseOption(1)
                client.chat("[james_bonds.how_much_1]")
                client.menu("[james_bonds.what]", "[james_bonds.buy]", "[james_bonds.redeem]", "[james_bonds.more]")
                client.closeModal()

                client.talk(VarrockSweepTalkers.MEREDITH)
                client.chat("[meredith.greeting]")
                client.menu("[meredith.how]", "[meredith.which]", "[meredith.drink]", "[meredith.thanks]")
                client.chooseOption(2)
                client.chat("[meredith.which]")
                client.chat("[meredith.which_1]")
                client.menu("[meredith.draughts]", "[meredith.runelink]", "[meredith.runesquares]", "[meredith.runeversi]")
                client.chooseOption(3)
                client.chat("[meredith.runesquares]")
                client.chat("[meredith.runesquares_1]")
                client.chat("[meredith.runesquares_2]")
                client.menu("[meredith.draughts]", "[meredith.runelink]", "[meredith.runesquares]", "[meredith.runeversi]")
            }
        }
    }
}
