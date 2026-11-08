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
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Agility step 5 through the headless client: laps of the Varrock Rooftop Course with its real locs (as the 241 map
 * places them) and agility.json, on open ground (no cache collision in tests) but for the missing floor around the wall,
 * blocked as the map has it. The rl-client script
 * test-runner/examples/agility-varrock.json runs the real course.
 */
class HeadlessVarrockRooftopTest {
    private class Obstacle(
        val id: Int, val name: String, val option: String, val shape: Int, val angle: Int, val x: Int, val z: Int, val level: Int,
        val endX: Int, val endZ: Int, val endLevel: Int, val width: Int = 1, val length: Int = 1,
    )

    private val course = listOf(
        Obstacle(14412, "Rough wall", "Climb", 5, 0, 3221, 3414, 0, 3219, 3414, 3),
        Obstacle(14413, "Clothes line", "Cross", LocShape.CENTREPIECE_STRAIGHT, 0, 3213, 3414, 3, 3208, 3414, 3),
        Obstacle(14414, "Gap", "Leap", LocShape.CENTREPIECE_STRAIGHT, 0, 3200, 3416, 3, 3197, 3416, 1),
        Obstacle(14832, "Wall", "Balance", LocShape.CENTREPIECE_STRAIGHT, 0, 3191, 3415, 1, 3192, 3406, 3, width = 2, length = 2),
        Obstacle(14833, "Gap", "Leap", LocShape.CENTREPIECE_STRAIGHT, 0, 3193, 3401, 3, 3193, 3398, 3),
        Obstacle(14834, "Gap", "Leap", LocShape.CENTREPIECE_STRAIGHT, 0, 3209, 3397, 3, 3218, 3397, 3),
        Obstacle(14835, "Gap", "Leap", LocShape.CENTREPIECE_STRAIGHT, 0, 3233, 3402, 3, 3236, 3403, 3),
        Obstacle(14836, "Ledge", "Hurdle", LocShape.CENTREPIECE_STRAIGHT, 0, 3236, 3409, 3, 3236, 3410, 3),
        Obstacle(14841, "Edge", "Jump-off", LocShape.CENTREPIECE_STRAIGHT, 0, 3236, 3416, 3, 3236, 3417, 0),
    )

    private val text = AgilityData::class.java.getResource(AgilityData.RESOURCE)!!.readText()

    private fun server(dir: Path, agilityData: AgilityData = AgilityData.parse(text)) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(3190 shr 6, 3414 shr 6)
                markLoaded(3221 shr 6, 3414 shr 6)
                // The roof's missing floor between the gap's landing and the wall, as the 241 map has it: the 2 by 2
                // wall is out of reach from the landing's roof, so it is used from its start (fromStart).
                for (x in 3189..3193) for (z in 3413..3418) add(x, z, 1, CollisionFlag.FLOOR)
            },
            locTypes = LocTypes(
                course.associate {
                    it.id to LocType(it.id, it.name, listOf(it.option, null, null, null, null), LocCollisionType(width = it.width, length = it.length, interactType = 0))
                },
            ),
            staticLocs = StaticLocs().apply { for (o in course) add(ZoneLoc(o.id, o.shape, o.angle, o.x, o.z, o.level)) },
            agilityData = agilityData, cycleMillis = millis,
        )
    }

    /**
     * The landing tile can arrive a tick before the obstacle's script ends, so each obstacle also waits for its XP;
     * clicking the next one earlier would land while the player is still busy.
     */
    private fun HeadlessClient.lap() {
        for (obstacle in course) {
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
        command("tele 3222 3414 0")
        await("at the course") { position?.takeIf { it.x == 3222 && it.z == 3414 } }
    }

    @Test
    fun `a lap at level 30 gives 269,7 XP and counts`(@TempDir dir: Path) {
        // Nothing fails here, so the lap is the whole course; the fails are the next test's.
        val sure = AgilityData.parse(text.replace(SUCCESS, "\"success\": [256, 256]"))
        server(dir, sure).use { server ->
            server.login("alice").use { client ->
                client.start()
                client.command("setlevel agility 30")
                client.await("level 30") { client.stat(AGILITY)?.takeIf { it.level == 30 } }
                val before = client.stat(AGILITY)!!.xp
                client.lap()
                client.await("the lap count") { client.messages().firstOrNull { it.text == "[agility.varrock_laps count=1]" } }
                // 269.7 XP: the client sees whole XP, and 13,363 plus 269.7 is 13,632.7.
                assertEquals(before + 269, client.await("the lap's XP") { client.stat(AGILITY)?.takeIf { it.xp >= before + 269 } }.xp)
            }
        }
    }

    @Test
    fun `below level 30 the course says so, and the clothes line can fail for 3 to 8 damage and no XP`(@TempDir dir: Path) {
        val falls = AgilityData.parse(text.replace(SUCCESS, "\"success\": [-1, -1]"))
        server(dir, falls).use { server ->
            server.login("bob").use { client ->
                client.start()
                client.command("setlevel hitpoints 20")
                client.await("20 hitpoints") { client.stat(HITPOINTS)?.takeIf { it.level == 20 } }
                client.locOp(14412, 3221, 3414, 1)
                client.await("the level message") { client.messages().firstOrNull { it.text == "[agility.varrock_level level=30]" } }
                client.command("setlevel agility 30")
                client.await("level 30") { client.stat(AGILITY)?.takeIf { it.level == 30 } }
                client.locOp(14412, 3221, 3414, 1)
                client.await("on the roof") { client.position?.takeIf { it.level == 3 } }
                // Level 30's 13,363 XP and the wall's 13.5.
                val before = client.await("the wall's XP") { client.stat(AGILITY)?.takeIf { it.xp == 13_376 } }.xp
                client.locOp(14413, 3213, 3414, 1)
                client.await("fallen to the street") { client.position?.takeIf { it.level == 0 && it.x == 3211 && it.z == 3415 } }
                val hp = client.await("the hit") { client.stat(HITPOINTS)?.takeIf { it.level < 20 } }.level
                assertTrue(hp in 12..17, "3 to 8 damage, left at $hp")
                client.awaitTicks(2)
                assertEquals(before, client.stat(AGILITY)!!.xp)
            }
        }
    }

    private companion object {
        const val AGILITY = 16
        const val HITPOINTS = 3

        /** The two fallible obstacles' success pair, as agility.json writes it. */
        val SUCCESS = Regex("\"success\": \\[\\s*225,\\s*255\\s*]")
    }
}
