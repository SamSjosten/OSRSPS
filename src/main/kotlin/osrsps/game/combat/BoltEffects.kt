package osrsps.game.combat

import osrsps.game.GameRandom
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.NpcStat
import osrsps.game.npc.Npc

/**
 * The enchanted bolts' effects on NPCs ([BoltEffect], an ammo entry's `"effect"`), rolled as a crossbow fires one.
 *
 * Verified on the OSRS Wiki, each bolt's (e) page (read 2026-11-10), for monsters:
 * - Opal, Lucky Lightning: 5%, hit or miss; your visible Ranged / 10 extra damage.
 * - Pearl, Sea Curse: 6%, hit or miss; your visible Ranged / 20 extra (the fiery 1/15 isn't built).
 * - Sapphire, Clear Mind: 25% on a hit; you gain an eighth of a tenth of the monster's Ranged level as prayer points.
 * - Emerald, Magical Poison: 55% on a hit; the monster is poisoned, 5 a hit at first, 75 in all.
 * - Ruby, Blood Forfeit: 6%, hit or miss; the hit is a fifth of the monster's hitpoints now, at most 100, and you lose
 *   a tenth of yours.
 * - Diamond, Armour Piercing: 10%, hit or miss; a sure hit with the max hit 15% higher.
 * - Dragonstone, Dragon's Breath: 6% on a hit; a fifth of your visible Ranged extra.
 * - Onyx, Life Leech: 11% on a hit; 20% more damage, and you heal a quarter of the damage.
 * - Topaz's Down to Earth works only on players, and Jade's Earth's Fury knockdown isn't built for monsters; neither
 *   rolls here.
 * Each shows its graphic on the monster as the bolt lands (the 242 cache's `xbows_*_spot_anim`, 749 to 758).
 *
 * Guessed: the effect rolled after accuracy and before damage, one draw only for an enchanted bolt (so other shots'
 * draws are unchanged); onyx's 20% applied to the rolled damage, rounded down; the heal, prayer and ruby's cost given
 * as the bolt lands; dragonfire-immune and undead monsters (whom dragonstone and onyx don't affect on live) not yet
 * told apart; the hard Kandarin Diary's 10% more not counted.
 */
object BoltEffects {
    /** What the effect does to one shot: the [damage] to reserve, a [sure] hit, and whether it [fired] (for its graphic). */
    data class Rolled(val hit: Boolean, val damage: Int, val fired: Boolean)

    /** Its chance out of 1000, whether it needs the accuracy roll to pass, and its graphic. */
    private class Spec(val perMille: Int, val onHit: Boolean, val spotanim: Int)

    private val specs = mapOf(
        BoltEffect.OPAL to Spec(50, onHit = false, spotanim = 749),
        BoltEffect.PEARL to Spec(60, onHit = false, spotanim = 750),
        BoltEffect.SAPPHIRE to Spec(250, onHit = true, spotanim = 751),
        BoltEffect.EMERALD to Spec(550, onHit = true, spotanim = 752),
        BoltEffect.ONYX to Spec(110, onHit = true, spotanim = 753),
        BoltEffect.RUBY to Spec(60, onHit = false, spotanim = 754),
        BoltEffect.DRAGONSTONE to Spec(60, onHit = true, spotanim = 756),
        BoltEffect.DIAMOND to Spec(100, onHit = false, spotanim = 758),
    )

    /**
     * The poison severity a Magical Poison gives: 25, hitting 5 first and 75 in all ([Poison]'s fifth rounded up, one
     * less a hit), the Wiki's "5 poison damage" and "75 health points over 7.5 minutes".
     */
    const val EMERALD_POISON = 25

    /** Ruby's cap. */
    const val RUBY_CAP = 100

    /** The graphic [effect] shows as it lands. */
    fun spotanim(effect: BoltEffect): Int = specs.getValue(effect).spotanim

    /** Whether [effect] does anything to a monster (and so takes a draw). */
    fun rolls(effect: BoltEffect): Boolean = effect in specs

    /**
     * One shot with [effect]: whether it fires ([random]'s one draw, taken only when it [rolls]), then the hit and its
     * damage, [roll] giving a normal roll for a max hit and [hit] the accuracy roll's result.
     */
    fun roll(effect: BoltEffect, random: GameRandom, player: Player, npc: Npc, hit: Boolean, maxHit: Int, roll: (Int) -> Int): Rolled {
        val spec = specs[effect] ?: return Rolled(hit, if (hit) roll(maxHit) else 0, false)
        val fires = (random.nextInt(1000) < spec.perMille) && (hit || !spec.onHit)
        if (!fires) return Rolled(hit, if (hit) roll(maxHit) else 0, false)
        val ranged = player.skills.currentLevel(Skill.RANGED)
        val base = if (hit) roll(maxHit) else 0
        return when (effect) {
            BoltEffect.OPAL -> Rolled(true, base + ranged / 10, true)
            BoltEffect.PEARL -> Rolled(true, base + ranged / 20, true)
            BoltEffect.DRAGONSTONE -> Rolled(true, base + ranged / 5, true)
            BoltEffect.ONYX -> Rolled(true, base * 6 / 5, true)
            BoltEffect.RUBY -> Rolled(true, minOf(RUBY_CAP, npc.hitpoints / 5), true)
            BoltEffect.DIAMOND -> Rolled(true, roll(maxHit * 115 / 100), true)
            else -> Rolled(hit, base, true)
        }
    }

    /** What a fired effect gives back as the bolt lands: prayer for sapphire, the heal for onyx (of [dealt]). */
    fun landed(effect: BoltEffect, player: Player, npc: Npc, dealt: Int) {
        when (effect) {
            BoltEffect.SAPPHIRE -> {
                val gain = npc.level(NpcStat.RANGED) / 10 / 8
                val max = player.skills.baseLevel(Skill.PRAYER)
                val now = player.skills.currentLevel(Skill.PRAYER)
                if (gain > 0 && now < max) player.skills.setCurrentLevel(Skill.PRAYER, minOf(max, now + gain))
            }
            BoltEffect.ONYX -> {
                val max = player.skills.baseLevel(Skill.HITPOINTS)
                if (dealt > 0 && player.hitpoints < max) player.hitpoints = minOf(max, player.hitpoints + dealt / 4)
            }
            else -> {}
        }
    }

    /** Ruby's cost to the shooter: a tenth of their hitpoints now. */
    fun rubyCost(player: Player): Int = player.hitpoints / 10
}
