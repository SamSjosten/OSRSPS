package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.collection.CollectionLogCatalog

private val logger = KotlinLogging.logger {}

/**
 * Reads the collection log's tabs, pages and items from the cache the way the log window's client scripts do
 * (Joshua-F/osrs-dumps, rev 242, 2026-11-18):
 * - collection_draw_tabs_all draws the five tabs from structs 471 to 475 (Bosses, Raids, Clues, Minigames, Other),
 *   each with its index in param 684 and its pages' enum in param 683;
 * - collection_draw_list walks a tab's page enum by index for each page's struct; collection_draw_log walks the page's
 *   param 690 enum by index for its items, swapping in enum_3721's dummy where it has one;
 * - script4103 is a switch on the page struct whose cases read the page's `_completed` varbit.
 */
object CollectionLogLoader {
    private const val CONFIG_INDEX = 2
    private const val CLIENTSCRIPT_INDEX = 12
    private const val ENUM_GROUP = 8
    private const val STRUCT_GROUP = 34

    /** struct_471 to struct_475, the tabs collection_draw_tabs_all draws. */
    val TAB_STRUCTS = 471..475
    const val PARAM_TAB_PAGES = 683
    const val PARAM_TAB_INDEX = 684
    const val PARAM_PAGE_ITEMS = 690
    const val DISPLAYED_ENUM = 3721
    const val COMPLETED_GETTER = 4103

    fun load(library: CacheLibrary): CollectionLogCatalog {
        val catalog = build(
            enum = { library.data(CONFIG_INDEX, ENUM_GROUP, it) },
            struct = { library.data(CONFIG_INDEX, STRUCT_GROUP, it) },
            script = { library.data(CLIENTSCRIPT_INDEX, it, 0) },
        )
        val pages = catalog.tabs.flatMap { it.pages }
        logger.info {
            "Loaded the collection log: ${catalog.tabs.size} tabs, ${pages.size} pages, ${pages.sumOf { it.items.size }} slots, " +
                "${catalog.items.size} items, ${pages.count { it.completedVarbit != null }} pages' completed varbits"
        }
        return catalog
    }

    fun build(enum: (Int) -> ByteArray?, struct: (Int) -> ByteArray?, script: (Int) -> ByteArray?): CollectionLogCatalog {
        fun enumType(id: Int) = enum(id)?.let(SettingsLoader::decodeEnum)
        val completed = script(COMPLETED_GETTER)?.let(::decodeCompleted) ?: emptyMap<Int, Int>().also {
            logger.warn { "No client script $COMPLETED_GETTER in the cache; no collection log page will turn green" }
        }
        val tabs = TAB_STRUCTS.mapNotNull { tabStruct ->
            val params = struct(tabStruct)?.let(SettingsLoader::decodeStructInts) ?: return@mapNotNull null
            val index = params[PARAM_TAB_INDEX] ?: return@mapNotNull null
            val pageEnum = params[PARAM_TAB_PAGES]?.let(::enumType) ?: return@mapNotNull null
            val pages = (0 until pageEnum.count).mapNotNull { at ->
                val pageStruct = pageEnum.outputs[at] ?: return@mapNotNull null
                val itemEnum = struct(pageStruct)?.let(SettingsLoader::decodeStructInts)?.get(PARAM_PAGE_ITEMS)?.let(::enumType)
                val items = if (itemEnum == null) emptyList() else (0 until itemEnum.count).mapNotNull { itemEnum.outputs[it] }
                CollectionLogCatalog.Page(pageStruct, index, at, items, completed[pageStruct])
            }
            CollectionLogCatalog.Tab(index, tabStruct, pages)
        }
        if (tabs.isEmpty()) logger.warn { "No collection log tabs (structs $TAB_STRUCTS) in the cache; nothing will be logged" }
        val displayed = enumType(DISPLAYED_ENUM)?.outputs.orEmpty()
        return CollectionLogCatalog(tabs, displayed)
    }

    /** script4103's cases, page struct to varbit: each `case struct_n: return(%varbit)` is get_varbit then return. */
    internal fun decodeCompleted(data: ByteArray): Map<Int, Int> {
        val script = ClientScript.decode(data)
        val switch = script.switches.singleOrNull() ?: return emptyMap()
        val switchAt = script.opcodes.indexOf(ClientScript.SWITCH)
        val ops = script.opcodes
        return buildMap {
            for ((page, offset) in switch) {
                val at = switchAt + 1 + offset
                if (at + 1 < ops.size && ops[at] == ClientScript.GET_VARBIT && ops[at + 1] == ClientScript.RETURN) put(page, script.intOperands[at])
            }
        }
    }
}
