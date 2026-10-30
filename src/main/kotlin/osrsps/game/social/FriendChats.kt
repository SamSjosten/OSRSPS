package osrsps.game.social

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.friendchat.MessageFriendChannel
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelFull
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelFullV2
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelSingleUser
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import osrsps.game.Player
import osrsps.game.SaveStore
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.social.FriendLists.Companion.sameName

private val logger = KotlinLogging.logger {}

/**
 * A player's own friends chat channel, kept in their save: its name (null while the channel is disabled), who may
 * enter, talk and kick, and the ranks given to friends. New channels let anyone enter and talk and only the owner kick
 * (inferred: the setup screen's first options, and the Wiki's "Who can kick" needing Corporal or above).
 */
data class FriendsChatSettings(
    val name: String? = null,
    val enter: Int = FriendChats.RANK_ANYONE,
    val talk: Int = FriendChats.RANK_ANYONE,
    val kick: Int = FriendChats.RANK_OWNER,
) {
    companion object {
        val DEFAULT = FriendsChatSettings()
    }
}

/**
 * Friends chat channels (OSRS Wiki, Friends chat): a player joins the channel of its owner by typing the owner's name
 * (the Join Chat button's prompt, client script joinchat_onop), talks in it with lines starting with `/` (public chat
 * of type 2), and leaves with Leave Chat. Each member's rank comes from the owner: owner, a rank given to a friend
 * (step 9), Friend for anyone else on the owner's friends list, or none.
 *
 * A channel is kept while anyone is in it and dropped when it empties, which on live resets it. The owner need not be
 * online: their settings and friends come from their save (inferred; the Wiki doesn't say). A player in a channel at
 * logout tries to join it again at login (Wiki, 30 January 2014 update).
 *
 * Notifications go out as friends chat notifications (chat type 11), so they show in the chatbox's Channel tab.
 */
class FriendChats(
    /** Everyone in the world, a dropped connection waiting for a reconnect included. */
    private val online: () -> List<Player>,
    /** The owner whose display name or save key is [name]: their settings and friends, from them or their save. */
    private val owner: (String) -> ChannelOwner?,
    private val ignores: IgnoreLists,
    private val words: (Player) -> DialogueText,
    /** The next message counter, shared with private messages: the client drops a world and counter it has seen. */
    private val nextCounter: () -> Int,
) {
    /** An owner as a channel needs them. */
    class ChannelOwner(val name: String, val settings: FriendsChatSettings, val friends: List<String>)

    /** An open channel, keyed by its owner's save key. */
    class Channel(val ownerKey: String, var ownerName: String, var settings: FriendsChatSettings, var ownerFriends: List<String>) {
        val members = ArrayList<Player>()
    }

    private val channels = HashMap<String, Channel>()

    /** The channel [player] is in, or null. */
    fun channelOf(player: Player): Channel? = channels.values.firstOrNull { player in it.members }

    /** The name typed into Join Chat's prompt; or, from [rejoin], the channel the player was in at logout. */
    fun join(player: Player, typed: String) {
        val name = typed.trim()
        if (name.isEmpty() || name.length > FriendLists.MAX_NAME_LENGTH) return
        // The client offers Leave Chat while in one; a join anyway moves the player.
        if (channelOf(player) != null) leave(player, quiet = true)
        notify(player, FriendsChatLines.JOINING)
        val key = SaveStore.saveKey(name)
        val channel = key?.let { channels[it] ?: open(it, name) }
        if (channel == null) {
            notify(player, FriendsChatLines.NO_CHANNEL)
            return
        }
        if (rank(channel, player) < channel.settings.enter) {
            notify(player, FriendsChatLines.NOT_ALLOWED)
            if (channel.members.isEmpty()) channels.remove(channel.ownerKey)
            return
        }
        if (channel.members.size >= MAX_MEMBERS) {
            // Inferred words; the Wiki's bumping of lower ranks from a full channel isn't built.
            notify(player, FriendsChatLines.FULL)
            return
        }
        channel.members += player
        channels[channel.ownerKey] = channel
        player.friendsChatOwner = channel.ownerName
        player.session.queue(fullUpdate(channel))
        val added = UpdateFriendChatChannelSingleUser(added(channel, player))
        for (member in channel.members) if (member !== player) member.session.queue(added)
        notify(player, FriendsChatLines.NOW_TALKING, "name" to channel.settings.name!!)
        notify(player, FriendsChatLines.HOW_TO_TALK)
        logger.info { "${player.username} joined ${channel.ownerName}'s friends chat" }
    }

    /** Leave Chat ([quiet] when the player is moving to another channel or logging out). */
    fun leave(player: Player, quiet: Boolean = false) {
        val channel = channelOf(player) ?: return
        channel.members -= player
        if (!quiet) player.friendsChatOwner = null
        if (!player.loggedOut) player.session.queue(UpdateFriendChatChannelFullV2(UpdateFriendChatChannelFullV2.LeaveUpdate))
        val removed = UpdateFriendChatChannelSingleUser(UpdateFriendChatChannelSingleUser.RemovedFriendChatUser(player.username, FriendLists.WORLD))
        for (member in channel.members) member.session.queue(removed)
        if (channel.members.isEmpty()) channels.remove(channel.ownerKey)
        if (!quiet) notify(player, FriendsChatLines.LEFT)
    }

    /** [player] is leaving the world: out of the channel, remembered for the next login. */
    fun logout(player: Player) = leave(player, quiet = true)

    /**
     * After a reconnect: the channel's whole list again, or, out of every channel, the leave, in case the player was
     * sent out (a disabled channel) while the old connection was dying.
     */
    fun resend(player: Player) {
        val channel = channelOf(player)
        player.session.queue(if (channel != null) fullUpdate(channel) else UpdateFriendChatChannelFullV2(UpdateFriendChatChannelFullV2.LeaveUpdate))
    }

    /** At login: back into the channel the player was in at logout. */
    fun rejoin(player: Player) {
        player.friendsChatOwner?.let { join(player, it) }
    }

    /** A line of chat starting with `/`: to everyone in the player's channel who doesn't ignore them. */
    fun talk(player: Player, text: String) {
        val channel = channelOf(player)
        if (channel == null) {
            notify(player, FriendsChatLines.NOT_IN_CHANNEL)
            return
        }
        val line = text.take(MAX_LENGTH)
        if (line.isBlank()) return
        if (rank(channel, player) < channel.settings.talk) {
            notify(player, FriendsChatLines.CANT_TALK)
            return
        }
        val message = MessageFriendChannel(player.username, channel.settings.name!!, FriendLists.WORLD, nextCounter(), FriendLists.RANK, line)
        for (member in channel.members) {
            if (ignores.ignores(member, player)) continue
            member.session.queue(message)
        }
    }

    /**
     * The owner set their channel's name on the setup screen. Members already in see the new name; a channel that was
     * disabled can be joined again.
     */
    fun setName(owner: Player, name: String) {
        owner.friendsChat = owner.friendsChat.copy(name = name)
        val channel = channels[owner.saveKey] ?: return
        channel.settings = owner.friendsChat
        broadcastFull(channel)
    }

    /** The owner disabled their channel: everyone in it is sent out (words guessed), and nobody can join. */
    fun disable(owner: Player) {
        owner.friendsChat = owner.friendsChat.copy(name = null)
        val channel = channels.remove(owner.saveKey) ?: return
        for (member in channel.members.toList()) {
            channel.members -= member
            member.friendsChatOwner = null
            member.session.queue(UpdateFriendChatChannelFullV2(UpdateFriendChatChannelFullV2.LeaveUpdate))
            notify(member, FriendsChatLines.DISABLED)
        }
    }

    /** The owner's friends list changed, so the ranks their friends hold do too. */
    fun ownerFriendsChanged(owner: Player) {
        val channel = channels[owner.saveKey] ?: return
        channel.ownerFriends = owner.friends.toList()
        broadcastFull(channel)
    }

    /** Fills in the setup screen's options as [owner]'s settings are. */
    fun showSetup(owner: Player) {
        val settings = owner.friendsChat
        val text = words(owner)
        val prefix = settings.name ?: text[FriendsChatLines.SETUP_DISABLED]
        owner.session.queue(IfSetText(SETUP, SETUP_PREFIX, prefix))
        owner.session.queue(IfSetText(SETUP, SETUP_ENTER, text[optionLine(settings.enter)]))
        owner.session.queue(IfSetText(SETUP, SETUP_TALK, text[optionLine(settings.talk)]))
        owner.session.queue(IfSetText(SETUP, SETUP_KICK, text[optionLine(settings.kick)]))
    }

    /** Whether [name] can be a channel's name: 1 to 12 letters, digits and spaces, not only spaces (guessed). */
    fun validName(name: String): Boolean =
        name.length in 1..FriendLists.MAX_NAME_LENGTH && name.isNotBlank() && name.all { it.isLetterOrDigit() || it == ' ' }

    private fun open(key: String, typed: String): Channel? {
        val owner = owner(typed) ?: return null
        if (owner.settings.name == null) return null
        return Channel(key, owner.name, owner.settings, owner.friends)
    }

    /** [player]'s rank in [channel]: owner, a friend of the owner, or none. Pass [friends] when ranking many at once. */
    fun rank(channel: Channel, player: Player, friends: List<String> = ownerFriends(channel)): Int = when {
        player.saveKey == channel.ownerKey -> RANK_OWNER
        friends.any { sameName(it, player.username) } -> RANK_FRIEND
        else -> RANK_NONE
    }

    /** The owner's friends now: from the owner while they are here, else as they were when the channel opened. */
    private fun ownerFriends(channel: Channel): List<String> =
        online().firstOrNull { it.saveKey == channel.ownerKey }?.friends ?: channel.ownerFriends

    /** The whole list to everyone in [channel], built once: a channel can hold 500. */
    private fun broadcastFull(channel: Channel) {
        val update = fullUpdate(channel)
        for (member in channel.members) member.session.queue(update)
    }

    private fun fullUpdate(channel: Channel): UpdateFriendChatChannelFullV2 {
        // The owner's friends are looked up once, not once a member.
        val friends = ownerFriends(channel)
        return UpdateFriendChatChannelFullV2(
            UpdateFriendChatChannelFullV2.JoinUpdate(
                channel.ownerName, channel.settings.name!!, channel.settings.kick,
                channel.members.map { UpdateFriendChatChannelFull.FriendChatEntry(it.username, FriendLists.WORLD, rank(channel, it, friends), "") },
            ),
        )
    }

    private fun added(channel: Channel, player: Player) =
        UpdateFriendChatChannelSingleUser.AddedFriendChatUser(player.username, FriendLists.WORLD, rank(channel, player), "")

    private fun optionLine(rank: Int): Line = when (rank) {
        RANK_ANYONE -> FriendsChatLines.OPTION_ANYONE
        RANK_FRIEND -> FriendsChatLines.OPTION_FRIENDS
        RANK_OWNER -> FriendsChatLines.OPTION_ONLY_ME
        else -> FriendsChatLines.OPTION_RANK_PLUS
    }

    private fun notify(player: Player, line: Line, vararg values: Pair<String, Any>) {
        if (player.loggedOut) return
        player.session.queue(MessageGame(CHATTYPE_FRIENDSCHATNOTIFICATION, words(player).get(line, *values)))
    }

    companion object {
        /** chatchannel_current, the Chat-channel tab's friends chat panel, opened inside side_channels' contents. */
        const val CURRENT = 7
        const val CURRENT_SETUP = 20
        const val SIDE_CHANNELS = 707
        const val SIDE_CHANNELS_CONTENTS = 7

        /** chatchannel_setup: the channel's name and who may enter, talk and kick. */
        const val SETUP = 94
        const val SETUP_PREFIX = 10
        const val SETUP_ENTER = 13
        const val SETUP_TALK = 16
        const val SETUP_KICK = 19

        /**
         * Ranks, as the client's friends chat packets carry them: none (not on the owner's list), Friend, Recruit to
         * General (1 to 6), owner. A setting's "Anyone" is [RANK_ANYONE], below every rank.
         */
        const val RANK_ANYONE = -2
        const val RANK_NONE = -1
        const val RANK_FRIEND = 0
        const val RANK_OWNER = 7

        /** OSRS Wiki, Friends chat: a channel holds 500. */
        const val MAX_MEMBERS = 500

        /** As public chat: the client stops typing at 80 characters. */
        const val MAX_LENGTH = 80

        /** chattype_friendschatnotification in the 241 cache. */
        const val CHATTYPE_FRIENDSCHATNOTIFICATION = 11
    }
}

object FriendsChatLines : Lines("friends_chat", "Friends chat") {
    val JOINING = line("joining", "Join Chat, before the answer")
    val NO_CHANNEL = line("no_channel", "joining a name with no channel, or a disabled one")
    val NOT_ALLOWED = line("not_allowed", "joining a channel whose entry rank is above yours")
    val FULL = line("full", "joining a channel of 500; words guessed")
    val NOW_TALKING = line("now_talking", "joined a channel", "name")
    val HOW_TO_TALK = line("how_to_talk", "joined: how to talk in the channel")
    val LEFT = line("left", "Leave Chat")
    val NOT_IN_CHANNEL = line("not_in_channel", "a / line while in no channel")
    val CANT_TALK = line("cant_talk", "a / line in a channel whose talk rank is above yours; words guessed")
    val DISABLED = line("disabled", "the owner disabled the channel you were in; words guessed")
    val SETUP_DISABLED = line("setup_disabled", "the setup screen's channel name while the channel is disabled")
    val SET_PREFIX = line("set_prefix", "the prompt for a new channel name")
    val BAD_NAME = line("bad_name", "a channel name that isn't 1 to 12 letters, digits and spaces; words guessed")
    val OPTION_ANYONE = line("option_anyone", "a setup option: Anyone")
    val OPTION_FRIENDS = line("option_friends", "a setup option: Any friends")
    val OPTION_RANK_PLUS = line("option_rank_plus", "a setup option: a rank and above")
    val OPTION_ONLY_ME = line("option_only_me", "a setup option: Only me")
}
