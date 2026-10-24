package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World

/** Logs the headless client in to the real network stack and checks what comes back. */
class HeadlessClientTest {
    private val spawn = Coord(World.SPAWN_X, World.SPAWN_Z, World.SPAWN_LEVEL)

    @Test
    fun `logs in at the spawn and gets the welcome message and ticks`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the welcome message") { client.messages().firstOrNull { it.text == World.WELCOME_MESSAGE } }
                assertEquals(spawn, client.position)
                client.awaitTicks(3)
            }
        }
    }

    @Test
    fun `walks where it clicks, a tile a tick, and the pos command agrees`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val target = Coord(spawn.x + 4, spawn.z, spawn.level)
                client.walk(target.x, target.z)
                client.await("arriving at $target") { if (client.position == target) Unit else null }

                val moves = client.moves()
                assertEquals((1..4).map { Coord(spawn.x + it, spawn.z, spawn.level) }, moves.map { it.move.to })
                assertTrue(moves.all { it.move.type == MoveType.WALK }, "$moves")
                assertEquals(listOf(1, 1, 1), moves.zipWithNext { a, b -> b.tick - a.tick }, "one step each tick: $moves")
                val pos = client.commandReply("pos") { POS_REPLY.matches(it) }
                assertTrue(pos.startsWith("${target.x}, ${target.z}, ${target.level} "), pos)
            }
        }
    }

    @Test
    fun `sees a second player walk into view and move`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("carol").use { carol ->
                server.login("dave").use { dave ->
                    carol.await("dave in view") { carol.otherPlayers()[dave.index] }
                    assertEquals(spawn, carol.otherPlayers()[dave.index])
                    dave.walk(spawn.x, spawn.z + 2)
                    carol.await("dave two tiles north") {
                        carol.otherPlayers()[dave.index]?.takeIf { it == Coord(spawn.x, spawn.z + 2, spawn.level) }
                    }
                }
            }
        }
    }

    @Test
    fun `a teleport arrives as a teleport`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele ${spawn.x - 10} ${spawn.z + 3}")
                client.await("the teleport") { client.moves().lastOrNull()?.move?.takeIf { it.type == MoveType.TELEPORT } }
                assertEquals(Coord(spawn.x - 10, spawn.z + 3, spawn.level), client.position)
            }
        }
    }

    private companion object {
        /** What `::pos` answers: "x, z, level (region r, local lx, lz)". */
        val POS_REPLY = Regex("""\d+, \d+, \d \(region \d+, local \d+, \d+\)""")
    }
}
