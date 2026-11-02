package osrsps.cache.overlay

import osrsps.cache.model.ModelCodec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/**
 * What we serve on top of the live cache, in memory, so the files on disk stay the live cache
 * ([osrsps.net.Js5Store] adds it to the cache it serves):
 *
 * - [groups] of our own: custom models from `data/models/<id>.dat`, each a model in the client's format
 *   (`osrsps import-model` writes one). An id has to be above every live group of its index, as the client takes the
 *   group it already has for a known id, and below 65536, as a JS5 request carries a 2-byte group id.
 * - [files] added to live groups: our own loc types in config archive 6, encoded in code
 *   ([osrsps.game.content.CustomLocs]). A file id has to be above every live file of its group.
 */
class CacheOverlay(val groups: List<Group>, val files: List<File> = emptyList()) {
    class Group(val index: Int, val group: Int, val data: ByteArray)

    class File(val index: Int, val group: Int, val file: Int, val data: ByteArray)

    val size: Int get() = groups.size + files.size

    fun byIndex(): Map<Int, List<Group>> = groups.groupBy { it.index }

    fun filesByGroup(): Map<Pair<Int, Int>, List<File>> = files.groupBy { it.index to it.group }

    operator fun plus(more: List<File>): CacheOverlay = CacheOverlay(groups, files + more)

    override fun toString(): String =
        (groups.map { "${it.index}/${it.group}" } + files.map { "${it.index}/${it.group}/${it.file}" }).joinToString()

    companion object {
        val NONE = CacheOverlay(emptyList())
        const val MODELS = 7
        const val MODELS_FOLDER = "models"
        const val MAX_GROUP = 0xFFFF

        /**
         * Reads [dataDir]/models/<id>.dat; other files there are left alone. A file that isn't a model, or whose id is
         * past [MAX_GROUP], stops the server with what is wrong.
         */
        fun load(dataDir: Path): CacheOverlay {
            val dir = dataDir.resolve(MODELS_FOLDER)
            if (!Files.isDirectory(dir)) return NONE
            val files = Files.list(dir).use { it.toList() }
                .mapNotNull { file ->
                    Regex("""(\d+)\.dat""").matchEntire(file.name)?.let {
                        // Too many digits for an Int is past MAX_GROUP too: say so rather than fail on the number.
                        val id = it.groupValues[1].toIntOrNull() ?: throw IllegalArgumentException("$file: a model id has to be below 65536")
                        id to file
                    }
                }
                .sortedBy { it.first }
            val groups = files.map { (id, file) ->
                require(id <= MAX_GROUP) { "$file: a model id has to be below 65536" }
                val data = Files.readAllBytes(file)
                try {
                    ModelCodec.decode(data)
                } catch (e: RuntimeException) {
                    throw IllegalArgumentException("$file is not a model the client reads: ${e.message}", e)
                }
                Group(MODELS, id, data)
            }
            return CacheOverlay(groups)
        }
    }
}
