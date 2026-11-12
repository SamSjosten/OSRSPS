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
    private val anna = talker(VarrockSweepPeople.ANNA_JONES, "Anna Jones")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            objTypes = ObjTypes.of(ObjType(VarrockSweepPeople.HAMMER, "Hammer"), ObjType(VarrockSweepPeople.BRONZE_PICKAXE, "Bronze pickaxe")),
            npcTypes = NpcTypes.of(guidor, jack, jeff, tutor, anna),
            npcSpawns = listOf(
                NpcSpawn(guidor, 3224, 3218, wander = 0, source = "test"),
                NpcSpawn(jack, 3220, 3218, wander = 0, source = "test"),
                NpcSpawn(jeff, 3222, 3221, wander = 0, source = "test"),
                NpcSpawn(tutor, 3222, 3215, wander = 0, source = "test"),
                NpcSpawn(anna, 3226, 3216, wander = 0, source = "test"),
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

    /** Each box of [keys] in turn, said by [speakers] ("P" the player, anything else the NPC of that name). */
    private fun HeadlessClient.boxes(prefix: String, player: String, keys: List<String>, speakers: String, npc: String) {
        assertEquals(keys.size, speakers.length)
        for ((key, who) in keys.zip(speakers.toList())) {
            // A line with values shows them after its key: "[anna_jones.greeting name=dana]".
            val text = "[$prefix.$key"
            val box = awaitDialogue(text) { it is DialogueView.Chat && (it.text == "$text]" || it.text.startsWith("$text ")) } as DialogueView.Chat
            assertEquals(if (who == 'P') player else npc, box.name, "who says $key")
            continueDialogue()
        }
    }

    @Test
    fun `Hooknosed Jack's Jimmy Dazzler and Anna Jones's tunnel and doing branches go in the Wiki's speaker order`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dana").use { client ->
                client.await("the login rebuild") { client.position }
                client.talk(VarrockSweepPeople.HOOKNOSED_JACK)
                client.awaitDialogue("his menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.awaitDialogue("the where menu") { it is DialogueView.Menu && (it as DialogueView.Menu).options.size == 5 }
                client.chooseOption(2)
                client.boxes("hooknosed_jack", "dana", listOf("jimmy") + (1..8).map { "jimmy_$it" }, "PJPJPJPJJ", "Hooknosed Jack")

                client.talk(VarrockSweepPeople.ANNA_JONES)
                client.boxes("anna_jones", "dana", listOf("greeting", "thanks", "surok", "ok", "awaits"), "APAPA", "Anna Jones")
                client.awaitDialogue("her menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.boxes("anna_jones", "dana", listOf("tunnel") + (1..7).map { "tunnel_$it" }, "PAPAPAPA", "Anna Jones")
                client.awaitDialogue("her menu again") { it is DialogueView.Menu }
                client.chooseOption(4)
                client.boxes("anna_jones", "dana", listOf("doing") + (1..6).map { "doing_$it" }, "PAPAPAP", "Anna Jones")
                client.awaitDialogue("her menu again") { it is DialogueView.Menu }
            }
        }
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

    @Test
    fun `a banked hammer counts, so the tutor gives no second one`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fred").use { client ->
                client.await("the login rebuild") { client.position }
                val fred = server.world.playerNamed("fred")!!
                fred.bank.add(server.world.objTypes[VarrockSweepPeople.HAMMER]!!, 1)
                client.talk(VarrockSweepPeople.MASTER_SMITHING_TUTOR)
                client.chat("[master_smithing_tutor.hello]")
                client.chat("[master_smithing_tutor.greeting]")
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("[master_smithing_tutor.train]")
                client.chat("[master_smithing_tutor.no_hammer]")
                for (n in 1..6) client.chat("[master_smithing_tutor.low_$n]")
                client.awaitTicks(2)
                assertEquals(0L, fred.inventory.count(VarrockSweepPeople.HAMMER))
            }
        }
    }

    @Test
    fun `with a full backpack the tutor and Anna Jones give nothing, and nothing lands on the floor`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                val erin = server.world.playerNamed("erin")!!
                // A full pack with no hammer or pickaxe in it.
                val rock = osrsps.game.ObjType(FILLER, "Rock")
                repeat(erin.inventory.freeSlots) { erin.inventory.add(rock, 1) }
                client.talk(VarrockSweepPeople.MASTER_SMITHING_TUTOR)
                client.chat("[master_smithing_tutor.hello]")
                client.chat("[master_smithing_tutor.greeting]")
                // Asked twice in one talk: each refused, never one dropped.
                repeat(2) {
                    client.awaitDialogue("the menu") { it is DialogueView.Menu }
                    client.chooseOption(1)
                    client.chat("[master_smithing_tutor.train]")
                    client.chat("[master_smithing_tutor.no_hammer]")
                    for (n in 1..6) client.chat("[master_smithing_tutor.low_$n]")
                    client.chat("[master_smithing_tutor.low_thanks]")
                    client.chat("[master_smithing_tutor.anything_else]")
                }
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(3)
                client.chat("[master_smithing_tutor.not_now]")
                client.chat("[master_smithing_tutor.come_back]")

                client.talk(VarrockSweepPeople.ANNA_JONES)
                client.awaitDialogue("her greeting") { it is DialogueView.Chat && it.text.startsWith("[anna_jones.greeting") }
                client.continueDialogue()
                client.chat("[anna_jones.thanks]")
                client.closeModal()
                client.await("the refusals") { if (client.messages().count { it.text == "[game.no_space]" } >= 3) Unit else null }
                client.awaitTicks(2)
                assertEquals(0, erin.inventory.count(VarrockSweepPeople.HAMMER) + erin.inventory.count(VarrockSweepPeople.BRONZE_PICKAXE))
                val floor = server.world.groundObjs.visibleAt(erin, erin.x, erin.z, 0).map { it.id }
                assertEquals(emptyList(), floor.filter { it == VarrockSweepPeople.HAMMER || it == VarrockSweepPeople.BRONZE_PICKAXE }, "nothing on the floor")
            }
        }
    }

    private companion object {
        const val FILLER = 436
    }
}
