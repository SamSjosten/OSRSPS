package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.AllSettings
import osrsps.game.GameRandom
import osrsps.game.ItemPrices
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.LootLines
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.dialogue.DialogueText
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * The All Settings loot drop notifications over the wire: a man killed by the player drops bones, priced here at 120,
 * and the chat says so only when the setting is on and the bones reach its minimum value. With no dialogue file the
 * message is its key. The rl-client script test-runner/examples/settings-loot.json does the same in the real client.
 */
class HeadlessLootNotificationsTest {
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(man)
        val objTypes = DropTestItems.objTypes()
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5), objTypes = objTypes, npcTypes = types,
            npcSpawns = listOf(NpcSpawn(man, 3224, 3218, source = "test")), npcCombat = NpcCombat.load(types, SeqNames.NONE),
            npcDrops = NpcDrops.load(types, objTypes), varTypes = VarTypes(6_000, AllSettings.VARBITS),
            itemPrices = ItemPrices(mapOf(BONES to ItemPrices.Entry("Bones", 120, null))), cycleMillis = millis,
        )
    }

    /** Sets the notification's minimum, turns it on, kills the man and waits for his bones on the floor. */
    private fun HeadlessClient.killWithMinimum(minimum: Int) {
        val npc = await("the man in view") { npcs().firstOrNull { it.id == man.id } }
        await("the login rebuild") { position }
        commandReply("varbit ${AllSettings.VARBIT_LOOT_NOTIFICATION_VALUE} $minimum") { it.startsWith("Varbit") }
        commandReply("varbit ${AllSettings.VARBIT_LOOT_NOTIFICATION} 1") { it.startsWith("Varbit") }
        assertEquals("Killed Man #${npc.index}.", commandReply("kill man") { it.startsWith("Killed") })
        await("the bones on the floor", timeoutMillis = 10_000) { groundObjs().firstOrNull { it.id == BONES } }
    }

    @Test
    fun `bones worth the minimum or more are announced in chat`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.killWithMinimum(120)
                val text = DialogueText.NONE[LootLines.VALUABLE, "item" to "Bones", "value" to "120"]
                client.await(text) { client.messages().firstOrNull { it.text == text } }
            }
        }
    }

    @Test
    fun `bones worth less than the minimum are not`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.killWithMinimum(121)
                // The message would come in the tick the bones appear, so a tick more is enough to see it isn't coming.
                client.awaitTicks(2)
                assertTrue(client.messages().none { it.text.startsWith("[loot.") }, "no loot message: ${client.messages()}")
            }
        }
    }

    private companion object {
        const val BONES = 526
    }
}
