package osrsps.net

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.ReferenceCountUtil
import org.jire.netty.haproxy.HAProxyAttributes.haproxyAttribute
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import osrsps.Revision
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

/**
 * Serves the two files a client launcher needs to find this server, `jav_config.ws` and `world_list.ws`,
 * `proxy-targets.yaml`, an RSProx target for this server that RSProx's "+" button imports from a URL, `version`,
 * the git commit of main this server runs ([servedVersion]), so a script can wait for a deploy to be up, and `status`,
 * that commit with what the server started from and how many JS5 connections it has let in. Our own launcher gets
 * copies of the first two under `/launcher/` ([ClientKind]), `status.json` ([statusJson]) and RuneLite's world list
 * ([runeLiteWorlds]).
 *
 * RSProx reads the jav_config's `codebase` host, looks for a world with that host in the world list,
 * and connects to it on the game port. So the host in them must be one the player's PC can reach. Each file is written
 * for the host the client asked for it by (its HTTP Host header), so players on the LAN and players coming in through
 * a VPN or relay at another address are each sent their own. [publicHost] is the fallback, for a request with no
 * usable Host header: 127.0.0.1 through an SSH tunnel, or the Proxmox host's LAN address.
 *
 * The one thing it takes is a POST of a problem report to `/report`, from the Odin's Gate launcher's "Send problem report"
 * button; [reports] keeps it ([ProblemReports]) and the answer is the report's number.
 *
 * Served with Netty rather than the JDK's HTTP server so that, like the game port, it reads a relay's PROXY header
 * ([proxy]): the VPS's HAProxy puts one in front of both ports or neither.
 */
class ClientConfig(
    private val publicHost: String,
    private val httpPort: Int,
    private val worldId: Int = WORLD_ID,
    /** Where the game listens; RSProx assumes 43594 unless its target says otherwise. */
    private val gamePort: Int = httpPort - 1,
    /** The git commit the server was built from (OSRSPS_COMMIT, set by deploy/auto-deploy.sh), or "unknown". */
    private val version: String = UNKNOWN_VERSION,
    /**
     * deploy/auto-deploy.sh's state folder (OSRSPS_DEPLOY_STATE, its `.deploy/` mounted read-only), or null. Its
     * `built` file names the commit the running server was built from and `deployed` the newest commit of main
     * deployed, which is newer when the merges since changed nothing the server is built from; see [servedVersion].
     */
    private val deployState: Path? = null,
    /** Who may put a PROXY header in front of a request; see [ProxyProtocol]. */
    private val proxy: ProxyProtocol = ProxyProtocol.OFF,
    /** The RuneLite build for each revision; the RSProx target names the one for [Revision.MAJOR]. */
    private val runelitePins: RuneLitePins = RuneLitePins.NONE,
    /** Where POST /report keeps the launcher's problem reports; null (tests) answers 503. */
    private val reports: ProblemReports? = null,
    /** The time now, for the read timeout and the report deadline ([sweep]); tests pass their own. */
    private val clock: () -> Instant = Instant::now,
) {
    private var group: EventLoopGroup? = null
    private var server: Channel? = null

    /**
     * What the server started from, served at /status after the commit: the cache's source line and the game frame
     * check's findings, set once the cache is loaded. Lets the test VM see a cache swap without a shell on the server.
     */
    @Volatile
    var status: List<String> = emptyList()

    /** The game as it is now, read again for each /status and /status.json request; down until the server sets it. */
    @Volatile
    var gameStatus: () -> GameStatus = { GameStatus.DOWN }

    /** The login RSA key's modulus in hex, once loaded; until then there is no RSProx target to hand out. */
    @Volatile
    var modulusHex: String? = null

    /** The jav_config for a client of [kind] that reached us at [host]. */
    fun javConfig(host: String = publicHost, kind: ClientKind = ClientKind.RSPROX): String = buildString {
        appendLine("title=${kind.title}")
        appendLine("adverturl=http://$host:$httpPort/advert.ws")
        appendLine("codebase=http://$host/")
        appendLine("cachedir=${kind.cacheDir}")
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
        appendLine("param=17=http://$host:$httpPort${kind.path}/world_list.ws")
        appendLine("param=18=")
        appendLine("param=19=")
        appendLine("param=20=http://$host:$httpPort/")
        appendLine("param=21=0")
        appendLine("param=22=http://$host:$httpPort/")
        appendLine("param=25=${Revision.MAJOR}")
        appendLine("param=28=http://$host:$httpPort/")
        appendLine("param=29=1")
    }

    /** The binary world list for a client of [kind] that reached us at [host]: one members world there. */
    fun worldList(host: String = publicHost, kind: ClientKind = ClientKind.RSPROX): ByteArray =
        encodeWorldList(listOf(World(worldId, WORLD_PROPERTIES, host, kind.worldActivity, WORLD_LOCATION, 1)))

    /**
     * RuneLite's world list, the `worlds.js` of its web API, for a client that reached us at [host]: our one world
     * there, members as [WORLD_PROPERTIES] says. The launcher points RuneLite's API at /rl/
     * (`-Drunelite.http-service.url`), so RuneLite's world hopper offers this world and never one of Jagex's.
     * RuneLite's other calls to its API find nothing there (404), as they found nothing at the launcher spike's
     * stand-in for the API, which RuneLite ran fine with.
     */
    fun runeLiteWorlds(host: String = publicHost): String = buildJsonObject {
        putJsonArray("worlds") {
            addJsonObject {
                put("id", worldId)
                putJsonArray("types") { add("MEMBERS") }
                put("address", host)
                put("activity", ClientKind.LAUNCHER.worldActivity)
                put("location", WORLD_LOCATION)
                put("players", gameStatus().players)
            }
        }
    }.toString()

    /**
     * How the server is doing, for the launcher: whether the game is up and taking logins, the revision it plays (which
     * RuneLite build to start), the commit it runs ([servedVersion]), how many players are in, and the JS5 count
     * /status gives. The launcher reads it before it starts the game and once a minute after, to see the server move
     * to a new revision.
     */
    fun statusJson(): String {
        val game = gameStatus()
        return buildJsonObject {
            put("up", game.up)
            put("revision", Revision.MAJOR)
            put("commit", servedVersion())
            put("players", game.players)
            put("acceptingLogins", game.acceptingLogins)
            put("js5Connections", game.js5Connections)
        }.toString()
    }

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

    /**
     * The commit of main this server runs: [version], or a newer commit deploy/auto-deploy.sh deployed without a
     * restart because the merges since changed nothing the server is built from (docs, tests, the RuneLite plugins,
     * the launcher). The script records that in [deployState] as it goes, so this reads it for each request. A server
     * that isn't the build the folder names (one started by hand, say) answers with its own commit.
     */
    fun servedVersion(): String {
        val state = deployState ?: return version
        fun read(name: String) = runCatching { Files.readString(state.resolve(name)).trim() }.getOrNull()
        val deployed = read("deployed") ?: return version
        return if (read("built") == version && COMMIT.matches(deployed)) deployed else version
    }

    /** The body and content type for a GET of [path] by a client that reached us at [host]; null for a 404. */
    internal fun respond(path: String, host: String): Response? = when (path) {
        "/jav_config.ws" -> javConfigResponse(host, ClientKind.RSPROX)
        "/world_list.ws" -> Response("application/octet-stream", worldList(host))
        "/launcher/jav_config.ws" -> javConfigResponse(host, ClientKind.LAUNCHER)
        "/launcher/world_list.ws" -> Response("application/octet-stream", worldList(host, ClientKind.LAUNCHER))
        "/rl/worlds.js" -> Response(JSON, runeLiteWorlds(host).toByteArray(Charsets.UTF_8))
        "/proxy-targets.yaml" -> proxyTargets(host)?.let { Response("text/yaml; charset=UTF-8", it.toByteArray(Charsets.UTF_8)) }
            ?: Response("text/plain", ByteArray(0), HttpResponseStatus.SERVICE_UNAVAILABLE)
        "/version" -> Response("text/plain; charset=UTF-8", "${servedVersion()}\n".toByteArray(Charsets.UTF_8))
        "/status" -> {
            val served = servedVersion()
            val commit = if (served == version) listOf("commit $served") else listOf("commit $served", "built from $version")
            val lines = commit + status + "js5 connections ${gameStatus().js5Connections}"
            Response("text/plain; charset=UTF-8", lines.joinToString("") { "$it\n" }.toByteArray(Charsets.UTF_8))
        }
        "/status.json" -> Response(JSON, statusJson().toByteArray(Charsets.UTF_8))
        else -> null
    }

    private fun javConfigResponse(host: String, kind: ClientKind) =
        Response("text/plain; charset=ISO-8859-1", javConfig(host, kind).toByteArray(Charsets.ISO_8859_1))

    internal class Response(val contentType: String, val body: ByteArray, val status: HttpResponseStatus = HttpResponseStatus.OK)

    /** Starts serving; throws (a BindException) when the port can't be bound, leaving nothing running. */
    fun start() {
        val group = MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
        try {
            val bootstrap = ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel::class.java)
                .handler(proxy.acceptor)
                .childHandler(HttpInitializer())
            server = bootstrap.bind(httpPort).sync().channel()
        } catch (e: Exception) {
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS)
            throw e
        }
        this.group = group
        group.next().scheduleWithFixedDelay(::sweep, SWEEP_SECONDS, SWEEP_SECONDS, TimeUnit.SECONDS)
        logger.info { "Client config at http://$publicHost:$httpPort/jav_config.ws (world $worldId on $publicHost)" }
        val pin = runelitePins[Revision.MAJOR]
        if (pin == null) logger.warn { "No RuneLite build pinned for revision ${Revision.MAJOR} in ${RuneLitePins.RESOURCE}; RSProx picks its own" }
        else logger.info { "The RSProx target pins RuneLite ${pin.runelite} (static.runelite.net commit ${pin.commit})" }
    }

    @ChannelHandler.Sharable
    private inner class HttpInitializer : ChannelInitializer<Channel>() {
        override fun initChannel(ch: Channel) {
            ch.pipeline().addLast(
                ConnectionLimit(), HttpServerCodec(), BodyLimit(), HttpObjectAggregator(ProblemReports.MAX_BYTES), RequestHandler(),
            )
        }
    }

    /** HTTP connections open now, against [MAX_CONNECTIONS]. */
    private val openConnections = AtomicInteger()
    /** Report bodies being read now, in all and by address, against [MAX_REPORTS_READING] and [MAX_REPORTS_READING_PER_ADDRESS]. */
    private val reportsReading = AtomicInteger()
    private val reportsReadingBy = ConcurrentHashMap<String, Int>()

    /** Each open connection, with when it last sent anything and since when it has been sending a report's body. */
    private val connections = ConcurrentHashMap<Channel, Activity>()

    /** How many report bodies are being read now, so a test can tell a report was taken in before it moves the clock. */
    internal val reportsBeingRead: Int get() = connections.values.count { it.readingSince != null }

    /** How many reads the connections have had, so a test can tell the server has seen what it sent. */
    internal val reads = java.util.concurrent.atomic.AtomicLong()

    private class Activity(@Volatile var lastRead: Instant) {
        @Volatile var readingSince: Instant? = null
    }

    /**
     * Closes every connection that has sent nothing for [READ_TIMEOUT_SECONDS], or has been sending one report's body
     * for [REPORT_DEADLINE_SECONDS], however slowly it trickles. Runs every [SWEEP_SECONDS] once started; tests call it
     * with their own clock.
     */
    internal fun sweep() {
        val now = clock()
        for ((channel, activity) in connections) {
            val idle = Duration.between(activity.lastRead, now) >= READ_TIMEOUT
            val late = activity.readingSince?.let { Duration.between(it, now) >= REPORT_DEADLINE } == true
            if (idle || late) channel.close()
        }
    }

    /**
     * Closes a connection past [MAX_CONNECTIONS] the moment it opens. The port is reachable from the internet, and a
     * connection costs a little memory however idle; [sweep] ends the idle ones.
     */
    private inner class ConnectionLimit : ChannelInboundHandlerAdapter() {
        private var counted = false

        override fun channelActive(ctx: ChannelHandlerContext) {
            if (openConnections.incrementAndGet() > MAX_CONNECTIONS) {
                openConnections.decrementAndGet()
                ctx.close()
                return
            }
            counted = true
            connections[ctx.channel()] = Activity(clock())
            ctx.fireChannelActive()
        }

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            connections[ctx.channel()]?.lastRead = clock()
            reads.incrementAndGet()
            ctx.fireChannelRead(msg)
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            if (counted) openConnections.decrementAndGet()
            counted = false
            connections.remove(ctx.channel())
            ctx.fireChannelInactive()
        }
    }

    /**
     * Decides, before the aggregator buffers anything, whether a request's body is worth reading. A body on anything
     * but a POST of a report is refused (413): the aggregator's own limit is a report's, which a GET has no use for.
     * A report is refused before its body is read when its sender is over the hourly limits ([ProblemReports.admits],
     * 429), already has [MAX_REPORTS_READING_PER_ADDRESS] bodies being read (429), or [MAX_REPORTS_READING] are being
     * read in all (503); one being read must arrive whole within [REPORT_DEADLINE_SECONDS] ([sweep]), however slowly it trickles.
     * So at most [MAX_REPORTS_READING] report bodies are ever held at once. A refused request is answered, the
     * connection is closed, and anything still arriving for it is dropped.
     */
    private inner class BodyLimit : ChannelInboundHandlerAdapter() {
        private var dropping = false
        /** The address whose report body this connection is reading, while it reads one. */
        private var reading: String? = null

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            if (msg is HttpRequest) {
                done(ctx)
                val refusal = when {
                    !isReport(msg) ->
                        if (HttpUtil.isTransferEncodingChunked(msg) || HttpUtil.getContentLength(msg, 0L) > MAX_REQUEST_BYTES) HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE else null
                    else -> startReading(ctx, addressOf(ctx.channel()))
                }
                dropping = refusal != null
                if (refusal != null) {
                    val reply = DefaultFullHttpResponse(msg.protocolVersion(), refusal)
                    if (refusal == HttpResponseStatus.TOO_MANY_REQUESTS) reply.headers().set(HttpHeaderNames.RETRY_AFTER, 3600)
                    HttpUtil.setContentLength(reply, 0)
                    ctx.writeAndFlush(reply).addListener(ChannelFutureListener.CLOSE)
                }
            }
            if (dropping && (msg is HttpRequest || msg is HttpContent)) {
                ReferenceCountUtil.release(msg)
                return
            }
            val last = msg is LastHttpContent
            ctx.fireChannelRead(msg)
            if (last) done(ctx)
        }

        /** Why a report from [address] can't be read now, or null when it can, counting it as being read. */
        private fun startReading(ctx: ChannelHandlerContext, address: String): HttpResponseStatus? {
            if (reports?.admits(address) == false) return HttpResponseStatus.TOO_MANY_REQUESTS
            if (reportsReading.incrementAndGet() > MAX_REPORTS_READING) {
                reportsReading.decrementAndGet()
                return HttpResponseStatus.SERVICE_UNAVAILABLE
            }
            if (reportsReadingBy.merge(address, 1, Int::plus)!! > MAX_REPORTS_READING_PER_ADDRESS) {
                release(address)
                return HttpResponseStatus.TOO_MANY_REQUESTS
            }
            reading = address
            connections[ctx.channel()]?.readingSince = clock()
            return null
        }

        private fun release(address: String) {
            reportsReading.decrementAndGet()
            reportsReadingBy.computeIfPresent(address) { _, n -> (n - 1).takeIf { it > 0 } }
        }

        /** The report body this connection was reading, if any, is no longer being read. */
        private fun done(ctx: ChannelHandlerContext) {
            connections[ctx.channel()]?.readingSince = null
            reading?.let(::release)
            reading = null
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            done(ctx)
            ctx.fireChannelInactive()
        }

        private fun isReport(request: HttpRequest) =
            request.method() == HttpMethod.POST && QueryStringDecoder(request.uri()).path() == REPORT_PATH
    }

    /** The answer to a POST /report: the report's number, or why it wasn't taken. */
    internal fun report(body: ByteArray, address: String): Response {
        val result = reports?.submit(body, address) ?: ProblemReports.Result.Unavailable
        fun text(status: HttpResponseStatus, line: String) = Response("text/plain; charset=UTF-8", "$line\n".toByteArray(Charsets.UTF_8), status)
        return when (result) {
            is ProblemReports.Result.Accepted -> text(HttpResponseStatus.CREATED, result.id)
            ProblemReports.Result.NotAReport -> text(HttpResponseStatus.BAD_REQUEST, "not a problem report")
            ProblemReports.Result.TooLarge -> text(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, "too large")
            ProblemReports.Result.TooMany -> text(HttpResponseStatus.TOO_MANY_REQUESTS, "too many reports, try again in an hour")
            ProblemReports.Result.Unavailable -> text(HttpResponseStatus.SERVICE_UNAVAILABLE, "reports can't be taken right now")
        }
    }

    private inner class RequestHandler : SimpleChannelInboundHandler<FullHttpRequest>() {
        override fun channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest) {
            val path = if (request.decoderResult().isSuccess) QueryStringDecoder(request.uri()).path() else null
            val response = if (path == null) {
                Response("text/plain", ByteArray(0), HttpResponseStatus.BAD_REQUEST)
            } else if (path == REPORT_PATH) {
                if (request.method() == HttpMethod.POST) {
                    val body = ByteArray(request.content().readableBytes()).also { request.content().readBytes(it) }
                    report(body, addressOf(ctx.channel()))
                } else {
                    Response("text/plain", ByteArray(0), HttpResponseStatus.METHOD_NOT_ALLOWED)
                }
            } else {
                respond(path, hostOf(request.headers().get(HttpHeaderNames.HOST)) ?: publicHost)
                    ?: Response("text/plain", ByteArray(0), HttpResponseStatus.NOT_FOUND)
            }
            val reply = DefaultFullHttpResponse(request.protocolVersion(), response.status, Unpooled.wrappedBuffer(response.body))
            reply.headers().set(HttpHeaderNames.CONTENT_TYPE, response.contentType)
            if (response.status == HttpResponseStatus.TOO_MANY_REQUESTS) reply.headers().set(HttpHeaderNames.RETRY_AFTER, 3600)
            HttpUtil.setContentLength(reply, response.body.size.toLong())
            val keepAlive = HttpUtil.isKeepAlive(request) && request.decoderResult().isSuccess
            HttpUtil.setKeepAlive(reply, keepAlive)
            val write = ctx.writeAndFlush(reply)
            if (!keepAlive) write.addListener(ChannelFutureListener.CLOSE)
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            logger.debug(cause) { "Client config connection from ${ctx.channel().remoteAddress()} failed" }
            ctx.close()
        }
    }

    /** The port the HTTP server listens on once started (differs from httpPort only when that was 0). */
    internal val boundPort: Int
        get() = (server?.localAddress() as? InetSocketAddress)?.port ?: httpPort

    fun stop() {
        server?.close()?.syncUninterruptibly()
        server = null
        group?.shutdownGracefully(0, 5, TimeUnit.SECONDS)?.syncUninterruptibly()
        group = null
    }

    data class World(val id: Int, val properties: Int, val host: String, val activity: String, val location: Int, val population: Int)

    /**
     * What /status.json says about the game: whether it is [up] (its game loop is running), how many [players] are in
     * it, whether it is [acceptingLogins], and how many [js5Connections] (cache connections) it has let in since it
     * started.
     */
    data class GameStatus(val up: Boolean, val players: Int, val acceptingLogins: Boolean, val js5Connections: Long) {
        companion object {
            val DOWN = GameStatus(up = false, players = 0, acceptingLogins = false, js5Connections = 0)
        }
    }

    /**
     * The clients the jav_config and world list are written for, each under its own [path]. RSProx's keep the names our
     * setup has always used. Our launcher's leave the game's names out of the jav_config's title and cache folder and
     * the world's activity line, as the launcher plan's naming rule asks: Odin's Gate, and odinsgate where a name must
     * do without the apostrophe and the space.
     */
    enum class ClientKind(val path: String, val title: String, val cacheDir: String, val worldActivity: String) {
        RSPROX("", "OSRSPS", "osrsps", "OSRSPS test world"),
        LAUNCHER("/launcher", "Odin's Gate", "odinsgate", "Odin's Gate"),
    }

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

        /** A full commit hash, as deploy/auto-deploy.sh writes them. */
        private val COMMIT = Regex("[0-9a-f]{40}")

        /** Requests other than a report are bodiless GETs; a body bigger than this on one is refused. */
        private const val MAX_REQUEST_BYTES = 8192

        const val REPORT_PATH = "/report"

        // Guessed: far above what a page load or a friend's launcher needs, low enough that a flood can't hold much.
        /** HTTP connections open at once, from everyone. */
        const val MAX_CONNECTIONS = 256
        /** A connection that sends nothing for this long is closed. */
        const val READ_TIMEOUT_SECONDS = 15
        /** Report bodies read at once, from everyone and from one address: at most 8 x 256 KB held. */
        const val MAX_REPORTS_READING = 8
        const val MAX_REPORTS_READING_PER_ADDRESS = 2
        /** A report's body must arrive whole within this long, however slowly it comes. */
        const val REPORT_DEADLINE_SECONDS = 30
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(READ_TIMEOUT_SECONDS.toLong())
        private val REPORT_DEADLINE: Duration = Duration.ofSeconds(REPORT_DEADLINE_SECONDS.toLong())
        /** How often [sweep] looks for connections to close. */
        private const val SWEEP_SECONDS = 1L

        /** Who sent a request: the PROXY header's source for one through the relay, else the connection's own address. */
        private fun addressOf(channel: Channel): String =
            channel.haproxyAttribute?.sourceHost
                ?: (channel.remoteAddress() as? InetSocketAddress)?.address?.hostAddress
                ?: channel.remoteAddress().toString()

        /** Members world. */
        const val WORLD_PROPERTIES = 1

        /** The world's location in both world lists: 0, the one RSProx gives the worlds it makes up itself. */
        const val WORLD_LOCATION = 0

        private const val JSON = "application/json; charset=UTF-8"

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
    }
}
