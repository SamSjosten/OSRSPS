package osrsps.net

import osrsps.Revision
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ClientConfigTest {
    private val config = ClientConfig("192.168.0.210", 43595)

    @Test
    fun `jav config points the launcher at the public host`() {
        val lines = config.javConfig().lines()
        assertContains(lines, "codebase=http://192.168.0.210/")
        assertContains(lines, "param=17=http://192.168.0.210:43595/world_list.ws")
        assertContains(lines, "param=25=241")
        assertContains(lines, "param=12=${ClientConfig.WORLD_ID}")
    }

    @Test
    fun `world list decodes back to one world on the public host`() {
        val input = DataInputStream(ByteArrayInputStream(config.worldList()))
        val payloadSize = input.readInt()
        assertEquals(config.worldList().size - 4, payloadSize)
        assertEquals(1, input.readUnsignedShort())
        assertEquals(ClientConfig.WORLD_ID, input.readUnsignedShort())
        assertEquals(ClientConfig.WORLD_PROPERTIES, input.readInt())
        assertEquals("192.168.0.210", input.readJagexString())
        assertEquals("OSRSPS test world", input.readJagexString())
        assertEquals(0, input.readUnsignedByte())
        assertEquals(1, input.readUnsignedShort())
        assertEquals(0, input.available())
    }

    @Test
    fun `the RSProx target points at the jav config with the login modulus`() {
        assertEquals(null, config.proxyTargets())
        config.modulusHex = "abc123"
        assertEquals(
            """
            config:
              - name: OSRSPS
                jav_config_url: http://192.168.0.210:43595/jav_config.ws
                revision: 241.1
                modulus: abc123

            """.trimIndent(),
            config.proxyTargets(),
        )
    }

    @Test
    fun `the RSProx target pins the RuneLite build for the server's revision`() {
        val pin = RuneLitePin(Revision.MAJOR, "1.13.4", "1e634e64717418713898b06061a189ec1cdf948d", "25f22ff874ddf8c4016c8d03150c24920cdaa536ae7ad44aed36173c826c0b2a")
        val pinned = ClientConfig("192.168.0.210", 43595, runelitePins = RuneLitePins(mapOf(pin.revision to pin))).apply { modulusHex = "ff" }
        assertContains(pinned.proxyTargets()!!.lines(), "    runelite_bootstrap_commithash: 1e634e64717418713898b06061a189ec1cdf948d")

        // A build for another revision isn't one RSProx should launch for this server.
        val other = pin.copy(revision = Revision.MAJOR - 1)
        val unpinned = ClientConfig("192.168.0.210", 43595, runelitePins = RuneLitePins(mapOf(other.revision to other))).apply { modulusHex = "ff" }
        assertFalse(unpinned.proxyTargets()!!.contains("runelite_bootstrap_commithash"))
    }

    @Test
    fun `the RSProx target names the game port when it isn't 43594`() {
        val other = ClientConfig("10.0.0.5", 50001, gamePort = 50000).apply { modulusHex = "ff" }
        assertContains(other.proxyTargets()!!.lines(), "    game_server_port: 50000")
    }

    @Test
    fun `version answers with the commit the server was built from`() {
        val http = ClientConfig("127.0.0.1", 0, gamePort = 43594, version = "3c1b194").also { it.start() }
        try {
            val port = http.boundPort
            val body = java.net.URI("http://127.0.0.1:$port/version").toURL().readText()
            assertEquals("3c1b194\n", body)
        } finally {
            http.stop()
        }
    }

    @Test
    fun `status answers with the commit and what the server started from`() {
        val http = ClientConfig("127.0.0.1", 0, gamePort = 43594, version = "3c1b194").also { it.start() }
        try {
            val url = java.net.URI("http://127.0.0.1:${http.boundPort}/status").toURL()
            assertEquals("commit 3c1b194\n", url.readText())
            http.status = listOf("cache openrs2 runescape/2740 build 241.3 2026-10-14", "Game frame: ids match the cache's names")
            assertEquals(
                "commit 3c1b194\ncache openrs2 runescape/2740 build 241.3 2026-10-14\nGame frame: ids match the cache's names\n",
                url.readText(),
            )
            var js5 = 0
            http.liveStatus = { listOf("js5 connections $js5") }
            assertEquals("js5 connections 0", url.readText().lines().dropLast(1).last())
            js5 = 3
            assertEquals("js5 connections 3", url.readText().lines().dropLast(1).last())
        } finally {
            http.stop()
        }
    }

    @Test
    fun `each file points at the host the client asked by`() {
        config.modulusHex = "ff"
        val lines = config.javConfig("osrsps.example.net").lines()
        assertContains(lines, "codebase=http://osrsps.example.net/")
        assertContains(lines, "param=17=http://osrsps.example.net:43595/world_list.ws")
        assertContains(config.proxyTargets("203.0.113.7")!!.lines(), "    jav_config_url: http://203.0.113.7:43595/jav_config.ws")
        val input = DataInputStream(ByteArrayInputStream(config.worldList("203.0.113.7")))
        input.readInt()
        input.readUnsignedShort()
        input.readUnsignedShort()
        input.readInt()
        assertEquals("203.0.113.7", input.readJagexString())
    }

    @Test
    fun `a Host header is used only when it is a plain host`() {
        assertEquals("192.168.0.210", ClientConfig.hostOf("192.168.0.210:43595"))
        assertEquals("osrsps.example.net", ClientConfig.hostOf(" osrsps.example.net "))
        assertEquals(null, ClientConfig.hostOf(null))
        assertEquals(null, ClientConfig.hostOf(""))
        assertEquals(null, ClientConfig.hostOf("evil\ncodebase=http://x/"))
        assertEquals(null, ClientConfig.hostOf("a b"))
        assertEquals(null, ClientConfig.hostOf("[::1]:43595"))
    }

    private fun DataInputStream.readJagexString(): String {
        val bytes = generateSequence { readUnsignedByte().takeIf { it != 0 } }.map { it.toByte() }.toList().toByteArray()
        return String(bytes, Charsets.ISO_8859_1)
    }
}
