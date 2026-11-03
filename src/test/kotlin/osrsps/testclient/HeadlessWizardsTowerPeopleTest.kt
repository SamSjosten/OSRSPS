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
import osrsps.game.content.GrayzagLines
import osrsps.game.content.MizgogLines
import osrsps.game.content.OnglewipLines
import osrsps.game.content.TraibornLines
import osrsps.game.content.WizardsTowerPeople
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Draynor piece step 15b through the headless client and the registered handlers: Wizard Traiborn's talk, Wizard
 * Mizgog's talk and Purchase Amulet trading the four beads for an amulet of accuracy (and turning a player away without
 * them), Wizard Grayzag's lines and Professor Onglewip's. The NPCs stand on open ground. The rl-client script
 * test-runner/examples/wizards-tower-people.json does the same on the real map.
 */
class HeadlessWizardsTowerPeopleTest {
    private val talk = listOf("Talk-to", null, null, null, null)
    private val traiborn = NpcType(WizardsTowerPeople.TRAIBORN, "Wizard Traiborn", ops = talk)
    private val mizgog = NpcType(WizardsTowerPeople.MIZGOG, "Wizard Mizgog", ops = listOf("Talk-to", null, "Purchase Amulet", null, null))
    private val grayzag = NpcType(WizardsTowerPeople.GRAYZAG, "Wizard Grayzag", ops = talk)
    private val onglewip = NpcType(WizardsTowerPeople.ONGLEWIP, "Professor Onglewip", ops = talk)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(WizardsTowerPeople.AMULET_OF_ACCURACY + 1)
        for (bead in WizardsTowerPeople.BEADS) objs[bead] = ObjType(bead, "Bead $bead")
        objs[WizardsTowerPeople.AMULET_OF_ACCURACY] = ObjType(WizardsTowerPeople.AMULET_OF_ACCURACY, "Amulet of accuracy")
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(traiborn, mizgog, grayzag, onglewip),
            npcSpawns = listOf(
                NpcSpawn(traiborn, 3220, 3216, source = "test"),
                NpcSpawn(mizgog, 3224, 3216, source = "test"),
                NpcSpawn(grayzag, 3220, 3220, source = "test"),
                NpcSpawn(onglewip, 3224, 3220, source = "test"),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.talkTo(server: TestServer, id: Int, op: Int = 1) {
        await("the login rebuild") { position }
        npcOp(server.world.npcs.all.first { it.type.id == id }.index, op)
    }

    private fun HeadlessClient.sees(vararg lines: Line) {
        for (line in lines) {
            awaitDialogue(line.key) { it is DialogueView.Chat && it.text == DialogueText.NONE[line] }
            continueDialogue()
        }
    }

    /** The amulet's item box: the headless client sees an objectbox as Other, and continues it by its own component. */
    private fun HeadlessClient.objbox() {
        awaitDialogue("the item box") { it is DialogueView.Other }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).count { it?.id == id }

    @Test
    fun `Traiborn's thingummywut and wizard branches`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                with(TraibornLines) {
                    client.talkTo(server, WizardsTowerPeople.TRAIBORN)
                    client.sees(HELLO)
                    client.awaitDialogue("the menu") { it is DialogueView.Menu }
                    client.chooseOption(1)
                    client.sees(WHATS_THAT, WHERE, PESKY)
                    client.awaitDialogue("the second menu") { it is DialogueView.Menu }
                    client.chooseOption(1)
                    client.sees(CALLED_ME_SAID, UP_CLOSE, PROOF)
                    client.awaitDialogue("the third menu") { it is DialogueView.Menu }
                    client.chooseOption(2)
                    client.sees(MAD, PITY)

                    client.talkTo(server, WizardsTowerPeople.TRAIBORN)
                    client.sees(HELLO)
                    client.awaitDialogue("the menu") { it is DialogueView.Menu }
                    client.chooseOption(2)
                    client.sees(TEACH, NO_TRUCK)
                    client.awaitDialogue("the second menu") { it is DialogueView.Menu }
                    client.chooseOption(1)
                    client.sees(ARENT_YOU, FROG)
                }
            }
        }
    }

    @Test
    fun `Mizgog turns away a player without the four beads, then trades them for an amulet of accuracy`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                with(MizgogLines) {
                    client.talkTo(server, WizardsTowerPeople.MIZGOG, WizardsTowerPeople.PURCHASE_AMULET)
                    client.sees(NO_BEADS)
                    client.talkTo(server, WizardsTowerPeople.MIZGOG)
                    client.awaitDialogue("the menu") { it is DialogueView.Menu }
                    client.chooseOption(3)
                    client.sees(AMULET, SPARE, COME_BACK, SEE_YOU)

                    for (bead in WizardsTowerPeople.BEADS) client.command("item $bead 2")
                    client.await("the beads") { if (WizardsTowerPeople.BEADS.all { client.count(it) == 2 }) Unit else null }
                    client.talkTo(server, WizardsTowerPeople.MIZGOG)
                    client.awaitDialogue("the menu") { it is DialogueView.Menu }
                    client.chooseOption(3)
                    client.sees(AMULET, SPARE)
                    client.awaitDialogue("have them") { it is DialogueView.Menu }
                    client.chooseOption(1)
                    client.sees(HERE_YOU_GO)
                    client.objbox()
                    client.sees(THANKS, WHAT_FOR, DONT_WANT)
                    assertEquals(1, client.count(WizardsTowerPeople.AMULET_OF_ACCURACY))
                    for (bead in WizardsTowerPeople.BEADS) assertEquals(1, client.count(bead))

                    // Purchase Amulet trades straight away.
                    client.talkTo(server, WizardsTowerPeople.MIZGOG, WizardsTowerPeople.PURCHASE_AMULET)
                    client.objbox()
                    client.await("the second amulet") { if (client.count(WizardsTowerPeople.AMULET_OF_ACCURACY) == 2) Unit else null }
                    for (bead in WizardsTowerPeople.BEADS) assertEquals(0, client.count(bead))
                }
            }
        }
    }

    @Test
    fun `Grayzag's and Onglewip's lines`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.talkTo(server, WizardsTowerPeople.GRAYZAG)
                with(GrayzagLines) { client.sees(CLEVER, WELL_YES, WATCH, MEDDLER) }
                client.talkTo(server, WizardsTowerPeople.ONGLEWIP)
                with(OnglewipLines) { client.sees(LIVE_HERE, STRONGHOLD, WHERE, NORTH_WEST, FOOD, MAKE_TIME, GNOMES) }
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
    }
}
