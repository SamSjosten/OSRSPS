package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.AgilityData
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Agility step 6 through the headless client: laps of the Gnome Stronghold Agility Course with its real locs (as the
 * 241 map places them, the log balance one level down off its bridge) and agility.json, on open ground (no cache
 * collision in tests). The rl-client script test-runner/examples/agility-gnome.json runs the real course.
 */
class HeadlessGnomeStrongholdTest {
    private class Obstacle(val id: Int, val name: String, val option: String, val angle: Int, val x: Int, val z: Int, val level: Int, val endX: Int, val endZ: Int, val endLevel: Int)

    private val course = listOf(
        Obstacle(23145, "Log balance", "Walk-across", 0, 2474, 3435, 0, 2474, 3429, 0),
        Obstacle(23134, "Obstacle net", "Climb-over", 0, 2473, 3425, 0, 2473, 3424, 1),
        Obstacle(23559, "Tree branch", "Climb", 0, 2473, 3422, 1, 2473, 3420, 2),
        Obstacle(23557, "Balancing rope", "Walk-on", 1, 2478, 3420, 2, 2483, 3420, 2),
        Obstacle(23560, "Tree branch", "Climb-down", 0, 2486, 3419, 2, 2487, 3420, 0),
        Obstacle(23135, "Obstacle net", "Climb-over", 2, 2485, 3426, 0, 2485, 3427, 0),
        Obstacle(23138, "Obstacle pipe", "Squeeze-through", 1, 2484, 3431, 0, 2484, 3437, 0),
    )

    /** The other loc types of the same obstacles: a middle piece of the rope, the second pipe, and another net. */
    private val others = listOf(
        Obstacle(23558, "Balancing rope", "Walk-on", 1, 2480, 3420, 2, 2483, 3420, 2),
        Obstacle(23135, "Obstacle net", "Climb-over", 2, 2483, 3426, 0, 2483, 3427, 0),
        Obstacle(23139, "Obstacle pipe", "Squeeze-through", 1, 2487, 3431, 0, 2487, 3437, 0),
    )

    private val text = AgilityData::class.java.getResource(AgilityData.RESOURCE)!!.readText()

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply { markLoaded(2474 shr 6, 3438 shr 6) },
            locTypes = LocTypes(
                (course + others).associate {
                    it.id to LocType(it.id, it.name, listOf(it.option, null, null, null, null), LocCollisionType(width = 1, length = 1, interactType = 0))
                },
            ),
            staticLocs = StaticLocs().apply { for (o in course + others) add(ZoneLoc(o.id, 10, o.angle, o.x, o.z, o.level)) },
            agilityData = AgilityData.parse(text), cycleMillis = millis,
        )
    }

    /** Crosses each obstacle, waiting for its landing tile and then its XP (the landing can come a tick before the end). */
    private fun HeadlessClient.cross(obstacles: List<Obstacle>) {
        for (obstacle in obstacles) {
            val xp = stat(AGILITY)?.xp ?: 0
            locOp(obstacle.id, obstacle.x, obstacle.z, 1)
            await("across ${obstacle.name} ${obstacle.id}") {
                position?.takeIf { it.x == obstacle.endX && it.z == obstacle.endZ && it.level == obstacle.endLevel }
            }
            await("the XP for ${obstacle.name} ${obstacle.id}") { stat(AGILITY)?.takeIf { it.xp > xp } }
        }
    }

    private fun HeadlessClient.start() {
        await("the login rebuild") { position }
        command("tele 2474 3438 0")
        await("at the course") { position?.takeIf { it.x == 2474 && it.z == 3438 } }
    }

    @Test
    fun `a lap at level 1 gives 110,5 XP with the 50 XP bonus, and counts`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.start()
                val before = client.await("the stats") { client.stat(AGILITY) }.xp
                client.cross(course)
                client.await("the lap count") { client.messages().firstOrNull { it.text == "[agility.gnome_laps count=1]" } }
                // 110.5 XP: the client sees whole XP.
                assertEquals(before + 110, client.await("the lap's XP") { client.stat(AGILITY)?.takeIf { it.xp >= before + 110 } }.xp)
            }
        }
    }

    @Test
    fun `the rope's other pieces, another net and the second pipe are the same obstacles, each crossed where it is clicked`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.start()
                client.cross(course.take(3) + others[0] + course[4] + others[1] + others[2])
                client.await("the lap count") { client.messages().firstOrNull { it.text == "[agility.gnome_laps count=1]" } }
            }
        }
    }

    private companion object {
        const val AGILITY = 16
    }
}
