package osrsps.game.social

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.clan.ClanChannelFull
import net.rsprot.protocol.game.outgoing.clan.ClanSettingsFull
import net.rsprot.protocol.game.outgoing.clan.MessageClanChannel
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.SetPlayerOp
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.Player
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

/**
 * Clans (the 2021 system; OSRS Wiki, Clans, and the update post "A Guide to the Clans System"), step 10a: founding a
 * clan at the Clan Hub, your clan in the Clans tab, and its chat.
 *
 * Founding: in the Clan Hub (the south-east corner of the Grand Exchange) a player who isn't in a clan sees the clan
 * creation panel (clans_creation_sidepanel, 705) in the Clans tab's "Your Clan" page. Its leader invites founders
 * with the panel's Invite button (target mode on a player, OPPLAYERT) or the invitee's own player option 1; the
 * invitee gets a clan creation invitation in the chatbox, whose "Form clan" sends option 1 on the leader back (client
 * script chat_op_core), which joins them. The leader names the clan (up to 12 characters; the name is only checked,
 * not kept for them) and confirms: that needs [ClanFoundingRule.founders] founders who have each played
 * [ClanFoundingRule.hours] hours, a name no clan has, and no clan founded by the leader today. Cancel ends the group
 * (the leader) or leaves it (a founder); leaving the Hub does the same. Live's rule is 5 founders and 25 hours.
 *
 * Your clan: members get the clan's settings and channel (CLANSETTINGS_FULL and CLANCHANNEL_FULL, clan type 0), which
 * the client's clans_sidepanel (701) draws, and join its channel at login (inferred). Lines typed with `//` or `/c`
 * (MESSAGE_PUBLIC type 3, clan type 0) go to everyone in the channel as MESSAGE_CLANCHANNEL. The panel's Chat/Leave
 * button joins and leaves the channel.
 *
 * Guessed: the Hub's edges, the founders' rank after founding (the lowest member rank, 0; the founder is owner, 126),
 * the creation time and join day encodings, and every game message (keys in [ClanLines], words from the Wiki where it
 * has them). Not built yet: inviting after founding, leaving the clan, ranks and permissions, settings, kicks and
 * bans, guest chat (steps 10b and 11).
 */
class Clans(
    private val store: ClanStore,
    val rule: ClanFoundingRule,
    private val send: (Player, OutgoingGameMessage) -> Unit,
    private val setVarbit: (Player, Int, Int) -> Unit,
    /** Opens an interface in the Clans tab's contents, in place of what is there. */
    private val openContents: (Player, Int) -> Unit,
    /** IF_SETEVENTS_V2: the player, interface, component, first and last slot, op bits, then the first word's bits. */
    private val setEvents: (Player, Int, Int, Int, Int, Int, Int) -> Unit,
    private val runClientScript: (Player, Int, List<Any>) -> Unit,
    private val words: (Player) -> DialogueText,
    private val ignores: IgnoreLists,
    /** Whether [Player] is still in the world, a dropped connection waiting for a reconnect included. */
    private val online: (Player) -> Boolean,
    /** The next message counter, shared with private and friends chat messages. */
    private val nextCounter: () -> Int,
    private val now: () -> Instant = Instant::now,
) {
    /** A clan being formed: its leader, the founders who joined, the invited and the name picked. */
    class Forming(val leader: Player) {
        val founders = ArrayList<Player>()
        val invited = HashSet<String>()
        var name: String? = null
    }

    /** Groups being formed, by their leader's save key. */
    private val forming = HashMap<String, Forming>()

    /** Online players in each clan's channel, by clan id. */
    private val channels = HashMap<Long, MutableList<Player>>()

    /** The Clans tab page each online player has open (0 chat-channel, 1 your clan, 2 another clan, 3 grouping). */
    private val tabs = HashMap<String, Int>()

    /** Players who were in the Clan Hub at their last [process]. */
    private val inHub = HashSet<String>()

    /**
     * The group name client script 4447 last gave each player's client, which keeps it (varc clan_create_name) and
     * shows it again whenever the creation panel opens; absent while it keeps none.
     */
    private val shownName = HashMap<String, String>()

    private var updateNum = 0L

    fun clanOf(player: Player): ClanStore.Clan? = store.clanOf(player.saveKey)

    fun inChannel(player: Player): Boolean = clanOf(player)?.let { channels[it.id]?.contains(player) } == true

    /** The group [player] leads or joined, or null. */
    fun formingOf(player: Player): Forming? = forming[player.saveKey] ?: forming.values.firstOrNull { player in it.founders }

    /** At login, after the game frame: their clan, or that they have none, and its channel. */
    fun login(player: Player) {
        // The game frame opens the chat-channel page at login; the saved tab selection is set to match (inferred).
        tabs[player.saveKey] = TAB_CHAT_CHANNEL
        setVarbit(player, VARBIT_TAB_SELECTED, TAB_CHAT_CHANNEL)
        val clan = clanOf(player)
        setVarbit(player, VARBIT_GUEST_AFFINITY, AFFINITY_NONE)
        if (clan == null) {
            setVarbit(player, VARBIT_AFFINITY, AFFINITY_NONE)
            return
        }
        clan.member(player.saveKey)!!.name = player.username
        setVarbit(player, VARBIT_AFFINITY, AFFINITY_CONNECTED)
        send(player, settingsFull(clan))
        joinChannel(player, clan)
    }

    /** A reconnect: everything clan the old connection may have lost, after the player options are sent again. */
    fun resync(player: Player) {
        showRecruit(player)
        setVarbit(player, VARBIT_GUEST_AFFINITY, AFFINITY_NONE)
        val clan = clanOf(player)
        if (clan == null) {
            setVarbit(player, VARBIT_AFFINITY, AFFINITY_NONE)
            send(player, ClanSettingsFull(CLAN_TYPE_CLAN, ClanSettingsFull.LeaveUpdate))
            send(player, ClanChannelFull(CLAN_TYPE_CLAN, ClanChannelFull.LeaveUpdate))
        } else {
            setVarbit(player, VARBIT_AFFINITY, AFFINITY_CONNECTED)
            send(player, settingsFull(clan))
            val members = channels[clan.id]
            send(player, if (members != null && player in members) channelFull(clan, members) else ClanChannelFull(CLAN_TYPE_CLAN, ClanChannelFull.LeaveUpdate))
        }
        // The old connection may have missed page changes and name updates: the name the client keeps is unknown now,
        // so the creation panel's next showForming sends the group's name, or clears it; and the page opens again.
        shownName[player.saveKey] = UNKNOWN_NAME
        if (tabs[player.saveKey] == TAB_YOUR_CLAN) openYourClan(player)
    }

    /** Writes the clans again if a write failed; see [ClanStore.flush]. */
    fun flush() = store.flush()

    fun logout(player: Player) {
        leaveForming(player)
        dropInvitations(player)
        clanOf(player)?.let { leaveChannel(player, it, tell = false) }
        tabs.remove(player.saveKey)
        inHub.remove(player.saveKey)
        shownName.remove(player.saveKey)
    }

    /** Every tick: walking into or out of the Clan Hub changes the "Your Clan" page of someone without a clan. */
    fun process(player: Player) {
        val now = inHub(player)
        val was = player.saveKey in inHub
        if (now == was) return
        if (now) inHub += player.saveKey else inHub -= player.saveKey
        if (!now) {
            leaveForming(player)
            dropInvitations(player)
        }
        showRecruit(player)
        if (tabs[player.saveKey] == TAB_YOUR_CLAN && clanOf(player) == null) openYourClan(player)
    }

    /**
     * Option 1 on players, "Recruit", while in the Hub without a clan (Wiki, Clans: "right-click a player in the Clan
     * Hub and choose Recruit"); it invites them, or accepts their invitation as its "Form clan" does.
     */
    private fun showRecruit(player: Player) {
        send(player, SetPlayerOp(PLAYER_OP_RECRUIT, false, RECRUIT.takeIf { inHub(player) && clanOf(player) == null }))
    }

    /** An admin's clan, deleted (`::clandisband`, for tests that found one again): its members have no clan. */
    fun disband(clan: ClanStore.Clan, online: (String) -> Player?) {
        store.delete(clan)
        channels.remove(clan.id)
        for (member in clan.members) {
            val player = online(member.key) ?: continue
            setVarbit(player, VARBIT_AFFINITY, AFFINITY_NONE)
            send(player, ClanChannelFull(CLAN_TYPE_CLAN, ClanChannelFull.LeaveUpdate))
            send(player, ClanSettingsFull(CLAN_TYPE_CLAN, ClanSettingsFull.LeaveUpdate))
            showRecruit(player)
            if (tabs[player.saveKey] == TAB_YOUR_CLAN) openYourClan(player)
        }
    }

    /** A click on one of the Clans tab's four tabs: the page it shows. The chat-channel page is the friends chat's. */
    fun selectTab(player: Player, tab: Int, openChatChannel: (Player) -> Unit) {
        // Grouping isn't built: the page, and the tab this tracks, stay as they were.
        if (tab !in TAB_CHAT_CHANNEL..TAB_OTHER_CLAN) return
        tabs[player.saveKey] = tab
        setVarbit(player, VARBIT_TAB_SELECTED, tab)
        when (tab) {
            TAB_CHAT_CHANNEL -> openChatChannel(player)
            TAB_YOUR_CLAN -> openYourClan(player)
            TAB_OTHER_CLAN -> openContents(player, GUEST_SIDEPANEL)
        }
    }

    private fun openYourClan(player: Player) {
        if (clanOf(player) == null && inHub(player)) {
            openContents(player, CREATION)
            val group = formingOf(player)
            val leader = group == null || group.leader === player
            setVarbit(player, VARBIT_FOUNDER, if (leader) 1 else 0)
            setVarbit(player, VARBIT_FOUNDER_MEMBER, if (leader) 0 else 1)
            setEvents(player, CREATION, CREATION_REFRESH, -1, -1, OP1, 0)
            setEvents(player, CREATION, CREATION_NAME, -1, -1, OP1, 0)
            setEvents(player, CREATION, CREATION_CANCEL, -1, -1, OP1, 0)
            setEvents(player, CREATION, CREATION_CONFIRM, -1, -1, OP1, 0)
            // Inferred: the Invite button's target mode on players (bits 14 and 17, as an inventory item's Use).
            setEvents(player, CREATION, CREATION_INVITE, -1, -1, OP1, TARGET_ON_PLAYER)
            setEvents(player, CREATION, CREATION_CONTENTS, 0, MAX_FOUNDERS - 1, OP1, 0)
            showForming(player)
        } else {
            openContents(player, YOUR_CLAN)
            setEvents(player, YOUR_CLAN, YOUR_CLAN_REFRESH, -1, -1, OP1, 0)
            setEvents(player, YOUR_CLAN, YOUR_CLAN_CHAT, -1, -1, OP1, 0)
            setEvents(player, YOUR_CLAN, YOUR_CLAN_SETTINGS, -1, -1, OP1, 0)
        }
    }

    /**
     * A click on the creation panel. Returns whether it was one. [askName] runs the name prompt, then [setName] and,
     * when its second argument is true (Confirm with no name yet), [confirm] again.
     */
    fun clickCreation(player: Player, component: Int, sub: Int, askName: (Player, Boolean) -> Unit): Boolean {
        when (component) {
            CREATION_NAME -> if (leads(player)) askName(player, false)
            CREATION_CANCEL -> cancel(player)
            CREATION_CONFIRM -> if (leads(player)) confirm(player, askName)
            CREATION_CONTENTS -> kick(player, sub)
            CREATION_REFRESH -> showForming(player)
            else -> return false
        }
        return true
    }

    /** Whether [player] may lead a group: in the Hub, in no clan and not a founder in someone else's group. */
    private fun leads(player: Player): Boolean {
        if (clanOf(player) != null || !inHub(player)) return false
        val group = formingOf(player)
        return group == null || group.leader === player
    }

    /**
     * The Invite button used on [target], or [player]'s option 1 on them: accepts [target]'s invitation if they sent
     * one, otherwise invites them.
     */
    fun inviteOrAccept(player: Player, target: Player) {
        val theirs = forming[target.saveKey]
        if (theirs != null && player.saveKey in theirs.invited) {
            accept(player, theirs)
            return
        }
        invite(player, target)
    }

    private fun invite(leader: Player, target: Player) {
        if (!leads(leader)) return
        if (clanOf(target) != null) {
            tell(leader, ClanLines.ALREADY_IN_CLAN, target.username)
            return
        }
        if (!inHub(target) || formingOf(target)?.let { it.founders.isNotEmpty() || it.leader !== target } == true) {
            tell(leader, ClanLines.CANT_INVITE, target.username)
            return
        }
        val group = forming.getOrPut(leader.saveKey) { Forming(leader) }
        if (group.founders.size + 1 >= MAX_FOUNDERS) {
            tell(leader, ClanLines.GROUP_FULL)
            return
        }
        if (ignores.ignores(target, leader)) return
        // One invitation line per invite: asking again doesn't send another (exploit review round 9, CLAN-1; guessed).
        if (!group.invited.add(target.saveKey)) return
        send(target, MessageGame(CHATTYPE_CLAN_CREATION_INVITATION, leader.username, words(target).get(ClanLines.INVITATION)))
        tell(leader, ClanLines.INVITE_SENT, target.username)
    }

    /**
     * Forgets [player]'s invitations when they leave the Hub or log out (an invitation is only good in the Hub), so a
     * group's invited never holds more than the players in the Hub.
     */
    private fun dropInvitations(player: Player) {
        for (group in forming.values) group.invited -= player.saveKey
    }

    private fun accept(player: Player, group: Forming) {
        if (clanOf(player) != null || !inHub(player) || !inHub(group.leader) || forming[group.leader.saveKey] !== group) return
        val own = formingOf(player)
        if (own === group) return
        if (own != null && (own.leader !== player || own.founders.isNotEmpty())) return
        if (group.founders.size + 1 >= MAX_FOUNDERS) {
            tell(player, ClanLines.GROUP_FULL)
            return
        }
        own?.let { forming.remove(player.saveKey) }
        group.invited -= player.saveKey
        group.founders += player
        for (member in members(group)) tell(member, ClanLines.JOINED, player.username)
        refresh(group)
    }

    /** The name typed into the creation panel's name prompt. Returns whether the group has it now. */
    fun setName(leader: Player, typed: String): Boolean {
        if (!leads(leader)) return false
        val name = typed.trim()
        if (!validName(name)) {
            tell(leader, ClanLines.BAD_NAME)
            return false
        }
        if (store.nameTaken(name)) {
            tell(leader, ClanLines.NAME_TAKEN, name)
            return false
        }
        val group = forming.getOrPut(leader.saveKey) { Forming(leader) }
        group.name = name
        tell(leader, ClanLines.NAME_AVAILABLE, name)
        refresh(group)
        return true
    }

    private fun cancel(player: Player) {
        val group = formingOf(player) ?: return
        if (group.leader === player) {
            forming.remove(player.saveKey)
            for (founder in group.founders) tell(founder, ClanLines.CANCELLED)
            refresh(group)
            showForming(player)
        } else {
            leaveForming(player)
        }
    }

    /** Kick on founder [index] in the panel's list: the leader is 0. */
    private fun kick(leader: Player, index: Int) {
        val group = forming[leader.saveKey] ?: return
        val founder = group.founders.getOrNull(index - 1) ?: return
        group.founders.remove(founder)
        tell(founder, ClanLines.REMOVED)
        refresh(group)
        showForming(founder)
    }

    /** Leaves the group [player] is in, or ends the one they lead. */
    private fun leaveForming(player: Player) {
        val group = formingOf(player) ?: return
        if (group.leader === player) {
            forming.remove(player.saveKey)
            for (founder in group.founders) tell(founder, ClanLines.CANCELLED)
            refresh(group)
        } else {
            group.founders.remove(player)
            for (member in members(group)) tell(member, ClanLines.LEFT, player.username)
            refresh(group)
            showForming(player)
        }
    }

    /** Confirm on the creation panel: founds the clan if the group meets [rule] and has a name; asks for one if not. */
    fun confirm(leader: Player, askName: (Player, Boolean) -> Unit) {
        if (!leads(leader)) return
        val group = forming[leader.saveKey]
        val founders = listOf(leader) + group?.founders.orEmpty()
        if (founders.size < rule.founders) {
            tell(leader, ClanLines.NOT_ENOUGH_FOUNDERS, rule.founders.toString())
            return
        }
        val short = founders.filter { hoursPlayed(it) < rule.hours }
        if (short.isNotEmpty()) {
            tell(leader, ClanLines.NOT_ENOUGH_HOURS, short.joinToString { it.username }, rule.hours.toString())
            return
        }
        val name = group?.name
        if (name == null) {
            askName(leader, true)
            return
        }
        if (store.nameTaken(name)) {
            tell(leader, ClanLines.NAME_TAKEN, name)
            return
        }
        val today = now().atOffset(ZoneOffset.UTC).toLocalDate().toEpochDay()
        if (store.foundedDay(leader.saveKey) == today) {
            tell(leader, ClanLines.ONCE_A_DAY)
            return
        }
        // Each founder again, as of now: one may have walked out of the Hub, or left the world, since the group last saw them.
        val gone = founders.filter { clanOf(it) != null || !inHub(it) || !online(it) }
        if (gone.isNotEmpty()) {
            gone.forEach(::leaveForming)
            tell(leader, ClanLines.NOT_ENOUGH_FOUNDERS, rule.founders.toString())
            return
        }
        forming.remove(leader.saveKey)
        val joined = runeDay()
        val members = founders.mapIndexed { i, p -> ClanStore.Member(p.saveKey, p.username, if (i == 0) RANK_OWNER else RANK_FOUNDER, joined) }
        val clan = store.create(name, now().toEpochMilli(), today, members)
        for (founder in founders) {
            setVarbit(founder, VARBIT_FOUNDER, 0)
            setVarbit(founder, VARBIT_FOUNDER_MEMBER, 0)
            setVarbit(founder, VARBIT_AFFINITY, AFFINITY_CONNECTED)
            send(founder, settingsFull(clan))
            joinChannel(founder, clan)
            tell(founder, ClanLines.FORMED, name)
            showRecruit(founder)
            if (tabs[founder.saveKey] == TAB_YOUR_CLAN) openYourClan(founder)
        }
    }

    /** A line typed with `//` or `/c`. */
    fun talk(player: Player, text: String) {
        val clan = clanOf(player)
        if (clan == null) {
            tell(player, ClanLines.NOT_IN_CLAN)
            return
        }
        val members = channels[clan.id]
        if (members == null || player !in members) {
            tell(player, ClanLines.NOT_IN_CHANNEL)
            return
        }
        val line = text.take(MAX_LENGTH)
        if (line.isBlank()) return
        val rank = clan.member(player.saveKey)?.rank ?: return
        if (rank < TALK_RANK) return
        val message = MessageClanChannel(CLAN_TYPE_CLAN, player.username, FriendLists.WORLD, nextCounter(), FriendLists.RANK, line)
        for (member in members) {
            if (ignores.ignores(member, player)) continue
            send(member, message)
        }
    }

    /** The "Your Clan" page's Chat/Leave button: joins or leaves the clan's channel. */
    fun toggleChannel(player: Player) {
        val clan = clanOf(player) ?: return
        if (channels[clan.id]?.contains(player) == true) leaveChannel(player, clan, tell = true) else joinChannel(player, clan)
    }

    private fun joinChannel(player: Player, clan: ClanStore.Clan) {
        val members = channels.getOrPut(clan.id) { ArrayList() }
        if (player !in members) members += player
        broadcastChannel(clan)
    }

    private fun leaveChannel(player: Player, clan: ClanStore.Clan, tell: Boolean) {
        val members = channels[clan.id] ?: return
        if (!members.remove(player)) return
        if (members.isEmpty()) channels.remove(clan.id)
        if (tell) send(player, ClanChannelFull(CLAN_TYPE_CLAN, ClanChannelFull.LeaveUpdate))
        broadcastChannel(clan)
    }

    /** Everyone in [clan]'s channel gets it whole: simpler than deltas, and channels are small here. */
    private fun broadcastChannel(clan: ClanStore.Clan) {
        val members = channels[clan.id] ?: return
        val full = channelFull(clan, members)
        for (member in members) send(member, full)
    }

    private fun channelFull(clan: ClanStore.Clan, online: List<Player>): ClanChannelFull {
        val members = online.map { ClanChannelFull.ClanMember(it.username, clan.member(it.saveKey)?.rank ?: RANK_GUEST, FriendLists.WORLD) }
        return ClanChannelFull(
            CLAN_TYPE_CLAN,
            ClanChannelFull.JoinUpdate(clan.id, ++updateNum, clan.name, false, KICK_RANK, TALK_RANK, members, ClanChannelFull.DEFAULT_OLDSCHOOL_VERSION, false),
        )
    }

    private fun settingsFull(clan: ClanStore.Clan): ClanSettingsFull {
        val members = clan.members.map { ClanSettingsFull.AffinedClanMember(it.name, it.rank, 0, it.joinedDay, false) }
        val created = ChronoUnit.MINUTES.between(RUNE_EPOCH.atStartOfDay().toInstant(ZoneOffset.UTC), Instant.ofEpochMilli(clan.createdMillis)).toInt()
        return ClanSettingsFull(
            CLAN_TYPE_CLAN,
            ClanSettingsFull.JoinUpdate(
                (++updateNum).toInt(), created, clan.name, true, TALK_RANK, KICK_RANK, RANK_OWNER, RANK_OWNER,
                members, emptyList(), emptyList(), false, true,
            ),
        )
    }

    /** Shows the creation panel's list and name to [player], if they have it open. */
    private fun showForming(player: Player) {
        if (tabs[player.saveKey] != TAB_YOUR_CLAN || clanOf(player) != null || !inHub(player)) return
        val group = formingOf(player)
        val names = (group?.let { members(it).map(Player::username) } ?: listOf(player.username)) + List(MAX_FOUNDERS) { "" }
        val leader = group == null || group.leader === player
        setVarbit(player, VARBIT_FOUNDER, if (leader) 1 else 0)
        setVarbit(player, VARBIT_FOUNDER_MEMBER, if (leader) 0 else 1)
        runClientScript(player, SCRIPT_LIST_PLAYERS, listOf<Any>(component(CREATION, CREATION_CONTENTS)) + names.take(MAX_FOUNDERS) + player.username)
        val name = group?.name
        if (name != null && shownName[player.saveKey] != name) {
            updateNameButton(player, name)
            shownName[player.saveKey] = name
        } else if (name == null && shownName.remove(player.saveKey) != null) {
            // Back to the unnamed button: clear the name script 4447 keeps, then open the panel again, so its onload
            // script draws the button as it does for a group with no name.
            updateNameButton(player, "")
            openYourClan(player)
        }
    }

    private fun updateNameButton(player: Player, name: String) {
        runClientScript(player, SCRIPT_UPDATE_BUTTON, listOf(component(CREATION, CREATION_UNIVERSE), component(CREATION, CREATION_NAME), ENUM_CREATION_BUTTONS, name))
    }

    private fun refresh(group: Forming) {
        for (member in members(group)) showForming(member)
    }

    private fun members(group: Forming): List<Player> = listOf(group.leader) + group.founders

    private fun tell(player: Player, line: Line, vararg values: String) {
        send(player, MessageGame(0, words(player).get(line, *line.args.zip(values.map<String, Any> { it }).toTypedArray())))
    }

    private fun runeDay(): Int = ChronoUnit.DAYS.between(RUNE_EPOCH, now().atOffset(ZoneOffset.UTC).toLocalDate()).toInt()

    companion object {
        /** The Clans tab (side_channels) and its contents. */
        const val SIDE_CHANNELS = 707
        val SIDE_CHANNELS_TABS = 2..5
        const val SIDE_CHANNELS_CONTENTS = 7

        const val TAB_CHAT_CHANNEL = 0
        const val TAB_YOUR_CLAN = 1
        const val TAB_OTHER_CLAN = 2

        /** clans_sidepanel: your clan. */
        const val YOUR_CLAN = 701
        const val YOUR_CLAN_REFRESH = 2
        const val YOUR_CLAN_CHAT = 8
        const val YOUR_CLAN_SETTINGS = 9

        /** clans_guest_sidepanel: another clan, as a guest. */
        const val GUEST_SIDEPANEL = 702

        /** clans_creation_sidepanel. */
        const val CREATION = 705
        const val CREATION_UNIVERSE = 0
        const val CREATION_REFRESH = 1
        const val CREATION_CONTENTS = 7
        const val CREATION_NAME = 8
        const val CREATION_INVITE = 9
        const val CREATION_CANCEL = 10
        const val CREATION_CONFIRM = 11

        /** clan_create_side_list_players and clan_create_update_button_text, and the enum the latter reads. */
        const val SCRIPT_LIST_PLAYERS = 4448
        const val SCRIPT_UPDATE_BUTTON = 4447

        /** [shownName] after a reconnect: no group name is empty, so this never equals one. */
        private const val UNKNOWN_NAME = ""

        const val ENUM_CREATION_BUTTONS = 3824

        const val VARBIT_AFFINITY = 933
        const val VARBIT_GUEST_AFFINITY = 12164
        const val VARBIT_FOUNDER = 12171
        const val VARBIT_FOUNDER_MEMBER = 12172
        const val VARBIT_TAB_SELECTED = 13071

        /** clan_affinitystatus: 1 shows "No current clan"; 3 with clan settings shows the clan (script 4288, proc 4397). */
        const val AFFINITY_NONE = 1
        const val AFFINITY_CONNECTED = 3

        const val CHATTYPE_CLAN_CREATION_INVITATION = 109
        const val CLAN_TYPE_CLAN = 0

        /** MESSAGE_PUBLIC's type for a clan line; its clan type 0 is the player's own clan, 1 a group ironman's. */
        const val CHAT_TYPE_CLAN = 3

        /** MESSAGE_PUBLIC's type for a guest clan line (`///`). */
        const val CHAT_TYPE_GUEST = 4

        /** The player option a clan creation invitation's "Form clan" sends on its sender. */
        const val PLAYER_OP_RECRUIT = 1
        const val RECRUIT = "Recruit"

        const val RANK_GUEST = -1
        const val RANK_FOUNDER = 0
        const val RANK_OWNER = 126

        /** Inferred defaults until clan settings are built: any member talks, the owner kicks. */
        const val TALK_RANK = 0
        const val KICK_RANK = RANK_OWNER

        /** The creation panel lists five. */
        const val MAX_FOUNDERS = 5
        const val MAX_NAME = 12
        const val MAX_LENGTH = 80

        /** Jagex's day 0 (runeday), which join days count from. */
        val RUNE_EPOCH: LocalDate = LocalDate.of(2002, 2, 27)

        /** Guessed edges of the Clan Hub, around its clerk (3181, 3471; mejrs's live NPC list). */
        val HUB_X = 3172..3191
        val HUB_Z = 3464..3480

        const val OP1 = 1 shl 0

        /** IF_SETEVENTS_V2 first word: target on players, and usable as a target (as the inventory's Use). */
        const val TARGET_ON_PLAYER = (1 shl 14) or (1 shl 17)

        fun inHub(player: Player): Boolean = player.level == 0 && player.x in HUB_X && player.z in HUB_Z

        /** Hours played: ticks logged in, 0.6 s each. */
        fun hoursPlayed(player: Player): Long = player.playedTicks * 6 / 36_000

        fun component(interfaceId: Int, component: Int): Int = (interfaceId shl 16) or component

        /**
         * ASCII letters, digits and single spaces, 1 to 12 characters, not starting or ending with a space (guessed, as
         * player names), so no name reads as another's (exploit review round 9, CLAN-2).
         */
        fun validName(name: String): Boolean = name.length in 1..MAX_NAME && name == name.trim() && "  " !in name &&
            name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == ' ' }
    }
}

object ClanLines : Lines("clan", "Clans") {
    val INVITATION = line("invitation", "the chatbox line a clan creation invitation shows")
    val INVITE_SENT = line("invite_sent", "the leader invited someone to found a clan", "name")
    val ALREADY_IN_CLAN = line("already_in_clan", "inviting someone who is already in a clan", "name")
    val CANT_INVITE = line("cant_invite", "inviting someone outside the Hub or in another group", "name")
    val GROUP_FULL = line("group_full", "inviting with five founders already")
    val JOINED = line("joined", "a founder joined the group (everyone in it)", "name")
    val LEFT = line("left", "a founder left the group (everyone in it)", "name")
    val REMOVED = line("removed", "the leader removed you from the group")
    val CANCELLED = line("cancelled", "the leader cancelled the group, or left the Hub")
    val BAD_NAME = line("bad_name", "a clan name that isn't allowed")
    val NAME_TAKEN = line("name_taken", "a clan name another clan has", "name")
    val NAME_AVAILABLE = line("name_available", "a clan name nobody has", "name")
    val NAME_PROMPT = line("name_prompt", "the clan name prompt")
    val NOT_ENOUGH_FOUNDERS = line("not_enough_founders", "Confirm with fewer founders than needed", "count")
    val NOT_ENOUGH_HOURS = line("not_enough_hours", "Confirm while founders lack the hours", "names", "hours")
    val ONCE_A_DAY = line("once_a_day", "Confirm by someone who founded a clan today")
    val FORMED = line("formed", "the clan was founded (every founder)", "name")
    val NOT_IN_CLAN = line("not_in_clan", "clan chat typed by someone in no clan")
    val NOT_IN_CHANNEL = line("not_in_channel", "clan chat typed while out of the clan's channel")
}
