package osrsps.game.command

import osrsps.game.World

/**
 * `::clog`: the collection log's record (collection log step 1). `::clog` counts what you've logged; `::clog add <item>
 * [count]` logs an item as if it had dropped for you, announcement and all; `::clog remove <item>` and `::clog reset`
 * take one item, or everything, out again.
 */
fun CommandRegistry.registerCollectionLogCommands(world: World) {
    val usage = "Usage: ::clog | ::clog add <item> [count] | ::clog remove <item> | ::clog reset"

    register("clog", "::clog [add <item> [count] | remove <item> | reset]", admin = true) {
        // Read here, not at registration: the commands are registered before the world has made its log.
        val log = world.collectionLog
        when (args.firstOrNull()?.lowercase()) {
            null -> reply("Collection log: ${log.obtained(player)} of ${log.catalog.items.size} items logged.")
            "add" -> {
                val count = args.takeIf { it.size > 2 }?.last()?.let(::parseCount)
                val text = args.drop(1).let { if (count != null) it.dropLast(1) else it }.joinToString(" ")
                if (text.isEmpty()) fail(usage)
                val type = findItem(world, text) ?: fail("No item '$text'.")
                if (type.id !in log.catalog) fail("${type.name} (${type.id}) isn't in the collection log.")
                log.add(player, type.id, count ?: 1)
                reply("Logged ${type.name}: ${log.count(player, type.id)} now, on ${log.catalog.pagesOf(type.id).size} page(s).")
            }
            "remove" -> {
                val text = args.drop(1).joinToString(" ")
                if (text.isEmpty()) fail(usage)
                val type = findItem(world, text) ?: fail("No item '$text'.")
                log.remove(player, type.id)
                reply("Took ${type.name} out of your collection log.")
            }
            "reset" -> {
                if (args.size != 1) fail(usage)
                log.remove(player, null)
                reply("Collection log emptied.")
            }
            else -> fail(usage)
        }
    }
}
