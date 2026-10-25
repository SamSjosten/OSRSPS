package osrsps.game.command

import osrsps.game.World

/** Tick control: freeze the game, run it a tick at a time, and let it go again. */
fun CommandRegistry.registerTickCommands(world: World) {
    val clock = world.clock

    register("pause", "::pause", admin = true) {
        when {
            !clock.paused -> {
                world.pause()
                reply("Paused after tick ${world.tick}. ::step [n] runs n ticks, ::resume starts the clock again")
            }
            clock.stepsLeft > 0 -> {
                val dropped = world.pause()
                reply("Stopped stepping with $dropped ${ticks(dropped)} left; paused after tick ${world.tick}")
            }
            else -> reply("Already paused after tick ${world.tick}")
        }
    }

    register("step", "::step [n]", admin = true) {
        if (!clock.paused) fail("The clock is running: ::pause first")
        if (args.size > 1) fail("Usage: ::step [n]")
        val n = args.firstOrNull()?.let { it.toIntOrNull() ?: fail("Usage: ::step [n] ('$it' is not a number)") } ?: 1
        if (n !in 1..MAX_STEP) fail("n must be 1 to $MAX_STEP")
        world.step(player, n)
        val total = clock.stepsLeft
        reply("Stepping $n ${ticks(n)}" + if (total > n) " ($total to go)" else "")
    }

    register("resume", "::resume", admin = true) {
        if (!clock.paused) fail("Not paused (tick ${world.tick})")
        world.resume()
        reply("Resumed after tick ${world.tick}")
    }
}

private fun ticks(n: Int) = if (n == 1) "tick" else "ticks"

/** Ten minutes of game time. Longer runs are ::resume and ::pause. */
private const val MAX_STEP = 1000
