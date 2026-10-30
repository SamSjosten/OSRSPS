package osrsps.game

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.incoming.messaging.MessagePublic

private val logger = KotlinLogging.logger {}

/**
 * A line of public chat, checked and ready for the player's chat extended info. The client shows it over the
 * speaker's head and in the chatbox of everyone who can see them, the speaker included.
 */
class PublicChat(
    val text: String,
    /** 0 to 20: yellow, red, green, ... rainbow (12), then patterns of 1 to 8 colours (13 to 20). */
    val colour: Int,
    /** 0 to 5: none, wave, wave2, shake, scroll, slide. */
    val effect: Int,
    /** One colour code per pattern step when [colour] is 13 to 20, otherwise null. */
    val pattern: ByteArray?,
    val autotyper: Boolean,
) {
    companion object {
        const val MAX_LENGTH = 80

        /** A line starting with `/`, for the player's friends chat channel; see [osrsps.game.social.FriendChats]. */
        const val TYPE_FRIENDS_CHAT = 2

        private const val TYPE_NORMAL = 0
        private const val TYPE_AUTOTYPER = 1
        private const val MAX_COLOUR = 20
        private const val FIRST_PATTERN_COLOUR = 13
        private const val MAX_EFFECT = 5

        /**
         * Reads a [MessagePublic], or returns null for the chat-channel types (2 to 4), which aren't public chat.
         *
         * Guessed, not checked against live: a colour, effect or pattern the client can't have sent is dropped to
         * plain yellow with no effect instead of rejecting the line. A bad pattern can crash the clients that see it,
         * so it never goes out.
         */
        fun from(message: MessagePublic): PublicChat? {
            val autotyper = when (message.type) {
                TYPE_NORMAL -> false
                TYPE_AUTOTYPER -> true
                else -> return null
            }
            var colour = message.colour.takeIf { it <= MAX_COLOUR } ?: 0
            var pattern: ByteArray? = null
            if (colour >= FIRST_PATTERN_COLOUR) {
                val sent = message.pattern
                if (sent != null && sent.isValid() && sent.length == colour - FIRST_PATTERN_COLOUR + 1) {
                    pattern = sent.toByteArray()
                } else {
                    colour = 0
                }
            }
            val effect = message.effect.takeIf { it <= MAX_EFFECT } ?: 0
            // The client stops typing at 80 characters; anything longer didn't come from it.
            return PublicChat(message.message.take(MAX_LENGTH), colour, effect, pattern, autotyper)
        }
    }
}

/** [player] typed [message] in the chatbox without a `::` in front. Runs in a tick's input phase, see [World.onPublicChat]. */
fun sayPublicChat(player: Player, message: MessagePublic) {
    val chat = PublicChat.from(message)
    if (chat == null) {
        // Guessed wording: there are no friends, clan or guest channels to talk in yet.
        player.message("There are no chat-channels on this server yet.")
        return
    }
    logger.info { "${player.username}: ${chat.text}" }
    // Mod icon 0: nobody on a test server wears a crown.
    player.infos.playerInfo.avatar.extendedInfo.setChat(chat.colour, chat.effect, 0, chat.autotyper, chat.text, chat.pattern)
}
