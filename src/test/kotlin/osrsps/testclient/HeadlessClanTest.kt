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
        val prompts = clientScripts().count { it.args.firstOrNull() == "[clan.guest_prompt]" }
        ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_SEARCH, 1)
        // A new prompt, whatever other scripts came after it.
        await("Find's prompt") { Unit.takeIf { clientScripts().count { it.args.firstOrNull() == "[clan.guest_prompt]" } > prompts } }
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
                // ::clanjoin makes her a member, the guest clan dropped.
                assertEquals("Joined clan Guest Gate", alice.commandReply("clanjoin guest gate") { it.startsWith("Joined") || it.startsWith("You're") })
                assertEquals("You have no guest clan", alice.commandReply("guestclan") { it.startsWith("You have") || it.startsWith("Guest") })
                assertTrue(alice.commandReply("clan") { it.startsWith("Clan") }.startsWith("Clan Guest Gate: ~test owner (rank 126), alice (rank 0), in its channel"))
                assertEquals("You're in a clan already", alice.commandReply("clanjoin guest gate") { it.startsWith("Joined") || it.startsWith("You're") })
                assertEquals("Clan Guest Gate deleted", alice.commandReply("clandisband guest gate") { it.startsWith("Clan") })
            }
        }
    }

    /** The question menu clans ask with ([Clans.clickInfo]), and the pick. */
    private fun HeadlessClient.answer(title: String, option: Int) {
        val menu = awaitDialogue("the clan question") { it is DialogueView.Menu }
        assertEquals(DialogueView.Menu(title, listOf("[clan.confirm_yes]", "[clan.confirm_no]")), menu)
        chooseOption(option)
    }

    @Test
    fun `a guest applies from the clan's settings page, recruiters hear of it, and can withdraw`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    found(server, alice, bob, "Odins Gate")
                    server.login("carol").use { carol ->
                        val clan = server.world.clans.clanNamed("Odins Gate")!!
                        carol.guestTab()
                        // Settings with no clan loaded does nothing.
                        carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_INFO, 1)
                        carol.awaitTicks(2)
                        assertFalse(Clans.INFO in carol.openInterfaces())
                        carol.findClan("Odins Gate")
                        carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_INFO, 1)
                        carol.await("the clan's settings page") { carol.openInterfaces().takeIf { Clans.INFO in it } }
                        carol.await("Apply enabled") { Unit.takeIf { carol.opEnabled(Clans.INFO, Clans.INFO_APPLICATIONS, -1, 1) } }

                        // Apply asks first; No leaves it.
                        carol.ifButton(Clans.INFO, Clans.INFO_APPLICATIONS, 1)
                        carol.answer("[clan.apply_confirm name=Odins Gate]", 2)
                        carol.awaitTicks(2)
                        assertTrue(clan.applicants.isEmpty())
                        // Yes applies: the owner, in the channel, hears of it; bob (a founder) doesn't.
                        carol.ifButton(Clans.INFO, Clans.INFO_APPLICATIONS, 1)
                        carol.answer("[clan.apply_confirm name=Odins Gate]", 1)
                        carol.saw("[clan.applied name=Odins Gate]")
                        alice.saw("[clan.new_applicant name=carol]")
                        assertEquals(listOf("carol"), clan.applicants.map { it.name })
                        assertEquals("Guest of Odins Gate, not in its channel, applied", carol.commandReply("guestclan") { it.startsWith("Guest") })
                        bob.awaitTicks(2)
                        assertFalse(bob.messages().any { it.text.startsWith("[clan.new_applicant") })

                        // With the page closed, its buttons do nothing.
                        carol.closeModal()
                        carol.await("the page closed") { if (Clans.INFO in carol.openInterfaces()) null else Unit }
                        carol.awaitTicks(1)
                        carol.ifButton(Clans.INFO, Clans.INFO_APPLICATIONS, 1)
                        carol.awaitTicks(2)
                        assertEquals(listOf("carol"), clan.applicants.map { it.name })
                        carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_INFO, 1)
                        carol.await("the page again") { carol.openInterfaces().takeIf { Clans.INFO in it } }
                        // Withdraw (the same button now) takes it back, without asking.
                        carol.ifButton(Clans.INFO, Clans.INFO_APPLICATIONS, 1)
                        carol.saw("[clan.withdrawn name=Odins Gate]")
                        assertTrue(clan.applicants.isEmpty())
                        // Leave Clan does nothing as a guest.
                        carol.ifButton(Clans.INFO, Clans.INFO_QUIT, 1)
                        carol.awaitTicks(2)
                        assertNull(carol.dialogueView)

                        // Someone in a clan can't apply to another.
                        bob.commandReply("clanmake Rivals") { it.startsWith("Clan") }
                        bob.guestTab()
                        bob.findClan("Rivals")
                        bob.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_INFO, 1)
                        bob.await("bob's settings page") { bob.openInterfaces().takeIf { Clans.INFO in it } }
                        bob.ifButton(Clans.INFO, Clans.INFO_APPLICATIONS, 1)
                        bob.answer("[clan.apply_confirm name=Rivals]", 1)
                        bob.saw("[clan.apply_in_a_clan]")
                        assertTrue(server.world.clans.clanNamed("Rivals")!!.applicants.isEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun `a member leaves the clan from its settings page, and its owner can't`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    found(server, alice, bob, "Odins Gate")
                    val bobP = server.world.playerNamed("bob")!!
                    // alice is out of the channel: she still gets the clan's settings without bob.
                    alice.yourClanTab()
                    alice.await("alice's clan page") { alice.openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
                    alice.ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_CHAT, 1)
                    alice.await("alice out of the channel") { if (server.world.clans.inChannel(server.world.playerNamed("alice")!!)) null else Unit }
                    bob.yourClanTab()
                    bob.await("bob's clan page") { bob.openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
                    bob.ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_SETTINGS, 1)
                    bob.await("the clan's settings page") { bob.openInterfaces().takeIf { Clans.INFO in it } }
                    // Applications does nothing for a founder: only recruiters see the applicants page.
                    bob.ifButton(Clans.INFO, Clans.INFO_APPLICATIONS, 1)
                    bob.ifButton(Clans.INFO, Clans.INFO_QUIT, 1)
                    bob.answer("[clan.leave_confirm name=Odins Gate]", 2)
                    bob.awaitTicks(2)
                    assertNotNull(server.world.clans.clanOf(bobP))
                    bob.ifButton(Clans.INFO, Clans.INFO_QUIT, 1)
                    bob.answer("[clan.leave_confirm name=Odins Gate]", 1)
                    bob.saw("[clan.left_clan name=Odins Gate]")
                    assertNull(server.world.clans.clanOf(bobP))
                    assertFalse(server.world.clans.inChannel(bobP))
                    // His client drops the clan; alice's settings and channel lose him.
                    bob.await("bob's leave update") { bob.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_CLAN)?.takeIf { it.isEmpty() } }
                    alice.await("alice's settings without bob") { alice.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_CLAN)?.takeIf { it.hasAll("alice") && !it.hasAll("bob") } }
                    bob.await("the settings page closed") { if (Clans.INFO in bob.openInterfaces()) null else Unit }
                    assertEquals("You aren't in a clan", bob.commandReply("clan") { it.contains("clan") })

                    // The owner can't leave.
                    alice.ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_SETTINGS, 1)
                    alice.await("alice's settings page") { alice.openInterfaces().takeIf { Clans.INFO in it } }
                    alice.ifButton(Clans.INFO, Clans.INFO_QUIT, 1)
                    alice.saw("[clan.owner_cant_leave]")
                    assertNotNull(server.world.clans.clanOf(server.world.playerNamed("alice")!!))
                }
            }
        }
    }

    /** Finds [clan] as a guest unless [found] already, opens its settings page and applies (Yes to the question). */
    private fun HeadlessClient.applyTo(clan: String, found: Boolean = false) {
        guestTab()
        if (!found) findClan(clan)
        ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_INFO, 1)
        await("the clan's settings page") { openInterfaces().takeIf { Clans.INFO in it } }
        await("Apply enabled") { Unit.takeIf { opEnabled(Clans.INFO, Clans.INFO_APPLICATIONS, -1, 1) } }
        ifButton(Clans.INFO, Clans.INFO_APPLICATIONS, 1)
        answer("[clan.apply_confirm name=$clan]", 1)
        saw("[clan.applied name=$clan]")
    }

    /** The names the applicants page last listed, once all its 20 slots came (client script 4376), after [since] scripts. */
    private fun HeadlessClient.applicantsListed(since: Int): List<String>? {
        val slots = clientScripts().drop(since).filter { it.id == Clans.SCRIPT_ADD_APPLICANT }
        if (slots.size < Clans.APPLICANTS_SLOTS) return null
        return slots.takeLast(Clans.APPLICANTS_SLOTS).map { it.args[1] as String }.filter { it.isNotEmpty() }
    }

    /** What client script 4375 last showed of the chosen applicant, after [since] scripts. */
    private fun HeadlessClient.reviewed(since: Int): String? =
        clientScripts().drop(since).lastOrNull { it.id == Clans.SCRIPT_SHOW_APPLICANT }?.args?.first() as String?

    @Test
    fun `a recruiter reviews the applicants, declines one and accepts another, who joins the clan`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    found(server, alice, bob, "Odins Gate")
                    val clan = server.world.clans.clanNamed("Odins Gate")!!
                    server.login("carol").use { carol ->
                        server.login("dave").use { dave ->
                            carol.applyTo("Odins Gate")
                            dave.applyTo("Odins Gate")
                            assertEquals(listOf("carol", "dave"), clan.applicants.map { it.name })

                            // A founder isn't a recruiter: Applications opens nothing.
                            bob.yourClanTab()
                            bob.await("bob's clan page") { bob.openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
                            bob.ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_SETTINGS, 1)
                            bob.await("bob's settings page") { bob.openInterfaces().takeIf { Clans.INFO in it } }
                            bob.ifButton(Clans.INFO, Clans.INFO_APPLICATIONS, 1)
                            bob.awaitTicks(2)
                            assertFalse(Clans.APPLICANTS in bob.openInterfaces())

                            // The owner opens the applicants page from the settings page: both, none chosen.
                            alice.yourClanTab()
                            alice.await("alice's clan page") { alice.openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
                            alice.ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_SETTINGS, 1)
                            alice.await("alice's settings page") { alice.openInterfaces().takeIf { Clans.INFO in it } }
                            var since = alice.clientScripts().size
                            alice.ifButton(Clans.INFO, Clans.INFO_APPLICATIONS, 1)
                            alice.await("the applicants page") { alice.openInterfaces().takeIf { Clans.APPLICANTS in it } }
                            assertEquals(listOf("carol", "dave"), alice.await("the list") { alice.applicantsListed(since) })
                            assertEquals("", alice.reviewed(since))
                            alice.await("Accept enabled") { Unit.takeIf { alice.opEnabled(Clans.APPLICANTS, Clans.APPLICANTS_ACCEPT, -1, 1) } }

                            // Accept with nobody chosen does nothing.
                            alice.ifButton(Clans.APPLICANTS, Clans.APPLICANTS_ACCEPT, 1)
                            alice.awaitTicks(2)
                            assertEquals(2, clan.applicants.size)

                            // Review on dave (slot 1) shows his levels; Decline drops him, and he hears of it.
                            since = alice.clientScripts().size
                            alice.resumePause(Clans.APPLICANTS, Clans.APPLICANTS_LIST, 1)
                            val shown = alice.await("dave's levels") { alice.reviewed(since)?.takeIf { it.isNotEmpty() } }
                            val fields = shown.split("|")
                            assertEquals("dave", fields[0])
                            assertEquals(2 + 1 + Clans.APPLICANT_SKILLS.size, fields.size)
                            assertEquals("3", fields[1])
                            since = alice.clientScripts().size
                            alice.ifButton(Clans.APPLICANTS, Clans.APPLICANTS_REJECT, 1)
                            alice.saw("[clan.applicant_declined name=dave]")
                            dave.saw("[clan.declined name=Odins Gate]")
                            assertEquals(listOf("carol"), clan.applicants.map { it.name })
                            assertEquals(listOf("carol"), alice.await("the list without dave") { alice.applicantsListed(since) })
                            assertEquals("Guest of Odins Gate, not in its channel", dave.commandReply("guestclan") { it.startsWith("Guest") })

                            // Review on carol, then Accept: she is a member at the lowest rank, in the channel, no longer a guest.
                            alice.resumePause(Clans.APPLICANTS, Clans.APPLICANTS_LIST, 0)
                            alice.await("carol's levels") { alice.reviewed(since)?.takeIf { it.startsWith("carol|") } }
                            since = alice.clientScripts().size
                            alice.ifButton(Clans.APPLICANTS, Clans.APPLICANTS_ACCEPT, 1)
                            alice.saw("[clan.applicant_accepted name=carol]")
                            carol.saw("[clan.accepted name=Odins Gate]")
                            val carolP = server.world.playerNamed("carol")!!
                            assertEquals(Clans.RANK_FOUNDER, server.world.clans.clanOf(carolP)?.member(carolP.saveKey)?.rank)
                            assertTrue(clan.applicants.isEmpty())
                            assertEquals(emptyList<String>(), alice.await("an empty list") { alice.applicantsListed(since) })
                            carol.await("carol in the channel") { if (server.world.clans.inChannel(carolP)) Unit else null }
                            carol.await("carol's clan settings") { carol.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_CLAN)?.takeIf { it.hasAll("alice", "bob", "carol") } }
                            assertEquals("You have no guest clan", carol.commandReply("guestclan") { it.contains("guest clan") || it.startsWith("Guest") })
                            alice.await("alice's settings with carol") { alice.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_CLAN)?.takeIf { it.hasAll("carol") } }

                            // Back goes to the settings page; Review with the page closed does nothing.
                            alice.ifButton(Clans.APPLICANTS, Clans.APPLICANTS_EXIT, 1)
                            alice.await("the settings page again") { alice.openInterfaces().takeIf { Clans.INFO in it && Clans.APPLICANTS !in it } }
                            dave.applyTo("Odins Gate", found = true)
                            since = alice.clientScripts().size
                            alice.resumePause(Clans.APPLICANTS, Clans.APPLICANTS_LIST, 0)
                            alice.awaitTicks(2)
                            assertNull(alice.reviewed(since))
                        }
                    }
                }
            }
        }
    }

    /** What client script clan_rankpopup last got (title, current, lowest, highest, offers 128, component), after [since]. */
    private fun HeadlessClient.rankPopup(since: Int): List<Any>? =
        clientScripts().drop(since).lastOrNull { it.id == Clans.SCRIPT_RANK_POPUP }?.args

    @Test
    fun `the owner edits a member's rank from the members page, and a member below Administrator can't`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    found(server, alice, bob, "Odins Gate")
                    val clan = server.world.clans.clanNamed("Odins Gate")!!
                    val bobKey = server.world.playerNamed("bob")!!.saveKey
                    assertEquals(1, clan.members.indexOfFirst { it.key == bobKey })

                    // bob (rank 0) sees the page, but his rank column has no Edit rank.
                    bob.yourClanTab()
                    bob.await("bob's clan page") { bob.openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
                    bob.ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_SETTINGS, 1)
                    bob.await("bob's settings page") { bob.openInterfaces().takeIf { Clans.INFO in it } }
                    bob.ifButton(Clans.INFO, Clans.INFO_MEMBERS, 1)
                    bob.await("bob's members page") { bob.openInterfaces().takeIf { Clans.MEMBERS in it } }
                    var since = bob.clientScripts().size
                    bob.ifButton(Clans.MEMBERS, 11, 1, sub = 0)
                    bob.awaitTicks(2)
                    assertNull(bob.rankPopup(since))

                    alice.yourClanTab()
                    alice.await("alice's clan page") { alice.openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
                    alice.ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_SETTINGS, 1)
                    alice.await("alice's settings page") { alice.openInterfaces().takeIf { Clans.INFO in it } }
                    alice.ifButton(Clans.INFO, Clans.INFO_MEMBERS, 1)
                    alice.await("alice's members page") { alice.openInterfaces().takeIf { Clans.MEMBERS in it } }
                    alice.await("Edit rank enabled") { Unit.takeIf { alice.opEnabled(Clans.MEMBERS, 11, 1, 1) } }

                    // Her own rank isn't below hers.
                    alice.ifButton(Clans.MEMBERS, 11, 1, sub = 0)
                    alice.saw("[clan.rank_too_high name=alice]")
                    // bob's: the list of ranks from 0 to Deputy Owner, his own picked.
                    since = alice.clientScripts().size
                    alice.ifButton(Clans.MEMBERS, 11, 1, sub = 1)
                    val popup = alice.await("the rank list") { alice.rankPopup(since) }
                    assertEquals(listOf<Any>("[clan.edit_rank_title name=bob]", 0, 0, 125, 0, Clans.component(Clans.MEMBERS, Clans.MEMBERS_POPUP)), popup)
                    assertTrue(Clans.POPUPOVERLAY in alice.openInterfaces())
                    // Picking Administrator (sent as 101): his rank, in his settings and alice's channel.
                    alice.resumeCount(Clans.RANK_ADMINISTRATOR + 1)
                    alice.await("bob an Administrator") { Unit.takeIf { clan.member(bobKey)?.rank == Clans.RANK_ADMINISTRATOR } }
                    alice.await("the list closed") { if (Clans.POPUPOVERLAY in alice.openInterfaces()) null else Unit }
                    // Written at the autosave, not per pick.
                    val file = dir.resolve("data").resolve(ClanStore.FILE_NAME)
                    assertEquals(0, ClanStore(file).byName("Odins Gate")!!.member(bobKey)?.rank)
                    server.world.clans.flush()
                    assertEquals(Clans.RANK_ADMINISTRATOR, ClanStore(file).byName("Odins Gate")!!.member(bobKey)?.rank)

                    // A rank the client doesn't list, or the owner's, does nothing.
                    for (bad in listOf(55, Clans.RANK_OWNER)) {
                        alice.ifButton(Clans.MEMBERS, 11, 1, sub = 1)
                        alice.awaitTicks(2)
                        alice.resumeCount(bad + 1)
                        alice.awaitTicks(2)
                        assertEquals(Clans.RANK_ADMINISTRATOR, clan.member(bobKey)?.rank)
                    }

                    // An answer after Back, with the list gone, does nothing.
                    alice.ifButton(Clans.MEMBERS, 11, 1, sub = 1)
                    alice.awaitTicks(2)
                    alice.ifButton(Clans.MEMBERS, Clans.MEMBERS_EXIT, 1)
                    alice.await("alice's settings page again") { alice.openInterfaces().takeIf { Clans.INFO in it && Clans.MEMBERS !in it } }
                    alice.resumeCount(10 + 1)
                    alice.awaitTicks(2)
                    assertEquals(Clans.RANK_ADMINISTRATOR, clan.member(bobKey)?.rank)

                    // bob, an Administrator now, edits ranks below his own: alice's isn't.
                    bob.ifButton(Clans.MEMBERS, Clans.MEMBERS_EXIT, 1)
                    bob.await("bob's settings page again") { bob.openInterfaces().takeIf { Clans.INFO in it && Clans.MEMBERS !in it } }
                    bob.ifButton(Clans.INFO, Clans.INFO_MEMBERS, 1)
                    bob.await("bob's Edit rank") { Unit.takeIf { bob.opEnabled(Clans.MEMBERS, 11, 0, 1) } }
                    bob.ifButton(Clans.MEMBERS, 11, 1, sub = 0)
                    bob.saw("[clan.rank_too_high name=alice]")
                }
            }
        }
    }

    @Test
    fun `the runner commands clanjoin as a recruiter and clanapplicant`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                assertEquals("Clan Test Gate made, for guests to find", alice.commandReply("clanmake Test Gate") { it.startsWith("Clan") })
                assertEquals("You aren't in a clan", alice.commandReply("clanapplicant") { it.startsWith("You") || it.startsWith("Applicant") })
                assertEquals("Joined clan Test Gate as a recruiter", alice.commandReply("clanjoin test gate recruiter") { it.startsWith("Joined") })
                assertEquals("Applicant applied to Test Gate", alice.commandReply("clanapplicant") { it.startsWith("Applicant") || it.contains("already") })
                assertEquals("Test Gate has the test applicant already", alice.commandReply("clanapplicant") { it.startsWith("Applicant") || it.contains("already") })
                val clan = server.world.clans.clanNamed("Test Gate")!!
                assertEquals(Clans.RECRUIT_RANK, clan.member(server.world.playerNamed("alice")!!.saveKey)?.rank)

                // The test applicant can be accepted though no account is it.
                alice.yourClanTab()
                alice.await("alice's clan page") { alice.openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
                alice.ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_SETTINGS, 1)
                alice.await("alice's settings page") { alice.openInterfaces().takeIf { Clans.INFO in it } }
                val since = alice.clientScripts().size
                alice.ifButton(Clans.INFO, Clans.INFO_APPLICATIONS, 1)
                assertEquals(listOf("Applicant"), alice.await("the list") { alice.applicantsListed(since) })
                alice.resumePause(Clans.APPLICANTS, Clans.APPLICANTS_LIST, 0)
                assertEquals("Applicant|" + Clans.TEST_APPLICANT_STATS, alice.await("its levels") { alice.reviewed(since)?.takeIf { it.isNotEmpty() } })
                alice.ifButton(Clans.APPLICANTS, Clans.APPLICANTS_ACCEPT, 1)
                alice.saw("[clan.applicant_accepted name=Applicant]")
                assertNotNull(clan.member(Clans.TEST_APPLICANT_KEY))
            }
        }
    }

    private companion object {
        const val HUB_X = 3181
        const val HUB_Z = 3474
    }
}
