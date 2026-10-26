package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import osrsps.game.ConditionalOp
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SubOp
import java.nio.charset.Charset

private val logger = KotlinLogging.logger {}

/**
 * Reads NPC types (config archive 9) and their gameval names (index 24, archive 1).
 *
 * The opcode layout is RuneLite's cache NpcLoader (master, 2026-10). Every one of the 16,631 NPC types in the
 * 2026-10-14 rev 241 cache decodes with it and ends exactly on its 0 opcode. Unlike items, NPC types had no new
 * opcodes to find: 61 and 62 (4-byte model ids) already replace 1 and 60, which 241 no longer uses. Every opcode has to
 * be known, so an unknown one fails loudly instead of misreading the rest.
 */
object NpcTypeLoader {
    private const val CONFIG_INDEX = 2
    private const val NPC_GROUP = 9
    private const val GAMEVAL_INDEX = 24
    private const val GAMEVAL_NPCS = 1
    private val CP1252: Charset = Charset.forName("windows-1252")

    fun load(library: CacheLibrary): NpcTypes {
        val files = library.index(CONFIG_INDEX)?.archive(NPC_GROUP)?.files().orEmpty()
        if (files.isEmpty()) {
            logger.warn { "No NPC types in the cache; no NPCs will spawn" }
            return NpcTypes.NONE
        }
        val names = library.index(GAMEVAL_INDEX)?.archive(GAMEVAL_NPCS)
        val array = arrayOfNulls<NpcType>(files.maxOf { it.id } + 1)
        var loaded = 0
        var failed = 0
        for (file in files) {
            val data = file.data ?: continue
            try {
                val internalName = names?.file(file.id)?.data?.let { String(it, CP1252) }
                array[file.id] = decode(file.id, data, internalName)
                loaded++
            } catch (e: Exception) {
                if (failed++ == 0) logger.warn(e) { "Could not decode NPC ${file.id}" }
            }
        }
        if (failed > 0) logger.warn { "$failed NPC types did not decode; they can't be spawned" }
        logger.info { "Loaded $loaded NPC types" + if (names == null) " (no gameval names in the cache)" else "" }
        return NpcTypes(array)
    }

    fun decode(id: Int, data: ByteArray, internalName: String? = null): NpcType {
        val buf = Unpooled.wrappedBuffer(data)
        var name = NpcType.DEFAULT_NAME
        var size = 1
        val ops = NpcType.DEFAULT_OPS.toMutableList()
        var combatLevel = -1
        var standAnim = -1
        var walkAnim = -1
        var multiVarbit = -1
        var multiVarp = -1
        var multiNpcs = IntArray(0)
        var interactable = true
        var params: Map<Int, Any> = emptyMap()
        val conditionalOps = ArrayList<ConditionalOp>()
        val subOps = ArrayList<SubOp>()
        val stats = IntArray(6) { 1 }
        while (true) {
            when (val opcode = buf.readUnsignedByte().toInt()) {
                0 -> break
                1, 60 -> buf.skipBytes(buf.readUnsignedByte() * 2)
                2 -> name = buf.readString()
                12 -> size = buf.readUnsignedByte().toInt()
                13 -> standAnim = buf.readUnsignedShort()
                14 -> walkAnim = buf.readUnsignedShort()
                15, 16, 18 -> buf.skipBytes(2)
                17 -> {
                    walkAnim = buf.readUnsignedShort()
                    buf.skipBytes(6)
                }
                in 30..34 -> ops[opcode - 30] = buf.readString().takeUnless { it.equals("Hidden", ignoreCase = true) }
                40, 41 -> buf.skipBytes(buf.readUnsignedByte() * 4)
                42 -> buf.skipBytes(2)
                61, 62 -> buf.skipBytes(buf.readUnsignedByte() * 4)
                in 74..79 -> stats[opcode - 74] = buf.readUnsignedShort()
                93, 99, 109, 111, 122, 123, 129, 130, 145, 147 -> {}
                95 -> combatLevel = buf.readUnsignedShort()
                97, 98, 103, 124, 126, 146 -> buf.skipBytes(2)
                100, 101 -> buf.skipBytes(1)
                102 -> {
                    // Head icons: a bitfield, then a sprite group and index for each bit set.
                    val bits = buf.readUnsignedByte().toInt()
                    for (bit in 0 until 32 - Integer.numberOfLeadingZeros(bits)) {
                        if (bits and (1 shl bit) == 0) continue
                        if (buf.getByte(buf.readerIndex()) < 0) buf.skipBytes(4) else buf.skipBytes(2)
                        if (buf.getUnsignedByte(buf.readerIndex()) < 128) buf.skipBytes(1) else buf.skipBytes(2)
                    }
                }
                106, 118 -> {
                    multiVarbit = buf.readUnsignedShort().takeUnless { it == 0xFFFF } ?: -1
                    multiVarp = buf.readUnsignedShort().takeUnless { it == 0xFFFF } ?: -1
                    val default = if (opcode == 118) buf.readUnsignedShort().takeUnless { it == 0xFFFF } ?: -1 else -1
                    val count = buf.readUnsignedByte().toInt()
                    multiNpcs = IntArray(count + 2)
                    for (i in 0..count) multiNpcs[i] = buf.readUnsignedShort().takeUnless { it == 0xFFFF } ?: -1
                    multiNpcs[count + 1] = default
                }
                107 -> interactable = false
                114, 116 -> buf.skipBytes(2)
                115, 117 -> buf.skipBytes(8)
                249 -> params = buf.readParams()
                251 -> {
                    // A sub-option: option index, sub index, text. Layouts of 251 to 253 match RuneLite's EntityOpsLoader.
                    val index = buf.readUnsignedByte().toInt()
                    val sub = buf.readUnsignedByte().toInt()
                    subOps += SubOp(index, sub, buf.readString().takeUnless { it.equals("Hidden", ignoreCase = true) })
                }
                252 -> {
                    // An option shown only while a varp or varbit is in a range: index, varp, varbit, min, max, text.
                    val index = buf.readUnsignedByte().toInt()
                    val varp = buf.readUnsignedShort().let { if (it == 0xFFFF) -1 else it }
                    val varbit = buf.readUnsignedShort().let { if (it == 0xFFFF) -1 else it }
                    val min = buf.readInt()
                    val max = buf.readInt()
                    val text = buf.readString().takeUnless { it.equals("Hidden", ignoreCase = true) }
                    conditionalOps += ConditionalOp(index, varp, varbit, min, max, text)
                }
                253 -> {
                    // A sub-option shown only while a var is in a range: index, sub index, varp, varbit, min, max, text.
                    val index = buf.readUnsignedByte().toInt()
                    val sub = buf.readUnsignedShort()
                    val varp = buf.readUnsignedShort().let { if (it == 0xFFFF) -1 else it }
                    val varbit = buf.readUnsignedShort().let { if (it == 0xFFFF) -1 else it }
                    val min = buf.readInt()
                    val max = buf.readInt()
                    val text = buf.readString().takeUnless { it.equals("Hidden", ignoreCase = true) }
                    subOps += SubOp(index, sub, text, ConditionalOp(index, varp, varbit, min, max, text))
                }
                else -> error("Unknown NPC opcode $opcode")
            }
        }
        check(!buf.isReadable) { "${buf.readableBytes()} bytes left after the end of NPC $id" }
        return NpcType(
            id, name, size, ops, combatLevel, standAnim, walkAnim, multiVarbit, multiVarp, multiNpcs, interactable,
            params, internalName, conditionalOps, subOps, NpcStats.of(stats),
        )
    }

    private fun ByteBuf.readString(): String {
        val start = readerIndex()
        while (readByte().toInt() != 0) continue
        return toString(start, readerIndex() - start - 1, CP1252)
    }

    /** Params are typed: 1 is a string, 2 a long (new since RSMod's rev 233 reader) and anything else an int. */
    private fun ByteBuf.readParams(): Map<Int, Any> {
        val params = HashMap<Int, Any>()
        repeat(readUnsignedByte().toInt()) {
            val kind = readUnsignedByte().toInt()
            val key = readUnsignedMedium()
            params[key] = when (kind) {
                1 -> readString()
                2 -> readLong()
                else -> readInt()
            }
        }
        return params
    }
}
