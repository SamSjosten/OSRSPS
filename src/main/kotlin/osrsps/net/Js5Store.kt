package osrsps.net

import com.displee.cache.CacheLibrary
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.rsprot.protocol.api.js5.Js5GroupProvider
import net.rsprot.protocol.api.js5.Js5Service

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

        fun load(library: CacheLibrary): Js5Store {
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
            responses[key(MASTER, MASTER)] = frame(MASTER, MASTER, masterIndex(crcs, versions))
            return Js5Store(crcs, responses)
        }

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
