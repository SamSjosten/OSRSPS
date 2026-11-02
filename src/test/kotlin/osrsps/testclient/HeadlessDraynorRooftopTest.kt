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
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Agility step 2 through the headless client: laps of the Draynor Village Rooftop Course with its real locs and
 * agility.json, on open ground (no cache collision in tests). The rl-client script
 * test-runner/examples/agility-draynor.json runs the real course.
 */
class HeadlessDraynorRooftopTest {
    private class Obstacle(val id: Int, val name: String, val option: String, val shape: Int, val angle: Int, val x: Int, val z: Int, val level: Int, val endX: Int, val endZ: Int)

    private val course = listOf(
        Obstacle(11404, "Rough wall", "Climb", 5, 0, 3103, 3279, 0, 3102, 3279),
        Obstacle(11405, "Tightrope", "Cross", LocShape.GROUND_DECOR, 3, 3098, 3277, 3, 3090, 3276),
        Obstacle(11406, "Tightrope", "Cross", LocShape.GROUND_DECOR, 2, 3092, 3276, 3, 3092, 3266),
        Obstacle(11430, "Narrow wall", "Balance", LocShape.CENTREPIECE_STRAIGHT, 0, 3089, 3264, 3, 3088, 3261),
        Obstacle(11630, "Wall", "Jump-up", LocShape.CENTREPIECE_STRAIGHT, 0, 3088, 3256, 3, 3088, 3255),
        Obstacle(11631, "Gap", "Jump", LocShape.CENTREPIECE_STRAIGHT, 0, 3095, 3255, 3, 3096, 3256),
        Obstacle(11632, "Crate", "Climb-down", LocShape.CENTREPIECE_STRAIGHT, 0, 3102, 3261, 3, 3103, 3261),
    )

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(3088 shr 6, 3255 shr 6)
                markLoaded(3088 shr 6, 3279 shr 6)
            },
            locTypes = LocTypes(
                course.associate {
                    it.id to LocType(it.id, it.name, listOf(it.option, null, null, null, null), LocCollisionType(width = 1, length = 1, interactType = 0))
                },
            ),
            staticLocs = StaticLocs().apply { for (o in course) add(ZoneLoc(o.id, o.shape, o.angle, o.x, o.z, o.level)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.lap(skip: Int = 0) {
        for (obstacle in course.drop(skip)) {
            locOp(obstacle.id, obstacle.x, obstacle.z, 1)
            await("across ${obstacle.name} ${obstacle.id}") { position?.takeIf { it.x == obstacle.endX && it.z == obstacle.endZ } }
        }
    }

    @Test
    fun `a lap gives 120 XP and counts, and a second lap counts 2`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 3104 3279 0")
                client.await("at the course") { client.position?.takeIf { it.x == 3104 } }
                client.lap()
                assertEquals(0, client.position!!.level)
                assertEquals(120, client.await("the lap's XP") { client.stat(AGILITY)?.takeIf { it.xp >= 120 } }.xp)
                client.await("the lap count") { client.messages().firstOrNull { it.text == "[agility.draynor_laps count=1]" } }
                client.command("tele 3104 3279 0")
                client.await("back at the start") { client.position?.takeIf { it.x == 3104 } }
                client.lap()
                client.await("the second lap count") { client.messages().firstOrNull { it.text == "[agility.draynor_laps count=2]" } }
                assertEquals(240, client.await("two laps' XP") { client.stat(AGILITY)?.takeIf { it.xp >= 240 } }.xp)
            }
        }
    }

    @Test
    fun `a lap that skips the first obstacles gives their XP but doesn't count`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 3099 3277 3")
                client.await("on the first roof") { client.position?.takeIf { it.x == 3099 && it.level == 3 } }
                client.lap(skip = 1)
                assertEquals(115, client.await("the XP") { client.stat(AGILITY)?.takeIf { it.xp >= 115 } }.xp)
                client.awaitTicks(3)
                assertTrue(client.messages().none { it.text.startsWith("[agility.draynor_laps") })
            }
        }
    }

    private companion object {
        const val AGILITY = 16
    }
}
