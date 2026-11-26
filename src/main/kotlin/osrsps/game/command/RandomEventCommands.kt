package osrsps.game.command

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.World

private val logger = KotlinLogging.logger {}

/**
 * `::randomevent`, for testing random events ([osrsps.game.content.randomevents.RandomEvents]); live has nothing
 * like it. Alone it says whether events are on and when the next is due; `end` lets the player out of an event that
 * holds them on the real map (the Pillory), back where they came from with nothing, for scripts that look and leave.
 */
fun CommandRegistry.registerRandomEventCommands(world: World) {
    val events = world.randomEvents
    val usage = "::randomevent [list|now|on|off|end|<event> [npc]]"

    register("randomevent", usage) {
        val arg = args.firstOrNull()?.lowercase()
        when {
            args.size > 2 || (args.size == 2 && args[1].toIntOrNull() == null) -> fail("Usage: $usage")
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
            arg == "end" -> {
                val ended = events.events.firstOrNull { it.end(world, player) } ?: fail("No random event holds you")
                logger.info { "${player.username} ended random event ${ended.key}" }
                reply("Ended random event ${ended.key}")
            }
            arg == "off" || arg == "on" -> {
                events.setOff(player, arg == "off")
                logger.info { "${player.username} turned random events $arg" }
                reply("Random events $arg")
            }
            else -> {
                val event = events.event(arg) ?: fail("No random event '$arg'; ::randomevent list names them")
                val npcId = args.getOrNull(1)?.toInt()
                if (npcId != null && npcId !in event.npcIds) fail("${event.key} comes as ${event.npcIds.joinToString()}, not $npcId")
                events.start(player, event, npcId) ?: fail("No room beside you, or an event is out already")
                logger.info { "${player.username} started random event ${event.key}" }
                reply("Started random event ${event.key}")
            }
        }
    }
}
