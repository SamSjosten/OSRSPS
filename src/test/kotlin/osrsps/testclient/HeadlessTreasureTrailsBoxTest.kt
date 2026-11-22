package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.npc.NpcSpawn
import osrsps.game.trail.ClueStep
import osrsps.game.trail.LightBoxState
import osrsps.game.trail.StepType
import osrsps.game.trail.TrailBoxes
import osrsps.game.trail.TrailChallenge
import osrsps.game.trail.TrailData
import osrsps.game.trail.TrailTarget
import osrsps.game.trail.TrailTier
import osrsps.game.trail.TrailTile
import osrsps.map.CollisionMap

/**
 * Treasure Trails step TT6 through the headless client: a hard step's NPC hands over the step's puzzle box, which opens
 * on the sliding puzzle with its pieces scrambled; a piece beside the gap slides into it; the NPC wants the puzzle solved
 * and then gives the next clue. A master step's NPC hands over a light box whose switches flip its lights into varp
 * trail_lights. Made-up steps: the real ones come from the cache.
 */
class HeadlessTreasureTrailsBoxTest {
    private val hard = ClueStep(
        StepType.ANAGRAM, 1, TrailTier.HARD, "A MADE UP ANAGRAM", listOf(TrailTarget.Npc(GUIDE, -1, TrailTile(3224, 3220, 0))),
        items = listOf(HARD_CLUE), challenge = TrailChallenge.Box("A puzzle box."),
    )
    private val master = ClueStep(
        StepType.ANAGRAM, 2, TrailTier.MASTER, "ANOTHER ANAGRAM", listOf(TrailTarget.Npc(GUIDE, -1, TrailTile(3224, 3220, 0))),
        challenge = TrailChallenge.Box("A light box."),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(25_000)
        val read = listOf("Read", "Check steps", null, null, "Drop")
        objs[HARD_CLUE] = ObjType(HARD_CLUE, "Clue scroll (hard)", iops = read, params = mapOf(TrailTier.HARD.param to 1))
        objs[TrailTier.MASTER.sharedClue] = ObjType(TrailTier.MASTER.sharedClue, "Clue scroll (master)", iops = read, params = mapOf(TrailTier.MASTER.param to 1))
        objs[TREE_BOX] = ObjType(TREE_BOX, "Puzzle box (hard)", iops = listOf("Open", null, null, null, "Drop"))
        objs[OTHER_BOX] = ObjType(OTHER_BOX, "Puzzle box (hard)", iops = listOf("Open", null, null, null, "Drop"))
        objs[TrailBoxes.LIGHT_BOX] = ObjType(TrailBoxes.LIGHT_BOX, "Light box", iops = listOf("Open", null, null, null, "Destroy"))
        for (piece in TrailBoxes.TREE until TrailBoxes.TREE + 24) objs[piece] = ObjType(piece, "null")
        for (tier in TrailTier.entries) objs[tier.rewardCasket] = ObjType(tier.rewardCasket, "Reward casket (${tier.key})", stackable = true, iops = listOf("Open", null, null, null, "Drop"))
        val types = NpcTypes.of(NpcType(GUIDE, "Made-up guide", ops = listOf("Talk-to", null, null, null, null)))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            npcTypes = types, npcSpawns = listOf(NpcSpawn(types[GUIDE]!!, 3224, 3220, wander = 0, source = "test")),
            trailData = TrailData(listOf(hard, master)), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.talkTo(server: TestServer) = npcOp(server.world.npcs.all.first { it.type.id == GUIDE }.index, 1)

    private fun HeadlessClient.chat(key: String) {
        val view = await(key) { (dialogueView as? DialogueView.Chat)?.takeIf { it.text == "[$key]" } }
        continueDialogue()
        // Gone before the next await, so a second line with the same key isn't matched by this one.
        await("$key closed") { if (dialogueView !== view) Unit else null }
    }

    private fun HeadlessClient.objbox(key: String) {
        await(key) { if (dialogueView is DialogueView.Other && text(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_TEXT) == "[$key]") Unit else null }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
    }

    @Test
    fun `the NPC hands over the step's puzzle box, a piece slides into the gap, and the solved box gives the clue`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("kit").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("clue hard anagram:1") { it.startsWith("Clue") }
                val player = server.world.playerNamed("kit")!!
                // Another step's box, solved, neither answers this step nor stops the NPC handing over its own.
                val solvedCastle = IntArray(25) { TrailBoxes.CASTLE + it }.also { it[24] = TrailBoxes.GAP }
                player.inventory.add(server.world.objTypes[OTHER_BOX]!!, 1)
                player.trails.puzzles[OTHER_BOX] = solvedCastle
                client.talkTo(server)
                client.chat("trail.npc_box")
                val box = client.slotOf(TREE_BOX)

                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 2, box, TREE_BOX)
                val pieces = client.await("the scrambled pieces") { client.inventory(TrailBoxes.PUZZLE_INV).takeIf { inv -> inv.count { it != null } == 24 } }
                assertEquals((TrailBoxes.TREE until TrailBoxes.TREE + 24).toSet(), pieces.mapNotNull { it?.id }.toSet())
                assertTrue(!TrailBoxes.isSolved(player.trails.puzzles.getValue(TREE_BOX)))

                client.talkTo(server)
                client.chat("trail.npc_box_unsolved")

                // Banked, the box keeps its scramble: the NPC hands over no fresh one.
                val scramble = player.trails.puzzles.getValue(TREE_BOX).copyOf()
                player.inventory.remove(box, 1)
                player.bank.add(server.world.objTypes[TREE_BOX]!!, 1)
                client.talkTo(server)
                client.chat("trail.npc_box_unsolved")
                assertEquals(0L, player.inventory.count(TREE_BOX))
                assertTrue(player.trails.puzzles.getValue(TREE_BOX).contentEquals(scramble))
                player.bank.remove(player.bank.contents().first { it.second.id == TREE_BOX }.first, 1)
                player.inventory[box] = osrsps.game.InvObj(TREE_BOX, 1)

                // A slide with the puzzle closed moves nothing.
                client.ifButton(TrailBoxes.SLIDE_PUZZLE, TrailBoxes.SLIDE_PIECES, 1, 24, 0)

                // One slide from solved: the last piece sits where the gap belongs.
                val slots = IntArray(25) { TrailBoxes.TREE + it }.also { it[23] = TrailBoxes.GAP; it[24] = TrailBoxes.TREE + 23 }
                player.trails.puzzles[TREE_BOX] = slots
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 2, box, TREE_BOX)
                client.ifButton(TrailBoxes.SLIDE_PUZZLE, TrailBoxes.SLIDE_PIECES, 1, 24, TrailBoxes.TREE + 23)
                client.await("the solved puzzle") { if (TrailBoxes.isSolved(player.trails.puzzles.getValue(TREE_BOX))) Unit else null }
                client.await("the last piece in place") { client.inventory(TrailBoxes.PUZZLE_INV).getOrNull(23)?.takeIf { it.id == TrailBoxes.TREE + 23 } }
                client.closeModal()

                client.talkTo(server)
                client.chat("trail.npc_box_solved")
                client.objbox("trail.casket")
                assertTrue(client.inventory(BACKPACK).none { it?.id == TREE_BOX || it?.id == HARD_CLUE })
                assertNull(player.trails.puzzles[TREE_BOX])
                client.slotOf(TrailTier.HARD.rewardCasket)
                // The slides went to the box on screen, not the other one held.
                assertEquals(1L, player.inventory.count(OTHER_BOX))
                assertTrue(player.trails.puzzles.getValue(OTHER_BOX).contentEquals(solvedCastle))
            }
        }
    }

    @Test
    fun `a light box's switches flip its lights, and all lit solves it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("lux").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("clue master anagram:2") { it.startsWith("Clue") }
                client.talkTo(server)
                client.chat("trail.npc_box")
                val box = client.slotOf(TrailBoxes.LIGHT_BOX)
                val player = server.world.playerNamed("lux")!!
                // Switch 1 lights the top row, switch 2 everything else; the box starts with switch 2 off.
                player.trails.lightBox = LightBoxState(intArrayOf(0x1F, TrailBoxes.ALL_LIT xor 0x1F, 1, 2, 4, 8, 16, 32), 0x1F)

                // A switch with the box closed flips nothing: the box opens with the lights it had.
                client.ifButton(TrailBoxes.LIGHT_PUZZLE, TrailBoxes.LIGHT_SWITCHES.first, 1)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 2, box, TrailBoxes.LIGHT_BOX)
                client.await("the lights") { if (client.varp(TrailBoxes.LIGHTS_VARP) == 0x1F) Unit else null }
                client.ifButton(TrailBoxes.LIGHT_PUZZLE, TrailBoxes.LIGHT_SWITCHES.first, 1)
                client.await("the top row off") { if (client.varp(TrailBoxes.LIGHTS_VARP) == 0) Unit else null }
                client.ifButton(TrailBoxes.LIGHT_PUZZLE, TrailBoxes.LIGHT_SWITCHES.first, 1)
                client.ifButton(TrailBoxes.LIGHT_PUZZLE, TrailBoxes.LIGHT_SWITCHES.first + 1, 1)
                client.await("all lit") { if (client.varp(TrailBoxes.LIGHTS_VARP) == TrailBoxes.ALL_LIT) Unit else null }
                client.closeModal()

                client.talkTo(server)
                client.chat("trail.npc_box_solved")
                client.objbox("trail.casket")
                assertNull(player.trails.lightBox)
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val GUIDE = 3_000
        const val HARD_CLUE = 19_886
        const val OTHER_BOX = 2_795
        const val TREE_BOX = 19_887
    }
}
