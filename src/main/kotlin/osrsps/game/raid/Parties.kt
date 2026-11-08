package osrsps.game.raid

import osrsps.game.Player

/**
 * A raid party: up to [PartyScaling.MAX_PARTY] players, the first of whom leads. It lasts until its last member
 * leaves, so a wipe doesn't break it up (OSRS Wiki, The Fractured Archive: the 28 October 2026 update stopped parties
 * disbanding after a wipe).
 */
class Party internal constructor(val id: Int, leader: Player) {
    private val list = arrayListOf(leader)

    /** Its members, in the order they joined; a leader who leaves hands over to the next. */
    val members: List<Player> get() = list

    val leader: Player get() = list.first()

    val size: Int get() = list.size

    val full: Boolean get() = list.size >= PartyScaling.MAX_PARTY

    /** Who the leader has invited and who hasn't joined yet, by [Player.saveKey]. */
    internal val invited = LinkedHashSet<String>()

    internal fun add(player: Player) {
        list += player
        invited -= player.saveKey
    }

    internal fun remove(player: Player): Boolean = list.remove(player)

    override fun toString() = "Party #$id led by ${leader.username}"
}

/** What became of an ask to [Parties]. */
enum class PartyResult {
    DONE,
    NOT_LEADER,
    IN_A_PARTY,
    ALREADY_MEMBER,
    FULL,
    NOT_INVITED,
    NO_PARTY,
    NOT_A_MEMBER,
    SELF,
}

/**
 * Who is in which raid party. The lobby's Grouping Board forms parties on live; the Wiki doesn't say how it works,
 * so for now `::raidparty` stands in for it. Guessed, until a recording shows the board: only the leader invites and kicks;
 * an invite lasts until it is taken up or the party ends; a player in a party can't join another; a member who logs
 * out leaves; a leader who leaves hands the party to the member who joined next.
 */
class Parties {
    private val byMember = HashMap<Player, Party>()
    private var nextId = 1

    /** Every party, oldest first. */
    val all: List<Party> get() = byMember.values.distinct().sortedBy { it.id }

    fun of(player: Player): Party? = byMember[player]

    /** [player]'s party, or a new one of their own, led by them, when they have none: a solo raid. */
    fun start(player: Player): Party = byMember[player] ?: Party(nextId++, player).also { byMember[player] = it }

    /**
     * [leader] invites [target] to their party, starting one led by [leader] if they have none. [target] may be in
     * another party when invited, but must leave it to join.
     */
    fun invite(leader: Player, target: Player): PartyResult {
        if (leader == target) return PartyResult.SELF
        val party = byMember[leader]
        if (party != null && party.leader != leader) return PartyResult.NOT_LEADER
        if (party != null && target in party.members) return PartyResult.ALREADY_MEMBER
        if (party != null && party.full) return PartyResult.FULL
        val inviting = party ?: Party(nextId++, leader).also { byMember[leader] = it }
        inviting.invited += target.saveKey
        return PartyResult.DONE
    }

    /** [player] joins the party [leader] leads, which must have invited them. */
    fun accept(player: Player, leader: Player): PartyResult {
        if (player == leader) return PartyResult.SELF
        if (byMember[player] != null) return PartyResult.IN_A_PARTY
        val party = byMember[leader]?.takeIf { it.leader == leader } ?: return PartyResult.NO_PARTY
        if (player.saveKey !in party.invited) return PartyResult.NOT_INVITED
        if (party.full) return PartyResult.FULL
        party.add(player)
        byMember[player] = party
        return PartyResult.DONE
    }

    /** [player] leaves their party, which ends with its last member. Returns the party they left, or null for none. */
    fun leave(player: Player): Party? {
        val party = byMember.remove(player) ?: return null
        party.remove(player)
        return party
    }

    /** [leader] puts [target] out of the party they lead. */
    fun kick(leader: Player, target: Player): PartyResult {
        if (leader == target) return PartyResult.SELF
        val party = byMember[leader] ?: return PartyResult.NO_PARTY
        if (party.leader != leader) return PartyResult.NOT_LEADER
        if (byMember[target] != party) return PartyResult.NOT_A_MEMBER
        leave(target)
        return PartyResult.DONE
    }

    /** A player leaving the game leaves their party. Guessed. */
    fun logout(player: Player) {
        leave(player)
    }
}
