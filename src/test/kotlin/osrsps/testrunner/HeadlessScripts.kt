package osrsps.testrunner

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.system.exitProcess
import osrsps.GameServer
import osrsps.cache.CacheEntry
import osrsps.cache.OpenRs2Archive
import osrsps.game.Accounts
import osrsps.game.FileAccountLock
import osrsps.game.FileSaveStore
import osrsps.game.GameFrame
import osrsps.game.World
import osrsps.game.command.Admins
import osrsps.net.RsaKeys
import osrsps.testclient.HeadlessClient

/**
 * Plays the RuneLite runner's JSON scripts (test-runner/examples) against our server with no RuneLite and no VM: the
 * server runs in this process on the live 242 cache, and each script's account plays through a [HeadlessClient]
 * ([ScriptSession]). As the nightly did on VM 217: a reset before every script, every failure run once more (a pass
 * then is "flaky"), a result file per script and one summary.json for the run.
 *
 * `./gradlew headlessScripts` runs every example; see the task in build.gradle.kts for its options.
 */
fun main(args: Array<String>) {
    exitProcess(HeadlessScripts.run(HeadlessScripts.Options.parse(args)))
}

internal object HeadlessScripts {
    /** The OpenRS2 cache VM 214 serves (its data/cache-source.txt), so a run plays what the live server does. */
    const val CACHE_ID = 2757

    /** Every run starts from the same random seed, so a run can be played again roll for roll. */
    const val SEED = 241L

    data class Options(
        /** Where the cache is kept between runs and the server keeps its saves; the saves are wiped at each start. */
        val dataDir: Path,
        val scripts: List<Path>,
        val outDir: Path,
        val tickMillis: Long = World.TICK_MILLIS,
        val rerunFailures: Boolean = true,
        val commit: String = "unknown",
        val runnerVersion: String = "unknown",
    ) {
        companion object {
            fun parse(args: Array<String>): Options {
                val named = HashMap<String, String>()
                val scripts = ArrayList<Path>()
                var i = 0
                while (i < args.size) {
                    val arg = args[i]
                    if (arg.startsWith("--")) {
                        require(i + 1 < args.size) { "$arg needs a value" }
                        named[arg.removePrefix("--")] = args[i + 1]
                        i += 2
                    } else {
                        scripts.add(Path.of(arg))
                        i++
                    }
                }
                val files = scripts.flatMap { path ->
                    if (path.isDirectory()) path.listDirectoryEntries("*.json").sortedBy { it.name } else listOf(path)
                }
                require(files.isNotEmpty()) { "Name the scripts to run, or a folder of them" }
                // A script's name is its id in the results and summary.json, so two of a name would overwrite each other.
                val twice = files.groupBy { it.name }.filterValues { it.size > 1 }.keys
                require(twice.isEmpty()) { "Two scripts share a name, which their results would share too: ${twice.sorted().joinToString()}" }
                return Options(
                    dataDir = Path.of(named["data"] ?: "build/headless/data"),
                    scripts = files,
                    outDir = Path.of(named["out"] ?: "build/headless/results"),
                    tickMillis = named["tick-ms"]?.toLong() ?: World.TICK_MILLIS,
                    rerunFailures = named["rerun"]?.toBooleanStrict() ?: true,
                    commit = named["commit"] ?: "unknown",
                    runnerVersion = named["runner-version"] ?: "unknown",
                )
            }
        }
    }

    /** One script's outcome in summary.json, as the nightly's rows have it. */
    data class Row(val script: String, val result: String, val steps: String, val seconds: Long, val failure: String?, val rerun: String?)

    /** Runs the scripts; returns 0 when none failed (a flaky pass and a skipped pair don't count), else 1. */
    fun run(options: Options): Int {
        val started = Instant.now()
        prepareData(options.dataDir)
        // An admin's name can't be claimed by a login, so the test accounts get the runner's default password first.
        val accountDir = options.dataDir.resolve("accounts")
        val accounts = Accounts(FileSaveStore(accountDir), lock = FileAccountLock(accountDir))
        for (account in ScriptSession.ACCOUNTS) check(accounts.setPassword(account, PASSWORD.toCharArray())) { "Can't set $account's password" }
        val ports = freePorts()
        // The test accounts must be admins for the scripts' commands; a fresh data folder has no other accounts.
        val server = GameServer(
            options.dataDir, ports, ports + 1, seed = SEED, admins = Admins.parse(ScriptSession.ACCOUNTS.joinToString(",")),
            version = options.commit, cycleMillis = options.tickMillis,
        )
        server.start()
        val key = RsaKeys.load(options.dataDir)
        val types = HeadlessGame.Types.of(server.world)
        val sessions = HashMap<String, ScriptSession>()
        fun session(account: String) = sessions.getOrPut(account.lowercase()) {
            ScriptSession(account, { name -> HeadlessClient.login("127.0.0.1", ports, key.modulus, RSA_EXPONENT, name, PASSWORD, huffman = server.huffman) }, types)
        }
        val writer = ResultWriter(options.outDir.resolve("test-results"), GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create(), "headless ${options.runnerVersion}")
        val rows = LinkedHashMap<String, Row>()
        val failed = ArrayList<Pair<Path, Script>>()
        try {
            for (file in options.scripts) {
                val script = try {
                    Script.parse(file.name, file.readText())
                } catch (e: Script.Invalid) {
                    rows[file.name] = Row(file.name, "error", "0/0", 0, "the script doesn't read: ${e.message}", null)
                    continue
                }
                val pair = pairReason(file.name)
                if (pair != null) {
                    rows[file.name] = Row(file.name, "skipped", "0/${script.steps.size}", 0, pair, null)
                    continue
                }
                val result = play(session(script.account), script, writer)
                rows[file.name] = row(file.name, result)
                if (result.result != Result.PASS) failed += file to script
            }
            if (options.rerunFailures) {
                for ((file, script) in failed) {
                    // Parsed again: a script's steps keep their progress (a command already sent), so a run can't be replayed.
                    val again = play(session(script.account), Script.parse(file.name, file.readText()), writer)
                    val first = rows.getValue(file.name)
                    rows[file.name] = if (again.result == Result.PASS) {
                        first.copy(result = "flaky", rerun = "pass")
                    } else {
                        first.copy(rerun = again.failure ?: again.result)
                    }
                }
            }
        } finally {
            sessions.values.forEach(ScriptSession::close)
            server.stop()
        }
        writeSummary(options, started, rows.values.toList())
        val failures = rows.values.count { it.result == Result.FAIL || it.result == "error" }
        println("Headless scripts: ${rows.values.count { it.result == Result.PASS }} passed, ${rows.values.count { it.result == "flaky" }} flaky, " +
            "$failures failed, ${rows.values.count { it.result == "skipped" }} skipped, of ${rows.size}")
        rows.values.filter { it.result != Result.PASS }.forEach { println("  ${it.result}: ${it.script}: ${it.failure}") }
        return if (failures == 0) 0 else 1
    }

    /** Puts the account back as the nightly did before each script, then plays the script and writes its result. */
    private fun play(session: ScriptSession, script: Script, writer: ResultWriter): Result {
        session.closeInterfaces()
        val reset = session.run(Script.parse("_between.json", resetScript(script.account)), "reset")
        if (reset.result != Result.PASS) {
            println("Reset before ${script.id} failed: ${reset.failure}")
        }
        val result = session.run(script, "headless")
        writer.write(result, null)
        println("${result.result}: ${script.id} (${result.ms / 1000} s)" + (result.failure?.let { ": $it" } ?: ""))
        return result
    }

    /**
     * The nightly's reset (its rc-instructions, steps 4 to 7): no dialogue, the account back, keys off, by the castle.
     * First the fixed game frame, which the VMs' clients used and the scripts' toplevel (548) clicks assume: a new
     * account here starts resizable, as live's do after Tutorial Island. A pick of the frame already shown does nothing.
     */
    fun resetScript(account: String) = """
        {"name": "reset between scripts", "account": "$account", "steps": [
          {"step": "waitForLogin"},
          {"step": "clickWidget", "interfaceId": ${GameFrame.SETTINGS_SIDE}, "component": ${GameFrame.SETTINGS_CLIENT_LAYOUT_BUTTONS}, "slot": $FIXED_LAYOUT_OPTION, "op": 1},
          {"step": "finishDialogue"},
          {"step": "command", "text": "::reset"},
          {"step": "command", "text": "::resetlocs 10"},
          {"step": "command", "text": "::keys off"},
          {"step": "command", "text": "::tele 3222 3218 0", "expectMessage": "Teleported to 3222, 3218, 0"},
          {"step": "waitTicks", "ticks": 2}
        ]}
    """.trimIndent()

    /**
     * Why a script is left out, or null: the two halves of a two-player pair (-test.json and -test2.json) have to play
     * at the same time, which this runner doesn't do yet.
     */
    fun pairReason(fileName: String): String? =
        if (fileName.endsWith("-test.json") || fileName.endsWith("-test2.json")) "one half of a two-player pair, which doesn't run headless yet" else null

    private fun row(file: String, r: Result): Row {
        val passed = r.steps.count { it.outcome == Result.PASS }
        return Row(file, r.result, "$passed/${r.steps.size}", r.ms / 1000, r.failure, null)
    }

    /**
     * The cache, fetched once from OpenRS2 and kept; everything the server wrote last time (saves, accounts, clans, its
     * key) wiped, so each run starts from the same fresh world.
     */
    private fun prepareData(dataDir: Path) {
        Files.createDirectories(dataDir)
        val keep = setOf("cache", "cache-source.txt", "item-prices.tsv")
        Files.list(dataDir).use { files -> files.filter { it.name !in keep }.toList() }.forEach { it.toFile().deleteRecursively() }
        val cacheDir = dataDir.resolve("cache")
        val source = dataDir.resolve("cache-source.txt")
        val wanted = "openrs2 runescape/$CACHE_ID build 242"
        if (cacheDir.exists() && source.exists() && source.readText().startsWith(wanted)) return
        println("Fetching OpenRS2 cache $CACHE_ID into $cacheDir")
        cacheDir.toFile().deleteRecursively()
        OpenRs2Archive().download(CacheEntry(CACHE_ID, "runescape", 242, null, null), cacheDir)
        Files.writeString(source, "$wanted\n")
    }

    /** A free port whose next port is free too: the game port and the client files' HTTP port beside it. */
    private fun freePorts(): Int {
        repeat(20) {
            val port = ServerSocket(0).use { it.localPort }
            if (port < 65535 && runCatching { ServerSocket(port + 1).close() }.isSuccess) return port
        }
        error("No two free ports side by side")
    }

    private fun writeSummary(options: Options, started: Instant, rows: List<Row>) {
        val json = JsonObject()
        json.addProperty("sha", options.commit)
        json.addProperty("serverVersionStart", options.commit)
        json.addProperty("serverVersionEnd", options.commit)
        json.addProperty("runner", "headless ${options.runnerVersion}")
        json.addProperty("started", started.toString())
        json.addProperty("finished", Instant.now().toString())
        val array = JsonArray()
        for (row in rows) {
            array.add(JsonObject().apply {
                addProperty("script", row.script)
                addProperty("result", row.result)
                addProperty("steps", row.steps)
                addProperty("seconds", row.seconds)
                addProperty("failure", row.failure)
                addProperty("rerun", row.rerun)
            })
        }
        json.add("rows", array)
        Files.createDirectories(options.outDir)
        Files.writeString(options.outDir.resolve("summary.json"), GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create().toJson(json) + "\n")
    }

    /** The settings side panel's "Game client layout" option for the fixed frame ([GameFrame.forLayoutOption]). */
    private const val FIXED_LAYOUT_OPTION = 1

    /** The RuneLite plugin's default Auto login password; this server is local to the run and gone after it. */
    private const val PASSWORD = "test"

    /** The public exponent [RsaKeys.generate] gives every key (Java's RSA default). */
    private val RSA_EXPONENT = java.math.BigInteger.valueOf(65537)
}
