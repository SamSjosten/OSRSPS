package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.AgilityData
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * The Edgeville Dungeon's obstacle pipes through the headless client, with their locs where the 242 map has them and
 * agility.json: the pipe east to the Varrock Sewers (two locs either side of the wall) and the south pipe, each crossed
 * there and back; below its level a pipe says so; getting stuck still takes the player through, for 1 to 6 damage, the
 * message and the XP, and at 6 hitpoints nobody gets stuck. The rl-client script test-runner/examples/misthalin-fixes-7.json
 * does the same on the live cache.
 */
class HeadlessEdgevilleDungeonPipesTest {
    private val text = AgilityData::class.java.getResource(AgilityData.RESOURCE)!!.readText()

    private fun pipe(id: Int) = LocType(id, "Obstacle pipe", listOf("Squeeze-through", null, null, null, null), LocCollisionType(width = 2, length = 1, interactType = 2))

    private fun server(dir: Path, data: AgilityData) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3150 shr 6, 9906 shr 6); markLoaded(3122 shr 6, 9881 shr 6) },
            locTypes = LocTypes(mapOf(NORTH to pipe(NORTH), SOUTH to pipe(SOUTH))),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(NORTH, LocShape.CENTREPIECE_STRAIGHT, 2, 3150, 9906, 0))
                add(ZoneLoc(NORTH, LocShape.CENTREPIECE_STRAIGHT, 0, 3153, 9906, 0))
                add(ZoneLoc(SOUTH, LocShape.CENTREPIECE_STRAIGHT, 1, 3122, 9881, 0))
            },
            agilityData = data, cycleMillis = millis,
        )
    }

    private fun data(success: String) = AgilityData.parse(text.replace(SUCCESS, "\"success\": $success"))

    private fun HeadlessClient.teleTo(x: Int, z: Int) {
        command("tele $x $z 0")
        await("at $x, $z") { position?.takeIf { it.x == x && it.z == z && it.level == 0 } }
    }

    private fun HeadlessClient.crossTo(id: Int, x: Int, z: Int, to: Pair<Int, Int>) {
        locOp(id, x, z, 1)
        await("across to $to by $id") { position?.takeIf { it.x == to.first && it.z == to.second && it.level == 0 } }
        awaitTicks(2)
        assertEquals(to, position!!.let { it.x to it.z }, "where $id was crossed to")
    }

    private fun HeadlessClient.level(skill: Int, level: Int) {
        command("setlevel ${if (skill == AGILITY) "agility" else "hitpoints"} $level")
        await("level $level") { stat(skill)?.takeIf { it.level == level } }
    }

    @Test
    fun `both pipes go there and back for 10 XP a crossing, and say so below their level`(@TempDir dir: Path) {
        server(dir, data("[256, 256]")).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.level(AGILITY, 50)
                client.teleTo(3149, 9906)
                client.locOp(NORTH, 3150, 9906, 1)
                client.await("the level message") { client.messages().firstOrNull { it.text == "[agility.edgeville_dungeon_pipe_level level=51]" } }
                client.level(AGILITY, 59)
                client.teleTo(3122, 9880)
                client.locOp(SOUTH, 3122, 9881, 1)
                client.await("the south level message") { client.messages().firstOrNull { it.text == "[agility.edgeville_dungeon_south_pipe_level level=60]" } }
                assertEquals(9880, client.position!!.z)

                client.level(AGILITY, 60)
                val before = client.stat(AGILITY)!!.xp
                client.teleTo(3149, 9906)
                client.crossTo(NORTH, 3150, 9906, 3155 to 9906)
                client.crossTo(NORTH, 3153, 9906, 3149 to 9906)
                client.teleTo(3122, 9880)
                client.crossTo(SOUTH, 3122, 9881, 3122 to 9883)
                client.crossTo(SOUTH, 3122, 9881, 3122 to 9880)
                assertEquals(before + 40, client.await("the pipes' XP") { client.stat(AGILITY)?.takeIf { it.xp >= before + 40 } }.xp)
            }
        }
    }

    @Test
    fun `stuck in a pipe the player still gets through, hurt, and at 6 hitpoints nobody gets stuck`(@TempDir dir: Path) {
        server(dir, data("[-1, -1]")).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.level(AGILITY, 60)
                client.level(HITPOINTS, 20)
                val before = client.stat(AGILITY)!!.xp
                client.teleTo(3122, 9880)
                client.crossTo(SOUTH, 3122, 9881, 3122 to 9883)
                client.await("the message") { client.messages().firstOrNull { it.text == "[agility.pipe_stuck]" } }
                val hp = client.await("the hit") { client.stat(HITPOINTS)?.takeIf { it.level < 20 } }.level
                assertTrue(hp in 14..19, "1 to 6 damage, left at $hp")
                assertEquals(before + 10, client.await("the XP") { client.stat(AGILITY)?.takeIf { it.xp > before } }.xp)

                client.level(HITPOINTS, 6)
                val stuck = client.messages().count { it.text == "[agility.pipe_stuck]" }
                client.teleTo(3149, 9906)
                client.crossTo(NORTH, 3150, 9906, 3155 to 9906)
                assertEquals(6, client.stat(HITPOINTS)!!.level)
                assertEquals(stuck, client.messages().count { it.text == "[agility.pipe_stuck]" })
            }
        }
    }

    private companion object {
        const val AGILITY = 16
        const val HITPOINTS = 3
        const val NORTH = 16511
        const val SOUTH = 62409

        /** The pipes' success pair, as agility.json writes it. */
        val SUCCESS = Regex("\"success\": \\[\\s*0,\\s*254\\s*]")
    }
}
