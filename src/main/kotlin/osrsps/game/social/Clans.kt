package osrsps.game.social

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.clan.ClanChannelFull
import net.rsprot.protocol.game.outgoing.clan.ClanSettingsFull
import net.rsprot.protocol.game.outgoing.clan.MessageClanChannel
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.SetPlayerOp
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.Player
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
 * the rank picked is theirs.
 *
 * Guessed: the Hub's edges, the founders' rank after founding (the lowest member rank, 0; the founder is owner, 126),
 * the creation time and join day encodings, that finding a clan doesn't join its channel (the panel's text says "You
 * are not currently chatting in this clan's channel" with a Chat button), guests not talking under the default talk
 * rank, the Apply and Leave Clan questions, that only the owner hears of applicants (until ranks and permissions),
 * that someone in a clan can't apply to another, and every game message (keys in [ClanLines], words from the Wiki where
 * it has them), the applicants page's Back going to the settings page, an accepted member's rank (the lowest, 0), the
 * applicant hearing of either answer, accepting dropping their applications elsewhere, and Administrators and up
 * editing the ranks below their own. Not built yet: inviting after founding, handing the clan over, permissions, rank
 * titles, settings, kicks and bans.
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
     * the applicants page for recruiters. The other buttons (events, members, hall, permissions, titles, bans) aren't
     * built.
     */
    fun clickInfo(player: Player, component: Int): Boolean {
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
        // Recruiters in the channel hear of it (inferred: the Wiki says recruiters are notified, not how).
        for (member in channels[clan.id].orEmpty()) {
            if (has(member, clan, VAR_RECRUITMENT)) tell(member, ClanLines.NEW_APPLICANT, player.username)
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
        setVarbit(player, VARBIT_AFFINITY, AFFINITY_NONE)
        send(player, ClanChannelFull(CLAN_TYPE_CLAN, ClanChannelFull.LeaveUpdate))
        send(player, ClanSettingsFull(CLAN_TYPE_CLAN, ClanSettingsFull.LeaveUpdate))
        sendSettings(clan)
        infoAsGuest.remove(player.saveKey)
        closeModals(player)
        showRecruit(player)
        if (tabs[player.saveKey] == TAB_YOUR_CLAN) openYourClan(player)
        tell(player, ClanLines.LEFT_CLAN, clan.name)
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

    private fun isRecruiter(player: Player, clan: ClanStore.Clan): Boolean = has(player, clan, VAR_RECRUITMENT)

    /** [clan]'s permission or setting var [id]: as set, else its default. */
    fun setting(clan: ClanStore.Clan, id: Int): Int = clan.settings[id] ?: SETTING_DEFAULTS[id] ?: RANK_NOBODY

    /** Whether [player]'s rank in [clan] meets the rank its permission var [id] requires. */
    private fun has(player: Player, clan: ClanStore.Clan, id: Int): Boolean = (clan.member(player.saveKey)?.rank ?: RANK_GUEST) >= setting(clan, id)

    /**
     * The settings page's Applications button as a member: the applicants page, for recruiters (inferred: others get
     * nothing). It opens with no applicant chosen.
     */
    private fun openApplicants(player: Player) {
        val clan = clanOf(player) ?: return
        if (!isRecruiter(player, clan)) return
        openModal(player, APPLICANTS)
        for (component in APPLICANTS_BUTTONS) setEvents(player, APPLICANTS, component, -1, -1, OP1, 0)
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
        val clan = clanOf(player)?.takeIf { isRecruiter(player, it) } ?: return true
        when (component) {
            APPLICANTS_EXIT -> openInfo(player, asGuest = false)
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
        } else {
            joinChannel(player, clan)
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
        if (RANK_GUEST < TALK_RANK) {
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
            ClanChannelFull.JoinUpdate(clan.id, ++updateNum, clan.name, false, KICK_RANK, TALK_RANK, members, ClanChannelFull.DEFAULT_OLDSCHOOL_VERSION, false),
        )
    }

    private fun settingsFull(clan: ClanStore.Clan, clanType: Int = CLAN_TYPE_CLAN): ClanSettingsFull {
        val members = clan.members.map { ClanSettingsFull.AffinedClanMember(it.name, it.rank, 0, it.joinedDay, false) }
        val created = ChronoUnit.MINUTES.between(RUNE_EPOCH.atStartOfDay().toInstant(ZoneOffset.UTC), Instant.ofEpochMilli(clan.createdMillis)).toInt()
        return ClanSettingsFull(
            clanType,
            ClanSettingsFull.JoinUpdate(
                (++updateNum).toInt(), created, clan.name, true, TALK_RANK, KICK_RANK, RANK_OWNER, RANK_OWNER,
                members, emptyList(), SETTING_DEFAULTS.keys.plus(clan.settings.keys).sorted().map { ClanSettingsFull.IntClanSetting(it, setting(clan, it)) }, false, true,
            ),
        )
    }

    /**
     * The settings page's Members button as a member: the members page (clans_members), which draws itself from the
     * clan settings. For those who may edit ranks, each member's rank (op 1, "Edit rank") answers.
     */
    private fun openMembers(player: Player) {
        val clan = clanOf(player) ?: return
        pickingRank.remove(player.saveKey)
        openModal(player, MEMBERS)
        setEvents(player, MEMBERS, MEMBERS_EXIT, -1, -1, OP1, 0)
        memberColumnEvents(player, clan)
    }

    /** The members page columns' op for [player]: on when they may edit ranks in [clan], else off. */
    private fun memberColumnEvents(player: Player, clan: ClanStore.Clan) {
        val ops = if (mayEditRanks(player, clan)) OP1 else 0
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

    /**
     * A click on the members page. Returns whether it was one. Back goes to the settings page; Edit rank on a member
     * (the column's slot is the member's place in the clan settings) shows the ranks below the editor's own, for a
     * member below them.
     */
    fun clickMembers(player: Player, component: Int, slot: Int): Boolean {
        if (component != MEMBERS_EXIT && component !in MEMBERS_COLUMNS) return false
        if (MEMBERS !in player.modals.values) return true
        val clan = clanOf(player) ?: return true
        if (component == MEMBERS_EXIT) {
            openInfo(player, asGuest = false)
            return true
        }
        if (!mayEditRanks(player, clan)) return true
        val target = clan.members.getOrNull(slot) ?: return true
        val own = clan.member(player.saveKey)!!.rank
        if (target.rank >= own) {
            tell(player, ClanLines.RANK_TOO_HIGH, target.name)
            return true
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
        return true
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
     * the rank the permission requires now; the rank picked, up to their own or Nobody, is the permission's.
     */
    fun clickPermissions(player: Player, component: Int, slot: Int): Boolean {
        if (component != PERMISSIONS_EXIT && component != PERMISSIONS_LIST) return false
        if (PERMISSIONS !in player.modals.values) return true
        val clan = clanOf(player) ?: return true
        if (component == PERMISSIONS_EXIT) {
            openInfo(player, asGuest = false)
            return true
        }
        val id = PERMISSION_VARS[slot] ?: return true
        if (!mayEditPermission(player, clan, id)) {
            tell(player, ClanLines.PERMISSION_DENIED)
            return true
        }
        val own = clan.member(player.saveKey)!!.rank
        pickingPermission[player.saveKey] = id
        // Inferred (no source): the list runs from Guest up to the editor's own rank, with Nobody; the server picks it.
        val list = RankList(PERMISSIONS, PERMISSIONS_POPUP, words(player)[ClanLines.PERMISSION_TITLE], setting(clan, id), RANK_GUEST, own, true)
        pickRank(player, list) { rank ->
            if (pickingPermission[player.saveKey] == id && PERMISSIONS in player.modals.values) {
                pickingPermission.remove(player.saveKey)
                setPermission(player, clan.id, id, rank)
            }
        }
        return true
    }

    /**
     * Whether [player] may change permission var [id]: as client proc clan_permissions_buttonresynch (rev 241) enables a
     * permission's button, which needs the Permission access rank and, unless the permission is set to Nobody, the rank
     * it is set to. The server checks the same, since a client can send any button.
     */
    private fun mayEditPermission(player: Player, clan: ClanStore.Clan, id: Int): Boolean {
        val rank = clan.member(player.saveKey)?.rank ?: return false
        val set = setting(clan, id)
        return rank >= setting(clan, VAR_PERMISSION_ACCESS) && (set == RANK_NOBODY || rank >= set)
    }

    /** A rank picked for permission var [id]: set if [editor] may still edit it and the rank is guest to theirs, or Nobody. */
    fun setPermission(editor: Player, clanId: Long, id: Int, rank: Int) {
        val clan = clanOf(editor)?.takeIf { it.id == clanId } ?: return
        if (id !in PERMISSION_VARS.values || !mayEditPermission(editor, clan, id)) return
        val own = clan.member(editor.saveKey)!!.rank
        if (rank != RANK_NOBODY && (rank !in MEMBER_RANKS + RANK_GUEST || rank > own)) return
        // Inferred (no source): Nobody isn't taken for Permission access, since no one could ever change it back.
        if (id == VAR_PERMISSION_ACCESS && rank == RANK_NOBODY) return
        store.setSetting(clan, id, rank)
        sendSettings(clan)
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
        const val INFO_APPLICATIONS = 21
        const val INFO_QUIT = 26
        val INFO_BUTTONS = 19..26
        const val VARBIT_INFO_AS_GUEST = 12165
        const val VARBIT_GUEST_APPLYING = 12166

        /** `::clanjoin <name> recruiter`'s rank: the owner's, which every permission allows by default. */
        const val RECRUIT_RANK = 126

        const val INFO_MEMBERS = 20

        /** clans_members: the members page, its Back button and its two columns (the rank column has Edit rank). */
        const val MEMBERS = 693
        const val MEMBERS_EXIT = 25
        val MEMBERS_COLUMNS = setOf(11, 13)

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
        val SETTING_DEFAULTS = mapOf(
            VAR_PERMISSION_ACCESS to RANK_ADMINISTRATOR, VAR_PUBLIC_PROFILE to RANK_ADMINISTRATOR, VAR_ASSIGN_RANKS to RANK_ADMINISTRATOR,
            VAR_EXPEL to RANK_ADMINISTRATOR, VAR_RANK_TITLES to RANK_ADMINISTRATOR, VAR_RECRUITMENT to RANK_OWNER,
            VAR_BAN_FROM_CHAT to RANK_ADMINISTRATOR, VAR_ICONS to RANK_ADMINISTRATOR,
        )

        /** clans_permissions: the permissions page, its Back button, its buttons' list and the popup's place. */
        const val INFO_PERMISSIONS = 23
        const val PERMISSIONS = 706
        const val PERMISSIONS_EXIT = 5
        const val PERMISSIONS_LIST = 12
        const val PERMISSIONS_POPUP = 14
        const val PERMISSION_BUTTONS = 54

        /**
         * The page's buttons the server answers, by slot (clan_permission_init: Permission access first, then enum_3825's
         * permissions in order), to their vars. The others (chat, events, coffer, hall and later ones) aren't built.
         */
        val PERMISSION_VARS = mapOf(
            0 to VAR_PERMISSION_ACCESS, 1 to VAR_RANK_TITLES, 2 to VAR_ASSIGN_RANKS, 3 to VAR_RECRUITMENT, 4 to VAR_EXPEL,
            5 to VAR_PUBLIC_PROFILE, 6 to VAR_ICONS, 9 to VAR_BAN_FROM_CHAT,
        )

        /** popupoverlay, opened in clans_members:popup, and clan_rankpopup, which lists ranks in it. */
        const val MEMBERS_POPUP = 27
        const val POPUPOVERLAY = 289
        const val SCRIPT_RANK_POPUP = 4316

        /** clans_applicants: the applicants page, its list of 20 and its buttons. */
        const val APPLICANTS = 699
        const val APPLICANTS_LIST = 11
        const val APPLICANTS_SLOTS = 20
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
    val GUEST_CANT_TALK = line("guest_cant_talk", "guest clan chat in a clan whose talk rank is above guests")
}

/** What the client's rank list shows: where it opens, its title, the rank picked, the ranks from [min] to [max], and Nobody. */
data class RankList(val parent: Int, val component: Int, val title: String, val current: Int, val min: Int, val max: Int, val nobody: Boolean)
