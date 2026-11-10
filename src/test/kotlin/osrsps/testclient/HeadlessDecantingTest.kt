package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Decanting
import osrsps.map.CollisionMap

/**
 * Artisan 2 step A2b through the headless client: Bob Barter's Decant option opens the decant interface, and a dose
 * button decants the backpack; his Talk-to reaches the same through "Can you decant things for me?". The rl-client
 * script test-runner/examples/decanting.json does the same in the real client.
 */
class HeadlessDecantingTest {
    private val drink = listOf("Drink", null, null, null, "Drop")
    private fun energy(dose: Int) = ObjType(ENERGY[dose - 1], "Super energy($dose)", iops = drink, certlink = ENERGY[dose - 1] + 1)
    private fun energyNote(dose: Int) = ObjType(ENERGY[dose - 1] + 1, "Super energy($dose)", stackable = true, certlink = ENERGY[dose - 1], certtemplate = 799)
    private val objs = ObjTypes.of(
        *(1..4).map { energy(it) }.toTypedArray(), *(1..4).map { energyNote(it) }.toTypedArray(),
        ObjType(Decanting.VIAL, "Vial", certlink = Decanting.VIAL_NOTE),
        ObjType(Decanting.VIAL_NOTE, "Vial", stackable = true, certlink = Decanting.VIAL, certtemplate = 799),
        ObjType(Decanting.COINS, "Coins", stackable = true),
    )
    private val bob = NpcType(Decanting.ID, "Bob Barter (herbs)", ops = listOf("Talk-to", null, "Prices", "Decant", null), internalName = "ge_expert_herbs")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objs, npcTypes = NpcTypes.of(bob),
            npcSpawns = listOf(NpcSpawn(bob, 3221, 3220, source = "test")), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.bobOp(op: Int) {
        val npc = await("Bob in view") { npcs().firstOrNull { it.id == Decanting.ID } }
        npcOp(npc.index, op)
    }

    private fun HeadlessClient.line(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.pickDose(dose: Int) {
        await("the decant interface") { if (Decanting.INTERFACE in openInterfaces()) Unit else null }
        resumePause(Decanting.INTERFACE, Decanting.BUTTONS[dose - 1])
    }

    @Test
    fun `Decant to 4 doses turns four 3-dose potions into three 4-dose and a vial`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${ENERGY[2]} 4")
                client.await("the potions") { if (client.held(ENERGY[2]) == 4) Unit else null }
                client.bobOp(Decanting.DECANT)
                client.pickDose(4)
                client.line("bob_barter.all_done")
                assertEquals(3, client.held(ENERGY[3]))
                assertEquals(0, client.held(ENERGY[2]))
                assertEquals(1, client.held(Decanting.VIAL))
            }
        }
    }

    @Test
    fun `noted potions through Talk-to come back noted at 1 dose, the vials bought`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${ENERGY[1] + 1} 2")
                client.command("item ${Decanting.COINS} 100")
                client.await("the notes and coins") { if (client.held(ENERGY[1] + 1) == 2 && client.held(Decanting.COINS) == 100) Unit else null }
                client.bobOp(Decanting.TALK_TO)
                client.line("bob_barter.hello")
                client.line("bob_barter.no_thanks")
                client.line("bob_barter.what_can_i_do")
                client.awaitDialogue("his menu") { it is DialogueView.Menu }
                client.chooseOption(3)
                client.line("bob_barter.decant")
                client.pickDose(1)
                client.awaitDialogue("his random line") { it is DialogueView.Chat && it.text in RANDOM }
                client.continueDialogue()
                client.line("bob_barter.all_done")
                // Two 2-dose potions are four 1-dose: their two vials plus two bought at 3 coins (no guide price here).
                assertEquals(4, client.held(ENERGY[0] + 1))
                assertEquals(0, client.held(ENERGY[1] + 1))
                assertEquals(94, client.held(Decanting.COINS))
            }
        }
    }

    private companion object {
        val ENERGY = listOf(3022, 3020, 3018, 3016)
        val RANDOM = setOf("[bob_barter.lets_see]", "[bob_barter.two_plus_two]")
    }
}
