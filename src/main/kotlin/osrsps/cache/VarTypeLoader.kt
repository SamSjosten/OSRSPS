package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.Unpooled
import osrsps.game.VarTypes
import osrsps.game.VarbitType

private val logger = KotlinLogging.logger {}

/** Reads varp types (config archive 16) and varbit types (config archive 14). */
object VarTypeLoader {
    private const val CONFIG_INDEX = 2
    private const val VARBIT_GROUP = 14
    private const val VARP_GROUP = 16

    fun load(library: CacheLibrary): VarTypes {
        val config = library.index(CONFIG_INDEX)
        // Inferred from the client, which sizes its varp array by this archive's file count: varps are numbered
        // 0 until the highest file id plus one. Varp types only hold client settings, so their data isn't read.
        val varpCount = config?.archive(VARP_GROUP)?.fileIds()?.maxOrNull()?.plus(1)
        if (varpCount == null) {
            logger.warn { "No varp types in the cache; allowing varps 0 to ${VarTypes.NONE.varpCount - 1}" }
        }
        val varbits = HashMap<Int, VarbitType>()
        var failed = 0
        for (file in config?.archive(VARBIT_GROUP)?.files().orEmpty()) {
            val data = file.data ?: continue
            try {
                varbits[file.id] = decodeVarbit(data)
            } catch (e: Exception) {
                if (failed++ == 0) logger.warn(e) { "Could not decode varbit ${file.id}" }
            }
        }
        if (failed > 0) logger.warn { "$failed varbit types did not decode; setting them will fail" }
        val types = VarTypes(varpCount ?: VarTypes.NONE.varpCount, varbits)
        for ((id, varbit) in varbits) {
            if (varbit.baseVar >= types.varpCount) logger.warn { "Varbit $id uses varp ${varbit.baseVar}, past the last varp" }
        }
        logger.info { "Loaded ${types.varpCount} varps and ${varbits.size} varbit types" }
        return types
    }

    /** Opcode 1 is the varbit's base varp and bit range, as RuneLite's cache VarbitLoader reads it. */
    fun decodeVarbit(data: ByteArray): VarbitType {
        val buf = Unpooled.wrappedBuffer(data)
        var type: VarbitType? = null
        while (true) {
            when (val opcode = buf.readUnsignedByte().toInt()) {
                0 -> break
                1 -> type = VarbitType(buf.readUnsignedShort(), buf.readUnsignedByte().toInt(), buf.readUnsignedByte().toInt())
                else -> error("Unknown varbit opcode $opcode")
            }
        }
        return type ?: error("Varbit has no bits (no opcode 1)")
    }
}
