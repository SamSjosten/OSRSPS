package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.randomevents.RandomEvents
import osrsps.game.content.randomevents.SurpriseExam
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocShape

/**
 * Random events step 18 through the headless client: the dunce takes his player to the classroom, Mr. Mordaut explains
 * the exam and asks what comes next; three right pass, and a door then sends the player home with a Book of Knowledge
 * (another reward when one is owned). A wrong answer gets the same question again; three wrong fail, and the door sends
 * the player home with nothing. The window shut, Talk-to on him asks again. The rl-client script
 * test-runner/examples/random-events-surprise-exam.json does part of it in the real game.
 */
class HeadlessSurpriseExamTest {
    private val npcs = listOf(
        NpcType(SurpriseExam.npcId, "Dunce", ops = listOf("Talk-to", null, null, null, "Dismiss")),
        NpcType(SurpriseExam.DUNCE, "Dunce"),
        NpcType(SurpriseExam.TEACHER, "Mr. Mordaut", size = 5, ops = listOf("Talk-to", null, null, null, null)),
    )
    private val objs = (SurpriseExam.OTHER_REWARDS.map { it.first } + SurpriseExam.BOOK).distinct()
        .map { ObjType(it, "Item $it", stackable = it == 995) }
    private val doors = listOf(
        ZoneLoc(9316, LocShape.WALL_STRAIGHT, 2, 1881, 5022, 0), ZoneLoc(9317, LocShape.WALL_STRAIGHT, 2, 1881, 5030, 0),
        ZoneLoc(9318, LocShape.WALL_STRAIGHT, 3, 1886, 5033, 0), ZoneLoc(9319, LocShape.WALL_STRAIGHT, 0, 1891, 5019, 0),
    )

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(29, 78) },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()), objTypes = ObjTypes.of(*objs.toTypedArray()),
            locTypes = LocTypes(SurpriseExam.DOORS.associateWith { LocType(it, "Door", ops = listOf("Open", null, null, null, null)) }),
            staticLocs = StaticLocs().apply { for (d in doors) add(d) },
            cycleMillis = millis,
        )
    }

    private val home = Coord(3222, 3218, 0)

    private fun HeadlessClient.ready() {
        await("the login rebuild") { position }
        command("tele 3222 3218 0")
        await("the teleport") { position?.takeIf { it == home } }
        commandReply("randomevent off") { it == "Random events off" }
    }

    private fun HeadlessClient.chat(text: String) {
        awaitDialogue(text) { it is DialogueView.Chat && it.text == text }
        continueDialogue()
    }

    private fun HeadlessClient.chatStarting(prefix: String): String {
        val view = awaitDialogue(prefix) { it is DialogueView.Chat && it.text.startsWith(prefix) } as DialogueView.Chat
        continueDialogue()
        return view.text
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filter { it?.id == id }.sumOf { it!!.count }

    /** The question shown after [seen] questions: its seven models drawn; returns the right option (0 to 3). */
    private fun HeadlessClient.question(seen: Int): Int {
        val models = await("question ${seen + 1}") {
            clientScripts().filter { it.id == SurpriseExam.SETMODEL_SCRIPT }.map { (it.args[1] as Number).toInt() }.takeIf { it.size >= (seen + 1) * 7 }
        }.drop(seen * 7).take(7)
        await("the window") { if (SurpriseExam.INTERFACE in openInterfaces()) Unit else null }
        val theme = SurpriseExam.THEMES.single { models[0] in it }
        assertTrue(models.take(3).all { it in theme }, "three of a theme")
        return models.drop(3).indexOfFirst { it in theme }
    }

    private fun HeadlessClient.pick(option: Int) = ifButton(SurpriseExam.INTERFACE, SurpriseExam.OPTIONS[option], 1)

    private fun HeadlessClient.intoClass(name: String) {
        command("randomevent ${SurpriseExam.key}")
        val npc = await("the dunce") { npcs().firstOrNull { it.id == SurpriseExam.npcId } }
        npcOp(npc.index, RandomEvents.TALK_TO)
        chat("[surprise_exam.invite name=$name]")
        awaitDialogue("the options") { it is DialogueView.Menu }
        chooseOption(1)
        chat("[surprise_exam.sure]")
        await("the classroom") { position?.takeIf { it != home } }
        chat("[surprise_exam.slacking name=$name]")
        chat("[surprise_exam.two_types]")
        chat("[surprise_exam.fifteen_cards]")
    }

    private fun HeadlessClient.door(server: TestServer) {
        val (away, _) = SurpriseExam.classOf(server.world, server.world.onlinePlayers.single())!!
        val at = away.inside(1881, 5022)
        locOp(9316, at.x, at.z, SurpriseExam.OPEN)
        await("home") { position?.takeIf { it == home } }
        await("the class ended") { if (server.world.instances.all.isEmpty()) Unit else null }
    }

    @Test
    fun `three right pass, a wrong one repeats the question, and the door gives the book`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.intoClass("alice")
                var seen = 0
                client.chat("[surprise_exam.pick_next]")
                val first = client.question(seen++)
                client.pick((first + 1) % 4)
                assertTrue(client.chatStarting("[surprise_exam.wrong_").matches(Regex("\\[surprise_exam\\.wrong_[1-3]]")))
                client.chat("[surprise_exam.remember]")
                client.chat("[surprise_exam.pick_next]")
                val again = client.question(seen++)
                val scripts = client.clientScripts().filter { it.id == SurpriseExam.SETMODEL_SCRIPT }.map { it.args[1] }
                assertEquals(scripts.subList(0, 7), scripts.subList(7, 14), "the same question again")

                // Shut the window: Talk-to on him asks it again.
                client.closeModal()
                client.await("the window shut") { if (SurpriseExam.INTERFACE !in client.openInterfaces()) Unit else null }
                client.awaitTicks(1)
                val teacher = client.npcs().first { it.id == SurpriseExam.TEACHER }
                client.npcOp(teacher.index, RandomEvents.TALK_TO)
                client.chat("[surprise_exam.pick_next]")
                assertEquals(again, client.question(seen++))

                client.pick(again)
                repeat(SurpriseExam.TO_PASS - 1) {
                    client.chatStarting("[surprise_exam.right_")
                    client.chat("[surprise_exam.pick_next]")
                    client.pick(client.question(seen++))
                }
                client.chat("[surprise_exam.well_done_book]")
                client.chat("[surprise_exam.to_exit]")
                client.over()
                client.door(server)
                client.await("the book") { if (client.count(SurpriseExam.BOOK) == 1) Unit else null }
            }
        }
    }

    @Test
    fun `no sends him off, and three wrong fail with nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("randomevent ${SurpriseExam.key}")
                val npc = client.await("the dunce") { client.npcs().firstOrNull { it.id == SurpriseExam.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[surprise_exam.invite name=bob]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.chat("[surprise_exam.dont_want]")
                client.over()
                client.await("him gone") { if (client.npcs().none { it.id == SurpriseExam.npcId }) Unit else null }

                client.awaitTicks(2)
                client.intoClass("bob")
                repeat(SurpriseExam.TO_FAIL) { n ->
                    client.chat("[surprise_exam.pick_next]")
                    client.pick((client.question(n) + 1) % 4)
                    if (n < SurpriseExam.TO_FAIL - 1) {
                        client.chatStarting("[surprise_exam.wrong_")
                        if (n == 0) client.chat("[surprise_exam.remember]")
                    }
                }
                client.chat("[surprise_exam.failed]")
                client.chat("[surprise_exam.to_exit]")
                client.over()
                client.door(server)
                client.awaitTicks(2)
                assertTrue(client.inventory(Inventory.INV).all { it == null })
            }
        }
    }
}
