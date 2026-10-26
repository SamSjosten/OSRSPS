package osrsps.cache

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import osrsps.game.PlayerVars
import osrsps.game.Quest
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
