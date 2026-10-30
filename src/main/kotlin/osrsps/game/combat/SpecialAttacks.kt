package osrsps.game.combat

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.NpcStat
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.dialogue.DialogueText
import osrsps.game.npc.Npc

private val logger = KotlinLogging.logger {}

/**
 * One weapon's special attack, as far as a melee swing goes: [anim] and the [spotanim] on the player in place of the
 * style's swing (at height 0, guessed), the attack roll raised by [accuracyPercent], the max hit by each of [damagePercents] in turn (rounding
 * down after each, as the Wiki gives the Bandos godsword's two 10%), and [effect] on the NPC once the hit lands.
 */
class SpecialAttack(
    val name: String,
    val anim: Int,
    val spotanim: Int,
    val accuracyPercent: Int = 0,
    val damagePercents: List<Int> = emptyList(),
    val effect: SpecialEffect = SpecialEffect.None,
) {
    fun attackRoll(roll: Int): Int = (roll.toLong() * (100 + accuracyPercent) / 100).toInt()

    fun maxHit(maxHit: Int): Int = damagePercents.fold(maxHit) { hit, percent -> hit * (100 + percent) / 100 }
}

/** What a special attack does to the NPC it hits, besides the damage. */
sealed interface SpecialEffect {
    data object None : SpecialEffect

    /** Drains [percent] of the NPC's Defence as it is now, if the hit dealt damage (the dragon warhammer's Smash). */
    data class DrainDefence(val percent: Int) : SpecialEffect
}

/**
 * Special attacks (OSRS Wiki, Special attacks, read 2026-10-30): the energy bar the combat tab and the minimap orb show,
 * which is varp 300 (sa_energy, tenths of a percent, so 1000 is full), and whether the bar is lit, varp 301 (sa_attack).
 * Each weapon's cost is the cache's enum 906, which the combat tab's own scripts read to show the bar and its cost
 * (combat_interface_sp_redraw, script3648), so the server and the bar agree. Energy comes back 10% every 30 seconds (50
 * ticks), on a timer that starts again at each login ("also resets when logging out"); the Wiki says the energy is the
 * player's, not the weapon's.
 *
 * Clicking the bar or the orb lights it for the next swing. The swing that uses it takes the energy and puts the bar out;
 * a weapon with a special that isn't in yet leaves it unlit. Guessed: that the regeneration timer runs at full energy too,
 * that a click without the energy leaves the bar unlit with a message, that a weapon with no special puts the bar out
 * when it is wielded, and that death fills the bar as it does the run energy ([Deaths]). Not in yet: the Lightbearer's
 * double rate, specials that hit at once (the granite maul), ranged and magic specials, and the offhand bar.
 */
class SpecialAttacks(
    /** Each weapon's cost in tenths of a percent, by obj id: enum 906. Weapons it doesn't name have no special. */
    private val costs: Map<Int, Int>,
    private val drains: NpcDrains,
    private val message: (Player, String) -> Unit = Player::message,
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
) {
    /** What [weapon]'s special costs, 0 for a weapon with none. */
    fun cost(weapon: ObjType?): Int = weapon?.let { costs[it.id] }?.coerceAtLeast(0) ?: 0

    /** [weapon]'s special attack, null for a weapon with none or one that isn't in yet. */
    fun special(weapon: ObjType?): SpecialAttack? = weapon?.takeIf { cost(it) > 0 }?.internalName?.let { BY_WEAPON[it] }

    fun energy(player: Player): Int = player.vars[VARP_ENERGY]

    fun setEnergy(player: Player, energy: Int) {
        require(energy in 0..FULL) { "Special attack energy $energy" }
        player.vars[VARP_ENERGY] = energy
    }

    fun active(player: Player): Boolean = player.vars[VARP_ACTIVE] != 0

    /** The bar or the orb was clicked with [weapon] wielded: lights it, or puts it out if it is lit. */
    fun toggle(player: Player, weapon: ObjType?) {
        if (active(player)) {
            player.vars[VARP_ACTIVE] = 0
            return
        }
        val cost = cost(weapon)
        // No bar shows for a weapon with no special; the orb's click does nothing then (guessed).
        if (cost <= 0) return
        if (special(weapon) == null) {
            logger.info { "${player.username} used the special attack of ${weapon?.name} (${weapon?.id}), which isn't in yet" }
            return
        }
        if (energy(player) < cost) {
            message(player, words(player)[CombatLines.SPEC_NOT_ENOUGH])
            return
        }
        player.vars[VARP_ACTIVE] = 1
    }

    /**
     * A swing with [weapon]: the special attack if the bar is lit and the energy is there, which it takes, putting the
     * bar out; null for an ordinary swing.
     */
    fun use(player: Player, weapon: ObjType?): SpecialAttack? {
        if (!active(player)) return null
        player.vars[VARP_ACTIVE] = 0
        val special = special(weapon) ?: return null
        val cost = cost(weapon)
        if (energy(player) < cost) {
            message(player, words(player)[CombatLines.SPEC_NOT_ENOUGH])
            return null
        }
        player.vars[VARP_ENERGY] = energy(player) - cost
        return special
    }

    /** [special]'s hit on [npc] landed, dealing [dealt]. */
    fun landed(npc: Npc, dealt: Int, special: SpecialAttack) {
        when (val effect = special.effect) {
            SpecialEffect.None -> {}
            is SpecialEffect.DrainDefence -> if (dealt > 0) drains.drainPercent(npc, NpcStat.DEFENCE, effect.percent)
        }
    }

    /** The weapon changed: the bar goes out under one with no special (guessed). */
    fun weaponChanged(player: Player, weapon: ObjType?) {
        if (active(player) && special(weapon) == null) player.vars[VARP_ACTIVE] = 0
    }

    /** A tick logged in: every [REGEN_TICKS], [REGEN] energy back, up to [FULL]. */
    fun process(player: Player) {
        if (++player.specRegenTicks < REGEN_TICKS) return
        player.specRegenTicks = 0
        val energy = energy(player)
        if (energy < FULL) player.vars[VARP_ENERGY] = minOf(FULL, energy + REGEN)
    }

    /** Full energy and the bar out, as a new account, a death and a `::scenario` leave it. */
    fun refill(player: Player) {
        player.vars[VARP_ENERGY] = FULL
        player.vars[VARP_ACTIVE] = 0
    }

    /** The energy and the regeneration timer, for `::spec`. */
    fun describe(player: Player): String {
        val energy = energy(player)
        val lit = if (active(player)) ", the bar lit" else ""
        val regen = if (energy < FULL) ", 10% back in ${REGEN_TICKS - player.specRegenTicks} ticks" else ""
        return "Special attack energy ${energy / 10}%$lit$regen."
    }

    companion object {
        /** sa_energy and sa_attack. */
        const val VARP_ENERGY = 300
        const val VARP_ACTIVE = 301

        const val FULL = 1000
        const val REGEN = 100
        const val REGEN_TICKS = 50

        /** The cache's enum of special attack costs, obj to tenths of a percent. */
        const val COST_ENUM = 906

        /**
         * Smash (OSRS Wiki, Dragon warhammer): 50% more damage and a 30% drain of the target's Defence as it is now, on a
         * hit that deals damage; accuracy as a normal swing. Animation dragon_warhammer_sa_player (1378) and spotanim
         * dragon_warhammer_sa_spotanim (1292), by their 241 cache names.
         */
        val DRAGON_WARHAMMER = SpecialAttack("Smash", anim = 1378, spotanim = 1292, damagePercents = listOf(50), effect = SpecialEffect.DrainDefence(30))

        /** The specials that are in, by their weapons' gameval names. */
        private val BY_WEAPON = mapOf(
            "dragon_warhammer" to DRAGON_WARHAMMER,
            "dragon_warhammer_ornament" to DRAGON_WARHAMMER,
        )
    }
}
