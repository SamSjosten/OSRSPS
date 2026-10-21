package osrsps.game.command

import osrsps.game.World
import osrsps.game.zone.Projectile
import osrsps.game.zone.ZoneObj
import kotlin.math.abs
import kotlin.math.max

/** Testing commands for zone updates: everyone near you sees what these make. */
fun CommandRegistry.registerZoneCommands(world: World) {
    val zones = world.zones

    register("gfx", "::gfx id [height] [delay]") {
        if (args.size !in 1..3) fail("Usage: ::gfx id [height] [delay]")
        val values = args.map { it.toIntOrNull() ?: fail("Usage: ::gfx id [height] [delay] ('$it' is not a number)") }
        val id = values[0]
        val height = values.getOrElse(1) { 0 }
        val delay = values.getOrElse(2) { 0 }
        if (id !in 0..MAX_ID) fail("Spotanim id must be 0 to $MAX_ID")
        if (height !in 0..255) fail("Height must be 0 to 255")
        if (delay !in 0..MAX_ID) fail("Delay must be 0 to $MAX_ID client cycles")
        zones.mapAnim(player.x, player.z, player.level, id, height, delay)
        reply("Spotanim $id on ${player.x}, ${player.z}")
    }

    register("proj", "::proj id [dx dz]") {
        if (args.size != 1 && args.size != 3) fail("Usage: ::proj id [dx dz]")
        val values = args.map { it.toIntOrNull() ?: fail("Usage: ::proj id [dx dz] ('$it' is not a number)") }
        val id = values[0]
        val dx = values.getOrElse(1) { 0 }
        val dz = values.getOrElse(2) { PROJ_DEFAULT_DISTANCE }
        if (id !in 0..MAX_ID) fail("Spotanim id must be 0 to $MAX_ID")
        if (dx == 0 && dz == 0) fail("dx and dz can't both be 0: the client doesn't draw a projectile that goes nowhere")
        if (abs(dx) > PROJ_MAX_DISTANCE || abs(dz) > PROJ_MAX_DISTANCE) fail("dx and dz must be within $PROJ_MAX_DISTANCE")
        val distance = max(abs(dx), abs(dz))
        // Guessed flight values, about a ranged attack's: heights, start delay, speed, arc and start offset are not
        // taken from any live projectile.
        zones.projectile(
            Projectile(
                id = id,
                fromX = player.x,
                fromZ = player.z,
                level = player.level,
                toX = player.x + dx,
                toZ = player.z + dz,
                startHeight = 172,
                endHeight = 124,
                startTime = 30,
                endTime = 30 + 10 + distance * 5,
                angle = 16,
                progress = 64,
            ),
        )
        reply("Projectile $id from ${player.x}, ${player.z} to ${player.x + dx}, ${player.z + dz}")
    }

    // Lasts until ::cleartile or a restart: ground item timers come with ground items.
    register("groundobj", "::groundobj id [count]") {
        if (args.size !in 1..2) fail("Usage: ::groundobj id [count]")
        val id = args[0].toIntOrNull() ?: fail("Usage: ::groundobj id [count] ('${args[0]}' is not a number)")
        val count = args.getOrNull(1)?.let { it.toIntOrNull() ?: fail("Usage: ::groundobj id [count] ('$it' is not a number)") } ?: 1
        if (id !in 0..MAX_OBJ_ID) fail("Obj id must be 0 to $MAX_OBJ_ID")
        if (count < 1) fail("Count must be at least 1")
        zones.addObj(ZoneObj(id, count, player.x, player.z, player.level))
        reply("Put obj $id x $count on ${player.x}, ${player.z}")
    }

    register("cleartile", "::cleartile") {
        if (args.isNotEmpty()) fail("Usage: ::cleartile")
        val objs = zones.objsAt(player.x, player.z, player.level)
        objs.forEach(zones::removeObj)
        reply("Removed ${objs.size} obj${if (objs.size == 1) "" else "s"} from ${player.x}, ${player.z}")
    }
}

/** Spotanim ids and client-cycle delays go in two bytes. */
private const val MAX_ID = 65535

/** The client keeps only the low 15 bits of an obj id when deleting it, so bigger ids couldn't be cleared again. */
private const val MAX_OBJ_ID = 32767

private const val PROJ_DEFAULT_DISTANCE = 5
private const val PROJ_MAX_DISTANCE = 20
