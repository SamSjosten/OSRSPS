package osrsps.game.look

import osrsps.game.FrameSlot
import osrsps.game.Interfaces
import osrsps.game.Player
import osrsps.game.PlayerLook

/**
 * The character design screen, `player_design` (679, "Character Creator"), which a new account gets at its first
 * login (looks step 4; on live it opens at the start of Tutorial Island, which nobody here does). Its components are
 * static and send op1 themselves (`transmitop=1` in the rev 242 osrs-dumps' `player_design.if3`):
 * - a left and a right arrow for each body part, hair to feet: 679:15 and 16, then every 4 to feet's 39 and 40;
 * - a left and a right arrow for each colour, hair, torso, legs, feet and skin: 679:46 and 47, then every 4 to 62 and 63;
 * - body type A and B (`gender_male` 68, `gender_female` 69), which the client draws from `player_design_bodytype`
 *   (varbit 14021);
 * - the Pronouns dropdown (`script6046`: the settings' own dropdown on struct 994, setting 387): option n (from 0) is
 *   child n + 1 of `pronouns_buttons` (679:78), as `settings_side_dropdown_open` makes it, and the client sets
 *   `settings_transmit_pronouns` itself;
 * - Confirm (679:74).
 * The model in the middle (`player`, clientcode 328) is the player's own, so each change goes on the player.
 *
 * GUESSED, not seen on live:
 * - an arrow steps through the styles the makeover screen's page for that part lists (the same rows, in the same
 *   order), wrapping at the ends; a kit no page lists steps to the first (right) or last (left) style;
 * - a colour arrow steps through the part's colours (the makeover pages' swatch counts), and skin through the colours
 *   that are always unlocked (0 to 7 and 12, as on the body types page);
 * - a body type switch keeps each part's style, as the body types page does;
 * - each change is kept at once, so closing the screen another way keeps what was picked; Confirm closes it. Nothing
 *   reopens it: a later look change is a makeover's.
 */
class DesignScreen(
    private val catalog: () -> MakeoverCatalog,
    private val interfaces: Interfaces,
    /** Keeps [PlayerLook] as the player's look and shows it. */
    private val keep: (Player, PlayerLook) -> Unit,
    /** A body type switch, as the makeover screen's body types page makes it. */
    private val switchBodyType: (PlayerLook, Int) -> PlayerLook?,
) {
    /** Whether the screen can be drawn: the cache's makeover pages are there (the tests' tiny caches have none). */
    fun available(): Boolean = MakeoverPage.entries.all { catalog()[it]?.styles?.isNotEmpty() == true }

    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == INTERFACE

    /** Opens the screen for [player] on their look. */
    fun open(player: Player) {
        if (!available()) return
        interfaces.closeModals(player)
        interfaces.setVarbitNow(player, VARBIT_BODYTYPE, player.look.bodyType)
        interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        interfaces.setEvents(player, INTERFACE, PRONOUNS_BUTTONS, 1, PlayerLook.PRONOUNS, Interfaces.IF_EVENT_OP1)
    }

    /** A click on the screen. Returns false when it isn't on this screen. */
    fun click(player: Player, interfaceId: Int, component: Int, sub: Int): Boolean {
        if (interfaceId != INTERFACE) return false
        if (!isOpen(player) || player.scripts.delayed) return true
        val look = player.look
        when (component) {
            in PART_ARROWS -> {
                val offset = component - PART_ARROWS.first
                if (offset % 4 > 1) return true
                step(look, offset / 4, if (offset % 4 == 0) -1 else 1)?.let { keep(player, it) }
            }
            in COLOUR_ARROWS -> {
                val offset = component - COLOUR_ARROWS.first
                if (offset % 4 > 1) return true
                val part = PlayerLook.ColourPart.entries[offset / 4]
                keep(player, look.withColour(part, stepColour(part, look.colours[part.ordinal], if (offset % 4 == 0) -1 else 1)))
            }
            BODY_TYPE_A, BODY_TYPE_B -> {
                val bodyType = if (component == BODY_TYPE_A) PlayerLook.BODY_TYPE_A else PlayerLook.BODY_TYPE_B
                if (bodyType == look.bodyType) return true
                val switched = switchBodyType(look, bodyType) ?: return true
                keep(player, switched)
                interfaces.setVarbitNow(player, VARBIT_BODYTYPE, bodyType)
            }
            PRONOUNS_BUTTONS -> if (sub in 1..PlayerLook.PRONOUNS && sub - 1 != look.pronoun) keep(player, look.copy(pronoun = sub - 1))
            CONFIRM -> interfaces.closeModals(player)
        }
        return true
    }

    /** [look] with body part [part]'s style moved [by] one along its page, or null when no page lists the part. */
    fun step(look: PlayerLook, part: Int, by: Int): PlayerLook? {
        val page = MakeoverPage.entries.firstOrNull { it.part == part } ?: return null
        val kits = catalog()[page]?.styles?.map { it.kit(look.bodyType) }?.distinct()?.takeIf { it.isNotEmpty() } ?: return null
        val at = kits.indexOf(look.kits[part])
        val next = when {
            at >= 0 -> Math.floorMod(at + by, kits.size)
            by > 0 -> 0
            else -> kits.size - 1
        }
        return look.withKit(part, kits[next])
    }

    /** Colour [colour] of [part] moved [by] one, wrapping. */
    fun stepColour(part: PlayerLook.ColourPart, colour: Int, by: Int): Int {
        val colours = if (part == PlayerLook.ColourPart.SKIN) SKINS else (0 until part.count).toList()
        val at = colours.indexOf(colour)
        return colours[if (at >= 0) Math.floorMod(at + by, colours.size) else if (by > 0) 0 else colours.size - 1]
    }

    companion object {
        const val INTERFACE = 679

        /** head_left (15) to feet_right (40): each part's left and right arrows, then the next part's layer and text. */
        val PART_ARROWS = 15..40

        /** hair_left (46) to skin_right (63), in [PlayerLook.ColourPart]'s order. */
        val COLOUR_ARROWS = 46..63
        const val BODY_TYPE_A = 68
        const val BODY_TYPE_B = 69
        const val CONFIRM = 74
        const val PRONOUNS_BUTTONS = 78

        /** player_design_bodytype, which draws which body type button is picked. */
        const val VARBIT_BODYTYPE = 14021

        /** The skin colours a design arrow steps through, in number order: those always unlocked. */
        val SKINS = MakeoverScreen.UNLOCKED_SKINS.sorted()
    }
}
