package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Quests
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.SlayerMasters
import osrsps.game.content.SlayerRewards
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Slayer
import osrsps.game.skill.SlayerData
import osrsps.game.skill.SlayerMaster
import osrsps.game.skill.SlayerMasterTask
import osrsps.game.skill.SlayerTask
import osrsps.game.skill.SlayerUnlock
import osrsps.map.CollisionMap

/**
 * Slayer step 4 through the headless client: the rewards screen opened from Vannaka, an unlock bought and a refundable
 * one turned off, too few points, "Extend everything", a task cancelled, and a task blocked so Vannaka no longer gives it,
 * then unblocked.
 */
class HeadlessSlayerRewardsTest {
    private val ops = listOf("Talk-to", null, "Assignment", "Trade", "Rewards")
    private val vannaka = NpcType(SlayerMaster.VANNAKA.npc, "Vannaka", ops = ops, internalName = "slayer_master_3")

    private val cows = SlayerTask(6219, 6, "cows", "Cows")
    private val rats = SlayerTask(6216, 3, "rats", "Rats")

    // The 242 cache's rows for these unlocks: Gargoyle smasher (bit 4, 100), Bigger and Badder (bit 35, 50, refundable)
    // and two extends, Need more darkness (bit 8, 100) and Ankou very much (bit 9, 100).
    private val data = SlayerData(
        listOf(cows, rats),
        listOf(SlayerMasterTask(SlayerMaster.VANNAKA.id, rats, 7, 40, 80), SlayerMasterTask(SlayerMaster.VANNAKA.id, cows, 7, 40, 80)),
        listOf(
            SlayerUnlock(6399, 4, 100), SlayerUnlock(6428, 35, 50, refundable = true),
            SlayerUnlock(6400, 8, 100, list = 1), SlayerUnlock(6401, 9, 100, list = 1),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5),
            // The 242 cache's Slayer varbits, Vannaka's block slots (slayer_rewards_blocked_5 and _6, the diary slot in
            // _diary_1) and slayer_master_in_focus (slayer_misc).
            varTypes = VarTypes(
                20_000,
                mapOf(
                    Slayer.VARBIT_MASTER to VarbitType(661, 2, 5), Slayer.VARBIT_POINTS to VarbitType(661, 6, 22),
                    Slayer.VARBIT_STREAK to VarbitType(1077, 0, 13), Slayer.VARBIT_COMBAT_CHECK_OFF to VarbitType(1077, 29, 29),
                    Slayer.VARBIT_WILDERNESS_STREAK to VarbitType(1565, 0, 13),
                    17824 to VarbitType(4833, 0, 7), 17825 to VarbitType(4833, 8, 15), 17826 to VarbitType(4833, 16, 23),
                    17827 to VarbitType(4833, 24, 31), 17828 to VarbitType(4834, 0, 7), 17829 to VarbitType(4834, 8, 15),
                    17862 to VarbitType(4842, 16, 23), SlayerRewards.VARBIT_MASTER_IN_FOCUS to VarbitType(4844, 0, 5),
                ),
            ),
            npcTypes = NpcTypes.of(vannaka),
            npcSpawns = listOf(NpcSpawn(vannaka, 3224, 3218, wander = 0, source = "test")),
            slayerData = data, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.open(): Int {
        val npc = await("Vannaka in view") { npcs().firstOrNull { it.id == vannaka.id } }
        npcOp(npc.index, SlayerRewards.REWARDS)
        await("the rewards screen") { if (SlayerRewards.INTERFACE in openInterfaces()) Unit else null }
        await("the confirm box's op") { if (opEnabled(SlayerRewards.INTERFACE, SlayerRewards.CONFIRM_BUTTON, SlayerRewards.BLOCK, 1)) Unit else null }
        return npc.index
    }

    private fun HeadlessClient.confirm(child: Int) = ifButton(SlayerRewards.INTERFACE, SlayerRewards.CONFIRM_BUTTON, 1, child)

    private fun HeadlessClient.points() = commandReply("slayer") { it.contains("points") }.substringAfterLast(", ").substringBefore(" points").toInt()

    private fun HeadlessClient.awaitVarp(varp: Int, what: String, test: (Int) -> Boolean) = await(what) { if (test(varp(varp))) Unit else null }

    @Test
    fun `unlocks are bought with points, a refundable one turned off, and Extend everything buys the rest`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.commandReply("slayer points 150") { it.startsWith("Streak") }
                client.open()
                assertEquals(SlayerMaster.VANNAKA.id, client.varp(4844) and 0x3F)

                client.confirm(4)
                client.awaitVarp(Slayer.UNLOCK_VARPS[0], "Gargoyle smasher bought") { it and (1 shl 4) != 0 }
                assertEquals(50, client.points())

                client.confirm(35)
                client.awaitVarp(Slayer.UNLOCK_VARPS[1], "Bigger and Badder bought") { it and (1 shl 3) != 0 }
                assertEquals(0, client.points())
                client.confirm(35)
                client.awaitVarp(Slayer.UNLOCK_VARPS[1], "Bigger and Badder off") { it and (1 shl 3) == 0 }
                assertEquals(0, client.points())

                client.confirm(SlayerRewards.EXTEND_ALL)
                client.awaitMessage("[slayer_rewards.not_enough_points cost=200]") { it == "[slayer_rewards.not_enough_points cost=200]" }
                client.commandReply("slayer points 200") { it.startsWith("Streak") }
                client.confirm(SlayerRewards.EXTEND_ALL)
                client.awaitVarp(Slayer.UNLOCK_VARPS[0], "both extends bought") { it and (3 shl 8) == 3 shl 8 }
                assertEquals(0, client.points())
            }
        }
    }

    @Test
    fun `a task is cancelled for 30 points, and a blocked task is no longer given until unblocked`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.commandReply("varp ${Quests.VARP_QUEST_POINTS} 100") { true }
                client.commandReply("setlevel all 40") { it.startsWith("Every skill") }
                client.commandReply("slayer points 200") { it.startsWith("Streak") }
                client.commandReply("slayer task rats 50 vannaka") { it.startsWith("Task") }
                val npc = client.open()
                assertEquals(3, client.varp(SlayerRewards.VARP_IF_TASK))
                assertEquals(50, client.varp(SlayerRewards.VARP_IF_COUNT))

                client.confirm(SlayerRewards.CANCEL)
                client.awaitVarp(Slayer.VARP_COUNT, "the task cancelled") { it == 0 }
                assertEquals(170, client.points())
                client.confirm(SlayerRewards.CANCEL)
                client.awaitMessage("[slayer_rewards.no_task]") { it == "[slayer_rewards.no_task]" }

                client.commandReply("slayer task rats 50 vannaka") { it.startsWith("Task") }
                client.confirm(SlayerRewards.BLOCK)
                client.awaitVarp(4833, "rats in Vannaka's first block slot") { it and 0xFF == 3 }
                assertEquals(0, client.varp(Slayer.VARP_COUNT))
                assertEquals(110, client.points())

                // Vannaka gives only cows now.
                client.closeModal()
                repeat(3) {
                    client.npcOp(npc, SlayerMasters.ASSIGNMENT)
                    val line = client.awaitDialogue("a new task") { it is DialogueView.Chat && it.text.startsWith("[slayer_master.new_task ") }
                    assertTrue((line as DialogueView.Chat).text.endsWith(" task=cows]"), line.text)
                    client.continueDialogue()
                    client.awaitDialogue("the tips menu") { it is DialogueView.Menu }
                    client.chooseOption(2)
                    client.awaitDialogue("okay great") { it is DialogueView.Chat }
                    client.continueDialogue()
                    client.commandReply("slayer clear") { it.startsWith("No task") }
                }

                client.open()
                client.confirm(SlayerRewards.UNBLOCK[0])
                client.awaitVarp(4833, "Vannaka's first block slot empty") { it and 0xFF == 0 }
            }
        }
    }
}
