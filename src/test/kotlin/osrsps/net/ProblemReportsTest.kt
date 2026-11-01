package osrsps.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import osrsps.net.ProblemReports.Result
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProblemReportsTest {
    private var now = Instant.parse("2026-11-01T03:00:00Z")

    private fun report(vararg extra: String) = (
        listOf(
            "${ProblemReports.TITLE}, 2026-11-01T02:59:00Z",
            "launcher 1.0.5",
            "system windows/x64, Windows 11 10.0, java 21.0.8 (Eclipse Adoptium)",
            "revision 241, pin RuneLite 1.13.4 at 1e634e64",
            "",
            "== last steps and checks ==",
        ) + extra
        ).joinToString("\n").toByteArray(Charsets.UTF_8)

    private fun saved(dir: Path, id: String) =
        Json.parseToJsonElement(Files.readString(dir.resolve("$id.json"))).jsonObject

    @Test
    fun `a report is saved under the next number with when it came and the launcher's version`(@TempDir dir: Path) {
        val reports = ProblemReports(dir) { now }
        assertEquals(Result.Accepted("R-0001"), reports.submit(report("FAILED the server is offline"), "1.2.3.4"))
        assertEquals(Result.Accepted("R-0002"), reports.submit(report(), "1.2.3.5"))
        val first = saved(dir, "R-0001")
        assertEquals("R-0001", first["id"]!!.jsonPrimitive.content)
        assertEquals("2026-11-01T03:00:00Z", first["received"]!!.jsonPrimitive.content)
        assertEquals("1.0.5", first["launcher"]!!.jsonPrimitive.content)
        assertContains(first["body"]!!.jsonPrimitive.content, "FAILED the server is offline")
        // The sender's address is used for the limits only.
        assertFalse(Files.readString(dir.resolve("R-0001.json")).contains("1.2.3.4"))
        // Nothing half-written is left beside them.
        assertEquals(listOf("R-0001.json", "R-0002.json"), dir.listDirectoryEntries().map { it.name }.sorted())
    }

    @Test
    fun `numbers carry on after a restart, counting reports a forwarder moved away`(@TempDir dir: Path) {
        Files.createDirectories(dir.resolve("sent"))
        Files.writeString(dir.resolve("sent/R-0041.json"), "{}")
        Files.writeString(dir.resolve("R-0007.json"), "{}")
        assertEquals(Result.Accepted("R-0042"), ProblemReports(dir) { now }.submit(report(), "1.2.3.4"))
    }

    @Test
    fun `text that isn't a launcher's report is refused`(@TempDir dir: Path) {
        val reports = ProblemReports(dir) { now }
        assertEquals(Result.NotAReport, reports.submit("hello".toByteArray(), "1.2.3.4"))
        assertEquals(Result.NotAReport, reports.submit("${ProblemReports.TITLE}\nno version here".toByteArray(), "1.2.3.4"))
        assertEquals(Result.NotAReport, reports.submit(ByteArray(0), "1.2.3.4"))
        assertEquals(emptyList(), dir.listDirectoryEntries())
    }

    @Test
    fun `a report over the size limit is refused`(@TempDir dir: Path) {
        val big = report("x".repeat(ProblemReports.MAX_BYTES))
        assertEquals(Result.TooLarge, ProblemReports(dir) { now }.submit(big, "1.2.3.4"))
    }

    @Test
    fun `one address may send a few an hour, and may again once the hour is up`(@TempDir dir: Path) {
        val reports = ProblemReports(dir) { now }
        repeat(ProblemReports.PER_ADDRESS_PER_HOUR) { assertTrue(reports.submit(report(), "1.2.3.4") is Result.Accepted) }
        assertEquals(Result.TooMany, reports.submit(report(), "1.2.3.4"))
        // Another address isn't held back by this one.
        assertTrue(reports.submit(report(), "5.6.7.8") is Result.Accepted)
        now += Duration.ofMinutes(59)
        assertEquals(Result.TooMany, reports.submit(report(), "1.2.3.4"))
        now += Duration.ofMinutes(1)
        assertTrue(reports.submit(report(), "1.2.3.4") is Result.Accepted)
    }

    @Test
    fun `everyone together may send only so many an hour`(@TempDir dir: Path) {
        val reports = ProblemReports(dir) { now }
        repeat(ProblemReports.PER_HOUR) { assertTrue(reports.submit(report(), "10.0.${it / 250}.${it % 250}") is Result.Accepted) }
        assertEquals(Result.TooMany, reports.submit(report(), "9.9.9.9"))
    }

    @Test
    fun `when too many wait unforwarded, more are refused until some move away`(@TempDir dir: Path) {
        repeat(ProblemReports.MAX_WAITING) { Files.writeString(dir.resolve("R-%04d.json".format(it + 1)), "{}") }
        val reports = ProblemReports(dir) { now }
        assertEquals(Result.Unavailable, reports.submit(report(), "1.2.3.4"))
        // A forwarder moves some to a folder of its own while the server runs: the count follows, the numbers don't go back.
        Files.createDirectories(dir.resolve("sent"))
        repeat(10) { Files.move(dir.resolve("R-%04d.json".format(it + 1)), dir.resolve("sent/R-%04d.json".format(it + 1))) }
        assertEquals(Result.Accepted("R-%04d".format(ProblemReports.MAX_WAITING + 1)), reports.submit(report(), "1.2.3.4"))
    }

    @Test
    fun `passwords, home folders and control characters are scrubbed again on this side`(@TempDir dir: Path) {
        val reports = ProblemReports(dir) { now }
        val id = (reports.submit(
            report(
                "login with Password=hunter2",
                "an Authorization: Bearer abc header",
                "loaded C:\\Users\\SamSmith\\AppData\\Local\\OdinsGate\\home",
                "mac /Users/jane/Library and linux /home/bob/.runelite",
                "spaced C:\\Users\\Sam Smith\\AppData and /Users/O'Connor/Library",
                "bell\u0007 and escape\u001B[0m",
            ),
            "1.2.3.4",
        ) as Result.Accepted).id
        val body = saved(dir, id)["body"]!!.jsonPrimitive.content
        assertFalse(body.contains("hunter2"))
        assertFalse(body.contains("Bearer"))
        assertEquals(2, body.lines().count { it == ProblemReports.LEFT_OUT })
        assertContains(body.lines(), "loaded C:\\Users\\<user>\\AppData\\Local\\OdinsGate\\home")
        assertContains(body.lines(), "mac /Users/<user>/Library and linux /home/<user>/.runelite")
        assertContains(body.lines(), "bell and escape[0m")
        assertContains(body.lines(), "spaced C:\\Users\\<user>\\AppData and /Users/<user>/Library")
        assertFalse(listOf("SamSmith", "jane", "bob", "Smith", "Connor").any(body::contains))
    }

    @Test
    fun `POST report over HTTP answers the report's number, and anything else about reports is refused`(@TempDir dir: Path) {
        val http = ClientConfig("127.0.0.1", 0, gamePort = 43594, reports = ProblemReports(dir) { now }).also { it.start() }
        try {
            val port = http.boundPort
            val accepted = request(port, "POST", "/report", report("FAILED"))
            assertEquals(201, accepted.code)
            assertEquals("R-0001\n", accepted.body)
            assertTrue(Files.exists(dir.resolve("R-0001.json")))

            assertEquals(400, request(port, "POST", "/report", "hello".toByteArray()).code)
            assertEquals(405, request(port, "GET", "/report").code)
            assertEquals(413, request(port, "POST", "/report", report("x".repeat(ProblemReports.MAX_BYTES))).code)
            // A body on anything but a report is refused before it is buffered.
            assertEquals(413, request(port, "POST", "/version", ByteArray(9000)).code)
            assertEquals(413, request(port, "GET", "/version", ByteArray(9000)).code)
            // A small body on a GET is still just ignored, as before.
            assertEquals(200, request(port, "GET", "/version", ByteArray(10)).code)

            repeat(ProblemReports.PER_ADDRESS_PER_HOUR - 1) { assertEquals(201, request(port, "POST", "/report", report()).code) }
            val limited = request(port, "POST", "/report", report())
            assertEquals(429, limited.code)
            assertContains(limited.head, "retry-after: 3600")
        } finally {
            http.stop()
        }
    }

    @Test
    fun `without a reports folder the server says reports can't be taken`() {
        val http = ClientConfig("127.0.0.1", 0, gamePort = 43594).also { it.start() }
        try {
            assertEquals(503, request(http.boundPort, "POST", "/report", report()).code)
        } finally {
            http.stop()
        }
    }

    private class Reply(val code: Int, val head: String, val body: String)

    private fun request(port: Int, method: String, path: String, body: ByteArray? = null): Reply =
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5000
            val length = body?.let { "Content-Length: ${it.size}\r\n" }.orEmpty()
            socket.getOutputStream().apply {
                write("$method $path HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n${length}Connection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                // The server may answer and close before taking a body it refuses; that is the point.
                runCatching { body?.let(::write); flush() }
            }
            val response = String(socket.getInputStream().readBytes(), Charsets.UTF_8)
            val head = response.substringBefore("\r\n\r\n")
            Reply(head.lines().first().split(' ')[1].toInt(), head.lowercase(), response.substringAfter("\r\n\r\n"))
        }
}
