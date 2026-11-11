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
import osrsps.game.content.VarrockSweepPeople
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Varrock's people from the Misthalin sweep through the headless client: Guidor's three boxes, Hooknosed Jack's where-to
 * menu, Jeff's one line, and the Master smithing tutor handing a hammer to a player without one and giving the levels
 * 1-20 answer before the menu comes back. Types and options are the 241 cache's.
 */
class HeadlessVarrockSweepPeopleTest {
    private fun talker(id: Int, name: String) = NpcType(id, name, ops = listOf("Talk-to", null, null, null, null))

    private val guidor = talker(VarrockSweepPeople.GUIDOR, "Guidor")
    private val jack = talker(VarrockSweepPeople.HOOKNOSED_JACK, "Hooknosed Jack")
    private val jeff = talker(VarrockSweepPeople.JEFF, "Jeff")
    private val tutor = talker(VarrockSweepPeople.MASTER_SMITHING_TUTOR, "Master smithing tutor")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            objTypes = ObjTypes.of(ObjType(VarrockSweepPeople.HAMMER, "Hammer")),
            npcTypes = NpcTypes.of(guidor, jack, jeff, tutor),
            npcSpawns = listOf(
                NpcSpawn(guidor, 3224, 3218, wander = 0, source = "test"),
                NpcSpawn(jack, 3220, 3218, wander = 0, source = "test"),
                NpcSpawn(jeff, 3222, 3221, wander = 0, source = "test"),
                NpcSpawn(tutor, 3222, 3215, wander = 0, source = "test"),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.talk(id: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, VarrockSweepPeople.TALK_TO)
    }

    private fun HeadlessClient.chat(text: String) {
        awaitDialogue(text) { it is DialogueView.Chat && it.text == text }
        continueDialogue()
    }

    @Test
    fun `Guidor, Hooknosed Jack and Jeff talk`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.talk(VarrockSweepPeople.GUIDOR)
                for (key in listOf("how", "coping", "good")) client.chat("[guidor.$key]")

                client.talk(VarrockSweepPeople.HOOKNOSED_JACK)
                client.awaitDialogue("his menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                val where = client.awaitDialogue("the where menu") { it is DialogueView.Menu && (it as DialogueView.Menu).options.size == 5 } as DialogueView.Menu
                assertEquals("[hooknosed_jack.grimesquit]", where.options.first())
                client.chooseOption(4)
                client.chat("[hooknosed_jack.face]")
                client.chat("[hooknosed_jack.face_1]")

                client.talk(VarrockSweepPeople.JEFF)
                client.chat("[mahogany_client.jeff]")
            }
        }
    }

    @Test
    fun `the Master smithing tutor gives a hammer and answers by level`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.talk(VarrockSweepPeople.MASTER_SMITHING_TUTOR)
                client.chat("[master_smithing_tutor.hello]")
                client.chat("[master_smithing_tutor.greeting]")
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("[master_smithing_tutor.train]")
                client.chat("[master_smithing_tutor.no_hammer]")
                client.await("the hammer") { client.inventory(Inventory.INV).firstOrNull { it?.id == VarrockSweepPeople.HAMMER } }
                for (n in 1..6) client.chat("[master_smithing_tutor.low_$n]")
                client.chat("[master_smithing_tutor.low_thanks]")
                client.chat("[master_smithing_tutor.anything_else]")
                client.awaitDialogue("the menu again") { it is DialogueView.Menu }
                client.chooseOption(3)
                client.chat("[master_smithing_tutor.not_now]")
                client.chat("[master_smithing_tutor.come_back]")
            }
        }
    }
}
