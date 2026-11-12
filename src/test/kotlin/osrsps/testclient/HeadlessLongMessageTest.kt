package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Player
import osrsps.game.Scenario
import osrsps.game.Scenarios
import osrsps.game.World
import osrsps.map.CollisionMap

/**
 * A game message longer than one MESSAGE_GAME carries arrives over several lines rather than not at all: RSProt drops
 * a packet past its 255 bytes, which lost `::scenario fishing-cooking`'s reply on the live server (nightly 2026-11-11).
 */
class HeadlessLongMessageTest {
    @Test
    fun `a scenario with a long about still says it started, over several lines`(@TempDir dir: Path) {
        val about = (1..200).joinToString(" ") { "word$it" }
        val long = Scenario.parse("long", """{ "about": "$about", "position": { "x": 3222, "z": 3218 } }""")
        TestServer(dir, cycleMillis = 20) { crcs, millis ->
            World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, scenarios = Scenarios(listOf(long)), cycleMillis = millis)
        }.use { server ->
            server.login("ann").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("scenario long")
                client.await("the reply's last line") { client.messages().firstOrNull { it.text.endsWith("::reset puts you back.") } }
                val lines = client.messages().map { it.text }.dropWhile { !it.startsWith("Started scenario long.") }
                    .let { it.take(it.indexOfFirst { line -> line.endsWith("::reset puts you back.") } + 1) }
                assertTrue(lines.size > 1, "split over lines: $lines")
                assertTrue(lines.all { it.length <= Player.MAX_MESSAGE })
                assertEquals("Started scenario long. $about ::reset puts you back.", lines.joinToString(" "))
            }
        }
    }

    @Test
    fun `lines break at the last space before the limit, and a word too long for one line is cut`() {
        assertEquals(listOf("short"), Player.messageLines("short"))
        val text = "a".repeat(250) + " " + "b".repeat(10)
        assertEquals(listOf("a".repeat(250), "b".repeat(10)), Player.messageLines(text))
        val word = "c".repeat(Player.MAX_MESSAGE + 5)
        assertEquals(listOf("c".repeat(Player.MAX_MESSAGE), "ccccc"), Player.messageLines(word))
    }
}
