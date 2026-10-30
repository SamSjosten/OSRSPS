package osrsps.game

import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val logger = KotlinLogging.logger {}

/**
 * The server-wide XP multiplier, in tenths like XP itself: 10 is live's 1x, 25 is 2.5x. Every XP the game grants goes
 * through [PlayerSkills.addTenths], which multiplies by it; `::addxp` and `::setlevel` set exact amounts and don't.
 *
 * Kept in [file] (data/xp-rate.txt on the server, outside git) so `::xprate` lasts across restarts and deploys: a
 * line with the rate, such as `2.5`, and optionally `previous 1`, the rate `::xprate restore` goes back to; blank lines
 * and lines starting with `#` are skipped. No file, or one that can't be read, is 1x. Without a file (tests) a change
 * lasts until the world stops. Read and changed on the game thread only.
 */
class XpRate(private val file: Path? = null) {
    /** The rate in tenths. */
    var tenths: Int = ONE
        private set

    /** The rate before the last [set], for `::xprate restore`. */
    private var previous: Int = ONE

    init {
        if (file != null && Files.exists(file)) {
            val lines = try {
                Files.readAllLines(file).map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }
            } catch (e: Exception) {
                logger.warn(e) { "${file.fileName} can't be read; using 1x" }
                emptyList()
            }
            val line = lines.firstOrNull { !it.startsWith(PREVIOUS) }
            val read = line?.let(::parse)
            if (read == null) {
                if (line != null) logger.warn { "${file.fileName}: '$line' isn't a rate from ${format(MIN)} to ${format(MAX)}; using 1x" }
            } else {
                tenths = read
                previous = lines.firstOrNull { it.startsWith(PREVIOUS) }?.removePrefix(PREVIOUS)?.let(::parse) ?: read
            }
        }
    }

    /** [amount] tenths of XP at this rate, rounded down to a whole tenth. At 1x it is [amount] exactly. */
    fun apply(amount: Int): Int = if (tenths == ONE) amount else (amount.toLong() * tenths / ONE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Writes the file, then sets the rate: if the write throws, the rate stays as it was, on disk and in the game. */
    fun set(value: Int) {
        require(value in MIN..MAX) { "XP rates are ${format(MIN)} to ${format(MAX)}" }
        write(value, tenths)
        previous = tenths
        tenths = value
    }

    /** Goes back to the rate before the last [set], and returns it. */
    fun restore(): Int {
        set(previous)
        return tenths
    }

    override fun toString(): String = "${format(tenths)}x"

    /** Writes the rates to a temporary file and moves it over [file], so a crash mid-write loses nothing. */
    private fun write(tenths: Int, previous: Int) {
        val file = file ?: return
        file.parent?.let(Files::createDirectories)
        val temp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(
            temp,
            "# The server-wide XP rate (1 is live's). Read at startup; ::xprate changes it.\n${format(tenths)}\n$PREVIOUS${format(previous)}\n",
        )
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        const val FILE_NAME = "xp-rate.txt"

        private const val PREVIOUS = "previous "

        /** 1x. */
        const val ONE = 10

        /** 0.1x. */
        const val MIN = 1

        /** 1000x: 13M XP (level 99) from a few hundred actions is plenty for testing. */
        const val MAX = 10_000

        /** "2.5" or "2.5x" to 25 tenths; null for anything outside [MIN] to [MAX] or with more than one decimal place. */
        fun parse(text: String): Int? {
            val value = text.trim().removeSuffix("x").removeSuffix("X").toBigDecimalOrNull() ?: return null
            if (value.stripTrailingZeros().scale() > 1) return null
            val tenths = value.multiply(BigDecimal.TEN)
            if (tenths < BigDecimal(MIN) || tenths > BigDecimal(MAX)) return null
            return tenths.intValueExact()
        }

        /** 25 tenths as "2.5", 10 as "1". */
        fun format(tenths: Int): String = if (tenths % ONE == 0) "${tenths / ONE}" else "${tenths / ONE}.${tenths % ONE}"
    }
}
