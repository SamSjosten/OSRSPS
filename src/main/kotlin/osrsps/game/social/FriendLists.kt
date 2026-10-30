package osrsps.game.social

import net.rsprot.protocol.game.outgoing.social.FriendListLoaded
import net.rsprot.protocol.game.outgoing.social.UpdateFriendList
import osrsps.game.Player
import osrsps.game.SaveStore
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/**
 * The friends list: who a player has added, kept in their save, and whether each friend shows as online.
 *
 * The client does most of it itself. Its Add Friend box refuses the player's own name, a name already on the list and a
 * full list with its own messages, and Delete takes the name off its list before it tells us (client scripts
 * friend_op and friend_update, osrs-dumps). The server keeps the list, refuses a name that isn't an account, and tells
 * each player when a friend comes online, goes offline or hides.
 *
 * Whether a friend shows as online follows their private chat setting (OSRS Wiki, Friends List): On shows them to
 * everyone who has added them, Friends only to players on their own list, Off to nobody.
 */
class FriendLists(
    /** Everyone in the world, a dropped connection waiting for a reconnect included. */
    private val online: () -> List<Player>,
    /** The display name of the account [name] is, or null when there is no such account. */
    private val accountName: (String) -> String?,
    /** Sends a game message. */
    private val message: (Player, Line) -> Unit,
) {
    /** Sends [player]'s whole list at login (or after a reconnect), and tells their friends they are here. */
    fun login(player: Player) {
        sendAll(player)
        changed(player)
    }

    /** Sends [player]'s whole list again, every friend as it is now. */
    fun sendAll(player: Player) {
        if (player.friends.isEmpty()) {
            player.session.queue(FriendListLoaded)
            return
        }
        // Not just added: the client skips its checks for friends it already had (RSProt's UpdateFriendList).
        player.session.queue(UpdateFriendList(player.friends.map { entry(player, it, added = false) }))
    }

    /** Tells everyone who has [player] on their list how [player] shows to them now. After a logout too. */
    fun changed(player: Player) {
        for (other in online()) {
            if (other === player || other.loggedOut) continue
            val name = other.friends.firstOrNull { sameName(it, player.username) } ?: continue
            other.session.queue(UpdateFriendList(listOf(entry(other, name, added = false))))
        }
    }

    /** The Add Friend box, or Add friend on a name in the chatbox. */
    fun add(player: Player, typed: String) {
        val name = typed.trim()
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) return
        // Inferred: the client has already refused these with its own messages, so the server just ignores them.
        if (sameName(name, player.username)) return
        if (player.friends.any { sameName(it, name) }) return
        if (player.friends.size >= MAX_FRIENDS) return
        val display = accountName(name)
        if (display == null) {
            message(player, SocialLines.UNKNOWN_FRIEND)
            return
        }
        player.friends += display
        player.session.queue(UpdateFriendList(listOf(entry(player, display, added = true))))
        // A player who shows only to friends now shows to the one they just added.
        refresh(player, display)
    }

    /** Delete on a friend, or the Del Friend box. The client has already taken them off its list. */
    fun delete(player: Player, typed: String) {
        val name = typed.trim()
        if (!player.friends.removeIf { sameName(it, name) }) return
        refresh(player, name)
    }

    /** [player]'s private chat setting changed from [old]: their friends may see them differently now. */
    fun privateFilterChanged(player: Player, old: Int) {
        if (old != player.chatFilters.private) changed(player)
    }

    /** Clears [player]'s list, for `::clearfriends`. The client is sent an empty list by the relog that follows. */
    fun clear(player: Player) {
        val names = player.friends.toList()
        player.friends.clear()
        for (name in names) refresh(player, name)
    }

    /** Whether [player] shows as online to [viewer]. */
    fun showsOnline(player: Player, viewer: Player): Boolean {
        if (player.loggedOut) return false
        return when (player.chatFilters.private) {
            PRIVATE_ON -> true
            PRIVATE_FRIENDS -> player.friends.any { sameName(it, viewer.username) }
            else -> false
        }
    }

    /** Tells the player [name], if online, how [player] shows to them now that [player]'s list changed. */
    private fun refresh(player: Player, name: String) {
        val other = online().firstOrNull { sameName(it.username, name) } ?: return
        if (other.loggedOut) return
        val entry = other.friends.firstOrNull { sameName(it, player.username) } ?: return
        other.session.queue(UpdateFriendList(listOf(entry(other, entry, added = false))))
    }

    /**
     * [name] on [viewer]'s list as [viewer] sees them, under the name it was added as: logins here aren't case-exact,
     * and the client matches a friend's updates by name.
     */
    private fun entry(viewer: Player, name: String, added: Boolean): UpdateFriendList.Friend {
        val friend = online().firstOrNull { sameName(it.username, name) }
        if (friend == null || !showsOnline(friend, viewer)) return UpdateFriendList.OfflineFriend(added, name, null, RANK, 0, "")
        return UpdateFriendList.OnlineFriend(added, name, null, WORLD, RANK, 0, "", WORLD_NAME, PLATFORM_OSRS, WORLD_FLAGS)
    }

    companion object {
        /** Members may have 400 friends, free players 200 (OSRS Wiki, Friends List). Our one world is a members world. */
        const val MAX_FRIENDS = 400

        /** Display names are at most 12 characters on live. */
        const val MAX_NAME_LENGTH = 12

        /** The private chat setting, as the chat filter packet carries it: On, Friends, Off. */
        const val PRIVATE_ON = 0
        const val PRIVATE_FRIENDS = 1

        /** Our one world, as the world list names it (ClientConfig.WORLD_ID). */
        const val WORLD = 301

        /** Inferred from RSProt's example ("Old School 35" for world 335). The OSRS client doesn't show it. */
        const val WORLD_NAME = "Old School 1"

        /** Members, as the world list says (ClientConfig.WORLD_PROPERTIES). */
        const val WORLD_FLAGS = 1

        /** RSProt: 8 is Old School. The OSRS client doesn't read it. */
        const val PLATFORM_OSRS = 8

        /** The friend's chat icon. Nobody wears a crown here, as in public chat. */
        const val RANK = 0

        /** Names match the way save names do: case, and spaces against underscores, don't matter. */
        fun sameName(a: String, b: String): Boolean = SaveStore.saveKey(a) != null && SaveStore.saveKey(a) == SaveStore.saveKey(b)
    }
}

/** The friends and ignore lists' game messages. */
object SocialLines : Lines("social", "Friends List") {
    val UNKNOWN_FRIEND = line("unknown_friend", "adding a name that isn't an account; words from memory")
}
