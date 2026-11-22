package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.look.MakeoverCatalog
import osrsps.game.look.MakeoverPage

private val logger = KotlinLogging.logger {}

/**
 * Reads the makeover screen's pages ([MakeoverPage]) the way `makeover_draw_items` and `makeover_draw_colours` do
 * (rev 242 osrs-dumps): each style enum's outputs in key order are db rows whose column 1 is the body type A kit and
 * column 2 the body type B kit (`player_kit_id_type_a`/`_b` in every `*_styles` table); a colour enum's output count
 * is how many swatches are drawn.
 */
object MakeoverLoader {
    private const val CONFIG_INDEX = 2
    private const val ENUM_GROUP = 8
    private const val DBROW_GROUP = 38
    private const val COL_KIT_A = 1
    private const val COL_KIT_B = 2

    fun load(library: CacheLibrary): MakeoverCatalog = build(
        enum = { library.data(CONFIG_INDEX, ENUM_GROUP, it) },
        dbrow = { library.data(CONFIG_INDEX, DBROW_GROUP, it) },
    )

    fun build(enum: (Int) -> ByteArray?, dbrow: (Int) -> ByteArray?): MakeoverCatalog {
        val pages = LinkedHashMap<MakeoverPage, MakeoverCatalog.Page>()
        for (page in MakeoverPage.entries) {
            val styles = enum(page.stylesEnum)?.let(SettingsLoader::decodeEnum)
            if (styles == null) {
                logger.warn { "No enum ${page.stylesEnum} (makeover ${page.name.lowercase()}) in the cache; that page offers nothing" }
                continue
            }
            val list = styles.outputs.keys.sorted().mapNotNull { key ->
                val row = dbrow(styles.outputs.getValue(key))?.let(DbRows::decode) ?: return@mapNotNull null
                val a = row.columns[COL_KIT_A]?.firstOrNull() as? Int
                val b = row.columns[COL_KIT_B]?.firstOrNull() as? Int
                if (a == null || b == null) null else MakeoverCatalog.Style(a, b)
            }
            val colours = page.coloursEnum?.let { enum(it)?.let(SettingsLoader::decodeEnum)?.count } ?: 0
            pages[page] = MakeoverCatalog.Page(list, colours)
        }
        logger.info { "Makeover pages: " + pages.entries.joinToString { (page, p) -> "${page.name.lowercase()} ${p.styles.size} styles, ${p.colours} colours" } }
        return MakeoverCatalog(pages)
    }
}
