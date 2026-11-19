package osrsps.game.collection

import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.Player

/**
 * The collection log's windows (collection log step 2): the log, `collection` (621), and its overview,
 * `collection_overview` (908), both in the main modal slot. The client draws them from the cache and the vars
 * [CollectionLog] keeps; the server says which page to draw and sends what it needs for it (rev 242 osrs-dumps).
 *
 * The log:
 * - Opening it runs `collection_draw` (7797) with the tab's components and struct and the page; the client draws the
 *   tabs, the page list and the page. Each tab's page list is `<tab>_background`'s children, whose op1 "Check" needs
 *   events from the server. A tab's button (`<tab>_tab`, static op1 "View") opens its first page.
 * - A page: the transmit inv `collection_transmit` (620) holds what of it the player has, by the obj the page shows
 *   (`enum_3721`'s dummies) with its count; `collection_draw_log` reads the counts from it. The kill counts above it are
 *   `collection_category_count`, `2` and `3` (2048, 2941, 2942), set from the boss's own kill count varps ([KILL_COUNTS]);
 *   then `collection_draw_list` (2730) redraws the list with the page picked. INFERRED: the inv holds the page only.
 * - Search (`search_toggle`, and the search bar, transmitop 1): `collection_delayed_transmit` (4100) per item the
 *   player has, with its count, which lights it among the results drawn. INFERRED from the script, which only marks
 *   results already drawn: what's typed after the last click shows greyed until the bar is clicked again.
 * - The burger menu (`burger_menu_frame`, children 10 "View Log" and 12 "Overview", from collection_create_dropdown)
 *   swaps between the two windows.
 * - The last tab and page are kept in `collection_last_tab` and `collection_last_category` (6905/6906, which no client
 *   script reads), so the log opens where it was left. INFERRED from their names.
 *
 * The overview draws itself as it loads, from the counts and latest items; its tab buttons (`subsection_buttons_click`,
 * one child per tab) open the log on that tab.
 *
 * The account summary's Collection Log row (`summary_click_layer` child 6, the seventh row in every mode) has op1
 * "Collection Log" and op2 "Collection Overview"; [osrsps.game.Interfaces.openJournalTab] sends its events ([SUMMARY_EVENTS]).
 */
class CollectionLogWindow(
    private val log: CollectionLog,
    private val openModal: (Player, FrameSlot, Int) -> Unit,
    private val closeModals: (Player) -> Unit,
    private val runScript: (Player, Int, List<Any>) -> Unit,
    private val setVarbit: (Player, Int, Int) -> Unit,
    /** Sets a varp and sends it now, ahead of the script that reads it. */
    private val setVarp: (Player, Int, Int) -> Unit,
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    private val catalog get() = log.catalog

    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == LOG

    fun isOverviewOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == OVERVIEW

    /** Opens the log on [tab] and [page], or where the player left it. */
    fun open(player: Player, tab: Int? = null, page: Int? = null) {
        if (catalog.tabs.isEmpty()) return
        val tabIndex = tab ?: varbit(player, VARBIT_LAST_TAB)
        val shown = tabOf(tabIndex) ?: catalog.tabs.first()
        val pageIndex = (page ?: if (shown.index == varbit(player, VARBIT_LAST_TAB)) varbit(player, VARBIT_LAST_PAGE) else 0)
            .takeIf { it in shown.pages.indices } ?: 0
        closeModals(player)
        log.login(player)
        openModal(player, FrameSlot.MAINMODAL, LOG)
        for (each in catalog.tabs) {
            val c = TAB_COMPONENTS.getOrNull(each.index) ?: continue
            if (each.pages.isNotEmpty()) send(player, IfSetEventsV2(LOG, c.background, 0, each.pages.size - 1, 0, OP1))
        }
        send(player, IfSetEventsV2(LOG, BURGER_FRAME, VIEW_LOG, VIEW_OVERVIEW, 0, OP1))
        send(player, IfSetEventsV2(LOG, SEARCH_TOGGLE, -1, -1, 0, OP1))
        sendPage(player, shown, pageIndex)
        runScript(player, DRAW, drawArgs(shown, pageIndex))
    }

    /** Opens the overview. */
    fun openOverview(player: Player) {
        closeModals(player)
        log.login(player)
        openModal(player, FrameSlot.MAINMODAL, OVERVIEW)
        if (catalog.tabs.isNotEmpty()) send(player, IfSetEventsV2(OVERVIEW, OVERVIEW_TAB_BUTTONS, 0, catalog.tabs.size - 1, 0, OP1))
        send(player, IfSetEventsV2(OVERVIEW, OVERVIEW_BURGER_FRAME, VIEW_LOG, VIEW_OVERVIEW, 0, OP1))
    }

    /** A click on either window or the summary's row. Returns false when the click isn't on one. */
    fun click(player: Player, interfaceId: Int, component: Int, sub: Int, op: Int): Boolean {
        when (interfaceId) {
            SUMMARY -> {
                if (component != SUMMARY_CLICK_LAYER || sub != SUMMARY_ROW) return false
                if (player.scripts.delayed) return true
                when (op) {
                    1 -> open(player)
                    2 -> openOverview(player)
                }
                return true
            }
            LOG -> {
                if (!isOpen(player)) return true
                if (component == CLOSE) return true.also { closeModals(player) }
                if (player.scripts.delayed) return true
                val tabButton = TAB_BUTTONS.indexOf(component)
                val listTab = TAB_COMPONENTS.indexOfFirst { it.background == component }
                when {
                    tabButton >= 0 -> tabOf(tabButton)?.let { switchTab(player, it) }
                    listTab >= 0 -> tabOf(listTab)?.takeIf { sub in it.pages.indices }?.let { showPage(player, it, sub) }
                    component == BURGER_FRAME && sub == VIEW_OVERVIEW -> openOverview(player)
                    component == SEARCH_TOGGLE || component == SEARCHBAR -> transmitAll(player)
                }
                return true
            }
            OVERVIEW -> {
                if (!isOverviewOpen(player)) return true
                if (component == OVERVIEW_CLOSE) return true.also { closeModals(player) }
                if (player.scripts.delayed) return true
                when {
                    component == OVERVIEW_TAB_BUTTONS -> tabOf(sub)?.let { open(player, it.index, 0) }
                    component == OVERVIEW_BURGER_FRAME && sub == VIEW_LOG -> open(player)
                }
                return true
            }
        }
        return false
    }

    private fun switchTab(player: Player, tab: CollectionLogCatalog.Tab) {
        if (tab.pages.isEmpty()) return
        sendPage(player, tab, 0)
        runScript(player, DRAW_TABS, listOf(tab.index))
        runScript(player, DRAW_LIST, drawArgs(tab, 0))
    }

    private fun showPage(player: Player, tab: CollectionLogCatalog.Tab, index: Int) {
        sendPage(player, tab, index)
        runScript(player, DRAW_LIST, drawArgs(tab, index))
    }

    /** Remembers the page, and sends its inv and kill counts. */
    private fun sendPage(player: Player, tab: CollectionLogCatalog.Tab, index: Int) {
        val page = tab.pages.getOrNull(index) ?: return
        setVarbit(player, VARBIT_LAST_TAB, tab.index)
        setVarbit(player, VARBIT_LAST_PAGE, index)
        val have = page.items.distinct().filter { log.count(player, it) > 0 }
        val objs = have.map { catalog.displayed(it) to log.count(player, it) }
        send(player, UpdateInvFull(TRANSMIT_INV, objs.size) { slot -> objs[slot].let { (obj, count) -> InventoryObject(slot, obj, count) } })
        val counts = KILL_COUNTS[page.struct].orEmpty()
        for ((i, varp) in CATEGORY_COUNT_VARPS.withIndex()) setVarp(player, varp, counts.getOrNull(i)?.let { varp(player, it) } ?: 0)
    }

    /** Every item the player has, with its count, for the search results (script 4100). */
    private fun transmitAll(player: Player) {
        for (tab in catalog.tabs) for (page in tab.pages) for ((slot, item) in page.items.withIndex()) {
            val count = log.count(player, item)
            if (count > 0) runScript(player, DELAYED_TRANSMIT, listOf(catalog.displayed(item), count, slot, page.struct))
        }
    }

    private fun drawArgs(tab: CollectionLogCatalog.Tab, page: Int): List<Any> {
        val c = TAB_COMPONENTS[tab.index]
        return listOf(tab.index, packed(c.container), packed(c.background), packed(c.text), packed(c.scrollbar), tab.struct, page)
    }

    private fun tabOf(index: Int): CollectionLogCatalog.Tab? =
        catalog.tabs.firstOrNull { it.index == index }?.takeIf { TAB_COMPONENTS.getOrNull(index) != null }

    private fun varbit(player: Player, id: Int): Int = if (player.vars.types.varbit(id) != null) player.vars.getVarbit(id) else 0

    private fun varp(player: Player, id: Int): Int = if (id < player.vars.types.varpCount) player.vars[id] else 0

    /** The components a tab's page list is drawn in. */
    data class TabComponents(val container: Int, val background: Int, val text: Int, val scrollbar: Int)

    companion object {
        /** collection and collection_overview. */
        const val LOG = 621
        const val OVERVIEW = 908

        /** collection:close, then boss_tab to other_tab (by tab index). */
        const val CLOSE = 1
        val TAB_BUTTONS = listOf(4, 5, 6, 7, 8)

        /** Each tab's container, background, text and scrollbar, by tab index (bosses, raids, clues, minigames, other). */
        val TAB_COMPONENTS = listOf(
            TabComponents(10, 11, 12, 13),
            TabComponents(14, 15, 16, 23),
            TabComponents(24, 32, 33, 25),
            TabComponents(26, 27, 36, 28),
            TabComponents(29, 34, 35, 30),
        )

        /** collection:burger_menu_frame, search_toggle and searchbar_image. */
        const val BURGER_FRAME = 73
        const val SEARCH_TOGGLE = 76
        const val SEARCHBAR = 78

        /** The burger menu's "View Log" and "Overview" texts among burger_menu_frame's children (collection_create_dropdown). */
        const val VIEW_LOG = 10
        const val VIEW_OVERVIEW = 12

        /** collection_overview:close, burger_menu_frame and subsection_buttons_click. */
        const val OVERVIEW_CLOSE = 9
        const val OVERVIEW_BURGER_FRAME = 7
        const val OVERVIEW_TAB_BUTTONS = 22

        /** account_summary_sidepanel:summary_click_layer and the Collection Log row's child (summary_sidepanel_draw). */
        const val SUMMARY = 712
        const val SUMMARY_CLICK_LAYER = 3
        const val SUMMARY_ROW = 6

        /** collection_transmit. */
        const val TRANSMIT_INV = 620

        /** collection_draw, collection_draw_tabs_all, collection_draw_list and collection_delayed_transmit. */
        const val DRAW = 7797
        const val DRAW_TABS = 2388
        const val DRAW_LIST = 2730
        const val DELAYED_TRANSMIT = 4100

        const val VARBIT_LAST_TAB = 6905
        const val VARBIT_LAST_PAGE = 6906

        /** collection_category_count, 2 and 3. */
        val CATEGORY_COUNT_VARPS = listOf(2048, 2941, 2942)

        /**
         * Each page's kill counts, by its struct: the varps collection_category_count, 2 and 3 are set from, in that order
         * (the order collection_category_count's lines read them). Varp names from rev 242 osrs-dumps. A page without one
         * here shows 0. Content that keeps a count for another page adds it here.
         */
        val KILL_COUNTS: Map<Int, List<Int>> = mapOf(
            478 to listOf(1733), // Bryophyta: total_bryophyta_kills
            479 to listOf(3761, 1510), // Callisto and Artio: Artio, then Callisto
            481 to listOf(1513), // Chaos Elemental
            482 to listOf(1519), // Chaos Fanatic
            485 to listOf(1521), // Crazy archaeologist
            491 to listOf(1514), // King Black Dragon
            495 to listOf(1529), // Obor
            496 to listOf(1520), // Scorpia
            499 to listOf(1585), // The Inferno: TzKal-Zuk
            500 to listOf(1522), // The Fight Caves: TzTok-Jad
            501 to listOf(3762, 1511), // Venenatis and Spindel: Spindel, then Venenatis
            502 to listOf(3763, 1512), // Vet'ion and Calvar'ion: Calvar'ion, then Vet'ion
            541 to listOf(2075), // Hespori
            1220 to listOf(5894), // The Fractured Archive: completions
        )

        private const val OP1 = 1 shl 0
        private const val OP2 = 1 shl 1

        /** The summary row's events: op1 and op2. */
        val SUMMARY_EVENTS: IfSetEventsV2 get() = IfSetEventsV2(SUMMARY, SUMMARY_CLICK_LAYER, SUMMARY_ROW, SUMMARY_ROW, 0, OP1 or OP2)

        private fun packed(component: Int): Int = (LOG shl 16) or component
    }
}
