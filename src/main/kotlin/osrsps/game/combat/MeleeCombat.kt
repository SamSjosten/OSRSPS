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

private val logger = KotlinLogging.logger {}

/** A player fighting [npc] with melee: they walk up to it and swing until one of them is gone. See [MeleeCombat]. */
class MeleeInteraction internal constructor(val npc: Npc, private val owner: MeleeCombat) : Interaction {
    /** Where the NPC stood when the player's route to it was last made. */
    internal var routedTo: Tile? = null

    /** Whether the player stood in reach of the NPC when last tried. */
    internal var inReach = false

    override fun tryInteract(player: Player) = owner.tryInteract(player, this)

    override fun unreachable(player: Player) = owner.unreachable(player)

    override fun holding(player: Player) = inReach
}

/**
 * Players attacking NPCs with melee. An Attack option becomes a [MeleeInteraction]: each movement phase the player
 * chases the NPC as for any NPC option (see [osrsps.game.npc.NpcInteractions]) and, once beside it, swings every
 * weapon-speed ticks. A swing rolls accuracy then damage ([CombatMath]), shows the hitsplat and health bar to everyone,
 * and gives XP by the attack style (OSRS Wiki, Combat). The NPC turns to its attacker and stands still while fought.
 *
 * From the Wiki: melee damage lands on the tick it is rolled; an NPC with no hitpoints left dies and comes back at its
 * spawn after its respawn time (never before its body has gone); in a single-way area like Lumbridge an NPC fighting another player says "Someone else
 * is fighting that." Guessed and marked below: how long an NPC counts as fighting after its last hit, how long its
 * body stays, and that the respawn time counts from the killing blow. NPCs hitting back is the next step of the combat
 * piece, so for now they only take it.
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
    /** What the player is told and shown; tests swap these for recorders. */
    private val message: (Player, String) -> Unit = Player::message,
    private val face: (Player, Npc) -> Unit = { _, _ -> },
    private val stopFacing: (Player) -> Unit = {},
) {
    private class Dying(val npc: Npc, val removeTick: Long, val respawnTick: Long)

    internal fun unreachable(player: Player) = stopFacing(player)

    private val dying = ArrayList<Dying>()

    /**
     * The player used Attack on [npc]. Returns false when the NPC has no combat definition, so the click falls through
     * to whatever else handles it. With a ranged or magic style the player is told melee is all there is for now.
     */
    fun attack(player: Player, npc: Npc): Boolean {
        combat.npcCombat[npc.type] ?: return false
        if (combat.meleeType(combat.style(player)) == null) {
            message(player, MELEE_ONLY)
            return true
        }
        if (foughtByAnother(player, npc)) {
            message(player, SOMEONE_ELSE)
            return true
        }
        val fight = MeleeInteraction(npc, this)
        player.interaction = fight
        // Beside it already (the click is handed over on arrival), so the first swing is this tick's.
        if (tryInteract(player, fight)) player.interaction = null
        return true
    }

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
        val tick = currentTick()
        if (tick >= player.nextAttackTick) swing(player, npc, tick)
        return false
    }

    /**
     * Whether another player hit [npc] within the last [IN_COMBAT_TICKS] ticks and is still here. Guessed: the Wiki
     * says a single-way NPC stays someone's for a while after their last hit, but not how long.
     */
    private fun foughtByAnother(player: Player, npc: Npc): Boolean {
        val other = npc.attacker ?: return false
        return other !== player && currentTick() - npc.attackedTick <= IN_COMBAT_TICKS && !other.loggedOut
    }

    private fun swing(player: Player, npc: Npc, tick: Long) {
        val style = combat.style(player)
        val matchup = combat.matchup(player, npc) ?: run {
            // The style changed to ranged or magic on the way (the combat tab is open while walking).
            message(player, MELEE_ONLY)
            player.interaction = null
            stopFacing(player)
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
     * the hitsplat and health bar for everyone, its block animation, or its death. Returns the damage dealt.
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
            if (def.anims.defend >= 0) npc.effects.anim(def.anims.defend)
        } else {
            die(npc, def, tick)
        }
        return dealt
    }

    private fun die(npc: Npc, def: NpcCombatDef, tick: Long) {
        npc.dead = true
        if (def.anims.death >= 0) npc.effects.anim(def.anims.death)
        // The body goes before the replacement comes, however short the respawn time (a rat's is 2 ticks).
        val respawnTick = tick + maxOf(def.respawnTicks, DEATH_TICKS)
        dying += Dying(npc, tick + DEATH_TICKS, respawnTick)
        logger.debug { "$npc died; back on tick $respawnTick" }
    }

    /** Takes out the NPCs whose death is over and puts back those whose respawn time is up. Run in the NPC phase. */
    fun process() {
        val tick = currentTick()
        val iterator = dying.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (tick >= entry.removeTick && npcs[entry.npc.index] === entry.npc) removeNpc(entry.npc)
            if (tick >= entry.respawnTick) {
                // Never before the body has gone: a respawn no shorter than DEATH_TICKS and the removal above see to it.
                check(npcs[entry.npc.index] !== entry.npc) { "$entry.npc respawning with its body still there" }
                iterator.remove()
                spawnNpc(entry.npc.spawn)
            }
        }
    }

    /** The NPCs dead and waiting to come back, for tests and commands. */
    val respawning: Int get() = dying.size

    companion object {
        const val SOMEONE_ELSE = "Someone else is fighting that."

        /** Not a live message: ranged and magic come with later pieces. */
        const val MELEE_ONLY = "Only melee is in so far; ranged and magic come later."

        /**
         * Ticks an NPC counts as fighting its last attacker, and stands still, after their last hit. Guessed; the
         * Wiki gives no number.
         */
        const val IN_COMBAT_TICKS = 8

        /** Ticks a dead NPC's body stays before it vanishes. Guessed; the death animations are a few seconds. */
        const val DEATH_TICKS = 4

        /** The headbar NPCs show their health on: id 0, 30 pixels wide (cache config 33, rev 241). */
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
