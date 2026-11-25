package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.content.MorUlRek
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Karamja step K2.1 through the headless client: the cave entrance into Mor Ul Rek breaking the rum carried in (1 damage
 * a bottle), the cave exit back to the dungeon, and TzHaar-Ket-Zuh's talk naming the player by TzHaar caste and opening
 * the bank. test-runner/examples/mor-ul-rek.json does the same on the real map.
 */
class HeadlessMorUlRekTest {
    private val banker = NpcType(MorUlRek.BANKER, "TzHaar-Ket-Zuh", ops = listOf("Talk-to", null, "Bank", "Collect", null), internalName = "tzhaar_banker1")
    private val objs = arrayOfNulls<ObjType>(RUM + 1).apply { this[RUM] = ObjType(RUM, "Karamjan rum") }

    private fun type(id: Int, name: String) =
        LocType(id, name, ops = listOf("Enter", null, null, null, null), collision = LocCollisionType(width = 1, length = 1))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(2863 shr 6, 9571 shr 6)
                markLoaded(2479 shr 6, 5176 shr 6)
            },
            objTypes = ObjTypes(objs), npcTypes = NpcTypes.of(banker),
            npcSpawns = listOf(NpcSpawn(banker, 2445, 5180, wander = 0, source = "test")),
            locTypes = LocTypes(mapOf(MorUlRek.CAVE_ENTRANCE to type(MorUlRek.CAVE_ENTRANCE, "Cave entrance"), MorUlRek.CAVE_EXIT to type(MorUlRek.CAVE_EXIT, "Cave exit"))),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(MorUlRek.CAVE_ENTRANCE, LocShape.CENTREPIECE_STRAIGHT, 0, 2863, 9571, 0))
                add(ZoneLoc(MorUlRek.CAVE_EXIT, LocShape.CENTREPIECE_STRAIGHT, 0, 2479, 5176, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        arrive(x, z)
    }

    private fun HeadlessClient.arrive(x: Int, z: Int) {
        await("the player at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.count(id: Int): Long = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    @Test
    fun `the cave entrance breaks the rum carried in, and the exit leads back out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.command("item $RUM 2")
                client.await("the rum") { if (client.count(RUM) == 2L) Unit else null }
                client.tele(2862, 9572)
                client.locOp(MorUlRek.CAVE_ENTRANCE, 2863, 9571, MorUlRek.ENTER)
                client.arrive(MorUlRek.IN_CITY.x, MorUlRek.IN_CITY.z)
                client.await("the rum breaking") { client.messages().firstOrNull { it.text == "[mor_ul_rek.rum_explodes]" } }
                client.await("the rum gone") { if (client.count(RUM) == 0L) Unit else null }
                val player = server.world.playerNamed("ada")!!
                client.await("2 damage") { if (player.hitpoints == 8) Unit else null }

                client.locOp(MorUlRek.CAVE_EXIT, 2479, 5176, MorUlRek.ENTER)
                client.arrive(MorUlRek.IN_DUNGEON.x, MorUlRek.IN_DUNGEON.z)
                client.locOp(MorUlRek.CAVE_ENTRANCE, 2863, 9571, MorUlRek.ENTER)
                client.arrive(MorUlRek.IN_CITY.x, MorUlRek.IN_CITY.z)
                assertEquals(8, player.hitpoints, "no rum, no damage")
            }
        }
    }

    @Test
    fun `TzHaar-Ket-Zuh names the player by caste and opens the bank`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ben").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(2445, 5178)
                val npc = client.await("the banker in view") { client.npcs().firstOrNull { it.id == MorUlRek.BANKER } }
                client.npcOp(npc.index, MorUlRek.TALK_TO)
                // A new account's skills are all 1: every pair adds up to 2, and a tie goes to Mej.
                client.awaitDialogue("tzhaar_banker.greeting") { it is DialogueView.Chat && it.text == "[tzhaar_banker.greeting name=JalYt-Mej-ben]" }
                client.continueDialogue()
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.awaitDialogue("tzhaar_banker.are_you_not") { it is DialogueView.Chat && it.text == "[tzhaar_banker.are_you_not name=JalYt-Mej-ben]" }
                client.continueDialogue()
                client.awaitDialogue("the caste menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.awaitDialogue("tzhaar_banker.whats_caste") { it is DialogueView.Chat && it.text == "[tzhaar_banker.whats_caste caste=JalYt-Mej]" }
                client.continueDialogue()
                client.awaitDialogue("tzhaar_banker.mej_is") { it is DialogueView.Chat && it.text == "[tzhaar_banker.mej_is]" }
                client.continueDialogue()
                client.awaitDialogue("tzhaar_banker.suppose") { it is DialogueView.Chat && it.text == "[tzhaar_banker.suppose]" }
                client.continueDialogue()
                client.awaitDialogue("tzhaar_banker.then_you") { it is DialogueView.Chat && it.text == "[tzhaar_banker.then_you caste=JalYt-Mej]" }
                client.continueDialogue()
                client.awaitDialogue("the last menu") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.awaitDialogue("tzhaar_banker.thanks") { it is DialogueView.Chat && it.text == "[tzhaar_banker.thanks]" }
                client.continueDialogue()
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }

                client.npcOp(npc.index, MorUlRek.BANK)
                client.await("the bank") { if (BankScreen.MAIN in client.openInterfaces()) Unit else null }
                assertTrue(BankScreen.MAIN in client.openInterfaces())
            }
        }
    }

    private companion object {
        const val RUM = 431
        const val BACKPACK = 93
    }
}
