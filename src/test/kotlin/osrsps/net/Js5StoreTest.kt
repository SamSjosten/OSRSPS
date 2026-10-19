package osrsps.net

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Js5StoreTest {
    @Test
    fun `strips the version trailer from a compressed container`() {
        // gzip type, 3 compressed bytes, 4-byte uncompressed length, then a 2-byte trailer.
        val stored = byteArrayOf(2, 0, 0, 0, 3, 9, 9, 9, 0, 0, 0, 7, 0x12, 0x34)
        assertContentEquals(stored.copyOf(12), Js5Store.stripVersionTrailer(stored))
    }

    @Test
    fun `keeps an uncompressed container without a trailer`() {
        val stored = byteArrayOf(0, 0, 0, 0, 2, 5, 6)
        assertContentEquals(stored, Js5Store.stripVersionTrailer(stored))
    }

    @Test
    fun `rejects a container shorter than its header says`() {
        assertFailsWith<IllegalArgumentException> { Js5Store.stripVersionTrailer(byteArrayOf(0, 0, 0, 0, 9, 1)) }
    }

    @Test
    fun `writes the master index as crc and version per archive`() {
        val container = Js5Store.masterIndex(intArrayOf(0x01020304, -1), intArrayOf(7, 8))
        assertEquals(0, container[0].toInt())
        assertEquals(16, container[4].toInt())
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 0, 0, 0, 7, -1, -1, -1, -1, 0, 0, 0, 8),
            container.copyOfRange(5, container.size),
        )
    }
}
