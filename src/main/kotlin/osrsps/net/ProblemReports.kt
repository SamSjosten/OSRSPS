package osrsps.net

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private val logger = KotlinLogging.logger {}

/**
 * Problem reports the Odin's Gate launcher sends with its "Send problem report" button, kept in [dir] (the data
 * folder's `reports/`, outside git) as one JSON file each, `R-0001.json` and on, until something forwards them. The
 * launcher builds the text (its ProblemReport, which already leaves out passwords, the data folder and the command
 * line); this side doesn't trust that and scrubs it again ([scrub]).
 *
 * The HTTP port is reachable from the internet through the relay, so a report is anyone's to send: [submit] caps how
 * big one is ([MAX_BYTES], which the HTTP side enforces before the body is read), how many one address may send in an
 * hour ([PER_ADDRESS_PER_HOUR]), how many everyone together may send in an hour ([PER_HOUR]), and how many may wait
 * here unforwarded ([MAX_WAITING]). Every report sent counts towards its address's limit, taken or not, so an address
 * the server keeps refusing is still held to it; at most [TRACKED] addresses are remembered, so the memory they take
 * is bounded, and past that a new address is refused rather than an old one's count forgotten early.
 * The HTTP side asks [admits] before it reads a body. A sender's address is used for the limits and never written
 * down. The waiting reports are counted at most every [RECOUNT], not for every report.
 *
 * Called from the HTTP server's one Netty thread, and synchronized all the same so a second caller can't share a
 * number.
 */
class ProblemReports(
    private val dir: Path,
    /** The time now; tests pass their own so the hourly limits never depend on the wall clock. */
    private val clock: () -> Instant = Instant::now,
) {
    /** When each accepted report came in, oldest first. */
    private val accepted = ArrayDeque<Instant>()
    /**
     * For each address that sent a report in the last hour, taken or not, when its latest few came in, oldest first:
     * the per-address limit counts these, so an address the server refuses for any reason is still held to it. Each
     * keeps at most [PER_ADDRESS_PER_HOUR], and at most [TRACKED] addresses are kept; past that a new address is
     * refused rather than any address's count let go before its hour is up.
     */
    private val attempts = HashMap<String, ArrayDeque<Instant>>()
    private var lastNumber = 0
    private var waiting = 0
    private var scanned = false
    private var countedAt: Instant? = null

    sealed interface Result {
        data class Accepted(val id: String) : Result
        /** Not text the launcher builds: no title line, or no version. */
        data object NotAReport : Result
        data object TooLarge : Result
        data object TooMany : Result
        /** Too many reports wait unforwarded, or this one couldn't be written. */
        data object Unavailable : Result
    }

    /**
     * Whether a report from [address] would be let past the hourly limits now, asked before its body is read so a
     * sender over them is turned away without the server holding the body. Records nothing.
     */
    @Synchronized
    fun admits(address: String): Boolean {
        forget(clock())
        return accepted.size < PER_HOUR && underLimit(address)
    }

    private fun underLimit(address: String): Boolean {
        val sent = attempts[address] ?: return attempts.size < TRACKED
        return sent.size < PER_ADDRESS_PER_HOUR
    }

    /** Takes the body of a POST /report from [address] (the sender's real address, a PROXY header's when relayed). */
    @Synchronized
    fun submit(body: ByteArray, address: String): Result {
        if (body.size > MAX_BYTES) return Result.TooLarge
        val now = clock()
        forget(now)
        if (!underLimit(address)) {
            // Still refused while it keeps sending: its newest stands in for its oldest, so its hour starts again.
            attempts[address]?.let { it.removeFirst(); it.addLast(now) }
            return Result.TooMany
        }
        attempts.getOrPut(address, ::ArrayDeque).addLast(now)
        if (accepted.size >= PER_HOUR) return Result.TooMany

        val text = String(body, Charsets.UTF_8)
        val lines = text.lines()
        if (lines.firstOrNull()?.startsWith(TITLE) != true) return Result.NotAReport
        val launcher = lines.firstNotNullOfOrNull { LAUNCHER_LINE.matchEntire(it)?.groupValues?.get(1) }
            ?: return Result.NotAReport

        try {
            count(now)
            if (waiting >= MAX_WAITING) return Result.Unavailable
            val id = "R-%04d".format(lastNumber + 1)
            val json = buildJsonObject {
                put("id", id)
                put("received", now.toString())
                put("launcher", launcher)
                put("body", scrub(text))
            }
            Files.createDirectories(dir)
            writeAtomically(dir.resolve("$id.json"), "$json\n")
            lastNumber++
            waiting++
            writeAtomically(dir.resolve(LAST_NUMBER), "$lastNumber\n")
            accepted.addLast(now)
            logger.info { "Problem report $id from launcher $launcher saved in ${dir.toAbsolutePath()}" }
            return Result.Accepted(id)
        } catch (e: Exception) {
            logger.warn(e) { "A problem report couldn't be saved in ${dir.toAbsolutePath()}" }
            return Result.Unavailable
        }
    }

    /** Lets go of what is more than an hour old. */
    private fun forget(now: Instant) {
        while (accepted.isNotEmpty() && Duration.between(accepted.first(), now) >= HOUR) accepted.removeFirst()
        val each = attempts.values.iterator()
        while (each.hasNext()) {
            val sent = each.next()
            while (sent.isNotEmpty() && Duration.between(sent.first(), now) >= HOUR) sent.removeFirst()
            if (sent.isEmpty()) each.remove()
        }
    }

    /**
     * Makes sure the count of reports waiting is fresh enough: the whole folder is read once ([scan]), then only the
     * reports waiting at its top are counted again, at most every [RECOUNT] (a forwarder or Sam moves them away
     * meanwhile), and the count goes up by one with each report taken in between.
     */
    private fun count(now: Instant) {
        if (!scanned) {
            scan()
            scanned = true
            countedAt = now
            return
        }
        val last = countedAt
        if (last != null && Duration.between(last, now) < RECOUNT) return
        waiting = if (Files.isDirectory(dir)) dir.listDirectoryEntries().count { FILE_NAME.matches(it.name) } else 0
        countedAt = now
    }

    /**
     * Counts the reports still waiting here, and finds the highest number given so far: the most of the one in
     * memory, the one last written to [LAST_NUMBER], and the highest report file here or in any folder under [dir]
     * (where [ReportForwarder] moves reports it has filed). So a number is never given twice, even after a report is
     * deleted or the server restarts. After that the number is kept in memory and in [LAST_NUMBER].
     */
    private fun scan() {
        val written = runCatching { Files.readString(dir.resolve(LAST_NUMBER)).trim().toInt() }.getOrDefault(0)
        var highest = maxOf(lastNumber, written)
        var count = 0
        if (Files.isDirectory(dir)) {
            for (entry in dir.listDirectoryEntries()) {
                val files = if (Files.isDirectory(entry)) entry.listDirectoryEntries() else listOf(entry)
                for (file in files) {
                    val number = FILE_NAME.matchEntire(file.name)?.groupValues?.get(1)?.toInt() ?: continue
                    highest = maxOf(highest, number)
                    if (file.parent == dir) count++
                }
            }
        }
        lastNumber = highest
        waiting = count
    }

    /** Writes [text] to a temporary file and moves it over [file], so a crash mid-write never leaves half of it. */
    private fun writeAtomically(file: Path, text: String) {
        val temp = Files.createTempFile(dir, ".${file.name}", ".tmp")
        try {
            Files.writeString(temp, text)
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    companion object {
        /** The file in [dir] that keeps the last number given, so a deleted report's number isn't given again. */
        const val LAST_NUMBER = "last-number.txt"

        /** The data folder's folder for reports. */
        const val FOLDER = "reports"

        /** The first line of every report, as the launcher writes it (Names.APP_NAME + " problem report"). */
        const val TITLE = "Odin's Gate problem report"

        /** Room for the launcher's two 64 KB log tails and the rest, with plenty to spare. */
        const val MAX_BYTES = 256 * 1024

        // Guessed: a handful of friends play, so these are far above what they'd send and low enough to stop a flood.
        const val PER_ADDRESS_PER_HOUR = 5
        const val PER_HOUR = 50
        const val MAX_WAITING = 500

        /** How many addresses the per-address limit remembers at most, so a flood from many can't fill memory. */
        const val TRACKED = 2_000

        /** How long a count of the waiting reports is trusted before the folder is counted again. */
        val RECOUNT: Duration = Duration.ofMinutes(1)

        private val HOUR: Duration = Duration.ofHours(1)
        private val LAUNCHER_LINE = Regex("launcher (\\d{1,4}\\.\\d{1,4}\\.\\d{1,4})")
        private val FILE_NAME = Regex("R-(\\d{1,9})\\.json")

        /** The words the launcher's own scrub drops a line for; kept the same so the two agree. */
        private val SECRET = Regex("password|passwd|secret|token|authorization", RegexOption.IGNORE_CASE)
        const val LEFT_OUT = "[a line that may hold a password was left out]"

        /**
         * A user's home folder on Windows, macOS or Linux, whose name is often the person's own. The name runs to the
         * next separator, quote or the end of the line, spaces and apostrophes included, since names may hold them;
         * a path at the end of a sentence loses the rest of the line too, which hides more, never less.
         */
        private val HOME_FOLDER = Regex("""(?i)([A-Z]:[\\/]+Users[\\/]+|/Users/|/home/)[^\\/"]+""")

        /** Control characters other than tab, which a log line may carry and an issue shouldn't. */
        private val CONTROL = Regex("[\\u0000-\\u0008\\u000B-\\u001F\\u007F]")

        /**
         * The report as it is kept: a line that mentions a password (or a token, a secret, an authorization) is
         * replaced whole, a home folder's name becomes `<user>`, control characters go, and line breaks become `\n`.
         */
        fun scrub(text: String): String = text.lines().joinToString("\n") { raw ->
            val line = raw.replace(CONTROL, "")
            if (SECRET.containsMatchIn(line)) LEFT_OUT else line.replace(HOME_FOLDER) { "${it.groupValues[1]}<user>" }
        }
    }
}
