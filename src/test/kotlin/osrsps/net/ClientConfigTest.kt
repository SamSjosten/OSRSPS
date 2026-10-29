package osrsps.net

import osrsps.Revision
import osrsps.net.ClientConfig.ClientKind
import osrsps.net.ClientConfig.GameStatus
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.io.TempDir

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
            assertEquals("commit 3c1b194\njs5 connections 0\n", url.readText())
            http.status = listOf("cache openrs2 runescape/2740 build 241.3 2026-10-14", "Game frame: ids match the cache's names")
            assertEquals(
                "commit 3c1b194\ncache openrs2 runescape/2740 build 241.3 2026-10-14\nGame frame: ids match the cache's names\n" +
                    "js5 connections 0\n",
                url.readText(),
            )
            var js5 = 1L
            http.gameStatus = { GameStatus(up = true, players = 0, acceptingLogins = true, js5Connections = js5) }
            assertEquals("js5 connections 1", url.readText().lines().dropLast(1).last())
            js5 = 3
            assertEquals("js5 connections 3", url.readText().lines().dropLast(1).last())
        } finally {
            http.stop()
        }
    }

    @Test
    fun `after merges that leave the server running, version and status answer with the newest commit`(@TempDir state: Path) {
        val built = "1".repeat(40)
        val newer = "2".repeat(40)
        val http = ClientConfig("127.0.0.1", 0, gamePort = 43594, version = built, deployState = state).also { it.start() }
        try {
            fun version() = get(http.boundPort, "/version").body
            fun status() = get(http.boundPort, "/status").body
            // Nothing written there yet: the commit the server was built from.
            assertEquals("$built\n", version())
            Files.writeString(state.resolve("built"), "$built\n")
            Files.writeString(state.resolve("deployed"), "$newer\n")
            assertEquals("$newer\n", version())
            assertEquals("commit $newer\nbuilt from $built\njs5 connections 0\n", status())
            assertContains(get(http.boundPort, "/status.json").body, "\"commit\":\"$newer\"")

            // A folder that names another build isn't about this server (one started by hand, say).
            Files.writeString(state.resolve("built"), "3".repeat(40) + "\n")
            assertEquals("$built\n", version())
            assertEquals("commit $built\njs5 connections 0\n", status())

            // An empty built: a restart under way, or one whose server didn't start, so which build runs isn't known.
            Files.writeString(state.resolve("built"), "\n")
            assertEquals("$built\n", version())
            assertEquals("commit $built\njs5 connections 0\n", status())

            // Nor is a deployed file without a commit in it.
            Files.writeString(state.resolve("built"), "$built\n")
            Files.writeString(state.resolve("deployed"), "\n")
            assertEquals("$built\n", version())
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
    fun `status json says whether the game is up, its revision and commit, and how busy it is`() {
        val http = ClientConfig("127.0.0.1", 0, gamePort = 43594, version = "3c1b194").also { it.start() }
        try {
            val down = get(http.boundPort, "/status.json")
            assertEquals(200, down.code)
            assertEquals("application/json; charset=UTF-8", down.contentType)
            assertEquals(
                """{"up":false,"revision":${Revision.MAJOR},"commit":"3c1b194","players":0,"acceptingLogins":false,"js5Connections":0}""",
                down.body,
            )
            http.gameStatus = { GameStatus(up = true, players = 3, acceptingLogins = true, js5Connections = 12) }
            assertEquals(
                """{"up":true,"revision":${Revision.MAJOR},"commit":"3c1b194","players":3,"acceptingLogins":true,"js5Connections":12}""",
                get(http.boundPort, "/status.json").body,
            )
        } finally {
            http.stop()
        }
    }

    @Test
    fun `RuneLite's world list has our one world at the host the client asked by`() {
        val http = ClientConfig("192.168.0.210", 0, gamePort = 43594).also { it.start() }
        try {
            http.gameStatus = { GameStatus(up = true, players = 2, acceptingLogins = true, js5Connections = 5) }
            val worlds = get(http.boundPort, "/rl/worlds.js", host = "odinsgate.net:43595")
            assertEquals(200, worlds.code)
            assertEquals("application/json; charset=UTF-8", worlds.contentType)
            // RuneLite's World: id, types, address, activity, location, players.
            assertEquals(
                """{"worlds":[{"id":301,"types":["MEMBERS"],"address":"odinsgate.net","activity":"Odin's Gate","location":0,"players":2}]}""",
                worlds.body,
            )
            assertContains(get(http.boundPort, "/rl/worlds.js", host = "203.0.113.7").body, """"address":"203.0.113.7"""")
            // With no usable Host header, the public host.
            assertContains(get(http.boundPort, "/rl/worlds.js", host = null).body, """"address":"192.168.0.210"""")
            assertContains(get(http.boundPort, "/rl/worlds.js", host = "a b").body, """"address":"192.168.0.210"""")
        } finally {
            http.stop()
        }
    }

    @Test
    fun `launcher clients get a jav config with neutral names, and RSProx keeps its own`() {
        val launcher = config.javConfig("odinsgate.net", ClientKind.LAUNCHER).lines()
        assertContains(launcher, "title=Odin's Gate")
        assertContains(launcher, "cachedir=odinsgate")
        assertContains(launcher, "codebase=http://odinsgate.net/")
        assertContains(launcher, "param=17=http://odinsgate.net:43595/launcher/world_list.ws")
        val rsprox = config.javConfig("odinsgate.net").lines()
        assertEquals(rsprox.size, launcher.size)
        assertEquals(
            listOf("title=OSRSPS", "cachedir=osrsps", "param=17=http://odinsgate.net:43595/world_list.ws"),
            rsprox.filterIndexed { i, line -> line != launcher[i] },
            "only the names and the world list's address differ",
        )
        val input = DataInputStream(ByteArrayInputStream(config.worldList("odinsgate.net", ClientKind.LAUNCHER)))
        input.readInt()
        input.readUnsignedShort()
        input.readUnsignedShort()
        input.readInt()
        assertEquals("odinsgate.net", input.readJagexString())
        assertEquals("Odin's Gate", input.readJagexString())
    }

    @Test
    fun `no name in the launcher's files is the game's`() {
        // The launcher plan's naming rule: none of RuneScape, OSRS, Jagex, -scape or RSPS.
        val banned = Regex("runescape|osrs|jagex|scape|rsps", RegexOption.IGNORE_CASE)
        for (name in listOf(ClientKind.LAUNCHER.title, ClientKind.LAUNCHER.cacheDir, ClientKind.LAUNCHER.worldActivity)) {
            assertFalse(banned.containsMatchIn(name), name)
        }
    }

    @Test
    fun `the launcher's files are served under launcher, next to RSProx's`() {
        // A port of its own rather than 0, as the files name the port they are served on.
        val port = ServerSocket(0).use { it.localPort }
        val http = ClientConfig("192.168.0.210", port, gamePort = 43594).also { it.start() }
        try {
            val javConfig = get(port, "/launcher/jav_config.ws", host = "odinsgate.net:$port")
            assertEquals(200, javConfig.code)
            assertEquals("text/plain; charset=ISO-8859-1", javConfig.contentType)
            assertContains(javConfig.body.lines(), "title=Odin's Gate")
            assertContains(javConfig.body.lines(), "param=17=http://odinsgate.net:$port/launcher/world_list.ws")
            val worldList = get(port, "/launcher/world_list.ws", host = "odinsgate.net:$port")
            assertEquals(200, worldList.code)
            assertEquals("application/octet-stream", worldList.contentType)
            assertEquals(
                http.worldList("odinsgate.net", ClientKind.LAUNCHER).toList(),
                worldList.body.toByteArray(Charsets.ISO_8859_1).toList(),
            )
            assertContains(get(port, "/jav_config.ws", host = "odinsgate.net:$port").body.lines(), "title=OSRSPS")
        } finally {
            http.stop()
        }
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

    private class Reply(val code: Int, val contentType: String?, val body: String)

    /** A GET of [path] over a plain socket, so the Host header is ours: [host], or none when it is null. */
    private fun get(port: Int, path: String, host: String? = "127.0.0.1:$port"): Reply =
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5000
            val hostLine = host?.let { "Host: $it\r\n" }.orEmpty()
            socket.getOutputStream().apply {
                write("GET $path HTTP/1.1\r\n${hostLine}Connection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
            // ISO-8859-1 keeps every byte as one char, so a binary body can be turned back into its bytes.
            val response = String(socket.getInputStream().readBytes(), Charsets.ISO_8859_1)
            val head = response.substringBefore("\r\n\r\n").split("\r\n")
            val contentType = head.drop(1).firstOrNull { it.startsWith("content-type:", ignoreCase = true) }
            Reply(head.first().split(' ')[1].toInt(), contentType?.substringAfter(':')?.trim(), response.substringAfter("\r\n\r\n"))
        }

    private fun DataInputStream.readJagexString(): String {
        val bytes = generateSequence { readUnsignedByte().takeIf { it != 0 } }.map { it.toByte() }.toList().toByteArray()
        return String(bytes, Charsets.ISO_8859_1)
    }
}
