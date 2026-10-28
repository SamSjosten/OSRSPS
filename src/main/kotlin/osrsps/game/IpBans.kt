package osrsps.game

import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Addresses that may not log in, by the player's real address (the PROXY header's for a connection through the relay).
 * Kept in [file] so they last across restarts: one address per line, then optionally a space and a note; blank lines
 * and lines starting with `#` are skipped. Without a file (tests) they last until the world stops.
 *
 * [relays] can't be banned: a relay connection without a PROXY header carries the relay's own address, so banning it
 * would lock out every player from the internet. Checked from the Netty and login threads and changed from the game
 * thread, so the set is concurrent and writes are synchronized.
 */
class IpBans(private val file: Path? = null, relays: Set<String> = emptySet()) {
    /** Address to note. */
    private val bans = ConcurrentHashMap<String, String>()

    private val relays: Set<String> = relays.mapNotNull(::normalize).toSet()

    init {
        if (file != null && Files.exists(file)) {
            Files.readAllLines(file).forEachIndexed { i, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val address = normalize(line.substringBefore(' ').substringBefore('\t'))
                if (address == null) {
                    logger.warn { "${file.fileName} line ${i + 1}: '$line' isn't an IP address; skipped" }
                    return@forEachIndexed
                }
                bans[address] = line.substring(line.indexOfFirst(Char::isWhitespace).takeIf { it >= 0 } ?: line.length).trim()
            }
        }
    }

    val size: Int get() = bans.size

    /** Every ban, address to note, by address. */
    val all: Map<String, String> get() = bans.toSortedMap()

    fun isBanned(address: String): Boolean = normalize(address)?.let(bans::containsKey) ?: false

    enum class Result { BANNED, ALREADY_BANNED, UNBANNED, NOT_BANNED, NOT_AN_ADDRESS, RELAY }

    /** Bans [address] with [note] (who and why), and writes the file. */
    @Synchronized
    fun ban(address: String, note: String = ""): Result {
        val key = normalize(address) ?: return Result.NOT_AN_ADDRESS
        if (key in relays) return Result.RELAY
        if (bans.containsKey(key)) return Result.ALREADY_BANNED
        bans[key] = note.replace(LINE_BREAK, " ").trim()
        write()
        return Result.BANNED
    }

    @Synchronized
    fun unban(address: String): Result {
        val key = normalize(address) ?: return Result.NOT_AN_ADDRESS
        if (bans.remove(key) == null) return Result.NOT_BANNED
        write()
        return Result.UNBANNED
    }

    /** Writes every ban to a temporary file and moves it over [file], so a crash mid-write loses nothing. */
    private fun write() {
        val file = file ?: return
        val text = buildString {
            appendLine("# IP bans: one address per line, then a note. Read at startup; ::ipban and ::ipunban change it.")
            for ((address, note) in all) appendLine(if (note.isEmpty()) address else "$address $note")
        }
        file.parent?.let(Files::createDirectories)
        val temp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(temp, text)
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        private fun notAnOctet(part: String) = part.toIntOrNull() !in 0..255 || (part.length > 1 && part.startsWith('0'))

        const val FILE_NAME = "ip-bans.txt"

        private val LINE_BREAK = Regex("[\\r\\n]+")

        /** IPv4 and IPv6 literals only: a host name would be looked up, and could change what it names. */
        private val LITERAL = Regex("[0-9A-Fa-f.:]+")

        /**
         * [address] written one way for every spelling of it (`::1` and `0:0:0:0:0:0:0:1`; an IPv4-mapped IPv6 address as
         * its IPv4 one), or null when it isn't an IP address.
         */
        fun normalize(address: String): String? {
            val literal = address.trim().removePrefix("[").removeSuffix("]")
            if (!LITERAL.matches(literal)) return null
            // Java takes anything with a colon as an IPv6 literal; anything else that isn't four plain numbers up to 255
            // it would look up as a host name.
            if (':' !in literal && literal.split('.').let { parts -> parts.size != 4 || parts.any(::notAnOctet) }) return null
            return try {
                InetAddress.getByName(literal).hostAddress
            } catch (e: Exception) {
                null
            }
        }
    }
}
