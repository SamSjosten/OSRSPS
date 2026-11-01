package osrsps.net

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import java.time.Duration
import java.time.Instant
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
    /** The time now, for the daily cap; tests pass their own. */
    private val clock: () -> Instant = Instant::now,
) {
    private val sent: Path = dir.resolve(SENT)
    private val index: Path = dir.resolve(INDEX)
    private val budget: Path = dir.resolve(BUDGET)
    private var budgetRead = false
    private var saidBudgetUnreadable = false
    private var executor: ScheduledExecutorService? = null
    private var saidNoTracker = false
    /** When each issue and comment of the last day was posted, oldest first, against [PER_DAY]. */
    private val posted = ArrayDeque<Instant>()
    /** When the last issue saying the cap was reached was opened, so there is one a day at most. */
    private var saidCapped: Instant? = null
    /** How many passes in a row each waiting report has failed to file, by its file name. */
    private val failures = HashMap<String, Int>()

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
        var failedInARow = 0
        val failed = mutableListOf<Path>()
        val now = clock()
        if (!readBudget(now)) return 0
        while (posted.isNotEmpty() && Duration.between(posted.first(), now) >= DAY) posted.removeFirst()
        for (file in waiting) {
            if (posted.size >= PER_DAY) {
                capped(tracker, now, waiting.size - filed)
                break
            }
            try {
                // Counted the moment GitHub takes it, so a local failure after (the index, the move to sent/) can't
                // post the same report again and again outside the day's count.
                file(tracker, file, now)
                filed++
                failedInARow = 0
                failures.remove(file.name)
            } catch (e: BudgetUnsaved) {
                // Posting on with the count only in memory would let a restart post the day's worth again.
                logger.warn { "${e.message}; no reports are posted until it can be" }
                break
            } catch (e: Rejected) {
                // Trying it again would only block every report behind it; it is kept aside for a look by hand.
                logger.warn { "Problem report ${file.name} can't be filed (${e.message}); moved to $REJECTED/" }
                setAside(file)
            } catch (e: Exception) {
                // One report that fails doesn't hold up the rest; several in a row is GitHub down, so the pass stops.
                logger.warn(e) { "Problem report ${file.name} couldn't be filed; trying again next pass" }
                failed.add(file)
                if (++failedInARow >= FAILURES_IN_A_ROW) break
            }
        }
        // A failure counts against its report only when GitHub answered meanwhile (another report went, or it says
        // whether a version was released), so a long outage never sets good reports aside.
        if (failed.isNotEmpty() && (filed > 0 || tracker.isRelease(PROBE_VERSION) != null)) {
            for (file in failed) {
                val times = failures.merge(file.name, 1, Int::plus)!!
                if (times >= GIVE_UP_AFTER) {
                    logger.warn { "Problem report ${file.name} failed to file $times times while GitHub answered; moved to $REJECTED/" }
                    setAside(file)
                }
            }
        }
        failures.keys.retainAll(waiting.map { it.name }.toSet())
        return filed
    }

    /**
     * The day's posts and the last cap notice, from [BUDGET], once: a restart (each deploy is one) keeps the day's count.
     * No file is a fresh start. A file that can't be read now says false, so nothing is posted until it can be. One
     * that isn't the budget at all (or a time in it that isn't one) counts as a day already full, so a damaged file
     * never lets another day's worth through; it is then written afresh.
     */
    private fun readBudget(now: Instant): Boolean {
        if (budgetRead) return true
        if (!Files.exists(budget)) {
            budgetRead = true
            return true
        }
        val text = try {
            Files.readString(budget)
        } catch (e: java.io.IOException) {
            if (!saidBudgetUnreadable) logger.warn(e) { "$BUDGET can't be read; no reports are posted until it can" }
            saidBudgetUnreadable = true
            return false
        }
        saidBudgetUnreadable = false
        budgetRead = true
        val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        val times = (json?.get("posted") as? JsonArray)?.map { item ->
            (item as? JsonPrimitive)?.takeIf { it.isString }?.let { runCatching { Instant.parse(it.content) }.getOrNull() }
        }
        if (times == null || null in times) {
            logger.error { "$BUDGET is damaged; counting today as full ($PER_DAY posts), so reports wait a day" }
            repeat(PER_DAY) { posted.addLast(now) }
            saidCapped = now
            runCatching(::saveBudget)
            return true
        }
        times.forEach { posted.addLast(it!!) }
        saidCapped = (json["capped"] as? JsonPrimitive)?.takeIf { it.isString }?.let { runCatching { Instant.parse(it.content) }.getOrNull() }
        return true
    }

    private fun saveBudget() {
        val json = JsonObject(buildMap {
            put("posted", JsonArray(posted.map { JsonPrimitive(it.toString()) }))
            saidCapped?.let { put("capped", JsonPrimitive(it.toString())) }
        })
        writeAtomically(budget, "$json\n")
    }

    /**
     * Does [post] (an issue or a comment) against the day's count, saved in [BUDGET] before it goes: if that can't be
     * saved nothing is posted ([BudgetUnsaved]), and a post that fails gives its place back.
     */
    private fun <T> budgeted(now: Instant, post: () -> T): T {
        posted.addLast(now)
        try {
            saveBudget()
        } catch (e: Exception) {
            posted.removeLast()
            throw BudgetUnsaved(e)
        }
        try {
            return post()
        } catch (e: Exception) {
            posted.removeLast()
            runCatching(::saveBudget)
            throw e
        }
    }

    /** [BUDGET] couldn't be saved, so nothing more is posted this pass. */
    class BudgetUnsaved(cause: Exception) : Exception("The day's report posts couldn't be saved in $BUDGET (${cause.message})", cause)

    /**
     * Every issue and comment emails Sam, and anyone may send reports, so at most [PER_DAY] are posted a day: the rest
     * wait in the folder (which [ProblemReports] caps) for tomorrow, and one issue a day says so.
     */
    private fun capped(tracker: IssueTracker, now: Instant, left: Int) {
        val last = saidCapped
        if (last != null && Duration.between(last, now) < DAY) return
        // Saved before it goes, like every post, so a restart can't open a second one the same day.
        saidCapped = now
        try {
            saveBudget()
        } catch (e: Exception) {
            saidCapped = last
            logger.warn(e) { "Problem reports hit the daily cap of $PER_DAY; $BUDGET couldn't be saved, so no issue says so yet" }
            return
        }
        try {
            val number = tracker.create(
                "Problem reports: more than $PER_DAY in a day",
                "More than $PER_DAY problem reports were filed or commented in the last day, so the rest wait in the " +
                    "server's `reports/` folder ($left now) and are filed as the day's count falls. Many at once may be " +
                    "a flood rather than real problems: have a look before they all come through.",
                listOf(LABEL, LABEL_CAPPED),
            )
            logger.warn { "Problem reports hit the daily cap of $PER_DAY; $left wait; said so in issue #$number" }
        } catch (e: Exception) {
            saidCapped = last
            runCatching(::saveBudget)
            logger.warn(e) { "Problem reports hit the daily cap of $PER_DAY; couldn't open an issue to say so" }
        }
    }

    private fun setAside(file: Path) {
        failures.remove(file.name)
        Files.createDirectories(dir.resolve(REJECTED))
        Files.move(file, dir.resolve(REJECTED).resolve(file.name), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun file(tracker: IssueTracker, file: Path, now: Instant) {
        val report = Json.parseToJsonElement(Files.readString(file)).jsonObject
        val id = report.string("id") ?: file.name.removeSuffix(".json")
        val body = report.string("body").orEmpty()
        val launcher = report.string("launcher").orEmpty()
        val check = check(tracker, launcher, body)
        val print = fingerprint(body)
        val known = readIndex()
        val existing = print?.let { known[it] }?.takeIf(tracker::isOpen)
        val text = issueBody(id, report.string("received"), check, print, body)
        val commented = existing != null && try {
            budgeted(now) { tracker.comment(existing, text) }
            true
        } catch (e: Gone) {
            logger.info { "Problem report $id matches issue #$existing, which ${e.message}; filing a new one" }
            false
        }
        if (commented) {
            logger.info { "Problem report $id is the same failure as issue #$existing; commented there" }
        } else {
            val number = budgeted(now) { tracker.create(title(id, launcher, body), text, check.labels) }
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
        val shown = shorten(body).replace(LONG_BACKTICKS, "`".repeat(FENCE - 1) + "[…]")
        val fence = "`".repeat(FENCE)
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
        writeAtomically(index, "$json\n")
    }

    private fun writeAtomically(file: Path, text: String) {
        val temp = Files.createTempFile(dir, ".${file.name}", ".tmp")
        try {
            Files.writeString(temp, text)
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
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

    /** The tracker will never take this report (GitHub's 400 or 422): it is set aside, not tried again. */
    class Rejected(message: String) : Exception(message)

    /** The issue a report matched takes no comments any more (locked, deleted, moved): the report gets a new one. */
    class Gone(message: String) : Exception(message)

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
        const val REJECTED = "rejected"
        const val INDEX = "issues.json"
        /** The day's posts and the last cap notice, so a restart doesn't start the day's count again. */
        const val BUDGET = "posted.json"
        /** Asked of the releases only to find out whether GitHub answers at all. */
        private const val PROBE_VERSION = "0.0.0"
        const val LABEL = "bug-report"
        /**
         * The launcher's version is one we released and its revision is the server's. Not proof the report came from
         * our launcher: anyone may claim a released version, so a report's text is never to be trusted.
         */
        const val LABEL_VERIFIED = "known-version"
        const val LABEL_CAPPED = "report-cap"
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

        // Guessed: GitHub failing three reports running is GitHub down, not the reports; an hour of failing passes
        // is a report GitHub won't ever take, so it is set aside rather than tried once a minute forever.
        const val FAILURES_IN_A_ROW = 3
        const val GIVE_UP_AFTER = 60

        // Guessed: a handful of friends file a few a week; 20 a day is far above that and a day's flood stays readable.
        const val PER_DAY = 20
        private val DAY: Duration = Duration.ofDays(1)

        /**
         * The report sits in a fence of this many backticks, and any longer run inside it is cut short, so no log line
         * can close the block and the fence costs a fixed few characters, whatever the report holds.
         */
        const val FENCE = 10
        private val LONG_BACKTICKS = Regex("`{$FENCE,}")

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
