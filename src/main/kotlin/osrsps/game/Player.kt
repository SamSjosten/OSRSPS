package osrsps.game

import net.rsprot.protocol.api.Session
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import osrsps.map.Tile

/** A logged-in player. Only touched on the game thread, apart from [disconnected]. */
class Player(
    val index: Int,
    val username: String,
    var x: Int,
    var z: Int,
    var level: Int,
) {
    lateinit var session: Session<Player>
    lateinit var infos: Infos

    /** 1 = fixed, 2 = resizable, from the client's WindowStatus. */
    var windowMode: Int = 0

    /** The top-level interface the client shows. */
    var frame: GameFrame = GameFrame.RESIZABLE_CLASSIC

    /** Varbit resizable_stone_arrangement: which resizable layout the player last chose. */
    var stoneArrangement: Int = 0

    /** A layout change waiting for its tick. */
    var pendingFrameMove: FrameMove? = null

    /** Open modal interfaces by where they are open. */
    val modals = LinkedHashMap<FrameSlot, Int>()

    /** Set by the logout button; the player is logged out at the end of the tick. */
    var logoutRequested: Boolean = false

    /** Set from a Netty thread when the connection drops; the game thread does the cleanup. */
    @Volatile
    var disconnected: Boolean = false

    /** Turning points of the current route, next one first. */
    val waypoints = ArrayDeque<Tile>()

    /** The run orb. */
    var runEnabled: Boolean = false

    /** Ctrl-click flips walk and run for the route it starts. */
    var invertRun: Boolean = false

    /** 0 to 10,000; 100 units show as 1% on the orb. */
    var runEnergy: Int = Movement.MAX_ENERGY

    /** What the client was last told, so changes are sent once. */
    var sentRunEnabled: Boolean? = null
    var sentRunEnergy: Int = -1

    /** Moved by a teleport this tick; the client is told when the tick's movement is sent. */
    var teleported: Boolean = false

    /** The zone the client's build area was last centred on. */
    var buildAreaZoneX: Int = x shr 3
    var buildAreaZoneZ: Int = z shr 3

    /** Shows [text] in the chatbox as a game message. */
    fun message(text: String) {
        session.queue(MessageGame(CHATTYPE_GAMEMESSAGE, text))
    }

    private companion object {
        const val CHATTYPE_GAMEMESSAGE = 0
    }
}
