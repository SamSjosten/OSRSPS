package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.Quests
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.bank.getVarbitIfKnown
import osrsps.game.content.JunaLines
import osrsps.game.content.TearsLines
import osrsps.game.content.TearsOfGuthix
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Post-quest content step 10 through the headless client: the tunnel down to the Chasm of Tears and back, a rock slope
 * crossed, Juna letting a player in, tears collected from the blue streams over the game's ticks, the drink's XP in
 * the lowest skill, and Juna turning them away until a week and 100,000 XP have passed. The rl-client script
 * test-runner/examples/tears-of-guthix.json does the same on the real map.
 */
class HeadlessTearsOfGuthixTest {
    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val juna = ZoneLoc(TearsOfGuthix.JUNA, LocShape.CENTREPIECE_STRAIGHT, 1, 3252, 9516, 2)
    private val tunnelDown = ZoneLoc(TearsOfGuthix.TUNNEL_DOWN, LocShape.CENTREPIECE_STRAIGHT, 1, 3225, 9539, 0)
    private val tunnelUp = ZoneLoc(TearsOfGuthix.TUNNEL_UP, LocShape.CENTREPIECE_STRAIGHT, 3, 3218, 9533, 2)
    private val rocks = ZoneLoc(TearsOfGuthix.ROCKS_UP, LocShape.CENTREPIECE_STRAIGHT, 0, 3240, 9524, 2)
    private val walls = TearsOfGuthix.WALLS.map { (x, z) -> ZoneLoc(TearsOfGuthix.WALL, LocShape.WALL_STRAIGHT, 1, x, z, 2) }
    private val streams = TearsOfGuthix.WALLS.map { (x, z) -> ZoneLoc(TearsOfGuthix.NONE_R, STREAM_SHAPE, 1, x, z, 2) }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3225 shr 6, 9539 shr 6); markLoaded(3252 shr 6, 9516 shr 6) },
            random = GameRandom(11),
            varTypes = VarTypes(
                6_000,
                mapOf(
                    TearsOfGuthix.VARBIT_JUNA to VarbitType(TOG_VARP, 0, 1), TearsOfGuthix.VARBIT_STORIES to VarbitType(TOG_VARP, 2, 5),
                    TearsOfGuthix.VARBIT_TEARS to VarbitType(TOG_VARP, 12, 20),
                ),
                21_000,
            ),
            locTypes = LocTypes(
                listOf(
                    LocType(
                        TearsOfGuthix.JUNA, "null", List(5) { null }, LocCollisionType(width = 1, length = 1), multiloc = true,
                        multiVarbit = TearsOfGuthix.VARBIT_JUNA, multiLocs = listOf(JUNA_1OP, JUNA_1OP, JUNA_2OPS, JUNA_2OPS, -1),
                    ),
                    type(JUNA_1OP, "Juna", "Talk-to"),
                    type(JUNA_2OPS, "Juna", "Talk-to", "Story"),
                    type(TearsOfGuthix.TUNNEL_DOWN, "Tunnel", "Enter"),
                    type(TearsOfGuthix.TUNNEL_UP, "Tunnel", "Enter"),
                    type(TearsOfGuthix.ROCKS_UP, "Rocks", "Climb"),
                    type(TearsOfGuthix.WALL, "Weeping wall", "Collect-from"),
                    type(TearsOfGuthix.BLUE_R, "Blue tears"), type(TearsOfGuthix.GREEN_R, "Green tears"), type(TearsOfGuthix.NONE_R, "Absence of tears"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { (listOf(juna, tunnelDown, tunnelUp, rocks) + walls + streams).forEach(::add) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int) = await("at $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        awaitAt(x, z, level)
    }

    private fun HeadlessClient.box(line: Line) {
        awaitDialogue(line.key) { it == DialogueView.Message(DialogueText.NONE[line]) }
        continueDialogue()
    }

    @Test
    fun `the tunnel goes down to the chasm and back, and the rock slope crosses`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3226, 9542, 0)
                client.locOp(tunnelDown.id, tunnelDown.x, tunnelDown.z, TearsOfGuthix.ENTER)
                client.awaitAt(TearsOfGuthix.CHASM_LANDING.x, TearsOfGuthix.CHASM_LANDING.z, 2)
                client.locOp(tunnelUp.id, tunnelUp.x, tunnelUp.z, TearsOfGuthix.ENTER)
                client.awaitAt(TearsOfGuthix.CAVES_LANDING.x, TearsOfGuthix.CAVES_LANDING.z, 0)
                client.tele(3239, 9524, 2)
                client.locOp(rocks.id, rocks.x, rocks.z, TearsOfGuthix.CLIMB)
                client.awaitAt(3241, 9524, 2)
                client.locOp(rocks.id, rocks.x, rocks.z, TearsOfGuthix.CLIMB)
                client.awaitAt(3239, 9524, 2)
            }
        }
    }

    @Test
    fun `Juna lets the player in, the blue streams fill the bowl, and the drink goes to the lowest skill once a week`(@TempDir dir: Path) {
        server(dir).use { server ->
            val tog = server.world.tearsOfGuthix
            var day = 20_000L
            tog.today = { day }
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val alice = server.world.playerNamed("alice")!!
                client.commandReply("varp ${Quests.VARP_QUEST_POINTS} $QUEST_POINTS") { it.contains("$QUEST_POINTS") }
                client.command("varbit ${TearsOfGuthix.VARBIT_JUNA} 2")
                client.tele(3251, 9516, 2)

                client.locOp(juna.id, juna.x, juna.z, TearsOfGuthix.STORY)
                client.box(JunaLines.STORIES)
                client.box(JunaLines.ENTERTAINED)
                client.box(JunaLines.COLLECT)
                client.awaitAt(TearsOfGuthix.START.x, TearsOfGuthix.START.z, 2)
                assertTrue(tog.isPlaying(alice))
                assertEquals(TearsOfGuthix.BOWL_STANCE, alice.stanceOverride)
                // A second start while playing changes nothing: the game's time isn't reset or doubled.
                val left = server.onTick { alice.vars.getVarbitIfKnown(TearsOfGuthix.VARBIT_COUNTDOWN) }
                server.onTick { tog.start(alice) }
                assertTrue(server.onTick { alice.vars.getVarbitIfKnown(TearsOfGuthix.VARBIT_COUNTDOWN) } <= left)

                // Collect from whichever wall runs blue, and again each time that stream moves on.
                val attack = alice.skills.tenths(Skill.ATTACK)
                var last = -1
                while (tog.isPlaying(alice)) {
                    val blue = TearsOfGuthix.WALLS.indices.firstOrNull { tog.streamAt(it) == true } ?: break
                    if (blue != last) {
                        val (x, z) = TearsOfGuthix.WALLS[blue]
                        client.locOp(TearsOfGuthix.WALL, x, z, TearsOfGuthix.COLLECT)
                        last = blue
                    }
                    client.awaitTicks(1)
                }
                client.awaitAt(TearsOfGuthix.END.x, TearsOfGuthix.END.z, 2)
                val skillLine = DialogueText.NONE[TearsLines.SKILL.getValue(Skill.ATTACK)]
                client.await("the drink's message") { client.messages().firstOrNull { it.text == skillLine } }
                val gained = alice.skills.tenths(Skill.ATTACK) - attack
                assertTrue(gained > 0, "some tears drunk")
                assertEquals(0, gained % 100, "10 XP a tear at Attack 1")
                assertTrue(gained / 100 <= QUEST_POINTS, "at most a tear a tick")
                assertEquals(null, alice.stanceOverride)
                assertEquals(day, tog.lastVisit(alice))

                // A start once the visit is recorded (a dialogue left open across the game) goes nowhere.
                server.onTick { tog.start(alice) }
                client.awaitTicks(2)
                assertFalse(tog.isPlaying(alice))
                assertEquals(TearsOfGuthix.END.x to TearsOfGuthix.END.z, alice.x to alice.z)

                // Back at once: not enough adventures since, and the week not up.
                client.locOp(juna.id, juna.x, juna.z, TearsOfGuthix.STORY)
                client.box(JunaLines.STORIES)
                client.awaitDialogue("no new stories") { it is DialogueView.Message && it.text.startsWith("[juna.no_new_stories_days") }
                client.continueDialogue()
                client.awaitDialogue("the XP due") { it is DialogueView.Message && it.text.startsWith("[juna.need_more") }
                client.continueDialogue()
                assertFalse(tog.isPlaying(alice))

                // A week on with 100,000 XP more, she lets them in again.
                day += 7
                client.command("addxp strength 100000")
                client.await("the XP") { if (tog.xpDue(alice) == 0L) Unit else null }
                assertEquals(0, tog.daysLeft(alice))
            }
        }
    }

    @Test
    fun `a walk ends collecting at a wall, the bowl kept`(@TempDir dir: Path) {
        server(dir).use { server ->
            val tog = server.world.tearsOfGuthix
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                val dave = server.world.playerNamed("dave")!!
                client.commandReply("varp ${Quests.VARP_QUEST_POINTS} 500") { it.contains("500") }
                client.command("varbit ${TearsOfGuthix.VARBIT_JUNA} 2")
                client.tele(3251, 9516, 2)
                client.locOp(juna.id, juna.x, juna.z, TearsOfGuthix.STORY)
                client.box(JunaLines.STORIES)
                client.box(JunaLines.ENTERTAINED)
                client.box(JunaLines.COLLECT)
                client.awaitAt(TearsOfGuthix.START.x, TearsOfGuthix.START.z, 2)

                val (x, z) = TearsOfGuthix.WALLS.first()
                client.locOp(TearsOfGuthix.WALL, x, z, TearsOfGuthix.COLLECT)
                client.await("collecting") { Unit.takeIf { tog.isCollecting(dave) } }
                client.walk(TearsOfGuthix.START.x, TearsOfGuthix.START.z)
                client.awaitAt(TearsOfGuthix.START.x, TearsOfGuthix.START.z, 2)
                client.await("collecting over") { Unit.takeIf { !tog.isCollecting(dave) } }
                val tears = tog.tears(dave)
                client.awaitTicks(3)
                assertEquals(tears, tog.tears(dave), "no tears from a wall left behind")
                assertTrue(tog.isPlaying(dave))

                // Each stream leaves its wall every 16 ticks, never landing back on it.
                var walls = tog.streamWalls()
                val since = IntArray(walls.size)
                repeat(4 * TearsOfGuthix.STREAM_TICKS) {
                    client.awaitTicks(1)
                    val now = tog.streamWalls()
                    for (i in now.indices) if (now[i] != walls[i]) since[i] = 0 else since[i]++
                    assertTrue(since.all { it <= TearsOfGuthix.STREAM_TICKS + 1 }, "a stream stayed on its wall: ${since.toList()}")
                    walls = now
                }
            }
        }
    }

    @Test
    fun `a player who logged out in the walls' room is put back by Juna, the game lost`(@TempDir dir: Path) {
        server(dir).use { server ->
            val tog = server.world.tearsOfGuthix
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("varp ${Quests.VARP_QUEST_POINTS} 500") { it.contains("500") }
                client.command("varbit ${TearsOfGuthix.VARBIT_JUNA} 2")
                client.tele(3251, 9516, 2)
                client.locOp(juna.id, juna.x, juna.z, TearsOfGuthix.STORY)
                client.box(JunaLines.STORIES)
                client.box(JunaLines.ENTERTAINED)
                client.box(JunaLines.COLLECT)
                client.awaitAt(TearsOfGuthix.START.x, TearsOfGuthix.START.z, 2)
            }
            // The grace after a dropped connection is counted in game ticks: step them with another player.
            server.login("carol").use { carol ->
                carol.await("the login rebuild") { carol.position }
                carol.commandReply("pause") { it.startsWith("Paused after tick") }
                carol.stepTicks(World.LINGER_TICKS.toInt() + 5)
                assertEquals(null, server.world.playerNamed("bob"), "bob's grace over")
                carol.commandReply("resume") { it.startsWith("Resumed") }
            }
            server.login("bob").use { client ->
                client.awaitAt(TearsOfGuthix.END.x, TearsOfGuthix.END.z, 2)
                val bob = server.world.playerNamed("bob")!!
                assertFalse(tog.isPlaying(bob))
                assertEquals(null, tog.lastVisit(bob), "no drink, no visit")
            }
        }
    }

    /** [block] run on the game's tick, its result back here. */
    private fun <T> TestServer.onTick(block: () -> T): T {
        val result = java.util.concurrent.CompletableFuture<T>()
        world.npcScripts.later(1) { result.complete(block()) }
        return result.get(10, java.util.concurrent.TimeUnit.SECONDS)
    }

    private companion object {
        const val TOG_VARP = 449
        const val JUNA_1OP = 28928
        const val JUNA_2OPS = 28929
        const val QUEST_POINTS = 40
        /** wall decoration, straight, as the map has the streams. */
        const val STREAM_SHAPE = 4
    }
}
