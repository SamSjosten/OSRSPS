package osrsps.game.combat

import osrsps.game.EquipmentBonuses
import osrsps.game.ObjType

/**
 * Dinh's bulwark's own rules (OSRS Wiki, Dinh's bulwark, read 2026-10-30), besides its special ([SpecialAttacks.SHIELD_BASH]):
 * its Pummel hits harder the better the defence worn, by [strengthBonus]; and on its Block style the player can't attack
 * ("players lose the ability to attack") but takes 20% less damage, from 8 ticks after the bulwark is wielded ("an 8-tick
 * delay before the damage reduction applies after switching to the bulwark"). Leaving Block for Pummel puts the next
 * attack 8 ticks off, whatever weapon makes it (World, at the combat tab's click). Guessed: that the reduced damage rounds
 * down, and that the 8 ticks also run from a login with it worn. Only monsters' melee hits are reduced so far, as they are
 * the only ones that hit players yet.
 */
object DinhsBulwark {
    /** The bulwark and its ornament kit's, by gameval name. */
    private val NAMES = setOf("dinhs_bulwark", "dinhs_bulwark_ornament")

    /** Ticks after it is wielded before Block reduces damage. */
    const val BLOCK_DELAY = 8

    /** The share of a hit taken on Block, in percent. */
    const val BLOCKED_PERCENT = 80

    fun isBulwark(weapon: ObjType?): Boolean = weapon?.internalName in NAMES

    /**
     * The melee strength the bulwark adds: max(0, (stab + slash + crush + ranged defence − 800) / 12 − 38), the Wiki's
     * formula, so none while the four average under 314.
     */
    fun strengthBonus(bonuses: EquipmentBonuses): Int =
        maxOf(0, (bonuses.stabDefence + bonuses.slashDefence + bonuses.crushDefence + bonuses.rangedDefence - 800) / 12 - 38)

    /** [damage] as Block lets it through. */
    fun blocked(damage: Int): Int = damage * BLOCKED_PERCENT / 100
}
