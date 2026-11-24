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
import osrsps.game.content.randomevents.Certer
import osrsps.game.content.randomevents.Certers
import osrsps.game.content.randomevents.Gifts
import osrsps.game.content.randomevents.RandomEvents
import osrsps.game.content.randomevents.SandwichLady
import osrsps.map.CollisionMap

/**
 * Random events step 3 through the headless client: a certer brother's window (three answers, the right one gets a
 * gift, a wrong one nothing) and the Sandwich lady's tray (the food she names is given, Key and Back swap the layers,
 * another food gets nothing). The rl-client scripts test-runner/examples/random-events-certer.json and
 * random-events-sandwich-lady.json do the window and the tray in the real game.
 */
class HeadlessRandomEventsTrayTest {
    private val events = listOf("Talk-to", null, null, null, "Dismiss")
    private val npcs = (Certer.npcIds + SandwichLady.npcId).map { NpcType(it, "Event $it", ops = events) }

    private val objs = (Gifts.TABLE.map { it.id } + SandwichLady.TRAY.map { it.second } + SandwichLady.STALE_BAGUETTE).distinct().map { id ->
        when (id) {
            Gifts.COINS -> ObjType(id, "Coins", stackable = true)
            6961 -> ObjType(id, "Baguette")
            6962 -> ObjType(id, "Triangle sandwich")
            6965 -> ObjType(id, "Square sandwich")
            6963 -> ObjType(id, "Roll")
            2327 -> ObjType(id, "Meat pie")
            1971 -> ObjType(id, "Kebab")
            1973 -> ObjType(id, "Chocolate bar")
            else -> ObjType(id, "Item $id")
        }
    }

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()), objTypes = ObjTypes.of(*objs.toTypedArray()), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready() {
        await("the login rebuild") { position }
        command("tele 3222 3218 0")
        await("the teleport") { position?.takeIf { it == Coord(3222, 3218, 0) } }
        commandReply("randomevent off") { it == "Random events off" }
    }

    private fun HeadlessClient.start(key: String, ids: List<Int>): NpcView {
        command("randomevent $key")
        return await("$key in view") { npcs().firstOrNull { it.id in ids } }
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.gone(ids: List<Int>) = await("the event NPC gone") { if (npcs().none { it.id in ids }) Unit else null }

    private fun HeadlessClient.items() = inventory(Inventory.INV).count { it != null }

    @Test
    fun `a certer brother shows an item with three answers, and only the right one gets a gift`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                val answers = setOf("fish", "sword", "battleaxe", "helm", "shield", "shears", "spade", "ring", "necklace").map { "[certer.answer_$it]" }
                var right = 0
                var wrong = 0
                var tries = 0
                while ((right == 0 || wrong == 0) && tries++ < 30) {
                    val npc = client.start(Certer.key, Certer.npcIds)
                    client.await("his greeting") { client.npcSaid().firstOrNull { it.index == npc.index && it.text == "[certer.greeting name=alice]" } }
                    client.npcOp(npc.index, RandomEvents.TALK_TO)
                    client.await("the window") { if (Certers.INTERFACE in client.openInterfaces()) Unit else null }
                    // The window's texts arrive one by one and the last show's can still be there for a moment: wait
                    // for a whole set of three, then check it.
                    val shown = runCatching {
                        client.await("the three answers") {
                            Certers.TEXTS.map { t -> client.text(Certers.INTERFACE, t) }.takeIf { s -> s.all { it in answers } && s.toSet().size == 3 }
                        }
                    }.getOrElse { Certers.TEXTS.map { t -> client.text(Certers.INTERFACE, t) } }
                    assertTrue(shown.all { it in answers } && shown.toSet().size == 3, "three different answers: $shown")
                    val before = client.items()
                    client.ifButton(Certers.INTERFACE, Certers.BUTTONS[0], 1)
                    val said = client.awaitDialogue("his answer") { it is DialogueView.Chat }
                    assertTrue(Certers.INTERFACE !in client.openInterfaces())
                    client.continueDialogue()
                    client.over()
                    client.gone(Certer.npcIds)
                    when ((said as DialogueView.Chat).text) {
                        "[certer.right]" -> {
                            client.await("a gift") { if (client.items() == before + 1) Unit else null }
                            right++
                        }
                        "[certer.wrong]" -> {
                            client.awaitTicks(1)
                            assertEquals(before, client.items())
                            wrong++
                        }
                        else -> error("unexpected ${said.text}")
                    }
                    client.command("empty")
                    client.await("an empty backpack") { if (client.items() == 0) Unit else null }
                }
                assertTrue(right > 0 && wrong > 0, "right $right, wrong $wrong in $tries")
            }
        }
    }

    @Test
    fun `the Sandwich lady gives the food she names from her tray, and nothing for another`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                var lady = client.start(SandwichLady.key, listOf(SandwichLady.npcId))
                client.npcOp(lady.index, RandomEvents.TALK_TO)
                val offer = client.awaitDialogue("her offer") { it is DialogueView.Chat && it.text.startsWith("[sandwich_lady.offer food=") } as DialogueView.Chat
                val food = offer.text.removePrefix("[sandwich_lady.offer food=").removeSuffix("]")
                val (button, id) = SandwichLady.TRAY.first { objs.first { o -> o.id == it.second }.name.lowercase() == food }
                client.continueDialogue()
                client.await("the tray") { if (SandwichLady.INTERFACE in client.openInterfaces()) Unit else null }
                client.ifButton(SandwichLady.INTERFACE, SandwichLady.KEY_BUTTON, 1)
                // The two layers' hides come as two packets: wait for both, as the client may have read only the first.
                client.await("the key, the tray hidden") {
                    if (client.hidden(SandwichLady.INTERFACE, SandwichLady.KEY_LAYER) == false &&
                        client.hidden(SandwichLady.INTERFACE, SandwichLady.TRAY_LAYER) == true) Unit else null
                }
                client.ifButton(SandwichLady.INTERFACE, SandwichLady.BACK_BUTTON, 1)
                client.await("the tray again") { if (client.hidden(SandwichLady.INTERFACE, SandwichLady.TRAY_LAYER) == false) Unit else null }
                client.ifButton(SandwichLady.INTERFACE, button, 1)
                client.awaitDialogue("her thanks") { it is DialogueView.Chat && it.text == "[sandwich_lady.fills_you_up]" }
                client.continueDialogue()
                client.over()
                client.await("the food") { client.inventory(Inventory.INV).firstOrNull { it?.id == id || id == SandwichLady.BAGUETTE && it?.id == SandwichLady.STALE_BAGUETTE } }

                client.command("empty")
                client.await("an empty backpack") { if (client.items() == 0) Unit else null }
                lady = client.start(SandwichLady.key, listOf(SandwichLady.npcId))
                client.npcOp(lady.index, RandomEvents.TALK_TO)
                val second = client.awaitDialogue("her offer") { it is DialogueView.Chat && it.text.startsWith("[sandwich_lady.offer food=") } as DialogueView.Chat
                val named = second.text.removePrefix("[sandwich_lady.offer food=").removeSuffix("]")
                val other = SandwichLady.TRAY.first { objs.first { o -> o.id == it.second }.name.lowercase() != named }.first
                client.continueDialogue()
                client.await("the tray") { if (SandwichLady.INTERFACE in client.openInterfaces()) Unit else null }
                client.ifButton(SandwichLady.INTERFACE, other, 1)
                client.awaitDialogue("her complaint") { it is DialogueView.Chat && it.text == "[sandwich_lady.didnt_say]" }
                // Shut there, she has nothing more: another Talk-to gets no second pick, and she goes soon after.
                client.closeModal()
                client.over()
                client.npcOp(lady.index, RandomEvents.TALK_TO)
                client.awaitTicks(2)
                assertEquals(null, client.dialogueView)
                client.gone(listOf(SandwichLady.npcId))
                assertEquals(0, client.items())
            }
        }
    }
}
