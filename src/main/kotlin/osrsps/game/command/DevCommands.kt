package osrsps.game.command

import osrsps.game.World

/** Testing commands: where am I, put me there, and what tick is it. */
fun CommandRegistry.registerDevCommands(world: World) {
    register("pos", "::pos") {
        val p = player
        // Same order and separators as RuneLite's world location, so the coordinates paste straight into ::tele.
        reply("${p.x}, ${p.z}, ${p.level} (region ${((p.x shr 6) shl 8) or (p.z shr 6)}, local ${p.x and 63}, ${p.z and 63})")
    }

    register("tele", "::tele x z [level]") {
        // Commas are accepted too, so "::tele 3222, 3218, 0" works.
        val numbers = args.joinToString(" ").split(COORD_SEPARATORS).filter { it.isNotEmpty() }
        if (numbers.size !in 2..3) fail("Usage: ::tele x z [level]")
        val values = numbers.map { it.toIntOrNull() ?: fail("Usage: ::tele x z [level] ('$it' is not a number)") }
        val x = values[0]
        val z = values[1]
        val level = values.getOrElse(2) { player.level }
        if (x !in 0 until MAX_COORD || z !in 0 until MAX_COORD) fail("x and z must be 0 to ${MAX_COORD - 1}")
        if (level !in 0..3) fail("Level must be 0 to 3")
        if (!world.hasMap(x, z)) fail("No map at $x, $z: the cache has no terrain there")
        world.teleport(player, x, z, level)
        reply("Teleported to $x, $z, $level")
    }

    register("tick", "::tick") {
        reply("Tick ${world.tick}")
    }
}

private val COORD_SEPARATORS = Regex("[\\s,]+")

/** Coordinates are 14 bits in the client's coord grid. */
private const val MAX_COORD = 1 shl 14
