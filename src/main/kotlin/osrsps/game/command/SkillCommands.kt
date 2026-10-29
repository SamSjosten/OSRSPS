package osrsps.game.command

import osrsps.game.Experience
import osrsps.game.PlayerSkills
import osrsps.game.Skill
import osrsps.game.combat.StatRestore
import java.math.BigDecimal
import java.util.Locale

/** Set the typing player's levels and XP, and show their boosts and drains. The client is sent any change at the end of
 * the tick. */
/** [inTick]: whether a tick is being run, for `::statcycles`; see [osrsps.game.combat.BoostCycle.restart]. */
fun CommandRegistry.registerSkillCommands(inTick: () -> Boolean = { true }) {
    register("setlevel", "::setlevel skill|all level") {
        if (args.size != 2) fail("Usage: ::setlevel skill|all level")
        val level = args[1].toIntOrNull() ?: fail("Usage: ::setlevel skill|all level ('${args[1]}' is not a number)")
        if (level !in 1..Experience.MAX_LEVEL) fail("Levels are 1 to ${Experience.MAX_LEVEL}")
        val skills = player.skills
        val combatBefore = skills.combatLevel
        val targets = if (args[0].equals("all", ignoreCase = true)) Skill.entries else listOf(skill(args[0]))
        for (skill in targets) skills.setLevel(skill, level)
        val what = targets.singleOrNull()?.displayName ?: "Every skill"
        reply("$what set to level $level (${format(Experience.xpForLevel(level))} xp).${combatChange(combatBefore, skills)}")
    }

    register("addxp", "::addxp skill xp") {
        if (args.size != 2) fail("Usage: ::addxp skill xp")
        val skill = skill(args[0])
        val tenths = tenths(args[1]) ?: fail("Usage: ::addxp skill xp (a number with at most one decimal place)")
        val skills = player.skills
        val combatBefore = skills.combatLevel
        val levelBefore = skills.baseLevel(skill)
        skills.addTenths(skill, tenths)
        val levels = if (skills.baseLevel(skill) == levelBefore) "level $levelBefore" else "level $levelBefore -> ${skills.baseLevel(skill)}"
        reply("${skill.displayName}: ${format(skills.xp(skill))} xp, $levels.${combatChange(combatBefore, skills)}")
    }

    register("boost", "::boost skill level") {
        if (args.size != 2) fail("Usage: ::boost skill level")
        val skill = skill(args[0])
        val level = args[1].toIntOrNull() ?: fail("Usage: ::boost skill level ('${args[1]}' is not a number)")
        if (level !in 0..PlayerSkills.MAX_CURRENT_LEVEL) fail("Current levels are 0 to ${PlayerSkills.MAX_CURRENT_LEVEL}")
        player.skills.setCurrentLevel(skill, level)
        reply("${skill.displayName} is now $level/${player.skills.baseLevel(skill)}")
    }

    register("stats", "::stats", admin = false) {
        val skills = player.skills
        val changed = Skill.entries.filter { skills.currentLevel(it) != skills.baseLevel(it) }
        val timers = player.statTimers
        val preserved = timers.preserve >= StatRestore.PRESERVE_DELAY
        val cycles = "next combat boost level in ${StatRestore.ticksLeft(timers.combatBoost, preserved)} ticks, other boosts " +
            "${StatRestore.ticksLeft(timers.skillBoost, preserved)}, hitpoints ${StatRestore.ticksLeft(timers.hitpoints, preserved)}, " +
            "drains ${StatRestore.CYCLE_TICKS - timers.restore}" +
            if (timers.preserve > 0) "; Preserve on for ${timers.preserve} ticks" else ""
        val levels = if (changed.isEmpty()) "Every stat is at its level" else changed.joinToString(", ") {
            "${it.displayName} ${skills.currentLevel(it)}/${skills.baseLevel(it)}"
        }
        reply("$levels; $cycles")
    }

    register("statcycles", "::statcycles") {
        val timers = player.statTimers
        val during = inTick()
        for (cycle in listOf(timers.combatBoost, timers.skillBoost, timers.hitpoints)) cycle.restart(during)
        timers.restore = if (during) -1 else 0
        // Rapid Restore's own cycle too, while it is on; off, StatRestore keeps it at 0.
        timers.rapidRestore = timers.restore
        reply("Boost and drain cycles started again: the next levels in ${StatRestore.CYCLE_TICKS} ticks")
    }
}

private fun CommandContext.skill(text: String): Skill = Skill.find(text) ?: fail("No skill '$text'")

/** "17.5" to 175 tenths; null for anything negative, over 200M or with more than one decimal place. */
private fun tenths(text: String): Int? {
    val value = text.toBigDecimalOrNull() ?: return null
    if (value.signum() < 0 || value.stripTrailingZeros().scale() > 1) return null
    val tenths = value.multiply(BigDecimal.TEN)
    return if (tenths > BigDecimal(Experience.MAX_TENTHS)) null else tenths.intValueExact()
}

private fun combatChange(before: Int, skills: PlayerSkills): String {
    val after = skills.combatLevel
    return if (after == before) "" else " Combat level $before -> $after."
}

private fun format(n: Int): String = String.format(Locale.ROOT, "%,d", n)
