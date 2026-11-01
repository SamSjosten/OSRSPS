package osrsps.net

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import osrsps.Revision
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportForwarderTest {
    private val now = Instant.parse("2026-11-01T03:00:00Z")

    private class FakeTracker(val releases: Set<String> = setOf("1.0.5"), val releasesKnown: Boolean = true) : ReportForwarder.IssueTracker {
        data class Issue(val title: String, val body: String, val labels: List<String>, var open: Boolean = true, val comments: MutableList<String> = mutableListOf())
        val issues = mutableListOf<Issue>()
        var failNext = false

        override fun isRelease(version: String) = if (releasesKnown) version in releases else null
        override fun isOpen(issue: Int) = issues[issue - 1].open
        override fun create(title: String, body: String, labels: List<String>): Int {
            if (failNext) { failNext = false; throw IOException("GitHub answered 502") }
            issues += Issue(title, body, labels)
            return issues.size
        }
        override fun comment(issue: Int, body: String) { issues[issue - 1].comments += body }
    }

    private fun body(launcher: String = "1.0.5", revision: Int? = Revision.MAJOR, failure: String? = "FAILED the server is offline: request timed out", extra: String = "") =
        listOfNotNull(
            "${ProblemReports.TITLE}, 2026-11-01T02:59:00Z",
            "launcher $launcher",
            "system windows/x64, Windows 11 10.0, java 21.0.8 (Eclipse Adoptium)",
            "revision ${revision ?: "not seen yet"}",
            "",
            "== last steps and checks ==",
            "02:58:59 CHECKING the server",
            failure?.let { "02:59:00 $it" },
            extra,
        ).joinToString("\n").toByteArray()

    /** Saves a report through [ProblemReports], the way POST /report does, from an address of its own. */
    private fun send(dir: Path, body: ByteArray, from: String = "1.2.3.${(Math.floorMod(body.contentHashCode(), 200))}"): String {
        val result = ProblemReports(dir) { now }.submit(body, from)
        return (result as ProblemReports.Result.Accepted).id
    }

    @Test
    fun `a report becomes a verified issue with the checks and the server's facts, and moves to sent`(@TempDir dir: Path) {
        val tracker = FakeTracker()
        val id = send(dir, body())
        val forwarder = ReportForwarder(dir, { tracker }, { listOf("commit abc, revision ${Revision.MAJOR}", "game up, 2 player(s) in") })
        assertEquals(1, forwarder.pass())

        val issue = tracker.issues.single()
        assertEquals("Problem report $id: FAILED the server is offline: request timed out", issue.title)
        assertEquals(listOf("bug-report", "verified"), issue.labels)
        assertContains(issue.body, "- launcher 1.0.5: a released version")
        assertContains(issue.body, "- revision ${Revision.MAJOR}: the server's")
        assertContains(issue.body, "- game up, 2 player(s) in")
        assertContains(issue.body, "```\n${ProblemReports.TITLE}")
        assertFalse(Files.exists(dir.resolve("$id.json")))
        assertTrue(Files.exists(dir.resolve("sent/$id.json")))
        // Nothing left to do on the next pass.
        assertEquals(0, forwarder.pass())
    }

    @Test
    fun `the same failure again is a comment on the open issue, and a new issue once that one is closed`(@TempDir dir: Path) {
        val tracker = FakeTracker()
        val forwarder = ReportForwarder(dir, { tracker })
        send(dir, body(), "1.1.1.1")
        forwarder.pass()
        // Another day's time on the line; the same failure.
        val second = send(dir, body(failure = "FAILED the server is offline: request timed out").toString(Charsets.UTF_8).replace("02:59:00", "17:04:31").toByteArray(), "2.2.2.2")
        forwarder.pass()
        assertEquals(1, tracker.issues.size)
        assertContains(tracker.issues[0].comments.single(), "Problem report **$second**")

        tracker.issues[0].open = false
        send(dir, body(), "3.3.3.3")
        forwarder.pass()
        assertEquals(2, tracker.issues.size)
        // A different failure is a different issue.
        send(dir, body(failure = "FAILED the pin doesn't fit"), "4.4.4.4")
        forwarder.pass()
        assertEquals(3, tracker.issues.size)
    }

    @Test
    fun `an unreleased launcher or another revision is labelled for it, not verified`(@TempDir dir: Path) {
        val tracker = FakeTracker()
        send(dir, body(launcher = "9.9.9", failure = "FAILED one"), "1.1.1.1")
        send(dir, body(revision = Revision.MAJOR + 1, failure = "FAILED two"), "2.2.2.2")
        send(dir, body(launcher = "1.0.1", revision = null, failure = "FAILED three"), "3.3.3.3")
        ReportForwarder(dir, { tracker }).pass()
        assertEquals(listOf("bug-report", "unreleased-launcher"), tracker.issues[0].labels)
        assertContains(tracker.issues[0].body, "NOT a released version")
        assertEquals(listOf("bug-report", "revision-mismatch"), tracker.issues[1].labels)
        // 1.0.0 and 1.0.1 were released from the server repository, and a launcher that saw no revision isn't wrong.
        assertEquals(listOf("bug-report", "verified"), tracker.issues[2].labels)

        val unsure = FakeTracker(releasesKnown = false)
        send(dir, body(failure = "FAILED four"), "4.4.4.4")
        ReportForwarder(dir, { unsure }).pass()
        assertEquals(listOf("bug-report", "unchecked-launcher"), unsure.issues.single().labels)
    }

    @Test
    fun `without a token reports wait, and a failed filing is tried again`(@TempDir dir: Path) {
        val id = send(dir, body())
        var tracker: FakeTracker? = null
        val forwarder = ReportForwarder(dir, { tracker })
        assertEquals(0, forwarder.pass())
        assertTrue(Files.exists(dir.resolve("$id.json")))

        tracker = FakeTracker().apply { failNext = true }
        assertEquals(0, forwarder.pass())
        assertTrue(Files.exists(dir.resolve("$id.json")))
        assertEquals(1, forwarder.pass())
        assertEquals(1, tracker.issues.size)
    }

    @Test
    fun `report numbers keep counting after reports move to sent, and sent ones don't count as waiting`(@TempDir dir: Path) {
        val tracker = FakeTracker()
        val forwarder = ReportForwarder(dir, { tracker })
        val reports = ProblemReports(dir) { now }
        assertEquals(ProblemReports.Result.Accepted("R-0001"), reports.submit(body(), "1.1.1.1"))
        forwarder.pass()
        assertEquals(ProblemReports.Result.Accepted("R-0002"), reports.submit(body(), "2.2.2.2"))
        assertEquals(setOf("R-0002.json", "issues.json", "sent", ProblemReports.LAST_NUMBER), dir.listDirectoryEntries().map { it.name }.toSet())
    }

    @Test
    fun `fingerprints ignore times and numbers, and a report with no failure has none`() {
        val a = ReportForwarder.fingerprint("t\n12:00:01 FAILED timed out after 30000 ms at 0x7ffe12ab34")
        val b = ReportForwarder.fingerprint("t\n23:59:59.123 FAILED timed out after 5000 ms at 0x1234abcd99")
        assertEquals(a, b)
        assertNotEquals(a, ReportForwarder.fingerprint("t\nFAILED something else"))
        assertNull(ReportForwarder.fingerprint("t\nall fine"))
        assertEquals(12, a!!.length)
    }

    @Test
    fun `a report too long for an issue is cut in the middle, and a fence inside can't close the block`(@TempDir dir: Path) {
        val tracker = FakeTracker()
        send(dir, body(extra = "```\n" + "y".repeat(ReportForwarder.MAX_SHOWN) + "\nTHE END"))
        ReportForwarder(dir, { tracker }).pass()
        val issue = tracker.issues.single().body
        assertTrue(issue.length < 65_536)
        assertContains(issue, "characters cut")
        assertContains(issue, "THE END")
        assertContains(issue, "\n````\n${ProblemReports.TITLE}")
    }

    @Test
    fun `GitHub is asked with the token for issues and comments in the repo and for releases in the launcher's`() {
        val seen = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            seen += "${exchange.requestMethod} ${exchange.requestURI.path} ${exchange.requestHeaders.getFirst("Authorization")} $body"
            val (code, reply) = when (exchange.requestURI.path) {
                "/repos/o/launcher/releases/tags/v1.0.5" -> 200 to "{}"
                "/repos/o/launcher/releases/tags/v9.9.9" -> 404 to "{}"
                "/repos/o/server/issues" -> 201 to """{"number":17}"""
                "/repos/o/server/issues/17" -> 200 to """{"state":"closed"}"""
                "/repos/o/server/issues/17/comments" -> 201 to "{}"
                else -> 500 to "{}"
            }
            exchange.sendResponseHeaders(code, reply.length.toLong())
            exchange.responseBody.use { it.write(reply.toByteArray()) }
        }
        server.start()
        try {
            val github = GitHubIssues("o/server", "tok123", launcherRepo = "o/launcher", api = "http://127.0.0.1:${server.address.port}")
            assertEquals(true, github.isRelease("1.0.5"))
            assertEquals(false, github.isRelease("9.9.9"))
            assertEquals(null, github.isRelease("1.2.3"))
            assertEquals(17, github.create("t", "b", listOf("bug-report", "verified")))
            assertFalse(github.isOpen(17))
            github.comment(17, "again")
            val create = seen.single { it.startsWith("POST /repos/o/server/issues ") }
            assertContains(create, "Bearer tok123")
            val json = Json.parseToJsonElement(create.substringAfter("Bearer tok123 ")).jsonObject
            assertEquals(listOf("bug-report", "verified"), json["labels"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertTrue(seen.any { it.startsWith("POST /repos/o/server/issues/17/comments Bearer tok123 {\"body\":\"again\"}") })
            // An error names what failed and never the token.
            val error = runCatching { github.comment(99, "x") }.exceptionOrNull()!!
            assertEquals("GitHub answered 500 commenting on #99", error.message)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `the token comes from its file, and an empty or missing file is no tracker`(@TempDir dir: Path) {
        val file = dir.resolve(GitHubIssues.TOKEN_FILE)
        assertNull(GitHubIssues.fromTokenFile(file))
        Files.writeString(file, "\n")
        assertNull(GitHubIssues.fromTokenFile(file))
        Files.writeString(file, "github_pat_x\n")
        assertTrue(GitHubIssues.fromTokenFile(file) != null)
    }
}
