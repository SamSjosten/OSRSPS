package osrsps.game.command

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.World
import osrsps.game.XpRate

private val logger = KotlinLogging.logger {}

/** The server-wide XP rate, which lasts across restarts; see [XpRate]. */
fun CommandRegistry.registerXpRateCommands(world: World) {
    val rate = world.xpRate

    register("xprate", "::xprate [rate|restore]") {
        when {
            args.isEmpty() -> reply("XP rate: $rate")
            args.size != 1 -> fail("Usage: ::xprate [rate|restore]")
            args[0].equals("restore", ignoreCase = true) -> {
                rate.restore()
                logger.info { "${player.username} put the XP rate back to $rate" }
                reply("XP rate back to $rate")
            }
            else -> {
                val tenths = XpRate.parse(args[0])
                    ?: fail("XP rates are ${XpRate.format(XpRate.MIN)} to ${XpRate.format(XpRate.MAX)}, with at most one decimal place")
                val before = rate.toString()
                rate.set(tenths)
                logger.info { "${player.username} set the XP rate from $before to $rate" }
                reply("XP rate set to $rate (was $before)")
            }
        }
    }
}
