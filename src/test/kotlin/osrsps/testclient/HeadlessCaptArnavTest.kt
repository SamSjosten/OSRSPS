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
import osrsps.game.content.randomevents.CaptArnav
import osrsps.game.content.randomevents.RandomEvents
import osrsps.map.CollisionMap

/**
 * Random events step 9 through the headless client: Capt' Arnav's chest. The lock window's three columns (varbits on
 * varp macro_transmit) roll with their arrows, each under a word; Unlock with the words matched gives one of his four
 * rewards, wrong once brings his explanation and the window again, wrong twice sends him off with nothing. Declining
 * gives nothing. The rl-client script test-runner/examples/random-events-capt-arnav.json does part of it in the real
 * game.
 */
class HeadlessCaptArnavTest {
    private val npcs = listOf(NpcType(CaptArnav.npcId, "Capt' Arnav", ops = listOf("Talk-to", null, null, null, "Dismiss")))

    private val rewards = listOf(CaptArnav.COINS, CaptArnav.GOLD_RING, CaptArnav.GOLD_NECKLACE, CaptArnav.GOLD_BAR)
    private val objs = listOf(
        ObjType(CaptArnav.COINS, "Coins", stackable = true), ObjType(CaptArnav.GOLD_RING, "Gold ring"),
        ObjType(CaptArnav.GOLD_NECKLACE, "Gold necklace"), ObjType(CaptArnav.GOLD_BAR, "Gold bar"),
    )

    private val varbits = CaptArnav.COLUMN_VARBITS.withIndex().associate { (i, id) -> id to VarbitType(CaptArnav.MACRO_TRANSMIT, i * 2, i * 2 + 1) }

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
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

    private fun HeadlessClient.items() = inventory(Inventory.INV).count { it != null }

    private fun HeadlessClient.column(i: Int) = (varp(CaptArnav.MACRO_TRANSMIT) shr (i * 2)) and 3

    /** The picture each column's word names, from the word's key. */
    private fun HeadlessClient.wanted(): List<Int> = CaptArnav.TEXTS.map { text ->
        await("column word") { text(CaptArnav.INTERFACE, text) }.removePrefix("[capt_arnav.picture_").removeSuffix("]").toInt()
    }

    private fun HeadlessClient.lock() = await("the lock") { if (CaptArnav.INTERFACE in openInterfaces()) Unit else null }

    /** Talk-to and yes: the lock window. */
    private fun HeadlessClient.start(name: String) {
        command("randomevent ${CaptArnav.key}")
        val npc = await("Capt' Arnav") { npcs().firstOrNull { it.id == CaptArnav.npcId } }
        await("his greeting") { npcSaid().firstOrNull { it.index == npc.index && it.text == "[capt_arnav.avast name=$name]" } }
        npcOp(npc.index, RandomEvents.TALK_TO)
        chat("[capt_arnav.dug_up]")
        chat("[capt_arnav.fiddly]")
        awaitDialogue("the options") { it is DialogueView.Menu }
        chooseOption(1)
        chat("[capt_arnav.yes]")
        chat("[capt_arnav.columns]")
        lock()
    }

    /** Rolls every column to its word: up for the left two, down for the right, as the arrows turn them. */
    private fun HeadlessClient.solve() {
        val wanted = wanted()
        for (i in 0 until 3) {
            while (column(i) != wanted[i]) {
                val before = column(i)
                val up = i < 2
                ifButton(CaptArnav.INTERFACE, if (up) CaptArnav.UP[i] else CaptArnav.DOWN[i], 1)
                val after = (before + if (up) 1 else 3) % 4
                await("column $i turned") { if (column(i) == after) Unit else null }
            }
        }
    }

    private fun HeadlessClient.wrong() {
        val wanted = wanted()
        // Turn the left column off its word if it's on it, so the combination is wrong.
        if (column(0) == wanted[0]) {
            ifButton(CaptArnav.INTERFACE, CaptArnav.UP[0], 1)
            await("the left column turned") { if (column(0) != wanted[0]) Unit else null }
        }
        ifButton(CaptArnav.INTERFACE, CaptArnav.CONFIRM, 1)
    }

    @Test
    fun `the lock rolled to its words opens the chest for a reward, after one wrong try and his explanation`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.start("alice")
                client.wrong()
                client.chat("[capt_arnav.nowhere_near]")
                client.chat("[capt_arnav.misunderstood]")
                client.chat("[capt_arnav.arrows]")
                client.chat("[capt_arnav.middle_row]")
                client.chat("[capt_arnav.clearer]")
                client.chat("[capt_arnav.try_again]")
                client.lock()
                client.solve()
                client.ifButton(CaptArnav.INTERFACE, CaptArnav.CONFIRM, 1)
                // Rewarded before his line: shutting it loses nothing, and talking again opens nothing.
                client.awaitDialogue("his line") { it is DialogueView.Chat && it.text == "[capt_arnav.well_done]" }
                client.closeModal()
                client.over()
                client.npcs().firstOrNull { it.id == CaptArnav.npcId }?.let { client.npcOp(it.index, RandomEvents.TALK_TO) }
                client.awaitTicks(2)
                assertEquals(null, client.dialogueView)
                client.await("him gone") { if (client.npcs().none { it.id == CaptArnav.npcId }) Unit else null }
                val got = client.await("a reward") { client.inventory(Inventory.INV).firstOrNull { it != null } }!!
                assertTrue(got.id in rewards, "reward ${got.id}")
                if (got.id == CaptArnav.COINS) assertEquals(CaptArnav.COIN_COUNT, got.count)
            }
        }
    }

    @Test
    fun `no sends him off, and two wrong tries send him off with nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("randomevent ${CaptArnav.key}")
                val npc = client.await("Capt' Arnav") { client.npcs().firstOrNull { it.id == CaptArnav.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[capt_arnav.dug_up]")
                client.chat("[capt_arnav.fiddly]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.chat("[capt_arnav.no]")
                client.over()
                client.await("him gone") { if (client.npcs().none { it.id == CaptArnav.npcId }) Unit else null }

                client.awaitTicks(2)
                client.start("bob")
                val words = client.wanted()
                client.wrong()
                val turned = (0 until 3).map { client.column(it) }
                // Shut partway through his explanation, then talked to again: the same lock, and the wrong try kept.
                client.awaitDialogue("his explanation") { it is DialogueView.Chat && it.text == "[capt_arnav.nowhere_near]" }
                client.closeModal()
                client.over()
                val again = client.await("Capt' Arnav still there") { client.npcs().firstOrNull { it.id == CaptArnav.npcId } }
                client.npcOp(again.index, RandomEvents.TALK_TO)
                client.chat("[capt_arnav.dug_up]")
                client.chat("[capt_arnav.fiddly]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("[capt_arnav.yes]")
                client.chat("[capt_arnav.columns]")
                client.lock()
                assertEquals(words, client.wanted())
                assertEquals(turned, (0 until 3).map { client.column(it) })
                client.wrong()
                // Shutting his last line gives no third try.
                client.awaitDialogue("his last line") { it is DialogueView.Chat && it.text == "[capt_arnav.someone_else]" }
                client.closeModal()
                client.over()
                client.npcs().firstOrNull { it.id == CaptArnav.npcId }?.let { client.npcOp(it.index, RandomEvents.TALK_TO) }
                client.awaitTicks(2)
                assertEquals(null, client.dialogueView)
                client.await("him gone again") { if (client.npcs().none { it.id == CaptArnav.npcId }) Unit else null }
                assertEquals(0, client.items())
            }
        }
    }
}
