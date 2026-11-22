package osrsps.game.look

import osrsps.game.FrameSlot
import osrsps.game.Interfaces
import osrsps.game.Player
import osrsps.game.PlayerLook

/**
 * The makeover screen, `makeover` (516), in the main modal slot: its hair, facial hair and clothes pages (looks steps
 * 2 and 3), and its body types page, which is another interface, `makeover_mage` (205; [BODY_TYPES]). The client draws
 * it from the cache as it loads (`makeover_init`, rev 242 osrs-dumps), reading which page to draw from
 * `makeover_interface_type` (varbit 4130) and the body type from `makeover_bodytype` (varbit 10989), both in varp
 * `makeover_temp`; the highlighted style from varp `makeover_hair` (3788) and the highlighted swatch from
 * `makeover_colour` (3789). A change to any of the three varps redraws it (`makeover_redraw_content`).
 *
 * What the client sends, given the events [open] sets:
 * - a style's op1 "Preview": `item_area` (516:7), child 5 × the style's place (each style draws five children: its
 *   backing, frame, two models and name; `makeover_draw_item`);
 * - a swatch's op1 "Colour": `colours` (516:24), child 2 × the swatch's place (a fill and a frame each);
 * - "Apply" on `select` (516:27) and "Confirm" on `select_and_close` (516:28), on the button's text child;
 * - the burger menu's "View <page>": `burger_menu_frame` (516:31), child 10 + 2 × the page's type
 *   (`makeover_create_dropdown`: its backing and steelbox take children 0 to 8, then a highlight and a text per page).
 *   The page being shown has no op.
 *
 * The body types page (205) draws from the generic varps `if1` (body type, 261), `if2` (skin colour, 262) and `if3`
 * (pronoun, 263), and sets them itself as they are clicked (`script5988`, `makeovermage_colour_op`), so the server
 * mirrors each click: Body type A and B (205:2 and 205:8, static op1), a skin swatch (`colour`, 205:13, child n for the
 * nth colour of enum 999's order, op1 "Select colour"), He/him, They/them and She/her (205:21, 24 and 27), Apply and
 * Confirm (205:17 and 18), and the same burger menu (205:32). Locked skin colours (the Halloween and League ones,
 * `makeover_unlockedcolour`) aren't drawn and are refused. A body type switch keeps each part's style, taking the
 * other body type's kit of it from the pages' rows (INFERRED from each row holding both kits); a part no page lists
 * takes that body type's first style. The pronoun follows the body type unless it is they/them (the Wiki's Makeover
 * page, read 2026-11-19).
 *
 * The model in the middle (`player_model`, clientcode 328) is the player's own, so a preview has to change the
 * player's appearance. GUESSED, not seen on live: a preview shows on the player for everyone around until Apply keeps
 * it or the screen closes without it, which puts the saved look back; Confirm keeps it and closes the screen; a page
 * switch drops what wasn't applied.
 */
class MakeoverScreen(
    private val catalog: () -> MakeoverCatalog,
    private val interfaces: Interfaces,
    /** Shows [PlayerLook] on the player without keeping it. */
    private val show: (Player, PlayerLook) -> Unit,
    /** Keeps [PlayerLook] as the player's look and shows it. */
    private val keep: (Player, PlayerLook) -> Unit,
    /** Runs after a Confirm, with the page (null for body types) and whoever opened the screen for the player (null for none). */
    private val confirmed: (Player, MakeoverPage?, Any?) -> Unit = { _, _, _ -> },
) {
    /**
     * What a player has open: the page (null for body types), who opened it (Thessalia's NPC, for her line after a
     * Confirm) and the preview.
     */
    private class Open(val page: MakeoverPage?, val by: Any?, var preview: PlayerLook)

    private val open = HashMap<Player, Open>()

    init {
        for (id in listOf(INTERFACE, BODY_TYPES)) {
            interfaces.onClose(id) { player ->
                // Whatever wasn't applied goes: the saved look is shown again.
                val was = open.remove(player) ?: return@onClose
                if (was.preview != player.look) show(player, player.look)
            }
        }
    }

    /** Lets go of [player]'s screen as they leave the world, which closes no modal; their saved look is what's kept. */
    fun forget(player: Player) {
        open.remove(player)
    }

    /**
     * [pronoun] picked somewhere else (the All Settings window, a floater the screen stays open under) and already kept:
     * the preview takes it too, so an Apply or Confirm after it doesn't put the old pronoun back.
     */
    fun pronounPicked(player: Player, pronoun: Int) {
        val state = open[player] ?: return
        preview(player, state, state.preview.copy(pronoun = pronoun))
        if (state.page == null) setVarp(player, VARP_IF3, pronoun)
    }

    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL].let { it == INTERFACE || it == BODY_TYPES } && player in open

    /** Opens the body types page (205) for [player]. */
    fun openBodyTypes(player: Player, by: Any? = null) {
        interfaces.closeModals(player)
        show(player, player.look)
        val look = player.look
        setVarp(player, VARP_IF1, look.bodyType)
        setVarp(player, VARP_IF2, look.colours[PlayerLook.ColourPart.SKIN.ordinal])
        setVarp(player, VARP_IF3, look.pronoun)
        interfaces.openModal(player, FrameSlot.MAINMODAL, BODY_TYPES)
        open[player] = Open(null, by, look)
        interfaces.setEvents(player, BODY_TYPES, BT_COLOUR, 0, SKIN_ORDER.size - 1, Interfaces.IF_EVENT_OP1)
        interfaces.setEvents(player, BODY_TYPES, BT_BURGER_MENU_FRAME, menuChild(0), menuChild(LAST_PAGE_TYPE), Interfaces.IF_EVENT_OP1)
    }

    /** Opens the screen on [page] for [player]; [by] is whoever offered it, handed back to [confirmed]. */
    fun open(player: Player, page: MakeoverPage, by: Any? = null) {
        val styles = catalog()[page] ?: return
        interfaces.closeModals(player)
        show(player, player.look)
        val look = player.look
        interfaces.setVarbitNow(player, VARBIT_INTERFACE_TYPE, page.type)
        interfaces.setVarbitNow(player, VARBIT_BODYTYPE, look.bodyType)
        setVarp(player, VARP_SELECTED, selected(styles, look, page))
        setVarp(player, VARP_COLOUR, page.colour?.let { look.colours[it.ordinal] } ?: 0)
        interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        open[player] = Open(page, by, look)
        if (styles.styles.isNotEmpty()) interfaces.setEvents(player, INTERFACE, ITEM_AREA, 0, styles.styles.size * CHILDREN_PER_STYLE - 1, Interfaces.IF_EVENT_OP1)
        if (styles.colours > 0) interfaces.setEvents(player, INTERFACE, COLOURS, 0, styles.colours * CHILDREN_PER_COLOUR - 1, Interfaces.IF_EVENT_OP1)
        for (button in listOf(SELECT, SELECT_AND_CLOSE)) interfaces.setEvents(player, INTERFACE, button, 0, BUTTON_CHILDREN, Interfaces.IF_EVENT_OP1)
        interfaces.setEvents(player, INTERFACE, BURGER_MENU_FRAME, menuChild(0), menuChild(LAST_PAGE_TYPE), Interfaces.IF_EVENT_OP1)
    }

    /** A click on the screen. Returns false when it isn't on this screen. */
    fun click(player: Player, interfaceId: Int, component: Int, sub: Int): Boolean {
        if (interfaceId != INTERFACE && interfaceId != BODY_TYPES) return false
        val state = open[player]?.takeIf { player.modals[FrameSlot.MAINMODAL] == interfaceId } ?: return true
        if (player.scripts.delayed) return true
        if (interfaceId == BODY_TYPES) {
            clickBodyTypes(player, state, component, sub)
            return true
        }
        val shown = state.page ?: return true
        val page = catalog()[shown] ?: return true
        when (component) {
            ITEM_AREA -> {
                if (sub % CHILDREN_PER_STYLE != 0) return true
                val style = page.styles.getOrNull(sub / CHILDREN_PER_STYLE) ?: return true
                preview(player, state, state.preview.withKit(shown.part, style.kit(state.preview.bodyType)))
                setVarp(player, VARP_SELECTED, sub)
            }
            COLOURS -> {
                val part = shown.colour ?: return true
                if (sub % CHILDREN_PER_COLOUR != 0) return true
                val colour = sub / CHILDREN_PER_COLOUR
                // The look's own colour range can be narrower than the page's swatches; neither is sent past its end.
                if (colour >= page.colours || colour >= part.count) return true
                preview(player, state, state.preview.withColour(part, colour))
                setVarp(player, VARP_COLOUR, colour)
            }
            SELECT -> keep(player, state.preview)
            SELECT_AND_CLOSE -> confirm(player, state)
            BURGER_MENU_FRAME -> switchPage(player, state, sub)
        }
        return true
    }

    private fun clickBodyTypes(player: Player, state: Open, component: Int, sub: Int) {
        val look = state.preview
        when (component) {
            BT_BODY_TYPE_A, BT_BODY_TYPE_B -> {
                val bodyType = if (component == BT_BODY_TYPE_A) PlayerLook.BODY_TYPE_A else PlayerLook.BODY_TYPE_B
                if (bodyType == look.bodyType) return
                val switched = switchBodyType(look, bodyType) ?: return
                preview(player, state, switched)
                setVarp(player, VARP_IF1, bodyType)
                setVarp(player, VARP_IF3, switched.pronoun)
            }
            BT_COLOUR -> {
                val colour = SKIN_ORDER.getOrNull(sub)?.takeIf { it in UNLOCKED_SKINS } ?: return
                preview(player, state, look.withColour(PlayerLook.ColourPart.SKIN, colour))
                setVarp(player, VARP_IF2, colour)
            }
            BT_HE_HIM, BT_THEY_THEM, BT_SHE_HER -> {
                val pronoun = when (component) {
                    BT_HE_HIM -> PRONOUN_HE
                    BT_SHE_HER -> PRONOUN_SHE
                    else -> PRONOUN_THEY
                }
                preview(player, state, look.copy(pronoun = pronoun))
                setVarp(player, VARP_IF3, pronoun)
            }
            BT_APPLY -> keep(player, state.preview)
            BT_CONFIRM -> confirm(player, state)
            BT_BURGER_MENU_FRAME -> switchPage(player, state, sub)
        }
    }

    /**
     * [look] in [bodyType]: each part's style kept, as that body type's kit of the same row, or its first style when no
     * page lists the kit; the pronoun follows unless it is they/them. Null when the catalog lacks a page it needs.
     */
    fun switchBodyType(look: PlayerLook, bodyType: Int): PlayerLook? {
        var kits = look.kits
        for (page in MakeoverPage.entries) {
            val styles = catalog()[page]?.styles?.takeIf { it.isNotEmpty() } ?: return null
            val style = styles.firstOrNull { it.kit(look.bodyType) == look.kits[page.part] } ?: styles.first()
            kits = kits.toMutableList().also { it[page.part] = style.kit(bodyType) }
        }
        val pronoun = if (look.pronoun == PRONOUN_THEY) PRONOUN_THEY else bodyType
        return look.copy(bodyType = bodyType, kits = kits, pronoun = pronoun)
    }

    private fun confirm(player: Player, state: Open) {
        keep(player, state.preview)
        interfaces.closeModals(player)
        confirmed(player, state.page, state.by)
    }

    /** The burger menu's "View <page>": [sub] is the page's text child. What wasn't applied is dropped. */
    private fun switchPage(player: Player, state: Open, sub: Int) {
        if (sub < menuChild(0) || sub % 2 != 0) return
        val type = (sub - menuChild(0)) / 2
        if (type == BODY_TYPES_TYPE) {
            if (state.page != null) openBodyTypes(player, state.by)
            return
        }
        val to = MakeoverPage.ofType(type)?.takeIf { it != state.page && catalog()[it] != null } ?: return
        open(player, to, state.by)
    }

    private fun preview(player: Player, state: Open, look: PlayerLook) {
        state.preview = look
        show(player, look)
    }

    private fun setVarp(player: Player, id: Int, value: Int) {
        if (id >= player.vars.types.varpCount) return
        player.vars[id] = value
        interfaces.sendVarpNow(player, id)
    }

    companion object {
        const val INTERFACE = 516
        const val ITEM_AREA = 7
        const val COLOURS = 24
        const val SELECT = 27
        const val SELECT_AND_CLOSE = 28
        const val BURGER_MENU_FRAME = 31

        const val BODY_TYPES = 205
        const val BT_BODY_TYPE_A = 2
        const val BT_BODY_TYPE_B = 8
        const val BT_COLOUR = 13
        const val BT_APPLY = 17
        const val BT_CONFIRM = 18
        const val BT_HE_HIM = 21
        const val BT_THEY_THEM = 24
        const val BT_SHE_HER = 27
        const val BT_BURGER_MENU_FRAME = 32

        /** The burger menu's Body types, the type between facial hair and torsos. */
        const val BODY_TYPES_TYPE = 2

        const val VARP_IF1 = 261
        const val VARP_IF2 = 262
        const val VARP_IF3 = 263

        /** The pronouns as `if3` and the appearance block number them. */
        const val PRONOUN_HE = 0
        const val PRONOUN_SHE = 1
        const val PRONOUN_THEY = 2

        /** The skin swatches' order, enum 999 in the 242 cache: the colour drawn as each `colour` child. */
        val SKIN_ORDER = listOf(7, 0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 13)

        /**
         * The skin colours `makeover_unlockedcolour` always draws: 0 to 7 and 12. The rest (8 to 11 and 13) need a
         * Halloween event or the demonic skin contract, which nobody here has done.
         */
        val UNLOCKED_SKINS = (0..7).toSet() + 12

        const val VARP_SELECTED = 3788
        const val VARP_COLOUR = 3789
        const val VARBIT_INTERFACE_TYPE = 4130
        const val VARBIT_BODYTYPE = 10989

        const val CHILDREN_PER_STYLE = 5
        const val CHILDREN_PER_COLOUR = 2

        /** The stone buttons' children (v2_stone_button_filled's pieces, then the text): a few, with room to spare. */
        const val BUTTON_CHILDREN = 15

        /** Wristwear, the burger menu's last page. */
        const val LAST_PAGE_TYPE = 7

        /** The burger menu's text child for page [type]. */
        fun menuChild(type: Int) = 10 + 2 * type

        /** The child of the style the player has now, for `makeover_hair`; -1 when the page doesn't list it. */
        fun selected(page: MakeoverCatalog.Page, look: PlayerLook, which: MakeoverPage): Int {
            val index = page.styles.indexOfFirst { it.kit(look.bodyType) == look.kits[which.part] }
            return if (index < 0) -1 else index * CHILDREN_PER_STYLE
        }
    }
}
