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
 * Slayer step 2 through the headless client: Vannaka's combat level gate, a new task with its tip, a task already held,
 * Aya's task and her talk about Turael, her swap of another master's task (which resets the streak), her pointer to a
 * higher master, and the combat level check turned off and on.
 */
class HeadlessSlayerMastersTest {
    private val ops = listOf("Talk-to", null, "Assignment", "Trade", "Rewards")
    private val vannaka = NpcType(SlayerMaster.VANNAKA.npc, "Vannaka", ops = ops, internalName = "slayer_master_3")
    private val aya = NpcType(SlayerMaster.AYA.npc, "Aya", ops = ops, internalName = "slayer_master_1_aya")

    private val cows = SlayerTask(6219, 6, "cows", "Cows")
    private val rats = SlayerTask(6216, 3, "rats", "Rats")

    // Aya gives only cows here and Vannaka only rats, each with the 242 cache's amounts for them.
    private val data = SlayerData(
        listOf(cows, rats),
        listOf(SlayerMasterTask(SlayerMaster.AYA.id, cows, 8, 15, 30), SlayerMasterTask(SlayerMaster.VANNAKA.id, rats, 7, 40, 80)),
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
            npcTypes = NpcTypes.of(vannaka, aya),
            npcSpawns = listOf(NpcSpawn(vannaka, 3224, 3218, wander = 0, source = "test"), NpcSpawn(aya, 3220, 3218, wander = 0, source = "test")),
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

    private fun HeadlessClient.combat(level: Int) {
        commandReply("setlevel all $level") { it.startsWith("Every skill") }
    }

    @Test
    fun `Vannaka turns a weak player away, then gives a task and its tip, and tells what is left of it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.npc(vannaka)
                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.says("[slayer_master.too_weak]")
                assertEquals(0, client.varp(Slayer.VARP_COUNT))

                client.combat(40)
                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                val line = client.saysStarting("[slayer_master.new_task ")
                val amount = Regex("amount=(\\d+) task=rats]").find(line)?.groupValues?.get(1)?.toInt() ?: error("no amount in $line")
                assertTrue(amount in 40..80, "amount $amount")
                assertEquals(3, client.varp(Slayer.VARP_TARGET))
                assertEquals(amount, client.varp(Slayer.VARP_COUNT))
                client.pick(1)
                client.says("[slayer_master.tips]")
                client.says("[slayer_tip.rats]")
                client.says("[slayer_master.tips_thanks]")

                client.npcOp(npc.index, SlayerMasters.TALK_TO)
                client.says("[slayer_master.greeting]")
                client.pick(1)
                client.says("[slayer_master.need_task]")
                client.says("[slayer_master.still_hunting_come_back task=rats amount=$amount]")
            }
        }
    }

    @Test
    fun `Aya gives a task from her list with no tip, and talks about Turael`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val npc = client.npc(aya)
                client.npcOp(npc.index, SlayerMasters.TALK_TO)
                client.says("[slayer_master.greeting]")
                client.pick(1)
                client.says("[slayer_master.need_task]")
                val line = client.saysStarting("[slayer_master.new_task ")
                assertTrue(line.endsWith(" task=cows]"), line)
                assertEquals(6, client.varp(Slayer.VARP_TARGET))
                assertTrue(client.varp(Slayer.VARP_COUNT) in 15..30)
                client.awaitTicks(2)
                assertTrue(client.dialogueView !is DialogueView.Menu, "no tips menu after Aya's task")

                client.npcOp(npc.index, SlayerMasters.TALK_TO)
                client.says("[slayer_master.greeting]")
                client.pick(4)
                for (key in listOf("about_turael", "condolences", "you_have", "thrill_seeker", "sorry", "hero")) client.says("[aya.$key]")
            }
        }
    }

    @Test
    fun `Aya swaps another master's task for one of his and resets the streak`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                val npc = client.npc(aya)
                client.commandReply("slayer task rats 50 vannaka") { it.startsWith("Task") }
                client.commandReply("slayer streak 7") { it.startsWith("Streak") }
                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.says("[slayer_master.still_hunting task=rats amount=50]")
                client.says("[aya.not_mine]")
                client.says("[aya.reset_streak streak=7 wilderness=0]")
                client.pick(1)
                client.saysStarting("[slayer_master.new_task ")
                assertEquals(6, client.varp(Slayer.VARP_TARGET))
                assertEquals("streak 0", client.commandReply("slayer") { it.contains("streak") }.substringAfter("; ").substringBefore(","))

                // Her own task she won't swap.
                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.saysStarting("[slayer_master.still_hunting task=cows ")
                client.awaitTicks(2)
                assertTrue((client.dialogueView as? DialogueView.Chat)?.text != "[aya.not_mine]")

                // Nor Spria's, even one not on her list (OSRS Wiki, Spria: her tasks can't be skipped through Turael).
                client.commandReply("slayer task rats 20 spria") { it.startsWith("Task") }
                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.says("[slayer_master.still_hunting task=rats amount=20]")
                client.awaitTicks(2)
                assertTrue(client.dialogueView == null, "no swap offered for Spria's task: ${client.dialogueView}")
                assertEquals(3, client.varp(Slayer.VARP_TARGET))
            }
        }
    }

    @Test
    fun `Aya points a strong player to a higher master`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dana").use { client ->
                val npc = client.npc(aya)
                client.combat(50)
                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.says("[slayer_master.strong_vannaka]")
                client.pick(2)
                client.says("[slayer_master.go_vannaka]")
                client.awaitTicks(2)
                assertEquals(0, client.varp(Slayer.VARP_COUNT))

                client.npcOp(npc.index, SlayerMasters.ASSIGNMENT)
                client.says("[slayer_master.strong_vannaka]")
                client.pick(1)
                client.says("[slayer_master.take_from_you]")
                client.saysStarting("[slayer_master.new_task ")
                assertEquals(6, client.varp(Slayer.VARP_TARGET))
            }
        }
    }

    @Test
    fun `the combat level check goes off and back on`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                val npc = client.npc(vannaka)
                client.combat(40)
                client.npcOp(npc.index, SlayerMasters.TALK_TO)
                client.says("[slayer_master.greeting]")
                client.pick(3)
                client.says("[slayer_master.difficulty]")
                client.says("[slayer_master.check_on]")
                client.pick(2)
                client.says("[slayer_master.stop_checking]")
                client.says("[slayer_master.check_off_now]")
                client.await("the check off") { if (client.varp(STREAK_VARP) and CHECK_BIT != 0) Unit else null }

                client.npcOp(npc.index, SlayerMasters.TALK_TO)
                client.says("[slayer_master.greeting]")
                client.pick(3)
                client.says("[slayer_master.difficulty]")
                client.says("[slayer_master.check_off]")
                client.pick(2)
                client.says("[slayer_master.not_too_tough]")
                client.says("[slayer_master.check_on_now]")
                client.await("the check on") { if (client.varp(STREAK_VARP) and CHECK_BIT == 0) Unit else null }
            }
        }
    }

    private companion object {
        /** slayer_tasks_completed_1, which holds the streak and the combat check's bit 29. */
        const val STREAK_VARP = 1077
        const val CHECK_BIT = 1 shl 29
    }
}
