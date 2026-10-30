package osrsps.game.social

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.social.MessagePrivate
import net.rsprot.protocol.game.outgoing.social.MessagePrivateEcho
import osrsps.game.ChatFilters
import osrsps.game.Player
import osrsps.game.dialogue.Line
import osrsps.game.social.FriendLists.Companion.sameName

private val logger = KotlinLogging.logger {}

/**
 * Private messages between players. The client lets a player message their friends and anyone who messaged them; the
 * server delivers the message if the recipient shows as online to the sender (their private chat setting, see
 * [FriendLists.showsOnline]) and hasn't ignored them (OSRS Wiki, Ignore List), and otherwise answers as if the
 * recipient were offline, so ignoring someone can't be told apart from being out.
 */
class PrivateMessages(
    private val friends: FriendLists,
    private val ignores: IgnoreLists,
    /** Everyone in the world. */
    private val online: () -> List<Player>,
    /** Sends a game message. */
    private val message: (Player, Line) -> Unit,
    /** Sends the player's chat filters to their client. */
    private val sendFilters: (Player) -> Unit,
    /**
     * Where the message counter starts. The client drops a message whose world and counter it saw in its last 100, so
     * each start of the server begins somewhere new: RSProt suggests the hour of the year times 50,000.
     */
    counterStart: Int,
) {
    private var counter = counterStart and COUNTER_MASK

    /** [sender] typed [text] to [name]. */
    fun send(sender: Player, name: String, text: String) {
        // The client stops typing at 80 characters, as in public chat; anything longer didn't come from it.
        val line = text.take(MAX_LENGTH)
        if (line.isBlank()) return
        // Inferred from RS: sending a message with private chat Off turns it to Friends, so the reply can come back.
        if (sender.chatFilters.private == FriendLists.PRIVATE_OFF) {
            val old = sender.chatFilters.private
            sender.chatFilters = ChatFilters(sender.chatFilters.public, FriendLists.PRIVATE_FRIENDS, sender.chatFilters.trade)
            sendFilters(sender)
            friends.privateFilterChanged(sender, old)
        }
        val recipient = online().firstOrNull { sameName(it.username, name.trim()) }
        if (recipient == null || recipient === sender || !friends.showsOnline(recipient, sender) || ignores.ignores(recipient, sender)) {
            message(sender, SocialLines.PLAYER_OFFLINE)
            return
        }
        logger.info { "${sender.username} -> ${recipient.username}: $line" }
        val shownAs = sender.friends.firstOrNull { sameName(it, recipient.username) } ?: recipient.username
        sender.session.queue(MessagePrivateEcho(shownAs, line))
        recipient.session.queue(MessagePrivate(sender.username, FriendLists.WORLD, nextCounter(), FriendLists.RANK, line))
    }

    /** The next message counter, for private messages and friends chat alike. */
    fun nextCounter(): Int {
        counter = (counter + 1) and COUNTER_MASK
        return counter
    }

    companion object {
        const val MAX_LENGTH = 80

        /** RSProt's suggested counter start for a server started at [now]: the hour of the year times 50,000. */
        fun counterStart(now: java.time.Instant): Int {
            val time = now.atZone(java.time.ZoneOffset.UTC)
            return ((time.dayOfYear - 1) * 24 + time.hour) * 50_000
        }

        /** Only the counter's low 24 bits go out (RSProt's MessagePrivate). */
        const val COUNTER_MASK = 0xFFFFFF
    }
}
