package osrsps.testclient

import java.nio.file.Path
import osrsps.game.World
import osrsps.game.social.FriendChats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/** Friends chat over the real protocol: naming a channel, joining, talking, leaving, and back in at login. */
class HeadlessFriendsChatTest {
    private fun HeadlessClient.saw(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.logout() {
        ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
        await("the logout") { packets().firstOrNull { it.prot.name == "LOGOUT" } }
    }

    /** The Setup button, then Set prefix with [name] typed into its prompt. */
    private fun HeadlessClient.nameChannel(name: String) {
        ifButton(FriendChats.CURRENT, FriendChats.CURRENT_SETUP, 1)
        await("the setup screen") { openInterfaces().takeIf { FriendChats.SETUP in it } }
        ifButton(FriendChats.SETUP, FriendChats.SETUP_PREFIX, 1)
        awaitTicks(1)
        resumeString(name)
        await("the new name") { text(FriendChats.SETUP, FriendChats.SETUP_PREFIX)?.takeIf { it == name } }
    }

    @Test
    fun `a named channel can be joined by its owner's name, talked in with a slash, and left`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the friends chat panel") { alice.openInterfaces().takeIf { FriendChats.CURRENT in it } }
                assertEquals("[friends_chat.setup_disabled]", run {
                    alice.ifButton(FriendChats.CURRENT, FriendChats.CURRENT_SETUP, 1)
                    alice.await("the setup screen") { alice.text(FriendChats.SETUP, FriendChats.SETUP_PREFIX) }
                })
                assertEquals("[friends_chat.option_anyone]", alice.text(FriendChats.SETUP, FriendChats.SETUP_ENTER))
                assertEquals("[friends_chat.option_only_me]", alice.text(FriendChats.SETUP, FriendChats.SETUP_KICK))
                alice.nameChannel("Alices Hut")
                alice.joinFriendsChat("alice")
                alice.saw("[friends_chat.now_talking name=Alices Hut]")
                server.login("bob").use { bob ->
                    bob.joinFriendsChat("Alice")
                    bob.saw("[friends_chat.joining]")
                    bob.saw("[friends_chat.now_talking name=Alices Hut]")
                    bob.saw("[friends_chat.how_to_talk]")
                    val chat = bob.await("the channel") { bob.friendsChat()?.takeIf { it.members.size == 2 } }
                    assertEquals("alice", chat.owner)
                    assertEquals("alices hut", chat.name.lowercase().replace('_', ' ').replace('\u00a0', ' '))
                    assertEquals(FriendChats.RANK_OWNER, chat.kickRank)
                    assertEquals(mapOf("alice" to FriendChats.RANK_OWNER, "bob" to FriendChats.RANK_NONE), chat.members)
                    alice.await("bob in her list") { alice.friendsChat()?.members?.get("bob") }
                    // Alice adds him as a friend: he shows as Friend.
                    alice.addFriend("bob")
                    alice.await("bob a friend") { alice.friendsChat()?.members?.get("bob")?.takeIf { it == FriendChats.RANK_FRIEND } }
                    bob.friendsChatSay("hello hut")
                    for (client in listOf(alice, bob)) {
                        val line = client.await("the line") { client.channelMessages().firstOrNull() }
                        assertEquals("bob", line.sender)
                        assertEquals("hello hut", line.text)
                    }
                    bob.leaveFriendsChat()
                    bob.saw("[friends_chat.left]")
                    bob.await("out") { if (bob.friendsChat() == null) Unit else null }
                    alice.await("bob gone from her list") { alice.friendsChat()?.members?.takeIf { "bob" !in it } }
                    bob.friendsChatSay("anyone?")
                    bob.saw("[friends_chat.not_in_channel]")
                }
            }
        }
    }

    @Test
    fun `a name with no channel, or no account, can't be joined`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    bob.joinFriendsChat("alice")
                    bob.saw("[friends_chat.no_channel]")
                    bob.joinFriendsChat("nobody here")
                    bob.await("again") { bob.messages().filter { it.text == "[friends_chat.no_channel]" }.takeIf { it.size == 2 } }
                    assertNull(bob.friendsChat())
                    // A bad name for a channel is refused.
                    alice.ifButton(FriendChats.CURRENT, FriendChats.CURRENT_SETUP, 1)
                    alice.await("the setup screen") { alice.openInterfaces().takeIf { FriendChats.SETUP in it } }
                    alice.ifButton(FriendChats.SETUP, FriendChats.SETUP_PREFIX, 1)
                    alice.awaitTicks(1)
                    alice.resumeString("no!good")
                    alice.saw("[friends_chat.bad_name]")
                }
            }
        }
    }

    @Test
    fun `an owner's channel works while they are offline, and members are back in it at login`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            // Bob's account exists before Alice adds him.
            server.login("bob").use { it.logout() }
            server.login("alice").use { alice ->
                alice.nameChannel("Hut")
                alice.addFriend("bob")
                alice.awaitTicks(1)
                alice.logout()
            }
            server.login("bob").use { bob ->
                bob.joinFriendsChat("alice")
                val chat = bob.await("the channel") { bob.friendsChat() }
                assertEquals(mapOf("bob" to FriendChats.RANK_FRIEND), chat.members)
                bob.logout()
            }
            server.login("bob").use { bob ->
                bob.saw("[friends_chat.now_talking name=Hut]")
                assertEquals("alice", bob.await("the channel") { bob.friendsChat() }.owner)
                // The owner comes in and joins: both listed.
                server.login("alice").use { alice ->
                    alice.joinFriendsChat("alice")
                    bob.await("alice in") { bob.friendsChat()?.members?.get("alice")?.takeIf { it == FriendChats.RANK_OWNER } }
                    // Disabling the channel sends everyone out.
                    alice.ifButton(FriendChats.CURRENT, FriendChats.CURRENT_SETUP, 1)
                    alice.await("the setup screen") { alice.openInterfaces().takeIf { FriendChats.SETUP in it } }
                    alice.ifButton(FriendChats.SETUP, FriendChats.SETUP_PREFIX, 2)
                    bob.saw("[friends_chat.disabled]")
                    bob.await("out") { if (bob.friendsChat() == null) Unit else null }
                    assertTrue(server.world.friendChats.channelOf(server.world.playerNamed("alice")!!) == null)
                }
            }
        }
    }

    @Test
    fun `a member's ignore list keeps the ignored player's lines from them`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                alice.nameChannel("Hut")
                alice.joinFriendsChat("alice")
                server.login("bob").use { bob ->
                    server.login("carol").use { carol ->
                        carol.addIgnore("bob")
                        for (client in listOf(bob, carol)) {
                            client.joinFriendsChat("alice")
                            client.await("in") { client.friendsChat() }
                        }
                        bob.friendsChatSay("from bob")
                        alice.await("bob's line") { alice.channelMessages().firstOrNull() }
                        carol.friendsChatSay("from carol")
                        carol.await("her own line") { carol.channelMessages().firstOrNull { it.sender == "carol" } }
                        assertTrue(carol.channelMessages().none { it.sender == "bob" })
                    }
                }
            }
        }
    }
}
