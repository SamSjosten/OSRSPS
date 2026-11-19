package osrsps.game.collection

/**
 * The collection log's tabs, pages and items, as the cache lists them for the log window's client scripts (read by
 * [osrsps.cache.CollectionLogLoader]). Every item is tracked by itself, not by where it came from, so an item on several
 * pages fills its slot on each (the OSRS Wiki's Collection log: "Each entry in the log is tracked by the item itself,
 * not where it was obtained").
 */
class CollectionLogCatalog(
    val tabs: List<Tab>,
    /** enum_3721: the dummy obj a page shows, and counts in its transmit inv, in place of the item obtained. */
    private val displayed: Map<Int, Int> = emptyMap(),
) {
    /** A tab: Bosses, Raids, Clues, Minigames or Other, at its [index] (struct param 684), with its [pages] in order. */
    class Tab(val index: Int, val struct: Int, val pages: List<Page>) {
        /** Every item on the tab's pages, each once. */
        val items: Set<Int> = pages.flatMapTo(LinkedHashSet()) { it.items }
    }

    /**
     * A page (a "category" to the client scripts): its [struct], its place on its tab, its [items] in the order the
     * window draws them, and the varbit `collection_<tab>_<page>_completed` that turns its name green once all are
     * obtained (script4103), or null when the cache has none for it.
     */
    class Page(val struct: Int, val tab: Int, val index: Int, val items: List<Int>, val completedVarbit: Int?)

    /** Every item the log tracks, each once. */
    val items: Set<Int> = tabs.flatMapTo(LinkedHashSet()) { it.items }

    private val pagesByItem: Map<Int, List<Page>> = buildMap<Int, MutableList<Page>> {
        for (tab in tabs) for (page in tab.pages) for (item in page.items.toSet()) getOrPut(item) { ArrayList() } += page
    }

    private val pagesByStruct: Map<Int, Page> = tabs.flatMap { it.pages }.associateBy { it.struct }

    /** The pages [item] is on; empty for an item the log doesn't track. */
    fun pagesOf(item: Int): List<Page> = pagesByItem[item].orEmpty()

    fun page(struct: Int): Page? = pagesByStruct[struct]

    operator fun contains(item: Int): Boolean = item in pagesByItem

    /** The obj the window shows for [item]: its dummy where enum_3721 has one (the Unsired, the gem bag), else itself. */
    fun displayed(item: Int): Int = displayed[item] ?: item

    companion object {
        val NONE = CollectionLogCatalog(emptyList())
    }
}
