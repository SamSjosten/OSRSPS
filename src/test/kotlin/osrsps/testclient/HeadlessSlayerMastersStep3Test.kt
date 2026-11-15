package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.SlayerMasters
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Slayer
import osrsps.game.skill.SlayerData
import osrsps.game.skill.SlayerMaster
import osrsps.game.skill.SlayerMasterTask
import osrsps.game.skill.SlayerTask
import osrsps.map.CollisionMap

/**
 * Slayer step 3 through the headless client: Mazchna's pointer to Chaeldar, her task, tip and still-hunting line;
 * Chaeldar's task; Steve's task with no tip and his talk about Nieve; Kuradal's Slayer level gate and her talk about the
 * Slayer cape; and a master's offer to cancel a task the player is no longer fit for.
 */
class HeadlessSlayerMastersStep3Test {
    private val ops = listOf("Talk-to", null, "Assignment", "Trade", "Rewards")
    private val mazchna = NpcType(SlayerMaster.MAZCHNA.npc, "Mazchna", ops = ops, internalName = "slayer_master_2_mazchna")
    private val chaeldar = NpcType(SlayerMaster.CHAELDAR.npc, "Chaeldar", ops = ops, internalName = "slayer_master_4")
    private val steve = NpcType(SlayerMaster.STEVE.npc, "Steve", ops = ops, internalName = "slayer_master_steve")
    private val kuradal = NpcType(SlayerMaster.KURADAL.npc, "Kuradal", ops = ops, internalName = "slayer_master_5_kuradal")
    private val vannaka = NpcType(SlayerMaster.VANNAKA.npc, "Vannaka", ops = ops, internalName = "slayer_master_3")

    private val ghouls = SlayerTask(6236, 23, "ghouls", "Ghouls", minCombat = 25)
    private val gargoyles = SlayerTask(6259, 46, "gargoyles", "Gargoyles", minCombat = 80)

    // Each master gives ghouls here (the 242 cache's amounts for Mazchna's), and Vannaka gargoyles too.
    private val data = SlayerData(
        listOf(ghouls, gargoyles),
        listOf(SlayerMaster.MAZCHNA, SlayerMaster.CHAELDAR, SlayerMaster.STEVE, SlayerMaster.KURADAL, SlayerMaster.VANNAKA)
            .map { SlayerMasterTask(it.id, ghouls, 7, 10, 20) } + SlayerMasterTask(SlayerMaster.VANNAKA.id, gargoyles, 5, 40, 80),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5),
            // The 242 cache's Slayer varbits.
            varTypes = VarTypes(
                6_000,
                mapOf(
                    Slayer.VARBIT_MASTER to VarbitType(661, 2, 5), Slayer.VARBIT_POINTS to VarbitType(661, 6, 22),
                    Slayer.VARBIT_STREAK to VarbitType(1077, 0, 13), Slayer.VARBIT_COMBAT_CHECK_OFF to VarbitType(1077, 29, 29),
                    Slayer.VARBIT_WILDERNESS_STREAK to VarbitType(1565, 0, 13),
                ),
            ),
            npcTypes = NpcTypes.of(mazchna, chaeldar, steve, kuradal, vannaka),
            npcSpawns = listOf(
                NpcSpawn(mazchna, 3220, 3218, wander = 0, source = "test"), NpcSpawn(chaeldar, 3224, 3218, wander = 0, source = "test"),
                NpcSpawn(steve, 3220, 3222, wander = 0, source = "test"), NpcSpawn(kuradal, 3224, 3222, wander = 0, source = "test"),
                NpcSpawn(vannaka, 3222, 3224, wander = 0, source = "test"),
            ),
            slayerData = data, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.npc(type: NpcType) = await("${type.name} in view") { npcs().firstOrNull { it.id == type.id } }

    private fun HeadlessClient.says(text: String) = awaitDialogue(text) { it is DialogueView.Chat && it.text == text }.also { continueDialogue() }

    private fun HeadlessClient.saysStarting(prefix: String) =
        awaitDialogue(prefix) { it is DialogueView.Chat && it.text.startsWith(prefix) }.let { (it as DialogueView.Chat).text }.also { continueDialogue() }

    private fun HeadlessClient.pick(option: Int) {
        awaitDialogue("a menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    private fun HeadlessClient.level(skill: String, level: Int) {
        commandReply("setlevel $skill $level") { it.contains("set to level") }
    }

    @Test
    fun `Mazchna points a strong player to Chaeldar, then gives a task with a tip and her own still-hunting line`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.npc(mazchna)
                client.level("all", 70)
                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.says("[slayer_master.strong_chaeldar]")
                client.pick(1)
                client.says("[slayer_master.take_from_you]")
                val line = client.saysStarting("[slayer_master.new_task ")
                assertTrue(line.endsWith(" task=ghouls]"), line)
                assertEquals(23, client.varp(Slayer.VARP_TARGET))
                client.pick(1)
                client.says("[slayer_master.tips]")
                client.says("[slayer_tip.ghouls]")
                client.says("[slayer_master.tips_thanks]")

                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.says("[mazchna.still_hunting task=ghouls amount=${client.varp(Slayer.VARP_COUNT)}]")
            }
        }
    }

    @Test
    fun `Mazchna talks about Lucien, and Chaeldar gives a task with a tip`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.level("all", 70)
                client.npcOp(client.npc(mazchna).index, SlayerMasters.TALK_TO)
                client.says("[slayer_master.greeting]")
                client.pick(4)
                for (key in listOf("about_lucien", "reckoning", "so_will_i", "side_by_side")) client.says("[mazchna.$key]")

                client.npcOp(client.npc(chaeldar).index, SlayerMasters.ASSIGNMENT)
                client.saysStarting("[slayer_master.new_task ")
                assertEquals(SlayerMaster.CHAELDAR.id, client.varp(SLAYER_VARP_661) shr 2 and 0xF)
                client.pick(2)
                client.says("[slayer_master.okay_great]")
                client.says("[slayer_master.good_luck]")
            }
        }
    }

    @Test
    fun `Steve gives a task with no tip and talks about Nieve`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                val npc = client.npc(steve)
                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.says("[slayer_master.too_weak]")

                client.level("all", 85)
                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.saysStarting("[slayer_master.new_task ")
                client.awaitTicks(2)
                assertTrue(client.dialogueView !is DialogueView.Menu, "no tips menu after Steve's task")

                client.npcOp(npc.index, SlayerMasters.TALK_TO)
                client.says("[slayer_master.greeting]")
                client.pick(4)
                for (key in listOf("new_master", "nieve_was", "no_shield", "how_sad")) client.says("[steve.$key]")
            }
        }
    }

    @Test
    fun `Kuradal wants Slayer 50 as well as combat 100, and talks about the Slayer cape below 99`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dana").use { client ->
                val npc = client.npc(kuradal)
                client.level("all", 98)
                client.level("slayer", 49)
                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.says("[slayer_master.too_weak]")

                client.level("slayer", 50)
                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.saysStarting("[slayer_master.new_task ")
                client.pick(2)
                client.says("[slayer_master.okay_great]")

                client.npcOp(npc.index, SlayerMasters.TALK_TO)
                client.says("[slayer_master.greeting]")
                client.pick(4)
                for (key in listOf("skillcape", "skillcape_about", "more_training")) client.says("[kuradal.$key]")
            }
        }
    }

    @Test
    fun `a master offers to cancel a task the player is no longer fit for, keeping the streak`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                val npc = client.npc(vannaka)
                client.level("all", 40)
                client.commandReply("slayer task gargoyles 50 vannaka") { it.startsWith("Task") }
                client.commandReply("slayer streak 4") { it.startsWith("Streak") }

                // Kept: "No, thanks".
                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.says("[slayer_master.still_hunting_with task=gargoyles amount=50]")
                client.says("[slayer_master.not_suitable]")
                client.pick(2)
                client.says("[slayer_master.no_try]")
                client.says("[slayer_master.good_luck_with_that]")
                assertEquals(50, client.varp(Slayer.VARP_COUNT))

                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.says("[slayer_master.still_hunting_with task=gargoyles amount=50]")
                client.says("[slayer_master.not_suitable]")
                client.pick(1)
                client.says("[slayer_master.yes_cancel]")
                client.says("[slayer_master.cancelled]")
                assertEquals(0, client.varp(Slayer.VARP_COUNT))
                assertEquals("streak 4", client.commandReply("slayer") { it.contains("streak") }.substringAfter("; ").substringBefore(","))
            }
        }
    }

    private companion object {
        /** The varp holding slayer_master (bits 2 to 5) and the points. */
        const val SLAYER_VARP_661 = 661
    }
}
