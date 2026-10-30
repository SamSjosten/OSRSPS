package osrsps.game

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.incoming.buttons.IfScriptTrigger.ParameterTypes
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptRunner

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
 * on live. Text inputs are the enhanced client's; their clicks are logged and left alone.
 *
 * A dropdown or keybind row's op opens a list of its options in dropdown_buttons (setting_show_dropdown), once the
 * row's dependencies are met; picking one sets the var in the client (settings_set_dropdown, settings_set_keybind),
 * or for most rows doesn't, and 35 client timer ticks later the row reads the var again (setting_dropdown_synch). So
 * the server keeps which row's list is open and stores the option picked, or the row would jump back. Of the sliders
 * only the four volumes tell the server anything (IF_SCRIPT_TRIGGER); the rest are the client's own (brightness,
 * zoom, draw distance and the like).
 *
 * A number or colour row's op only checks its dependencies in the client (settings_input_op,
 * settings_colour_input_click): the server asks for the value. A number is an amount typed in the chatbox. A colour is
 * picked in the colour picker (colour_pallet, 288), whose Save and Cancel send the colour, or 2^31 - 1, as
 * resume_countdialog (script614).
 *
 * A button row's op only makes it the selected setting in the client (settings_clicked_button; the two cases it acts
 * on are enhanced-client rows): the server does what the button says. Each one sits at the end of the section it
 * works on, the rows since the last header.
 */
class AllSettings(
    private val catalog: SettingsCatalog,
    private val interfaces: Interfaces,
    /** Runs the prompts; without one (tests of other things) number and colour rows do nothing. */
    private val scripts: ScriptRunner? = null,
    /** The words of [SettingsLines] for a player, as the dialogue file has them. */
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
) {
    init {
        // Walking or another interface drops the picker's wait for a colour; the picker goes with it.
        interfaces.onCountDropped(::inputDropped)
        // Another prompt (the bank's Withdraw-X, say) can take the wait over; the picker mustn't answer it.
        scripts?.onCountReplaced(::inputDropped)
    }

    /** The side panel's All Settings button. RSMod (rev 233) opens the window as an overlay in the floater. */
    fun open(player: Player) {
        // Guess (live not seen): the window opens on its category, never mid-search. A search left on when the window
        // closed (and saved over a relog) would otherwise reopen it searching, where searchbar_setup_op gives no op.
        setVarbit(player, VARBIT_SEARCHING, 0)
        // settings_init draws the window from settings_tracking as it loads, so the client gets the server's copy first.
        interfaces.sendVarpNow(player, VARP_SETTINGS_TRACKING)
        player.settingsDropdown = -1
        interfaces.openFloater(player, INTERFACE)
        // Inferred from settings_create_tabs and settings_populate_settings, which give these children their ops:
        // one tab per category, one clickzone child per row. Live's ranges aren't known; these cover every child.
        interfaces.setEvents(player, INTERFACE, CATEGORIES_CLICKZONE, 0, maxOf(catalog.categories.size - 1, 0), Interfaces.IF_EVENT_OP1)
        // A slider's bobble is its row's clickzone child; the volumes' settings_*_pos send IF_SCRIPT_TRIGGER from it.
        interfaces.setEvents(
            player, INTERFACE, SETTINGS_CLICKZONE, 0, maxOf(catalog.slotCount - 1, 0), Interfaces.IF_EVENT_OP1,
            scriptEvents = Interfaces.IF_EVENT_SCRIPT_TRIGGER,
        )
        // setting_show_dropdown makes three children per option, the third (3n + 2) the one with op 1.
        interfaces.setEvents(player, INTERFACE, DROPDOWN_BUTTONS, 0, maxOf(catalog.maxOptions * 3 - 1, 0), Interfaces.IF_EVENT_OP1)
    }

    /**
     * Handles a click on [component] of the window on [tick]; false when the window isn't open or the click isn't one
     * of these.
     */
    fun click(player: Player, component: Int, sub: Int, op: Int, tick: Long): Boolean {
        if (player.floater != INTERFACE) return false
        when (component) {
            // The close button's op only plays a sound in the client (settings_window_setup); Esc is its op 2.
            CLOSE -> {
                player.settingsDropdown = -1
                // A number prompt or the picker goes with the window it was for, so a late answer changes nothing.
                if (player.settingsInputOpen) interfaces.dropCount(player)
                player.colourPickerOpen = false
                restoreChatInput(player)
                interfaces.closeFloater(player)
            }
            // script3829 and settings_declutter_op: the "Less info" and "Hide locked" buttons.
            TOOLTIP_TOGGLE -> flipVarbit(player, VARBIT_TOOLTIPS_HIDDEN)
            DECLUTTER_TOGGLE -> flipVarbit(player, VARBIT_DECLUTTER)
            // searchbar_op, which does nothing while a search is on (the client also takes the op away then). It also
            // turns on floater_search_listen_for_keyboard, the next bit of settings_tracking, which searchbar_keypress
            // needs: the server sends the whole varp, so it must copy both or the client's copy of that bit goes to 0.
            SEARCH -> if (varbit(player, VARBIT_SEARCHING) == 0) {
                player.settingsDropdown = -1
                setVarbit(player, VARBIT_SEARCHING, 1)
                setVarbit(player, VARBIT_SEARCH_KEYBOARD, 1)
            }
            CATEGORIES_CLICKZONE -> clickTab(player, sub)
            SETTINGS_CLICKZONE -> clickRow(player, sub, op)
            // setting_dropdown_entry_close, the "Close dropdown" op on the dark area around the list.
            DROPDOWN_CLOSE -> player.settingsDropdown = -1
            DROPDOWN_BUTTONS -> if (op == 1) pick(player, sub, tick)
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
        player.settingsDropdown = -1
        restoreChatInput(player)
        setVarbit(player, VARBIT_CATEGORY, category.id)
        setVarbit(player, VARBIT_SEARCHING, 0)
        setVarbit(player, VARBIT_SELECTED_SETTING, 0)
    }

    /**
     * searchbar_op takes the chat's keys away (chatdefault_stopinput) and nothing in the client gives them back, so
     * when a search ends, by a tab or by closing the window, the server runs chatdefault_restoreinput, as RSMod does
     * after its menu. Guess (live not seen): without it, chat ignores every key until the next login.
     */
    private fun restoreChatInput(player: Player) {
        if (varbit(player, VARBIT_SEARCHING) == 1) interfaces.runClientScript(player, CHATDEFAULT_RESTOREINPUT_SCRIPT, emptyList())
    }

    /** The row the client drew as clickzone child [sub]: see [rowAt]. */
    private fun clickRow(player: Player, sub: Int, op: Int) {
        val row = rowAt(player, sub) ?: return
        when (row.kind) {
            SettingRow.KIND_TOGGLE, SettingRow.KIND_DROPDOWN, SettingRow.KIND_KEYBIND, SettingRow.KIND_NUMBER, SettingRow.KIND_COLOUR,
            SettingRow.KIND_BUTTON -> Unit
            // A slider's bobble has no op of its own: its notches (other components) and drags work it.
            SettingRow.KIND_SLIDER -> return
            else -> {
                logger.info { "${player.username} clicked setting ${row.id} (kind ${row.kind}) op $op, not handled yet" }
                return
            }
        }
        if (op != 1) return
        if (blocked(player, row.params)) return
        // settings_op_checker: a row whose dependencies aren't met says why (the client's own message) and stops. (A
        // row with param 1926 is hidden instead, which the same check covers.) setting_show_dropdown runs it too.
        if (!dependenciesMet(player, row)) return
        when (row.kind) {
            SettingRow.KIND_DROPDOWN, SettingRow.KIND_KEYBIND -> player.settingsDropdown = row.struct
            SettingRow.KIND_NUMBER -> askNumber(player, row)
            SettingRow.KIND_COLOUR -> pickColour(player, row)
            SettingRow.KIND_BUTTON -> press(player, row)
            else -> {
                setVarbit(player, VARBIT_SELECTED_SETTING, row.id)
                toggle(player, row.id)
            }
        }
    }

    /**
     * A number row: the amount typed is stored where settings_get_number_input reads it. Guessed: the server makes the
     * row the selected setting first, as the client does for every other kind; the prompt's words are its own line for
     * each row (none is on the Wiki yet), and an amount too big for the var is cut to the largest it holds.
     */
    private fun askNumber(player: Player, row: SettingRow) {
        val runner = scripts ?: return
        val number = catalog.numbers[row.id]?.takeIf { it != SettingVar.Client } ?: run {
            logger.info { "${player.username} clicked number input ${row.id}, which settings_get_number_input doesn't read" }
            return
        }
        // One input at a time: an unanswered prompt or picker goes first, so its answer can't land on this one.
        interfaces.dropCount(player)
        // settings_input_op hands the keyboard from the search to the prompt; copied for the same reason as SEARCH's.
        setVarbit(player, VARBIT_SEARCH_KEYBOARD, 0)
        setVarbit(player, VARBIT_SELECTED_SETTING, row.id)
        val prompt = SettingsLines.NUMBER_PROMPTS[row.id] ?: SettingsLines.NUMBER_PROMPT
        player.settingsInputOpen = true
        runner.start(player) {
            val amount = countDialog(words(player)[prompt])
            player.settingsInputOpen = false
            number.set(player.vars, amount.coerceAtMost(largest(player, number)))
        }
    }

    /**
     * A colour row: colour_pallet finds the row from settings_selected_setting, so the server makes it the selected
     * setting, opens the picker over the window and runs colour_pallet with the colour the row has (-1 for its default,
     * which the script then shows). Guessed: the picker opens in the window's popup layer (134:9), as an overlay.
     */
    private fun pickColour(player: Player, row: SettingRow) {
        val runner = scripts ?: return
        val colour = catalog.colours[row.id]?.takeIf { it != SettingVar.Client } ?: run {
            logger.info { "${player.username} clicked colour input ${row.id}, which settings_get_colour doesn't read" }
            return
        }
        interfaces.dropCount(player)
        // Sent now, not with the end-of-tick flush: colour_pallet reads it as it runs.
        interfaces.setVarbitNow(player, VARBIT_SELECTED_SETTING, row.id)
        interfaces.openInside(player, INTERFACE, POPUP, COLOUR_PALLET)
        player.colourPickerOpen = true
        player.settingsInputOpen = true
        interfaces.runClientScript(player, COLOUR_PALLET_SCRIPT, listOf((INTERFACE shl 16) or POPUP, colour.read(player.vars) ?: -1))
        runner.start(player) {
            val picked = waitForCount()
            player.settingsInputOpen = false
            closeColourPicker(player)
            // colour_pallet_update refuses anything past 0xffffff; Cancel and closing send 2^31 - 1.
            if (picked in 0..MAX_COLOUR) colour.set(player.vars, picked)
        }
    }

    private fun inputDropped(player: Player) {
        player.settingsInputOpen = false
        closeColourPicker(player)
    }

    private fun closeColourPicker(player: Player) {
        if (!player.colourPickerOpen) return
        player.colourPickerOpen = false
        interfaces.closeInside(player, INTERFACE, POPUP)
    }

    /** The largest value [setting] can hold: its varbit's, or any int for a varp. */
    private fun largest(player: Player, setting: SettingVar): Int = when (setting) {
        is SettingVar.Varbit -> player.vars.types.varbit(setting.id)?.mask?.takeIf { it >= 0 } ?: Int.MAX_VALUE
        is SettingVar.Offset -> (largest(player, setting.of).toLong() + setting.by).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        else -> Int.MAX_VALUE
    }

    /**
     * setting_dropdown_entry_op: option n of the open list is child 3n + 2 of dropdown_buttons. It closes the list and
     * sets the option through settings_set_dropdown or settings_set_keybind, which first make the row the selected
     * setting. An option past the row's count, or a click with no list open, does nothing.
     */
    private fun pick(player: Player, sub: Int, tick: Long) {
        val row = catalog.rows[player.settingsDropdown] ?: return
        player.settingsDropdown = -1
        if (sub < 0 || sub % 3 != 2) return
        val option = sub / 3
        if (option >= row.options) return
        setVarbit(player, VARBIT_SELECTED_SETTING, row.id)
        when (row.kind) {
            SettingRow.KIND_DROPDOWN -> setDropdown(player, row.id, option, tick)
            SettingRow.KIND_KEYBIND -> setKeybind(player, row.id, option)
        }
    }

    /**
     * Dropdown [id] set to [option]. Most cases of settings_set_dropdown set nothing, and those that do set the var
     * settings_get_dropdown reads to the option (clamped to the option count): the server sets that var either way.
     * The cases below read more than one var, or aren't a var.
     */
    private fun setDropdown(player: Player, id: Int, option: Int, tick: Long) {
        when (id) {
            // "Game client layout": settings_set_dropdown has no case for it, so the server moves the frame, as from
            // the side panel's dropdown.
            CLIENT_LAYOUT_DROPDOWN -> GameFrame.forLayoutOption(option + 1)?.let { interfaces.requestFrame(player, it, tick) }
            // loginlogout_setting_get: loginlogout_permanent when set, else loginlogout_setting. Guessed: a pick clears
            // the permanent one, which nothing in the 241 scripts sets.
            LOGIN_LOGOUT_DROPDOWN -> {
                setVarbit(player, VARBIT_LOGINLOGOUT_PERMANENT, 0)
                setVarbit(player, VARBIT_LOGINLOGOUT_SETTING, option)
            }
            // script662: 0 when level-up messages are off, else 2 for the popup list, else 1. Guessed as its inverse.
            LEVEL_UP_DROPDOWN -> {
                setVarbit(player, VARBIT_LEVEL_UP_MESSAGE_DISABLED, if (option == 0) 1 else 0)
                if (option != 0) setVarbit(player, VARBIT_LEVEL_UP_POPUP_LIST, if (option == 2) 1 else 0)
            }
            else -> if (catalog.dropdowns[id]?.set(player.vars, option) != true) {
                logger.info { "${player.username} picked option $option of dropdown $id, which the client keeps to itself" }
            }
        }
    }

    /**
     * Keybind [id] set to key [option] (0 none, 1 to 12 F1 to F12, 13 Esc in enum 3506). settings_set_keybind sets
     * nothing in the client, so the key is the server's to store. Guessed: a key already bound to another tab is taken
     * from it, so no key opens two tabs.
     */
    private fun setKeybind(player: Player, id: Int, option: Int) {
        val keybind = catalog.keybinds[id] ?: return
        if (option != 0) {
            for ((other, otherVar) in catalog.keybinds) {
                if (other != id && otherVar.read(player.vars) == option) otherVar.set(player.vars, 0)
            }
        }
        keybind.set(player.vars, option)
    }

    /**
     * Button [row]: the client has made it the selected setting (settings_clicked_button), so the server does too, then
     * what the button's name says. Guessed for each, as no script shows the server's half. The beta world buttons do
     * nothing here, as there are no beta worlds.
     */
    private fun press(player: Player, row: SettingRow) {
        setVarbit(player, VARBIT_SELECTED_SETTING, row.id)
        when (row.id) {
            RESET_VOLUMES -> resetVolumes(player)
            in WIPE_PLAYLISTS -> {
                val first = PLAYLIST_VARPS_START + WIPE_PLAYLISTS.indexOf(row.id) * PLAYLIST_VARPS
                for (varp in first until first + PLAYLIST_VARPS) if (varp < player.vars.types.varpCount) player.vars[varp] = 0
            }
            RESTORE_KEYBINDS -> restoreKeybinds(player)
            in RESET_COLOURS -> for (colour in section(row).mapNotNull { catalog.colours[it.id] }) {
                // settings_get_colour reads the varp less 1, so 0 is the row's default colour.
                if (colour is SettingVar.Offset) colour.of.set(player.vars, 0) else colour.set(player.vars, 0)
            }
            ENABLE_TELEPORT_WARNINGS, ENABLE_TABLET_WARNINGS -> setSectionToggles(player, row, 1)
            DISABLE_TELEPORT_WARNINGS, DISABLE_TABLET_WARNINGS -> setSectionToggles(player, row, 0)
            else -> logger.info { "${player.username} pressed setting button ${row.id}, which does nothing here" }
        }
    }

    /** The rows [button] works on: those of its category since the last header before it. */
    private fun section(button: SettingRow): List<SettingRow> {
        val rows = catalog.categories.firstNotNullOfOrNull { category -> category?.rows?.takeIf { button in it } } ?: return emptyList()
        val end = rows.indexOf(button)
        val start = rows.subList(0, end).indexOfLast { it.kind == SettingRow.KIND_HEADER } + 1
        return rows.subList(start, end)
    }

    /** Sets every toggle in [button]'s section to [value]: the Warnings tab's "Show warning when..." rows. */
    private fun setSectionToggles(player: Player, button: SettingRow, value: Int) {
        for (toggle in section(button).filter { it.kind == SettingRow.KIND_TOGGLE }.mapNotNull { catalog.toggles[it.id] }) {
            toggle.set(player.vars, value)
        }
    }

    /** Guessed: the defaults the sliders go back to are script9255's, what unmuting a volume with none saved gives. */
    private fun resetVolumes(player: Player) {
        for (volume in SettingsSide.Volume.entries) volume.set(player, volume.unmuteDefault)
    }

    private fun restoreKeybinds(player: Player) {
        for ((varbit, key) in DEFAULT_KEYBINDS) setVarbit(player, varbit, key)
    }

    /**
     * A new account's settings, for an account that never did Tutorial Island, where live sets them. Guessed: the
     * keybinds the Wiki gives as the defaults, the volumes [resetVolumes] gives, and every Wilderness teleport and
     * tablet warning shown. tut2_default_settings, which the caller runs, does the window mode and zoom.
     */
    fun newAccount(player: Player) {
        restoreKeybinds(player)
        resetVolumes(player)
        for (id in listOf(ENABLE_TELEPORT_WARNINGS, ENABLE_TABLET_WARNINGS)) {
            val button = catalog.rows.values.firstOrNull { it.id == id && it.kind == SettingRow.KIND_BUTTON } ?: continue
            setSectionToggles(player, button, 1)
        }
        // Guessed: the dropdown's enum (4074) defaults to Run -> Walk only, but the varbit's own default, 0, is Never.
        // Live's starting value isn't known; this keeps ctrl useful while running, the enum's choice.
        setVarbit(player, Movement.VARBIT_RUN_INVERT_MODE, Movement.RUN_INVERT_RUN_TO_WALK)
    }

    /**
     * IF_SCRIPT_TRIGGER from the window: settings_master_volume_pos, settings_music_pos, script9272 and their area
     * sound twin send (setting id, volume) from the slider's clickzone child [sub] when it is let go, or moves to or
     * from 0. False when it isn't one of these.
     */
    fun scriptTrigger(player: Player, component: Int, sub: Int, args: List<Any>): Boolean {
        if (player.floater != INTERFACE || component != SETTINGS_CLICKZONE) return false
        val id = args.getOrNull(0) as? Int ?: return false
        val value = args.getOrNull(1) as? Int ?: return false
        val volume = VOLUMES[id] ?: return false
        if (rowAt(player, sub)?.id != id) return false
        volume.set(player, value.coerceIn(0, SettingsSide.MAX_VOLUME))
        return true
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
            LOGIN_LOGOUT_DROPDOWN -> varbit(player, VARBIT_LOGINLOGOUT_PERMANENT).takeIf { it != 0 } ?: varbit(player, VARBIT_LOGINLOGOUT_SETTING)
            LEVEL_UP_DROPDOWN -> if (varbit(player, VARBIT_LEVEL_UP_MESSAGE_DISABLED) == 1) 0 else if (varbit(player, VARBIT_LEVEL_UP_POPUP_LIST) == 1) 2 else 1
            else -> catalog.dropdowns[row.id]?.read(player.vars)
        }
        SettingRow.KIND_KEYBIND -> catalog.keybinds[row.id]?.read(player.vars)
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
        const val DROPDOWN_CLOSE = 26
        const val DROPDOWN_BUTTONS = 29
        const val POPUP = 9

        /** colour_pallet, the colour picker, and its onload script (4185, colour_pallet(component, colour)). */
        const val COLOUR_PALLET = 288
        const val COLOUR_PALLET_SCRIPT = 4185
        const val MAX_COLOUR = 0xffffff

        /** Enum 422's Leagues category, which settings_create_tabs leaves out off a league world. */
        const val LEAGUES_CATEGORY_STRUCT = 1108
        const val CLIENT_LAYOUT_DROPDOWN = 12
        const val LOGIN_LOGOUT_DROPDOWN = 44
        const val LEVEL_UP_DROPDOWN = 315
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
        const val VARBIT_SEARCH_KEYBOARD = 16074
        /** chatdefault_restoreinput, which gives the chat its onkey back. */
        const val CHATDEFAULT_RESTOREINPUT_SCRIPT = 2158
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
        const val VARBIT_LOGINLOGOUT_PERMANENT = 1627
        const val VARBIT_LOGINLOGOUT_SETTING = 12274
        const val VARBIT_LEVEL_UP_MESSAGE_DISABLED = 20157
        const val VARBIT_LEVEL_UP_POPUP_LIST = 4371

        // The window's buttons on a desktop client, by setting id.
        const val RESET_VOLUMES = 468
        const val RESTORE_KEYBINDS = 58
        const val ENABLE_TELEPORT_WARNINGS = 77
        const val DISABLE_TELEPORT_WARNINGS = 76
        const val ENABLE_TABLET_WARNINGS = 75
        const val DISABLE_TABLET_WARNINGS = 74

        /** "Wipe custom music playlist" 1 to 3. */
        val WIPE_PLAYLISTS = listOf(490, 491, 492)

        /** The opaque, transparent and split chat colours, the quest list's and the music player's. */
        val RESET_COLOURS = setOf(107, 108, 109, 228, 489)

        /** music_playlist_1_1: each playlist is 50 varps (music_playlist_N_1 to _50), one after another. */
        const val PLAYLIST_VARPS_START = 5239
        const val PLAYLIST_VARPS = 50

        // The side panel's keybind varbits (sidestone_keybindings_1 to _3), for each tab.
        const val VARBIT_COMBAT_KEY = 4675
        const val VARBIT_STATS_KEY = 4676
        const val VARBIT_JOURNAL_KEY = 4677
        const val VARBIT_INVENTORY_KEY = 4678
        const val VARBIT_WORN_KEY = 4679
        const val VARBIT_PRAYER_KEY = 4680
        const val VARBIT_MAGIC_KEY = 4682
        const val VARBIT_CLANCHAT_KEY = 4683
        const val VARBIT_FRIENDS_KEY = 4684
        const val VARBIT_ACCOUNT_KEY = 6517
        const val VARBIT_SETTINGS_KEY = 4686
        const val VARBIT_EMOTES_KEY = 4687
        const val VARBIT_MUSIC_KEY = 4688
        const val VARBIT_LOGOUT_KEY = 4689

        /**
         * The keys a new account has, from the Wiki's keyboard shortcuts page: F1 to F12 across the tabs and Esc for the
         * inventory, none for logout. Values as enum 3506 numbers them: 1 to 12 the F keys, 13 Esc, 0 none.
         */
        val DEFAULT_KEYBINDS: Map<Int, Int> = mapOf(
            VARBIT_COMBAT_KEY to 1, VARBIT_STATS_KEY to 2, VARBIT_JOURNAL_KEY to 3, VARBIT_WORN_KEY to 4,
            VARBIT_PRAYER_KEY to 5, VARBIT_MAGIC_KEY to 6, VARBIT_CLANCHAT_KEY to 7, VARBIT_FRIENDS_KEY to 8,
            VARBIT_ACCOUNT_KEY to 9, VARBIT_SETTINGS_KEY to 10, VARBIT_EMOTES_KEY to 11, VARBIT_MUSIC_KEY to 12,
            VARBIT_INVENTORY_KEY to 13, VARBIT_LOGOUT_KEY to 0,
        )

        /** tut2_default_settings: resizable window mode and the default zoom, which Tutorial Island's end runs. */
        const val TUT2_DEFAULT_SETTINGS_SCRIPT = 3390

        /** The volume sliders by setting id (settings_slider_choose_onop). */
        val VOLUMES: Map<Int, SettingsSide.Volume> = mapOf(
            319 to SettingsSide.Volume.MASTER, 30 to SettingsSide.Volume.MUSIC,
            31 to SettingsSide.Volume.SOUND, 32 to SettingsSide.Volume.AREA_SOUND,
        )

        /**
         * The volume sliders' IF_SCRIPT_TRIGGER script (ifscript_3416078959 in the dump), the same for all four, and its
         * arguments: the setting id and the volume. Read from the dump only.
         */
        val SCRIPT_TRIGGERS: Map<Int, ParameterTypes> =
            mapOf(3_416_078_959L.toInt() to ParameterTypes.of(ParameterTypes.INT, ParameterTypes.INT))

        /** Each varbit's place in the 241 cache (config/dump.varbit), checked against the cache at start. */
        val VARBITS: Map<Int, VarbitType> = mapOf(
            VARBIT_SEARCHING to VarbitType(VARP_SETTINGS_TRACKING, 0, 0),
            VARBIT_SEARCH_KEYBOARD to VarbitType(VARP_SETTINGS_TRACKING, 1, 1),
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
            VARBIT_LOGINLOGOUT_PERMANENT to VarbitType(1055, 7, 7),
            VARBIT_LOGINLOGOUT_SETTING to VarbitType(3053, 0, 1),
            VARBIT_LEVEL_UP_MESSAGE_DISABLED to VarbitType(3190, 7, 7),
            VARBIT_LEVEL_UP_POPUP_LIST to VarbitType(1734, 18, 18),
            Movement.VARBIT_RUN_INVERT_MODE to VarbitType(3075, 17, 18),
            Movement.VARBIT_RUN_AUTOENABLE to VarbitType(3190, 17, 23),
            VARBIT_COMBAT_KEY to VarbitType(1224, 0, 4),
            VARBIT_STATS_KEY to VarbitType(1224, 5, 9),
            VARBIT_JOURNAL_KEY to VarbitType(1224, 10, 14),
            VARBIT_INVENTORY_KEY to VarbitType(1224, 15, 19),
            VARBIT_WORN_KEY to VarbitType(1224, 20, 24),
            VARBIT_PRAYER_KEY to VarbitType(1224, 25, 29),
            VARBIT_MAGIC_KEY to VarbitType(1225, 0, 4),
            VARBIT_CLANCHAT_KEY to VarbitType(1225, 5, 9),
            VARBIT_FRIENDS_KEY to VarbitType(1225, 10, 14),
            VARBIT_ACCOUNT_KEY to VarbitType(1225, 15, 19),
            VARBIT_SETTINGS_KEY to VarbitType(1225, 20, 24),
            VARBIT_EMOTES_KEY to VarbitType(1225, 25, 29),
            VARBIT_MUSIC_KEY to VarbitType(1226, 0, 4),
            VARBIT_LOGOUT_KEY to VarbitType(1226, 5, 9),
        )

        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            INTERFACE to (
                "settings" to mapOf(
                    CLOSE to "close", TOOLTIP_TOGGLE to "tooltip_inside_if_clickzone", DECLUTTER_TOGGLE to "declutter_button_clickzone",
                    SEARCH to "searchbar_image", SETTINGS_CLICKZONE to "settings_clickzone", CATEGORIES_CLICKZONE to "categories_clickzone",
                    DROPDOWN_CLOSE to "dropdown_close", DROPDOWN_BUTTONS to "dropdown_buttons", POPUP to "popup",
                )
                ),
            COLOUR_PALLET to ("colour_pallet" to emptyMap()),
        )

        /** The problems with [VARBITS] in [types], empty when each varbit is where the server expects it. */
        fun checkVarbits(types: VarTypes): List<String> = VARBITS.mapNotNull { (id, expected) ->
            val actual = types.varbit(id)
            if (actual == expected) null else "varbit $id should be $expected but is $actual"
        }
    }
}

/**
 * The All Settings window's prompts for a number, one for each number row a desktop client shows (by setting id) and
 * one for any other. The Wiki doesn't quote them; the words go in the dialogue file once seen on live.
 */
object SettingsLines : Lines("settings", "Settings") {
    val NUMBER_PROMPT = line("number_prompt", "the chatbox prompt for a number row with no line of its own")
    private val LOOT_NOTIFICATION = line("loot_notification_value", "the prompt after clicking the loot notification value")
    private val DROP_WARNING = line("drop_warning_value", "the prompt after clicking the drop item warning value")
    private val ALCHEMY_WARNING = line("alchemy_warning_value", "the prompt after clicking the Alchemy spells warning value")
    private val HITSPLAT_THRESHOLD = line("hitsplat_threshold", "the prompt after clicking the max hit hitsplats threshold")
    private val RUN_ENERGY = line("run_energy_threshold", "the prompt after clicking the energy threshold to re-enable running")
    private val BIRDHOUSE_WARNING = line("birdhouse_warning_value", "the prompt after clicking the birdhouse warning value")
    private val CANNON_LOAD_X = line("cannon_load_x", "the prompt after clicking the Dwarf multicannon's Load-X value")

    val NUMBER_PROMPTS = mapOf(
        39 to LOOT_NOTIFICATION, 43 to DROP_WARNING, 66 to ALCHEMY_WARNING, 280 to HITSPLAT_THRESHOLD, 389 to RUN_ENERGY,
        449 to BIRDHOUSE_WARNING, 450 to CANNON_LOAD_X,
    )
}
