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
import osrsps.game.content.ShantayPass
import osrsps.game.content.XpLamp
import osrsps.game.content.randomevents.FreakyForester
import osrsps.game.content.randomevents.RandomEvents
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Random events step 4 through the headless client: the Freaky Forester takes his player to a forest of their own,
 * asks for a pheasant, takes any pheasant's carcass, and the portal brings them back where they were with a Lederhosen
 * piece (a lamp once they have all three); declining him and an early portal give nothing. The rl-client script
 * test-runner/examples/random-events-freaky-forester.json does it in the real game.
 */
class HeadlessFreakyForesterTest {
    private val npcs = listOf(
        NpcType(FreakyForester.npcId, "Freaky Forester", ops = listOf("Talk-to", null, null, null, "Dismiss")),
        NpcType(FreakyForester.FOREST_FORESTER, "Freaky Forester", ops = listOf("Talk-to", null, null, null, null)),
    ) + FreakyForester.PHEASANTS.keys.map { NpcType(it, "Pheasant", ops = listOf(null, "Kill", null, null, null)) }

    private val objs = listOf(
        ObjType(FreakyForester.RAW_PHEASANT, "Raw pheasant"),
        ObjType(FreakyForester.HAT, "Lederhosen hat"), ObjType(FreakyForester.TOP, "Lederhosen top"), ObjType(FreakyForester.SHORTS, "Lederhosen shorts"),
        ObjType(XpLamp.LAMP, "Lamp"), ObjType(BEER, "Beer"),
    )

    private val portal = LocType(FreakyForester.PORTAL, "Exit portal", ops = listOf("Use", null, null, null, null), collision = LocCollisionType(width = 1, length = 1))

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(40, 74) },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()), objTypes = ObjTypes.of(*objs.toTypedArray()),
            locTypes = LocTypes(mapOf(FreakyForester.PORTAL to portal)),
            staticLocs = StaticLocs().apply { add(ZoneLoc(FreakyForester.PORTAL, LocShape.CENTREPIECE_STRAIGHT, 0, PORTAL_X, PORTAL_Z, 0)) },
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

    private fun HeadlessClient.has(id: Int) = inventory(Inventory.INV).count { it?.id == id }

    /** Talk-to on the Forester out in the world and yes: into the forest, and his question. Returns the forest's offset. */
    private fun HeadlessClient.intoForest(name: String): Pair<Int, Int> {
        command("randomevent ${FreakyForester.key}")
        val npc = await("the Forester") { npcs().firstOrNull { it.id == FreakyForester.npcId } }
        npcOp(npc.index, RandomEvents.TALK_TO)
        chat("[freaky_forester.help name=$name]")
        awaitDialogue("the options") { it is DialogueView.Menu }
        chooseOption(1)
        chat("[freaky_forester.okay]")
        val asked = awaitDialogue("his question") { it is DialogueView.Chat && it.text.startsWith("[freaky_forester.ask_") } as DialogueView.Chat
        assertTrue(Regex("""\[freaky_forester\.ask_(words|kill|need) name=\w+ tails=(two|three|four|[234])]""").matches(asked.text), asked.text)
        continueDialogue()
        over()
        val at = position!!
        return at.x - FreakyForester.ARRIVE.x to at.z - FreakyForester.ARRIVE.z
    }

    private fun HeadlessClient.killAndHandIn(name: String, dx: Int, dz: Int, beforePortal: () -> Unit = {}) {
        val pheasant = await("a pheasant") { npcs().firstOrNull { it.id in FreakyForester.PHEASANTS } }
        npcOp(pheasant.index, FreakyForester.KILL)
        val carcass = await("the carcass") { groundObjs().firstOrNull { it.id == FreakyForester.RAW_PHEASANT } }
        // A second Kill while the first carcass lies there is still allowed; with one held it isn't.
        objOp(FreakyForester.RAW_PHEASANT, carcass.coord.x, carcass.coord.z, TAKE)
        await("the carcass taken") { if (has(FreakyForester.RAW_PHEASANT) == 1) Unit else null }
        val other = await("another pheasant") { npcs().firstOrNull { it.id in FreakyForester.PHEASANTS } }
        npcOp(other.index, FreakyForester.KILL)
        await("the refusal") { messages().firstOrNull { it.text == "[freaky_forester.already_killed]" } }
        val forester = await("the forest's Forester") { npcs().firstOrNull { it.id == FreakyForester.FOREST_FORESTER } }
        npcOp(forester.index, RandomEvents.TALK_TO)
        chat("[freaky_forester.thanks name=$name]")
        over()
        assertEquals(0, has(FreakyForester.RAW_PHEASANT))
        beforePortal()
        locOp(FreakyForester.PORTAL, PORTAL_X + dx, PORTAL_Z + dz, 1)
    }

    @Test
    fun `the Forester's forest, a pheasant handed in, and the portal back with a Lederhosen piece, then a lamp once all are had`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                val (dx, dz) = client.intoForest("alice")
                assertTrue(dx != 0 || dz != 0, "in an instance")
                client.killAndHandIn("alice", dx, dz)
                val piece = client.awaitDialogue("the reward") { it is DialogueView.Chat && it.text.startsWith("[freaky_forester.reward piece=") } as DialogueView.Chat
                client.continueDialogue()
                client.await("back where he came from") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                val id = mapOf("hat" to FreakyForester.HAT, "top" to FreakyForester.TOP, "shorts" to FreakyForester.SHORTS).getValue(piece.text.substringAfter("piece=").removeSuffix("]"))
                client.await("the piece") { if (client.has(id) == 1) Unit else null }

                // With all three had (banked counts too), a lamp.
                client.command("item ${FreakyForester.HAT}")
                client.command("item ${FreakyForester.TOP}")
                client.command("item ${FreakyForester.SHORTS}")
                client.await("all three") { if (listOf(FreakyForester.HAT, FreakyForester.TOP, FreakyForester.SHORTS).all { client.has(it) > 0 }) Unit else null }
                val (dx2, dz2) = client.intoForest("alice")
                client.killAndHandIn("alice", dx2, dz2)
                client.awaitDialogue("the lamp line") { it is DialogueView.Chat && it.text == "[freaky_forester.reward_lamp]" }
                // Back, and the lamp given, before his line: shutting it loses nothing.
                client.await("back again") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.await("the lamp") { if (client.has(XpLamp.LAMP) == 1) Unit else null }
                client.closeModal()
                client.over()
                client.awaitTicks(2)
                assertEquals(1, client.has(XpLamp.LAMP))
            }
        }
    }

    @Test
    fun `with a full backpack the piece goes on the ground back home, not in the forest`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                val (dx, dz) = client.intoForest("carol")
                client.killAndHandIn("carol", dx, dz) {
                    client.command("item $BEER 28")
                    client.await("a full backpack") { if (client.inventory(Inventory.INV).count { it != null } == 28) Unit else null }
                }
                client.awaitDialogue("the reward") { it is DialogueView.Chat && it.text.startsWith("[freaky_forester.reward piece=") }
                client.await("back") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                val pieces = setOf(FreakyForester.HAT, FreakyForester.TOP, FreakyForester.SHORTS)
                val obj = client.await("the piece on the ground") { client.groundObjs().firstOrNull { it.id in pieces } }
                assertEquals(Coord(3222, 3218, 0), obj.coord)
            }
        }
    }

    @Test
    fun `busy sends him off, and an early portal brings the player back with nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("randomevent ${FreakyForester.key}")
                val npc = client.await("the Forester") { client.npcs().firstOrNull { it.id == FreakyForester.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[freaky_forester.help name=bob]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.chat("[freaky_forester.busy]")
                client.over()
                client.await("him gone") { if (client.npcs().none { it.id == FreakyForester.npcId }) Unit else null }
                assertEquals(Coord(3222, 3218, 0), client.position)

                client.awaitTicks(2)
                val (dx, dz) = client.intoForest("bob")
                client.locOp(FreakyForester.PORTAL, PORTAL_X + dx, PORTAL_Z + dz, 1)
                client.await("back") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.awaitTicks(2)
                assertTrue(client.inventory(Inventory.INV).all { it == null })
                assertTrue(server.world.instances.all.isEmpty(), "the forest ended")
            }
        }
    }

    @Test
    fun `a raw pheasant stays in the forest when the player teleports out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("eve").use { client ->
                client.ready()
                client.intoForest("eve")
                val pheasant = client.await("a pheasant") { client.npcs().firstOrNull { it.id in FreakyForester.PHEASANTS } }
                client.npcOp(pheasant.index, FreakyForester.KILL)
                val carcass = client.await("the carcass") { client.groundObjs().firstOrNull { it.id == FreakyForester.RAW_PHEASANT } }
                client.objOp(FreakyForester.RAW_PHEASANT, carcass.coord.x, carcass.coord.z, TAKE)
                client.await("the carcass taken") { if (client.has(FreakyForester.RAW_PHEASANT) == 1) Unit else null }
                // Out by a teleport, not the portal: the forest's own item stays there (exploit review R51 A-1).
                client.command("tele 3222 3218 0")
                client.await("out") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.awaitTicks(1)
                assertEquals(0, client.has(FreakyForester.RAW_PHEASANT))
            }
        }
    }

    @Test
    fun `he never takes away a player in a fight, even one who says yes, and a stale jail flag doesn't count`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dan").use { client ->
                client.ready()
                val player = server.world.playerNamed("dan")!!
                client.command("randomevent ${FreakyForester.key}")
                val npc = client.await("the Forester") { client.npcs().firstOrNull { it.id == FreakyForester.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[freaky_forester.help name=dan]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                // In a fight that began during the talk: yes keeps them here, and he goes (exploit review R50). The hit
                // is dated ahead so the logout timer holds however the ticks fall.
                player.attackedTick = server.world.tick + 200
                client.chooseOption(1)
                client.chat("[freaky_forester.okay]")
                client.over()
                client.await("him gone") { if (client.npcs().none { it.id == FreakyForester.npcId }) Unit else null }
                assertEquals(Coord(3222, 3218, 0), client.position)
                assertTrue(server.world.instances.all.isEmpty())
                assertTrue(!server.world.randomEvents.allowedAt(player, FreakyForester))
                player.attackedTick = -1
                assertTrue(server.world.randomEvents.allowedAt(player, FreakyForester))
                // Shantay's jail flag counts only in his cell: one who got out some other way isn't held to it.
                player.charges[ShantayPass.JAILED] = 1
                assertTrue(server.world.randomEvents.allowedAt(player, FreakyForester))
                player.charges[ShantayPass.JAILED] = 0
            }
        }
    }

    private companion object {
        const val PORTAL_X = 2593
        const val BEER = 1917
        const val PORTAL_Z = 4775
        const val TAKE = 3
    }
}
