package osrsps.game.combat

import osrsps.game.Player
import osrsps.game.WearPos
import osrsps.game.dialogue.Lines

/** Dragonfire's words (keys only; the words are in data/dialogue.txt). */
object DragonfireLines : Lines("dragonfire", "Dragonfire") {
    val RESISTED = line("resisted", "an unprotected player who won the dragonfire's accuracy roll")
    val BURNT = line("burnt", "an unprotected player who lost the dragonfire's accuracy roll")
    val SHIELD = line("shield", "dragonfire taken with an anti-dragon shield (or a dragonfire shield, ward or ancient wyvern shield)")
    val PRAYER = line("prayer", "dragonfire taken with Protect from Magic and no shield")
}

/**
 * A chromatic dragon's dragonfire (Wilderness step W2.4), breathed in melee reach one turn in [chance] instead of a
 * swing (Mod Ash via the Wiki's Dragonfire page: 1 in 6). [anim] and [cast] are its animation and the spotanim on it.
 * A [metallic] dragon's (bronze, iron, steel...) is typeless: Protect from Magic does nothing against it (the Wiki's
 * Dragonfire page, read 2026-11-25: "Metallic dragons, Drakes, and Galvek are unaffected by Protect from Magic").
 * A dragon with a [range] (the metal dragons, which breathe from a distance on live) breathes at a player it can't reach
 * but sees within that many tiles, each time its attack is due, so standing out of its reach is no safespot. 0 is
 * melee reach only.
 */
data class NpcDragonfire(val chance: Int, val anim: Int = -1, val cast: Int = -1, val metallic: Boolean = false, val range: Int = 0)

/**
 * The chromatic dragons' dragonfire table (the Wiki's Dragonfire page, Damage reduction, read 2026-11-15): unprotected
 * it rolls the dragon's Magic accuracy against the player's Magic defence, up to 50 when the player loses and 30 when
 * they resist; an anti-dragon shield or its upgrades cap it at 5, Protect from Magic alone at 10, both at 5. Antifire
 * potions aren't in the game yet. Guessed: the hit lands at once, as melee's does, and the shield's and the prayer's
 * messages, which the page doesn't give.
 */
object Dragonfire {
    const val UNPROTECTED_MAX = 50
    const val RESISTED_MAX = 30
    const val SHIELD_MAX = 5
    const val PRAYER_MAX = 10

    /** The anti-dragon shield, its other copy, the dragonfire shield (both), the dragonfire ward (both) and the ancient wyvern shield (both). */
    val SHIELDS = setOf(1540, 8282, 11283, 11284, 22002, 22003, 21633, 21634)

    fun shielded(player: Player): Boolean = player.equipment[WearPos.SHIELD]?.id in SHIELDS

    /** The most this breath can hit [player] for, with or without [shield] and [prayer], when they [resisted] or not. */
    fun maxHit(shield: Boolean, prayer: Boolean, resisted: Boolean): Int = when {
        shield -> SHIELD_MAX
        prayer -> PRAYER_MAX
        resisted -> RESISTED_MAX
        else -> UNPROTECTED_MAX
    }

    fun line(shield: Boolean, prayer: Boolean, resisted: Boolean) = when {
        shield -> DragonfireLines.SHIELD
        prayer -> DragonfireLines.PRAYER
        resisted -> DragonfireLines.RESISTED
        else -> DragonfireLines.BURNT
    }
}
