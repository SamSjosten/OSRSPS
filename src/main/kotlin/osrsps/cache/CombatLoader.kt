package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.SeqNames
import osrsps.game.combat.AttackStyles

private val logger = KotlinLogging.logger {}

/** Reads what combat takes from the cache: the combat tab's attack styles, and animation names for the data files. */
object CombatLoader {
    private const val GAMEVAL_INDEX = 24
    private const val GAMEVAL_SEQS = 7

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

    /** Every seq's gameval name, or [SeqNames.NONE] when the cache has no gameval index. */
    fun seqNames(library: CacheLibrary): SeqNames {
        val archive = library.index(GAMEVAL_INDEX)?.archive(GAMEVAL_SEQS) ?: return SeqNames.NONE
        val ids = HashMap<String, Int>()
        for (file in archive.files()) {
            val data = file.data ?: continue
            ids[GameFrameCheck.decodeName(data)] = file.id
        }
        return SeqNames(ids)
    }
}
