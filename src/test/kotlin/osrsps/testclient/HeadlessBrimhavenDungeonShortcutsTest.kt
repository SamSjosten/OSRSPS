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

/**
 * Karamja step K3.3's Brimhaven Dungeon shortcuts through the headless client, with agility.json and their locs where
 * the 242 map has them: the western stepping stones, the log, the black demons' pipe, both pairs of eastern stepping
 * stones (one loc type, told apart by tile) and the vine up to the upper level, each crossed there and back (the
 * entrance pipe is HeadlessBrimhavenDungeonTest's); and the western stones failing both ways, back to where the player
 * jumped from for 1 to 3 damage and no XP. test-runner/examples/brimhaven-dungeon-shortcuts.json does the crossings on
 * the real map.
 */
class HeadlessBrimhavenDungeonShortcutsTest {
    /** A loc: its type, name and option, and where and how the map places it. */
    private class Placed(val id: Int, val name: String, val option: String, val shape: Int, val angle: Int, val x: Int, val z: Int, val level: Int = 0)

    /** A crossing: from a tile, by a placed loc, to a tile. */
    private class Crossing(val from: Triple<Int, Int, Int>, val by: Placed, val to: Triple<Int, Int, Int>)

    private val westNear = Placed(21738, "Stepping stone", "Jump-from", 22, 3, 2649, 9561)
    private val westFar = Placed(21739, "Stepping stone", "Jump-from", 22, 3, 2647, 9558)
    private val logWest = Placed(20882, "Log balance", "Walk-across", 22, 1, 2683, 9506)
    private val logEast = Placed(20884, "Log balance", "Walk-across", 22, 3, 2686, 9506)
    private val pipeNorth = Placed(21727, "Pipe", "Squeeze-through", 10, 1, 2698, 9498)
    private val pipeSouth = Placed(60840, "Pipe", "Squeeze-through", 10, 3, 2698, 9493)
    private val northA = Placed(19040, "Stepping stones", "Cross", 22, 1, 2684, 9548)
    private val northB = Placed(19040, "Stepping stones", "Cross", 22, 2, 2688, 9547)
    private val southA = Placed(19040, "Stepping stones", "Cross", 22, 3, 2695, 9531)
    private val southB = Placed(19040, "Stepping stones", "Cross", 22, 0, 2696, 9527)
    private val vineBottom = Placed(26880, "Vine", "Climb", 0, 2, 2672, 9583)
    private val vineTop = Placed(26882, "Vine", "Climb", 22, 2, 2671, 9583, 2)

    private val placed = listOf(westNear, westFar, logWest, logEast, pipeNorth, pipeSouth, northA, northB, southA, southB, vineBottom, vineTop)

    private val crossings = listOf(
        Crossing(Triple(2649, 9562, 0), westNear, Triple(2647, 9557, 0)),
        Crossing(Triple(2647, 9557, 0), westFar, Triple(2649, 9562, 0)),
        Crossing(Triple(2682, 9506, 0), logWest, Triple(2687, 9506, 0)),
        Crossing(Triple(2687, 9506, 0), logEast, Triple(2682, 9506, 0)),
        Crossing(Triple(2698, 9500, 0), pipeNorth, Triple(2698, 9492, 0)),
        Crossing(Triple(2698, 9492, 0), pipeSouth, Triple(2698, 9500, 0)),
        Crossing(Triple(2682, 9548, 0), northA, Triple(2690, 9547, 0)),
        Crossing(Triple(2690, 9547, 0), northB, Triple(2682, 9548, 0)),
        Crossing(Triple(2697, 9525, 0), southB, Triple(2695, 9533, 0)),
        Crossing(Triple(2695, 9533, 0), southA, Triple(2697, 9525, 0)),
        Crossing(Triple(2673, 9583, 0), vineBottom, Triple(2671, 9583, 2)),
        Crossing(Triple(2671, 9583, 2), vineTop, Triple(2673, 9583, 0)),
    )

    private val text = AgilityData::class.java.getResource(AgilityData.RESOURCE)!!.readText().also {
        check(WEST_SUCCESS_TAIL in it) { "agility.json no longer writes the western stones' success pair as this test expects" }
    }

    private fun server(dir: Path, agilityData: AgilityData) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply { for (p in placed) markLoaded(p.x shr 6, p.z shr 6) },
            locTypes = LocTypes(placed.associate { it.id to LocType(it.id, it.name, listOf(it.option, null, null, null, null), LocCollisionType(width = 1, length = 1, interactType = 0)) }),
            staticLocs = StaticLocs().apply { for (p in placed) add(ZoneLoc(p.id, p.shape, p.angle, p.x, p.z, p.level)) },
            agilityData = agilityData, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.at(tile: Triple<Int, Int, Int>) = position?.takeIf { it.x == tile.first && it.z == tile.second && it.level == tile.third }

    private fun HeadlessClient.teleTo(tile: Triple<Int, Int, Int>) {
        command("tele ${tile.first} ${tile.second} ${tile.third}")
        await("at $tile") { at(tile) }
    }

    @Test
    fun `each dungeon shortcut goes there and back, the eastern stones by the tile clicked`(@TempDir dir: Path) {
        val sure = AgilityData.parse(text.replace(WEST_SUCCESS_TAIL, WEST_SUCCESS_TAIL.replace("225, 255", "256, 256")))
        server(dir, sure).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel agility 87")
                val before = client.await("level 87") { client.stat(AGILITY)?.takeIf { it.level == 87 } }.xp
                for (c in crossings) {
                    client.teleTo(c.from)
                    client.locOp(c.by.id, c.by.x, c.by.z, 1)
                    client.await("across to ${c.to} by ${c.by.id} at ${c.by.x}, ${c.by.z}") { client.at(c.to) }
                    client.awaitTicks(2)
                    assertEquals(c.to, client.position!!.let { Triple(it.x, it.z, it.level) }, "still across by ${c.by.id}")
                }
                // 7.5 XP twice on the western stones, 10 twice on the log and twice through the pipe: 55 XP.
                assertEquals(before + 55, client.await("the shortcuts' XP") { client.stat(AGILITY)?.takeIf { it.xp >= before + 55 } }.xp)
            }
        }
    }

    @Test
    fun `the western stones can fail either way, back where the player jumped from for 1 to 3 damage and no XP`(@TempDir dir: Path) {
        val falls = AgilityData.parse(text.replace(WEST_SUCCESS_TAIL, WEST_SUCCESS_TAIL.replace("225, 255", "-1, -1")))
        server(dir, falls).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel agility 12")
                val before = client.await("level 12") { client.stat(AGILITY)?.takeIf { it.level == 12 } }.xp
                var hp = 99
                client.command("setlevel hitpoints 99")
                client.await("99 hitpoints") { client.stat(HITPOINTS)?.takeIf { it.level == 99 } }
                for (c in crossings.take(2)) {
                    client.teleTo(c.from)
                    client.locOp(c.by.id, c.by.x, c.by.z, 1)
                    val left = client.await("the fall by ${c.by.id}") { client.stat(HITPOINTS)?.takeIf { it.level < hp } }.level
                    assertTrue(hp - left in 1..3, "1 to 3 damage, ${hp - left} taken")
                    hp = left
                    client.await("back where it started") { client.at(c.from) }
                }
                assertEquals(before, client.stat(AGILITY)!!.xp, "no XP for a fall")
            }
        }
    }

    private companion object {
        const val AGILITY = 16
        const val HITPOINTS = 3

        /** The western stones' success pair, as agility.json writes it, with what follows it to tell them apart. */
        const val WEST_SUCCESS_TAIL = "\"success\": [225, 255], \"damage\": [1, 3], \"fromStart\": true, \"routes\": [{\"from\": [2649, 9562"
    }
}
