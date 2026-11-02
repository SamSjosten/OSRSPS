package osrsps.cache.overlay

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * A group's files, as the client splits a group (RuneLite's ArchiveFiles.loadContents): a group of one file is that
 * file's bytes as they are; one of more files is the files' bytes, in file id order, in one or more chunks, then for
 * each chunk an int per file (the first file's size in that chunk, then each file's size as a change from the one
 * before), then the chunk count in the last byte. The config groups (index 2) hold every loc, NPC and item type
 * this way: archive 6 is 62,534 loc types in one chunk in the 241 cache.
 */
object Js5Group {
    /** [fileIds] are the group's file ids as its reference table lists them, in order. */
    fun unpack(data: ByteArray, fileIds: List<Int>): LinkedHashMap<Int, ByteArray> {
        require(fileIds.isNotEmpty()) { "A group has at least one file" }
        val files = LinkedHashMap<Int, ByteArray>()
        if (fileIds.size == 1) {
            files[fileIds[0]] = data
            return files
        }
        val chunks = data[data.size - 1].toInt() and 0xFF
        val sizesAt = data.size - 1 - chunks * fileIds.size * 4
        require(chunks > 0 && sizesAt >= 0) { "A group of ${fileIds.size} files in $chunks chunks doesn't fit ${data.size} bytes" }
        val sizes = ByteBuffer.wrap(data, sizesAt, chunks * fileIds.size * 4)
        val chunkSizes = Array(chunks) { IntArray(fileIds.size) }
        for (chunk in 0 until chunks) {
            var size = 0
            for (file in fileIds.indices) {
                size += sizes.int
                require(size >= 0) { "File ${fileIds[file]} has a negative size in chunk $chunk" }
                chunkSizes[chunk][file] = size
            }
        }
        val totals = IntArray(fileIds.size) { file -> chunkSizes.sumOf { it[file] } }
        val outputs = Array(fileIds.size) { ByteArray(totals[it]) }
        val written = IntArray(fileIds.size)
        var at = 0
        for (chunk in 0 until chunks) {
            for (file in fileIds.indices) {
                val size = chunkSizes[chunk][file]
                require(size >= 0 && at + size <= sizesAt) { "File ${fileIds[file]}'s chunk runs past the group's data" }
                System.arraycopy(data, at, outputs[file], written[file], size)
                written[file] += size
                at += size
            }
        }
        require(at == sizesAt) { "The group's chunks leave ${sizesAt - at} bytes unaccounted for" }
        for ((i, id) in fileIds.withIndex()) files[id] = outputs[i]
        return files
    }

    /** Writes [files] in id order, in one chunk; a single file as it is. */
    fun pack(files: Map<Int, ByteArray>): ByteArray {
        require(files.isNotEmpty()) { "A group has at least one file" }
        val sorted = files.toSortedMap()
        if (sorted.size == 1) return sorted.values.first()
        val out = ByteArrayOutputStream(sorted.values.sumOf { it.size } + sorted.size * 4 + 1)
        for (data in sorted.values) out.write(data)
        var previous = 0
        for (data in sorted.values) {
            out.int(data.size - previous)
            previous = data.size
        }
        out.write(1)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.int(value: Int) {
        write(value ushr 24)
        write(value ushr 16 and 0xFF)
        write(value ushr 8 and 0xFF)
        write(value and 0xFF)
    }
}
