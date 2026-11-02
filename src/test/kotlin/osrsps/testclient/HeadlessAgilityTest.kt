package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

import osrsps.game.GameRandom
import osrsps.game.Stance
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
 * Agility step 1 through the headless client: test obstacles beside the spawn that walk the player to their start,
 * climb, balance and slide them across, give the XP, fail and hurt, and check the level. The courses' own tests come
 * with each course.
 */
class HeadlessAgilityTest {
    private val obstacles = AgilityData.parse(
        """
        {"obstacles": [
          {"key": "test_wall", "loc": $WALL, "option": "Climb", "level": 1, "xpTenths": 125,
           "routes": [{"from": [3223, 3216, 0], "moves": [
             {"type": "anim", "anim": 828, "ticks": 2},
             {"type": "teleport", "to": [3224, 3217, 0]},
             {"type": "walk", "path": [[3224, 3220]], "anim": 762},
             {"type": "slide", "to": [3224, 3223, 0], "anim": 741, "startCycles": 0, "endCycles": 60, "ticks": 2}
           ]}]},
          {"key": "test_rope", "loc": $ROPE, "option": "Walk-across", "level": 1, "xpTenths": 80, "success": [-1, -1], "damage": [2, 2],
           "routes": [{"from": [3219, 3216, 0], "moves": [{"type": "walk", "path": [[3219, 3212]], "anim": 762}],
             "fail": [{"type": "anim", "anim": 846, "ticks": 1}, {"type": "teleport", "to": [3218, 3214, 0]}]}]},
          {"key": "test_ledge", "loc": $LEDGE, "option": "Jump", "level": 20, "xpTenths": 10,
           "routes": [{"from": [3226, 3218, 0], "moves": [{"type": "teleport", "to": [3227, 3218, 0]}]}]}
        ]}
        """,
    )

    private fun loc(id: Int, name: String, op: String) = LocType(id, name, listOf(op, null, null, null, null), LocCollisionType(width = 1, length = 1))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(3),
            locTypes = LocTypes(listOf(loc(WALL, "Rough wall", "Climb"), loc(ROPE, "Tightrope", "Walk-across"), loc(LEDGE, "Ledge", "Jump")).associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(WALL, LocShape.CENTREPIECE_STRAIGHT, 0, 3224, 3216, 0))
                add(ZoneLoc(ROPE, LocShape.CENTREPIECE_STRAIGHT, 0, 3219, 3215, 0))
                add(ZoneLoc(LEDGE, LocShape.CENTREPIECE_STRAIGHT, 0, 3227, 3219, 0))
            },
            agilityData = obstacles, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.at(x: Int, z: Int, what: String) =
        await(what) { position?.takeIf { it.x == x && it.z == z } }

    @Test
    fun `an obstacle walks the player to its start, climbs, balances and slides them across, then gives the XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(WALL, 3224, 3216, 1)
                val player = server.world.onlinePlayers.single()
                // The balance shows its own animation as the whole stance.
                client.await("the balancing stance") { player.sentStance?.takeIf { it == Stance(762, 762, 762, 762, 762, 762, 762) } }
                // A click while on the obstacle does nothing, so the player ends across and stays there.
                client.walk(3215, 3216)
                client.at(3224, 3223, "across")
                assertEquals(12, client.await("the Agility XP") { client.stat(AGILITY)?.takeIf { it.xp > 0 } }.xp)
                // At the start (3223, 3216): two ticks of climbing, over the wall (a teleport the protocol may send as a step),
                // a tile a tick along the balance from the tick after, then the slide on the tick after the balance's last step,
                // its exact move sent as it starts and the player on its end 2 ticks later.
                val all = client.moves()
                val moves = all.drop(all.indexOfLast { it.move.to == Coord(3223, 3216, 0) })
                assertEquals(
                    listOf(Coord(3223, 3216), Coord(3224, 3217), Coord(3224, 3218), Coord(3224, 3219), Coord(3224, 3220), Coord(3224, 3223)),
                    moves.map { it.move.to },
                )
                val ticks = moves.map { it.tick }
                assertEquals(3, ticks[1] - ticks[0], "climbing, from the tick after reaching the start")
                assertEquals(listOf(1, 1, 1), listOf(ticks[2] - ticks[1], ticks[3] - ticks[2], ticks[4] - ticks[3]), "balancing")
                val slide = client.exactMoveTicks().single()
                assertEquals(ticks[4] + 1, slide)
                assertEquals(2, ticks[5] - slide, "sliding")
                client.awaitTicks(3)
                assertEquals(3224, client.position!!.x)
                assertEquals(3223, client.position!!.z)
                assertNull(player.stanceOverride)
                client.await("the weapon's stance back") { player.sentStance?.takeIf { it.walk != 762 } }
            }
        }
    }

    @Test
    fun `a failure plays the fall, deals its damage and gives no XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(ROPE, 3219, 3215, 1)
                client.at(3218, 3214, "the fall's tile")
                client.await("the hit") { client.stat(HITPOINTS)?.takeIf { it.level == 8 } }
                client.awaitTicks(2)
                assertEquals(0, client.stat(AGILITY)?.xp ?: 0)
            }
        }
    }

    @Test
    fun `an obstacle above the player's level says so and leaves them where they are`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(LEDGE, 3227, 3219, 1)
                client.await("the level message") { client.messages().firstOrNull { it.text == "[agility.level level=20]" } }
                val stood = client.position!!
                client.awaitTicks(2)
                assertEquals(stood, client.position)
                client.command("setlevel agility 20")
                client.await("level 20") { client.stat(AGILITY)?.takeIf { it.level == 20 } }
                client.locOp(LEDGE, 3227, 3219, 1)
                client.at(3227, 3218, "across the ledge")
                assertTrue(client.await("the XP") { client.stat(AGILITY)?.takeIf { it.xp > 4_470 } }.xp == 4_471)
            }
        }
    }

    private companion object {
        const val WALL = 11_404
        const val ROPE = 11_405
        const val LEDGE = 11_406
        const val HITPOINTS = 3
        const val AGILITY = 16
    }
}
