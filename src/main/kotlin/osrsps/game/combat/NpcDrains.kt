package osrsps.game.combat

import osrsps.game.NpcStat
import osrsps.game.npc.Npc

/**
 * Drained monster stats (OSRS Wiki, Temporary skill drain and Dragon warhammer, read 2026-10-30): a drain lowers a
 * monster's level of a stat, and its combat rolls use the level it has now ([Npc.level]). A drain by a percentage takes
 * that much of the level it has now, rounded down, so drains stack ("each calculated from the current temporary
 * level"). Stats come back one level every 60 seconds ("Stats are restored at a rate of 1 per 60 seconds", which the
 * Wiki says holds for monsters by default). A monster's drain cap ([NpcCombatDef.drainCaps]) is the most of its level
 * drains can take: The Fractured Archive's bosses' Defence can lose 60% at most (the 21 October hotfix).
 *
 * Guessed: that a level can be drained to 0 where no cap says otherwise; that one timer brings every drained stat back
 * a level at a time, started by the first drain; that a cap rounds the levels it keeps up; and that a respawned
 * monster starts undrained (a new [Npc]). Not here yet: the drains themselves (special attacks, step 8; the curse
 * spells), monsters immune to drains, and stats raised or restored by a boss's phases.
 */
class NpcDrains(
    /** What an NPC fights with, for its caps; null for one that can't be fought. */
    private val npcCombat: (Npc) -> NpcCombatDef?,
) {
    /**
     * Drains [npc]'s [stat] by [levels], as far as its cap lets it; returns the levels drained. Nothing happens to a dead
     * NPC or one that can't be fought.
     */
    fun drain(npc: Npc, stat: NpcStat, levels: Int): Int {
        require(levels >= 0) { "A drain of $levels levels" }
        val def = npcCombat(npc) ?: return 0
        if (npc.dead) return 0
        val base = npc.type.stats[stat]
        val cap = def.drainCaps[stat] ?: 100
        // The levels the cap keeps: 40% of 75 is 30, and of 76 is 31 (rounded up, guessed).
        val floor = base - base * cap / 100
        val drained = minOf(levels, npc.level(stat) - floor).coerceAtLeast(0)
        if (drained == 0) return 0
        // The timer starts at the next [process], which may run later this tick, so the minute isn't a tick short.
        npc.drained[stat.ordinal] += drained
        return drained
    }

    /** Drains [percent] of [npc]'s [stat] as it is now, rounded down, as far as its cap lets it; returns the levels drained. */
    fun drainPercent(npc: Npc, stat: NpcStat, percent: Int): Int {
        require(percent in 0..100) { "A drain of $percent%" }
        return drain(npc, stat, npc.level(stat) * percent / 100)
    }

    /**
     * Counts [npc]'s restore timer down by a tick: each [RESTORE_TICKS], every drained stat comes back a level. On the
     * tick of its first drain the timer starts at [RESTORE_TICKS] without counting down, as [Poison]'s does, so the
     * first level comes back a full minute after the drain.
     */
    fun process(npc: Npc) {
        if (npc.dead) return
        if (npc.drainRestoreTicks < 0) {
            if (npc.drained.any { it > 0 }) npc.drainRestoreTicks = RESTORE_TICKS
            return
        }
        if (--npc.drainRestoreTicks > 0) return
        for (i in npc.drained.indices) if (npc.drained[i] > 0) npc.drained[i]--
        npc.drainRestoreTicks = if (npc.drained.any { it > 0 }) RESTORE_TICKS else -1
    }

    /** What [npc]'s drained stats are, for `::npcs`; null with none drained. */
    fun describe(npc: Npc): String? {
        val drained = NpcStat.entries.filter { npc.drained[it.ordinal] > 0 }
        if (drained.isEmpty()) return null
        return "drained " + drained.joinToString { "${it.name.lowercase()} ${npc.level(it)}/${npc.type.stats[it]}" } +
            ", a level back in ${ticksToRestore(npc)} ticks"
    }

    /** Ticks until [npc]'s next level comes back; a full [RESTORE_TICKS] before its timer has started. */
    private fun ticksToRestore(npc: Npc): Int = if (npc.drainRestoreTicks < 0) RESTORE_TICKS else npc.drainRestoreTicks

    companion object {
        /** A level back each minute. */
        const val RESTORE_TICKS = 100
    }
}
