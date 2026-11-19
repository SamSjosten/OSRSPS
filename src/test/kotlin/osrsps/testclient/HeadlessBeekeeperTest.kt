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
import osrsps.game.content.randomevents.Beekeeper
import osrsps.game.content.randomevents.RandomEvents
import osrsps.map.CollisionMap

/**
 * Random events step 8 through the headless client: the Bee keeper takes his player to his field and opens the beehive
 * window; its parts (varbits on varp macro_bee_pos_1) are dragged into the four slots and confirmed. Wrong takes a try
 * and reopens the window; right brings the player back with a lamp and an outfit piece (or noted flax with the whole
 * outfit); six wrong send them home with nothing. Declining gives nothing. The rl-client script
 * test-runner/examples/random-events-beekeeper.json does part of it in the real game.
 */
class HeadlessBeekeeperTest {
    private val npcs = listOf(
        NpcType(Beekeeper.npcId, "Bee keeper", ops = listOf("Talk-to", null, null, null, "Dismiss")),
        NpcType(Beekeeper.FIELD_KEEPER, "Bee keeper", ops = listOf("Talk-to", null, null, null, null)),
    )

    private val objs = Beekeeper.OUTFIT.map { ObjType(it, "Beekeeper $it") } + ObjType(XpLamp.LAMP, "Lamp") +
        ObjType(Beekeeper.NOTED_FLAX, "Flax", stackable = true) + ObjType(BEER, "Beer")

    private val varbits = (Beekeeper.START_VARBITS + Beekeeper.DEST_VARBITS).withIndex()
        .associate { (i, id) -> id to VarbitType(Beekeeper.MACRO_BEE_POS, i * 3, i * 3 + 2) }

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(30, 78) },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()), objTypes = ObjTypes.of(*objs.toTypedArray()),
            varTypes = VarTypes(2_000, varbits), cycleMillis = millis,
        )
    }

    private val places = Beekeeper.STARTS + Beekeeper.DESTS

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

    private fun HeadlessClient.slotOf(id: Int) = inventory(Inventory.INV).indexOfFirst { it?.id == id }

    private fun HeadlessClient.window() = await("the beehive") { if (Beekeeper.INTERFACE in openInterfaces()) Unit else null }

    /** The part (1 to 4, 0 for none) in each of the eight places, starts first. */
    private fun HeadlessClient.parts(): List<Int> = (0 until 8).map { (varp(Beekeeper.MACRO_BEE_POS) shr (it * 3)) and 7 }

    private fun HeadlessClient.drag(from: Int, to: Int) {
        val before = parts()
        ifButtonD(Beekeeper.INTERFACE, places[from], -1, -1, Beekeeper.INTERFACE, places[to], -1)
        val after = before.toMutableList().also { it[from] = before[to]; it[to] = before[from] }
        await("the swap") { if (parts() == after) Unit else null }
    }

    /** Talk-to on him out in the world and yes: into the field, his talk, and the window. */
    private fun HeadlessClient.intoField(name: String) {
        command("randomevent ${Beekeeper.key}")
        val npc = await("the Bee keeper") { npcs().firstOrNull { it.id == Beekeeper.npcId } }
        npcOp(npc.index, RandomEvents.TALK_TO)
        chat("[beekeeper.help name=$name]")
        awaitDialogue("the options") { it is DialogueView.Menu }
        chooseOption(1)
        chat("[beekeeper.ooh]")
        chat("[beekeeper.drag_away name=$name]")
        chat("[beekeeper.what_expect]")
        chat("[beekeeper.jumbled]")
        chat("[beekeeper.take_a_look]")
        window()
        val start = parts()
        assertEquals(setOf(1, 2, 3, 4), start.take(4).toSet(), "four parts to start: $start")
        assertTrue(start.drop(4).all { it == 0 })
    }

    /** Every part into its slot: lid, body, entrance, legs. */
    private fun HeadlessClient.build() {
        for (part in 1..4) {
            val at = parts().indexOf(part)
            val slot = 4 + part - 1
            if (at != slot) drag(at, slot)
        }
        ifButton(Beekeeper.INTERFACE, Beekeeper.CONFIRM, 1)
    }

    @Test
    fun `the beehive, one wrong build, then right, and back with a lamp and a piece, then flax with the whole outfit`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.intoField("alice")
                // Confirmed empty: wrong, five tries left, the window again.
                client.ifButton(Beekeeper.INTERFACE, Beekeeper.CONFIRM, 1)
                client.chat("[beekeeper.not_right]")
                client.chat("[beekeeper.tries_5]")
                client.window()
                client.build()
                client.chat("[beekeeper.perfect]")
                client.chat("[beekeeper.offer]")
                client.await("back where they came from") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.await("the lamp") { if (client.slotOf(XpLamp.LAMP) >= 0) Unit else null }
                client.await("a piece") { if (Beekeeper.OUTFIT.count { client.slotOf(it) >= 0 } == 1) Unit else null }

                client.command("empty")
                for (piece in Beekeeper.OUTFIT) client.command("item $piece")
                client.await("the whole outfit") { if (Beekeeper.OUTFIT.all { client.slotOf(it) >= 0 }) Unit else null }
                client.awaitTicks(2)
                client.intoField("alice")
                client.build()
                // Back, and rewarded, before his lines: shutting them loses nothing.
                client.awaitDialogue("his line") { it is DialogueView.Chat && it.text == "[beekeeper.perfect]" }
                client.await("back again") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.closeModal()
                client.await("the talk over") { if (client.dialogueView == null) Unit else null }
                client.await("the flax") { if (client.slotOf(Beekeeper.NOTED_FLAX) >= 0) Unit else null }
                val flax = client.inventory(Inventory.INV).first { it?.id == Beekeeper.NOTED_FLAX }!!.count
                assertTrue(flax in 1..Beekeeper.FLAX_MAX, "flax $flax")
                client.await("the field ended") { if (server.world.instances.all.isEmpty()) Unit else null }
            }
        }
    }

    @Test
    fun `with a full backpack the lamp and piece go on the ground back home, not in the field`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                client.intoField("carol")
                client.command("item $BEER 28")
                client.await("a full backpack") { if (client.inventory(Inventory.INV).count { it != null } == 28) Unit else null }
                client.build()
                client.chat("[beekeeper.perfect]")
                client.chat("[beekeeper.offer]")
                client.await("back") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                val dropped = client.await("the lamp and a piece on the ground") {
                    client.groundObjs().filter { it.id == XpLamp.LAMP || it.id in Beekeeper.OUTFIT }.takeIf { it.size == 2 }
                }
                assertTrue(dropped.all { it.coord == Coord(3222, 3218, 0) }, dropped.toString())
            }
        }
    }

    @Test
    fun `buzz off sends him off, talking reopens the window, and six wrong builds send the player home with nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("randomevent ${Beekeeper.key}")
                val npc = client.await("the Bee keeper") { client.npcs().firstOrNull { it.id == Beekeeper.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[beekeeper.help name=bob]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.chat("[beekeeper.buzz_off]")
                client.await("him gone") { if (client.npcs().none { it.id == Beekeeper.npcId }) Unit else null }

                client.awaitTicks(2)
                client.intoField("bob")
                client.closeModal()
                client.await("the window closed") { if (Beekeeper.INTERFACE !in client.openInterfaces()) Unit else null }
                val keeper = client.await("the field's Bee keeper") { client.npcs().firstOrNull { it.id == Beekeeper.FIELD_KEEPER } }
                client.npcOp(keeper.index, RandomEvents.TALK_TO)
                client.chat("[beekeeper.ready]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("[beekeeper.remind]")
                client.chat("[beekeeper.jumbled]")
                client.window()
                for (tries in listOf(5, 4, 3, 2, 1)) {
                    client.ifButton(Beekeeper.INTERFACE, Beekeeper.CONFIRM, 1)
                    client.chat("[beekeeper.not_right]")
                    client.chat("[beekeeper.tries_$tries]")
                    client.window()
                }
                client.ifButton(Beekeeper.INTERFACE, Beekeeper.CONFIRM, 1)
                client.chat("[beekeeper.fed_up]")
                client.await("the message") { client.messages().firstOrNull { it.text == "[beekeeper.teleported_away]" } }
                client.await("back") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.awaitTicks(2)
                assertTrue(client.inventory(Inventory.INV).all { it == null })
                client.await("the field ended") { if (server.world.instances.all.isEmpty()) Unit else null }
            }
        }
    }

    @Test
    fun `walking off his talk in the field, and talking to him still gives a jumbled hive`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready()
                client.command("randomevent ${Beekeeper.key}")
                val npc = client.await("the Bee keeper") { client.npcs().firstOrNull { it.id == Beekeeper.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[beekeeper.help name=dave]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("[beekeeper.ooh]")
                client.awaitDialogue("his line in the field") { it is DialogueView.Chat && it.text == "[beekeeper.drag_away name=dave]" }
                val at = client.position!!
                client.walk(at.x - 1, at.z)
                client.await("the talk over") { if (client.dialogueView == null) Unit else null }
                val keeper = client.await("the field's Bee keeper") { client.npcs().firstOrNull { it.id == Beekeeper.FIELD_KEEPER } }
                client.npcOp(keeper.index, RandomEvents.TALK_TO)
                client.chat("[beekeeper.ready]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.chat("[beekeeper.get_on]")
                client.window()
                val start = client.parts()
                assertEquals(setOf(1, 2, 3, 4), start.take(4).toSet(), "four parts to start: $start")
                client.build()
                client.await("back") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.await("the lamp") { if (client.slotOf(XpLamp.LAMP) >= 0) Unit else null }
            }
        }
    }

    private companion object {
        const val BEER = 1917
    }
}
