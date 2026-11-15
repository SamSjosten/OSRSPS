package osrsps.game.combat

import osrsps.game.Player
import osrsps.game.SettingsSide

/**
 * Skulls (Wilderness step W1.6). OSRS Wiki, Skull (status): a player who attacks another player unprovoked in the
 * Wilderness is skulled for 30 minutes, and the timer starts again whenever they start a fight with another player;
 * the player they attacked can fight back without a skull. The PK Skull Prevention setting (varbit
 * skull_prevent_enabled, the side panel's toggle) refuses an attack that would skull a player who has none.
 *
 * Inferred, as the Wiki gives no attack list: whoever was attacked unprovoked may hit their attacker back unskulled for
 * as long as the attacker's skull from that attack lasts, [TICKS]. The attacker keeps the list ([Player.attackedFirst]),
 * counted down with the skull and saved with it, so neither one's relog loses it. Guessed:
 * every attack on someone who never attacked first counts as starting a fight, so it starts the timer again; the timer
 * runs only while logged in, and is saved.
 */
object Skulls {
    /** 30 minutes (OSRS Wiki, Skull (status)). */
    const val TICKS = 3_000

    /** headicons_pk's white skull, the Wilderness's. */
    const val ICON = 0

    const val VARBIT_PREVENTION = SettingsSide.VARBIT_SKULL_PREVENTION

    /** Whether [attacker]'s attack on [target] on [tick] is unprovoked: [target] hasn't attacked them first, unskulled. */
    @Suppress("UNUSED_PARAMETER")
    fun unprovoked(attacker: Player, target: Player, tick: Long): Boolean =
        (target.attackedFirst[attacker.saveKey] ?: 0) <= 0

    /** Whether the PK Skull Prevention setting refuses [attacker]'s attack on [target]: it would give them a skull. */
    fun prevented(attacker: Player, target: Player, tick: Long): Boolean =
        attacker.skullTicks == 0 && prevention(attacker) && unprovoked(attacker, target, tick)

    private fun prevention(player: Player): Boolean =
        player.vars.types.varbit(VARBIT_PREVENTION) != null && player.vars.getVarbit(VARBIT_PREVENTION) == 1

    /**
     * [attacker] attacked [target] on [tick]: unprovoked, they are skulled for [TICKS] from now and [target] may hit
     * back unskulled until then; a retaliation changes nothing.
     */
    fun attacked(attacker: Player, target: Player, tick: Long) {
        if (!unprovoked(attacker, target, tick)) return
        attacker.skullTicks = TICKS
        attacker.attackedFirst[target.saveKey] = TICKS
    }

    /** A tick of [player]'s skull goes by, and of the time each player they attacked first may hit them back. */
    @Suppress("UNUSED_PARAMETER")
    fun countDown(player: Player, tick: Long) {
        if (player.skullTicks > 0) player.skullTicks--
        if (player.attackedFirst.isNotEmpty()) {
            val each = player.attackedFirst.entries.iterator()
            while (each.hasNext()) {
                val entry = each.next()
                if (entry.value <= 1) each.remove() else entry.setValue(entry.value - 1)
            }
        }
    }

    /** The head icon [player] should show now: [ICON] while skulled, -1 for none. */
    fun icon(player: Player): Int = if (player.skullTicks > 0) ICON else -1
}
