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
import kotlin.test.assertFailsWith
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
        /** Whether GitHub fails every request, as when it is down. */
        var down = false
        var attempts = 0
        /** Issues Sam deleted, and issues he locked. */
        val deleted = mutableSetOf<Int>()
        val locked = mutableSetOf<Int>()
        /** A report GitHub fails to file every time, whatever is wrong with it. */
        var failing: (String) -> Boolean = { false }
        /** Every issue is made, but the answer saying so is lost. */
        var answerLost = false

        override fun isRelease(version: String) = if (releasesKnown && !down) version in releases else null
        override fun isOpen(issue: Int) = issue !in deleted && issues[issue - 1].open
        override fun create(title: String, body: String, labels: List<String>): Int {
            attempts++
            if (down || failing(body)) throw ReportForwarder.NotPosted("GitHub answered 502")
            if (failNext) { failNext = false; throw ReportForwarder.NotPosted("GitHub answered 502") }
            issues += Issue(title, body, labels)
            // GitHub took it, but its answer never came back.
            if (answerLost) throw IOException("request timed out")
            return issues.size
        }
        override fun comment(issue: Int, body: String) {
            if (issue in locked) throw ReportForwarder.Gone("issue #$issue takes no comments (403)")
            issues[issue - 1].comments += body
        }
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
        assertEquals(listOf("bug-report", "known-version"), issue.labels)
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
        assertEquals(listOf("bug-report", "known-version"), tracker.issues[2].labels)

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
        assertEquals(setOf("R-0002.json", "issues.json", ReportForwarder.BUDGET, "sent", ProblemReports.LAST_NUMBER), dir.listDirectoryEntries().map { it.name }.toSet())
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
        assertContains(issue, "\n${"`".repeat(ReportForwarder.FENCE)}\n${ProblemReports.TITLE}")
    }

    @Test
    fun `a report full of backticks still fits an issue, and one GitHub refuses is set aside, not retried forever`(@TempDir dir: Path) {
        val tracker = FakeTracker()
        send(dir, body(extra = "`".repeat(59_000)), "1.1.1.1")
        ReportForwarder(dir, { tracker }).pass()
        val issue = tracker.issues.single().body
        assertTrue(issue.length < 65_536, "${issue.length}")
        assertFalse(issue.contains("`".repeat(ReportForwarder.FENCE + 1)))

        val refusing = object : ReportForwarder.IssueTracker by tracker {
            var refused = 0
            override fun create(title: String, body: String, labels: List<String>): Int {
                if (refused++ == 0) throw ReportForwarder.Rejected("GitHub refused opening an issue (422)")
                return tracker.create(title, body, labels)
            }
        }
        val bad = send(dir, body(failure = "FAILED bad"), "2.2.2.2")
        val good = send(dir, body(failure = "FAILED good"), "3.3.3.3")
        assertEquals(1, ReportForwarder(dir, { refusing }).pass())
        assertTrue(Files.exists(dir.resolve("rejected/$bad.json")))
        assertTrue(Files.exists(dir.resolve("sent/$good.json")), "the report behind it still went")
    }

    @Test
    fun `a matching issue Sam deleted or locked gets a new issue, and nothing waits behind it`(@TempDir dir: Path) {
        val tracker = FakeTracker()
        val forwarder = ReportForwarder(dir, { tracker })
        send(dir, body(failure = "ERROR spam 1"), from = "203.0.113.1")
        assertEquals(1, forwarder.pass())
        tracker.deleted += 1
        send(dir, body(failure = "ERROR spam 2"), from = "203.0.113.1")
        send(dir, body(failure = "ERROR the client crashed at login"), from = "198.51.100.7")
        assertEquals(2, forwarder.pass())
        assertEquals(3, tracker.issues.size, "the deleted one's match is a new issue; the friend's has its own")
        assertTrue(waiting(dir).isEmpty())

        tracker.locked += 2
        send(dir, body(failure = "ERROR spam 3"), from = "203.0.113.2")
        assertEquals(1, forwarder.pass())
        assertEquals(4, tracker.issues.size, "a locked issue's match is a new issue too")
        // The index now points at the new issue, so the next match is a comment there.
        send(dir, body(failure = "ERROR spam 4"), from = "203.0.113.3")
        assertEquals(1, forwarder.pass())
        assertEquals(1, tracker.issues[3].comments.size)
    }

    @Test
    fun `a report that keeps failing doesn't hold up the rest, and is set aside after an hour of passes`(@TempDir dir: Path) {
        val tracker = FakeTracker().apply { failing = { it.contains("poison") } }
        val forwarder = ReportForwarder(dir, { tracker })
        val poison = send(dir, body(failure = "ERROR poison"), from = "203.0.113.1")
        send(dir, body(failure = "ERROR the client crashed at login"), from = "198.51.100.7")
        assertEquals(1, forwarder.pass(), "the friend's report behind it is filed")
        assertEquals(listOf("$poison.json"), waiting(dir))
        repeat(ReportForwarder.GIVE_UP_AFTER - 2) { assertEquals(0, forwarder.pass()) }
        assertEquals(listOf("$poison.json"), waiting(dir))
        forwarder.pass()
        assertTrue(waiting(dir).isEmpty())
        assertTrue(Files.exists(dir.resolve(ReportForwarder.REJECTED).resolve("$poison.json")))
    }

    @Test
    fun `while GitHub is down a pass stops after a few failures instead of trying every report`(@TempDir dir: Path) {
        val tracker = FakeTracker().apply { down = true }
        val forwarder = ReportForwarder(dir, { tracker })
        repeat(6) { send(dir, body(failure = "ERROR number $it went wrong ${"x".repeat(it)}"), from = "203.0.113.$it") }
        assertEquals(0, forwarder.pass())
        assertEquals(ReportForwarder.FAILURES_IN_A_ROW, tracker.attempts)
        // However long it stays down, nothing is set aside for it.
        repeat(ReportForwarder.GIVE_UP_AFTER + 5) { forwarder.pass() }
        assertEquals(6, waiting(dir).size, "none is set aside for GitHub being down")
        tracker.down = false
        assertEquals(6, forwarder.pass())
    }

    @Test
    fun `at most so many are posted a day, the rest wait, and one issue a day says so`(@TempDir dir: Path) {
        var clock = now
        val tracker = FakeTracker()
        val forwarder = ReportForwarder(dir, { tracker }, clock = { clock })
        val extra = 5
        repeat(ReportForwarder.PER_DAY + extra) { i ->
            send(dir, body(failure = "ERROR flood ${"y".repeat(i)}"), from = "10.0.${i / 4}.${i % 4}")
        }
        assertEquals(ReportForwarder.PER_DAY, forwarder.pass())
        val notice = tracker.issues.last()
        assertEquals(listOf(ReportForwarder.LABEL, ReportForwarder.LABEL_CAPPED), notice.labels)
        assertContains(notice.body, "($extra now)")
        assertEquals(extra, waiting(dir).size)
        clock += java.time.Duration.ofHours(23)
        assertEquals(0, forwarder.pass())
        assertEquals(ReportForwarder.PER_DAY + 1, tracker.issues.size, "one notice a day, not one a pass")
        // A restart (every deploy is one) keeps the day's count and its notice.
        val restarted = ReportForwarder(dir, { tracker }, clock = { clock })
        assertEquals(0, restarted.pass())
        assertEquals(ReportForwarder.PER_DAY + 1, tracker.issues.size)
        clock += java.time.Duration.ofHours(1)
        assertEquals(extra, restarted.pass())
        assertTrue(waiting(dir).isEmpty())
    }

    @Test
    fun `a budget file that can't be read holds reports back, and a damaged one counts as a full day`(@TempDir dir: Path) {
        var clock = now
        val tracker = FakeTracker()
        send(dir, body(failure = "ERROR budget"))
        // Unreadable (here a folder where the file should be): nothing goes until it can be read.
        val budget = dir.resolve(ReportForwarder.BUDGET)
        java.nio.file.Files.createDirectory(budget)
        val forwarder = ReportForwarder(dir, { tracker }, clock = { clock })
        assertEquals(0, forwarder.pass())
        assertEquals(1, waiting(dir).size)
        java.nio.file.Files.delete(budget)
        assertEquals(1, forwarder.pass())
        // Damaged: the next process counts the day as full, opens no notice, and posts again a day later.
        send(dir, body(failure = "ERROR budget again"))
        java.nio.file.Files.writeString(budget, "{\"posted\": [\"not a time\"]")
        val restarted = ReportForwarder(dir, { tracker }, clock = { clock })
        val issues = tracker.issues.size
        assertEquals(0, restarted.pass())
        assertEquals(issues, tracker.issues.size, "no notice for a day it didn't count")
        assertEquals(1, waiting(dir).size)
        clock += java.time.Duration.ofHours(24)
        assertEquals(1, restarted.pass())
        assertTrue(waiting(dir).isEmpty())
    }

    @Test
    fun `a report GitHub took but that couldn't be moved to sent still counts against the day`(@TempDir dir: Path) {
        val tracker = FakeTracker()
        val forwarder = ReportForwarder(dir, { tracker }, clock = { now })
        send(dir, body(failure = "ERROR stuck"))
        // A file where sent/ should be: every move fails after GitHub has taken the report.
        java.nio.file.Files.writeString(dir.resolve("sent"), "")
        repeat(ReportForwarder.PER_DAY + 5) { forwarder.pass() }
        val posts = tracker.issues.count { ReportForwarder.LABEL_CAPPED !in it.labels } + tracker.issues.sumOf { it.comments.size }
        assertEquals(ReportForwarder.PER_DAY, posts)
    }

    @Test
    fun `while the day's count can't be saved nothing is posted, and posting resumes once it can`(@TempDir dir: Path) {
        val tracker = FakeTracker()
        val forwarder = ReportForwarder(dir, { tracker }, clock = { now })
        send(dir, body(failure = "ERROR first"))
        assertEquals(1, forwarder.pass())
        // A folder with something in it where the file goes: saving it fails (as on a full disk).
        val budget = dir.resolve(ReportForwarder.BUDGET)
        java.nio.file.Files.delete(budget)
        java.nio.file.Files.createDirectories(budget.resolve("in-the-way"))
        send(dir, body(failure = "ERROR second"))
        val posts = tracker.issues.size
        repeat(3) { assertEquals(0, forwarder.pass()) }
        assertEquals(posts, tracker.issues.size)
        assertEquals(0, tracker.attempts - posts, "GitHub wasn't even asked")
        java.nio.file.Files.delete(budget.resolve("in-the-way"))
        java.nio.file.Files.delete(budget)
        assertEquals(1, forwarder.pass())
        assertTrue(waiting(dir).isEmpty())
        assertTrue(java.nio.file.Files.readString(budget).contains("posted"))
    }

    @Test
    fun `a post whose answer was lost still counts against the day, since GitHub may have it`(@TempDir dir: Path) {
        val tracker = FakeTracker().apply { answerLost = true }
        val forwarder = ReportForwarder(dir, { tracker }, clock = { now })
        send(dir, body(failure = "ERROR lost"))
        repeat(ReportForwarder.PER_DAY + 5) { forwarder.pass() }
        assertEquals(ReportForwarder.PER_DAY, tracker.issues.count { ReportForwarder.LABEL_CAPPED !in it.labels })
    }

    @Test
    fun `GitHub that can't be reached or answers an error surely has nothing, so the post isn't counted`() {
        val closed = java.net.ServerSocket(0).use { it.localPort }
        val github = GitHubIssues("o/server", "tok123", api = "http://127.0.0.1:$closed")
        assertFailsWith<ReportForwarder.NotPosted> { github.create("t", "b", emptyList()) }
    }

    @Test
    fun `a post is dated when GitHub answered, so a slow pass's posts don't leave the day early`(@TempDir dir: Path) {
        var clock = now
        // Each issue takes GitHub ten minutes to answer.
        val tracker = object : ReportForwarder.IssueTracker by FakeTracker() {
            override fun create(title: String, body: String, labels: List<String>): Int {
                clock += java.time.Duration.ofMinutes(10)
                return 1
            }
        }
        val forwarder = ReportForwarder(dir, { tracker }, clock = { clock })
        send(dir, body(failure = "ERROR slow"))
        assertEquals(1, forwarder.pass())
        val saved = java.nio.file.Files.readString(dir.resolve(ReportForwarder.BUDGET))
        assertContains(saved, (now + java.time.Duration.ofMinutes(10)).toString())
    }

    private fun waiting(dir: Path) = dir.listDirectoryEntries().map { it.name }.filter { it.startsWith("R-") }.sorted()

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
                "/repos/o/server/issues/422/comments" -> 422 to "{}"
                "/repos/o/server/issues/18" -> 410 to """{"message":"This issue was deleted"}"""
                "/repos/o/server/issues/19" -> 301 to "{}"
                "/repos/o/server/issues/20/comments" -> 403 to """{"message":"locked"}"""
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
            assertEquals(17, github.create("t", "b", listOf("bug-report", "known-version")))
            assertFalse(github.isOpen(17))
            github.comment(17, "again")
            val create = seen.single { it.startsWith("POST /repos/o/server/issues ") }
            assertContains(create, "Bearer tok123")
            val json = Json.parseToJsonElement(create.substringAfter("Bearer tok123 ")).jsonObject
            assertEquals(listOf("bug-report", "known-version"), json["labels"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertTrue(seen.any { it.startsWith("POST /repos/o/server/issues/17/comments Bearer tok123 {\"body\":\"again\"}") })
            // An error names what failed and never the token.
            val error = runCatching { github.comment(99, "x") }.exceptionOrNull()!!
            assertEquals("GitHub answered 500 commenting on #99", error.message)
            assertTrue(runCatching { github.comment(422, "x") }.exceptionOrNull() is ReportForwarder.Rejected)
            // Deleted or moved isn't open, and a locked issue's comment is refused as gone, so a new issue is filed.
            assertFalse(github.isOpen(18))
            assertFalse(github.isOpen(19))
            assertTrue(runCatching { github.comment(20, "x") }.exceptionOrNull() is ReportForwarder.Gone)
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
        Files.writeString(file, "github_pat_11ABCDEFG0123456789_abcdefXYZ\n")
        assertTrue(GitHubIssues.fromTokenFile(file) != null)
        // A second line pasted after it, or a space, would break the header or print the token: no tracker.
        Files.writeString(file, "github_pat_11ABCDEFG0123456789_abcdefXYZ\nsomething else\n")
        assertNull(GitHubIssues.fromTokenFile(file))
        Files.writeString(file, "Bearer github_pat_11ABCDEFG0123456789_abcdefXYZ")
        assertNull(GitHubIssues.fromTokenFile(file))
    }
}
