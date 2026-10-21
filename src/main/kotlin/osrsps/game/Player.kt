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
    varTypes: VarTypes = VarTypes.NONE,
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

    val vars = PlayerVars(varTypes)

    /** The run orb, which is varp 173 (option_run), so `::varp 173 1` turns it on too. */
    var runEnabled: Boolean
        get() = vars[World.VARP_RUN] != 0
        set(value) {
            vars[World.VARP_RUN] = if (value) 1 else 0
        }

    val skills = PlayerSkills()

    /** Current hitpoints, which are the Hitpoints skill's current level. */
    var hitpoints: Int
        get() = skills.currentLevel(Skill.HITPOINTS)
        set(value) = skills.setCurrentLevel(Skill.HITPOINTS, value)

    /** Ctrl-click flips walk and run for the route it starts. */
    var invertRun: Boolean = false

    /** 0 to 10,000; 100 units show as 1% on the orb. */
    var runEnergy: Int = Movement.MAX_ENERGY

    /** What the client was last told, so changes are sent once. Varps are tracked by [vars]. */
    var sentRunEnabled: Boolean? = null
    var sentRunEnergy: Int = -1
    var sentCombatLevel: Int = -1

    /** Moved by a teleport this tick; the client is told when the tick's movement is sent. */
    var teleported: Boolean = false

    /** Clicks and buttons that arrived while the clock was paused, run at the start of the next tick. */
    val heldActions = ArrayDeque<() -> Unit>()

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
