package osrsps.game.command

import osrsps.game.World

/** The game's random seed: show it, or set it to repeat a run. */
fun CommandRegistry.registerSeedCommands(world: World) {
    val random = world.random

    register("seed", "::seed [n]", admin = true) {
        if (args.size > 1) fail("Usage: ::seed [n]")
        if (args.isEmpty()) {
            reply("Seed ${random.seed}, ${random.draws} ${draws(random.draws)} since it was set")
            return@register
        }
        val seed = args[0].toLongOrNull() ?: fail("Usage: ::seed [n] ('${args[0]}' is not a whole number)")
        random.reseed(seed)
        reply("Seed set to $seed. The same input on the same ticks from here gives the same rolls")
    }
}

private fun draws(n: Long) = if (n == 1L) "draw" else "draws"
