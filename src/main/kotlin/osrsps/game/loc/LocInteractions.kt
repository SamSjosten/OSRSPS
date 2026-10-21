package osrsps.game.loc

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.misc.player.SetMapFlagV2
import osrsps.game.Interaction
import osrsps.game.Movement
import osrsps.game.Player
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.Reach
import osrsps.map.RouteTarget

private val logger = KotlinLogging.logger {}

/** A loc option the player clicked and is walking to. */
data class LocInteraction(val loc: ZoneLoc, val option: Int) : Interaction

/**
 * Clicks on locs: walk to the loc, then run its handler.
 *
 * A click routes the player to the nearest tile that reaches the loc (see [Reach]). Each tick's movement phase then
 * tries the option before the player moves and again after, so a loc next to the player is used on the tick the click
 * is read, and one further away on the tick the player arrives. That two-check order is the RS2 world cycle as the
 * 2004Scape project reconstructed it, assumed to carry over to OSRS; task 21 checks it against recordings.
 *
 * A loc with no handler for the option says "Nothing interesting happens.", and one the route can't reach says
 * "I can't reach that!" once the player stops; both are live's messages.
 */
class LocInteractions(
    private val locs: Locs,
    private val collision: CollisionMap,
    private val movement: Movement,
    private val handlers: Handlers,
    private val scripts: ScriptRunner,
    private val doors: Doors,
    /** What the player is told and shown; tests swap these for recorders, as they have no client. */
    private val message: (Player, String) -> Unit = Player::message,
    private val faceLoc: (Player, RouteTarget) -> Unit = ::faceLoc,
    private val clearFlag: (Player) -> Unit = { it.session.queue(SetMapFlagV2.RESET) },
) {
    /** The player clicked option [option] (1 to 5) on loc [id] at [x], [z]. [invertRun] is the ctrl key. */
    fun click(player: Player, id: Int, x: Int, z: Int, option: Int, invertRun: Boolean) {
        // The client only sends what it shows, so a miss is a loc that changed before the click arrived, or a forged packet.
        val loc = locs.find(id, x, z, player.level)
        if (loc == null) {
            logger.debug { "${player.username} clicked loc $id at $x, $z, ${player.level}, which isn't there" }
            return
        }
        val type = locs.types[id]
        // A multiloc's options belong to whichever type the player's vars show, so any option is let through for it.
        if (option !in 1..5 || (type != null && !type.multiloc && !type.hasOp(option))) {
            logger.debug { "${player.username} clicked option $option of loc $id, which has no such option" }
            return
        }
        scripts.interrupt(player)
        player.interaction = LocInteraction(loc, option)
        val route = movement.request(player, target(loc), invertRun)
        // Guessed, as for a walk: with no route to walk the client's flag would stay up.
        if (route.waypoints.isEmpty()) clearFlag(player)
    }

    /**
     * Runs [player]'s part of the movement phase: their loc interaction, if any, around [move], which walks them one
     * tick. A delayed player's interaction waits for the delay to end.
     */
    fun process(player: Player, move: () -> Unit) {
        val interaction = player.interaction as? LocInteraction
        if (interaction == null || player.scripts.delayed || tryInteract(player, interaction)) {
            move()
            return
        }
        move()
        if (player.interaction !== interaction || tryInteract(player, interaction)) return
        if (player.waypoints.isEmpty()) {
            player.interaction = null
            message(player, CANT_REACH)
        }
    }

    /** Uses the loc if the player has reached it. Returns true once the interaction is over, used or gone. */
    private fun tryInteract(player: Player, interaction: LocInteraction): Boolean {
        val loc = interaction.loc
        if (!locs.exists(loc)) {
            // Guessed: someone else changed it (opened the door first); the player gives up and finishes their walk.
            player.interaction = null
            return true
        }
        if (!Reach.reached(collision, player.level, player.x, player.z, target(loc))) return false
        player.interaction = null
        player.waypoints.clear()
        // Guessed: the player turns to the loc as they use it, the way they do on live, and keeps that until they walk.
        faceLoc(player, target(loc))
        val op = Op(OpTarget.LOC, loc.id, interaction.option, loc = loc)
        if (handlers.find(op) != null) {
            scripts.trigger(player, op)
        } else if (doors.toggle(loc, interaction.option) == null) {
            logger.info { "${player.username} used $op at ${loc.x}, ${loc.z}, ${loc.level} (not handled)" }
            message(player, NOTHING_INTERESTING)
        }
        return true
    }

    private fun target(loc: ZoneLoc): RouteTarget {
        val type = locs.types[loc.id]
        return RouteTarget.loc(loc.x, loc.z, loc.shape, loc.angle, type?.width ?: 1, type?.length ?: 1, type?.accessBlock ?: 0)
    }

    companion object {
        private fun faceLoc(player: Player, target: RouteTarget) =
            player.infos.playerInfo.avatar.extendedInfo.setFaceLoc(target.x, target.z, target.width, target.length, instant = false, walkMode = 0)

        const val CANT_REACH = "I can't reach that!"
        const val NOTHING_INTERESTING = "Nothing interesting happens."
    }
}
