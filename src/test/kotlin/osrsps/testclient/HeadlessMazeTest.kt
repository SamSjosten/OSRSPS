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
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.content.randomevents.Maze
import osrsps.game.content.randomevents.RandomEvents
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Random events step 14 through the headless client: the Mysterious Old Man's maze form takes his player to a corner
 * of the maze, the timer window shows the reward potential, a chest gives a small reward for a point of it, and the
 * shrine at the centre sends the player home with the shrine's rolls. Busy sends him off; a potential run down to 0
 * sends the player home with nothing. The rl-client script test-runner/examples/random-events-maze.json does part of it
 * in the real game.
 */
class HeadlessMazeTest {
    private val npcs = listOf(NpcType(Maze.npcId, "Mysterious Old Man", ops = listOf("Talk-to", null, null, null, "Dismiss")))
    private val objs = (Maze.SHRINE_TABLE + Maze.CHEST_TABLE).map { it.first }.distinct().map { ObjType(it, "Item $it", stackable = true) }
    private val locs = listOf(
        LocType(Maze.SHRINE, "Strange shrine", ops = listOf("Touch", null, null, null, null)),
        LocType(Maze.CHEST, "Chest", ops = listOf("Open", null, null, null, null)),
        LocType(Maze.CHEST_EMPTY, "Chest", ops = listOf("Search", null, null, null, null)),
    )
    private val shrine = Tile(2911, 4575)

    /** A chest beside each start, so whichever corner the player lands in has one near. */
    private val chests = listOf(Tile(2890, 4599), Tile(2930, 4595), Tile(2890, 4553), Tile(2931, 4553))

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(45, 71) },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()), objTypes = ObjTypes.of(*objs.toTypedArray()),
            locTypes = LocTypes(locs.associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Maze.SHRINE, LocShape.CENTREPIECE_STRAIGHT, 0, shrine.x, shrine.z, 0))
                for (c in chests) add(ZoneLoc(Maze.CHEST, LocShape.CENTREPIECE_STRAIGHT, 0, c.x, c.z, 0))
            },
            varTypes = VarTypes(2_000, emptyMap()),
            cycleMillis = millis,
        )
    }

    private val home = Coord(3222, 3218, 0)

    private fun HeadlessClient.ready() {
        await("the login rebuild") { position }
        command("tele 3222 3218 0")
        await("the teleport") { position?.takeIf { it == home } }
        commandReply("randomevent off") { it == "Random events off" }
    }

    private fun HeadlessClient.chat(text: String) {
        awaitDialogue(text) { it is DialogueView.Chat && it.text == text }
        continueDialogue()
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    /** Talk-to and yes: into the maze, at one of its corners. */
    private fun HeadlessClient.intoMaze(name: String) {
        command("randomevent ${Maze.key}")
        val npc = await("the Old Man") { npcs().firstOrNull { it.id == Maze.npcId } }
        npcOp(npc.index, RandomEvents.TALK_TO)
        chat("[maze.invite name=$name]")
        awaitDialogue("the options") { it is DialogueView.Menu }
        chooseOption(1)
        chat("[maze.yes]")
        chat("[maze.reach_centre]")
        over()
        await("the maze") { position?.takeIf { it != home } }
    }

    @Test
    fun `the shrine's amount follows the Wiki's examples`() {
        assertEquals(4430, Maze.shrineAmount(1772, 75, 1))
        // The page's example says 491 chaos runes, but its formula, and its own maximum of 632.5 at 2277 total, give 492.
        assertEquals(492, Maze.shrineAmount(1772, 75, 9))
        assertEquals(27, Maze.shrineAmount(1772, 75, 162))
    }

    @Test
    fun `the timer counts down, a chest costs a point, and the shrine sends home with its rolls`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.intoMaze("alice")
                assertTrue(Maze.TIMER_INTERFACE in client.openInterfaces())
                val run = Maze.runOf(server.world, server.world.onlinePlayers.single())!!.second
                client.await("the potential shown") { client.varp(Maze.POTENTIAL_VARP).takeIf { it in 1..Maze.FULL } }
                client.await("a point gone") { if (run.potential < Maze.FULL) Unit else null }

                val (away, _) = Maze.runOf(server.world, server.world.onlinePlayers.single())!!
                val at = client.position!!
                val chest = chests.minBy { c -> away.inside(c.x, c.z).let { Math.abs(it.x - at.x) + Math.abs(it.z - at.z) } }
                val inside = away.inside(chest.x, chest.z)
                val before = run.potential
                client.locOp(Maze.CHEST, inside.x, inside.z, Maze.OPEN)
                client.await("the chest open") { if (server.world.locs.at(inside.x, inside.z, 0).any { it.id == Maze.CHEST_EMPTY }) Unit else null }
                assertTrue(run.potential <= before - Maze.CHEST_COST, "a point for the chest")
                val fromChest = client.await("the chest's reward") { client.inventory(Inventory.INV).filterNotNull().singleOrNull() }
                assertTrue(Maze.CHEST_TABLE.any { (id, count) -> id == fromChest.id && count == fromChest.count }, "chest reward $fromChest")

                val shrineAt = away.inside(shrine.x, shrine.z)
                client.locOp(Maze.SHRINE, shrineAt.x, shrineAt.z, Maze.TOUCH)
                client.await("home") { client.position?.takeIf { it == home } }
                client.await("the timer closed") { if (Maze.TIMER_INTERFACE !in client.openInterfaces()) Unit else null }
                client.awaitTicks(1)
                val shrineIds = Maze.SHRINE_TABLE.map { it.first }.toSet()
                val got = client.inventory(Inventory.INV).filterNotNull()
                assertTrue(got.all { it.id in shrineIds || it.id == fromChest.id }, "rewards $got")
                assertTrue(run.done)
                client.await("the maze ended") { if (server.world.instances.all.isEmpty()) Unit else null }
            }
        }
    }

    @Test
    fun `busy sends him off, a teleport out closes the timer, and a potential run out sends home with nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("randomevent ${Maze.key}")
                val npc = client.await("the Old Man") { client.npcs().firstOrNull { it.id == Maze.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[maze.invite name=bob]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.chat("[maze.busy]")
                client.over()
                client.await("him gone") { if (client.npcs().none { it.id == Maze.npcId }) Unit else null }

                // Teleported out another way: the timer closes within one drain.
                client.awaitTicks(2)
                client.intoMaze("bob")
                client.command("tele 3222 3218 0")
                client.await("home") { client.position?.takeIf { it == home } }
                client.await("the timer closed") { if (Maze.TIMER_INTERFACE !in client.openInterfaces()) Unit else null }
                client.await("the maze ended") { if (server.world.instances.all.isEmpty()) Unit else null }

                client.awaitTicks(2)
                client.intoMaze("bob")
                val run = Maze.runOf(server.world, server.world.onlinePlayers.single())!!.second
                run.potential = 2
                client.await("home") { client.position?.takeIf { it == home } }
                assertEquals(0, client.varp(Maze.POTENTIAL_VARP))
                assertTrue(client.inventory(Inventory.INV).all { it == null })
                client.await("the maze ended") { if (server.world.instances.all.isEmpty()) Unit else null }
            }
        }
    }
}
