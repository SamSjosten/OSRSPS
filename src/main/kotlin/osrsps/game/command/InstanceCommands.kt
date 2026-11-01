package osrsps.game.command

import osrsps.game.World
import osrsps.game.instance.Instances

/**
 * `::instance`: a private copy of the map around you, to try instances on (raid step 10). The raid's own rooms will
 * make theirs in content; this only copies what is here.
 */
fun CommandRegistry.registerInstanceCommands(world: World) {
    val instances = world.instances

    register("instance", "::instance [radius] | ::instance leave", admin = true) {
        if (args.singleOrNull() == "leave") {
            val instance = instances.at(player.x, player.z) ?: fail("You're not in an instance")
            world.scripts.interrupt(player)
            world.teleport(player, instance.exitX, instance.exitZ, instance.exitLevel)
            reply("Left instance #${instance.id} for ${instance.exitX}, ${instance.exitZ}, ${instance.exitLevel}")
            return@register
        }
        if (args.size > 1) fail("Usage: ::instance [radius] | ::instance leave")
        val radius = args.firstOrNull()?.let { it.toIntOrNull() ?: fail("Usage: ::instance [radius] ('$it' is not a number)") } ?: DEFAULT_RADIUS
        if (radius !in 0..MAX_RADIUS) fail("Radius must be 0 to $MAX_RADIUS zones")
        instances.at(player.x, player.z)?.let { fail("You're in instance #${it.id}: ::instance leave first") }
        val size = 2 * radius + 1
        val fromX = ((player.x shr 3) - radius) shl 3
        val fromZ = ((player.z shr 3) - radius) shl 3
        if (fromX < 0 || fromZ < 0) fail("Too near the edge of the map")
        val instance = instances.create(fromX, fromZ, size, size, player.x, player.z, player.level)
            ?: fail("No room for another instance")
        val x = instance.baseX + (player.x - fromX)
        val z = instance.baseZ + (player.z - fromZ)
        world.scripts.interrupt(player)
        world.teleport(player, x, z, player.level)
        reply("Made instance #${instance.id}: $size x $size zones from $fromX, $fromZ, you at $x, $z, ${player.level}")
    }
}

/** 13 x 13 zones: a whole build area's worth around you. */
private const val DEFAULT_RADIUS = 6

private const val MAX_RADIUS = (Instances.MAX_ZONES - 1) / 2
