package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.content.AltarLines
import osrsps.game.content.Church
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Rest of Lumbridge step 10 through the headless client: the altar's Pray-at fills drained prayer points, Father Aereck
 * talks, and Nigel, a multi-NPC, talks as the Nigel a normal world shows. The points are drained with ::pray, as the
 * raid's tests already drain them by praying. The rl-client script test-runner/examples/lumbridge-church.json does the
 * same in the real client, in the church.
 */
class HeadlessLumbridgeChurchTest {
    private val talkTo = listOf("Talk-to", null, null, null, null)
    private val aereck = NpcType(Church.FATHER_AERECK, "Father Aereck", ops = talkTo, internalName = "father_aereck")
    private val nigel = NpcType(
        Church.NIGEL, "null", ops = NpcType.DEFAULT_OPS, multiVarp = 0,
        multiNpcs = intArrayOf(Church.NIGEL_REGULAR, NIGEL_DMM, Church.NIGEL_REGULAR), internalName = "deadman_nigel",
    )
    private val count = NpcType(Church.COUNT_CHECK, "Count Check", ops = talkTo, internalName = "count_check")
    private val nigelRegular = NpcType(
        Church.NIGEL_REGULAR, "Nigel", ops = listOf("Talk-to", null, null, "Trade", null), internalName = "deadman_nigel_regular",
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val altar = LocType(
            Church.ALTAR, "Altar", ops = listOf("Pray-at", null, null, null, null), collision = LocCollisionType(width = 2, length = 1),
        )
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, npcTypes = NpcTypes.of(aereck, nigel, nigelRegular, count),
            npcSpawns = listOf(
                NpcSpawn(aereck, 3220, 3218, wander = 0, source = "test"),
                NpcSpawn(nigel, 3224, 3222, wander = 0, source = "test"),
                NpcSpawn(count, 3220, 3222, wander = 0, source = "test"),
            ),
            locTypes = LocTypes(mapOf(Church.ALTAR to altar)),
            staticLocs = StaticLocs().apply { add(ZoneLoc(Church.ALTAR, LocShape.CENTREPIECE_STRAIGHT, 0, 3225, 3218, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `Pray-at fills drained prayer points to the level, and says so again when they are full`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel prayer 43") { it.contains("43") }
                client.commandReply("pray 3") { it.startsWith("Prayer points set") }
                client.await("3 points") { client.stat(PRAYER)?.takeIf { it.level == 3 } }
                client.locOp(Church.ALTAR, 3225, 3218, Church.PRAY_AT)
                client.seen(DialogueText.NONE[AltarLines.RECHARGE])
                assertEquals(43, client.await("43 points") { client.stat(PRAYER)?.takeIf { it.level == 43 } }.level)
                client.locOp(Church.ALTAR, 3225, 3218, Church.PRAY_AT)
                client.seen(DialogueText.NONE[AltarLines.FULL])
            }
        }
    }

    @Test
    fun `Father Aereck thanks the player, and Nigel talks as a normal world's Nigel`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val father = client.await("Father Aereck in view") { client.npcs().firstOrNull { it.id == Church.FATHER_AERECK } }
                client.npcOp(father.index, Church.TALK_TO)
                for (key in listOf("father_aereck.thanks", "father_aereck.new_quest", "father_aereck.one_quest")) {
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
                    client.continueDialogue()
                }
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }

                val npc = client.await("Nigel in view") { client.npcs().firstOrNull { it.id == Church.NIGEL } }
                client.npcOp(npc.index, Church.TALK_TO)
                client.awaitDialogue("the player's question") { it is DialogueView.Chat && it.text == "[nigel.who]" }
                client.continueDialogue()
                val intro = client.awaitDialogue("his introduction") { it is DialogueView.Chat && it.text == "[nigel.intro]" } as DialogueView.Chat
                assertEquals("Nigel", intro.name)
                client.continueDialogue()
                val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[nigel.question]", "[nigel.rewards]", "[nigel.goodbye]"), menu.options)
                client.chooseOption(3)
                client.awaitDialogue("goodbye") { it is DialogueView.Chat && it.text == "[nigel.goodbye]" }
                client.continueDialogue()
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
            }
        }
    }

    @Test
    fun `Count Check sends the player to the Stronghold of Security`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val npc = client.await("Count Check in view") { client.npcs().firstOrNull { it.id == Church.COUNT_CHECK } }
                client.npcOp(npc.index, Church.TALK_TO)
                client.awaitDialogue("his first line") { it is DialogueView.Chat && it.text == "[count_check.intro]" }
                client.continueDialogue()
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(3)
                for (key in listOf("count_check.learn", "count_check.stronghold")) {
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
                    client.continueDialogue()
                }
                val menu = client.awaitDialogue("yes or no") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[count_check.yes]", "[count_check.no]"), menu.options)
                client.chooseOption(1)
                client.await("the Stronghold's entrance") { client.position?.takeIf { it.x == Church.STRONGHOLD.x && it.z == Church.STRONGHOLD.z } }
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
            }
        }
    }

    private companion object {
        const val PRAYER = 5
        const val NIGEL_DMM = 8390
    }
}
