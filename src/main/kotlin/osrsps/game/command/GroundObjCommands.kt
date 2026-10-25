package osrsps.game.command

import osrsps.game.GroundObjs
import osrsps.game.ObjSpawn
import osrsps.game.ObjType
import osrsps.game.World
import osrsps.game.zone.ZoneObj

/** Testing commands for ground items, on the typing player's tile. Drop and Take themselves are the game's own options. */
fun CommandRegistry.registerGroundObjCommands(world: World) {
    val ground = world.groundObjs

    // Public and permanent, so a second client sees it at once: for testing what clients are sent, not live behaviour.
    register("groundobj", "::groundobj id|name [count]", admin = true) {
        val (type, numbers) = itemAndNumbers(world, "::groundobj id|name [count]", maxNumbers = 1)
        val count = numbers.getOrElse(0) { 1 }
        if (!type.stackable && count > GroundObjs.MAX_SPLIT) fail("${type.name} doesn't stack: at most ${GroundObjs.MAX_SPLIT} at once")
        ground.addPublic(type, count, player.x, player.z, player.level)
        reply("Put ${type.name} (${type.id}) x $count on ${player.x}, ${player.z}")
    }

    register("objspawn", "::objspawn id|name [count [respawn ticks]]", admin = true) {
        val (type, numbers) = itemAndNumbers(world, "::objspawn id|name [count [respawn ticks]]", maxNumbers = 2)
        val count = numbers.getOrElse(0) { 1 }
        val respawn = numbers.getOrElse(1) { GroundObjs.DEFAULT_RESPAWN_TICKS }
        if (!type.stackable && count != 1) fail("${type.name} doesn't stack, so a spawn of it is 1")
        ground.addSpawn(ObjSpawn(type.id, count, player.x, player.z, player.level, respawn))
        reply("Item spawn: ${type.name} (${type.id}) x $count on ${player.x}, ${player.z}, back $respawn ticks after it's taken (until a restart)")
    }

    register("grounditems", "::grounditems") {
        if (args.isNotEmpty()) fail("Usage: ::grounditems")
        val objs = world.zones.objsAt(player.x, player.z, player.level)
        if (objs.isEmpty()) {
            reply("Nothing on ${player.x}, ${player.z}")
            return@register
        }
        for (obj in objs) reply(describe(world, obj))
    }

    register("cleartile", "::cleartile", admin = true) {
        if (args.isNotEmpty()) fail("Usage: ::cleartile")
        val removed = ground.clear(player.x, player.z, player.level)
        reply("Removed $removed obj${if (removed == 1) "" else "s"} from ${player.x}, ${player.z}")
    }
}

/**
 * Reads "id|name [n] [n]": an item, by id or name with spaces allowed, then up to [maxNumbers] counts or tick numbers
 * from the end, which must be at least 1.
 */
private fun CommandContext.itemAndNumbers(world: World, usage: String, maxNumbers: Int): Pair<ObjType, List<Int>> {
    if (args.isEmpty()) fail("Usage: $usage")
    var words = args
    val numbers = ArrayList<Int>()
    while (numbers.size < maxNumbers && words.size > 1) {
        val n = parseCount(words.last()) ?: break
        numbers.add(0, n)
        words = words.dropLast(1)
    }
    val text = words.joinToString(" ")
    val type = findItem(world, text) ?: fail("No item '$text'. Try its id, its name, or its gameval name like bronze_axe")
    return type to numbers
}

private fun describe(world: World, obj: ZoneObj): String {
    val name = world.objTypes[obj.id]?.name ?: "?"
    val who = when {
        obj.owner != null -> "private to ${obj.owner}" + if (obj.neverPublic) ", never public" else ", public in ${obj.ticksUntilPublic} ticks"
        obj.droppedBy != null -> "public, dropped by ${obj.droppedBy}"
        else -> "public"
    }
    val despawn = if (obj.ticksUntilDespawn == ZoneObj.NEVER) "stays" else "gone in ${obj.ticksUntilDespawn} ticks"
    val spawn = world.groundObjs.spawnOf(obj)?.let { ", item spawn back ${it.respawnTicks} ticks after it's taken" } ?: ""
    return "$name (${obj.id}) x ${obj.count}: $who, $despawn$spawn"
}
