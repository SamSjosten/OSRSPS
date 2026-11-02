package osrsps.net

import com.displee.cache.CacheLibrary
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.rsprot.protocol.api.js5.Js5GroupProvider
import net.rsprot.protocol.api.js5.Js5Service
import osrsps.cache.overlay.CacheOverlay
import osrsps.cache.overlay.Js5Container
import osrsps.cache.overlay.Js5Group
import osrsps.cache.overlay.ReferenceTable

/**
 * Every JS5 response the client can ask for, framed and held in memory.
 *
 * Groups are served exactly as stored (still compressed), minus the 2-byte version trailer.
 * Archive 255 holds each index's reference table, and 255/255 is the master index:
 * a CRC and version per archive, which is also what the client's login CRCs are checked against.
 */
class Js5Store private constructor(
    val crcs: IntArray,
    private val responses: Map<Int, ByteBuf>,
) : Js5GroupProvider {
    val size: Int get() = responses.size

    override fun provide(archive: Int, group: Int): ByteBuf? = responses[key(archive, group)]

    companion object {
        const val MASTER = 255

        /**
         * Everything in [library], plus [overlay]: each of its groups is added to its index's reference table with
         * the CRC of the container it is sent in, each of its files goes into its live group, which is packed again
         * and gets its new CRC in the table, and each changed table's CRC goes in the master index, so a client sees
         * the index changed, fetches the table again and then whatever it lists with a CRC the client doesn't have.
         */
        fun load(library: CacheLibrary, overlay: CacheOverlay = CacheOverlay.NONE): Js5Store {
            val indices = library.indices.filterNotNull().sortedBy { it.id }
            val archiveCount = (indices.maxOfOrNull { it.id } ?: -1) + 1
            val crcs = IntArray(archiveCount)
            val versions = IntArray(archiveCount)
            val responses = HashMap<Int, ByteBuf>()
            for (index in indices) {
                crcs[index.id] = index.crc
                versions[index.id] = index.revision
                library.index255?.readArchiveSector(index.id)?.data?.let {
                    responses[key(MASTER, index.id)] = frame(MASTER, index.id, it)
                }
                for (group in index.archiveIds()) {
                    val data = index.readArchiveSector(group)?.data ?: continue
                    responses[key(index.id, group)] = frame(index.id, group, stripVersionTrailer(data))
                }
            }
            val storedTables = HashMap<Int, ByteArray>()
            val tables = HashMap<Int, ReferenceTable>()
            fun table(indexId: Int): ReferenceTable = tables.getOrPut(indexId) {
                require(indexId in indices.map { it.id }) { "The overlay names index $indexId, which the cache doesn't have" }
                val stored = library.index255?.readArchiveSector(indexId)?.data ?: error("The cache has no reference table for index $indexId")
                storedTables[indexId] = stored
                ReferenceTable.decode(Js5Container.unpack(stored))
            }
            for ((indexId, groups) in overlay.byIndex()) {
                val table = table(indexId)
                val liveMax = table.maxGroupId
                for (group in groups) {
                    require(group.group > liveMax) { "Custom group ${group.group} of index $indexId has to be above the cache's own groups, which go up to $liveMax" }
                    require(group.group <= CacheOverlay.MAX_GROUP) { "Custom group ${group.group} of index $indexId doesn't fit a JS5 request" }
                    val container = Js5Container.pack(group.data)
                    // Guessed: the sizes are the container's and the data's; no index we overlay is sized.
                    table.put(ReferenceTable.Group(group.group, 0, Js5Container.crc(container), container.size, group.data.size, OVERLAY_VERSION, group.files.map { ReferenceTable.File(it, 0) }))
                    responses[key(indexId, group.group)] = frame(indexId, group.group, container)
                }
            }
            for ((where, files) in overlay.filesByGroup()) {
                val (indexId, groupId) = where
                val table = table(indexId)
                val entry = table.group(groupId) ?: error("Index $indexId has no group $groupId to add files to")
                val stored = library.index(indexId)?.readArchiveSector(groupId)?.data ?: error("The cache has no data for group $indexId/$groupId")
                val container = stripVersionTrailer(stored)
                val unpacked = Js5Group.unpack(Js5Container.unpack(container), entry.files.map { it.id })
                val liveMax = entry.files.maxOf { it.id }
                for (file in files) {
                    require(file.file > liveMax) { "Custom file ${file.file} of group $indexId/$groupId has to be above the cache's own files, which go up to $liveMax" }
                    require(unpacked.put(file.file, file.data) == null) { "File ${file.file} of group $indexId/$groupId is in the overlay twice" }
                }
                val data = Js5Group.pack(unpacked)
                val repacked = Js5Container.pack(data, Js5Container.type(container))
                val fileList = (entry.files + files.map { ReferenceTable.File(it.file, 0) }).sortedBy { it.id }
                table.put(ReferenceTable.Group(groupId, entry.nameHash, Js5Container.crc(repacked), repacked.size, data.size, entry.version, fileList))
                responses[key(indexId, groupId)] = frame(indexId, groupId, repacked)
            }
            for ((indexId, table) in tables) {
                val rewritten = Js5Container.pack(table.encode(), Js5Container.type(storedTables.getValue(indexId)))
                crcs[indexId] = Js5Container.crc(rewritten)
                responses[key(MASTER, indexId)] = frame(MASTER, indexId, rewritten)
            }
            responses[key(MASTER, MASTER)] = frame(MASTER, MASTER, masterIndex(crcs, versions))
            return Js5Store(crcs, responses)
        }

        /** The version an overlay group carries; a changed model has a new CRC, which is what makes the client fetch it again. */
        const val OVERLAY_VERSION = 1

        /** Builds the uncompressed 255/255 container: per archive, a 4-byte CRC then a 4-byte version. */
        fun masterIndex(crcs: IntArray, versions: IntArray): ByteArray {
            val payload = Unpooled.buffer(crcs.size * 8)
            for (i in crcs.indices) {
                payload.writeInt(crcs[i])
                payload.writeInt(versions[i])
            }
            val container = Unpooled.buffer(5 + payload.readableBytes())
            container.writeByte(0)
            container.writeInt(payload.readableBytes())
            container.writeBytes(payload)
            return ByteArray(container.readableBytes()).also { container.readBytes(it) }
        }

        /** Cuts a stored container down to its header-declared length, dropping any version trailer. */
        fun stripVersionTrailer(data: ByteArray): ByteArray {
            require(data.size >= 5) { "Container too short: ${data.size} bytes" }
            val type = data[0].toInt() and 0xFF
            val compressedLength = ((data[1].toInt() and 0xFF) shl 24) or ((data[2].toInt() and 0xFF) shl 16) or
                ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
            val length = 5 + compressedLength + if (type != 0) 4 else 0
            require(length <= data.size) { "Container declares $length bytes but has ${data.size}" }
            return if (length == data.size) data else data.copyOf(length)
        }

        private fun frame(archive: Int, group: Int, container: ByteArray): ByteBuf {
            val input = Unpooled.wrappedBuffer(container)
            val output = Unpooled.directBuffer(container.size + 3 + container.size / 511 + 1)
            Js5Service.prepareJs5Buffer(archive, group, input, output)
            // RSProt writes slices of this buffer without retaining it, so it must never be freed.
            return Unpooled.unreleasableBuffer(output)
        }

        private fun key(archive: Int, group: Int): Int = (archive shl 16) or group
    }
}
