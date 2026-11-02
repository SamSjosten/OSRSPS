package osrsps.net

import com.displee.cache.CacheLibrary
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.GameServer
import osrsps.Revision
import osrsps.cache.model.ModelCodec
import osrsps.cache.model.OsrsModel
import osrsps.cache.InterfaceCodec
import osrsps.cache.LocTypeDecoder
import osrsps.cache.MapElementCodec
import osrsps.cache.Sprite
import osrsps.cache.SpriteCodec
import osrsps.cache.VarTypeLoader
import osrsps.cache.overlay.Js5Container
import osrsps.cache.overlay.Js5Group
import osrsps.cache.overlay.ReferenceTable
import osrsps.game.content.CustomLocs
import osrsps.game.content.CustomVars
import osrsps.game.VarbitType
import osrsps.game.content.LodestoneMap
import osrsps.game.content.Lodestones
import osrsps.testclient.Js5Crcs

/** Boots the real server on a tiny generated cache and talks JS5 to it over a socket. */
class GameServerTest {
    /** The highest varp and varbit file ids in the tiny cache: OpenRS2 build 2746's (VM 214's cache). */
    private val LAST_VARP = 5995
    private val LAST_VARBIT = 20410

    private fun writeTinyCache(dataDir: Path) {
        val cacheDir = Files.createDirectories(dataDir.resolve("cache"))
        cacheDir.resolve("main_file_cache.dat2").createFile()
        cacheDir.resolve("main_file_cache.idx255").createFile()
        val library = CacheLibrary.create(cacheDir.toString())
        repeat(11) { library.createIndex(named = true) }
        // 256 eight-bit codewords: a valid (if useless) Huffman table.
        library.put(10, "huffman", ByteArray(256) { 8 })
        library.put(0, 1, byteArrayOf(1, 2, 3))
        library.put(7, 3, byteArrayOf(4, 5, 6))
        // One loc type, one varbit and one varp, so the server has the config groups to add its own to. The var ids
        // are the highest in OpenRS2's build 2746 of revision 241 (VM 214's cache), past the ids another 241 build
        // ends at: ours have to follow whichever cache is loaded.
        library.put(2, 6, 0, byteArrayOf(0))
        library.put(2, 35, 0, byteArrayOf(0))
        library.put(2, 14, LAST_VARBIT, byteArrayOf(1, 0, 0, 0, 0, 0))
        library.put(2, 16, LAST_VARP, byteArrayOf(0))
        // One interface and one sprite, so the server has the indices to add its own interface and sprites to.
        library.put(3, 0, byteArrayOf(0))
        library.put(8, 0, byteArrayOf(0))
        library.update()
        library.close()
    }

    @Test
    fun `releases the game port when the HTTP port cannot be bound`(@TempDir dataDir: Path) {
        writeTinyCache(dataDir)
        val port = ServerSocket(0).use { it.localPort }
        ServerSocket(0).use { taken ->
            val server = GameServer(dataDir, port, taken.localPort, "127.0.0.1")
            assertFailsWith<IOException> { server.start() }
        }
        assertFailsWith<IOException>("game port should be closed again") { Socket("127.0.0.1", port).close() }
    }

    @Test
    fun `won't start on another revision's cache`(@TempDir dataDir: Path) {
        writeTinyCache(dataDir)
        Files.writeString(dataDir.resolve("cache-source.txt"), "openrs2 runescape/2600 build ${Revision.MAJOR - 1}.2 2026-08-05\n")
        val port = ServerSocket(0).use { it.localPort }
        val server = GameServer(dataDir, port, ServerSocket(0).use { it.localPort }, "127.0.0.1")
        val refused = assertFailsWith<IllegalStateException> { server.start() }
        assertContains(refused.message!!, "revision ${Revision.MAJOR - 1}'s")
        assertFailsWith<IOException>("game port should not be open") { Socket("127.0.0.1", port).close() }
    }

    @Test
    fun `serves the master index over JS5 and writes the RSA modulus`(@TempDir dataDir: Path) {
        writeTinyCache(dataDir)
        // Our revision's cache, so the revision check lets it start.
        Files.writeString(dataDir.resolve("cache-source.txt"), "openrs2 runescape/2746 build ${Revision.MAJOR} 2026-10-20\n")

        val port = ServerSocket(0).use { it.localPort }
        val httpPort = ServerSocket(0).use { it.localPort }
        val server = GameServer(dataDir, port, httpPort, "127.0.0.1")
        server.start()
        try {
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 5000
                val out = DataOutputStream(socket.getOutputStream())
                val input = DataInputStream(socket.getInputStream())
                out.writeByte(15) // INIT_JS5REMOTE_CONNECTION
                out.writeInt(Revision.MAJOR)
                repeat(4) { out.writeInt(0) }
                out.flush()
                assertEquals(0, input.readUnsignedByte(), "JS5 handshake response")

                out.writeByte(1) // urgent request
                out.writeByte(255)
                out.writeShort(255)
                out.flush()
                assertEquals(255, input.readUnsignedByte())
                assertEquals(255, input.readUnsignedShort())
                assertEquals(0, input.readUnsignedByte(), "uncompressed container")
                assertEquals(11 * 8, input.readInt(), "crc and version for each of the 11 archives")
            }
            assertEquals(256, Files.readString(dataDir.resolve("rsa-modulus.txt")).trim().length)
            val javConfig = URI("http://127.0.0.1:$httpPort/jav_config.ws").toURL().readText()
            assertTrue(javConfig.lines().contains("codebase=http://127.0.0.1/"), javConfig)
            // Asked by another name for this machine, the files follow the name used rather than the configured host.
            val byName = URI("http://localhost:$httpPort/jav_config.ws").toURL().readText()
            assertTrue(byName.lines().contains("codebase=http://localhost/"), byName)
            val modulus = Files.readString(dataDir.resolve("rsa-modulus.txt")).trim()
            val targets = URI("http://127.0.0.1:$httpPort/proxy-targets.yaml").toURL().readText().lines()
            assertTrue("    modulus: $modulus" in targets, targets.joinToString("\n"))
            assertTrue("    game_server_port: $port" in targets, targets.joinToString("\n"))
            // The world is running with nobody in it, and the one JS5 connection above was let in.
            assertEquals(
                """{"up":true,"revision":${Revision.MAJOR},"commit":"unknown","players":0,"acceptingLogins":true,"js5Connections":1}""",
                URI("http://127.0.0.1:$httpPort/status.json").toURL().readText(),
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun `serves a model from data models over JS5 as part of the cache`(@TempDir dataDir: Path) {
        writeTinyCache(dataDir)
        Files.writeString(dataDir.resolve("cache-source.txt"), "openrs2 runescape/2746 build ${Revision.MAJOR} 2026-10-20\n")
        val model = ModelCodec.encode(
            OsrsModel(
                vertexX = intArrayOf(0, 100, 0), vertexY = intArrayOf(0, 0, -50), vertexZ = intArrayOf(0, 0, 100),
                faceA = intArrayOf(0), faceB = intArrayOf(1), faceC = intArrayOf(2), faceColors = intArrayOf(0x7F),
            ),
        )
        Files.write(Files.createDirectories(dataDir.resolve("models")).resolve("65000.dat"), model)
        // A sprite file under the lodestone map's image's id takes the place of the one drawn in code.
        val mapImage = SpriteCodec.encode(Sprite(2, 2, IntArray(4) { 0xFF123456.toInt() }))
        Files.write(Files.createDirectories(dataDir.resolve("sprites")).resolve("${LodestoneMap.MAP_SPRITE}.dat"), mapImage)

        val port = ServerSocket(0).use { it.localPort }
        val server = GameServer(dataDir, port, ServerSocket(0).use { it.localPort }, "127.0.0.1")
        server.start()
        try {
            // What the client does: the master index says index 7 changed, so it fetches the table, which lists the
            // new group with the CRC of the container it then receives.
            val crcs = Js5Crcs.fetch("127.0.0.1", port)
            val tableContainer = Js5Crcs.fetchGroup("127.0.0.1", port, 255, 7)
            assertEquals(crcs[7], Js5Container.crc(tableContainer))
            val table = ReferenceTable.decode(Js5Container.unpack(tableContainer))
            assertEquals(listOf(3, 65000), table.groups.map { it.id })
            val group = Js5Crcs.fetchGroup("127.0.0.1", port, 7, 65000)
            assertEquals(table.group(65000)!!.crc, Js5Container.crc(group))
            assertContentEquals(model, Js5Container.unpack(group))

            // Our own loc types ride in the cache's loc types group the same way.
            val configs = ReferenceTable.decode(Js5Container.unpack(Js5Crcs.fetchGroup("127.0.0.1", port, 255, 2)))
            assertEquals(crcs[2], Js5Container.crc(Js5Crcs.fetchGroup("127.0.0.1", port, 255, 2)))
            // The ids the server derives from this cache: its varps run to LAST_VARP, its varbits to LAST_VARBIT.
            val customVars = CustomVars(LAST_VARP + 1, LAST_VARBIT + 1)
            val locTypes = configs.group(6)!!
            assertEquals(listOf(0) + CustomLocs(customVars).all.map { it.id }, locTypes.files.map { it.id })
            val locGroup = Js5Crcs.fetchGroup("127.0.0.1", port, 2, 6)
            assertEquals(locTypes.crc, Js5Container.crc(locGroup))
            val files = Js5Group.unpack(Js5Container.unpack(locGroup), locTypes.files.map { it.id })
            val lodestone = LocTypeDecoder.decode(Lodestones.DORMANT, files.getValue(Lodestones.DORMANT))
            assertEquals("Lodestone", lodestone.name)
            assertEquals("Activate", lodestone.ops[0])
            val lumbridgeVarbit = customVars.lodestoneVarbit(Lodestones.LUMBRIDGE)
            val lumbridgeStone = LocTypeDecoder.decode(Lodestones.LUMBRIDGE.id, files.getValue(Lodestones.LUMBRIDGE.id))
            assertTrue(lumbridgeStone.multiloc, "its look follows the varbit after this cache's")
            assertEquals(lumbridgeVarbit, Lodestones.LUMBRIDGE.type(lumbridgeVarbit).definition.transform?.varbit)
            assertContentEquals(Lodestones.LUMBRIDGE.type(lumbridgeVarbit).encoded, files.getValue(Lodestones.LUMBRIDGE.id))
            // The map element both looks name for the minimap's icon, in the cache's map elements group the same way.
            val elements = configs.group(MapElementCodec.GROUP)!!
            assertEquals(listOf(0, Lodestones.MAP_ELEMENT), elements.files.map { it.id })
            val elementFiles = Js5Group.unpack(Js5Container.unpack(Js5Crcs.fetchGroup("127.0.0.1", port, 2, MapElementCodec.GROUP)), elements.files.map { it.id })
            assertEquals(Lodestones.MAP_ELEMENT_TYPE, MapElementCodec.decode(elementFiles.getValue(Lodestones.MAP_ELEMENT)))
            // And our var types in the varbit and varp groups, right after the cache's own, so the client sizes its
            // varps to hold ours and reads our varbits.
            assertEquals(listOf(LAST_VARP) + customVars.varps, configs.group(16)!!.files.map { it.id })
            val varbits = configs.group(14)!!
            assertEquals(listOf(LAST_VARBIT) + customVars.varbits.keys.sorted(), varbits.files.map { it.id })
            val varbitFiles = Js5Group.unpack(Js5Container.unpack(Js5Crcs.fetchGroup("127.0.0.1", port, 2, 14)), varbits.files.map { it.id })
            assertEquals(LAST_VARBIT + 1, lumbridgeVarbit)
            assertEquals(VarbitType(LAST_VARP + 1, 0, 0), VarTypeLoader.decodeVarbit(varbitFiles.getValue(lumbridgeVarbit)))

            // The lodestone map: our interface as a group of the interface index past the cache's own, its components
            // the files, and our sprites as groups of the sprite index, the map's image from the file in data/sprites.
            val interfaces = ReferenceTable.decode(Js5Container.unpack(Js5Crcs.fetchGroup("127.0.0.1", port, 255, InterfaceCodec.INDEX)))
            assertEquals(listOf(0, LodestoneMap.INTERFACE), interfaces.groups.map { it.id })
            val mapEntry = interfaces.group(LodestoneMap.INTERFACE)!!
            assertEquals(LodestoneMap.components.indices.toList(), mapEntry.files.map { it.id })
            val mapGroup = Js5Crcs.fetchGroup("127.0.0.1", port, InterfaceCodec.INDEX, LodestoneMap.INTERFACE)
            assertEquals(mapEntry.crc, Js5Container.crc(mapGroup))
            assertEquals(LodestoneMap.components, InterfaceCodec.decodeGroup(Js5Container.unpack(mapGroup), mapEntry.files.size))
            val sprites = ReferenceTable.decode(Js5Container.unpack(Js5Crcs.fetchGroup("127.0.0.1", port, 255, SpriteCodec.INDEX)))
            assertEquals(listOf(0) + LodestoneMap.sprites.keys.sorted(), sprites.groups.map { it.id })
            val icon = SpriteCodec.decode(Js5Container.unpack(Js5Crcs.fetchGroup("127.0.0.1", port, SpriteCodec.INDEX, LodestoneMap.ACTIVE_SPRITE))).single()
            assertContentEquals(LodestoneMap.sprites.getValue(LodestoneMap.ACTIVE_SPRITE).pixels, icon.pixels)
            val served = Js5Crcs.fetchGroup("127.0.0.1", port, SpriteCodec.INDEX, LodestoneMap.MAP_SPRITE)
            assertEquals(sprites.group(LodestoneMap.MAP_SPRITE)!!.crc, Js5Container.crc(served))
            assertContentEquals(mapImage, Js5Container.unpack(served), "the file's image, not the code's")
        } finally {
            server.stop()
        }
    }
}
