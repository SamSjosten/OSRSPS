package osrsps.game.command

import osrsps.game.World
import osrsps.game.combat.HitDelay
import osrsps.game.zone.Projectile
import osrsps.map.CollisionMap
import osrsps.map.LineOfSight
import kotlin.math.abs
import kotlin.math.max

/**
 * Testing commands for zone updates: everyone near you sees what these make. Ground objs are in GroundObjCommands.
 * `::los` only answers you.
 */
fun CommandRegistry.registerZoneCommands(world: World, collision: CollisionMap) {
    val zones = world.zones

    register("gfx", "::gfx id [height] [delay]", admin = true) {
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

    register("proj", "::proj id [dx dz]", admin = true) {
        if (args.size != 1 && args.size != 3) fail("Usage: ::proj id [dx dz]")
        val values = args.map { it.toIntOrNull() ?: fail("Usage: ::proj id [dx dz] ('$it' is not a number)") }
        val id = values[0]
        val dx = values.getOrElse(1) { 0 }
        val dz = values.getOrElse(2) { PROJ_DEFAULT_DISTANCE }
        if (id !in 0..MAX_ID) fail("Spotanim id must be 0 to $MAX_ID")
        if (dx == 0 && dz == 0) fail("dx and dz can't both be 0: the client doesn't draw a projectile that goes nowhere")
        // A range check rather than abs(), which leaves Int.MIN_VALUE negative.
        if (dx !in -PROJ_MAX_DISTANCE..PROJ_MAX_DISTANCE || dz !in -PROJ_MAX_DISTANCE..PROJ_MAX_DISTANCE) fail("dx and dz must be within $PROJ_MAX_DISTANCE")
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

    register("los", "::los npcIndex | ::los dx dz") {
        if (args.size !in 1..2) fail("Usage: ::los npcIndex | ::los dx dz")
        val values = args.map { it.toIntOrNull() ?: fail("Usage: ::los npcIndex | ::los dx dz ('$it' is not a number)") }
        val name: String
        val x: Int
        val z: Int
        val size: Int
        if (values.size == 1) {
            val npc = world.npcs[values[0]] ?: fail("No NPC ${values[0]}")
            if (npc.level != player.level) fail("${npc.type.name} (${npc.index}) is on another level")
            name = "${npc.type.name} (${npc.index})"
            x = npc.x
            z = npc.z
            size = npc.type.size
        } else {
            if (values.any { it !in -LOS_MAX_DISTANCE..LOS_MAX_DISTANCE }) fail("dx and dz must be within $LOS_MAX_DISTANCE")
            x = player.x + values[0]
            z = player.z + values[1]
            name = "$x, $z"
            size = 1
        }
        val distance = LineOfSight.distance(player.x, player.z, 1, x, z, size, size)
        val clear = LineOfSight.clear(collision, player.level, player.x, player.z, 1, x, z, size, size)
        reply(
            "$name: distance $distance, line of sight " + (if (clear) "clear" else "blocked") +
                "; hit delay in ticks: bow ${HitDelay.BOW.ticks(distance)}, thrown ${HitDelay.THROWN.ticks(distance)}, " +
                "magic ${HitDelay.MAGIC.ticks(distance)}",
        )
    }
}

/** Spotanim ids and client-cycle delays go in two bytes. */
private const val MAX_ID = 65535

private const val PROJ_DEFAULT_DISTANCE = 5
private const val PROJ_MAX_DISTANCE = 20
private const val LOS_MAX_DISTANCE = 32
