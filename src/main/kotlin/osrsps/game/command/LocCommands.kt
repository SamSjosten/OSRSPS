package osrsps.game.command

import osrsps.game.World
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape

/** Testing commands for locs: list the ones around you, add one, remove one, put a tile back. */
fun CommandRegistry.registerLocCommands(world: World) {
    val locs = world.locs

    register("locs", "::locs [radius]") {
        if (args.size > 1) fail("Usage: ::locs [radius]")
        val radius = args.getOrNull(0)?.let { it.toIntOrNull() ?: fail("Usage: ::locs [radius] ('$it' is not a number)") } ?: 1
        if (radius !in 0..MAX_RADIUS) fail("Radius must be 0 to $MAX_RADIUS")
        val found = ArrayList<ZoneLoc>()
        for (x in player.x - radius..player.x + radius) {
            for (z in player.z - radius..player.z + radius) found += locs.at(x, z, player.level)
        }
        if (found.isEmpty()) reply("No locs within $radius of ${player.x}, ${player.z}")
        // One line each: the chatbox cuts long messages.
        for (loc in found.take(MAX_LISTED)) {
            val type = locs.types[loc.id]
            val changed = if (locs.cached(loc.x, loc.z, loc.level, loc.layer) == loc) "" else " (changed)"
            reply("${loc.id} ${type?.name ?: "?"} at ${loc.x}, ${loc.z} shape ${loc.shape} angle ${loc.angle}$changed")
        }
        if (found.size > MAX_LISTED) reply("... and ${found.size - MAX_LISTED} more; try a smaller radius")
    }

    register("addloc", "::addloc id [shape] [angle]", admin = true) {
        if (args.size !in 1..3) fail("Usage: ::addloc id [shape] [angle]")
        val values = args.map { it.toIntOrNull() ?: fail("Usage: ::addloc id [shape] [angle] ('$it' is not a number)") }
        val id = values[0]
        val shape = values.getOrElse(1) { LocShape.CENTREPIECE_STRAIGHT }
        val angle = values.getOrElse(2) { 0 }
        if (locs.types[id] == null) fail("The cache has no loc $id")
        if (shape !in 0..LocShape.GROUND_DECOR) fail("Shape must be 0 to ${LocShape.GROUND_DECOR}")
        if (angle !in 0..3) fail("Angle must be 0 to 3")
        locs.add(ZoneLoc(id, shape, angle, player.x, player.z, player.level))
        reply("Added loc $id shape $shape angle $angle on ${player.x}, ${player.z}")
    }

    register("delloc", "::delloc", admin = true) {
        if (args.isNotEmpty()) fail("Usage: ::delloc")
        val here = locs.at(player.x, player.z, player.level)
        here.forEach { locs.remove(it) }
        reply("Removed ${here.size} loc${if (here.size == 1) "" else "s"} from ${player.x}, ${player.z}")
    }

    register("resetlocs", "::resetlocs [radius]", admin = true) {
        if (args.size > 1) fail("Usage: ::resetlocs [radius]")
        val radius = args.getOrNull(0)?.let { it.toIntOrNull() ?: fail("Usage: ::resetlocs [radius] ('$it' is not a number)") } ?: 0
        if (radius !in 0..MAX_RADIUS) fail("Radius must be 0 to $MAX_RADIUS")
        for (x in player.x - radius..player.x + radius) {
            for (z in player.z - radius..player.z + radius) {
                for (layer in 0 until 4) locs.restore(x, z, player.level, layer)
            }
        }
        reply("Put the locs within $radius of ${player.x}, ${player.z} back as the cache has them")
    }
}

private const val MAX_RADIUS = 10
private const val MAX_LISTED = 20
