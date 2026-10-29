package osrsps.game

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * The All Settings window (settings, 134), opened from the side panel's "All Settings" button, and its toggles.
 *
 * The window is drawn by the client from the cache ([SettingsCatalog]): every row of the open category, or of every
 * category while searching, is one child of settings_clickzone, numbered in drawing order, hidden rows included. A
 * toggle's op runs settings_toggle_op in the client, which checks the row's dependencies and then flips the setting's
 * var in the client's own copy (settings_clicked_toggle); the same click reaches the server as a button on that child.
 * The server works out which row the child is from varp settings_tracking (the open category and whether a search is
 * on), checks the same dependencies and makes the same change, so the varp it next sends agrees with the client.
 *
 * Read from the rev 241 client scripts (Joshua-F/osrs-dumps, 2026-10-28) each comment names; none of it has been seen
 * on live. Dropdowns, sliders, keybinds and inputs are the next steps; their clicks are logged and left alone.
 */
class AllSettings(private val catalog: SettingsCatalog, private val interfaces: Interfaces) {
    /** The side panel's All Settings button. RSMod (rev 233) opens the window as an overlay in the floater. */
    fun open(player: Player) {
        // settings_init draws the window from settings_tracking as it loads, so the client gets the server's copy first.
        interfaces.sendVarpNow(player, VARP_SETTINGS_TRACKING)
        interfaces.openFloater(player, INTERFACE)
        // Inferred from settings_create_tabs and settings_populate_settings, which give these children their ops:
        // one tab per category, one clickzone child per row. Live's ranges aren't known; these cover every child.
        interfaces.setEvents(player, INTERFACE, CATEGORIES_CLICKZONE, 0, maxOf(catalog.categories.size - 1, 0), Interfaces.IF_EVENT_OP1)
        interfaces.setEvents(player, INTERFACE, SETTINGS_CLICKZONE, 0, maxOf(catalog.slotCount - 1, 0), Interfaces.IF_EVENT_OP1)
    }

    /** Handles a click on [component] of the window; false when the window isn't open or the click isn't one of these. */
    fun click(player: Player, component: Int, sub: Int, op: Int): Boolean {
        if (player.floater != INTERFACE) return false
        when (component) {
            // The close button's op only plays a sound in the client (settings_window_setup); Esc is its op 2.
            CLOSE -> interfaces.closeFloater(player)
            // script3829 and settings_declutter_op: the "Less info" and "Hide locked" buttons.
            TOOLTIP_TOGGLE -> flipVarbit(player, VARBIT_TOOLTIPS_HIDDEN)
            DECLUTTER_TOGGLE -> flipVarbit(player, VARBIT_DECLUTTER)
            // searchbar_op, which does nothing while a search is on (the client also takes the op away then).
            SEARCH -> if (varbit(player, VARBIT_SEARCHING) == 0) setVarbit(player, VARBIT_SEARCHING, 1)
            CATEGORIES_CLICKZONE -> clickTab(player, sub)
            SETTINGS_CLICKZONE -> clickRow(player, sub, op)
            else -> return false
        }
        return true
    }

    /**
     * settings_create_tabs gives each category of enum 422 one child of categories_clickzone, except Leagues off a
     * league world, which it skips. The open tab has no op; the others run settings_tab_op, which opens the category
     * and ends any search.
     */
    private fun clickTab(player: Player, sub: Int) {
        val category = catalog.categories.filter { it?.struct != LEAGUES_CATEGORY_STRUCT }.getOrNull(sub) ?: return
        if (blocked(player, category.params)) return
        setVarbit(player, VARBIT_CATEGORY, category.id)
        setVarbit(player, VARBIT_SEARCHING, 0)
        setVarbit(player, VARBIT_SELECTED_SETTING, 0)
    }

    /** The row the client drew as clickzone child [sub]: see [rowAt]. */
    private fun clickRow(player: Player, sub: Int, op: Int) {
        val row = rowAt(player, sub) ?: return
        if (row.kind != SettingRow.KIND_TOGGLE) {
            logger.info { "${player.username} clicked setting ${row.id} (kind ${row.kind}) op $op, not handled yet" }
            return
        }
        if (op != 1) return
        if (blocked(player, row.params)) return
        // settings_op_checker: a row whose dependencies aren't met says why (the client's own message) and stops. (A
        // row with param 1926 is hidden instead, which the same check covers.)
        if (!dependenciesMet(player, row)) return
        setVarbit(player, VARBIT_SELECTED_SETTING, row.id)
        toggle(player, row.id)
    }

    /**
     * settings_populate_settings numbers the clickzone children across one category (enum 423 at settings_category),
     * or while searching across every category of enum 422 that isn't blocked, one per row whether drawn or hidden.
     */
    fun rowAt(player: Player, sub: Int): SettingRow? {
        if (sub < 0) return null
        if (varbit(player, VARBIT_SEARCHING) == 0) return catalog.category(varbit(player, VARBIT_CATEGORY))?.rows?.getOrNull(sub)
        var first = 0
        for (category in catalog.categories) {
            if (category == null || blocked(player, category.params)) continue
            if (sub < first + category.rows.size) return category.rows[sub - first]
            first += category.rows.size
        }
        return null
    }

    /**
     * settings_clicked_toggle for toggle [id]. Most cases flip the var settings_get_toggle reads; the cases below are
     * the ones a desktop client can show that do something else, each from the proc it calls. Cases that need the
     * enhanced client or mobile are hidden from RuneLite and never clicked.
     */
    private fun toggle(player: Player, id: Int) {
        val vars = player.vars
        val resizable = player.frame.resizable
        when (id) {
            // settings_wiki_lookup, settings_store_button_desktop, settings_content_recommender_button: not while the orbs are off.
            9, 181, 257 -> if (varbit(player, VARBIT_ORBS_DISABLED) == 1) return
            // settings_resizable_transparent_side_panel, settings_resizable_opaque_chatbox: resizable only.
            2, 11 -> if (!resizable) return
            // settings_hide_private_chat: needs Split Private Chat and resizable (the client says so itself).
            36 -> if (vars[VARP_SPLIT_PRIVATE_CHAT] == 0 || !resizable) return
            // settings_loot_drop_notification and settings_drop_item_warnings: no turning on without a value set.
            38 -> if (varbit(player, VARBIT_LOOT_NOTIFICATION) == 0 && varbit(player, VARBIT_LOOT_NOTIFICATION_VALUE) <= 0) return
            42 -> if (varbit(player, VARBIT_DROP_WARNING) == 0 && varbit(player, VARBIT_DROP_WARNING_VALUE) <= 0) return
            // settings_untradeable_loot_notification: only while loot notifications are on.
            40 -> if (varbit(player, VARBIT_LOOT_NOTIFICATION) != 1) return
            // script3988: transparent chatbox, resizable only.
            46 -> if (varbit(player, VARBIT_CHATBOX_TRANSPARENT) == 0 || !resizable) return
            // script3995: the modern layout only.
            54 -> if (player.stoneArrangement != 1) return
            // settings_accept_aid: ironmen can't turn it on (the client says so itself).
            59 -> if (varbit(player, VARBIT_ACCEPT_AID) == 0 && varbit(player, SettingsSide.VARBIT_IRONMAN) != 0) return
            // script2390: turning failure notifications off turns re-failure notifications off too.
            161 -> {
                flip(player, id)
                if (varbit(player, VARBIT_CA_FAILURE) == 0) setVarbit(player, VARBIT_CA_REFAILURE, 0)
                return
            }
            // The group ironman broadcasts: anyone else gets them off.
            201, 202, 203, 204 -> if (!groupIron(player)) {
                (catalog.toggles[id] as? SettingVar.Varbit)?.let { setVarbit(player, it.id, 0) }
                return
            }
            // settings_wilderness_lever_first_op: once any Wilderness diary is done.
            296 -> if (varbit(player, VARBIT_WILDERNESS_DIARY_ANY) == 0) return
            // script5127, script5128: once the quest step that teaches them is done.
            380 -> if (vars[VARP_BARCRAWL] != 2 && vars[VARP_SCORPCATCHER] < 6) return
            381 -> if (varbit(player, VARBIT_BRUT_FARMING_SMASHING) < 3) return
            // Ultimate ironmen only.
            386 -> if (varbit(player, SettingsSide.VARBIT_IRONMAN) != 2) return
        }
        flip(player, id)
    }

    private fun flip(player: Player, id: Int) {
        catalog.toggles[id]?.flip(player.vars)
    }

    /**
     * settings_check_block for a category's or row's params, with what the server knows of the client: a desktop
     * client that isn't the enhanced one (RuneLite), on a members world that isn't a league, speedrun, beta, PvP
     * arena, deadman or rental world. "Hide locked" (declutter) and "see more" also hide rows, but only in the
     * client's drawing, never moving a slot; the client can't click a hidden row, so they aren't checked here.
     */
    fun blocked(player: Player, params: Map<Int, Int>): Boolean {
        fun flag(param: Int) = (params[param] ?: 0) != 0
        val ironman = varbit(player, SettingsSide.VARBIT_IRONMAN)
        return flag(PARAM_ENHANCED_OR_MOBILE_ONLY) || flag(PARAM_MOBILE_ONLY) || flag(PARAM_ENHANCED_ONLY) ||
            (flag(PARAM_NOT_IRONMAN) && ironman != 0) || (flag(PARAM_IRONMAN_ONLY) && ironman == 0) ||
            flag(PARAM_LEAGUE_ONLY) || flag(PARAM_LEAGUE_MASTERIES_ONLY) || flag(PARAM_SPEEDRUN_ONLY) ||
            // Guessed: feature flag 3 is off.
            flag(PARAM_ENHANCED_LEAGUE_OR_FLAG) ||
            (flag(PARAM_GROUP_IRON_ONLY) && !groupIron(player)) || (flag(PARAM_ULTIMATE_ONLY) && ironman != 2) ||
            (flag(PARAM_AFTER_TUTORIAL) && player.vars[VARP_TUTORIAL] < 1000 && player.vars[VARP_TUTORIAL_2] < 1000) ||
            flag(PARAM_RENTAL_ONLY) ||
            // settings_create_setting: a custom check, in 241 only row 53's, which needs the mobile layout.
            flag(PARAM_CUSTOM_CHECK)
    }

    private fun dependenciesMet(player: Player, row: SettingRow): Boolean = dependenciesMet(player, row.params, row.requires, row.excludes)

    /**
     * settings_dependency_checker: the row's custom dependency (param 1115), then every row it requires has its value
     * and no row it excludes has the excluded one (settings_check_dependency, which checks theirs in turn).
     */
    private fun dependenciesMet(player: Player, params: Map<Int, Int>, requires: List<SettingDependency>, excludes: List<SettingDependency>): Boolean {
        if ((params[PARAM_CUSTOM_DEPENDENCY] ?: 0) != 0 && !customDependency(player, params[SettingRow.PARAM_ID] ?: 0)) return false
        return check(player, requires, equal = true, depth = 0) && check(player, excludes, equal = false, depth = 0)
    }

    private fun check(player: Player, list: List<SettingDependency>, equal: Boolean, depth: Int): Boolean {
        if (depth > MAX_DEPENDENCY_DEPTH) return false
        for (dependency in list) {
            val row = dependency.struct?.let(catalog.rows::get) ?: return false
            if (!check(player, row.requires, true, depth + 1) || !check(player, row.excludes, false, depth + 1)) return false
            if (row.id == 0) return false
            // A value the server can't read (the client's own) is taken as meeting the dependency. Guessed.
            val value = value(player, row) ?: continue
            if ((value == dependency.value) != equal) return false
        }
        return true
    }

    /** settings_get_toggle or settings_get_dropdown for [row], or null when the server can't know it. */
    private fun value(player: Player, row: SettingRow): Int? = when (row.kind) {
        SettingRow.KIND_TOGGLE -> catalog.toggles[row.id]?.read(player.vars)
        SettingRow.KIND_DROPDOWN -> when (row.id) {
            // settings_get_dropdown case 12, "Game client layout": fixed 0, resizable classic 1, modern 2.
            CLIENT_LAYOUT_DROPDOWN -> if (!player.frame.resizable) 0 else if (player.stoneArrangement == 0) 1 else 2
            else -> catalog.dropdowns[row.id]?.read(player.vars)
        }
        else -> null
    }

    /** settings_custom_dependency, for the ids that have param 1115. */
    private fun customDependency(player: Player, id: Int): Boolean {
        val resizable = player.frame.resizable
        val transparent = varbit(player, VARBIT_CHATBOX_TRANSPARENT) == 1
        val ironman = varbit(player, SettingsSide.VARBIT_IRONMAN)
        return when (id) {
            87, 89, 92, 94, 97, 99, 101, 103, 105, 107, 196, 434 -> !(resizable && transparent)
            193, 198 -> groupIron(player) && !(resizable && transparent)
            194, 199 -> groupIron(player) && resizable && transparent
            // The popout panels need the enhanced client.
            191, 320 -> false
            296 -> varbit(player, VARBIT_WILDERNESS_DIARY_ANY) != 0
            380 -> player.vars[VARP_BARCRAWL] == 2 || player.vars[VARP_SCORPCATCHER] >= 6
            381 -> varbit(player, VARBIT_BRUT_FARMING_SMASHING) >= 3
            373 -> varbit(player, VARBIT_BRUT_HERB_POTION) >= 3
            205 -> !(ironman > 0 && !groupIron(player))
            // The renderer choice: the enhanced client's. Guessed false, as script9751 isn't read.
            502 -> false
            else -> true
        }
    }

    /** is_group_iron: ironman 4 to 6. */
    private fun groupIron(player: Player): Boolean = varbit(player, SettingsSide.VARBIT_IRONMAN) in 4..6

    private fun flipVarbit(player: Player, id: Int) = setVarbit(player, id, 1 - varbit(player, id))

    private fun varbit(player: Player, id: Int): Int = if (player.vars.types.varbit(id) != null) player.vars.getVarbit(id) else 0

    /** Sets varbit [id], or does nothing when the cache has no such varbit (tests). */
    private fun setVarbit(player: Player, id: Int, value: Int) {
        if (player.vars.types.varbit(id) != null) player.vars.setVarbit(id, value)
    }

    companion object {
        const val INTERFACE = 134

        const val CLOSE = 4
        const val TOOLTIP_TOGGLE = 5
        const val DECLUTTER_TOGGLE = 7
        const val SEARCH = 11
        const val SETTINGS_CLICKZONE = 20
        const val CATEGORIES_CLICKZONE = 24

        /** Enum 422's Leagues category, which settings_create_tabs leaves out off a league world. */
        const val LEAGUES_CATEGORY_STRUCT = 1108
        const val CLIENT_LAYOUT_DROPDOWN = 12
        private const val MAX_DEPENDENCY_DEPTH = 16

        // settings_check_block's params, by what each one hides the row from.
        const val PARAM_ENHANCED_OR_MOBILE_ONLY = 1271
        const val PARAM_MOBILE_ONLY = 740
        const val PARAM_ENHANCED_ONLY = 1157
        const val PARAM_NOT_IRONMAN = 741
        const val PARAM_IRONMAN_ONLY = 742
        const val PARAM_LEAGUE_ONLY = 1272
        const val PARAM_LEAGUE_MASTERIES_ONLY = 1996
        const val PARAM_SPEEDRUN_ONLY = 1608
        const val PARAM_ENHANCED_LEAGUE_OR_FLAG = 1273
        const val PARAM_GROUP_IRON_ONLY = 1414
        const val PARAM_ULTIMATE_ONLY = 1957
        const val PARAM_AFTER_TUTORIAL = 1995
        const val PARAM_RENTAL_ONLY = 2280
        const val PARAM_CUSTOM_DEPENDENCY = 1115
        const val PARAM_CUSTOM_CHECK = 1229

        const val VARP_TUTORIAL = 281
        const val VARP_TUTORIAL_2 = 2686
        const val VARP_SPLIT_PRIVATE_CHAT = 287
        const val VARP_SCORPCATCHER = 76
        const val VARP_BARCRAWL = 77
        const val VARP_SETTINGS_TRACKING = 2855

        const val VARBIT_SEARCHING = 16073
        const val VARBIT_CATEGORY = 9656
        const val VARBIT_SELECTED_SETTING = 9657
        const val VARBIT_DECLUTTER = 17796
        const val VARBIT_TOOLTIPS_HIDDEN = 9665
        const val VARBIT_ORBS_DISABLED = 4084
        const val VARBIT_CHATBOX_TRANSPARENT = 4608
        const val VARBIT_ACCEPT_AID = SettingsSide.VARBIT_ACCEPT_AID
        const val VARBIT_LOOT_NOTIFICATION = 5399
        const val VARBIT_LOOT_NOTIFICATION_VALUE = 5400
        const val VARBIT_DROP_WARNING = 5411
        const val VARBIT_DROP_WARNING_VALUE = 5412
        const val VARBIT_CA_FAILURE = 12454
        const val VARBIT_CA_REFAILURE = 12457
        const val VARBIT_WILDERNESS_DIARY_ANY = 4470
        const val VARBIT_BRUT_HERB_POTION = 3761
        const val VARBIT_BRUT_FARMING_SMASHING = 9610

        /** Each varbit's place in the 241 cache (config/dump.varbit), checked against the cache at start. */
        val VARBITS: Map<Int, VarbitType> = mapOf(
            VARBIT_SEARCHING to VarbitType(VARP_SETTINGS_TRACKING, 0, 0),
            VARBIT_CATEGORY to VarbitType(VARP_SETTINGS_TRACKING, 4, 7),
            VARBIT_SELECTED_SETTING to VarbitType(VARP_SETTINGS_TRACKING, 8, 17),
            VARBIT_DECLUTTER to VarbitType(3552, 27, 27),
            VARBIT_TOOLTIPS_HIDDEN to VarbitType(2856, 0, 0),
            VARBIT_ORBS_DISABLED to VarbitType(1055, 3, 3),
            VARBIT_CHATBOX_TRANSPARENT to VarbitType(1055, 9, 9),
            VARBIT_LOOT_NOTIFICATION to VarbitType(427, 2, 2),
            VARBIT_LOOT_NOTIFICATION_VALUE to VarbitType(427, 3, 31),
            VARBIT_DROP_WARNING to VarbitType(284, 0, 0),
            VARBIT_DROP_WARNING_VALUE to VarbitType(284, 1, 29),
            VARBIT_CA_FAILURE to VarbitType(1795, 16, 16),
            VARBIT_CA_REFAILURE to VarbitType(1795, 19, 19),
            VARBIT_WILDERNESS_DIARY_ANY to VarbitType(1188, 18, 21),
            VARBIT_BRUT_HERB_POTION to VarbitType(1023, 11, 12),
            VARBIT_BRUT_FARMING_SMASHING to VarbitType(4086, 0, 1),
        )

        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            INTERFACE to (
                "settings" to mapOf(
                    CLOSE to "close", TOOLTIP_TOGGLE to "tooltip_inside_if_clickzone", DECLUTTER_TOGGLE to "declutter_button_clickzone",
                    SEARCH to "searchbar_image", SETTINGS_CLICKZONE to "settings_clickzone", CATEGORIES_CLICKZONE to "categories_clickzone",
                )
                ),
        )

        /** The problems with [VARBITS] in [types], empty when each varbit is where the server expects it. */
        fun checkVarbits(types: VarTypes): List<String> = VARBITS.mapNotNull { (id, expected) ->
            val actual = types.varbit(id)
            if (actual == expected) null else "varbit $id should be $expected but is $actual"
        }
    }
}
