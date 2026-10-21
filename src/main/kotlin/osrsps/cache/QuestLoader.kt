package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import osrsps.game.Quest
import osrsps.game.QuestVar
import osrsps.game.Quests

private val logger = KotlinLogging.logger {}

/**
 * Reads the quests the same way the client's quest list does:
 * - the quest db table (table 0, rows in config archive 38) gives each quest's name, end state and quest points;
 * - client script 4024 (quest_progress_get) is a switch on the quest's row that returns its progress varp or varbit.
 * Script 4029 (quest_status_get) calls 4024 and counts the quest finished once that value reaches the end state.
 * Checked by decoding both from the live 241 cache on 2026-10-21: Cook's Assistant is varp 29 (cookquest), end 2.
 */
object QuestLoader {
    private const val CONFIG_INDEX = 2
    private const val DBROW_GROUP = 38
    private const val CLIENTSCRIPT_INDEX = 12
    private const val PROGRESS_SCRIPT = 4024

    const val QUEST_TABLE = 0
    private const val COL_DISPLAYNAME = 2
    private const val COL_RELEASE_TYPE = 3
    private const val COL_QUESTPOINTS = 17
    private const val COL_ENDSTATE = 19

    /**
     * Release types whose quests are left alone. Script 4029 reports type 2 as not started, so it is not out yet
     * (Crab Quest in 241). Type 0 is only set on three rows named "." in 241; inferred to be placeholders.
     */
    private val SKIPPED_RELEASE_TYPES = setOf(0, 2)

    fun load(library: CacheLibrary): Quests {
        val script = library.data(CLIENTSCRIPT_INDEX, PROGRESS_SCRIPT, 0)
        if (script == null) {
            logger.warn { "No client script $PROGRESS_SCRIPT in the cache; quests will not be set complete" }
            return Quests.NONE
        }
        val progressVars = decodeProgressSwitch(script)
        val rows = HashMap<Int, DbRow>()
        for (file in library.index(CONFIG_INDEX)?.archive(DBROW_GROUP)?.files().orEmpty()) {
            val row = decodeDbRow(file.data ?: continue)
            if (row.table == QUEST_TABLE) rows[file.id] = row
        }
        return Quests(buildQuests(rows, progressVars))
    }

    internal fun buildQuests(rows: Map<Int, DbRow>, progressVars: Map<Int, QuestVar>): List<Quest> {
        val quests = ArrayList<Quest>()
        for ((rowId, row) in rows.toSortedMap()) {
            val name = row.columns[COL_DISPLAYNAME]?.firstOrNull() as? String ?: "row $rowId"
            val releaseType = row.columns[COL_RELEASE_TYPE]?.firstOrNull() as? Int
            if (releaseType in SKIPPED_RELEASE_TYPES) continue
            val progress = progressVars[rowId]
            val end = row.columns[COL_ENDSTATE]?.firstOrNull() as? Int
            if (progress == null || end == null) {
                logger.warn { "Quest $name (row $rowId) has no progress var or end state; left unfinished" }
                continue
            }
            val points = row.columns[COL_QUESTPOINTS]?.firstOrNull() as? Int ?: 0
            quests += Quest(rowId, name, progress, end, points)
        }
        logger.info { "Loaded ${quests.size} quests worth ${quests.sumOf { it.questPoints }} quest points" }
        return quests
    }

    /** A db row: its table and each column's values, tuples flattened. */
    data class DbRow(val table: Int, val columns: Map<Int, List<Any>>)

    /** As RuneLite's cache DBRowLoader reads it. */
    fun decodeDbRow(data: ByteArray): DbRow {
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
                            for (type in types) values += if (type == TYPE_STRING) buf.readString() else buf.readInt()
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

    /**
     * Reads script 4024's switch: each case is `get_varp n` or `get_varbit n` then `return`. Anything else in a case
     * fails loudly, as the script would then no longer be a plain lookup.
     */
    fun decodeProgressSwitch(data: ByteArray): Map<Int, QuestVar> {
        val script = ClientScript.decode(data)
        val switch = script.switches.singleOrNull() ?: error("Script $PROGRESS_SCRIPT has ${script.switches.size} switches, expected 1")
        val switchAt = script.opcodes.indexOf(ClientScript.SWITCH)
        val result = HashMap<Int, QuestVar>()
        for ((key, offset) in switch) {
            val at = switchAt + 1 + offset
            val operand = script.intOperands[at]
            result[key] = when (script.opcodes[at]) {
                ClientScript.GET_VARP -> QuestVar.Varp(operand)
                ClientScript.GET_VARBIT -> QuestVar.Varbit(operand)
                else -> error("Script $PROGRESS_SCRIPT case $key starts with opcode ${script.opcodes[at]}, not a var read")
            }
            check(script.opcodes[at + 1] == ClientScript.RETURN) { "Script $PROGRESS_SCRIPT case $key does more than return its var" }
        }
        return result
    }

    private const val TYPE_STRING = 36

    private fun ByteBuf.readUnsignedSmart(): Int =
        if (getUnsignedByte(readerIndex()) < 128) readUnsignedByte().toInt() else readUnsignedShort() - 0x8000

    private fun ByteBuf.readVarInt(): Int {
        var b = readUnsignedByte().toInt()
        var value = b and 0x7F
        while (b and 0x80 != 0) {
            b = readUnsignedByte().toInt()
            value = (value shl 7) or (b and 0x7F)
        }
        return value
    }

    private fun ByteBuf.readString(): String {
        val end = indexOf(readerIndex(), writerIndex(), 0)
        check(end >= 0) { "Unterminated string" }
        val s = toString(readerIndex(), end - readerIndex(), Charsets.ISO_8859_1)
        readerIndex(end + 1)
        return s
    }
}

/**
 * Just enough of a client script to read its instructions and switch tables. Layout as RuneLite's cache
 * ScriptLoader reads it, with the 241 trailer: instruction count, then six u16 local/argument counts (int, object,
 * long), then the switch tables, then their length.
 */
class ClientScript(val opcodes: IntArray, val intOperands: IntArray, val switches: List<Map<Int, Int>>) {
    companion object {
        const val GET_VARP = 1
        const val SCONST = 3
        const val RETURN = 21
        const val GET_VARBIT = 25
        const val POP_INT = 38
        const val POP_OBJECT = 39
        const val SWITCH = 60

        fun decode(data: ByteArray): ClientScript {
            val buf = Unpooled.wrappedBuffer(data)
            val switchLength = buf.getUnsignedShort(data.size - 2)
            val trailer = data.size - 2 - switchLength - 16
            val count = buf.getInt(trailer)
            val switches = ArrayList<Map<Int, Int>>()
            if (switchLength > 0) {
                buf.readerIndex(trailer + 16)
                repeat(buf.readUnsignedByte().toInt()) {
                    val cases = LinkedHashMap<Int, Int>()
                    repeat(buf.readUnsignedShort()) { cases[buf.readInt()] = buf.readInt() }
                    switches += cases
                }
            }
            // The script's name comes first; cache scripts have none, just the terminator.
            buf.readerIndex(buf.indexOf(0, trailer, 0) + 1)
            val opcodes = IntArray(count)
            val operands = IntArray(count)
            for (i in 0 until count) {
                val opcode = buf.readUnsignedShort()
                opcodes[i] = opcode
                when {
                    opcode == SCONST -> buf.readerIndex(buf.indexOf(buf.readerIndex(), trailer, 0) + 1)
                    opcode < 100 && opcode != RETURN && opcode != POP_INT && opcode != POP_OBJECT -> operands[i] = buf.readInt()
                    else -> operands[i] = buf.readUnsignedByte().toInt()
                }
            }
            check(buf.readerIndex() == trailer) { "Script instructions end at ${buf.readerIndex()}, not $trailer" }
            return ClientScript(opcodes, operands, switches)
        }
    }
}
