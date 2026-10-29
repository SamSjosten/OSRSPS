package osrsps.game.skill

import osrsps.game.Player

/** Where a gathering action is in its cycle on this tick; see [SkillClock.step]. */
enum class ClockStep {
    /** The clock had run out: it was set again, and the first roll is [SkillClock.step]'s cycle less one tick away. */
    STARTED,

    /** Between rolls. */
    WAITING,

    /** A roll is due this tick. */
    DUE,
}

/**
 * The skill clock gathering actions share: one tick number per player ([Player.actionDelay]) that says when the next roll
 * is due, as RS2's `%action_delay` does in the 2004Scape project's woodcutting, mining and fishing scripts. An action
 * re-runs every tick (see [osrsps.game.World.repeatOp]) and asks [step] what to do.
 *
 * A clock that hasn't run out isn't set again, so clicking the same tree again, or another rock, keeps the rolls on the
 * same ticks; and anything else that sets the clock (tick manipulation on live) moves them. That much is the RS2 model;
 * whether OSRS keeps the same shared clock is inferred from tick manipulation working on live, not measured.
 */
object SkillClock {
    /**
     * Moves [player]'s clock for an action that rolls every [cycle] ticks. When the clock has run out it is set to roll
     * [cycle] - 1 ticks from now ([ClockStep.STARTED]): with the re-run on the tick after the roll finding it run out
     * again, that gives one roll every [cycle] ticks (the Wiki's 4 for woodcutting).
     */
    fun step(player: Player, tick: Long, cycle: Int): ClockStep {
        require(cycle >= 1) { "A cycle is at least one tick, not $cycle" }
        if (player.actionDelay < tick) {
            player.actionDelay = tick + cycle - 1
            // A one-tick cycle rolls straight away.
            return if (cycle == 1) ClockStep.DUE else ClockStep.STARTED
        }
        return if (player.actionDelay == tick) ClockStep.DUE else ClockStep.WAITING
    }
}
