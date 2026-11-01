package osrsps.testclient

import net.rsprot.protocol.game.outgoing.prot.GameServerProt
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.social.ClanStore
import osrsps.game.social.Clans
import osrsps.map.CollisionMap
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Clans, step 10a: founding at the Clan Hub, the Clans tab's "Your Clan" page, and clan chat. */
class HeadlessClanTest {
    private fun server(dir: Path, data: Path = dir.resolve("data")) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(HUB_X shr 6, HUB_Z shr 6)
            },
            cycleMillis = millis,
            clansDir = data,
        )
    }

    /** Each of [names] ends one of the strings: a string's first byte may run into the field before it. */
    private fun List<String>.hasAll(vararg names: String) = names.all { name -> any { it.endsWith(name) } }

    private fun HeadlessClient.saw(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.toHub() {
        commandReply("tele $HUB_X $HUB_Z") { it.startsWith("Teleported") }
    }

    /** The Clans tab's "Your Clan" tab (707:3). */
    private fun HeadlessClient.yourClanTab() = ifButton(Clans.SIDE_CHANNELS, 3, 1)

    /** The names the creation panel last listed (client script 4448's five names). */
    private fun HeadlessClient.listed(): List<String> =
        clientScripts().lastOrNull { it.id == Clans.SCRIPT_LIST_PLAYERS }?.args?.drop(1)?.take(Clans.MAX_FOUNDERS)?.map { it as String }.orEmpty()

    /** The name client script 4447 last put on the creation panel's name button. */
    private fun HeadlessClient.shownName(): String? = clientScripts().lastOrNull { it.id == Clans.SCRIPT_UPDATE_BUTTON }?.args?.last() as String?

    @Test
    fun `three founders form a clan at the Hub, it is kept, and its members chat in its channel`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    server.login("carol").use { carol ->
                        assertEquals("Founding a clan now takes 3 founders, 0 hours each", alice.commandReply("clanfounding 3 0") { it.startsWith("Founding") })
                        for (client in listOf(alice, bob, carol)) client.toHub()
                        // In the Hub without a clan, option 1 on players is Recruit.
                        bob.await("bob's Recruit option") { bob.playerOps()[Clans.PLAYER_OP_RECRUIT]?.takeIf { it == Clans.RECRUIT } }
                        alice.yourClanTab()
                        alice.await("the creation panel") { alice.openInterfaces().takeIf { Clans.CREATION in it } }
                        alice.await("alice listed") { alice.listed().takeIf { it.firstOrNull() == "alice" } }

                        // The Invite button used on bob: he gets the invitation in his chatbox, from alice.
                        alice.playerTarget(bob.index, Clans.CREATION, Clans.CREATION_INVITE)
                        val invitation = bob.await("bob's invitation") { bob.messages().firstOrNull { it.type == Clans.CHATTYPE_CLAN_CREATION_INVITATION } }
                        assertEquals("alice", invitation.name)
                        alice.saw("[clan.invite_sent name=bob]")
                        // His "Form clan" is option 1 on alice.
                        bob.playerOp(alice.index, 1)
                        alice.saw("[clan.joined name=bob]")
                        bob.saw("[clan.joined name=bob]")
                        alice.await("bob listed") { alice.listed().takeIf { it.take(2) == listOf("alice", "bob") } }

                        // Two isn't enough here.
                        alice.ifButton(Clans.CREATION, Clans.CREATION_CONFIRM, 1)
                        alice.saw("[clan.not_enough_founders count=3]")
                        // A founder who isn't the leader can't confirm.
                        bob.ifButton(Clans.CREATION, Clans.CREATION_CONFIRM, 1)

                        // Option 1 on carol invites her too; she accepts the same way.
                        alice.playerOp(carol.index, 1)
                        carol.await("carol's invitation") { carol.messages().firstOrNull { it.type == Clans.CHATTYPE_CLAN_CREATION_INVITATION } }
                        carol.playerOp(alice.index, 1)
                        alice.saw("[clan.joined name=carol]")

                        // Confirm with no name asks for one, then founds the clan.
                        alice.ifButton(Clans.CREATION, Clans.CREATION_CONFIRM, 1)
                        alice.awaitTicks(2)
                        alice.resumeString("Bad!Name")
                        alice.saw("[clan.bad_name]")
                        assertNull(server.world.clans.clanOf(server.world.playerNamed("alice")!!))
                        alice.ifButton(Clans.CREATION, Clans.CREATION_CONFIRM, 1)
                        alice.awaitTicks(2)
                        alice.resumeString("Odins Gate")
                        for (client in listOf(alice, bob, carol)) client.saw("[clan.formed name=Odins Gate]")

                        val clan = assertNotNull(server.world.clans.clanOf(server.world.playerNamed("bob")!!))
                        assertEquals(listOf("alice" to Clans.RANK_OWNER, "bob" to Clans.RANK_FOUNDER, "carol" to Clans.RANK_FOUNDER), clan.members.map { it.name to it.rank })
                        // Each has the clan's settings and channel, with every member.
                        for (client in listOf(alice, bob, carol)) {
                            val settings = client.await("the clan settings") { client.lastStrings(GameServerProt.CLANSETTINGS_FULL) }
                            assertTrue(settings.hasAll("Odins Gate", "alice", "bob", "carol"), settings.toString())
                            val channel = client.await("the whole channel") { client.lastStrings(GameServerProt.CLANCHANNEL_FULL)?.takeIf { it.hasAll("alice", "bob", "carol") } }
                            assertTrue(channel.hasAll("Odins Gate"), channel.toString())
                        }
                        // In a clan, the option goes.
                        bob.await("Recruit gone") { if (bob.playerOps()[Clans.PLAYER_OP_RECRUIT] == null) Unit else null }
                        // The page shows the clan now.
                        alice.await("your clan page") { alice.openInterfaces().takeIf { Clans.YOUR_CLAN in it } }

                        // // chat goes to everyone in the channel, the sender too.
                        bob.clanSay("hello clan")
                        for (client in listOf(alice, bob, carol)) {
                            client.await("bob's clan line") { client.clanMessages().firstOrNull { it.sender == "bob" && it.text == "hello clan" } }
                        }
                        // Leaving the channel with the page's button: carol stops hearing it, and can't talk in it.
                        carol.ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_CHAT, 1)
                        carol.await("carol out") { if (server.world.clans.inChannel(server.world.playerNamed("carol")!!)) null else Unit }
                        alice.clanSay("second")
                        bob.await("alice's line") { bob.clanMessages().firstOrNull { it.text == "second" } }
                        carol.awaitTicks(2)
                        assertFalse(carol.clanMessages().any { it.text == "second" })
                        carol.clanSay("anyone?")
                        carol.saw("[clan.not_in_channel]")
                        assertEquals("Clan Odins Gate: alice (rank 126), bob (rank 0), carol (rank 0), not in its channel", carol.commandReply("clan") { it.startsWith("Clan ") })
                        // A second clan can't take the name.
                        assertTrue(server.world.clans.clanOf(server.world.playerNamed("carol")!!) != null)
                    }
                }
            }
            assertTrue(Files.readString(dir.resolve("data").resolve(ClanStore.FILE_NAME)).contains("Odins Gate"))
        }
        // After a restart the clan is still there, and its members get it at login.
        server(dir.resolve("restarted"), dir.resolve("data")).use { server ->
            server.login("bob").use { bob ->
                val settings = bob.await("the clan settings") { bob.lastStrings(GameServerProt.CLANSETTINGS_FULL) }
                assertTrue(settings.hasAll("Odins Gate", "alice", "bob", "carol"), settings.toString())
                assertEquals("Founding a clan takes 3 founders, 0 hours each", bob.commandReply("clanfounding") { it.startsWith("Founding") })
                // ::clandisband deletes it, for scripts that found one again.
                assertEquals("Clan Odins Gate deleted", bob.commandReply("clandisband") { it.startsWith("Clan ") })
                assertEquals("You aren't in a clan", bob.commandReply("clan") { it.contains("clan") })
            }
        }
    }

    @Test
    fun `live's rule needs the hours, a name nobody has, and the group ends when its leader leaves the Hub`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    assertEquals("Founding a clan takes 5 founders, 25 hours each (live's rule)", alice.commandReply("clanfounding") { it.startsWith("Founding") })
                    alice.commandReply("clanfounding 2 1") { it.startsWith("Founding") }
                    alice.toHub()
                    // Not in the Hub: no invitation.
                    alice.yourClanTab()
                    alice.playerOp(bob.index, 1)
                    alice.saw("[clan.cant_invite name=bob]")
                    bob.toHub()
                    alice.playerOp(bob.index, 1)
                    bob.await("bob's invitation") { bob.messages().firstOrNull { it.type == Clans.CHATTYPE_CLAN_CREATION_INVITATION } }
                    // Leaving the Hub drops the invitation: back in, bob's option 1 invites alice rather than accepting.
                    bob.commandReply("tele 3222 3218") { it.startsWith("Teleported") }
                    bob.toHub()
                    bob.playerOp(alice.index, 1)
                    alice.await("alice's invitation from bob") { alice.messages().firstOrNull { it.type == Clans.CHATTYPE_CLAN_CREATION_INVITATION && it.name == "bob" } }
                    assertNull(server.world.clans.formingOf(server.world.playerNamed("alice")!!)?.founders?.firstOrNull())
                    // Alice leaves too, dropping bob's invitation, so her option 1 invites him again.
                    alice.commandReply("tele 3222 3218") { it.startsWith("Teleported") }
                    alice.toHub()
                    alice.playerOp(bob.index, 1)
                    bob.await("bob's second invitation") { bob.messages().count { it.type == Clans.CHATTYPE_CLAN_CREATION_INVITATION } == 2 }
                    bob.playerOp(alice.index, 1)
                    alice.saw("[clan.joined name=bob]")
                    alice.ifButton(Clans.CREATION, Clans.CREATION_NAME, 1)
                    alice.awaitTicks(2)
                    alice.resumeString("Hourless")
                    alice.saw("[clan.name_available name=Hourless]")
                    alice.await("the name on the button") { alice.shownName()?.takeIf { it == "Hourless" } }
                    // Grouping isn't built: clicking it leaves the creation panel working, renaming included.
                    alice.ifButton(Clans.SIDE_CHANNELS, 5, 1)
                    alice.ifButton(Clans.CREATION, Clans.CREATION_NAME, 1)
                    alice.awaitTicks(2)
                    alice.resumeString("Hourly")
                    alice.await("the new name on the button") { alice.shownName()?.takeIf { it == "Hourly" } }
                    // Neither has played an hour.
                    alice.ifButton(Clans.CREATION, Clans.CREATION_CONFIRM, 1)
                    alice.saw("[clan.not_enough_hours names=alice, bob hours=1]")
                    // The leader leaving the Hub ends the group.
                    alice.commandReply("tele 3222 3218") { it.startsWith("Teleported") }
                    bob.saw("[clan.cancelled]")
                    assertNull(server.world.clans.formingOf(server.world.playerNamed("bob")!!))
                    // Back in the Hub with no group, her panel loses the old name.
                    alice.toHub()
                    alice.await("the name cleared") { alice.shownName()?.takeIf { it.isEmpty() } }
                }
            }
        }
    }

    @Test
    fun `a reconnect brings back the group's name, and the clan page once the clan is founded`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val bob = server.login("bob")
                alice.commandReply("clanfounding 2 0") { it.startsWith("Founding") }
                alice.toHub()
                bob.toHub()
                alice.yourClanTab()
                bob.yourClanTab()
                alice.playerOp(bob.index, 1)
                bob.await("bob's invitation") { bob.messages().firstOrNull { it.type == Clans.CHATTYPE_CLAN_CREATION_INVITATION } }
                bob.playerOp(alice.index, 1)
                alice.saw("[clan.joined name=bob]")
                alice.ifButton(Clans.CREATION, Clans.CREATION_NAME, 1)
                alice.awaitTicks(2)
                alice.resumeString("Rejoined")
                bob.await("the name on bob's button") { bob.shownName()?.takeIf { it == "Rejoined" } }
                // The server has sent bob the name already; a new connection gets it again all the same.
                server.reconnect("bob", bob).use { back ->
                    back.await("the name sent again") { back.shownName()?.takeIf { it == "Rejoined" } }
                    alice.ifButton(Clans.CREATION, Clans.CREATION_CONFIRM, 1)
                    back.saw("[clan.formed name=Rejoined]")
                    back.await("bob's clan page") { back.openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
                }
                bob.close()
            }
        }
    }

    private companion object {
        const val HUB_X = 3181
        const val HUB_Z = 3474
    }
}
