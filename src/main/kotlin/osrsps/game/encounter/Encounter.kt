package osrsps.game.encounter

import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.npc.Npc
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/**
 * A boss fight as content writes it: [boss] fought through [phases] in order, from the first. Each boss of the raid is
 * one of these, and [Encounters] runs it a tick at a time. [name] is what `::raid` takes, one word. With
 * [scriptedAttacks] the boss attacks only as its phases' steps say ([Npc.scriptedAttacks]), never by itself. [status]
 * says what `::raid` shows of the fight's NPCs; without it, the boss's hitpoints (a fight whose [boss] is only its
 * anchor, as Bork and the Smorks' is, shows its two). [held] says which of the fight's NPCs `::raid hold` freezes;
 * without it, the boss (such a fight names the one that walks). For a raid room's points, damage to [boss] and to NPCs
 * of the types in [bosses] counts in full, and damage to every other NPC of the fight (its adds) only up to a cap
 * ([osrsps.game.raid.ArchiveRaids.MINION_CAP_PERCENT]).
 */
class EncounterDef(
    val name: String,
    val boss: NpcType,
    val phases: List<PhaseDef>,
    val scriptedAttacks: Boolean = false,
    val status: (EncounterContext.() -> String)? = null,
    val held: (EncounterContext.() -> List<Npc>)? = null,
    val bosses: Set<Int> = emptySet(),
) {
    init {
        require(name.isNotEmpty() && name.none(Char::isWhitespace)) { "Bad encounter name '$name'" }
        require(phases.isNotEmpty()) { "$name has no phases" }
        require(phases.map { it.name }.toSet().size == phases.size) { "$name has two phases with the same name" }
    }

    fun phase(name: String): Int? = phases.indexOfFirst { it.name.equals(name, ignoreCase = true) }.takeIf { it >= 0 }
}

/**
 * One phase of an [EncounterDef]: [onStart] when it begins, then its [cycle] over and over, each step [CycleStep.delay]
 * ticks after the one before, until the boss's hitpoints are at or below [endsAtPercent] of its maximum, [endsAfter]
 * ticks have passed or [endsWhen] says so, when the next phase begins: the one after it, or the one [next] names (a
 * fight that goes round: a shield that comes back). The last phase lasts until the fight ends, unless it has a [next].
 */
class PhaseDef(
    val name: String,
    val cycle: List<CycleStep> = emptyList(),
    /** The share of the boss's hitpoints, in percent, at or below which the next phase begins; null for none. */
    val endsAtPercent: Int? = null,
    /** The ticks after which the next phase begins; null for none. */
    val endsAfter: Int? = null,
    val onStart: EncounterContext.() -> Unit = {},
    /** Whether the next phase begins now, asked each tick before the cycle; null for never. A shield broken, say. */
    val endsWhen: (EncounterContext.() -> Boolean)? = null,
    /**
     * The name of the phase that begins when this one ends, asked as it ends; null, or a null answer, for the one
     * after it in the list. It may name an earlier phase, or this one again.
     */
    val next: (EncounterContext.() -> String?)? = null,
) {
    init {
        require(endsAtPercent == null || endsAtPercent in 1..99) { "$name ends at $endsAtPercent%" }
        require(endsAfter == null || endsAfter > 0) { "$name ends after $endsAfter ticks" }
        require(cycle.isEmpty() || cycle.sumOf { it.delay } > 0) { "$name's cycle takes no time" }
    }
}

/** A step of a phase's attack cycle: [action], [delay] ticks after the step before it (or after the phase began). */
class CycleStep(val delay: Int, val action: EncounterContext.() -> Unit) {
    init {
        require(delay >= 0) { "a step of $delay ticks" }
    }
}

/**
 * Tiles that hurt whoever stands on them: shown with [warning] when made, then from [startTick] to [endTick] (both
 * included) dealing [minDamage] to [maxDamage] each tick to every player on one, with [spotanim] played on each tile
 * on [startTick].
 */
class Hazard internal constructor(
    val tiles: Set<Tile>,
    val level: Int,
    val startTick: Long,
    val endTick: Long,
    val minDamage: Int,
    val maxDamage: Int,
    val spotanim: Int,
)

/** What a running encounter gives its phases' steps: the boss, the players, and the ways to hurt them. */
interface EncounterContext {
    /** The world the fight is in, for what the steps below don't cover: the boss's swings and shots, its forms. */
    val world: World

    val boss: Npc

    /** Ticks since the current phase began; 0 on the tick it began. */
    val phaseTick: Long

    /** The players in the fight, alive, in [Player.index] order. */
    val players: List<Player>

    /** A number from [min] to [max], both included, from the world's random. */
    fun roll(min: Int, max: Int): Int

    /** One of [players], picked at random; null when there are none. */
    fun randomPlayer(): Player?

    /** [player] takes [damage] from the boss now, as a melee hit would land. Returns the damage dealt. */
    fun hit(player: Player, damage: Int): Int

    /**
     * Marks [tiles] with [warning] now, and hurts whoever stands on one with [minDamage] to [maxDamage] each tick from
     * [warnTicks] ticks on, for [ticks] ticks, with [spotanim] on each tile when they start; -1 for no spotanim.
     */
    fun hazard(tiles: Collection<Tile>, warning: Int, warnTicks: Int, ticks: Int, minDamage: Int, maxDamage: Int, spotanim: Int = -1): Hazard

    /** Puts in an add of [type] on [x], [z] on the boss's level, gone when the fight ends; null with no room for it. */
    fun spawnAdd(type: NpcType, x: Int, z: Int): Npc?

    /**
     * Puts a loc of type [id] (a centrepiece, turned [rotation] quarter turns clockwise from facing north) with its
     * south-west tile on [x], [z] on the boss's level, replacing what is on its layer there, until [clearLoc] or the
     * fight's end puts the tile back as the cache has it. What grows in the fight: a mushroom. A turned loc wider than
     * it is long blocks its length along x and its width along z.
     */
    fun placeLoc(id: Int, x: Int, z: Int, rotation: Int = 0): ZoneLoc

    /** Puts [loc]'s tile back as the cache has it, if [placeLoc] put it there and nothing has changed it since. */
    fun clearLoc(loc: ZoneLoc)

    /** The adds that are still in. */
    val adds: List<Npc>

    /**
     * Runs [action] [ticks] ticks from now (at least 1), in the fight's part of that tick after the cycle's steps and
     * before the hazards; never once the fight has ended. What is aimed now and lands later: a beam, a slam.
     */
    fun after(ticks: Int, action: EncounterContext.() -> Unit)

    /** Writes [text] to the fight's log, which tests and `::encounter` read. */
    fun log(text: String)
}
