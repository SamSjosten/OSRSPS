package osrsps.game

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Where player saves are kept, one text file per [saveKey]. Used only on the game thread. */
interface SaveStore {
    /** The save for [key], or null for a new player. */
    fun read(key: String): String?

    fun write(key: String, text: String)

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
     * Writes to a temporary file and then moves it over the old save, so a crash mid-write leaves the old save whole.
     * Done on the game thread: a save is a few KB, and doing it in place keeps a quick logout and login from reading
     * a save that hasn't been written yet.
     */
    override fun write(key: String, text: String) {
        Files.createDirectories(dir)
        val target = file(key)
        val temp = dir.resolve("$key.json.tmp")
        Files.writeString(temp, text)
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun file(key: String): Path = dir.resolve("$key.json")
}

/** Keeps saves in memory, for tests and for a world with nowhere to write. */
class MemorySaveStore : SaveStore {
    val saves = HashMap<String, String>()

    override fun read(key: String): String? = saves[key]

    override fun write(key: String, text: String) {
        saves[key] = text
    }
}
