package osrsps.game.combat

import osrsps.game.GameRandom
import osrsps.game.Interaction
import osrsps.game.Movement
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.map.CollisionMap
import osrsps.map.LineOfSight
import osrsps.map.Reach
import osrsps.map.RouteTarget
import osrsps.map.Tile
import kotlin.math.abs

/**
 * A player fighting [target], another player: they walk until the target is in reach, then attack with their style, or
 * cast [spell] once if one was picked from the spellbook. See [PvpCombat].
 */
class PvpInteraction internal constructor(val target: Player, spell: CombatSpell?, private val owner: PvpCombat) : Interaction {
    /** The spell picked in the spellbook, cast once; null for the player's own style (autocast for magic). */
    internal var spell: CombatSpell? = spell

    /** Where the target stood when the player's route to them was last made. */
    internal var routedTo: Tile? = null

    /** Whether the player stood in reach of the target when last tried. */
    internal var inReach = false

    override fun tryInteract(player: Player) = owner.tryInteract(player, this)

    override fun holding(player: Player) = inReach
}

/**
 * Fighting between players in the Wilderness (steps W1.3 and W1.4). Attack on a player the rules allow ([PvpRules]), or
 * a combat spell cast on them, becomes a [PvpInteraction]: each movement phase the player walks until the target is in
 * reach and attacks every weapon-speed ticks, as at an NPC. Melee reaches the tiles beside the target ([MeleeCombat]);
 * a bow, crossbow, thrown weapon or blowpipe its range in line of sight, using its ammunition as at an NPC
 * ([RangedCombat.spend]); a spell 10 tiles in line of sight, with its runes ([MagicCombat.castAt]). The rules are asked
 * again before every attack, so a target who steps out of the Wilderness or out of range is let go.
 *
 * Each attack rolls the attacker's attack roll against the target's own defence roll for it ([Combat.pvpMatchup],
 * [Combat.pvpRangedMatchup], [Combat.pvpMagicMatchup]), then damage, as against an NPC (OSRS Wiki, Combat). A
 * protection prayer against the attack's type takes 40% off (OSRS Wiki, Protect from Melee, Protect from Missiles,
 * Protect from Magic: "40% of melee damage by other players", and the same for the others); guessed: rounded down,
 * and asked as the attack is rolled. XP is given as the attack is made, for the damage it can still deal, as against an
 * NPC; a spell that misses splashes, giving its base XP only.
 *
 * The hit lands after the attack's own delay ([HitDelay]: none for melee, the distance's for ranged and magic), and in
 * PvP the processing order (PID) decides whether it waits one tick more (OSRS Wiki, Hit delay). Here a hit lands when
 * the target is next processed in the movement phase ([land]), one tick later if the target was already processed this
 * tick when it was made. Inferred: players are processed in index order, while live shuffles PIDs.
 *
 * An ice spell or a bind that hits freezes the target for its ticks unless they are frozen or immune; a freeze breaks
 * once the two are more than 10 tiles apart, and 5 ticks of immunity follow it (OSRS Wiki, Freeze). An ice spell tells
 * the target "You have been frozen!" (the Wiki's words, held with the other keys); a bind says nothing (inferred from
 * the Wiki listing only the ice spells' message).
 *
 * A target with auto retaliate and nothing else to do fights back from the tick after the hit or splash, with their
 * attack delay set to half their weapon's speed rounded up, as against an NPC (OSRS Wiki, Auto Retaliate), if the
 * rules let them. Not here yet: special attacks and enchanted bolts' effects on players, poison and venom from a
 * player's weapon, Burst and Barrage spells' other targets and chinchompas' blast (W1.5, with multi-way combat), the
 * single-way rules (W1.5), skulls (W1.6) and the PvP death (W1.7). Powered staves can't attack players (OSRS Wiki,
 * Trident of the seas); the refusal's words are not sourced.
 */
class PvpCombat(
    private val collision: CollisionMap,
    private val movement: Movement,
    private val combat: Combat,
    private val melee: MeleeCombat,
    private val ranged: RangedCombat,
    private val magic: MagicCombat,
    private val random: GameRandom,
    private val currentTick: () -> Long,
    /** Whether [Player] is still in the world. */
    private val inWorld: (Player) -> Boolean,
    private val message: (Player, String) -> Unit = Player::message,
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    /** Whether the player's protection prayers block an attack of this type; see [Prayers.protects]. */
    private val protects: (Player, AttackType) -> Boolean = { _, _ -> false },
) {
    /**
     * An attack on its way to [target], landing when they are next processed from [landTick]: [damage] of [type], or a
     * splash of [spell] if not [hit]. [landed] runs as it lands, whatever became of the target (a shot's ammunition).
     * It does nothing more if they are no longer on [level].
     */
    private class Hit(
        val attacker: Player,
        val target: Player,
        val damage: Int,
        val landTick: Long,
        val spell: CombatSpell? = null,
        val hit: Boolean = true,
        val landed: () -> Unit = {},
        val level: Int = target.level,
    )

    private val hits = ArrayList<Hit>()

    /** Hits made and not landed yet, for tests. */
    val hitsLanding: Int get() = hits.size

    /** Whether a hit is on its way to [player]: they are in combat as if hit already, so can't log out from under it. */
    fun incoming(player: Player): Boolean = hits.any { it.target === player }

    /**
     * [attacker] used Attack on [target]. Refused with the rules' lines ([PvpRules.refusal]), returned so the caller can
     * reset the client's flag; otherwise the fight starts, its first attack in the attacker's own part of the movement
     * phase, so processing order counts for it as for every other (a click comes in the input phase, before anyone's).
     */
    fun attack(attacker: Player, target: Player): List<Line> = start(attacker, target, null)

    /** [attacker] cast the spell at the magic tab's [component] on [target]; refused as [attack] is. */
    fun cast(attacker: Player, target: Player, component: Int): List<Line> {
        val spell = magic.bookSpell(attacker, component) ?: return emptyList()
        return start(attacker, target, spell)
    }

    private fun start(attacker: Player, target: Player, spell: CombatSpell?): List<Line> {
        val refusal = PvpRules.refusal(attacker, target)
        if (refusal.isNotEmpty()) return refusal
        if (mode(attacker, spell) == null) {
            cantFight(attacker)
            return emptyList()
        }
        attacker.interaction = PvpInteraction(target, spell, this)
        return emptyList()
    }

    /** How a player fights another for now: with [spell], with ranged [weapon], or with melee if both are null. */
    private class Mode(val spell: CombatSpell?, val weapon: RangedWeapon?)

    /** [player]'s way of fighting with [picked] (a spellbook spell, or null), or null when their style can't fight a player. */
    private fun mode(player: Player, picked: CombatSpell?): Mode? {
        // Dinh's bulwark's Block can't attack at all, a spell from the book included, as against an NPC.
        if (combat.bulwarkBlock(player)) return null
        if (picked != null) return Mode(picked, null)
        val style = combat.style(player)
        if (style.type == AttackType.MAGIC) return magic.pvpFightSpell(player)?.let { Mode(it, null) }
        if (style.type == AttackType.RANGED) return ranged.pvpWeapon(player)?.let { Mode(null, it) }
        return if (combat.meleeType(style) != null) Mode(null, null) else null
    }

    /** Tells [player] they can't fight with their style: a powered staff can't attack players, nor what isn't in yet. */
    private fun cantFight(player: Player) {
        if (combat.bulwarkBlock(player)) message(player, words(player)[CombatLines.BULWARK_BLOCK])
        else if (combat.style(player).type == AttackType.MAGIC) message(player, words(player)[PvpLines.POWERED_STAFF])
        else message(player, MeleeCombat.MELEE_ONLY)
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
        val mode = mode(player, fight.spell) ?: run {
            cantFight(player)
            return true
        }
        val reached = when {
            mode.spell != null -> LineOfSight.inRange(collision, player.level, player.x, player.z, 1, target.x, target.z, 1, 1, magic.reach(player, mode.spell))
            mode.weapon != null -> LineOfSight.inRange(collision, player.level, player.x, player.z, 1, target.x, target.z, 1, 1, ranged.reach(player, mode.weapon))
            else -> Reach.reachedEntity(collision, player.level, player.x, player.z, 1, target.x, target.z, 1, 1)
        }
        if (!reached) {
            fight.inReach = false
            val at = Tile(target.x, target.z)
            if (fight.routedTo != at) {
                fight.routedTo = at
                movement.request(player, RouteTarget.entity(target.x, target.z, 1), player.invertRun)
            }
            return false
        }
        fight.inReach = true
        fight.routedTo = null
        player.waypoints.clear()
        val tick = currentTick()
        if (tick < player.nextAttackTick) return false
        return when {
            mode.spell != null -> castSpell(player, target, fight, mode.spell, tick)
            mode.weapon != null -> shoot(player, target, mode.weapon, tick)
            else -> {
                swing(player, target, tick)
                false
            }
        }
    }

    /** One more tick on a hit [target] would take if they were processed already this tick; see the class note. */
    private fun late(target: Player, tick: Long): Int = if (target.pvpTick == tick) 1 else 0

    /** What [target] has left after the hits on their way to them. */
    private fun left(target: Player): Int = (target.hitpoints - hits.sumOf { if (it.target === target) it.damage else 0 }).coerceAtLeast(0)

    /** [rolled] of [type] through [target]'s protection prayer and capped at what they have [left]. */
    private fun dealt(target: Player, type: AttackType, rolled: Int): Int =
        minOf(if (protects(target, type)) prayed(rolled) else rolled, left(target))

    /** Accuracy then damage, as against an NPC: 0 for a miss; `::sure` makes it hit, `::sure max` deal [maxHit]. */
    private fun roll(player: Player, attackRoll: Int, defenceRoll: Int, maxHit: Int): Int? {
        if (!CombatMath.rollHits(random, attackRoll, defenceRoll) && !player.sureHits) return null
        return CombatMath.rollDamage(random, maxHit).let { if (player.sureMaxHits) maxHit else it }
    }

    private fun styleXp(player: Player, style: AttackStyle, damage: Int) {
        for (skill in style.xp) player.skills.addTenths(skill, CombatMath.styleXpTenths(damage, style.xp.size))
        player.skills.addTenths(Skill.HITPOINTS, CombatMath.hitpointsXpTenths(damage))
    }

    private fun swing(player: Player, target: Player, tick: Long) {
        val matchup = combat.pvpMatchup(player, target) ?: return
        player.nextAttackTick = tick + combat.attackSpeed(player)
        player.effects.anim(combat.anims(player).attack(matchup.style), 0)
        val damage = dealt(target, matchup.style.type, roll(player, matchup.attackRoll, matchup.defenceRoll, matchup.maxHit) ?: 0)
        styleXp(player, matchup.style, damage)
        hits += Hit(player, target, damage, tick + late(target, tick))
    }

    /** Fires [weapon] at [target]; returns true when the fight ends (nothing left to fire, and the player told). */
    private fun shoot(player: Player, target: Player, weapon: RangedWeapon, tick: Long): Boolean {
        // The numbers first: they count the ammunition worn, and the last piece is about to go.
        val matchup = combat.pvpRangedMatchup(player, target) ?: return true
        val spent = ranged.spend(player, weapon, matchup.style) ?: return true
        val travel = ranged.launch(player, weapon, spent, tick)
        val distance = LineOfSight.distance(player.x, player.z, 1, target.x, target.z, 1, 1)
        val delay = weapon.delay.ticks(distance) + late(target, tick)
        repeat(spent.shots) { shot ->
            ranged.flyAt(player, target, travel, delay.toLong(), shot)
            val damage = dealt(target, AttackType.RANGED, roll(player, matchup.attackRoll, matchup.defenceRoll, matchup.maxHit) ?: 0)
            styleXp(player, matchup.style, damage)
            hits += Hit(player, target, damage, tick + delay, landed = { ranged.landAmmo(player, spent, target.x, target.z, target.level) })
        }
        // The last one thrown: the fight ends rather than going on bare-handed (guessed, as at an NPC).
        return weapon.thrown && player.equipment[WearPos.WEAPON] == null
    }

    /**
     * Casts [spell] at [target]; returns true when the fight ends: the player couldn't cast it (and was told), or it was
     * a spellbook spell and they don't autocast, so one cast is all.
     */
    private fun castSpell(player: Player, target: Player, fight: PvpInteraction, spell: CombatSpell, tick: Long): Boolean {
        val fighting = fight.spell == null
        val speed = if (fighting) combat.attackSpeed(player) else MagicCombat.SPEED + combat.wearyDelay(player)
        val late = late(target, tick)
        val delay = magic.castAt(player, target, spell, tick, speed, late) ?: return true
        val matchup = combat.pvpMagicMatchup(player, target, spell)
        val rolled = roll(player, matchup.attackRoll, matchup.defenceRoll, matchup.maxHit)
        val damage = dealt(target, AttackType.MAGIC, rolled ?: 0)
        player.skills.addTenths(Skill.MAGIC, if (rolled != null) spell.xpTenths else spell.splashXpTenths)
        if (fighting && Autocast.defensive(player)) {
            player.skills.addTenths(Skill.MAGIC, CombatMath.defensiveSpellMagicXpTenths(damage))
            player.skills.addTenths(Skill.DEFENCE, CombatMath.defensiveSpellDefenceXpTenths(damage))
        } else {
            player.skills.addTenths(Skill.MAGIC, CombatMath.spellDamageXpTenths(damage))
        }
        player.skills.addTenths(Skill.HITPOINTS, CombatMath.hitpointsXpTenths(damage))
        hits += Hit(player, target, damage, tick + delay + late, spell, hit = rolled != null)
        // A spell from the book is cast once; the autocast spell, if any, carries the fight on.
        fight.spell = null
        return magic.pvpFightSpell(player) == null
    }

    /**
     * Lands the hits on [player] whose tick has come, at the start of their part of the movement phase; see the class
     * note. A hit whose target died or went to another level since does nothing but what [Hit.landed] does (guessed, as
     * for an NPC that died).
     */
    fun land(player: Player) {
        val tick = currentTick()
        player.pvpTick = tick
        if (hits.isEmpty()) return
        val landing = hits.filter { it.target === player && tick >= it.landTick }
        if (landing.isEmpty()) return
        hits.removeAll(landing.toSet())
        for (hit in landing) {
            hit.landed()
            if (!inWorld(player) || player.dead || player.hitpoints <= 0 || player.level != hit.level) continue
            val spell = hit.spell
            if (spell != null) magic.landedOn(player, spell, hit.hit)
            if (spell == null || (hit.hit && spell.maxHit > 0)) {
                melee.hitPlayerBy(player, hit.attacker, hit.damage, tick)
            } else {
                // A splash, or a bind that can't do damage: no hitsplat, but they were attacked (guessed, as for an NPC).
                player.attackedByPlayer = hit.attacker
                player.attackedByPlayerTick = tick
            }
            if (player.dead || player.hitpoints <= 0) continue
            if (spell != null && hit.hit && spell.freezeTicks > 0) freeze(player, hit.attacker, spell)
            retaliate(player, hit.attacker, tick)
        }
    }

    /** [player] is frozen by [caster]'s [spell] for its ticks, unless frozen or immune already; see the class note. */
    private fun freeze(player: Player, caster: Player, spell: CombatSpell) {
        if (player.freezeTicks > 0 || player.freezeImmunityTicks > 0) return
        player.freezeTicks = spell.freezeTicks
        player.frozenBy = caster
        player.waypoints.clear()
        if (!spell.holds) message(player, words(player)[PvpLines.FROZEN])
    }

    /**
     * At the start of the tick: [player]'s freeze counts down, breaking once they are more than [FREEZE_BREAK_DISTANCE]
     * tiles from who cast it on either axis (the Wiki: only x and y count, not the plane); then the immunity counts
     * down. Guessed: a caster who left the world keeps the freeze on to its end.
     */
    fun countDown(player: Player) {
        if (player.freezeTicks > 0) {
            val caster = player.frozenBy
            val apart = caster != null && inWorld(caster) &&
                maxOf(abs(caster.x - player.x), abs(caster.z - player.z)) > FREEZE_BREAK_DISTANCE
            player.freezeTicks = if (apart) 0 else player.freezeTicks - 1
            if (player.freezeTicks == 0) {
                player.frozenBy = null
                player.freezeImmunityTicks = FREEZE_IMMUNITY_TICKS
            }
        } else if (player.freezeImmunityTicks > 0) {
            player.freezeImmunityTicks--
        }
    }

    /** The hits on their way to or from [player] are dropped, and freezes they cast forgotten: they left the world. */
    fun forget(player: Player) {
        hits.removeAll { it.attacker === player || it.target === player }
    }

    /**
     * Auto retaliate: [player] turns on [attacker], who hit them on [tick], if they have it on, nothing else to do, no
     * modal open, aren't stunned, can fight with their style and the rules let them (guessed: silently otherwise).
     */
    private fun retaliate(player: Player, attacker: Player, tick: Long) {
        if (!player.autoRetaliate || player.interaction != null || player.modalOpen || tick < player.stunnedUntil) return
        if (!inWorld(attacker) || attacker.dead || PvpRules.refusal(player, attacker).isNotEmpty()) return
        mode(player, null) ?: return
        player.interaction = PvpInteraction(attacker, null, this)
        player.nextAttackTick = maxOf(tick + (combat.attackSpeed(player) + 1) / 2, player.attackLockedUntil)
    }

    companion object {
        /** What gets through a protection prayer against a player, out of 100 (OSRS Wiki: 40% is resisted). */
        const val PROTECTED_PERCENT = 60

        /** A freeze breaks once caster and target are further apart than this, the 10-tile weapon range (OSRS Wiki, Freeze). */
        const val FREEZE_BREAK_DISTANCE = 10

        /** Ticks of immunity after a freeze (OSRS Wiki, Freeze: 5 ticks for all but the Grasp spells). */
        const val FREEZE_IMMUNITY_TICKS = 5

        /** [damage] through the target's protection prayer: 60% of it, rounded down (guessed rounding). */
        fun prayed(damage: Int): Int = damage * PROTECTED_PERCENT / 100
    }
}
