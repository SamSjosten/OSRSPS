package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.TimeUnit
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
import osrsps.game.World.Companion.LOGOUT_BUTTON
import osrsps.game.World.Companion.LOGOUT_TAB
import osrsps.game.content.randomevents.PrisonPete
import osrsps.game.content.randomevents.RandomEvents
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Random events step 11 through the headless client: Evil Bob takes his player to ScapeRune's prison. The lever picks a
 * balloon animal shape; the key from that shape takes a barrier away, any other puts one back, and three right in a
 * row free Pete. Walking out through the exit brings the player home with a present; saying they want to leave brings
 * them home with nothing. The rl-client script test-runner/examples/random-events-prison-pete.json does part of it in
 * the real game.
 */
class HeadlessPrisonPeteTest {
    private val npcs = listOf(
        NpcType(PrisonPete.npcId, "Evil Bob", ops = listOf("Talk-to", null, null, null, "Dismiss")),
        NpcType(PrisonPete.PETE, "Prison Pete", ops = listOf("Talk-to", null, null, null, null)),
    ) + PrisonPete.BALLOONS.map { NpcType(it, "Balloon Animal", size = 2, ops = listOf("Pop", null, null, null, null)) }

    private val objs = listOf(ObjType(PrisonPete.KEY, "Prison key", iops = listOf("Return", null, null, null, "Drop"))) +
        PrisonPete.PRESENTS.map { ObjType(it.obj, "Present ${it.obj}", stackable = true) }

    private val locs = listOf(
        LocType(PrisonPete.LEVER, "Lever", ops = listOf("Pull", null, null, null, null)),
        LocType(PrisonPete.MAIN_DOORS[1], "Locked door", ops = listOf("Open", null, null, null, null)),
    ) + PrisonPete.DOORS.map { LocType(it, "null") }

    private val mainDoor = Tile(2086, 4459)

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(32, 69) },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()), objTypes = ObjTypes.of(*objs.toTypedArray()),
            locTypes = LocTypes(locs.associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(PrisonPete.LEVER, LocShape.CENTREPIECE_STRAIGHT, 3, PrisonPete.LEVER_AT.x, PrisonPete.LEVER_AT.z, 0))
                add(ZoneLoc(PrisonPete.MAIN_DOORS[1], LocShape.CENTREPIECE_STRAIGHT, 1, mainDoor.x, mainDoor.z, 0))
                for ((i, tile) in PrisonPete.DOOR_TILES.withIndex()) add(ZoneLoc(PrisonPete.DOORS[i], LocShape.CENTREPIECE_STRAIGHT, 2, tile.x, tile.z, 0))
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

    /** Talk-to on Evil Bob and yes: into the prison and Pete's explanation, up to its choice. Returns its offset. */
    private fun HeadlessClient.intoPrison(): Pair<Int, Int> {
        command("randomevent ${PrisonPete.key}")
        val npc = await("Evil Bob") { npcs().firstOrNull { it.id == PrisonPete.npcId } }
        npcOp(npc.index, RandomEvents.TALK_TO)
        chat("[prison_pete.pet]")
        menu(1)
        chat("[prison_pete.captured_too]")
        chat("[prison_pete.captured]")
        chat("[prison_pete.stuck_me]")
        explanation()
        val at = position!!
        return at.x - PrisonPete.ARRIVE.x to at.z - PrisonPete.ARRIVE.z
    }

    private fun HeadlessClient.explanation() {
        chat("[prison_pete.how_get_out]")
        chat("[prison_pete.balloon_keys]")
        chat("[prison_pete.pull_lever]")
        chat("[prison_pete.bring_keys]")
    }

    private fun HeadlessClient.pull(dx: Int, dz: Int) {
        locOp(PrisonPete.LEVER, PrisonPete.LEVER_AT.x + dx, PrisonPete.LEVER_AT.z + dz, PrisonPete.PULL)
        chat("[prison_pete.thats_right]")
        await("the balloon window") { if (PrisonPete.INTERFACE in openInterfaces()) Unit else null }
        over()
    }

    /** Pop on a balloon animal of [animal]'s shape, or of any other with [right] false; then Pete tries the key. */
    private fun HeadlessClient.pop(animal: PrisonPete.Animal, right: Boolean, then: String) {
        // A balloon just popped can still be in the client's view for a tick, and Pop on it does nothing: pick again.
        for (attempt in 1..3) {
            val balloon = await("a balloon") { npcs().firstOrNull { PrisonPete.Animal.of(it.id)?.let { a -> (a == animal) == right } == true } }
            npcOp(balloon.index, PrisonPete.POP)
            val got = runCatching { awaitDialogue("[prison_pete.got_key]", 4000) { it is DialogueView.Chat && it.text == "[prison_pete.got_key]" } }
            if (got.isSuccess) break
            if (attempt == 3) throw got.exceptionOrNull()!!
        }
        continueDialogue()
        chat("[prison_pete.ooh_thanks]")
        chat(then)
    }

    @Test
    fun `three right keys in a row free Pete, and walking out brings a present`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                val (dx, dz) = client.intoPrison()
                client.menu(1)
                client.chat("[prison_pete.okay]")
                client.over()
                val prison = PrisonPete.prisonOf(server.world, server.world.onlinePlayers.single())!!.second

                val any = client.await("a balloon") { client.npcs().firstOrNull { it.id in PrisonPete.BALLOONS } }
                client.npcOp(any.index, PrisonPete.POP)
                client.chat("[prison_pete.pull_first]")
                client.over()

                client.pull(dx, dz)
                client.pop(prison.target!!, right = true, then = "[prison_pete.right_one]")
                client.over()
                assertEquals(1, prison.open)
                client.pull(dx, dz)
                client.pop(prison.target!!, right = false, then = "[prison_pete.wrong_key]")
                client.over()
                assertEquals(0, prison.open, "a wrong key puts the barrier back")

                for (then in listOf("[prison_pete.right_one]", "[prison_pete.right_one]", "[prison_pete.all_keys]")) {
                    client.pull(dx, dz)
                    client.pop(prison.target!!, right = true, then = then)
                }
                client.chat("[prison_pete.get_out]")
                client.over()
                assertTrue(prison.free)
                assertEquals(0, client.inventory(Inventory.INV).count { it?.id == PrisonPete.KEY })
                assertTrue(PrisonPete.DOOR_TILES.all { t -> server.world.locs.at(t.x + dx, t.z + dz, 0).none { it.id in PrisonPete.DOORS } }, "the barriers are gone")

                // A balloon popped at the far west end can leave the player out of view of Pete (more than 15 tiles):
                // back to the arrival tile first.
                client.walk(PrisonPete.ARRIVE.x + dx, PrisonPete.ARRIVE.z + dz)
                client.await("the arrival tile") { client.position?.takeIf { it.x == PrisonPete.ARRIVE.x + dx && it.z == PrisonPete.ARRIVE.z + dz } }
                val pete = client.await("Pete") { client.npcs().firstOrNull { it.id == PrisonPete.PETE } }
                client.npcOp(pete.index, RandomEvents.TALK_TO)
                client.chat("[prison_pete.come_on]")
                client.over()

                client.walk(PrisonPete.OUT_X + 1 + dx, PrisonPete.DOOR_TILES[0].z + dz)
                client.await("home") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                // The present comes before his line: shutting it loses nothing.
                client.awaitDialogue("his line") { it is DialogueView.Chat && it.text == "[prison_pete.present]" }
                client.closeModal()
                client.over()
                client.awaitTicks(1)
                val got = client.inventory(Inventory.INV).filterNotNull()
                assertEquals(1, got.size, "one present: $got")
                assertTrue(got.single().id in PrisonPete.PRESENTS.map { it.obj })
                client.await("the prison ended") { if (server.world.instances.all.isEmpty()) Unit else null }
            }
        }
    }

    @Test
    fun `logging out holding a balloon's key leaves it in the prison`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                val (dx, dz) = client.intoPrison()
                client.menu(1)
                client.chat("[prison_pete.okay]")
                client.over()
                val prison = PrisonPete.prisonOf(server.world, server.world.onlinePlayers.single())!!.second
                client.pull(dx, dz)
                val balloon = client.await("a balloon") { client.npcs().firstOrNull { PrisonPete.Animal.of(it.id) == prison.target } }
                client.npcOp(balloon.index, PrisonPete.POP)
                client.await("the key") { if (client.inventory(Inventory.INV).any { it?.id == PrisonPete.KEY }) Unit else null }
                client.ifButton(LOGOUT_TAB, LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (server.world.playerCount != 0) {
                check(System.nanoTime() < deadline) { "Timed out waiting for the world to let the last player go" }
                Thread.sleep(10)
            }
            server.login("carol").use { client ->
                client.await("back where they came from") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.awaitTicks(2)
                assertEquals(0, client.inventory(Inventory.INV).count { it?.id == PrisonPete.KEY }, "the key stays in the prison")
            }
        }
    }

    @Test
    fun `no leaves him alone, the main door is not the way, and leaving early brings nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("randomevent ${PrisonPete.key}")
                val npc = client.await("Evil Bob") { client.npcs().firstOrNull { it.id == PrisonPete.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[prison_pete.pet]")
                client.menu(2)
                client.awaitDialogue("the box") { it is DialogueView.Message && it.text == "[evil_bob.leave_alone]" }
                client.continueDialogue()
                client.over()
                client.await("him gone") { if (client.npcs().none { it.id == PrisonPete.npcId }) Unit else null }

                client.awaitTicks(2)
                val (dx, dz) = client.intoPrison()
                client.menu(1)
                client.chat("[prison_pete.okay]")
                client.over()

                client.locOp(PrisonPete.MAIN_DOORS[1], mainDoor.x + dx, mainDoor.z + dz, PrisonPete.OPEN)
                client.chat("[prison_pete.this_exit]")
                client.over()

                val pete = client.await("Pete") { client.npcs().firstOrNull { it.id == PrisonPete.PETE } }
                client.npcOp(pete.index, RandomEvents.TALK_TO)
                client.menu(1)
                client.chat("[prison_pete.what_place]")
                client.chat("[prison_pete.dont_remember]")
                client.chat("[prison_pete.my_rights]")
                client.chat("[prison_pete.doesnt_care]")
                client.explanation()
                client.menu(2)
                client.chat("[prison_pete.leave_now]")
                client.chat("[prison_pete.still_stuck]")
                client.menu(1)
                client.await("home") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.awaitTicks(2)
                assertTrue(client.inventory(Inventory.INV).all { it == null })
                client.await("the prison ended") { if (server.world.instances.all.isEmpty()) Unit else null }
            }
        }
    }
}
