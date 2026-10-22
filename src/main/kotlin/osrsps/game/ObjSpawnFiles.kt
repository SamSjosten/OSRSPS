package osrsps.game

import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Item spawn files: tab-separated text, one spawn a line, under `src/main/resources/obj-spawns/` (any `*.tsv` there,
 * one file per area). Columns:
 *
 * ```
 * obj          count  x     z     level  respawn
 * bronze_axe   1      3225  3218  0      100
 * ```
 *
 * `obj` is a gameval name (as in `::item`) or an id; `respawn` is the base respawn time in ticks on an empty world (the
 * wiki's BaseRate; see [GroundObjs.respawnDelay]). A line whose first column is `obj` is a header. Everything after a
 * `#` is a comment, so say where a row's facts came from there.
 */
object ObjSpawnFiles {
    const val RESOURCE_DIR = "obj-spawns"

    /** The spawns in one file's [text]. [source] names it in errors. Throws on any line that doesn't read. */
    fun parse(text: String, objTypes: ObjTypes, source: String): List<ObjSpawn> {
        val spawns = ArrayList<ObjSpawn>()
        for ((index, raw) in text.lines().withIndex()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val where = "$source line ${index + 1}"
            val columns = line.split('\t').map(String::trim).filter(String::isNotEmpty)
            if (columns.first().equals("obj", ignoreCase = true)) continue
            require(columns.size == COLUMNS) { "$where: expected $COLUMNS tab-separated columns (obj count x z level respawn), got ${columns.size}" }
            val type = columns[0].toIntOrNull()?.let(objTypes::get) ?: objTypes.find(columns[0])
            requireNotNull(type) { "$where: no obj '${columns[0]}' in the cache" }
            val numbers = columns.drop(1).map { requireNotNull(it.toIntOrNull()) { "$where: '$it' is not a number" } }
            val (count, x, z, level, respawn) = numbers
            require(level in 0..3) { "$where: level $level must be 0 to 3" }
            require(type.stackable || count == 1) { "$where: ${type.name} doesn't stack, so its count must be 1" }
            spawns += try {
                ObjSpawn(type.id, count, x, z, level, respawn)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$where: ${e.message}", e)
            }
        }
        return spawns
    }

    /** Every spawn file in the resources, in file name order, as (file name, text). Works from a jar and from a folder. */
    fun resources(loader: ClassLoader = ObjSpawnFiles::class.java.classLoader): List<Pair<String, String>> {
        val url = loader.getResource(RESOURCE_DIR) ?: return emptyList()
        val uri = url.toURI()
        if (uri.scheme != "jar") return read(Path.of(uri))
        try {
            FileSystems.newFileSystem(uri, emptyMap<String, Any>())
        } catch (_: FileSystemAlreadyExistsException) {
            null
        }.use { opened ->
            val fs = opened ?: FileSystems.getFileSystem(uri)
            return read(fs.getPath("/$RESOURCE_DIR"))
        }
    }

    private fun read(dir: Path): List<Pair<String, String>> = Files.list(dir).use { files ->
        files.filter { it.extension == "tsv" }.sorted().map { it.name to it.readText() }.toList()
    }

    private const val COLUMNS = 6
}
