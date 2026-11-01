package osrsps.cache.overlay

import com.displee.compress.type.BZIP2Compressor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * A JS5 container, the wrapping every group and reference table is stored and sent in: a compression type byte
 * (0 none, 1 bzip2, 2 gzip), the compressed length, then for a compressed container the plain length, then the data.
 * A stored container may carry a 2-byte version trailer after that, which [unpack] ignores and [pack] never writes.
 */
object Js5Container {
    const val NONE = 0
    const val BZIP2 = 1
    const val GZIP = 2

    fun type(container: ByteArray): Int = container[0].toInt() and 0xFF

    fun unpack(container: ByteArray): ByteArray {
        val buf = ByteBuffer.wrap(container)
        val type = buf.get().toInt()
        val length = buf.int
        require(length >= 0 && 5 + length <= container.size) { "Container declares $length bytes but has ${container.size}" }
        if (type == NONE) return container.copyOfRange(5, 5 + length)
        val plainLength = buf.int
        require(plainLength >= 0) { "Container declares a plain length of $plainLength" }
        return when (type) {
            BZIP2 -> ByteArray(plainLength).also { BZIP2Compressor().decompress(it, plainLength, container, length, 9) }
            GZIP -> GZIPInputStream(ByteArrayInputStream(container, 9, length)).readBytes().also {
                require(it.size == plainLength) { "Gzip container holds ${it.size} bytes, not the $plainLength declared" }
            }
            else -> throw IllegalArgumentException("Unknown compression $type")
        }
    }

    /** Wraps [data] uncompressed, or gzipped when [type] is [GZIP]; bzip2 is read but never written. */
    fun pack(data: ByteArray, type: Int = NONE): ByteArray {
        val out = ByteArrayOutputStream(data.size + 9)
        when (type) {
            NONE -> {
                out.write(NONE)
                out.writeInt(data.size)
                out.write(data)
            }
            GZIP, BZIP2 -> {
                val compressed = ByteArrayOutputStream().also { GZIPOutputStream(it).use { gzip -> gzip.write(data) } }.toByteArray()
                out.write(GZIP)
                out.writeInt(compressed.size)
                out.writeInt(data.size)
                out.write(compressed)
            }
            else -> throw IllegalArgumentException("Unknown compression $type")
        }
        return out.toByteArray()
    }

    /** The CRC the master index and the reference tables carry: CRC-32 of the whole container as sent. */
    fun crc(container: ByteArray): Int = CRC32().also { it.update(container) }.value.toInt()

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write(value ushr 24)
        write(value ushr 16 and 0xFF)
        write(value ushr 8 and 0xFF)
        write(value and 0xFF)
    }
}
