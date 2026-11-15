package osrsps.game.command

import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.combat.Skulls

/**
 * `::wildy <level>` jumps to that Wilderness level on the line north of Edgeville (x 3090), on the first tile of the
 * level's 8 rows that can be stood on, looking a few tiles either side when the line itself is blocked there. Level 1
 * lands north of the ditch, inside.
 */
fun CommandRegistry.registerWildernessCommands(world: World) {
    register("wildy", "::wildy level") {
        val level = args.singleOrNull()?.toIntOrNull() ?: fail("Usage: ::wildy level (1 to ${WildernessJumps.MAX_LEVEL})")
        if (level !in 1..WildernessJumps.MAX_LEVEL) fail("Usage: ::wildy level (1 to ${WildernessJumps.MAX_LEVEL})")
        val (x, z) = WildernessJumps.tileFor(level) { x, z -> world.hasMap(x, z) && world.canStand(x, z, 0) }
            ?: fail("Nowhere to stand at level $level near x ${WildernessJumps.LINE_X}")
        // As a click does: whatever the player had open closes and its script is dropped.
        world.scripts.interrupt(player)
        world.teleport(player, x, z, 0)
        reply("Jumped to $x, $z: level $level Wilderness")
    }

    // on and off rather than a toggle, so a test script sets the state it needs whatever an earlier one left; with
    // neither it only says, so a script can check a skull it got from a fight.
    register("skull", "::skull [on|off]", admin = true) {
        if (args.size > 1) fail("Usage: ::skull [on|off]")
        val on = when (args.firstOrNull()?.lowercase()) {
            null -> {
                reply(if (player.skullTicks > 0) "You're skulled: ${player.skullTicks} ticks left." else "You have no skull.")
                return@register
            }
            "on" -> true
            "off" -> false
            else -> fail("Usage: ::skull [on|off]")
        }
        player.skullTicks = if (on) Skulls.TICKS else 0
        reply(if (on) "You're skulled for ${Skulls.TICKS} ticks (30 minutes)." else "Your skull is gone.")
    }

    register("pj", "::pj", admin = true) {
        if (args.isNotEmpty()) fail("Usage: ::pj")
        val left = (player.pjUntil - world.tick).coerceAtLeast(0)
        player.pjOpponent = null
        player.pjUntil = 0
        reply(if (left > 0) "Your PJ timer is cleared ($left ticks were left): anyone may attack you in single-way combat." else "No PJ timer was running.")
    }
}

internal object WildernessJumps {
    const val LINE_X = 3090
    const val MAX_LEVEL = 56
    private const val SEARCH = 16

    /** The tile [level] jumps to: rows south to north, the line first and then out either side; null when none [fits]. */
    fun tileFor(level: Int, fits: (x: Int, z: Int) -> Boolean): Pair<Int, Int>? {
        val south = maxOf(Wilderness.DITCH_NORTH + 1, 3520 + (level - 1) * 8)
        val north = 3520 + level * 8 - 1
        for (z in south..north) {
            for (offset in 0..SEARCH) {
                for (x in listOf(LINE_X + offset, LINE_X - offset).distinct()) {
                    if (Wilderness.level(x, z) == level && Wilderness.inside(x, z) && fits(x, z)) return x to z
                }
            }
        }
        return null
    }
}
