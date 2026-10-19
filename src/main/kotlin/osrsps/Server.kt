package osrsps

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.Unpooled
import net.rsprot.compression.HuffmanCodec
import net.rsprot.compression.provider.DefaultHuffmanCodecProvider
import net.rsprot.protocol.api.NetworkService
import osrsps.game.Player
import osrsps.game.World
import osrsps.net.ClientConfig
import osrsps.net.Js5Store
import osrsps.net.NetworkFactory
import osrsps.net.RsaKeys
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/** The JS5 and game server on [port], built from the cache in [dataDir]/cache. */
@OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class)
class GameServer(
    private val dataDir: Path,
    private val port: Int,
    private val httpPort: Int = port + 1,
    private val publicHost: String = "127.0.0.1",
) {
    private lateinit var world: World
    private val clientConfig = ClientConfig(publicHost, httpPort)
    private lateinit var network: NetworkService<Player>

    fun start() {
        val library = CacheLibrary.create(dataDir.resolve("cache").toString())
        val js5: Js5Store
        val huffman: HuffmanCodec
        try {
            js5 = Js5Store.load(library)
            val huffmanData = library.data(HUFFMAN_ARCHIVE, "huffman", 0) ?: error("No huffman group in the cache")
            huffman = HuffmanCodec.create(Unpooled.wrappedBuffer(huffmanData))
        } finally {
            library.close()
        }
        logger.info { "Loaded ${js5.size} JS5 responses for ${js5.crcs.size} archives" }

        val rsa = RsaKeys.loadOrCreate(dataDir)
        world = World(js5.crcs)
        network = NetworkFactory(port, rsa, DefaultHuffmanCodecProvider(huffman), js5, world).build()
        world.network = network
        network.start()
        clientConfig.start()
        world.start()
        logger.info { "Listening on port $port. RSProx modulus: ${dataDir.resolve("rsa-modulus.txt").toAbsolutePath()}" }
    }

    fun stop() {
        world.stop()
        clientConfig.stop()
        network.shutdownNow(0, 5, TimeUnit.SECONDS)
    }

    private companion object {
        const val HUFFMAN_ARCHIVE = 10
    }
}
