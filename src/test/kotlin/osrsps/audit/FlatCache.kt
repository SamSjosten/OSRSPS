package osrsps.audit

import com.displee.compress.type.BZIP2Compressor
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.Base64
import java.util.zip.GZIPInputStream
import kotlin.io.path.useLines

/**
 * Reads groups from a cache in abextm's flatcache layout (github.com/abextm/osrs-cache): one `<index>.flatcache` text
 * file per index, a block of `key=value` lines per group, `contents=` holding the group's JS5 container in base64 and
 * one `file=<id>=<namehash>` line per file in it. Only for the audits, which run against a clone of that repository in
 * the cloud, where the server's own cache isn't.
 */
class FlatCache(private val dir: Path) {
    /** The files of group [group] in index [index], by file id; empty when the group isn't there. */
    fun group(index: Int, group: Int): Map<Int, ByteArray> {
        var inGroup = false
        var contents: String? = null
        val fileIds = ArrayList<Int>()
        dir.resolve("$index.flatcache").useLines { lines ->
            for (line in lines) {
                val key = line.substringBefore('=')
                val value = line.substringAfter('=')
                when {
                    key == "id" && inGroup -> break
                    key == "id" -> inGroup = value.toInt() == group
                    !inGroup -> continue
                    key == "contents" -> contents = value
                    key == "file" -> fileIds += value.substringBefore('=').toInt()
                }
            }
        }
        val data = unpack(Base64.getDecoder().decode(contents ?: return emptyMap()))
        return split(data, fileIds.ifEmpty { listOf(0) })
    }

    companion object {
        /** A JS5 container: compression type, compressed length, then (when compressed) the plain length and data. */
        fun unpack(container: ByteArray): ByteArray {
            val buf = ByteBuffer.wrap(container)
            val type = buf.get().toInt()
            val length = buf.int
            if (type == 0) return container.copyOfRange(5, 5 + length)
            val plainLength = buf.int
            return when (type) {
                1 -> ByteArray(plainLength).also { BZIP2Compressor().decompress(it, plainLength, container, length, 9) }
                2 -> GZIPInputStream(ByteArrayInputStream(container, 9, length)).readBytes()
                else -> error("Unknown compression $type")
            }
        }

        /** Splits a group into its files: the last byte counts the chunks, before it each chunk's size deltas. */
        fun split(data: ByteArray, fileIds: List<Int>): Map<Int, ByteArray> {
            if (fileIds.size == 1) return mapOf(fileIds[0] to data)
            val chunks = data[data.size - 1].toInt() and 0xFF
            val table = ByteBuffer.wrap(data, data.size - 1 - chunks * fileIds.size * 4, chunks * fileIds.size * 4)
            val sizes = Array(chunks) { IntArray(fileIds.size) }
            val totals = IntArray(fileIds.size)
            for (chunk in 0 until chunks) {
                var size = 0
                for (file in fileIds.indices) {
                    size += table.int
                    sizes[chunk][file] = size
                    totals[file] += size
                }
            }
            val files = Array(fileIds.size) { ByteArray(totals[it]) }
            val written = IntArray(fileIds.size)
            var offset = 0
            for (chunk in 0 until chunks) {
                for (file in fileIds.indices) {
                    val size = sizes[chunk][file]
                    System.arraycopy(data, offset, files[file], written[file], size)
                    written[file] += size
                    offset += size
                }
            }
            return fileIds.indices.associate { fileIds[it] to files[it] }
        }
    }
}
