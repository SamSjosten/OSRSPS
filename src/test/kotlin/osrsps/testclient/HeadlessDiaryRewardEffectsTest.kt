package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.ExplorersRing
import osrsps.game.content.Twiggy
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * The diary rewards' effects through the headless client: the Explorer's ring 2's three teleports a day to the cabbage
 * patch and then no more, its restore through Functions, and Check counting what's left; Twiggy O'Korn's menu, back
 * after "Who are you?", and the talk ending after the cape.
 */
class HeadlessDiaryRewardEffectsTest {
    private val ring2 = ExplorersRing.TIERS[1].obj
    private val twiggy = NpcType(Twiggy.ID, "Twiggy O'Korn", ops = listOf("Talk-to", null, null, null, null))
    private val objs = ObjTypes.of(ObjType(ring2, "Explorer's ring 2", iops = listOf("Wear", "Check", "Teleport", "Functions", "Drop")))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(ExplorersRing.CABBAGE_PATCH.first shr 6, ExplorersRing.CABBAGE_PATCH.second shr 6)
            },
            objTypes = objs, npcTypes = NpcTypes.of(twiggy),
            npcSpawns = listOf(NpcSpawn(twiggy, 3224, 3218, wander = 0, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `ring 2 teleports three times a day, restores through Functions and counts what's left`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $ring2 1")
                val slot = client.slotOf(ring2)
                for (left in 2 downTo 0) {
                    client.forgetPackets()
                    client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, TELEPORT_OP, slot, ring2)
                    client.seen("[explorers_ring.teleports_left left=$left]")
                    client.await("at the cabbage patch") {
                        client.position?.takeIf { it.x == ExplorersRing.CABBAGE_PATCH.first && it.z == ExplorersRing.CABBAGE_PATCH.second }
                    }
                    client.command("tele 3222 3218 0")
                    client.await("back") { client.position?.takeIf { it.x == 3222 && it.z == 3218 } }
                }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, TELEPORT_OP, slot, ring2)
                client.seen("[explorers_ring.teleport_none]")

                server.world.playerNamed("alice")!!.runEnergy = 1_000
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, FUNCTIONS_OP, slot, ring2)
                client.awaitDialogue("the functions menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.seen("[explorers_ring.energy left=2]")
                assertTrue(server.world.playerNamed("alice")!!.runEnergy >= 6_000)

                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, CHECK_OP, slot, ring2)
                client.seen("[explorers_ring.check_teleports energy=2 teleports=0 alchemy=30]")
            }
        }
    }

    @Test
    fun `Twiggy's menu comes back after who she is, and the cape ends the talk`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val npc = client.await("Twiggy in view") { client.npcs().firstOrNull { it.id == Twiggy.ID } }
                client.npcOp(npc.index, Twiggy.TALK_TO)
                client.awaitDialogue("hey there") { it is DialogueView.Chat && it.text == "[twiggy.hey_there]" }
                client.continueDialogue()
                // The chocolate is a 1 in 50 chance: take it if it comes.
                val first = client.awaitDialogue("her menu or chocolate") { it is DialogueView.Menu || (it is DialogueView.Chat && it.text == "[twiggy.chocolate]") }
                if (first is DialogueView.Chat) {
                    client.continueDialogue()
                    client.awaitDialogue("thanks") { it is DialogueView.Chat && it.text == "[twiggy.wow_thanks]" }
                    client.continueDialogue()
                }
                val menu = client.awaitDialogue("her menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[twiggy.who_are_you]", "[twiggy.nice_cape]", "[twiggy.goodbye]"), menu.options)
                client.chooseOption(1)
                for (text in listOf("[twiggy.who_are_you]", "[twiggy.mistress]", "[twiggy.likewise]")) {
                    client.awaitDialogue(text) { it is DialogueView.Chat && it.text == text }
                    client.continueDialogue()
                }
                client.awaitDialogue("her menu again") { it is DialogueView.Menu }
                client.chooseOption(2)
                for (text in listOf("[twiggy.nice_cape]", "[twiggy.cape]")) {
                    client.awaitDialogue(text) { it is DialogueView.Chat && it.text == text }
                    client.continueDialogue()
                }
                client.await("the talk over") { Unit.takeIf { client.dialogueView == null } }
            }
        }
    }

    private companion object {
        const val INV = 93

        /** iop2 (Check) is button op 3, iop3 (Teleport) 4, iop4 (Functions) 6 ([InventoryTab]). */
        const val CHECK_OP = 3
        const val TELEPORT_OP = 4
        const val FUNCTIONS_OP = 6
    }
}
