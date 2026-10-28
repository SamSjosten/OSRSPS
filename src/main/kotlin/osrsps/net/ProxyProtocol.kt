package osrsps.net

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.haproxy.HAProxyCommand
import io.netty.handler.codec.haproxy.HAProxyMessage
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder
import org.jire.netty.haproxy.HAProxyAttribute
import org.jire.netty.haproxy.HAProxyAttributes.haproxyAttribute
import org.jire.netty.haproxy.HAProxyTrustPredicate
import java.net.InetSocketAddress
import java.net.SocketAddress

private val logger = KotlinLogging.logger {}

/**
 * Who may put a PROXY protocol header (v1 or v2) in front of a connection to say whose it really is. The VPS relay
 * forwards every internet player from its own address, and HAProxy there can start each connection with the player's
 * address and port, which passes the tunnel and NAT unchanged.
 *
 * Only [relays] may send one: a header from anyone else is dropped with the connection, or anyone could claim any
 * address. With [Mode.OPTIONAL] a relay's connection may come with or without one (one without counts as the relay's own,
 * so HAProxy can be switched over with nobody locked out); with [Mode.REQUIRED] one without is dropped. Everyone else is
 * a plain client, known by their TCP address.
 *
 * Both ports use it, through [acceptor]. The header's address goes where RSProt's PROXY support keeps it (the channel's
 * [haproxyAttribute]), so RSProt counts, limits and names the connection by it (Session.inetAddress, the address
 * validator, the login handlers). RSProt's own reading of headers stays off: in our tests its "header or not" mode
 * stalled every relay login that came without one (the login block was never read), which optional mode needs to work.
 */
class ProxyProtocol(relays: Set<String>, mode: Mode) {
    enum class Mode { OFF, OPTIONAL, REQUIRED }

    /** Relays only count while a header is accepted from them; with none, nobody may send one. */
    val mode: Mode = if (relays.isEmpty()) Mode.OFF else mode

    val relays: Set<String> = if (this.mode == Mode.OFF) emptySet() else relays

    /** Whether a TCP peer is one of [relays] (IP addresses; Main refuses anything else). */
    private val trust: HAProxyTrustPredicate =
        if (this.relays.isEmpty()) HAProxyTrustPredicate { false } else HAProxyTrustPredicate.ofCidrs(*this.relays.toTypedArray())

    fun isRelay(peer: SocketAddress?): Boolean = trust.isTrusted(peer)

    /**
     * For a listening (server) channel's pipeline: puts a [Guard] at the front of every connection it accepts, ahead of
     * everything the connection's own initializer adds.
     */
    val acceptor: ChannelHandler = Acceptor()

    /** The startup log line, e.g. `proxy protocol: optional from relays [192.168.0.216]`. */
    override fun toString(): String =
        if (mode == Mode.OFF) "proxy protocol: off" else "proxy protocol: ${mode.name.lowercase()} from relays [${relays.joinToString(", ")}]"

    @ChannelHandler.Sharable
    private inner class Acceptor : ChannelInboundHandlerAdapter() {
        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            if (msg is Channel) msg.pipeline().addFirst(GUARD_NAME, Guard(isRelay(msg.remoteAddress()), mode == Mode.REQUIRED))
            ctx.fireChannelRead(msg)
        }
    }

    /**
     * Reads just far enough into a new connection to tell whether it starts with a PROXY header, and drops it if that
     * isn't allowed from this peer. Otherwise it steps aside, passing every byte on untouched, with a header decoder and
     * [HeaderReader] in its place when there is a header. Neither a game client (whose first byte is an opcode such as
     * 14 or 15) nor an HTTP request starts like one. All of it happens within the read that brought the bytes, so the
     * handlers behind see the bytes just as they would have without us.
     */
    internal class Guard(private val relay: Boolean, private val required: Boolean) : ByteToMessageDecoder() {
        override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
            // Too few bytes to tell yet: ByteToMessageDecoder keeps them and asks for more.
            val header = startsWithHeader(buf) ?: return
            val drop = when {
                header && !relay -> "it sent a PROXY header but is not a relay"
                !header && relay && required -> "a relay's connection must start with a PROXY header"
                else -> null
            }
            if (drop != null) {
                logger.warn { "Dropping a connection from ${peer(ctx.channel())}: $drop" }
                buf.skipBytes(buf.readableBytes())
                ctx.close()
                return
            }
            if (header) {
                ctx.pipeline().addAfter(ctx.name(), DECODER_NAME, HAProxyMessageDecoder())
                ctx.pipeline().addAfter(DECODER_NAME, READER_NAME, HeaderReader())
            }
            // Removing a decoder hands what it has read on to the next handler.
            ctx.pipeline().remove(this)
        }
    }

    /**
     * Takes the decoded header and keeps its source as the connection's address. The decoder removes itself after the
     * header and hands the bytes after it on, so this runs before anything behind it sees them. A LOCAL header (a
     * health check from the relay itself) leaves the connection known by its own address.
     *
     * HAProxy sends the header as soon as it connects, usually ahead of the player's first bytes, so the read that
     * brought it may bring nothing for the handlers behind. With auto-read off (RSProt's game port) they would then
     * never ask for more, so this asks for the next read itself.
     */
    internal class HeaderReader : ChannelInboundHandlerAdapter() {
        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            if (msg !is HAProxyMessage) {
                ctx.fireChannelRead(msg)
                return
            }
            try {
                val source = msg.sourceAddress()
                if (msg.command() == HAProxyCommand.PROXY && source != null) {
                    ctx.channel().haproxyAttribute = HAProxyAttribute(
                        msg.protocolVersion(), msg.proxiedProtocol(), source, msg.sourcePort(),
                        msg.destinationAddress().orEmpty(), msg.destinationPort(),
                    )
                }
            } finally {
                msg.release()
            }
            ctx.pipeline().remove(this)
            if (!ctx.channel().config().isAutoRead) ctx.read()
        }
    }

    companion object {
        const val GUARD_NAME = "proxy-guard"
        private const val DECODER_NAME = "proxy-header-decoder"
        private const val READER_NAME = "proxy-header-reader"

        val OFF = ProxyProtocol(emptySet(), Mode.OFF)

        /** v2's fixed 12-byte signature. */
        private val V2_SIGNATURE = byteArrayOf(0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A)

        private val V1_SIGNATURE = "PROXY ".toByteArray(Charsets.US_ASCII)

        /**
         * Whether [buf] starts with a PROXY header's signature: null while the bytes so far could still be one, so at
         * most 12 bytes are needed. Doesn't move the reader index.
         */
        fun startsWithHeader(buf: ByteBuf): Boolean? {
            var undecided = false
            for (signature in arrayOf(V2_SIGNATURE, V1_SIGNATURE)) {
                when (matches(buf, signature)) {
                    true -> return true
                    null -> undecided = true
                    false -> {}
                }
            }
            return if (undecided) null else false
        }

        private fun matches(buf: ByteBuf, signature: ByteArray): Boolean? {
            val start = buf.readerIndex()
            val available = minOf(buf.readableBytes(), signature.size)
            for (i in 0 until available) if (buf.getByte(start + i) != signature[i]) return false
            return if (available == signature.size) true else null
        }

        /**
         * Reads the setting: OSRSPS_PROXY_PROTOCOL is `off`, `optional` or `required` (unset: optional), and
         * [relays] are the IP addresses (or CIDR ranges) allowed to send a header.
         */
        fun parse(setting: String?, relays: Set<String>): ProxyProtocol {
            val mode = when (setting?.trim()?.lowercase().orEmpty()) {
                "", "optional" -> Mode.OPTIONAL
                "required" -> Mode.REQUIRED
                "off" -> Mode.OFF
                else -> error("OSRSPS_PROXY_PROTOCOL must be off, optional or required, was '$setting'")
            }
            return ProxyProtocol(relays, mode)
        }

        private fun peer(channel: Channel): String =
            (channel.remoteAddress() as? InetSocketAddress)?.let { "${it.hostString}:${it.port}" } ?: channel.remoteAddress().toString()
    }
}

/** One handler for a listening channel's pipeline that adds [handlers] in order; ServerBootstrap takes only one. */
internal fun serverHandlers(vararg handlers: ChannelHandler): ChannelHandler =
    object : ChannelInitializer<Channel>() {
        override fun initChannel(ch: Channel) {
            ch.pipeline().addLast(*handlers)
        }
    }
