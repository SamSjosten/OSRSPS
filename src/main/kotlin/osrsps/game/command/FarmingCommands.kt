package osrsps.game.command

import osrsps.game.World
import osrsps.game.skill.farming.FarmingClock

/**
 * `::farmtick [ticks]` moves the player's farming clock on that many 5-minute farming ticks (1 by default) and grows
 * their patches to it, so a crop can be grown without waiting. It moves only that player's clock, and for good: their
 * crops keep growing from there.
 *
 * `::farmclock pause` holds the player's farming clock where it is, so their crops grow only by `::farmtick` (a test
 * that checks each stage can't have a real 5-minute tick land between its steps); `::farmclock run` lets it run
 * again, the crops catching up on the time it was held. A login runs it again too.
 */
fun CommandRegistry.registerFarmingCommands(world: World) {
    register("farmclock", "::farmclock <pause|run>") {
        when (args.singleOrNull()) {
            "pause" -> {
                world.farming.pause(player, true)
                reply("Farming clock paused: crops grow only by ::farmtick")
            }
            "run" -> {
                world.farming.pause(player, false)
                reply("Farming clock running")
            }
            else -> fail("Usage: ::farmclock <pause|run>")
        }
    }

    register("farmtick", "::farmtick [ticks]") {
        if (args.size > 1) fail("Usage: ::farmtick [ticks]")
        val ticks = args.firstOrNull()?.let { it.toIntOrNull() ?: fail("'$it' isn't a number") } ?: 1
        if (ticks !in 1..FarmingClock.MAX_CATCH_UP) fail("Ticks are 1 to ${FarmingClock.MAX_CATCH_UP}")
        world.farming.skip(player, ticks)
        reply("Farming clock moved on $ticks tick${if (ticks == 1) "" else "s"} (${ticks * FarmingClock.TICK_MINUTES} minutes)")
    }
}
