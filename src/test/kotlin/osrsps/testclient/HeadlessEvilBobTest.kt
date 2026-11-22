package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.World.Companion.LOGOUT_BUTTON
import osrsps.game.World.Companion.LOGOUT_TAB
import osrsps.game.content.randomevents.EvilBob
import osrsps.game.content.randomevents.RandomEvents
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Random events step 10 through the headless client: Evil Bob takes his player to his island, where his servant shows
 * the right set of fishing spots. A fishlike thing netted there, uncooked at the pot and fed to him puts him to sleep;
 * a wrong one makes him want one more. The portal then takes the player home with 650 XP; before he sleeps, with
 * nothing. Declining leaves him. The rl-client script test-runner/examples/random-events-evil-bob.json
 * does part of it in the real game.
 */
class HeadlessEvilBobTest {
    private val npcs = listOf(
        NpcType(EvilBob.npcId, "Evil Bob", ops = listOf("Talk-to", null, null, null, "Dismiss")),
        NpcType(EvilBob.ASLEEP, "Evil Bob", ops = listOf("Talk-to", null, null, null, null)),
        NpcType(EvilBob.SERVANT, "Servant", ops = listOf("Talk-to", null, null, null, null)),
        NpcType(osrsps.game.combat.Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null)),
    )

    private val objs = listOf(
        ObjType(EvilBob.RAW_RIGHT, "Raw fishlike thing"), ObjType(EvilBob.RAW_WRONG, "Raw fishlike thing"),
        ObjType(EvilBob.COOKED_RIGHT, "Fishlike thing", iops = listOf("Eat", null, null, null, "Drop")),
        ObjType(EvilBob.COOKED_WRONG, "Fishlike thing", iops = listOf("Eat", null, null, null, "Drop")),
        ObjType(EvilBob.NET, "Small fishing net"),
        // Worth more than the net and fish, for the death test.
        ObjType(1277, "Bronze sword", cost = 26), ObjType(379, "Lobster", cost = 150), ObjType(995, "Coins", cost = 1, stackable = true),
        ObjType(526, "Bones", cost = 1),
    )

    private val locs = listOf(
        LocType(EvilBob.UNCOOKING_POT, "Uncooking pot"),
        LocType(EvilBob.FISHING_SPOT, "Fishing spot", ops = listOf("Net", null, null, null, null)),
        LocType(EvilBob.PORTAL, "Portal", ops = listOf("Enter", null, null, null, null)),
    )

    /** One spot of each set, on the real map. */
    private val spotOf = mapOf(
        EvilBob.Spots.SOUTH to Tile(2524, 4764), EvilBob.Spots.EAST to Tile(2543, 4776),
        EvilBob.Spots.NORTH to Tile(2526, 4791), EvilBob.Spots.WEST to Tile(2510, 4774),
    )
    private val pot = Tile(2524, 4779)
    private val portal = Tile(2523, 4777)

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(39, 74) },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()), objTypes = ObjTypes.of(*objs.toTypedArray()),
            locTypes = LocTypes(locs.associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                for (tile in spotOf.values) add(ZoneLoc(EvilBob.FISHING_SPOT, LocShape.CENTREPIECE_STRAIGHT, 0, tile.x, tile.z, 0))
                add(ZoneLoc(EvilBob.UNCOOKING_POT, LocShape.CENTREPIECE_STRAIGHT, 0, pot.x, pot.z, 0))
                add(ZoneLoc(EvilBob.PORTAL, LocShape.CENTREPIECE_STRAIGHT, 0, portal.x, portal.z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready() {
        await("the login rebuild") { position }
        command("tele 3222 3218 0")
        await("the teleport") { position?.takeIf { it == Coord(3222, 3218, 0) } }
        commandReply("randomevent off") { it == "Random events off" }
    }

    private fun HeadlessClient.chat(text: String) {
        awaitDialogue(text) { it is DialogueView.Chat && it.text == text }
        continueDialogue()
    }

    private fun HeadlessClient.box(text: String) {
        awaitDialogue(text) { it is DialogueView.Message && it.text == text }
        continueDialogue()
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.slotOf(id: Int) = inventory(Inventory.INV).indexOfFirst { it?.id == id }

    private fun HeadlessClient.has(id: Int) = await("item $id") { if (slotOf(id) >= 0) Unit else null }

    /** Talk-to on him out in the world and yes: onto the island. Returns its offset. */
    private fun HeadlessClient.ontoIsland(name: String): Pair<Int, Int> {
        command("randomevent ${EvilBob.key}")
        val npc = await("Evil Bob") { npcs().firstOrNull { it.id == EvilBob.npcId } }
        npcOp(npc.index, RandomEvents.TALK_TO)
        chat("[evil_bob.come_fishing name=$name]")
        val menu = awaitDialogue("the options") { it is DialogueView.Menu } as DialogueView.Menu
        assertEquals("[evil_bob.follow]", menu.title)
        chooseOption(1)
        chat("[evil_bob.huh]")
        chat("[evil_bob.where_am_i]")
        chat("[evil_bob.my_island]")
        chat("[evil_bob.your_leader]")
        chat("[evil_bob.i_am_leader]")
        chat("[evil_bob.not_a_slave]")
        chat("[evil_bob.ahaha]")
        over()
        val at = position!!
        return at.x - EvilBob.ARRIVE.x to at.z - EvilBob.ARRIVE.z
    }

    private fun HeadlessClient.bob() = await("Evil Bob on his island") { npcs().firstOrNull { it.id == EvilBob.npcId } }

    /** A net from the beach. */
    private fun HeadlessClient.takeNet(dx: Int, dz: Int) {
        val tile = EvilBob.NETS_AT.first()
        objOp(EvilBob.NET, tile.x + dx, tile.z + dz, TAKE)
        has(EvilBob.NET)
    }

    /** Net on [spots]' spot, then the catch box. */
    private fun HeadlessClient.fish(spots: EvilBob.Spots, cooked: Int, dx: Int, dz: Int) {
        val tile = spotOf.getValue(spots)
        locOp(EvilBob.FISHING_SPOT, tile.x + dx, tile.z + dz, EvilBob.NET_OP)
        awaitDialogue("the catch") { it is DialogueView.Other && it.interfaceId == Chatbox.OBJECTBOX }
        has(cooked)
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
        over()
    }

    private fun HeadlessClient.uncook(cooked: Int, raw: Int, dx: Int, dz: Int) {
        locTarget(EvilBob.UNCOOKING_POT, pot.x + dx, pot.z + dz, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(cooked), cooked)
        has(raw)
    }

    /** [obj] used on him, from beside him: a far spot (the east one) is out of his view. */
    private fun HeadlessClient.feed(obj: Int, dx: Int, dz: Int) {
        val beside = Coord(EvilBob.BOB_AT.x + 2 + dx, EvilBob.BOB_AT.z + dz, 0)
        walk(beside.x, beside.z)
        await("beside him") { position?.takeIf { it == beside } }
        npcTarget(bob().index, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(obj), obj)
    }

    @Test
    fun `the servant shows the spot, a wrong fish wants one more, two right put him to sleep, and the portal gives the XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                val (dx, dz) = client.ontoIsland("alice")
                val player = server.world.onlinePlayers.single()
                val island = EvilBob.islandOf(server.world, player)!!.second
                val wrong = EvilBob.Spots.entries.first { it != island.right }

                val servant = client.await("the servant") { client.npcs().firstOrNull { it.id == EvilBob.SERVANT } }
                client.npcOp(servant.index, RandomEvents.TALK_TO)
                client.chat("[evil_bob_servant.kidnapped]")
                client.chat("[evil_bob_servant.cant_help]")
                client.chat("[evil_bob_servant.listen]")
                client.chat("[evil_bob_servant.fish_he_likes]")
                client.chat("[evil_bob_servant.over_there]")
                client.await("the cutscene") { if (EvilBob.CUTSCENE_INTERFACE in client.openInterfaces()) Unit else null }
                client.await("the cutscene over") { if (EvilBob.CUTSCENE_INTERFACE !in client.openInterfaces()) Unit else null }
                client.npcOp(servant.index, RandomEvents.TALK_TO)
                client.chat("[evil_bob_servant.fish_he_likes]")
                client.over()

                client.takeNet(dx, dz)
                client.fish(wrong, EvilBob.COOKED_WRONG, dx, dz)
                client.feed(EvilBob.COOKED_WRONG, dx, dz)
                client.chat("[evil_bob.cooked]")
                client.chat("[evil_bob.uncook_it]")
                client.chat("[evil_bob.cold_fire]")
                client.over()
                client.uncook(EvilBob.COOKED_WRONG, EvilBob.RAW_WRONG, dx, dz)
                client.feed(EvilBob.RAW_WRONG, dx, dz)
                client.chat("[evil_bob.disgusting]")
                client.chat("[evil_bob.what_kind]")
                client.box("[evil_bob.more_attentive]")
                client.over()
                assertEquals(2, island.wanted)

                client.fish(island.right, EvilBob.COOKED_RIGHT, dx, dz)
                client.uncook(EvilBob.COOKED_RIGHT, EvilBob.RAW_RIGHT, dx, dz)
                client.feed(EvilBob.RAW_RIGHT, dx, dz)
                client.chat("[evil_bob.thats_delicious]")
                client.chat("[evil_bob.another_one]")
                client.box("[evil_bob.less_attentive]")
                client.over()

                client.fish(island.right, EvilBob.COOKED_RIGHT, dx, dz)
                client.uncook(EvilBob.COOKED_RIGHT, EvilBob.RAW_RIGHT, dx, dz)
                val bob = client.bob()
                client.feed(EvilBob.RAW_RIGHT, dx, dz)
                // Shutting his first line still leaves him asleep: the hand-in counts before he speaks.
                client.awaitDialogue("his line") { it is DialogueView.Chat && it.text == "[evil_bob.delicious]" }
                client.closeModal()
                client.over()
                client.await("his snore") { client.npcSaid().firstOrNull { it.index == bob.index && it.text == "[evil_bob.zzz]" } }
                assertTrue(island.asleep)
                assertEquals(EvilBob.ASLEEP, island.bob!!.type.id)
                client.npcOp(bob.index, RandomEvents.TALK_TO)
                client.box("[evil_bob.sleeping]")
                client.over()

                client.npcOp(servant.index, RandomEvents.TALK_TO)
                client.chat("[evil_bob_servant.fallen_asleep]")
                client.chat("[evil_bob_servant.come_where]")
                client.chat("[evil_bob_servant.away]")
                client.chat("[evil_bob_servant.dont_belong name=alice]")
                client.awaitDialogue("her options") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("[evil_bob_servant.love_you]")
                client.chat("[evil_bob_servant.never_be name=alice]")
                client.chat("[evil_bob_servant.go_now]")
                client.over()

                client.locOp(EvilBob.PORTAL, portal.x + dx, portal.z + dz, EvilBob.ENTER)
                client.await("home") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.await("the message") { client.messages().firstOrNull { it.text == "[evil_bob.better_at skill=Fishing]" } }
                assertEquals(650, player.skills.xp(Skill.FISHING))
                assertTrue(client.inventory(Inventory.INV).all { it == null }, "the net goes")
                client.await("the island ended") { if (server.world.instances.all.isEmpty()) Unit else null }
            }
        }
    }

    @Test
    fun `no leaves him alone, and the portal before he sleeps takes the player home with nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("randomevent ${EvilBob.key}")
                val npc = client.await("Evil Bob") { client.npcs().firstOrNull { it.id == EvilBob.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[evil_bob.come_fishing name=bob]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.box("[evil_bob.leave_alone]")
                client.over()
                client.await("him gone") { if (client.npcs().none { it.id == EvilBob.npcId }) Unit else null }

                client.awaitTicks(2)
                val (dx, dz) = client.ontoIsland("bob")
                client.npcOp(client.bob().index, RandomEvents.TALK_TO)
                client.chat("[evil_bob.let_me_out]")
                client.chat("[evil_bob.never_let_go name=bob]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.chat("[evil_bob.why_not]")
                client.chat("[evil_bob.uhm]")
                client.chat("[evil_bob.say_so]")
                client.chat("[evil_bob.hungry]")
                client.chat("[evil_bob.what_fish]")
                client.chat("[evil_bob.hurry]")
                client.over()

                client.takeNet(dx, dz)
                client.feed(EvilBob.NET, dx, dz)
                client.chat("[evil_bob.anything_else]")
                client.over()
                val right = EvilBob.islandOf(server.world, server.world.onlinePlayers.single())!!.second.right
                val cooked = if (right == EvilBob.Spots.NORTH) EvilBob.COOKED_RIGHT else EvilBob.COOKED_WRONG
                client.fish(EvilBob.Spots.NORTH, cooked, dx, dz)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 2, client.slotOf(cooked), cooked)
                client.box("[evil_bob.not_eating]")
                client.over()

                client.locOp(EvilBob.PORTAL, portal.x + dx, portal.z + dz, EvilBob.ENTER)
                client.await("home") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.awaitTicks(2)
                assertTrue(client.messages().none { it.text.startsWith("[evil_bob.better_at") })
                assertEquals(0, server.world.onlinePlayers.single().skills.xp(Skill.FISHING))
                assertTrue(client.inventory(Inventory.INV).all { it == null }, "the net and fish go")
                client.await("the island ended") { if (server.world.instances.all.isEmpty()) Unit else null }
            }
        }
    }

    @Test
    fun `teleporting off the island leaves the net and fish behind`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready()
                val (dx, dz) = client.ontoIsland("dave")
                client.takeNet(dx, dz)
                val right = EvilBob.islandOf(server.world, server.world.onlinePlayers.single())!!.second.right
                val cooked = if (right == EvilBob.Spots.NORTH) EvilBob.COOKED_RIGHT else EvilBob.COOKED_WRONG
                client.fish(EvilBob.Spots.NORTH, cooked, dx, dz)
                // Any way off counts, a spell or tablet as much as the portal: the teleport command stands in for one.
                client.command("tele 3200 3200 0")
                client.await("off the island") { client.position?.takeIf { it == Coord(3200, 3200, 0) } }
                client.awaitTicks(2)
                assertTrue(client.inventory(Inventory.INV).all { it == null }, "the net and fish stay on the island")
            }
        }
    }

    @Test
    fun `dying on the island leaves the net and fish behind, out of the grave too`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.ready()
                val (dx, dz) = client.ontoIsland("erin")
                client.takeNet(dx, dz)
                val right = EvilBob.islandOf(server.world, server.world.onlinePlayers.single())!!.second.right
                val cooked = if (right == EvilBob.Spots.NORTH) EvilBob.COOKED_RIGHT else EvilBob.COOKED_WRONG
                client.fish(EvilBob.Spots.NORTH, cooked, dx, dz)
                // Three things worth more to keep, so the net and fish would be lost with the bones, into the grave.
                for (item in listOf("1277", "379", "995 100", "526")) client.commandReply("item $item") { true }.let { assertTrue(it.startsWith("Added"), "item $item given: $it") }
                client.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                client.await("the respawn", timeoutMillis = 10_000) { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.awaitTicks(2)
                assertTrue(client.inventory(Inventory.INV).none { it?.id in EvilBob.FISH || it?.id == EvilBob.NET }, "the net and fish are not kept")
                val grave = server.world.deaths.graveOf(server.world.onlinePlayers.single())
                assertNotNull(grave, "the bones, not kept, make a grave")
                assertTrue((0 until grave.items.capacity).none { grave.items[it]?.id.let { id -> id in EvilBob.FISH || id == EvilBob.NET } }, "nor put in the grave")
            }
        }
    }

    @Test
    fun `dying in the cutscene puts the camera back and shuts its window`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("frank").use { client ->
                client.ready()
                client.ontoIsland("frank")
                val servant = client.await("the servant") { client.npcs().firstOrNull { it.id == EvilBob.SERVANT } }
                client.npcOp(servant.index, RandomEvents.TALK_TO)
                client.chat("[evil_bob_servant.kidnapped]")
                client.chat("[evil_bob_servant.cant_help]")
                client.chat("[evil_bob_servant.listen]")
                client.chat("[evil_bob_servant.fish_he_likes]")
                client.chat("[evil_bob_servant.over_there]")
                client.await("the cutscene") { if (EvilBob.CUTSCENE_INTERFACE in client.openInterfaces()) Unit else null }
                val resetsBefore = client.packets().count { it.prot.name == "CAM_RESET" }
                client.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                client.await("the respawn", timeoutMillis = 10_000) { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.await("the camera back") { client.packets().count { it.prot.name == "CAM_RESET" }.takeIf { it > resetsBefore } }
                client.awaitTicks(EvilBob.CUTSCENE_TICKS + 2)
                assertTrue(EvilBob.CUTSCENE_INTERFACE !in client.openInterfaces(), "the cutscene window shut")
            }
        }
    }

    @Test
    fun `logging out on the island leaves the net and fish behind`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                val (dx, dz) = client.ontoIsland("carol")
                client.takeNet(dx, dz)
                val right = EvilBob.islandOf(server.world, server.world.onlinePlayers.single())!!.second.right
                val cooked = if (right == EvilBob.Spots.NORTH) EvilBob.COOKED_RIGHT else EvilBob.COOKED_WRONG
                client.fish(EvilBob.Spots.NORTH, cooked, dx, dz)
                client.ifButton(LOGOUT_TAB, LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.awaitNobodyOn()
            server.login("carol").use { client ->
                client.await("back where they came from") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.awaitTicks(2)
                assertTrue(client.inventory(Inventory.INV).all { it == null }, "the net and fish stay on the island")
            }
        }
    }

    private fun TestServer.awaitNobodyOn() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (world.playerCount != 0) {
            check(System.nanoTime() < deadline) { "Timed out waiting for the world to let the last player go" }
            Thread.sleep(10)
        }
    }

    private companion object {
        const val TAKE = 3
    }
}
