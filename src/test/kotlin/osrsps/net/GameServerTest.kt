package osrsps.net

import com.displee.cache.CacheLibrary
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.GameServer
import osrsps.Revision

/** Boots the real server on a tiny generated cache and talks JS5 to it over a socket. */
class GameServerTest {
    @Test
    fun `serves the master index over JS5 and writes the RSA modulus`(@TempDir dataDir: Path) {
        val cacheDir = Files.createDirectories(dataDir.resolve("cache"))
        cacheDir.resolve("main_file_cache.dat2").createFile()
        cacheDir.resolve("main_file_cache.idx255").createFile()
        val library = CacheLibrary.create(cacheDir.toString())
        repeat(11) { library.createIndex(named = true) }
        // 256 eight-bit codewords: a valid (if useless) Huffman table.
        library.put(10, "huffman", ByteArray(256) { 8 })
        library.put(0, 1, byteArrayOf(1, 2, 3))
        library.update()
        library.close()

        val port = ServerSocket(0).use { it.localPort }
        val server = GameServer(dataDir, port)
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
        } finally {
            server.stop()
        }
    }
}
