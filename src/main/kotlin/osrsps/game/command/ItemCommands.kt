package osrsps.game.command

import osrsps.game.ObjType
import osrsps.game.World
import java.util.Locale

/** Put items in the typing player's inventory and empty it. The client is sent the change at the end of the cycle. */
fun CommandRegistry.registerItemCommands(world: World) {
    register("item", "::item id|name [count]") {
        if (args.isEmpty()) fail("Usage: ::item id|name [count]")
        // A last word that reads as a count is the count, so names can have spaces: ::item bronze axe 2.
        val count = args.takeIf { it.size > 1 }?.last()?.let(::parseCount)
        val words = if (count != null) args.dropLast(1) else args
        val text = words.joinToString(" ")
        val type = findItem(world, text) ?: fail("No item '$text'. Try its id, its name, or its gameval name like bronze_axe")
        val wanted = count ?: 1
        val added = player.inventory.add(type, wanted)
        val what = "${format(added)} x ${type.name} (${type.id}${if (type.isCert) ", noted" else ""})"
        when {
            added == wanted -> reply("Added $what.")
            added == 0 -> reply("No room for ${type.name}.")
            else -> reply("Added $what; no room for the other ${format(wanted - added)}.")
        }
    }

    register("empty", "::empty") {
        if (args.isNotEmpty()) fail("Usage: ::empty")
        player.inventory.clear()
        reply("Inventory emptied.")
    }
}

internal fun findItem(world: World, text: String): ObjType? {
    val types = world.objTypes
    text.toIntOrNull()?.let { return types[it] }
    return types.find(text.replace(' ', '_')) ?: types.findByName(text)
}

/** "5", "10k" or "2m" to a count from 1 to Int.MAX_VALUE; null for anything else. */
internal fun parseCount(text: String): Int? {
    val multiplier = when (text.lowercase().lastOrNull()) {
        'k' -> 1_000L
        'm' -> 1_000_000L
        else -> 1L
    }
    val digits = if (multiplier == 1L) text else text.dropLast(1)
    val value = digits.toLongOrNull() ?: return null
    if (value < 1) return null
    return (value * multiplier).takeIf { it <= Int.MAX_VALUE }?.toInt()
}

private fun format(n: Int): String = String.format(Locale.ROOT, "%,d", n)
