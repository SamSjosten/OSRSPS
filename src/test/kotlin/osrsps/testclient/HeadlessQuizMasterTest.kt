package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.randomevents.MysteryBoxLines
import osrsps.game.content.randomevents.QuizMaster
import osrsps.game.content.randomevents.RandomEvents
import osrsps.map.CollisionMap

/**
 * Random events step 17 through the headless client: the Quiz Master takes his player to the black chair in his
 * studio, welcomes them, and asks odd-one-out questions until four in a row are right; a wrong one starts the count
 * again. The prize is the player's pick of 1000 coins or a mystery box, and the box opens to one item of its table. A
 * quiz dropped with Esc is asked again. The rl-client script test-runner/examples/random-events-quiz-master.json does
 * part of it in the real game.
 */
class HeadlessQuizMasterTest {
    private val npcs = listOf(
        NpcType(QuizMaster.npcId, "Quiz Master", ops = listOf("Talk-to", null, null, null, "Dismiss")),
        NpcType(QuizMaster.HOST, "Quiz Master"),
    )
    private val objs = (QuizMaster.BOX_TABLE.map { it.first } + listOf(QuizMaster.BAGUETTE, QuizMaster.COINS)).distinct()
        .map { ObjType(it, "Item $it", stackable = it == QuizMaster.COINS || it == 561) } +
        ObjType(QuizMaster.MYSTERY_BOX, "Mystery box", iops = listOf("Open", null, null, null, "Drop"))

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(30, 74) },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()), objTypes = ObjTypes.of(*objs.toTypedArray()),
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

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    /** The question shown after [seen] quiz_init runs: waits for it and returns the odd one's place (0 to 2). */
    private fun HeadlessClient.question(seen: Int): Int {
        val script = await("question ${seen + 1}") { clientScripts().filter { it.id == QuizMaster.INIT_SCRIPT }.getOrNull(seen) }
        await("the window") { if (QuizMaster.INTERFACE in openInterfaces()) Unit else null }
        val models = listOf(script.args[1], script.args[3], script.args[5]).map { (it as Number).toInt() }
        val kinds = models.map { m -> QuizMaster.KINDS.indexOfFirst { kind -> kind.any { it.model == m } } }
        return kinds.indices.single { i -> kinds.count { it == kinds[i] } == 1 }
    }

    private fun HeadlessClient.pick(sub: Int) = ifButton(QuizMaster.INTERFACE, QuizMaster.BUTTONS, 1, sub)

    /** Talk-to and yes: to the chair, and the welcome. */
    private fun HeadlessClient.intoStudio(name: String) {
        command("randomevent ${QuizMaster.key}")
        val npc = await("the Quiz Master") { npcs().firstOrNull { it.id == QuizMaster.npcId } }
        npcOp(npc.index, RandomEvents.TALK_TO)
        chat("[quiz_master.invite name=$name]")
        awaitDialogue("the options") { it is DialogueView.Menu }
        chooseOption(1)
        chat("[quiz_master.yes]")
        await("the studio") { position?.takeIf { it != home && it.level == QuizMaster.LEVEL } }
        chat("[quiz_master.welcome]")
        chat("[quiz_master.odd_one_out]")
        chat("[quiz_master.contestant name=$name]")
    }

    @Test
    fun `four right in a row win, a wrong one starts again, and the box opens`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.intoStudio("alice")
                // Seated facing the host, though they turned to the invitation beside them (Codex on #995).
                assertEquals(QuizMaster.HOST, server.world.onlinePlayers.single().facingNpc?.type?.id, "facing the Quiz Master")
                var seen = 0
                // One right, then a wrong one: the count starts again.
                client.pick(client.question(seen++))
                assertTrue(client.chatStarting("[quiz_master.right_").matches(Regex("\\[quiz_master\\.right_[1-6]]")))
                val odd = client.question(seen++)
                client.pick((odd + 1) % 3)
                assertTrue(client.chatStarting("[quiz_master.wrong_").matches(Regex("\\[quiz_master\\.wrong_[1-9]]")))

                // Esc on a question: it is asked again.
                client.question(seen++)
                // The re-asked question is the next quiz_init the loop below waits for (the window can be shut for a single tick).
                client.closeModal()

                repeat(QuizMaster.TO_WIN) { n ->
                    client.pick(client.question(seen++))
                    if (n < QuizMaster.TO_WIN - 1) client.chatStarting("[quiz_master.right_")
                }
                client.chat("[quiz_master.winner]")
                client.awaitDialogue("the prizes") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.await("home") { client.position?.takeIf { it == home } }
                client.await("the box") { if (client.count(QuizMaster.MYSTERY_BOX) == 1) Unit else null }
                client.await("the show ended") { if (server.world.instances.all.isEmpty()) Unit else null }

                val slot = client.inventory(Inventory.INV).indexOfFirst { it?.id == QuizMaster.MYSTERY_BOX }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, OPEN_BUTTON_OP, slot, QuizMaster.MYSTERY_BOX)
                val message = client.awaitDialogue("the box's message") { it is DialogueView.Message } as DialogueView.Message
                val key = message.text.removePrefix("[mystery_box.").removeSuffix("]")
                // The roll can also come up empty (the table's last three of fifteen): its own message and no item.
                val line = (QuizMaster.BOX_TABLE.map { it.third } + MysteryBoxLines.BAGUETTE + MysteryBoxLines.NOTHING).single { it.key == "mystery_box.$key" }
                val item = QuizMaster.BOX_TABLE.firstOrNull { it.third == line }?.let { it.first to it.second }
                    ?: if (line == MysteryBoxLines.BAGUETTE) QuizMaster.BAGUETTE to 1 else null
                if (item != null) client.await("the box's item") { if (client.count(item.first) == item.second) Unit else null }
                client.await("the box used") { if (client.count(QuizMaster.MYSTERY_BOX) == 0) Unit else null }
                if (item == null) assertTrue(client.inventory(Inventory.INV).all { it == null }, "nothing from an empty roll")
            }
        }
    }

    @Test
    fun `no sends him off, and the coins prize`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("randomevent ${QuizMaster.key}")
                val npc = client.await("the Quiz Master") { client.npcs().firstOrNull { it.id == QuizMaster.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[quiz_master.invite name=bob]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.over()
                client.await("him gone") { if (client.npcs().none { it.id == QuizMaster.npcId }) Unit else null }

                client.awaitTicks(2)
                client.intoStudio("bob")
                repeat(QuizMaster.TO_WIN) { n ->
                    client.pick(client.question(n))
                    if (n < QuizMaster.TO_WIN - 1) client.chatStarting("[quiz_master.right_")
                }
                client.chat("[quiz_master.winner]")
                client.awaitDialogue("the prizes") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.await("home") { client.position?.takeIf { it == home } }
                client.await("the coins") { if (client.count(QuizMaster.COINS) == QuizMaster.COINS_PRIZE) Unit else null }
                client.await("the show ended") { if (server.world.instances.all.isEmpty()) Unit else null }
            }
        }
    }

    private companion object {
        const val OPEN_BUTTON_OP = 2
    }
}
