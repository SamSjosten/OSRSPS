package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.Player
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.dialogue.ChatFont
import osrsps.map.CollisionMap

/** The diaries' varbits as the 242 cache defines them (osrs-dumps 2026-11-18b-rev242, config/dump.varbit). */
val DIARY_VARBITS: Map<Int, VarbitType> = mapOf(
    // started_lumbridge_diary, started_varrock_diary: achievement_diary (1188) bits 5 and 7.
    4453 to VarbitType(1188, 5, 5),
    4455 to VarbitType(1188, 7, 7),
    // varrock_diary_<tier>_complete: 1188 bits 30, 31, then 1189 bits 0, 1.
    4479 to VarbitType(1188, 30, 30),
    4480 to VarbitType(1188, 31, 31),
    4481 to VarbitType(1189, 0, 0),
    4482 to VarbitType(1189, 1, 1),
    // lumbridge_diary_<tier>_complete: 1189 bits 14 to 17.
    4495 to VarbitType(1189, 14, 14),
    4496 to VarbitType(1189, 15, 15),
    4497 to VarbitType(1189, 16, 16),
    4498 to VarbitType(1189, 17, 17),
    // lumbridge_<tier>_count: achievement_diary_taskcounts4 (1722), varrock_<tier>_count: ..5 (1723), 4 bits each.
    6311 to VarbitType(1722, 0, 3),
    6312 to VarbitType(1722, 4, 7),
    6313 to VarbitType(1722, 8, 11),
    6314 to VarbitType(1722, 12, 15),
    6319 to VarbitType(1723, 0, 3),
    6320 to VarbitType(1723, 4, 7),
    6321 to VarbitType(1723, 8, 11),
    6322 to VarbitType(1723, 12, 15),
    // varrock_<tier>_reward (achievement_diary_rewards): 1190 bits 20 to 23; lumbridge_<tier>_reward (..2): 1191 bits 4 to 7.
    4519 to VarbitType(1190, 20, 20),
    4520 to VarbitType(1190, 21, 21),
    4521 to VarbitType(1190, 22, 22),
    4522 to VarbitType(1190, 23, 23),
    4535 to VarbitType(1191, 4, 4),
    4536 to VarbitType(1191, 5, 5),
    4537 to VarbitType(1191, 6, 6),
    4538 to VarbitType(1191, 7, 7),
)

/** The diary table against the client's own task counts, and a task's bits, counts and flags as it is done and undone. */
class DiariesTest {
    private val world = World(intArrayOf(), CollisionMap(), varTypes = VarTypes(4_000, DIARY_VARBITS))
    private val diaries = Diaries(world, ChatFont.APPROXIMATE)

    private fun player() = Player(1, "alice", 3222, 3218, 0, VarTypes(4_000, DIARY_VARBITS))

    /** What [Diaries.complete] does short of the messages, which need a session: the bit, then the panel's varbits. */
    private fun Player.mark(task: DiaryTask) {
        vars[task.varp] = vars[task.varp] or (1 shl task.bit)
        diaries.refresh(this, task.area)
    }

    @Test
    fun `each tier has as many tasks as the side panel counts to`() {
        // The totals client proc diary_completion_info returns for Lumbridge (6) and Varrock (8).
        val panel = mapOf(DiaryArea.LUMBRIDGE to listOf(12, 12, 11, 6), DiaryArea.VARROCK to listOf(14, 13, 10, 5))
        for ((area, counts) in panel) {
            assertEquals(counts, DiaryTier.entries.map { area.tasks(it).size }, area.name)
            for (tier in DiaryTier.entries) assertEquals((1..counts[tier.ordinal]).toList(), area.tasks(tier).map { it.number })
        }
    }

    @Test
    fun `no two tasks share a bit`() {
        for (area in DiaryArea.entries) {
            val bits = area.tasks.map { it.varp to it.bit }
            assertEquals(bits.size, bits.toSet().size, area.name)
            assertTrue(area.tasks.all { it.bit in 0..31 })
        }
    }

    @Test
    fun `a done task's bit sets, the count and the started flag, and the last of a tier its flag`() {
        val player = player()
        player.mark(DiaryTask.LUMBRIDGE_EASY_5)
        assertTrue(diaries.done(player, DiaryTask.LUMBRIDGE_EASY_5))
        assertFalse(diaries.complete(player, DiaryTask.LUMBRIDGE_EASY_5), "a task is done once")
        assertEquals(1 shl 5, player.vars[1194])
        assertEquals(1, player.vars.getVarbit(6311))
        assertEquals(1, player.vars.getVarbit(4453))
        assertEquals(0, player.vars.getVarbit(4495))
        DiaryArea.LUMBRIDGE.tasks(DiaryTier.EASY).forEach { player.mark(it) }
        assertEquals(12, player.vars.getVarbit(6311))
        assertEquals(1, player.vars.getVarbit(4495))
        assertEquals(0, player.vars.getVarbit(4455), "Varrock not started")
        diaries.undo(player, DiaryTask.LUMBRIDGE_EASY_1)
        assertEquals(11, player.vars.getVarbit(6311))
        assertEquals(0, player.vars.getVarbit(4495))
    }

    @Test
    fun `resetting a task that remembers progress clears that progress too, and other resets leave it`() {
        val player = player()
        player.charges[Diaries.SOS_EMOTES_CHARGE] = 15
        player.charges[Diaries.SPOTTIER_BOUGHT_CHARGE] = 1
        diaries.undo(player, DiaryTask.VARROCK_EASY_1)
        assertEquals(15, player.charges[Diaries.SOS_EMOTES_CHARGE])
        assertEquals(1, player.charges[Diaries.SPOTTIER_BOUGHT_CHARGE])
        diaries.undo(player, DiaryTask.VARROCK_MEDIUM_5)
        assertEquals(0, player.charges[Diaries.SOS_EMOTES_CHARGE])
        diaries.undo(player, DiaryTask.VARROCK_HARD_1)
        assertEquals(0, player.charges[Diaries.SPOTTIER_BOUGHT_CHARGE])
    }

    @Test
    fun `the second varp holds the later tasks`() {
        val player = player()
        player.mark(DiaryTask.VARROCK_ELITE_5)
        assertEquals(1 shl 11, player.vars[1177])
        assertEquals(1, player.vars.getVarbit(6322))
        assertEquals(0, player.vars[1176])
    }

    @Test
    fun `the journal strikes every line of a done task and lists each tier`() {
        val player = player().apply { showDialogueKeys = true }
        player.mark(DiaryTask.VARROCK_EASY_2)
        val lines = diaries.journalLines(player, DiaryArea.VARROCK)
        assertEquals("[diary_varrock.title]", lines[0])
        assertEquals("<str>[diary_varrock_tasks.easy_2]", lines.single { "easy_2]" in it })
        assertEquals("[diary_varrock_tasks.easy_1]", lines.single { "easy_1]" in it })
        assertEquals(4, lines.count { it.startsWith("<col=") })
        assertEquals(1 + 4 * 2 + 42, lines.size)
    }
}
