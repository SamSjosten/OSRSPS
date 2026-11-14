package osrsps.game.command

import osrsps.game.World
import osrsps.game.skill.farming.FarmingClock
import osrsps.game.skill.farming.FarmingPatches

/**
 * `::farmtick [ticks]` moves the player's farming clock on that many 5-minute farming ticks (1 by default) and grows
 * their patches to it, so a crop can be grown without waiting. It moves only that player's clock, and for good: their
 * crops keep growing from there.
 *
 * `::farmclock pause` holds the player's farming clock where it is, so their crops grow only by `::farmtick` (a test
 * that checks each stage can't have a real 5-minute tick land between its steps); `::farmclock run` lets it run
 * again, the crops catching up on the time it was held. A login runs it again too.
 *
 * `::farmdisease <patch>` makes the crop in one of the player's patches (by its save key, such as
 * `falador_allotment_nw`) diseased now, to try curing and dying without waiting on the dice. `::farmdisease off`
 * stops the player's crops falling ill on their own (a script that grows a crop through many stages can't be left to
 * the dice), and `::farmdisease on` brings it back; a login brings it back too.
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

    register("farmdisease", "::farmdisease <patch|off|on>") {
        val keys = FarmingPatches.ALL.joinToString(", ") { it.key }
        if (args.size != 1) fail("Usage: ::farmdisease <patch|off|on>, a patch one of $keys")
        when (args[0]) {
            "off" -> {
                player.farming.noDisease = true
                return@register reply("Farming diseases off: your crops fall ill only by ::farmdisease")
            }
            "on" -> {
                player.farming.noDisease = false
                return@register reply("Farming diseases on")
            }
        }
        val patch = FarmingPatches.byKey(args[0]) ?: fail("No patch '${args[0]}': one of $keys")
        if (!world.farming.makeDiseased(player, patch)) fail("Nothing is growing past its first stage in ${patch.key}")
        reply("${patch.key} is diseased")
    }
}
