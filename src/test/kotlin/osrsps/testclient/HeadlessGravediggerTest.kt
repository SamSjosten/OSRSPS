package osrsps.testclient

import java.nio.file.Path
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
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.XpLamp
import osrsps.game.content.randomevents.Gravedigger
import osrsps.game.content.randomevents.RandomEvents
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Random events step 6 through the headless client: Leo takes his player to a graveyard of their own, where the five
 * coffins are in the wrong graves. Take-Coffin, Check and Read work; saying it's finished is refused until every
 * coffin is under its own gravestone, then brings the player back with two zombie pieces, or with the whole outfit a
 * zombie emote, or with both emotes a lamp. Declining and leaving early give nothing. The rl-client
 * script test-runner/examples/random-events-gravedigger.json does part of it in the real game.
 */
class HeadlessGravediggerTest {
    private val npcs = listOf(
        NpcType(Gravedigger.npcId, "Leo", ops = listOf("Talk-to", null, null, null, "Dismiss")),
        NpcType(Gravedigger.GRAVEYARD_LEO, "Leo", ops = listOf("Talk-to", null, null, null, null)),
    )

    private val objs = Gravedigger.COFFINS.map { ObjType(it, "Coffin", iops = listOf("Check", null, null, null, "Drop")) } +
        Gravedigger.OUTFIT.map { ObjType(it, "Zombie $it") } + ObjType(XpLamp.LAMP, "Lamp") + ObjType(BEER, "Beer")

    private val grave = LocCollisionType(width = 1, length = 2)
    private val locs = Gravedigger.FULL_GRAVES.map { LocType(it, "Grave", ops = listOf("Take-Coffin", null, null, null, null), collision = grave) } +
        Gravedigger.EMPTY_GRAVES.map { LocType(it, "Grave", collision = grave) } +
        Gravedigger.GRAVESTONES.map { LocType(it, "Gravestone", ops = listOf("Read", null, null, null, null)) }

    private val varbits = Gravedigger.EMOTE_VARBITS.withIndex().associate { (i, id) -> id to VarbitType(EMOTE_ACCESS, 5 + i, 5 + i) }

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(30, 78) },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()), objTypes = ObjTypes.of(*objs.toTypedArray()),
            locTypes = LocTypes(locs.associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                for (i in 0 until 5) {
                    add(ZoneLoc(Gravedigger.FULL_GRAVES[i], LocShape.CENTREPIECE_STRAIGHT, 0, Gravedigger.GRAVE_TILES[i].x, Gravedigger.GRAVE_TILES[i].z, 0))
                    add(ZoneLoc(Gravedigger.GRAVESTONES[i], LocShape.CENTREPIECE_STRAIGHT, 0, Gravedigger.GRAVESTONE_TILES[i].x, Gravedigger.GRAVESTONE_TILES[i].z, 0))
                }
            },
            varTypes = VarTypes(2_000, varbits), cycleMillis = millis,
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

    private fun HeadlessClient.slotOf(id: Int) = inventory(Inventory.INV).indexOfFirst { it?.id == id }

    /** Talk-to on Leo out in the world and yes: into the graveyard. Returns its offset. */
    private fun HeadlessClient.intoYard(name: String): Pair<Int, Int> {
        command("randomevent ${Gravedigger.key}")
        val npc = await("Leo") { npcs().firstOrNull { it.id == Gravedigger.npcId } }
        npcOp(npc.index, RandomEvents.TALK_TO)
        chat("[gravedigger.help name=$name]")
        awaitDialogue("the options") { it is DialogueView.Menu }
        chooseOption(1)
        chat("[gravedigger.okay]")
        chat("[gravedigger.wrong_coffins name=$name]")
        chat("[gravedigger.pick_up]")
        over()
        val at = position!!
        return at.x - Gravedigger.ARRIVE.x to at.z - Gravedigger.ARRIVE.z
    }

    private fun HeadlessClient.talkToLeo(option: Int, said: String) {
        val leo = await("the graveyard's Leo") { npcs().firstOrNull { it.id == Gravedigger.GRAVEYARD_LEO } }
        npcOp(leo.index, RandomEvents.TALK_TO)
        awaitDialogue("his options") { it is DialogueView.Menu }
        chooseOption(option)
        chat(said)
    }

    private fun HeadlessClient.takeCoffin(grave: Int, dx: Int, dz: Int) {
        val before = inventory(Inventory.INV).count { it?.id in Gravedigger.COFFINS }
        val tile = Gravedigger.GRAVE_TILES[grave]
        locOp(Gravedigger.FULL_GRAVES[grave], tile.x + dx, tile.z + dz, Gravedigger.TAKE_COFFIN)
        await("grave ${grave + 1}'s coffin") { if (inventory(Inventory.INV).count { it?.id in Gravedigger.COFFINS } == before + 1) Unit else null }
    }

    private fun HeadlessClient.bury(grave: Int, coffin: Int, dx: Int, dz: Int) {
        val tile = Gravedigger.GRAVE_TILES[grave]
        val slot = slotOf(coffin)
        locTarget(Gravedigger.EMPTY_GRAVES[grave], tile.x + dx, tile.z + dz, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, coffin)
        await("coffin $coffin buried") { if (slotOf(coffin) < 0) Unit else null }
    }

    /** Every coffin out, then each into its own grave, as the gravestones (the server's) say. */
    private fun HeadlessClient.solve(server: TestServer, dx: Int, dz: Int) {
        for (grave in 0 until 5) takeCoffin(grave, dx, dz)
        val (_, yard) = Gravedigger.yardOf(server.world, server.world.onlinePlayers.single())!!
        for (grave in 0 until 5) bury(grave, yard.owners[grave].coffin, dx, dz)
        assertTrue(yard.solved())
    }

    @Test
    fun `Leo's graveyard, a wrong answer refused, then every coffin in its grave and back with zombie pieces, an emote, a lamp`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                val (dx, dz) = client.intoYard("alice")
                assertTrue(dx != 0 || dz != 0, "in an instance")
                // Read and Check show their windows.
                val stone = Gravedigger.GRAVESTONE_TILES[0]
                client.locOp(Gravedigger.GRAVESTONES[0], stone.x + dx, stone.z + dz, Gravedigger.READ)
                client.await("the gravestone") { if (Gravedigger.GRAVESTONE_INTERFACE in client.openInterfaces()) Unit else null }
                client.closeModal()
                client.takeCoffin(0, dx, dz)
                val coffin = client.inventory(Inventory.INV).first { it?.id in Gravedigger.COFFINS }!!.id
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, CHECK_BUTTON_OP, client.slotOf(coffin), coffin)
                client.await("the coffin's contents") { if (Gravedigger.COFFIN_INTERFACE in client.openInterfaces()) Unit else null }
                client.closeModal()
                // None started right, and one is out: not finished.
                client.talkToLeo(1, "[gravedigger.finished]")
                client.chat("[gravedigger.good_attempt]")
                client.chat("[gravedigger.another_shot]")
                client.over()
                client.bury(0, coffin, dx, dz)

                client.solve(server, dx, dz)
                client.talkToLeo(1, "[gravedigger.finished]")
                client.chat("[gravedigger.wonderful]")
                client.await("back where they came from") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.await("two zombie pieces") { if (Gravedigger.OUTFIT.count { client.slotOf(it) >= 0 } == 2) Unit else null }
                assertTrue(client.inventory(Inventory.INV).none { it?.id in Gravedigger.COFFINS })

                // With the whole outfit, an emote; with both, a lamp.
                for (piece in Gravedigger.OUTFIT) if (client.slotOf(piece) < 0) client.command("item $piece")
                client.await("the whole outfit") { if (Gravedigger.OUTFIT.all { client.slotOf(it) >= 0 }) Unit else null }
                repeat(2) { round ->
                    client.awaitTicks(2)
                    val (dx2, dz2) = client.intoYard("alice")
                    client.solve(server, dx2, dz2)
                    client.talkToLeo(1, "[gravedigger.finished]")
                    client.chat("[gravedigger.wonderful]")
                    client.await("back") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                    client.await("emote ${round + 1}") { if (Integer.bitCount(client.varp(EMOTE_ACCESS) and EMOTE_BITS) == round + 1) Unit else null }
                }
                assertEquals(-1, client.slotOf(XpLamp.LAMP))
                client.awaitTicks(2)
                val (dx3, dz3) = client.intoYard("alice")
                client.solve(server, dx3, dz3)
                client.talkToLeo(1, "[gravedigger.finished]")
                // Back, and the lamp given, before his line: shutting it loses nothing.
                client.awaitDialogue("his line") { it is DialogueView.Chat && it.text == "[gravedigger.wonderful]" }
                client.await("back again") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.await("the lamp") { if (client.slotOf(XpLamp.LAMP) >= 0) Unit else null }
                client.closeModal()
                client.over()
                client.await("the graveyard ended") { if (server.world.instances.all.isEmpty()) Unit else null }
                client.awaitTicks(2)
                assertEquals(1, client.inventory(Inventory.INV).count { it?.id == XpLamp.LAMP })
            }
        }
    }

    @Test
    fun `with a full backpack the pieces go on the ground back home, not in the graveyard`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                val (dx, dz) = client.intoYard("carol")
                client.solve(server, dx, dz)
                client.command("item $BEER 28")
                client.await("a full backpack") { if (client.inventory(Inventory.INV).count { it != null } == 28) Unit else null }
                client.talkToLeo(1, "[gravedigger.finished]")
                client.chat("[gravedigger.wonderful]")
                client.await("back") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                val pieces = client.await("two pieces on the ground") { client.groundObjs().filter { it.id in Gravedigger.OUTFIT }.takeIf { it.size == 2 } }
                assertTrue(pieces.all { it.coord == Coord(3222, 3218, 0) }, pieces.toString())
            }
        }
    }

    @Test
    fun `busy sends him off, remind me repeats the task, and leaving early takes the coffins and gives nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("randomevent ${Gravedigger.key}")
                val npc = client.await("Leo") { client.npcs().firstOrNull { it.id == Gravedigger.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[gravedigger.help name=bob]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.chat("[gravedigger.busy]")
                client.over()
                client.await("him gone") { if (client.npcs().none { it.id == Gravedigger.npcId }) Unit else null }

                client.awaitTicks(2)
                val (dx, dz) = client.intoYard("bob")
                client.talkToLeo(2, "[gravedigger.remind]")
                client.chat("[gravedigger.pick_up]")
                client.awaitDialogue("the steps") { it is DialogueView.Message && it.text == "[gravedigger.instructions]" }
                client.continueDialogue()
                client.over()
                client.takeCoffin(2, dx, dz)
                client.talkToLeo(3, "[gravedigger.leave]")
                client.chat("[gravedigger.take_you_back]")
                client.await("back") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.awaitTicks(2)
                assertTrue(client.inventory(Inventory.INV).all { it == null })
                client.await("the graveyard ended") { if (server.world.instances.all.isEmpty()) Unit else null }
            }
        }
    }

    private companion object {
        const val EMOTE_ACCESS = 313
        const val EMOTE_BITS = (1 shl 5) or (1 shl 6)
        const val CHECK_BUTTON_OP = 2
        const val BEER = 1917
    }
}
