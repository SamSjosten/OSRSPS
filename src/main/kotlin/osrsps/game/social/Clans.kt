package osrsps.game.social

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.clan.ClanChannelFull
import net.rsprot.protocol.game.outgoing.clan.ClanSettingsFull
import net.rsprot.protocol.game.outgoing.clan.MessageClanChannel
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.SetPlayerOp
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.Player
import osrsps.game.Quests
import osrsps.game.Skill
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
 * Another clan, as a guest (step 10b): the Clans tab's "Another Clan" page (clans_guest_sidepanel, 702) has Find,
 * which asks for a clan's name and loads that clan's settings as the listened clan (clan type -1, clan_guest_affinitystatus
 * 3), and then Discard, which drops it. Its Chat/Leave button joins and leaves that clan's channel as a guest (rank -1),
 * where guests read the channel and, when the clan lets their rank talk, write to it with `///` or `/g` (MESSAGE_PUBLIC
 * type 4). Members see guests in their channel's list.
 *
 * The clan's settings page (clans_info, 690) opens from either page's Settings button and draws itself from the clan
 * settings the client has. As a guest its Apply button asks first and then applies (kept with the clan; the owner, in
 * the channel, hears of it), and then reads Withdraw (clan_transmit_guestisapplying). As a member its Leave Clan
 * button asks first and then leaves; the owner can't leave (live: they must hand the clan over first). Its Applications
 * button opens the applicants page (clans_applicants, 699) for recruiters: the applicants by name (client script 4376),
 * Review on one shows their levels (4375), and Accept makes them a member while Decline drops the application. Its
 * Members button opens the members page (clans_members, 693), which draws itself from the clan settings; there Edit
 * rank on a member below an Administrator or higher shows the client's rank list (clan_rankpopup in popupoverlay), and
 * the rank picked is theirs. Its columns' dropdowns pick what each column shows (the server keeps track); with Expel
 * picked, Expel on a member below a member who may expel asks first and then takes them out of the clan. Its
 * Permissions button opens the permissions page (clans_permissions, 706).
 *
 * Kicks and bans (step 11d): in the channel's list, Kick guest takes a guest out of the channel and keeps them out for
 * an hour, and Ban guest takes them out and puts them on the clan's ban list (in the clan settings), which keeps them
 * out until Unban on the settings page's ban list (clans_banned, 689) takes them off it.
 *
 * Permissions follow client proc clan_permission_get (rev 241): the clan settings carry each permission var as its rank
 * plus 2, a permission never needs less than its floor (Administrator for most) or the permission it rests on, and
 * Talk in chat and Kick from chat are the settings' talk and kick ranks, which the permissions page also changes.
 *
 * Guessed: the Hub's edges, the founders' rank after founding (the lowest member rank, 0; the founder is owner, 126),
 * the creation time and join day encodings, that finding a clan doesn't join its channel (the panel's text says "You
 * are not currently chatting in this clan's channel" with a Chat button), guests not talking under the default talk
 * rank, the Apply and Leave Clan questions, that only the owner hears of applicants (until ranks and permissions),
 * that someone in a clan can't apply to another, and every game message (keys in [ClanLines], words from the Wiki where
 * it has them), the applicants page's Back going to the settings page, an accepted member's rank (the lowest, 0), the
 * applicant hearing of either answer, accepting dropping their applications elsewhere, and Administrators and up
 * editing the ranks below their own, the Expel question and expelling only those below, the kick lasting an hour,
 * guests still finding a clan they are banned from, and a new clan's permissions (Administrator where that is the
 * floor, the owner for recruiting and kicking, any member talks). Not built yet: inviting after founding, handing the
 * clan over, rank titles, the guest settings (listening and requirements), the ban list's Add button, and muting.
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
    /** Opens an interface as the main modal. */
    private val openModal: (Player, Int) -> Unit,
    /** Closes the player's modals. */
    private val closeModals: (Player) -> Unit,
    /** Asks the player a yes/no question titled with the given words; runs the action on yes. */
    private val confirm: (Player, String, () -> Unit) -> Unit,
    /** The online player with a save key, or null. */
    private val playerByKey: (String) -> Player?,
    /**
     * Shows the client's rank list (clan_rankpopup) in popupoverlay, opened in the given interface and component, titled
     * with the given words, the current rank picked and the ranks from the first to the second number (with Nobody when
     * asked); runs the action with the rank clicked.
     */
    private val pickRank: (Player, RankList, (Int) -> Unit) -> Unit = { _, _, _ -> },
    /** Shows the chatbox menu of the given options under the given title; runs the action with the one picked, from 0. */
    private val pickOption: (Player, String, List<String>, (Int) -> Unit) -> Unit = { _, _, _, _ -> },
    /** Asks for an amount with the given prompt over the chatbox's input line; runs the action with the amount typed. */
    private val askAmount: (Player, String, (Int) -> Unit) -> Unit = { _, _, _ -> },
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

    /** The clan each online player has loaded as a guest (Find on the Another Clan page), by save key: its id. */
    private val listening = HashMap<String, Long>()

    /** How each player last opened the clan's settings page (clans_info): true as a guest. */
    private val infoAsGuest = HashMap<String, Boolean>()

    /** The applicants each recruiter's applicants page lists, by slot: their save keys. */
    private val shownApplicants = HashMap<String, List<String>>()

    /** The applicant each recruiter chose (Review) on the applicants page, by save key: theirs. */
    private val reviewing = HashMap<String, String>()

    /** The member each editor has the rank list open for, by save key: theirs. Gone once answered or left. */
    private val pickingRank = HashMap<String, String>()

    /** The permission var each editor has the rank list open for, by save key. Gone once answered or left. */
    private val pickingPermission = HashMap<String, Int>()

    /**
     * What each player's members page columns show, by save key: enum_3793 entries (0 Rank ... 6 Expel), picked from the
     * columns' dropdowns. The client keeps them in varbits it sets itself (clan_members_column_1/2), which start at 0 at
     * login, so the first column shows Rank and the second Joined until a pick.
     */
    private val columns = HashMap<String, IntArray>()

    /** The clans whose recruiters heard of each player's application this session, by save key: their ids. */
    private val told = HashMap<String, MutableSet<Long>>()

    /**
     * Guests kicked from each clan's channel, by clan id: until when, by save key. Kept over a logout (a kick that a
     * relog undid wouldn't keep anyone out). Kicks that ran out go at each new kick and at the guest's next try, so it
     * holds no more than the kicks of the last [KICK_MINUTES].
     */
    private val kicked = HashMap<Long, HashMap<String, Instant>>()

    private var updateNum = 0L

    fun clanOf(player: Player): ClanStore.Clan? = store.clanOf(player.saveKey)

    fun inChannel(player: Player): Boolean = clanOf(player)?.let { channels[it.id]?.contains(player) } == true

    /** The clan [player] has loaded as a guest, or null. */
    fun guestClanOf(player: Player): ClanStore.Clan? = listening[player.saveKey]?.let { store.byId(it) }

    /** Whether [player] is in the channel of the clan they have loaded as a guest. */
    fun inGuestChannel(player: Player): Boolean = guestClanOf(player)?.let { channels[it.id]?.contains(player) } == true

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
        val guest = guestClanOf(player)
        if (guest == null) {
            listening.remove(player.saveKey)
            send(player, ClanSettingsFull(CLAN_TYPE_LISTENED, ClanSettingsFull.LeaveUpdate))
            send(player, ClanChannelFull(CLAN_TYPE_LISTENED, ClanChannelFull.LeaveUpdate))
        } else {
            setVarbit(player, VARBIT_GUEST_AFFINITY, AFFINITY_CONNECTED)
            send(player, settingsFull(guest, CLAN_TYPE_LISTENED))
            val members = channels[guest.id]
            send(player, if (members != null && player in members) channelFull(guest, members, CLAN_TYPE_LISTENED) else ClanChannelFull(CLAN_TYPE_LISTENED, ClanChannelFull.LeaveUpdate))
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
        guestClanOf(player)?.let { leaveChannel(player, it, tell = false) }
        listening.remove(player.saveKey)
        infoAsGuest.remove(player.saveKey)
        shownApplicants.remove(player.saveKey)
        reviewing.remove(player.saveKey)
        pickingRank.remove(player.saveKey)
        pickingPermission.remove(player.saveKey)
        columns.remove(player.saveKey)
        told.remove(player.saveKey)
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
        kicked.remove(clan.id)
        // Its guests lose it too, as a Discard would, whether or not they were in its channel.
        for (key in listening.filterValues { it == clan.id }.keys.toList()) {
            listening.remove(key)
            online(key)?.let(::dropGuest)
        }
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
            TAB_OTHER_CLAN -> openGuestPanel(player)
        }
    }

    private fun openGuestPanel(player: Player) {
        openContents(player, GUEST_SIDEPANEL)
        for (component in intArrayOf(GUEST_REFRESH, GUEST_CHAT, GUEST_INFO, GUEST_SEARCH)) {
            setEvents(player, GUEST_SIDEPANEL, component, -1, -1, OP1, 0)
        }
    }

    /**
     * A click on the Another Clan page. Returns whether it was one. [askName] runs Find's name prompt, then [find].
     * Settings opens the clan's settings page as a guest, where Apply is.
     */
    fun clickGuest(player: Player, component: Int, askName: (Player) -> Unit): Boolean {
        when (component) {
            GUEST_SEARCH -> if (guestClanOf(player) != null) discard(player) else askName(player)
            GUEST_CHAT -> toggleGuestChannel(player)
            GUEST_REFRESH -> guestClanOf(player)?.let { refreshGuest(player, it) }
            GUEST_INFO -> if (guestClanOf(player) != null) openInfo(player, asGuest = true)
            else -> return false
        }
        return true
    }

    /**
     * `::clanmake`: a clan for test scripts to find as guests, whose only member is an owner no account can be (a name
     * with a `~`), so the account testing isn't in it. Null if the name is taken or not allowed.
     */
    fun makeTestClan(name: String): ClanStore.Clan? {
        if (!validName(name) || store.nameTaken(name)) return null
        val owner = ClanStore.Member(TEST_OWNER_KEY, TEST_OWNER_KEY, RANK_OWNER, runeDay())
        return store.create(name, now().toEpochMilli(), now().atOffset(ZoneOffset.UTC).toLocalDate().toEpochDay(), listOf(owner))
    }

    /** The clan called [name], any spelling, or null. */
    fun clanNamed(name: String): ClanStore.Clan? = store.byName(name)

    /** The "Your Clan" page's Settings button: the clan's settings page as a member. */
    fun openYourInfo(player: Player) {
        if (clanOf(player) != null) openInfo(player, asGuest = false)
    }

    /**
     * Opens clans_info, which draws itself from the clan settings the client has: the listened clan's with
     * clan_transmit_loadasguest 1, else the player's own. As a guest its Applications button reads Apply or Withdraw by
     * clan_transmit_guestisapplying.
     */
    private fun openInfo(player: Player, asGuest: Boolean) {
        pickingRank.remove(player.saveKey)
        pickingPermission.remove(player.saveKey)
        infoAsGuest[player.saveKey] = asGuest
        setVarbit(player, VARBIT_INFO_AS_GUEST, if (asGuest) 1 else 0)
        if (asGuest) showApplying(player)
        openModal(player, INFO)
        for (component in INFO_BUTTONS) setEvents(player, INFO, component, -1, -1, OP1, 0)
    }

    private fun showApplying(player: Player) {
        val applying = guestClanOf(player)?.applicant(player.saveKey) != null
        setVarbit(player, VARBIT_GUEST_APPLYING, if (applying) 1 else 0)
    }

    /**
     * A click on the clan's settings page. Returns whether it was one. As a guest, Apply asks first and then applies;
     * Withdraw takes the application back. As a member, Leave Clan asks first and then leaves, and Applications opens
     * the applicants page for recruiters, Members the members page and Permissions the permissions page; Banned opens the
     * ban list either way. The other buttons (events, hall, titles) aren't built.
     */
    fun clickInfo(player: Player, component: Int): Boolean {
        // The frame's close button (clans_info:close, transmitop 1): the client sends the click and leaves the closing to
        // the server when clan_info_editable hasn't given it closebutton_click (VM 215, 2026-11-08: the page stayed open).
        if (component == INFO_CLOSE) {
            if (INFO in player.modals.values) closeModals(player)
            return true
        }
        if (component !in INFO_BUTTONS) return false
        // Only while the page is open: a click sent after it closed, or forged, does nothing.
        if (INFO !in player.modals.values) return true
        val asGuest = infoAsGuest[player.saveKey] ?: return true
        when {
            component == INFO_APPLICATIONS && asGuest -> {
                val clan = guestClanOf(player) ?: return true
                if (clan.applicant(player.saveKey) != null) {
                    withdraw(player, clan)
                } else {
                    confirm(player, words(player)[ClanLines.APPLY_CONFIRM, "name" to clan.name]) { apply(player, clan.id) }
                }
            }
            component == INFO_APPLICATIONS && !asGuest -> openApplicants(player)
            component == INFO_MEMBERS && !asGuest -> openMembers(player)
            component == INFO_PERMISSIONS && !asGuest -> openPermissions(player)
            component == INFO_BANNED -> openBanned(player)
            component == INFO_QUIT && !asGuest -> {
                val clan = clanOf(player) ?: return true
                if (clan.member(player.saveKey)?.rank == RANK_OWNER) {
                    tell(player, ClanLines.OWNER_CANT_LEAVE)
                } else {
                    confirm(player, words(player)[ClanLines.LEAVE_CONFIRM, "name" to clan.name]) { leaveClan(player, clan.id) }
                }
            }
        }
        return true
    }

    /** Apply, confirmed: [player] becomes an applicant of the clan with [clanId], if still its guest and in no clan. */
    fun apply(player: Player, clanId: Long) {
        val clan = guestClanOf(player)?.takeIf { it.id == clanId } ?: return
        if (clanOf(player) != null) {
            tell(player, ClanLines.APPLY_IN_A_CLAN)
            return
        }
        if (clan.applicant(player.saveKey) != null) return
        store.addApplicant(clan, player.saveKey, player.username, statsOf(player))
        showApplying(player)
        tell(player, ClanLines.APPLIED, clan.name)
        // Recruiters in the channel hear of it (inferred: the Wiki says recruiters are notified, not how): once per clan
        // per session, so Apply / Withdraw in a loop can't flood them, and not those who ignore the applicant.
        if (!told.getOrPut(player.saveKey) { HashSet() }.add(clan.id)) return
        for (member in channels[clan.id].orEmpty()) {
            if (has(member, clan, VAR_RECRUITMENT) && !ignores.ignores(member, player)) tell(member, ClanLines.NEW_APPLICANT, player.username)
        }
    }

    private fun withdraw(player: Player, clan: ClanStore.Clan) {
        if (!store.removeApplicant(clan, player.saveKey)) return
        showApplying(player)
        tell(player, ClanLines.WITHDRAWN, clan.name)
    }

    /** Leave Clan, confirmed: [player] is no longer a member of the clan with [clanId]. Its owner can't leave. */
    fun leaveClan(player: Player, clanId: Long) {
        val clan = clanOf(player)?.takeIf { it.id == clanId } ?: return
        if (clan.member(player.saveKey)?.rank == RANK_OWNER) return
        leaveChannel(player, clan, tell = false)
        store.removeMember(clan, player.saveKey)
        removed(player)
        sendSettings(clan)
        tell(player, ClanLines.LEFT_CLAN, clan.name)
    }

    /** [player] was just taken out of their clan: their client drops it, and the Clans tab shows them out of a clan. */
    private fun removed(player: Player) {
        setVarbit(player, VARBIT_AFFINITY, AFFINITY_NONE)
        send(player, ClanChannelFull(CLAN_TYPE_CLAN, ClanChannelFull.LeaveUpdate))
        send(player, ClanSettingsFull(CLAN_TYPE_CLAN, ClanSettingsFull.LeaveUpdate))
        infoAsGuest.remove(player.saveKey)
        pickingRank.remove(player.saveKey)
        pickingPermission.remove(player.saveKey)
        closeModals(player)
        showRecruit(player)
        if (tabs[player.saveKey] == TAB_YOUR_CLAN) openYourClan(player)
    }

    /** [clan]'s settings again, to every online member and everyone with it loaded as a guest, in its channel or not. */
    private fun sendSettings(clan: ClanStore.Clan) {
        for (member in clan.members) playerByKey(member.key)?.let { send(it, settingsFull(clan)) }
        for ((key, id) in listening) if (id == clan.id) playerByKey(key)?.let { send(it, settingsFull(clan, CLAN_TYPE_LISTENED)) }
    }

    /**
     * `::clanjoin`: [player] joins [clan] as its lowest member rank, for test scripts that leave a clan without founding
     * one. False if they are in a clan already.
     */
    fun joinTestClan(player: Player, clan: ClanStore.Clan, rank: Int = RANK_FOUNDER): Boolean {
        if (clanOf(player) != null) return false
        store.addMember(clan, ClanStore.Member(player.saveKey, player.username, rank, runeDay()))
        admitted(clan, player)
        return true
    }

    /** [player] was just made a member of [clan]: their client gets it, and they join its channel. */
    private fun admitted(clan: ClanStore.Clan, player: Player) {
        if (listening[player.saveKey] == clan.id) discard(player)
        setVarbit(player, VARBIT_AFFINITY, AFFINITY_CONNECTED)
        sendSettings(clan)
        joinChannel(player, clan)
        showRecruit(player)
        if (tabs[player.saveKey] == TAB_YOUR_CLAN) openYourClan(player)
    }

    /**
     * `::clanapplicant`: an applicant no account can be (a name with a `~`) for [clan], for test scripts to accept or
     * decline. False if there is one already.
     */
    fun addTestApplicant(clan: ClanStore.Clan): Boolean {
        if (clan.applicant(TEST_APPLICANT_KEY) != null || clan.member(TEST_APPLICANT_KEY) != null) return false
        store.addApplicant(clan, TEST_APPLICANT_KEY, TEST_APPLICANT_NAME, TEST_APPLICANT_STATS)
        return true
    }

    /**
     * `::clanbanned`: a ban no account can be under (a name with a `~`) on [clan]'s ban list, for test scripts to unban.
     * False if it is there already.
     */
    fun addTestBan(clan: ClanStore.Clan): Boolean {
        if (clan.banned(TEST_BANNED_KEY) != null) return false
        store.addBanned(clan, TEST_BANNED_KEY, TEST_BANNED_NAME)
        sendSettings(clan)
        return true
    }

    private fun isRecruiter(player: Player, clan: ClanStore.Clan): Boolean = has(player, clan, VAR_RECRUITMENT)

    /**
     * The rank [clan] has set for permission [id] (a varclansetting id, or [PERM_TALK] / [PERM_KICK]): as set, else its
     * default; a rank the client doesn't take for it (Nobody, kept by an earlier build, or Guest where it isn't allowed)
     * reads as the owner's, as client proc clan_permission_get reads it (rev 241).
     */
    fun setting(clan: ClanStore.Clan, id: Int): Int {
        val rank = clan.settings[id] ?: SETTING_DEFAULTS[id] ?: RANK_OWNER
        return if (validFor(id, rank)) rank else RANK_OWNER
    }

    /**
     * The rank permission [id] needs in [clan], as client proc clan_permission_get works it out: the rank set, but no
     * lower than the permission's floor (its struct's param_1253: Administrator for most) or the rank of the permission
     * it rests on (param_1254: Expel on Assign member ranks, Kick on Talk, Ban on Kick).
     */
    fun permission(clan: ClanStore.Clan, id: Int): Int {
        var rank = maxOf(setting(clan, id), PERMISSION_FLOORS[id] ?: 0)
        PERMISSION_BASES[id]?.let { rank = maxOf(rank, permission(clan, it)) }
        return rank
    }

    /** Whether [rank] can be set for permission [id]: a member rank, or Guest where the floor is below members (Talk). */
    private fun validFor(id: Int, rank: Int): Boolean = rank in MEMBER_RANKS || (rank == RANK_GUEST && (PERMISSION_FLOORS[id] ?: 0) < 0)

    /** Whether [player]'s rank in [clan] meets the rank permission [id] needs. */
    private fun has(player: Player, clan: ClanStore.Clan, id: Int): Boolean = (clan.member(player.saveKey)?.rank ?: RANK_GUEST) >= permission(clan, id)

    /**
     * The settings page's Applications button as a member: the applicants page, for recruiters (inferred: others get
     * nothing). It opens with no applicant chosen.
     */
    private fun openApplicants(player: Player) {
        val clan = clanOf(player) ?: return
        if (!isRecruiter(player, clan)) return
        openModal(player, APPLICANTS)
        for (component in APPLICANTS_BUTTONS) setEvents(player, APPLICANTS, component, -1, -1, OP1, 0)
        // Each entry's Review is op 1 on the entry's own component, which clan_applicants_entry_init gives the op and
        // its handler; the client shows an op only when the server has enabled it, so without these the list had no
        // Review (RuneLite nightly 2026-11-08, social-clan-applicants step 15).
        for (component in APPLICANTS_ENTRIES) setEvents(player, APPLICANTS, component, -1, -1, OP1, 0)
        setEvents(player, APPLICANTS, APPLICANTS_LIST, 0, APPLICANTS_SLOTS - 1, 0, EVENT_PAUSE_BUTTON)
        showApplicants(player, clan)
    }

    /** The applicants page's list again, oldest first, and no applicant chosen. */
    private fun showApplicants(player: Player, clan: ClanStore.Clan) {
        reviewing.remove(player.saveKey)
        runClientScript(player, SCRIPT_SHOW_APPLICANT, listOf(""))
        val shown = clan.applicants.take(APPLICANTS_SLOTS)
        shownApplicants[player.saveKey] = shown.map { it.key }
        // Every slot, empty ones with no name: the script draws the list once it has the last.
        for (slot in 0 until APPLICANTS_SLOTS) runClientScript(player, SCRIPT_ADD_APPLICANT, listOf(slot, shown.getOrNull(slot)?.name ?: ""))
    }

    /**
     * Review on an applicant: the list's entry sends resume_pausebutton on 699:11 with its slot. Shows their levels,
     * taken again if they are online. Returns whether it was the applicants list.
     */
    fun reviewApplicant(player: Player, interfaceId: Int, component: Int, slot: Int): Boolean {
        if (interfaceId != APPLICANTS || component != APPLICANTS_LIST) return false
        if (APPLICANTS !in player.modals.values) return true
        val clan = clanOf(player)?.takeIf { isRecruiter(player, it) } ?: return true
        val key = shownApplicants[player.saveKey]?.getOrNull(slot) ?: return true
        val applicant = clan.applicant(key)
        if (applicant == null) {
            // Withdrawn, or answered by another recruiter, since the list was drawn.
            showApplicants(player, clan)
            return true
        }
        playerByKey(key)?.let { store.setApplicantStats(applicant, statsOf(it)) }
        reviewing[player.saveKey] = key
        runClientScript(player, SCRIPT_SHOW_APPLICANT, listOf(applicant.name + "|" + applicant.stats))
        return true
    }

    /**
     * A click on the applicants page's Back, Refresh, Accept or Decline. Returns whether it was one. Back goes to the
     * settings page; Accept and Decline answer the chosen applicant, then the list is drawn again.
     */
    fun clickApplicants(player: Player, component: Int): Boolean {
        if (component !in APPLICANTS_BUTTONS) return false
        if (APPLICANTS !in player.modals.values) return true
        // Back works even after Recruitment was raised above the player's rank while the page was open.
        if (component == APPLICANTS_EXIT) {
            if (clanOf(player) != null) openInfo(player, asGuest = false)
            return true
        }
        val clan = clanOf(player)?.takeIf { isRecruiter(player, it) } ?: return true
        when (component) {
            APPLICANTS_REFRESH -> showApplicants(player, clan)
            APPLICANTS_ACCEPT, APPLICANTS_REJECT -> {
                val applicant = reviewing[player.saveKey]?.let { clan.applicant(it) }
                if (applicant != null) {
                    if (component == APPLICANTS_ACCEPT) accept(player, clan, applicant) else decline(player, clan, applicant)
                }
                showApplicants(player, clan)
            }
        }
        return true
    }

    /**
     * Accept: [applicant] becomes a member of [clan] at the lowest rank, and their other applications go. Refused while
     * the clan is full, or once they are in another clan (which drops the application).
     */
    private fun accept(recruiter: Player, clan: ClanStore.Clan, applicant: ClanStore.Applicant) {
        if (store.clanOf(applicant.key) != null) {
            store.removeApplicant(clan, applicant.key)
            tell(recruiter, ClanLines.APPLICANT_IN_A_CLAN, applicant.name)
            return
        }
        if (clan.members.size >= MAX_MEMBERS) {
            tell(recruiter, ClanLines.CLAN_FULL)
            return
        }
        store.removeApplications(applicant.key)
        store.addMember(clan, ClanStore.Member(applicant.key, applicant.name, RANK_FOUNDER, runeDay()))
        tell(recruiter, ClanLines.APPLICANT_ACCEPTED, applicant.name)
        val joiner = playerByKey(applicant.key)
        if (joiner == null) {
            sendSettings(clan)
            return
        }
        // A guest of another clan they had applied to sees Apply again.
        if (listening[joiner.saveKey] != clan.id) showApplying(joiner)
        admitted(clan, joiner)
        tell(joiner, ClanLines.ACCEPTED, clan.name)
    }

    /** Decline: [applicant]'s application to [clan] goes. */
    private fun decline(recruiter: Player, clan: ClanStore.Clan, applicant: ClanStore.Applicant) {
        store.removeApplicant(clan, applicant.key)
        tell(recruiter, ClanLines.APPLICANT_DECLINED, applicant.name)
        val applicantPlayer = playerByKey(applicant.key) ?: return
        if (listening[applicantPlayer.saveKey] == clan.id) showApplying(applicantPlayer)
        tell(applicantPlayer, ClanLines.DECLINED, clan.name)
    }

    /** Find's typed name: loads that clan as [player]'s guest clan. */
    fun find(player: Player, typed: String) {
        val name = typed.trim()
        if (name.isEmpty()) return
        val clan = store.byName(name)
        if (clan == null) {
            tell(player, ClanLines.GUEST_NOT_FOUND, name)
            return
        }
        if (clan.member(player.saveKey) != null) {
            tell(player, ClanLines.GUEST_OWN_CLAN)
            return
        }
        guestClanOf(player)?.let { leaveChannel(player, it, tell = true, clanType = CLAN_TYPE_LISTENED) }
        listening[player.saveKey] = clan.id
        setVarbit(player, VARBIT_GUEST_AFFINITY, AFFINITY_CONNECTED)
        send(player, settingsFull(clan, CLAN_TYPE_LISTENED))
    }

    /** Discard: drops the guest clan, and leaves its channel. */
    private fun discard(player: Player) {
        val clan = guestClanOf(player)
        if (clan != null) leaveChannel(player, clan, tell = false)
        listening.remove(player.saveKey)
        dropGuest(player)
    }

    private fun dropGuest(player: Player) {
        setVarbit(player, VARBIT_GUEST_AFFINITY, AFFINITY_NONE)
        send(player, ClanChannelFull(CLAN_TYPE_LISTENED, ClanChannelFull.LeaveUpdate))
        send(player, ClanSettingsFull(CLAN_TYPE_LISTENED, ClanSettingsFull.LeaveUpdate))
    }

    private fun refreshGuest(player: Player, clan: ClanStore.Clan) {
        send(player, settingsFull(clan, CLAN_TYPE_LISTENED))
        val members = channels[clan.id]
        if (members != null && player in members) send(player, channelFull(clan, members, CLAN_TYPE_LISTENED))
    }

    /** The Another Clan page's Chat/Leave button: joins or leaves the guest clan's channel. */
    private fun toggleGuestChannel(player: Player) {
        val clan = guestClanOf(player) ?: return
        if (channels[clan.id]?.contains(player) == true) {
            leaveChannel(player, clan, tell = true, clanType = CLAN_TYPE_LISTENED)
        } else if (mayJoinAsGuest(player, clan)) {
            joinChannel(player, clan)
        }
    }

    /** How many kicks are kept, every clan's (for tests). */
    fun kicksKept(): Int = kicked.values.sumOf { it.size }

    /**
     * Whether [player] may join [clan]'s channel as a guest: not banned from it, the clan lets guests listen, they meet its
     * guest requirements, and they weren't kicked from it in the last hour.
     */
    private fun mayJoinAsGuest(player: Player, clan: ClanStore.Clan): Boolean {
        if (clan.banned(player.saveKey) != null) {
            tell(player, ClanLines.STILL_BANNED, clan.name)
            return false
        }
        if (!guestsMayListen(clan)) {
            tell(player, ClanLines.GUESTS_BLOCKED, clan.name)
            return false
        }
        if (!meetsGuestRequirements(player, clan)) {
            tell(player, ClanLines.GUEST_REQUIREMENTS, clan.name)
            return false
        }
        val kicks = kicked[clan.id]
        val until = kicks?.get(player.saveKey) ?: return true
        if (now().isBefore(until)) {
            tell(player, ClanLines.STILL_KICKED, clan.name)
            return false
        }
        kicks.remove(player.saveKey)
        if (kicks.isEmpty()) kicked.remove(clan.id)
        return true
    }

    /**
     * Kick guest on a guest in the clan channel's list (CLANCHANNEL_KICKUSER, from clan_sidepanel_op 7): out of the
     * channel, and kept out for [KICK_MINUTES]. The client offers it on guests to members whose rank meets the channel's
     * kick rank (clan_sidepanel_drawchannel, rev 241; the "Kick from chat" permission is "kick guests temporarily").
     */
    fun kickFromChannel(player: Player, clanType: Int, name: String) {
        val clan = clanOf(player) ?: return
        val target = channelGuest(player, clan, clanType, name) ?: return
        if (!has(player, clan, PERM_KICK)) {
            tell(player, ClanLines.KICK_DENIED)
            return
        }
        val time = now()
        // Kicks that ran out go first, every clan's: else guests who never came back would be kept for good.
        kicked.values.forEach { kicks -> kicks.values.removeIf { !time.isBefore(it) } }
        kicked.values.removeIf { it.isEmpty() }
        kicked.getOrPut(clan.id) { HashMap() }[target.saveKey] = time.plus(KICK_MINUTES, ChronoUnit.MINUTES)
        leaveChannel(target, clan, tell = true, clanType = CLAN_TYPE_LISTENED)
        tell(target, ClanLines.KICKED, clan.name)
        tell(player, ClanLines.KICKED_GUEST, target.username)
    }

    /**
     * Ban guest on a guest in the clan channel's list (AFFINEDCLANSETTINGS_ADDBANNED_FROMCHANNEL, from clan_sidepanel_op
     * 8): out of the channel and on the clan's ban list, which keeps them out until unbanned. The client offers it on
     * guests to members whose rank meets Ban from chat.
     */
    fun banFromChannel(player: Player, clanType: Int, name: String) {
        val clan = clanOf(player) ?: return
        val target = channelGuest(player, clan, clanType, name) ?: return
        if (!has(player, clan, VAR_BAN_FROM_CHAT)) {
            tell(player, ClanLines.BAN_DENIED)
            return
        }
        if (clan.banned.size >= MAX_BANNED) {
            tell(player, ClanLines.BAN_LIST_FULL)
            return
        }
        store.addBanned(clan, target.saveKey, target.username)
        leaveChannel(target, clan, tell = true, clanType = CLAN_TYPE_LISTENED)
        sendSettings(clan)
        tell(target, ClanLines.BANNED, clan.name)
        tell(player, ClanLines.BANNED_GUEST, target.username)
    }

    /**
     * The guest named [name] in [clan]'s channel, for a kick or ban from [player], who must be in that channel too; null
     * when the request isn't for their own clan's channel (clan type 0), or the name isn't a guest there (gone since, or
     * a member, whom the client never offers these on).
     */
    private fun channelGuest(player: Player, clan: ClanStore.Clan, clanType: Int, name: String): Player? {
        if (clanType != CLAN_TYPE_CLAN) return null
        val members = channels[clan.id] ?: return null
        if (player !in members) return null
        return members.firstOrNull { ClanStore.sameName(it.username, name) }?.takeIf { clan.member(it.saveKey) == null }
    }

    /**
     * The settings page's Banned button: the ban list (clans_banned, 689), which draws itself from the clan settings the
     * client has, as clans_info does (the guest clan's when opened as a guest).
     */
    private fun openBanned(player: Player) {
        openModal(player, BANNED)
        setEvents(player, BANNED, BANNED_EXIT, -1, -1, OP1, 0)
    }

    /** A click on the ban list. Returns whether it was one: Back goes to the settings page. */
    fun clickBanned(player: Player, component: Int): Boolean {
        if (component != BANNED_EXIT) return false
        if (BANNED !in player.modals.values) return true
        openInfo(player, infoAsGuest[player.saveKey] ?: return true)
        return true
    }

    /**
     * Text typed while nothing waits for any, with the ban list open: Unban on a name there, which client script
     * clan_banned_unban sends as resume_stringdialog with the name (rev 241). Lifts the ban for a member whose rank meets
     * Ban from chat. Returns whether it was one.
     */
    fun typedOnBanned(player: Player, text: String): Boolean {
        if (BANNED !in player.modals.values) return false
        // clan_banned_op set clan_editing_lookupinprogress, which greys the list until the server clears it. Set and
        // cleared in the same tick, the varp still goes out at the end of it.
        setVarbit(player, VARBIT_LOOKUP_IN_PROGRESS, 1)
        setVarbit(player, VARBIT_LOOKUP_IN_PROGRESS, 0)
        if (infoAsGuest[player.saveKey] != false) return true
        val clan = clanOf(player) ?: return true
        if (!has(player, clan, VAR_BAN_FROM_CHAT)) {
            tell(player, ClanLines.UNBAN_DENIED)
            return true
        }
        val banned = clan.banned.firstOrNull { ClanStore.sameName(it.name, text) } ?: return true
        store.removeBanned(clan, banned.key)
        sendSettings(clan)
        tell(player, ClanLines.UNBANNED, banned.name)
        return true
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
        if (rank < permission(clan, PERM_TALK)) return
        sayInChannel(player, clan, members, line)
    }

    /** A line typed with `///` or `/g`: to the channel of the clan [player] is a guest of, if guests may talk. */
    fun talkGuest(player: Player, text: String) {
        val clan = guestClanOf(player)
        val members = clan?.let { channels[it.id] }
        if (clan == null || members == null || player !in members) {
            tell(player, ClanLines.NOT_IN_GUEST_CHANNEL)
            return
        }
        val line = text.take(MAX_LENGTH)
        if (line.isBlank()) return
        if (RANK_GUEST < permission(clan, PERM_TALK)) {
            tell(player, ClanLines.GUEST_CANT_TALK)
            return
        }
        sayInChannel(player, clan, members, line)
    }

    /** Sends [line] from [player] to everyone in [clan]'s channel: members as their clan, guests as their guest clan. */
    private fun sayInChannel(player: Player, clan: ClanStore.Clan, members: List<Player>, line: String) {
        val counter = nextCounter()
        for (member in members) {
            if (ignores.ignores(member, player)) continue
            send(member, MessageClanChannel(typeFor(member, clan), player.username, FriendLists.WORLD, counter, FriendLists.RANK, line))
        }
    }

    /** The clan type [player] has [clan] as: their own clan, or their guest clan. */
    private fun typeFor(player: Player, clan: ClanStore.Clan): Int = if (clan.member(player.saveKey) != null) CLAN_TYPE_CLAN else CLAN_TYPE_LISTENED

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

    private fun leaveChannel(player: Player, clan: ClanStore.Clan, tell: Boolean, clanType: Int = CLAN_TYPE_CLAN) {
        val members = channels[clan.id] ?: return
        if (!members.remove(player)) return
        if (members.isEmpty()) channels.remove(clan.id)
        if (tell) send(player, ClanChannelFull(clanType, ClanChannelFull.LeaveUpdate))
        broadcastChannel(clan)
    }

    /**
     * Everyone in [clan]'s channel gets it whole: simpler than deltas. One message per clan type, members' and guests',
     * shared by everyone with that type, as the friends chat's full update is.
     */
    private fun broadcastChannel(clan: ClanStore.Clan) {
        val members = channels[clan.id] ?: return
        val byType = HashMap<Int, ClanChannelFull>(2)
        for (member in members) {
            val type = typeFor(member, clan)
            send(member, byType.getOrPut(type) { channelFull(clan, members, type) })
        }
    }

    private fun channelFull(clan: ClanStore.Clan, online: List<Player>, clanType: Int = CLAN_TYPE_CLAN): ClanChannelFull {
        val members = online.map { ClanChannelFull.ClanMember(it.username, clan.member(it.saveKey)?.rank ?: RANK_GUEST, FriendLists.WORLD) }
        return ClanChannelFull(
            clanType,
            // The ranks the channel's own fields need: Kick never below Talk (the settings carry the ranks as set, and
            // the client works that out from them itself).
            ClanChannelFull.JoinUpdate(clan.id, ++updateNum, clan.name, false, permission(clan, PERM_KICK), permission(clan, PERM_TALK), members, ClanChannelFull.DEFAULT_OLDSCHOOL_VERSION, false),
        )
    }

    private fun settingsFull(clan: ClanStore.Clan, clanType: Int = CLAN_TYPE_CLAN): ClanSettingsFull {
        val members = clan.members.map { ClanSettingsFull.AffinedClanMember(it.name, it.rank, 0, it.joinedDay, false) }
        val created = ChronoUnit.MINUTES.between(RUNE_EPOCH.atStartOfDay().toInstant(ZoneOffset.UTC), Instant.ofEpochMilli(clan.createdMillis)).toInt()
        return ClanSettingsFull(
            clanType,
            ClanSettingsFull.JoinUpdate(
                (++updateNum).toInt(), created, clan.name, guestsMayListen(clan), setting(clan, PERM_TALK), setting(clan, PERM_KICK), RANK_OWNER, RANK_OWNER,
                members, clan.banned.map { ClanSettingsFull.BannedClanMember(it.name) }, settingVars(clan), false, true,
            ),
        )
    }

    /**
     * [clan]'s permission vars as the clan settings carry them: each rank plus 2, as client proc clan_permission_get
     * takes 2 off what it reads (its "unset" is 126 + 2). Talk and Kick go in the settings' own fields instead.
     */
    internal fun settingVars(clan: ClanStore.Clan): List<ClanSettingsFull.IntClanSetting> {
        val ranks = SETTING_DEFAULTS.keys.plus(clan.settings.keys).filter { it !in CHANNEL_PERMISSIONS && it !in GUEST_SETTINGS }
            .map { ClanSettingsFull.IntClanSetting(it, setting(clan, it) + PERMISSION_VAR_OFFSET) }
        // The guest requirements go as they are, packed (clan_permissions_buttonresynch reads their bits).
        val guests = clan.settings[VAR_GUEST_REQUIREMENTS]?.let { listOf(ClanSettingsFull.IntClanSetting(VAR_GUEST_REQUIREMENTS, it)) }.orEmpty()
        return (ranks + guests).sortedBy { it.id }
    }

    /** Whether guests may listen in [clan]'s channel (the clan settings' allow-unaffined). Inferred: permitted unless blocked. */
    fun guestsMayListen(clan: ClanStore.Clan): Boolean = (clan.settings[GUEST_LISTEN] ?: 1) != 0

    /** The guest requirement [requirement] in [clan]: 0 when there is none. */
    fun guestRequirement(clan: ClanStore.Clan, requirement: GuestRequirement): Int {
        val packed = clan.settings[VAR_GUEST_REQUIREMENTS] ?: return 0
        return (packed ushr requirement.shift) and ((1 shl requirement.bits) - 1)
    }

    /** Whether [player] meets [clan]'s guest requirements. Iron status: nobody here is an Ironman, so any but None shuts all out. */
    private fun meetsGuestRequirements(player: Player, clan: ClanStore.Clan): Boolean {
        val skills = player.skills
        val total = Skill.entries.sumOf { skills.baseLevel(it) }
        return skills.combatLevel >= guestRequirement(clan, GuestRequirement.COMBAT) &&
            total >= guestRequirement(clan, GuestRequirement.SKILL_TOTAL) &&
            // Every account has each quest done at login (Quests.completeAll), and the quest points with them.
            player.vars[Quests.VARP_QUEST_POINTS] >= guestRequirement(clan, GuestRequirement.QUEST_POINTS) &&
            guestRequirement(clan, GuestRequirement.IRON_STATUS) == 0
    }

    /**
     * The settings page's Members button as a member: the members page (clans_members), which draws itself from the
     * clan settings. Each column's dropdown entries (op 1, "Select") answer, so the server knows what the columns show.
     * For those who may edit ranks or expel, each member's entry in a column (op 1, "Edit rank" or "Expel") answers.
     */
    private fun openMembers(player: Player) {
        val clan = clanOf(player) ?: return
        pickingRank.remove(player.saveKey)
        openModal(player, MEMBERS)
        setEvents(player, MEMBERS, MEMBERS_EXIT, -1, -1, OP1, 0)
        for (options in MEMBERS_OPTIONS) setEvents(player, MEMBERS, options, 1, MEMBERS_COLUMN_KINDS, OP1, 0)
        memberColumnEvents(player, clan)
    }

    /** The members page columns' op for [player]: on when they may edit ranks or expel in [clan], else off. */
    private fun memberColumnEvents(player: Player, clan: ClanStore.Clan) {
        val ops = if (mayEditRanks(player, clan) || has(player, clan, VAR_EXPEL)) OP1 else 0
        for (column in MEMBERS_COLUMNS) setEvents(player, MEMBERS, column, 0, MAX_MEMBERS - 1, ops, 0)
    }

    /** After a rank or permission change in [clan]: each online member with its members page open gets their ops again. */
    private fun refreshMembersPages(clan: ClanStore.Clan) {
        for (member in clan.members) {
            val player = playerByKey(member.key) ?: continue
            if (MEMBERS in player.modals.values) memberColumnEvents(player, clan)
        }
    }

    private fun mayEditRanks(player: Player, clan: ClanStore.Clan): Boolean = has(player, clan, VAR_ASSIGN_RANKS)

    /** What [player]'s members page column [column] (693:11 or 693:13) shows: an enum_3793 entry. */
    private fun columnShows(player: Player, column: Int): Int =
        columns[player.saveKey]?.get(MEMBERS_COLUMNS.indexOf(column)) ?: if (column == MEMBERS_COLUMNS.first()) COLUMN_RANK else COLUMN_JOINED

    /**
     * A click on the members page. Returns whether it was one. Back goes to the settings page; Edit rank on a member
     * (the column's slot is the member's place in the clan settings) shows the ranks below the editor's own, for a
     * member below them.
     */
    fun clickMembers(player: Player, component: Int, slot: Int): Boolean {
        if (component != MEMBERS_EXIT && component !in MEMBERS_COLUMNS && component !in MEMBERS_OPTIONS) return false
        if (MEMBERS !in player.modals.values) return true
        val clan = clanOf(player) ?: return true
        if (component == MEMBERS_EXIT) {
            openInfo(player, asGuest = false)
            return true
        }
        if (component in MEMBERS_OPTIONS) {
            // The dropdown's entries are its children 1..7, for enum_3793's entries 0..6 (clan_members_header_open, rev
            // 241); clan_members_header_op keeps the pick in varbits only the client sets, so the server tracks it here.
            if (slot in 1..MEMBERS_COLUMN_KINDS) {
                columns.getOrPut(player.saveKey) { intArrayOf(COLUMN_RANK, COLUMN_JOINED) }[MEMBERS_OPTIONS.indexOf(component)] = slot - 1
            }
            return true
        }
        when (columnShows(player, component)) {
            COLUMN_RANK -> editRank(player, clan, slot)
            COLUMN_EXPEL -> askExpel(player, clan, slot)
        }
        return true
    }

    /**
     * Expel on the member in [slot]: the question, for a member who may expel and a member below them. The Expel column
     * and its op come from client proc script4262 (rev 241). Inferred (no source): the question, and that only members
     * below the expeller's rank can be expelled (as Edit rank).
     */
    private fun askExpel(player: Player, clan: ClanStore.Clan, slot: Int) {
        if (!has(player, clan, VAR_EXPEL)) {
            tell(player, ClanLines.EXPEL_DENIED)
            return
        }
        val target = clan.members.getOrNull(slot) ?: return
        if (target.rank >= clan.member(player.saveKey)!!.rank) {
            tell(player, ClanLines.EXPEL_TOO_HIGH, target.name)
            return
        }
        val key = target.key
        confirm(player, words(player)[ClanLines.EXPEL_CONFIRM, "name" to target.name]) { expel(player, clan.id, key) }
    }

    /**
     * Expel, confirmed: the member with [key] is no longer in the clan with [clanId], if [editor] may still expel there
     * and the member is still below them. An expelled member online hears of it.
     */
    fun expel(editor: Player, clanId: Long, key: String) {
        val clan = clanOf(editor)?.takeIf { it.id == clanId && has(editor, it, VAR_EXPEL) } ?: return
        val target = clan.member(key) ?: return
        if (target.rank >= clan.member(editor.saveKey)!!.rank) return
        val expelled = playerByKey(key)
        expelled?.let { leaveChannel(it, clan, tell = false) }
        store.removeMember(clan, key)
        expelled?.let {
            removed(it)
            tell(it, ClanLines.EXPELLED, clan.name)
        }
        sendSettings(clan)
        broadcastChannel(clan)
        tell(editor, ClanLines.EXPELLED_MEMBER, target.name)
    }

    /** Edit rank on the member in [slot]: the ranks below the editor's own, for a member below them. */
    private fun editRank(player: Player, clan: ClanStore.Clan, slot: Int) {
        if (!mayEditRanks(player, clan)) return
        val target = clan.members.getOrNull(slot) ?: return
        val own = clan.member(player.saveKey)!!.rank
        if (target.rank >= own) {
            tell(player, ClanLines.RANK_TOO_HIGH, target.name)
            return
        }
        val key = target.key
        pickingRank[player.saveKey] = key
        val list = RankList(MEMBERS, MEMBERS_POPUP, words(player)[ClanLines.EDIT_RANK_TITLE, "name" to target.name], target.rank, RANK_FOUNDER, own - 1, false)
        pickRank(player, list) { rank ->
            // Only the list still open, for this member: not after Back, another page or a newer list.
            if (pickingRank[player.saveKey] == key && MEMBERS in player.modals.values) {
                pickingRank.remove(player.saveKey)
                setRank(player, clan.id, key, rank)
            }
        }
    }

    /**
     * A rank picked for the member with [key]: theirs if [editor] still edits ranks in the clan with [clanId], the member
     * is still below them and the rank is one of the client's member ranks below theirs.
     */
    fun setRank(editor: Player, clanId: Long, key: String, rank: Int) {
        val clan = clanOf(editor)?.takeIf { it.id == clanId && mayEditRanks(editor, it) } ?: return
        val own = clan.member(editor.saveKey)!!.rank
        val target = clan.member(key) ?: return
        if (target.rank >= own || rank >= own || rank !in MEMBER_RANKS || rank == target.rank) return
        store.setRank(clan, target, rank)
        sendSettings(clan)
        broadcastChannel(clan)
        refreshMembersPages(clan)
    }

    /** The settings page's Permissions button as a member: the permissions page, drawn from the clan settings. */
    private fun openPermissions(player: Player) {
        if (clanOf(player) == null) return
        pickingPermission.remove(player.saveKey)
        openModal(player, PERMISSIONS)
        setEvents(player, PERMISSIONS, PERMISSIONS_EXIT, -1, -1, OP1, 0)
        setEvents(player, PERMISSIONS, PERMISSIONS_LIST, 0, PERMISSION_BUTTONS - 1, OP1, 0)
    }

    /**
     * A click on the permissions page. Returns whether it was one. Back goes to the settings page. A permission's button
     * (the list's slot is its place on the page) shows the rank list, for a member who meets both Permission access and
     * the rank the permission requires now; the rank picked, from the lowest it can need up to their own, is the
     * permission's.
     */
    fun clickPermissions(player: Player, component: Int, slot: Int): Boolean {
        if (component != PERMISSIONS_EXIT && component != PERMISSIONS_LIST) return false
        if (PERMISSIONS !in player.modals.values) return true
        val clan = clanOf(player) ?: return true
        if (component == PERMISSIONS_EXIT) {
            openInfo(player, asGuest = false)
            return true
        }
        if (slot == GUEST_LISTEN_SLOT || GuestRequirement.entries.any { it.slot == slot }) {
            clickGuestSetting(player, clan, slot)
            return true
        }
        val id = PERMISSION_VARS[slot] ?: return true
        if (!mayEditPermission(player, clan, id)) {
            tell(player, ClanLines.PERMISSION_DENIED)
            return true
        }
        val own = clan.member(player.saveKey)!!.rank
        pickingPermission[player.saveKey] = id
        // The list runs from the lowest rank the permission can need up to the editor's own; none of these permissions
        // takes Nobody (no param_1292 on their structs, rev 241).
        val list = RankList(PERMISSIONS, PERMISSIONS_POPUP, words(player)[ClanLines.PERMISSION_TITLE], permission(clan, id), lowest(clan, id), own, false)
        pickRank(player, list) { rank ->
            if (pickingPermission[player.saveKey] == id && PERMISSIONS in player.modals.values) {
                pickingPermission.remove(player.saveKey)
                setPermission(player, clan.id, id, rank)
            }
        }
        return true
    }

    /**
     * A guest setting's button on the permissions page, for a member who meets Permission access (the buttons need no other
     * rank, clan_permissions_buttonresynch). Guessed, as live's way is unknown: Guests may listen and Iron status ask with
     * the chatbox menu, the others with an amount (0 for none).
     */
    private fun clickGuestSetting(player: Player, clan: ClanStore.Clan, slot: Int) {
        if (!has(player, clan, VAR_PERMISSION_ACCESS)) {
            tell(player, ClanLines.PERMISSION_DENIED)
            return
        }
        val words = words(player)
        val clanId = clan.id
        when (slot) {
            GUEST_LISTEN_SLOT -> pickOption(player, words[ClanLines.GUEST_LISTEN_TITLE], listOf(words[ClanLines.GUEST_PERMITTED], words[ClanLines.GUEST_BLOCKED])) {
                setGuestsMayListen(player, clanId, it == 0)
            }
            else -> {
                val requirement = GuestRequirement.entries.first { it.slot == slot }
                if (requirement == GuestRequirement.IRON_STATUS) {
                    pickOption(player, words[ClanLines.GUEST_IRON_TITLE], ClanLines.IRON_STATUSES.map { words[it] }) {
                        setGuestRequirement(player, clanId, requirement, it)
                    }
                } else {
                    askAmount(player, words[requirement.prompt!!]) { setGuestRequirement(player, clanId, requirement, it) }
                }
            }
        }
    }

    /**
     * Guests may listen, set by [editor] if they still meet Permission access. Guessed: blocking puts the guests in the
     * channel out of it, since they may no longer listen.
     */
    fun setGuestsMayListen(editor: Player, clanId: Long, permitted: Boolean) {
        val clan = clanOf(editor)?.takeIf { it.id == clanId } ?: return
        if (!has(editor, clan, VAR_PERMISSION_ACCESS) || guestsMayListen(clan) == permitted) return
        store.setSetting(clan, GUEST_LISTEN, if (permitted) 1 else 0)
        sendSettings(clan)
        if (!permitted) {
            // All the guests go at once, and the channel's list goes out once after.
            val members = channels[clan.id] ?: return
            val guests = members.filter { clan.member(it.saveKey) == null }
            if (guests.isEmpty()) return
            members.removeAll(guests.toSet())
            if (members.isEmpty()) channels.remove(clan.id)
            for (guest in guests) {
                send(guest, ClanChannelFull(CLAN_TYPE_LISTENED, ClanChannelFull.LeaveUpdate))
                tell(guest, ClanLines.GUESTS_BLOCKED, clan.name)
            }
            broadcastChannel(clan)
        }
    }

    /**
     * A guest requirement, set by [editor] if they still meet Permission access: cut to what its bits hold (an amount) or
     * refused (an Iron status the list hasn't). Guests in the channel stay, as the setting's own words say.
     */
    fun setGuestRequirement(editor: Player, clanId: Long, requirement: GuestRequirement, value: Int) {
        val clan = clanOf(editor)?.takeIf { it.id == clanId } ?: return
        if (!has(editor, clan, VAR_PERMISSION_ACCESS)) return
        val max = (1 shl requirement.bits) - 1
        if (requirement == GuestRequirement.IRON_STATUS && value !in 0 until ClanLines.IRON_STATUSES.size) return
        val mask = max shl requirement.shift
        val packed = clan.settings[VAR_GUEST_REQUIREMENTS] ?: 0
        val updated = (packed and mask.inv()) or (value.coerceIn(0, max) shl requirement.shift)
        if (updated == packed && clan.settings.containsKey(VAR_GUEST_REQUIREMENTS)) return
        store.setSetting(clan, VAR_GUEST_REQUIREMENTS, updated)
        sendSettings(clan)
    }

    /**
     * Whether [player] may change permission var [id]: as client proc clan_permissions_buttonresynch (rev 241) enables a
     * permission's button, which needs the rank Permission access needs and the rank the permission needs now. The
     * server checks the same, since a client can send any button.
     */
    private fun mayEditPermission(player: Player, clan: ClanStore.Clan, id: Int): Boolean {
        val rank = clan.member(player.saveKey)?.rank ?: return false
        return rank >= permission(clan, VAR_PERMISSION_ACCESS) && rank >= permission(clan, id)
    }

    /** The lowest rank permission [id] can need in [clan] now: its floor, or the rank of the permission it rests on. */
    private fun lowest(clan: ClanStore.Clan, id: Int): Int {
        val floor = PERMISSION_FLOORS[id] ?: 0
        return PERMISSION_BASES[id]?.let { maxOf(floor, permission(clan, it)) } ?: floor
    }

    /**
     * A rank picked for permission [id]: set if [editor] may still edit it and the rank is one it takes, from its lowest
     * up to the editor's own. A change to Talk or Kick reaches the channel's list too.
     */
    fun setPermission(editor: Player, clanId: Long, id: Int, rank: Int) {
        val clan = clanOf(editor)?.takeIf { it.id == clanId } ?: return
        if (id !in PERMISSION_VARS.values || !mayEditPermission(editor, clan, id)) return
        val own = clan.member(editor.saveKey)!!.rank
        if (!validFor(id, rank) || rank > own || rank < lowest(clan, id)) return
        store.setSetting(clan, id, rank)
        sendSettings(clan)
        if (id in CHANNEL_PERMISSIONS) broadcastChannel(clan)
        refreshMembersPages(clan)
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

    /** [player]'s levels as client script 4375 reads them: combat, total, then each skill in its order. */
    private fun statsOf(player: Player): String {
        val skills = player.skills
        val total = Skill.entries.sumOf { skills.baseLevel(it) }
        return (listOf(skills.combatLevel, total) + APPLICANT_SKILLS.map { skills.baseLevel(it) }).joinToString("|")
    }

    private fun runeDay(): Int = ChronoUnit.DAYS.between(RUNE_EPOCH, now().atOffset(ZoneOffset.UTC).toLocalDate()).toInt()

    companion object {
        /** Every interface and component id above with its gameval name in the rev 242 cache, for [osrsps.cache.GameFrameCheck]. */
        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
            get() = mapOf(
                SIDE_CHANNELS to ("side_channels" to mapOf(2 to "tab_0", 3 to "tab_1", 4 to "tab_2", 5 to "tab_3", SIDE_CHANNELS_CONTENTS to "contents")),
                YOUR_CLAN to ("clans_sidepanel" to mapOf(YOUR_CLAN_REFRESH to "refresh", YOUR_CLAN_CHAT to "chat", YOUR_CLAN_SETTINGS to "info")),
                GUEST_SIDEPANEL to ("clans_guest_sidepanel" to mapOf(GUEST_REFRESH to "refresh", GUEST_CHAT to "chat", GUEST_INFO to "info", GUEST_SEARCH to "search")),
                CREATION to ("clans_creation_sidepanel" to mapOf(CREATION_UNIVERSE to "universe", CREATION_REFRESH to "refresh", CREATION_CONTENTS to "contents", CREATION_NAME to "name", CREATION_INVITE to "invite", CREATION_CANCEL to "cancel", CREATION_CONFIRM to "confirm")),
                INFO to ("clans_info" to mapOf(INFO_CLOSE to "close", INFO_BUTTONS.first to "events", INFO_MEMBERS to "members", INFO_APPLICATIONS to "applications", INFO_PERMISSIONS to "permissions", INFO_BANNED to "banned", INFO_QUIT to "quit")),
                BANNED to ("clans_banned" to mapOf(BANNED_EXIT to "exit")),
                MEMBERS to ("clans_members" to mapOf(MEMBERS_COLUMNS[0] to "column1", MEMBERS_COLUMNS[1] to "column2", MEMBERS_OPTIONS[0] to "options1", MEMBERS_OPTIONS[1] to "options2", MEMBERS_EXIT to "exit", MEMBERS_POPUP to "popup")),
                PERMISSIONS to ("clans_permissions" to mapOf(PERMISSIONS_EXIT to "exit", PERMISSIONS_LIST to "list_contents", PERMISSIONS_POPUP to "popup")),
                APPLICANTS to ("clans_applicants" to mapOf(APPLICANTS_LIST to "list", APPLICANTS_ENTRIES.first to "00", APPLICANTS_ENTRIES.last to "19", APPLICANTS_EXIT to "exit", APPLICANTS_REFRESH to "refresh", APPLICANTS_REJECT to "reject", APPLICANTS_ACCEPT to "accept")),
                POPUPOVERLAY to ("popupoverlay" to emptyMap()),
            )

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
        const val GUEST_REFRESH = 2
        const val GUEST_CHAT = 8
        const val GUEST_INFO = 9
        const val GUEST_SEARCH = 10

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

        /** The clan type of the clan a player listens to as a guest (the client's "listened" clan). */
        const val CLAN_TYPE_LISTENED = -1

        /** clans_info: the clan's settings page, and its buttons. */
        const val INFO = 690
        const val INFO_CLOSE = 3
        const val INFO_APPLICATIONS = 21
        const val INFO_QUIT = 26
        val INFO_BUTTONS = 19..26
        const val VARBIT_INFO_AS_GUEST = 12165
        const val VARBIT_GUEST_APPLYING = 12166

        /** `::clanjoin <name> recruiter`'s rank: the owner's, which every permission allows by default. */
        const val RECRUIT_RANK = 126

        const val INFO_MEMBERS = 20

        /** clans_banned: the ban list, opened from the settings page's Banned button, and its Back button. */
        const val INFO_BANNED = 25
        const val BANNED = 689
        const val BANNED_EXIT = 11

        /** clan_editing_lookupinprogress: set by the client on Unban, it greys the ban list until the server clears it. */
        const val VARBIT_LOOKUP_IN_PROGRESS = 12199

        /** The most the ban list holds: clans_banned draws 500 entries. */
        const val MAX_BANNED = 500

        /** How long a kicked guest is kept out of the channel (guessed: the old clan chat's hour). */
        const val KICK_MINUTES = 60L

        /** clans_members: the members page, its Back button and its two columns (the rank column has Edit rank). */
        const val MEMBERS = 693
        const val MEMBERS_EXIT = 25
        val MEMBERS_COLUMNS = listOf(11, 13)

        /** The members page columns' dropdowns (options1, options2), in [MEMBERS_COLUMNS]' order. */
        val MEMBERS_OPTIONS = listOf(19, 20)

        /** What a members page column can show: enum_3793's entries, of which these answer a click on a member. */
        const val MEMBERS_COLUMN_KINDS = 7
        const val COLUMN_RANK = 0
        const val COLUMN_JOINED = 1
        const val COLUMN_EXPEL = 6

        /**
         * The member ranks the client lists (enum_3795, without guest, Jmod and Nobody): titled ranks 0 to 124, then
         * Administrator (100 among them), Deputy Owner (125) and Owner (126).
         */
        val MEMBER_RANKS = setOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 110, 115, 120, 124, 125, 126)
        const val RANK_ADMINISTRATOR = 100

        const val RANK_NOBODY = 128

        /**
         * Permission vars (varclansetting ids, proc clan_permission_get) and the cache's defaults (param_1253 of each
         * permission's struct): Permission access, public profile, assign ranks, expel, rank titles, recruitment, ban from
         * chat, icons. Recruitment has no default in the cache; the owner's is inferred.
         */
        const val VAR_PERMISSION_ACCESS = 12
        const val VAR_PUBLIC_PROFILE = 13
        const val VAR_ASSIGN_RANKS = 14
        const val VAR_EXPEL = 15
        const val VAR_RANK_TITLES = 16
        const val VAR_RECRUITMENT = 17
        const val VAR_BAN_FROM_CHAT = 18
        const val VAR_ICONS = 25

        /**
         * Talk in chat and Kick from chat, which the clan settings and channel carry in their own fields (talk rank, kick
         * rank) rather than as vars; kept with the clan's settings under these ids, which no varclansetting has.
         */
        const val PERM_TALK = 1001
        const val PERM_KICK = 1002
        val CHANNEL_PERMISSIONS = setOf(PERM_TALK, PERM_KICK)

        /**
         * The guest settings (structs 2957-2961, param_1255): Guests may listen, the clan settings' allow-unaffined, kept
         * under this id, which no varclansetting has; the requirements, packed in varclansetting 26.
         */
        const val GUEST_LISTEN = 1003
        const val VAR_GUEST_REQUIREMENTS = 26
        val GUEST_SETTINGS = setOf(GUEST_LISTEN, VAR_GUEST_REQUIREMENTS)

        /** The permissions page's Guests may listen button (after Ban from chat, enum_3827 order); the requirements follow. */
        const val GUEST_LISTEN_SLOT = 10

        /** What client proc clan_permission_get takes off a permission var's value to get its rank. */
        const val PERMISSION_VAR_OFFSET = 2

        /**
         * Inferred defaults (a new clan's are unknown): Administrator for the permissions whose floor it is, the owner for
         * recruitment and kicking, and any member talks.
         */
        val SETTING_DEFAULTS = mapOf(
            VAR_PERMISSION_ACCESS to RANK_ADMINISTRATOR, VAR_PUBLIC_PROFILE to RANK_ADMINISTRATOR, VAR_ASSIGN_RANKS to RANK_ADMINISTRATOR,
            VAR_EXPEL to RANK_ADMINISTRATOR, VAR_RANK_TITLES to RANK_ADMINISTRATOR, VAR_RECRUITMENT to RANK_OWNER,
            VAR_BAN_FROM_CHAT to RANK_ADMINISTRATOR, VAR_ICONS to RANK_ADMINISTRATOR, PERM_TALK to RANK_FOUNDER, PERM_KICK to RANK_OWNER,
        )

        /**
         * Each permission's floor, its struct's param_1253 (0 when it has none; rev 241): no permission needs less, and
         * only one with a floor below 0 (Talk in chat) takes Guest.
         */
        val PERMISSION_FLOORS = mapOf(
            VAR_PERMISSION_ACCESS to RANK_ADMINISTRATOR, VAR_PUBLIC_PROFILE to RANK_ADMINISTRATOR, VAR_ASSIGN_RANKS to RANK_ADMINISTRATOR,
            VAR_EXPEL to RANK_ADMINISTRATOR, VAR_RANK_TITLES to RANK_ADMINISTRATOR, VAR_BAN_FROM_CHAT to RANK_ADMINISTRATOR,
            VAR_ICONS to RANK_ADMINISTRATOR, PERM_TALK to RANK_GUEST,
        )

        /** The permission each rests on (param_1254): it never needs less than that one. */
        val PERMISSION_BASES = mapOf(VAR_EXPEL to VAR_ASSIGN_RANKS, PERM_KICK to PERM_TALK, VAR_BAN_FROM_CHAT to PERM_KICK)

        /** clans_permissions: the permissions page, its Back button, its buttons' list and the popup's place. */
        const val INFO_PERMISSIONS = 23
        const val PERMISSIONS = 706
        const val PERMISSIONS_EXIT = 5
        const val PERMISSIONS_LIST = 12
        const val PERMISSIONS_POPUP = 14
        const val PERMISSION_BUTTONS = 54

        /**
         * The page's buttons the server answers, by slot (clan_permission_init: Permission access first, then enum_3825's
         * permissions in order), to their vars or channel ranks. The guest settings follow (slots 10-14, [GUEST_LISTEN_SLOT]
         * and [GuestRequirement]); the others (events, coffer, hall and later ones) aren't built.
         */
        val PERMISSION_VARS = mapOf(
            0 to VAR_PERMISSION_ACCESS, 1 to VAR_RANK_TITLES, 2 to VAR_ASSIGN_RANKS, 3 to VAR_RECRUITMENT, 4 to VAR_EXPEL,
            5 to VAR_PUBLIC_PROFILE, 6 to VAR_ICONS, 7 to PERM_TALK, 8 to PERM_KICK, 9 to VAR_BAN_FROM_CHAT,
        )

        /** popupoverlay, opened in clans_members:popup, and clan_rankpopup, which lists ranks in it. */
        const val MEMBERS_POPUP = 27
        const val POPUPOVERLAY = 289
        const val SCRIPT_RANK_POPUP = 4316

        /** clans_applicants: the applicants page, its list of 20 and its buttons. */
        const val APPLICANTS = 699
        const val APPLICANTS_LIST = 11
        const val APPLICANTS_SLOTS = 20

        /** The list's entries, clans_applicants:00 to :19 (enum_3805 maps slot n to component 12 + n). */
        val APPLICANTS_ENTRIES = 12 until 12 + APPLICANTS_SLOTS
        const val APPLICANTS_EXIT = 33
        const val APPLICANTS_REFRESH = 34
        const val APPLICANTS_REJECT = 39
        const val APPLICANTS_ACCEPT = 40
        val APPLICANTS_BUTTONS = setOf(APPLICANTS_EXIT, APPLICANTS_REFRESH, APPLICANTS_REJECT, APPLICANTS_ACCEPT)

        /** clan_applicants_addapplicant(slot, name): one slot of the list; an empty name empties it. */
        const val SCRIPT_ADD_APPLICANT = 4376

        /** The chosen applicant's levels, `|` between ("" for none chosen); Accept and Decline show with a name. */
        const val SCRIPT_SHOW_APPLICANT = 4375

        /** The skills in client script 4375's order (enum_681). */
        val APPLICANT_SKILLS = listOf(
            Skill.ATTACK, Skill.STRENGTH, Skill.RANGED, Skill.MAGIC, Skill.DEFENCE, Skill.HITPOINTS, Skill.PRAYER,
            Skill.AGILITY, Skill.HERBLORE, Skill.THIEVING, Skill.CRAFTING, Skill.RUNECRAFT, Skill.MINING, Skill.SMITHING,
            Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING, Skill.FLETCHING, Skill.SLAYER,
            Skill.FARMING, Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING,
        )

        /** The interface event bit that lets a component's slots send resume_pausebutton (the first word's bit 0). */
        const val EVENT_PAUSE_BUTTON = 1

        /** A clan's member limit (the Wiki: 500). */
        const val MAX_MEMBERS = 500

        /** [addTestApplicant]'s applicant: `~` is in no account's name. */
        const val TEST_APPLICANT_KEY = "~test applicant"
        const val TEST_APPLICANT_NAME = "Applicant"
        val TEST_APPLICANT_STATS = (listOf(3, 32) + List(24) { if (it == 5) 10 else 1 }).joinToString("|")

        /** [addTestBan]'s banned player: `~` is in no account's name. */
        const val TEST_BANNED_KEY = "~test banned"
        const val TEST_BANNED_NAME = "Banned"

        /** [makeTestClan]'s owner: `~` is in no account's name. */
        const val TEST_OWNER_KEY = "~test owner"

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
    val GUEST_PROMPT = line("guest_prompt", "Find's prompt on the Another Clan page")
    val GUEST_NOT_FOUND = line("guest_not_found", "Find with a name no clan has", "name")
    val GUEST_OWN_CLAN = line("guest_own_clan", "Find with the name of your own clan")
    val NOT_IN_GUEST_CHANNEL = line("not_in_guest_channel", "guest clan chat typed while out of a guest clan's channel")
    val APPLY_CONFIRM = line("apply_confirm", "the question before applying to a clan", "name")
    val CONFIRM_YES = line("confirm_yes", "the yes option of a clan question")
    val CONFIRM_NO = line("confirm_no", "the no option of a clan question")
    val APPLIED = line("applied", "you applied to join a clan", "name")
    val WITHDRAWN = line("withdrawn", "you withdrew your application to a clan", "name")
    val APPLY_IN_A_CLAN = line("apply_in_a_clan", "applying while already in a clan")
    val NEW_APPLICANT = line("new_applicant", "a recruiter hears someone applied to their clan", "name")
    val LEAVE_CONFIRM = line("leave_confirm", "the question before leaving your clan", "name")
    val OWNER_CANT_LEAVE = line("owner_cant_leave", "the owner pressing Leave Clan")
    val LEFT_CLAN = line("left_clan", "you left your clan", "name")
    val APPLICANT_ACCEPTED = line("applicant_accepted", "a recruiter accepted an applicant", "name")
    val APPLICANT_DECLINED = line("applicant_declined", "a recruiter declined an applicant", "name")
    val APPLICANT_IN_A_CLAN = line("applicant_in_a_clan", "accepting an applicant who joined another clan since", "name")
    val CLAN_FULL = line("clan_full", "accepting an applicant into a clan with the most members")
    val ACCEPTED = line("accepted", "your application to a clan was accepted", "name")
    val DECLINED = line("declined", "your application to a clan was declined", "name")
    val EDIT_RANK_TITLE = line("edit_rank_title", "the title over the rank list when editing a member's rank", "name")
    val RANK_TOO_HIGH = line("rank_too_high", "Edit rank on a member whose rank isn't below yours", "name")
    val PERMISSION_TITLE = line("permission_title", "the title over the rank list when changing a clan permission")
    val PERMISSION_DENIED = line("permission_denied", "changing a clan permission your rank can't change")
    val EXPEL_DENIED = line("expel_denied", "Expel on a member when your rank can't expel")
    val EXPEL_TOO_HIGH = line("expel_too_high", "Expel on a member whose rank isn't below yours", "name")
    val EXPEL_CONFIRM = line("expel_confirm", "the question before expelling a clan member", "name")
    val EXPELLED_MEMBER = line("expelled_member", "you expelled a member from your clan", "name")
    val EXPELLED = line("expelled", "you were expelled from your clan", "name")
    val GUEST_CANT_TALK = line("guest_cant_talk", "guest clan chat in a clan whose talk rank is above guests")
    val KICK_DENIED = line("kick_denied", "Kick guest when your rank is below the clan's kick rank")
    val KICKED_GUEST = line("kicked_guest", "you kicked a guest from your clan's channel", "name")
    val KICKED = line("kicked", "you were kicked from a clan's channel as a guest", "name")
    val STILL_KICKED = line("still_kicked", "joining a clan's channel within the hour after being kicked from it", "name")
    val BAN_DENIED = line("ban_denied", "Ban guest when your rank can't ban from chat")
    val BAN_LIST_FULL = line("ban_list_full", "Ban guest with the clan's ban list full")
    val BANNED_GUEST = line("banned_guest", "you banned a guest from your clan's channel", "name")
    val BANNED = line("banned", "you were banned from a clan's channel as a guest", "name")
    val STILL_BANNED = line("still_banned", "joining a clan's channel you are banned from", "name")
    val UNBAN_DENIED = line("unban_denied", "Unban on the ban list when your rank can't ban from chat")
    val UNBANNED = line("unbanned", "you took a player off your clan's ban list", "name")
    val GUESTS_BLOCKED = line("guests_blocked", "joining, or being in, a clan's channel as a guest when it blocks guests", "name")
    val GUEST_REQUIREMENTS = line("guest_requirements", "joining a clan's channel as a guest without meeting its guest requirements", "name")
    val GUEST_LISTEN_TITLE = line("guest_listen_title", "the title over the Guests may listen choice")
    val GUEST_PERMITTED = line("guest_permitted", "the Guests may listen option that lets them (the page shows the cache's Permitted)")
    val GUEST_BLOCKED = line("guest_blocked", "the Guests may listen option that stops them (the page shows the cache's Blocked)")
    val GUEST_COMBAT_PROMPT = line("guest_combat_prompt", "the prompt for a clan's guest minimum combat level")
    val GUEST_TOTAL_PROMPT = line("guest_total_prompt", "the prompt for a clan's guest minimum skill total")
    val GUEST_QP_PROMPT = line("guest_qp_prompt", "the prompt for a clan's guest minimum quest points")
    val GUEST_IRON_TITLE = line("guest_iron_title", "the title over the guest Iron status choice")
    val IRON_STATUSES = listOf(
        line("iron_none", "guest Iron status None (enum_3828's default)"),
        line("iron_any", "guest Iron status Any Ironman (enum_3828 1)"),
        line("iron_uim", "guest Iron status UIM only (enum_3828 2)"),
        line("iron_hcim", "guest Iron status HCIM only (enum_3828 3)"),
    )
}

/**
 * The guest requirements on the permissions page: each one's button slot and its bits in varclansetting 26 (clan proc
 * clan_permissions_buttonresynch, rev 241), and the prompt for an amount (Iron status is picked from enum_3828's list).
 */
enum class GuestRequirement(val slot: Int, val shift: Int, val bits: Int, val prompt: Line?) {
    COMBAT(11, 0, 7, ClanLines.GUEST_COMBAT_PROMPT),
    SKILL_TOTAL(12, 7, 12, ClanLines.GUEST_TOTAL_PROMPT),
    QUEST_POINTS(13, 19, 11, ClanLines.GUEST_QP_PROMPT),
    IRON_STATUS(14, 30, 2, null),
}

/** What the client's rank list shows: where it opens, its title, the rank picked, the ranks from [min] to [max], and Nobody. */
data class RankList(val parent: Int, val component: Int, val title: String, val current: Int, val min: Int, val max: Int, val nobody: Boolean)
