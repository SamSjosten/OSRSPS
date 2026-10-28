package osrsps.game.combat

import osrsps.game.GameRandom

/**
 * The melee and ranged formulas, from the OSRS Wiki's "Damage per second/Melee" and "/Ranged" and the Wiki's DPS calculator (weirdgloop's
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

    /** The style bonus to effective Ranged, for accuracy and damage alike: +3 accurate (OSRS Wiki, Ranged). */
    fun rangedStanceBonus(stance: AttackStance): Int = if (stance == AttackStance.ACCURATE) 3 else 0

    /** A player's ranged attack roll: (Ranged + style bonus + 8) * (ranged attack bonus + 64). */
    fun playerRangedAttackRoll(rangedLevel: Int, stance: AttackStance, rangedAttackBonus: Int): Int =
        (rangedLevel + rangedStanceBonus(stance) + 8) * (rangedAttackBonus + 64)

    /** A player's ranged max hit: ((Ranged + style bonus + 8) * (ranged strength + 64) + 320) / 640, rounded down. */
    fun playerRangedMaxHit(rangedLevel: Int, stance: AttackStance, rangedStrength: Int): Int =
        ((rangedLevel + rangedStanceBonus(stance) + 8) * (rangedStrength + 64) + 320) / 640

    /**
     * The twisted bow's accuracy, in percent of the attack roll, against a target whose Magic is [magic] (the higher of
     * its Magic level and magic attack bonus, at most [TWISTED_BOW_MAGIC_CAP]): 140 + (3M - 10) / 100 - (3M / 10 - 100)²
     * / 100, from 0 to 140 (OSRS Wiki, Twisted bow). Where the game rounds down is inferred; the Wiki's chart (39.9% at
     * 0, 93.9% at 100, 140% from 244) is the unrounded curve, which this follows to within a point.
     */
    fun twistedBowAccuracy(magic: Int): Int {
        val m = magic.coerceIn(0, TWISTED_BOW_MAGIC_CAP)
        val tenth = 3 * m / 10
        return (140 + (3 * m - 10) / 100 - (tenth - 100) * (tenth - 100) / 100).coerceIn(0, 140)
    }

    /** The twisted bow's damage, in percent of the max hit: 250 + (3M - 14) / 100 - (3M / 10 - 140)² / 100, 0 to 250. */
    fun twistedBowDamage(magic: Int): Int {
        val m = magic.coerceIn(0, TWISTED_BOW_MAGIC_CAP)
        val tenth = 3 * m / 10
        return (250 + (3 * m - 14) / 100 - (tenth - 140) * (tenth - 140) / 100).coerceIn(0, 250)
    }

    /** Outside the Chambers of Xeric (350 there; OSRS Wiki, Twisted bow). */
    const val TWISTED_BOW_MAGIC_CAP = 250

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
     * XP in tenths for [damage] dealt with a style that trains [skills]: 4 XP a point to one skill, 2 to each of two
     * (ranged and magic longrange: Ranged or Magic, and Defence), or 4/3 to each of three for a shared melee style (OSRS
     * Wiki, Combat). Hitpoints gets 4/3 a point too; see [hitpointsXpTenths]. Inferred: the thirds are rounded down in
     * tenths, so 1 damage gives 1.3 XP. A style that trains nothing gives none.
     */
    fun styleXpTenths(damage: Int, skills: Int): Int = when {
        skills <= 0 -> 0
        skills == 1 -> damage * 40
        skills == 2 -> damage * 20
        else -> damage * 40 / 3
    }

    /** Hitpoints XP in tenths for [damage]: 1.33 a point (OSRS Wiki, Combat), rounded down in tenths (inferred). */
    fun hitpointsXpTenths(damage: Int): Int = damage * 40 / 3
}
