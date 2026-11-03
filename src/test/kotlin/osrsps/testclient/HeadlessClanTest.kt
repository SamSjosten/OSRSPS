package osrsps.testclient

import net.rsprot.protocol.game.outgoing.prot.GameServerProt
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Quests
import osrsps.game.World
import osrsps.game.script.ScriptContext
import osrsps.game.social.ClanStore
import osrsps.game.social.Clans
import osrsps.game.social.GuestRequirement
import osrsps.map.CollisionMap
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Clans, steps 10a and 10b: founding at the Clan Hub, the Clans tab's "Your Clan" page, clan chat, and guest clans. */
class HeadlessClanTest {
    private fun server(dir: Path, data: Path = dir.resolve("data"), clock: () -> Instant = Instant::now) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(HUB_X shr 6, HUB_Z shr 6)
            },
            cycleMillis = millis,
            clansDir = data,
            clansClock = clock,
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
                        // Applying again (CLAN-3: Apply / Withdraw in a loop) doesn't tell the recruiters again this session.
                        carol.ifButton(Clans.INFO, Clans.INFO_APPLICATIONS, 1)
                        carol.answer("[clan.apply_confirm name=Odins Gate]", 1)
                        carol.await("applied again") { Unit.takeIf { carol.messages().count { it.text == "[clan.applied name=Odins Gate]" } == 2 } }
                        alice.awaitTicks(2)
                        assertEquals(1, alice.messages().count { it.text == "[clan.new_applicant name=carol]" })
                        carol.ifButton(Clans.INFO, Clans.INFO_APPLICATIONS, 1)
                        carol.await("withdrawn again") { Unit.takeIf { carol.messages().count { it.text == "[clan.withdrawn name=Odins Gate]" } == 2 } }
                        // A recruiter who ignores an applicant doesn't hear of them.
                        server.login("dave").use { dave ->
                            alice.addIgnore("dave")
                            alice.await("dave ignored") { alice.ignores().firstOrNull { it.equals("dave", ignoreCase = true) } }
                            dave.applyTo("Odins Gate")
                            alice.awaitTicks(2)
                            assertFalse(alice.messages().any { it.text == "[clan.new_applicant name=dave]" })
                            assertEquals(listOf("dave"), clan.applicants.map { it.name })
                            dave.ifButton(Clans.INFO, Clans.INFO_APPLICATIONS, 1)
                            dave.saw("[clan.withdrawn name=Odins Gate]")
                        }
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
                    assertFalse(bob.opEnabled(Clans.MEMBERS, 11, 0, 1))

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

                    // bob, an Administrator now, edits ranks below his own: alice's isn't. His open page got Edit rank.
                    assertTrue(Clans.MEMBERS in bob.openInterfaces())
                    bob.await("bob's Edit rank") { Unit.takeIf { bob.opEnabled(Clans.MEMBERS, 11, 0, 1) } }
                    bob.ifButton(Clans.MEMBERS, 11, 1, sub = 0)
                    bob.saw("[clan.rank_too_high name=alice]")
                }
            }
        }
    }

    @Test
    fun `the owner expels a member from the Expel column, after the question, and only those who may expel can`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    server.login("carol").use { carol ->
                        found(server, alice, bob, "Odins Gate")
                        assertEquals("Joined clan Odins Gate", carol.commandReply("clanjoin odins gate") { it.startsWith("Joined") || it.startsWith("You're") })
                        val clan = server.world.clans.clanNamed("Odins Gate")!!
                        val bobKey = server.world.playerNamed("bob")!!.saveKey
                        val carolKey = server.world.playerNamed("carol")!!.saveKey
                        val aliceKey = server.world.playerNamed("alice")!!.saveKey

                        // bob (rank 0) may pick what the columns show, but has nothing to click on a member.
                        bob.openMembersPage()
                        bob.await("bob's dropdown") { Unit.takeIf { bob.opEnabled(Clans.MEMBERS, Clans.MEMBERS_OPTIONS[1], Clans.COLUMN_EXPEL + 1, 1) } }
                        assertFalse(bob.opEnabled(Clans.MEMBERS, 13, 0, 1))

                        alice.openMembersPage()
                        alice.await("alice's Expel") { Unit.takeIf { alice.opEnabled(Clans.MEMBERS, 13, 1, 1) } }
                        // The second column shows Joined until a pick: a click on it does nothing.
                        alice.ifButton(Clans.MEMBERS, 13, 1, sub = clan.members.indexOfFirst { it.key == bobKey })
                        alice.awaitTicks(2)
                        assertNotEquals("[clan.expel_confirm name=bob]", (alice.dialogueView as? DialogueView.Menu)?.title)
                        // Expel picked in the second column's dropdown (child 7).
                        alice.ifButton(Clans.MEMBERS, Clans.MEMBERS_OPTIONS[1], 1, sub = Clans.COLUMN_EXPEL + 1)
                        // Her own entry isn't below her.
                        alice.ifButton(Clans.MEMBERS, 13, 1, sub = clan.members.indexOfFirst { it.key == aliceKey })
                        alice.saw("[clan.expel_too_high name=alice]")
                        // No to the question keeps bob; yes expels him.
                        alice.ifButton(Clans.MEMBERS, 13, 1, sub = clan.members.indexOfFirst { it.key == bobKey })
                        alice.answer("[clan.expel_confirm name=bob]", 2)
                        alice.awaitTicks(2)
                        assertNotNull(clan.member(bobKey))
                        alice.ifButton(Clans.MEMBERS, 13, 1, sub = clan.members.indexOfFirst { it.key == bobKey })
                        alice.answer("[clan.expel_confirm name=bob]", 1)
                        alice.saw("[clan.expelled_member name=bob]")
                        bob.saw("[clan.expelled name=Odins Gate]")
                        assertNull(clan.member(bobKey))
                        assertNull(server.world.clans.clanOf(server.world.playerNamed("bob")!!))
                        bob.await("bob's leave update") { bob.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_CLAN)?.takeIf { it.isEmpty() } }
                        bob.await("bob's members page closed") { if (Clans.MEMBERS in bob.openInterfaces()) null else Unit }
                        alice.await("alice's settings without bob") { alice.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_CLAN)?.takeIf { it.hasAll("alice", "carol") && !it.hasAll("bob") } }

                        // The first column still shows Rank: a click there is Edit rank.
                        val since = alice.clientScripts().size
                        alice.ifButton(Clans.MEMBERS, 11, 1, sub = clan.members.indexOfFirst { it.key == carolKey })
                        alice.await("the rank list") { alice.rankPopup(since) }

                        // carol, an Administrator, may edit ranks, but not expel once Expel needs 110.
                        clan.member(carolKey)!!.rank = Clans.RANK_ADMINISTRATOR
                        clan.settings[Clans.VAR_EXPEL] = 110
                        carol.openMembersPage()
                        carol.await("carol's columns") { Unit.takeIf { carol.opEnabled(Clans.MEMBERS, 13, 0, 1) } }
                        carol.ifButton(Clans.MEMBERS, Clans.MEMBERS_OPTIONS[1], 1, sub = Clans.COLUMN_EXPEL + 1)
                        carol.ifButton(Clans.MEMBERS, 13, 1, sub = clan.members.indexOfFirst { it.key == aliceKey })
                        carol.saw("[clan.expel_denied]")
                        assertNotNull(clan.member(aliceKey))
                    }
                }
            }
        }
    }

    @Test
    fun `the owner kicks a guest from the channel for an hour and bans another, who stays out until unbanned`(@TempDir dir: Path) {
        var now = Instant.parse("2026-11-03T06:00:00Z")
        server(dir, clock = { now }).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    server.login("carol").use { carol ->
                        server.login("dave").use { dave ->
                            found(server, alice, bob, "Odins Gate")
                            val clan = server.world.clans.clanNamed("Odins Gate")!!
                            val carolP = server.world.playerNamed("carol")!!
                            val daveP = server.world.playerNamed("dave")!!
                            for (guest in listOf(carol, dave)) {
                                guest.guestTab()
                                guest.await("the guest page") { guest.openInterfaces().takeIf { Clans.GUEST_SIDEPANEL in it } }
                                guest.findClan("Odins Gate")
                                guest.await("the guest clan") { guest.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_LISTENED)?.takeIf { it.isNotEmpty() } }
                                guest.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                            }
                            alice.await("both guests in") { Unit.takeIf { server.world.clans.inGuestChannel(carolP) && server.world.clans.inGuestChannel(daveP) } }

                            // bob (rank 0) is below the kick rank (the owner's) and Ban from chat (Administrator).
                            bob.kickFromClanChannel("carol", 2)
                            bob.saw("[clan.kick_denied]")
                            bob.banFromClanChannel("carol", 2)
                            bob.saw("[clan.ban_denied]")
                            // A member isn't a guest, and another clan type isn't the clan's channel: both do nothing.
                            alice.kickFromClanChannel("bob", 1)
                            alice.kickFromClanChannel("carol", 2, clanType = 1)
                            alice.awaitTicks(2)
                            assertTrue(server.world.clans.inGuestChannel(carolP))
                            assertNotNull(server.world.clans.clanOf(server.world.playerNamed("bob")!!))

                            // Kick: out of the channel, and kept out for the hour.
                            alice.kickFromClanChannel("CAROL", 2)
                            alice.saw("[clan.kicked_guest name=carol]")
                            carol.saw("[clan.kicked name=Odins Gate]")
                            assertFalse(server.world.clans.inGuestChannel(carolP))
                            carol.await("carol's leave update") { carol.lastClanStrings(GameServerProt.CLANCHANNEL_FULL, Clans.CLAN_TYPE_LISTENED)?.takeIf { it.isEmpty() } }
                            carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                            carol.saw("[clan.still_kicked name=Odins Gate]")
                            assertFalse(server.world.clans.inGuestChannel(carolP))
                            // A kick that ran out goes at the next kick, though carol never came back: only dave's is kept.
                            now = now.plusSeconds(Clans.KICK_MINUTES * 60 + 1)
                            alice.kickFromClanChannel("dave", 3)
                            alice.saw("[clan.kicked_guest name=dave]")
                            assertEquals(1, server.world.clans.kicksKept())
                            now = now.plusSeconds(Clans.KICK_MINUTES * 60 + 1)
                            carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                            carol.await("carol back in after the hour") { Unit.takeIf { server.world.clans.inGuestChannel(carolP) } }
                            dave.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                            dave.await("dave back in after the hour") { Unit.takeIf { server.world.clans.inGuestChannel(daveP) } }
                            assertEquals(0, server.world.clans.kicksKept())

                            // Ban: out of the channel and on the ban list, which the members' clan settings carry.
                            alice.banFromClanChannel("dave", 3)
                            alice.saw("[clan.banned_guest name=dave]")
                            dave.saw("[clan.banned name=Odins Gate]")
                            assertFalse(server.world.clans.inGuestChannel(daveP))
                            assertEquals(listOf(daveP.saveKey), clan.banned.map { it.key })
                            alice.await("the ban in alice's settings") { alice.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_CLAN)?.takeIf { it.hasAll("dave") } }
                            now = now.plusSeconds(Clans.KICK_MINUTES * 60 + 1)
                            dave.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                            dave.saw("[clan.still_banned name=Odins Gate]")
                            assertFalse(server.world.clans.inGuestChannel(daveP))

                            // The ban list: text typed with it closed is nothing to it; bob may look but not unban.
                            alice.resumeString("dave")
                            bob.openBannedPage()
                            bob.resumeString("dave")
                            bob.saw("[clan.unban_denied]")
                            assertNotNull(clan.banned(daveP.saveKey))
                            // Unban (the client sends the name as typed text): off the list. (The test cache has no varbits,
                            // so the clearing of clan_editing_lookupinprogress is for the VM script to see.)
                            alice.openBannedPage()
                            alice.resumeString("Dave")
                            alice.saw("[clan.unbanned name=dave]")
                            assertNull(clan.banned(daveP.saveKey))
                            alice.await("alice's settings without the ban") { alice.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_CLAN)?.takeIf { !it.hasAll("dave") } }
                            alice.ifButton(Clans.BANNED, Clans.BANNED_EXIT, 1)
                            alice.await("the settings page again") { alice.openInterfaces().takeIf { Clans.INFO in it } }
                            dave.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                            dave.await("dave back in") { Unit.takeIf { server.world.clans.inGuestChannel(daveP) } }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Talk in chat lets guests talk and Kick from chat lets members kick, set on the permissions page`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    server.login("carol").use { carol ->
                        found(server, alice, bob, "Odins Gate")
                        val clans = server.world.clans
                        val clan = clans.clanNamed("Odins Gate")!!
                        val carolP = server.world.playerNamed("carol")!!
                        carol.guestTab()
                        carol.await("the guest page") { carol.openInterfaces().takeIf { Clans.GUEST_SIDEPANEL in it } }
                        carol.findClan("Odins Gate")
                        carol.await("the guest clan") { carol.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_LISTENED)?.takeIf { it.isNotEmpty() } }
                        carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                        carol.await("carol in") { Unit.takeIf { clans.inGuestChannel(carolP) } }
                        carol.guestSay("hello?")
                        carol.saw("[clan.guest_cant_talk]")

                        alice.yourClanTab()
                        alice.await("the clan page") { alice.openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
                        alice.ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_SETTINGS, 1)
                        alice.await("the settings page") { alice.openInterfaces().takeIf { Clans.INFO in it } }
                        alice.ifButton(Clans.INFO, Clans.INFO_PERMISSIONS, 1)
                        alice.await("the permissions page") { alice.openInterfaces().takeIf { Clans.PERMISSIONS in it } }
                        val popupAt = Clans.component(Clans.PERMISSIONS, Clans.PERMISSIONS_POPUP)

                        // Talk in chat (slot 7) runs from Guest: picked, guests talk.
                        var since = alice.clientScripts().size
                        alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = 7)
                        assertEquals(listOf<Any>("[clan.permission_title]", Clans.RANK_FOUNDER, Clans.RANK_GUEST, Clans.RANK_OWNER, 0, popupAt), alice.await("the talk list") { alice.rankPopup(since) })
                        alice.resumeCount(0)
                        alice.await("Talk at Guest") { Unit.takeIf { clans.setting(clan, Clans.PERM_TALK) == Clans.RANK_GUEST } }
                        carol.guestSay("hello!")
                        val heard = alice.await("carol's line") { alice.clanMessages().firstOrNull { it.text == "hello!" } }
                        assertEquals("carol", heard.sender)

                        // Kick from chat (slot 8) rests on Talk but takes no Guest: it runs from rank 0, and Guest is refused.
                        alice.await("the list closed") { if (Clans.POPUPOVERLAY in alice.openInterfaces()) null else Unit }
                        since = alice.clientScripts().size
                        alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = 8)
                        assertEquals(listOf<Any>("[clan.permission_title]", Clans.RANK_OWNER, Clans.RANK_FOUNDER, Clans.RANK_OWNER, 0, popupAt), alice.await("the kick list") { alice.rankPopup(since) })
                        alice.resumeCount(0)
                        alice.awaitTicks(2)
                        assertEquals(Clans.RANK_OWNER, clans.setting(clan, Clans.PERM_KICK))
                        alice.await("the list closed again") { if (Clans.POPUPOVERLAY in alice.openInterfaces()) null else Unit }
                        alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = 8)
                        alice.awaitTicks(2)
                        alice.resumeCount(0 + 1)
                        alice.await("Kick at 0") { Unit.takeIf { clans.setting(clan, Clans.PERM_KICK) == 0 } }

                        // bob (rank 0) may kick now.
                        bob.kickFromClanChannel("carol", 2)
                        bob.saw("[clan.kicked_guest name=carol]")
                        assertFalse(clans.inGuestChannel(carolP))
                        // Talk raised above Kick: Kick needs Talk's rank, so bob can't kick any more.
                        clans.setPermission(server.world.playerNamed("alice")!!, clan.id, Clans.PERM_TALK, 10)
                        assertEquals(10, clans.setting(clan, Clans.PERM_TALK))
                        assertEquals(0, clans.setting(clan, Clans.PERM_KICK))
                        assertEquals(10, clans.permission(clan, Clans.PERM_KICK))
                        // Ban rests on Kick, and never needs less than Administrator.
                        assertEquals(Clans.RANK_ADMINISTRATOR, clans.permission(clan, Clans.VAR_BAN_FROM_CHAT))
                        clan.settings[Clans.PERM_KICK] = 110
                        assertEquals(110, clans.permission(clan, Clans.VAR_BAN_FROM_CHAT))
                    }
                }
            }
        }
    }

    @Test
    fun `the guest settings block guests or set requirements, which guests must meet to join the channel`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    server.login("carol").use { carol ->
                        found(server, alice, bob, "Odins Gate")
                        val clans = server.world.clans
                        val clan = clans.clanNamed("Odins Gate")!!
                        val carolP = server.world.playerNamed("carol")!!
                        carol.guestTab()
                        carol.await("the guest page") { carol.openInterfaces().takeIf { Clans.GUEST_SIDEPANEL in it } }
                        carol.findClan("Odins Gate")
                        carol.await("the guest clan") { carol.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_LISTENED)?.takeIf { it.isNotEmpty() } }
                        carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                        carol.await("carol in") { Unit.takeIf { clans.inGuestChannel(carolP) } }
                        assertTrue(clans.guestsMayListen(clan))
                        assertTrue(clans.settingVars(clan).none { it.id == Clans.VAR_GUEST_REQUIREMENTS })

                        // bob (rank 0) is below Permission access.
                        bob.yourClanTab()
                        bob.await("bob's clan page") { bob.openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
                        bob.ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_SETTINGS, 1)
                        bob.await("bob's settings page") { bob.openInterfaces().takeIf { Clans.INFO in it } }
                        bob.ifButton(Clans.INFO, Clans.INFO_PERMISSIONS, 1)
                        bob.await("bob's permissions page") { bob.openInterfaces().takeIf { Clans.PERMISSIONS in it } }
                        bob.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = Clans.GUEST_LISTEN_SLOT)
                        bob.saw("[clan.permission_denied]")

                        alice.yourClanTab()
                        alice.await("the clan page") { alice.openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
                        alice.ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_SETTINGS, 1)
                        alice.await("the settings page") { alice.openInterfaces().takeIf { Clans.INFO in it } }
                        alice.ifButton(Clans.INFO, Clans.INFO_PERMISSIONS, 1)
                        alice.await("the permissions page") { alice.openInterfaces().takeIf { Clans.PERMISSIONS in it } }

                        // Guests may listen: Blocked puts carol out, and keeps her out.
                        alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = Clans.GUEST_LISTEN_SLOT)
                        val menu = alice.awaitDialogue("the listen choice") { it is DialogueView.Menu }
                        assertEquals(DialogueView.Menu("[clan.guest_listen_title]", listOf("[clan.guest_permitted]", "[clan.guest_blocked]")), menu)
                        alice.chooseOption(2)
                        alice.await("guests blocked") { Unit.takeIf { !clans.guestsMayListen(clan) } }
                        carol.saw("[clan.guests_blocked name=Odins Gate]")
                        assertFalse(clans.inGuestChannel(carolP))
                        carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                        carol.await("refused while blocked") { carol.messages().count { it.text == "[clan.guests_blocked name=Odins Gate]" }.takeIf { it == 2 } }
                        assertFalse(clans.inGuestChannel(carolP))
                        assertTrue(alice.permissionsPageOpen())

                        // Permitted again.
                        alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = Clans.GUEST_LISTEN_SLOT)
                        alice.awaitDialogue("the listen choice again") { it is DialogueView.Menu }
                        alice.chooseOption(1)
                        alice.await("guests permitted") { Unit.takeIf { clans.guestsMayListen(clan) } }

                        // Guest minimum combat: typed, cut to what its 7 bits hold, then set to 50, which carol lacks.
                        var since = alice.clientScripts().size
                        alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = GuestRequirement.COMBAT.slot)
                        alice.await("the combat prompt") { alice.clientScripts().drop(since).lastOrNull { it.id == ScriptContext.MESLAYER_MODE7 }?.takeIf { it.args == listOf("[clan.guest_combat_prompt]") } }
                        alice.resumeCount(500)
                        alice.await("combat at 127") { Unit.takeIf { clans.guestRequirement(clan, GuestRequirement.COMBAT) == 127 } }
                        since = alice.clientScripts().size
                        alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = GuestRequirement.COMBAT.slot)
                        alice.await("the combat prompt again") { alice.clientScripts().drop(since).lastOrNull { it.id == ScriptContext.MESLAYER_MODE7 } }
                        alice.resumeCount(50)
                        alice.await("combat at 50") { Unit.takeIf { clans.guestRequirement(clan, GuestRequirement.COMBAT) == 50 } }
                        assertEquals(50, clans.settingVars(clan).single { it.id == Clans.VAR_GUEST_REQUIREMENTS }.value)
                        carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                        carol.saw("[clan.guest_requirements name=Odins Gate]")
                        assertFalse(clans.inGuestChannel(carolP))

                        // Skill total in its own bits, beside combat; back to none, carol gets in.
                        since = alice.clientScripts().size
                        alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = GuestRequirement.SKILL_TOTAL.slot)
                        alice.await("the total prompt") { alice.clientScripts().drop(since).lastOrNull { it.id == ScriptContext.MESLAYER_MODE7 }?.takeIf { it.args == listOf("[clan.guest_total_prompt]") } }
                        alice.resumeCount(1500)
                        alice.await("total at 1500") { Unit.takeIf { clans.guestRequirement(clan, GuestRequirement.SKILL_TOTAL) == 1500 } }
                        assertEquals(50 or (1500 shl 7), clans.settingVars(clan).single { it.id == Clans.VAR_GUEST_REQUIREMENTS }.value)
                        val aliceP = server.world.playerNamed("alice")!!
                        clans.setGuestRequirement(aliceP, clan.id, GuestRequirement.COMBAT, 0)
                        clans.setGuestRequirement(aliceP, clan.id, GuestRequirement.SKILL_TOTAL, 0)
                        // Quest points: the ones every account has from login (all quests done), so one more keeps carol out.
                        val qp = carolP.vars[Quests.VARP_QUEST_POINTS]
                        clans.setGuestRequirement(aliceP, clan.id, GuestRequirement.QUEST_POINTS, qp + 1)
                        carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                        carol.await("refused for quest points") { carol.messages().count { it.text == "[clan.guest_requirements name=Odins Gate]" }.takeIf { it == 2 } }
                        assertFalse(clans.inGuestChannel(carolP))
                        clans.setGuestRequirement(aliceP, clan.id, GuestRequirement.QUEST_POINTS, qp)
                        carol.ifButton(Clans.GUEST_SIDEPANEL, Clans.GUEST_CHAT, 1)
                        carol.await("carol back in") { Unit.takeIf { clans.inGuestChannel(carolP) } }
                        clans.setGuestRequirement(aliceP, clan.id, GuestRequirement.QUEST_POINTS, 0)

                        // Iron status: picked from the list; nobody here is an Ironman, but carol, already in, stays.
                        alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = GuestRequirement.IRON_STATUS.slot)
                        val iron = alice.awaitDialogue("the iron choice") { it is DialogueView.Menu }
                        assertEquals(DialogueView.Menu("[clan.guest_iron_title]", listOf("[clan.iron_none]", "[clan.iron_any]", "[clan.iron_uim]", "[clan.iron_hcim]")), iron)
                        alice.chooseOption(2)
                        alice.await("any Ironman") { Unit.takeIf { clans.guestRequirement(clan, GuestRequirement.IRON_STATUS) == 1 } }
                        assertTrue(clans.inGuestChannel(carolP))
                        assertEquals(1 shl 30, clans.settingVars(clan).single { it.id == Clans.VAR_GUEST_REQUIREMENTS }.value)
                        assertEquals("Guests permitted; combat 0, skill_total 0, quest_points 0, iron_status 1", alice.commandReply("clanguests") { it.startsWith("Guests") })
                    }
                }
            }
        }
    }

    private fun HeadlessClient.permissionsPageOpen() = Clans.PERMISSIONS in openInterfaces()

    /** Opens the ban list from the Your Clan page's settings. */
    private fun HeadlessClient.openBannedPage() {
        yourClanTab()
        await("the clan page") { openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
        ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_SETTINGS, 1)
        await("the settings page") { openInterfaces().takeIf { Clans.INFO in it } }
        ifButton(Clans.INFO, Clans.INFO_BANNED, 1)
        await("the ban list") { openInterfaces().takeIf { Clans.BANNED in it } }
    }

    /** Opens the members page from the Your Clan page's settings. */
    private fun HeadlessClient.openMembersPage() {
        yourClanTab()
        await("the clan page") { openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
        ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_SETTINGS, 1)
        await("the settings page") { openInterfaces().takeIf { Clans.INFO in it } }
        ifButton(Clans.INFO, Clans.INFO_MEMBERS, 1)
        await("the members page") { openInterfaces().takeIf { Clans.MEMBERS in it } }
    }

    @Test
    fun `the owner changes permissions, which a member without Permission access can't, and they take effect`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    found(server, alice, bob, "Odins Gate")
                    val clans = server.world.clans
                    val clan = clans.clanNamed("Odins Gate")!!
                    val popupAt = Clans.component(Clans.PERMISSIONS, Clans.PERMISSIONS_POPUP)
                    // The cache's defaults until changed.
                    assertEquals(Clans.RANK_ADMINISTRATOR, clans.setting(clan, Clans.VAR_ASSIGN_RANKS))

                    for (c in listOf(alice, bob)) {
                        c.yourClanTab()
                        c.await("the clan page") { c.openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
                        c.ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_SETTINGS, 1)
                        c.await("the settings page") { c.openInterfaces().takeIf { Clans.INFO in it } }
                        c.ifButton(Clans.INFO, Clans.INFO_PERMISSIONS, 1)
                        c.await("the permissions page") { c.openInterfaces().takeIf { Clans.PERMISSIONS in it } }
                    }
                    // bob (rank 0) lacks Permission access.
                    bob.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = 2)
                    bob.saw("[clan.permission_denied]")

                    // alice sets Assign member ranks (slot 2): the list runs from its floor, Administrator, to her rank.
                    // A rank below the floor is refused; 110 is taken.
                    var since = alice.clientScripts().size
                    alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = 2)
                    assertEquals(listOf<Any>("[clan.permission_title]", Clans.RANK_ADMINISTRATOR, Clans.RANK_ADMINISTRATOR, Clans.RANK_OWNER, 0, popupAt), alice.await("the rank list") { alice.rankPopup(since) })
                    alice.resumeCount(0 + 1)
                    alice.awaitTicks(2)
                    assertEquals(Clans.RANK_ADMINISTRATOR, clans.setting(clan, Clans.VAR_ASSIGN_RANKS))
                    alice.await("the list closed") { if (Clans.POPUPOVERLAY in alice.openInterfaces()) null else Unit }
                    alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = 2)
                    alice.awaitTicks(2)
                    alice.resumeCount(110 + 1)
                    alice.await("Assign member ranks at 110") { Unit.takeIf { clans.setting(clan, Clans.VAR_ASSIGN_RANKS) == 110 } }
                    // Expel rests on it: it needs 110 now though it is set to Administrator.
                    assertEquals(Clans.RANK_ADMINISTRATOR, clans.setting(clan, Clans.VAR_EXPEL))
                    assertEquals(110, clans.permission(clan, Clans.VAR_EXPEL))
                    // The clan settings carry each var as its rank plus 2, as client proc clan_permission_get reads it.
                    val vars = clans.settingVars(clan).associate { it.id to it.value }
                    assertEquals(110 + Clans.PERMISSION_VAR_OFFSET, vars[Clans.VAR_ASSIGN_RANKS])
                    assertEquals(Clans.RANK_OWNER + Clans.PERMISSION_VAR_OFFSET, vars[Clans.VAR_RECRUITMENT])
                    assertFalse(Clans.PERM_TALK in vars || Clans.PERM_KICK in vars)
                    // Recruitment (slot 3) has no floor: rank 0 is taken, Guest (sent as 0) isn't.
                    alice.await("the list closed again") { if (Clans.POPUPOVERLAY in alice.openInterfaces()) null else Unit }
                    alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = 3)
                    alice.awaitTicks(2)
                    alice.resumeCount(0)
                    alice.awaitTicks(2)
                    assertEquals(Clans.RANK_OWNER, clans.setting(clan, Clans.VAR_RECRUITMENT))
                    alice.await("the list closed a third time") { if (Clans.POPUPOVERLAY in alice.openInterfaces()) null else Unit }
                    alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = 3)
                    alice.awaitTicks(2)
                    alice.resumeCount(0 + 1)
                    alice.await("Recruitment at 0") { Unit.takeIf { clans.setting(clan, Clans.VAR_RECRUITMENT) == 0 } }
                    // A list made stale by someone else's change since: closing it (the current rank it showed) keeps theirs.
                    alice.await("the list closed a fourth time") { if (Clans.POPUPOVERLAY in alice.openInterfaces()) null else Unit }
                    alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = 2)
                    alice.awaitTicks(2)
                    clan.settings[Clans.VAR_ASSIGN_RANKS] = 120
                    alice.resumeCount(110 + 1)
                    alice.awaitTicks(2)
                    assertEquals(120, clans.setting(clan, Clans.VAR_ASSIGN_RANKS))
                    clan.settings[Clans.VAR_ASSIGN_RANKS] = 110
                    // bob, a recruiter now (rank 0 meets 0), opens the applicants page; Back still works after
                    // Recruitment is raised above him meanwhile.
                    bob.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_EXIT, 1)
                    bob.await("bob's settings page first") { bob.openInterfaces().takeIf { Clans.INFO in it && Clans.PERMISSIONS !in it } }
                    bob.ifButton(Clans.INFO, Clans.INFO_APPLICATIONS, 1)
                    bob.await("bob's applicants page") { bob.openInterfaces().takeIf { Clans.APPLICANTS in it } }
                    clan.settings[Clans.VAR_RECRUITMENT] = Clans.RANK_OWNER
                    bob.ifButton(Clans.APPLICANTS, Clans.APPLICANTS_EXIT, 1)
                    bob.await("bob's settings page from the applicants page") { bob.openInterfaces().takeIf { Clans.INFO in it && Clans.APPLICANTS !in it } }
                    bob.ifButton(Clans.INFO, Clans.INFO_PERMISSIONS, 1)
                    bob.await("bob's permissions page again") { bob.openInterfaces().takeIf { Clans.PERMISSIONS in it } }
                    clan.settings[Clans.VAR_RECRUITMENT] = 0
                    // No permission takes Nobody; Nobody kept by an earlier build reads as the owner's. A button not
                    // built (Guests may listen, slot 10) does nothing.
                    alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = 0)
                    alice.awaitTicks(2)
                    alice.resumeCount(Clans.RANK_NOBODY + 1)
                    alice.awaitTicks(2)
                    assertEquals(Clans.RANK_ADMINISTRATOR, clans.setting(clan, Clans.VAR_PERMISSION_ACCESS))
                    clan.settings[Clans.VAR_ICONS] = Clans.RANK_NOBODY
                    assertEquals(Clans.RANK_OWNER, clans.setting(clan, Clans.VAR_ICONS))
                    clan.settings.remove(Clans.VAR_ICONS)
                    alice.await("the list closed a fifth time") { if (Clans.POPUPOVERLAY in alice.openInterfaces()) null else Unit }
                    since = alice.clientScripts().size
                    alice.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_LIST, 1, sub = 10)
                    alice.awaitTicks(2)
                    assertNull(alice.rankPopup(since))

                    // Kept at the autosave.
                    val file = dir.resolve("data").resolve(ClanStore.FILE_NAME)
                    clans.flush()
                    val saved = ClanStore(file).byName("Odins Gate")!!
                    assertEquals(110, saved.settings[Clans.VAR_ASSIGN_RANKS])
                    assertEquals(0, saved.settings[Clans.VAR_RECRUITMENT])

                    // bob at 110 now assigns ranks: Edit rank on alice reaches the rank check.
                    clan.member(server.world.playerNamed("bob")!!.saveKey)!!.rank = 110
                    bob.ifButton(Clans.PERMISSIONS, Clans.PERMISSIONS_EXIT, 1)
                    bob.await("bob's settings page again") { bob.openInterfaces().takeIf { Clans.INFO in it && Clans.PERMISSIONS !in it } }
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

    @Test
    fun `the runner command clanbanned puts a ban on the list that Unban takes off`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                assertEquals("You aren't in a clan", alice.commandReply("clanbanned") { it.startsWith("You") || it.startsWith("Banned") })
                alice.commandReply("clanmake Ban Gate") { it.startsWith("Clan") }
                alice.commandReply("clanjoin ban gate recruiter") { it.startsWith("Joined") }
                assertEquals("Banned banned from Ban Gate", alice.commandReply("clanbanned") { it.startsWith("Banned") || it.contains("already") })
                assertEquals("Ban Gate has the test ban already", alice.commandReply("clanbanned") { it.startsWith("Banned") || it.contains("already") })
                val clan = server.world.clans.clanNamed("Ban Gate")!!
                alice.await("the ban in alice's settings") { alice.lastClanStrings(GameServerProt.CLANSETTINGS_FULL, Clans.CLAN_TYPE_CLAN)?.takeIf { it.hasAll("Banned") } }
                alice.openBannedPage()
                alice.resumeString("Banned")
                alice.saw("[clan.unbanned name=Banned]")
                assertNull(clan.banned(Clans.TEST_BANNED_KEY))
            }
        }
    }

    private companion object {
        const val HUB_X = 3181
        const val HUB_Z = 3474
    }
}
