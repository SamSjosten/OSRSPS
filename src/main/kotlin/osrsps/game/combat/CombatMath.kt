package osrsps.game.combat

import osrsps.game.GameRandom

/**
 * The melee formulas, from the OSRS Wiki's "Damage per second/Melee" and the Wiki's DPS calculator (weirdgloop's
 * osrs-dps-calc, PlayerVsNPCCalc and NPCVsPlayerCalc), which the Wiki keeps in line with Jagex's answers. Prayers,
 * potions' boosts aside from the current level, void and target-specific gear come with the pieces that add them.
 *
 * An attack hits when a roll of 0 to the attacker's attack roll beats a roll of 0 to the defender's defence roll; a hit
 * does 0 to the max hit, every value as likely. Both from the Wiki.
 */
object CombatMath {
    /** The style bonus to effective Attack: +3 accurate, +1 controlled. */
    fun attackStanceBonus(stance: AttackStance): Int = when (stance) {
        AttackStance.ACCURATE -> 3
        AttackStance.CONTROLLED -> 1
        else -> 0
    }

    /** The style bonus to effective Strength: +3 aggressive, +1 controlled. */
    fun strengthStanceBonus(stance: AttackStance): Int = when (stance) {
        AttackStance.AGGRESSIVE -> 3
        AttackStance.CONTROLLED -> 1
        else -> 0
    }

    /** The style bonus to effective Defence: +3 defensive or longrange, +1 controlled. */
    fun defenceStanceBonus(stance: AttackStance): Int = when (stance) {
        AttackStance.DEFENSIVE, AttackStance.LONGRANGE -> 3
        AttackStance.CONTROLLED -> 1
        else -> 0
    }

    /** A player's melee attack roll: (Attack + style bonus + 8) * (attack bonus + 64). [attackLevel] is the current level. */
    fun playerAttackRoll(attackLevel: Int, stance: AttackStance, attackBonus: Int): Int =
        (attackLevel + attackStanceBonus(stance) + 8) * (attackBonus + 64)

    /** A player's melee max hit: ((Strength + style bonus + 8) * (strength bonus + 64) + 320) / 640, rounded down. */
    fun playerMaxHit(strengthLevel: Int, stance: AttackStance, strengthBonus: Int): Int =
        ((strengthLevel + strengthStanceBonus(stance) + 8) * (strengthBonus + 64) + 320) / 640

    /** A player's defence roll against an NPC's attack: (Defence + style bonus + 8) * (defence bonus + 64). */
    fun playerDefenceRoll(defenceLevel: Int, stance: AttackStance, defenceBonus: Int): Int =
        (defenceLevel + defenceStanceBonus(stance) + 8) * (defenceBonus + 64)

    /** An NPC's melee attack roll: (Attack + 9) * (attack bonus + 64). NPCs' +1 style bonus and +8 make the 9. */
    fun npcAttackRoll(attackLevel: Int, attackBonus: Int): Int = (attackLevel + 9) * (attackBonus + 64)

    /** An NPC's defence roll: (Defence + 9) * (defence bonus + 64). */
    fun npcDefenceRoll(defenceLevel: Int, defenceBonus: Int): Int = (defenceLevel + 9) * (defenceBonus + 64)

    /** An NPC's melee max hit from its levels: ((Strength + 9) * (strength bonus + 64) + 320) / 640, rounded down. */
    fun npcMaxHit(strengthLevel: Int, strengthBonus: Int): Int = ((strengthLevel + 9) * (strengthBonus + 64) + 320) / 640

    /**
     * The chance an attack roll of [attack] beats a defence roll of [defence], as the Wiki's calculator has it:
     * 1 - (defence + 2) / (2 * (attack + 1)) when the attack roll is higher, attack / (2 * (defence + 1)) otherwise. A
     * roll below 0 (a bonus under -64) counts as 0 here and in [rollHits]; the calculator treats those cases more finely,
     * and no Lumbridge fight has one.
     */
    fun hitChance(attack: Int, defence: Int): Double {
        val a = attack.coerceAtLeast(0).toDouble()
        val d = defence.coerceAtLeast(0).toDouble()
        return if (a > d) 1 - (d + 2) / (2 * (a + 1)) else a / (2 * (d + 1))
    }

    /** Rolls whether [attack] beats [defence]: two rolls from [random], the attack's first. */
    fun rollHits(random: GameRandom, attack: Int, defence: Int): Boolean {
        val attackRoll = random.between(0, attack.coerceAtLeast(0))
        val defenceRoll = random.between(0, defence.coerceAtLeast(0))
        return attackRoll > defenceRoll
    }

    /** Rolls a hit's damage, 0 to [maxHit]. */
    fun rollDamage(random: GameRandom, maxHit: Int): Int = random.between(0, maxHit.coerceAtLeast(0))

    /**
     * XP in tenths for [damage] dealt with a melee style that trains [skills]: 4 XP a point to one skill, or 4/3 to each
     * of three for a shared style (OSRS Wiki, Combat). Hitpoints gets 4/3 a point too; see [hitpointsXpTenths].
     * Inferred: the thirds are rounded down in tenths, so 1 damage gives 1.3 XP.
     */
    fun styleXpTenths(damage: Int, skills: Int): Int = if (skills <= 1) damage * 40 else damage * 40 / 3

    /** Hitpoints XP in tenths for [damage]: 1.33 a point (OSRS Wiki, Combat), rounded down in tenths (inferred). */
    fun hitpointsXpTenths(damage: Int): Int = damage * 40 / 3
}
