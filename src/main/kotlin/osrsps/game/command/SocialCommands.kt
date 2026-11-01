package osrsps.game.command

import osrsps.game.World
import osrsps.game.social.FriendLists
import osrsps.game.social.Reports

/** The friends and ignore lists as the server holds them, for tests that start from known lists. */
fun CommandRegistry.registerSocialCommands(world: World) {
    val friends = world.friendLists

    register("friends", "::friends", admin = false) {
        if (args.isNotEmpty()) fail("Usage: ::friends")
        if (player.friends.isEmpty()) {
            reply("Your friends list is empty")
            return@register
        }
        reply(
            player.friends.joinToString(prefix = "Friends: ") { name ->
                val friend = world.playerNamed(name)
                name + if (friend != null && friends.showsOnline(friend, player)) " (world ${FriendLists.WORLD})" else " (offline)"
            },
        )
    }

    register("clearfriends", "::clearfriends") {
        if (args.isNotEmpty()) fail("Usage: ::clearfriends")
        friends.clear(player)
        // As each delete does: the ranks given to them go too.
        world.friendChats.ownerFriendsChanged(player)
        reply("Friends list emptied. Log out and in to see it in the Friends tab")
    }

    register("reports", "::reports") {
        if (args.isNotEmpty()) fail("Usage: ::reports")
        val reports = world.reports.recent()
        if (reports.isEmpty()) {
            reply("No reports since the server started")
            return@register
        }
        // The newest few; the file on the server (data/reports.jsonl) has them all, with the chat lines.
        for (report in reports.takeLast(5)) {
            reply("${report.reporter} reported ${report.name}: ${Reports.RULES[report.rule]} (${report.chat.size} chat lines)")
        }
    }

    register("ignores", "::ignores", admin = false) {
        if (args.isNotEmpty()) fail("Usage: ::ignores")
        reply(if (player.ignores.isEmpty()) "Your ignore list is empty" else player.ignores.joinToString(prefix = "Ignored: "))
    }

    register("clearignores", "::clearignores") {
        if (args.isNotEmpty()) fail("Usage: ::clearignores")
        world.ignoreLists.clear(player)
        reply("Ignore list emptied")
    }
}
