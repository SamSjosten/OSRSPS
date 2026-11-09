package osrsps.game.social

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import osrsps.game.Player
import osrsps.game.SaveStore
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Report abuse: the report screen (interface reportabuse, 875) opened from Report on a player, which names them, or from
 * the chatbox's Report button, which asks for a name; the rule picked there comes back as SEND_SNAPSHOT and is kept for
 * admins. Live sends a report to Jagex; here it is one JSON line in [file] (data/reports.jsonl on the server, outside
 * git) and the last [RECENT] in memory, which `::reports` lists. Each holds the time, the reporter, the name reported,
 * the rule and the reported player's last [CHAT_LINES] public chat lines that the server heard (live's report carries
 * the recent chat the reporter's client saw).
 *
 * The screen and its rule buttons are the client's own (client script reportabuse_init, run with the name). The
 * report is taken only while the screen is open, or just after its close button closed it, one per opening: the
 * client's rule select script sends the report and then triggers the close button, and the real client's close click
 * can reach the server first (seen on VM 215, 2026-11-01), so a report up to [CLOSE_GRACE] after that close is taken
 * too. Anything else is a client that didn't open it. The ignore tick box is the
 * client's too: it sends an ordinary ignore add. The mute tick box only shows for Jagex moderators, so a mute flag is
 * logged and dropped.
 *
 * Limits, so one account can't fill the server's disk (exploit review SOC-3): one report per [COOLDOWN] per reporter,
 * [HOURLY] an hour, the same reporter, name and rule once per [REPEAT_WINDOW], and nothing more written once the file
 * reaches [FILE_CAP] bytes (kept in memory only, with one logged error). Our own numbers. A report refused by a limit
 * still closes the screen and gets the usual reply, so the reporter sees what a kept one shows.
 */
class Reports(
    /** Where reports are appended; null keeps them in memory only (tests). */
    private val file: Path?,
    /** Opens the report screen in the chatbox. */
    private val openScreen: (Player) -> Unit,
    private val runClientScript: (Player, Int, List<Any>) -> Unit,
    private val closeScreen: (Player) -> Unit,
    private val message: (Player, Line) -> Unit,
    private val now: () -> Instant = Instant::now,
) {
    /** One report, as written to the file. */
    data class Report(val time: Instant, val reporter: String, val name: String, val rule: Int, val chat: List<String>) {
        fun toJson(): String = buildJsonObject {
            put("time", time.toString())
            put("reporter", reporter)
            put("name", name)
            put("rule", rule)
            put("ruleName", RULES[rule])
            put("chat", JsonArray(chat.map(::JsonPrimitive)))
        }.toString()
    }

    /** The last public chat lines heard from each player, by save key; the eldest players drop out past [PLAYERS_HEARD]. */
    private val heard = object : LinkedHashMap<String, ArrayDeque<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArrayDeque<String>>) = size > PLAYERS_HEARD
    }

    private val recent = ArrayDeque<Report>()

    /** Each reporter's reports in the last hour, by save key, oldest first: when, the name's save key and the rule. */
    private data class Sent(val time: Instant, val name: String, val rule: Int)
    private val sent = object : LinkedHashMap<String, ArrayDeque<Sent>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArrayDeque<Sent>>) = size > PLAYERS_HEARD
    }

    /** Set once the file reached [FILE_CAP], so the error is logged once. */
    private var fileFull = false

    /** The last reports, oldest first, for `::reports`. */
    fun recent(): List<Report> = recent.toList()

    /** [player] said [text] in public chat. */
    fun heard(player: Player, text: String) {
        val lines = heard.getOrPut(player.saveKey) { ArrayDeque() }
        lines.addLast(text)
        while (lines.size > CHAT_LINES) lines.removeFirst()
    }

    /** When each player's report screen was last closed by its close button, until a report uses it. */
    private val closedAt = HashMap<String, Instant>()

    /** Report on a player ([name]), or the chatbox's Report button (null: the screen asks for a name). */
    fun open(player: Player, name: String?) {
        closedAt.remove(player.saveKey)
        openScreen(player)
        // Args: a real report system (0 makes the screen say it's unavailable), whether a name is given, the name.
        runClientScript(player, INIT_SCRIPT, listOf(1, if (name != null) 1 else 0, name ?: ""))
    }

    /** The screen's close button, with the screen open: closes it, and a report may follow for [CLOSE_GRACE]. */
    fun close(player: Player) {
        closeScreen(player)
        closedAt[player.saveKey] = now()
    }

    /** Forgets [player]'s recent close (logout). */
    fun forget(player: Player) {
        closedAt.remove(player.saveKey)
    }

    /** SEND_SNAPSHOT: a rule picked on the screen for [name]. */
    fun submit(player: Player, name: String, rule: Int, mute: Boolean) {
        val typed = name.trim()
        val justClosed = closedAt.remove(player.saveKey)?.let { Duration.between(it, now()) <= CLOSE_GRACE } == true
        if ((player.chatModal != INTERFACE && !justClosed) || rule !in RULES || typed.isEmpty() || typed.length > MAX_NAME) {
            logger.info { "${player.username} sent a report on '$name' for rule $rule with no report screen open, or one it doesn't offer" }
            return
        }
        if (mute) logger.info { "${player.username} asked to mute $typed; only moderators can, and there are none here" }
        val nameKey = SaveStore.saveKey(typed)
        val time = now()
        val refused = limited(player.saveKey, nameKey ?: typed.lowercase(), rule, time)
        if (refused != null) {
            logger.info { "${player.username}'s report on $typed for ${RULES[rule]} not kept: $refused" }
            if (player.chatModal == INTERFACE) closeScreen(player)
            message(player, ReportLines.RECEIVED)
            return
        }
        val chat = nameKey?.let { heard[it]?.toList() }.orEmpty()
        val report = Report(time, player.username, typed, rule, chat)
        recent.addLast(report)
        while (recent.size > RECENT) recent.removeFirst()
        write(report)
        logger.info { "${player.username} reported $typed for ${RULES[rule]}" }
        // Closed here as well as by the client, so a second snapshot before its close arrives isn't taken.
        if (player.chatModal == INTERFACE) closeScreen(player)
        message(player, ReportLines.RECEIVED)
    }

    /** Why a report from [reporter] on [name] for [rule] at [time] isn't kept, or null to keep it (and count it). */
    private fun limited(reporter: String, name: String, rule: Int, time: Instant): String? {
        val mine = sent.getOrPut(reporter) { ArrayDeque() }
        while (mine.isNotEmpty() && Duration.between(mine.first().time, time) >= HOUR) mine.removeFirst()
        val last = mine.lastOrNull()
        return when {
            last != null && Duration.between(last.time, time) < COOLDOWN -> "within ${COOLDOWN.seconds}s of the last"
            mine.size >= HOURLY -> "$HOURLY already this hour"
            mine.any { it.name == name && it.rule == rule && Duration.between(it.time, time) < REPEAT_WINDOW } ->
                "the same report within ${REPEAT_WINDOW.toMinutes()} minutes"
            else -> {
                mine.addLast(Sent(time, name, rule))
                null
            }
        }
    }

    private fun write(report: Report) {
        val file = file ?: return
        try {
            file.parent?.let(Files::createDirectories)
            if (Files.exists(file) && Files.size(file) >= FILE_CAP) {
                if (!fileFull) logger.error { "${file.fileName} has reached $FILE_CAP bytes; new reports are kept in memory only until it's moved" }
                fileFull = true
                return
            }
            fileFull = false
            Files.writeString(file, report.toJson() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } catch (e: Exception) {
            // Kept in memory still, so ::reports shows it until a restart.
            logger.error(e) { "Couldn't write ${report.reporter}'s report on ${report.name} to ${file.fileName}" }
        }
    }

    companion object {
        /** Every interface and component id above with its gameval name in the rev 242 cache, for [osrsps.cache.GameFrameCheck]. */
        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
            get() = mapOf(
                INTERFACE to ("reportabuse" to mapOf(CLOSE to "close")),
            )

        const val INTERFACE = 875
        const val CLOSE = 23

        /** reportabuse_init. */
        const val INIT_SCRIPT = 5084

        /** The chatbox's Report button (chatbox:reportabuse): op 2 is Report abuse, op 4 Report game bug. */
        const val CHATBOX_BUTTON = 31
        const val OP_REPORT_ABUSE = 2

        const val FILE_NAME = "reports.jsonl"
        const val CHAT_LINES = 10
        const val RECENT = 50
        const val PLAYERS_HEARD = 2_000
        const val MAX_NAME = 12

        val COOLDOWN: Duration = Duration.ofSeconds(10)

        /** How long after the close button a report is still taken (the real client's close can arrive first). */
        val CLOSE_GRACE: Duration = Duration.ofSeconds(3)
        const val HOURLY = 20
        val REPEAT_WINDOW: Duration = Duration.ofMinutes(5)
        private val HOUR: Duration = Duration.ofHours(1)
        const val FILE_CAP = 50L * 1024 * 1024

        /**
         * The rules the screen offers, by the id the client sends, with our own short names for admins. The client sends
         * one less than the id in the screen's scripts (reportabuse_init): Scamming is 15 there and 14 on the wire, as
         * RSProx showed on VM 215 (its table has the same ids).
         */
        val RULES: Map<Int, String> = mapOf(
            3 to "bug abuse",
            4 to "staff impersonation",
            5 to "account trading",
            6 to "botting",
            7 to "pvp cheating",
            8 to "real-world trading or encouraging rule breaking",
            10 to "advertising",
            12 to "contact information",
            14 to "scamming",
            15 to "offensive language",
            16 to "solicitation",
            17 to "disruptive behaviour",
            18 to "offensive name",
            19 to "real-life threats",
            20 to "breaking real-world laws",
            21 to "games of chance or deathmatching",
        )
    }
}

object ReportLines : Lines("report", "Report Abuse") {
    val RECEIVED = line("received", "a report sent from the report screen")
}
