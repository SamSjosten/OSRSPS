package osrsps.game.encounter

import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.game.npc.Npc
import osrsps.map.Tile

/**
 * A boss fight as content writes it: [boss] fought through [phases] in order, from the first. Each boss of the raid is
 * one of these, and [Encounters] runs it a tick at a time. [name] is what `::raid` takes, one word.
 */
class EncounterDef(val name: String, val boss: NpcType, val phases: List<PhaseDef>) {
    init {
        require(name.isNotEmpty() && name.none(Char::isWhitespace)) { "Bad encounter name '$name'" }
        require(phases.isNotEmpty()) { "$name has no phases" }
        require(phases.map { it.name }.toSet().size == phases.size) { "$name has two phases with the same name" }
    }

    fun phase(name: String): Int? = phases.indexOfFirst { it.name.equals(name, ignoreCase = true) }.takeIf { it >= 0 }
}

/**
 * One phase of an [EncounterDef]: [onStart] when it begins, then its [cycle] over and over, each step [CycleStep.delay]
 * ticks after the one before, until the boss's hitpoints are at or below [endsAtPercent] of its maximum, or [endsAfter]
 * ticks have passed, when the next phase begins. The last phase lasts until the fight ends.
 */
class PhaseDef(
    val name: String,
    val cycle: List<CycleStep> = emptyList(),
    /** The share of the boss's hitpoints, in percent, at or below which the next phase begins; null for none. */
    val endsAtPercent: Int? = null,
    /** The ticks after which the next phase begins; null for none. */
    val endsAfter: Int? = null,
    val onStart: EncounterContext.() -> Unit = {},
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

    /** The adds that are still in. */
    val adds: List<Npc>

    /** Writes [text] to the fight's log, which tests and `::encounter` read. */
    fun log(text: String)
}
