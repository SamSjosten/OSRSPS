package osrsps

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.Unpooled
import net.rsprot.compression.HuffmanCodec
import net.rsprot.compression.provider.DefaultHuffmanCodecProvider
import net.rsprot.protocol.api.NetworkService
import osrsps.cache.CacheInfo
import osrsps.cache.CombatLoader
import osrsps.cache.EffectIdLoader
import osrsps.cache.FontLoader
import osrsps.cache.GameFrameCheck
import osrsps.cache.MapCollisionLoader
import osrsps.cache.NpcTypeLoader
import osrsps.cache.ObjTypeLoader
import osrsps.cache.QuestLoader
import osrsps.cache.SettingsLoader
import osrsps.cache.SkillUnlockLoader
import osrsps.cache.VarTypeLoader
import osrsps.cache.WorldMap
import osrsps.game.EffectIds
import osrsps.game.AllSettings
import osrsps.game.SettingsCatalog
import osrsps.game.SettingsSide
import osrsps.game.Accounts
import osrsps.game.FileAccountLock
import osrsps.game.IpBans
import osrsps.game.ItemPrices
import osrsps.game.command.Admins
import osrsps.game.FileSaveStore
import osrsps.game.GameRandom
import osrsps.game.NpcTypes
import osrsps.game.ObjSpawnFiles
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Quests
import osrsps.game.Scenarios
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.XpRate
import osrsps.game.WearRequirements
import osrsps.game.SeqNames
import osrsps.game.SpotanimNames
import osrsps.game.combat.AttackStyles
import osrsps.game.combat.Foods
import osrsps.game.combat.Potions
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.combat.Prayers
import osrsps.game.combat.RangedWeapons
import osrsps.game.combat.Spells
import osrsps.game.dialogue.ChatFont
import osrsps.game.dialogue.DialogueFile
import osrsps.game.npc.NpcSpawnFile
import osrsps.game.shop.ShopTypes
import osrsps.game.skill.SkillUnlocks
import osrsps.net.ClientConfig
import osrsps.net.Js5Store
import osrsps.net.RelayAwareValidator
import osrsps.net.NetworkFactory
import osrsps.net.ProxyProtocol
import osrsps.net.RsaKeys
import osrsps.net.RuneLitePins
import java.math.BigInteger
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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
    /** Who may run the world-changing commands; see [Admins]. */
    private val admins: Admins = Admins.EVERYONE,
    /** The git commit this server was built from, served at /version; see [ClientConfig]. */
    version: String = ClientConfig.UNKNOWN_VERSION,
    /** Addresses that forward many players, exempt from the per-address connection limit; see [RelayAwareValidator]. */
    private val relays: Set<String> = emptySet(),
    /** Who may put a PROXY header in front of a connection on either port; see [ProxyProtocol]. */
    private val proxy: ProxyProtocol = ProxyProtocol.OFF,
    /** The modulus the data folder's RSA key must have (the committed one), or null for any key; see [RsaKeys.load]. */
    private val rsaModulus: BigInteger? = null,
    /** deploy/auto-deploy.sh's state folder, which can name a newer commit for /version; see [ClientConfig]. */
    deployState: Path? = null,
) {
    private lateinit var world: World
    private val clientConfig = ClientConfig(
        publicHost, httpPort, gamePort = port, version = version, deployState = deployState, proxy = proxy,
        runelitePins = RuneLitePins.load(),
    )
    private val js5Connections = AtomicLong()
    private lateinit var factory: NetworkFactory
    private lateinit var network: NetworkService<Player>

    fun start() {
        val library = CacheLibrary.create(dataDir.resolve("cache").toString())
        val js5: Js5Store
        val huffman: HuffmanCodec
        val map: WorldMap
        val interfaceIds: Set<Int>
        val varTypes: VarTypes
        val objTypes: ObjTypes
        val npcTypes: NpcTypes
        val quests: Quests
        val effectIds: EffectIds
        val chatFont: ChatFont
        val attackStyles: AttackStyles
        val seqNames: SeqNames
        val spotanimNames: SpotanimNames
        val skillUnlocks: SkillUnlocks
        val settings: SettingsCatalog
        try {
            js5 = Js5Store.load(library)
            map = MapCollisionLoader.loadWorld(library)
            varTypes = VarTypeLoader.load(library)
            objTypes = ObjTypeLoader.load(library)
            npcTypes = NpcTypeLoader.load(library)
            quests = QuestLoader.load(library)
            effectIds = EffectIdLoader.load(library)
            chatFont = FontLoader.loadDialogueFont(library)
            attackStyles = CombatLoader.attackStyles(library)
            seqNames = CombatLoader.seqNames(library)
            spotanimNames = CombatLoader.spotanimNames(library)
            skillUnlocks = SkillUnlockLoader.load(library)
            settings = SettingsLoader.load(library)
            interfaceIds = library.index(INTERFACE_INDEX)?.archiveIds()?.toSet().orEmpty()
            val huffmanData = library.data(HUFFMAN_ARCHIVE, "huffman", 0) ?: error("No huffman group in the cache")
            huffman = HuffmanCodec.create(Unpooled.wrappedBuffer(huffmanData))
            val frameProblems = GameFrameCheck.check(library)
            if (frameProblems.isEmpty()) {
                logger.info { "Game frame, dialogue and chat animation ids match the cache's names" }
            } else {
                frameProblems.forEach { logger.warn { "Game frame: $it" } }
            }
            (SettingsSide.checkVarbits(varTypes) + AllSettings.checkVarbits(varTypes)).forEach { logger.warn { "Settings: $it" } }
            val source = dataDir.resolve("cache-source.txt").toFile().takeIf { it.exists() }?.readText()?.trim()
            // Another revision's cache would hand clients files they can't use, and the next revision's clients our old
            // ones. Without cache-source.txt (a cache copied in by hand, the tests' tiny ones) there's nothing to check.
            source?.let(CacheInfo::sourceRevision)?.let { revision ->
                check(revision == Revision.MAJOR) {
                    "The cache is revision $revision's (cache-source.txt: $source), but the server plays revision " +
                        "${Revision.MAJOR}: fetch-cache the right one, or change Revision.MAJOR"
                }
            }
            clientConfig.status = listOf("cache ${source ?: "of unknown source (no cache-source.txt)"}") +
                frameProblems.ifEmpty { listOf("ids match the cache's names") }.map { "Game frame: $it" }
        } finally {
            library.close()
        }
        logger.info { "Loaded ${js5.size} JS5 responses for ${js5.crcs.size} archives" }

        val rsa = RsaKeys.load(dataDir, rsaModulus)
        clientConfig.modulusHex = rsa.modulus.toString(16)
        val random = GameRandom(seed ?: GameRandom.newSeed())
        logger.info { "Random seed ${random.seed}" + if (seed == null) " (picked at startup; set OSRSPS_SEED to fix it)" else " (from OSRSPS_SEED)" }
        val saveDir = dataDir.resolve("saves")
        logger.info { "Player saves in ${saveDir.toAbsolutePath()}" }
        val accountDir = dataDir.resolve("accounts")
        logger.info { "Accounts in ${accountDir.toAbsolutePath()}" }
        val ipBans = IpBans(dataDir.resolve(IpBans.FILE_NAME), relays)
        logger.info { "IP bans: ${ipBans.size} in ${dataDir.resolve(IpBans.FILE_NAME).toAbsolutePath()}" }
        val xpRate = XpRate(dataDir.resolve(XpRate.FILE_NAME))
        logger.info { "XP rate $xpRate (::xprate changes it; kept in ${dataDir.resolve(XpRate.FILE_NAME).toAbsolutePath()})" }
        if (admins.everyone) logger.warn { "Every player is an admin; set OSRSPS_ADMINS to the names that should be" }
        else logger.info { "Admins: $admins" }
        val npcSpawns = NpcSpawnFile.loadAll(npcTypes)
        logger.info { "Loaded ${npcSpawns.size} NPC spawns" }
        val npcCombat = NpcCombat.load(npcTypes, seqNames)
        logger.info { "Loaded the combat of ${npcCombat.size} NPC types" }
        val npcDrops = NpcDrops.load(npcTypes, objTypes)
        logger.info { "Loaded the drops of ${npcDrops.size} NPC types" }
        val rangedWeapons = RangedWeapons.load(objTypes, seqNames, spotanimNames)
        logger.info { "Loaded ${rangedWeapons.weaponCount} bows and crossbows and ${rangedWeapons.ammoCount} kinds of ammunition" }
        val spells = Spells.load(objTypes, seqNames, spotanimNames)
        logger.info { "Loaded ${spells.size} combat spells" }
        val prayers = Prayers.load(objTypes)
        logger.info { "Loaded ${prayers.size} prayers" }
        val shopTypes = ShopTypes.load(npcTypes, objTypes)
        logger.info { "Loaded ${shopTypes.all.size} shops" }
        val wearRequirements = WearRequirements.load(objTypes)
        logger.info { "Loaded the wear requirements of ${wearRequirements.size} items" }
        val itemPricesFile = dataDir.resolve(ItemPrices.FILE_NAME)
        val itemPrices = ItemPrices.loadOrDownload(itemPricesFile)
        logger.info { "Grand Exchange prices: ${itemPrices.size} (${itemPrices.snapshot ?: "none"}) in ${itemPricesFile.toAbsolutePath()}" }
                val scenarios = Scenarios.load()
        for (name in scenarios.names) {
            // Checked again when started; warned about here so a bad item name shows in the deploy log.
            try {
                scenarios[name]!!.loadout(objTypes)
            } catch (e: IllegalArgumentException) {
                logger.warn { "Scenario $name can't start: ${e.message}" }
            }
        }
        logger.info { "Loaded ${scenarios.names.size} scenarios: ${scenarios.names.joinToString()}" }
        world = World(
            js5.crcs, map.collision, varTypes, interfaceIds, random, quests, FileSaveStore(saveDir),
            Accounts(FileSaveStore(accountDir), lock = FileAccountLock(accountDir)), objTypes, npcTypes, npcSpawns, effectIds, locTypes = map.locTypes, staticLocs = map.locs,
            scenarios = scenarios, chatFont = chatFont, dialogueFile = dataDir.resolve(DialogueFile.NAME), admins = admins,
            attackStyles = attackStyles, npcCombat = npcCombat, npcDrops = npcDrops, rangedWeapons = rangedWeapons,
            spells = spells, prayerList = prayers, foodList = Foods.load(objTypes), potionData = Potions.load(objTypes), shopTypes = shopTypes, skillUnlocks = skillUnlocks, ipBans = ipBans, xpRate = xpRate,
            wearRequirements = wearRequirements, itemPrices = itemPrices, itemPricesFile = itemPricesFile, settings = settings,
        )
        loadObjSpawns(objTypes)
        factory = NetworkFactory(port, rsa, DefaultHuffmanCodecProvider(huffman), js5, world, relays, proxy, js5Connections)
        clientConfig.gameStatus = {
            ClientConfig.GameStatus(world.isUp(), world.playerCount, world.acceptingLogins, js5Connections.get())
        }
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

    /**
     * Puts every item spawn from the resources' spawn files on the ground. A file that doesn't read stops the start,
     * unless the cache has no item types at all (the tests' tiny caches): then no spawn could be placed anyway.
     */
    private fun loadObjSpawns(objTypes: ObjTypes) {
        if (objTypes.size == 0) {
            logger.warn { "No item types in the cache; placing no item spawns" }
            return
        }
        var count = 0
        for ((name, text) in ObjSpawnFiles.resources()) {
            for (spawn in ObjSpawnFiles.parse(text, objTypes, name)) {
                world.groundObjs.addSpawn(spawn)
                count++
            }
        }
        logger.info { "Placed $count item spawn${if (count == 1) "" else "s"}" }
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
