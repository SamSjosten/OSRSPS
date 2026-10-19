package osrsps.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.zip.ZipInputStream

private val logger = KotlinLogging.logger {}

/** One entry of archive.openrs2.org/caches.json. */
data class CacheEntry(
    val id: Int,
    val scope: String,
    val major: Int,
    val minor: Int?,
    val timestamp: String?,
) {
    /** The build as OpenRS2 lists it, e.g. `241.3`, or `241` when it has no minor build. */
    val build: String get() = minor?.let { "$major.$it" } ?: "$major"
}

/** Downloads a live OSRS cache and its XTEA keys from the OpenRS2 archive. */
class OpenRs2Archive(
    private val baseUrl: String = "https://archive.openrs2.org",
    private val http: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build(),
) {
    fun listCaches(): String = String(get("$baseUrl/caches.json").readAllBytes())

    /** Downloads [entry] into [cacheDir]: the flat-file cache plus keys.json. */
    fun download(entry: CacheEntry, cacheDir: Path) {
        Files.createDirectories(cacheDir)
        val prefix = "$baseUrl/caches/${entry.scope}/${entry.id}"
        logger.info { "Downloading $prefix/disk.zip" }
        get("$prefix/disk.zip").use { extractCache(it, cacheDir) }
        logger.info { "Downloading $prefix/keys.json" }
        get("$prefix/keys.json").use {
            Files.copy(it, cacheDir.resolve("keys.json"), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun get(url: String): InputStream {
        val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            response.body().close()
            error("GET $url returned HTTP ${response.statusCode()}")
        }
        return response.body()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Picks the newest live English OSRS cache for [major], by timestamp then minor build.
         * Only entries whose disk store OpenRS2 has finished and validated (`disk_store_valid: true`) qualify;
         * null means it is still being processed.
         */
        fun selectLatest(cachesJson: String, major: Int): CacheEntry? = json.parseToJsonElement(cachesJson).jsonArray
            .map { it.jsonObject }
            .filter { it.string("game") == "oldschool" && it.string("environment") == "live" }
            .filter { it.string("language") == null || it.string("language") == "en" }
            .filter { it["disk_store_valid"]?.jsonPrimitive?.booleanOrNull == true }
            .mapNotNull { it.toEntry(major) }
            .maxWithOrNull(compareBy<CacheEntry>({ it.timestamp ?: "" }, { it.minor ?: -1 }))

        private fun JsonObject.toEntry(major: Int): CacheEntry? {
            val build = (this["builds"] as? JsonArray)
                ?.map { it.jsonObject }
                ?.filter { it["major"]?.jsonPrimitive?.intOrNull == major }
                ?.maxByOrNull { it["minor"]?.jsonPrimitive?.intOrNull ?: -1 }
                ?: return null
            return CacheEntry(
                id = this["id"]!!.jsonPrimitive.intOrNull!!,
                scope = string("scope") ?: "runescape",
                major = major,
                minor = build["minor"]?.jsonPrimitive?.intOrNull,
                timestamp = string("timestamp"),
            )
        }

        private fun JsonObject.string(key: String): String? =
            this[key]?.jsonPrimitive?.takeIf { it.isString }?.content

        /** Extracts the cache files from disk.zip, dropping the leading `cache/` folder. */
        fun extractCache(zip: InputStream, cacheDir: Path) {
            val root = cacheDir.toAbsolutePath().normalize()
            var count = 0
            ZipInputStream(zip).use { stream ->
                generateSequence { stream.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
                    val name = entry.name.removePrefix("cache/")
                    val target = root.resolve(name).normalize()
                    require(target.startsWith(root) && target != root) { "Refusing zip entry outside cache dir: ${entry.name}" }
                    Files.createDirectories(target.parent)
                    Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING)
                    count++
                }
            }
            check(count > 0) { "disk.zip contained no files" }
            logger.info { "Extracted $count files to $root" }
        }
    }
}
