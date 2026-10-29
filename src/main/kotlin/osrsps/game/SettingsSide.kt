package osrsps.game

import net.rsprot.protocol.game.incoming.buttons.IfScriptTrigger.ParameterTypes

/**
 * The settings side panel (settings_side, 116): its Controls, Audio and Display tabs.
 *
 * The client changes most of these settings itself before it tells the server: its scripts flip the varbit, or set
 * the volume, in its own copy of the varps, then the click goes out as a button (or, for a volume slider, as
 * IF_SCRIPT_TRIGGER). The server makes the same change here, so the varp it next sends (a whole varp, which other
 * settings share) doesn't undo it, and the save keeps it. The attack priority dropdowns are the exception: the client
 * only shows the option picked and reads the varp again 30 client cycles later, so a server that ignored the click
 * would see the choice jump back.
 *
 * Every rule here is read from the rev 241 client scripts (Joshua-F/osrs-dumps, 2026-10-28) that each comment names;
 * none of it has been seen on live. Brightness, the zoom slider, "Restore Default Zoom" and the renderer toggle are
 * the client's own (device options and camera varcs) and never reach the server.
 */
class SettingsSide {
    /** Handles a click on [component] of the side panel; false when it isn't one of these settings. */
    fun click(player: Player, component: Int, sub: Int, op: Int): Boolean {
        when (component) {
            // settings_side_tabs: each tab's op runs settings_side_switch_tab(n).
            CONTROLS_TAB -> setVarbit(player, VARBIT_PANEL_TAB, PANEL_CONTROLS)
            AUDIO_TAB -> setVarbit(player, VARBIT_PANEL_TAB, PANEL_AUDIO)
            DISPLAY_TAB -> setVarbit(player, VARBIT_PANEL_TAB, PANEL_DISPLAY)
            // settings_side_runmode_op flips option_run, as the run orb does.
            RUN_MODE -> player.runEnabled = !player.runEnabled
            // settings_accept_aid_op turns it on only for an account that isn't an ironman.
            ACCEPT_AID -> {
                val on = varbit(player, VARBIT_ACCEPT_AID) == 0 && varbit(player, VARBIT_IRONMAN) == 0
                setVarbit(player, VARBIT_ACCEPT_AID, if (on) 1 else 0)
            }
            // script5526, the panel's "PK Skull Prevention" op.
            SKULL_PREVENTION -> setVarbit(player, VARBIT_SKULL_PREVENTION, 1 - varbit(player, VARBIT_SKULL_PREVENTION))
            // zoom_mouse_toggle: op 1 flips scroll-wheel zoom; op 2 (Restore Default Zoom) only moves the camera.
            ZOOM_TOGGLE -> if (op == 1) setVarbit(player, VARBIT_ZOOM_MOUSE_DISABLED, 1 - varbit(player, VARBIT_ZOOM_MOUSE_DISABLED))
            // settings_side_dropdown_open makes option n (from 0) child n + 1; settings_get_dropdown reads the varp as n.
            ATTACK_PRIORITY_PLAYER_BUTTONS -> if (sub in 1..PLAYER_PRIORITY_OPTIONS) player.vars[VARP_ATTACK_PRIORITY_PLAYER] = sub - 1
            ATTACK_PRIORITY_NPC_BUTTONS -> if (sub in 1..NPC_PRIORITY_OPTIONS) player.vars[VARP_ATTACK_PRIORITY_NPC] = sub - 1
            MASTER_ICON, MUSIC_ICON, SOUND_ICON, AREASOUND_ICON -> toggleMute(player, Volume.forIcon(component)!!)
            // Our own words: there's no house or bond pouch here yet. Live opens poh_options and bond_main (RSMod).
            HOUSE_OPTIONS -> player.message("There are no player-owned houses on this server yet.")
            BOND_OPTIONS -> player.message("There are no bonds on this server.")
            else -> return false
        }
        return true
    }

    /**
     * A volume slider let go, or moved to or from 0: settings_side_*_volume_pos (and script9250 for area sounds) set the
     * volume and send it as IF_SCRIPT_TRIGGER on the slider's bobble. False when [component] isn't one.
     */
    fun scriptTrigger(player: Player, component: Int, args: List<Any>): Boolean {
        val volume = Volume.forSlider(component) ?: return false
        val value = args.singleOrNull() as? Int ?: return false
        volume.set(player, value.coerceIn(0, MAX_VOLUME))
        return true
    }

    /** script9255, a volume's icon: mute keeps the volume to come back to; unmute brings it back, or the default. */
    private fun toggleMute(player: Player, volume: Volume) {
        val current = volume.get(player)
        if (current > 0) {
            setVarbit(player, volume.savedVarbit, current)
            volume.set(player, 0)
            return
        }
        if (varbit(player, volume.savedVarbit) <= 0) setVarbit(player, volume.savedVarbit, volume.unmuteDefault)
        volume.set(player, varbit(player, volume.savedVarbit))
    }

    /**
     * The four volumes, 0 to 100 in their varps. [unmuteDefault] is what unmuting a volume with nothing saved gives
     * (script9255).
     */
    enum class Volume(val slider: Int, val icon: Int, val varp: Int, val savedVarbit: Int, val unmuteDefault: Int) {
        MASTER(MASTER_SLIDER_BOBBLE, MASTER_ICON, VARP_MASTER_VOLUME, VARBIT_MASTER_SAVED, 100),
        MUSIC(MUSIC_SLIDER_BOBBLE, MUSIC_ICON, VARP_MUSIC_VOLUME, VARBIT_MUSIC_SAVED, 20),
        SOUND(SOUND_SLIDER_BOBBLE, SOUND_ICON, VARP_SOUND_VOLUME, VARBIT_SOUND_SAVED, 45),
        AREA_SOUND(AREASOUNDS_SLIDER_BOBBLE, AREASOUND_ICON, VARP_AREASOUND_VOLUME, VARBIT_AREASOUND_SAVED, 25),
        ;

        // areasounds_get/areasounds_set: while areasounds_override is 1, area sounds use the override varp instead.
        private fun varp(player: Player): Int =
            if (this == AREA_SOUND && player.vars[VARP_AREASOUNDS_OVERRIDE] == 1) VARP_AREASOUND_OVERRIDE_VOLUME else varp

        fun get(player: Player): Int = player.vars[varp(player)]

        fun set(player: Player, value: Int) {
            player.vars[varp(player)] = value
        }

        companion object {
            fun forSlider(component: Int): Volume? = entries.firstOrNull { it.slider == component }

            fun forIcon(component: Int): Volume? = entries.firstOrNull { it.icon == component }
        }
    }

    companion object {
        const val INTERFACE = GameFrame.SETTINGS_SIDE

        const val SKULL_PREVENTION = 5
        const val ACCEPT_AID = 29
        const val RUN_MODE = 30
        const val HOUSE_OPTIONS = 31
        const val SETTINGS_OPEN = 32
        const val BOND_OPTIONS = 33
        const val ATTACK_PRIORITY_PLAYER_BUTTONS = 38
        const val ATTACK_PRIORITY_NPC_BUTTONS = 39
        const val ZOOM_TOGGLE = 43
        const val CONTROLS_TAB = 58
        const val AUDIO_TAB = 66
        const val DISPLAY_TAB = 67
        const val MASTER_SLIDER_BOBBLE = 95
        const val MASTER_ICON = 96
        const val MUSIC_SLIDER_BOBBLE = 109
        const val MUSIC_ICON = 110
        const val SOUND_SLIDER_BOBBLE = 123
        const val SOUND_ICON = 124
        const val AREASOUNDS_SLIDER_BOBBLE = 137
        const val AREASOUND_ICON = 138

        /**
         * The attack priority dropdowns' option counts, as RSMod (rev 233) has them: player 5 (combat level, always right
         * click, left click, hidden, right click on clan members), NPC 4. The 241 enums behind them are not checked.
         */
        const val PLAYER_PRIORITY_OPTIONS = 5
        const val NPC_PRIORITY_OPTIONS = 4

        const val PANEL_CONTROLS = 0
        const val PANEL_AUDIO = 1
        const val PANEL_DISPLAY = 2
        const val MAX_VOLUME = 100

        const val VARP_MUSIC_VOLUME = 168
        const val VARP_SOUND_VOLUME = 169
        const val VARP_AREASOUND_VOLUME = 872
        const val VARP_ATTACK_PRIORITY_PLAYER = 1107
        const val VARP_ATTACK_PRIORITY_NPC = 1306
        const val VARP_MASTER_VOLUME = 3796
        const val VARP_AREASOUNDS_OVERRIDE = 5588
        const val VARP_AREASOUND_OVERRIDE_VOLUME = 5589

        const val VARBIT_IRONMAN = 1777
        const val VARBIT_ACCEPT_AID = 4180
        const val VARBIT_ZOOM_MOUSE_DISABLED = 6357
        const val VARBIT_PANEL_TAB = 9683
        const val VARBIT_MUSIC_SAVED = 12426
        const val VARBIT_SOUND_SAVED = 12427
        const val VARBIT_AREASOUND_SAVED = 12428
        const val VARBIT_SKULL_PREVENTION = 13131
        const val VARBIT_MASTER_SAVED = 14817

        /**
         * Each varbit's place in the 241 cache (config/dump.varbit): checked against the cache at start, since a
         * revision that moves one would have the server write the wrong bits.
         */
        val VARBITS: Map<Int, VarbitType> = mapOf(
            VARBIT_IRONMAN to VarbitType(499, 3, 5),
            VARBIT_ACCEPT_AID to VarbitType(427, 0, 0),
            VARBIT_ZOOM_MOUSE_DISABLED to VarbitType(GameFrame.STONE_ARRANGEMENT_VARP, 29, 29),
            VARBIT_PANEL_TAB to VarbitType(1795, 10, 12),
            VARBIT_MUSIC_SAVED to VarbitType(3109, 0, 7),
            VARBIT_SOUND_SAVED to VarbitType(3109, 8, 14),
            VARBIT_AREASOUND_SAVED to VarbitType(3109, 15, 21),
            VARBIT_SKULL_PREVENTION to VarbitType(3075, 16, 16),
            VARBIT_MASTER_SAVED to VarbitType(3797, 0, 7),
        )

        /**
         * The volume sliders' IF_SCRIPT_TRIGGER scripts, by the id the client sends (the ifscript_ number in the dump),
         * and their arguments: each sends the new volume as one int. Master's was seen arriving from the VM's client
         * (2026-10-29); the other three are read the same way from the dump. The client sends one only while the
         * slider's bobble has [Interfaces.IF_EVENT_SCRIPT_TRIGGER].
         */
        val SCRIPT_TRIGGERS: Map<Int, ParameterTypes> = listOf(3_081_216_314L, 406_545_006L, 3_225_222_424L, 2_493_361_579L)
            .associate { it.toInt() to ParameterTypes.of(ParameterTypes.INT) }

        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            INTERFACE to (
                "settings_side" to mapOf(
                    SKULL_PREVENTION to "skull_prevention", ACCEPT_AID to "acceptaid", RUN_MODE to "runmode",
                    HOUSE_OPTIONS to "houseoptions", SETTINGS_OPEN to "settings_open", BOND_OPTIONS to "bondoptions",
                    ATTACK_PRIORITY_PLAYER_BUTTONS to "attack_priority_player_buttons",
                    ATTACK_PRIORITY_NPC_BUTTONS to "attack_priority_npc_buttons",
                    GameFrame.SETTINGS_CLIENT_LAYOUT_BUTTONS to "display_dynamic_setting_1_buttons",
                    ZOOM_TOGGLE to "zoom_toggle", CONTROLS_TAB to "settings_tab", AUDIO_TAB to "audio_tab", DISPLAY_TAB to "display_tab",
                    MASTER_SLIDER_BOBBLE to "master_slider_bobble", MASTER_ICON to "master_icon",
                    MUSIC_SLIDER_BOBBLE to "music_slider_bobble", MUSIC_ICON to "music_icon",
                    SOUND_SLIDER_BOBBLE to "sound_slider_bobble", SOUND_ICON to "sound_icon",
                    AREASOUNDS_SLIDER_BOBBLE to "areasounds_slider_bobble", AREASOUND_ICON to "areasound_icon",
                )
                ),
        )

        /** The problems with [VARBITS] in [types], empty when each varbit is where the server expects it. */
        fun checkVarbits(types: VarTypes): List<String> = VARBITS.mapNotNull { (id, expected) ->
            val actual = types.varbit(id)
            if (actual == expected) null else "varbit $id should be $expected but is $actual"
        }

        private fun varbit(player: Player, id: Int): Int = if (player.vars.types.varbit(id) != null) player.vars.getVarbit(id) else 0

        /** Sets varbit [id], or does nothing when the cache has no such varbit (tests). */
        private fun setVarbit(player: Player, id: Int, value: Int) {
            if (player.vars.types.varbit(id) != null) player.vars.setVarbit(id, value)
        }
    }
}
