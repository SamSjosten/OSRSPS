package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.raid.PartyScaling
import osrsps.map.CollisionMap

/**
 * Raid parties through the headless client: three players form a party with `::raidparty`, the Archive Guardian's
 * hitpoints follow its size, it lasts through a member's death, and a member who logs out leaves it.
 */
class HeadlessPartyTest {
    private val guardian = NpcType(16659, "Archive Guardian", size = 5, internalName = "tfa_guardian", stats = NpcStats(hitpoints = 550))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = NpcTypes.of(guardian)
        World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, npcTypes = types, partyScaling = PartyScaling.load(types), cycleMillis = millis)
    }

    /** Waits for a message to [client] that starts with [start], counting from [from] of its messages. */
    private fun HeadlessClient.saw(from: Int, start: String): String =
        await("a message starting '$start'") { messages().drop(from).firstOrNull { it.text.startsWith(start) }?.text }

    private fun HeadlessClient.join(leader: HeadlessClient, leaderName: String, name: String) {
        val from = messages().size
        leader.commandReply("raidparty invite $name") { it.startsWith("Invited $name") }
        saw(from, "$leaderName invited you to their party")
        commandReply("raidparty accept $leaderName") { it.startsWith("You joined $leaderName's party") }
    }

    @Test
    fun `a party of three scales the Archive Guardian, lasts through a death and loses a member who logs out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.await("alice's login rebuild") { alice.position }
                    bob.await("bob's login rebuild") { bob.position }
                    bob.join(alice, "alice", "bob")
                    assertEquals(
                        "Spawned Archive Guardian (16659) as #1 at 3222, 3218 for a party of 2: 550 hp",
                        alice.commandReply("raidnpc tfa_guardian") { it.startsWith("Spawned") },
                    )
                    server.login("carol").use { carol ->
                        carol.await("carol's login rebuild") { carol.position }
                        val from = bob.messages().size
                        carol.join(alice, "alice", "carol")
                        bob.saw(from, "carol joined the party.")
                        assertEquals(
                            "Spawned Archive Guardian (16659) as #2 at 3222, 3218 for a party of 3: 800 hp",
                            alice.commandReply("raidnpc tfa_guardian") { it.startsWith("Spawned") },
                        )

                        // A wipe doesn't break the party up (OSRS Wiki, 28 October 2026 update).
                        bob.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                        bob.awaitTicks(10)
                        assertEquals("You're in alice's party of 3: alice, bob, carol", bob.commandReply("raidparty") { it.startsWith("You") })

                        // Carol logs out, and so leaves the party.
                        carol.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                        carol.await("carol's logout") { carol.packets().firstOrNull { it.prot.name == "LOGOUT" } }
                    }
                    alice.awaitTicks(2)
                    assertEquals("You lead a party of 2: alice, bob", alice.commandReply("raidparty") { it.startsWith("You") })
                    val from = alice.messages().size
                    bob.commandReply("raidparty leave") { it == "You left the party." }
                    alice.saw(from, "bob left the party; alice leads it now.")
                    assertEquals("You lead a party of 1: alice", alice.commandReply("raidparty") { it.startsWith("You") })
                }
            }
        }
    }
}
