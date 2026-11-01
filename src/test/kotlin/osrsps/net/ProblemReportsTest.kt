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
        assertEquals(listOf("R-0001.json", "R-0002.json", ProblemReports.LAST_NUMBER), dir.listDirectoryEntries().map { it.name }.sorted())
    }

    @Test
    fun `numbers carry on after a restart, counting reports a forwarder moved away`(@TempDir dir: Path) {
        Files.createDirectories(dir.resolve("sent"))
        Files.writeString(dir.resolve("sent/R-0041.json"), "{}")
        Files.writeString(dir.resolve("R-0007.json"), "{}")
        assertEquals(Result.Accepted("R-0042"), ProblemReports(dir) { now }.submit(report(), "1.2.3.4"))
    }

    @Test
    fun `a deleted report's number is never given again, in the same run or after a restart`(@TempDir dir: Path) {
        val reports = ProblemReports(dir) { now }
        assertEquals(Result.Accepted("R-0001"), reports.submit(report(), "1.1.1.1"))
        Files.delete(dir.resolve("R-0001.json"))
        assertEquals(Result.Accepted("R-0002"), reports.submit(report(), "2.2.2.2"))
        Files.delete(dir.resolve("R-0002.json"))
        assertEquals(Result.Accepted("R-0003"), ProblemReports(dir) { now }.submit(report(), "3.3.3.3"))
    }

    @Test
    fun `text that isn't a launcher's report is refused`(@TempDir dir: Path) {
        val reports = ProblemReports(dir) { now }
        assertEquals(Result.NotAReport, reports.submit("hello".toByteArray(), "1.2.3.4"))
        assertEquals(Result.NotAReport, reports.submit("${ProblemReports.TITLE}\nno version here".toByteArray(), "1.2.3.4"))
        assertEquals(Result.NotAReport, reports.submit(ByteArray(0), "1.2.3.4"))
        assertEquals(emptyList(), dir.listDirectoryEntries().map { it.name })
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
        // The count is trusted for a minute, so a flood doesn't read the folder once a report.
        assertEquals(Result.Unavailable, reports.submit(report(), "1.2.3.5"))
        now += ProblemReports.RECOUNT
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

            // The refused "hello" counts toward this address's hour too: every report sent does, taken or not.
            repeat(ProblemReports.PER_ADDRESS_PER_HOUR - 2) { assertEquals(201, request(port, "POST", "/report", report()).code) }
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

    @Test
    fun `an address the server keeps refusing is still held to its limit`(@TempDir dir: Path) {
        repeat(ProblemReports.MAX_WAITING) { Files.writeString(dir.resolve("R-%04d.json".format(it + 1)), "{}") }
        val reports = ProblemReports(dir) { now }
        repeat(ProblemReports.PER_ADDRESS_PER_HOUR) { assertEquals(Result.Unavailable, reports.submit(report(), "6.6.6.6")) }
        // Refused for a full queue or for text that isn't a report, it is still a report sent.
        assertEquals(Result.TooMany, reports.submit(report(), "6.6.6.6"))
        assertFalse(reports.admits("6.6.6.6"))
        repeat(ProblemReports.PER_ADDRESS_PER_HOUR) { assertEquals(Result.NotAReport, reports.submit("hello".toByteArray(), "7.7.7.7")) }
        assertEquals(Result.TooMany, reports.submit(report(), "7.7.7.7"))
        assertTrue(reports.admits("8.8.8.8"))
    }

    @Test
    fun `a sender over the limit is refused before its body is read`(@TempDir dir: Path) {
        val reports = ProblemReports(dir) { now }
        repeat(ProblemReports.PER_ADDRESS_PER_HOUR) { assertTrue(reports.submit(report(), "127.0.0.1") is Result.Accepted) }
        serve(reports) { port ->
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 5000
                // Headers only: the answer comes without waiting for the body they promise.
                socket.getOutputStream().write(header(port, ProblemReports.MAX_BYTES))
                assertEquals("HTTP/1.1 429", String(socket.getInputStream().readNBytes(12), Charsets.US_ASCII))
            }
        }
    }

    @Test
    fun `half-sent report bodies are dropped, and one address can't hold more than two at once`(@TempDir dir: Path) {
        serve(ProblemReports(dir) { now }, readTimeout = Duration.ofSeconds(1)) { port ->
            val sockets = (1..20).map { Socket("127.0.0.1", port) }
            try {
                for (socket in sockets) {
                    socket.soTimeout = 5000
                    runCatching {
                        socket.getOutputStream().apply { write(header(port, ProblemReports.MAX_BYTES)); write(ByteArray(ProblemReports.MAX_BYTES - 1)); flush() }
                    }
                }
                // All but two are answered 429 at once; those two are closed once the rest stops coming.
                val started = System.nanoTime()
                val answers = sockets.map(::answer)
                assertEquals(ClientConfig.MAX_REPORTS_READING_PER_ADDRESS, answers.count { it == "closed" }, answers.toString())
                assertEquals(20 - ClientConfig.MAX_REPORTS_READING_PER_ADDRESS, answers.count { it == "HTTP/1.1 429" }, answers.toString())
                assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(4))
            } finally {
                sockets.forEach { runCatching { it.close() } }
            }
        }
    }

    @Test
    fun `a body that trickles in is cut off at the deadline`(@TempDir dir: Path) {
        serve(ProblemReports(dir) { now }, readTimeout = Duration.ofSeconds(5), reportDeadline = Duration.ofSeconds(1)) { port ->
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 200
                val out = socket.getOutputStream()
                out.write(header(port, ProblemReports.MAX_BYTES))
                val started = System.nanoTime()
                var closed = false
                // A byte every 100 ms keeps the read timeout away, not the deadline.
                while (!closed && Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(4)) {
                    closed = runCatching { out.write(0); out.flush(); socket.getInputStream().read() == -1 }
                        .getOrElse { it !is java.net.SocketTimeoutException }
                }
                assertTrue(closed, "the trickled body was still being read after 4 s")
            }
        }
    }

    @Test
    fun `only so many report bodies are read at once, from everyone`(@TempDir dir: Path) {
        val proxy = ProxyProtocol(setOf("127.0.0.1"), ProxyProtocol.Mode.OPTIONAL)
        serve(ProblemReports(dir) { now }, proxy = proxy) { port ->
            val sockets = (1..ClientConfig.MAX_REPORTS_READING + 1).map { Socket("127.0.0.1", port) }
            try {
                sockets.forEachIndexed { i, socket ->
                    socket.soTimeout = 3000
                    socket.getOutputStream().apply { write(ProxyHeaders.v1("203.0.113.${i + 1}", 40000)); write(header(port, 1000)); flush() }
                    // The server takes them in turn, so the last is the one past the cap.
                    if (i < ClientConfig.MAX_REPORTS_READING) Thread.sleep(50)
                }
                assertEquals("HTTP/1.1 503", String(sockets.last().getInputStream().readNBytes(12), Charsets.US_ASCII))
                // One finishing makes room for the next.
                sockets.first().getOutputStream().apply { write(report().copyOf(1000)); flush() }
                assertEquals("HTTP/1.1 201", String(sockets.first().getInputStream().readNBytes(12), Charsets.US_ASCII))
                Socket("127.0.0.1", port).use { socket ->
                    socket.soTimeout = 3000
                    val body = report()
                    socket.getOutputStream().apply { write(ProxyHeaders.v1("203.0.113.99", 40000)); write(header(port, body.size)); write(body); flush() }
                    assertEquals("HTTP/1.1 201", String(socket.getInputStream().readNBytes(12), Charsets.US_ASCII))
                }
            } finally {
                sockets.forEach { runCatching { it.close() } }
            }
        }
    }

    private fun serve(
        reports: ProblemReports,
        proxy: ProxyProtocol = ProxyProtocol.OFF,
        readTimeout: Duration = Duration.ofSeconds(ClientConfig.READ_TIMEOUT_SECONDS.toLong()),
        reportDeadline: Duration = Duration.ofSeconds(ClientConfig.REPORT_DEADLINE_SECONDS.toLong()),
        block: (Int) -> Unit,
    ) {
        val http = ClientConfig("127.0.0.1", 0, gamePort = 43594, proxy = proxy, reports = reports, readTimeout = readTimeout, reportDeadline = reportDeadline)
        http.start()
        try {
            block(http.boundPort)
        } finally {
            http.stop()
        }
    }

    private fun header(port: Int, length: Int) =
        "POST /report HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nContent-Length: $length\r\n\r\n".toByteArray(Charsets.US_ASCII)

    /** The status line the server answered on [socket], or "closed" when it closed without one. */
    private fun answer(socket: Socket): String = runCatching {
        val bytes = socket.getInputStream().readNBytes(12)
        if (bytes.isEmpty()) "closed" else String(bytes, Charsets.US_ASCII)
    }.getOrElse { if (it is java.net.SocketTimeoutException) "open" else "closed" }

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
