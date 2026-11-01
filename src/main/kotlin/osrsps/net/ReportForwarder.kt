package osrsps.net

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.Revision
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private val logger = KotlinLogging.logger {}

/**
 * Turns the problem reports [ProblemReports] saved in [dir] into issues, then moves each into [dir]/`sent`. Before it
 * files one it checks it ([check]): that the launcher's version is one we released, and that the report's revision is
 * the server's, and labels the issue for what it found. It adds what the server knows as it files ([facts]: its commit,
 * revision and whether the game is up). Reports of the same failure share a [fingerprint]: the second and later become
 * comments on the first one's issue while it is open, so one bug is one issue.
 *
 * The tracker is made from the token file each pass ([tracker]), so a token put in place later is picked up without a
 * restart; without one the reports wait. A report that fails to file stays and is tried again next pass.
 */
class ReportForwarder(
    private val dir: Path,
    /** The tracker to file in, or null while there is none (no token yet). Called once a pass. */
    private val tracker: () -> IssueTracker?,
    /** What the server knows now, a line each, added to every issue and comment. */
    private val facts: () -> List<String> = { emptyList() },
) {
    private val sent: Path = dir.resolve(SENT)
    private val index: Path = dir.resolve(INDEX)
    private var executor: ScheduledExecutorService? = null
    private var saidNoTracker = false

    /** Every pass: files what waits. Returns how many reports were filed. Runs on one thread only. */
    fun pass(): Int {
        val waiting = if (Files.isDirectory(dir)) dir.listDirectoryEntries().filter { REPORT.matches(it.name) }.sortedBy { it.name } else emptyList()
        if (waiting.isEmpty()) return 0
        val tracker = tracker()
        if (tracker == null) {
            if (!saidNoTracker) logger.info { "${waiting.size} problem report(s) wait in ${dir.toAbsolutePath()}: no GitHub token yet" }
            saidNoTracker = true
            return 0
        }
        saidNoTracker = false
        var filed = 0
        for (file in waiting) {
            try {
                file(tracker, file)
                filed++
            } catch (e: Exception) {
                logger.warn(e) { "Problem report ${file.name} couldn't be filed; trying again next pass" }
                break
            }
        }
        return filed
    }

    private fun file(tracker: IssueTracker, file: Path) {
        val report = Json.parseToJsonElement(Files.readString(file)).jsonObject
        val id = report.string("id") ?: file.name.removeSuffix(".json")
        val body = report.string("body").orEmpty()
        val launcher = report.string("launcher").orEmpty()
        val check = check(tracker, launcher, body)
        val print = fingerprint(body)
        val known = readIndex()
        val existing = print?.let { known[it] }?.takeIf(tracker::isOpen)
        val text = issueBody(id, report.string("received"), check, print, body)
        if (existing != null) {
            tracker.comment(existing, text)
            logger.info { "Problem report $id is the same failure as issue #$existing; commented there" }
        } else {
            val number = tracker.create(title(id, launcher, body), text, check.labels)
            if (print != null) writeIndex(known + (print to number))
            logger.info { "Problem report $id filed as issue #$number" }
        }
        Files.createDirectories(sent)
        Files.move(file, sent.resolve(file.name), StandardCopyOption.REPLACE_EXISTING)
    }

    /** What [check] found: a line each for the issue, and the labels it earns. */
    data class Check(val lines: List<String>, val labels: List<String>)

    internal fun check(tracker: IssueTracker, launcher: String, body: String): Check {
        val lines = mutableListOf<String>()
        val labels = mutableListOf(LABEL)
        val released = if (launcher in LEGACY_RELEASES) true else tracker.isRelease(launcher)
        when (released) {
            true -> lines += "launcher $launcher: a released version"
            false -> { lines += "launcher $launcher: NOT a released version (a dev build, or made up)"; labels += LABEL_UNRELEASED }
            null -> { lines += "launcher $launcher: couldn't check the releases"; labels += LABEL_UNCHECKED }
        }
        val revision = REVISION_LINE.find(body)?.groupValues?.get(1)
        when {
            revision == null -> lines += "revision: the launcher hadn't seen one yet"
            revision.toInt() == Revision.MAJOR -> lines += "revision $revision: the server's"
            else -> { lines += "revision $revision: NOT the server's ${Revision.MAJOR}"; labels += LABEL_REVISION }
        }
        if (labels.size == 1) labels += LABEL_VERIFIED
        return Check(lines, labels)
    }

    private fun issueBody(id: String, received: String?, check: Check, print: String?, body: String): String = buildString {
        appendLine("Problem report **$id**, sent from the Odin's Gate launcher${received?.let { " at $it" }.orEmpty()}.")
        appendLine()
        appendLine("Checks:")
        for (line in check.lines) appendLine("- $line")
        val now = facts()
        if (now.isNotEmpty()) {
            appendLine()
            appendLine("The server when it was filed:")
            for (line in now) appendLine("- $line")
        }
        appendLine()
        appendLine("Fingerprint: `${print ?: "none (no failure line found)"}`")
        appendLine()
        val shown = shorten(body)
        val fence = "`".repeat(maxOf(3, (Regex("`+").findAll(shown).maxOfOrNull { it.value.length } ?: 0) + 1))
        appendLine(fence)
        appendLine(shown)
        appendLine(fence)
        if (shown.length < body.length) appendLine("The report was cut to fit; the whole of it is `reports/$SENT/$id.json` in the server's data folder.")
    }

    private fun readIndex(): Map<String, Int> =
        if (!Files.exists(index)) emptyMap()
        else Json.parseToJsonElement(Files.readString(index)).jsonObject.mapValues { it.value.jsonPrimitive.int }

    private fun writeIndex(map: Map<String, Int>) {
        val json = JsonObject(map.toSortedMap().mapValues { JsonPrimitive(it.value) })
        val temp = Files.createTempFile(dir, ".issues", ".tmp")
        try {
            Files.writeString(temp, "$json\n")
            Files.move(temp, index, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    /** Runs a pass every [PERIOD_SECONDS] on a thread of its own, so GitHub's answers never hold the game or HTTP up. */
    fun start() {
        val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "report-forwarder").apply { isDaemon = true } }
        executor.scheduleWithFixedDelay({ runCatching(::pass).onFailure { logger.warn(it) { "Report forwarding failed" } } },
            FIRST_PASS_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS)
        this.executor = executor
    }

    fun stop() {
        executor?.shutdownNow()
        executor = null
    }

    /** Files and comments on issues, and knows which launcher versions were released. */
    interface IssueTracker {
        /** Whether [version] is a released launcher; null when that couldn't be found out. */
        fun isRelease(version: String): Boolean?
        fun isOpen(issue: Int): Boolean
        /** Opens an issue and returns its number. */
        fun create(title: String, body: String, labels: List<String>): Int
        fun comment(issue: Int, body: String)
    }

    companion object {
        const val SENT = "sent"
        const val INDEX = "issues.json"
        const val LABEL = "bug-report"
        const val LABEL_VERIFIED = "verified"
        const val LABEL_UNRELEASED = "unreleased-launcher"
        const val LABEL_UNCHECKED = "unchecked-launcher"
        const val LABEL_REVISION = "revision-mismatch"

        /** The two versions released from the server repository before the launcher had its own; see OdinsGate. */
        val LEGACY_RELEASES = setOf("1.0.0", "1.0.1")

        // Guessed: a minute is quick enough for a friend to see the issue soon after sending, and light on GitHub.
        const val PERIOD_SECONDS = 60L
        private const val FIRST_PASS_SECONDS = 10L

        /** GitHub refuses an issue body over 65,536 characters; this leaves room for the rest of the issue. */
        const val MAX_SHOWN = 60_000

        private val REPORT = Regex("R-\\d{1,9}\\.json")
        private val REVISION_LINE = Regex("(?m)^revision (\\d{1,4})\\b")
        private val FAILURE = Regex("(?i)\\b(FAILED|exception|error)\\b")
        private val TIMESTAMP = Regex("^\\S*\\d{2}:\\d{2}:\\d{2}\\S*\\s+")
        private val HEX = Regex("\\b(?:0x)?[0-9a-fA-F]{6,}\\b")
        private val NUMBER = Regex("\\d+")

        private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        /**
         * The first line that names a failure, with its time, hex values and numbers taken out so the same failure
         * on another day or machine matches: the first 12 hex digits of its SHA-256. Null when no line names one.
         */
        fun fingerprint(body: String): String? {
            val line = body.lines().drop(1).firstOrNull { FAILURE.containsMatchIn(it) } ?: return null
            val shape = line.trim().replace(TIMESTAMP, "").replace(HEX, "#").replace(NUMBER, "#")
            return MessageDigest.getInstance("SHA-256").digest(shape.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }.take(12)
        }

        /** "Problem report R-0001: <the failure line>", or the launcher's version and system when there is none. */
        fun title(id: String, launcher: String, body: String): String {
            val failure = body.lines().drop(1).firstOrNull { FAILURE.containsMatchIn(it) }?.trim()?.replace(TIMESTAMP, "")
            val system = body.lines().firstOrNull { it.startsWith("system ") }?.removePrefix("system ")?.substringBefore(',')
            val what = failure ?: "launcher $launcher${system?.let { " on $it" }.orEmpty()}"
            return "Problem report $id: ${if (what.length > 100) what.take(99) + "…" else what}"
        }

        /** The report, or when it is too long for an issue, its start and its end around a note that it was cut. */
        fun shorten(body: String): String {
            if (body.length <= MAX_SHOWN) return body
            val cut = "\n[… ${body.length - MAX_SHOWN} characters cut …]\n"
            val head = MAX_SHOWN / 3
            return body.take(head) + cut + body.takeLast(MAX_SHOWN - head - cut.length)
        }
    }
}
