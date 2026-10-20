package osrsps.game

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.api.GameConnectionHandler
import net.rsprot.protocol.api.NetworkService
import net.rsprot.protocol.api.login.GameLoginResponseHandler
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.outgoing.info.util.isEmpty
import net.rsprot.protocol.game.outgoing.info.util.onFailure
import net.rsprot.protocol.game.outgoing.info.util.onSuccess
import net.rsprot.protocol.game.outgoing.info.util.safeReleaseOrThrow
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenTop
import net.rsprot.protocol.game.outgoing.map.RebuildLoginV2
import net.rsprot.protocol.game.outgoing.map.RebuildNormalV2
import net.rsprot.protocol.game.outgoing.misc.player.SetMapFlagV2
import net.rsprot.protocol.game.outgoing.misc.player.UpdateRunEnergy
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import net.rsprot.protocol.loginprot.incoming.util.AuthenticationType
import net.rsprot.protocol.loginprot.incoming.util.LoginBlock
import net.rsprot.crypto.xtea.XteaKey
import net.rsprot.protocol.loginprot.outgoing.LoginResponse
import net.rsprot.protocol.loginprot.outgoing.util.AuthenticatorResponse
import osrsps.game.command.CommandRegistry
import osrsps.game.command.registerDevCommands
import osrsps.game.command.registerVarCommands
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * The game world: a single thread that runs a 600 ms tick.
 * Logins arrive from RSProt's login threads and are completed at the start of the next tick.
 */
class World(
    private val js5Crcs: IntArray,
    private val collision: CollisionMap,
    val varTypes: VarTypes = VarTypes.NONE,
) : GameConnectionHandler<Player> {
    lateinit var network: NetworkService<Player>

    private val movement = Movement(collision)

    val commands = CommandRegistry().apply {
        registerDevCommands(this@World)
        registerVarCommands()
    }

    private val pendingLogins = ConcurrentLinkedQueue<PendingLogin>()
    private val players = arrayOfNulls<Player>(MAX_PLAYERS)
    private val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "game") }
    var tick: Long = 0
        private set

    private class PendingLogin(val responseHandler: GameLoginResponseHandler<Player>, val block: LoginBlock<AuthenticationType>)

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
        responseHandler.writeFailedResponse(LoginResponse.ConnectFail)
    }

    fun start() {
        executor.execute { network.setCommunicationThread(Thread.currentThread(), true) }
        executor.scheduleAtFixedRate(::safeTick, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS)
    }

    fun stop() {
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun safeTick() {
        try {
            tick()
        } catch (e: Throwable) {
            logger.error(e) { "Tick $tick failed" }
        }
    }

    private fun tick() {
        tick++
        acceptLogins()
        for (player in players) {
            if (player == null) continue
            if (player.disconnected) remove(player) else player.session.processIncomingPackets(player)
        }
        for (player in players) {
            if (player == null) continue
            move(player)
            sendVars(player)
            player.infos.updateRootCoord(player.level, player.x, player.z)
        }
        network.infoProtocols.update()
        for (player in players) {
            if (player != null) sendInfo(player)
        }
    }

    private fun acceptLogins() {
        while (true) {
            val login = pendingLogins.poll() ?: return
            val index = (1 until MAX_PLAYERS).firstOrNull { players[it] == null }
            if (index == null) {
                login.responseHandler.writeFailedResponse(LoginResponse.ServerFull)
                continue
            }
            val ok = LoginResponse.Ok(AuthenticatorResponse.NoAuthenticator, 2, false, index, true, index.toLong(), index.toLong(), index.toLong())
            val player = Player(index, login.block.username, SPAWN_X, SPAWN_Z, SPAWN_LEVEL, varTypes)
            player.session = login.responseHandler.writeSuccessfulResponse(ok, login.block)
            player.session.setDisconnectionHook { player.disconnected = true }
            players[index] = player
            enterWorld(player)
            logger.info { "${player.username} logged in as player $index" }
        }
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
        appearance.setCombatLevel(3)
        // Default male look and the standard human animations. Guessed, not checked against live.
        DEFAULT_IDENT_KITS.forEachIndexed { slot, kit -> appearance.setIdentKit(slot, kit) }
        for (slot in 0 until 5) appearance.setColour(slot, 0)
        appearance.setBaseAnimationSet(808, 823, 819, 820, 821, 822, 824)
        sendRunState(player)
        allowChat(player)
        player.message(WELCOME_MESSAGE)

        session.queue(IfOpenTop(GameFrame.TOPLEVEL_RESIZABLE))
        for ((interfaceId, component) in GameFrame.overlays) {
            session.queue(IfOpenSub(GameFrame.TOPLEVEL_RESIZABLE, component, interfaceId, OVERLAY))
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
    fun onMoveClick(player: Player, x: Int, z: Int, keyCombination: Int) {
        if (keyCombination == 2) {
            // Ctrl+shift-click is a J-Mod teleport on live; here it is a testing tool for everyone.
            teleport(player, x, z, player.level)
            return
        }
        val route = movement.request(player, x, z, invertRun = keyCombination == 1)
        // Guessed: with no route the client's flag would stay up forever, so clear it.
        if (route.waypoints.isEmpty()) player.session.queue(SetMapFlagV2.RESET)
    }

    /** A `::` command typed in the chatbox, without the `::`. */
    fun onCommand(player: Player, input: String) {
        commands.execute(player, input, player::message)
    }

    /** Moves [player] straight to the tile, dropping any route. The client is told at the end of the tick. */
    fun teleport(player: Player, x: Int, z: Int, level: Int) {
        player.waypoints.clear()
        player.x = x
        player.z = z
        player.level = level
        player.teleported = true
    }

    /** Whether the cache has terrain for the map square holding [x], [z]. */
    fun hasMap(x: Int, z: Int): Boolean = collision[x, z, 0] != CollisionFlag.UNLOADED

    fun onButton(player: Player, interfaceId: Int, componentId: Int, op: Int) {
        if (interfaceId == GameFrame.ORBS && componentId == GameFrame.ORBS_RUN_BUTTON) {
            player.runEnabled = !player.runEnabled
            return
        }
        logger.info { "${player.username} clicked $interfaceId:$componentId op $op (not handled)" }
    }

    private fun move(player: Player) {
        val result = movement.process(player)
        val extendedInfo = player.infos.playerInfo.avatar.extendedInfo
        val speed = when (result) {
            MoveResult.NONE -> null
            MoveResult.WALKED -> MOVE_SPEED_WALK
            MoveResult.RAN -> MOVE_SPEED_RUN
        }
        if (player.teleported) {
            // A teleport is sent as the one-tick teleport speed so the client jumps instead of walking there, and the
            // old route's flag is cleared. Clearing the flag is guessed, not checked against live.
            extendedInfo.setTempMoveSpeed(MOVE_SPEED_TELEPORT)
            player.session.queue(SetMapFlagV2.RESET)
            player.teleported = false
        } else if (speed != null && speed != moveSpeed(player)) {
            // Inferred: a tick moved at a speed other than the orb's (ctrl-click, the last odd tile of a run) is sent
            // as a one-tick temporary speed so the client animates it right.
            extendedInfo.setTempMoveSpeed(speed)
        }
        sendRunState(player)
        rebuildIfNeeded(player)
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
    }

    private fun sendInfo(player: Player) {
        val session = player.session
        val packets = player.infos.getPackets().rootWorldInfoPackets
        session.queue(packets.activeWorld)
        session.queue(packets.npcUpdateOrigin)
        packets.worldEntityInfo.onSuccess(session::queue).onFailure { logger.error(it) { "World entity info failed for ${player.username}" } }
        packets.playerInfo.onSuccess(session::queue).onFailure { logger.error(it) { "Player info failed for ${player.username}" } }
        val npcInfo = packets.npcInfo
        if (npcInfo.isEmpty()) npcInfo.safeReleaseOrThrow() else npcInfo.onSuccess(session::queue)
        session.flush()
    }

    private fun remove(player: Player) {
        network.infoProtocols.dealloc(player.infos)
        player.session.clear()
        players[player.index] = null
        logger.info { "${player.username} disconnected" }
    }

    companion object {
        const val TICK_MILLIS = 600L
        const val MAX_PLAYERS = 2048
        const val OVERLAY = 1

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

        /** has_displayname_transmitter in RuneLite's VarbitID. */
        const val VARBIT_HAS_DISPLAYNAME = 8119

        /** The first game message after login on live, from memory rather than a recording. */
        const val WELCOME_MESSAGE = "Welcome to Old School RuneScape."

        private val DEFAULT_IDENT_KITS = intArrayOf(0, 10, 18, 26, 33, 36, 42)
    }
}
