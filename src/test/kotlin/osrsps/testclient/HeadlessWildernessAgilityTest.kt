package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.content.WildernessAgility
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Wilderness step W6.4 through the headless client: the Wilderness Agility Course with its real locs and agility.json,
 * on open ground (no cache collision in tests). A lap, a fall from the ropeswing into the pit below, the way-in door's
 * level, the dungeon ladder and the dispenser's tickets. The rl-client script test-runner/examples/wilderness-agility.json
 * runs the real course.
 */
class HeadlessWildernessAgilityTest {
    private class Loc(val id: Int, val name: String, val option: String, val shape: Int, val angle: Int, val x: Int, val z: Int, val ops: List<String?>? = null)

    private val locs = listOf(
        Loc(DOOR, "Door", "Open", LocShape.WALL_STRAIGHT, 3, 2998, 3917),
        Loc(GATE_LEFT, "Gate", "Open", LocShape.WALL_STRAIGHT, 3, 2998, 3931),
        Loc(GATE_RIGHT, "Gate", "Open", LocShape.WALL_STRAIGHT, 3, 2997, 3931),
        Loc(PIPE, "Obstacle pipe", "Squeeze-through", LocShape.CENTREPIECE_STRAIGHT, 3, 3004, 3938),
        Loc(PIPE, "Obstacle pipe", "Squeeze-through", LocShape.CENTREPIECE_STRAIGHT, 1, 3004, 3948),
        Loc(ROPESWING, "Ropeswing", "Swing-on", LocShape.CENTREPIECE_STRAIGHT, 2, 3005, 3952),
        Loc(STONE, "Stepping stone", "Cross", LocShape.GROUND_DECOR, 0, 3001, 3960),
        Loc(LOG, "Log balance", "Walk-across", LocShape.GROUND_DECOR, 3, 3001, 3945),
        Loc(ROCKS, "Rocks", "Climb", LocShape.GROUND_DECOR, 3, 2993, 3936),
        Loc(ROCKS, "Rocks", "Climb", LocShape.GROUND_DECOR, 2, 2994, 3936),
        Loc(ROCKS, "Rocks", "Climb", LocShape.GROUND_DECOR, 1, 2995, 3936),
        Loc(WildernessAgility.DISPENSER, "Agility dispenser", "Tag", LocShape.CENTREPIECE_STRAIGHT, 1, 3005, 3936, listOf("Tag", "Pay", "Redeem", null, null)),
        Loc(WildernessAgility.LADDER_DOWN, "Ladder", "Climb-down", LocShape.CENTREPIECE_STRAIGHT, 1, 3005, 3963),
        Loc(LADDER_UP, "Ladder", "Climb-up", LocShape.CENTREPIECE_STRAIGHT, 0, 3005, 10363),
    )

    private fun server(dir: Path, seed: Long = 7) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(2998 shr 6, 3917 shr 6)
                markLoaded(3005 shr 6, 3936 shr 6)
                markLoaded(3005 shr 6, 10357 shr 6)
            },
            locTypes = LocTypes(
                locs.associate {
                    it.id to LocType(it.id, it.name, it.ops ?: listOf(it.option, null, null, null, null), LocCollisionType(width = 1, length = 1, interactType = 0))
                },
            ),
            staticLocs = StaticLocs().apply { for (l in locs) add(ZoneLoc(l.id, l.shape, l.angle, l.x, l.z, 0)) },
            objTypes = ObjTypes.of(ObjType(WildernessAgility.TICKET, "Wilderness agility ticket", stackable = true)),
            random = GameRandom(seed), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.ready(level: Int) {
        await("the login rebuild") { position }
        commandReply("keys on") { it == "Dialogue shows keys" }
        command("setlevel agility $level")
        await("Agility $level") { stat(AGILITY)?.takeIf { it.level == level } }
    }

    /** Crosses an obstacle and waits for its landing tile and then its XP, the script's last act. */
    private fun HeadlessClient.cross(id: Int, x: Int, z: Int, endX: Int, endZ: Int) {
        val xp = stat(AGILITY)?.xp ?: 0
        locOp(id, x, z, 1)
        await("across $id to $endX, $endZ") { position?.takeIf { it.x == endX && it.z == endZ } }
        await("the XP for $id") { stat(AGILITY)?.takeIf { it.xp > xp } }
    }

    private fun HeadlessClient.lap() {
        cross(PIPE, 3004, 3938, 3004, 3950)
        cross(ROPESWING, 3005, 3952, 3005, 3958)
        cross(STONE, 3001, 3960, 2996, 3960)
        cross(LOG, 3001, 3945, 2994, 3945)
        cross(ROCKS, 2994, 3936, 2994, 3933)
    }

    private fun HeadlessClient.tickets() = inventory(BACKPACK).sumOf { if (it?.id == WildernessAgility.TICKET) it.count else 0 }

    @Test
    fun `a lap gives 571 XP and counts, and the dispenser gives one ticket for it and redeems it for 200 XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("vera").use { client ->
                client.ready(99)
                assertTrue(Wilderness.level(3004, 3937) in 50..56)
                val player = server.world.onlinePlayers.single { it.username == "vera" }
                val before = player.skills.tenths(Skill.AGILITY)
                client.tele(3004, 3937)
                client.lap()
                client.await("the lap count") { client.messages().firstOrNull { it.text == "[agility.wilderness_laps count=1]" } }
                assertEquals(5714, player.skills.tenths(Skill.AGILITY) - before, "12.5 + 20 + 20 + 20 + 498.9")
                assertTrue(client.messages().any { it.text == "[agility.wilderness_ropeswing]" })
                assertTrue(client.messages().any { it.text == "[agility.wilderness_stones_start]" })
                assertTrue(client.messages().any { it.text == "[agility.wilderness_log]" })

                player.runEnergy = 0
                client.locOp(WildernessAgility.DISPENSER, 3005, 3936, 1)
                client.await("a ticket") { Unit.takeIf { client.tickets() == 1 } }
                client.await("the ticket message") { client.messages().firstOrNull { it.text == "[wilderness_agility.ticket]" } }
                assertEquals(osrsps.game.Movement.MAX_ENERGY, player.runEnergy, "the tag restores run energy")
                client.locOp(WildernessAgility.DISPENSER, 3005, 3936, 1)
                client.awaitTicks(3)
                assertEquals(1, client.tickets(), "one ticket a lap")

                val held = player.skills.tenths(Skill.AGILITY)
                client.locOp(WildernessAgility.DISPENSER, 3005, 3936, 3)
                client.await("the ticket redeemed") { client.messages().firstOrNull { it.text == "[wilderness_agility.redeemed count=1 xp=200]" } }
                // The backpack's update can come a packet after the message.
                client.await("the ticket gone") { Unit.takeIf { client.tickets() == 0 } }
                assertEquals(2000, player.skills.tenths(Skill.AGILITY) - held)
            }
        }
    }

    @Test
    fun `frozen, an obstacle clicked from beside it but off its start is refused, and from its start goes ahead`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("wren").use { client ->
                client.ready(99)
                val player = server.world.onlinePlayers.single { it.username == "wren" }
                // Exploit review round 35, R35A-6: the walk to an obstacle is forced, which a freeze didn't stop.
                client.tele(3003, 3938)
                player.freezeTicks = 50
                client.locOp(PIPE, 3004, 3938, 1)
                client.await("the magical force") { client.messages().firstOrNull { it.text == "[pvp.magical_force]" } }
                client.awaitTicks(3)
                assertEquals(Coord(3003, 3938, 0), client.position, "still beside the pipe, not walked round to its start")
                player.freezeTicks = 0
                client.tele(3004, 3937)
                player.freezeTicks = 50
                client.cross(PIPE, 3004, 3938, 3004, 3950)
            }
        }
    }

    @Test
    fun `a ropeswing fall lands in the pit for 15 percent of hitpoints plus 1, and a lap without the swing doesn't count`(@TempDir dir: Path) {
        server(dir, seed = 11).use { server ->
            server.login("wren").use { client ->
                client.ready(52)
                client.command("setlevel hitpoints 60")
                client.await("60 hitpoints") { client.stat(HITPOINTS)?.takeIf { it.level == 60 } }
                // Seeded: swing until the roll fails (88.7% at 52), at most twenty tries.
                var fell = false
                for (attempt in 1..20) {
                    client.tele(3005, 3953)
                    client.locOp(ROPESWING, 3005, 3952, 1)
                    val landed = client.await("across or down") { client.position?.takeIf { it.z == 3958 || it.z == 10357 } }
                    if (landed.z == 10357) {
                        fell = true
                        break
                    }
                }
                assertTrue(fell, "a fall within twenty swings")
                assertEquals(Coord(3004, 10357, 0), client.position)
                client.await("the fall's damage") { client.stat(HITPOINTS)?.takeIf { it.level == 60 - (60 * 15 / 100 + 1) } }
                client.await("the fall message") { client.messages().firstOrNull { it.text == "[agility.wilderness_ropeswing_fail]" } }

                // The ladder up from the pit and back down.
                client.locOp(LADDER_UP, 3005, 10363, 1)
                client.await("up the ladder") { client.position?.takeIf { it.z < 6400 } }
                client.locOp(WildernessAgility.LADDER_DOWN, 3005, 3963, 1)
                client.await("down the ladder") { client.position?.takeIf { it == Coord(3005, 10362, 0) } }

                // A lap from the pipe that skips the ropeswing: the rocks give nothing and the lap doesn't count.
                client.command("setlevel agility 99")
                client.await("Agility 99") { client.stat(AGILITY)?.takeIf { it.level == 99 } }
                client.tele(3004, 3937)
                client.cross(PIPE, 3004, 3938, 3004, 3950)
                client.cross(STONE, 3001, 3960, 2996, 3960)
                client.cross(LOG, 3001, 3945, 2994, 3945)
                val xp = client.stat(AGILITY)!!.xp
                client.locOp(ROCKS, 2994, 3936, 1)
                client.await("down the rocks") { client.position?.takeIf { it == Coord(2994, 3933, 0) } }
                client.awaitTicks(3)
                assertEquals(xp, client.stat(AGILITY)!!.xp)
                assertTrue(client.messages().none { it.text.startsWith("[agility.wilderness_laps") })
                client.locOp(WildernessAgility.DISPENSER, 3005, 3936, 1)
                client.awaitTicks(3)
                assertEquals(0, client.tickets(), "no lap, no ticket")
            }
        }
    }

    @Test
    fun `the way-in door needs Agility 52 and leads along the ridge into the course, and the gates lead back out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("yara").use { client ->
                client.ready(51)
                client.tele(2998, 3915)
                client.locOp(DOOR, 2998, 3917, 1)
                client.await("the level message") { client.messages().firstOrNull { it.text == "[agility.level level=52]" } }
                client.command("setlevel agility 52")
                client.await("Agility 52") { client.stat(AGILITY)?.takeIf { it.level == 52 } }
                client.locOp(DOOR, 2998, 3917, 1)
                client.await("in the course") { client.position?.takeIf { it == Coord(2998, 3931, 0) } }
                client.locOp(GATE_RIGHT, 2997, 3931, 1)
                client.await("back out") { client.position?.takeIf { it == Coord(2998, 3916, 0) } }
            }
        }
    }

    @Test
    fun `redeeming more tickets at once gives more XP for each`() {
        assertEquals(2000L, WildernessAgility.redeemXp(10))
        assertEquals(11 * 210L, WildernessAgility.redeemXp(11))
        assertEquals(100 * 220L, WildernessAgility.redeemXp(100))
        assertEquals(101 * 230L, WildernessAgility.redeemXp(101))
    }

    private companion object {
        const val DOOR = 23555
        const val GATE_LEFT = 23552
        const val GATE_RIGHT = 23554
        const val PIPE = 23137
        const val ROPESWING = 23132
        const val STONE = 23556
        const val LOG = 23542
        const val ROCKS = 23640
        const val LADDER_UP = 17385
        const val BACKPACK = 93
        const val AGILITY = 16
        const val HITPOINTS = 3
    }
}
