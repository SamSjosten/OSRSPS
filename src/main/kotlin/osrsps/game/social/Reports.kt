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
 * report is taken only while the screen is open, one per opening: the client closes it after sending (its rule select
 * script triggers the close button), and anything else is a client that didn't open it. The ignore tick box is the
 * client's too: it sends an ordinary ignore add. The mute tick box only shows for Jagex moderators, so a mute flag is
 * logged and dropped.
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

    /** The last reports, oldest first, for `::reports`. */
    fun recent(): List<Report> = recent.toList()

    /** [player] said [text] in public chat. */
    fun heard(player: Player, text: String) {
        val lines = heard.getOrPut(player.saveKey) { ArrayDeque() }
        lines.addLast(text)
        while (lines.size > CHAT_LINES) lines.removeFirst()
    }

    /** Report on a player ([name]), or the chatbox's Report button (null: the screen asks for a name). */
    fun open(player: Player, name: String?) {
        openScreen(player)
        // Args: a real report system (0 makes the screen say it's unavailable), whether a name is given, the name.
        runClientScript(player, INIT_SCRIPT, listOf(1, if (name != null) 1 else 0, name ?: ""))
    }

    /** SEND_SNAPSHOT: a rule picked on the screen for [name]. */
    fun submit(player: Player, name: String, rule: Int, mute: Boolean) {
        val typed = name.trim()
        if (player.chatModal != INTERFACE || rule !in RULES || typed.isEmpty() || typed.length > MAX_NAME) {
            logger.info { "${player.username} sent a report on '$name' for rule $rule with no report screen open, or one it doesn't offer" }
            return
        }
        if (mute) logger.info { "${player.username} asked to mute $typed; only moderators can, and there are none here" }
        val chat = SaveStore.saveKey(typed)?.let { heard[it]?.toList() }.orEmpty()
        val report = Report(now(), player.username, typed, rule, chat)
        recent.addLast(report)
        while (recent.size > RECENT) recent.removeFirst()
        write(report)
        logger.info { "${player.username} reported $typed for ${RULES[rule]}" }
        // Closed here as well as by the client, so a second snapshot before its close arrives isn't taken.
        closeScreen(player)
        message(player, ReportLines.RECEIVED)
    }

    private fun write(report: Report) {
        val file = file ?: return
        try {
            file.parent?.let(Files::createDirectories)
            Files.writeString(file, report.toJson() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } catch (e: Exception) {
            // Kept in memory still, so ::reports shows it until a restart.
            logger.error(e) { "Couldn't write ${report.reporter}'s report on ${report.name} to ${file.fileName}" }
        }
    }

    companion object {
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

        /** The rules the screen offers, by the id it sends, with our own short names for admins (reportabuse_init). */
        val RULES: Map<Int, String> = mapOf(
            4 to "bug abuse",
            5 to "staff impersonation",
            6 to "account trading",
            7 to "botting",
            8 to "pvp cheating",
            9 to "real-world trading or encouraging rule breaking",
            11 to "advertising",
            13 to "contact information",
            15 to "scamming",
            16 to "offensive language",
            17 to "solicitation",
            18 to "disruptive behaviour",
            19 to "offensive name",
            20 to "real-life threats",
            21 to "breaking real-world laws",
            22 to "games of chance or deathmatching",
        )
    }
}

object ReportLines : Lines("report", "Report Abuse") {
    val RECEIVED = line("received", "a report sent from the report screen")
}
