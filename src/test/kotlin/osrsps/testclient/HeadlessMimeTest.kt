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
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.XpLamp
import osrsps.game.content.randomevents.Mime
import osrsps.game.content.randomevents.RandomEvents
import osrsps.map.CollisionMap

/**
 * Random events step 7 through the headless client: the Mysterious Old Man takes his player to the mime's stage; the
 * mime does an emote, the window opens, and the player copies it (cheer) or not (cry). Four right bring the player
 * back with mime outfit pieces, or with the whole outfit a mime emote, or with all four a lamp; too slow, the mime does
 * another. Declining gives nothing. The rl-client script test-runner/examples/random-events-mime.json does it
 * in the real game.
 */
class HeadlessMimeTest {
    private val npcs = listOf(
        NpcType(Mime.npcId, "Mysterious Old Man", ops = listOf("Talk-to", null, null, null, "Dismiss")),
        NpcType(Mime.MIME_NPC, "Mime"),
    )

    private val objs = Mime.OUTFIT.map { ObjType(it, "Mime $it") } + ObjType(XpLamp.LAMP, "Lamp") + ObjType(BEER, "Beer")

    /** emote_glasswall, _glassbox, _climbrope, _lean: bits 0 to 3 of emote_access. */
    private val varbits = mapOf(1367 to 0, 1368 to 1, 1369 to 2, 1370 to 3).mapValues { (_, bit) -> VarbitType(EMOTE_ACCESS, bit, bit) }

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(31, 74) },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()), objTypes = ObjTypes.of(*objs.toTypedArray()),
            varTypes = VarTypes(2_000, varbits), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready() {
        await("the login rebuild") { position }
        command("tele 3222 3218 0")
        await("the teleport") { position?.takeIf { it == Coord(3222, 3218, 0) } }
        commandReply("randomevent off") { it == "Random events off" }
    }

    private fun HeadlessClient.chat(text: String) {
        awaitDialogue(text) { it is DialogueView.Chat && it.text == text }
        continueDialogue()
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.slotOf(id: Int) = inventory(Inventory.INV).indexOfFirst { it?.id == id }

    /** Talk-to on the old man and yes: onto the stage. Returns the mime's index. */
    private fun HeadlessClient.onStage(name: String): Int {
        command("randomevent ${Mime.key}")
        val npc = await("the old man") { npcs().firstOrNull { it.id == Mime.npcId } }
        npcOp(npc.index, RandomEvents.TALK_TO)
        chat("[mime.invite name=$name]")
        awaitDialogue("the options") { it is DialogueView.Menu }
        chooseOption(1)
        chat("[mime.yes]")
        chat("[mime.copy]")
        return await("the mime") { npcs().firstOrNull { it.id == Mime.MIME_NPC } }.index
    }

    /** Waits for the mime's next emote and the window; returns the emote's button. */
    private fun HeadlessClient.watch(mime: Int, from: Int): Int {
        await("the window") { if (Mime.INTERFACE in openInterfaces()) Unit else null }
        val anim = actorEvents(from).last { it.npc && it.index == mime && it.kind == ActorEventKind.ANIMATION && it.id >= 0 }.id
        return Mime.FIRST_BUTTON + Mime.Emote.entries.indexOfFirst { it.anim == anim }
    }

    private fun HeadlessClient.ownAnim(from: Int, anim: Int) =
        await("animation $anim") { actorEvents(from).firstOrNull { !it.npc && it.index == index && it.kind == ActorEventKind.ANIMATION && it.id == anim } }

    /** Four rounds, the first one wrong. */
    private fun HeadlessClient.perform(mime: Int, wrongFirst: Boolean) {
        var right = 0
        var first = true
        var mark = 0
        while (right < Mime.ROUNDS) {
            val button = watch(mime, mark)
            val pick = if (first && wrongFirst) (if (button == Mime.FIRST_BUTTON) button + 1 else Mime.FIRST_BUTTON) else button
            val from = actorEvents().size
            ifButton(Mime.INTERFACE, pick, 1)
            await("the window closed") { if (Mime.INTERFACE !in openInterfaces()) Unit else null }
            if (pick == button) {
                ownAnim(from, Mime.CHEER)
                right++
            } else {
                ownAnim(from, Mime.CRY)
            }
            first = false
            mark = actorEvents().size
        }
    }

    @Test
    fun `the mime's show, a wrong copy and four right, and back with outfit pieces, then an emote, then a lamp`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                val mime = client.onStage("alice")
                client.perform(mime, wrongFirst = true)
                client.await("back where they came from") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.await("a piece or two") { if (Mime.OUTFIT.count { client.slotOf(it) >= 0 } in 1..2) Unit else null }

                for (piece in Mime.OUTFIT) if (client.slotOf(piece) < 0) client.command("item $piece")
                client.await("the whole outfit") { if (Mime.OUTFIT.all { client.slotOf(it) >= 0 }) Unit else null }
                client.awaitTicks(2)
                client.perform(client.onStage("alice"), wrongFirst = false)
                client.await("back") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.await("one emote") { if (Integer.bitCount(client.varp(EMOTE_ACCESS) and 0xF) == 1) Unit else null }

                client.command("varp $EMOTE_ACCESS 15")
                client.await("all four emotes") { if (client.varp(EMOTE_ACCESS) and 0xF == 0xF) Unit else null }
                client.awaitTicks(2)
                client.perform(client.onStage("alice"), wrongFirst = false)
                client.await("the lamp") { if (client.slotOf(XpLamp.LAMP) >= 0) Unit else null }
                client.await("the stage ended") { if (server.world.instances.all.isEmpty()) Unit else null }
            }
        }
    }

    @Test
    fun `with a full backpack the pieces go on the ground back home, not on the stage`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                val mime = client.onStage("carol")
                client.command("item $BEER 28")
                client.await("a full backpack") { if (client.inventory(Inventory.INV).count { it != null } == 28) Unit else null }
                client.perform(mime, wrongFirst = false)
                client.await("back") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                val pieces = client.await("a piece or two on the ground") { client.groundObjs().filter { it.id in Mime.OUTFIT }.takeIf { it.isNotEmpty() } }
                assertTrue(pieces.all { it.coord == Coord(3222, 3218, 0) }, pieces.toString())
            }
        }
    }

    @Test
    fun `busy sends him off, and a slow player gets another emote`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("randomevent ${Mime.key}")
                val npc = client.await("the old man") { client.npcs().firstOrNull { it.id == Mime.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[mime.invite name=bob]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.chat("[mime.busy]")
                client.over()
                client.await("him gone") { if (client.npcs().none { it.id == Mime.npcId }) Unit else null }
                assertEquals(Coord(3222, 3218, 0), client.position)

                client.awaitTicks(2)
                val mime = client.onStage("bob")
                client.await("the window") { if (Mime.INTERFACE in client.openInterfaces()) Unit else null }
                val from = client.actorEvents().size
                client.await("the window closed for another") { if (Mime.INTERFACE !in client.openInterfaces()) Unit else null }
                client.await("his next emote") { client.actorEvents(from).firstOrNull { it.npc && it.index == mime && it.kind == ActorEventKind.ANIMATION } }
                client.await("the window again") { if (Mime.INTERFACE in client.openInterfaces()) Unit else null }
                assertTrue(client.inventory(Inventory.INV).all { it == null })
            }
        }
    }

    @Test
    fun `walking off the opening line still starts the show`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready()
                client.command("randomevent ${Mime.key}")
                val npc = client.await("the old man") { client.npcs().firstOrNull { it.id == Mime.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[mime.invite name=dave]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("[mime.yes]")
                client.awaitDialogue("the opening line") { it is DialogueView.Chat && it.text == "[mime.copy]" }
                val stage = client.position!!
                client.walk(stage.x - 1, stage.z)
                client.over()
                val mime = client.await("the mime") { client.npcs().firstOrNull { it.id == Mime.MIME_NPC } }.index
                client.perform(mime, wrongFirst = false)
                client.await("back") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.await("a piece or two") { if (Mime.OUTFIT.count { client.slotOf(it) >= 0 } in 1..2) Unit else null }
            }
        }
    }

    private companion object {
        const val EMOTE_ACCESS = 313
        const val BEER = 1917
    }
}
