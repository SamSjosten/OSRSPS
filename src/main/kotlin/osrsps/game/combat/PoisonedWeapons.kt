package osrsps.game.combat

import osrsps.game.ObjType

/**
 * Which weapons and ammunition poison or envenom what they hit (OSRS Wiki, Poison and Venom, read 2026-10-30). A
 * poisoned weapon is one whose name ends "(p)", "(p+)" or "(p++)": weapon poison, (+) and (++), severities 20, 25 and
 * 30, so its poison starts at 4, 5 or 6 damage. Ranged ammunition and thrown weapons take 14 off ("Ranged poisons have
 * their severity lowered by 14"), starting at 2, 3 or 4. A melee hit poisons one time in four, a ranged one in eight,
 * and only a hit that does damage ("Damage must be non-zero"). The toxic blowpipe envenoms one hit in four.
 *
 * Guessed: that a hit's dice for poison are rolled as it lands, after its damage; and that venom needs a damaging hit
 * too (the Venom page doesn't say). Not here yet: karambwan paste, dynamite(p), the abyssal tentacle, smoke spells,
 * emerald bolts (e), the Serpentine helm's raised chances, and the other venom weapons (the trident of the swamp, the
 * toxic staff of the dead, the noxious halberd).
 */
object PoisonedWeapons {
    /** The poison severity a melee hit with [weapon] gives, 0 for none. */
    fun melee(weapon: ObjType?): Int = severity(weapon)

    /** The poison severity a ranged hit with [ammo] (a thrown weapon, or the ammunition fired) gives, 0 for none. */
    fun ranged(ammo: ObjType?): Int = severity(ammo).let { if (it > 0) it - RANGED_LESS else 0 }

    private fun severity(type: ObjType?): Int {
        val name = type?.name ?: return 0
        return when {
            name.endsWith("(p++)") -> POISON_PLUS_PLUS
            name.endsWith("(p+)") -> POISON_PLUS
            name.endsWith("(p)") -> POISON
            else -> 0
        }
    }

    const val POISON = 20
    const val POISON_PLUS = 25
    const val POISON_PLUS_PLUS = 30
    const val RANGED_LESS = 14

    /** One melee hit in four, and one ranged hit in eight, poisons. */
    const val MELEE_CHANCE = 4
    const val RANGED_CHANCE = 8

    /** One toxic blowpipe hit in four envenoms. */
    const val VENOM_CHANCE = 4
}
