package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Skill
import osrsps.game.skill.LevelUps
import osrsps.game.skill.SkillUnlocks

private val logger = KotlinLogging.logger {}

/**
 * Reads which levels unlock something from the cache's skill_features table (213): column 3 (`skill`) holds (stat, level,
 * a third int) tuples, the stat being the client's stat id. Checked against the 241 cache from abextm/osrs-cache on
 * 2026-10-29: a Woodcutting row holds (8, 72, 2).
 */
object SkillUnlockLoader {
    private const val COL_SKILL = 3
    private const val TUPLE = 3

    fun load(library: CacheLibrary): SkillUnlocks = build(DbRows.load(library, LevelUps.SKILL_FEATURES_TABLE).values)

    internal fun build(rows: Collection<DbRow>): SkillUnlocks {
        val levels = HashMap<Skill, MutableSet<Int>>()
        for (row in rows) {
            val values = row.columns[COL_SKILL] ?: continue
            for (tuple in values.chunked(TUPLE)) {
                val skill = Skill.entries.getOrNull(tuple[0] as Int) ?: continue
                levels.getOrPut(skill) { HashSet() } += tuple[1] as Int
            }
        }
        logger.info { "Loaded unlock levels for ${levels.size} skills from skill_features" }
        return SkillUnlocks(levels)
    }
}
