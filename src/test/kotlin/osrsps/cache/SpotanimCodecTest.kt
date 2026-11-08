package osrsps.cache

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpotanimCodecTest {
    @Test
    fun `a graphic is a model on a sequence with its lighting and recolours, laid out as the cache's recoloured Tele Other graphics are`() {
        val definition = SpotanimCodec.Definition(model = 5802, seq = 1813, ambient = 100, contrast = 100, recolours = listOf(49969 to 30513))
        val bytes = SpotanimCodec.encode(definition)
        assertContentEquals(
            byteArrayOf(
                3, 0, 0, 0x16, 0xAA.toByte(), // the model, 4 bytes (5802 = 0x16AA)
                2, 0x07, 0x15, // the sequence (1813 = 0x715)
                7, 100, 8, 100, // ambient, contrast
                40, 1, 0xC3.toByte(), 0x31, 0x77, 0x31, // one recolour: 49969 (0xC331) drawn as 30513 (0x7731)
                0,
            ),
            bytes,
        )
        assertEquals(definition, SpotanimCodec.decode(bytes))
    }

    @Test
    fun `sizes, a rotation and retextures round-trip, and the client's defaults go unwritten`() {
        val definition = SpotanimCodec.Definition(model = 3091, seq = 667, resizeH = 64, resizeV = 200, rotation = 512, retextures = listOf(1 to 2))
        val bytes = SpotanimCodec.encode(definition)
        assertEquals(definition, SpotanimCodec.decode(bytes))
        assertEquals(listOf(3, 2, 4, 5, 6, 41), opcodes(bytes))
        assertEquals(listOf(3, 2), opcodes(SpotanimCodec.encode(SpotanimCodec.Definition(model = 1, seq = 1))))
    }

    @Test
    fun `the 2-byte model the older cache wrote reads back too`() {
        assertEquals(SpotanimCodec.Definition(model = 3091, seq = 5), SpotanimCodec.decode(byteArrayOf(1, 0x0C, 0x13, 2, 0, 5, 0)))
    }

    @Test
    fun `a colour packs as the client keeps one`() {
        assertEquals(49969, SpotanimCodec.hsl(48, 6, 49))
        assertEquals(30513, SpotanimCodec.hsl(29, 6, 49))
        assertEquals(30052, SpotanimCodec.hsl(29, 2, 100))
        assertFailsWith<IllegalArgumentException> { SpotanimCodec.hsl(64, 0, 0) }
        assertFailsWith<IllegalArgumentException> { SpotanimCodec.hsl(0, 8, 0) }
        assertFailsWith<IllegalArgumentException> { SpotanimCodec.hsl(0, 0, 128) }
    }

    @Test
    fun `an opcode ours don't write, a graphic without a model or sequence, or bytes after the end are refused`() {
        // Opcode 9 is the cache's debug name.
        assertFailsWith<IllegalStateException> { SpotanimCodec.decode(byteArrayOf(9, 0)) }
        assertFailsWith<IllegalArgumentException> { SpotanimCodec.decode(byteArrayOf(2, 0, 1, 0)) }
        assertFailsWith<IllegalArgumentException> { SpotanimCodec.decode(byteArrayOf(3, 0, 0, 0, 1, 0)) }
        assertFailsWith<IllegalArgumentException> { SpotanimCodec.decode(SpotanimCodec.encode(SpotanimCodec.Definition(1, 1)) + byteArrayOf(0)) }
        assertFailsWith<IllegalArgumentException> { SpotanimCodec.Definition(model = -1, seq = 1) }
        assertFailsWith<IllegalArgumentException> { SpotanimCodec.Definition(model = 1, seq = 65536) }
    }

    /** The opcodes in [bytes], skipping each one's payload as the decoder would. */
    private fun opcodes(bytes: ByteArray): List<Int> {
        val out = mutableListOf<Int>()
        var i = 0
        while (true) {
            val opcode = bytes[i++].toInt() and 0xFF
            if (opcode == 0) return out
            out += opcode
            i += when (opcode) {
                3 -> 4
                2, 4, 5, 6 -> 2
                7, 8 -> 1
                40, 41 -> 1 + 4 * (bytes[i].toInt() and 0xFF)
                else -> error("opcode $opcode")
            }
        }
    }
}
