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

/**
 * Wilderness step W5.1b through the headless client: the Revenant Caves' five Jump-to pillars (one loc type, told apart
 * by their tiles as the 242 map places them) and agility.json, each crossed there and back; one below its level says
 * so; and a failure still crosses, for 4 to 11 damage. The pillars' tiles are blocked between the two landings, as on
 * the map. The rl-client script test-runner/examples/revenant-caves.json jumps one on the real map.
 */
class HeadlessRevenantPillarsTest {
    /** A pillar: its tile and turn, and the two landings. */
    private class Pillar(val x: Int, val z: Int, val angle: Int, val a: Pair<Int, Int>, val b: Pair<Int, Int>, val level: Int)

    private val pillars = listOf(
        Pillar(3220, 10086, 0, 3220 to 10084, 3220 to 10088, 65),
        Pillar(3200, 10136, 1, 3198 to 10136, 3202 to 10136, 75),
        Pillar(3202, 10196, 1, 3200 to 10196, 3204 to 10196, 75),
        Pillar(3180, 10209, 0, 3180 to 10207, 3180 to 10211, 75),
        Pillar(3241, 10145, 1, 3239 to 10145, 3243 to 10145, 89),
    )

    private val text = AgilityData::class.java.getResource(AgilityData.RESOURCE)!!.readText()

    private fun server(dir: Path, agilityData: AgilityData) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                for (p in pillars) {
                    for ((x, z) in listOf(p.a, p.b)) markLoaded(x shr 6, z shr 6)
                    // The gap between the landings, all but the landings blocked, as the map has it.
                    for (x in minOf(p.a.first, p.b.first)..maxOf(p.a.first, p.b.first)) for (z in minOf(p.a.second, p.b.second)..maxOf(p.a.second, p.b.second)) {
                        if (x to z != p.a && x to z != p.b) add(x, z, 0, CollisionFlag.FLOOR)
                    }
                }
            },
            locTypes = LocTypes(mapOf(PILLAR to LocType(PILLAR, "Pillar", listOf("Jump-to", null, null, null, null), LocCollisionType(width = 1, length = 1, interactType = 0)))),
            staticLocs = StaticLocs().apply { for (p in pillars) add(ZoneLoc(PILLAR, 10, p.angle, p.x, p.z, 0)) },
            agilityData = agilityData, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.teleTo(x: Int, z: Int) {
        command("tele $x $z 0")
        await("at $x, $z") { position?.takeIf { it.x == x && it.z == z && it.level == 0 } }
    }

    private fun HeadlessClient.crossTo(p: Pillar, to: Pair<Int, Int>) {
        locOp(PILLAR, p.x, p.z, 1)
        await("across to $to") { position?.takeIf { it.x == to.first && it.z == to.second && it.level == 0 } }
        awaitTicks(2)
    }

    @Test
    fun `each pillar is jumped there and back`(@TempDir dir: Path) {
        server(dir, AgilityData.parse(text.replace(SUCCESS, "\"success\": [256, 256]"))).use { server ->
            server.login("abe").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel agility 89")
                val before = client.await("level 89") { client.stat(AGILITY)?.takeIf { it.level == 89 } }.xp
                for (p in pillars) {
                    client.teleTo(p.a.first, p.a.second)
                    client.crossTo(p, p.b)
                    client.crossTo(p, p.a)
                }
                // Twice each: 6.5, three times 7.5 and 8.9, so 2 * 37.9 = 75.8 XP.
                val after = client.await("the pillars' XP") { client.stat(AGILITY)?.takeIf { it.xp >= before + 75 } }.xp
                assertTrue(after in before + 75..before + 76, "75.8 XP, got ${after - before}")
            }
        }
    }

    @Test
    fun `below its level a pillar says so, and a failure still crosses for 4 to 11 damage`(@TempDir dir: Path) {
        server(dir, AgilityData.parse(text.replace(SUCCESS, "\"success\": [-1, -1]"))).use { server ->
            server.login("bo").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel agility 74")
                client.await("level 74") { client.stat(AGILITY)?.takeIf { it.level == 74 } }
                val medium = pillars[2]
                client.teleTo(medium.a.first, medium.a.second)
                client.locOp(PILLAR, medium.x, medium.z, 1)
                client.await("the level message") { client.messages().firstOrNull { it.text == "[agility.level level=75]" } }
                assertEquals(medium.a.first, client.position!!.x)

                client.command("setlevel hitpoints 20")
                client.await("20 hitpoints") { client.stat(HITPOINTS)?.takeIf { it.level == 20 } }
                val easy = pillars[0]
                client.teleTo(easy.a.first, easy.a.second)
                client.crossTo(easy, easy.b)
                val hp = client.await("the hit") { client.stat(HITPOINTS)?.takeIf { it.level < 20 } }.level
                assertTrue(hp in 9..16, "4 to 11 damage, left at $hp")
            }
        }
    }

    @Test
    fun `frozen or bound, no jump until 3 ticks of it are left, even from the start`(@TempDir dir: Path) {
        server(dir, AgilityData.parse(text.replace(SUCCESS, "\"success\": [256, 256]"))).use { server ->
            server.login("cid").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel agility 89")
                client.await("level 89") { client.stat(AGILITY)?.takeIf { it.level == 89 } }
                val easy = pillars[0]
                client.teleTo(easy.a.first, easy.a.second)
                val cid = server.world.playerNamed("cid")!!
                // Codex review on #1012: on its start tile, the jump was a way out of a freeze.
                cid.freezeTicks = 1_000
                client.locOp(PILLAR, easy.x, easy.z, 1)
                client.await("the refusal") { client.messages().firstOrNull { it.text == "[pvp.magical_force]" } }
                client.awaitTicks(3)
                assertEquals(easy.a.first to easy.a.second, client.position!!.let { it.x to it.z }, "still on the start")
                cid.freezeTicks = 0
                cid.rootedTicks = 1_000
                client.locOp(PILLAR, easy.x, easy.z, 1)
                client.await("the bound refusal") { client.messages().count { it.text == "[pvp.magical_force]" }.takeIf { it >= 2 } }
                cid.rootedTicks = 0
                cid.freezeTicks = 3
                client.crossTo(easy, easy.b)
            }
        }
    }

    private companion object {
        const val PILLAR = 31561
        const val AGILITY = 16
        const val HITPOINTS = 3

        /** The pillars' success pair, as agility.json writes it. */
        val SUCCESS = Regex("\"success\": \\[\\s*150,\\s*255\\s*]")
    }
}
