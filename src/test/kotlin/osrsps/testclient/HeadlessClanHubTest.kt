package osrsps.testclient

import org.junit.jupiter.api.io.TempDir
import osrsps.game.EquipmentTab
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SettingsSide
import osrsps.game.VarTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.ClanHub
import osrsps.game.dialogue.Chatbox
import osrsps.game.npc.NpcSpawn
import osrsps.game.social.Clans
import osrsps.map.CollisionMap
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Clan Hub's Clerk through the headless client: his talk's menus, the clan cloak and vexillum he gives clan members
 * in their clan's colour, the clan's motif (Icon customisation from the clan settings page) that sets that colour, and
 * what he says to someone in no clan or with a full inventory. The rl-client script test-runner/examples/clan-clerk.json
 * does the same on the real map.
 */
class HeadlessClanHubTest {
    private val clerk = NpcType(ClanHub.CLERK, "Clerk", ops = listOf("Talk-to", null, "Items", null, null), internalName = "clan_clerk")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(ClanHub.VEXILLA.max() + 1)
        for (id in ClanHub.CLOAKS) objs[id] = ObjType(id, "Clan cloak $id", iops = listOf(null, "Wear", null, null, "Destroy"), wearpos1 = WearPos.CAPE)
        for (id in ClanHub.VEXILLA) objs[id] = ObjType(id, "Clan vexillum $id", iops = listOf(null, "Wield", null, null, "Destroy"), wearpos1 = WearPos.WEAPON)
        objs[FILLER] = ObjType(FILLER, "Filler")
        World(
            crcs,
            CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(clerk),
            npcSpawns = listOf(NpcSpawn(clerk, 3222, 3220, source = "test")),
            varTypes = VarTypes(VARP_COUNT, mapOf(SettingsSide.VARBIT_ACCEPT_AID to SettingsSide.VARBITS.getValue(SettingsSide.VARBIT_ACCEPT_AID))),
            cycleMillis = millis,
            clansDir = dir.resolve("data"),
        )
    }

    private fun HeadlessClient.ready() = await("the login rebuild") { position }

    private fun HeadlessClient.talk(op: Int = ClanHub.TALK_TO) {
        val npc = await("the Clerk in view") { npcs().firstOrNull { it.id == ClanHub.CLERK } }
        npcOp(npc.index, op)
    }

    private fun HeadlessClient.chats(vararg keys: String) {
        for (key in keys) {
            awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
            continueDialogue()
        }
    }

    private fun HeadlessClient.menu(vararg keys: String) {
        val menu = awaitDialogue("the menu ${keys.toList()}") { it is DialogueView.Menu && it.options == keys.map { k -> "[clan_clerk.$k]" } }
        assertEquals(keys.map { "[clan_clerk.$it]" }, (menu as DialogueView.Menu).options)
    }

    private fun HeadlessClient.awaitClosed() = await("the dialogue to close") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.boxes() = clientScripts().filter { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }

    /** Waits for items box number [box] (since login), checks it offers [cloak] and [vexillum], and picks [index]. */
    private fun HeadlessClient.pick(box: Int, cloak: Int, vexillum: Int, index: Int) {
        val setup = await("items box $box") { boxes().getOrNull(box - 1) }
        assertTrue(cloak in setup.args && vexillum in setup.args, "the box shows $cloak and $vexillum: ${setup.args}")
        resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + index, 1)
    }

    private fun HeadlessClient.inClan(name: String) {
        commandReply("clanmake $name") { it.startsWith("Clan") || it.startsWith("Can't") }
        assertEquals("Joined clan $name as a recruiter", commandReply("clanjoin $name recruiter") { it.startsWith("Joined") || it.startsWith("You're") })
    }

    @Test
    fun `the Clerk's talk follows the transcript's menus and ends on Goodbye`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.talk()
                client.chats("clan_clerk.good_day")
                client.menu("who", "what_do", "what_clan", "give", "goodbye")
                client.chooseOption(2)
                client.chats("clan_clerk.what_do", "clan_clerk.mission")
                client.menu("why_here", "who", "what_clan", "give", "goodbye")
                client.chooseOption(1)
                client.chats("clan_clerk.why_here", "clan_clerk.gather", "clan_clerk.crowded", "clan_clerk.corner")
                client.menu("what_hub", "what_clan", "give", "goodbye")
                client.chooseOption(2)
                client.chats("clan_clerk.what_clan", "clan_clerk.clans_1", "clan_clerk.clans_2", "clan_clerk.clans_3")
                client.menu("how_join", "offer", "give", "goodbye")
                client.chooseOption(2)
                client.chats("clan_clerk.offer", "clan_clerk.offer_1", "clan_clerk.offer_2", "clan_clerk.offer_3", "clan_clerk.offer_4")
                client.menu("what_coffer", "what_hall", "goodbye", "give")
                client.chooseOption(2)
                client.chats("clan_clerk.what_hall", "clan_clerk.hall", "clan_clerk.house", "clan_clerk.both")
                // Back to the first menu.
                client.menu("who", "what_do", "what_clan", "give", "goodbye")
                client.chooseOption(5)
                client.chats("clan_clerk.goodbye", "clan_clerk.come_back")
                client.awaitClosed()

                // In no clan, asking for items gets his line for that, and nothing.
                client.talk()
                client.chats("clan_clerk.good_day")
                client.menu("who", "what_do", "what_clan", "give", "goodbye")
                client.chooseOption(4)
                client.chats("clan_clerk.give", "clan_clerk.not_in_clan")
                client.awaitClosed()
                assertTrue(client.boxes().isEmpty())
            }
        }
    }

    @Test
    fun `a clan member gets a cloak and a vexillum in blue, the box opening again until the inventory is full`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.inClan("Odins Gate")
                // 26 slots filled: room for two.
                client.command("item $FILLER 26")
                client.await("the filler") { Unit.takeIf { client.count(FILLER) == 26 } }
                client.talk()
                client.chats("clan_clerk.good_day")
                client.menu("who", "what_do", "what_clan", "give", "goodbye")
                client.chooseOption(4)
                client.chats("clan_clerk.give", "clan_clerk.certainly")
                // A clan that never picked a colour has blue (index 2).
                client.pick(1, ClanHub.CLOAKS[2], ClanHub.VEXILLA[2], 0)
                client.await("the cloak") { Unit.takeIf { client.count(ClanHub.CLOAKS[2]) == 1 } }
                client.pick(2, ClanHub.CLOAKS[2], ClanHub.VEXILLA[2], 1)
                client.await("the vexillum") { Unit.takeIf { client.count(ClanHub.VEXILLA[2]) == 1 } }
                client.chats("clan_clerk.thanks")
                client.awaitClosed()
                assertEquals(2, client.boxes().size)

                // Full now: Items gets his no-space line, Talk-to the no-space talk and the first menu without the ask.
                client.talk(ClanHub.ITEMS)
                client.chats("clan_clerk.no_space")
                client.awaitClosed()
                client.talk()
                client.chats("clan_clerk.good_day")
                client.menu("who", "what_do", "what_clan", "give", "goodbye")
                client.chooseOption(4)
                client.chats("clan_clerk.give", "clan_clerk.no_space", "clan_clerk.free_space", "clan_clerk.anything_else")
                client.menu("who", "what_do", "what_clan", "goodbye")
                client.chooseOption(4)
                client.chats("clan_clerk.goodbye", "clan_clerk.come_back")
                client.awaitClosed()
                assertEquals(2, client.boxes().size)
            }
        }
    }

    @Test
    fun `the clan's motif colour, saved from Icon customisation, is the colour of what the Clerk gives`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.ready()
                alice.inClan("Odins Gate")
                // A blue cloak and vexillum from before, and a blue cloak worn: the new colour reaches them too.
                alice.commandReply("item ${ClanHub.CLOAKS[2]}") { true }
                alice.commandReply("item ${ClanHub.VEXILLA[2]}") { true }
                alice.await("the blue items") { Unit.takeIf { alice.count(ClanHub.CLOAKS[2]) == 1 && alice.count(ClanHub.VEXILLA[2]) == 1 } }
                val player = server.world.playerNamed("alice")!!
                player.equipment[osrsps.game.WearPos.CAPE] = osrsps.game.InvObj(ClanHub.CLOAKS[2], 1)
                alice.ifButton(Clans.SIDE_CHANNELS, 3, 1)
                alice.await("the clan page") { alice.openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
                alice.ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_SETTINGS, 1)
                alice.await("the settings page") { alice.openInterfaces().takeIf { Clans.INFO in it } }
                alice.await("Edit enabled on the motif") { Unit.takeIf { alice.opEnabled(Clans.INFO, Clans.INFO_OUTFIT, -1, 1) } }

                var since = alice.clientScripts().size
                alice.ifButton(Clans.INFO, Clans.INFO_OUTFIT, 1)
                val opened = alice.await("Icon customisation") { alice.clientScripts().drop(since).lastOrNull { it.id == Clans.SCRIPT_OUTFIT } }
                assertEquals(listOf<Any>(Clans.component(Clans.INFO, Clans.INFO_POPUP)), opened.args)
                assertTrue(Clans.OUTFIT in alice.openInterfaces())
                // Saved: icons 3 and 5, colour red (3), each plus one.
                val red = 4 or (6 shl 10) or (4 shl 20)
                alice.resumeCount(red)
                val clan = server.world.clans.clanNamed("Odins Gate")!!
                alice.await("the motif saved") { Unit.takeIf { clan.settings[Clans.VAR_MOTIF] == red } }
                alice.await("Icon customisation closed") { if (Clans.OUTFIT in alice.openInterfaces()) null else Unit }
                assertEquals(3, server.world.clans.colour(clan))
                alice.await("the cloak and vexillum turned red") {
                    Unit.takeIf { alice.count(ClanHub.CLOAKS[3]) == 1 && alice.count(ClanHub.VEXILLA[3]) == 1 && alice.count(ClanHub.VEXILLA[2]) == 0 }
                }
                assertEquals(ClanHub.CLOAKS[3], player.equipment[osrsps.game.WearPos.CAPE]?.id)
                // Every member's client gets it, raw, in the clan settings.
                assertTrue(server.world.clans.settingVars(clan).any { it.id == Clans.VAR_MOTIF && it.value == red })

                // Cancel (0) and a motif the client can't make change nothing.
                for (bad in listOf(0, 9 shl 20, 1 shl 30)) {
                    since = alice.clientScripts().size
                    alice.ifButton(Clans.INFO, Clans.INFO_OUTFIT, 1)
                    alice.await("Icon customisation again") { alice.clientScripts().drop(since).lastOrNull { it.id == Clans.SCRIPT_OUTFIT } }
                    alice.resumeCount(bad)
                    alice.awaitTicks(2)
                    assertEquals(red, clan.settings[Clans.VAR_MOTIF])
                }

                alice.talk(ClanHub.ITEMS)
                alice.pick(1, ClanHub.CLOAKS[3], ClanHub.VEXILLA[3], 1)
                alice.await("another red vexillum") { Unit.takeIf { alice.count(ClanHub.VEXILLA[3]) == 2 } }
                assertFalse(alice.count(ClanHub.VEXILLA[2]) > 0)
            }
        }
    }

    @Test
    fun `joining a clan turns the cloak and vexillum already held to its colour`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.ready()
                alice.commandReply("clanmake Red Gate") { it.startsWith("Clan") }
                server.world.clans.clanNamed("Red Gate")!!.settings[Clans.VAR_MOTIF] = 4 shl 20
                alice.commandReply("item ${ClanHub.CLOAKS[2]}") { true }
                alice.commandReply("item ${ClanHub.VEXILLA[2]}") { true }
                alice.await("the blue items") { Unit.takeIf { alice.count(ClanHub.CLOAKS[2]) == 1 && alice.count(ClanHub.VEXILLA[2]) == 1 } }
                alice.commandReply("clanjoin Red Gate") { it.startsWith("Joined") }
                alice.await("the cloak and vexillum turned red") {
                    Unit.takeIf { alice.count(ClanHub.CLOAKS[3]) == 1 && alice.count(ClanHub.VEXILLA[3]) == 1 && alice.count(ClanHub.CLOAKS[2]) == 0 }
                }
            }
        }
    }

    @Test
    fun `a member below the Icons permission gets no Icon customisation`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { bob ->
                bob.ready()
                bob.commandReply("clanmake Odins Gate") { it.startsWith("Clan") }
                assertEquals("Joined clan Odins Gate", bob.commandReply("clanjoin Odins Gate") { it.startsWith("Joined") })
                bob.ifButton(Clans.SIDE_CHANNELS, 3, 1)
                bob.await("the clan page") { bob.openInterfaces().takeIf { Clans.YOUR_CLAN in it } }
                bob.ifButton(Clans.YOUR_CLAN, Clans.YOUR_CLAN_SETTINGS, 1)
                bob.await("the settings page") { bob.openInterfaces().takeIf { Clans.INFO in it } }
                val since = bob.clientScripts().size
                bob.ifButton(Clans.INFO, Clans.INFO_OUTFIT, 1)
                bob.awaitTicks(2)
                assertTrue(bob.clientScripts().drop(since).none { it.id == Clans.SCRIPT_OUTFIT })
                assertFalse(Clans.OUTFIT in bob.openInterfaces())
            }
        }
    }

    private fun HeadlessClient.wield(id: Int) {
        val slot = await("item $id in the backpack") { inventory(Inventory.INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }
        ifButton(INVENTORY, 0, WEAR_BUTTON, slot, id)
        await("item $id wielded") { inventory(WORN).firstOrNull { it?.id == id } }
    }

    private fun HeadlessClient.saw(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.recruitShown() = playerOps()[Clans.PLAYER_OP_RECRUIT] == Clans.RECRUIT

    @Test
    fun `wielding a vexillum gives Recruit, and a yes from someone with Accept Aid on joins them to the clan`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.ready()
                    bob.ready()
                    alice.inClan("Odins Gate")
                    alice.awaitTicks(2)
                    assertFalse(alice.recruitShown())
                    alice.command("item ${ClanHub.VEXILLA[2]} 1")
                    alice.wield(ClanHub.VEXILLA[2])
                    alice.await("Recruit on players") { Unit.takeIf { alice.recruitShown() } }

                    // Accept Aid off: refused.
                    alice.playerOp(bob.index, Clans.PLAYER_OP_RECRUIT)
                    alice.saw("[clan.recruit_no_aid name=bob]")
                    bob.command("varbit ${SettingsSide.VARBIT_ACCEPT_AID} 1")
                    bob.awaitTicks(2)

                    // Asked, and no: he stays out, and alice hears of it.
                    alice.playerOp(bob.index, Clans.PLAYER_OP_RECRUIT)
                    alice.saw("[clan.recruit_sent name=bob]")
                    val question = bob.awaitDialogue("the question") { it is DialogueView.Menu } as DialogueView.Menu
                    assertEquals("[clan.recruit_question name=alice clan=Odins Gate]", question.title)
                    // A second Recruit while he is asked: busy.
                    alice.playerOp(bob.index, Clans.PLAYER_OP_RECRUIT)
                    alice.saw("[clan.recruit_busy name=bob]")
                    bob.chooseOption(2)
                    alice.saw("[clan.recruit_declined name=bob]")
                    assertNull(server.world.clans.clanOf(server.world.playerNamed("bob")!!))

                    // Asked again, and yes: a member of the lowest rank.
                    bob.await("the question closed") { if (bob.dialogueView == null) Unit else null }
                    alice.playerOp(bob.index, Clans.PLAYER_OP_RECRUIT)
                    bob.awaitDialogue("the question again") { it is DialogueView.Menu }
                    bob.chooseOption(1)
                    bob.saw("[clan.recruited name=Odins Gate]")
                    alice.saw("[clan.recruit_accepted name=bob]")
                    val clan = server.world.clans.clanNamed("Odins Gate")!!
                    assertEquals(0, clan.member(server.world.playerNamed("bob")!!.saveKey)?.rank)

                    // In the clan now: Recruit on him says so.
                    alice.playerOp(bob.index, Clans.PLAYER_OP_RECRUIT)
                    alice.saw("[clan.already_in_clan name=bob]")
                }
            }
        }
    }

    @Test
    fun `a member whose rank can't recruit is told so, and taking the vexillum off takes Recruit away`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.ready()
                    bob.ready()
                    alice.commandReply("clanmake Odins Gate") { it.startsWith("Clan") }
                    assertEquals("Joined clan Odins Gate", alice.commandReply("clanjoin Odins Gate") { it.startsWith("Joined") })
                    alice.command("item ${ClanHub.VEXILLA[0]} 1")
                    alice.wield(ClanHub.VEXILLA[0])
                    alice.await("Recruit on players") { Unit.takeIf { alice.recruitShown() } }
                    bob.command("varbit ${SettingsSide.VARBIT_ACCEPT_AID} 1")
                    bob.awaitTicks(2)
                    alice.playerOp(bob.index, Clans.PLAYER_OP_RECRUIT)
                    alice.saw("[clan.recruit_not_allowed]")
                    assertNull(bob.dialogueView)

                    alice.ifButton(EquipmentTab.WORNITEMS, EquipmentTab.WORNITEMS_SLOTS[EquipmentTab.SLOT_WEARPOS.indexOf(WearPos.WEAPON)], 1)
                    alice.await("Recruit gone") { if (alice.recruitShown()) null else Unit }
                }
            }
        }
    }

    @Test
    fun `Recruit on someone held in a delayed action reads as busy, and asks nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.ready()
                    bob.ready()
                    alice.inClan("Odins Gate")
                    alice.command("item ${ClanHub.VEXILLA[2]} 1")
                    alice.wield(ClanHub.VEXILLA[2])
                    alice.await("Recruit on players") { Unit.takeIf { alice.recruitShown() } }
                    bob.command("varbit ${SettingsSide.VARBIT_ACCEPT_AID} 1")
                    bob.awaitTicks(2)
                    // Codex on PR 836: a delayed action with no interface open is busy too (ScriptRunner.busy).
                    alice.commandReply("pause") { it.contains("aused after tick") }
                    val him = server.world.playerNamed("bob")!!
                    server.world.scripts.start(him) { delay(20) }
                    assertTrue(him.scripts.delayed && !him.modalOpen)
                    alice.playerOp(bob.index, Clans.PLAYER_OP_RECRUIT)
                    alice.stepTicks(2)
                    alice.saw("[clan.recruit_busy name=bob]")
                    assertNull(bob.dialogueView)
                }
            }
        }
    }

    @Test
    fun `Recruit on someone in combat reads as busy, and asks nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.ready()
                    bob.ready()
                    alice.inClan("Odins Gate")
                    alice.command("item ${ClanHub.VEXILLA[2]} 1")
                    alice.wield(ClanHub.VEXILLA[2])
                    alice.await("Recruit on players") { Unit.takeIf { alice.recruitShown() } }
                    bob.command("varbit ${SettingsSide.VARBIT_ACCEPT_AID} 1")
                    bob.awaitTicks(2)
                    // Codex on PR 836: a question open in combat would stop his auto-retaliate.
                    alice.commandReply("pause") { it.contains("aused after tick") }
                    val him = server.world.playerNamed("bob")!!
                    him.attackedTick = server.world.tick
                    assertTrue(server.world.inCombat(him) && !him.modalOpen && !him.scripts.delayed)
                    alice.playerOp(bob.index, Clans.PLAYER_OP_RECRUIT)
                    alice.stepTicks(2)
                    alice.saw("[clan.recruit_busy name=bob]")
                    assertNull(bob.dialogueView)
                }
            }
        }
    }

    private companion object {
        const val VARP_COUNT = 5_000
        const val FILLER = 1511
        const val INVENTORY = 149
        const val WORN = 94
        const val WEAR_BUTTON = 3
    }
}
