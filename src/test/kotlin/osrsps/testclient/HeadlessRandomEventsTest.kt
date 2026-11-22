package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Scenario
import osrsps.game.Scenarios
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.XpLamp
import osrsps.game.content.randomevents.Genie
import osrsps.game.content.randomevents.RandomEvent
import osrsps.game.content.randomevents.RandomEvents
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Random events step 1 through the headless client: the timer (due 60 to 120 minutes on, looked at every 500 ticks),
 * `::randomevent`, the Genie (his greeting, nags, giving up after a minute, Talk-to and Dismiss by his player and by
 * someone else), the lamp's experience reward window, and an NPC that won't follow its player into a bank. The rl-client
 * script test-runner/examples/random-events-genie.json does the Genie and the lamp in the real game.
 */
class HeadlessRandomEventsTest {
    private val events = listOf("Talk-to", null, null, null, "Dismiss")
    private val genie = NpcType(Genie.npcId, "Genie", ops = events, internalName = "macro_geni")
    private val tester = NpcType(TESTER, "Tester", ops = events, internalName = "tester")
    private val lamp = ObjType(XpLamp.LAMP, "Lamp", iops = listOf("Rub", null, null, null, "Destroy"))
    private val booth = LocType(BOOTH, "Bank booth", ops = listOf(null, "Bank", null, null, null), collision = LocCollisionType(width = 1, length = 1))

    /** A test event that, unlike the Genie, stays out of banks. */
    object TesterLines : Lines("random_event_tester", "none: a test event") {
        val HI = line("hi", "greeting", "name")
        val NAG = line("nag", "nag")
        val BYE = line("bye", "leaving")
        val NOT_YOURS = line("not_yours", "not yours")
    }

    object Tester : RandomEvent("tester", TESTER) {
        override val greeting = TesterLines.HI
        override val nags = listOf(TesterLines.NAG)
        override val leaving = TesterLines.BYE
        override val notYours = TesterLines.NOT_YOURS
        override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean {
            chatNpc(ChatAnim.NEUTRAL, TesterLines.HI)
            return true
        }
    }

    private val clock = AtomicLong(START_MILLIS)

    private fun server(dir: Path, cycleMillis: Long = 100) = TestServer(dir, cycleMillis) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            npcTypes = NpcTypes.of(genie, tester), objTypes = ObjTypes.of(lamp),
            locTypes = LocTypes(mapOf(BOOTH to booth)),
            staticLocs = StaticLocs().apply { add(ZoneLoc(BOOTH, LocShape.CENTREPIECE_STRAIGHT, 0, 3230, 3218, 0)) },
            scenarios = Scenarios(listOf(Scenario.parse("bare", """{ "position": { "x": 3222, "z": 3218 } }"""))),
            randomEventClock = clock::get, cycleMillis = millis,
        ).also { it.randomEvents.register(Tester) }
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.npc(id: Int) = await("NPC $id in view") { npcs().firstOrNull { it.id == id } }

    private fun HeadlessClient.gone(id: Int) = await("NPC $id gone") { if (npcs().none { it.id == id }) Unit else null }

    private fun HeadlessClient.said(index: Int, text: String) = await("'$text' overhead") { npcSaid().firstOrNull { it.index == index && it.text == text } }

    private fun HeadlessClient.chat(text: String) {
        awaitDialogue(text) { it is DialogueView.Chat && it.text == text }
        continueDialogue()
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(Inventory.INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    @Test
    fun `the Genie comes on command, greets, gives a lamp, and the lamp gives ten times the level`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3222, 3218)
                client.command("randomevent genie")
                val npc = client.npc(Genie.npcId)
                assertEquals(Coord(3221, 3218, 0), npc.coord, "he appears west of the player")
                client.said(npc.index, "[genie.greeting name=alice]")
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[genie.lamp name=alice]")
                client.over()
                client.gone(Genie.npcId)
                val slot = client.slotOf(XpLamp.LAMP)
                client.command("setlevel agility 30")
                client.await("Agility 30") { client.stat(Skill.AGILITY.id)?.takeIf { it.level == 30 } }
                val before = client.stat(Skill.AGILITY.id)!!.xp
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, RUB_BUTTON_OP, slot, XpLamp.LAMP)
                client.await("the reward window") { if (XpLamp.INTERFACE in client.openInterfaces()) Unit else null }
                assertEquals(XpLamp.ALL_SKILLS, client.await("if2") { client.varp(XpLamp.VARP_IF2).takeIf { it != 0 } })
                assertEquals("[xp_lamp.title]", client.text(XpLamp.INTERFACE, XpLamp.TITLE))
                // Construction without a house is refused, as the client greys it out: the window stays open.
                client.resumePause(XpLamp.INTERFACE, XpLamp.UNIVERSE, XpLamp.BY_INDEX.indexOf(Skill.CONSTRUCTION))
                client.awaitTicks(2)
                assertTrue(XpLamp.INTERFACE in client.openInterfaces())
                assertEquals(0, client.stat(Skill.CONSTRUCTION.id)?.xp ?: 0)
                // Agility is index 8 in enum_681, so its button is child 7.
                client.resumePause(XpLamp.INTERFACE, XpLamp.UNIVERSE, XpLamp.BY_INDEX.indexOf(Skill.AGILITY))
                client.await("300 Agility XP") { client.stat(Skill.AGILITY.id)?.takeIf { it.xp == before + 300 } }
                client.await("the window closed") { if (XpLamp.INTERFACE !in client.openInterfaces()) Unit else null }
                client.await("the lamp used") { if (client.inventory(Inventory.INV).none { it?.id == XpLamp.LAMP }) Unit else null }
                client.await("the message") { client.messages().firstOrNull { it.text == "[xp_lamp.granted xp=300 skill=Agility]" } }
            }
        }
    }

    @Test
    fun `an ignored Genie nags, gives up after a minute, and only his player can talk to him or dismiss him`(@TempDir dir: Path) {
        server(dir, cycleMillis = 30).use { server ->
            server.login("bob").use { bob ->
                server.login("carol").use { carol ->
                    bob.await("the login rebuild") { bob.position }
                    carol.await("the login rebuild") { carol.position }
                    bob.tele(3222, 3218)
                    carol.tele(3224, 3218)
                    bob.command("randomevent genie")
                    val npc = carol.npc(Genie.npcId)
                    carol.npcOp(npc.index, RandomEvents.TALK_TO)
                    carol.chat("[genie.someone_else]")
                    carol.over()
                    carol.npcOp(npc.index, RandomEvents.DISMISS)
                    carol.chat("[genie.someone_else]")
                    carol.over()
                    val nags = setOf("[genie.hello name=bob]", "[genie.ehem name=bob]", "[genie.waiting name=bob]")
                    bob.await("a nag") { bob.npcSaid().firstOrNull { it.index == npc.index && it.text in nags } }
                    bob.said(npc.index, "[genie.maybe_later]")
                    bob.gone(Genie.npcId)
                    assertTrue(bob.inventory(Inventory.INV).none { it?.id == XpLamp.LAMP })

                    bob.command("randomevent genie")
                    val again = bob.npc(Genie.npcId)
                    bob.npcOp(again.index, RandomEvents.DISMISS)
                    bob.gone(Genie.npcId)
                }
            }
        }
    }

    @Test
    fun `the timer brings an event once due, not while off or in a setup, and not in a bank for one that stays out of banks`(@TempDir dir: Path) {
        server(dir, cycleMillis = 20).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3222, 3218)
                val player = server.world.onlinePlayers.single()
                // A new account's first time is set at its first look, or here by asking.
                assertEquals(0, player.charges[RandomEvents.DUE])
                client.commandReply("randomevent") { it.startsWith("Random events on, next due in") }
                val due = player.charges[RandomEvents.DUE]
                val now = (START_MILLIS / 60_000).toInt()
                assertTrue(due in now + RandomEvents.DUE_MIN_MINUTES..now + RandomEvents.DUE_MAX_MINUTES, "due $due, now $now")

                // Off holds it back, however due, and a scenario keeps both the timer and the switch.
                client.commandReply("randomevent off") { it == "Random events off" }
                client.command("scenario bare")
                client.await("the scenario") { player.scenario?.takeIf { it == "bare" } }
                assertTrue(server.world.randomEvents.isOff(player))
                assertEquals(due, player.charges[RandomEvents.DUE])
                clock.set(START_MILLIS + 121 * 60_000L)
                client.command("randomevent now")
                client.await("500 ticks", timeoutMillis = 30_000) { if (player.playedTicks > 520) Unit else null }
                assertTrue(client.npcs().none { it.id == Genie.npcId || it.id == TESTER })

                // On, none comes during the setup either (exploit review R50A-1), and putting it back keeps the timer
                // as it is now, not the snapshot's from before the setup.
                client.commandReply("randomevent on") { it == "Random events on" }
                val dueNow = player.charges[RandomEvents.DUE]
                assertTrue(dueNow != due)
                client.await("another 500 ticks", timeoutMillis = 30_000) { if (player.playedTicks > 1_020) Unit else null }
                assertTrue(client.npcs().none { it.id == Genie.npcId || it.id == TESTER })
                client.command("reset")
                client.await("the setup put back") { if (player.scenario == null) Unit else null }
                assertEquals(dueNow, player.charges[RandomEvents.DUE])
                assertTrue(!server.world.randomEvents.isOff(player))
                client.tele(3222, 3218)

                // Out of it, it comes at the next 500-tick look, and the next is set 60 to 120 minutes on.
                client.await("an event NPC", timeoutMillis = 30_000) { client.npcs().firstOrNull { it.id == Genie.npcId || it.id == TESTER } }
                val next = player.charges[RandomEvents.DUE] - (clock.get() / 60_000).toInt()
                assertTrue(next in RandomEvents.DUE_MIN_MINUTES..RandomEvents.DUE_MAX_MINUTES, "next in $next")

                // In a bank, only an event that enters banks can come: the tester never does, and leaves if followed in.
                client.command("randomevent off")
                client.await("events off") { if (server.world.randomEvents.isOff(player)) Unit else null }
                server.world.randomEvents.outFor(player)?.let { client.npcOp(it.npc.index, RandomEvents.DISMISS) }
                client.await("no event NPC") { if (client.npcs().none { it.id == Genie.npcId || it.id == TESTER }) Unit else null }
                client.tele(3222, 3218)
                client.command("randomevent tester")
                client.npc(TESTER)
                client.tele(3229, 3218)
                client.gone(TESTER)
                assertTrue(server.world.randomEvents.inBank(3229, 3218, 0))
                assertTrue(!server.world.randomEvents.allowedAt(player, Tester))
                assertTrue(server.world.randomEvents.allowedAt(player, Genie))
            }
        }
    }

    private companion object {
        const val TESTER = 9001
        const val BOOTH = 10355
        const val RUB_BUTTON_OP = 2
        const val START_MILLIS = 1_800_000_000_000L
    }
}
