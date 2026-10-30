package osrsps.game.social

import net.rsprot.protocol.game.outgoing.social.UpdateIgnoreList
import osrsps.game.Player
import osrsps.game.dialogue.Line
import osrsps.game.social.FriendLists.Companion.sameName

/**
 * The ignore list: the names a player has ignored, kept in their save.
 *
 * As with friends, the client checks first: its Add Name box refuses the player's own name, a name already ignored, a
 * friend and a full list, and Delete takes the name off its own list (client scripts ignore_op and ignore_update,
 * osrs-dumps). The client also hides an ignored player's public chat itself. Private messages and trade requests from
 * them are refused by the server, in their own steps.
 *
 * Ignoring someone doesn't make you show as offline to them (OSRS Wiki, Ignore List).
 */
class IgnoreLists(
    /** The display name of the account [name] is, or null when there is no such account. */
    private val accountName: (String) -> String?,
    /** Sends a game message. */
    private val message: (Player, Line) -> Unit,
) {
    /** Sends [player]'s whole list, at login or after a reconnect. Before the friends list, which marks both loaded. */
    fun sendAll(player: Player) {
        if (player.ignores.isEmpty()) return
        // Not just added: the client skips its checks for names it already had (RSProt's UpdateIgnoreList).
        player.session.queue(UpdateIgnoreList(player.ignores.map { UpdateIgnoreList.AddedIgnoredEntry(it, null, "", false) }))
    }

    /** The Add Name box, or Add ignore on a name in the chatbox. */
    fun add(player: Player, typed: String) {
        val name = typed.trim()
        if (name.isEmpty() || name.length > FriendLists.MAX_NAME_LENGTH) return
        // Inferred: the client has already refused these with its own messages, so the server just ignores them.
        if (sameName(name, player.username)) return
        if (player.ignores.any { sameName(it, name) }) return
        if (player.friends.any { sameName(it, name) }) return
        if (player.ignores.size >= MAX_IGNORES) return
        val display = accountName(name)
        if (display == null) {
            message(player, SocialLines.UNKNOWN_IGNORE)
            return
        }
        player.ignores += display
        player.session.queue(UpdateIgnoreList(listOf(UpdateIgnoreList.AddedIgnoredEntry(display, null, "", true))))
    }

    /** Delete on an ignored name, or the Del Name box. The client has already taken it off its list. */
    fun delete(player: Player, typed: String) {
        player.ignores.removeIf { sameName(it, typed.trim()) }
    }

    /** Clears [player]'s list, for `::clearignores`, and tells the client. */
    fun clear(player: Player) {
        if (player.ignores.isEmpty()) return
        player.session.queue(UpdateIgnoreList(player.ignores.map { UpdateIgnoreList.RemovedIgnoredEntry(it) }))
        player.ignores.clear()
    }

    /** Whether [player] has ignored [other]. */
    fun ignores(player: Player, other: Player): Boolean = player.ignores.any { sameName(it, other.username) }

    companion object {
        /** Members may ignore 400 names, free players 100 (OSRS Wiki, Ignore List). Our one world is a members world. */
        const val MAX_IGNORES = 400

        /** The Friends tab's ignore button, friends:ignore, and the ignore tab's friends button, ignore:friends. */
        const val FRIENDS_TAB = 429
        const val FRIENDS_TAB_IGNORE_BUTTON = 1
        const val IGNORE_TAB = 432
        const val IGNORE_TAB_FRIENDS_BUTTON = 1
    }
}
