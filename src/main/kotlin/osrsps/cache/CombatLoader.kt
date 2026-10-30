package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.SeqNames
import osrsps.game.SpotanimNames
import osrsps.game.combat.AttackStyles
import osrsps.game.combat.SpecialAttacks

private val logger = KotlinLogging.logger {}

/**
 * Reads what combat takes from the cache: the combat tab's attack styles, and animation and spotanim names for the
 * data files.
 */
object CombatLoader {
    private const val GAMEVAL_INDEX = 24
    private const val CONFIG_INDEX = 2
    private const val ENUM_GROUP = 8
    private const val GAMEVAL_SEQS = 7
    private const val GAMEVAL_SPOTANIMS = 8

    /** dbtable combat_interface_weapon_category, which the combat tab reads its buttons from. */
    const val WEAPON_CATEGORY_TABLE = 78
    private const val COL_CATEGORY = 0
    private const val COL_BUTTONS = 1

    /** The attack styles of every weapon category, or [AttackStyles.DEFAULT] when the cache has no such table. */
    fun attackStyles(library: CacheLibrary): AttackStyles {
        val rows = DbRows.load(library, WEAPON_CATEGORY_TABLE)
        if (rows.isEmpty()) {
            logger.warn { "No dbtable $WEAPON_CATEGORY_TABLE in the cache; only unarmed attack styles are known" }
            return AttackStyles.DEFAULT
        }
        val problems = ArrayList<String>()
        val styles = AttackStyles.fromRows(rows.values.map { it.columns[COL_CATEGORY] to it.columns[COL_BUTTONS] }, problems)
        problems.forEach { logger.warn { "Attack styles: $it" } }
        logger.info { "Loaded the attack styles of ${styles.categories} weapon categories" }
        return styles
    }

    /**
     * Each weapon's special attack cost in tenths of a percent, by obj id: the enum the combat tab reads its bar from
     * ([SpecialAttacks.COST_ENUM]). Empty, so no weapon has a special, when the cache has no such enum.
     */
    fun specialAttackCosts(library: CacheLibrary): Map<Int, Int> {
        val data = library.data(CONFIG_INDEX, ENUM_GROUP, SpecialAttacks.COST_ENUM)
        if (data == null) {
            logger.warn { "No enum ${SpecialAttacks.COST_ENUM} in the cache; no weapon has a special attack" }
            return emptyMap()
        }
        val costs = SettingsLoader.decodeEnum(data).outputs
        logger.info { "Loaded the special attack costs of ${costs.size} weapons" }
        return costs
    }

    /** Every seq's gameval name, or [SeqNames.NONE] when the cache has no gameval index. */
    fun seqNames(library: CacheLibrary): SeqNames = SeqNames(names(library, GAMEVAL_SEQS))

    /** Every spotanim's gameval name, or [SpotanimNames.NONE] when the cache has no gameval index. */
    fun spotanimNames(library: CacheLibrary): SpotanimNames = SpotanimNames(names(library, GAMEVAL_SPOTANIMS))

    private fun names(library: CacheLibrary, group: Int): Map<String, Int> {
        val archive = library.index(GAMEVAL_INDEX)?.archive(group) ?: return emptyMap()
        val ids = HashMap<String, Int>()
        for (file in archive.files()) {
            val data = file.data ?: continue
            ids[GameFrameCheck.decodeName(data)] = file.id
        }
        return ids
    }
}
