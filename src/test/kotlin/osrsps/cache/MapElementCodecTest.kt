package osrsps.cache

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MapElementCodecTest {
    @Test
    fun `an icon element is the sprite, the icon byte and the flags the cache's icons carry, read back the same`() {
        val definition = MapElementCodec.Definition(sprite = 1448)
        val bytes = MapElementCodec.encode(definition)
        assertContentEquals(byteArrayOf(1, 0x05, 0xA8.toByte(), 8, 1, 7, 3, 0), bytes)
        assertEquals(definition, MapElementCodec.decode(bytes))
    }

    @Test
    fun `a sprite id past 32767 takes the 4-byte form with the top bit set, as the client's big smart reads it`() {
        val definition = MapElementCodec.Definition(sprite = 65004, category = 1050)
        val bytes = MapElementCodec.encode(definition)
        assertContentEquals(byteArrayOf(19, 0x04, 0x1A, 1, 0x80.toByte(), 0, 0xFD.toByte(), 0xEC.toByte(), 8, 1, 7, 3, 0), bytes)
        assertEquals(definition, MapElementCodec.decode(bytes))
    }

    @Test
    fun `a label, or a sprite the client can't name, is refused`() {
        assertFailsWith<IllegalArgumentException> { MapElementCodec.Definition(sprite = 65536) }
        // A label: name (3), text size (6), colour (4), category (19), opcode 8 with a 0.
        val label = byteArrayOf(3) + "Altar".toByteArray() + byteArrayOf(0, 6, 0, 4, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 19, 4, 0x69, 8, 0, 0)
        assertFailsWith<IllegalStateException> { MapElementCodec.decode(label) }
        assertFailsWith<IllegalArgumentException> { MapElementCodec.decode(byteArrayOf(1, 0, 1, 8, 0, 7, 3, 0)) }
    }
}
