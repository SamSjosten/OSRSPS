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
import osrsps.game.content.randomevents.KissTheFrog
import osrsps.game.content.randomevents.RandomEvents
import osrsps.map.CollisionMap

/**
 * Random events step 13 through the headless client: the frog in the pointy hat takes its player to the swamp, the
 * other frogs send them to the crowned one, and a touch or a kiss turns it into a royal who gives a frog token. A polite
 * no sends the player home with nothing; asking to be a frog sends them to Frogland, left by talking to its frog. The
 * rl-client script test-runner/examples/random-events-kiss-the-frog.json does part of it in the real game.
 */
class HeadlessKissTheFrogTest {
    private val frogOps = listOf("Talk-to", null, null, null, "Dismiss")
    private val npcs = listOf(
        NpcType(KissTheFrog.npcId, "Frog", ops = frogOps),
        NpcType(KissTheFrog.TOP_HAT, "Frog", ops = frogOps),
        NpcType(KissTheFrog.HATLESS, "Frog", ops = frogOps),
        NpcType(KissTheFrog.CROWNED, "Frog", ops = listOf("Talk-to", null, null, null, null)),
        NpcType(KissTheFrog.FROGLAND_FROG, "Frog", ops = listOf("Talk-to", null, null, null, null)),
        NpcType(KissTheFrog.PRINCE, "Frog Prince"),
        NpcType(KissTheFrog.PRINCESS, "Frog Princess"),
    )

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(38, 74) },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()),
            objTypes = ObjTypes.of(ObjType(KissTheFrog.TOKEN, "Frog token", stackable = true)),
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

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    /** Talk-to on the pointy-hat frog: into the swamp. */
    private fun HeadlessClient.intoSwamp() {
        command("randomevent ${KissTheFrog.key}")
        val frog = await("the frog") { npcs().firstOrNull { it.id == KissTheFrog.npcId } }
        npcOp(frog.index, RandomEvents.TALK_TO)
        await("the swamp") { position?.takeIf { it != home } }
    }

    /** Talk-to on the crowned frog, up to its options. */
    private fun HeadlessClient.askCrowned(): List<String> {
        val crowned = await("the crowned frog") { npcs().firstOrNull { it.id == KissTheFrog.CROWNED } }
        npcOp(crowned.index, RandomEvents.TALK_TO)
        chat("[kiss_the_frog.please_help]")
        val menu = awaitDialogue("the options") { it is DialogueView.Menu } as DialogueView.Menu
        return menu.options
    }

    private fun HeadlessClient.pick(options: List<String>, prefix: String): String {
        val i = options.indexOfFirst { it.startsWith("[kiss_the_frog.$prefix") }
        assertTrue(i >= 0, "no $prefix in $options")
        chooseOption(i + 1)
        chat(options[i])
        return options[i]
    }

    @Test
    fun `the other frogs point to the crowned one, a touch brings a token, and another player gets the frog's other lines`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { carol ->
                carol.ready()
                server.login("alice").use { client ->
                    client.ready()
                    client.command("randomevent ${KissTheFrog.key}")
                    val herald = carol.await("alice's frog") { carol.npcs().firstOrNull { it.id == KissTheFrog.npcId } }
                    carol.npcOp(herald.index, RandomEvents.TALK_TO)
                    carol.awaitDialogue("not yours") {
                        it is DialogueView.Chat && it.text in listOf("[kiss_the_frog.not_needed]", "[kiss_the_frog.just_a_frog]")
                    }
                    carol.continueDialogue()

                    val frog = client.await("the frog") { client.npcs().firstOrNull { it.id == KissTheFrog.npcId } }
                    client.npcOp(frog.index, RandomEvents.TALK_TO)
                    client.await("the swamp") { client.position?.takeIf { it != home } }
                    client.await("eight frogs") { client.npcs().filter { it.id == KissTheFrog.CROWNED || it.id in KissTheFrog.PLAIN }.takeIf { it.size == 8 } }
                    val (_, swamp) = KissTheFrog.swampOf(server.world, server.world.onlinePlayers.single { it.username == "alice" })!!
                    val royal = if (swamp.princess) "princess" else "prince"

                    val other = client.npcs().first { it.id == KissTheFrog.TOP_HAT }
                    client.npcOp(other.index, RandomEvents.TALK_TO)
                    client.awaitDialogue("another frog") {
                        it is DialogueView.Chat && it.text in listOf("[kiss_the_frog.hurrah_$royal]", "[kiss_the_frog.speak_to_$royal]")
                    }
                    client.continueDialogue()
                    client.over()

                    client.pick(client.askCrowned(), "positive_")
                    // The royal is drawn in the swamp before the player goes home.
                    val royalId = if (swamp.princess) KissTheFrog.PRINCESS else KissTheFrog.PRINCE
                    client.await("the royal in the swamp") { client.npcs().firstOrNull { it.id == royalId } }
                    client.awaitDialogue("the royal's thanks") {
                        it is DialogueView.Chat && it.text == "[kiss_the_frog.thank_you name=alice]" && it.name == "Frog ${royal.replaceFirstChar(Char::uppercase)}"
                    }
                    client.continueDialogue()
                    client.await("home") { client.position?.takeIf { it == home } }
                    client.over()
                    assertEquals(1, client.inventory(Inventory.INV).count { it?.id == KissTheFrog.TOKEN })
                    client.await("the swamp ended") { if (server.world.instances.all.isEmpty()) Unit else null }
                }
            }
        }
    }

    @Test
    fun `a kiss works too, a polite no sends home with nothing, and asking to be a frog goes to Frogland`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.intoSwamp()
                client.pick(client.askCrowned(), "kiss_")
                client.chat("[kiss_the_frog.yes]")
                // Home, with the token, before the thanks: shutting it loses nothing.
                client.awaitDialogue("the thanks") { it is DialogueView.Chat && it.text == "[kiss_the_frog.thank_you name=bob]" }
                client.await("home") { client.position?.takeIf { it == home } }
                client.closeModal()
                client.over()
                assertEquals(1, client.inventory(Inventory.INV).count { it?.id == KissTheFrog.TOKEN })
                client.await("the swamp ended") { if (server.world.instances.all.isEmpty()) Unit else null }

                // The refusal is one of four at random: polite ones are taken (home, nothing) until a frog one comes.
                var frogland = false
                repeat(20) {
                    if (frogland) return@repeat
                    client.awaitTicks(2)
                    client.intoSwamp()
                    val options = client.askCrowned()
                    if (options.any { it.startsWith("[kiss_the_frog.frog_too_") }) {
                        client.pick(options, "frog_too_")
                        frogland = true
                    } else {
                        client.pick(options, "polite_")
                        client.await("home") { client.position?.takeIf { it == home } }
                        client.over()
                        client.await("the swamp ended") { if (server.world.instances.all.isEmpty()) Unit else null }
                    }
                }
                assertTrue(frogland, "no frog refusal in 20 tries")
                client.over()
                client.await("the swamp's frogs gone") { if (client.npcs().none { it.id == KissTheFrog.CROWNED || it.id in KissTheFrog.PLAIN }) Unit else null }

                val froglandFrog = client.await("the Frogland frog") { client.npcs().firstOrNull { it.id == KissTheFrog.FROGLAND_FROG } }
                client.npcOp(froglandFrog.index, RandomEvents.TALK_TO)
                client.chat("[kiss_the_frog.ready_to_leave]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.chat("[kiss_the_frog.rather_stay]")
                client.over()
                assertTrue(client.position != home)

                client.npcOp(froglandFrog.index, RandomEvents.TALK_TO)
                client.chat("[kiss_the_frog.ready_to_leave]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("[kiss_the_frog.leave_now]")
                client.await("home") { client.position?.takeIf { it == home } }
                assertEquals(1, client.inventory(Inventory.INV).count { it?.id == KissTheFrog.TOKEN }, "one token, from the kiss only")
                client.await("the swamp ended") { if (server.world.instances.all.isEmpty()) Unit else null }
            }
        }
    }
}
