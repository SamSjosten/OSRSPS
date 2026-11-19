package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import osrsps.game.IdkType
import osrsps.game.IdkTypes

private val logger = KotlinLogging.logger {}

/**
 * Reads identity kit types (config archive 3): each kit's body part and whether the design screens offer it. Only
 * those two are kept; the models and recolours are read past. The opcode layout is RuneLite's cache KitLoader, plus 5
 * and 70 to 79, which carry the models and chat head models as 4-byte ids (read from the 242 cache's kits, 2026-11-19:
 * kit 0 is `01 00 46 0000003f 05 01 000000e6 00`, body part 0, head model 63, model 230, as osrs-dumps' dump.idk has
 * it). Every one of the 307 kits in that cache decodes with it and ends exactly on its 0 opcode.
 */
object IdkTypeLoader {
    private const val CONFIG_INDEX = 2
    private const val IDK_GROUP = 3

    fun load(library: CacheLibrary): IdkTypes {
        val files = library.index(CONFIG_INDEX)?.archive(IDK_GROUP)?.files().orEmpty()
        if (files.isEmpty()) {
            logger.warn { "No identity kits in the cache; ::look can't check kits" }
            return IdkTypes.NONE
        }
        val array = arrayOfNulls<IdkType>(files.maxOf { it.id } + 1)
        var failed = 0
        for (file in files) {
            val data = file.data ?: continue
            try {
                array[file.id] = decode(file.id, data)
            } catch (e: Exception) {
                if (failed++ == 0) logger.warn(e) { "Could not decode identity kit ${file.id}" }
            }
        }
        if (failed > 0) logger.warn { "$failed identity kits did not decode" }
        val types = IdkTypes(array)
        logger.info { "Loaded ${types.size} identity kits" }
        return types
    }

    fun decode(id: Int, data: ByteArray): IdkType {
        val buf = Unpooled.wrappedBuffer(data)
        var bodyPart = -1
        var selectable = true
        while (true) {
            when (val opcode = buf.readUnsignedByte().toInt()) {
                0 -> break
                1 -> bodyPart = buf.readUnsignedByte().toInt()
                2 -> repeat(buf.readUnsignedByte().toInt()) { buf.readUnsignedShort() }
                3 -> selectable = false
                40, 41 -> skipPairs(buf)
                // 4-byte model ids, as NPC types' 61 and 62 replace 1 and 60: what the 242 cache's kits all use.
                5 -> repeat(buf.readUnsignedByte().toInt()) { buf.readInt() }
                in 60..69 -> buf.readUnsignedShort()
                in 70..79 -> buf.readInt()
                else -> error("Identity kit $id: unknown opcode $opcode")
            }
        }
        require(!buf.isReadable) { "Identity kit $id: ${buf.readableBytes()} bytes after the end" }
        return IdkType(id, bodyPart, selectable)
    }

    private fun skipPairs(buf: ByteBuf) {
        repeat(buf.readUnsignedByte().toInt()) {
            buf.readUnsignedShort()
            buf.readUnsignedShort()
        }
    }
}
