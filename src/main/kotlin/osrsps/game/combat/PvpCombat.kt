package osrsps.game.combat

import osrsps.game.FountainOfRune
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
 * Fighting between players in the Wilderness (steps W1.3 to W1.7). Attack on a player the rules allow ([PvpRules]), or
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
 * tick when it was made. Inferred: players are processed in index order, while live shuffles PIDs. A hit already on
 * its way lands even if the target has stepped out of the Wilderness since (a freeze with it), as an attack's XP was
 * given when it was made; only a target who changed plane or left the world escapes it (inferred, as live resolves an
 * attack when it is made; exploit review R35A-1).
 *
 * An ice spell or a bind that hits freezes the target for its ticks unless they are frozen or immune; a freeze breaks
 * once the two are more than 10 tiles apart, and 5 ticks of immunity follow it (OSRS Wiki, Freeze). An ice spell tells
 * the target "You have been frozen!" (the Wiki's words, held with the other keys); a bind says nothing (inferred from
 * the Wiki listing only the ice spells' message).
 *
 * A target with auto retaliate and nothing else to do fights back from the tick after the hit or splash, with their
 * attack delay set to half their weapon's speed rounded up, as against an NPC (OSRS Wiki, Auto Retaliate), if the
 * rules let them.
 *
 * Single-way combat (step W1.5) keeps a third player off a fight with the PJ timer ([singleWayRefusal]); in multi-way
 * combat ([MultiCombat]) anyone can join, and a Burst or Barrage hits the players around its target too ([around]).
 * Each attack skulls an attacker who wasn't attacked first ([Skulls], step W1.6). A player killed soon after a
 * player's hit dies a PvP death ([killerOf], step W1.7): [Deaths] drops what they lose for the killer. Tele Block that
 * lands stops the target's teleports ([Teleblock], step W1.8).
 *
 * Not here yet: special attacks and enchanted bolts' effects on players, poison and venom from a player's weapon, and
 * chinchompas' blast on players. Powered staves can't attack players (OSRS Wiki,
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
    /** Where the single-way rules don't hold. */
    private val multi: MultiCombat = MultiCombat(),
    /** Everyone in the world, for a Burst or Barrage's other targets. */
    private val players: () -> Sequence<Player> = { emptySequence() },
    /** Where combat is singles-plus ([osrsps.game.content.SinglesPlus]): a monster's fight bars no player's attack. */
    private val singlesPlus: (level: Int, x: Int, z: Int) -> Boolean = { _, _, _ -> false },
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
    ) {
        /** A phoenix necklace's heal before it landed ([dropIncoming]): it lands nowhere, [landed] aside. */
        var cancelled = false
    }

    private val hits = ArrayList<Hit>()

    /** The hits [land] is landing now, so [dropIncoming] reaches those not landed yet. */
    private var landingNow: List<Hit> = emptyList()

    /**
     * A phoenix necklace healed [player]: the attacks on their way to them land nowhere (OSRS Wiki, Phoenix necklace),
     * those queued and those left in the batch landing now (Codex review on #956).
     */
    fun dropIncoming(player: Player) {
        for (hit in hits) if (hit.target === player) hit.cancelled = true
        for (hit in landingNow) if (hit.target === player) hit.cancelled = true
    }

    /** Hits made and not landed yet, for tests. */
    val hitsLanding: Int get() = hits.size

    /** Whether a hit is on its way to [player]: they are in combat as if hit already, so can't log out from under it. */
    fun incoming(player: Player): Boolean = hits.any { it.target === player && !it.cancelled }

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
        singleWayRefusal(attacker, target, currentTick())?.let { return listOf(it) }
        if (Skulls.prevented(attacker, target, currentTick())) return listOf(PvpLines.SKULL_PREVENTED)
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
        // Someone else may have got to either of them on the way.
        singleWayRefusal(player, target, currentTick())?.let {
            message(player, words(player)[it])
            return true
        }
        // A skull can run out on the way.
        if (Skulls.prevented(player, target, currentTick())) {
            message(player, words(player)[PvpLines.SKULL_PREVENTED])
            return true
        }
        // A weapon for rats alone (Codex, PR 878), asked each attempt as the weapon can change mid-fight.
        combat.weaponBarredOnPlayer(player)?.let {
            message(player, words(player)[it])
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

    /**
     * Why [attacker] can't attack [target] in single-way combat, or null when they can (OSRS Wiki, PJ timer: both
     * players of a PvP fight start a timer of [PJ_TICKS], refreshed with each attack, and while it runs no one else can
     * attack either of them, nor can they attack anyone else). Refused too: a target a monster is fighting (hit them
     * within [MeleeCombat.IN_COMBAT_TICKS], the Wiki's monster timer) or that is fighting one (attacked it within as
     * long, so before it hits back too: Codex review on #710), and an attacker one is fighting or who is fighting one
     * (guessed for the second: the same timer both ways), as for an NPC
     * ("I'm already under attack."). By the attacker's tile for their own timer and the target's for theirs, as
     * [MeleeCombat] does for NPCs (guessed). The target's refusal's words are held.
     */
    fun singleWayRefusal(attacker: Player, target: Player, tick: Long): Line? {
        if (!multi.at(attacker.level, attacker.x, attacker.z) &&
            (fightingOther(attacker, target, tick) || (!lockedOn(attacker, target, tick) && fightingAnyNpc(attacker, tick)))
        ) {
            return CombatLines.ALREADY_UNDER_ATTACK
        }
        if (!multi.at(target.level, target.x, target.z) &&
            (fightingOther(target, attacker, tick) || (!lockedOn(target, attacker, tick) && fightingAnyNpc(target, tick)))
        ) {
            return PvpLines.TARGET_IN_COMBAT
        }
        return null
    }

    /**
     * Whether [player]'s PJ timer runs for a fight with someone other than [other]. An opponent who has left the world
     * is none, so a friend can't splash and log out to shield them; one who has come back is the same player by save
     * key (exploit review R35A-3). Guessed: the Wiki's PJ timer page doesn't say what an opponent's logout does; this
     * is our rule against the shield. The timer after a kill holds though the victim logs out.
     */
    private fun fightingOther(player: Player, other: Player, tick: Long): Boolean {
        val opponent = player.pjOpponent ?: return false
        return tick < player.pjUntil && (player.pjAfterKill || inWorld(opponent)) && opponent.saveKey != other.saveKey
    }

    /**
     * Whether [player]'s PJ timer runs for their fight with [other]: a monster they hit since doesn't keep [other] off
     * them, or one of the two could end the fight by hitting any monster nearby (exploit review R35A-2). Guessed: no
     * source covers it; this is our rule against ending a fight that way.
     */
    private fun lockedOn(player: Player, other: Player, tick: Long): Boolean {
        val opponent = player.pjOpponent ?: return false
        return tick < player.pjUntil && opponent.saveKey == other.saveKey
    }

    /** Whether a monster is fighting [player], or they are fighting one, outside singles-plus combat ([npcBars]). */
    private fun fightingAnyNpc(player: Player, tick: Long): Boolean =
        npcBars(player, tick) || (!singlesPlus(player.level, player.x, player.z) && melee.fightingNpc(player))

    /**
     * Whether a monster's fight with [player] bars a PvP attack, theirs or on them: not in singles-plus combat, where
     * "another player attacking will take priority over fighting the monster" (OSRS Wiki, Singles-plus combat).
     */
    private fun npcBars(player: Player, tick: Long): Boolean = !singlesPlus(player.level, player.x, player.z) && npcFighting(player, tick)

    /** Whether a monster fighting [player] hit them within the monster PJ timer. */
    private fun npcFighting(player: Player, tick: Long): Boolean {
        val npc = player.attackedBy ?: return false
        return npc.target === player && !npc.dead && tick - player.attackedTick <= MeleeCombat.IN_COMBAT_TICKS
    }

    /** [attacker] attacked [target] on [tick]: both PJ timers start again, each naming the other, and a skull if unprovoked. */
    private fun fought(attacker: Player, target: Player, tick: Long) {
        Skulls.attacked(attacker, target, tick)
        attacker.pjOpponent = target
        attacker.pjUntil = tick + PJ_TICKS
        attacker.pjAfterKill = false
        target.pjOpponent = attacker
        target.pjUntil = tick + PJ_TICKS
        target.pjAfterKill = false
        // In singles-plus combat "another player attacking will take priority over fighting the monster" (OSRS Wiki,
        // Singles-plus combat): the target's monster fight ends, theirs on it and its on them (Codex review on #1002).
        if (singlesPlus(target.level, target.x, target.z)) melee.leaveNpcFight(target)
    }

    /** One more tick on a hit [target] would take if they were processed already this tick; see the class note. */
    private fun late(target: Player, tick: Long): Int = if (target.pvpTick == tick) 1 else 0

    /** What [target] has left after the hits on their way to them. */
    private fun left(target: Player): Int = (target.hitpoints - hits.sumOf { if (it.target === target && !it.cancelled) it.damage else 0 }).coerceAtLeast(0)

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
        fought(player, target, tick)
    }

    /** Fires [weapon] at [target]; returns true when the fight ends (nothing left to fire, and the player told). */
    private fun shoot(player: Player, target: Player, weapon: RangedWeapon, tick: Long): Boolean {
        // The numbers first: they count the ammunition worn, and the last piece is about to go.
        val matchup = combat.pvpRangedMatchup(player, target) ?: return true
        val spent = ranged.spend(player, weapon, matchup.style) ?: return true
        val travel = ranged.launch(player, weapon, spent, tick)
        // A blowpipe is a tick slower at a player than at a monster, on every style (OSRS Wiki, Attack speed).
        if (weapon.loaded) player.nextAttackTick += BLOWPIPE_PVP_SLOWER
        fought(player, target, tick)
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
    private fun castSpell(player: Player, target: Player, fight: PvpInteraction, spell: CombatSpell, tick: Long): Boolean =
        // At the Fountain of Rune the cast is free and trains nothing ([FountainOfRune]).
        player.skills.withoutXpIf(FountainOfRune.covers(player)) { castSpellPaid(player, target, fight, spell, tick) }

    private fun castSpellPaid(player: Player, target: Player, fight: PvpInteraction, spell: CombatSpell, tick: Long): Boolean {
        val fighting = fight.spell == null
        val speed = if (fighting) combat.attackSpeed(player) else MagicCombat.SPEED + combat.wearyDelay(player)
        val late = late(target, tick)
        val delay = magic.castAt(player, target, spell, tick, speed, late) ?: return true
        fought(player, target, tick)
        val defensive = fighting && Autocast.defensive(player)
        val hit = spellOn(player, target, spell, tick + delay + late, defensive)
        player.skills.addTenths(Skill.MAGIC, if (hit) spell.xpTenths else spell.splashXpTenths)
        if (spell.area && multi.at(target.level, target.x, target.z)) {
            for (other in around(player, target, tick)) {
                spellOn(player, other, spell, tick + delay + late(other, tick), defensive)
                // Each one hit is attacked: their PJ timer starts as the target's does, naming the caster, whose own
                // stays on the target (guessed: the Wiki doesn't say).
                other.pjOpponent = player
                other.pjUntil = tick + PJ_TICKS
                other.pjAfterKill = false
                // And unprovoked, a skull for the caster and the right to hit back for them, as for the target.
                Skulls.attacked(player, other, tick)
            }
        }
        // A spell from the book is cast once; the autocast spell, if any, carries the fight on. With none, the fight
        // ends here, and the player keeps facing the target ([Player.castAtPlayer]).
        fight.spell = null
        val ends = magic.pvpFightSpell(player) == null
        if (ends) player.castAtPlayer = target
        return ends
    }

    /**
     * Rolls [spell] on [target] and sends it on its way to land on [landTick], with the XP for its damage (the base XP
     * is the caller's); whether it hits.
     */
    private fun spellOn(player: Player, target: Player, spell: CombatSpell, landTick: Long, defensive: Boolean): Boolean {
        val matchup = combat.pvpMagicMatchup(player, target, spell)
        val rolled = roll(player, matchup.attackRoll, matchup.defenceRoll, matchup.maxHit)
        val damage = dealt(target, AttackType.MAGIC, rolled ?: 0)
        if (defensive) {
            player.skills.addTenths(Skill.MAGIC, CombatMath.defensiveSpellMagicXpTenths(damage))
            player.skills.addTenths(Skill.DEFENCE, CombatMath.defensiveSpellDefenceXpTenths(damage))
        } else {
            player.skills.addTenths(Skill.MAGIC, CombatMath.spellDamageXpTenths(damage))
        }
        player.skills.addTenths(Skill.HITPOINTS, CombatMath.hitpointsXpTenths(damage))
        hits += Hit(player, target, damage, landTick, spell, hit = rolled != null)
        return rolled != null
    }

    /**
     * The other players a Burst or Barrage on [target] hits in multi-way combat: those standing on the 3x3 around the
     * target, in multi-way combat themselves, whom [player] could attack ([PvpRules], [Skulls.prevented], and [singleWayRefusal]: a caster
     * standing in single-way combat is held to their target), eight at most, lowest index first (OSRS Wiki, Ancient
     * Magicks: up to nine targets in the 3x3; the order guessed, as for NPCs).
     */
    private fun around(player: Player, target: Player, tick: Long): List<Player> =
        players()
            .filter {
                it !== player && it !== target && inWorld(it) && !it.dead && it.hitpoints > 0 && it.level == target.level &&
                    abs(it.x - target.x) <= 1 && abs(it.z - target.z) <= 1 && multi.at(it.level, it.x, it.z) &&
                    PvpRules.refusal(player, it).isEmpty() && singleWayRefusal(player, it, tick) == null &&
                    !Skulls.prevented(player, it, tick)
            }
            .sortedBy { it.index }
            .take(MagicCombat.AREA_TARGETS - 1)
            .toList()

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
        landingNow = landing
        try {
            landBatch(player, landing, tick)
        } finally {
            landingNow = emptyList()
        }
    }

    private fun landBatch(player: Player, landing: List<Hit>, tick: Long) {
        for (hit in landing) {
            hit.landed()
            if (hit.cancelled) continue
            if (!inWorld(player) || player.dead || player.hitpoints <= 0 || player.level != hit.level) continue
            val spell = hit.spell
            if (spell != null) magic.landedOn(player, spell, hit.hit)
            if (spell == null || (hit.hit && spell.maxHit > 0)) {
                // Noted before the hit, as a killing hit's death asks who killed them (Deaths.die).
                dealtBy(player, hit.attacker, minOf(hit.damage, player.hitpoints), tick)
                val (fromX, fromZ, fromLevel) = Triple(player.x, player.z, player.level)
                melee.hitPlayerBy(player, hit.attacker, hit.damage, tick)
                // A ring of life took them away: out of the fight, no hitting back or freeze (Codex review on #956).
                if (player.x != fromX || player.z != fromZ || player.level != fromLevel) continue
                // A kill starts the killer's timer (OSRS Wiki, PJ timer: 12 seconds).
                if (player.dead || player.hitpoints <= 0) {
                    hit.attacker.pjOpponent = player
                    hit.attacker.pjUntil = tick + PJ_TICKS
                    hit.attacker.pjAfterKill = true
                }
            } else {
                // A splash, or a bind that can't do damage: no hitsplat, but they were attacked (guessed, as for an NPC).
                player.attackedByPlayer = hit.attacker
                player.attackedByPlayerTick = tick
            }
            if (player.dead || player.hitpoints <= 0) continue
            if (spell != null && hit.hit && spell.freezeTicks > 0) freeze(player, hit.attacker, spell)
            if (spell != null && hit.hit && spell.teleblock) teleblock(player, hit.attacker)
            retaliate(player, hit.attacker, tick)
        }
    }

    /**
     * [attacker] hit [player] for [damage] on [tick], for [killerOf]. What others dealt earlier is forgotten once
     * [RECENT_TICKS] have gone by without a player's hit.
     */
    internal fun dealtBy(player: Player, attacker: Player, damage: Int, tick: Long) {
        if (tick - player.lastPvpHitTick > RECENT_TICKS) player.pvpDamage.clear()
        // Moved to the end on every hit, so the map runs from the longest-ago hitter to the latest (killerOf's tie).
        val dealt = player.pvpDamage.remove(attacker.saveKey) ?: 0
        player.pvpDamage[attacker.saveKey] = dealt + damage
        player.lastPvpHitTick = tick
    }

    /**
     * Who killed [player], dying now, by save key: the player who dealt them the most damage, the last to hit them on
     * a tie, if a player hit them within [RECENT_TICKS]; null when none did, for a death like any other (OSRS Wiki,
     * Items Kept on Death: a death with no recent player damage is a normal PvM death). Guessed: the most damage
     * deciding, and how recent counts.
     */
    fun killerOf(player: Player): String? {
        if (player.lastPvpHitTick < 0 || currentTick() - player.lastPvpHitTick > RECENT_TICKS || player.pvpDamage.isEmpty()) return null
        val most = player.pvpDamage.values.max()
        return player.pvpDamage.entries.last { it.value == most }.key
    }

    /**
     * [killer] (a save key) is credited with [player]'s death: their PJ timer starts, naming [player] (OSRS Wiki, PJ
     * timer: 12 seconds after a kill), whoever dealt the last hit.
     */
    fun credited(player: Player, killer: String) {
        val credited = players().firstOrNull { it.saveKey == killer } ?: return
        credited.pjOpponent = player
        credited.pjUntil = currentTick() + PJ_TICKS
        credited.pjAfterKill = true
        killedTeleblocker(credited, player)
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
     * [player] is Tele Blocked by [caster]: for [Teleblock.PROTECTED_TICKS] if they pray Protect from Magic as it lands,
     * else [Teleblock.TICKS], and told who cast it. One already blocked can't be (refused at the cast), but a second
     * cast can be on its way as the first lands: it does nothing then (guessed).
     */
    private fun teleblock(player: Player, caster: Player) {
        if (player.teleblockTicks > 0) return
        Teleblock.block(player, caster, if (protects(player, AttackType.MAGIC)) Teleblock.PROTECTED_TICKS else Teleblock.TICKS)
        message(player, words(player)[PvpLines.TELEBLOCKED, "name" to caster.username])
    }

    /**
     * [killer] is credited with [victim]'s death ([credited]): a Tele Block [victim] cast on [killer] is over (OSRS Wiki,
     * Tele Block). Guessed: the kill is the credited one, the most damage, not whoever dealt the last hit, so that the
     * block ends for whoever gets the loot.
     */
    private fun killedTeleblocker(killer: Player, victim: Player) {
        if (killer.teleblockTicks <= 0 || killer.teleblockedBy != victim.saveKey) return
        Teleblock.clear(killer)
        message(killer, words(killer)[PvpLines.TELEBLOCK_REMOVED, "name" to victim.username])
    }

    /**
     * At the start of the tick: [player]'s skull counts down ([Skulls.countDown]), their Tele Block ([Teleblock.countDown]),
     * and their freeze, breaking once they are more than [FREEZE_BREAK_DISTANCE] tiles from who cast it on either axis
     * (the Wiki: only x and y count, not the plane); then the immunity counts down. A caster who left the world breaks it at once.
     */
    fun countDown(player: Player) {
        Skulls.countDown(player, currentTick())
        Teleblock.countDown(player)
        if (player.freezeTicks > 0) {
            val caster = player.frozenBy
            // A caster who left the world is as far away as can be: the freeze breaks (OSRS Wiki, Free-to-play PvP
            // techniques: logging out unfreezes the target).
            val apart = caster != null && (
                !inWorld(caster) || maxOf(abs(caster.x - player.x), abs(caster.z - player.z)) > FREEZE_BREAK_DISTANCE
                )
            player.freezeTicks = if (apart) 0 else player.freezeTicks - 1
            if (player.freezeTicks == 0) {
                player.frozenBy = null
                player.freezeImmunityTicks = FREEZE_IMMUNITY_TICKS
            }
        } else if (player.freezeImmunityTicks > 0) {
            player.freezeImmunityTicks--
        }
    }

    /**
     * Everything PvP about [player] is forgotten, for a test that starts afresh (`::scenario`, `::reset`): the hits on
     * their way to or from them, other players' attacks on them (so an opponent still swinging or retaliating doesn't
     * carry the old fight into the new setup), their PJ timer, skull, Tele Block, whom they attacked first and the damage
     * players dealt them. Not live content.
     */
    fun clear(player: Player) {
        forget(player)
        for (other in players()) {
            if ((other.interaction as? PvpInteraction)?.target === player) other.interaction = null
        }
        player.pjOpponent = null
        player.pjUntil = 0
        player.skullTicks = 0
        player.attackedFirst.clear()
        Teleblock.clear(player)
        player.pvpDamage.clear()
        player.lastPvpHitTick = -1
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
        if (singleWayRefusal(player, attacker, tick) != null || Skulls.prevented(player, attacker, tick)) return
        val mode = mode(player, null) ?: return
        player.interaction = PvpInteraction(attacker, null, this)
        player.nextAttackTick = maxOf(tick + (interval(player, mode) + 1) / 2, player.attackLockedUntil)
    }

    /**
     * The ticks between [player]'s attacks in [mode], as their next attack would be timed: the style's speed, a tick
     * fewer on rapid with a ranged weapon and a tick more for a blowpipe at a player (OSRS Wiki, Ranged; Attack speed).
     */
    /** The ticks between [player]'s attacks with what they wear, as auto retaliate times them; null if they can't fight. */
    internal fun interval(player: Player): Int? = mode(player, null)?.let { interval(player, it) }

    private fun interval(player: Player, mode: Mode): Int {
        val weapon = mode.weapon ?: return combat.attackSpeed(player)
        val rapid = combat.style(player).stance == AttackStance.RAPID
        return combat.attackSpeed(player) - (if (rapid) 1 else 0) + (if (weapon.loaded) BLOWPIPE_PVP_SLOWER else 0)
    }

    companion object {
        /** What gets through a protection prayer against a player, out of 100 (OSRS Wiki: 40% is resisted). */
        const val PROTECTED_PERCENT = 60

        /** How long after a player's last hit a death still counts as theirs: a minute (guessed). */
        const val RECENT_TICKS = 100

        /** The PvP PJ timer: 20 ticks, 12 seconds (OSRS Wiki, PJ timer). */
        const val PJ_TICKS = 20

        /** A freeze breaks once caster and target are further apart than this, the 10-tile weapon range (OSRS Wiki, Freeze). */
        const val FREEZE_BREAK_DISTANCE = 10

        /** Ticks of immunity after a freeze (OSRS Wiki, Freeze: 5 ticks for all but the Grasp spells). */
        const val FREEZE_IMMUNITY_TICKS = 5

        /** The ticks a blowpipe adds to its attack speed against a player (OSRS Wiki, Attack speed). */
        const val BLOWPIPE_PVP_SLOWER = 1

        /** [damage] through the target's protection prayer: 60% of it, rounded down (guessed rounding). */
        fun prayed(damage: Int): Int = damage * PROTECTED_PERCENT / 100
    }
}
