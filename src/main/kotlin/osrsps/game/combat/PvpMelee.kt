package osrsps.game.combat

import osrsps.game.GameRandom
import osrsps.game.Interaction
import osrsps.game.Movement
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.map.CollisionMap
import osrsps.map.Reach
import osrsps.map.RouteTarget
import osrsps.map.Tile

/** A player fighting [target], another player, with melee: they walk up to them and swing. See [PvpMelee]. */
class PvpInteraction internal constructor(val target: Player, private val owner: PvpMelee) : Interaction {
    /** Where the target stood when the player's route to them was last made. */
    internal var routedTo: Tile? = null

    /** Whether the player stood in reach of the target when last tried. */
    internal var inReach = false

    override fun tryInteract(player: Player) = owner.tryInteract(player, this)

    override fun holding(player: Player) = inReach
}

/**
 * Melee between players in the Wilderness (step W1.3). Attack on a player the rules allow ([PvpRules]) becomes a
 * [PvpInteraction]: each movement phase the player walks up to the target and, once beside them, swings every
 * weapon-speed ticks, as at an NPC ([MeleeCombat]). The rules are asked again before every swing, so a target who steps
 * out of the Wilderness or out of range is let go.
 *
 * A swing rolls the attacker's attack roll against the target's own defence roll ([Combat.pvpMatchup]), then damage,
 * as against an NPC (OSRS Wiki, Combat). Protect from Melee on the target cuts the damage by 40% (OSRS Wiki, Protect
 * from Melee: it "will resist 40% of melee damage by other players"); guessed: rounded down, and asked as the swing is
 * rolled. XP is given for the damage the swing deals, as against an NPC.
 *
 * Melee has no hit delay (OSRS Wiki, Hit delay), and in PvP the processing order (PID) decides whether a hit waits a
 * tick. Here the hit lands when the target is next processed in the movement phase ([land]): the same tick when the
 * attacker is processed first, else the next. Inferred: players are processed in index order, live shuffles PIDs.
 *
 * A target with auto retaliate and nothing else to do fights back from the tick after the hit, with their attack delay
 * set to half their weapon's speed rounded up, as against an NPC (OSRS Wiki, Auto Retaliate), if the rules let them.
 * Not here yet: special attacks (the bar isn't spent on a player), poisoned weapons, ranged and magic (W1.4), the
 * single-way rules (W1.5), skulls (W1.6) and the PvP death (W1.7).
 */
class PvpMelee(
    private val collision: CollisionMap,
    private val movement: Movement,
    private val combat: Combat,
    private val melee: MeleeCombat,
    private val random: GameRandom,
    private val currentTick: () -> Long,
    /** Whether [Player] is still in the world. */
    private val inWorld: (Player) -> Boolean,
    private val message: (Player, String) -> Unit = Player::message,
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    /** Whether the player's protection prayers block an attack of this type; see [Prayers.protects]. */
    private val protects: (Player, AttackType) -> Boolean = { _, _ -> false },
) {
    /** A swing on its way to [target] on [level], landing when they are next processed from [landTick]. */
    private class Hit(val attacker: Player, val target: Player, val damage: Int, val landTick: Long, val level: Int)

    private val hits = ArrayList<Hit>()

    /** Hits rolled and not landed yet, for tests. */
    val hitsLanding: Int get() = hits.size

    /** Whether a hit is on its way to [player]: they are in combat as if hit already, so can't log out from under it. */
    fun incoming(player: Player): Boolean = hits.any { it.target === player }

    /**
     * [attacker] used Attack on [target]. Refused with the rules' lines ([PvpRules.refusal]), returned so the caller can
     * reset the client's flag; otherwise the fight starts, its first swing in the attacker's own part of the movement
     * phase, so processing order counts for it as for every other (a click comes in the input phase, before anyone's).
     */
    fun attack(attacker: Player, target: Player): List<Line> {
        val refusal = PvpRules.refusal(attacker, target)
        if (refusal.isNotEmpty()) return refusal
        if (!canFight(attacker)) return emptyList()
        attacker.interaction = PvpInteraction(target, this)
        return emptyList()
    }

    internal fun tryInteract(player: Player, fight: PvpInteraction): Boolean {
        val target = fight.target
        if (!inWorld(target) || target.dead || target.hitpoints <= 0 || target.level != player.level) return true
        val refusal = PvpRules.refusal(player, target)
        if (refusal.isNotEmpty()) {
            for (line in refusal) message(player, words(player)[line])
            return true
        }
        // The style can change on the way (the combat tab is open while walking).
        if (!canFight(player)) return true
        if (!Reach.reachedEntity(collision, player.level, player.x, player.z, 1, target.x, target.z, 1, 1)) {
            fight.inReach = false
            val at = Tile(target.x, target.z)
            if (fight.routedTo != at) {
                fight.routedTo = at
                movement.request(player, RouteTarget.entity(target.x, target.z, 1), player.invertRun)
            }
            return false
        }
        fight.inReach = true
        player.waypoints.clear()
        val tick = currentTick()
        if (tick >= player.nextAttackTick) swing(player, target, tick)
        return false
    }

    /**
     * Whether [player]'s style can fight a player; told why not otherwise: Dinh's bulwark's Block can't attack at all,
     * as against an NPC, and only melee is in yet.
     */
    private fun canFight(player: Player): Boolean {
        if (combat.bulwarkBlock(player)) {
            message(player, words(player)[CombatLines.BULWARK_BLOCK])
            return false
        }
        if (combat.meleeType(combat.style(player)) == null) {
            message(player, MeleeCombat.MELEE_ONLY)
            return false
        }
        return true
    }

    /** What [target] has left after the hits on their way to them. */
    private fun left(target: Player): Int = (target.hitpoints - hits.sumOf { if (it.target === target) it.damage else 0 }).coerceAtLeast(0)

    private fun swing(player: Player, target: Player, tick: Long) {
        val matchup = combat.pvpMatchup(player, target) ?: return
        player.nextAttackTick = tick + combat.attackSpeed(player)
        player.effects.anim(combat.anims(player).attack(matchup.style), 0)
        val rolled = if (CombatMath.rollHits(random, matchup.attackRoll, matchup.defenceRoll) || player.sureHits) {
            CombatMath.rollDamage(random, matchup.maxHit).let { if (player.sureMaxHits) matchup.maxHit else it }
        } else {
            0
        }
        val protected = if (protects(target, matchup.style.type)) prayed(rolled) else rolled
        // Capped at what the hits already on their way leave, so two swings can't both pay XP for the same hitpoints.
        val damage = minOf(protected, left(target))
        for (skill in matchup.style.xp) player.skills.addTenths(skill, CombatMath.styleXpTenths(damage, matchup.style.xp.size))
        player.skills.addTenths(Skill.HITPOINTS, CombatMath.hitpointsXpTenths(damage))
        hits += Hit(player, target, damage, tick, target.level)
    }

    /**
     * Lands the hits on [player] whose tick has come, at the start of their part of the movement phase: one a lower
     * index made this tick lands now, one a higher index made lands next tick. A hit whose target died, left or went to
     * another level since does nothing (guessed, as for an NPC that died).
     */
    fun land(player: Player) {
        if (hits.isEmpty()) return
        val tick = currentTick()
        val iterator = hits.iterator()
        while (iterator.hasNext()) {
            val hit = iterator.next()
            if (hit.target !== player || tick < hit.landTick) continue
            iterator.remove()
            if (!inWorld(player) || player.dead || player.level != hit.level) continue
            melee.hitPlayerBy(player, hit.attacker, hit.damage, tick)
            if (player.hitpoints > 0 && !player.dead) retaliate(player, hit.attacker, tick)
        }
    }

    /** The hits on their way to or from [player] are dropped: they left the world. */
    fun forget(player: Player) {
        hits.removeAll { it.attacker === player || it.target === player }
    }

    /**
     * Auto retaliate: [player] turns on [attacker], who hit them on [tick], if they have it on, nothing else to do, no
     * modal open, aren't stunned, fight with melee and the rules let them (guessed: silently otherwise).
     */
    private fun retaliate(player: Player, attacker: Player, tick: Long) {
        if (!player.autoRetaliate || player.interaction != null || player.modalOpen || tick < player.stunnedUntil) return
        if (!inWorld(attacker) || attacker.dead || PvpRules.refusal(player, attacker).isNotEmpty()) return
        if (combat.bulwarkBlock(player) || combat.meleeType(combat.style(player)) == null) return
        player.interaction = PvpInteraction(attacker, this)
        player.nextAttackTick = maxOf(tick + (combat.attackSpeed(player) + 1) / 2, player.attackLockedUntil)
    }

    companion object {
        /** What gets through a protection prayer against a player, out of 100 (OSRS Wiki: 40% is resisted). */
        const val PROTECTED_PERCENT = 60

        /** [damage] through the target's protection prayer: 60% of it, rounded down (guessed rounding). */
        fun prayed(damage: Int): Int = damage * PROTECTED_PERCENT / 100
    }
}
