package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Skill
import osrsps.game.skill.SlayerData
import osrsps.game.skill.SlayerMasterTask
import osrsps.game.skill.SlayerTask
import osrsps.game.skill.SlayerUnlock

private val logger = KotlinLogging.logger {}

/**
 * Reads Slayer's tables from the cache's db rows. Table ids and column order are osrs-dumps' dump.dbtable (rev 242,
 * read 2026-11-15): slayer_task 113, slayer_master_task 114, slayer_unlock 117. A column the cache leaves out of a row
 * takes the table's default (min_comlevel 0). Columns osrs-dumps lists without types (slayer_tip, task_sublist, the
 * quest columns) are the server's own and aren't in the cache.
 */
object SlayerLoader {
    const val TASK_TABLE = 113
    const val MASTER_TASK_TABLE = 114
    const val UNLOCK_TABLE = 117

    // slayer_task's columns.
    private const val TASK_ID = 0
    private const val TASK_MIN_COMBAT = 1
    private const val TASK_STATS_ALL = 2
    private const val TASK_STATS_ANY = 3
    private const val TASK_NAME_LOWER = 9
    private const val TASK_NAME_UPPER = 10
    private const val TASK_EXTENSION = 15
    private const val TASK_BLOCK_UNLOCK = 18

    // slayer_master_task's.
    private const val MASTER_ID = 0
    private const val MASTER_TASK = 1
    private const val MASTER_WEIGHT = 2
    private const val MASTER_MIN = 3
    private const val MASTER_MAX = 4
    private const val MASTER_AREAS = 5
    private const val MASTER_UNLOCK = 6

    // slayer_unlock's.
    private const val UNLOCK_BIT = 0
    private const val UNLOCK_COST = 1

    fun load(library: CacheLibrary): SlayerData =
        build(DbRows.load(library, TASK_TABLE), DbRows.load(library, MASTER_TASK_TABLE), DbRows.load(library, UNLOCK_TABLE))

    internal fun build(taskRows: Map<Int, DbRow>, masterRows: Map<Int, DbRow>, unlockRows: Map<Int, DbRow>): SlayerData {
        val tasks = HashMap<Int, SlayerTask>()
        for ((row, db) in taskRows) {
            val id = db.int(TASK_ID) ?: continue
            // Kree'arra's and the other boss rows have id -1: they are bosses picked inside a boss task, not tasks.
            if (id < 0) continue
            tasks[row] = SlayerTask(
                row = row,
                id = id,
                nameLower = db.string(TASK_NAME_LOWER) ?: continue,
                nameUpper = db.string(TASK_NAME_UPPER) ?: continue,
                minCombat = db.int(TASK_MIN_COMBAT) ?: 0,
                statsAll = db.stats(TASK_STATS_ALL),
                statsAny = db.stats(TASK_STATS_ANY),
                blockedBy = db.int(TASK_BLOCK_UNLOCK),
                extensions = db.columns[TASK_EXTENSION].orEmpty().chunked(3).associate { (unlock, min, max) ->
                    unlock as Int to (min as Int)..(max as Int)
                },
            )
        }
        val masterTasks = ArrayList<SlayerMasterTask>()
        for ((_, db) in masterRows.toSortedMap()) {
            val task = tasks[db.int(MASTER_TASK) ?: continue] ?: continue
            masterTasks += SlayerMasterTask(
                master = db.int(MASTER_ID) ?: continue,
                task = task,
                weight = db.int(MASTER_WEIGHT) ?: continue,
                min = db.int(MASTER_MIN) ?: continue,
                max = db.int(MASTER_MAX) ?: continue,
                areas = db.columns[MASTER_AREAS].orEmpty().map { it as Int },
                unlockedBy = db.int(MASTER_UNLOCK),
            )
        }
        val unlocks = unlockRows.mapNotNull { (row, db) ->
            SlayerUnlock(row, db.int(UNLOCK_BIT) ?: return@mapNotNull null, db.int(UNLOCK_COST) ?: return@mapNotNull null)
        }
        logger.info { "Loaded ${tasks.size} Slayer tasks, ${masterTasks.size} master assignments and ${unlocks.size} rewards" }
        return SlayerData(tasks.values, masterTasks, unlocks)
    }

    private fun DbRow.int(column: Int): Int? = columns[column]?.firstOrNull() as? Int

    private fun DbRow.string(column: Int): String? = columns[column]?.firstOrNull() as? String

    /** (stat, level) tuples; the stat is the client's stat id, [Skill]'s order. */
    private fun DbRow.stats(column: Int): List<Pair<Skill, Int>> = columns[column].orEmpty().chunked(2).mapNotNull { (level, stat) ->
        Skill.entries.getOrNull(stat as Int)?.let { it to level as Int }
    }
}
