package osrsps

import io.github.oshai.kotlinlogging.KotlinLogging
import com.displee.cache.CacheLibrary
import osrsps.cache.CacheInfo
import osrsps.cache.MapCollisionLoader
import osrsps.map.CollisionPrinter
import osrsps.cache.OpenRs2Archive
import osrsps.game.Accounts
import osrsps.game.FileSaveStore
import osrsps.game.IpBans
import osrsps.game.command.Admins
import osrsps.game.content.DialogueLines
import osrsps.game.dialogue.DialogueFile
import osrsps.net.ClientConfig
import osrsps.net.ProxyProtocol
import osrsps.net.RsaKeys
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/** Where the server keeps Jagex files. Git-ignored; override with OSRSPS_DATA. */
private val dataDir: Path = Path.of(System.getenv("OSRSPS_DATA") ?: "data")
private val cacheDir: Path = dataDir.resolve("cache")

/** fetch-cache's exit code when OpenRS2 doesn't list the asked-for build as the newest yet. */
private const val EXIT_BUILD_NOT_LISTED = 3

private const val DEFAULT_PORT = 43594

/** The address players' PCs reach the server on: 127.0.0.1 through a tunnel, or the LAN address that forwards to it. */
private val publicHost: String = System.getenv("OSRSPS_PUBLIC_HOST") ?: "127.0.0.1"

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        null -> logger.info { "OSRSPS server skeleton, revision ${Revision.MAJOR}, Java ${Runtime.version()}" }
        "fetch-cache" -> fetchCache(args.getOrNull(1))
        "cache-info" -> cacheInfo()
        "serve" -> serve(args.getOrNull(1)?.toInt() ?: DEFAULT_PORT)
        "collision" -> printCollision(args.getOrNull(1)?.toInt() ?: 3222, args.getOrNull(2)?.toInt() ?: 3218, args.getOrNull(3)?.toInt() ?: 0)
        "dialogue-keys" -> writeDialogueKeys()
        "set-password" -> setPassword(args.getOrNull(1))
        else -> {
            System.err.println("Usage: osrsps [fetch-cache [build] | cache-info | serve [port] | collision [x z [level]] | dialogue-keys | set-password name]  (HTTP config on port+1)")
            exitProcess(2)
        }
    }
}

/**
 * Downloads OpenRS2's newest cache for our revision. With [build] (e.g. `241.4`) only when that is the newest build:
 * otherwise nothing changes and it exits with [EXIT_BUILD_NOT_LISTED], so deploy/auto-deploy.sh can try again later.
 */
private fun fetchCache(build: String?) {
    val archive = OpenRs2Archive()
    val entry = OpenRs2Archive.selectLatest(archive.listCaches(), Revision.MAJOR)
        ?: error("OpenRS2 lists no live oldschool cache for revision ${Revision.MAJOR}")
    if (build != null && entry.build != build) {
        logger.warn { "OpenRS2's newest cache for revision ${Revision.MAJOR} is build ${entry.build}, not $build; not fetching" }
        exitProcess(EXIT_BUILD_NOT_LISTED)
    }
    logger.info { "Selected OpenRS2 cache ${entry.id}: build ${entry.build}, ${entry.timestamp}" }
    // Download and check into a sibling folder so a failed fetch leaves the current cache untouched.
    val staging = dataDir.resolve("cache.download")
    staging.toFile().deleteRecursively()
    archive.download(entry, staging)
    CacheInfo.read(staging)
    // Keep the cache being replaced (and what it came from) so a bad build can be rolled back by swapping the folders.
    val previous = dataDir.resolve("cache.previous")
    if (Files.exists(cacheDir)) {
        previous.toFile().deleteRecursively()
        Files.move(cacheDir, previous)
        dataDir.resolve("cache-source.txt").toFile().takeIf { it.exists() }
            ?.copyTo(dataDir.resolve("cache.previous-source.txt").toFile(), overwrite = true)
    }
    Files.move(staging, cacheDir)
    Files.writeString(dataDir.resolve("cache-source.txt"), "openrs2 ${entry.scope}/${entry.id} build ${entry.build} ${entry.timestamp}\n")
    cacheInfo()
}

private fun serve(port: Int) {
    // A fixed seed makes every random roll repeat from startup; leave it unset for a new one each start.
    val seed = System.getenv("OSRSPS_SEED")?.let { it.toLongOrNull() ?: error("OSRSPS_SEED must be a whole number, was '$it'") }
    // Names, comma separated, who may run the commands that change the world for everyone; unset, everyone may.
    val admins = Admins.parse(System.getenv("OSRSPS_ADMINS"))
    // The git commit deploy/auto-deploy.sh built this from, served at /version so scripts can wait for a deploy.
    val version = System.getenv("OSRSPS_COMMIT")?.takeIf { it.isNotBlank() } ?: ClientConfig.UNKNOWN_VERSION
    // The script's state folder, mounted read-only: after a merge that changes nothing the server is built from, the
    // script leaves the server running and /version answers with that merge's commit from there.
    val deployState = System.getenv("OSRSPS_DEPLOY_STATE")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
    // Addresses, comma separated, that forward many players (the VPS relay); each may hold more than 10 connections.
    // Plain IP addresses only, written the way connections report them: the connection limit, the PROXY header rule and
    // the bans all match a relay by its exact address.
    val relays = System.getenv("OSRSPS_RELAYS").orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
        .map { IpBans.normalize(it) ?: error("OSRSPS_RELAYS must list IP addresses, was '$it'") }.toSet()
    if (relays.isNotEmpty()) logger.info { "Relays: ${relays.joinToString()}" }
    // Whether the relays put a PROXY header in front of each player's connection, naming the player's own address:
    // off, optional (the default: with or without one) or required.
    val proxy = ProxyProtocol.parse(System.getenv("OSRSPS_PROXY_PROTOCOL"), relays)
    logger.info { proxy.toString() }
    // The live server's key must be the one whose modulus is committed, which friends' launchers carry. A local server
    // can use, or make, a key of its own with OSRSPS_LOCAL_KEY=1.
    val localKey = System.getenv("OSRSPS_LOCAL_KEY") == "1"
    if (localKey) logger.warn { "OSRSPS_LOCAL_KEY=1: any RSA key will do; our launcher can't log in unless it's the live one" }
    val rsaModulus = if (localKey) null else RsaKeys.committedModulus()
    val server = GameServer(dataDir, port, port + 1, publicHost, seed, admins, version, relays, proxy, rsaModulus, deployState)
    server.start()
    Runtime.getRuntime().addShutdownHook(Thread(server::stop))
}

private fun cacheInfo() {
    val summary = CacheInfo.read(cacheDir)
    summary.indices.forEach { logger.info { "index ${it.id}: ${it.groups} groups, revision ${it.revision}, crc ${it.crc}" } }
    logger.info { "Cache at ${cacheDir.toAbsolutePath()} has ${summary.indices.size} indices" }
}

/**
 * Writes the dialogue file with every key the server has, each under the Wiki section its words come from, keeping the
 * words already in it. The old file is kept as dialogue.txt.bak. A line it can't read stops it, so nothing is lost.
 */
private fun writeDialogueKeys() {
    val file = dataDir.resolve(DialogueFile.NAME)
    val old = if (Files.exists(file)) Files.readString(file) else null
    val read = old?.let(DialogueFile::parse)
    if (read != null && read.problems.isNotEmpty()) {
        read.problems.forEach { logger.error { "${file.fileName}: $it" } }
        logger.error { "Not rewriting ${file.toAbsolutePath()}: fix the lines above first" }
        exitProcess(1)
    }
    Files.createDirectories(dataDir)
    if (old != null) Files.writeString(dataDir.resolve(DialogueFile.NAME + ".bak"), old)
    Files.writeString(file, DialogueFile.render(DialogueLines.ALL, read?.words.orEmpty()))
    val loaded = DialogueFile.load(file, DialogueLines.ALL)
    logger.info { "Wrote ${file.toAbsolutePath()}: ${loaded.summary}" }
    loaded.problems.forEach { logger.warn { "${file.fileName}: $it" } }
}

/**
 * Sets an account's password, asked for twice without echo (or read as one line from a pipe), so it never lands in a
 * shell history, a log or a chat. Writes data/accounts/<name>.json the way a login claim does, which the running
 * server reads at each login, so no restart is needed. Works for an admin's name, which no login can claim.
 */
private fun setPassword(name: String?) {
    if (name == null) {
        System.err.println("Usage: osrsps set-password <name>")
        exitProcess(2)
    }
    val console = System.console()
    val password: CharArray
    if (console != null) {
        password = console.readPassword("New password for %s: ", name) ?: exitProcess(1)
        val again = console.readPassword("Again: ") ?: exitProcess(1)
        val same = password.contentEquals(again)
        again.fill('\u0000')
        if (!same) {
            password.fill('\u0000')
            System.err.println("The two passwords differ; nothing changed")
            exitProcess(1)
        }
    } else {
        password = (readlnOrNull() ?: "").toCharArray()
    }
    try {
        if (password.size < Accounts.MIN_SET_PASSWORD) {
            System.err.println("A password needs at least ${Accounts.MIN_SET_PASSWORD} characters; nothing changed")
            exitProcess(1)
        }
        if (!Accounts(FileSaveStore(dataDir.resolve("accounts"))).setPassword(name, password)) {
            System.err.println("'$name' isn't a name an account can have; nothing changed")
            exitProcess(1)
        }
    } finally {
        password.fill('\u0000')
    }
    println("Password set for $name")
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

