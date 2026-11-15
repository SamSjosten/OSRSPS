package osrsps.game.combat

import osrsps.game.Player
import osrsps.game.Wilderness
import osrsps.game.bank.setVarbitIfKnown
import osrsps.game.content.HomeTeleportLines
import osrsps.game.dialogue.Line

/**
 * Tele Block (step W1.8), and the one check every teleport asks before it starts ([refusal]). Verified on the OSRS Wiki
 * (Tele Block, read 2026-11-15): it lasts 5 minutes, halved to 2 minutes 30 seconds when the target had Protect from
 * Magic up as it landed; the target can't be blocked again while it lasts; killing the caster, leaving the Wilderness
 * (over the ditch or by dying) and logging out end it; forcing the caster out of the Wilderness doesn't. The cast itself
 * is a combat spell on a player ([PvpCombat]), which can splash.
 *
 * Varbit `teleblock_cycles` (4163 in the 242 cache) is what RuneLite's Timers plugin reads as `Varbits.TELEBLOCK`, the
 * ticks left plus [VARBIT_OFFSET] while blocked. Guessed: it is sent as the block starts and ends only, the client and
 * RuneLite counting down by themselves; and no immunity after a block ends (the Wiki says "briefly", with no length).
 * Not built: the 30-second warning and the expiry message, which the Wiki describes without their words.
 */
object Teleblock {
    /** 5 minutes. */
    const val TICKS = 500

    /** 2 minutes 30 seconds, with Protect from Magic up as it lands. */
    const val PROTECTED_TICKS = 250

    /** `teleblock_cycles`. */
    const val VARBIT = 4163

    /** What [VARBIT] holds over the ticks left: RuneLite shows a timer only for a value over it. */
    const val VARBIT_OFFSET = 100

    /**
     * The lines refusing any teleport of [player], standing at Wilderness level [level], or empty when they may: every
     * teleport stops above level 20 Wilderness ([Wilderness.TELEPORT_LIMIT]), and all of them while Tele Blocked.
     * Guessed: the level is told first when both hold.
     */
    fun refusal(player: Player, level: Int): List<Line> = when {
        level > Wilderness.TELEPORT_LIMIT -> listOf(HomeTeleportLines.WILDERNESS, HomeTeleportLines.WILDERNESS_2)
        player.teleblockTicks > 0 -> listOf(PvpLines.TELEPORT_BLOCKED)
        else -> emptyList()
    }

    /** [player] is Tele Blocked by [caster] for [ticks]. */
    fun block(player: Player, caster: Player, ticks: Int) {
        player.teleblockTicks = ticks
        player.teleblockedBy = caster.saveKey
        player.vars.setVarbitIfKnown(VARBIT, ticks + VARBIT_OFFSET)
    }

    /** [player]'s Tele Block, if any, is over. */
    fun clear(player: Player) {
        if (player.teleblockTicks == 0 && player.teleblockedBy == null) return
        player.teleblockTicks = 0
        player.teleblockedBy = null
        player.vars.setVarbitIfKnown(VARBIT, 0)
    }

    /** At the start of the tick: one tick less, and none once [player] is out of the Wilderness, however they left. */
    fun countDown(player: Player) {
        if (player.teleblockTicks <= 0) return
        if (!Wilderness.inside(player.x, player.z) || player.teleblockTicks == 1) clear(player)
        else player.teleblockTicks--
    }
}
