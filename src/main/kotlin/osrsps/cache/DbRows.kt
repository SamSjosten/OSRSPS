package osrsps.cache

import com.displee.cache.CacheLibrary
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

/** A db row: its table and each column's values, tuples flattened. */
data class DbRow(val table: Int, val columns: Map<Int, List<Any>>)

/**
 * Db rows, config archive 38: the client's own data tables (quests, the combat tab's attack styles, ...). Each row
 * names its table and holds typed columns.
 */
object DbRows {
    private const val CONFIG_INDEX = 2
    private const val DBROW_GROUP = 38

    private const val TYPE_STRING = 36
    private const val TYPE_LONG = 49

    /** Every row of [table], by row id. */
    fun load(library: CacheLibrary, table: Int): Map<Int, DbRow> {
        val rows = HashMap<Int, DbRow>()
        for (file in library.index(CONFIG_INDEX)?.archive(DBROW_GROUP)?.files().orEmpty()) {
            val row = decode(file.data ?: continue)
            if (row.table == table) rows[file.id] = row
        }
        return rows
    }

    /** As RuneLite's cache DBRowLoader reads it. */
    fun decode(data: ByteArray): DbRow {
        val buf = Unpooled.wrappedBuffer(data)
        var table = -1
        val columns = HashMap<Int, List<Any>>()
        while (true) {
            when (val opcode = buf.readUnsignedByte().toInt()) {
                0 -> break
                3 -> {
                    buf.skipBytes(1) // column count
                    while (true) {
                        val column = buf.readUnsignedByte().toInt()
                        if (column == 255) break
                        val types = IntArray(buf.readUnsignedByte().toInt()) { buf.readUnsignedSmart() }
                        val values = ArrayList<Any>()
                        repeat(buf.readUnsignedSmart()) {
                            for (type in types) {
                                values += when (type) {
                                    TYPE_STRING -> buf.readString()
                                    TYPE_LONG -> buf.readLong()
                                    else -> buf.readInt()
                                }
                            }
                        }
                        columns[column] = values
                    }
                }
                4 -> table = buf.readVarInt()
                else -> error("Unknown db row opcode $opcode")
            }
        }
        return DbRow(table, columns)
    }

    private fun ByteBuf.readUnsignedSmart(): Int =
        if (getUnsignedByte(readerIndex()) < 128) readUnsignedByte().toInt() else readUnsignedShort() - 0x8000

    /**
     * Seven bits a byte, lowest first, as RuneLite's readVarInt2 reads it: table 213 is `d5 01`. Read highest first it
     * came out as 10881, which only tables under 128 (all this loader read before) got away with.
     */
    private fun ByteBuf.readVarInt(): Int {
        var value = 0
        var shift = 0
        while (true) {
            val b = readUnsignedByte().toInt()
            value = value or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return value
            shift += 7
        }
    }

    private fun ByteBuf.readString(): String {
        val end = indexOf(readerIndex(), writerIndex(), 0)
        check(end >= 0) { "Unterminated string" }
        val s = toString(readerIndex(), end - readerIndex(), Charsets.ISO_8859_1)
        readerIndex(end + 1)
        return s
    }
}
