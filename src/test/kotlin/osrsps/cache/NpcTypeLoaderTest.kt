package osrsps.cache

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import osrsps.game.NpcType

class NpcTypeLoaderTest {
    /** Writes an NPC type the way the cache stores it. These are made-up NPCs, not copies of the cache's. */
    private fun npc(build: DataOutputStream.() -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            build()
            writeByte(0)
        }
        return bytes.toByteArray()
    }

    private fun DataOutputStream.text(text: String) {
        write(text.toByteArray(Charsets.ISO_8859_1))
        writeByte(0)
    }

    private fun DataOutputStream.string(opcode: Int, text: String) {
        writeByte(opcode)
        text(text)
    }

    /** A cow-like NPC laid out like the rev 241 cache's: 4-byte models, a recolour, stats, params with a long. */
    private val beast = npc {
        writeByte(12); writeByte(2)
        writeByte(14); writeShort(5848)
        writeByte(13); writeShort(5852)
        writeByte(74); writeShort(1)
        writeByte(77); writeShort(8)
        string(31, "Attack")
        string(34, "Hidden")
        writeByte(18); writeShort(365)
        writeByte(95); writeShort(2)
        string(2, "Test beast")
        writeByte(61); writeByte(2); writeInt(70_000); writeInt(70_001)
        writeByte(62); writeByte(1); writeInt(70_002)
        writeByte(40); writeByte(1); writeShort(10); writeShort(20)
        writeByte(17); writeShort(1); writeShort(2); writeShort(3); writeShort(4)
        writeByte(99)
        writeByte(100); writeByte(-5)
        writeByte(103); writeShort(32)
        writeByte(115); writeShort(5); writeShort(6); writeShort(7); writeShort(8)
        writeByte(249); writeByte(3)
        writeByte(0); writeByte(0); writeShort(26); writeInt(2)
        writeByte(1); writeByte(0); writeShort(27); text("word")
        writeByte(2); writeByte(0); writeShort(28); writeLong(5_000_000_000L)
    }

    @Test
    fun `decodes the fields the server uses`() {
        val type = NpcTypeLoader.decode(9000, beast, "test_beast")
        assertEquals("Test beast", type.name)
        assertEquals(2, type.size)
        assertEquals(listOf(null, "Attack", null, null, null), type.ops)
        assertEquals(2, type.combatLevel)
        assertEquals(5852, type.standAnim)
        // Opcode 17 sets the walk animation again, after 14.
        assertEquals(1, type.walkAnim)
        assertEquals(mapOf<Int, Any>(26 to 2, 27 to "word", 28 to 5_000_000_000L), type.params)
        assertEquals("test_beast", type.internalName)
        assertEquals("Attack", type.op(2))
    }

    @Test
    fun `defaults for an empty type`() {
        val type = NpcTypeLoader.decode(1, npc {})
        assertEquals(NpcType.DEFAULT_NAME, type.name)
        assertEquals(1, type.size)
        assertEquals(-1, type.combatLevel)
        assertEquals(NpcType.DEFAULT_OPS, type.ops)
    }

    @Test
    fun `multi NPCs, head icons, flags and conditional options`() {
        val type = NpcTypeLoader.decode(2, npc {
            writeByte(106); writeShort(0xFFFF); writeShort(300); writeByte(1); writeShort(10); writeShort(0xFFFF)
            writeByte(102); writeByte(0b101); writeShort(440); writeByte(3); writeInt(1 shl 31 or 70_000); writeShort(0x8000 + 500)
            writeByte(107)
            writeByte(93); writeByte(109); writeByte(111); writeByte(122); writeByte(123); writeByte(130); writeByte(145); writeByte(147)
            writeByte(251); writeByte(1); writeByte(2); text("Sub")
            writeByte(252); writeByte(3); writeShort(5); writeShort(0xFFFF); writeInt(1); writeInt(2); text("Cond")
            writeByte(253); writeByte(3); writeShort(4); writeShort(5); writeShort(0xFFFF); writeInt(1); writeInt(2); text("Cond sub")
            writeByte(118); writeShort(77); writeShort(0xFFFF); writeShort(12); writeByte(0); writeShort(11)
        })
        assertEquals(77, type.multiVarbit)
        assertEquals(-1, type.multiVarp)
        // Opcode 118 replaced 106's list: one entry, then its default.
        assertContentEquals(intArrayOf(11, 12), type.multiNpcs)
        assertFalse(type.interactable)
    }

    @Test
    fun `opcode 106 ends its list with no default`() {
        val type = NpcTypeLoader.decode(3, npc {
            writeByte(106); writeShort(0xFFFF); writeShort(300); writeByte(1); writeShort(10); writeShort(0xFFFF)
        })
        assertEquals(-1, type.multiVarbit)
        assertEquals(300, type.multiVarp)
        assertContentEquals(intArrayOf(10, -1, -1), type.multiNpcs)
    }

    @Test
    fun `an unknown opcode or bytes after the end fail`() {
        assertFailsWith<IllegalStateException> { NpcTypeLoader.decode(4, npc { writeByte(200) }) }
        assertFailsWith<IllegalStateException> { NpcTypeLoader.decode(5, npc {} + byteArrayOf(1)) }
    }
}
