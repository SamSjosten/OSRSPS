package osrsps.game.raid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import osrsps.game.Player

class PartiesTest {
    private val parties = Parties()
    private val players = (1..10).map { Player(it, "p$it", 3222, 3218, 0) }
    private val leader = players[0]

    @Test
    fun `an invite starts a party the inviter leads, and the invited player joins it`() {
        assertEquals(PartyResult.DONE, parties.invite(leader, players[1]))
        val party = parties.of(leader)!!
        assertEquals(listOf(leader), party.members)
        assertNull(parties.of(players[1]), "invited isn't joined")
        assertEquals(PartyResult.DONE, parties.accept(players[1], leader))
        assertSame(party, parties.of(players[1]))
        assertEquals(listOf(leader, players[1]), party.members)
        assertEquals(leader, party.leader)
    }

    @Test
    fun `a party holds 8, and a ninth can't join or be invited`() {
        for (player in players.subList(1, 8)) {
            assertEquals(PartyResult.DONE, parties.invite(leader, player))
            assertEquals(PartyResult.DONE, parties.accept(player, leader))
        }
        assertEquals(8, parties.of(leader)!!.size)
        assertEquals(PartyResult.FULL, parties.invite(leader, players[8]))
    }

    @Test
    fun `an invite made before the party filled can't be taken up once it is full`() {
        assertEquals(PartyResult.DONE, parties.invite(leader, players[9]))
        for (player in players.subList(1, 8)) {
            parties.invite(leader, player)
            parties.accept(player, leader)
        }
        assertEquals(PartyResult.FULL, parties.accept(players[9], leader))
        assertNull(parties.of(players[9]))
    }

    @Test
    fun `only the invited join, only into a leader's party, and never two parties`() {
        parties.invite(leader, players[1])
        assertEquals(PartyResult.NOT_INVITED, parties.accept(players[2], leader))
        assertEquals(PartyResult.NO_PARTY, parties.accept(players[1], players[3]))
        parties.accept(players[1], leader)
        assertEquals(PartyResult.NOT_LEADER, parties.invite(players[1], players[2]), "a member can't invite")
        assertEquals(PartyResult.ALREADY_MEMBER, parties.invite(leader, players[1]))
        assertEquals(PartyResult.SELF, parties.invite(leader, leader))

        // Someone in a party may be invited to another, but must leave theirs first.
        val other = players[5]
        parties.invite(other, players[1])
        assertEquals(PartyResult.IN_A_PARTY, parties.accept(players[1], other))
        parties.leave(players[1])
        assertEquals(PartyResult.DONE, parties.accept(players[1], other))
        assertEquals(listOf(other, players[1]), parties.of(other)!!.members)
    }

    @Test
    fun `the leader kicks, a leader who leaves hands over, and the party ends with its last member`() {
        for (player in players.subList(1, 4)) {
            parties.invite(leader, player)
            parties.accept(player, leader)
        }
        val party = parties.of(leader)!!
        assertEquals(PartyResult.NOT_LEADER, parties.kick(players[1], players[2]))
        assertEquals(PartyResult.NOT_A_MEMBER, parties.kick(leader, players[6]))
        assertEquals(PartyResult.DONE, parties.kick(leader, players[3]))
        assertNull(parties.of(players[3]))

        assertSame(party, parties.leave(leader))
        assertEquals(players[1], party.leader, "the next to have joined leads")
        assertEquals(listOf(players[1], players[2]), party.members)

        parties.logout(players[1])
        assertEquals(listOf(players[2]), party.members)
        parties.leave(players[2])
        assertEquals(emptyList(), parties.all)
        assertNull(parties.leave(players[2]))
    }
}
