package osrsps.game

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.api.GameConnectionHandler
import net.rsprot.protocol.api.NetworkService
import net.rsprot.protocol.api.login.GameLoginResponseHandler
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.incoming.messaging.MessagePublic
import net.rsprot.protocol.game.outgoing.info.util.isEmpty
import net.rsprot.protocol.game.outgoing.info.util.onFailure
import net.rsprot.protocol.game.outgoing.info.util.onSuccess
import net.rsprot.protocol.game.outgoing.info.util.safeReleaseOrThrow
import net.rsprot.protocol.game.outgoing.logout.Logout
import net.rsprot.protocol.game.outgoing.map.RebuildLoginV2
import net.rsprot.protocol.game.outgoing.map.RebuildNormalV2
import net.rsprot.protocol.game.outgoing.misc.client.ServerTickEnd
import net.rsprot.protocol.game.outgoing.misc.player.ChatFilterSettings
import net.rsprot.protocol.game.outgoing.misc.player.ChatFilterSettingsPrivateChat
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.SetMapFlagV2
import net.rsprot.protocol.game.outgoing.misc.player.UpdateRunEnergy
import net.rsprot.protocol.game.outgoing.misc.player.UpdateRunWeight
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStatV2
import net.rsprot.protocol.game.outgoing.sound.MidiJingle
import net.rsprot.protocol.game.outgoing.sound.SynthSound
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import net.rsprot.protocol.loginprot.incoming.util.AuthenticationType
import net.rsprot.protocol.loginprot.incoming.util.LoginBlock
import net.rsprot.crypto.xtea.XteaKey
import net.rsprot.protocol.loginprot.outgoing.LoginResponse
import net.rsprot.protocol.loginprot.outgoing.util.AuthenticatorResponse
import net.rsprot.protocol.channel.hostAddress
import osrsps.game.command.Admins
import osrsps.game.command.CommandRegistry
import osrsps.game.command.registerCombatCommands
import osrsps.game.command.registerDevCommands
import osrsps.game.command.registerEffectCommands
import osrsps.game.command.registerGroundObjCommands
import osrsps.game.command.registerIpCommands
import osrsps.game.command.registerGrandExchangeCommands
import osrsps.game.command.registerItemCommands
import osrsps.game.command.registerLocCommands
import osrsps.game.command.registerNpcCommands
import osrsps.game.command.registerScenarioCommands
import osrsps.game.command.registerScriptCommands
import osrsps.game.command.registerSeedCommands
import osrsps.game.command.registerSkillCommands
import osrsps.game.command.registerSocialCommands
import osrsps.game.command.registerXpRateCommands
import osrsps.game.command.registerTickCommands
import osrsps.game.command.registerVarCommands
import osrsps.game.command.registerZoneCommands
import osrsps.game.bank.Bank
import osrsps.game.bank.BankPinSettings
import osrsps.game.bank.BankScreen
import osrsps.game.bank.DepositBox
import osrsps.game.bank.setVarbitIfKnown
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopTypes
import osrsps.game.combat.AttackStyles
import osrsps.game.combat.Combat
import osrsps.game.combat.Deaths
import osrsps.game.combat.DinhsBulwark
import osrsps.game.combat.MagicCombat
import osrsps.game.combat.MeleeCombat
import osrsps.game.combat.MultiCombat
import osrsps.game.combat.PoweredStaves
import osrsps.game.combat.Prayer
import osrsps.game.combat.Food
import osrsps.game.combat.Foods
import osrsps.game.combat.Poison
import osrsps.game.combat.StatusEffects
import osrsps.game.combat.Potions
import osrsps.game.combat.Prayers
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrains
import osrsps.game.combat.NpcDrops
import osrsps.game.combat.RangedCombat
import osrsps.game.combat.RangedWeapons
import osrsps.game.combat.SpecialAttacks
import osrsps.game.combat.Spells
import osrsps.game.combat.StatRestore
import osrsps.game.content.DialogueLines
import osrsps.game.content.Examines
import osrsps.game.content.registerAdventurersCamp
import osrsps.game.content.registerBarfyBill
import osrsps.game.content.registerCandleSeller
import osrsps.game.content.registerChurch
import osrsps.game.content.registerFatherUrhney
import osrsps.game.content.registerSwampTutors
import osrsps.game.content.registerHans
import osrsps.game.content.registerBanking
import osrsps.game.content.registerShops
import osrsps.game.content.Ducks
import osrsps.game.content.registerCrops
import osrsps.game.content.registerFredsFarm
import osrsps.game.content.registerGroatsFarm
import osrsps.game.content.registerMillLaneMill
import osrsps.game.content.registerLumbridgeTown
import osrsps.game.content.registerAlKharid
import osrsps.game.content.registerDraynorVillage
import osrsps.game.content.registerAxeLogs
import osrsps.game.content.registerDraynorMarket
import osrsps.game.content.registerBones
import osrsps.game.content.registerCombatTutors
import osrsps.game.content.registerLumbridgeCastle
import osrsps.game.dialogue.ChatFont
import osrsps.game.dialogue.DialogueFile
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Dialogues
import osrsps.game.ge.GrandExchange
import osrsps.game.ge.GrandExchangeScreen
import osrsps.game.ge.PriceChecker
import osrsps.game.loc.Doors
import osrsps.game.loc.Gates
import osrsps.game.loc.LocInteractions
import osrsps.game.loc.LocTypes
import osrsps.game.loc.Locs
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcEffects
import osrsps.game.npc.NpcInteractions
import osrsps.game.npc.NpcScripts
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.skill.BirdNests
import osrsps.game.skill.Firemaking
import osrsps.game.skill.LevelUps
import osrsps.game.skill.Making
import osrsps.game.skill.Cooking
import osrsps.game.skill.Crafting
import osrsps.game.skill.CraftingData
import osrsps.game.skill.Fishing
import osrsps.game.skill.Fletching
import osrsps.game.skill.FletchingData
import osrsps.game.skill.Herblore
import osrsps.game.skill.HerbloreData
import osrsps.game.skill.Mining
import osrsps.game.skill.SkillUnlocks
import osrsps.game.skill.Smithing
import osrsps.game.skill.Woodcutting
import osrsps.game.skill.WoodcuttingData
import osrsps.game.script.ScriptRunner
import osrsps.game.social.FriendLists
import osrsps.game.social.IgnoreLists
import osrsps.game.social.PrivateMessages
import osrsps.game.trade.Trades
import osrsps.game.zone.Zones
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

/**
 * The game world: a single thread that runs a 600 ms cycle. Each cycle is a game tick in the [TickPhase] order unless
 * [clock] is paused. Logins arrive from RSProt's login threads and are completed at the start of the next cycle.
 */
class World(
    private val js5Crcs: IntArray,
    private val collision: CollisionMap,
    val varTypes: VarTypes = VarTypes.NONE,
    /** Interface ids in the cache, or null when unknown (tests). */
    private val interfaceIds: Set<Int>? = null,
    /** Every random roll in the game comes from here; see [GameRandom]. */
    val random: GameRandom = GameRandom(),
    /** Set complete for every player at login. */
    val quests: Quests = Quests.NONE,
    /** Where players are saved on logout and every [AUTOSAVE_CYCLES]. */
    private val saves: SaveStore = MemorySaveStore(),
    /** Whose name is whose; see [Accounts]. The default, kept in memory with a cheap hash, is for tests. */
    private val accounts: Accounts = Accounts(MemorySaveStore(), iterations = 1_000),
    val objTypes: ObjTypes = ObjTypes.NONE,
    val npcTypes: NpcTypes = NpcTypes.NONE,
    /** The NPCs put in the world when it starts. */
    private val npcSpawns: List<NpcSpawn> = emptyList(),
    /** The animation, spotanim, sound and jingle ids the dev commands accept. */
    val effectIds: EffectIds = EffectIds.UNKNOWN,
    locTypes: LocTypes = LocTypes.NONE,
    /** The locs the cache's maps place. */
    staticLocs: StaticLocs = StaticLocs(),
    /** What `::scenario` can start. */
    val scenarios: Scenarios = Scenarios.NONE,
    /** The dialogue font's widths, for counting a dialogue's lines; see [ChatFont]. */
    chatFont: ChatFont = ChatFont.APPROXIMATE,
    /** The dialogue file, read at start and again by `::dialogue`; null for none, so every line shows its key. */
    private val dialogueFile: Path? = null,
    /** Who may run the commands that change the world for everyone. */
    val admins: Admins = Admins.EVERYONE,
    /** The combat tab's attack styles by weapon category, from the cache; tests get unarmed only. */
    attackStyles: AttackStyles = AttackStyles.DEFAULT,
    /** What each NPC fights with; an NPC with none can't be attacked. */
    npcCombat: NpcCombat = NpcCombat.NONE,
    /** What each NPC drops when it dies; see [NpcDrops]. */
    val npcDrops: NpcDrops = NpcDrops.NONE,
    /** The bows, crossbows and ammunition [RangedCombat] fires; tests that shoot pass their own. */
    val rangedWeapons: RangedWeapons = RangedWeapons.NONE,
    /** The combat spells [MagicCombat] casts; tests that cast pass their own. */
    val spells: Spells = Spells.NONE,
    /** The prayer book's prayers ([Prayers]); tests that pray pass their own. */
    prayerList: List<Prayer> = emptyList(),
    /** What can be eaten, by obj type id ([Foods]); tests that eat pass their own. */
    foodList: Map<Int, Food> = emptyMap(),
    /** What can be drunk ([Potions]); tests that drink pass their own. */
    potionData: Potions.Data = Potions.Data.NONE,
    /** The shops and their stock; tests that shop pass their own. */
    val shopTypes: ShopTypes = ShopTypes.NONE,
    /** The levels whose level-up plays a skill's unlock jingle; see [LevelUps]. */
    skillUnlocks: SkillUnlocks = SkillUnlocks.NONE,
    /** The levels needed to wear items; tests that check levels pass their own. */
    wearRequirements: WearRequirements = WearRequirements.NONE,
    /** The Grand Exchange price snapshot at start; see [ItemPrices]. */
    itemPrices: ItemPrices = ItemPrices.NONE,
    /** Where `::itemprices` reads the snapshot again and writes a new one; null for none (tests). */
    private val itemPricesFile: Path? = null,
    /** Locks a name after repeated wrong passwords; see [LoginGuard]. */
    private val loginGuard: LoginGuard = LoginGuard(),
    /** Addresses that may not log in; see [IpBans]. */
    val ipBans: IpBans = IpBans(),
    /** The server-wide XP multiplier every player's XP drops are multiplied by; see [XpRate]. */
    val xpRate: XpRate = XpRate(),
    /** The All Settings window's settings, from the cache; tests that click them pass their own. */
    settings: SettingsCatalog = SettingsCatalog.NONE,
    /** Each weapon's special attack cost, by obj id, from the cache's enum 906 ([SpecialAttacks]); tests that spec pass their own. */
    specialAttackCosts: Map<Int, Int> = emptyMap(),
    /** Real time between cycles. Only tests shorten it; the game itself always runs at [TICK_MILLIS]. */
    private val cycleMillis: Long = TICK_MILLIS,
) : GameConnectionHandler<Player> {
    lateinit var network: NetworkService<Player>

    private val movement = Movement(collision)
    internal val interfaces = Interfaces()

    // Before commands: the tick commands keep hold of it.
    val clock = TickClock()

    /** Content handlers for clicks, by what was clicked and which option. */
    val handlers = Handlers()

    /** Locs, ground objs, graphics, projectiles and sounds, sent to the players near them. */
    val zones = Zones()

    /** Every loc in the world now; changing one updates collision and the zones. */
    val locs = Locs(locTypes, staticLocs, collision, zones) { tick }

    val doors = Doors(locs)

    /** Fence gates, both leaves at once; see [Gates]. */
    val gates = Gates(locs)

    val npcs = Npcs(collision, random)

    /** What NPCs do on their own: timers, per-tick actions and deaths that content gives them; see [NpcScripts]. */
    val npcScripts = NpcScripts(npcs) { tick }

    val scripts = ScriptRunner(handlers, interfaces::closeModals) { tick }

    /** Items on the ground: drops, takes, their timers and item spawns. */
    val groundObjs = GroundObjs(
        zones, objTypes, scripts, collision, { findPlayer(it) != null }, { players.count { it != null } },
        // Both sides of a hand-over are saved at once, so a crash can't give the item back to one and leave it with
        // the other. The one who let it go first: a crash between the two writes then loses the item rather than
        // doubling it. Live's saves are its own business; this is ours.
        handedOver = { taker, owner ->
            findPlayer(owner)?.let(::save)
            save(taker)
        },
        words = ::textFor,
    )

    private val equipment = Equipment(objTypes, wearRequirements)
    private val itemWeights = ItemWeights(objTypes)

    /** Melee combat with NPCs; see [Combat]. */
    val combat = Combat(equipment, attackStyles, npcCombat, rangedWeapons, prayerBoosts = { prayers.boosts(it) })

    /** The prayer book; see [Prayers]. */
    val prayers = Prayers(
        prayerList, equipment, setOverhead = { player, icon -> player.infos.playerInfo.avatar.extendedInfo.setOverheadIcon(icon) }, words = ::textFor,
    )

    /** The Grand Exchange prices now; `::itemprices` reads the file again. */
    @Volatile
    var itemPrices: ItemPrices = itemPrices
        private set

    private val inventoryTab: InventoryTab = InventoryTab(
        objTypes, scripts, interfaces::closeModals,
        wear = { player, slot -> equipmentTab.wear(player, slot) },
        onDrop = groundObjs::drop,
        price = { this.itemPrices.price(it) },
        desc = { player, type -> if (type.isCert) textFor(player)[Examines.NOTE] else type.desc ?: "" },
        words = ::textFor,
    )

    /** The bank's rules and the bank interface; see [BankScreen]. */
    val bank = Bank(objTypes)

    val bankScreen = BankScreen(
        bank, objTypes, scripts, interfaces::openModal,
        examine = { player, id, count -> objTypes[id]?.let { inventoryTab.examine(player, it, count) } },
        bonuses = equipment::bonuses,
        wear = { player, slot -> equipmentTab.wear(player, slot) },
        remove = { player, wearpos -> equipmentTab.remove(player, wearpos) },
        words = ::textFor,
    )

    /** Bank deposit boxes; see [DepositBox]. */
    val depositBox = DepositBox(
        bankScreen, interfaces::openModal,
        examine = { player, id, count -> objTypes[id]?.let { inventoryTab.examine(player, it, count) } },
    )

    /** The bankers' PIN settings screen; see [BankPinSettings]. */
    val bankPinSettings = BankPinSettings(interfaces::openModal)

    private val settingsSide = SettingsSide()
    private val allSettings = AllSettings(settings, interfaces, scripts, words = ::textFor)

    /** Grand Exchange offers; see [GrandExchange]. */
    val grandExchange = GrandExchange(objTypes, { this.itemPrices }, { tick }, taxExempt = GrandExchange.taxExempt(objTypes))

    /** The Grand Exchange's offers screen; see [GrandExchangeScreen]. */
    val grandExchangeScreen = GrandExchangeScreen(
        grandExchange, interfaces::openModal, scripts,
        examine = { player, id, count -> objTypes[id]?.let { inventoryTab.examine(player, it, count) } },
        words = ::textFor,
        toBank = ::bankItems,
        closeModals = interfaces::closeModals,
    )

    /** The Grand Exchange guide prices screen; see [PriceChecker]. */
    val priceChecker = PriceChecker(
        grandExchange, objTypes, interfaces::openModal, interfaces::closeModals, scripts,
        examine = { player, id, count -> objTypes[id]?.let { inventoryTab.examine(player, it, count) } },
        words = ::textFor,
        drop = { player, type, count -> dropUnder(player, type, count) },
    )

    /** Puts [count] of [id] into [player]'s bank as a deposit would, a note as its item; returns how many went in. */
    private fun bankItems(player: Player, id: Int, count: Int): Int {
        val from = Inventory(-1, 1)
        from[0] = InvObj(id, count)
        return when (val deposit = bank.deposit(player.bank, bankScreen.tabs(player), from, 0, count)) {
            is Bank.Deposit.Done -> deposit.count
            is Bank.Deposit.Partly -> deposit.count
            else -> 0
        }
    }

    /** Shops and their shared stock; see [ShopScreen]. */
    val shopScreen = ShopScreen(shopTypes, objTypes, interfaces::openModal, examine = inventoryTab::examine, words = ::textFor)

    private val equipmentTab = EquipmentTab(equipment, objTypes, scripts, inventoryTab, interfaces::openModal, spells = spells, words = ::textFor)

    private val interactions = Interactions(words = ::textFor)

    private val follows = Follows(movement, { players[it.index] === it })

    private val locInteractions = LocInteractions(locs, collision, movement, handlers, scripts, doors, gates, words = ::textFor)

    val npcInteractions: NpcInteractions = NpcInteractions(
        npcs, npcTypes, collision, movement, handlers, scripts, { tick },
        attack = { player, npc -> meleeCombat.attack(player, npc) },
        attackFromAfar = { player, npc -> meleeCombat.attackFromAfar(player, npc) },
        words = ::textFor,
    )

    /** Where combat is multi-way; see [MultiCombat]. */
    val multiCombat = MultiCombat()

    /** Players fighting NPCs; see [MeleeCombat]. */
    val meleeCombat: MeleeCombat = MeleeCombat(
        npcs, collision, movement, combat, random, { tick }, removeNpc = ::removeNpc, spawnNpc = { spawnNpc(it) },
        players = { players.asSequence().filterNotNull() },
        face = { player, npc -> npcInteractions.face(player, npc) }, stopFacing = { npcInteractions.stopFacing(it) },
        onDeath = { deaths.die(it) },
        loot = ::dropLoot,
        ranged = { player, npc -> magicCombat.fight(player, npc) ?: rangedCombat.fight(player, npc) },
        multi = multiCombat, protects = prayers::protects, hasPrayer = prayers::has, prayersOff = prayers::deactivateAll,
        words = ::textFor,
        npcDied = { npcScripts.died(it) },
        poisonNpc = { npc, severity -> poison.poison(npc, severity) }, envenomNpc = { poison.envenom(it) },
        poisonPlayer = { player, severity -> poison.poison(player, severity) },
        special = { specialAttacks.use(it, combat.weapon(it)) },
        specialLanded = { npc, dealt, special -> specialAttacks.landed(npc, dealt, special) },
    )

    /** Players shooting NPCs with bows and crossbows; see [RangedCombat]. */
    val rangedCombat: RangedCombat = RangedCombat(
        npcs, collision, movement, combat, objTypes, rangedWeapons, meleeCombat, random, { tick },
        projectile = zones::projectile, dropAmmo = ::dropAmmo,
        face = { player, npc -> npcInteractions.face(player, npc) }, stopFacing = { npcInteractions.stopFacing(it) },
        multi = multiCombat, words = ::textFor,
    )

    /** Tumeken's shadow and the powered staves to come; see [PoweredStaves]. */
    val poweredStaves = PoweredStaves(objTypes, words = ::textFor)

    /** Players casting combat spells on NPCs; see [MagicCombat]. */
    val magicCombat: MagicCombat = MagicCombat(
        npcs, collision, movement, combat, objTypes, spells, meleeCombat, random, { tick },
        projectile = zones::projectile,
        face = { player, npc -> npcInteractions.face(player, npc) }, stopFacing = { npcInteractions.stopFacing(it) },
        multi = multiCombat, poweredStaves = poweredStaves, words = ::textFor,
    )

    /** Player death and gravestones; see [Deaths]. */
    val deaths: Deaths = Deaths(
        objTypes, graveType = npcTypes[Deaths.GRAVE], currentTick = { tick },
        players = { players.asSequence().filterNotNull() }, findPlayer = ::findPlayer,
        spawnNpc = ::spawnNpc, removeNpc = ::removeNpc,
        teleportToSpawn = { teleport(it, SPAWN_X, SPAWN_Z, SPAWN_LEVEL) },
        openModal = { player, id -> interfaces.openModal(player, FrameSlot.MAINMODAL, id) },
        closeModals = interfaces::closeModals, setVarbitNow = interfaces::setVarbitNow, cancelScripts = scripts::clearAll,
        prayersOff = prayers::deactivateAll, protectItem = { prayers.has(it, Prayers.PROTECT_ITEM) },
        examine = inventoryTab::examine, value = { this.itemPrices.deathValue(it) },
        words = ::textFor, cure = {
            poison.clear(it)
            statusEffects.clear(it)
            specialAttacks.refill(it)
        },
    )

    /** The words of every dialogue line now; see [loadDialogue]. */
    var dialogueText: DialogueText = DialogueText.NONE
        private set

    /** The words [player] sees: none, so every line shows its key, while they have `::keys` on. */
    fun textFor(player: Player): DialogueText = if (player.showDialogueKeys) DialogueText.NONE else dialogueText

    /** The friends lists; see [FriendLists]. */
    val friendLists = FriendLists({ onlinePlayers }, ::accountName) { player, line -> player.message(textFor(player)[line]) }

    /** The ignore lists; see [IgnoreLists]. */
    val ignoreLists = IgnoreLists(::accountName) { player, line -> player.message(textFor(player)[line]) }

    /** Trading between players; see [Trades]. */
    val trades = Trades(
        collision, movement, { players[it.index] === it }, ignoreLists, ::textFor, interfaces::openModal, interfaces::closeModals,
        objTypes = objTypes,
        price = { itemPrices.price(it) },
        startScript = { player, script -> if (!player.scripts.delayed) scripts.start(player, script) },
        examine = { player, type, count -> inventoryTab.examine(player, type, count) },
        overflow = { player, obj -> objTypes[obj.id]?.let { groundObjs.give(player, it, obj.count) } },
        setVarbit = { player, id, value -> player.vars.setVarbitIfKnown(id, value) },
    )

    /** Private messages; see [PrivateMessages]. */
    val privateMessages = PrivateMessages(
        friendLists, ignoreLists, { onlinePlayers }, { player, line -> player.message(textFor(player)[line]) }, ::sendChatFilters,
        PrivateMessages.counterStart(java.time.Instant.now()),
    )

    /** Bird nests that fall from trees. */
    val birdNests = BirdNests(this, collision)

    /** The ducks' quacks, the ducklings following them, and the land ducks running off; see [Ducks]. */
    val ducks = Ducks(npcs, npcScripts, collision, random, { tick }, { dialogueText }, { onlinePlayers }) { npc, id ->
        zones.soundArea(npc.x, npc.z, npc.level, id)
    }

    val woodcutting = Woodcutting(this, WoodcuttingData.load(), birdNests)

    val firemaking = Firemaking(this, collision)

    val mining = Mining(this)

    val fishing = Fishing(this)

    val cooking = Cooking(this)
    val smithing = Smithing(this)

    /** The Make-X loop the artisan skills share. */
    val making = Making(this)
    val fletching = Fletching(this, FletchingData.load())
    val crafting = Crafting(this, CraftingData.load())
    val herblore = Herblore(this, HerbloreData.load())

    /** Boosted and drained stats going back; see [StatRestore]. */
    private val statRestore = StatRestore(prayers::has, potionData.divineHolds)

    private val levelUps = LevelUps(interfaces, scripts, skillUnlocks, words = ::textFor)

    val foods = Foods(foodList, words = ::textFor)

    /** Poison and venom on players; see [Poison]. */
    val poison = Poison(
        hit = { player, damage, hitsplat -> meleeCombat.statusHit(player, damage, hitsplat) }, words = ::textFor,
        hitNpc = { npc, damage, hitsplat -> meleeCombat.statusHit(npc, damage, hitsplat) }, npcCombat = { combat.npcCombat[it.type] },
    )

    val potions = Potions(potionData, words = ::textFor, poison = poison)

    /** Monsters' drained stats; see [NpcDrains]. */
    val npcDrains = NpcDrains { combat.npcCombat[it.type] }

    /** The special attack bar and the specials that are in; see [SpecialAttacks]. */
    val specialAttacks = SpecialAttacks(specialAttackCosts, npcDrains, words = ::textFor)

    /** Stuns on players; see [StatusEffects]. */
    val statusEffects = StatusEffects(
        currentTick = { tick },
        stop = { player ->
            player.waypoints.clear()
            player.interaction = null
            npcInteractions.leave(player)
            player.session.queue(SetMapFlagV2.RESET)
        },
        hit = { player, npc, damage -> meleeCombat.hitPlayer(player, npc, damage, tick) },
    )

    /** Chat dialogues for content scripts. */
    val dialogues = Dialogues(interfaces, scripts, chatFont, npcInteractions::shownType, words = { dialogueText })

    val commands = CommandRegistry(admins::isAdmin).apply {
        registerDevCommands(this@World)
        registerTickCommands(this@World)
        registerVarCommands()
        registerSkillCommands { phase != null }
        registerXpRateCommands(this@World)
        registerSeedCommands(this@World)
        registerScriptCommands(this@World)
        registerZoneCommands(this@World, collision)
        registerEffectCommands(this@World)
        registerItemCommands(this@World)
        registerGrandExchangeCommands(this@World)
        registerGroundObjCommands(this@World)
        registerLocCommands(this@World)
        registerNpcCommands(this@World)
        registerScenarioCommands(this@World)
        registerCombatCommands(this@World)
        registerIpCommands(this@World)
        registerSocialCommands(this@World)
    }

    init {
        deaths.register(handlers)
        rangedCombat.blowpipe.register(handlers, Player::message)
        poweredStaves.register(handlers, Player::message)
        registerHans(this)
        registerLumbridgeCastle(this)
        registerCombatTutors(this)
        registerBones(this)
        foods.register(this)
        potions.register(this)
        registerBanking(this)
        interfaces.onClose(BankScreen.MAIN, bankScreen::closed)
        registerShops(this)
        registerLumbridgeTown(this)
        registerDraynorVillage(this)
        registerAxeLogs(this)
        registerDraynorMarket(this)
        registerFredsFarm(this)
        registerGroatsFarm(this)
        registerMillLaneMill(this)
        registerBarfyBill(this)
        registerFatherUrhney(this)
        registerAdventurersCamp(this)
        registerCandleSeller(this)
        registerSwampTutors(this)
        registerChurch(this)
        registerAlKharid(this)
        registerCrops(this)
        ducks.register()
        interfaces.onClose(ShopScreen.MAIN, shopScreen::closed)
        interfaces.onClose(GrandExchangeScreen.MAIN, grandExchangeScreen::closed)
        interfaces.onClose(Trades.MAIN, trades::decline)
        interfaces.onClose(PriceChecker.MAIN, priceChecker::closed)
        woodcutting.register()
        birdNests.register()
        firemaking.register()
        mining.register()
        fishing.register()
        cooking.register()
        smithing.register()
        fletching.register()
        crafting.register()
        herblore.register()
        loadDialogue()
    }

    /** Reads the Grand Exchange price file again, for a hand edit; says what it read. */
    fun loadItemPrices(): String {
        val file = itemPricesFile ?: return "No price file"
        if (!file.exists()) return "No ${file.fileName} yet: ::itemprices refresh downloads one"
        itemPrices = ItemPrices.parse(file.readText())
        return "Read ${itemPrices.size} Grand Exchange prices (${itemPrices.snapshot ?: "no snapshot date"})"
    }

    /**
     * Downloads a new Grand Exchange snapshot from the Wiki on a thread of its own, so the tick doesn't wait on the
     * network, and writes it to the file; `::itemprices` then reads it. The outcome goes to the server log.
     */
    fun refreshItemPrices(): String {
        val file = itemPricesFile ?: return "No price file"
        Thread({
            try {
                val prices = ItemPrices.refresh(file)
                logger.info { "Downloaded ${prices.size} Grand Exchange prices (${prices.snapshot}) into $file" }
            } catch (e: Exception) {
                logger.warn(e) { "Could not download the Grand Exchange prices" }
            }
        }, "item-prices").apply { isDaemon = true }.start()
        return "Downloading the Wiki's Grand Exchange prices; run ::itemprices in a few seconds to read them"
    }

    /**
     * Reads the dialogue file again, so new words show from the next line said. Returns what was read and what was
     * wrong with it, which is logged too.
     */
    fun loadDialogue(): List<String> {
        val file = dialogueFile ?: return listOf("No dialogue file: every line shows its key").onEach { logger.info { it } }
        val loaded = DialogueFile.load(file, DialogueLines.ALL)
        dialogueText = loaded.text
        logger.info { loaded.summary }
        loaded.problems.forEach { logger.warn { "${file.fileName}: $it" } }
        return listOf(loaded.summary) + loaded.problems
    }

    /** The phase of the tick being run, or null between ticks and on paused cycles. */
    var phase: TickPhase? = null
        private set

    /** Who to tell when the last tick of a ::step has run. */
    private var stepper: Player? = null

    private val pendingLogins = ConcurrentLinkedQueue<PendingLogin>()
    private val players = arrayOfNulls<Player>(MAX_PLAYERS)

    /**
     * Slots emptied in the last [SLOT_HOLD_CYCLES] cycles, by the cycle they were emptied on. A login takes one only when
     * no other slot is free, so a click on a player who just left, even one that arrives late, can't land on someone new
     * in their slot. How long live holds a slot isn't known.
     */
    private val freedSlots = HashMap<Int, Long>()
    private val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "game") }

    /**
     * Checks login passwords, off the game thread as a hash takes tens of milliseconds. One thread, so two first logins
     * as one name can't both claim it.
     */
    private val authExecutor = Executors.newSingleThreadExecutor { Thread(it, "login-auth").apply { isDaemon = true } }

    /** Logins queued for or in a password check, held to [MAX_AUTH_WAITING] so a flood can't queue hashes without end. */
    private val authWaiting = AtomicInteger()
    var tick: Long = 0
        private set

    /** Cycles run, paused ones included, for saving every few minutes of real time. */
    private var cycles: Long = 0

    /** Whether [start] has run and [stop] hasn't begun; read off the game thread by [isUp]. */
    @Volatile
    private var running = false

    /** When the last cycle ended, or when the world started, in epoch millis; read off the game thread by [isUp]. */
    @Volatile
    private var lastCycleMillis = 0L

    /**
     * How many players are in the world, dropped connections waiting for a reconnect included, as the last cycle left
     * it; for /status.json, off the game thread.
     */
    @Volatile
    var playerCount = 0
        private set

    /** A login or, with [reconnectSeed], a reconnect: the ISAAC seed the client says its last connection used. */
    private class PendingLogin(
        val responseHandler: GameLoginResponseHandler<Player>,
        val block: LoginBlock<*>,
        val reconnectSeed: IntArray? = null,
    )

    override fun onLogin(responseHandler: GameLoginResponseHandler<Player>, block: LoginBlock<AuthenticationType>) {
        // A copy for the check below, so the login block's own copy can be wiped now.
        val password = (block.authentication as? AuthenticationType.PasswordAuthentication)?.password?.asCharArray()
        block.authentication.clear()
        val address = responseHandler.address
        if (isEmailAddress(block.username)) {
            password?.fill('\u0000')
            refuseEmailAddress(responseHandler, "login")
            return
        }
        if (!Accounts.validName(block.username)) {
            password?.fill('\u0000')
            refuseBadName(responseHandler, "login")
            return
        }
        if (ipBans.isBanned(address)) {
            logger.info { "Rejecting ${block.username} from $address: that address is banned" }
            responseHandler.writeFailedResponse(LoginResponse.IPBlocked)
            password?.fill('\u0000')
            return
        }
        // RSProt's per-address limit; without it one host could queue logins without bound.
        if (!responseHandler.validateNewConnection()) {
            logger.info { "Rejecting ${block.username} from $address: too many connections from one address" }
            responseHandler.writeFailedResponse(LoginResponse.IPLimit)
            password?.fill('\u0000')
            return
        }
        if (!block.crc.validate(js5Crcs)) {
            logger.info { "Rejecting ${block.username}: client cache CRCs do not match ours" }
            responseHandler.writeFailedResponse(LoginResponse.OutOfDateReload)
            password?.fill('\u0000')
            return
        }
        if (password == null) {
            // A Jagex launcher token: only Jagex can check one, and our accounts have passwords.
            logger.info { "Rejecting ${block.username}: logged in with a token, not a password" }
            responseHandler.writeFailedResponse(LoginResponse.InvalidUsernameOrPassword)
            return
        }
        // The relay puts every internet player behind one address, so the address limit above can't stop a flood.
        if (authWaiting.incrementAndGet() > MAX_AUTH_WAITING) {
            authWaiting.decrementAndGet()
            logger.info { "Rejecting ${block.username}: $MAX_AUTH_WAITING logins are already waiting on a password check" }
            responseHandler.writeFailedResponse(LoginResponse.TooManyAttempts)
            password.fill('\u0000')
            return
        }
        authExecutor.execute {
            try {
                checkPassword(responseHandler, block, password)
            } finally {
                authWaiting.decrementAndGet()
            }
        }
    }

    /**
     * Whether a login's name has an "@", as an email address does: likely the friend's real game login, typed where
     * the launcher asks for a made-up name. Refused before the name is logged, hashed or saved anywhere.
     */
    private fun isEmailAddress(username: String): Boolean = '@' in username

    /** Not logged, as the name could hold anything, line breaks included. */
    private fun refuseBadName(responseHandler: GameLoginResponseHandler<Player>, what: String) {
        logger.info { "Rejecting a $what from ${responseHandler.address}: its name breaks the name rules (not logged)" }
        responseHandler.writeFailedResponse(LoginResponse.InvalidUsernameOrPassword)
    }

    private fun refuseEmailAddress(responseHandler: GameLoginResponseHandler<Player>, what: String) {
        logger.info { "Rejecting a $what from ${responseHandler.address}: its name is an email address (not logged)" }
        responseHandler.writeFailedResponse(LoginResponse.InvalidUsernameOrPassword)
    }

    /** On the login-auth thread: queues the login if [password] is right for its name, or claims the name for it. */
    private fun checkPassword(responseHandler: GameLoginResponseHandler<Player>, block: LoginBlock<AuthenticationType>, password: CharArray) {
        val key = SaveStore.saveKey(block.username)
        // Checked before hashing, so guesses at a locked name cost nothing. The client says to wait a few minutes.
        val address = responseHandler.address
        if (key != null && loginGuard.locked(key, address)) {
            password.fill('\u0000')
            logger.info { "Rejecting ${block.username} from $address: locked there after ${LoginGuard.MAX_WRONG} wrong passwords" }
            responseHandler.writeFailedResponse(LoginResponse.TooManyAttempts)
            return
        }
        val check = try {
            accounts.check(block.username, password, mayClaim = key == null || !admins.lists(key))
        } catch (e: Exception) {
            logger.error(e) { "Rejecting ${block.username}: their account can't be read or written" }
            responseHandler.writeFailedResponse(LoginResponse.ConnectFail)
            return
        } finally {
            password.fill('\u0000')
        }
        when (check) {
            Accounts.Check.WRONG_PASSWORD -> {
                loginGuard.wrongPassword(key!!, address)
                logger.info { "Rejecting ${block.username} from ${responseHandler.address}: wrong password" }
                responseHandler.writeFailedResponse(LoginResponse.InvalidUsernameOrPassword)
                return
            }
            Accounts.Check.CLAIMED -> {
                // Strikes against a name whose file was deleted for a reset don't carry over to its new password.
                loginGuard.forget(key!!)
                logger.info { "${block.username} claimed their name" }
            }
            Accounts.Check.NOT_CLAIMABLE -> {
                logger.info { "Rejecting ${block.username}: an admin's name nobody has claimed yet; claim it before listing it in OSRSPS_ADMINS" }
                responseHandler.writeFailedResponse(LoginResponse.InvalidUsernameOrPassword)
                return
            }
            Accounts.Check.MATCHED -> loginGuard.rightPassword(key!!, address)
            Accounts.Check.NO_SUCH_NAME -> {}
        }
        pendingLogins.add(PendingLogin(responseHandler, block))
    }

    override fun onReconnect(responseHandler: GameLoginResponseHandler<Player>, block: LoginBlock<XteaKey>) {
        val address = responseHandler.address
        if (isEmailAddress(block.username)) {
            refuseEmailAddress(responseHandler, "reconnect")
            return
        }
        if (!Accounts.validName(block.username)) {
            refuseBadName(responseHandler, "reconnect")
            return
        }
        if (ipBans.isBanned(address)) {
            logger.info { "Rejecting a reconnect for ${block.username} from $address: that address is banned" }
            responseHandler.writeFailedResponse(LoginResponse.IPBlocked)
            return
        }
        // The same per-address limit as a login, so reconnects can't queue without bound either. The old connection may
        // still count against it until it times out, which the limit of 10 leaves room for.
        if (!responseHandler.validateNewConnection()) {
            logger.info { "Rejecting a reconnect for ${block.username} from $address: too many connections from one address" }
            responseHandler.writeFailedResponse(LoginResponse.IPLimit)
            return
        }
        if (!block.crc.validate(js5Crcs)) {
            logger.info { "Rejecting a reconnect for ${block.username}: client cache CRCs do not match ours" }
            responseHandler.writeFailedResponse(LoginResponse.OutOfDateReload)
            return
        }
        pendingLogins.add(PendingLogin(responseHandler, block, block.authentication.key.copyOf()))
    }

    fun start() {
        lastCycleMillis = System.currentTimeMillis()
        running = true
        executor.execute {
            network.setCommunicationThread(Thread.currentThread(), true)
            // On the game thread: RSProt hands out NPC avatars only there.
            spawnNpcs()
        }
        executor.scheduleAtFixedRate(::safeCycle, cycleMillis, cycleMillis, TimeUnit.MILLISECONDS)
    }

    /** Stops the cycle and saves everyone still in the world. */
    fun stop() {
        running = false
        authExecutor.shutdownNow()
        executor.shutdown()
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            // A cycle stuck waiting (a save on a slow disk) may still give way to an interrupt.
            logger.warn { "The game thread didn't stop in 5 s; interrupting it" }
            executor.shutdownNow()
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                // Saving now would race the game thread for the players and the save files; the last autosave stands.
                logger.error { "The game thread didn't stop in 10 s; not saving on shutdown. ${players.count { it != null }} players and ${unwritten.size} unwritten saves go back to their last save" }
                return
            }
        }
        // Once the executor has stopped this thread is the only one touching the players. Nobody is saved dead: a
        // death the stop cut short finishes now, grave and all (the grave is lost with the restart).
        for (player in players) if (player?.dead == true) deaths.respawn(player)
        saveAll()
    }

    private fun safeCycle() {
        try {
            if (clock.advance()) tick() else pausedCycle()
        } catch (e: Throwable) {
            logger.error(e) { "Tick $tick failed" }
        } finally {
            phase = null
        }
        cycles++
        // Guessed interval: the task asks for "every few minutes"; live's own autosave interval isn't known.
        if (cycles % AUTOSAVE_CYCLES == 0L) saveAll()
        playerCount = players.count { it != null }
        lastCycleMillis = System.currentTimeMillis()
    }

    /**
     * Whether the game is running, for /status.json: started, not stopping, and a cycle has ended within
     * [STALL_MILLIS] of [now], so a stuck game thread shows as down even though the HTTP port still answers.
     */
    fun isUp(now: Long = System.currentTimeMillis()): Boolean = running && now - lastCycleMillis < STALL_MILLIS

    /** Whether a login now would be let in as far as the world can tell: it is up, with a slot free. */
    val acceptingLogins: Boolean get() = isUp() && playerCount < MAX_PLAYERS - 1

    private fun tick() {
        tick++
        phase = TickPhase.WORLD
        for (player in players) if (player != null) prayers.startTick(player)
        groundObjs.process()
        phase = TickPhase.INPUT
        readInput(ticking = true)
        phase = TickPhase.NPCS
        npcs.countDownFreezes()
        npcs.countDownTransforms()
        npcInteractions.releaseNpcs()
        // Shots and spells land before NPCs fight, as melee hits from the last tick are already in; a shot NPC strikes from the next.
        rangedCombat.process()
        magicCombat.process()
        meleeCombat.process()
        for (npc in npcs.all) {
            poison.process(npc)
            npcDrains.process(npc)
        }
        deaths.process()
        npcScripts.process()
        npcs.process()
        fishing.process()
        shopScreen.restock(tick)
        phase = TickPhase.SCRIPTS
        for (player in players) {
            if (player == null) continue
            player.playedTicks++
            statRestore.process(player)
            poison.process(player)
            specialAttacks.process(player)
            prayers.drain(player)
            levelUps.process(player)
            scripts.process(player)
            grandExchange.process(player)
        }
        phase = TickPhase.MOVEMENT
        // Guessed place in the tick: before anyone walks, so a door that closes itself blocks this tick's steps.
        locs.process()
        firemaking.process()
        follows.startMovement(players.filterNotNull())
        for (player in players) {
            if (player == null || player.loggedOut) continue
            perPlayer(player, "movement") { movePhase(player) }
        }
        phase = TickPhase.UPDATES
        if (clock.paused && clock.stepsLeft == 0) finishStepping()
        sendUpdates(tickEnd = true)
    }

    /** One player's part of the movement phase. */
    private fun movePhase(player: Player) {
        interfaces.processFrameMove(player, tick)
        sendWeight(player)
        // Before the interactions, so a loc or NPC used this tick can still turn the player after a follow ends;
        // after them for a follow that ended in them.
        sendFace(player)
        // A stunned player neither walks nor uses anything; the stun ended their route and interaction already. The
        // standing still is still a move, so run energy comes back as it does while delayed.
        if (statusEffects.isStunned(player, tick)) {
            player.waypoints.clear()
            move(player)
        } else {
            follows.process(player, move = { move(player) }) { interactions.process(player) { move(player) } }
        }
        sendFace(player)
        // A loc or obj used after the player's step (a ladder on arrival) can teleport them once move() has run.
        if (player.teleported) {
            finishTeleport(player)
            rebuildIfNeeded(player)
        }
        player.infos.updateRootCoord(player.level, player.x, player.z)
    }

    /**
     * Runs [part] of a tick for one [player], so a failure costs only them: once the shared protocol state has moved on,
     * whatever failed for one player would otherwise stop the rest of the tick for everyone after them, every tick (the
     * exploit review found two ways to cause that). The player is kicked, as their state can't be trusted.
     */
    private inline fun perPlayer(player: Player, part: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            logger.error(e) { "The $part of tick $tick failed for ${player.username}; kicking them" }
            kick(player)
        }
    }

    /** Drops [player]'s connection and removes them at the start of the next cycle, as a logout does. */
    private fun kick(player: Player) {
        player.loggedOut = true
        // The save that follows would keep a place the map can't hold, and a save like that refuses to load.
        if (player.level !in 0 until LEVELS || player.x !in 0 until MAP_SIZE || player.z !in 0 until MAP_SIZE) {
            logger.warn { "${player.username} was at ${player.x}, ${player.z}, ${player.level}; saving them at the spawn" }
            player.x = SPAWN_X
            player.z = SPAWN_Z
            player.level = SPAWN_LEVEL
        }
        try {
            player.session.requestClose()
        } catch (e: Exception) {
            logger.error(e) { "Couldn't close ${player.username}'s connection" }
        }
    }

    /**
     * A cycle while paused: no game time passes, but logins, commands and the connection carry on. Clicks and buttons
     * are held for the next tick.
     */
    private fun pausedCycle() {
        readInput(ticking = false)
        // Inferred, not measured: the client shows "Connection lost" after about 15 s with nothing from the server, so a
        // frozen world still sends its unchanged player info every cycle. This also flushes command replies.
        sendUpdates(tickEnd = false)
    }

    private fun readInput(ticking: Boolean) {
        // Leavers go first, so their save is written before a login on the same name reads it.
        for (player in players) {
            if (player != null && (player.loggedOut || (player.connectionLost && lingerOver(player)))) {
                try {
                    remove(player)
                } catch (e: Exception) {
                    logger.error(e) { "Removing ${player.username} failed" }
                }
            }
        }
        acceptLogins()
        for (player in players) {
            if (player == null) continue
            if (ticking) runHeldActions(player)
            player.session.processIncomingPackets(player)
        }
        freedSlots.values.removeIf { cycles - it >= SLOT_HOLD_CYCLES }
    }

    /** Sends every player this cycle's updates. [tickEnd] says whether the cycle was a game tick. */
    private fun sendUpdates(tickEnd: Boolean) {
        // Here rather than in the movement loop so a ::varp or ::setlevel typed while paused shows without waiting for a
        // step.
        for (player in players) {
            if (player == null) continue
            perPlayer(player, "updates") {
                if (player.equipment.hasChanges) {
                    val weapon = combat.weapon(player)
                    if ((weapon?.id ?: -1) != player.weaponId) {
                        player.weaponId = weapon?.id ?: -1
                        player.weaponSince = tick
                    }
                    specialAttacks.weaponChanged(player, weapon)
                }
                equipmentTab.updateVars(player)
                bankScreen.updateVars(player)
                depositBox.updateVars(player)
                sendVars(player)
                sendStats(player)
                sendEffects(player)
                inventoryTab.flush(player)
                equipmentTab.flush(player)
                bankScreen.flush(player)
                shopScreen.flush(player)
                grandExchangeScreen.flush(player)
                priceChecker.flush(player)
                trades.flush(player)
            }
        }
        for (player in players) if (player != null) trades.flushed(player)
        for (npc in npcs.all) sendNpcEffects(npc)
        network.infoProtocols.update()
        try {
            for (player in players) {
                if (player != null) perPlayer(player, "info") { sendInfo(player, tickEnd) }
            }
        } finally {
            // Even after a failure, so a graphic is never sent twice and the shared buffers are always freed.
            zones.endCycle()
        }
    }

    /** Pauses the clock once the current tick, if any, has finished. Returns the number of queued steps it dropped. */
    fun pause(): Int {
        stepper = null
        return clock.pause()
    }

    fun resume() {
        stepper = null
        clock.resume()
    }

    /** Runs [ticks] more ticks while paused and tells [player] when they're done. */
    fun step(player: Player, ticks: Int) {
        clock.step(ticks)
        stepper = player
    }

    private fun finishStepping() {
        val player = stepper ?: return
        stepper = null
        if (players[player.index] === player) player.message("Paused after tick $tick")
    }

    /**
     * Runs a game action from a client packet now during a tick's input phase. On a paused cycle the action is held
     * and runs at the start of the next tick's input, before that tick's packets, as if the packet had arrived then.
     */
    private fun act(player: Player, action: () -> Unit) {
        if (phase == TickPhase.INPUT) {
            guarded(player, action)
            return
        }
        if (player.heldActions.size >= MAX_HELD_ACTIONS) {
            logger.info { "Dropping a held action for ${player.username}: $MAX_HELD_ACTIONS already waiting" }
            return
        }
        player.heldActions.addLast(action)
    }

    /**
     * Whether [player]'s click on the map, an NPC, a loc, an obj on the ground or a player is dropped: while delayed
     * (inferred: a delayed player can't walk away, as on an agility obstacle) or stunned ([StatusEffects]). A stunned
     * player's map flag is put back down, as the click goes nowhere (guessed).
     */
    private fun held(player: Player): Boolean {
        if (player.scripts.delayed) return true
        if (!statusEffects.isStunned(player, tick)) return false
        player.session.queue(SetMapFlagV2.RESET)
        return true
    }

    /**
     * Input that goes through while the player is dead: side panel lists the client has already changed for itself,
     * which dropping would leave out of step with the server.
     */
    private class WhileDead(val action: () -> Unit) : () -> Unit {
        override fun invoke() = action()
    }

    /** [act] for input that still counts while dead; see [WhileDead]. */
    private fun actWhileDead(player: Player, action: () -> Unit) = act(player, WhileDead(action))

    /** A dead player's clicks, buttons and chat do nothing until they respawn; see [Deaths]. */
    private fun guarded(player: Player, action: () -> Unit) {
        if (player.dead && action !is WhileDead) {
            logger.debug { "Dropping input from ${player.username}: dead" }
            return
        }
        action()
    }

    internal fun runHeldActions(player: Player) {
        while (true) {
            val action = player.heldActions.removeFirstOrNull() ?: return
            // One bad click shouldn't cost everyone the tick.
            try {
                guarded(player, action)
            } catch (e: Exception) {
                logger.error(e) { "A held action for ${player.username} failed" }
            }
        }
    }

    /**
     * Whether a player whose connection dropped has waited long enough for a reconnect. The wait is counted in ticks,
     * so a paused world keeps them until it runs again.
     */
    private fun lingerOver(player: Player): Boolean {
        if (player.connectionLostTick < 0) {
            player.connectionLostTick = tick
            logger.info { "${player.username} lost their connection; keeping them for $LINGER_TICKS ticks for a reconnect" }
        }
        return tick - player.connectionLostTick >= LINGER_TICKS
    }

    private fun acceptLogins() {
        while (true) {
            val login = pendingLogins.poll() ?: return
            val seed = login.reconnectSeed
            if (seed != null) reconnect(login, seed) else login(login)
        }
    }

    private fun login(login: PendingLogin) {
        val username = login.block.username
        if (refusedForBan(login)) return
        val key = SaveStore.saveKey(username)
        if (key == null) {
            logger.info { "Rejecting '$username': not a name a save can be kept under" }
            login.responseHandler.writeFailedResponse(LoginResponse.InvalidUsernameOrPassword)
            return
        }
        // Live refuses a login while the account is still in the world, a dropped connection waiting for a
        // reconnect included, with "Your account is already logged in". Two players on one save would also overwrite
        // each other's progress.
        if (findPlayer(key) != null) {
            logger.info { "Rejecting $username: already in the world" }
            login.responseHandler.writeFailedResponse(LoginResponse.Duplicate)
            return
        }
        val index = (1 until MAX_PLAYERS).firstOrNull { players[it] == null && it !in freedSlots }
            ?: (1 until MAX_PLAYERS).firstOrNull { players[it] == null }
        if (index == null) {
            login.responseHandler.writeFailedResponse(LoginResponse.ServerFull)
            return
        }
        val player = try {
            loadPlayer(index, username)
        } catch (e: Exception) {
            // Refused rather than started afresh, which would overwrite the save on logout.
            logger.error(e) { "Rejecting $username: their save can't be read" }
            login.responseHandler.writeFailedResponse(LoginResponse.InvalidSave)
            return
        }
        val ok = LoginResponse.Ok(AuthenticatorResponse.NoAuthenticator, 2, false, index, true, index.toLong(), index.toLong(), index.toLong())
        player.loginSeed = login.block.seed.copyOf()
        player.session = login.responseHandler.writeSuccessfulResponse(ok, login.block)
        watchConnection(player)
        player.frame = GameFrame.forLogin(login.block.resizable, player.stoneArrangement)
        players[index] = player
        enterWorld(player)
        logger.info { "${player.username} logged in from ${player.address} as player $index at ${player.x}, ${player.z}, ${player.level}" }
    }

    /**
     * Builds the player logging in as [username] from their save, or a new player in Lumbridge if they have none.
     * Throws if the save can't be read.
     */
    internal fun loadPlayer(index: Int, username: String): Player {
        val player = Player(index, username, SPAWN_X, SPAWN_Z, SPAWN_LEVEL, varTypes, xpRate)
        // A save that couldn't be written is newer than the file, which would hand back what the player had before.
        val text = unwritten[player.saveKey] ?: saves.read(player.saveKey) ?: return player.also {
            it.newAccount = true
            specialAttacks.refill(it)
        }
        PlayerSave.fromJson(text).applyTo(player, inTick = phase != null)
        // An autosave can catch a player lying dead (0 hitpoints, their items still on them); a crash before their
        // respawn would strand them at 0, so they come back whole. The death itself is lost, which the autosave is for.
        if (player.hitpoints <= 0) player.hitpoints = player.skills.baseLevel(Skill.HITPOINTS)
        return player
    }

    /**
     * Saves that couldn't be written, by save key: kept to write again at each autosave, and loaded in place of the
     * file meanwhile. Without them a player whose last save failed would come back with what the file had, items they
     * had since dropped for someone else included.
     */
    private val unwritten = HashMap<String, String>()

    /** [player]'s state as a save has it, the items in the guide prices screen counted as in the inventory. */
    fun snapshot(player: Player): PlayerSave {
        val (inventory, bank) = priceChecker.saved(player)
        return PlayerSave.of(player, inventory, bank)
    }

    /** Writes [player]'s save. Returns false, after logging why, if it couldn't be written; see [unwritten]. */
    internal fun save(player: Player): Boolean {
        val text = try {
            snapshot(player).toJson(player.username)
        } catch (e: Exception) {
            logger.error(e) { "Couldn't save ${player.username}" }
            return false
        }
        return write(player.saveKey, text)
    }

    private fun write(key: String, text: String): Boolean = try {
        saves.write(key, text)
        unwritten.remove(key)
        true
    } catch (e: Exception) {
        unwritten[key] = text
        logger.error(e) { "Couldn't write the save for $key; keeping it in memory to write again" }
        false
    }

    /** Saves that are still only in memory, for tests. */
    internal val unwrittenSaves: Set<String> get() = unwritten.keys

    private fun saveAll() {
        var count = 0
        for (player in players) {
            if (player != null && save(player)) count++
        }
        // Those who left while the saves couldn't be written.
        for ((key, text) in unwritten.toList()) {
            if (findPlayer(key) == null && write(key, text)) count++
        }
        if (count > 0) logger.info { "Saved $count player${if (count == 1) "" else "s"}" }
        if (unwritten.isNotEmpty()) logger.error { "${unwritten.size} save${if (unwritten.size == 1) " is" else "s are"} still only in memory: ${unwritten.keys.sorted()}" }
    }

    /**
     * Checked again as a login is accepted, on the game thread: an `::ipban` typed while the login waited on its password
     * check would otherwise let it in, as the ban only logs out players already in the world.
     */
    private fun refusedForBan(login: PendingLogin): Boolean {
        val address = login.responseHandler.address
        if (!ipBans.isBanned(address)) return false
        logger.info { "Rejecting ${login.block.username} from $address: that address is banned" }
        login.responseHandler.writeFailedResponse(LoginResponse.IPBlocked)
        return true
    }

    private fun findPlayer(saveKey: String): Player? = players.firstOrNull { it?.saveKey == saveKey }

    /** The player in the world under [name], matched the way save names are, or null. */
    fun playerNamed(name: String): Player? = SaveStore.saveKey(name)?.let(::findPlayer)

    /** Everyone in the world, a dropped connection waiting for a reconnect included. */
    val onlinePlayers: List<Player> get() = players.filterNotNull()

    /**
     * Puts a reconnecting client back on its player, who is still in the world. The client proves it is the same one
     * by sending back the ISAAC seed of the connection it lost.
     */
    private fun reconnect(login: PendingLogin, seed: IntArray) {
        val username = login.block.username
        if (refusedForBan(login)) return
        val player = reconnectTarget(username, seed)
        if (player == null) {
            // Inferred: with nobody to reconnect to, live sends the client back to the login screen.
            logger.info { "Refusing a reconnect for $username: no such player waiting, or the seed doesn't match" }
            login.responseHandler.writeFailedResponse(LoginResponse.ConnectFail)
            return
        }
        val old = player.session
        val infos = player.infos
        // RSProt needs all three reset before it writes the reconnect response's player info block.
        infos.playerInfo.onReconnect()
        infos.npcInfo.onReconnect()
        infos.worldEntityInfo.onReconnect()
        player.session = login.responseHandler.writeSuccessfulResponse(LoginResponse.ReconnectOk(infos.playerInfo), login.block, old)
        // Whatever the old connection still had queued can't arrive. Closing it also drops its disconnection hook;
        // if it is still open (a pulled cable the server hasn't noticed yet), RSProt's idle timeout ends it.
        old.clear()
        old.requestClose()
        player.loginSeed = login.block.seed.copyOf()
        player.connectionLostTick = -1
        watchConnection(player)
        resync(player)
        logger.info { "${player.username} reconnected from ${player.address} as player ${player.index}" }
    }

    /** The player a reconnect as [username] with [seed] goes back to, or null if there is none. */
    internal fun reconnectTarget(username: String, seed: IntArray): Player? {
        val key = SaveStore.saveKey(username) ?: return null
        val player = findPlayer(key) ?: return null
        if (player.loggedOut || !player.loginSeed.contentEquals(seed)) return null
        return player
    }

    /** Marks the player's connection lost when its session drops. See [Player.droppedSession]. */
    private fun watchConnection(player: Player) {
        val session = player.session
        session.setDisconnectionHook { player.droppedSession = session }
    }

    /**
     * After a reconnect, sends again what the client may have missed: anything queued for the old connection after
     * it really died never arrived. Every varp the client may hold, every stat, the inventory and the run state go
     * again, and the map is rebuilt around the player. Inferred, not checked against a live reconnect: the client is taken to keep
     * its interfaces over a reconnect, so they aren't reopened.
     */
    internal fun resync(player: Player) {
        player.vars.resendAll()
        player.skills.resendAll()
        player.inventory.resendAll()
        player.equipment.resendAll()
        player.geSent.resendAll()
        player.sentRunEnabled = null
        player.sentRunEnergy = -1
        player.sentWeight = Int.MIN_VALUE
        player.sentCombatLevel = -1
        sendRunState(player)
        sendWeight(player)
        ignoreLists.sendAll(player)
        friendLists.sendAll(player)
        player.buildAreaZoneX = player.x shr 3
        player.buildAreaZoneZ = player.z shr 3
        player.infos.updateRootBuildAreaCenteredOnPlayer(player.x, player.z)
        player.session.queue(RebuildNormalV2(player.buildAreaZoneX, player.buildAreaZoneZ, -1))
        // The ground items and loc changes it missed go again too, as after any rebuild.
        player.zoneView.reset()
    }

    private fun enterWorld(player: Player) {
        val infos = network.infoProtocols.alloc(player.index, OldSchoolClientType.DESKTOP)
        player.infos = infos
        infos.updateRootCoord(player.level, player.x, player.z)
        infos.updateRootBuildAreaCenteredOnPlayer(player.x, player.z)
        val session = player.session
        session.queue(RebuildLoginV2(player.x shr 3, player.z shr 3, -1, infos.playerInfo))

        val appearance = infos.playerInfo.avatar.extendedInfo
        appearance.setName(player.username)
        // Default male look. Guessed, not checked against live. Worn items and the stance go with the first flush.
        DEFAULT_IDENT_KITS.forEachIndexed { slot, kit -> appearance.setIdentKit(slot, kit) }
        for (slot in 0 until 5) appearance.setColour(slot, 0)
        sendRunState(player)
        PlayerOps.send(player.session::queue)
        allowChat(player)
        // Guessed: live sends the saved filters at login; a new account's are all on.
        sendChatFilters(player)
        ignoreLists.sendAll(player)
        friendLists.login(player)
        quests.completeAll(player.vars)
        prayers.login(player)
        if (player.newAccount) allSettings.newAccount(player)
        player.message(textFor(player)[GameLines.WELCOME])

        // The settings go before the interfaces that read them as they load (the side panel, the orbs), rather than
        // with the end-of-tick flush. Inferred from those onload scripts, not seen on live.
        sendVars(player)
        interfaces.openGameFrame(player)
        inventoryTab.open(player)
        if (player.newAccount) {
            // Tutorial Island's end on live: resizable mode and the default zoom, and the frame to match. Nobody here
            // does the tutorial, so a new account gets them at its first login.
            interfaces.runClientScript(player, AllSettings.TUT2_DEFAULT_SETTINGS_SCRIPT, emptyList())
            interfaces.requestFrame(player, GameFrame.RESIZABLE_CLASSIC, tick)
        }
    }

    /**
     * Tells the client the account has a display name. Without it the chatbox refuses all typing, `::` commands
     * included, with "You must set a name before you can chat". Inferred from the varbit's name and that message.
     */
    internal fun allowChat(player: Player) {
        if (varTypes.varbit(VARBIT_HAS_DISPLAYNAME) == null) {
            logger.warn { "No varbit $VARBIT_HAS_DISPLAYNAME in the cache; the client will not let ${player.username} chat" }
            return
        }
        player.vars.setVarbit(VARBIT_HAS_DISPLAYNAME, 1)
    }

    /** A click on the game view or minimap. [keyCombination] is 1 with ctrl held, 2 with ctrl and shift. */
    fun onMoveClick(player: Player, x: Int, z: Int, keyCombination: Int) = act(player) {
        trades.interrupt(player)
        // Inferred: a delayed player can't walk away, as on an agility obstacle.
        if (held(player)) return@act
        scripts.interrupt(player)
        player.interaction = null
        npcInteractions.leave(player)
        if (keyCombination == 2 && admins.isAdmin(player)) {
            // Ctrl+shift-click is a J-Mod teleport on live; here it is a testing tool for admins, with ::tele's map check.
            if (hasMap(x, z)) teleport(player, x, z, player.level) else player.message("No map at $x, $z")
            return@act
        }
        // Inferred: for anyone else the client's ctrl+shift click is an ordinary walk, as on live.
        val route = movement.request(player, x, z, invertRun = keyCombination == 1)
        // Guessed: with no route the client's flag would stay up forever, so clear it.
        if (route.waypoints.isEmpty()) player.session.queue(SetMapFlagV2.RESET)
    }

    /**
     * A click on option [option] (1 to 5) of obj [id] on the ground. The player walks there and the option runs once
     * they reach it; see [GroundObjs]. [ctrl] flips walk and run, as on a move click.
     */
    fun onGroundObjOp(player: Player, id: Int, x: Int, z: Int, option: Int, ctrl: Boolean) = act(player) {
        trades.interrupt(player)
        // Delayed players can't walk off, as for a move click.
        if (held(player)) return@act
        val interaction = groundObjs.click(player, id, x, z, option)
        if (interaction == null) {
            follows.clickedElsewhere(player)
            return@act
        }
        npcInteractions.leave(player)
        scripts.interrupt(player)
        player.interaction = interaction
        val route = movement.request(player, interaction.target, invertRun = ctrl)
        // Guessed, as for a walk: with no route to walk the client's flag would stay up.
        if (route.waypoints.isEmpty()) player.session.queue(SetMapFlagV2.RESET)
    }

    /**
     * A click on option [option] (1 to 8) of the player at [index]. Only Follow does anything yet; see [PlayerOps]. [ctrl]
     * flips walk and run for the walk to them.
     */
    fun onPlayerOp(player: Player, index: Int, option: Int, ctrl: Boolean) {
        // Looked up as the packet is read, and checked again when it runs, so a click held while paused can't land on
        // someone who took the slot meanwhile.
        val clicked = players.getOrNull(index)
        act(player) { playerOp(player, clicked, option, ctrl) }
    }

    /** [clicked] is who held the slot when the packet was read, null if nobody did. */
    private fun playerOp(player: Player, clicked: Player?, option: Int, ctrl: Boolean) {
        // Inferred, as for a walk: a delayed player's clicks do nothing.
        if (held(player)) return
        trades.interrupt(player)
        if (option != PlayerOps.FOLLOW && option != PlayerOps.TRADE && option != PlayerOps.REPORT) {
            logger.info { "${player.username} sent op $option on player ${clicked?.username}, which has no such option" }
            return
        }
        // Any of the options replaces what the player was doing, a follow included. Guessed for Trade with and Report,
        // which aren't built, and for a player who has left: they stop the player where they stand.
        scripts.interrupt(player)
        player.interaction = null
        npcInteractions.leave(player)
        player.waypoints.clear()
        // The client was behind: they left.
        val target = clicked?.takeIf { players[it.index] === it }
        if (target == null) {
            player.session.queue(SetMapFlagV2.RESET)
            return
        }
        when (option) {
            PlayerOps.FOLLOW -> follows.click(player, target, ctrl)
            PlayerOps.TRADE -> {
                interfaces.closeModals(player)
                trades.click(player, target, ctrl)
            }
            else -> {
                // Guessed, as for a walk with nowhere to go: the client's flag would otherwise stay up.
                player.session.queue(SetMapFlagV2.RESET)
                player.message(PlayerOps.REPORT_NOT_BUILT)
            }
        }
    }

    /** Examine on obj [id] on the ground: the same examine as in the inventory. Guessed, not checked against live. */
    fun onGroundObjExamine(player: Player, id: Int, x: Int, z: Int) = act(player) {
        val obj = groundObjs.visibleAt(player, x, z, player.level).firstOrNull { it.id == id } ?: return@act
        val type = objTypes[id] ?: return@act
        inventoryTab.examine(player, type, obj.count)
    }

    /**
     * Runs [op] again for [player] from the next tick, as RS2's `p_oploc`, `p_opobj` and `p_opnpc` do, until something
     * else replaces their interaction: how a gathering action keeps going. Options on locs, objs on the ground and NPCs
     * (fishing spots).
     */
    fun repeatOp(player: Player, op: Op) {
        val loc = op.loc
        val obj = op.groundObj
        val npc = op.npc
        when {
            op.target == OpTarget.LOC && loc != null -> locInteractions.repeat(player, loc, op.option)
            op.target == OpTarget.GROUND_OBJ && obj != null -> groundObjs.repeat(player, obj, op.option)
            op.target == OpTarget.NPC && npc != null -> npcInteractions.repeat(player, npc, op.option)
            else -> throw IllegalArgumentException("Only loc, ground obj and NPC options can repeat, not $op")
        }
    }

    /** A click on option [option] (1 to 5) of loc [id] whose south-west tile is [x], [z]. [ctrl] flips walk and run. */
    fun onLocOp(player: Player, id: Int, x: Int, z: Int, option: Int, ctrl: Boolean) = act(player) {
        trades.interrupt(player)
        // Inferred, as for a walk: a delayed player's clicks do nothing.
        if (held(player)) return@act
        npcInteractions.leave(player)
        locInteractions.click(player, id, x, z, option, ctrl)
        // A loc that had gone by the time the click was read leaves any follow behind; the click still ends it.
        follows.clickedElsewhere(player)
    }

    /**
     * The player used something on loc [id] whose south-west tile is [x], [z]: the item in slot [sub] of
     * [interfaceId]:[componentId], or a spell. Only inventory items are handled; spells on locs come later.
     */
    fun onLocTarget(player: Player, id: Int, x: Int, z: Int, interfaceId: Int, componentId: Int, sub: Int, obj: Int, ctrl: Boolean) = act(player) {
        if (held(player)) return@act
        trades.interrupt(player)
        if (player.scripts.delayed) return@act
        if (interfaceId != InventoryTab.INTERFACE || componentId != InventoryTab.ITEMS) {
            logger.info { "${player.username} used $interfaceId:$componentId sub $sub on loc $id (not handled)" }
            follows.clickedElsewhere(player)
            return@act
        }
        npcInteractions.leave(player)
        locInteractions.useObj(player, id, x, z, sub, obj, ctrl)
        follows.clickedElsewhere(player)
    }

    /**
     * A click on option [option] (1 to 5) of the NPC at [index]; [sub] is the sub-option, -1 for none. [ctrl] flips walk
     * and run.
     */
    fun onNpcOp(player: Player, index: Int, option: Int, sub: Int, ctrl: Boolean) = act(player) {
        trades.interrupt(player)
        // Inferred, as for a walk: a delayed player's clicks do nothing.
        if (held(player)) return@act
        npcInteractions.click(player, index, option, sub, ctrl)
        follows.clickedElsewhere(player)
    }

    /**
     * The player used something on the NPC at [index]: the item in slot [sub] of [interfaceId]:[componentId], or a spell.
     * Only inventory items are handled; spells come with magic.
     */
    fun onNpcTarget(player: Player, index: Int, interfaceId: Int, componentId: Int, sub: Int, obj: Int, ctrl: Boolean) = act(player) {
        if (held(player)) return@act
        trades.interrupt(player)
        if (player.scripts.delayed) return@act
        if (interfaceId == Spells.SPELLBOOK) {
            npcInteractions.castOn(player, index, ctrl) { npc -> magicCombat.cast(player, npc, componentId) }
            follows.clickedElsewhere(player)
            return@act
        }
        if (interfaceId != InventoryTab.INTERFACE || componentId != InventoryTab.ITEMS) {
            logger.info { "${player.username} used $interfaceId:$componentId sub $sub on NPC $index (not handled)" }
            follows.clickedElsewhere(player)
            return@act
        }
        npcInteractions.useObj(player, index, sub, obj, ctrl)
        follows.clickedElsewhere(player)
    }

    /**
     * Examine on loc [id], the type the player sees (after any multiloc change). Sent as an object-examine chat message,
     * type 29 in RuneLite's ChatMessageType. Live's examine texts aren't in the cache: the words are in the dialogue
     * file (see [Examines]), and for a loc not listed there yet the message says what the loc is.
     */
    fun onLocExamine(player: Player, id: Int) = act(player) {
        val line = Examines.loc(id)
        if (line != null) {
            player.session.queue(MessageGame(CHATTYPE_OBJEXAMINE, textFor(player)[line]))
            return@act
        }
        val type = locs.types[id]
        val name = type?.internalName?.let { " $it" }.orEmpty()
        player.session.queue(MessageGame(CHATTYPE_OBJEXAMINE, "${type?.name ?: "Loc"} (loc $id$name): no examine text yet."))
    }

    /** A `::` command typed in the chatbox, without the `::`. Commands run straight away, even while paused. */
    fun onCommand(player: Player, input: String) {
        // A command that changes things (an admin one) leaves a trade first, so nothing changes the inventory under it.
        if (player.trade != null && commands.isAdminCommand(input)) trades.interrupt(player)
        commands.execute(player, input, player::message)
    }

    /** Moves [player] straight to the tile, dropping any route. The client is told at the end of the tick. */
    fun teleport(player: Player, x: Int, z: Int, level: Int): Boolean {
        // A tile off the map would fail the player's info every tick from here on; see [kick].
        if (level !in 0 until LEVELS || x !in 0 until MAP_SIZE || z !in 0 until MAP_SIZE) {
            logger.error(IllegalArgumentException("teleport to $x, $z, $level")) { "Refused to teleport ${player.username} off the map" }
            return false
        }
        player.waypoints.clear()
        player.interaction = null
        npcInteractions.leave(player)
        player.x = x
        player.z = z
        player.level = level
        // Anyone following them heads for the tile west of where they land, as after a login (RSMod).
        player.lastStepFrom = null
        player.teleported = true
        return true
    }

    /** Whether the cache has terrain for the map square holding [x], [z]. */
    fun hasMap(x: Int, z: Int): Boolean = collision[x, z, 0] != CollisionFlag.UNLOADED

    /** Public chat is game input like a click, so while paused it is held for the next tick. */
    fun onPublicChat(player: Player, message: MessagePublic) = act(player) { sayPublicChat(player, message) }

    /** A click on an interface component. [obj] is the item the client showed there, -1 if none. */
    fun onButton(player: Player, interfaceId: Int, componentId: Int, sub: Int, op: Int, obj: Int = -1) = act(player) {
        if (player.echoButtons) player.message("Button $interfaceId:$componentId slot $sub op $op" + if (obj >= 0) " obj $obj" else "")
        if (interfaceId == InventoryTab.INTERFACE && componentId == InventoryTab.ITEMS) {
            inventoryTab.click(player, sub, obj, op)
            return@act
        }
        if (trades.click(player, interfaceId, componentId, sub, obj, op)) return@act
        trades.interrupt(player)
        if (bankScreen.click(player, interfaceId, componentId, sub, obj, op)) return@act
        if (depositBox.click(player, interfaceId, componentId, sub, obj, op)) return@act
        if (bankPinSettings.resume(player, interfaceId, componentId)) return@act
        if (shopScreen.click(player, interfaceId, componentId, sub, obj, op)) return@act
        if (grandExchangeScreen.click(player, interfaceId, componentId, sub, op, obj)) return@act
        if (priceChecker.click(player, interfaceId, componentId, sub, op, obj)) return@act
        if (interfaceId == EquipmentTab.WORNITEMS && componentId == PriceChecker.WORNITEMS_BUTTON) {
            priceChecker.open(player)
            return@act
        }
        if (interfaceId == EquipmentTab.WORNITEMS && equipmentTab.clickWornItems(player, componentId, op)) return@act
        if (equipmentTab.clickStats(player, interfaceId, componentId, sub, obj, op)) return@act
        if (interfaceId == EquipmentTab.COMBAT && componentId == EquipmentTab.COMBAT_SPECIAL || interfaceId == GameFrame.ORBS && componentId == GameFrame.ORBS_SPEC_BUTTON) {
            specialAttacks.toggle(player, combat.weapon(player))
            return@act
        }
        if (interfaceId == EquipmentTab.COMBAT) {
            val wasBlock = combat.bulwarkBlock(player)
            if (equipmentTab.clickCombatTab(player, componentId)) {
                // Off the bulwark's Block, the next attack waits 8 ticks, whatever weapon makes it (OSRS Wiki, Dinh's bulwark).
                if (wasBlock && !combat.bulwarkBlock(player)) {
                    player.attackLockedUntil = tick + DinhsBulwark.BLOCK_DELAY
                    player.nextAttackTick = maxOf(player.nextAttackTick, player.attackLockedUntil)
                }
                return@act
            }
        }
        if (interfaceId == EquipmentTab.AUTOCAST && equipmentTab.clickAutocastMenu(player, componentId, sub)) return@act
        if (interfaceId == Prayers.PRAYERBOOK && prayers.click(player, componentId, op)) return@act
        if (interfaceId == GameFrame.ORBS && componentId == GameFrame.ORBS_RUN_BUTTON) {
            player.runEnabled = !player.runEnabled
            return@act
        }
        if (interfaceId == GameFrame.ORBS && componentId == GameFrame.ORBS_PRAYER_BUTTON) {
            when (op) {
                1 -> prayers.toggleQuick(player)
                2 -> openQuickPrayerSetup(player)
            }
            return@act
        }
        if (interfaceId == Prayers.QUICKPRAYER && clickQuickPrayerSetup(player, componentId, sub)) return@act
        if (interfaceId == GameFrame.SETTINGS_SIDE && componentId == GameFrame.SETTINGS_CLIENT_LAYOUT_BUTTONS) {
            val frame = GameFrame.forLayoutOption(sub)
            if (frame != null) interfaces.requestFrame(player, frame, tick)
            return@act
        }
        if (interfaceId == SettingsSide.INTERFACE && componentId == SettingsSide.SETTINGS_OPEN) {
            allSettings.open(player)
            return@act
        }
        if (interfaceId == SettingsSide.INTERFACE && settingsSide.click(player, componentId, sub, op)) return@act
        if (interfaceId == AllSettings.INTERFACE && allSettings.click(player, componentId, sub, op, tick)) return@act
        if (interfaceId == JournalTab.SIDE_JOURNAL) {
            val tab = JournalTab.forButton(componentId)
            if (tab != null) {
                interfaces.openJournalTab(player, tab)
                return@act
            }
        }
        // The Friends tab and the ignore tab swap places in the side panel (RSMod opens them the same way).
        if (interfaceId == IgnoreLists.FRIENDS_TAB && componentId == IgnoreLists.FRIENDS_TAB_IGNORE_BUTTON) {
            interfaces.openSide(player, FrameSlot.SIDE9, IgnoreLists.IGNORE_TAB)
            return@act
        }
        if (interfaceId == IgnoreLists.IGNORE_TAB && componentId == IgnoreLists.IGNORE_TAB_FRIENDS_BUTTON) {
            interfaces.openSide(player, FrameSlot.SIDE9, IgnoreLists.FRIENDS_TAB)
            return@act
        }
        if (interfaceId == LOGOUT_TAB && componentId == LOGOUT_BUTTON) {
            if (inCombat(player)) {
                player.message(textFor(player)[GameLines.CANT_LOG_OUT])
                return@act
            }
            interfaces.closeModals(player)
            player.logoutRequested = true
            return@act
        }
        if (scripts.trigger(player, Op.button(interfaceId, componentId, op, sub))) return@act
        logger.info { "${player.username} clicked $interfaceId:$componentId sub $sub op $op (not handled)" }
    }

    /**
     * A client script told the server to run its server-side half (IF_SCRIPT_TRIGGER) on [componentId] of [interfaceId],
     * with [args] decoded by the types [SettingsSide.SCRIPT_TRIGGERS] or [AllSettings.SCRIPT_TRIGGERS] give for its id.
     */
    fun onScriptTrigger(player: Player, interfaceId: Int, componentId: Int, sub: Int, args: List<Any>) = act(player) {
        trades.interrupt(player)
        if (player.echoButtons) player.message("Script trigger $interfaceId:$componentId slot $sub args ${args.toList()}")
        if (interfaceId == SettingsSide.INTERFACE && settingsSide.scriptTrigger(player, componentId, args)) return@act
        if (interfaceId == AllSettings.INTERFACE && allSettings.scriptTrigger(player, componentId, sub, args)) return@act
        logger.info { "${player.username} triggered a script on $interfaceId:$componentId sub $sub args $args (not handled)" }
    }

    /** A script trigger whose script or arguments the server can't read: logged, and echoed with `::buttons` so a test sees it arrived. */
    fun onUnreadScriptTrigger(player: Player, interfaceId: Int, componentId: Int, sub: Int, script: Int, problem: String) = act(player) {
        if (player.echoButtons) player.message("Script trigger $interfaceId:$componentId slot $sub script ${script.toUInt()} $problem")
        logger.info { "${player.username} triggered script ${script.toUInt()} on $interfaceId:$componentId sub $sub, which $problem" }
    }

    /** The player dragged a component's item, from slot [fromSub] onto [toComponentId]'s slot [toSub]. */
    fun onButtonDrag(player: Player, interfaceId: Int, componentId: Int, fromSub: Int, fromObj: Int, toInterfaceId: Int, toComponentId: Int, toSub: Int) = act(player) {
        trades.interrupt(player)
        val inventory = interfaceId == InventoryTab.INTERFACE && componentId == InventoryTab.ITEMS
        if (inventory && toInterfaceId == interfaceId && toComponentId == componentId) {
            inventoryTab.drag(player, fromSub, fromObj, toSub)
            return@act
        }
        if (bankScreen.drag(player, interfaceId, componentId, fromSub, fromObj, toInterfaceId, toComponentId, toSub)) return@act
        if (depositBox.drag(player, interfaceId, componentId, fromSub, fromObj, toInterfaceId, toComponentId, toSub)) return@act
        logger.info { "${player.username} dragged $interfaceId:$componentId sub $fromSub onto $toInterfaceId:$toComponentId sub $toSub (not handled)" }
    }

    /** The player used one component's item on another's ("Use" then a click). */
    fun onButtonTarget(player: Player, interfaceId: Int, componentId: Int, fromSub: Int, fromObj: Int, toInterfaceId: Int, toComponentId: Int, toSub: Int, toObj: Int) = act(player) {
        trades.interrupt(player)
        val fromInventory = interfaceId == InventoryTab.INTERFACE && componentId == InventoryTab.ITEMS
        if (fromInventory && toInterfaceId == interfaceId && toComponentId == componentId) {
            inventoryTab.use(player, fromSub, fromObj, toSub, toObj)
            return@act
        }
        logger.info { "${player.username} used $interfaceId:$componentId sub $fromSub on $toInterfaceId:$toComponentId sub $toSub (not handled)" }
    }

    /**
     * Examine on an NPC of type [id], its words from the dialogue file (see [Examines]). Inferred: live answers with a
     * game message of chat type 28, which RuneLite's ChatMessageType calls NPC_EXAMINE; not checked against a recording.
     */
    fun onNpcExamine(player: Player, id: Int) = act(player) {
        val line = Examines.npc(id)
        if (line != null) {
            player.session.queue(MessageGame(CHATTYPE_NPC_EXAMINE, textFor(player)[line]))
            return@act
        }
        // Not live behaviour: tells the tester the NPC isn't in examines.json.
        val name = npcTypes[id]?.name ?: "NPC"
        player.message("No examine text for $name ($id) yet.")
    }

    private fun spawnNpcs() {
        var spawned = 0
        for (spawn in npcSpawns) {
            if (spawnNpc(spawn) != null) spawned++
        }
        if (npcSpawns.isNotEmpty()) logger.info { "Spawned $spawned of ${npcSpawns.size} NPCs" }
    }

    /** Puts an NPC in the world. Returns null, after logging why, if there is no room for it. */
    fun spawnNpc(spawn: NpcSpawn): Npc? {
        val npc = npcs.add(spawn, tick)
        if (npc == null) {
            logger.warn { "No free NPC slot for ${spawn.type} from ${spawn.source}" }
            return null
        }
        // No network in unit tests: the NPC then exists without being sent to anyone.
        if (this::network.isInitialized) {
            npc.avatar = network.npcAvatarFactory.alloc(npc.index, spawn.type.id, npc.level, npc.x, npc.z, direction = spawn.direction.id)
        }
        return npc
    }

    /**
     * Rolls [npc]'s drops and puts them on its tile: the killer's alone for [GroundObjs.DROP_PRIVATE_TICKS], then
     * everyone's, gone after [GroundObjs.LOOT_DESPAWN_TICKS] (OSRS Wiki, Drop). With no killer, or one who has left,
     * they are everyone's from the start (guessed).
     */
    fun dropLoot(npc: Npc, killer: Player?) {
        val def = npcDrops[npc.type] ?: return
        val owner = killer?.takeIf { !it.loggedOut }?.saveKey
        for (drop in def.roll(random)) {
            if (owner != null) {
                groundObjs.addPrivate(drop.type, drop.count, npc.x, npc.z, npc.level, owner, GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.LOOT_DESPAWN_TICKS)
            } else {
                groundObjs.addPublic(drop.type, drop.count, npc.x, npc.z, npc.level, GroundObjs.LOOT_DESPAWN_TICKS)
            }
        }
    }

    /**
     * Puts a fired arrow or bolt down under its target for [owner] alone for [GroundObjs.DROP_PRIVATE_TICKS], gone after
     * [GroundObjs.DROP_DESPAWN_TICKS]; everyone's from the start when they have left. Guessed: the Wiki gives no timers
     * for fired ammunition, so these are a drop's.
     */
    /** Drops [count] of [type] under [player], theirs alone at first, as an item dropped from the backpack is. */
    private fun dropUnder(player: Player, type: ObjType, count: Int) {
        val stacks = if (type.stackable) listOf(count) else List(count) { 1 }
        for (n in stacks) {
            groundObjs.addPrivate(type, n, player.x, player.z, player.level, player.saveKey, GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.DROP_DESPAWN_TICKS)
        }
    }

    private fun dropAmmo(type: ObjType, x: Int, z: Int, level: Int, owner: Player) {
        // Lost in water: the Wiki's Duck page, for ducks off land, taken to hold on any floor that can't be walked on.
        if (collision[x, z, level] and CollisionFlag.FLOOR != 0) return
        if (owner.loggedOut) {
            groundObjs.addPublic(type, 1, x, z, level, GroundObjs.DROP_DESPAWN_TICKS)
        } else {
            groundObjs.addPrivate(type, 1, x, z, level, owner.saveKey, GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.DROP_DESPAWN_TICKS)
        }
    }

    fun removeNpc(npc: Npc) {
        // Players walking to it give up in their next movement phase; see NpcInteractions.
        npcs.remove(npc)
        npcInteractions.removed(npc, players.asIterable())
        npc.avatar?.let(network.npcAvatarFactory::release)
        npc.avatar = null
    }

    /**
     * The player clicked a dialogue's "Click here to continue" or a chat menu option: [sub] is -1, or the option from 1.
     * See [Dialogues.resume]. A click while delayed is dropped, as other clicks are.
     */
    fun onResumePauseButton(player: Player, interfaceId: Int, componentId: Int, sub: Int) = act(player) {
        trades.interrupt(player)
        if (player.scripts.delayed) return@act
        if (!dialogues.resume(player, interfaceId, componentId, sub) && !bankPinSettings.resume(player, interfaceId, componentId)) {
            logger.debug { "${player.username} continued $interfaceId:$componentId sub $sub, which nothing waits for" }
        }
    }

    /**
     * The player typed an amount into a prompt (resume_p_countdialog). A script waiting for it carries on now; with none
     * waiting (the prompt was closed) it does nothing. Dropped while delayed, as other clicks are.
     */
    /** The client picked [obj] in the chatbox's item search (resume_p_objdialog). */
    fun onResumeObj(player: Player, obj: Int) = act(player) {
        if (!grandExchangeScreen.chooseItem(player, obj) && !priceChecker.chooseItem(player, obj)) logger.debug { "${player.username} picked item $obj, which nothing waits for" }
    }

    fun onResumeCount(player: Player, count: Int) = act(player) {
        if (player.scripts.delayed) return@act
        if (!scripts.resumeCount(player, count)) logger.debug { "${player.username} entered $count, which nothing waits for" }
    }

    /**
     * The player changed a chatbox tab's filter. Inferred: the tab's label changes only when the server sends the
     * filters back. They are saved and sent again at login; nothing filters chat on the server yet.
     */
    fun onChatFilters(player: Player, filters: ChatFilters) = act(player) {
        val old = player.chatFilters.private
        player.chatFilters = filters
        sendChatFilters(player)
        friendLists.privateFilterChanged(player, old)
    }

    /** The player added [name] to their friends list. Kept while dead: the death animation doesn't close the tab. */
    fun onFriendAdd(player: Player, name: String) = actWhileDead(player) { friendLists.add(player, name) }

    /** The player deleted [name] from their friends list, which their client has already done. Kept while dead. */
    fun onFriendDelete(player: Player, name: String) = actWhileDead(player) { friendLists.delete(player, name) }

    /** The player sent [name] a private message. Guessed: kept while dead, as the side panel's lists are. */
    fun onPrivateMessage(player: Player, name: String, text: String) = actWhileDead(player) { privateMessages.send(player, name, text) }

    /** The player added [name] to their ignore list. Kept while dead, as friends are. */
    fun onIgnoreAdd(player: Player, name: String) = actWhileDead(player) { ignoreLists.add(player, name) }

    /** The player deleted [name] from their ignore list, which their client has already done. Kept while dead, as friends are. */
    fun onIgnoreDelete(player: Player, name: String) = actWhileDead(player) { ignoreLists.delete(player, name) }

    /**
     * The display name of the account [name] is: the player's if they are here, else the one in their save. Null when
     * there is no such account, or no save to tell.
     */
    internal fun accountName(name: String): String? {
        val key = SaveStore.saveKey(name) ?: return null
        findPlayer(key)?.let { return it.username }
        val text = unwritten[key] ?: try {
            saves.read(key)
        } catch (e: Exception) {
            logger.error(e) { "Couldn't read the save for $key" }
            null
        } ?: return null
        return PlayerSave.usernameIn(text) ?: name.trim()
    }

    private fun sendChatFilters(player: Player) {
        val filters = player.chatFilters
        player.session.queue(ChatFilterSettings(filters.public, filters.trade))
        player.session.queue(ChatFilterSettingsPrivateChat(filters.private))
    }

    /** The client closed its modal: Esc, or a close button. */
    fun onCloseModal(player: Player) = act(player) { scripts.interrupt(player) }

    fun interfaceExists(interfaceId: Int): Boolean = interfaceIds?.contains(interfaceId) ?: (interfaceId >= 0)

    fun openModal(player: Player, slot: FrameSlot, interfaceId: Int) = interfaces.openModal(player, slot, interfaceId)

    private fun move(player: Player) {
        val result = movement.process(player)
        if (result != MoveResult.NONE) player.lastMoveTick = tick
        val extendedInfo = player.infos.playerInfo.avatar.extendedInfo
        val speed = when (result) {
            MoveResult.NONE -> null
            MoveResult.WALKED -> MOVE_SPEED_WALK
            MoveResult.RAN -> MOVE_SPEED_RUN
        }
        if (player.teleported) {
            finishTeleport(player)
        } else if (speed != null && speed != moveSpeed(player)) {
            // Inferred: a tick moved at a speed other than the orb's (ctrl-click, the last odd tile of a run) is sent
            // as a one-tick temporary speed so the client animates it right.
            extendedInfo.setTempMoveSpeed(speed)
        }
        sendRunState(player)
        rebuildIfNeeded(player)
    }

    /**
     * A teleport is sent as the one-tick teleport speed so the client jumps instead of walking there, and the old route's
     * flag is cleared. Clearing the flag is guessed, not checked against live.
     */
    private fun finishTeleport(player: Player) {
        player.infos.playerInfo.avatar.extendedInfo.setTempMoveSpeed(MOVE_SPEED_TELEPORT)
        player.session.queue(SetMapFlagV2.RESET)
        player.teleported = false
    }

    private fun moveSpeed(player: Player) = if (player.runEnabled) MOVE_SPEED_RUN else MOVE_SPEED_WALK

    /**
     * Works out what [player] carries for this tick's run energy, and tells the client when the whole kilograms change
     * (UPDATE_RUNWEIGHT, which the Equipment Stats screen and RuneLite show). Inferred: live sends it on a change only.
     */
    private fun sendWeight(player: Player) {
        player.weightGrams = itemWeights.total(player)
        val kilograms = ItemWeights.kilograms(player.weightGrams)
        if (kilograms != player.sentWeight) {
            player.session.queue(UpdateRunWeight(kilograms))
            player.sentWeight = kilograms
        }
    }

    /** Sends the move speed that goes with the run orb, and the run energy, when they change. The orb is a varp. */
    private fun sendRunState(player: Player) {
        if (player.runEnabled != player.sentRunEnabled) {
            player.infos.playerInfo.avatar.extendedInfo.setMoveSpeed(moveSpeed(player))
            player.sentRunEnabled = player.runEnabled
        }
        if (player.runEnergy != player.sentRunEnergy) {
            player.session.queue(UpdateRunEnergy(player.runEnergy))
            player.sentRunEnergy = player.runEnergy
        }
    }

    /**
     * Turns the player to whoever they follow, and stops turning them once they don't, when that changes. Walk mode 0:
     * they look where they walk and turn back to the target once they stop. Inferred from RSProt's docs and RSMod, not
     * checked against live. Only in the movement phase: a face set later in the cycle (a loc's) must not be undone.
     */
    private fun sendFace(player: Player) = follows.updateFace(player) { target ->
        val extendedInfo = player.infos.playerInfo.avatar.extendedInfo
        if (target == null) {
            extendedInfo.resetFacing()
        } else {
            extendedInfo.setFacePlayer(target.index, false, 0, Follows.angle(player.x, player.z, target.x, target.z))
        }
    }

    /**
     * Sends the varps that changed this tick. Varps start at 0 on both sides at login, so only changes are sent; live
     * may also send zeros at login, which isn't checked.
     */
    private fun sendVars(player: Player) {
        player.vars.flush { varp, value ->
            // Same split as the client's two packets: a value that fits a signed byte goes in the small one.
            player.session.queue(if (value in Byte.MIN_VALUE..Byte.MAX_VALUE) VarpSmall(varp, value) else VarpLarge(varp, value))
        }
    }

    /**
     * Sends the skills that changed this cycle, and the combat level over player info when it changes. Every skill is
     * sent on the first cycle, as the client starts them all at 0.
     */
    private fun sendStats(player: Player) {
        player.skills.flush { skill, currentLevel, xp ->
            // Guessed: the invisible boost is the current level unless something hides a boost, which nothing does yet.
            player.session.queue(UpdateStatV2(skill.id, currentLevel, currentLevel, xp))
        }
        val combatLevel = player.skills.combatLevel
        if (combatLevel != player.sentCombatLevel) {
            player.infos.playerInfo.avatar.extendedInfo.setCombatLevel(combatLevel)
            player.sentCombatLevel = combatLevel
        }
    }

    /**
     * Sends the animation, spotanims, sound effects and jingle set on [player] this cycle: the first two through player
     * info for everyone who sees them, the rest to the player. Also on paused cycles, so a ::anim typed while paused
     * plays straight away.
     */
    private fun sendEffects(player: Player) {
        val extendedInfo = player.infos.playerInfo.avatar.extendedInfo
        player.effects.flush(
            anim = { extendedInfo.setSequence(it.id, it.delay) },
            spotanim = { slot, spot -> extendedInfo.setSpotAnim(slot, spot.id, spot.delay, spot.height) },
            sound = { player.session.queue(SynthSound(it.id, it.loops, it.delay)) },
            // The length is no longer read by the client (RSProt's MidiJingle), so it is left at 0.
            jingle = { player.session.queue(MidiJingle(it)) },
            hit = { extendedInfo.addHitMark(it.source, it.selfType, it.sourceType, it.otherType, it.value, it.delay) },
            headBar = { extendedInfo.addHeadBar(it.source, it.type, it.type, it.fill) },
        )
    }

    /** Sends an NPC's animation, hitsplats, health bars and overhead text through its avatar; see [NpcEffects]. */
    private fun sendNpcEffects(npc: Npc) {
        val extendedInfo = npc.avatar?.extendedInfo
        npc.effects.flush(
            anim = { extendedInfo?.setSequence(it.id, it.delay) },
            hit = { extendedInfo?.addHitMark(it.source, it.selfType, it.otherType, it.value, it.delay) },
            headBar = { extendedInfo?.addHeadBar(it.source, it.type, it.type, it.fill) },
            spotanim = { extendedInfo?.setSpotAnim(it.slot, it.id, it.delay, it.height) },
            say = { extendedInfo?.setSay(it) },
        )
    }

    /**
     * Rebuilds the client's 104x104 build area around the player once they come within 16 tiles of its edge.
     * The 16-tile margin is inferred from the client's scene size, not measured on live.
     */
    private fun rebuildIfNeeded(player: Player) {
        val originX = (player.buildAreaZoneX - 6) shl 3
        val originZ = (player.buildAreaZoneZ - 6) shl 3
        val localX = player.x - originX
        val localZ = player.z - originZ
        if (localX in REBUILD_MARGIN until BUILD_AREA_SIZE - REBUILD_MARGIN &&
            localZ in REBUILD_MARGIN until BUILD_AREA_SIZE - REBUILD_MARGIN
        ) return
        player.buildAreaZoneX = player.x shr 3
        player.buildAreaZoneZ = player.z shr 3
        player.infos.updateRootBuildAreaCenteredOnPlayer(player.x, player.z)
        player.session.queue(RebuildNormalV2(player.buildAreaZoneX, player.buildAreaZoneZ, -1))
        // Guessed safe rather than checked: the client may carry zones that stay in view across a rebuild, but sending
        // them all again in full is right either way.
        player.zoneView.reset()
    }

    private fun sendInfo(player: Player, tickEnd: Boolean) {
        val session = player.session
        val packets = player.infos.getPackets().rootWorldInfoPackets
        session.queue(packets.activeWorld)
        session.queue(packets.npcUpdateOrigin)
        packets.worldEntityInfo.onSuccess(session::queue).onFailure { logger.error(it) { "World entity info failed for ${player.username}" } }
        packets.playerInfo.onSuccess(session::queue).onFailure { logger.error(it) { "Player info failed for ${player.username}" } }
        val npcInfo = packets.npcInfo
        if (npcInfo.isEmpty()) npcInfo.safeReleaseOrThrow() else npcInfo.onSuccess(session::queue)
        // After the infos, as RSProt's guide orders them. Also on paused cycles, so a ::gfx typed while paused shows.
        zones.update(player, session::queue)
        // Live closes every tick with ServerTickEnd, and RuneLite fires its GameTick (and replays deferred events such
        // as VarbitChanged) only on it, so without it the client never sees a tick. A paused cycle is not a tick.
        if (tickEnd) session.queue(ServerTickEnd)
        if (player.logoutRequested && !player.logoutForced && inCombat(player)) {
            // Hit later in the tick that the button was clicked in: the NPC phase comes after input.
            player.logoutRequested = false
            player.message(textFor(player)[GameLines.CANT_LOG_OUT])
        }
        if (player.logoutRequested || player.logoutForced) {
            // Sends the client back to the login screen. requestClose flushes and drops our disconnection hook, so
            // the player is removed on the next tick by hand.
            session.queue(Logout)
            session.requestClose()
            player.loggedOut = true
            return
        }
        session.flush()
    }

    /**
     * The prayer orb's Setup: the quick prayer setup in the prayer book's place, its prayers (one client-made child per
     * bit, laid out by its onload script quickprayer_init) clickable, and the prayer tab shown. As RSMod opens it.
     */
    private fun openQuickPrayerSetup(player: Player) {
        interfaces.openSide(player, FrameSlot.SIDE5, Prayers.QUICKPRAYER)
        interfaces.setEvents(player, Prayers.QUICKPRAYER, Prayers.QUICKPRAYER_BUTTONS, 0, Prayers.ALL_BITS.countOneBits() - 1, Interfaces.IF_EVENT_OP1)
        interfaces.switchSideTab(player, GameFrame.SIDE_TAB_PRAYER)
    }

    /** A click in the quick prayer setup: a prayer, by its bit, or Done, which puts the prayer book back. */
    private fun clickQuickPrayerSetup(player: Player, component: Int, sub: Int): Boolean = when (component) {
        Prayers.QUICKPRAYER_BUTTONS -> prayers.selectQuick(player, sub)
        Prayers.QUICKPRAYER_CLOSE -> {
            interfaces.openSide(player, FrameSlot.SIDE5, Prayers.PRAYERBOOK)
            true
        }
        else -> false
    }

    /** Whether [player] was hit within the last [LOGOUT_COMBAT_TICKS], so can't log out. */
    private fun inCombat(player: Player): Boolean = player.attackedTick >= 0 && tick - player.attackedTick < LOGOUT_COMBAT_TICKS

    /** Saves and removes a player who logged out or whose wait for a reconnect is over. */
    private fun remove(player: Player) {
        try {
            // Nobody is saved dead: a leaver's death finishes now, grave and all.
            if (player.dead) deaths.respawn(player)
            trades.leave(player)
            npcs.forget(player, scripts::busy)
            save(player)
        } finally {
            // Whatever failed above, the slot is freed, or the removal would fail again every cycle.
            players[player.index] = null
            freedSlots[player.index] = cycles
            network.infoProtocols.dealloc(player.infos)
            player.session.clear()
        }
        // Once they are out of the world, so they show as offline.
        try {
            friendLists.changed(player)
        } catch (e: Exception) {
            logger.error(e) { "Couldn't tell ${player.username}'s friends they left" }
        }
        logger.info { "${player.username} logged out" + if (player.loggedOut) "" else " (connection lost)" }
    }

    companion object {
        const val TICK_MILLIS = 600L
        const val MAX_PLAYERS = 2048

        /** Levels are 0 to 3, and x and z 0 to 16383 (the client's 14 bits). */
        const val LEVELS = 4
        const val MAP_SIZE = 1 shl 14

        /** The logout tab and its "Click here to logout" button, logout:logout. */
        const val LOGOUT_TAB = 182
        const val LOGOUT_BUTTON = 8

        /** The logout button does nothing until this many ticks after the player was last hit (OSRS Wiki, Logout button). */
        const val LOGOUT_COMBAT_TICKS = 16L

        /**
         * How long a player whose connection dropped stays in the world for the client to reconnect: 60 s, the
         * wiki's "60 second timer before the game forcibly logs you out after an unexpected disconnection" (Logout
         * button). The wiki gives it for combat; using it outside combat too is inferred.
         */
        const val LINGER_TICKS = 100L

        /** 3 minutes of cycles. */
        const val AUTOSAVE_CYCLES = 300L

        /** How long the game may go without ending a cycle before [isUp] calls it down: 10 s. Our own figure. */
        const val STALL_MILLIS = 10_000L

        /** How long an emptied slot is kept from logins while others are free: 10 cycles (6 s). Guessed. */
        const val SLOT_HOLD_CYCLES = 10L

        /**
         * Logins at most waiting on a password check; more are refused with "too many login attempts". At tens of ms a
         * hash, 32 is a second or two of work. Our own figure.
         */
        const val MAX_AUTH_WAITING = 32

        /** Guessed bound on clicks held per player while paused, so a long pause can't pile them up without end. */
        const val MAX_HELD_ACTIONS = 50

        const val MOVE_SPEED_WALK = 1
        const val MOVE_SPEED_RUN = 2
        const val MOVE_SPEED_TELEPORT = 127

        /** option_run: the run orb's on/off state. */
        const val VARP_RUN = 173

        const val BUILD_AREA_SIZE = 104
        const val REBUILD_MARGIN = 16

        // Lumbridge castle courtyard.
        const val SPAWN_X = 3222
        const val SPAWN_Z = 3218
        const val SPAWN_LEVEL = 0

        /** OBJECT_EXAMINE in RuneLite's ChatMessageType. */
        const val CHATTYPE_OBJEXAMINE = 29
        /** RuneLite's ChatMessageType.NPC_EXAMINE. */
        const val CHATTYPE_NPC_EXAMINE = 28

        /** has_displayname_transmitter in RuneLite's VarbitID. */
        const val VARBIT_HAS_DISPLAYNAME = 8119

        private val DEFAULT_IDENT_KITS = intArrayOf(0, 10, 18, 26, 33, 36, 42)
    }
}

/** The player's real address: with a relay's PROXY header, the one it names, else the connection's own. */
private val GameLoginResponseHandler<*>.address: String get() = ctx.hostAddress()
