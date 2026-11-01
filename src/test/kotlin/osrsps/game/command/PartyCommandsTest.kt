package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.raid.PartyScaling
import osrsps.map.CollisionMap

class PartyCommandsTest {
    private val types = NpcTypes.of(
        NpcType(16659, "Archive Guardian", size = 5, internalName = "tfa_guardian", stats = NpcStats(hitpoints = 550)),
        NpcType(16660, "Archive Guardian", size = 5, internalName = "tfa_guardian_unshielded", stats = NpcStats(hitpoints = 1100)),
        NpcType(3106, "Man", internalName = "man", stats = NpcStats(hitpoints = 7)),
    )
    private val world = World(IntArray(0), CollisionMap(), npcTypes = types, partyScaling = PartyScaling.load(types))
    private val player = Player(1, "test", 3222, 3218, 0)
    private val replies = mutableListOf<String>()

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    @Test
    fun `raidnpc puts in an Archive Guardian with the Wiki's hitpoints for 1, 2, 3 and 8 players`() {
        run("raidnpc tfa_guardian")
        run("raidnpc tfa_guardian 2")
        run("raidnpc tfa_guardian 3")
        run("raidnpc tfa_guardian 8")
        run("raidnpc tfa_guardian_unshielded 1")
        run("raidnpc 16660 8")
        assertEquals(
            listOf(
                "Spawned Archive Guardian (16659) as #1 at 3222, 3218 for a party of 1 (counted as 2): 550 hp",
                "Spawned Archive Guardian (16659) as #2 at 3222, 3218 for a party of 2: 550 hp",
                "Spawned Archive Guardian (16659) as #3 at 3222, 3218 for a party of 3: 800 hp",
                "Spawned Archive Guardian (16659) as #4 at 3222, 3218 for a party of 8: 1950 hp",
                "Spawned Archive Guardian (16660) as #5 at 3222, 3218 for a party of 1 (counted as 2): 1100 hp",
                "Spawned Archive Guardian (16660) as #6 at 3222, 3218 for a party of 8: 3910 hp",
            ),
            replies,
        )
        assertEquals(3910, world.npcs[6]!!.hitpoints)
        assertEquals("::npc by test", world.npcs[6]!!.spawn.source, "::delnpc mine takes them out")
        replies.clear()
        run("npcs 0")
        assertTrue(replies.any { it.startsWith("#3 Archive Guardian (16659) at 3222, 3218; spawn 3222, 3218, wander 0; scaled for 3 players, 800 hp") }, "$replies")
    }

    @Test
    fun `raidnpc without a size scales for the party you are in`() {
        val bob = Player(2, "bob", 3222, 3218, 0)
        val carol = Player(3, "carol", 3222, 3218, 0)
        for (other in listOf(bob, carol)) {
            world.parties.invite(player, other)
            world.parties.accept(other, player)
        }
        run("raidparty")
        run("raidnpc tfa_guardian")
        assertEquals(
            listOf("You lead a party of 3: test, bob, carol", "Spawned Archive Guardian (16659) as #1 at 3222, 3218 for a party of 3: 800 hp"),
            replies,
        )
    }

    @Test
    fun `raidnpc and party refuse what they can't do`() {
        run("raidnpc")
        run("raidnpc man")
        run("raidnpc tfa_guardian 9")
        run("raidnpc goblin")
        run("raidparty")
        run("raidparty leave")
        run("raidparty kick bob")
        run("raidparty invite nobody")
        run("raidparty dance")
        assertEquals(
            listOf(
                "Usage: ::raidnpc id|name [party size]",
                "Man (3106) doesn't scale with party size",
                "The party size must be 1 to 8",
                "No NPC 'goblin'. Try its id, its name, or its gameval name like tfa_guardian",
                "You're not in a party.",
                "You're not in a party.",
                "No one called bob is online",
                "No one called nobody is online",
                "Usage: ::raidparty | ::raidparty invite|accept|kick name | ::raidparty leave",
            ),
            replies,
        )
        assertEquals(0, world.npcs.count)
    }
}
