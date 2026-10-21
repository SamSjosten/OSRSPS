package osrsps

import io.github.oshai.kotlinlogging.KotlinLogging
import com.displee.cache.CacheLibrary
import osrsps.cache.CacheInfo
import osrsps.cache.MapCollisionLoader
import osrsps.map.CollisionPrinter
import osrsps.cache.OpenRs2Archive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/** Where the server keeps Jagex files. Git-ignored; override with OSRSPS_DATA. */
private val dataDir: Path = Path.of(System.getenv("OSRSPS_DATA") ?: "data")
private val cacheDir: Path = dataDir.resolve("cache")

private const val DEFAULT_PORT = 43594

/** The address players' PCs reach the server on: 127.0.0.1 through a tunnel, or the LAN address that forwards to it. */
private val publicHost: String = System.getenv("OSRSPS_PUBLIC_HOST") ?: "127.0.0.1"

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        null -> logger.info { "OSRSPS server skeleton, revision ${Revision.MAJOR}, Java ${Runtime.version()}" }
        "fetch-cache" -> fetchCache()
        "cache-info" -> cacheInfo()
        "serve" -> serve(args.getOrNull(1)?.toInt() ?: DEFAULT_PORT)
        "collision" -> printCollision(args.getOrNull(1)?.toInt() ?: 3222, args.getOrNull(2)?.toInt() ?: 3218, args.getOrNull(3)?.toInt() ?: 0)
        else -> {
            System.err.println("Usage: osrsps [fetch-cache | cache-info | serve [port] | collision [x z [level]]]  (HTTP config on port+1)")
            exitProcess(2)
        }
    }
}

private fun fetchCache() {
    val archive = OpenRs2Archive()
    val entry = OpenRs2Archive.selectLatest(archive.listCaches(), Revision.MAJOR)
        ?: error("OpenRS2 lists no live oldschool cache for revision ${Revision.MAJOR}")
    logger.info { "Selected OpenRS2 cache ${entry.id}: build ${entry.build}, ${entry.timestamp}" }
    // Download and check into a sibling folder so a failed fetch leaves the current cache untouched.
    val staging = dataDir.resolve("cache.download")
    staging.toFile().deleteRecursively()
    archive.download(entry, staging)
    CacheInfo.read(staging)
    cacheDir.toFile().deleteRecursively()
    Files.move(staging, cacheDir)
    Files.writeString(dataDir.resolve("cache-source.txt"), "openrs2 ${entry.scope}/${entry.id} build ${entry.build} ${entry.timestamp}\n")
    cacheInfo()
}

private fun serve(port: Int) {
    // A fixed seed makes every random roll repeat from startup; leave it unset for a new one each start.
    val seed = System.getenv("OSRSPS_SEED")?.let { it.toLongOrNull() ?: error("OSRSPS_SEED must be a whole number, was '$it'") }
    val server = GameServer(dataDir, port, port + 1, publicHost, seed)
    server.start()
    Runtime.getRuntime().addShutdownHook(Thread(server::stop))
}

private fun cacheInfo() {
    val summary = CacheInfo.read(cacheDir)
    summary.indices.forEach { logger.info { "index ${it.id}: ${it.groups} groups, revision ${it.revision}, crc ${it.crc}" } }
    logger.info { "Cache at ${cacheDir.toAbsolutePath()} has ${summary.indices.size} indices" }
}

/** Prints the collision 15 tiles around a tile, north up, so it can be compared with the game. */
private fun printCollision(x: Int, z: Int, level: Int) {
    val library = CacheLibrary.create(cacheDir.toString())
    val map = try {
        MapCollisionLoader.load(library)
    } finally {
        library.close()
    }
    println(CollisionPrinter.render(map, x, z, level, 15))
}

