package osrsps.cache

import com.displee.cache.CacheLibrary
import osrsps.game.content.Destination
import osrsps.game.content.FairyRingCode
import osrsps.game.content.FairyRingCodes

/**
 * The cache's fairy ring table (dbtable 89, `fairyring`): one row for each of the 64 three-letter codes, with the dials'
 * number, the state the rings' Last-destination option shows it by, where it lands, its travel log line and entry, and two
 * flags. The client draws the travel log from the same rows; the server reads them so every code goes where the cache
 * says, and so the log's lines are the cache's own.
 */
object FairyRingLoader {
    const val TABLE = 89

    private const val COLUMN_ID = 0
    private const val COLUMN_STATE = 1
    private const val COLUMN_DEST = 2
    private const val COLUMN_CODE = 3
    private const val COLUMN_TEXT_COMPONENT = 4
    private const val COLUMN_FAVE_ICON_COMPONENT = 5
    private const val COLUMN_DESC = 8
    private const val COLUMN_APPARITION = 9
    private const val COLUMN_NO_STAFF_RETURN = 11

    fun load(library: CacheLibrary): FairyRingCodes = FairyRingCodes(DbRows.load(library, TABLE).values.mapNotNull(::code))

    /** One row as a [FairyRingCode]; null for a row without the id, state or code columns. */
    fun code(row: DbRow): FairyRingCode? {
        val id = row.columns[COLUMN_ID]?.firstOrNull() as? Int ?: return null
        val state = row.columns[COLUMN_STATE]?.firstOrNull() as? Int ?: return null
        val code = (row.columns[COLUMN_CODE]?.firstOrNull() as? String)?.replace(" ", "") ?: return null
        val coord = row.columns[COLUMN_DEST]?.firstOrNull() as? Int
        return FairyRingCode(
            id, state, code,
            coord?.let(::destination),
            row.columns[COLUMN_APPARITION]?.firstOrNull() as? Int,
            (row.columns[COLUMN_NO_STAFF_RETURN]?.firstOrNull() as? Int ?: 0) != 0,
            row.columns[COLUMN_DESC]?.firstOrNull() as? String ?: "",
            component(row, COLUMN_TEXT_COMPONENT),
            component(row, COLUMN_FAVE_ICON_COMPONENT),
        )
    }

    /** A column holding a packed component (interface in the top 16 bits) as the component's own number, or -1. */
    private fun component(row: DbRow, column: Int): Int = (row.columns[column]?.firstOrNull() as? Int)?.and(0xFFFF) ?: -1

    /**
     * A packed coord (level in the top 2 bits, x in the next 14, z in the low 14) as a landing, or null for the two
     * the unused codes hold: 1_1_1_1_1 (65, 65 on level 1) and 0_0_0_0_0, which DIQ, the player-owned house's ring, has.
     */
    fun destination(coord: Int): Destination? {
        val d = Destination((coord ushr 14) and 0x3FFF, coord and 0x3FFF, coord ushr 28)
        return d.takeUnless { it == UNUSED || it == NOWHERE }
    }

    private val UNUSED = Destination(65, 65, 1)
    private val NOWHERE = Destination(0, 0, 0)
}
