package osrsps.game.command

import osrsps.game.World
import osrsps.game.script.QueueType
import osrsps.game.script.TimerType

/** Try the queues, delays and timers by hand, and see what a player has waiting. */
fun CommandRegistry.registerScriptCommands(world: World) {
    val scripts = world.scripts

    register("queue", "::queue weak|normal|strong|soft [delay]") {
        if (args.size !in 1..2) fail("Usage: ::queue weak|normal|strong|soft [delay]")
        val type = QueueType.entries.firstOrNull { it.name.equals(args[0], ignoreCase = true) }
            ?: fail("Usage: ::queue weak|normal|strong|soft [delay] ('${args[0]}' is not a queue type)")
        val delay = args.getOrNull(1)?.let { it.toIntOrNull() ?: fail("Usage: ::queue ... [delay] ('$it' is not a number)") } ?: 0
        if (delay !in 0..MAX_TICKS) fail("delay must be 0 to $MAX_TICKS")
        val queuedAt = moment(world)
        scripts.queue(player, DEV_SCRIPT, type, delay) {
            reply("${type.label} script ran on tick $tick (queued $queuedAt with delay $delay)")
        }
        reply("Queued a ${type.label} script with delay $delay $queuedAt")
    }

    register("delay", "::delay [ticks]") {
        if (args.size > 1) fail("Usage: ::delay [ticks]")
        val ticks = args.firstOrNull()?.let { it.toIntOrNull() ?: fail("Usage: ::delay [ticks] ('$it' is not a number)") } ?: 1
        if (ticks !in 1..MAX_TICKS) fail("ticks must be 1 to $MAX_TICKS")
        if (player.scripts.delayed) fail("Already delayed until tick ${player.scripts.delayedUntil}")
        scripts.start(player) {
            reply("Delayed ${moment(world)} for $ticks ${ticks(ticks)}")
            delay(ticks)
            reply("Delay ended on tick $tick")
        }
    }

    register("timer", "::timer normal|soft interval [times] | ::timer off") {
        if (args.singleOrNull()?.equals("off", ignoreCase = true) == true) {
            scripts.clearTimer(player, DEV_SCRIPT)
            reply("Timer cleared")
            return@register
        }
        if (args.size !in 2..3) fail("Usage: ::timer normal|soft interval [times] | ::timer off")
        val type = TimerType.entries.firstOrNull { it.name.equals(args[0], ignoreCase = true) }
            ?: fail("Usage: ::timer normal|soft interval [times] ('${args[0]}' is not a timer type)")
        val interval = args[1].toIntOrNull() ?: fail("Usage: ::timer ... interval ('${args[1]}' is not a number)")
        if (interval !in 1..MAX_TICKS) fail("interval must be 1 to $MAX_TICKS")
        val times = args.getOrNull(2)?.let { it.toIntOrNull() ?: fail("Usage: ::timer ... [times] ('$it' is not a number)") } ?: 3
        if (times !in 1..MAX_TICKS) fail("times must be 1 to $MAX_TICKS")
        var fired = 0
        scripts.setTimer(player, DEV_SCRIPT, interval, type) {
            fired++
            reply("${type.name.lowercase()} timer fired on tick $tick ($fired of $times)")
            if (fired == times) clearTimer(DEV_SCRIPT)
        }
        reply("Set a ${type.name.lowercase()} timer every $interval ${ticks(interval)} ${moment(world)}")
    }

    register("scripts", "::scripts") {
        val state = player.scripts
        val delay = if (state.delayed) "delayed until tick ${state.delayedUntil}" else "not delayed"
        val modals = if (player.modalOpen) "modal open" else "no modal"
        val queued = state.queued.joinToString { "${it.name} (${it.type.label}, delay ${it.delay.coerceAtLeast(0)})" }
            .ifEmpty { "none" }
        val timers = state.activeTimers
            .joinToString { "${it.name} (${it.type.name.lowercase()} every ${it.interval}, clock ${it.clock.coerceAtLeast(0)})" }
            .ifEmpty { "none" }
        reply("Tick ${world.tick}: $delay, $modals. Queued: $queued. Timers: $timers")
    }
}

private val QueueType.label get() = name.lowercase()

/** Commands run in a tick's input phase while the clock runs, and between ticks while paused. */
private fun moment(world: World) = if (world.phase == null) "after tick ${world.tick}" else "during tick ${world.tick}"

private fun ticks(n: Int) = if (n == 1) "tick" else "ticks"

/** The name the commands give what they queue and set, so ::timer off finds it. */
private const val DEV_SCRIPT = "dev"

/** Ten minutes of game time, like ::step. */
private const val MAX_TICKS = 1000
