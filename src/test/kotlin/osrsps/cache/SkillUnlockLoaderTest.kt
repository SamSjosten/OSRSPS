package osrsps.cache

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.Skill

class SkillUnlockLoaderTest {
    /** A skill_features row as the 241 cache has it: table 213 as the var int `d5 01`, then (stat, level, n). */
    private fun row(stat: Int, level: Int) = ByteArrayOutputStream().also { out ->
        DataOutputStream(out).apply {
            writeByte(4); writeByte(0xD5); writeByte(0x01)
            writeByte(3); writeByte(11)
            writeByte(3); writeByte(3); repeat(3) { writeByte(0) }; writeByte(1); writeInt(stat); writeInt(level); writeInt(2)
            writeByte(255)
            writeByte(0)
        }
    }.toByteArray()

    @Test
    fun `a table id over 127 reads lowest seven bits first`() {
        assertEquals(213, DbRows.decode(row(8, 72)).table)
    }

    @Test
    fun `unlock levels by skill`() {
        val unlocks = SkillUnlockLoader.build(listOf(DbRows.decode(row(8, 72)), DbRows.decode(row(14, 30))))
        assertTrue(unlocks.unlocks(Skill.WOODCUTTING, 72))
        assertTrue(unlocks.unlocks(Skill.MINING, 30))
        assertFalse(unlocks.unlocks(Skill.WOODCUTTING, 71))
        assertFalse(unlocks.unlocks(Skill.FISHING, 72))
    }
}
