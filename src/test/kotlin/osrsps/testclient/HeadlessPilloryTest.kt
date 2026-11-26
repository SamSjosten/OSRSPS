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
import osrsps.game.content.HomeTeleport
import osrsps.game.content.randomevents.Pillory
import osrsps.game.content.randomevents.RandomEvents
import osrsps.game.combat.Spells
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Random events step 15 through the headless client: the Pillory Guard locks his player in a cage, where only the lock
 * window lets them out. A wrong key adds a lock and starts the count again; all undone send the player back with one
 * of the table's rewards. Others can't unlock the cage; the prisoner's Unlock opens the window again; logging out
 * doesn't get them out. The rl-client script test-runner/examples/random-events-pillory.json does part of it in the
 * real game.
 */
class HeadlessPilloryTest {
    private val npcs = listOf(NpcType(Pillory.npcId, "Pillory Guard", ops = listOf("Talk-to", null, null, null, "Dismiss")))
    private val objs = Pillory.REWARDS.map { it.first }.distinct().map { ObjType(it, "Item $it", stackable = it == 995) }

    /** Each cage's railing: south of the Varrock cells, north of the others. */
    private fun railing(cell: Tile) = if (cell.z == 3407) Tile(cell.x, cell.z + 1) else Tile(cell.x, cell.z - 1)

    /** The tile outside a cage, beyond its railing. */
    private fun outside(cell: Tile) = if (cell.z == 3407) Tile(cell.x, cell.z + 2) else Tile(cell.x, cell.z - 2)

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                for (cell in Pillory.CELLS) markLoaded(cell.x shr 6, cell.z shr 6)
            },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()), objTypes = ObjTypes.of(*objs.toTypedArray()),
            locTypes = LocTypes(mapOf(Pillory.CAGE to LocType(Pillory.CAGE, "Cage", ops = listOf("Unlock", null, null, null, null)))),
            staticLocs = StaticLocs().apply {
                for (cell in Pillory.CELLS) railing(cell).let { add(ZoneLoc(Pillory.CAGE, LocShape.GROUND_DECOR, 0, it.x, it.z, 0)) }
            },
            cycleMillis = millis,
        )
    }

    private val home = Coord(3222, 3218, 0)

    private fun HeadlessClient.ready(x: Int = 3222, z: Int = 3218) {
        await("the login rebuild") { position }
        command("tele $x $z 0")
        await("the teleport") { position?.takeIf { it == Coord(x, z, 0) } }
        commandReply("randomevent off") { it == "Random events off" }
    }

    private fun HeadlessClient.chat(text: String) {
        awaitDialogue(text) { it is DialogueView.Chat && it.text == text }
        continueDialogue()
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    /** Talk-to and come quietly: into a cage. Returns the cage. */
    private fun HeadlessClient.arrested(): Tile {
        command("randomevent ${Pillory.key}")
        val npc = await("the guard") { npcs().firstOrNull { it.id == Pillory.npcId } }
        npcOp(npc.index, RandomEvents.TALK_TO)
        chat("[pillory_guard.arrest]")
        awaitDialogue("the options") { it is DialogueView.Menu }
        chooseOption(1)
        chat("[pillory_guard.come_quietly]")
        val at = await("the cage") { position?.takeIf { p -> Pillory.CELLS.any { it.x == p.x && it.z == p.z } } }
        await("the lock window") { if (Pillory.INTERFACE in openInterfaces()) Unit else null }
        return Tile(at.x, at.z)
    }

    /** Select on a key: the right one when [right], else a wrong one. */
    private fun HeadlessClient.select(stay: Pillory.Stay, right: Boolean) {
        val index = stay.keys.indexOfFirst { (it == stay.shape) == right }
        val before = stay.locks to stay.undone
        ifButton(Pillory.INTERFACE, Pillory.BUTTONS[index], 1)
        await("the lock tried") { if ((stay.locks to stay.undone) != before || Pillory.INTERFACE !in openInterfaces()) Unit else null }
    }

    @Test
    fun `not coming leaves him in a huff`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("randomevent ${Pillory.key}")
                val npc = client.await("the guard") { client.npcs().firstOrNull { it.id == Pillory.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[pillory_guard.arrest]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.chat("[pillory_guard.not_coming]")
                client.chat("[pillory_guard.hmmph]")
                client.over()
                client.await("him gone") { if (client.npcs().none { it.id == Pillory.npcId }) Unit else null }
                assertEquals(home, client.position)
            }
        }
    }

    @Test
    fun `a wrong key adds a lock, others can't unlock, and four right keys free the player with a reward`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                val cell = client.arrested()
                val alice = server.world.onlinePlayers.single()
                val stay = Pillory.stayOf(alice)!!
                assertEquals(Pillory.START_LOCKS, stay.locks)

                client.select(stay, right = true)
                assertEquals(1, stay.undone)
                client.select(stay, right = false)
                assertEquals(Pillory.START_LOCKS + 1 to 0, stay.locks to stay.undone, "a lock more, and the count again")

                server.login("carol").use { carol ->
                    val out = outside(cell)
                    carol.ready(out.x, out.z)
                    val r = railing(cell)
                    carol.locOp(Pillory.CAGE, r.x, r.z, Pillory.UNLOCK)
                    carol.await("the refusal") { carol.messages().firstOrNull { it.text == "[pillory.unlock]" } }
                }

                client.closeModal()
                client.await("the window shut") { if (Pillory.INTERFACE !in client.openInterfaces()) Unit else null }
                // A key sent with the window shut is ignored.
                client.ifButton(Pillory.INTERFACE, Pillory.BUTTONS[stay.keys.indexOf(stay.shape)], 1)
                client.awaitTicks(2)
                assertEquals(Pillory.START_LOCKS + 1 to 0, stay.locks to stay.undone, "nothing tried with the window shut")
                val r = railing(cell)
                client.locOp(Pillory.CAGE, r.x, r.z, Pillory.UNLOCK)
                client.await("the window again") { if (Pillory.INTERFACE in client.openInterfaces()) Unit else null }

                repeat(Pillory.START_LOCKS + 1) { client.select(stay, right = true) }
                client.await("home") { client.position?.takeIf { it == home } }
                client.await("the window shut") { if (Pillory.INTERFACE !in client.openInterfaces()) Unit else null }
                val got = client.await("the reward") { client.inventory(Inventory.INV).filterNotNull().singleOrNull() }
                assertTrue(Pillory.REWARDS.any { (id, count, _) -> id == got.id && count == got.count }, "reward $got")
                assertEquals(null, Pillory.stayOf(alice))
            }
        }
    }

    @Test
    fun `logging out in a cage keeps the player in, done they go to Lumbridge, and the end command lets one out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready(3205, 3210)
                client.arrested()
                client.closeModal()
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            // The save and the slot are freed a tick or two after the client goes.
            val deadline = System.nanoTime() + 10_000_000_000
            while (server.world.onlinePlayers.isNotEmpty()) {
                check(System.nanoTime() < deadline) { "dave still online" }
                Thread.sleep(20)
            }
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.await("the lock window") { if (Pillory.INTERFACE in client.openInterfaces()) Unit else null }
                val stay = Pillory.stayOf(server.world.onlinePlayers.single())!!
                repeat(stay.locks) { client.select(stay, right = true) }
                client.await("Lumbridge") { client.position?.takeIf { it == Coord(World.SPAWN_X, World.SPAWN_Z, 0) } }

                // ::randomevent end lets a prisoner out with nothing.
                client.command("tele 3205 3210 0")
                client.await("the teleport") { client.position?.takeIf { it == Coord(3205, 3210, 0) } }
                val before = client.inventory(Inventory.INV).filterNotNull()
                client.arrested()
                client.commandReply("randomevent end") { it == "Ended random event ${Pillory.key}" }
                client.await("back") { client.position?.takeIf { it == Coord(3205, 3210, 0) } }
                client.await("the window shut") { if (Pillory.INTERFACE !in client.openInterfaces()) Unit else null }
                assertEquals(before, client.inventory(Inventory.INV).filterNotNull())
                client.commandReply("randomevent end") { it == "No random event holds you" }
            }
        }
    }

    @Test
    fun `a teleport from the cage is refused, and a fight begun during the talk keeps the player out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.ready()
                val erin = server.world.onlinePlayers.single()

                // Codex on #993 (P1): yes after a fight began during the talk doesn't take them.
                client.command("randomevent ${Pillory.key}")
                val npc = client.await("the guard") { client.npcs().firstOrNull { it.id == Pillory.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[pillory_guard.arrest]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                erin.attackedTick = server.world.tick
                client.chooseOption(1)
                client.chat("[pillory_guard.come_quietly]")
                client.over()
                client.awaitTicks(2)
                assertEquals(home, client.position)
                assertEquals(null, Pillory.stayOf(erin))
                client.awaitTicks(World.LOGOUT_COMBAT_TICKS.toInt() + 1)

                // Codex on #993 (P2): in the cage, Home Teleport is refused and they stay held.
                val cell = client.arrested()
                client.closeModal()
                val before = client.messages().size
                client.ifButton(Spells.SPELLBOOK, HomeTeleport.COMPONENT, HomeTeleport.CAST)
                client.await("the refusal") { Unit.takeIf { client.messages().drop(before).any { it.text == "[pillory_guard.no_teleport]" } } }
                client.awaitTicks(HomeTeleport.CAST_TICKS + 2)
                assertEquals(Coord(cell.x, cell.z, 0), client.position)
                assertTrue(Pillory.stayOf(erin) != null, "still held")

                // Moved out by any other way (here the admin teleport), the stay is over.
                client.command("tele 3222 3218 0")
                client.await("home") { client.position?.takeIf { it == home } }
                client.awaitTicks(1)
                assertEquals(null, Pillory.stayOf(erin))
            }
        }
    }
}
