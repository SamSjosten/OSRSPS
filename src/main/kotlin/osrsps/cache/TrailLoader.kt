package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.ObjTypes
import osrsps.game.trail.ClueStep
import osrsps.game.trail.StepType
import osrsps.game.trail.TrailData
import osrsps.game.trail.TrailTarget
import osrsps.game.trail.TrailTier
import osrsps.game.trail.TrailTile

private val logger = KotlinLogging.logger {}

/**
 * Reads every Treasure Trail step from the cache's own clue helper tables (the in-game clue helper Jagex added): one
 * table per step type (cluehelper_clue_anagram 4 to cluehelper_clue_skillchallenge 14) and one per kind of target
 * (cluehelper_target_npc 15 to cluehelper_target_kill 20). Table ids and column order are osrs-dumps' dump.dbtable
 * (rev 242, read 2026-11-19). A clue scroll item names its step with obj param 623 (the row) and its tier with the
 * tier's own param ([TrailTier.param]); beginner and master steps share one item each and have none.
 *
 * The clue words come from the cache at runtime, so no Jagex text is in the repository.
 */
object TrailLoader {
    /** The obj param holding a clue scroll's cluehelper row. */
    const val PARAM_STEP_ROW = 623

    const val TARGET_NPC = 15
    const val TARGET_LOC = 16
    const val TARGET_ZONE = 17
    const val TARGET_COORD = 18
    const val TARGET_KEY = 19
    const val TARGET_KILL = 20

    // Every cluehelper_clue_* table starts id, difficulty.
    private const val ID = 0
    private const val DIFFICULTY = 1

    /** The column holding the clue text, the target list and the fight, by step type (-1 for none). */
    private class Columns(val text: Int, val target: Int, val combat: Int = -1)

    private val COLUMNS = mapOf(
        StepType.ANAGRAM to Columns(text = 2, target = 3),
        StepType.MAP to Columns(text = -1, target = 2),
        StepType.CIPHER to Columns(text = 2, target = 3),
        StepType.COORDINATE to Columns(text = 2, target = 3, combat = 5),
        StepType.CRYPTIC to Columns(text = 2, target = 3),
        StepType.EMOTE to Columns(text = 2, target = 3, combat = 9),
        StepType.FAIRY_RING to Columns(text = 2, target = 5),
        StepType.FALO to Columns(text = 2, target = 3),
        StepType.HOT_COLD to Columns(text = -1, target = 2, combat = 4),
        StepType.MUSIC to Columns(text = 3, target = 4),
        StepType.SKILL_CHALLENGE to Columns(text = 2, target = 4),
    )

    fun load(library: CacheLibrary, objTypes: ObjTypes): TrailData {
        val rows = HashMap<Int, DbRow>()
        val tables = StepType.entries.map { it.table }.toSet() + (TARGET_NPC..TARGET_KILL)
        for (file in library.index(2)?.archive(38)?.files().orEmpty()) {
            val row = DbRows.decode(file.data ?: continue)
            if (row.table in tables) rows[file.id] = row
        }
        val itemRows = HashMap<Int, MutableList<Int>>()
        for (id in 0 until objTypes.size) {
            val type = objTypes[id] ?: continue
            val row = type.params[PARAM_STEP_ROW] as? Int ?: continue
            itemRows.getOrPut(row) { ArrayList() } += id
        }
        return build(rows, itemRows)
    }

    /** [rows] by row id; [itemRows] the clue items naming each row. */
    fun build(rows: Map<Int, DbRow>, itemRows: Map<Int, List<Int>>): TrailData {
        val steps = ArrayList<ClueStep>()
        for ((rowId, row) in rows.toSortedMap()) {
            val type = StepType.byTable(row.table) ?: continue
            val columns = COLUMNS.getValue(type)
            val id = row.int(ID) ?: continue
            // Difficulty 6 is X Marks the Spot's own clues: the quest counts as done, so they never come up.
            val tier = TrailTier.byDifficulty(row.int(DIFFICULTY) ?: continue) ?: continue
            val items = itemRows[rowId].orEmpty().sorted()
            // Every easy to elite step is its own item: a row no item names (X Marks the Spot's treasure scroll and
            // mysterious orb maps) can't be held.
            if (tier.sharedClue < 0 && items.isEmpty()) continue
            val targets = row.columns[columns.target].orEmpty().mapNotNull { target(rows, it as Int) }
            if (targets.isEmpty()) continue
            steps += ClueStep(
                type = type,
                id = id,
                tier = tier,
                text = if (columns.text >= 0) row.string(columns.text) else null,
                targets = targets,
                items = items,
                combat = columns.combat >= 0 && row.columns[columns.combat].orEmpty().isNotEmpty(),
            )
        }
        logger.info { "Loaded ${steps.size} Treasure Trail steps: ${steps.groupingBy { it.tier.key }.eachCount()}" }
        return TrailData(steps)
    }

    private fun target(rows: Map<Int, DbRow>, rowId: Int): TrailTarget? {
        val row = rows[rowId] ?: return null
        fun tile(column: Int) = row.int(column)?.let(TrailTile::unpack)
        return when (row.table) {
            TARGET_COORD -> TrailTarget.Dig(tile(0) ?: return null)
            TARGET_NPC -> TrailTarget.Npc(row.int(0) ?: return null, row.int(1) ?: -1, tile(2) ?: return null)
            TARGET_LOC -> TrailTarget.Loc(row.int(0) ?: return null, row.int(1) ?: -1, tile(2) ?: return null)
            TARGET_ZONE -> TrailTarget.Zone(tile(0) ?: return null, tile(1) ?: return null)
            TARGET_KEY -> TrailTarget.Key(row.int(0) ?: return null, tile(1) ?: return null, row.ints(2), row.int(3) ?: return null)
            TARGET_KILL -> TrailTarget.Kill(row.ints(0), tile(1) ?: TrailTile(0, 0, 0))
            else -> null
        }
    }

    private fun DbRow.int(column: Int): Int? = columns[column]?.firstOrNull() as? Int

    private fun DbRow.ints(column: Int): List<Int> = columns[column].orEmpty().mapNotNull { it as? Int }

    private fun DbRow.string(column: Int): String? = columns[column]?.firstOrNull() as? String
}
