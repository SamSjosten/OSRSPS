package osrsps.game.look

import osrsps.game.FrameSlot
import osrsps.game.Interfaces
import osrsps.game.Player
import osrsps.game.PlayerLook

/**
 * The makeover screen, `makeover` (516), in the main modal slot (looks step 2: its clothes pages). The client draws
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
    /** Runs after a Confirm, with the page and whoever opened the screen for the player (null for none). */
    private val confirmed: (Player, MakeoverPage, Any?) -> Unit = { _, _, _ -> },
) {
    /** What a player has open: the page, who opened it (Thessalia's NPC, for her line after a Confirm) and the preview. */
    private class Open(val page: MakeoverPage, val by: Any?, var preview: PlayerLook)

    private val open = HashMap<Player, Open>()

    init {
        interfaces.onClose(INTERFACE) { player ->
            // Whatever wasn't applied goes: the saved look is shown again.
            val was = open.remove(player) ?: return@onClose
            if (was.preview != player.look) show(player, player.look)
        }
    }

    /** Lets go of [player]'s screen as they leave the world, which closes no modal; their saved look is what's kept. */
    fun forget(player: Player) {
        open.remove(player)
    }

    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == INTERFACE && player in open

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
        if (interfaceId != INTERFACE) return false
        val state = open[player]?.takeIf { isOpen(player) } ?: return true
        if (player.scripts.delayed) return true
        val page = catalog()[state.page] ?: return true
        when (component) {
            ITEM_AREA -> {
                if (sub % CHILDREN_PER_STYLE != 0) return true
                val style = page.styles.getOrNull(sub / CHILDREN_PER_STYLE) ?: return true
                preview(player, state, state.preview.withKit(state.page.part, style.kit(state.preview.bodyType)))
                setVarp(player, VARP_SELECTED, sub)
            }
            COLOURS -> {
                val part = state.page.colour ?: return true
                if (sub % CHILDREN_PER_COLOUR != 0) return true
                val colour = sub / CHILDREN_PER_COLOUR
                // The look's own colour range can be narrower than the page's swatches; neither is sent past its end.
                if (colour >= page.colours || colour >= part.count) return true
                preview(player, state, state.preview.withColour(part, colour))
                setVarp(player, VARP_COLOUR, colour)
            }
            SELECT -> keep(player, state.preview)
            SELECT_AND_CLOSE -> {
                keep(player, state.preview)
                interfaces.closeModals(player)
                confirmed(player, state.page, state.by)
            }
            BURGER_MENU_FRAME -> {
                if (sub % 2 != 0) return true
                val type = (sub - menuChild(0)) / 2
                val to = MakeoverPage.ofType(type)?.takeIf { it != state.page && catalog()[it] != null } ?: return true
                open(player, to, state.by)
            }
        }
        return true
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
