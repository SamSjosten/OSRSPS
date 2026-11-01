package osrsps.game.social

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val logger = KotlinLogging.logger {}

/**
 * What founding a clan takes: how many founders (live: five, the founder and four others) and how many hours each must
 * have played (live: 25; OSRS Wiki, Clans). Live's numbers by default; Sam's pick (2026-11-01) is that a server setting
 * can lower them, for a group smaller than five.
 *
 * Kept in [file] (data/clan-founding.txt on the server, outside git), read at startup and changed by `::clanfounding`:
 * one line with the founders and the hours, such as `5 25`; blank lines and lines starting with `#` are skipped. No
 * file, or one that can't be read, is live's rule. Read and changed on the game thread only.
 */
class ClanFoundingRule(private val file: Path? = null) {
    var founders: Int = LIVE_FOUNDERS
        private set
    var hours: Int = LIVE_HOURS
        private set

    init {
        if (file != null && Files.exists(file)) {
            val line = try {
                Files.readAllLines(file).map(String::trim).firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            } catch (e: Exception) {
                logger.warn(e) { "${file.fileName} can't be read; using live's clan founding rule" }
                null
            }
            val read = line?.let(::parse)
            if (read == null) {
                if (line != null) logger.warn { "${file.fileName}: '$line' isn't founders ($MIN_FOUNDERS to $LIVE_FOUNDERS) and hours (0 to $MAX_HOURS); using live's rule" }
            } else {
                founders = read.first
                hours = read.second
            }
        }
    }

    /** Writes the file, then sets the rule: if the write throws, the rule stays as it was. */
    fun set(founders: Int, hours: Int) {
        require(founders in MIN_FOUNDERS..LIVE_FOUNDERS && hours in 0..MAX_HOURS) { "Founders are $MIN_FOUNDERS to $LIVE_FOUNDERS, hours 0 to $MAX_HOURS" }
        file?.let { file ->
            file.parent?.let(Files::createDirectories)
            val temp = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(temp, "# Founding a clan: founders (live 5) and hours each has played (live 25). ::clanfounding changes it.\n$founders $hours\n")
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        this.founders = founders
        this.hours = hours
    }

    override fun toString(): String = "$founders founders, $hours hours each" + if (founders == LIVE_FOUNDERS && hours == LIVE_HOURS) " (live's rule)" else ""

    companion object {
        const val FILE_NAME = "clan-founding.txt"
        const val LIVE_FOUNDERS = 5
        const val LIVE_HOURS = 25

        /** A clan of one: the founder alone. */
        const val MIN_FOUNDERS = 1
        const val MAX_HOURS = 10_000

        /** "5 25" to (5, 25); null for anything else or out of range. */
        fun parse(text: String): Pair<Int, Int>? {
            val parts = text.trim().split(Regex("\\s+"))
            if (parts.size != 2) return null
            val founders = parts[0].toIntOrNull() ?: return null
            val hours = parts[1].toIntOrNull() ?: return null
            if (founders !in MIN_FOUNDERS..LIVE_FOUNDERS || hours !in 0..MAX_HOURS) return null
            return founders to hours
        }
    }
}
