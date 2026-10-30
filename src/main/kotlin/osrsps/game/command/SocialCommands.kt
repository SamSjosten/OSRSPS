package osrsps.game.command

import osrsps.game.World
import osrsps.game.social.FriendLists

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
        reply("Friends list emptied. Log out and in to see it in the Friends tab")
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
