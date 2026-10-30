package osrsps.testclient

import java.nio.file.Path
import osrsps.game.World
import osrsps.game.social.FriendLists
import osrsps.game.social.IgnoreLists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/** The ignore list over the real protocol: adding, refusing, the saved list, and the tab swap. */
class HeadlessIgnoreTest {
    @Test
    fun `an ignored name is kept over a relog, and ignoring doesn't hide the player from the one ignored`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("Bob").use { bob ->
                    alice.addIgnore("bob")
                    alice.await("Bob ignored") { alice.ignores().takeIf { "Bob" in it } }
                    // Bob still sees her online (OSRS Wiki, Ignore List).
                    bob.addFriend("alice")
                    bob.await("alice online for bob") { bob.friends()["alice"]?.takeIf { it == FriendLists.WORLD } }
                    alice.addIgnore("Nobody")
                    alice.awaitMessage("refused") { it == "[social.unknown_ignore]" }
                    // Her own name and a second add are ignored.
                    alice.addIgnore("alice")
                    alice.addIgnore("BOB")
                    alice.awaitTicks(2)
                    assertEquals(listOf("Bob"), alice.ignores())
                    alice.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                    alice.await("the logout") { alice.packets().firstOrNull { it.prot.name == "LOGOUT" } }
                }
            }
            server.login("alice").use { alice ->
                alice.await("the saved list") { alice.ignores().takeIf { it == listOf("Bob") } }
                alice.await("the friends list, which marks both loaded") { alice.friendsLoaded.takeIf { it } }
                alice.deleteIgnore("Bob")
                alice.awaitTicks(2)
                assertTrue(server.world.playerNamed("alice")!!.ignores.isEmpty())
            }
        }
    }

    @Test
    fun `a friend can't be ignored until deleted, and clearing empties the list`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("bob").use { _ ->
                server.login("alice").use { alice ->
                    alice.addFriend("bob")
                    alice.await("bob a friend") { alice.friends()["bob"] }
                    alice.addIgnore("bob")
                    alice.awaitTicks(2)
                    assertEquals(emptyList(), alice.ignores())
                    alice.deleteFriend("bob")
                    alice.addIgnore("bob")
                    alice.await("bob ignored") { alice.ignores().takeIf { it == listOf("bob") } }
                    alice.command("::clearignores")
                    alice.await("emptied") { alice.ignores().takeIf { it.isEmpty() } }
                    assertTrue(server.world.playerNamed("alice")!!.ignores.isEmpty())
                }
            }
        }
    }

    @Test
    fun `the Friends tab's ignore button opens the ignore tab, and its friends button goes back`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                alice.awaitTicks(1)
                alice.ifButton(IgnoreLists.FRIENDS_TAB, IgnoreLists.FRIENDS_TAB_IGNORE_BUTTON, 1)
                alice.await("the ignore tab") { alice.openInterfaces().takeIf { IgnoreLists.IGNORE_TAB in it } }
                alice.ifButton(IgnoreLists.IGNORE_TAB, IgnoreLists.IGNORE_TAB_FRIENDS_BUTTON, 1)
                alice.await("the Friends tab") { alice.openInterfaces().takeIf { IgnoreLists.FRIENDS_TAB in it && IgnoreLists.IGNORE_TAB !in it } }
            }
        }
    }
}
