package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InvObj
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.npc.NpcSpawn
import osrsps.game.trail.ClueStep
import osrsps.game.trail.HotCold
import osrsps.game.trail.StepType
import osrsps.game.trail.TrailData
import osrsps.game.trail.TrailTarget
import osrsps.game.trail.TrailTier
import osrsps.game.trail.TrailTile
import osrsps.game.trail.TreasureTrails
import osrsps.map.CollisionMap

/**
 * Treasure Trails step TT8 through the headless client: Charlie sets a task that the clue then shows, waits for its
 * item and takes it for the next clue; Reldo hands over the strange device on a hot-cold step, which says how hot the
 * spot is and whether it got warmer, and Dig near the spot solves it. Made-up steps: the real ones come from the cache.
 */
class HeadlessTreasureTrailsCharlieTest {
    private val charlie = ClueStep(StepType.CRYPTIC, 1, TrailTier.BEGINNER, "Talk to a made-up tramp.", listOf(TrailTarget.Npc(TreasureTrails.CHARLIE, -1, TrailTile(3224, 3220, 0))))
    private val trout = ClueStep(StepType.SKILL_CHALLENGE, 549, TrailTier.BEGINNER, "Give him a made-up trout.", listOf(TrailTarget.Npc(TreasureTrails.CHARLIE, -1, TrailTile(3224, 3220, 0))))
    private val hotCold = ClueStep(StepType.HOT_COLD, 2, TrailTier.BEGINNER, null, listOf(TrailTarget.Dig(TrailTile(3222, 3240, 0))))
    /** A master hot-cold step with no Brassican Mage, so Dig is just the spot. */
    private val masterHotCold = ClueStep(StepType.HOT_COLD, 3, TrailTier.MASTER, null, listOf(TrailTarget.Dig(TrailTile(3222, 3240, 0))))

    private fun server(dir: Path, steps: List<ClueStep>) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(25_000)
        val shared = TrailTier.BEGINNER.sharedClue
        objs[shared] = ObjType(shared, "Clue scroll (beginner)", iops = listOf("Read", "Check steps", null, null, "Drop"), params = mapOf(TrailTier.BEGINNER.param to 1))
        val master = TrailTier.MASTER.sharedClue
        objs[master] = ObjType(master, "Clue scroll (master)", iops = listOf("Read", "Check steps", null, null, "Drop"), params = mapOf(TrailTier.MASTER.param to 1))
        objs[MASTER_DEVICE] = ObjType(MASTER_DEVICE, "Strange device", iops = listOf("Feel", null, null, null, "Drop"))
        objs[TrailTier.MASTER.rewardCasket] = ObjType(TrailTier.MASTER.rewardCasket, "Reward casket (master)", stackable = true, iops = listOf("Open", null, null, null, "Drop"))
        objs[TROUT] = ObjType(TROUT, "Trout")
        objs[DEVICE] = ObjType(DEVICE, "Strange device", iops = listOf("Feel", null, null, null, "Drop"))
        objs[TreasureTrails.SPADE] = ObjType(TreasureTrails.SPADE, "Spade", iops = listOf("Dig", null, null, null, "Drop"))
        objs[TrailTier.BEGINNER.rewardCasket] = ObjType(TrailTier.BEGINNER.rewardCasket, "Reward casket (beginner)", stackable = true, iops = listOf("Open", null, null, null, "Drop"))
        val talkTo = listOf("Talk-to", null, null, null, null)
        val types = NpcTypes.of(NpcType(TreasureTrails.CHARLIE, "Charlie the Tramp", ops = talkTo), NpcType(RELDO, "Reldo", ops = talkTo))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(3222 shr 6, 3240 shr 6) }, objTypes = ObjTypes(objs), npcTypes = types,
            npcSpawns = listOf(NpcSpawn(types[TreasureTrails.CHARLIE]!!, 3224, 3220, source = "test"), NpcSpawn(types[RELDO]!!, 3220, 3220, source = "test")),
            trailData = TrailData(steps), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.op(id: Int, op: Int) = ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, op + 1, slotOf(id), id)

    private fun HeadlessClient.talkTo(server: TestServer, type: Int) = npcOp(server.world.npcs.all.first { it.type.id == type }.index, 1)

    private fun HeadlessClient.chat(key: String) {
        await(key) { (dialogueView as? DialogueView.Chat)?.takeIf { it.text == "[$key]" } }
        continueDialogue()
    }

    private fun HeadlessClient.objbox(key: String) {
        await(key) { if (dialogueView is DialogueView.Other && text(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_TEXT) == "[$key]") Unit else null }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
    }

    /** Read shows [words] on trail_cluetext. */
    private fun HeadlessClient.reads(words: String) {
        op(TrailTier.BEGINNER.sharedClue, 1)
        await(words) { text(TreasureTrails.CLUE_TEXT, TreasureTrails.CLUE_TEXT_WORDS)?.takeIf { it == words } }
        closeModal()
    }

    @Test
    fun `Charlie sets his task, which the clue shows, and takes its item for the next clue`(@TempDir dir: Path) {
        server(dir, listOf(charlie, trout)).use { server ->
            server.login("pip").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("clue beginner cryptic:1") { it.startsWith("Clue") }
                client.reads("Talk to a made-up tramp.")
                client.talkTo(server, TreasureTrails.CHARLIE)
                client.chat("trail.charlie_task")
                client.reads("Give him a made-up trout.")

                client.talkTo(server, TreasureTrails.CHARLIE)
                client.chat("trail.charlie_waiting")
                server.world.playerNamed("pip")!!.inventory[10] = InvObj(TROUT, 1)
                client.talkTo(server, TreasureTrails.CHARLIE)
                client.chat("trail.charlie_thanks")
                client.objbox("trail.casket")
                assertTrue(client.inventory(BACKPACK).none { it?.id == TROUT })
            }
        }
    }

    @Test
    fun `Reldo hands over the device, which says how hot the spot is, and Dig on it solves the step`(@TempDir dir: Path) {
        server(dir, listOf(hotCold)).use { server ->
            server.login("quin").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("clue beginner hotcold:2") { it.startsWith("Clue") }
                client.reads("[hotcold.clue_beginner]")
                client.talkTo(server, RELDO)
                client.chat("hotcold.gives_device")
                client.slotOf(DEVICE)

                client.commandReply("tele 3222 3222") { it.startsWith("Teleported") }
                client.awaitTicks(2)
                var from = client.messages().size
                client.op(DEVICE, 1)
                client.await("18 tiles off") { client.messages().drop(from).firstOrNull { it.text == "[hotcold.first temperature=[hotcold.incredibly_hot]]" } }
                client.commandReply("tele 3222 3238") { it.startsWith("Teleported") }
                client.awaitTicks(2)
                from = client.messages().size
                client.op(DEVICE, 1)
                client.await("on the spot") { client.messages().drop(from).firstOrNull { it.text == "[hotcold.again temperature=[hotcold.shaking] change=[hotcold.warmer]]" } }

                client.command("item ${TreasureTrails.SPADE}")
                client.op(TreasureTrails.SPADE, 1)
                client.objbox("trail.casket")
            }
        }
    }

    @Test
    fun `the master device hurts on each feel, keeps its own reading beside the beginner one, and both dig from 4 tiles`(@TempDir dir: Path) {
        server(dir, listOf(hotCold, masterHotCold)).use { server ->
            server.login("rue").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel hitpoints 99") { true }
                client.commandReply("clue beginner hotcold:2") { it.startsWith("Clue") }
                client.commandReply("clue master hotcold:3") { it.startsWith("Clue") }
                client.command("item $DEVICE")
                client.command("item $MASTER_DEVICE")
                client.command("item ${TreasureTrails.SPADE}")
                client.commandReply("tele 3222 3222") { it.startsWith("Teleported") }
                client.awaitTicks(2)
                val player = server.world.playerNamed("rue")!!

                var from = client.messages().size
                val hp = player.skills.currentLevel(Skill.HITPOINTS)
                client.op(MASTER_DEVICE, 1)
                client.await("the master reading") { client.messages().drop(from).firstOrNull { it.text == "[hotcold.first temperature=[hotcold.incredibly_hot]]" } }
                client.await("the device's hit") { (hp - player.skills.currentLevel(Skill.HITPOINTS)).takeIf { it != 0 } }
                    .let { assertTrue(it in HotCold.MASTER_MIN_HIT..HotCold.MASTER_MAX_HIT, "hit $it") }

                // The beginner device in between leaves the master reading alone.
                from = client.messages().size
                client.op(DEVICE, 1)
                client.await("the beginner reading") { client.messages().drop(from).firstOrNull { it.text == "[hotcold.first temperature=[hotcold.incredibly_hot]]" } }
                client.commandReply("tele 3222 3235") { it.startsWith("Teleported") }
                client.awaitTicks(2)
                from = client.messages().size
                client.op(MASTER_DEVICE, 1)
                client.await("warmer on the master device") { client.messages().drop(from).firstOrNull { it.text == "[hotcold.again temperature=[hotcold.incredibly_hot] change=[hotcold.warmer]]" } }

                // Five tiles off is outside the Wiki's 9x9 square: Dig finds nothing. Four tiles off is inside it, for
                // either tier: the first Dig solves the beginner step (its clue comes first in the backpack), the next
                // the master one.
                client.op(TreasureTrails.SPADE, 1)
                client.awaitMessage("nothing at 5 tiles") { it == "[game.nothing_interesting]" }
                assertTrue(client.inventory(BACKPACK).any { it?.id == TrailTier.MASTER.sharedClue }, "the master clue is still held")
                client.commandReply("tele 3222 3236") { it.startsWith("Teleported") }
                client.awaitTicks(2)
                client.op(TreasureTrails.SPADE, 1)
                client.objbox("trail.casket")
                client.await("the beginner casket") { client.inventory(BACKPACK).firstOrNull { it?.id == TrailTier.BEGINNER.rewardCasket } }
                client.op(TreasureTrails.SPADE, 1)
                client.objbox("trail.casket")
                client.await("the master casket") { client.inventory(BACKPACK).firstOrNull { it?.id == TrailTier.MASTER.rewardCasket } }
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val TROUT = 333
        const val RELDO = 4243
        val DEVICE = HotCold.DEVICES.getValue(TrailTier.BEGINNER)
        val MASTER_DEVICE = HotCold.DEVICES.getValue(TrailTier.MASTER)
    }
}
