package osrsps.cache.overlay

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * An index's reference table (archive 255, group = the index), which lists the index's groups with their CRCs and
 * versions and each group's files; the client reads it to know what to ask for and what it already has. Protocols 5
 * to 7, with the named and sized flags, as RuneLite's IndexData reads and writes it; any other flag is refused.
 */
class ReferenceTable(
    val protocol: Int,
    val revision: Int,
    val named: Boolean,
    val sized: Boolean,
    val groups: MutableList<Group>,
) {
    class Group(
        val id: Int,
        val nameHash: Int,
        val crc: Int,
        val compressedSize: Int,
        val decompressedSize: Int,
        val version: Int,
        val files: List<File>,
    )

    class File(val id: Int, val nameHash: Int)

    val maxGroupId: Int get() = groups.maxOfOrNull { it.id } ?: -1

    fun group(id: Int): Group? = groups.firstOrNull { it.id == id }

    /** Adds [group], or replaces the one with its id, keeping the list in id order. */
    fun put(group: Group) {
        groups.removeAll { it.id == group.id }
        val at = groups.indexOfFirst { it.id > group.id }
        if (at == -1) groups += group else groups.add(at, group)
    }

    fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(protocol)
        if (protocol >= 6) out.int(revision)
        out.write((if (named) NAMED else 0) or (if (sized) SIZED else 0))
        out.smart(groups.size)
        var last = 0
        for ((i, group) in groups.withIndex()) {
            require(if (i == 0) group.id >= 0 else group.id > last) { "Group ids must rise: ${group.id} after $last" }
            out.smart(group.id - last)
            last = group.id
        }
        if (named) for (group in groups) out.int(group.nameHash)
        for (group in groups) out.int(group.crc)
        if (sized) for (group in groups) {
            out.int(group.compressedSize)
            out.int(group.decompressedSize)
        }
        for (group in groups) out.int(group.version)
        for (group in groups) out.smart(group.files.size)
        for (group in groups) {
            var lastFile = 0
            for (file in group.files) {
                out.smart(file.id - lastFile)
                lastFile = file.id
            }
        }
        if (named) for (group in groups) for (file in group.files) out.int(file.nameHash)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.int(value: Int) {
        write(value ushr 24)
        write(value ushr 16 and 0xFF)
        write(value ushr 8 and 0xFF)
        write(value and 0xFF)
    }

    /** A count or id: 2 bytes below protocol 7; from 7 on, 2 bytes under 32768, else 4 with the top bit set. */
    private fun ByteArrayOutputStream.smart(value: Int) {
        require(value >= 0) { "A reference table holds no negative value, got $value" }
        if (protocol >= 7 && value >= 0x8000) {
            int(value or Int.MIN_VALUE)
        } else {
            require(value < 0x10000) { "$value doesn't fit 2 bytes (protocol $protocol)" }
            write(value ushr 8)
            write(value and 0xFF)
        }
    }

    companion object {
        private const val NAMED = 1
        private const val SIZED = 4

        fun decode(data: ByteArray): ReferenceTable {
            val buf = ByteBuffer.wrap(data)
            val protocol = buf.get().toInt() and 0xFF
            require(protocol in 5..7) { "Unsupported reference table protocol $protocol" }
            val revision = if (protocol >= 6) buf.int else 0
            val flags = buf.get().toInt() and 0xFF
            require(flags and (NAMED or SIZED).inv() == 0) { "Unknown reference table flags $flags" }
            val named = flags and NAMED != 0
            val sized = flags and SIZED != 0
            fun smart(): Int = if (protocol >= 7) {
                if (buf.get(buf.position()).toInt() < 0) buf.int and Int.MAX_VALUE else buf.short.toInt() and 0xFFFF
            } else {
                buf.short.toInt() and 0xFFFF
            }
            val count = smart()
            val ids = IntArray(count)
            var last = 0
            for (i in 0 until count) {
                last += smart()
                ids[i] = last
            }
            val nameHashes = IntArray(count) { if (named) buf.int else 0 }
            val crcs = IntArray(count) { buf.int }
            val compressedSizes = IntArray(count)
            val decompressedSizes = IntArray(count)
            if (sized) for (i in 0 until count) {
                compressedSizes[i] = buf.int
                decompressedSizes[i] = buf.int
            }
            val versions = IntArray(count) { buf.int }
            val fileCounts = IntArray(count) { smart() }
            val fileIds = Array(count) { group ->
                var lastFile = 0
                IntArray(fileCounts[group]) {
                    lastFile += smart()
                    lastFile
                }
            }
            val fileNameHashes = Array(count) { group -> IntArray(fileCounts[group]) { if (named) buf.int else 0 } }
            require(!buf.hasRemaining()) { "${buf.remaining()} bytes left after the reference table" }
            val groups = ArrayList<Group>(count)
            for (i in 0 until count) {
                groups += Group(
                    ids[i], nameHashes[i], crcs[i], compressedSizes[i], decompressedSizes[i], versions[i],
                    fileIds[i].indices.map { File(fileIds[i][it], fileNameHashes[i][it]) },
                )
            }
            return ReferenceTable(protocol, revision, named, sized, groups)
        }
    }
}
