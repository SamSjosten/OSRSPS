package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Skill
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
import osrsps.game.skill.SlayerUnlock
import osrsps.map.CollisionMap

/**
 * Slayer step 2's task eligibility through Vannaka's Assignment: a task's combat level (and the check turned off), its
 * Slayer level, the reward it needs bought, the reward that blocks it, and an extend's amounts. Vannaka's list here
 * holds one task for each gate, and each case leaves exactly one of them open, so the task given shows the gate at work.
 */
class HeadlessSlayerEligibilityTest {
    private val vannaka = NpcType(SlayerMaster.VANNAKA.npc, "Vannaka", ops = listOf("Talk-to", null, "Assignment", "Trade", "Rewards"), internalName = "slayer_master_3")

    // Made-up rows and bits; the gates are what the cache's rows carry (min_comlevel, min_stat_requirement_all,
    // task_unlock, block_unlock, extension_min_max).
    private val strong = SlayerTask(7001, 21, "strong things", "Strong things", minCombat = 60, blockedBy = BLOCK_STRONG)
    private val skilled = SlayerTask(7002, 22, "skilled things", "Skilled things", statsAll = listOf(Skill.SLAYER to 50), blockedBy = BLOCK_SKILLED)
    private val unlockable = SlayerTask(7003, 23, "unlocked things", "Unlocked things")
    private val blockable = SlayerTask(7004, 24, "blocked things", "Blocked things", blockedBy = BLOCK_BLOCKABLE)
    private val extendable = SlayerTask(7005, 25, "extended things", "Extended things", blockedBy = BLOCK_EXTENDABLE, extensions = mapOf(EXTEND to 200..200))

    private val data = SlayerData(
        listOf(strong, skilled, unlockable, blockable, extendable),
        listOf(
            SlayerMasterTask(SlayerMaster.VANNAKA.id, strong, 5, 30, 30),
            SlayerMasterTask(SlayerMaster.VANNAKA.id, skilled, 5, 31, 31),
            SlayerMasterTask(SlayerMaster.VANNAKA.id, unlockable, 5, 32, 32, unlockedBy = UNLOCK),
            SlayerMasterTask(SlayerMaster.VANNAKA.id, blockable, 5, 33, 33),
            SlayerMasterTask(SlayerMaster.VANNAKA.id, extendable, 5, 40, 40),
        ),
        listOf(
            SlayerUnlock(UNLOCK, 1, 100), SlayerUnlock(BLOCK_STRONG, 2, 100), SlayerUnlock(BLOCK_SKILLED, 3, 100),
            SlayerUnlock(BLOCK_BLOCKABLE, 4, 100), SlayerUnlock(BLOCK_EXTENDABLE, 5, 100), SlayerUnlock(EXTEND, 6, 100),
        ),
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
            npcTypes = NpcTypes.of(vannaka), npcSpawns = listOf(NpcSpawn(vannaka, 3224, 3218, wander = 0, source = "test")),
            slayerData = data, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.unlocks(vararg bits: Int) {
        val value = bits.fold(0) { v, bit -> v or (1 shl bit) }
        commandReply("varp ${Slayer.UNLOCK_VARPS[0]} $value") { it.startsWith("Varp ") }
    }

    /** Vannaka's Assignment: the task id and amount he gives, then the task taken away again for the next case. */
    private fun HeadlessClient.assignment(npc: Int): Pair<Int, Int> {
        npcOp(npc, SlayerMasters.ASSIGNMENT)
        awaitDialogue("a new task") { it is DialogueView.Chat && it.text.startsWith("[slayer_master.new_task ") }
        continueDialogue()
        awaitDialogue("the tips menu") { it is DialogueView.Menu }
        val given = varp(Slayer.VARP_TARGET) to varp(Slayer.VARP_COUNT)
        chooseOption(2)
        awaitTicks(2)
        commandReply("slayer clear") { it.startsWith("No task") }
        return given
    }

    @Test
    fun `each gate keeps its task out until it is met`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("kira").use { client ->
                val npc = client.await("Vannaka in view") { client.npcs().firstOrNull { it.id == vannaka.id } }.index
                // Combat about 50 and Slayer 40: the strong and skilled tasks are out, the unlockable one isn't bought.
                client.commandReply("setlevel all 40") { it.startsWith("Every skill") }

                // The blockable one blocked: only the extendable one, at its own amount.
                client.unlocks(4)
                assertEquals(extendable.id to 40, client.assignment(npc))

                // Its extend bought: the extend's amount.
                client.unlocks(4, 6)
                assertEquals(extendable.id to 200, client.assignment(npc))

                // Everything but the strong task blocked, and the combat check off: the strong task, below its level.
                client.commandReply("varbit ${Slayer.VARBIT_COMBAT_CHECK_OFF} 1") { it.startsWith("Varbit ") }
                client.unlocks(3, 4, 5)
                assertEquals(strong.id to 30, client.assignment(npc))

                // The check back on and the strong task blocked too, at Slayer 50: the skilled task.
                client.commandReply("varbit ${Slayer.VARBIT_COMBAT_CHECK_OFF} 0") { it.startsWith("Varbit ") }
                client.commandReply("setlevel slayer 50") { it.contains("set to level 50") }
                client.unlocks(2, 4, 5)
                assertEquals(skilled.id to 31, client.assignment(npc))

                // Back to Slayer 40 with the unlock bought: the unlocked task.
                client.commandReply("setlevel slayer 40") { it.contains("set to level 40") }
                client.unlocks(1, 2, 4, 5)
                assertEquals(unlockable.id to 32, client.assignment(npc))
            }
        }
    }

    private companion object {
        const val UNLOCK = 9001
        const val BLOCK_STRONG = 9002
        const val BLOCK_SKILLED = 9003
        const val BLOCK_BLOCKABLE = 9004
        const val BLOCK_EXTENDABLE = 9005
        const val EXTEND = 9006
    }
}
