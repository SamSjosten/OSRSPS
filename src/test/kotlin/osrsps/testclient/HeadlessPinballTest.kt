package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameFrame
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.WorldMapScreen
import osrsps.game.content.randomevents.Pinball
import osrsps.game.content.randomevents.RandomEvents
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocShape

/**
 * Random events step 16 through the headless client: Flippa takes his player to the pinball room, where Tilt gives the
 * rules and the score window shows; tagging the glowing post scores, another puts the score back to 0, and ten points
 * then the exit send the player home with a gem reward. Declining leaves him waiting till he is talked to again;
 * the exit before ten, or a teleport out, sends the player home with nothing. The rl-client script
 * test-runner/examples/random-events-pinball.json does part of it in the real game.
 */
class HeadlessPinballTest {
    private val npcs = listOf(
        NpcType(Pinball.npcId, "Flippa", size = 2, ops = listOf("Talk-to", null, null, null, "Dismiss")),
        NpcType(Pinball.FLIPPA, "Flippa", size = 2, ops = listOf("Talk-To", null, null, null, null)),
        NpcType(Pinball.TILT, "Tilt", size = 2, ops = listOf("Talk-To", null, null, null, null)),
    )
    private val objs = Pinball.REWARDS.map { ObjType(it.first, "Item ${it.first}", stackable = true) }
    private val locs = Pinball.POSTS.flatMap { listOf(it.plain, it.glowing) }.map { LocType(it, "Pinball Post", ops = listOf("Tag", null, null, null, null)) } +
        LocType(Pinball.EXIT, "Cave Exit", ops = listOf("Exit", null, null, null, null))

    /** macro_pinball_var (727) and its four varbits, as the cache has them. */
    private val varp = 727
    private val varbits = mapOf(
        Pinball.CURRENT_VARBIT to VarbitType(varp, 1, 4), Pinball.NEXT_VARBIT to VarbitType(varp, 5, 8),
        Pinball.SCORE_VARBIT to VarbitType(varp, 9, 18), Pinball.COMPLETE_VARBIT to VarbitType(varp, 19, 19),
    )

    private fun server(dir: Path) = TestServer(dir, 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(30, 78) },
            npcTypes = NpcTypes.of(*npcs.toTypedArray()), objTypes = ObjTypes.of(*objs.toTypedArray()),
            locTypes = LocTypes(locs.associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                for (p in Pinball.POSTS) add(ZoneLoc(p.plain, LocShape.CENTREPIECE_STRAIGHT, 0, p.at.x, p.at.z, 0))
                add(ZoneLoc(Pinball.EXIT, LocShape.CENTREPIECE_STRAIGHT, 2, 1971, 5036, 0))
            },
            varTypes = VarTypes(2_000, varbits),
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

    private fun HeadlessClient.score() = (varp(varp) shr 9) and 1023

    /** Talk-to and yes: into the room, with Tilt's rules. */
    private fun HeadlessClient.intoRoom(name: String) {
        command("randomevent ${Pinball.key}")
        val npc = await("Flippa") { npcs().firstOrNull { it.id == Pinball.npcId } }
        npcOp(npc.index, RandomEvents.TALK_TO)
        chat("[pinball.invite name=$name]")
        awaitDialogue("the options") { it is DialogueView.Menu }
        chooseOption(1)
        chat("[pinball.yes]")
        chat("[pinball.arrival]")
        over()
        await("the room") { position?.takeIf { it != home } }
    }

    @Test
    fun `tags score, a wrong one starts again, and ten then the exit send home with a gem`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.intoRoom("alice")
                assertTrue(Pinball.OVERLAY in client.openInterfaces())
                // The world map takes the floater; shut, the score window comes back.
                client.ifButton(GameFrame.ORBS, GameFrame.ORBS_WORLDMAP, 1)
                client.await("the world map") { if (WorldMapScreen.INTERFACE in client.openInterfaces()) Unit else null }
                client.ifButton(WorldMapScreen.INTERFACE, WorldMapScreen.CLOSE, 1)
                client.await("the score window back") { if (Pinball.OVERLAY in client.openInterfaces()) Unit else null }
                val world = server.world
                val (away, game) = Pinball.gameOf(world, world.onlinePlayers.single())!!
                fun tagPost(index: Int) {
                    val post = Pinball.POSTS[index]
                    val at = away.inside(post.at.x, post.at.z)
                    val id = world.locs.at(at.x, at.z, 0).single { it.id == post.plain || it.id == post.glowing }.id
                    client.locOp(id, at.x, at.z, Pinball.TAG)
                }
                fun lit(): List<Int> = Pinball.POSTS.indices.filter { i ->
                    val p = Pinball.POSTS[i]
                    val at = away.inside(p.at.x, p.at.z)
                    world.locs.at(at.x, at.z, 0).any { it.id == p.glowing }
                }
                fun glowing(): Int = lit().single()
                assertEquals(game.current, glowing())

                // One right, then a wrong one: back to 0.
                tagPost(glowing())
                client.await("a point") { if (client.score() == 1) Unit else null }
                tagPost((glowing() + 1) % Pinball.POSTS.size)
                client.await("back to 0") { if (client.score() == 0 && game.score == 0) Unit else null }

                // Before ten, the trolls give the rules.
                val tilt = client.npcs().first { it.id == Pinball.TILT }
                client.npcOp(tilt.index, RandomEvents.TALK_TO)
                for (key in listOf("what_to_do", "poke_pillars", "all_of_them", "not_rings", "flashing", "ten_times")) client.chat("[pinball.$key]")
                client.over()

                for (n in 1..Pinball.TO_WIN) {
                    tagPost(glowing())
                    client.await("point $n") { if (client.score() == n) Unit else null }
                }
                client.await("complete") { if ((client.varp(varp) shr 19) and 1 == 1) Unit else null }
                assertEquals(emptyList(), lit(), "none glows once done")

                client.npcOp(client.npcs().first { it.id == Pinball.FLIPPA }.index, RandomEvents.TALK_TO)
                for (key in listOf("free_to_go", "get_going", "nice_break")) client.chat("[pinball.$key]")
                client.over()

                val exit = away.inside(1971, 5036)
                client.locOp(Pinball.EXIT, exit.x, exit.z, Pinball.EXIT_OP)
                client.await("home") { client.position?.takeIf { it == home } }
                client.await("the score window closed") { if (Pinball.OVERLAY !in client.openInterfaces()) Unit else null }
                val got = client.await("the reward") { client.inventory(Inventory.INV).filterNotNull().singleOrNull() }
                assertTrue(Pinball.REWARDS.any { (id, count) -> id == got.id && count == got.count }, "reward $got")
                assertEquals(0, client.varp(varp))
                client.await("the room ended") { if (world.instances.all.isEmpty()) Unit else null }
            }
        }
    }

    @Test
    fun `no thanks leaves him till talked to again, and leaving early gives nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.command("randomevent ${Pinball.key}")
                val npc = client.await("Flippa") { client.npcs().firstOrNull { it.id == Pinball.npcId } }
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[pinball.invite name=bob]")
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.chat("[pinball.no_thanks]")
                client.over()
                client.awaitTicks(2)
                assertTrue(client.npcs().any { it.id == Pinball.npcId }, "he waits")
                client.npcOp(npc.index, RandomEvents.TALK_TO)
                client.chat("[pinball.going_away]")
                client.over()
                client.await("him gone") { if (client.npcs().none { it.id == Pinball.npcId }) Unit else null }

                // The exit before ten: home with nothing.
                client.awaitTicks(2)
                client.intoRoom("bob")
                val (away, _) = Pinball.gameOf(server.world, server.world.onlinePlayers.single())!!
                val exit = away.inside(1971, 5036)
                client.locOp(Pinball.EXIT, exit.x, exit.z, Pinball.EXIT_OP)
                client.await("home") { client.position?.takeIf { it == home } }
                client.await("the score window closed") { if (Pinball.OVERLAY !in client.openInterfaces()) Unit else null }
                client.awaitTicks(1)
                assertTrue(client.inventory(Inventory.INV).all { it == null })
                client.await("the room ended") { if (server.world.instances.all.isEmpty()) Unit else null }

                // Out by teleport: the score window goes too.
                client.awaitTicks(2)
                client.intoRoom("bob")
                client.command("tele 3222 3218 0")
                client.await("home") { client.position?.takeIf { it == home } }
                client.await("the score window closed") { if (Pinball.OVERLAY !in client.openInterfaces()) Unit else null }
                client.await("the room ended") { if (server.world.instances.all.isEmpty()) Unit else null }
            }
        }
    }
}
