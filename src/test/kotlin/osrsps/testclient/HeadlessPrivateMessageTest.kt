package osrsps.testclient

import java.nio.file.Path
import osrsps.game.social.FriendLists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/** Private messages over the real protocol: delivery, the echo, and who can't be reached. */
class HeadlessPrivateMessageTest {
    @Test
    fun `a message reaches the recipient from our world, and the sender sees it echoed`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("Bob").use { bob ->
                    alice.addFriend("bob")
                    alice.await("Bob online") { alice.friends()["Bob"]?.takeIf { it == FriendLists.WORLD } }
                    alice.privateMessage("bob", "hello there")
                    val received = bob.await("the message") { bob.privateMessages().firstOrNull { it.from != null } }
                    assertEquals("alice", received.from)
                    assertEquals("hello there", received.text)
                    assertEquals(FriendLists.WORLD, received.world)
                    // Echoed under the name on her friends list.
                    val echo = alice.await("the echo") { alice.privateMessages().firstOrNull { it.to != null } }
                    assertEquals(PrivateMessage(null, "Bob", "hello there"), echo)
                    // Each message has its own counter, so the client doesn't drop the second as a repeat.
                    alice.privateMessage("bob", "hello there")
                    val both = bob.await("the second") { bob.privateMessages().filter { it.from != null }.takeIf { it.size == 2 } }
                    assertEquals(both[0].counter + 1, both[1].counter)
                }
            }
        }
    }

    @Test
    fun `an absent, hidden or ignoring recipient reads as offline`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                alice.privateMessage("nobody", "hi")
                alice.awaitMessage("offline") { it == "[social.player_offline]" }
                server.login("bob").use { bob ->
                    bob.awaitTicks(1)
                    // Bob on Friends, Alice not his friend: hidden.
                    bob.setChatFilters(0, FriendLists.PRIVATE_FRIENDS, 0)
                    bob.awaitTicks(2)
                    alice.privateMessage("bob", "one")
                    alice.await("refused") { alice.messages().filter { it.text == "[social.player_offline]" }.takeIf { it.size == 2 } }
                    // His friend now: delivered.
                    bob.addFriend("alice")
                    bob.awaitTicks(2)
                    alice.privateMessage("bob", "two")
                    bob.await("two") { bob.privateMessages().firstOrNull { it.text == "two" } }
                    // Ignoring her makes him read as offline though he shows online.
                    bob.deleteFriend("alice")
                    bob.setChatFilters(0, FriendLists.PRIVATE_ON, 0)
                    bob.addIgnore("alice")
                    bob.awaitTicks(2)
                    alice.privateMessage("bob", "three")
                    alice.await("refused again") { alice.messages().filter { it.text == "[social.player_offline]" }.takeIf { it.size == 3 } }
                    bob.awaitTicks(2)
                    assertTrue(bob.privateMessages().none { it.text == "one" || it.text == "three" })
                }
            }
        }
    }

    @Test
    fun `sending with private chat Off turns it to Friends`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.setChatFilters(0, FriendLists.PRIVATE_OFF, 0)
                    alice.awaitTicks(2)
                    alice.privateMessage("bob", "hi")
                    bob.await("hi") { bob.privateMessages().firstOrNull { it.text == "hi" } }
                    alice.awaitTicks(1)
                    assertEquals(FriendLists.PRIVATE_FRIENDS, server.world.playerNamed("alice")!!.chatFilters.private)
                }
            }
        }
    }
}
