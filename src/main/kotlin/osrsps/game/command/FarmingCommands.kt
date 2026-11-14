package osrsps.game.command

import osrsps.game.World
import osrsps.game.skill.farming.FarmingClock

/**
 * `::farmtick [ticks]` moves the player's farming clock on that many 5-minute farming ticks (1 by default) and grows
 * their patches to it, so a crop can be grown without waiting. It moves only that player's clock, and for good: their
 * crops keep growing from there.
 */
fun CommandRegistry.registerFarmingCommands(world: World) {
    register("farmtick", "::farmtick [ticks]") {
        if (args.size > 1) fail("Usage: ::farmtick [ticks]")
        val ticks = args.firstOrNull()?.let { it.toIntOrNull() ?: fail("'$it' isn't a number") } ?: 1
        if (ticks !in 1..FarmingClock.MAX_CATCH_UP) fail("Ticks are 1 to ${FarmingClock.MAX_CATCH_UP}")
        world.farming.skip(player, ticks)
        reply("Farming clock moved on $ticks tick${if (ticks == 1) "" else "s"} (${ticks * FarmingClock.TICK_MINUTES} minutes)")
    }
}
