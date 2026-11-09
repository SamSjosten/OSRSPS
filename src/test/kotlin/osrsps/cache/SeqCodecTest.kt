package osrsps.cache

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SeqCodecTest {
    @Test
    fun `a sequence is its settings, then its frames as the cache writes them, read back the same`() {
        val definition = SeqCodec.Definition(
            frames = listOf(SeqCodec.frame(11003, 1), SeqCodec.frame(11003, 2)), delays = listOf(6, 2000),
            priority = 6, leftHand = 0, rightHand = 0, verticalOffset = 8,
        )
        val bytes = SeqCodec.encode(definition)
        assertContentEquals(
            byteArrayOf(
                5, 6, 6, 0, 0, 7, 0, 0, 16, 8,
                // Opcode 1: the count, the delays, the frames' files, then the frames' groups (11003 = 0x2AFB).
                1, 0, 2, 0, 6, 0x07, 0xD0.toByte(), 0, 1, 0, 2, 0x2A, 0xFB.toByte(), 0x2A, 0xFB.toByte(),
                0,
            ),
            bytes,
        )
        assertEquals(definition, SeqCodec.decode(bytes))
        assertEquals(2006, definition.cycles)
    }

    @Test
    fun `every setting round-trips, a negative vertical offset with its sign`() {
        val definition = SeqCodec.Definition(
            frames = listOf(SeqCodec.frame(7588, 17), SeqCodec.frame(7588, 16), SeqCodec.frame(7588, 15)), delays = listOf(4, 4, 65535),
            loopOffset = 1, priority = 5, leftHand = 1234, rightHand = 65535, maxLoops = 3, precedenceAnimating = 2, priorityWalking = 1, replyMode = 2, verticalOffset = -8,
        )
        assertEquals(definition, SeqCodec.decode(SeqCodec.encode(definition)))
        assertEquals(listOf(7588), definition.frames.map { it ushr 16 }.distinct())
        assertEquals(listOf(17, 16, 15), definition.frames.map { it and 0xFFFF })
    }

    @Test
    fun `a frame id is its group in the top half and its file in the low half`() {
        assertEquals((11679 shl 16) or 17, SeqCodec.frame(11679, 17))
        assertFailsWith<IllegalArgumentException> { SeqCodec.frame(65536, 0) }
        assertFailsWith<IllegalArgumentException> { SeqCodec.frame(1, -1) }
    }

    @Test
    fun `no frames, a delay for every frame, and the settings' ranges are required`() {
        assertFailsWith<IllegalArgumentException> { SeqCodec.Definition(emptyList(), emptyList()) }
        assertFailsWith<IllegalArgumentException> { SeqCodec.Definition(listOf(1), listOf(4, 4)) }
        assertFailsWith<IllegalArgumentException> { SeqCodec.Definition(listOf(1), listOf(65536)) }
        assertFailsWith<IllegalArgumentException> { SeqCodec.Definition(listOf(1), listOf(4), priority = 256) }
        assertFailsWith<IllegalArgumentException> { SeqCodec.Definition(listOf(1), listOf(4), verticalOffset = 128) }
    }

    @Test
    fun `an opcode ours don't write, a sequence without frames or bytes after the end are refused`() {
        // Opcode 3 is the cache's interleave list, which ours don't use.
        assertFailsWith<IllegalStateException> { SeqCodec.decode(byteArrayOf(3, 0, 0)) }
        assertFailsWith<IllegalArgumentException> { SeqCodec.decode(byteArrayOf(5, 6, 0)) }
        val valid = SeqCodec.encode(SeqCodec.Definition(listOf(SeqCodec.frame(1, 1)), listOf(4)))
        assertFailsWith<IllegalArgumentException> { SeqCodec.decode(valid + byteArrayOf(0)) }
    }
}
