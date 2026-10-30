package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.FatherUrhney
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Rest of Lumbridge step 9 through the headless client, with the real combat and drop files: a giant rat killed leaves
 * its bones and raw rat meat, and Father Urhney talks. The rl-client script test-runner/examples/lumbridge-swamp.json does the same in the real
 * client, in the swamp, and kills a giant frog for its big bones.
 */
class HeadlessLumbridgeSwampTest {
    private val giantRat = NpcType(
        GIANT_RAT, "Giant rat", ops = listOf(null, "Attack", null, null, null), combatLevel = 3, size = 2,
        internalName = "giantrat", stats = NpcStats(attack = 2, defence = 2, strength = 3, hitpoints = 5),
    )

    private val urhney = NpcType(FatherUrhney.ID, "Father Urhney", ops = listOf("Talk-to", null, null, null, null), internalName = "father_urhney")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(giantRat, urhney)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(9), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(giantRat, 3224, 3218, source = "test"), NpcSpawn(urhney, 3220, 3218, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    @Test
    fun `a giant rat killed drops its bones and raw rat meat`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the giant rat in view") { client.npcs().firstOrNull { it.id == GIANT_RAT } }
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.npcOp(npc.index, 2)
                // Every hit is 0 or 1, so five of them, however the dice fall.
                client.await("the giant rat's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == GIANT_RAT }) Unit else null }
                val drops = client.await("the bones and the meat") {
                    client.groundObjs().map { it.id }.takeIf { BONES in it && RAW_RAT_MEAT in it }
                }
                assertEquals(1, drops.count { it == BONES })
                assertEquals(5 * 4, client.stat(ATTACK)!!.xp)
            }
        }
    }

    @Test
    fun `Father Urhney tells the player to go, and the friendly remark gets the same`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val npc = client.await("Father Urhney in view") { client.npcs().firstOrNull { it.id == FatherUrhney.ID } }
                client.npcOp(npc.index, FatherUrhney.TALK_TO)
                val first = client.awaitDialogue("his first line") { it is DialogueView.Chat && it.text == "[father_urhney.go_away]" } as DialogueView.Chat
                assertEquals("Father Urhney", first.name)
                client.continueDialogue()
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[father_urhney.friendly]", "[father_urhney.lost_amulet]", "[father_urhney.repossess]"), menu.options)
                client.chooseOption(1)
                for (key in listOf("father_urhney.friendly", "father_urhney.still_go", "father_urhney.grouch")) {
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
                    client.continueDialogue()
                }
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
            }
        }
    }

    private companion object {
        const val GIANT_RAT = 2856
        const val BONES = 526
        const val RAW_RAT_MEAT = 2134
        const val ATTACK = 0
    }
}
