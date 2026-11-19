package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.randomevents.EvilTwin
import osrsps.game.content.randomevents.RandomEvents
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.Npc
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Random events step 12 through the headless client: Postie Pete brings the player to Molly, who explains; the door
 * lets them through once they know what to do, and the control panel's arrows move the claw over the pen. The twin
 * caught brings 2 to 4 noted uncut gems; two civilians caught bring a teleport somewhere else with nothing. The
 * rl-client script test-runner/examples/random-events-evil-twin.json does part of it in the real game.
 */
class HeadlessEvilTwinTest {
    private val npcs = listOf(NpcType(EvilTwin.npcId, "Postie Pete", ops = listOf("Talk-to", null, null, null, "Dismiss"))) +
        EvilTwin.MOLLIES.map { NpcType(it, "Molly", ops = listOf("Talk-to", null, null, null, null)) } +
        EvilTwin.ALL_SUSPECTS.map { NpcType(it, "Suspect", ops = listOf("Grab", null, null, null, null)) }

    private val objs = EvilTwin.GEMS.map { ObjType(it, "Gem $it", stackable = true) }

    private val locs = listOf(
        LocType(EvilTwin.PANEL, "Control panel", ops = listOf("Use", null, null, null, null)),
        LocType(EvilTwin.DOOR, "Door", ops = listOf("Open", null, null, null, null)),
        LocType(EvilTwin.CLAW, "Evil claw"),
        LocType(EvilTwin.MARK, "null"),
    )

    private val door = Tile(EvilTwin.DOOR_X, 5137)
    private val panel = Tile(1870, 5135)

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(29, 80)
                for ((x, z) in EvilTwin.FAIL_SPOTS) markLoaded(x shr 6, z shr 6)
            },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()), objTypes = ObjTypes.of(*objs.toTypedArray()),
            locTypes = LocTypes(locs.associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(EvilTwin.PANEL, LocShape.CENTREPIECE_STRAIGHT, 0, panel.x, panel.z, 0))
                add(ZoneLoc(EvilTwin.DOOR, LocShape.WALL_STRAIGHT, 2, door.x, door.z, 0))
                add(ZoneLoc(EvilTwin.CLAW, LocShape.CENTREPIECE_STRAIGHT, 0, EvilTwin.CLAW_START.x, EvilTwin.CLAW_START.z, 0))
                add(ZoneLoc(EvilTwin.MARK, LocShape.GROUND_DECOR, 0, EvilTwin.CLAW_START.x, EvilTwin.CLAW_START.z, 0))
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

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.menu(option: Int) {
        awaitDialogue("the options") { it is DialogueView.Menu }
        chooseOption(option)
    }

    /** Talk-to on Postie Pete and yes: into Molly's room. Returns its offset. */
    private fun HeadlessClient.intoRooms(name: String): Pair<Int, Int> {
        command("randomevent ${EvilTwin.key}")
        val npc = await("Postie Pete") { npcs().firstOrNull { it.id == EvilTwin.npcId } }
        npcOp(npc.index, RandomEvents.TALK_TO)
        chat("[evil_twin_postie.needs_help name=$name]")
        menu(1)
        chat("[evil_twin_postie.sure]")
        over()
        await("Molly's room") { position?.takeIf { it != Coord(3222, 3218, 0) } }
        val at = position!!
        return at.x - EvilTwin.ARRIVE.x to at.z - EvilTwin.ARRIVE.z
    }

    private fun HeadlessClient.openDoor(dx: Int, dz: Int) = locOp(EvilTwin.DOOR, door.x + dx, door.z + dz, EvilTwin.OPEN)

    private fun HeadlessClient.awaitSide(panelSide: Boolean, dx: Int) =
        await(if (panelSide) "the control room" else "Molly's room") {
            position?.takeIf { if (panelSide) it.x - dx > EvilTwin.DOOR_X else it.x - dx <= EvilTwin.DOOR_X }
        }

    private fun HeadlessClient.usePanel(dx: Int, dz: Int) {
        locOp(EvilTwin.PANEL, panel.x + dx, panel.z + dz, EvilTwin.USE)
        await("the controls") { if (EvilTwin.INTERFACE in openInterfaces()) Unit else null }
    }

    /** The claw moved onto [suspect] (once it stands within reach) and Grab, until she is caught. */
    private fun HeadlessClient.catch(world: World, rooms: EvilTwin.Rooms, suspect: Npc, dx: Int, dz: Int) {
        val before = rooms.caught
        while (rooms.caught == before) {
            val at = Tile(suspect.x - dx, suspect.z - dz)
            if (at.x !in EvilTwin.CLAW_X || at.z !in EvilTwin.CLAW_Z || (at.x in EvilTwin.CAGE_X && at.z in EvilTwin.CAGE_Z)) {
                awaitTicks(1)
                continue
            }
            while (rooms.claw != at) {
                val button = when {
                    rooms.claw.x < at.x -> EvilTwin.MOVE_RIGHT
                    rooms.claw.x > at.x -> EvilTwin.MOVE_LEFT
                    rooms.claw.z < at.z -> EvilTwin.MOVE_UP
                    else -> EvilTwin.MOVE_DOWN
                }
                val from = rooms.claw
                ifButton(EvilTwin.INTERFACE, button, 1)
                await("the claw moved") { if (rooms.claw != from) Unit else null }
            }
            if (Tile(suspect.x - dx, suspect.z - dz) != at) continue
            ifButton(EvilTwin.INTERFACE, EvilTwin.GRAB, 1)
            awaitTicks(1)
        }
        assertTrue(world.npcs.all.contains(suspect))
    }

    private fun HeadlessClient.brief() {
        chat("[molly.what_do_i_do]")
        chat("[molly.catch_my_sister]")
    }

    @Test
    fun `Molly explains, the door lets the player through, and the twin caught after a civilian brings gems`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                val (dx, dz) = client.intoRooms("alice")
                val rooms = EvilTwin.roomsOf(server.world, server.world.onlinePlayers.single())!!.second

                client.openDoor(dx, dz)
                client.chat("[molly.wait]")
                client.menu(2)
                client.chat("[molly.no_i_dont]")
                client.chat("[molly.come_over]")
                client.over()

                val molly = client.await("Molly") { client.npcs().firstOrNull { it.id in EvilTwin.MOLLIES } }
                assertEquals(EvilTwin.MOLLY[rooms.build][rooms.colour.ordinal], molly.id)
                client.npcOp(molly.index, RandomEvents.TALK_TO)
                for (line in listOf("thanks_for_coming", "whats_the_problem", "evil_sister", "to_do_with_me", "glad_you_asked", "through_that_door", "lured")) {
                    client.chat("[molly.$line]")
                }
                client.brief()
                for (line in listOf("no_more_bother", "easy_enough", "take_a_look", "two_attempts", "do_my_best", "controls")) client.chat("[molly.$line]")
                client.menu(1)
                for (line in listOf("yes_please", "glowing_mark", "click_direction", "lever_points", "hit_the_button", "be_careful", "my_sister", "does_that_help", "covers_everything", "good_luck")) {
                    client.chat("[molly.$line]")
                }
                client.over()

                client.openDoor(dx, dz)
                client.awaitSide(panelSide = true, dx)
                client.usePanel(dx, dz)
                // The claw's buttons sent with the controls shut are ignored.
                client.closeModal()
                client.await("the controls shut") { if (EvilTwin.INTERFACE !in client.openInterfaces()) Unit else null }
                val clawAt = rooms.claw
                for (button in listOf(EvilTwin.MOVE_RIGHT, EvilTwin.MOVE_LEFT, EvilTwin.MOVE_UP, EvilTwin.MOVE_DOWN, EvilTwin.GRAB)) client.ifButton(EvilTwin.INTERFACE, button, 1)
                client.awaitTicks(2)
                assertEquals(clawAt, rooms.claw)
                assertEquals(0, rooms.caught)
                client.usePanel(dx, dz)
                val civilian = rooms.suspects.first { it.spawn.type.id != rooms.twinId }
                client.catch(server.world, rooms, civilian, dx, dz)
                assertEquals(1, rooms.caught)
                val twin = rooms.suspects.first { it.spawn.type.id == rooms.twinId }
                client.catch(server.world, rooms, twin, dx, dz)
                client.chat("[molly.well_done]")
                client.chat("[molly.come_next_door]")
                client.over()
                assertTrue(rooms.gotTwin)

                client.openDoor(dx, dz)
                client.awaitSide(panelSide = false, dx)
                client.npcOp(molly.index, RandomEvents.TALK_TO)
                // Home, with the gems, before the talk: shutting it loses nothing.
                client.awaitDialogue("the player's line") { it is DialogueView.Chat && it.text == "[molly.in_the_cage]" }
                client.await("home") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                val gem = client.inventory(Inventory.INV).filterNotNull().single()
                assertTrue(gem.id in EvilTwin.GEMS && gem.count in EvilTwin.GEMS_MIN..EvilTwin.GEMS_MAX, "gems $gem")
                client.closeModal()
                client.over()
                client.await("the rooms ended") { if (server.world.instances.all.isEmpty()) Unit else null }
            }
        }
    }

    @Test
    fun `get stuffed sends him off, grabbing by hand is refused, and two civilians bring a teleport elsewhere`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("randomevent ${EvilTwin.key}")
                val npc = client.await("Postie Pete") { client.npcs().firstOrNull { it.id == EvilTwin.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[evil_twin_postie.needs_help name=bob]")
                client.menu(2)
                client.chat("[evil_twin_postie.stuffed]")
                client.over()
                client.await("him gone") { if (client.npcs().none { it.id == EvilTwin.npcId }) Unit else null }

                client.awaitTicks(2)
                val (dx, dz) = client.intoRooms("bob")
                val rooms = EvilTwin.roomsOf(server.world, server.world.onlinePlayers.single())!!.second
                client.openDoor(dx, dz)
                client.chat("[molly.wait]")
                client.menu(1)
                client.chat("[molly.yes_i_know]")
                client.chat("[molly.good_luck_then]")
                client.awaitSide(panelSide = true, dx)
                client.over()

                val suspect = client.await("a suspect") { client.npcs().firstOrNull { it.id in EvilTwin.ALL_SUSPECTS } }
                client.npcOp(suspect.index, EvilTwin.GRAB_OP)
                client.chat("[molly.use_the_panel]")
                client.over()
                // Nothing fences the pen in this test's map, so the walk to her took the player into it: back out first.
                client.walk(EvilTwin.PANEL_SIDE.x + dx, EvilTwin.PANEL_SIDE.z + dz)
                client.await("out of the pen") { client.position?.takeIf { it == Coord(EvilTwin.PANEL_SIDE.x + dx, EvilTwin.PANEL_SIDE.z + dz, 0) } }

                client.openDoor(dx, dz)
                client.chat("[molly.finish_first]")
                client.menu(1)
                client.chat("[molly.okay]")
                client.over()

                client.usePanel(dx, dz)
                val civilians = rooms.suspects.filter { it.spawn.type.id != rooms.twinId }
                client.catch(server.world, rooms, civilians[0], dx, dz)
                client.catch(server.world, rooms, civilians[1], dx, dz)
                client.chat("[molly.tried_your_best]")
                client.over()
                assertTrue(!rooms.gotTwin && rooms.over)

                client.openDoor(dx, dz)
                client.awaitSide(panelSide = false, dx)
                val molly = client.await("Molly") { client.npcs().firstOrNull { it.id in EvilTwin.MOLLIES } }
                client.npcOp(molly.index, RandomEvents.TALK_TO)
                client.chat("[molly.sorry]")
                client.chat("[molly.incompetence]")
                client.chat("[molly.out_of_my_sight]")
                val spots = EvilTwin.FAIL_SPOTS.map { (x, z, level) -> Coord(x, z, level) }
                client.await("somewhere else") { client.position?.takeIf { it in spots } }
                assertTrue(client.inventory(Inventory.INV).all { it == null })
                client.await("the rooms ended") { if (server.world.instances.all.isEmpty()) Unit else null }
            }
        }
    }
}
