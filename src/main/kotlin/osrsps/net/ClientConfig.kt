package osrsps.net

import com.sun.net.httpserver.HttpServer
import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.Revision
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress

private val logger = KotlinLogging.logger {}

/**
 * Serves the two files a client launcher needs to find this server, `jav_config.ws` and `world_list.ws`,
 * `proxy-targets.yaml`, an RSProx target for this server that RSProx's "+" button imports from a URL, `version`,
 * the git commit this server was built from, so a script can wait for a deploy to be up, and `status`, that commit
 * with what the server started from and how many JS5 connections it has let in.
 *
 * RSProx reads the jav_config's `codebase` host, looks for a world with that host in the world list,
 * and connects to it on the game port. So the host in them must be one the player's PC can reach. Each file is written
 * for the host the client asked for it by (its HTTP Host header), so players on the LAN and players coming in through
 * a VPN or relay at another address are each sent their own. [publicHost] is the fallback, for a request with no
 * usable Host header: 127.0.0.1 through an SSH tunnel, or the Proxmox host's LAN address.
 */
class ClientConfig(
    private val publicHost: String,
    private val httpPort: Int,
    private val worldId: Int = WORLD_ID,
    /** Where the game listens; RSProx assumes 43594 unless its target says otherwise. */
    private val gamePort: Int = httpPort - 1,
    /** The git commit the server was built from (OSRSPS_COMMIT, set by deploy/auto-deploy.sh), or "unknown". */
    private val version: String = UNKNOWN_VERSION,
    /** The RuneLite build for each revision; the RSProx target names the one for [Revision.MAJOR]. */
    private val runelitePins: RuneLitePins = RuneLitePins.NONE,
) {
    private var server: HttpServer? = null

    /**
     * What the server started from, served at /status after the commit: the cache's source line and the game frame
     * check's findings, set once the cache is loaded. Lets the test VM see a cache swap without a shell on the server.
     */
    @Volatile
    var status: List<String> = emptyList()

    /** Lines worked out again for each /status request, after [status]: how many JS5 connections have come in. */
    @Volatile
    var liveStatus: () -> List<String> = { emptyList() }

    /** The login RSA key's modulus in hex, once loaded; until then there is no RSProx target to hand out. */
    @Volatile
    var modulusHex: String? = null

    /** The jav_config for a client that reached us at [host]. */
    fun javConfig(host: String = publicHost): String = buildString {
        appendLine("title=OSRSPS")
        appendLine("adverturl=http://$host:$httpPort/advert.ws")
        appendLine("codebase=http://$host/")
        appendLine("cachedir=osrsps")
        appendLine("storebase=0")
        appendLine("initial_jar=gamepack.jar")
        appendLine("initial_class=client.class")
        appendLine("termsurl=http://$host:$httpPort/terms.ws")
        appendLine("privacyurl=http://$host:$httpPort/privacy.ws")
        appendLine("viewerversion=124")
        appendLine("win_sub_version=1")
        appendLine("mac_sub_version=2")
        appendLine("other_sub_version=2")
        appendLine("download=0")
        appendLine("window_preferredwidth=800")
        appendLine("window_preferredheight=600")
        appendLine("advert_height=96")
        appendLine("applet_minwidth=765")
        appendLine("applet_minheight=503")
        appendLine("applet_maxwidth=5760")
        appendLine("applet_maxheight=2160")
        appendLine("msg=lang0=English")
        appendLine("msg=ok=OK")
        appendLine("msg=cancel=Cancel")
        appendLine("msg=message=Message")
        appendLine("msg=information=Information")
        appendLine("msg=options=Options")
        appendLine("msg=language=Language")
        appendLine("msg=loading_app=Loading application")
        appendLine("msg=loading_app_resources=Loading application resources")
        appendLine("msg=new_version=Update available")
        // Parameters as the live 241 client uses them, with the world list, world and auth hosts pointed at us.
        appendLine("param=2=http://$host:$httpPort/")
        appendLine("param=3=true")
        appendLine("param=4=1")
        appendLine("param=5=$WORLD_PROPERTIES")
        appendLine("param=7=0")
        appendLine("param=8=true")
        appendLine("param=9=ElZAIrq5NpKN6D3mDdihco3oPeYN2KFy2DCquj7JMmECPmLrDP3Bnw")
        appendLine("param=10=5")
        appendLine("param=11=http://$host:$httpPort/")
        appendLine("param=12=$worldId")
        appendLine("param=13=.runescape.com")
        appendLine("param=14=0")
        appendLine("param=15=0")
        appendLine("param=16=false")
        appendLine("param=17=http://$host:$httpPort/world_list.ws")
        appendLine("param=18=")
        appendLine("param=19=")
        appendLine("param=20=http://$host:$httpPort/")
        appendLine("param=21=0")
        appendLine("param=22=http://$host:$httpPort/")
        appendLine("param=25=${Revision.MAJOR}")
        appendLine("param=28=http://$host:$httpPort/")
        appendLine("param=29=1")
    }

    /** The binary world list for a client that reached us at [host]: one members world there. */
    fun worldList(host: String = publicHost): ByteArray =
        encodeWorldList(listOf(World(worldId, WORLD_PROPERTIES, host, "OSRSPS test world", 0, 1)))

    /**
     * The RSProx target for this server, in the format of RSProx's `proxy-targets.yaml`, or null before [modulusHex]
     * is known. The name is the one our own setup has always used, so RSProx keeps its client cache for it. The `.1`
     * subrevision is the one RSProx's README calls the safe pick: every revision has it. When [runelitePins] has a
     * build for [Revision.MAJOR], the target names its commit, so RSProx keeps launching that RuneLite after live and
     * RuneLite move to the next revision, which our server would turn away. RSProx's own table picks the same build
     * today; the line holds it there if that table changes.
     */
    fun proxyTargets(host: String = publicHost): String? {
        val modulus = modulusHex ?: return null
        return buildString {
            appendLine("config:")
            appendLine("  - name: $TARGET_NAME")
            appendLine("    jav_config_url: http://$host:$httpPort/jav_config.ws")
            appendLine("    revision: ${Revision.MAJOR}.1")
            appendLine("    modulus: $modulus")
            runelitePins[Revision.MAJOR]?.let { appendLine("    runelite_bootstrap_commithash: ${it.commit}") }
            if (gamePort != DEFAULT_GAME_PORT) appendLine("    game_server_port: $gamePort")
        }
    }

    fun start() {
        val http = HttpServer.create(InetSocketAddress(httpPort), 0)
        http.createContext("/jav_config.ws") { exchange ->
            exchange.respond("text/plain; charset=ISO-8859-1", javConfig(requestHost(exchange)).toByteArray(Charsets.ISO_8859_1))
        }
        http.createContext("/world_list.ws") { exchange ->
            exchange.respond("application/octet-stream", worldList(requestHost(exchange)))
        }
        http.createContext("/proxy-targets.yaml") { exchange ->
            val yaml = proxyTargets(requestHost(exchange))
            if (yaml == null) exchange.respond("text/plain", ByteArray(0), 503)
            else exchange.respond("text/yaml; charset=UTF-8", yaml.toByteArray(Charsets.UTF_8))
        }
        http.createContext("/version") { exchange ->
            exchange.respond("text/plain; charset=UTF-8", "$version\n".toByteArray(Charsets.UTF_8))
        }
        http.createContext("/status") { exchange ->
            val body = (listOf("commit $version") + status + liveStatus()).joinToString("") { "$it\n" }
            exchange.respond("text/plain; charset=UTF-8", body.toByteArray(Charsets.UTF_8))
        }
        http.createContext("/") { exchange -> exchange.respond("text/plain", ByteArray(0), 404) }
        http.start()
        server = http
        logger.info { "Client config at http://$publicHost:$httpPort/jav_config.ws (world $worldId on $publicHost)" }
        val pin = runelitePins[Revision.MAJOR]
        if (pin == null) logger.warn { "No RuneLite build pinned for revision ${Revision.MAJOR} in ${RuneLitePins.RESOURCE}; RSProx picks its own" }
        else logger.info { "The RSProx target pins RuneLite ${pin.runelite} (static.runelite.net commit ${pin.commit})" }
    }

    /**
     * The host [exchange] was addressed to, without its port, or [publicHost] when its Host header is missing or isn't a
     * plain host name or IPv4 address. Checked because it is written into the files: a line break or odd character
     * there could change what the client reads.
     */
    private fun requestHost(exchange: com.sun.net.httpserver.HttpExchange): String =
        hostOf(exchange.requestHeaders.getFirst("Host")) ?: publicHost

    /** The port the HTTP server listens on once started (differs from httpPort only when that was 0). */
    internal val boundPort: Int
        get() = server?.address?.port ?: httpPort

    fun stop() {
        server?.stop(0)
        server = null
    }

    data class World(val id: Int, val properties: Int, val host: String, val activity: String, val location: Int, val population: Int)

    companion object {
        const val WORLD_ID = 301

        private val HOST_NAME = Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?")

        /** The host part of a Host header ("example.com:43595" gives "example.com"), or null if it isn't a usable one. */
        fun hostOf(header: String?): String? {
            val host = header?.trim()?.substringBefore(':') ?: return null
            return host.takeIf { HOST_NAME.matches(it) }
        }
        const val TARGET_NAME = "OSRSPS"
        const val DEFAULT_GAME_PORT = 43594
        const val UNKNOWN_VERSION = "unknown"

        /** Members world. */
        const val WORLD_PROPERTIES = 1

        fun encodeWorldList(worlds: List<World>): ByteArray {
            val payload = ByteArrayOutputStream()
            DataOutputStream(payload).use { out ->
                out.writeShort(worlds.size)
                for (world in worlds) {
                    out.writeShort(world.id)
                    out.writeInt(world.properties)
                    out.writeJagexString(world.host)
                    out.writeJagexString(world.activity)
                    out.writeByte(world.location)
                    out.writeShort(world.population)
                }
            }
            val body = payload.toByteArray()
            val full = ByteArrayOutputStream()
            DataOutputStream(full).use { out ->
                out.writeInt(body.size)
                out.write(body)
            }
            return full.toByteArray()
        }

        private fun DataOutputStream.writeJagexString(value: String) {
            write(value.toByteArray(Charsets.ISO_8859_1))
            writeByte(0)
        }

        private fun com.sun.net.httpserver.HttpExchange.respond(contentType: String, body: ByteArray, status: Int = 200) {
            responseHeaders.add("Content-Type", contentType)
            sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
            responseBody.use { if (body.isNotEmpty()) it.write(body) }
        }
    }
}
