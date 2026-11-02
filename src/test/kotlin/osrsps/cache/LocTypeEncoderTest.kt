package osrsps.cache

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocTypeEncoderTest {
    @Test
    fun `the decoder reads back what the encoder writes`() {
        val definition = LocTypeEncoder.Definition(
            "Lodestone", listOf(65000, 70000), ops = listOf("Activate", null, "Teleport", null, null), width = 3, length = 2, solid = false,
        )
        val type = LocTypeDecoder.decode(65000, LocTypeEncoder.encode(definition))
        assertEquals(65000, type.id)
        assertEquals("Lodestone", type.name)
        assertEquals(listOf("Activate", null, "Teleport", null, null), type.ops)
        assertEquals(1, type.op("activate"))
        assertEquals(3, type.width)
        assertEquals(2, type.length)
        assertEquals(0, type.collision.interactType, "walked over")
        assertFalse(type.collision.blocksProjectile)
        assertFalse(type.multiloc)
    }

    @Test
    fun `writes the 241 model list, the name and only the fields that differ from the defaults`() {
        val bytes = LocTypeEncoder.encode(LocTypeEncoder.Definition("Rock", listOf(0x01020304), ops = listOf(null, "Mine", null, null, null)))
        assertContentEquals(
            byteArrayOf(7, 1, 1, 2, 3, 4) + byteArrayOf(2) + "Rock".toByteArray() + 0 + byteArrayOf(31) + "Mine".toByteArray() + 0 + 0,
            bytes,
        )
        val type = LocTypeDecoder.decode(1, bytes)
        assertEquals(1, type.width)
        assertEquals(2, type.collision.interactType, "solid")
        assertTrue(type.collision.blocksProjectile)
    }

    @Test
    fun `a solid loc projectiles pass gets opcode 18`() {
        val bytes = LocTypeEncoder.encode(LocTypeEncoder.Definition("Fence", listOf(1), blocksProjectiles = false))
        assertTrue(18.toByte() in bytes)
        assertFalse(17.toByte() in bytes)
        val type = LocTypeDecoder.decode(1, bytes)
        assertEquals(2, type.collision.interactType)
        assertFalse(type.collision.blocksProjectile)
    }

    @Test
    fun `a transform writes opcode 77 with the varbit, no varp and the children, and the decoder sees a multiloc`() {
        val definition = LocTypeEncoder.Definition(
            "Lodestone", listOf(65000), ops = listOf("Activate", null, null, null, null), width = 3, length = 3, solid = false,
            transform = LocTypeEncoder.Transform(20411, listOf(65000, 65001)),
        )
        val bytes = LocTypeEncoder.encode(definition)
        val transform = byteArrayOf(77, 0x4F, 0xBB.toByte(), 0xFF.toByte(), 0xFF.toByte(), 1, 0xFD.toByte(), 0xE8.toByte(), 0xFD.toByte(), 0xE9.toByte())
        assertContentEquals(transform + 0, bytes.copyOfRange(bytes.size - transform.size - 1, bytes.size))
        val type = LocTypeDecoder.decode(65002, bytes)
        assertTrue(type.multiloc)
        assertEquals(listOf("Activate", null, null, null, null), type.ops)
        assertEquals(3, type.width)
        assertEquals(0, type.collision.interactType)
        val none = LocTypeEncoder.encode(LocTypeEncoder.Definition("X", listOf(1), transform = LocTypeEncoder.Transform(5, listOf(LocTypeEncoder.NONE))))
        assertContentEquals(byteArrayOf(77, 0, 5, -1, -1, 0, -1, -1, 0), none.copyOfRange(none.size - 9, none.size))
    }

    @Test
    fun `refuses a definition the client couldn't read`() {
        assertFailsWith<IllegalArgumentException>("no models") { LocTypeEncoder.Definition("X", emptyList()) }
        assertFailsWith<IllegalArgumentException>("empty name") { LocTypeEncoder.Definition("", listOf(1)) }
        assertFailsWith<IllegalArgumentException>("four ops") { LocTypeEncoder.Definition("X", listOf(1), ops = listOf(null, null, null, null)) }
        assertFailsWith<IllegalArgumentException>("empty op") { LocTypeEncoder.Definition("X", listOf(1), ops = listOf("", null, null, null, null)) }
        assertFailsWith<IllegalArgumentException>("too wide") { LocTypeEncoder.Definition("X", listOf(1), width = 256) }
        assertFailsWith<IllegalArgumentException>("no children") { LocTypeEncoder.Transform(1, emptyList()) }
        assertFailsWith<IllegalArgumentException>("too many children") { LocTypeEncoder.Transform(1, List(257) { 0 }) }
        assertFailsWith<IllegalArgumentException>("bad child") { LocTypeEncoder.Transform(1, listOf(0x10000)) }
        assertFailsWith<IllegalArgumentException>("bad varbit") { LocTypeEncoder.Transform(LocTypeEncoder.NONE, listOf(1)) }
    }
}
