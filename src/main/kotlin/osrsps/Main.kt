package osrsps

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.cache.CacheInfo
import osrsps.cache.OpenRs2Archive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/** Where the server keeps Jagex files. Git-ignored; override with OSRSPS_DATA. */
private val dataDir: Path = Path.of(System.getenv("OSRSPS_DATA") ?: "data")
private val cacheDir: Path = dataDir.resolve("cache")

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        null -> logger.info { "OSRSPS server skeleton, revision ${Revision.MAJOR}, Java ${Runtime.version()}" }
        "fetch-cache" -> fetchCache()
        "cache-info" -> cacheInfo()
        else -> {
            System.err.println("Usage: osrsps [fetch-cache | cache-info]")
            exitProcess(2)
        }
    }
}

private fun fetchCache() {
    val archive = OpenRs2Archive()
    val entry = OpenRs2Archive.selectLatest(archive.listCaches(), Revision.MAJOR)
        ?: error("OpenRS2 lists no live oldschool cache for revision ${Revision.MAJOR}")
    logger.info { "Selected OpenRS2 cache ${entry.id}: build ${entry.major}.${entry.minor}, ${entry.timestamp}" }
    // Download and check into a sibling folder so a failed fetch leaves the current cache untouched.
    val staging = dataDir.resolve("cache.download")
    staging.toFile().deleteRecursively()
    archive.download(entry, staging)
    CacheInfo.read(staging)
    cacheDir.toFile().deleteRecursively()
    Files.move(staging, cacheDir)
    Files.writeString(dataDir.resolve("cache-source.txt"), "openrs2 ${entry.scope}/${entry.id} build ${entry.major}.${entry.minor} ${entry.timestamp}\n")
    cacheInfo()
}

private fun cacheInfo() {
    val summary = CacheInfo.read(cacheDir)
    summary.indices.forEach { logger.info { "index ${it.id}: ${it.groups} groups, revision ${it.revision}, crc ${it.crc}" } }
    logger.info { "Cache at ${cacheDir.toAbsolutePath()} has ${summary.indices.size} indices" }
}
