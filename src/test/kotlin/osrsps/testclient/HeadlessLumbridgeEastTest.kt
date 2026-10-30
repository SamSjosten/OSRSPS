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
import osrsps.game.content.BarfyBill
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Rest of Lumbridge step 8 through the headless client, with the real combat and drop files: a goblin killed leaves
 * its bones, and Barfy Bill talks. The rl-client script test-runner/examples/lumbridge-east.json does the same in the
 * real client, among the goblins east of the river.
 */
class HeadlessLumbridgeEastTest {
    private val goblin = NpcType(
        GOBLIN, "Goblin", ops = listOf(null, "Attack", null, null, null), combatLevel = 2,
        internalName = "goblin_unarmed_melee_1", stats = NpcStats(hitpoints = 5),
    )
    private val bill = NpcType(BarfyBill.ID, "Barfy Bill", ops = listOf("Talk-to", null, "Store-axe", null, null), internalName = "canoeing_bill")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(goblin, bill)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(8), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(goblin, 3224, 3218, source = "test"), NpcSpawn(bill, 3220, 3218, source = "test")),
            npcCombat = NpcCombat.load(types, SeqNames.NONE), npcDrops = NpcDrops.load(types, objTypes), cycleMillis = millis,
        )
    }

    @Test
    fun `a goblin killed drops its bones where it died`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the goblin in view") { client.npcs().firstOrNull { it.id == GOBLIN } }
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.npcOp(npc.index, 2)
                // Every hit is 0 or 1, so five of them, however the dice fall.
                client.await("the goblin's death", timeoutMillis = 60_000) { if (client.npcs().none { it.id == GOBLIN }) Unit else null }
                val bones = client.await("the bones") { client.groundObjs().firstOrNull { it.id == BONES } }
                assertEquals(1, bones.count)
                assertEquals(5 * 4, client.stat(ATTACK)!!.xp)
            }
        }
    }

    @Test
    fun `Barfy Bill greets the player and asks what they want to know`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val npc = client.await("Barfy Bill in view") { client.npcs().firstOrNull { it.id == BarfyBill.ID } }
                client.npcOp(npc.index, BarfyBill.TALK_TO)
                for ((name, key) in listOf("bob" to "barfy_bill.hello", "Barfy Bill" to "barfy_bill.oh_hello")) {
                    val page = client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" } as DialogueView.Chat
                    assertEquals(name, page.name)
                    client.continueDialogue()
                }
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[barfy_bill.who]", "[barfy_bill.teach]"), menu.options)
                client.chooseOption(2)
                // A new account is below level 12 Woodcutting.
                for (key in listOf("barfy_bill.teach_me", "barfy_bill.no_skill", "barfy_bill.level_12", "barfy_bill.quicker")) {
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
                    client.continueDialogue()
                }
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
            }
        }
    }

    private companion object {
        const val GOBLIN = 3029
        const val BONES = 526
        const val ATTACK = 0
    }
}
