package osrsps.testclient

import java.nio.file.Path
import osrsps.game.World
import osrsps.game.social.FriendLists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/** The friends list over the real protocol: adding, online status, the private chat setting, and the saved list. */
class HeadlessFriendsTest {
    private val world = FriendLists.WORLD

    @Test
    fun `a friend shows on our world while in, offline once gone, and the list comes back at login`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the empty list") { alice.friendsLoaded.takeIf { it } }
                server.login("Bob").use { bob ->
                    alice.addFriend("bob")
                    // Listed under the name the account has, not the one typed.
                    alice.await("Bob online") { alice.friends()["Bob"]?.takeIf { it == world } }
                    bob.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                    alice.await("Bob offline") { alice.friends()["Bob"]?.takeIf { it == 0 } }
                }
                // Back in: the server tells his friends.
                server.login("bob").use { bob ->
                    alice.await("Bob online again") { alice.friends()["Bob"]?.takeIf { it == world } }
                    bob.awaitTicks(1)
                }
                alice.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                alice.await("the logout") { alice.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.login("alice").use { alice ->
                alice.await("the saved list") { alice.friends()["Bob"]?.takeIf { it == 0 } }
                assertEquals(listOf("Bob"), alice.friends().keys.toList())
            }
        }
    }

    @Test
    fun `names that aren't accounts are refused, and an account that is out can be added`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("Carol").use { carol ->
                carol.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                carol.await("the logout") { carol.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.login("alice").use { alice ->
                alice.await("the empty list") { alice.friendsLoaded.takeIf { it } }
                alice.addFriend("Nobody")
                alice.awaitMessage("refused") { it == "[social.unknown_friend]" }
                alice.addFriend("carol")
                alice.await("Carol offline") { alice.friends()["Carol"]?.takeIf { it == 0 } }
                // The client already refuses its own name and a second add; the server ignores them too.
                alice.addFriend("Alice")
                alice.addFriend("CAROL")
                alice.awaitTicks(2)
                assertEquals(mapOf("Carol" to 0), alice.friends())
                assertEquals(listOf("Carol"), server.world.playerNamed("alice")!!.friends)
                alice.deleteFriend("Carol")
                alice.awaitTicks(2)
                assertEquals(emptyList(), server.world.playerNamed("alice")!!.friends)
            }
        }
    }

    @Test
    fun `the private setting decides who sees a player online`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.addFriend("bob")
                    alice.await("bob online") { alice.friends()["bob"]?.takeIf { it == world } }
                    // Friends: only players on his own list see him.
                    bob.setChatFilters(0, FriendLists.PRIVATE_FRIENDS, 0)
                    alice.await("bob hidden") { alice.friends()["bob"]?.takeIf { it == 0 } }
                    bob.addFriend("alice")
                    alice.await("bob shown to a friend") { alice.friends()["bob"]?.takeIf { it == world } }
                    bob.deleteFriend("alice")
                    alice.await("bob hidden once she's off his list") { alice.friends()["bob"]?.takeIf { it == 0 } }
                    // Off: nobody sees him.
                    bob.addFriend("alice")
                    alice.await("bob shown again") { alice.friends()["bob"]?.takeIf { it == world } }
                    bob.setChatFilters(0, 2, 0)
                    alice.await("bob off") { alice.friends()["bob"]?.takeIf { it == 0 } }
                    bob.setChatFilters(0, FriendLists.PRIVATE_ON, 0)
                    alice.await("bob on") { alice.friends()["bob"]?.takeIf { it == world } }
                    // His list was sent at login and then grew; hers shows to him as On.
                    bob.await("alice online for bob") { bob.friends()["alice"]?.takeIf { it == world } }
                    assertFalse(alice.friends().containsKey("alice"))
                }
            }
        }
    }

    @Test
    fun `adds and deletes made during the death animation still count`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("bob").use { _ ->
                server.login("alice").use { alice ->
                    alice.addFriend("bob")
                    alice.await("bob a friend") { alice.friends()["bob"] }
                    alice.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                    alice.await("[combat.dead]") { alice.messages().firstOrNull { it.text == "[combat.dead]" } }
                    assertTrue(server.world.playerNamed("alice")!!.dead)
                    alice.deleteFriend("bob")
                    alice.awaitTicks(2)
                    assertEquals(emptyList(), server.world.playerNamed("alice")!!.friends)
                    alice.addFriend("bob")
                    alice.await("bob back") { alice.friends()["bob"] }
                    assertTrue(server.world.playerNamed("alice")!!.dead)
                }
            }
        }
    }
}
