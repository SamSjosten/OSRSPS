package osrsps

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.Unpooled
import net.rsprot.compression.HuffmanCodec
import net.rsprot.compression.provider.DefaultHuffmanCodecProvider
import net.rsprot.protocol.api.NetworkService
import osrsps.cache.EffectIdLoader
import osrsps.cache.GameFrameCheck
import osrsps.cache.MapCollisionLoader
import osrsps.cache.NpcTypeLoader
import osrsps.cache.ObjTypeLoader
import osrsps.cache.QuestLoader
import osrsps.cache.VarTypeLoader
import osrsps.game.EffectIds
import osrsps.game.FileSaveStore
import osrsps.game.GameRandom
import osrsps.game.NpcTypes
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Quests
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.npc.NpcExamines
import osrsps.game.npc.NpcSpawnFile
import osrsps.map.CollisionMap
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
    /** The game's random seed; null picks one, which is logged so the run can be repeated. */
    private val seed: Long? = null,
) {
    private lateinit var world: World
    private val clientConfig = ClientConfig(publicHost, httpPort)
    private lateinit var factory: NetworkFactory
    private lateinit var network: NetworkService<Player>

    fun start() {
        val library = CacheLibrary.create(dataDir.resolve("cache").toString())
        val js5: Js5Store
        val huffman: HuffmanCodec
        val collision: CollisionMap
        val interfaceIds: Set<Int>
        val varTypes: VarTypes
        val objTypes: ObjTypes
        val npcTypes: NpcTypes
        val quests: Quests
        val effectIds: EffectIds
        try {
            js5 = Js5Store.load(library)
            collision = MapCollisionLoader.load(library)
            varTypes = VarTypeLoader.load(library)
            objTypes = ObjTypeLoader.load(library)
            npcTypes = NpcTypeLoader.load(library)
            quests = QuestLoader.load(library)
            effectIds = EffectIdLoader.load(library)
            interfaceIds = library.index(INTERFACE_INDEX)?.archiveIds()?.toSet().orEmpty()
            val huffmanData = library.data(HUFFMAN_ARCHIVE, "huffman", 0) ?: error("No huffman group in the cache")
            huffman = HuffmanCodec.create(Unpooled.wrappedBuffer(huffmanData))
            val frameProblems = GameFrameCheck.check(library)
            if (frameProblems.isEmpty()) {
                logger.info { "Game frame ids match the cache's names" }
            } else {
                frameProblems.forEach { logger.warn { "Game frame: $it" } }
            }
        } finally {
            library.close()
        }
        logger.info { "Loaded ${js5.size} JS5 responses for ${js5.crcs.size} archives" }

        val rsa = RsaKeys.loadOrCreate(dataDir)
        val random = GameRandom(seed ?: GameRandom.newSeed())
        logger.info { "Random seed ${random.seed}" + if (seed == null) " (picked at startup; set OSRSPS_SEED to fix it)" else " (from OSRSPS_SEED)" }
        val saveDir = dataDir.resolve("saves")
        logger.info { "Player saves in ${saveDir.toAbsolutePath()}" }
        val npcSpawns = NpcSpawnFile.loadAll(npcTypes)
        val npcExamines = NpcExamines.load()
        logger.info { "Loaded ${npcSpawns.size} NPC spawns and ${npcExamines.size} NPC examine texts" }
        world = World(
            js5.crcs, collision, varTypes, interfaceIds, random, quests, FileSaveStore(saveDir), objTypes, npcTypes,
            npcSpawns, npcExamines, effectIds,
        )
        factory = NetworkFactory(port, rsa, DefaultHuffmanCodecProvider(huffman), js5, world)
        network = factory.build()
        world.network = network
        network.start()
        try {
            clientConfig.start()
            world.start()
        } catch (e: Exception) {
            // Don't leave the game port open with no world behind it: fail the whole start.
            clientConfig.stop()
            stopNetwork()
            throw e
        }
        logger.info { "Listening on port $port. RSProx modulus: ${dataDir.resolve("rsa-modulus.txt").toAbsolutePath()}" }
    }

    fun stop() {
        world.stop()
        clientConfig.stop()
        stopNetwork()
    }

    private fun stopNetwork() {
        factory.closeListeners()
        network.shutdownNow(0, 5, TimeUnit.SECONDS)
    }

    private companion object {
        const val HUFFMAN_ARCHIVE = 10
        const val INTERFACE_INDEX = 3
    }
}
