package osrsps.game.combat

import osrsps.game.Player
import osrsps.game.Skill

/** A player's stat timers, counted up each tick by [StatRestore]; not saved, so each starts again at login. */
class StatTimers {
    /** Ticks into the combat skills' boost cycle. */
    var combatBoost = 0

    /** Ticks into the other skills' boost cycle. */
    var skillBoost = 0

    /** Ticks into the drained stats' restore cycle. */
    var restore = 0

    /** Ticks into Rapid Restore's own cycle, 0 while it is off. */
    var rapidRestore = 0

    /** Ticks Preserve has been on for in a row, 0 while it is off. */
    var preserve = 0
}

/**
 * Boosted and drained stats going back to their base levels (OSRS Wiki, Temporary skill boost, Rapid Restore and
 * Preserve, read 2026-10-29). A boosted stat loses a level a minute ([CYCLE_TICKS]), on a cycle that runs in the
 * background from login rather than from the boost, one for the combat skills and another for the rest. Preserve, once
 * on for 15 seconds ([PRESERVE_DELAY]), makes a cycle a minute and a half ([PRESERVE_CYCLE_TICKS]). A drained stat gets a
 * level back a minute; Rapid Restore adds a second minute-long cycle of its own, counted from when it is turned on, for
 * every drained stat but Hitpoints and Prayer. Hitpoints below the level come back by [MeleeCombat]'s regeneration, and
 * prayer points only at an altar or from potions.
 *
 * Guessed and not checked on live: which skills are the combat ones (Attack, Strength, Defence, Ranged, Magic,
 * Hitpoints and Prayer); that boosted Hitpoints and Prayer go down on the combat cycle, Preserve included; that a
 * cycle Preserve has already lengthened ends as soon as Preserve goes off; that drained stats share one restore cycle,
 * separate from the boost cycles and not lengthened by Preserve; and where in the tick it happens (with the prayer
 * drain, after the NPCs' attacks). Divine potions' fixed five minutes come with the potions, step 6c.
 */
class StatRestore(private val hasPrayer: (Player, String) -> Boolean = { _, _ -> false }) {
    /** Counts [player]'s timers on by a tick, moving stats a level toward their base where a cycle ends. */
    fun process(player: Player) {
        if (player.dead) return
        val timers = player.statTimers
        timers.preserve = if (hasPrayer(player, Prayers.PRESERVE)) timers.preserve + 1 else 0
        val boostCycle = if (timers.preserve >= PRESERVE_DELAY) PRESERVE_CYCLE_TICKS else CYCLE_TICKS
        if (++timers.combatBoost >= boostCycle) {
            timers.combatBoost = 0
            for (skill in COMBAT) decay(player, skill)
        }
        if (++timers.skillBoost >= boostCycle) {
            timers.skillBoost = 0
            for (skill in Skill.entries) if (skill !in COMBAT) decay(player, skill)
        }
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

        /** A minute and a half, with Preserve. */
        const val PRESERVE_CYCLE_TICKS = 150

        /** Preserve counts once it has been on for 15 seconds. */
        const val PRESERVE_DELAY = 25

        val COMBAT = setOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC, Skill.HITPOINTS, Skill.PRAYER)
    }
}
