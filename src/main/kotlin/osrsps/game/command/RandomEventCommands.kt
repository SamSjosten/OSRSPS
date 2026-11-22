package osrsps.game.command

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.World

private val logger = KotlinLogging.logger {}

/**
 * `::randomevent`, for testing random events ([osrsps.game.content.randomevents.RandomEvents]); live has nothing
 * like it. Alone it says whether events are on and when the next is due.
 */
fun CommandRegistry.registerRandomEventCommands(world: World) {
    val events = world.randomEvents
    val usage = "::randomevent [list|now|on|off|<event>]"

    register("randomevent", usage) {
        val arg = args.singleOrNull()?.lowercase()
        when {
            args.size > 1 -> fail("Usage: $usage")
            arg == null -> {
                val out = events.outFor(player)
                val state = if (events.isOff(player)) "off" else "on, next due in ${events.dueIn(player).coerceAtLeast(0)} min"
                reply("Random events $state" + (out?.let { "; ${it.event.key} is out" } ?: ""))
            }
            arg == "list" -> reply("Random events: " + events.events.joinToString { it.key })
            arg == "now" -> {
                events.makeDue(player)
                reply("Random event due now")
            }
            arg == "off" || arg == "on" -> {
                events.setOff(player, arg == "off")
                logger.info { "${player.username} turned random events $arg" }
                reply("Random events $arg")
            }
            else -> {
                val event = events.event(arg) ?: fail("No random event '$arg'; ::randomevent list names them")
                events.start(player, event) ?: fail("No room beside you, or an event is out already")
                logger.info { "${player.username} started random event ${event.key}" }
                reply("Started random event ${event.key}")
            }
        }
    }
}
