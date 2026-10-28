package osrsps.game.combat

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.GameRandom
import osrsps.game.Interaction
import osrsps.game.Movement
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcEffects
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.map.CollisionMap
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
 * gone, dead, on another level, further than [CHASE_RANGE] from its spawn, or fought by another NPC. A player hit
 * with auto retaliate on fights back too (OSRS Wiki, Auto Retaliate: one tick after the hit, with their attack delay
 * set to half their weapon's speed, rounded up). Lumbridge is single-way: an NPC another player hit recently says
 * "Someone else is fighting that.", and a player another NPC is hitting can't attack a second one ("I'm already under
 * attack."). Hitpoints come back one a minute (Wiki, Hitpoints) for players and NPCs alike.
 *
 * From the Wiki: melee damage lands on the tick it is rolled; an NPC with no hitpoints left dies and comes back at its
 * spawn after its respawn time. Guessed and marked below: how long an NPC counts as fighting after its last hit, how
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
    /** A fight with a style melee doesn't fight with, or null for none: [RangedCombat.fight]. */
    private val ranged: (Player, Npc) -> Interaction? = { _, _ -> null },
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
        val fight = fight(player, npc)
        if (fight == null) {
            message(player, MELEE_ONLY)
            return true
        }
        if (underAttackByAnother(player, npc)) {
            message(player, ALREADY_UNDER_ATTACK)
            return true
        }
        if (foughtByAnother(player, npc)) {
            message(player, SOMEONE_ELSE)
            return true
        }
        player.interaction = fight
        // Beside it already: the click is handed over on arrival, so the first swing is this tick's.
        if (now && fight.tryInteract(player) && player.interaction === fight) player.interaction = null
        return true
    }

    /** A fight with [npc] in the player's style: melee here, ranged from [ranged]; null when nothing fights with it. */
    fun fight(player: Player, npc: Npc): Interaction? =
        if (combat.meleeType(combat.style(player)) != null) MeleeInteraction(npc, this) else ranged(player, npc)

    internal fun tryInteract(player: Player, interaction: MeleeInteraction): Boolean {
        val npc = interaction.npc
        if (npcs[npc.index] !== npc || npc.level != player.level || npc.dead) {
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
            message(player, SOMEONE_ELSE)
            stopFacing(player)
            return true
        }
        // Another NPC may have caught them on the way; once the fight is on, a newcomer's hits don't stop it.
        if (!interaction.swung && underAttackByAnother(player, npc)) {
            message(player, ALREADY_UNDER_ATTACK)
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
     * Whether another player hit [npc] within the last [IN_COMBAT_TICKS] ticks and is still here. Guessed: the Wiki
     * says a single-way NPC stays someone's for a while after their last hit, but not how long.
     */
    internal fun foughtByAnother(player: Player, npc: Npc): Boolean {
        val other = npc.attacker ?: return false
        return other !== player && currentTick() - npc.attackedTick <= IN_COMBAT_TICKS && !other.loggedOut
    }

    /** Whether an NPC other than [npc] is fighting [player] and hit them within the last [IN_COMBAT_TICKS] ticks. */
    internal fun underAttackByAnother(player: Player, npc: Npc): Boolean {
        val other = player.attackedBy ?: return false
        return other !== npc && other.target === player && !other.dead && currentTick() - player.attackedTick <= IN_COMBAT_TICKS
    }

    private fun swing(player: Player, npc: Npc, tick: Long) {
        val style = combat.style(player)
        val matchup = combat.matchup(player, npc) ?: run {
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
        player.effects.anim(combat.anims(player).attack(style), 0)
        face(player, npc)
        // Accuracy first, then damage, as the Wiki describes a hit; the attack's random draws are always these two.
        val damage = if (CombatMath.rollHits(random, matchup.attackRoll, matchup.defenceRoll)) CombatMath.rollDamage(random, matchup.maxHit) else 0
        val dealt = hit(npc, player, damage, tick)
        // XP for the damage actually dealt, which a nearly dead NPC caps.
        for (skill in style.xp) player.skills.addTenths(skill, CombatMath.styleXpTenths(dealt, style.xp.size))
        player.skills.addTenths(Skill.HITPOINTS, CombatMath.hitpointsXpTenths(dealt))
    }

    /**
     * [npc] takes [damage] from [source] (null for none, as from a command), capped at what it has left, on [tick]:
     * the hitsplat and health bar for everyone, its block animation, or its death. A hit NPC turns on whoever hit it.
     * Returns the damage dealt.
     */
    fun hit(npc: Npc, source: Player?, damage: Int, tick: Long): Int {
        val def = combat.npcCombat[npc.type] ?: throw IllegalArgumentException("${npc.type.name} can't be fought")
        if (npc.dead) return 0
        val dealt = damage.coerceIn(0, npc.hitpoints)
        npc.hitpoints -= dealt
        val from = if (source != null) NpcEffects.PLAYER_SOURCE + source.index else -1
        if (dealt > 0) npc.effects.hit(from, HitsplatType.DAMAGE_ME, HitsplatType.DAMAGE_OTHER, dealt)
        else npc.effects.hit(from, HitsplatType.BLOCK_ME, HitsplatType.BLOCK_OTHER, 0)
        npc.effects.headBar(from, HEALTH_BAR, healthBarFill(npc.hitpoints, npc.type.stats.hitpoints))
        npc.attacker = source
        npc.attackedTick = tick
        npc.holdTicks = IN_COMBAT_TICKS
        if (source != null) npc.face(source)
        if (npc.hitpoints > 0) {
            // Its own swing this tick wins over the block (guessed).
            if (def.anims.defend >= 0 && npc.effects.anim == null) npc.effects.anim(def.anims.defend)
            if (source != null) retaliate(npc, source, tick)
        } else {
            die(npc, def, source, tick)
        }
        return dealt
    }

    /** [npc] was hit by [player]: it turns on them, striking from the next tick (guessed) if it isn't fighting already. */
    private fun retaliate(npc: Npc, player: Player, tick: Long) {
        if (npc.target === player) return
        npc.target = player
        npc.nextAttackTick = maxOf(npc.nextAttackTick, tick + 1)
        npcs.follow(npc, player)
    }

    private fun die(npc: Npc, def: NpcCombatDef, killer: Player?, tick: Long) {
        npc.dead = true
        giveUp(npc)
        if (def.anims.death >= 0) npc.effects.anim(def.anims.death)
        // The body goes before the replacement comes, however short the respawn time (a rat's is 2 ticks).
        val respawnTick = tick + maxOf(def.respawnTicks, DEATH_TICKS)
        dying += Dying(npc, killer, tick + DEATH_TICKS, respawnTick)
        logger.debug { "$npc died; back on tick $respawnTick" }
    }

    /** [npc] stops fighting whoever it fought and goes back to what it did. */
    private fun giveUp(npc: Npc) {
        if (npc.target == null) return
        npc.target = null
        npcs.stopFollowing(npc)
    }

    /**
     * The NPC phase's combat: every fighting NPC chases and hits its target or gives up, the NPCs whose death is over
     * are taken out with their drops put down, those whose respawn time is up put back, and once a minute everyone
     * heals a point.
     */
    fun process() {
        val tick = currentTick()
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
                spawnNpc(entry.npc.spawn)
            }
        }
        for (npc in npcs.all) {
            val target = npc.target ?: continue
            fightBack(npc, target, tick)
        }
        if (tick % REGEN_TICKS == 0L) regenerate()
    }

    private fun fightBack(npc: Npc, player: Player, tick: Long) {
        val def = combat.npcCombat[npc.type] ?: return giveUp(npc)
        if (npc.dead) return
        if (player.loggedOut || player.level != npc.level || player.hitpoints <= 0 || outOfRange(npc, player) || underAttackByAnother(player, npc)) {
            giveUp(npc)
            return
        }
        // Chase's own far limit may have let go of them; a fighting NPC keeps after them while they are in range.
        if (npc.following !== player) npcs.follow(npc, player)
        val size = npc.type.size
        if (!Reach.reachedEntity(collision, npc.level, npc.x, npc.z, size, player.x, player.z, 1, 1)) return
        if (tick < npc.nextAttackTick) return
        npc.nextAttackTick = tick + def.attackSpeed
        if (def.anims.attack >= 0) npc.effects.anim(def.anims.attack)
        npc.face(player)
        val roll = combat.npcAttack(npc, player) ?: return
        val damage = if (CombatMath.rollHits(random, roll.attackRoll, roll.defenceRoll)) CombatMath.rollDamage(random, roll.maxHit) else 0
        hitPlayer(player, npc, damage, tick)
    }

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
        } else if (source != null && player.autoRetaliate && player.interaction == null && !player.modalOpen) {
            retaliate(player, source, tick)
        }
        return dealt
    }

    /**
     * Auto retaliate: [player] turns on [npc], which hit them on [tick]. OSRS Wiki, Auto Retaliate: the character
     * retaliates one tick after being attacked, with the attack delay set to the weapon's speed halved and rounded up
     * whatever it was. The fight starts now and the first swing comes with that delay.
     */
    private fun retaliate(player: Player, npc: Npc, tick: Long) {
        if (foughtByAnother(player, npc)) return
        player.interaction = fight(player, npc) ?: return
        player.nextAttackTick = tick + (combat.attackSpeed(player) + 1) / 2
    }

    /** "Oh dear, you are dead!": every fight with the player ends, and [Deaths] has them from here. */
    private fun died(player: Player) {
        message(player, DEAD)
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

    /** A hitpoint back for every hurt player and NPC; OSRS Wiki, Hitpoints: one a minute. Guessed for NPCs. */
    private fun regenerate() {
        for (player in players()) {
            val max = player.skills.baseLevel(Skill.HITPOINTS)
            if (player.hitpoints in 1 until max) player.hitpoints++
        }
        for (npc in npcs.all) {
            if (!npc.dead && npc.hitpoints < npc.type.stats.hitpoints && combat.npcCombat[npc.type] != null) npc.hitpoints++
        }
    }

    /** The NPCs dead and waiting to come back, for tests and commands. */
    val respawning: Int get() = dying.size

    companion object {
        const val SOMEONE_ELSE = "Someone else is fighting that."
        const val ALREADY_UNDER_ATTACK = "I'm already under attack."
        const val DEAD = "Oh dear, you are dead!"

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

        /** Ticks between hitpoints coming back: one a minute (OSRS Wiki, Hitpoints). */
        const val REGEN_TICKS = 100

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
}
