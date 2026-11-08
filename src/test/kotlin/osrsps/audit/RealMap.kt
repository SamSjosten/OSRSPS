package osrsps.audit

import org.junit.jupiter.api.Assumptions.assumeTrue
import osrsps.cache.LocTypeDecoder
import osrsps.cache.MapCollisionLoader
import osrsps.cache.WorldMap
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.useLines

/**
 * The world as the server builds it from the real 241 cache, for the audits: a checkout of github.com/abextm/osrs-cache
 * at a rev 241 tag (2.flatcache, 5.flatcache and 24.flatcache) in OSRSPS_FLATCACHE. A test that loads it is skipped
 * without one, as in CI, which has no cache. Loaded once for all the audits in a run.
 */
internal object RealMap {
    class Loaded(val map: WorldMap, val squares: List<MapCollisionLoader.MapSquare>, val failedTypes: Int, val loadedSquares: Int)

    private val dir: Path? = System.getenv("OSRSPS_FLATCACHE")?.let(Path::of)
    private var cached: Loaded? = null

    fun load(): Loaded {
        assumeTrue(dir != null, "OSRSPS_FLATCACHE not set")
        cached?.let { return it }
        val cache = FlatCache(dir!!)
        val names = cache.group(GAMEVAL_INDEX, GAMEVAL_LOCS)
        val types = HashMap<Int, LocType>()
        var failedTypes = 0
        for ((id, data) in cache.group(CONFIG_INDEX, LOC_TYPES)) {
            try {
                types[id] = LocTypeDecoder.decode(id, data, names[id]?.let { String(it, Charsets.ISO_8859_1) })
            } catch (e: Exception) {
                failedTypes++
            }
        }
        val squares = readSquares(dir)
        val map = MapCollisionLoader.buildWorld(LocTypes(types), squares.asSequence())
        // Map squares that loaded are those whose tiles don't read as unloaded.
        val loaded = squares.count { map.collision[(it.x shl 6) + 1, (it.z shl 6) + 1, 0] != osrsps.map.CollisionFlag.UNLOADED }
        return Loaded(map, squares, failedTypes, loaded).also { cached = it }
    }

    /** Every group in 5.flatcache, in one pass: reading them one by one would scan the 15 MB file thousands of times. */
    private fun readSquares(dir: Path): List<MapCollisionLoader.MapSquare> {
        val squares = ArrayList<MapCollisionLoader.MapSquare>()
        var id = -1
        var contents: String? = null
        val fileIds = ArrayList<Int>()
        fun flush() {
            val data = contents ?: return
            val files = FlatCache.split(FlatCache.unpack(Base64.getDecoder().decode(data)), fileIds.ifEmpty { listOf(0) })
            squares += MapCollisionLoader.MapSquare(id shr 8, id and 0xFF, MapCollisionLoader.SquareFiles({ files[0] }, { files[1] }))
        }
        dir.resolve("$MAPS_INDEX.flatcache").useLines { lines ->
            for (line in lines) {
                when (line.substringBefore('=')) {
                    "id" -> { flush(); id = line.substringAfter('=').toInt(); contents = null; fileIds.clear() }
                    "contents" -> contents = line.substringAfter('=')
                    "file" -> fileIds += line.substringAfter('=').substringBefore('=').toInt()
                }
            }
        }
        flush()
        return squares
    }

    private const val CONFIG_INDEX = 2
    private const val LOC_TYPES = 6
    private const val MAPS_INDEX = 5
    private const val GAMEVAL_INDEX = 24
    private const val GAMEVAL_LOCS = 6
}
