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
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.SetMapFlagV2
import net.rsprot.protocol.game.outgoing.misc.player.UpdateRunEnergy
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
import osrsps.game.command.CommandRegistry
import osrsps.game.command.registerDevCommands
import osrsps.game.command.registerEffectCommands
import osrsps.game.command.registerGroundObjCommands
import osrsps.game.command.registerItemCommands
import osrsps.game.command.registerLocCommands
import osrsps.game.command.registerNpcCommands
import osrsps.game.command.registerScenarioCommands
import osrsps.game.command.registerScriptCommands
import osrsps.game.command.registerSeedCommands
import osrsps.game.command.registerSkillCommands
import osrsps.game.command.registerTickCommands
import osrsps.game.command.registerVarCommands
import osrsps.game.command.registerZoneCommands
import osrsps.game.loc.Doors
import osrsps.game.loc.LocInteractions
import osrsps.game.loc.LocTypes
import osrsps.game.loc.Locs
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcExamines
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.ScriptRunner
import osrsps.game.zone.Zones
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    val objTypes: ObjTypes = ObjTypes.NONE,
    val npcTypes: NpcTypes = NpcTypes.NONE,
    /** The NPCs put in the world when it starts. */
    private val npcSpawns: List<NpcSpawn> = emptyList(),
    private val npcExamines: NpcExamines = NpcExamines.NONE,
    /** The animation, spotanim, sound and jingle ids the dev commands accept. */
    val effectIds: EffectIds = EffectIds.UNKNOWN,
    locTypes: LocTypes = LocTypes.NONE,
    /** The locs the cache's maps place. */
    staticLocs: StaticLocs = StaticLocs(),
    /** What `::scenario` can start. */
    val scenarios: Scenarios = Scenarios.NONE,
) : GameConnectionHandler<Player> {
    lateinit var network: NetworkService<Player>

    private val movement = Movement(collision)
    private val interfaces = Interfaces()

    // Before commands: the tick commands keep hold of it.
    val clock = TickClock()

    /** Content handlers for clicks, by what was clicked and which option. */
    val handlers = Handlers()

    /** Locs, ground objs, graphics, projectiles and sounds, sent to the players near them. */
    val zones = Zones()

    /** Every loc in the world now; changing one updates collision and the zones. */
    val locs = Locs(locTypes, staticLocs, collision, zones) { tick }

    val doors = Doors(locs)

    val npcs = Npcs(collision, random)

    val scripts = ScriptRunner(handlers, interfaces::closeModals) { tick }

    /** Items on the ground: drops, takes, their timers and item spawns. */
    val groundObjs = GroundObjs(zones, objTypes, scripts, collision, { findPlayer(it) != null }, { players.count { it != null } })

    private val equipment = Equipment(objTypes)

    private val inventoryTab: InventoryTab = InventoryTab(
        objTypes, scripts, interfaces::closeModals,
        wear = { player, slot -> equipmentTab.wear(player, slot) },
        onDrop = groundObjs::drop,
    )

    private val equipmentTab = EquipmentTab(equipment, objTypes, scripts, inventoryTab, interfaces::openModal)

    private val interactions = Interactions()

    private val locInteractions = LocInteractions(locs, collision, movement, handlers, scripts, doors)

    val commands = CommandRegistry().apply {
        registerDevCommands(this@World)
        registerTickCommands(this@World)
        registerVarCommands()
        registerSkillCommands()
        registerSeedCommands(this@World)
        registerScriptCommands(this@World)
        registerZoneCommands(this@World)
        registerEffectCommands(this@World)
        registerItemCommands(this@World)
        registerGroundObjCommands(this@World)
        registerLocCommands(this@World)
        registerNpcCommands(this@World)
        registerScenarioCommands(this@World)
    }

    /** The phase of the tick being run, or null between ticks and on paused cycles. */
    var phase: TickPhase? = null
        private set

    /** Who to tell when the last tick of a ::step has run. */
    private var stepper: Player? = null

    private val pendingLogins = ConcurrentLinkedQueue<PendingLogin>()
    private val players = arrayOfNulls<Player>(MAX_PLAYERS)
    private val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "game") }
    var tick: Long = 0
        private set

    /** Cycles run, paused ones included, for saving every few minutes of real time. */
    private var cycles: Long = 0

    /** A login or, with [reconnectSeed], a reconnect: the ISAAC seed the client says its last connection used. */
    private class PendingLogin(
        val responseHandler: GameLoginResponseHandler<Player>,
        val block: LoginBlock<*>,
        val reconnectSeed: IntArray? = null,
    )

    override fun onLogin(responseHandler: GameLoginResponseHandler<Player>, block: LoginBlock<AuthenticationType>) {
        block.authentication.clear()
        // RSProt's per-address limit; without it one host could queue logins without bound.
        if (!responseHandler.validateNewConnection()) {
            logger.info { "Rejecting ${block.username}: too many connections from one address" }
            responseHandler.writeFailedResponse(LoginResponse.IPLimit)
            return
        }
        if (!block.crc.validate(js5Crcs)) {
            logger.info { "Rejecting ${block.username}: client cache CRCs do not match ours" }
            responseHandler.writeFailedResponse(LoginResponse.OutOfDateReload)
            return
        }
        pendingLogins.add(PendingLogin(responseHandler, block))
    }

    override fun onReconnect(responseHandler: GameLoginResponseHandler<Player>, block: LoginBlock<XteaKey>) {
        // The same per-address limit as a login, so reconnects can't queue without bound either. The old connection may
        // still count against it until it times out, which the limit of 10 leaves room for.
        if (!responseHandler.validateNewConnection()) {
            logger.info { "Rejecting a reconnect for ${block.username}: too many connections from one address" }
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
        executor.execute {
            network.setCommunicationThread(Thread.currentThread(), true)
            // On the game thread: RSProt hands out NPC avatars only there.
            spawnNpcs()
        }
        executor.scheduleAtFixedRate(::safeCycle, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS)
    }

    /** Stops the cycle and saves everyone still in the world. */
    fun stop() {
        executor.shutdown()
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            // Saving now would race the game thread for the players and the save files; the last autosave stands.
            logger.error { "The game thread didn't stop in 5 s; not saving on shutdown" }
            return
        }
        // Once the executor has stopped this thread is the only one touching the players.
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
    }

    private fun tick() {
        tick++
        phase = TickPhase.WORLD
        groundObjs.process()
        phase = TickPhase.INPUT
        readInput(ticking = true)
        phase = TickPhase.NPCS
        npcs.process()
        phase = TickPhase.SCRIPTS
        for (player in players) {
            if (player != null) scripts.process(player)
        }
        phase = TickPhase.MOVEMENT
        // Guessed place in the tick: before anyone walks, so a door that closes itself blocks this tick's steps.
        locs.process()
        for (player in players) {
            if (player == null) continue
            interfaces.processFrameMove(player, tick)
            interactions.process(player) { move(player) }
            // A loc or obj used after the player's step (a ladder on arrival) can teleport them once move() has run.
            if (player.teleported) {
                finishTeleport(player)
                rebuildIfNeeded(player)
            }
            player.infos.updateRootCoord(player.level, player.x, player.z)
        }
        phase = TickPhase.UPDATES
        if (clock.paused && clock.stepsLeft == 0) finishStepping()
        sendUpdates(tickEnd = true)
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
            if (player != null && (player.loggedOut || (player.connectionLost && lingerOver(player)))) remove(player)
        }
        acceptLogins()
        for (player in players) {
            if (player == null) continue
            if (ticking) runHeldActions(player)
            player.session.processIncomingPackets(player)
        }
    }

    /** Sends every player this cycle's updates. [tickEnd] says whether the cycle was a game tick. */
    private fun sendUpdates(tickEnd: Boolean) {
        // Here rather than in the movement loop so a ::varp or ::setlevel typed while paused shows without waiting for a
        // step.
        for (player in players) {
            if (player == null) continue
            sendVars(player)
            sendStats(player)
            sendEffects(player)
            inventoryTab.flush(player)
            equipmentTab.flush(player)
        }
        network.infoProtocols.update()
        try {
            for (player in players) {
                if (player != null) sendInfo(player, tickEnd)
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
            action()
            return
        }
        if (player.heldActions.size >= MAX_HELD_ACTIONS) {
            logger.info { "Dropping a held action for ${player.username}: $MAX_HELD_ACTIONS already waiting" }
            return
        }
        player.heldActions.addLast(action)
    }

    internal fun runHeldActions(player: Player) {
        while (true) {
            val action = player.heldActions.removeFirstOrNull() ?: return
            // One bad click shouldn't cost everyone the tick.
            try {
                action()
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
        val index = (1 until MAX_PLAYERS).firstOrNull { players[it] == null }
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
        logger.info { "${player.username} logged in as player $index at ${player.x}, ${player.z}, ${player.level}" }
    }

    /**
     * Builds the player logging in as [username] from their save, or a new player in Lumbridge if they have none.
     * Throws if the save can't be read.
     */
    internal fun loadPlayer(index: Int, username: String): Player {
        val player = Player(index, username, SPAWN_X, SPAWN_Z, SPAWN_LEVEL, varTypes)
        val text = saves.read(player.saveKey) ?: return player
        PlayerSave.fromJson(text).applyTo(player)
        return player
    }

    /** Writes [player]'s save. Returns false, after logging why, if it couldn't be written. */
    internal fun save(player: Player): Boolean = try {
        saves.write(player.saveKey, PlayerSave.of(player).toJson(player.username))
        true
    } catch (e: Exception) {
        logger.error(e) { "Couldn't save ${player.username}" }
        false
    }

    private fun saveAll() {
        var count = 0
        for (player in players) {
            if (player != null && save(player)) count++
        }
        if (count > 0) logger.info { "Saved $count player${if (count == 1) "" else "s"}" }
    }

    private fun findPlayer(saveKey: String): Player? = players.firstOrNull { it?.saveKey == saveKey }

    /**
     * Puts a reconnecting client back on its player, who is still in the world. The client proves it is the same one
     * by sending back the ISAAC seed of the connection it lost.
     */
    private fun reconnect(login: PendingLogin, seed: IntArray) {
        val username = login.block.username
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
        logger.info { "${player.username} reconnected as player ${player.index}" }
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
        player.sentRunEnabled = null
        player.sentRunEnergy = -1
        player.sentCombatLevel = -1
        sendRunState(player)
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
        allowChat(player)
        quests.completeAll(player.vars)
        player.message(WELCOME_MESSAGE)

        interfaces.openGameFrame(player)
        inventoryTab.open(player)
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
        // Inferred: a delayed player can't walk away, as on an agility obstacle.
        if (player.scripts.delayed) return@act
        scripts.interrupt(player)
        player.interaction = null
        if (keyCombination == 2) {
            // Ctrl+shift-click is a J-Mod teleport on live; here it is a testing tool for everyone.
            teleport(player, x, z, player.level)
            return@act
        }
        val route = movement.request(player, x, z, invertRun = keyCombination == 1)
        // Guessed: with no route the client's flag would stay up forever, so clear it.
        if (route.waypoints.isEmpty()) player.session.queue(SetMapFlagV2.RESET)
    }

    /**
     * A click on option [option] (1 to 5) of obj [id] on the ground. The player walks there and the option runs once
     * they reach it; see [GroundObjs]. [ctrl] flips walk and run, as on a move click.
     */
    fun onGroundObjOp(player: Player, id: Int, x: Int, z: Int, option: Int, ctrl: Boolean) = act(player) {
        // Delayed players can't walk off, as for a move click.
        if (player.scripts.delayed) return@act
        val interaction = groundObjs.click(player, id, x, z, option) ?: return@act
        scripts.interrupt(player)
        player.interaction = interaction
        val route = movement.request(player, interaction.target, invertRun = ctrl)
        // Guessed, as for a walk: with no route to walk the client's flag would stay up.
        if (route.waypoints.isEmpty()) player.session.queue(SetMapFlagV2.RESET)
    }

    /** Examine on obj [id] on the ground: the same examine as in the inventory. Guessed, not checked against live. */
    fun onGroundObjExamine(player: Player, id: Int, x: Int, z: Int) = act(player) {
        val obj = groundObjs.visibleAt(player, x, z, player.level).firstOrNull { it.id == id } ?: return@act
        val type = objTypes[id] ?: return@act
        inventoryTab.examine(player, type, obj.count)
    }

    /** A click on option [option] (1 to 5) of loc [id] whose south-west tile is [x], [z]. [ctrl] flips walk and run. */
    fun onLocOp(player: Player, id: Int, x: Int, z: Int, option: Int, ctrl: Boolean) = act(player) {
        // Inferred, as for a walk: a delayed player's clicks do nothing.
        if (player.scripts.delayed) return@act
        locInteractions.click(player, id, x, z, option, ctrl)
    }

    /**
     * Examine on loc [id], the type the player sees (after any multiloc change). Sent as an object-examine chat message,
     * type 29 in RuneLite's ChatMessageType. Live's examine texts aren't in the cache, so until content has them the
     * message says what the loc is.
     */
    fun onLocExamine(player: Player, id: Int) = act(player) {
        val type = locs.types[id]
        val name = type?.internalName?.let { " $it" }.orEmpty()
        player.session.queue(MessageGame(CHATTYPE_OBJEXAMINE, "${type?.name ?: "Loc"} (loc $id$name): no examine text yet."))
    }

    /** A `::` command typed in the chatbox, without the `::`. Commands run straight away, even while paused. */
    fun onCommand(player: Player, input: String) {
        commands.execute(player, input, player::message)
    }

    /** Moves [player] straight to the tile, dropping any route. The client is told at the end of the tick. */
    fun teleport(player: Player, x: Int, z: Int, level: Int) {
        player.waypoints.clear()
        player.interaction = null
        player.x = x
        player.z = z
        player.level = level
        player.teleported = true
    }

    /** Whether the cache has terrain for the map square holding [x], [z]. */
    fun hasMap(x: Int, z: Int): Boolean = collision[x, z, 0] != CollisionFlag.UNLOADED

    /** Public chat is game input like a click, so while paused it is held for the next tick. */
    fun onPublicChat(player: Player, message: MessagePublic) = act(player) { sayPublicChat(player, message) }

    /** A click on an interface component. [obj] is the item the client showed there, -1 if none. */
    fun onButton(player: Player, interfaceId: Int, componentId: Int, sub: Int, op: Int, obj: Int = -1) = act(player) {
        if (interfaceId == InventoryTab.INTERFACE && componentId == InventoryTab.ITEMS) {
            inventoryTab.click(player, sub, obj, op)
            return@act
        }
        if (interfaceId == EquipmentTab.WORNITEMS && equipmentTab.clickWornItems(player, componentId, op)) return@act
        if (equipmentTab.clickStats(player, interfaceId, componentId, sub, obj, op)) return@act
        if (interfaceId == EquipmentTab.COMBAT && equipmentTab.clickCombatTab(player, componentId)) return@act
        if (interfaceId == GameFrame.ORBS && componentId == GameFrame.ORBS_RUN_BUTTON) {
            player.runEnabled = !player.runEnabled
            return@act
        }
        if (interfaceId == GameFrame.SETTINGS_SIDE && componentId == GameFrame.SETTINGS_CLIENT_LAYOUT_BUTTONS) {
            val frame = GameFrame.forLayoutOption(sub)
            if (frame != null) interfaces.requestFrame(player, frame, tick)
            return@act
        }
        if (interfaceId == JournalTab.SIDE_JOURNAL) {
            val tab = JournalTab.forButton(componentId)
            if (tab != null) {
                interfaces.openJournalTab(player, tab)
                return@act
            }
        }
        if (interfaceId == LOGOUT_TAB && componentId == LOGOUT_BUTTON) {
            interfaces.closeModals(player)
            player.logoutRequested = true
            return@act
        }
        if (scripts.trigger(player, Op.button(interfaceId, componentId, op, sub))) return@act
        logger.info { "${player.username} clicked $interfaceId:$componentId sub $sub op $op (not handled)" }
    }

    /** The player dragged a component's item, from slot [fromSub] onto [toComponentId]'s slot [toSub]. */
    fun onButtonDrag(player: Player, interfaceId: Int, componentId: Int, fromSub: Int, fromObj: Int, toInterfaceId: Int, toComponentId: Int, toSub: Int) = act(player) {
        val inventory = interfaceId == InventoryTab.INTERFACE && componentId == InventoryTab.ITEMS
        if (inventory && toInterfaceId == interfaceId && toComponentId == componentId) {
            inventoryTab.drag(player, fromSub, fromObj, toSub)
            return@act
        }
        logger.info { "${player.username} dragged $interfaceId:$componentId sub $fromSub onto $toInterfaceId:$toComponentId sub $toSub (not handled)" }
    }

    /** The player used one component's item on another's ("Use" then a click). */
    fun onButtonTarget(player: Player, interfaceId: Int, componentId: Int, fromSub: Int, fromObj: Int, toInterfaceId: Int, toComponentId: Int, toSub: Int, toObj: Int) = act(player) {
        val fromInventory = interfaceId == InventoryTab.INTERFACE && componentId == InventoryTab.ITEMS
        if (fromInventory && toInterfaceId == interfaceId && toComponentId == componentId) {
            inventoryTab.use(player, fromSub, fromObj, toSub, toObj)
            return@act
        }
        logger.info { "${player.username} used $interfaceId:$componentId sub $fromSub on $toInterfaceId:$toComponentId sub $toSub (not handled)" }
    }

    /**
     * Examine on an NPC of type [id]. Inferred: live answers with a game message of chat type 28, which RuneLite's
     * ChatMessageType calls NPC_EXAMINE; not checked against a recording.
     */
    fun onNpcExamine(player: Player, id: Int) = act(player) {
        val text = npcExamines[id]
        if (text != null) {
            player.session.queue(MessageGame(CHATTYPE_NPC_EXAMINE, text))
            return@act
        }
        // Not live behaviour: tells the tester the text is missing from npc-examines.json.
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
        val npc = npcs.add(spawn)
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

    fun removeNpc(npc: Npc) {
        npcs.remove(npc)
        npc.avatar?.let(network.npcAvatarFactory::release)
        npc.avatar = null
    }

    /** The client closed its modal: Esc, or a close button. */
    fun onCloseModal(player: Player) = act(player) { scripts.interrupt(player) }

    fun interfaceExists(interfaceId: Int): Boolean = interfaceIds?.contains(interfaceId) ?: (interfaceId >= 0)

    fun openModal(player: Player, slot: FrameSlot, interfaceId: Int) = interfaces.openModal(player, slot, interfaceId)

    private fun move(player: Player) {
        val result = movement.process(player)
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
        if (player.logoutRequested) {
            // Sends the client back to the login screen. requestClose flushes and drops our disconnection hook, so
            // the player is removed on the next tick by hand.
            session.queue(Logout)
            session.requestClose()
            player.loggedOut = true
            return
        }
        session.flush()
    }

    /** Saves and removes a player who logged out or whose wait for a reconnect is over. */
    private fun remove(player: Player) {
        save(player)
        network.infoProtocols.dealloc(player.infos)
        player.session.clear()
        players[player.index] = null
        logger.info { "${player.username} logged out" + if (player.loggedOut) "" else " (connection lost)" }
    }

    companion object {
        const val TICK_MILLIS = 600L
        const val MAX_PLAYERS = 2048

        /** The logout tab and its "Click here to logout" button, logout:logout. */
        const val LOGOUT_TAB = 182
        const val LOGOUT_BUTTON = 8

        /**
         * How long a player whose connection dropped stays in the world for the client to reconnect: 60 s, the
         * wiki's "60 second timer before the game forcibly logs you out after an unexpected disconnection" (Logout
         * button). The wiki gives it for combat; using it outside combat too is inferred.
         */
        const val LINGER_TICKS = 100L

        /** 3 minutes of cycles. */
        const val AUTOSAVE_CYCLES = 300L

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

        /** The first game message after login on live, from memory rather than a recording. */
        const val WELCOME_MESSAGE = "Welcome to Old School RuneScape."

        private val DEFAULT_IDENT_KITS = intArrayOf(0, 10, 18, 26, 33, 36, 42)
    }
}
