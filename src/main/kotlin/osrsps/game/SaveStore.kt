package osrsps.game

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Where player saves are kept, one text file per [saveKey]. Used only on the game thread. */
interface SaveStore {
    /** The save for [key], or null for a new player. */
    fun read(key: String): String?

    fun write(key: String, text: String)

    /**
     * The trade journal: two saves that must land together (see [osrsps.game.trade.TradeJournal]), or null when there
     * is none. Kept beside the saves under a name no save key can have.
     */
    fun readJournal(): String? = null

    /** Writes the journal, forced to the disk before this returns. */
    fun writeJournal(text: String) {}

    /** Removes the journal once both its saves are written. */
    fun clearJournal() {}

    companion object {
        /** Saves longer than this are refused, as a file name can only be so long. */
        const val MAX_KEY_LENGTH = 200

        /**
         * The file name a username saves under, or null when it can't be one. Case, leading and trailing spaces, and
         * spaces against underscores don't matter, so "Zezima", "zezima " and "ZEZIMA" share a save; live treats
         * names the same way (inferred from its display name rules). Characters other than a to z, 0 to 9, `_` and
         * `-` are written as `%xx` bytes so no name can reach outside the save folder.
         */
        fun saveKey(username: String): String? {
            val name = username.trim().lowercase().replace(' ', '_')
            if (name.isEmpty()) return null
            val key = buildString {
                for (byte in name.toByteArray(Charsets.UTF_8)) {
                    val c = byte.toInt().toChar()
                    if (c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '-') append(c) else append("%%%02x".format(byte.toInt() and 0xFF))
                }
            }
            return key.takeIf { it.length <= MAX_KEY_LENGTH }
        }
    }
}

/** Saves as `<key>.json` files in [dir]. */
class FileSaveStore(private val dir: Path) : SaveStore {
    override fun read(key: String): String? = try {
        Files.readString(file(key))
    } catch (_: NoSuchFileException) {
        null
    }

    /**
     * Writes to a temporary file, forces it to the disk, and then moves it over the old save, so a crash mid-write or
     * a power cut just after leaves the old save or the new one whole, never an empty file. Done on the game thread: a
     * save is a few KB, and doing it in place keeps a quick logout and login from reading a save that hasn't been
     * written yet.
     */
    override fun write(key: String, text: String) = writeFile(file(key), dir.resolve("$key.json.tmp"), text)

    override fun readJournal(): String? = try {
        Files.readString(dir.resolve(JOURNAL))
    } catch (_: NoSuchFileException) {
        null
    }

    override fun writeJournal(text: String) = writeFile(dir.resolve(JOURNAL), dir.resolve("$JOURNAL.tmp"), text)

    override fun clearJournal() {
        Files.deleteIfExists(dir.resolve(JOURNAL))
        forceDir()
    }

    private fun writeFile(target: Path, temp: Path, text: String) {
        Files.createDirectories(dir)
        FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
            val bytes = ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8))
            while (bytes.hasRemaining()) channel.write(bytes)
            channel.force(true)
        }
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
        forceDir()
    }

    /** Forces the move itself to the disk. Linux allows opening a folder for this; Windows doesn't, so it's skipped there. */
    private fun forceDir() {
        try {
            FileChannel.open(dir, StandardOpenOption.READ).use { it.force(true) }
        } catch (_: IOException) {
        }
    }

    private fun file(key: String): Path = dir.resolve("$key.json")

    private companion object {
        /** Saves are `<key>.json`; a key never has a dot (see [SaveStore.saveKey]), so this can't be one. */
        const val JOURNAL = "trade.journal"
    }
}

/** Keeps saves in memory, for tests and for a world with nowhere to write. */
class MemorySaveStore : SaveStore {
    val saves = HashMap<String, String>()

    override fun read(key: String): String? = saves[key]

    override fun write(key: String, text: String) {
        saves[key] = text
    }

    var journal: String? = null

    override fun readJournal(): String? = journal

    override fun writeJournal(text: String) {
        journal = text
    }

    override fun clearJournal() {
        journal = null
    }
}
