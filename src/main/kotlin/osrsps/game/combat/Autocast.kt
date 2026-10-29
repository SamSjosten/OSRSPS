package osrsps.game.combat

import osrsps.game.EquipmentTab
import osrsps.game.Player
import osrsps.game.Weapons

/**
 * A player's autocast setting, kept in the varps the client's combat tab and autocast menu read (241 cache): varp 108
 * (lastcastspell) holds varbits autocast_set (bit 0) and autocast_spell (bits 1 to 31, the spell's enum 1986 key, see
 * [CombatSpell.autocast]), and varp 439 (alternate_spells) holds autocast_defmode (bit 8), which lights the combat tab's
 * defensive spell button rather than the normal one. Read and written as raw varp bits, so it works without the
 * cache's varbit types (tests). It is saved with the other varps, so it lasts across logins (OSRS Wiki, Autocast: the
 * setting is remembered).
 */
object Autocast {
    const val VARP_LASTCASTSPELL = 108
    const val VARP_ALTERNATE_SPELLS = 439
    const val DEFMODE_BIT = 8

    /**
     * Varp 43 (com_mode) while autocasting: a value no style button has, so none of them is lit next to the lit spell
     * button. Guessed: 4 is the next free value; live's isn't known.
     */
    const val COM_MODE = 4

    /**
     * The weapon categories whose combat tab shows the spell buttons, as the combat tab's script shows them: staves (18)
     * and the bladed staves (21 and 22).
     */
    val CATEGORIES = setOf(Weapons.STAFF, 21, 22)

    /** The enum 1986 key of the spell the player has picked, 0 for none. */
    fun key(player: Player): Int {
        val varp = player.vars[VARP_LASTCASTSPELL]
        return if (varp and 1 != 0) varp ushr 1 else 0
    }

    /** Whether the player picked the spell with the defensive (shield) button. */
    fun defensive(player: Player): Boolean = (player.vars[VARP_ALTERNATE_SPELLS] ushr DEFMODE_BIT) and 1 == 1

    /** Whether the player autocasts with a weapon of [category]: a spell is picked and the weapon can cast it. */
    fun active(player: Player, category: Int): Boolean = key(player) != 0 && category in CATEGORIES

    /** Picks [spell] to autocast, defensively or not, and unlights the style buttons. */
    fun set(player: Player, spell: CombatSpell, defensive: Boolean) {
        require(spell.autocast >= 1) { "${spell.name} can't be autocast" }
        player.vars[VARP_LASTCASTSPELL] = (spell.autocast shl 1) or 1
        setDefensive(player, defensive)
        player.vars[EquipmentTab.VARP_COM_MODE] = COM_MODE
    }

    /** No spell picked any more; the style buttons are left to the caller. */
    fun clear(player: Player) {
        player.vars[VARP_LASTCASTSPELL] = 0
        setDefensive(player, false)
    }

    private fun setDefensive(player: Player, defensive: Boolean) {
        val bit = 1 shl DEFMODE_BIT
        val varp = player.vars[VARP_ALTERNATE_SPELLS]
        player.vars[VARP_ALTERNATE_SPELLS] = if (defensive) varp or bit else varp and bit.inv()
    }
}
