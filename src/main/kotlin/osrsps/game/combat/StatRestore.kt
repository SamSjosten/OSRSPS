package osrsps.game.combat

import osrsps.game.Player
import osrsps.game.Skill

/** Where a boost cycle is: ticks into it, and whether Preserve has lengthened it for good. */
class BoostCycle {
    var ticks = 0

    /** Set once Preserve has carried the cycle into its last [StatRestore.PRESERVE_KEEP_TICKS]; turning Preserve off then doesn't shorten it. */
    var lengthened = false

    /**
     * Back to the cycle's start, from somewhere earlier in a tick than [StatRestore] (a login, a prayer clicked): that
     * tick's count brings it to 0, so the whole cycle comes after it, as the hitpoints countdown has it ([MeleeCombat]).
     * Between ticks ([inTick] false, a command on a paused clock) no count is to come, so it starts at 0.
     */
    fun restart(inTick: Boolean = true) {
        ticks = if (inTick) -1 else 0
        lengthened = false
    }
}

/**
 * A player's stat timers, counted up each tick by [StatRestore]. The two boost cycles' segments are saved (see
 * [StatRestore.segment]); the rest start again at login.
 */
class StatTimers {
    /** The combat skills' boost cycle. */
    val combatBoost = BoostCycle()

    /** The other skills' boost cycle. */
    val skillBoost = BoostCycle()

    /** Boosted Hitpoints' cycle, started again by turning Rapid Heal on and by logging in. */
    val hitpoints = BoostCycle()

    /** Ticks into the drained stats' restore cycle. */
    var restore = 0

    /** Ticks into Rapid Restore's own cycle, 0 while it is off. */
    var rapidRestore = 0

    /** Ticks Preserve has been on for in a row, 0 while it is off. */
    var preserve = 0
}

/**
 * Boosted and drained stats going back to their base levels (OSRS Wiki, Temporary skill boost, Rapid Restore and
 * Preserve, Hitpoints, read 2026-10-29). A boosted stat loses a level a minute ([CYCLE_TICKS]), on a cycle that runs in
 * the background from login rather than from the boost, one for the combat skills and another for the rest. A cycle is
 * five 12-second segments ([SEGMENT_TICKS]): logging out keeps the segment, which starts again at login. Boosted
 * Hitpoints have a cycle of their own, started again by turning Rapid Heal on and by logging in. Preserve, once on for
 * 15 seconds ([PRESERVE_DELAY]), makes a cycle a minute and a half ([PRESERVE_CYCLE_TICKS]), and can be turned off with
 * under 13 seconds to go without losing it. A drained stat gets a level back a minute; Rapid Restore adds a second
 * minute-long cycle of its own, counted from when it is turned on, for every drained stat but Hitpoints and Prayer.
 * Hitpoints below the level come back by [MeleeCombat]'s regeneration, and prayer points only at an altar or from potions.
 *
 * Guessed and not checked on live: which skills are the combat ones (Attack, Strength, Defence, Ranged, Magic and
 * Prayer); that boosted Prayer goes down on the combat cycle and boosted Hitpoints' cycle is lengthened by Preserve too;
 * that Preserve's "under 13 seconds" is the last 21 ticks counted from the end of the tick it was last on in, and turning
 * it off any earlier ends a cycle already past a minute at once; that a cycle saved past a minute, Preserve having lengthened it, comes back as its last segment; that
 * drained stats share one restore cycle, separate from the boost cycles, not lengthened by Preserve and not saved; and
 * where in the tick it happens (with the prayer drain, after the NPCs' attacks). Divine potions' fixed five minutes come
 * with the potions, step 6c.
 */
class StatRestore(private val hasPrayer: (Player, String) -> Boolean = { _, _ -> false }) {
    /** Counts [player]'s timers on by a tick, moving stats a level toward their base where a cycle ends. */
    fun process(player: Player) {
        if (player.dead) return
        val timers = player.statTimers
        timers.preserve = if (hasPrayer(player, Prayers.PRESERVE)) timers.preserve + 1 else 0
        val preserved = timers.preserve >= PRESERVE_DELAY
        if (advance(timers.combatBoost, preserved)) for (skill in COMBAT) decay(player, skill)
        if (advance(timers.skillBoost, preserved)) {
            for (skill in Skill.entries) if (skill !in COMBAT && skill != Skill.HITPOINTS) decay(player, skill)
        }
        if (advance(timers.hitpoints, preserved)) decay(player, Skill.HITPOINTS)
        if (++timers.restore >= CYCLE_TICKS) {
            timers.restore = 0
            restoreDrained(player)
        }
        if (!hasPrayer(player, Prayers.RAPID_RESTORE)) {
            timers.rapidRestore = 0
        } else if (++timers.rapidRestore >= CYCLE_TICKS) {
            timers.rapidRestore = 0
            restoreDrained(player)
        }
    }

    /** Counts [cycle] on by a tick; true when it ends, at a minute, or a minute and a half with Preserve. */
    private fun advance(cycle: BoostCycle, preserved: Boolean): Boolean {
        cycle.ticks++
        if (preserved && cycle.ticks >= PRESERVE_CYCLE_TICKS - PRESERVE_KEEP_TICKS) cycle.lengthened = true
        if (cycle.ticks < if (preserved || cycle.lengthened) PRESERVE_CYCLE_TICKS else CYCLE_TICKS) return false
        cycle.ticks = 0
        cycle.lengthened = false
        return true
    }

    private fun decay(player: Player, skill: Skill) {
        val current = player.skills.currentLevel(skill)
        if (current > player.skills.baseLevel(skill)) player.skills.setCurrentLevel(skill, current - 1)
    }

    private fun restoreDrained(player: Player) {
        for (skill in Skill.entries) {
            if (skill == Skill.HITPOINTS || skill == Skill.PRAYER) continue
            val current = player.skills.currentLevel(skill)
            if (current < player.skills.baseLevel(skill)) player.skills.setCurrentLevel(skill, current + 1)
        }
    }

    companion object {
        /** A minute. */
        const val CYCLE_TICKS = 100

        /** A cycle's segment, 12 seconds. */
        const val SEGMENT_TICKS = 20

        /** A minute and a half, with Preserve. */
        const val PRESERVE_CYCLE_TICKS = 150

        /** Under 13 seconds: Preserve on with this many ticks or fewer to go can be turned off without losing the 90 seconds. */
        const val PRESERVE_KEEP_TICKS = 21

        /** Preserve counts once it has been on for 15 seconds. */
        const val PRESERVE_DELAY = 25

        /** The skills on the combat boost cycle; Hitpoints has its own. */
        val COMBAT = setOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC, Skill.PRAYER)

        /** The segment [cycle] is in, for a save: one of a minute's five, the last for a cycle Preserve has carried past it. */
        fun segment(cycle: BoostCycle): Int = (cycle.ticks / SEGMENT_TICKS).coerceIn(0, CYCLE_TICKS / SEGMENT_TICKS - 1)

        /** Puts [cycle] at the start of a saved [segment], at login, in a tick's input or [inTick] not; see [BoostCycle.restart]. */
        fun resume(cycle: BoostCycle, segment: Int, inTick: Boolean = true) {
            cycle.restart(inTick)
            cycle.ticks += segment * SEGMENT_TICKS
        }

        /** The ticks left in [cycle], given whether Preserve counts now. */
        fun ticksLeft(cycle: BoostCycle, preserved: Boolean): Int =
            (if (preserved || cycle.lengthened) PRESERVE_CYCLE_TICKS else CYCLE_TICKS) - cycle.ticks
    }
}
