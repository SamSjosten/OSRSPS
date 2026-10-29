package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import java.nio.charset.Charset

private val logger = KotlinLogging.logger {}

/**
 * Reads item types (config archive 10) and their gameval names (index 24, archive 0).
 *
 * The opcode layouts follow RuneLite's cache ItemLoader and RSMod's ObjTypeDecoder (rev 233), plus what the rev 241
 * cache needed beyond them, found by decoding all 34,646 items in the 2026-10-14 cache (and 34,732 in the 2026-10-20 one) until every one ended exactly
 * on its 0 opcode:
 * - 44 is the inventory model and 45 to 54 the worn and head models as 4-byte ints, replacing 1, 23 to 26, 78, 79 and
 *   90 to 93 (none of which the 241 cache uses). 45 and 48 carry the offset byte as 23 and 25 did. Which model each
 *   one is is inferred from the old opcodes' order; only the sizes are checked.
 * - 15, 160 and 251 carry no data. 251 is on exactly the items with 65 (tradeable). What they mean is unknown.
 * - 161 is a list of 2-byte values with a 2-byte count, read from the 241.4 cache (2026-10-20), where only the
 *   Fractured Archive's Obligator and Zorya's tome (charged and uncharged) have it, each with 14 values near 15,100.
 *   What the values are is unknown; a 1-byte 0 then a 1-byte count would read the same on those four items.
 * - 94 is the item's category, as in RuneLite's ItemLoader. It groups weapons by kind (every axe and battleaxe is
 *   35), which [osrsps.game.Weapons] maps to the combat tab's weapon categories.
 * Every other opcode has to be known too, so an unknown one fails loudly instead of misreading the rest.
 */
object ObjTypeLoader {
    private const val CONFIG_INDEX = 2
    private const val OBJ_GROUP = 10
    private const val GAMEVAL_INDEX = 24
    private const val GAMEVAL_OBJS = 0
    private val CP1252: Charset = Charset.forName("windows-1252")

    /**
     * The examine text of every noted item. The cache has none for notes (the client builds them from the note
     * template), so it comes from the server. From memory of live, not checked against a recording.
     */
    const val CERT_DESC = "Swap this note at any bank for the equivalent item."

    fun load(library: CacheLibrary): ObjTypes {
        val files = library.index(CONFIG_INDEX)?.archive(OBJ_GROUP)?.files().orEmpty()
        if (files.isEmpty()) {
            logger.warn { "No item types in the cache; ::item and the inventory will know no items" }
            return ObjTypes.NONE
        }
        val names = library.index(GAMEVAL_INDEX)?.archive(GAMEVAL_OBJS)
        val decoded = HashMap<Int, ObjType>()
        var failed = 0
        for (file in files) {
            val data = file.data ?: continue
            try {
                val internalName = names?.file(file.id)?.data?.let { String(it, CP1252) }
                decoded[file.id] = decode(file.id, data, internalName)
            } catch (e: Exception) {
                if (failed++ == 0) logger.warn(e) { "Could not decode item ${file.id}" }
            }
        }
        if (failed > 0) logger.warn { "$failed item types did not decode; they can't be used" }
        val types = link(decoded)
        logger.info { "Loaded ${decoded.size} item types" + if (names == null) " (no gameval names in the cache)" else "" }
        return types
    }

    /**
     * Fills in noted items and placeholders from the items they stand for, as the client does. Only from an item that
     * links back: the client doesn't check, but a one-way link would give a note another item's value to sell at.
     */
    fun link(decoded: Map<Int, ObjType>): ObjTypes {
        val array = arrayOfNulls<ObjType>((decoded.keys.maxOrNull() ?: -1) + 1)
        for ((id, type) in decoded) {
            array[id] = when {
                type.isCert -> decoded[type.certlink]?.takeIf { !it.isCert && it.certlink == id }?.let { cert(type, it) } ?: type
                type.isPlaceholder ->
                    decoded[type.placeholderlink]?.takeIf { !it.isPlaceholder && it.placeholderlink == id }?.let { placeholder(type, it) } ?: type
                else -> type
            }
        }
        return ObjTypes(array)
    }

    /** The client's genCert takes the name, value and members flag from the real item and makes the note stack. */
    private fun cert(note: ObjType, item: ObjType) = copy(
        note,
        name = item.name,
        desc = CERT_DESC,
        cost = item.cost,
        stackable = true,
        members = item.members,
        tradeable = item.tradeable,
    )

    /** Inferred from the client's genPlaceholder: the placeholder has the item's name and nothing else of it. */
    private fun placeholder(placeholder: ObjType, item: ObjType) = copy(placeholder, name = item.name, cost = 0)

    private fun copy(
        type: ObjType,
        name: String,
        desc: String? = type.desc,
        cost: Int,
        stackable: Boolean = type.stackable,
        members: Boolean = type.members,
        tradeable: Boolean = type.tradeable,
    ) = ObjType(
        id = type.id, name = name, desc = desc, cost = cost, stackable = stackable, members = members,
        tradeable = tradeable, ops = type.ops, iops = type.iops, wearpos1 = type.wearpos1, wearpos2 = type.wearpos2,
        wearpos3 = type.wearpos3, weight = type.weight, certlink = type.certlink, certtemplate = type.certtemplate,
        placeholderlink = type.placeholderlink, placeholdertemplate = type.placeholdertemplate, params = type.params,
        internalName = type.internalName, category = type.category,
    )

    fun decode(id: Int, data: ByteArray, internalName: String? = null): ObjType {
        val buf = Unpooled.wrappedBuffer(data)
        var name = ObjType.DEFAULT_NAME
        var desc: String? = null
        var cost = 1
        var stackable = false
        var members = false
        var tradeable = false
        val ops = ObjType.DEFAULT_OPS.toMutableList()
        val iops = ObjType.DEFAULT_IOPS.toMutableList()
        var wearpos1 = -1
        var wearpos2 = -1
        var wearpos3 = -1
        var weight = 0
        var certlink = -1
        var certtemplate = -1
        var placeholderlink = -1
        var placeholdertemplate = -1
        var params: Map<Int, Any> = emptyMap()
        var category = -1
        while (true) {
            when (val opcode = buf.readUnsignedByte().toInt()) {
                0 -> break
                94 -> category = buf.readUnsignedShort()
                1, 4, 5, 6, 7, 8, 24, 26, 78, 79, in 90..93, 95 -> buf.skipBytes(2)
                2 -> name = buf.readString()
                3 -> desc = buf.readString()
                9 -> buf.readString()
                11 -> stackable = true
                12 -> cost = buf.readInt()
                13 -> wearpos1 = buf.readUnsignedByte().toInt()
                14 -> wearpos2 = buf.readUnsignedByte().toInt()
                15, 160, 251 -> {}
                16 -> members = true
                23, 25 -> buf.skipBytes(3)
                27 -> wearpos3 = buf.readUnsignedByte().toInt()
                in 30..34 -> ops[opcode - 30] = buf.readString().takeUnless { it.equals("Hidden", ignoreCase = true) }
                in 35..39 -> iops[opcode - 35] = buf.readString()
                40, 41 -> buf.skipBytes(buf.readUnsignedByte() * 4)
                42 -> buf.skipBytes(1)
                43 -> {
                    // Sub-options of one inventory option: its index, then (index + 1, text) pairs ending with 0.
                    buf.skipBytes(1)
                    while (buf.readUnsignedByte().toInt() != 0) buf.readString()
                }
                44, 46, 47, 49, 50, in 51..54 -> buf.skipBytes(4)
                45, 48 -> buf.skipBytes(5)
                65 -> tradeable = true
                75 -> weight = buf.readShort().toInt()
                97 -> certlink = buf.readUnsignedShort()
                98 -> certtemplate = buf.readUnsignedShort()
                in 100..109 -> buf.skipBytes(4)
                110, 111, 112 -> buf.skipBytes(2)
                113, 114, 115 -> buf.skipBytes(1)
                139, 140 -> buf.skipBytes(2)
                161 -> buf.skipBytes(buf.readUnsignedShort() * 2)
                148 -> placeholderlink = buf.readUnsignedShort()
                149 -> placeholdertemplate = buf.readUnsignedShort()
                249 -> params = buf.readParams()
                else -> error("Unknown item opcode $opcode")
            }
        }
        check(!buf.isReadable) { "${buf.readableBytes()} bytes left after the end of item $id" }
        return ObjType(
            id, name, desc, cost, stackable, members, tradeable, ops, iops, wearpos1, wearpos2, wearpos3, weight,
            certlink, certtemplate, placeholderlink, placeholdertemplate, params, internalName, category,
        )
    }

    private fun ByteBuf.readString(): String {
        val start = readerIndex()
        while (readByte().toInt() != 0) continue
        return toString(start, readerIndex() - start - 1, CP1252)
    }

    private fun ByteBuf.readParams(): Map<Int, Any> {
        val params = HashMap<Int, Any>()
        repeat(readUnsignedByte().toInt()) {
            val isString = readUnsignedByte().toInt() == 1
            val key = readUnsignedMedium()
            params[key] = if (isString) readString() else readInt()
        }
        return params
    }
}
