package osrsps.game.combat

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.GameRandom
import osrsps.game.Interaction
import osrsps.game.Movement
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.Weapons
import osrsps.game.dialogue.DialogueText
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcEffects
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.game.zone.Projectile
import osrsps.map.CollisionMap
import osrsps.map.LineOfSight
import osrsps.map.Reach
import osrsps.map.RouteTarget
import osrsps.map.Tile
import kotlin.math.abs

private val logger = KotlinLogging.logger {}

/** A player fighting [npc] with melee: they walk up to it and swing until one of them is gone. See [MeleeCombat]. */
class MeleeInteraction internal constructor(val npc: Npc, private val owner: MeleeCombat) : Interaction {
    /** Where the NPC stood when the player's route to it was last made. */
    internal var routedTo: Tile? = null

    /** Whether the player stood in reach of the NPC when last tried. */
    internal var inReach = false

    /** Whether the player has swung at the NPC yet; until then the fight can still be refused on arrival. */
    internal var swung = false

    override fun tryInteract(player: Player) = owner.tryInteract(player, this)

    override fun unreachable(player: Player) = owner.unreachable(player)

    override fun holding(player: Player) = inReach
}

/**
 * Melee between players and NPCs. An Attack option becomes a [MeleeInteraction]: each movement phase the player
 * chases the NPC as for any NPC option (see [osrsps.game.npc.NpcInteractions]) and, once beside it, swings every
 * weapon-speed ticks. A swing rolls accuracy then damage ([CombatMath]), shows the hitsplat and health bar to everyone,
 * and gives XP by the attack style (OSRS Wiki, Combat).
 *
 * An NPC that is hit fights back ([process], in the NPC phase): it chases its attacker, keeping within its max range
 * of its spawn ([Npcs.MAX_RANGE]), and swings every attack-speed ticks once beside them; it gives up when they are
 * gone, dead, on another level, further than [CHASE_RANGE] from its spawn, or fought by another NPC, and walks home
 * when they have stood outside its aggression range for a while ([leftBehind]). A player hit
 * with auto retaliate on fights back too (OSRS Wiki, Auto Retaliate: one tick after the hit, with their attack delay
 * set to half their weapon's speed, rounded up). Lumbridge is single-way: an NPC another player hit recently says
 * "Someone else is fighting that.", and a player another NPC is hitting can't attack a second one ("I'm already under
 * attack."). Hitpoints come back one a minute (Wiki, Hitpoints) for players and NPCs alike.
 *
 * From the Wiki (Hit delay): melee has no delay of its own, but NPCs are processed before players, so a player's swing
 * on an NPC lands in the next tick's NPC phase ([HitDelay.ON_NPC]); the XP comes with the swing, for the damage it can
 * still deal. An NPC's swing on a player lands on its own tick. An NPC with no hitpoints left dies and comes back at
 * its spawn after its respawn time. Guessed and marked below: how long an NPC counts as fighting after its last hit, how
 * long its body stays, that the respawn time counts from the killing blow, that an NPC strikes back from the tick
 * after it is hit, that the Wiki's retreat ranges are also the chase ranges, and which animation wins when a swing and
 * a block land on one tick (the swing). A player with no hitpoints left dies: every fight with them ends here and
 * [Deaths] takes it from there.
 */
class MeleeCombat(
    private val npcs: Npcs,
    private val collision: CollisionMap,
    private val movement: Movement,
    private val combat: Combat,
    private val random: GameRandom,
    private val currentTick: () -> Long,
    /** Takes a dead NPC out of the world, and puts a respawn in. */
    private val removeNpc: (Npc) -> Unit,
    private val spawnNpc: (NpcSpawn) -> Unit,
    /** Everyone logged in, for hitpoints regeneration. */
    private val players: () -> Sequence<Player> = { emptySequence() },
    /** What the player is told and shown; tests swap these for recorders. */
    private val message: (Player, String) -> Unit = Player::message,
    private val face: (Player, Npc) -> Unit = { _, _ -> },
    private val stopFacing: (Player) -> Unit = {},
    /** Told of a player with no hitpoints left; see [Deaths.die]. */
    private val onDeath: (Player) -> Unit = {},
    /** Puts a dead NPC's drops on the floor for its killer (null for none) once its body has gone. */
    private val loot: (Npc, Player?) -> Unit = { _, _ -> },
    /** A fight with a style melee doesn't fight with, or null for none: [MagicCombat.fight] or [RangedCombat.fight]. */
    private val ranged: (Player, Npc) -> Interaction? = { _, _ -> null },
    /** Where the single-way rules below don't hold. */
    private val multi: MultiCombat = MultiCombat(),
    /** Whether the player's protection prayers block an NPC's attack of this type; see [Prayers.protects]. */
    private val protects: (Player, AttackType) -> Boolean = { _, _ -> false },
    /** Whether a prayer with the effect counts for the player this tick; see [Prayers.has]. */
    private val hasPrayer: (Player, String) -> Boolean = { _, _ -> false },
    /** Every prayer off ([Prayers.deactivateAll]), for Redemption. */
    private val prayersOff: (Player) -> Unit = {},
    private val sound: (Player, Int) -> Unit = { player, id -> player.sound(id) },
    /** Each player's words for the game messages ([CombatLines]). */
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    /** Told of an NPC on the tick it is killed; see [osrsps.game.npc.NpcScripts.onDeath]. */
    private val npcDied: (Npc) -> Unit = {},
    /** Poisons an NPC at a severity, or envenoms it, as [Poison] does; false when it is immune or poisoned already. */
    private val poisonNpc: (Npc, Int) -> Unit = { _, _ -> },
    private val envenomNpc: (Npc) -> Unit = {},
    /** Poisons a player at a severity ([Poison.poison]), for an NPC whose attacks poison. */
    private val poisonPlayer: (Player, Int) -> Unit = { _, _ -> },
    /** The special attack a swing uses, if the bar is lit and paid for ([SpecialAttacks.use]); null for an ordinary swing. */
    private val special: (Player) -> SpecialAttack? = { null },
    /** A special attack's hit landed, with the damage it dealt ([SpecialAttacks.landed]). */
    private val specialLanded: (Npc, Int, SpecialAttack) -> Unit = { _, _, _ -> },
    /** Fires a magic or ranged NPC's projectile for everyone near it. */
    private val projectile: (Projectile) -> Unit = {},
    /** A player's hit, swing, shot, spell or command, took this much off an NPC (not poison or venom): a raid's contribution. */
    private val npcDamaged: (Npc, Player, Int) -> Unit = { _, _, _ -> },
) {
    private class Dying(val npc: Npc, val killer: Player?, val removeTick: Long, val respawnTick: Long)

    internal fun unreachable(player: Player) = stopFacing(player)

    private val dying = ArrayList<Dying>()

    /**
     * The player used Attack on [npc]. Returns false when the NPC has no combat definition, so the click falls through
     * to whatever else handles it. With a style nothing fights with yet (magic, thrown weapons) the player is told so.
     */
    fun attack(player: Player, npc: Npc): Boolean = start(player, npc, now = true)

    /**
     * The player used Attack on [npc] with a style that doesn't walk up to it first (ranged, or one nothing fights with
     * yet, which is refused straight away). The fight starts in the movement phase, as one handed over on arrival does.
     * False for melee, which [attack] takes on arrival.
     */
    fun attackFromAfar(player: Player, npc: Npc): Boolean =
        combat.meleeType(combat.style(player)) == null && start(player, npc, now = false)

    /** Starts a fight with [npc], tried at once when [now]. False when the NPC can't be fought; see [attack]. */
    private fun start(player: Player, npc: Npc, now: Boolean): Boolean {
        combat.npcCombat[npc.type] ?: return false
        if (combat.bulwarkBlock(player)) {
            message(player, words(player)[CombatLines.BULWARK_BLOCK])
            return true
        }
        val fight = fight(player, npc)
        if (fight == null) {
            message(player, MELEE_ONLY)
            return true
        }
        if (underAttackByAnother(player, npc)) {
            message(player, words(player)[CombatLines.ALREADY_UNDER_ATTACK])
            return true
        }
        if (foughtByAnother(player, npc)) {
            message(player, words(player)[CombatLines.SOMEONE_ELSE])
            return true
        }
        player.interaction = fight
        // Beside it already: the click is handed over on arrival, so the first swing is this tick's.
        if (now && fight.tryInteract(player) && player.interaction === fight) player.interaction = null
        return true
    }

    /**
     * Whether [npc] flies out of [player]'s melee reach ([NpcCombatDef.flying]): only a halberd's reaches it (OSRS Wiki,
     * The Fractured Archive/Strategies: while the Archive Guardian levitates "only ranged, magic, and halberds can hit
     * it"). Melee styles only; a bow or spell isn't asked.
     */
    private fun flyingOutOfReach(player: Player, npc: Npc): Boolean =
        combat.npcCombat[npc.type]?.flying == true && combat.weaponCategory(player) != Weapons.POLEARM

    /** A fight with [npc] in the player's style: melee here, magic or ranged from [ranged]; null when nothing fights with it. */
    fun fight(player: Player, npc: Npc): Interaction? =
        if (combat.meleeType(combat.style(player)) != null) MeleeInteraction(npc, this) else ranged(player, npc)

    internal fun tryInteract(player: Player, interaction: MeleeInteraction): Boolean {
        val npc = interaction.npc
        if (npcs[npc.index] !== npc || npc.level != player.level || npc.dead) {
            stopFacing(player)
            return true
        }
        // It can take off mid-fight, as the Archive Guardian does once it is low.
        if (flyingOutOfReach(player, npc)) {
            message(player, words(player)[CombatLines.FLYING])
            stopFacing(player)
            return true
        }
        val size = npc.type.size
        if (!Reach.reachedEntity(collision, player.level, player.x, player.z, 1, npc.x, npc.z, size, size)) {
            interaction.inReach = false
            val at = Tile(npc.x, npc.z)
            if (interaction.routedTo != at) {
                interaction.routedTo = at
                movement.request(player, RouteTarget.entity(npc.x, npc.z, size), player.invertRun)
            }
            return false
        }
        interaction.inReach = true
        player.waypoints.clear()
        if (foughtByAnother(player, npc)) {
            message(player, words(player)[CombatLines.SOMEONE_ELSE])
            stopFacing(player)
            return true
        }
        // Another NPC may have caught them on the way; once the fight is on, a newcomer's hits don't stop it.
        if (!interaction.swung && underAttackByAnother(player, npc)) {
            message(player, words(player)[CombatLines.ALREADY_UNDER_ATTACK])
            stopFacing(player)
            return true
        }
        val tick = currentTick()
        if (tick >= player.nextAttackTick) {
            swing(player, npc, tick)
            interaction.swung = true
        }
        return false
    }

    /**
     * Whether [npc] is someone else's: it took on or swung at another player within the last [IN_COMBAT_TICKS] ticks,
     * one who never hit it too (an aggressive monster's prey with auto retaliate off), or another player hit it within
     * them and is still here. So an NPC held at its max range by a player out of its reach is free again once neither
     * side has fought for that long. Guessed: the Wiki says a single-way NPC stays someone's for a while after their
     * last hit, but not how long, and that only one player or monster can attack one at a time. Never for an NPC
     * standing in a multi-way area (guessed: by the NPC's tile, not the player's).
     */
    internal fun foughtByAnother(player: Player, npc: Npc): Boolean {
        if (multi.at(npc.level, npc.x, npc.z)) return false
        val tick = currentTick()
        val target = npc.target
        if (target != null && target !== player && !target.loggedOut && tick - npc.fightTick <= IN_COMBAT_TICKS) return true
        val other = npc.attacker ?: return false
        return other !== player && tick - npc.attackedTick <= IN_COMBAT_TICKS && !other.loggedOut
    }

    /** Whether [player] hit [npc] within the last [IN_COMBAT_TICKS] ticks. */
    private fun hitBy(npc: Npc, player: Player): Boolean =
        npc.attacker === player && currentTick() - npc.attackedTick <= IN_COMBAT_TICKS

    /**
     * Whether [player] is fighting an NPC other than [npc]: they attacked one (a swing, shot or cast made, not just
     * clicked, so a bow with no arrows doesn't count) or hit one within the last [IN_COMBAT_TICKS] ticks, and it still
     * stands. Never for a player standing in a multi-way area (by the player's tile, as [underAttackByAnother]). So an
     * aggressive monster doesn't join in on a single-way fight.
     */
    internal fun fightingAnother(player: Player, npc: Npc): Boolean {
        if (multi.at(player.level, player.x, player.z)) return false
        val other = player.attackedNpc ?: return false
        return other !== npc && !other.dead && npcs[other.index] === other && currentTick() - player.attackedNpcTick <= IN_COMBAT_TICKS
    }

    /** [player] made an attack on [npc] on [tick], whatever it does when it lands; see [fightingAnother]. */
    internal fun attackMade(player: Player, npc: Npc, tick: Long) {
        player.attackedNpc = npc
        player.attackedNpcTick = tick
    }

    /**
     * Whether an NPC other than [npc] is fighting [player] and hit them within the last [IN_COMBAT_TICKS] ticks. Never
     * for a player standing in a multi-way area (guessed: by the player's tile).
     */
    internal fun underAttackByAnother(player: Player, npc: Npc): Boolean {
        if (multi.at(player.level, player.x, player.z)) return false
        val other = player.attackedBy ?: return false
        return other !== npc && other.target === player && !other.dead && currentTick() - player.attackedTick <= IN_COMBAT_TICKS
    }

    private fun swing(player: Player, npc: Npc, tick: Long) {
        val style = combat.style(player)
        if (combat.bulwarkBlock(player)) {
            // Block picked on the way (the combat tab is open while walking).
            message(player, words(player)[CombatLines.BULWARK_BLOCK])
            stopFacing(player)
            player.interaction = null
            return
        }
        var matchup = combat.matchup(player, npc) ?: run {
            // The style changed on the way (the combat tab is open while walking): a bow carries on as a ranged fight.
            val next = ranged(player, npc)
            if (next == null) {
                message(player, MELEE_ONLY)
                stopFacing(player)
            }
            player.interaction = next
            return
        }
        player.nextAttackTick = tick + combat.attackSpeed(player)
        val spec = special(player)
        if (spec?.against != null) matchup = combat.matchup(player, npc, spec.against)!!
        if (spec == null) {
            player.effects.anim(combat.anims(player).attack(style), 0)
        } else {
            player.effects.anim(spec.anim, 0)
            if (spec.spotanim >= 0) player.spotanim(spec.spotanim)
        }
        face(player, npc)
        strike(player, npc, matchup, spec, tick)
        // A special that hits around the player does so only in multi-way combat; in single-way it is one hit (guessed).
        if (spec == null || spec.radius <= 0 || !multi.at(player.level, player.x, player.z)) return
        // The second hit on the target doesn't drain (OSRS Wiki, Dinh's bulwark: the 1 December 2021 update).
        repeat(spec.targetHits - 1) { strike(player, npc, matchup, spec, tick, effect = false) }
        for (other in around(player, npc, spec.radius).take(spec.targets - 1)) {
            strike(player, other, combat.matchup(player, other, spec.against) ?: continue, spec, tick)
        }
    }

    /**
     * The fightable NPCs other than [target] within [radius] tiles of [player] on either axis, by any tile they cover,
     * standing in multi-way combat themselves (by their south-west tile, as a barrage's splash goes), nearest first.
     * One flying out of reach ([flyingOutOfReach]) is left out, as a swing at it is refused (exploit review round 13,
     * R13A-2). Guessed: nothing in the way stops the hit, and the nearest are hit first when there are too many.
     */
    private fun around(player: Player, target: Npc, radius: Int): List<Npc> {
        fun distance(npc: Npc): Int {
            val dx = maxOf(npc.x - player.x, player.x - (npc.x + npc.type.size - 1), 0)
            val dz = maxOf(npc.z - player.z, player.z - (npc.z + npc.type.size - 1), 0)
            return maxOf(dx, dz)
        }
        return npcs.near(player.x, player.z, player.level, radius + MAX_NPC_SIZE)
            .filter {
                it !== target && !it.dead && npcs[it.index] === it && combat.npcCombat[it.type] != null && distance(it) <= radius &&
                    multi.at(it.level, it.x, it.z) && !flyingOutOfReach(player, it)
            }
            .sortedBy(::distance)
    }

    /**
     * One hit of [player]'s swing on [npc] with [matchup]'s numbers, raised by [spec]'s if it is a special: rolled now,
     * its XP given now, and landing in the next tick's NPC phase with [spec]'s effect unless not [effect].
     */
    private fun strike(player: Player, npc: Npc, matchup: MeleeMatchup, spec: SpecialAttack?, tick: Long, effect: Boolean = true) {
        val style = matchup.style
        val attackRoll = spec?.attackRoll(matchup.attackRoll) ?: matchup.attackRoll
        val maxHit = spec?.maxHit(matchup.maxHit) ?: matchup.maxHit
        // Accuracy first, then damage, as the Wiki describes a hit; the attack's random draws are always these two.
        val rolled = if (CombatMath.rollHits(random, attackRoll, matchup.defenceRoll) || player.sureHits) {
            CombatMath.rollDamage(random, maxHit).let { if (player.sureMaxHits) maxHit else it }
        } else {
            0
        }
        // XP for what the swing can still deal: what the NPC has left after the swings and shots already on their way.
        val damage = minOf(rolled, (npc.hitpoints - npc.incoming).coerceAtLeast(0))
        npc.incoming += damage
        val bonus = combat.npcCombat[npc.type]?.xpBonusTenths ?: 0
        for (skill in style.xp) player.skills.addTenths(skill, CombatMath.withXpBonus(CombatMath.styleXpTenths(damage, style.xp.size), bonus))
        player.skills.addTenths(Skill.HITPOINTS, CombatMath.withXpBonus(CombatMath.hitpointsXpTenths(damage), bonus))
        val poison = PoisonedWeapons.melee(combat.weapon(player))
        attackMade(player, npc, tick)
        landing += Swing(player, npc, damage, tick + HitDelay.MELEE.ticks(0) + HitDelay.ON_NPC, poison, spec?.takeIf { effect }, maxHit, Tile(player.x, player.z))
    }

    /**
     * A player's swing on its way to an NPC: it lands on [landTick], poisoning at [poison] if over 0, with [special]'s
     * effect if it was one. [maxHit] is what it was rolled against, for the max hit hitsplat; [from] is where the
     * player stood to swing.
     */
    private class Swing(val player: Player, val npc: Npc, val damage: Int, val landTick: Long, val poison: Int, val special: SpecialAttack?, val maxHit: Int, val from: Tile)

    /** A magic or ranged NPC's [attack] on its way to [player], landing on [landTick] for [damage] if [hit] and not [blocked]. */
    private class Shot(val npc: Npc, val player: Player, val attack: NpcRangedAttack, val hit: Boolean, val blocked: Boolean, val damage: Int, val landTick: Long)

    private val shots = ArrayList<Shot>()

    private val landing = ArrayList<Swing>()

    /** Swings rolled and not landed yet, for tests. */
    val swingsLanding: Int get() = landing.size

    /**
     * The swings whose tick has come land, as [RangedCombat]'s shots do, whatever happened to the swinger since; one
     * whose NPC died or went does nothing.
     */
    private fun land(tick: Long) {
        val iterator = landing.iterator()
        while (iterator.hasNext()) {
            val swing = iterator.next()
            if (tick < swing.landTick) continue
            iterator.remove()
            val npc = swing.npc
            npc.incoming -= swing.damage
            if (npcs[npc.index] !== npc || npc.dead) continue
            val dealt = hit(npc, swing.player, swing.damage, tick, swing.maxHit, swing.from)
            if (swing.special != null) specialLanded(npc, dealt, swing.special)
            weaponPoison(npc, dealt, swing.poison, venom = false, ranged = false)
        }
    }

    /**
     * The hitsplat [source] sees for their own hit of [dealt]: the max hit one (damage_max_me) when it is the [maxHit]
     * it was rolled against and that is at least their All Settings "Max hit hitsplats threshold" (varbit
     * settings_hitsplat_threshold, "Max hits below this threshold will not show max hit hitsplats"). Whether it looks
     * different is the client's: hitmark 43's multimark on hitsplat_maxhit_disabled, the "Max hit hitsplats" toggle.
     * Guessed: a hit cut short by what the NPC had left isn't a max hit, and everyone else sees an ordinary hitsplat.
     */
    private fun damageMe(source: Player?, dealt: Int, maxHit: Int): Int {
        if (source == null || maxHit <= 0 || dealt != maxHit) return HitsplatType.DAMAGE_ME
        val types = source.vars.types
        val threshold = if (types.varbit(VARBIT_MAX_HIT_THRESHOLD) != null) source.vars.getVarbit(VARBIT_MAX_HIT_THRESHOLD) else 0
        return if (maxHit >= threshold) HitsplatType.DAMAGE_MAX_ME else HitsplatType.DAMAGE_ME
    }

    /**
     * After a player's hit on [npc] that [dealt] damage: one with a poisoned weapon or ammunition ([poison] its
     * severity, 0 for none) poisons it one time in four for melee and in eight for [ranged], and a toxic blowpipe's
     * ([venom]) envenoms it one time in four ([PoisonedWeapons]). A hit of 0, or one that killed it, does neither, and
     * rolls no dice.
     */
    fun weaponPoison(npc: Npc, dealt: Int, poison: Int, venom: Boolean, ranged: Boolean) {
        if (dealt <= 0 || npc.dead) return
        if (venom) {
            if (random.chance(1, PoisonedWeapons.VENOM_CHANCE)) envenomNpc(npc)
        } else if (poison > 0) {
            if (random.chance(1, if (ranged) PoisonedWeapons.RANGED_CHANCE else PoisonedWeapons.MELEE_CHANCE)) poisonNpc(npc, poison)
        }
    }

    /**
     * [npc] takes [damage] from [source] (null for none, as from a command), capped at what it has left, on [tick]:
     * the hitsplat and health bar for everyone, its block animation, or its death. A hit NPC turns on whoever hit it, or
     * retreats from them; [attackedFrom] is where they stood when they attacked, their tile now if null. [maxHit] is the
     * most a rolled hit could have done, 0 for damage that wasn't rolled against one (recoil, retribution, poison,
     * commands). Returns the damage dealt.
     */
    fun hit(npc: Npc, source: Player?, damage: Int, tick: Long, maxHit: Int = 0, attackedFrom: Tile? = null): Int {
        val def = combat.npcCombat[npc.type] ?: throw IllegalArgumentException("${npc.type.name} can't be fought")
        if (npc.dead) return 0
        val dealt = damage.coerceIn(0, npc.hitpoints)
        npc.hitpoints -= dealt
        if (source != null && dealt > 0) npcDamaged(npc, source, dealt)
        val from = if (source != null) NpcEffects.PLAYER_SOURCE + source.index else -1
        if (dealt > 0) npc.effects.hit(from, damageMe(source, dealt, maxHit), HitsplatType.DAMAGE_OTHER, dealt)
        else npc.effects.hit(from, HitsplatType.BLOCK_ME, HitsplatType.BLOCK_OTHER, 0)
        npc.effects.headBar(from, HEALTH_BAR, healthBarFill(npc.hitpoints, npc.maxHitpoints))
        attacked(npc, source, tick)
        if (npc.hitpoints > 0 || npc.holdsAtZero) {
            // Its own swing this tick wins over the block (guessed).
            if (def.anims.defend >= 0 && npc.effects.anim == null) npc.effects.anim(def.anims.defend)
            if (source != null) hitBy(npc, def, source, attackedFrom ?: Tile(source.x, source.z), tick)
        } else {
            die(npc, def, source, tick)
        }
        return dealt
    }

    /**
     * A spell [source] cast on [npc] splashed: no hitsplat, health bar or block animation (guessed: the Wiki shows the
     * splash graphic alone), but the NPC is under attack all the same and turns on whoever cast it, as one that is hit
     * does (OSRS Wiki, Splashing: it is how players train Magic on monsters that fight back).
     */
    fun splashed(npc: Npc, source: Player, tick: Long, attackedFrom: Tile? = null) {
        val def = combat.npcCombat[npc.type] ?: throw IllegalArgumentException("${npc.type.name} can't be fought")
        if (npc.dead) return
        attacked(npc, source, tick)
        hitBy(npc, def, source, attackedFrom ?: Tile(source.x, source.z), tick)
    }

    /**
     * [npc] was attacked by [player], who stood at [from] as they swung, fired or cast: it fights back, or retreats from them (OSRS Wiki, Retreat, read 2026-10-30). It
     * retreats when attacked from outside its aggression range ([Aggression.inAggressionRange]), and when its
     * hitpoints are at or below its [NpcCombatDef.retreatsAt]. Retreating, it walks away from the player and doesn't
     * fight back, keeping within its max range of its spawn ([Npcs.MAX_RANGE]), until the player leaves its interaction
     * range ([CHASE_RANGE], the max range plus 11) or dies or leaves. Guessed (the Wiki gives none of these): that a
     * hit from within the aggression range ends a retreat its hitpoints don't call for, and it fights back; the
     * hitpoints threshold of each NPC; walking straight away from the player, diagonals first; and that a retreat also
     * ends [IN_COMBAT_TICKS] after the last hit, so a player standing within range without hitting it doesn't keep an
     * aggressive NPC from hunting anyone else.
     */
    private fun hitBy(npc: Npc, def: NpcCombatDef, player: Player, from: Tile, tick: Long) {
        // A boss whose fight picks its moves never retreats, whatever its range or hitpoints.
        if (npc.scriptedAttacks) return retaliate(npc, player, tick)
        val inRange = Aggression.inAggressionRange(npc, from.x, from.z, combat.npcCombat[npc.type]?.range ?: Aggression.ATTACK_RANGE)
        if (lowOnHitpoints(npc, def) || (!inRange && npc.retreatingFrom == null)) return retreat(npc, player)
        if (npc.retreatingFrom != null) {
            if (!inRange) {
                // Another hit from out of range keeps it going.
                npc.retreatTick = tick
                return
            }
            npc.retreatingFrom = null
        }
        retaliate(npc, player, tick)
    }

    /** [npc] stops fighting and walks away from [player], from this tick; see [hitBy]. */
    private fun retreat(npc: Npc, player: Player) {
        giveUp(npc)
        npc.retreatingFrom = player
        npc.retreatTick = currentTick()
        npc.destination = null
    }

    /** Whether [npc]'s hitpoints are at or below its [NpcCombatDef.retreatsAt] share, rounded up. */
    private fun lowOnHitpoints(npc: Npc, def: NpcCombatDef): Boolean =
        def.retreatsAt > 0 && npc.hitpoints <= (npc.maxHitpoints * def.retreatsAt + 99) / 100

    /**
     * Ends [npc]'s retreat once [player] has left its interaction range, died or gone, or not hit it for
     * [IN_COMBAT_TICKS] (guessed); see [hitBy].
     */
    private fun keepRetreating(npc: Npc, player: Player, tick: Long) {
        if (player.loggedOut || player.dead || player.level != npc.level || outOfRange(npc, player) || tick - npc.retreatTick > IN_COMBAT_TICKS) {
            npc.retreatingFrom = null
        }
    }

    /**
     * [npc] turns on [player] unprovoked, as a market guard does on a thief: it faces them and attacks from the next tick,
     * as an aggressive monster does, with [osrsps.game.combat.Aggression.huntable]'s single-way checks: it doesn't leave a
     * fight with another player, nor join in while another NPC is hitting them or they are fighting one, and of two guards
     * sent at once only the first to land a hit keeps on. Does nothing to an NPC that can't fight.
     */
    fun provoke(npc: Npc, player: Player, tick: Long) {
        if (npc.dead || combat.npcCombat[npc.type] == null) return
        if (foughtByAnother(player, npc) || underAttackByAnother(player, npc) || fightingAnother(player, npc)) return
        npc.face(player)
        aggress(npc, player, tick)
    }

    /** [npc] is in combat with [source] (null for none) from [tick], and faces them. */
    private fun attacked(npc: Npc, source: Player?, tick: Long) {
        npc.attacker = source
        if (source != null) {
            npc.lastAttacker = source
            source.attackedNpc = npc
            source.attackedNpcTick = tick
        }
        npc.attackedTick = tick
        npc.holdTicks = IN_COMBAT_TICKS
        if (source != null) npc.face(source)
    }

    /** [npc] was hit by [player]: it turns on them, striking from the next tick (guessed) if it isn't fighting already. */
    private fun retaliate(npc: Npc, player: Player, tick: Long) {
        if (npc.target === player) return
        // A boss whose fight picks its attacks keeps to the player it took on first while they last (Npc.scriptedAttacks).
        if (npc.scriptedAttacks) {
            val held = npc.target
            if (held != null && !held.loggedOut && !held.dead && held.level == npc.level) return
            npc.target = player
            npc.fightTick = tick
            return
        }
        npc.target = player
        npc.fightTick = tick
        npc.nextAttackTick = maxOf(npc.nextAttackTick, tick + 1)
        npcs.follow(npc, player)
    }

    /** [npc] goes for [player] unprovoked ([Aggression]), as if they had hit it. */
    internal fun aggress(npc: Npc, player: Player, tick: Long) = retaliate(npc, player, tick)

    private fun die(npc: Npc, def: NpcCombatDef, killer: Player?, tick: Long) {
        npc.dead = true
        npc.retreatingFrom = null
        giveUp(npc)
        if (def.anims.death >= 0) npc.effects.anim(def.anims.death)
        // The body goes before the replacement comes, however short the respawn time (a rat's is 2 ticks).
        val respawnTick = tick + maxOf(def.respawnTicks, DEATH_TICKS)
        dying += Dying(npc, killer, tick + DEATH_TICKS, respawnTick)
        logger.debug { "$npc died; back on tick $respawnTick" }
        npcDied(npc)
    }

    /** [npc] stops fighting whoever it fought and goes back to what it did. */
    private fun giveUp(npc: Npc) {
        if (npc.target == null) return
        npc.target = null
        npcs.stopFollowing(npc)
    }

    /**
     * The NPC phase's combat: last tick's player swings land, every fighting NPC chases and hits its target or gives
     * up, the NPCs whose death is over are taken out with their drops put down, those whose respawn time is up put
     * back, and every hurt player and NPC whose minute is up heals a point.
     */
    fun process() {
        val tick = currentTick()
        land(tick)
        landShots(tick)
        val iterator = dying.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (tick >= entry.removeTick && npcs[entry.npc.index] === entry.npc) {
                removeNpc(entry.npc)
                // The drops show as the body goes (OSRS Wiki, Drop: loot appears when the monster's death animation ends).
                loot(entry.npc, entry.killer)
            }
            if (tick >= entry.respawnTick) {
                // Never before the body has gone: a respawn no shorter than DEATH_TICKS and the removal above see to it.
                check(npcs[entry.npc.index] !== entry.npc) { "${entry.npc} respawning with its body still there" }
                iterator.remove()
                if (entry.npc.spawn.respawns) spawnNpc(entry.npc.spawn)
            }
        }
        for (npc in npcs.all) {
            npc.retreatingFrom?.let { keepRetreating(npc, it, tick) }
            val target = npc.target ?: continue
            fightBack(npc, target, tick)
        }
        regeneratePlayers()
        regenerateNpcs()
    }

    private fun fightBack(npc: Npc, player: Player, tick: Long) {
        val def = combat.npcCombat[npc.type] ?: return giveUp(npc)
        if (npc.dead) return
        // A newcomer in single-way combat doesn't make it give up on a player who is hitting it themselves.
        if (player.loggedOut || player.level != npc.level || player.hitpoints <= 0 || outOfRange(npc, player) ||
            (underAttackByAnother(player, npc) && !hitBy(npc, player))
        ) {
            giveUp(npc)
            return
        }
        if (leftBehind(npc, player, tick)) {
            giveUp(npc)
            // Home, so it hunts from its spawn again rather than from the edge of its range. Guessed.
            npc.destination = Tile(npc.spawn.x, npc.spawn.z)
            return
        }
        // Poison or another player can take it down to where it gives up the fight.
        if (lowOnHitpoints(npc, def)) return retreat(npc, player)
        if (npc.scriptedAttacks) {
            npc.fightTick = tick
            npc.face(player)
            return
        }
        if (def.range > 1) return shoot(npc, def, player, tick)
        // Chase's own far limit may have let go of them; a fighting NPC keeps after them while they are in range.
        if (npc.following !== player) npcs.follow(npc, player)
        val size = npc.type.size
        if (!Reach.reachedEntity(collision, npc.level, npc.x, npc.z, size, player.x, player.z, 1, 1)) return
        if (tick < npc.nextAttackTick) return
        swingAt(npc, player, tick)
    }

    /**
     * [npc] swings its melee attack at [player] now, as its fight back does once it is in reach and its attack is due:
     * the animation, the roll, a protection prayer or Dinh's bulwark, and its poison. A boss fight calls it for its
     * boss's own swings ([Npc.scriptedAttacks]); [anim] is the swing's animation, -1 for its combat definition's.
     */
    fun swingAt(npc: Npc, player: Player, tick: Long, anim: Int = -1) {
        val def = combat.npcCombat[npc.type] ?: return
        npc.nextAttackTick = tick + def.attackSpeed
        npc.fightTick = tick
        // Only an NPC with a spell rolls for it, so World.random runs as before for every other.
        val spell = def.spell
        if (spell != null && spellCasts(spell, player) && random.chance(1, spell.chance)) return cast(npc, player, spell.attack, tick)
        val shown = if (anim >= 0) anim else def.anims.attack
        if (shown >= 0) npc.effects.anim(shown)
        npc.face(player)
        val roll = combat.npcAttack(npc, player) ?: return
        val damage = if (CombatMath.rollHits(random, roll.attackRoll, roll.defenceRoll)) CombatMath.rollDamage(random, roll.maxHit) else 0
        // The dice are rolled either way, so a prayer doesn't change what comes after (World.random is shared).
        val blocked = if (combat.bulwarkBlocking(player, tick)) DinhsBulwark.blocked(damage) else damage
        hitPlayer(player, npc, if (protects(player, def.attackType)) 0 else blocked, tick)
        // Its poison comes whatever the hit did, through a protection prayer too (OSRS Wiki, Poison). Guessed: one
        // attack in four, as for a player's poisoned melee weapon; the Wiki gives no chance for monsters.
        if (def.poison > 0 && !player.dead && random.chance(1, PoisonedWeapons.MELEE_CHANCE)) poisonPlayer(player, def.poison)
    }

    /**
     * Whether [npc] has lost [player]: they stand outside its aggression range ([Aggression.inAggressionRange]), where
     * it can't follow them (it keeps within its max range of its spawn, [Npcs.MAX_RANGE]), it hasn't swung at them or
     * taken them on for [IN_COMBAT_TICKS], and they haven't hit it in that time. The Wiki (Aggressiveness) says the
     * aggression range "defines the outer limit of where the player can stand and still be attacked"; how long it
     * waits there first is guessed.
     */
    private fun leftBehind(npc: Npc, player: Player, tick: Long): Boolean =
        !Aggression.inAggressionRange(npc, player.x, player.z, combat.npcCombat[npc.type]?.range ?: Aggression.ATTACK_RANGE) &&
            tick - npc.fightTick > IN_COMBAT_TICKS && !hitBy(npc, player)

    /**
     * A magic or ranged NPC's turn: out of its range or sight of [player] it walks after them; in both it stands and,
     * when its next attack is due, fires one of its [NpcCombatDef.attacks] (by weight). The roll is made as it fires
     * and the hit lands after [HitDelay.MAGIC] or [HitDelay.BOW] for the distance, as a player's does. A protection
     * prayer up as it fires blocks the damage and the drain (guessed, as for melee: the prayer counts when rolled).
     * Guessed: the casting and impact spotanims' heights, the projectile's heights and arc (a player's spell's), that it
     * stands still once in range rather than stepping closer, and that a miss shows the splash and no hitsplat.
     */
    private fun shoot(npc: Npc, def: NpcCombatDef, player: Player, tick: Long) {
        if (!inShot(npc, def, player)) {
            if (npc.following !== player) npcs.follow(npc, player)
            return
        }
        if (npc.following === player) npcs.stopFollowing(npc)
        npc.face(player)
        if (tick < npc.nextAttackTick || def.attacks.isEmpty()) return
        fireAt(npc, player, tick)
    }

    /** Whether [player] is in [npc]'s range and sight for its magic or ranged attacks, as its fight back asks before a shot. */
    fun inShot(npc: Npc, player: Player): Boolean = combat.npcCombat[npc.type]?.let { inShot(npc, it, player) } ?: false

    private fun inShot(npc: Npc, def: NpcCombatDef, player: Player): Boolean {
        val size = npc.type.size
        val distance = LineOfSight.distance(npc.x, npc.z, size, player.x, player.z)
        return distance in 1..def.range && LineOfSight.clear(collision, npc.level, npc.x, npc.z, size, player.x, player.z)
    }

    /**
     * [npc] fires one of its magic or ranged attacks (by weight) at [player] now, as its fight back does when its attack
     * is due and they are in range and sight: the roll as it fires and the hit landing after the flight. A boss fight
     * calls it for its boss's own shots ([Npc.scriptedAttacks]). Does nothing for an NPC with no such attacks.
     */
    fun fireAt(npc: Npc, player: Player, tick: Long) {
        val def = combat.npcCombat[npc.type] ?: return
        if (def.attacks.isEmpty()) return
        val distance = maxOf(1, LineOfSight.distance(npc.x, npc.z, npc.type.size, player.x, player.z))
        npc.face(player)
        npc.nextAttackTick = tick + def.attackSpeed
        // As a melee swing does, so another player can't take it over while it keeps firing.
        npc.fightTick = tick
        val attack = pick(def.attacks)
        val anim = if (attack.anim >= 0) attack.anim else def.anims.attack
        if (anim >= 0) npc.effects.anim(anim)
        if (attack.cast >= 0) npc.effects.spotanim(0, attack.cast, CAST_HEIGHT)
        val delay = (if (def.attackType == AttackType.MAGIC) HitDelay.MAGIC else HitDelay.BOW).ticks(distance)
        if (attack.projectile >= 0) projectile(flight(npc, player, attack.projectile, delay))
        val roll = combat.npcAttack(npc, player) ?: return
        val hit = CombatMath.rollHits(random, roll.attackRoll, roll.defenceRoll)
        val max = attack.maxHit ?: roll.maxHit
        val damage = if (hit && max > 0) CombatMath.rollDamage(random, max) else 0
        val blocked = protects(player, def.attackType)
        // Dinh's bulwark counts as it fires, as the prayer does and as for a melee swing.
        val reduced = if (combat.bulwarkBlocking(player, tick)) DinhsBulwark.blocked(damage) else damage
        shots += Shot(npc, player, attack, hit, blocked, if (blocked) 0 else reduced, tick + delay)
    }

    private fun spellCasts(spell: NpcMeleeSpell, player: Player): Boolean {
        val skill = spell.attack.drain?.skill ?: return true
        return spell.casts(player.skills.baseLevel(skill), player.skills.currentLevel(skill))
    }

    /**
     * A melee NPC's spell at [player] in its reach ([NpcMeleeSpell]): its animation, the projectile, and a magic roll
     * against their Magic defence, landing after [HitDelay.MAGIC] as an NPC's shot does ([shoot]); Protect from
     * Magic blocks it. Guessed: no casting spotanim, and the spell's heights and arc are a player's.
     */
    private fun cast(npc: Npc, player: Player, attack: NpcRangedAttack, tick: Long) {
        val def = combat.npcCombat[npc.type] ?: return
        val anim = if (attack.anim >= 0) attack.anim else def.anims.attack
        if (anim >= 0) npc.effects.anim(anim)
        npc.face(player)
        if (attack.cast >= 0) npc.effects.spotanim(0, attack.cast, CAST_HEIGHT)
        val distance = LineOfSight.distance(npc.x, npc.z, npc.type.size, player.x, player.z).coerceAtLeast(1)
        val delay = HitDelay.MAGIC.ticks(distance)
        if (attack.projectile >= 0) projectile(flight(npc, player, attack.projectile, delay))
        val roll = combat.npcAttack(npc, player, AttackType.MAGIC) ?: return
        val hit = CombatMath.rollHits(random, roll.attackRoll, roll.defenceRoll)
        val max = attack.maxHit ?: roll.maxHit
        val damage = if (hit && max > 0) CombatMath.rollDamage(random, max) else 0
        val blocked = protects(player, AttackType.MAGIC)
        val reduced = if (combat.bulwarkBlocking(player, tick)) DinhsBulwark.blocked(damage) else damage
        shots += Shot(npc, player, attack, hit, blocked, if (blocked) 0 else reduced, tick + delay)
    }

    private fun pick(attacks: List<NpcRangedAttack>): NpcRangedAttack {
        if (attacks.size == 1) return attacks[0]
        var roll = random.nextInt(attacks.sumOf { it.weight })
        for (attack in attacks) {
            if (roll < attack.weight) return attack
            roll -= attack.weight
        }
        error("Unreachable")
    }

    /** The shots whose time has come land: the impact and the hitsplat (none for a drain-only spell), or the splash. */
    private fun landShots(tick: Long) {
        val iterator = shots.iterator()
        while (iterator.hasNext()) {
            val shot = iterator.next()
            if (tick < shot.landTick) continue
            iterator.remove()
            val player = shot.player
            if (player.loggedOut || player.dead || player.hitpoints <= 0) continue
            if (shot.blocked && shot.attack.maxHit != 0) {
                // The prayer shows a 0, hit or miss.
                hitPlayer(player, shot.npc, 0, tick)
                continue
            }
            if (!shot.hit || shot.blocked) {
                player.effects.spotanim(0, SPLASH, IMPACT_HEIGHT, 0)
                attackedWithoutHit(player, shot.npc, tick)
                continue
            }
            if (shot.attack.impact >= 0) player.effects.spotanim(0, shot.attack.impact, IMPACT_HEIGHT, 0)
            shot.attack.drain?.let { drain(player, it) }
            if (shot.attack.maxHit == 0) {
                attackedWithoutHit(player, shot.npc, tick)
            } else {
                hitPlayer(player, shot.npc, shot.damage, tick)
            }
        }
    }

    /**
     * [npc]'s shot reached [player] without a hitsplat (a splash, or a drain-only spell): they are attacked by it all the
     * same, and auto-retaliate turns them on it as a hit would ([hitPlayer]).
     */
    private fun attackedWithoutHit(player: Player, npc: Npc, tick: Long) {
        player.attackedBy = npc
        player.attackedTick = tick
        if (player.autoRetaliate && player.interaction == null && !player.modalOpen && tick >= player.stunnedUntil) retaliate(player, npc, tick)
    }

    /** Lowers [drain]'s skill by its percent of the base level (at least 1), unless it is that low already (Confuse). */
    private fun drain(player: Player, drain: StatDrain) {
        val base = player.skills.baseLevel(drain.skill)
        val by = maxOf(1, base * drain.percent / 100)
        if (drain.stacks) {
            val current = player.skills.currentLevel(drain.skill)
            if (current > 1) player.skills.setCurrentLevel(drain.skill, maxOf(1, current - by))
            return
        }
        val floor = base - by
        if (player.skills.currentLevel(drain.skill) <= floor) return
        player.skills.setCurrentLevel(drain.skill, floor)
    }

    /** [npc]'s projectile at [player], landing [ticks] later, in client cycles; a player's spell's numbers (guessed). */
    private fun flight(npc: Npc, player: Player, id: Int, ticks: Int): Projectile = Projectile(
        id = id,
        fromX = npc.x,
        fromZ = npc.z,
        level = npc.level,
        toX = player.x,
        toZ = player.z,
        startHeight = PROJECTILE_START_HEIGHT,
        endHeight = PROJECTILE_END_HEIGHT,
        // A player's spell leaves at cycle 51, but an NPC's lands a tick sooner (no HitDelay.ON_NPC): a short flight leaves
        // earlier so it still arrives as the hit lands. Guessed.
        startTime = minOf(PROJECTILE_START, ticks * CYCLES_PER_TICK - PROJECTILE_MIN_FLIGHT).coerceAtLeast(0),
        endTime = maxOf(1, ticks * CYCLES_PER_TICK),
        angle = PROJECTILE_ANGLE,
        progress = PROJECTILE_PROGRESS,
        sourceIndex = npc.index + 1,
        targetIndex = -(player.index + 1),
    )

    /** Magic and ranged NPC shots on their way, for tests. */
    val shotsInFlight: Int get() = shots.size

    /** Whether [player] is further than [CHASE_RANGE] from [npc]'s spawn on either axis. */
    private fun outOfRange(npc: Npc, player: Player): Boolean =
        abs(player.x - npc.spawn.x) > CHASE_RANGE || abs(player.z - npc.spawn.z) > CHASE_RANGE

    /**
     * [player] takes [damage] from [source] (null for none), capped at their hitpoints and 0 under `::god`, on [tick]:
     * the hitsplat and health bar for everyone, their block animation unless they swing this tick, and auto retaliate.
     * At no hitpoints they die; see the class note. Returns the damage dealt.
     */
    fun hitPlayer(player: Player, source: Npc?, damage: Int, tick: Long): Int {
        if (player.hitpoints <= 0 || player.dead) return 0
        val dealt = if (player.god) 0 else damage.coerceIn(0, player.hitpoints)
        player.hitpoints -= dealt
        val from = source?.index ?: -1
        if (dealt > 0) player.effects.hit(from, HitsplatType.DAMAGE_ME, HitsplatType.DAMAGE_OTHER, HitsplatType.DAMAGE_OTHER, dealt)
        else player.effects.hit(from, HitsplatType.BLOCK_ME, HitsplatType.BLOCK_OTHER, HitsplatType.BLOCK_OTHER, 0)
        player.effects.headBar(from, HEALTH_BAR, healthBarFill(player.hitpoints, player.skills.baseLevel(Skill.HITPOINTS)))
        // The NPC phase comes before the player's own swing, which overwrites this (guessed: the swing wins).
        if (player.effects.anim == null) player.effects.anim(combat.anims(player).defend, 0)
        if (source != null) {
            player.attackedBy = source
            player.attackedTick = tick
        }
        if (player.hitpoints <= 0) {
            died(player)
            return dealt
        }
        redemption(player)
        if (source != null && player.autoRetaliate && player.interaction == null && !player.modalOpen && tick >= player.stunnedUntil) {
            retaliate(player, source, tick)
        }
        return dealt
    }

    /**
     * [player] takes [damage] of poison or venom ([Poison]) with [hitsplat], capped at their hitpoints and 0 under
     * `::god`: no one hits them, so there is no block animation, no attacker and no retaliation, but it can kill and
     * Redemption counts it (guessed). Others see [otherHitsplat], the same unless the caller says. Returns the damage
     * dealt.
     */
    fun statusHit(player: Player, damage: Int, hitsplat: Int, otherHitsplat: Int = hitsplat): Int {
        if (player.hitpoints <= 0 || player.dead) return 0
        val dealt = if (player.god) 0 else damage.coerceIn(0, player.hitpoints)
        player.hitpoints -= dealt
        player.effects.hit(-1, hitsplat, otherHitsplat, otherHitsplat, dealt)
        player.effects.headBar(-1, HEALTH_BAR, healthBarFill(player.hitpoints, player.skills.baseLevel(Skill.HITPOINTS)))
        if (player.hitpoints <= 0) died(player) else redemption(player)
        return dealt
    }

    /**
     * [npc] takes [damage] of poison or venom with [hitsplat], capped at what it has left: the hitsplat and health bar,
     * but no block animation, no XP and no one to turn on. Killed by it, its drops go to whoever fought it last, even
     * once that fight is over ([Npc.lastAttacker]). Taken down to its [NpcCombatDef.retreatsAt] share while fighting,
     * it retreats from its target at once, before this tick's movement, as from a hit ([hitBy]).
     * Returns the damage dealt.
     */
    fun statusHit(npc: Npc, damage: Int, hitsplat: Int): Int {
        val def = combat.npcCombat[npc.type] ?: return 0
        if (npc.dead || npc.hitpoints <= 0) return 0
        val dealt = damage.coerceIn(0, npc.hitpoints)
        npc.hitpoints -= dealt
        npc.effects.hit(-1, hitsplat, hitsplat, dealt)
        npc.effects.headBar(-1, HEALTH_BAR, healthBarFill(npc.hitpoints, npc.maxHitpoints))
        if (npc.hitpoints <= 0 && !npc.holdsAtZero) die(npc, def, npc.attacker ?: npc.lastAttacker, currentTick())
        else npc.target?.let { if (lowOnHitpoints(npc, def)) retreat(npc, it) }
        return dealt
    }

    /**
     * Redemption: hit to under 10% of their Hitpoints level, a 0 included, [player] is healed a quarter of their Prayer
     * level, rounded down, their prayer points go to 0 and every prayer goes off (OSRS Wiki, Redemption). Guessed: that
     * the heal comes on the tick of the hit, with the spotanim and the heal sound on the player.
     */
    private fun redemption(player: Player) {
        if (player.hitpoints * 10 >= player.skills.baseLevel(Skill.HITPOINTS) || !hasPrayer(player, Prayers.REDEMPTION)) return
        val max = player.skills.baseLevel(Skill.HITPOINTS)
        player.hitpoints = minOf(max, player.hitpoints + player.skills.baseLevel(Skill.PRAYER) / 4)
        player.skills.setCurrentLevel(Skill.PRAYER, 0)
        prayersOff(player)
        player.spotanim(REDEMPTION_SPOTANIM)
        sound(player, REDEMPTION_HEAL_SOUND)
        player.effects.headBar(-1, HEALTH_BAR, healthBarFill(player.hitpoints, max))
    }

    /**
     * Retribution, as [player] falls: every NPC they could fight within a tile of them takes up to a quarter of their
     * Prayer level, rounded down, a roll each, 0s included (OSRS Wiki, Retribution). Guessed: the Wiki says only
     * "nearby enemies", so the 3x3 around the player in multi-way combat, and in single-way the NPC that last hit them
     * alone, if it is in that 3x3; the spotanim on the player; the targets' hits on this tick.
     */
    private fun retribution(player: Player, tick: Long) {
        if (!hasPrayer(player, Prayers.RETRIBUTION)) return
        player.spotanim(RETRIBUTION_SPOTANIM)
        val max = player.skills.baseLevel(Skill.PRAYER) / 4
        val targets = if (multi.at(player.level, player.x, player.z)) {
            npcs.all.filter { !it.dead && it.level == player.level && combat.npcCombat[it.type] != null && withinATile(player, it) }.toList()
        } else {
            listOfNotNull(player.attackedBy?.takeIf { !it.dead && npcs[it.index] === it && it.level == player.level && withinATile(player, it) })
        }
        for (npc in targets) hit(npc, player, CombatMath.rollDamage(random, max), tick)
    }

    private fun withinATile(player: Player, npc: Npc): Boolean {
        val size = npc.type.size
        return player.x >= npc.x - 1 && player.x <= npc.x + size && player.z >= npc.z - 1 && player.z <= npc.z + size
    }

    /**
     * Auto retaliate: [player] turns on [npc], which hit them on [tick]. OSRS Wiki, Auto Retaliate: the character
     * retaliates one tick after being attacked, with the attack delay set to the weapon's speed halved and rounded up
     * whatever it was, but never before [Player.attackLockedUntil]. The fight starts now and the first swing comes with
     * that delay. A stunned player doesn't retaliate ([StatusEffects], guessed).
     */
    private fun retaliate(player: Player, npc: Npc, tick: Long) {
        if (foughtByAnother(player, npc)) return
        player.interaction = fight(player, npc) ?: return
        player.nextAttackTick = maxOf(tick + (combat.attackSpeed(player) + 1) / 2, player.attackLockedUntil)
    }

    /** "Oh dear, you are dead!": every fight with the player ends, and [Deaths] has them from here. */
    private fun died(player: Player) {
        message(player, words(player)[CombatLines.DEAD])
        retribution(player, currentTick())
        endFights(player)
        onDeath(player)
    }

    /** Every fight with [player] ends: theirs, and those of the NPCs fighting them. */
    fun endFights(player: Player) {
        player.interaction = null
        stopFacing(player)
        for (npc in npcs.all) if (npc.target === player) giveUp(npc)
        player.attackedBy = null
    }

    /**
     * Each player's hitpoints countdown ([Player.hitpointsRegenTicks]): at 0 a hurt player gets a hitpoint back and it
     * starts again, at [REGEN_TICKS] (OSRS Wiki, Hitpoints: one a minute) or [Prayers.RAPID_HEAL_TICKS] with Rapid Heal.
     * A countdown set earlier in the tick (login, Rapid Heal toggled) counts this tick, so the hitpoint comes that many
     * ticks later; one started again here has had this tick, so it starts one lower.
     * Guessed: the countdown runs from login, and while hitpoints are full.
     */
    private fun regeneratePlayers() {
        for (player in players()) {
            if (player.hitpointsRegenTicks > 0) {
                player.hitpointsRegenTicks--
                continue
            }
            player.hitpointsRegenTicks = (if (hasPrayer(player, Prayers.RAPID_HEAL)) Prayers.RAPID_HEAL_TICKS else REGEN_TICKS) - 1
            val max = player.skills.baseLevel(Skill.HITPOINTS)
            if (player.hitpoints in 1 until max && !player.dead) player.hitpoints++
        }
    }

    /**
     * Each NPC's own hitpoints countdown ([Npc.hitpointsRegenTicks]), started on the tick it spawns: at 0 a hurt NPC gets a
     * hitpoint back and it starts again at [REGEN_TICKS], in a fight or out of one. The default rate is the players'
     * one a minute (OSRS Wiki, Guardian (Chambers of Xeric), quoting Mod Ash: "default should be 100"; User:Orion/Regen
     * measures 100 ticks for most monsters and has no in-or-out-of-combat condition). Inferred: the countdown starts at
     * the spawn and runs while hitpoints are full, as the players' does. No health bar is sent for it: the client shows
     * an NPC's bar only for 6 s after its last update (healthbar 0's duration, 300 client cycles, in the 241 cache), and
     * nothing says live sends one for regeneration.
     */
    private fun regenerateNpcs() {
        for (npc in npcs.all) {
            if (npc.dead || combat.npcCombat[npc.type] == null) continue
            // Held at 0 (a boss's shield broken, Npc.holdsAtZero): it stays at 0 till its fight moves it on, or a point
            // back on this tick would mend the shield before the fight sees it broken.
            if (npc.holdsAtZero && npc.hitpoints <= 0) continue
            // The tick it appears on starts the countdown without counting, whether it came before this phase (a
            // command, the server starting) or in it (a respawn), so the first hitpoint comes a full minute later.
            if (npc.hitpointsRegenTicks < 0) {
                npc.hitpointsRegenTicks = REGEN_TICKS
                continue
            }
            if (--npc.hitpointsRegenTicks > 0) continue
            npc.hitpointsRegenTicks = REGEN_TICKS
            if (npc.hitpoints < npc.maxHitpoints) npc.hitpoints++
        }
    }

    /** The NPCs dead and waiting to come back, for tests and commands. */
    val respawning: Int get() = dying.size

    /** The spawns of the dead NPCs that will come back, with the tick each comes back on, soonest first. */
    /** Forgets the deaths of NPCs in, or from, the tiles where [inside] holds (an instance ending): none comes back there. */
    fun forgetArea(inside: (Int, Int) -> Boolean) {
        dying.removeIf { inside(it.npc.x, it.npc.z) || inside(it.npc.spawn.x, it.npc.spawn.z) }
    }

    fun pendingRespawns(): List<Pair<NpcSpawn, Long>> =
        dying.filter { it.npc.spawn.respawns }.map { it.npc.spawn to it.respawnTick }.sortedBy { it.second }

    companion object {
        /** settings_hitsplat_threshold. */
        const val VARBIT_MAX_HIT_THRESHOLD = 14195

        /** `failedspell_impact`, a spell that missed (as spells.json names it for players). */
        const val SPLASH = 85

        /** Guessed, as for a player's spells: about the hands, and the middle of a man-sized target. */
        const val CAST_HEIGHT = 92
        const val IMPACT_HEIGHT = 124

        private const val CYCLES_PER_TICK = 30
        private const val PROJECTILE_START = 51

        /** The fewest client cycles an NPC's projectile flies for. Guessed. */
        private const val PROJECTILE_MIN_FLIGHT = 10
        private const val PROJECTILE_START_HEIGHT = 172
        private const val PROJECTILE_END_HEIGHT = 124
        private const val PROJECTILE_ANGLE = 16
        private const val PROJECTILE_PROGRESS = 64

        /** Not a live message: magic and the other ranged weapons come with later pieces. */
        const val MELEE_ONLY = "Only melee, bows and crossbows are in so far; the rest comes later."

        /**
         * Ticks an NPC counts as fighting its last attacker, and stands still, after their last hit; also how long a
         * player counts as under attack by the NPC that hit them last. Guessed; the Wiki gives no number.
         */
        const val IN_COMBAT_TICKS = 8

        /** Ticks a dead NPC's body stays before it vanishes. Guessed; the death animations are a few seconds. */
        const val DEATH_TICKS = 4

        /**
         * How far from its spawn, on either axis, an NPC's target may be before it gives up: the Wiki's retreat max
         * range (7) plus its interaction range (11). Guessed that chasing uses the same numbers.
         */
        const val CHASE_RANGE = Npcs.MAX_RANGE + 11

        /** Wider than any NPC, so one whose south-west tile is this far off can still cover a tile in range. */
        private const val MAX_NPC_SIZE = 16

        /** Ticks between hitpoints coming back: one a minute (OSRS Wiki, Hitpoints). */
        const val REGEN_TICKS = 100

        /** redemption_spotanim and retribution_spotanim in the 241 cache; the redemption_heal sound (OSRS Wiki, Redemption). */
        const val REDEMPTION_SPOTANIM = 436
        const val RETRIBUTION_SPOTANIM = 437
        const val REDEMPTION_HEAL_SOUND = 2681

        /** The headbar NPCs and players show their health on: id 0, 30 pixels wide (cache config 33, rev 241). */
        const val HEALTH_BAR = 0
        const val HEALTH_BAR_WIDTH = 30

        /** Pixels of the health bar for [hitpoints] of [max]: at least one while alive, none when dead. */
        fun healthBarFill(hitpoints: Int, max: Int): Int {
            if (hitpoints <= 0 || max <= 0) return 0
            return (hitpoints * HEALTH_BAR_WIDTH / max).coerceIn(1, HEALTH_BAR_WIDTH)
        }
    }
}

/** The client's hitsplat ids (RuneLite's HitsplatID): the red damage splat and the blue block, each for the fighters and for onlookers. */
object HitsplatType {
    const val BLOCK_ME = 12
    const val BLOCK_OTHER = 13
    const val DAMAGE_ME = 16
    const val DAMAGE_OTHER = 17

    /** damage_max_me: the client shows it as a max hit, or as an ordinary hit with "Max hit hitsplats" off. */
    const val DAMAGE_MAX_ME = 43
}
