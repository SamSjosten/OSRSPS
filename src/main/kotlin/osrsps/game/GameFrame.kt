package osrsps.game

/**
 * The three top-level interfaces the client can show, and the overlays opened into them on login.
 *
 * Every interface and component id here was checked against the names in the rev 241 cache's gameval index
 * (cache of 2026-10-14). [GameFrameCheck] repeats that check against our cache on every server start, so a
 * revision bump that moves a component shows up as a warning in the log instead of a missing panel.
 */
enum class GameFrame(
    val interfaceId: Int,
    /** The interface's name in the cache's gameval index. */
    val cacheName: String,
    val resizable: Boolean,
    /** The argument client script [CLIENT_MODE_SCRIPT] takes to switch the client to this frame's window mode. */
    val clientMode: Int,
    /** Value of varbit resizable_stone_arrangement for this frame: 1 puts all side stones along the bottom. */
    val stoneArrangement: Int,
) {
    FIXED(548, "toplevel", resizable = false, clientMode = 0, stoneArrangement = 0),
    RESIZABLE_CLASSIC(161, "toplevel_osrs_stretch", resizable = true, clientMode = 1, stoneArrangement = 0),
    RESIZABLE_MODERN(164, "toplevel_pre_eoc", resizable = true, clientMode = 2, stoneArrangement = 1),
    ;

    fun component(slot: FrameSlot): Int = when (this) {
        FIXED -> slot.fixed
        RESIZABLE_CLASSIC -> slot.classic
        RESIZABLE_MODERN -> slot.modern
    }

    companion object {
        /**
         * The frame a client logging in with [resizable] gets. Without saved settings a resizable client gets
         * classic unless [stoneArrangement] says modern; live remembers the choice per account (task 8, saving).
         */
        fun forLogin(resizable: Boolean, stoneArrangement: Int): GameFrame = when {
            !resizable -> FIXED
            stoneArrangement == RESIZABLE_MODERN.stoneArrangement -> RESIZABLE_MODERN
            else -> RESIZABLE_CLASSIC
        }

        /**
         * Client script that switches the client between fixed and resizable window mode. In the 241 cache it
         * takes one int (0 fixed, 1 or 2 resizable) and calls getwindowmode/setwindowmode/setdefaultwindowmode.
         */
        const val CLIENT_MODE_SCRIPT = 3998

        /** Client scripts RSMod runs once the world hop listener has moved: settings scaling and buff bar redraw. No arguments in 241. */
        const val SETTINGS_INTERFACE_SCALING_SCRIPT = 2358
        const val BUFF_BAR_LAYOUT_REDRAW_SCRIPT = 5937

        /** Client script that sets the scroll-wheel zoom limits to their defaults (128 to 896). No arguments in 241. */
        const val ZOOM_LIMITS_DEFAULT_SCRIPT = 626

        /** Varbit resizable_stone_arrangement (4607) is bit 8 of this varp in the 241 cache. */
        const val STONE_ARRANGEMENT_VARP = 1055
        const val STONE_ARRANGEMENT_BIT = 8

        /** The minimap orbs and the run orb's button, orbs:runbutton. */
        const val ORBS = 160
        const val ORBS_RUN_BUTTON = 28

        /** The settings side panel and its "Game client layout" dropdown, settings_side:display_dynamic_setting_1_buttons. */
        const val SETTINGS_SIDE = 116
        const val SETTINGS_CLIENT_LAYOUT_BUTTONS = 40

        /**
         * Dropdown option (the button's sub id) to frame, as RSMod has it. Inferred from RSMod (rev 233), not checked
         * against live: the cache only tells us the component is the first dynamic display setting.
         */
        fun forLayoutOption(sub: Int): GameFrame? = when (sub) {
            1 -> FIXED
            2 -> RESIZABLE_CLASSIC
            3 -> RESIZABLE_MODERN
            else -> null
        }

        /** Overlay interface, its gameval name and the slot it opens into on login. */
        val overlays: List<Overlay> = listOf(
            Overlay(162, "chatbox", FrameSlot.CHAT_CONTAINER),
            Overlay(651, "buff_bar", FrameSlot.BUFF_BAR),
            Overlay(708, "stat_boosts_hud", FrameSlot.STAT_BOOSTS_HUD),
            Overlay(163, "pm_chat", FrameSlot.PM_CONTAINER),
            Overlay(303, "hpbar_hud", FrameSlot.HPBAR_HUD),
            Overlay(90, "pvp_icons", FrameSlot.PVP_ICONS),
            Overlay(ORBS, "orbs", FrameSlot.ORBS),
            Overlay(122, "xp_drops", FrameSlot.XP_DROPS),
            Overlay(728, "popout", FrameSlot.POPOUT),
            Overlay(896, "ehc_worldhop", FrameSlot.TLI_LISTENER),
            Overlay(593, "combat_interface", FrameSlot.SIDE0),
            Overlay(320, "stats", FrameSlot.SIDE1),
            Overlay(629, "side_journal", FrameSlot.SIDE2),
            Overlay(149, "inventory", FrameSlot.SIDE3),
            Overlay(387, "wornitems", FrameSlot.SIDE4),
            Overlay(541, "prayerbook", FrameSlot.SIDE5),
            Overlay(218, "magic_spellbook", FrameSlot.SIDE6),
            Overlay(707, "side_channels", FrameSlot.SIDE7),
            Overlay(109, "account", FrameSlot.SIDE8),
            Overlay(429, "friends", FrameSlot.SIDE9),
            Overlay(182, "logout", FrameSlot.SIDE10),
            Overlay(SETTINGS_SIDE, "settings_side", FrameSlot.SIDE11),
            Overlay(216, "emote", FrameSlot.SIDE12),
            Overlay(239, "music", FrameSlot.SIDE13),
        )
    }
}

data class Overlay(val interfaceId: Int, val cacheName: String, val slot: FrameSlot)

/**
 * A place in the game frame that something opens into. Each slot is the component with the same gameval name
 * ([cacheName]) in all three top-levels; the numbers are its component id in fixed, resizable classic and
 * resizable modern. When the frame changes, everything in these slots is moved, in this order (RSMod's order).
 */
enum class FrameSlot(val fixed: Int, val classic: Int, val modern: Int) {
    CHAT_CONTAINER(11, 96, 93),
    MAINMODAL(41, 16, 16),
    MAINCRM(42, 17, 17),
    OVERLAY_ATMOSPHERE(27, 1, 1),
    OVERLAY_HUD(33, 8, 8),
    SIDEMODAL(79, 74, 71),
    SIDE0(81, 76, 73),
    SIDE1(82, 77, 74),
    SIDE2(83, 78, 75),
    SIDE3(84, 79, 76),
    SIDE4(85, 80, 77),
    SIDE5(86, 81, 78),
    SIDE6(87, 82, 79),
    SIDE7(88, 83, 80),
    SIDE8(89, 84, 81),
    SIDE9(90, 85, 82),
    SIDE10(91, 86, 83),
    SIDE11(92, 87, 84),
    SIDE12(93, 88, 85),
    SIDE13(94, 89, 86),
    SIDECRM(95, 90, 87),
    PVP_ICONS(29, 3, 3),
    PM_CONTAINER(36, 93, 90),
    ORBS(25, 33, 33),
    XP_DROPS(34, 9, 9),
    ZEAH(35, 10, 10),
    FLOATER(43, 18, 18),
    BUFF_BAR(32, 6, 6),
    STAT_BOOSTS_HUD(31, 5, 5),
    HELPER_CONTENT(39, 12, 12),
    HPBAR_HUD(28, 2, 2),
    POPOUT(3, 35, 67),
    TLI_LISTENER(4, 36, 68),
    ;

    val cacheName: String get() = name.lowercase()
}
