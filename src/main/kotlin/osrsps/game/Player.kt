package osrsps.game

import net.rsprot.protocol.api.Session
import net.rsprot.protocol.game.outgoing.info.Infos

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

    /** Set from a Netty thread when the connection drops; the game thread does the cleanup. */
    @Volatile
    var disconnected: Boolean = false
}
