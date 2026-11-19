package osrsps.game.collection

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import osrsps.game.Player
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Lines
import osrsps.game.social.Clans

/** The collection log's words (keys only; the words are in data/dialogue.txt). */
object CollectionLogLines : Lines("collection_log", "Collection log") {
    val NEW_ITEM = line("new_item", "the chat message for an item new to the log, with the New addition notification setting on", "item")
    val POPUP_TITLE = line("popup_title", "the pop-up's title for an item new to the log, with the New addition popup setting on")
    val POPUP_TEXT = line("popup_text", "the pop-up's text under its title: the item", "item")
}

/**
 * The collection log's record (collection log step 1): which of the [catalog]'s items each player has obtained and how
 * many, kept in [Player.collectionLog] and saved with them, and the vars the log's windows read.
 *
 * Content hands each item to [add] where live logs it: as an NPC's drop lands for its looter (with the loot
 * notifications, [osrsps.game.World.lootDropped]), as a boss chest's or the raid chest's loot is given, as a reward is
 * handed over, and as an item is bought from a shop that logs its sales (Grace's). An item the log doesn't track is
 * ignored. The OSRS Wiki's Collection log: "Each entry in the log is tracked by the item itself, not where it was
 * obtained", so an item fills its slot on every page it is on.
 *
 * The vars, by their rev 242 names (osrs-dumps), all set from the record at login and on each new item:
 * - `collection_count` and `collection_count_max`, the window title's "Collection Log - count/max": items obtained and
 *   items tracked, each counted once however many pages it is on (inferred from the Wiki counting "unique entries");
 * - each tab's `collection_count_<tab>` and `_max`, the same within the tab (inferred, as for the whole log);
 * - each page's `collection_<tab>_<page>_completed` varbit, 1 once every item on it is obtained (script4103 turns the
 *   page's name green with it);
 * - `collection_overview_last_item0` to `11` with their `_date` (rune days, as `collection_overview_last_obtained`
 *   reads them), the overview's latest items, item0 the newest (inferred from the order it draws them), and
 *   `current_runeday`, which that script counts back from. INFERRED: only an item new to the log goes in the latest.
 *
 * A new item is announced as the All Settings rows ask (varbit `option_collection_new_item`: bit 0 "New addition
 * notification", a chat message; bit 1 "New addition popup", the pop-up interface notification_display with
 * notification_display_init). GUESSED: the pop-up's colour is the script's default (null), as nothing gives live's.
 *
 * Counts stop at the Wiki's caps: 250 for the bracelet of ethereum ([CAPS]) and 65,535 for Zulrah's scales. GUESSED:
 * 65,535 for every other item, as the Wiki says the cap depends on the item without listing the others.
 */
class CollectionLog(
    val catalog: CollectionLogCatalog,
    private val name: (Int) -> String,
    private val words: (Player) -> DialogueText,
    /** Shows the pop-up: its title and text. */
    private val popup: (Player, String, String) -> Unit = { _, _, _ -> },
    private val now: () -> Instant = Instant::now,
    private val message: (Player, String) -> Unit = { player, text -> player.message(text) },
) {
    /** How many of [item] [player] has logged; 0 for none. */
    fun count(player: Player, item: Int): Int = player.collectionLog[item] ?: 0

    /** How many of the log's items [player] has, each counted once. */
    fun obtained(player: Player): Int = player.collectionLog.keys.count { it in catalog }

    /**
     * Logs [count] of [item] for [player]: counted on if they have it, else added, its pages and counts updated and the
     * player told as their settings ask. Nothing for an item the log doesn't track.
     */
    fun add(player: Player, item: Int, count: Int) {
        if (count <= 0 || item !in catalog) return
        val had = count(player, item)
        player.collectionLog[item] = minOf(cap(item).toLong(), had.toLong() + count).toInt()
        if (had > 0) return
        val day = runeDay()
        pushLatest(player, item, day)
        sync(player)
        announce(player, item)
    }

    /** For tests and `::clog`: takes [item] out of [player]'s record, or everything for null, the latest items too. */
    fun remove(player: Player, item: Int?) {
        if (item == null) player.collectionLog.clear() else player.collectionLog.remove(item)
        for (slot in 0 until LATEST) {
            if (item == null || varp(player, VARP_LATEST + slot * 2) == item) {
                setVarp(player, VARP_LATEST + slot * 2, -1)
                setVarp(player, VARP_LATEST + slot * 2 + 1, 0)
            }
        }
        sync(player)
    }

    /** Items logged for players who were offline when they dropped, by save key, until they log in. */
    private val pending = HashMap<String, MutableList<Pair<Int, Int>>>()

    /**
     * Logs [count] of [item] for the player saved under [key], who isn't online: a drop kept for them after they logged
     * out (a solo boss's top damager, a Wilderness boss's share). It is added at their next login, as [add] would.
     * INFERRED: live logs it as it drops. Lost if the server restarts first.
     */
    fun addLater(key: String, item: Int, count: Int) {
        if (count <= 0 || item !in catalog) return
        pending.getOrPut(key) { ArrayList() } += item to count
    }

    /** Sets the log's vars from [player]'s record, as at login, after anything logged for them while offline. */
    fun login(player: Player) {
        pending.remove(player.saveKey)?.forEach { (item, count) -> add(player, item, count) }
        sync(player)
    }

    /** Every var the log's windows read, from [player]'s record; see the class comment. */
    fun sync(player: Player) {
        val have = player.collectionLog
        setVarp(player, VARP_COUNT, catalog.items.count { it in have })
        setVarp(player, VARP_COUNT_MAX, catalog.items.size)
        for (tab in catalog.tabs) {
            val varps = TAB_VARPS.getOrNull(tab.index) ?: continue
            setVarp(player, varps.first, tab.items.count { it in have })
            setVarp(player, varps.second, tab.items.size)
        }
        for (tab in catalog.tabs) for (page in tab.pages) {
            val varbit = page.completedVarbit ?: continue
            setVarbit(player, varbit, if (page.items.isNotEmpty() && page.items.all { it in have }) 1 else 0)
        }
        // An obj varp of 0 would show obj 0 (Dwarf remains): a save from before the log has none, so -1 for empty.
        for (slot in 0 until LATEST) if (varp(player, VARP_LATEST + slot * 2) == 0) setVarp(player, VARP_LATEST + slot * 2, -1)
        setVarbit(player, VARBIT_CURRENT_RUNEDAY, runeDay())
    }

    private fun pushLatest(player: Player, item: Int, day: Int) {
        for (slot in LATEST - 1 downTo 1) {
            setVarp(player, VARP_LATEST + slot * 2, varp(player, VARP_LATEST + (slot - 1) * 2))
            setVarp(player, VARP_LATEST + slot * 2 + 1, varp(player, VARP_LATEST + (slot - 1) * 2 + 1))
        }
        setVarp(player, VARP_LATEST, item)
        setVarp(player, VARP_LATEST + 1, day)
    }

    private fun announce(player: Player, item: Int) {
        val setting = varbit(player, VARBIT_NEW_ITEM_OPTION)
        val text = words(player)
        if (setting and NOTIFY_CHAT != 0) message(player, text[CollectionLogLines.NEW_ITEM, "item" to name(item)])
        if (setting and NOTIFY_POPUP != 0) popup(player, text[CollectionLogLines.POPUP_TITLE], text[CollectionLogLines.POPUP_TEXT, "item" to name(item)])
    }

    private fun runeDay(): Int = ChronoUnit.DAYS.between(Clans.RUNE_EPOCH, now().atOffset(ZoneOffset.UTC).toLocalDate()).toInt()

    private fun varp(player: Player, id: Int): Int = if (id < player.vars.types.varpCount) player.vars[id] else 0

    private fun setVarp(player: Player, id: Int, value: Int) {
        if (id < player.vars.types.varpCount && player.vars[id] != value) player.vars[id] = value
    }

    private fun varbit(player: Player, id: Int): Int = if (player.vars.types.varbit(id) != null) player.vars.getVarbit(id) else 0

    private fun setVarbit(player: Player, id: Int, value: Int) {
        val type = player.vars.types.varbit(id) ?: return
        if (type.baseVar < player.vars.types.varpCount && player.vars.getVarbit(id) != value) player.vars.setVarbit(id, value)
    }

    companion object {
        /** The cap on an item's count where [CAPS] has none: the Wiki's for Zulrah's scales, GUESSED for the rest. */
        const val MAX_COUNT = 65_535

        /** wild_cave_bracelet_uncharged, the bracelet of ethereum the Revenants page logs. */
        const val BRACELET_OF_ETHEREUM = 21817

        /** Items whose count stops below [MAX_COUNT]: the Wiki's Collection log page. */
        val CAPS: Map<Int, Int> = mapOf(BRACELET_OF_ETHEREUM to 250)

        /** The most of [item] the log counts. */
        fun cap(item: Int): Int = CAPS[item] ?: MAX_COUNT

        const val VARP_COUNT = 2943
        const val VARP_COUNT_MAX = 2944

        /** `collection_count_bosses`/`_max`, then raids, clues, minigames and other: by tab index (struct param 684). */
        val TAB_VARPS = listOf(4613 to 4614, 4615 to 4616, 4617 to 4618, 4619 to 4620, 4621 to 4622)

        /** `collection_overview_last_item0`; each slot is an item varp and its `_date` after it, up to item11. */
        const val VARP_LATEST = 4623
        const val LATEST = 12

        const val VARBIT_NEW_ITEM_OPTION = 11959
        const val NOTIFY_CHAT = 1
        const val NOTIFY_POPUP = 2
        const val VARBIT_CURRENT_RUNEDAY = 9535

        /** notification_display, opened in the frame's notifications slot, and its notification_display_init. */
        const val NOTIFICATION_DISPLAY = 660
        const val NOTIFICATION_DISPLAY_INIT = 3343
    }
}
