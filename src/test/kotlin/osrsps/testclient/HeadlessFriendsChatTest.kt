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
    fun `a join from inside a channel to one that refuses leaves the player out of both, at login too`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                alice.nameChannel("Hut")
                server.login("bob").use { bob ->
                    bob.joinFriendsChat("alice")
                    bob.saw("[friends_chat.now_talking name=Hut]")
                    // The client offers only Leave Chat while in one; a forged join names a channel that isn't there.
                    bob.joinFriendsChat("nobody here")
                    bob.saw("[friends_chat.no_channel]")
                    val player = server.world.playerNamed("bob")!!
                    assertNull(server.world.friendChats.channelOf(player))
                    assertNull(player.friendsChatOwner)
                    bob.logout()
                }
                server.login("bob").use { bob ->
                    bob.awaitTicks(2)
                    assertNull(bob.friendsChat())
                    assertTrue(bob.messages().none { it.text.startsWith("[friends_chat.now_talking") })
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

    /** Opens the setup screen if it isn't, and picks option [op] on [component]. */
    private fun HeadlessClient.setupOption(component: Int, op: Int) {
        if (FriendChats.SETUP !in openInterfaces()) {
            ifButton(FriendChats.CURRENT, FriendChats.CURRENT_SETUP, 1)
            await("the setup screen") { openInterfaces().takeIf { FriendChats.SETUP in it } }
        }
        ifButton(FriendChats.SETUP, component, op)
    }

    @Test
    fun `who may enter and talk follows the setup screen and the ranks given to friends`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("bob").use { it.logout() }
            server.login("alice").use { alice ->
                alice.nameChannel("Hut")
                alice.addFriend("bob")
                alice.setupOption(FriendChats.SETUP_ENTER, 2) // Any friends
                alice.await("any friends") { alice.text(FriendChats.SETUP, FriendChats.SETUP_ENTER)?.takeIf { it == "[friends_chat.option_friends]" } }
                alice.setupOption(FriendChats.SETUP_TALK, 3) // Recruit+
                alice.await("recruit+") { alice.text(FriendChats.SETUP, FriendChats.SETUP_TALK)?.takeIf { it == "[friends_chat.option_recruit]" } }
                server.login("carol").use { carol ->
                    carol.joinFriendsChat("alice")
                    carol.saw("[friends_chat.not_allowed]")
                    assertNull(carol.friendsChat())
                }
                server.login("bob").use { bob ->
                    bob.joinFriendsChat("alice")
                    bob.await("in") { bob.friendsChat()?.members?.get("bob")?.takeIf { it == FriendChats.RANK_FRIEND } }
                    bob.friendsChatSay("can I talk?")
                    bob.saw("[friends_chat.cant_talk]")
                    // Recruit: he can talk now, and shows as one.
                    alice.setFriendsChatRank("bob", FriendChats.RANK_RECRUIT)
                    bob.await("recruit") { bob.friendsChat()?.members?.get("bob")?.takeIf { it == FriendChats.RANK_RECRUIT } }
                    bob.friendsChatSay("now I can")
                    bob.await("his line") { bob.channelMessages().firstOrNull { it.text == "now I can" } }
                    assertEquals(mapOf("bob" to FriendChats.RANK_RECRUIT), server.world.playerNamed("alice")!!.friendsChat.ranks)
                    // A rank only goes to a friend.
                    alice.setFriendsChatRank("carol", FriendChats.RANK_GENERAL)
                    // Deleting him as a friend takes his rank away, and he's unranked in the channel.
                    alice.deleteFriend("bob")
                    bob.await("unranked") { bob.friendsChat()?.members?.get("bob")?.takeIf { it == FriendChats.RANK_NONE } }
                    assertTrue(server.world.playerNamed("alice")!!.friendsChat.ranks.isEmpty())
                }
            }
        }
    }

    @Test
    fun `a kick needs the kick rank and a higher rank, bars the player for an hour, and ends when the channel empties`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("bob").use { it.logout() }
            server.login("alice").use { alice ->
                alice.nameChannel("Hut")
                alice.addFriend("bob")
                alice.setupOption(FriendChats.SETUP_KICK, 4) // Corporal+
                alice.await("corporal+") { alice.text(FriendChats.SETUP, FriendChats.SETUP_KICK)?.takeIf { it == "[friends_chat.option_corporal]" } }
                alice.setFriendsChatRank("bob", FriendChats.RANK_CORPORAL)
                alice.joinFriendsChat("alice")
                server.login("bob").use { bob ->
                    server.login("carol").use { carol ->
                        for (client in listOf(bob, carol)) {
                            client.joinFriendsChat("alice")
                            client.await("in") { client.friendsChat() }
                        }
                        assertEquals(FriendChats.RANK_CORPORAL, carol.await("the kick rank") { carol.friendsChat() }.kickRank)
                        // Carol has no rank, and Bob can't kick the owner.
                        carol.kickFromFriendsChat("bob")
                        carol.saw("[friends_chat.cant_kick]")
                        bob.kickFromFriendsChat("alice")
                        bob.saw("[friends_chat.cant_kick]")
                        bob.kickFromFriendsChat("carol")
                        carol.saw("[friends_chat.kicked]")
                        carol.await("out") { if (carol.friendsChat() == null) Unit else null }
                        alice.await("carol gone") { alice.friendsChat()?.members?.takeIf { "carol" !in it } }
                        carol.joinFriendsChat("alice")
                        carol.saw("[friends_chat.banned]")
                        // Everyone leaves: the channel resets, and the ban with it.
                        bob.leaveFriendsChat()
                        alice.leaveFriendsChat()
                        alice.await("out") { if (alice.friendsChat() == null) Unit else null }
                        bob.await("out") { if (bob.friendsChat() == null) Unit else null }
                        carol.joinFriendsChat("alice")
                        carol.await("back in") { carol.friendsChat() }
                    }
                }
            }
        }
    }
}
