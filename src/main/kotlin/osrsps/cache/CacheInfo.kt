package osrsps.cache

import com.displee.cache.CacheLibrary
import java.nio.file.Files
import java.nio.file.Path

/** A short summary of a flat-file cache, used to check a download is readable. */
data class CacheSummary(val indices: List<IndexSummary>)

data class IndexSummary(val id: Int, val revision: Int, val crc: Int, val groups: Int)

object CacheInfo {
    private val BUILD = Regex("""\bbuild (\d+)\b""")

    /** The revision a cache-source.txt line names: `openrs2 runescape/2746 build 241.3 ...` gives 241; null for none. */
    fun sourceRevision(source: String): Int? = BUILD.find(source)?.groupValues?.get(1)?.toInt()

    fun read(cacheDir: Path): CacheSummary {
        require(Files.exists(cacheDir.resolve(CacheLibrary.CACHE_FILE_NAME + ".dat2"))) {
            "No ${CacheLibrary.CACHE_FILE_NAME}.dat2 in $cacheDir. Run fetch-cache first."
        }
        val library = CacheLibrary.create(cacheDir.toString())
        try {
            val indices = library.indices.filterNotNull().map { IndexSummary(it.id, it.revision, it.crc, it.archiveIds().size) }
            return CacheSummary(indices)
        } finally {
            library.close()
        }
    }
}
