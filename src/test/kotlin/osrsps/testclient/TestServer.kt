package osrsps.testclient

import com.displee.cache.CacheLibrary
import io.netty.buffer.Unpooled
import java.math.BigInteger
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createFile
import net.rsprot.compression.HuffmanCodec
import net.rsprot.compression.provider.DefaultHuffmanCodecProvider
import net.rsprot.crypto.rsa.RsaKeyPair
import net.rsprot.protocol.api.NetworkService
import osrsps.game.Player
import osrsps.game.World
import osrsps.map.CollisionMap
import osrsps.net.Js5Store
import osrsps.net.NetworkFactory
import osrsps.net.ProxyProtocol
import osrsps.net.RsaKeys

/**
 * Our real network stack and [World] on a free local port, for [HeadlessClient] tests. Nothing comes from the game
 * cache: the JS5 store is a generated cache of empty archives, the map is open ground in the squares [world] marks
 * loaded, and the world gets whatever else [world] gives it.
 */
@OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class)
class TestServer(
    dataDir: Path,
    /** Real time between game cycles. Shorter than live's 600 ms so tests run fast; the tick logic is the same. */
    cycleMillis: Long = 100,
    /** Who may send a PROXY header; to test one from a relay, make 127.0.0.1 the relay. */
    proxy: ProxyProtocol = ProxyProtocol.OFF,
    /** Builds the world from the CRCs and the cycle length; the default is Lumbridge's map square, open ground. */
    world: (crcs: IntArray, cycleMillis: Long) -> World = { crcs, millis ->
        World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, cycleMillis = millis)
    },
) : AutoCloseable {
    val port: Int = ServerSocket(0).use { it.localPort }
    private val key = RsaKeys.generate()
    val rsaModulus: BigInteger get() = key.modulus
    val rsaExponent: BigInteger get() = key.publicExponent

    val world: World
    private val factory: NetworkFactory
    private val network: NetworkService<Player>

    init {
        val js5 = writeEmptyCache(dataDir.resolve("cache"))
        this.world = world(js5.crcs, cycleMillis)
        // 256 eight-bit codewords: a valid Huffman table, enough for chat to encode.
        val huffman = HuffmanCodec.create(Unpooled.wrappedBuffer(ByteArray(256) { 8 }))
        factory = NetworkFactory(port, RsaKeyPair(key.privateExponent, key.modulus), DefaultHuffmanCodecProvider(huffman), js5, this.world, proxy.relays, proxy)
        network = factory.build()
        this.world.network = network
        network.start()
        this.world.start()
    }

    /** Logs a [HeadlessClient] in as [username]. */
    fun login(username: String, password: String = "test", header: ByteArray = ByteArray(0)): HeadlessClient =
        HeadlessClient.login("127.0.0.1", port, rsaModulus, rsaExponent, username, password, header = header)

    override fun close() {
        world.stop()
        factory.closeListeners()
        network.shutdownNow(0, 5, TimeUnit.SECONDS)
    }

    private companion object {
        /** The login checks 23 archive CRCs, so the cache has 23 archives, each with one small group. */
        const val ARCHIVES = 23

        fun writeEmptyCache(cacheDir: Path): Js5Store {
            Files.createDirectories(cacheDir)
            cacheDir.resolve("main_file_cache.dat2").createFile()
            cacheDir.resolve("main_file_cache.idx255").createFile()
            val library = CacheLibrary.create(cacheDir.toString())
            try {
                repeat(ARCHIVES) { library.createIndex() }
                repeat(ARCHIVES) { library.put(it, 0, byteArrayOf(it.toByte())) }
                library.update()
                return Js5Store.load(library)
            } finally {
                library.close()
            }
        }
    }
}
