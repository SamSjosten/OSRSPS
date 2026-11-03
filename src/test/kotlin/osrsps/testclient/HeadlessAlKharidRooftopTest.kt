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
 * Agility step 4 through the headless client: laps of the Al Kharid Rooftop Course with its real locs (as the 241 map
 * places them) and agility.json, on open ground (no cache collision in tests). The rl-client script
 * test-runner/examples/agility-alkharid.json runs the real course.
 */
class HeadlessAlKharidRooftopTest {
    private class Obstacle(val id: Int, val name: String, val option: String, val shape: Int, val angle: Int, val x: Int, val z: Int, val level: Int, val endX: Int, val endZ: Int, val endLevel: Int)

    private val course = listOf(
        Obstacle(11633, "Rough wall", "Climb", 4, 3, 3273, 3195, 0, 3273, 3192, 3),
        Obstacle(14398, "Tightrope", "Cross", LocShape.GROUND_DECOR, 2, 3272, 3181, 3, 3272, 3172, 3),
        Obstacle(14402, "Cable", "Swing-across", LocShape.CENTREPIECE_STRAIGHT, 1, 3269, 3166, 3, 3284, 3166, 3),
        Obstacle(14403, "Zip line", "Teeth-grip", LocShape.CENTREPIECE_STRAIGHT, 3, 3302, 3163, 3, 3315, 3163, 1),
        Obstacle(14404, "Tropical tree", "Swing-across", LocShape.CENTREPIECE_STRAIGHT, 0, 3318, 3166, 1, 3317, 3174, 2),
        Obstacle(11634, "Roof top beams", "Climb", 4, 1, 3316, 3179, 2, 3316, 3180, 3),
        Obstacle(14409, "Tightrope", "Cross", LocShape.GROUND_DECOR, 3, 3313, 3186, 3, 3305, 3186, 3),
        Obstacle(14399, "Gap", "Jump", LocShape.CENTREPIECE_DIAGONAL, 3, 3300, 3193, 3, 3299, 3194, 0),
    )

    private val text = AgilityData::class.java.getResource(AgilityData.RESOURCE)!!.readText()

    private fun server(dir: Path, agilityData: AgilityData = AgilityData.parse(text)) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(3273 shr 6, 3195 shr 6)
            },
            locTypes = LocTypes(
                course.associate {
                    it.id to LocType(it.id, it.name, listOf(it.option, null, null, null, null), LocCollisionType(width = 1, length = 1, interactType = 0))
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
        command("tele 3273 3196 0")
        await("at the course") { position?.takeIf { it.x == 3273 && it.z == 3196 } }
    }

    @Test
    fun `a lap at level 20 gives 216 XP and counts`(@TempDir dir: Path) {
        // Nothing fails here, so the lap is the whole course; the fails are the next test's.
        val sure = AgilityData.parse(text.replace(SUCCESS, "\"success\": [256, 256]"))
        server(dir, sure).use { server ->
            server.login("alice").use { client ->
                client.start()
                client.command("setlevel agility 20")
                client.await("level 20") { client.stat(AGILITY)?.takeIf { it.level == 20 } }
                val before = client.stat(AGILITY)!!.xp
                client.lap()
                client.await("the lap count") { client.messages().firstOrNull { it.text == "[agility.alkharid_laps count=1]" } }
                assertEquals(before + 216, client.await("the lap's XP") { client.stat(AGILITY)?.takeIf { it.xp >= before + 216 } }.xp)
            }
        }
    }

    @Test
    fun `below level 20 the course says so, and Tightrope 1 can fail for 1 to 5 damage and no XP`(@TempDir dir: Path) {
        val falls = AgilityData.parse(text.replace(SUCCESS, "\"success\": [-1, -1]"))
        server(dir, falls).use { server ->
            server.login("bob").use { client ->
                client.start()
                client.locOp(11633, 3273, 3195, 1)
                client.await("the level message") { client.messages().firstOrNull { it.text == "[agility.alkharid_level level=20]" } }
                client.command("setlevel agility 20")
                client.await("level 20") { client.stat(AGILITY)?.takeIf { it.level == 20 } }
                client.locOp(11633, 3273, 3195, 1)
                client.await("on the roof") { client.position?.takeIf { it.level == 3 } }
                // Level 20's 4,470 XP and the wall's 12.
                val before = client.await("the wall's XP") { client.stat(AGILITY)?.takeIf { it.xp == 4_482 } }.xp
                client.locOp(14398, 3272, 3181, 1)
                client.await("fallen to the street") { client.position?.takeIf { it.level == 0 && it.x == 3273 && it.z == 3178 } }
                val hp = client.await("the hit") { client.stat(HITPOINTS)?.takeIf { it.level < 10 } }.level
                assertTrue(hp in 5..9, "1 to 5 damage, left at $hp")
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
