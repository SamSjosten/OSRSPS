package osrsps.cache.overlay

import com.displee.compress.type.BZIP2Compressor
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Js5ContainerTest {
    private val data = ByteArray(3000) { (it * 7).toByte() }

    @Test
    fun `packs uncompressed as a type byte, the length and the data`() {
        val container = Js5Container.pack(byteArrayOf(5, 6, 7))
        assertContentEquals(byteArrayOf(0, 0, 0, 0, 3, 5, 6, 7), container)
        assertEquals(Js5Container.NONE, Js5Container.type(container))
        assertContentEquals(byteArrayOf(5, 6, 7), Js5Container.unpack(container))
    }

    @Test
    fun `packs gzip with both lengths and unpacks it again`() {
        val container = Js5Container.pack(data, Js5Container.GZIP)
        assertEquals(Js5Container.GZIP, Js5Container.type(container))
        val compressedLength = int(container, 1)
        assertEquals(9 + compressedLength, container.size)
        assertEquals(data.size, int(container, 5), "plain length")
        assertContentEquals(data, Js5Container.unpack(container))
    }

    @Test
    fun `unpacks bzip2 as the cache stores it`() {
        val compressed = BZIP2Compressor().compress(data)
        val container = ByteArray(9 + compressed.size)
        container[0] = Js5Container.BZIP2.toByte()
        putInt(container, 1, compressed.size)
        putInt(container, 5, data.size)
        compressed.copyInto(container, 9)
        assertContentEquals(data, Js5Container.unpack(container))
    }

    @Test
    fun `ignores a stored container's version trailer`() {
        val container = Js5Container.pack(byteArrayOf(1, 2)) + byteArrayOf(0, 9)
        assertContentEquals(byteArrayOf(1, 2), Js5Container.unpack(container))
    }

    @Test
    fun `refuses a container shorter than its length or of an unknown type`() {
        assertFailsWith<IllegalArgumentException> { Js5Container.unpack(byteArrayOf(0, 0, 0, 0, 9, 1)) }
        assertFailsWith<IllegalArgumentException> { Js5Container.unpack(byteArrayOf(3, 0, 0, 0, 1, 0, 0, 0, 1, 1)) }
        assertFailsWith<IllegalArgumentException> { Js5Container.pack(byteArrayOf(1), 3) }
    }

    @Test
    fun `crc is CRC-32 of the whole container`() {
        val container = Js5Container.pack("123456789".toByteArray())
        assertEquals(CRC32().also { it.update(container) }.value.toInt(), Js5Container.crc(container))
        // Not just of the data: that would be the well-known 0xCBF43926.
        assertEquals(0xCBF43926.toInt(), CRC32().also { it.update("123456789".toByteArray()) }.value.toInt())
        assert(Js5Container.crc(container) != 0xCBF43926.toInt())
    }

    private fun int(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)

    private fun putInt(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = (value ushr 24).toByte()
        bytes[at + 1] = (value ushr 16).toByte()
        bytes[at + 2] = (value ushr 8).toByte()
        bytes[at + 3] = value.toByte()
    }
}
