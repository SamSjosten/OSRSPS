package osrsps.net

import io.netty.buffer.Unpooled
import java.net.Socket
import osrsps.net.ProxyProtocol.Mode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The PROXY header rule's setting and signature check, and the client files' port behind a relay's header. */
class ProxyProtocolTest {
    @Test
    fun `the startup line says the mode and the relays`() {
        assertEquals("proxy protocol: optional from relays [192.168.0.216]", ProxyProtocol.parse(null, setOf("192.168.0.216")).toString())
        assertEquals("proxy protocol: required from relays [192.168.0.216]", ProxyProtocol.parse("required", setOf("192.168.0.216")).toString())
        assertEquals("proxy protocol: off", ProxyProtocol.parse("off", setOf("192.168.0.216")).toString())
        // No relays, nobody to accept a header from.
        assertEquals("proxy protocol: off", ProxyProtocol.parse("required", emptySet()).toString())
        assertFailsWith<IllegalStateException> { ProxyProtocol.parse("on", setOf("192.168.0.216")) }
        assertFailsWith<IllegalArgumentException> { ProxyProtocol.parse(null, setOf("relay.example.net")) }
    }

    @Test
    fun `a header is told apart from a game client's or a browser's first bytes`() {
        fun check(bytes: ByteArray) = ProxyProtocol.startsWithHeader(Unpooled.wrappedBuffer(bytes))
        val v2 = ProxyHeaders.v2("203.0.113.7", 1)
        assertEquals(true, check(v2))
        assertEquals(true, check(ProxyHeaders.v1("203.0.113.7", 1)))
        assertEquals(null, check(v2.copyOf(11)), "11 bytes of the signature could still be one")
        assertEquals(null, check("PROX".toByteArray()))
        assertEquals(false, check(byteArrayOf(15)), "JS5 handshake")
        assertEquals(false, check(byteArrayOf(14)), "game login")
        assertEquals(false, check("GET / HTTP/1.1\r\n".toByteArray()))
        assertEquals(false, check("POST / HTTP/1.1\r\n".toByteArray()))
        assertEquals(false, check(byteArrayOf(0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0B)))
    }

    @Test
    fun `the client files come behind a relay's header, and without one`() {
        serve(ProxyProtocol(setOf("127.0.0.1"), Mode.OPTIONAL)) { port ->
            val proxied = get(port, ProxyHeaders.v2("203.0.113.7", 40000, destinationPort = 43595))
            assertTrue(proxied.startsWith("HTTP/1.1 200"), proxied)
            assertContains(proxied, "codebase=http://odinsgate.net/")
            assertContains(get(port, ProxyHeaders.v1("203.0.113.7", 40000)), "codebase=http://odinsgate.net/")
            assertContains(get(port), "codebase=http://odinsgate.net/")
        }
    }

    @Test
    fun `the client files drop a header from anyone but a relay`() {
        serve(ProxyProtocol(setOf("192.0.2.1"), Mode.OPTIONAL)) { port ->
            assertEquals("", get(port, ProxyHeaders.v2("203.0.113.7", 40000)))
            assertContains(get(port), "codebase=http://odinsgate.net/")
        }
    }

    @Test
    fun `in required mode the client files drop a relay's request without a header`() {
        serve(ProxyProtocol(setOf("127.0.0.1"), Mode.REQUIRED)) { port ->
            assertEquals("", get(port))
            assertContains(get(port, ProxyHeaders.v2("203.0.113.7", 40000)), "codebase=http://odinsgate.net/")
        }
    }

    @Test
    fun `unknown paths are 404 and a query string is ignored`() {
        serve(ProxyProtocol.OFF) { port ->
            assertTrue(get(port, path = "/nothing").startsWith("HTTP/1.1 404"))
            assertContains(get(port, path = "/jav_config.ws?binaryType=2"), "codebase=http://odinsgate.net/")
        }
    }

    private fun serve(proxy: ProxyProtocol, block: (Int) -> Unit) {
        val http = ClientConfig("192.168.0.210", 0, gamePort = 43594, proxy = proxy).also { it.start() }
        try {
            block(http.boundPort)
        } finally {
            http.stop()
        }
    }

    /** A GET as a launcher sends it, after [header]; the whole response, or "" if the server closed without one. */
    private fun get(port: Int, header: ByteArray = ByteArray(0), path: String = "/jav_config.ws"): String =
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5000
            socket.getOutputStream().apply {
                write(header)
                write("GET $path HTTP/1.1\r\nHost: odinsgate.net:43595\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
            try {
                String(socket.getInputStream().readBytes(), Charsets.ISO_8859_1)
            } catch (e: java.net.SocketException) {
                ""
            }
        }
}
