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
    fun `a flood of other addresses can't make the server forget one's limit`(@TempDir dir: Path) {
        val reports = ProblemReports(dir) { now }
        repeat(ProblemReports.PER_ADDRESS_PER_HOUR) { assertTrue(reports.submit(report(), "1.2.3.4") is Result.Accepted) }
        repeat(ProblemReports.TRACKED - 1) { i ->
            assertEquals(Result.NotAReport, reports.submit("hello".toByteArray(), "10.${i / 65536}.${i / 256 % 256}.${i % 256}"))
        }
        assertEquals(Result.TooMany, reports.submit(report(), "1.2.3.4"))
        // With no room to remember another address, a new one is refused rather than an old one's count forgotten.
        assertFalse(reports.admits("9.9.9.9"))
        assertEquals(Result.TooMany, reports.submit(report(), "9.9.9.9"))
        now += Duration.ofHours(1)
        assertTrue(reports.submit(report(), "9.9.9.9") is Result.Accepted)
    }

    @Test
    fun `a sender over the limit is refused before its body is read`(@TempDir dir: Path) {
        val reports = ProblemReports(dir) { now }
        repeat(ProblemReports.PER_ADDRESS_PER_HOUR) { assertTrue(reports.submit(report(), "127.0.0.1") is Result.Accepted) }
        serve(reports) { _, port ->
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
        serve(ProblemReports(dir) { now }) { http, port ->
            // As many as one address may open at all.
            val sockets = (1..ClientConfig.MAX_CONNECTIONS_PER_ADDRESS).map { Socket("127.0.0.1", port) }
            try {
                for (socket in sockets) {
                    socket.soTimeout = 5000
                    socket.getOutputStream().apply { write(header(port, ProblemReports.MAX_BYTES)); flush() }
                }
                // All but two are answered 429 at once, whatever order the server took them in.
                val refused = waitForAnswers(sockets, sockets.size - ClientConfig.MAX_REPORTS_READING_PER_ADDRESS)
                refused.forEach { assertEquals("HTTP/1.1 429", String(it.getInputStream().readNBytes(12), Charsets.US_ASCII)) }
                val reading = sockets - refused.toSet()
                assertTrue(reading.all { it.getInputStream().available() == 0 })
                // Both are taken in, all they sent read, before the clock moves.
                await { http.reportsBeingRead == ClientConfig.MAX_REPORTS_READING_PER_ADDRESS }
                // Those two are closed once they have sent nothing for the read timeout.
                clock += Duration.ofSeconds(ClientConfig.READ_TIMEOUT_SECONDS.toLong())
                http.sweep()
                reading.forEach { assertEquals(-1, it.getInputStream().read()) }
            } finally {
                sockets.forEach { runCatching { it.close() } }
            }
        }
    }

    @Test
    fun `a body that trickles in is cut off at the deadline`(@TempDir dir: Path) {
        serve(ProblemReports(dir) { now }) { http, port ->
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 5000
                val out = socket.getOutputStream()
                out.write(header(port, ProblemReports.MAX_BYTES))
                out.flush()
                // Taken in, so its deadline starts now, before the clock moves.
                await { http.reportsBeingRead == 1 }
                // A byte every 10 s keeps it from going idle, but not past the deadline.
                repeat(2) {
                    clock += Duration.ofSeconds(10)
                    sent(http) { out.write(0); out.flush() }
                    http.sweep()
                }
                assertEquals(0, socket.getInputStream().available())
                clock += Duration.ofSeconds(ClientConfig.REPORT_DEADLINE_SECONDS - 20L)
                http.sweep()
                assertEquals(-1, socket.getInputStream().read())
            }
        }
    }

    @Test
    fun `only so many report bodies are read at once, from everyone`(@TempDir dir: Path) {
        val proxy = ProxyProtocol(setOf("127.0.0.1"), ProxyProtocol.Mode.OPTIONAL)
        serve(ProblemReports(dir) { now }, proxy = proxy) { _, port ->
            val sockets = (1..ClientConfig.MAX_REPORTS_READING + 1).map { Socket("127.0.0.1", port) }
            try {
                sockets.forEachIndexed { i, socket ->
                    socket.soTimeout = 3000
                    socket.getOutputStream().apply { write(ProxyHeaders.v1("203.0.113.${i + 1}", 40000)); write(header(port, 1000)); flush() }
                }
                // Whichever the server took last is refused; which one that is doesn't matter.
                val refused = waitForAnswers(sockets, 1).single()
                assertEquals("HTTP/1.1 503", String(refused.getInputStream().readNBytes(12), Charsets.US_ASCII))
                val reading = sockets - refused
                assertTrue(reading.all { it.getInputStream().available() == 0 }, "more than one was refused")
                // One finishing makes room for the next.
                reading.first().getOutputStream().apply { write(report().copyOf(1000)); flush() }
                assertEquals("HTTP/1.1 201", String(reading.first().getInputStream().readNBytes(12), Charsets.US_ASCII))
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

    @Test
    fun `one address can't hold every connection, and others still get answers`(@TempDir dir: Path) {
        val proxy = ProxyProtocol(setOf("127.0.0.1"), ProxyProtocol.Mode.OPTIONAL)
        serve(ProblemReports(dir) { now }, proxy = proxy) { http, port ->
            val held = (1..ClientConfig.MAX_CONNECTIONS_PER_ADDRESS).map { Socket("127.0.0.1", port).apply { soTimeout = 5000 } }
            try {
                for (socket in held) sent(http) { socket.getOutputStream().apply { write("GET /version HTTP/1.1\r\nX-Slow: ".toByteArray()); flush() } }
                Socket("127.0.0.1", port).use { one ->
                    one.soTimeout = 5000
                    runCatching { one.getOutputStream().apply { write("GET /version HTTP/1.1\r\n".toByteArray()); flush() } }
                    assertClosed(one, "one too many from one address")
                }
                // Someone else, through the relay, is answered all the same.
                Socket("127.0.0.1", port).use { other ->
                    other.soTimeout = 5000
                    other.getOutputStream().apply {
                        write(ProxyHeaders.v1("203.0.113.9", 40000))
                        write("GET /version HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n".toByteArray())
                        flush()
                    }
                    assertEquals("HTTP/1.1 200", String(other.getInputStream().readNBytes(12), Charsets.US_ASCII))
                }
            } finally {
                held.forEach { runCatching { it.close() } }
            }
        }
    }

    @Test
    fun `connections that send only a PROXY header still count against their sender`(@TempDir dir: Path) {
        val proxy = ProxyProtocol(setOf("127.0.0.1"), ProxyProtocol.Mode.OPTIONAL)
        serve(ProblemReports(dir) { now }, proxy = proxy) { http, port ->
            val held = (1..ClientConfig.MAX_CONNECTIONS_PER_ADDRESS).map { Socket("127.0.0.1", port).apply { soTimeout = 5000 } }
            try {
                for (socket in held) sent(http) { socket.getOutputStream().apply { write(ProxyHeaders.v1("203.0.113.50", 40000)); flush() } }
                Socket("127.0.0.1", port).use { one ->
                    one.soTimeout = 5000
                    runCatching { one.getOutputStream().apply { write(ProxyHeaders.v1("203.0.113.50", 40001)); flush() } }
                    assertClosed(one, "one too many from one address")
                }
                Socket("127.0.0.1", port).use { other ->
                    other.soTimeout = 5000
                    other.getOutputStream().apply {
                        write(ProxyHeaders.v1("203.0.113.51", 40000))
                        write("GET /version HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n".toByteArray())
                        flush()
                    }
                    assertEquals("HTTP/1.1 200", String(other.getInputStream().readNBytes(12), Charsets.US_ASCII))
                }
            } finally {
                held.forEach { runCatching { it.close() } }
            }
        }
    }

    @Test
    fun `headers that trickle in are cut off at their deadline`(@TempDir dir: Path) {
        serve(ProblemReports(dir) { now }) { http, port ->
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 5000
                val out = socket.getOutputStream()
                sent(http) { out.write("GET /version HTTP/1.1\r\nX-Slow: ".toByteArray()); out.flush() }
                // A byte every 5 s keeps it from going idle, but not past the headers' deadline.
                repeat(ClientConfig.HEADERS_DEADLINE_SECONDS / 5 - 1) {
                    clock += Duration.ofSeconds(5)
                    sent(http) { out.write('a'.code); out.flush() }
                    http.sweep()
                }
                clock += Duration.ofSeconds(5)
                http.sweep()
                assertEquals(-1, socket.getInputStream().read())
            }
        }
    }

    @Test
    fun `a connection kept alive starts its headers' deadline afresh with each request`(@TempDir dir: Path) {
        serve(ProblemReports(dir) { now }) { http, port ->
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 5000
                val out = socket.getOutputStream()
                val input = socket.getInputStream()
                repeat(2) {
                    out.write("GET /version HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                    out.flush()
                    val head = String(input.readNBytes(12), Charsets.US_ASCII)
                    assertEquals("HTTP/1.1 200", head)
                    // Read the rest of this answer: its headers, then as many bytes as it says.
                    val rest = StringBuilder()
                    while (!rest.endsWith("\r\n\r\n")) rest.append(input.read().toChar())
                    val length = Regex("(?i)content-length: (\\d+)").find(rest)!!.groupValues[1].toInt()
                    input.readNBytes(length)
                    clock += Duration.ofSeconds(ClientConfig.HEADERS_DEADLINE_SECONDS - 1L)
                    http.sweep()
                }
            }
        }
    }

    /** The HTTP side's clock, which only the tests move: the read timeout and the deadline never wait on real time. */
    private var clock = Instant.parse("2026-11-01T03:00:00Z")

    private fun serve(reports: ProblemReports, proxy: ProxyProtocol = ProxyProtocol.OFF, block: (ClientConfig, Int) -> Unit) {
        val http = ClientConfig("127.0.0.1", 0, gamePort = 43594, proxy = proxy, reports = reports, clock = { clock })
        http.start()
        try {
            block(http, http.boundPort)
        } finally {
            http.stop()
        }
    }

    /** Does [write], then waits until the server has read something since, so a clock moved after it comes after. */
    private fun sent(http: ClientConfig, write: () -> Unit) {
        val before = http.reads.get()
        write()
        await { http.reads.get() > before }
    }

    /** Asserts the server closed [socket]: a reset counts, but a read that times out (still open) fails. */
    private fun assertClosed(socket: Socket, message: String) {
        val read = runCatching { socket.getInputStream().read() }
            .getOrElse { if (it is java.net.SocketTimeoutException) throw AssertionError("$message: still open", it) else -1 }
        assertEquals(-1, read, message)
    }

    /** Waits up to 5 s for [done], polling: for the server to have done something, never for time to pass. */
    private fun await(done: () -> Boolean) {
        repeat(500) {
            if (done()) return
            Thread.sleep(10)
        }
        error("the server never got there")
    }

    private fun header(port: Int, length: Int) =
        "POST /report HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nContent-Length: $length\r\n\r\n".toByteArray(Charsets.US_ASCII)

    /** The first [count] of [sockets] the server has answered on, waiting for them up to 5 s. */
    private fun waitForAnswers(sockets: List<Socket>, count: Int): List<Socket> {
        repeat(500) {
            val answered = sockets.filter { it.getInputStream().available() > 0 }
            if (answered.size >= count) return answered
            Thread.sleep(10)
        }
        error("fewer than $count sockets were answered")
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
