package osrsps.net

import com.sun.net.httpserver.HttpServer
import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.Revision
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress

private val logger = KotlinLogging.logger {}

/**
 * Serves the two files a client launcher needs to find this server, `jav_config.ws` and `world_list.ws`, and
 * `proxy-targets.yaml`, an RSProx target for this server that RSProx's "+" button imports from a URL.
 *
 * RSProx reads the jav_config's `codebase` host, looks for a world with that host in the world list,
 * and connects to it on the game port. So [publicHost] must be the address the player's PC can reach:
 * 127.0.0.1 through an SSH tunnel, or the Proxmox host's LAN address once it forwards the ports.
 */
class ClientConfig(
    private val publicHost: String,
    private val httpPort: Int,
    private val worldId: Int = WORLD_ID,
    /** Where the game listens; RSProx assumes 43594 unless its target says otherwise. */
    private val gamePort: Int = httpPort - 1,
) {
    private var server: HttpServer? = null

    /** The login RSA key's modulus in hex, once loaded; until then there is no RSProx target to hand out. */
    @Volatile
    var modulusHex: String? = null

    val javConfig: String
        get() = buildString {
            appendLine("title=OSRSPS")
            appendLine("adverturl=http://$publicHost:$httpPort/advert.ws")
            appendLine("codebase=http://$publicHost/")
            appendLine("cachedir=osrsps")
            appendLine("storebase=0")
            appendLine("initial_jar=gamepack.jar")
            appendLine("initial_class=client.class")
            appendLine("termsurl=http://$publicHost:$httpPort/terms.ws")
            appendLine("privacyurl=http://$publicHost:$httpPort/privacy.ws")
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
            appendLine("param=2=http://$publicHost:$httpPort/")
            appendLine("param=3=true")
            appendLine("param=4=1")
            appendLine("param=5=$WORLD_PROPERTIES")
            appendLine("param=7=0")
            appendLine("param=8=true")
            appendLine("param=9=ElZAIrq5NpKN6D3mDdihco3oPeYN2KFy2DCquj7JMmECPmLrDP3Bnw")
            appendLine("param=10=5")
            appendLine("param=11=http://$publicHost:$httpPort/")
            appendLine("param=12=$worldId")
            appendLine("param=13=.runescape.com")
            appendLine("param=14=0")
            appendLine("param=15=0")
            appendLine("param=16=false")
            appendLine("param=17=http://$publicHost:$httpPort/world_list.ws")
            appendLine("param=18=")
            appendLine("param=19=")
            appendLine("param=20=http://$publicHost:$httpPort/")
            appendLine("param=21=0")
            appendLine("param=22=http://$publicHost:$httpPort/")
            appendLine("param=25=${Revision.MAJOR}")
            appendLine("param=28=http://$publicHost:$httpPort/")
            appendLine("param=29=1")
        }

    /** The binary world list: one members world at [publicHost]. */
    val worldList: ByteArray
        get() = encodeWorldList(listOf(World(worldId, WORLD_PROPERTIES, publicHost, "OSRSPS test world", 0, 1)))

    /**
     * The RSProx target for this server, in the format of RSProx's `proxy-targets.yaml`, or null before [modulusHex]
     * is known. The name is the one our own setup has always used, so RSProx keeps its client cache for it. The `.1`
     * subrevision is the one RSProx's README calls the safe pick: every revision has it.
     */
    val proxyTargets: String?
        get() {
            val modulus = modulusHex ?: return null
            return buildString {
                appendLine("config:")
                appendLine("  - name: $TARGET_NAME")
                appendLine("    jav_config_url: http://$publicHost:$httpPort/jav_config.ws")
                appendLine("    revision: ${Revision.MAJOR}.1")
                appendLine("    modulus: $modulus")
                if (gamePort != DEFAULT_GAME_PORT) appendLine("    game_server_port: $gamePort")
            }
        }

    fun start() {
        val http = HttpServer.create(InetSocketAddress(httpPort), 0)
        http.createContext("/jav_config.ws") { exchange ->
            exchange.respond("text/plain; charset=ISO-8859-1", javConfig.toByteArray(Charsets.ISO_8859_1))
        }
        http.createContext("/world_list.ws") { exchange ->
            exchange.respond("application/octet-stream", worldList)
        }
        http.createContext("/proxy-targets.yaml") { exchange ->
            val yaml = proxyTargets
            if (yaml == null) exchange.respond("text/plain", ByteArray(0), 503)
            else exchange.respond("text/yaml; charset=UTF-8", yaml.toByteArray(Charsets.UTF_8))
        }
        http.createContext("/") { exchange -> exchange.respond("text/plain", ByteArray(0), 404) }
        http.start()
        server = http
        logger.info { "Client config at http://$publicHost:$httpPort/jav_config.ws (world $worldId on $publicHost)" }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    data class World(val id: Int, val properties: Int, val host: String, val activity: String, val location: Int, val population: Int)

    companion object {
        const val WORLD_ID = 301
        const val TARGET_NAME = "OSRSPS"
        const val DEFAULT_GAME_PORT = 43594

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
