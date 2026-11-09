package osrsps.cache.overlay

import osrsps.cache.SpriteCodec
import osrsps.cache.model.ModelCodec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/**
 * What we serve on top of the live cache, in memory, so the files on disk stay the live cache
 * ([osrsps.net.Js5Store] adds it to the cache it serves):
 *
 * - [groups] of our own: custom models from `data/models/<id>.dat`, each a model in the client's format
 *   (`osrsps import-model` writes one), and custom sprites from `data/sprites/<id>.dat`, each a sprite group in the
 *   client's format ([osrsps.cache.SpriteCodec]), which take the place of the sprites built in code under the same
 *   id (the lodestone map's image). An id has to be above every live group of its index, as the client takes the
 *   group it already has for a known id, and below 65536, as a JS5 request carries a 2-byte group id.
 * - [files] added to live groups: our own loc types in config archive 6 and map elements in 35
 *   ([osrsps.game.content.CustomLocs]), var types in 14 and 16 ([osrsps.game.content.CustomVars]) and sequences and
 *   spotanims in 12 and 13 ([osrsps.game.content.CustomAnims]), all encoded in code. A file id has to be above every
 *   live file of its group.
 */
class CacheOverlay(val groups: List<Group>, val files: List<File> = emptyList()) {
    /** A whole group of ours: [data] is the group as the client unpacks it, holding [files] (one, id 0, unless packed as several). */
    class Group(val index: Int, val group: Int, val data: ByteArray, val files: List<Int> = listOf(0)) {
        init {
            require(files.isNotEmpty() && files == files.sorted() && files.toSet().size == files.size) { "Group $index/$group's files are in order, none twice: $files" }
        }
    }

    class File(val index: Int, val group: Int, val file: Int, val data: ByteArray)

    val size: Int get() = groups.size + files.size

    fun byIndex(): Map<Int, List<Group>> = groups.groupBy { it.index }

    fun filesByGroup(): Map<Pair<Int, Int>, List<File>> = files.groupBy { it.index to it.group }

    operator fun plus(more: List<File>): CacheOverlay = CacheOverlay(groups, files + more)

    /** This overlay with [more] groups, except any whose index and id this one already has: a file in `data` wins over code. */
    fun withGroups(more: List<Group>): CacheOverlay =
        CacheOverlay(groups + more.filter { candidate -> groups.none { it.index == candidate.index && it.group == candidate.group } }, files)

    override fun toString(): String =
        (groups.map { "${it.index}/${it.group}" } + files.map { "${it.index}/${it.group}/${it.file}" }).joinToString()

    companion object {
        val NONE = CacheOverlay(emptyList())
        const val MODELS = 7
        const val MODELS_FOLDER = "models"
        const val SPRITES = SpriteCodec.INDEX
        const val SPRITES_FOLDER = "sprites"
        const val MAX_GROUP = 0xFFFF

        /**
         * Reads [dataDir]/models/<id>.dat and [dataDir]/sprites/<id>.dat; other files there are left alone. A file that
         * isn't a model or sprite the client reads, or whose id is past [MAX_GROUP], stops the server with what is wrong.
         */
        fun load(dataDir: Path): CacheOverlay = CacheOverlay(
            loadFolder(dataDir.resolve(MODELS_FOLDER), MODELS, "model") { ModelCodec.decode(it) } +
                loadFolder(dataDir.resolve(SPRITES_FOLDER), SPRITES, "sprite") { SpriteCodec.decode(it) },
        )

        private fun loadFolder(dir: Path, index: Int, what: String, check: (ByteArray) -> Any): List<Group> {
            if (!Files.isDirectory(dir)) return emptyList()
            val files = Files.list(dir).use { it.toList() }
                .mapNotNull { file ->
                    Regex("""(\d+)\.dat""").matchEntire(file.name)?.let {
                        // Too many digits for an Int is past MAX_GROUP too: say so rather than fail on the number.
                        val id = it.groupValues[1].toIntOrNull() ?: throw IllegalArgumentException("$file: a $what id has to be below 65536")
                        id to file
                    }
                }
                .sortedBy { it.first }
            return files.map { (id, file) ->
                require(id <= MAX_GROUP) { "$file: a $what id has to be below 65536" }
                val data = Files.readAllBytes(file)
                try {
                    check(data)
                } catch (e: RuntimeException) {
                    throw IllegalArgumentException("$file is not a $what the client reads: ${e.message}", e)
                }
                Group(index, id, data)
            }
        }
    }
}
