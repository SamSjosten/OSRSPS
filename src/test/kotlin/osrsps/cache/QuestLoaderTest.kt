package osrsps.cache

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import osrsps.game.PlayerVars
import osrsps.game.Quest
import osrsps.game.QuestKind
import osrsps.game.QuestVar
import osrsps.game.Quests
import osrsps.game.VarTypes
import osrsps.game.VarbitType

class QuestLoaderTest {
    @Test
    fun `decodes a quest db row`() {
        val row = bytes {
            writeByte(4); writeByte(0) // table 0
            writeByte(3); writeByte(20)
            writeByte(2); writeByte(1); writeByte(36); writeByte(1); writeBytes("Cook's Assistant"); writeByte(0)
            writeByte(9); writeByte(3); repeat(3) { writeByte(0) }; writeByte(1); writeInt(2001); writeInt(1); writeInt(4)
            writeByte(19); writeByte(1); writeByte(0); writeByte(1); writeInt(2)
            writeByte(255)
            writeByte(0)
        }
        val decoded = QuestLoader.decodeDbRow(row)
        assertEquals(0, decoded.table)
        assertEquals(listOf<Any>("Cook's Assistant"), decoded.columns[2])
        assertEquals(listOf<Any>(2001, 1, 4), decoded.columns[9])
        assertEquals(listOf<Any>(2), decoded.columns[19])
    }

    @Test
    fun `reads the progress var for each case of the switch`() {
        val decoded = QuestLoader.decodeProgressSwitch(progressScript(getVarbitOp = 25))
        assertEquals(mapOf(17 to QuestVar.Varp(29), 117 to QuestVar.Varbit(1850)), decoded)
    }

    @Test
    fun `fails loudly when a case is not a plain var read`() {
        assertFailsWith<IllegalStateException> { QuestLoader.decodeProgressSwitch(progressScript(getVarbitOp = 0)) }
    }

    @Test
    fun `skips unreleased quests and quests with no progress var`() {
        fun row(name: String, releaseType: Int?, end: Int, points: Int) = DbRow(
            QuestLoader.QUEST_TABLE,
            buildMap {
                put(2, listOf(name))
                if (releaseType != null) put(3, listOf(releaseType))
                put(17, listOf(points))
                put(19, listOf(end))
            },
        )
        val rows = mapOf(
            17 to row("Cook's Assistant", null, 2, 1),
            5192 to row(".", 0, 28, 1),
            16972 to row("Crab Quest", 2, 63, 1),
            99 to row("No var", null, 4, 1),
        )
        val quests = QuestLoader.buildQuests(rows, mapOf(17 to QuestVar.Varp(29), 5192 to QuestVar.Varbit(1), 16972 to QuestVar.Varbit(2)))
        assertEquals(listOf(Quest(17, "Cook's Assistant", QuestVar.Varp(29), 2, 1)), quests)
    }

    @Test
    fun `completing sets every var to its end, the highest one when shared, and the quest points`() {
        val vars = PlayerVars(VarTypes(3_000, mapOf(1850 to VarbitType(2_000, 4, 7))))
        vars[63] = 9 // already past its end: left alone
        Quests(
            listOf(
                Quest(17, "Cook's Assistant", QuestVar.Varp(29), 2, 1),
                Quest(125, "Rune Mysteries", QuestVar.Varp(63), 6, 1),
                Quest(117, "Recipe for Disaster", QuestVar.Varbit(1850), 5, 0),
                Quest(2307, "Recipe for Disaster - Another Cook's Quest", QuestVar.Varbit(1850), 2, 1),
            ),
        ).completeAll(vars)
        assertEquals(2, vars[29])
        assertEquals(9, vars[63])
        assertEquals(5, vars.getVarbit(1850))
        assertEquals(3, vars[Quests.VARP_QUEST_POINTS])
    }

    @Test
    fun `tells quests, miniquests and Recipe for Disaster's parts apart`() {
        val rows = mapOf(
            17 to DbRow(QuestLoader.QUEST_TABLE, mapOf(2 to listOf("A quest"), 4 to listOf(0), 17 to listOf(1), 19 to listOf(2))),
            30 to DbRow(QuestLoader.QUEST_TABLE, mapOf(2 to listOf("A miniquest"), 4 to listOf(1), 17 to listOf(0), 19 to listOf(5))),
            40 to DbRow(QuestLoader.QUEST_TABLE, mapOf(2 to listOf("A part"), 4 to listOf(0), 17 to listOf(1), 19 to listOf(3), 21 to listOf(117))),
        )
        val quests = QuestLoader.buildQuests(rows, mapOf(17 to QuestVar.Varp(29), 30 to QuestVar.Varp(30), 40 to QuestVar.Varbit(1850)))
        assertEquals(listOf(QuestKind.QUEST, QuestKind.MINIQUEST, QuestKind.SUBQUEST), quests.map { it.kind })
    }

    @Test
    fun `the quest list footer counts every quest finished and every quest point earned`() {
        val counters = listOf(
            Quests.VARBIT_QP_MAX, Quests.VARBIT_QUESTS_COMPLETED, Quests.VARBIT_QUESTS_TOTAL,
            Quests.VARBIT_SUBQUESTS_COMPLETED, Quests.VARBIT_MINIQUESTS_COMPLETED,
        )
        // Bit ranges as the 241 cache has them: qp_max 0-9 and quests_completed_count 20-28 of varp 904,
        // quests_total_count 0-8 and subquests_completed_count 17-25 of varp 2920, miniquests_completed_count 0-8 of 2921.
        val vars = PlayerVars(
            VarTypes(
                3_000,
                mapOf(
                    Quests.VARBIT_QP_MAX to VarbitType(904, 0, 9),
                    Quests.VARBIT_QUESTS_COMPLETED to VarbitType(904, 20, 28),
                    Quests.VARBIT_QUESTS_TOTAL to VarbitType(2920, 0, 8),
                    Quests.VARBIT_SUBQUESTS_COMPLETED to VarbitType(2920, 17, 25),
                    Quests.VARBIT_MINIQUESTS_COMPLETED to VarbitType(2921, 0, 8),
                ),
            ),
        )
        Quests(
            listOf(
                Quest(17, "A", QuestVar.Varp(29), 2, 1),
                Quest(18, "B", QuestVar.Varp(30), 2, 3),
                Quest(19, "C", QuestVar.Varp(31), 2, 2, QuestKind.SUBQUEST),
                Quest(20, "D", QuestVar.Varp(32), 2, 0, QuestKind.MINIQUEST),
            ),
        ).completeAll(vars)
        assertEquals(listOf(6, 2, 2, 1, 1), counters.map { vars.getVarbit(it) })
        assertEquals(6, vars[Quests.VARP_QUEST_POINTS])
        // Points earned never exceed points there are, so the tab never shows more than the maximum again.
        assertEquals(vars[Quests.VARP_QUEST_POINTS], vars.getVarbit(Quests.VARBIT_QP_MAX))
    }

    /** `iload 0; switch; jump; get_varp 29; return; <op> 1850; return`, cases 17 -> get_varp and 117 -> the other. */
    private fun progressScript(getVarbitOp: Int): ByteArray = bytes {
        writeByte(0) // no name
        writeShort(33); writeInt(0)
        writeShort(60); writeInt(0)
        writeShort(6); writeInt(4)
        writeShort(1); writeInt(29)
        writeShort(21); writeByte(0)
        writeShort(getVarbitOp); writeInt(1850)
        writeShort(21); writeByte(0)
        writeInt(7)
        repeat(6) { writeShort(if (it == 3) 1 else 0) }
        writeByte(1); writeShort(2)
        writeInt(17); writeInt(1)
        writeInt(117); writeInt(3)
        writeShort(1 + 2 + 2 * 8)
    }

    private fun bytes(block: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { DataOutputStream(it).use(block) }.toByteArray()
}
