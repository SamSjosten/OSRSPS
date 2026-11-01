package osrsps.cache.overlay

import osrsps.cache.model.ModelCodec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Groups of our own to serve on top of the live cache: custom models from `data/models/<id>.dat`, each a model in
 * the client's format (`osrsps import-model` writes one). [osrsps.net.Js5Store] adds them to the cache it serves,
 * in memory, so the files on disk stay the live cache. An id has to be above every live group of its index, as the
 * client takes the group it already has for a known id, and below 65536, as a JS5 request carries a 2-byte group id.
 */
class CacheOverlay(val groups: List<Group>) {
    class Group(val index: Int, val group: Int, val data: ByteArray)

    val size: Int get() = groups.size

    fun byIndex(): Map<Int, List<Group>> = groups.groupBy { it.index }

    override fun toString(): String = groups.joinToString { "${it.index}/${it.group}" }

    companion object {
        val NONE = CacheOverlay(emptyList())
        const val MODELS = 7
        const val MODELS_FOLDER = "models"
        const val MAX_GROUP = 0xFFFF

        /** Reads [dataDir]/models/<id>.dat; other files there are left alone. A file that isn't a model stops the server. */
        fun load(dataDir: Path): CacheOverlay {
            val dir = dataDir.resolve(MODELS_FOLDER)
            if (!Files.isDirectory(dir)) return NONE
            val files = Files.list(dir).use { it.toList() }
                .mapNotNull { file -> Regex("""(\d+)\.dat""").matchEntire(file.name)?.let { it.groupValues[1].toInt() to file } }
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
