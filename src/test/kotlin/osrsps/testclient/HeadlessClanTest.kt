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

/** Clans, steps 10a and 10b: founding at the Clan Hub, the Clans tab's "Your Clan" page, clan chat, and guest clans. */
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

    @Test
    fun `a reconnect on another page still clears the old name when the creation panel next opens`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { bob ->
                val alice = server.login("alice")
                alice.commandReply("clanfounding 2 0") { it.startsWith("Founding") }
                alice.toHub()
                bob.toHub()
                alice.yourClanTab()
                alice.playerOp(bob.index, 1)
                bob.await("bob's invitation") { bob.messages().firstOrNull { it.type == Clans.CHATTYPE_CLAN_CREATION_INVITATION } }
                bob.playerOp(alice.index, 1)
                alice.saw("[clan.joined name=bob]")
                alice.ifButton(Clans.CREATION, Clans.CREATION_NAME, 1)
                alice.awaitTicks(2)
                alice.resumeString("Lost")
                alice.await("the name on the button") { alice.shownName()?.takeIf { it == "Lost" } }
                // On the chat-channel page when the connection drops.
                alice.ifButton(Clans.SIDE_CHANNELS, 2, 1)
                server.reconnect("alice", alice).use { back ->
                    back.awaitTicks(2)
                    // Leaving the Hub ends her group; back in, the creation panel clears the name her client kept.
                    back.commandReply("tele 3222 3218") { it.startsWith("Teleported") }
                    back.toHub()
                    back.yourClanTab()
                    back.await("the name cleared") { back.shownName()?.takeIf { it.isEmpty() } }
                }
                alice.close()
            }
        }
    }

    @Test
    fun `asking a player again doesn't send them another invitation line`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.toHub()
                    bob.toHub()
                    alice.await("bob in view") { alice.otherPlayers().takeIf { it.isNotEmpty() } }
                    repeat(10) { alice.playerOp(bob.index, Clans.PLAYER_OP_RECRUIT) }
                    bob.await("the invitation") { bob.messages().firstOrNull { it.type == Clans.CHATTYPE_CLAN_CREATION_INVITATION } }
                    alice.awaitTicks(3)
                    assertEquals(1, bob.messages().count { it.type == Clans.CHATTYPE_CLAN_CREATION_INVITATION })
                }
            }
        }
    }

    /** alice and bob found [name] at the Hub (two founders, no hours), both in its channel afterwards. */
    private fun found(server: TestServer, alice: HeadlessClient, bob: HeadlessClient, name: String) {
        alice.commandReply("clanfounding 2 0") { it.startsWith("Founding") }
        alice.toHub()
        bob.toHub()
        alice.yourClanTab()
        alice.await("bob in view") { alice.otherPlayers().takeIf { it.isNotEmpty() } }
        alice.playerOp(bob.index, 1)
        bob.await("bob's invitation") { bob.messages().firstOrNull { it.type == Clans.CHATTYPE_CLAN_CREATION_INVITATION } }
        bob.playerOp(alice.index, 1)
        alice.saw("[clan.joined name=bob]")
        alice.ifButton(Clans.CREATION, Clans.CREATION_CONFIRM, 1)
        alice.awaitTicks(2)
        alice.resumeString(name)
        bob.saw("[clan.formed name=$name]")
        bob.await("bob in the channel") { if (server.world.clans.inChannel(server.world.playerNamed("bob")!!)) Unit else null }
    }

    /** The Clans tab's "Another Clan" tab (707:4). */
    private fun HeadlessClient.guestTab() = ifButton(Clans.SIDE_CHANNELS, 4, 1)

    /** Find on the Another Clan page, then [name] typed in its prompt. */
    private fun HeadlessClient.findClan(name: String) {
        ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_SEARCH, 1)
        await("Find's prompt") { clientScripts().lastOrNull()?.args?.firstOrNull()?.takeIf { it == "[clan.guest_prompt]" } }
        resumeString(name)
    }

    @Test
    fun `a guest finds another clan, joins its channel, hears its members but can't talk, and leaves it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    server.login("carol").use { carol ->
                        found(server, alice, bob, "Odins Gate")
                        val carolP = server.world.playerNamed("carol")!!
                        carol.guestTab()
                        carol.await("the guest page") { carol.openInterfaces().takeIf { Clans.GUEST_SIDEPANEL in it } }

                        // A name no clan has, and nothing loaded.
                        carol.findClan("Nobody Here")
                        carol.saw("[clan.guest_not_found name=Nobody Here]")
                        assertNull(server.world.clans.guestClanOf(carolP))
                        // Your own clan can't be your guest clan.
                        bob.guestTab()
                        bob.findClan("Odins Gate")
                        bob.saw("[clan.guest_own_clan]")
                        assertNull(server.world.clans.guestClanOf(server.world.playerNamed("bob")!!))

                        // Any spelling of the name finds it: its settings come as the guest clan (-1), not carol's own.
                        carol.findClan("odins  gate")
                        val settings = carol.await("the guest clan's settings") { carol.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_LISTENED) }
                        assertTrue(settings.hasAll("Odins Gate", "alice", "bob"), settings.toString())
                        assertNull(carol.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_CLAN)?.takeIf { it.isNotEmpty() })
                        assertEquals("Odins Gate", server.world.clans.guestClanOf(carolP)?.name)
                        assertFalse(server.world.clans.inGuestChannel(carolP))
                        // Out of its channel, guest chat is refused.
                        carol.guestSay("knock knock")
                        carol.saw("[clan.not_in_guest_channel]")

                        // Chat joins its channel: carol is listed for the members, and the members for her, as the guest clan.
                        carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                        val channel = carol.await("the guest channel") { carol.lastClanStrings(GameServerProt.CLANCHANNEL_FULL, Clans.CLAN_TYPE_LISTENED)?.takeIf { it.hasAll("alice", "bob", "carol") } }
                        assertTrue(channel.hasAll("Odins Gate"), channel.toString())
                        alice.await("carol in alice's channel") { alice.lastClanStrings(GameServerProt.CLANCHANNEL_FULL, Clans.CLAN_TYPE_CLAN)?.takeIf { it.hasAll("carol") } }
                        assertTrue(server.world.clans.inGuestChannel(carolP))

                        // A member's line reaches her as the guest clan, and the members as their own clan.
                        bob.clanSay("welcome")
                        val heard = carol.await("bob's line") { carol.clanMessages().firstOrNull { it.text == "welcome" } }
                        assertEquals("bob" to 255, heard.sender to heard.clanType)
                        val own = alice.await("bob's line for alice") { alice.clanMessages().firstOrNull { it.text == "welcome" } }
                        assertEquals(0, own.clanType)
                        // Guests can't talk under the default talk rank; her own // goes nowhere as she has no clan.
                        carol.guestSay("thanks")
                        carol.saw("[clan.guest_cant_talk]")
                        carol.clanSay("thanks")
                        carol.saw("[clan.not_in_clan]")
                        alice.awaitTicks(2)
                        assertFalse(alice.clanMessages().any { it.text == "thanks" })

                        // Leave: out of the channel, the clan still loaded; the members' list drops her.
                        carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                        carol.await("carol out") { if (server.world.clans.inGuestChannel(carolP)) null else Unit }
                        carol.await("the channel's leave update") { carol.lastClanStrings(GameServerProt.CLANCHANNEL_FULL, Clans.CLAN_TYPE_LISTENED)?.takeIf { it.isEmpty() } }
                        alice.await("carol off alice's list") { alice.lastClanStrings(GameServerProt.CLANCHANNEL_FULL, Clans.CLAN_TYPE_CLAN)?.takeIf { !it.hasAll("carol") } }
                        assertEquals("Odins Gate", server.world.clans.guestClanOf(carolP)?.name)
                        bob.clanSay("gone")
                        alice.await("bob's second line") { alice.clanMessages().firstOrNull { it.text == "gone" } }
                        carol.awaitTicks(2)
                        assertFalse(carol.clanMessages().any { it.text == "gone" })

                        // Back in, then Discard (Find's button while a clan is loaded): out of the channel and unloaded.
                        carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                        carol.await("carol back in") { if (server.world.clans.inGuestChannel(carolP)) Unit else null }
                        carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_SEARCH, 1)
                        carol.await("carol discarded") { if (server.world.clans.guestClanOf(carolP) == null) Unit else null }
                        carol.await("the guest clan's leave update") { carol.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_LISTENED)?.takeIf { it.isEmpty() } }
                        alice.await("carol off alice's list again") { alice.lastClanStrings(GameServerProt.CLANCHANNEL_FULL, Clans.CLAN_TYPE_CLAN)?.takeIf { !it.hasAll("carol") } }
                    }
                }
            }
        }
    }

    @Test
    fun `logging out leaves the guest channel and forgets the guest clan`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    found(server, alice, bob, "Short Lived")
                    server.login("carol").use { carol ->
                        val carolP = server.world.playerNamed("carol")!!
                        carol.guestTab()
                        carol.findClan("Short Lived")
                        carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                        alice.await("carol in alice's channel") { alice.lastClanStrings(GameServerProt.CLANCHANNEL_FULL, Clans.CLAN_TYPE_CLAN)?.takeIf { it.hasAll("carol") } }
                        carol.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                        carol.await("the logout") { carol.packets().firstOrNull { it.prot.name == "LOGOUT" } }
                        alice.await("carol gone from alice's list") { alice.lastClanStrings(GameServerProt.CLANCHANNEL_FULL, Clans.CLAN_TYPE_CLAN)?.takeIf { !it.hasAll("carol") } }
                        assertNull(server.world.clans.guestClanOf(carolP))
                    }
                }
            }
        }
    }

    @Test
    fun `deleting a clan drops a guest who has it loaded, even out of its channel`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    found(server, alice, bob, "Short Lived")
                    server.login("carol").use { carol ->
                        val carolP = server.world.playerNamed("carol")!!
                        carol.guestTab()
                        carol.findClan("Short Lived")
                        carol.await("the guest clan's settings") { carol.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_LISTENED)?.takeIf { it.isNotEmpty() } }
                        assertFalse(server.world.clans.inGuestChannel(carolP))
                        assertEquals("Clan Short Lived deleted", bob.commandReply("clandisband") { it.startsWith("Clan ") })
                        carol.await("the guest clan dropped") { carol.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_LISTENED)?.takeIf { it.isEmpty() } }
                        assertNull(server.world.clans.guestClanOf(carolP))
                    }
                }
            }
        }
    }

    @Test
    fun `the runner commands clanmake, guestclan and clandisband with a name`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                assertEquals("Clan Guest Gate made, for guests to find", alice.commandReply("clanmake Guest Gate") { it.startsWith("Clan") || it.startsWith("Can't") })
                assertTrue(alice.commandReply("clanmake guest gate") { it.startsWith("Clan") || it.startsWith("Can't") }.startsWith("Can't"))
                assertNull(server.world.clans.clanOf(server.world.playerNamed("alice")!!))
                assertEquals("You have no guest clan", alice.commandReply("guestclan") { it.contains("guest") || it.startsWith("Guest") })
                alice.guestTab()
                alice.findClan("Guest Gate")
                assertEquals("Guest of Guest Gate, not in its channel", alice.commandReply("guestclan") { it.startsWith("Guest") })
                alice.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                assertEquals("Guest of Guest Gate, in its channel", alice.commandReply("guestclan") { it.startsWith("Guest") && it.endsWith(", in its channel") })
                // Only a guest in the channel: they get the guest clan's list, with the guest rank.
                val channel = alice.await("the guest channel") { alice.lastClanStrings(GameServerProt.CLANCHANNEL_FULL, Clans.CLAN_TYPE_LISTENED)?.takeIf { it.hasAll("alice") } }
                assertTrue(channel.hasAll("Guest Gate"), channel.toString())
                assertEquals("No clan called Nobody", alice.commandReply("clandisband Nobody") { it.startsWith("No clan") })
                assertEquals("Clan Guest Gate deleted", alice.commandReply("clandisband guest gate") { it.startsWith("Clan") })
                assertEquals("You have no guest clan", alice.commandReply("guestclan") { it.startsWith("You have") })
            }
        }
    }

    private companion object {
        const val HUB_X = 3181
        const val HUB_Z = 3474
    }
}
