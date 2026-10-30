package osrsps.game.combat

import osrsps.game.Player
import osrsps.game.npc.Npc

/**
 * Stuns on a player (OSRS Wiki, Stun (status) and Pickpocketing, read 2026-10-30): a stunned player can't move or
 * attack, with yellow birds circling their head until it ends. Pickpocketing's stun lasts nine ticks and blocks another
 * pickpocket for eight more (that wait is the thieving code's); eating while stunned is allowed, as the Wiki's
 * pickpocketing page says it only pushes the next pickpocket back. [stun] stops what the player was doing and
 * [isStunned] is what the walk, option and retaliation code checks.
 *
 * Guessed: that a stunned player's clicks on the map, NPCs, locs, objs on the ground and other players are dropped, not
 * held for when the stun ends; that they don't retaliate; that eating, drinking, equipping, prayers, interfaces and
 * commands all work; that a stun can't be stunned over (a second one while stunned does nothing, as for a freeze); the
 * birds' height; and that dying ends a stun. Not here yet: stuns on NPCs (blackjacks, Barbarian Assault), and stuns that
 * also stop eating and teleporting (Kamil, Karamel).
 */
class StatusEffects(
    private val currentTick: () -> Long,
    /** Ends what [Player] was doing: their route, their interaction (a fight included) and a follow. */
    private val stop: (Player) -> Unit = {},
    /** [Player] takes a hit of this much from the NPC ([MeleeCombat.hitPlayer]), which can kill. */
    private val hit: (Player, Npc?, Int) -> Unit = { _, _, _ -> },
) {
    /**
     * Stuns [player] for [ticks] from this tick: they can't move or attack this tick or the [ticks] - 1 after. Shows
     * [spotanim] (the plain birds by default; thieving has its own) and, with [damage] over 0, deals that hit from
     * [source] after the stun, so it can't start a retaliation. False, and nothing happens, while they are stunned already.
     */
    fun stun(player: Player, ticks: Int, damage: Int = 0, source: Npc? = null, spotanim: Int = STUN_SPOTANIM): Boolean {
        require(ticks > 0) { "A stun lasts at least a tick, not $ticks" }
        val tick = currentTick()
        if (isStunned(player, tick)) return false
        player.stunnedUntil = tick + ticks
        stop(player)
        player.spotanim(spotanim, STUN_HEIGHT)
        if (damage > 0) hit(player, source, damage)
        return true
    }

    /** Whether [player] is stunned on [tick]. */
    fun isStunned(player: Player, tick: Long = currentTick()): Boolean = tick < player.stunnedUntil

    /** Ends [player]'s stun: death, or a scenario starting. */
    fun clear(player: Player) {
        player.stunnedUntil = 0
    }

    /** What [player]'s stun is, for `::stun`. */
    fun describe(player: Player): String {
        val left = player.stunnedUntil - currentTick()
        return if (left > 0) "Stunned for $left more tick${if (left == 1L) "" else "s"}." else "Not stunned."
    }

    companion object {
        /** Pickpocketing's stun (Wiki: "prevents the player from moving for nine ticks"). */
        const val PICKPOCKET_TICKS = 9

        /** `stunned`, the plain circling birds; `stunned_thieving` is 245 (RuneLite's SpotanimID). */
        const val STUN_SPOTANIM = 80
        const val THIEVING_SPOTANIM = 245

        /** Guessed: about head height. */
        const val STUN_HEIGHT = 100
    }
}
