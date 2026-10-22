package osrsps.game

import net.rsprot.protocol.game.outgoing.misc.player.SetMapFlagV2
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.map.Tile
import kotlin.math.abs
import kotlin.math.atan2

/**
 * The player clicked Follow on [target] and walks after them until they click elsewhere, teleport, or lose sight of
 * them. [Follows] runs it; it never ends by being reached, so [Interactions] never sees it.
 */
class FollowInteraction(val target: Player) : Interaction {
    /** The follow tile the player's current route leads to. */
    internal var routedTo: Tile? = null

    // Follows.process handles a follow before Interactions would ask, so this is never called.
    override fun tryInteract(player: Player): Boolean = false
}

/**
 * Follow, the option on other players: the follower walks to the tile the target last stepped off, so they end up one
 * tile behind, and turns to face the target whenever they stop.
 *
 * The follow tile is RSMod's (rev 233): the tile the target was on before their last step, or the tile west of them if
 * they haven't stepped since logging in or teleporting, as it stood at the start of the tick's movement phase. RSMod
 * walks straight at it once the route from the click is used up; here the follower takes a route to it, so a wall
 * between them doesn't stop the follow. Neither is checked against live; task 21 compares against recordings.
 */
class Follows(
    private val movement: Movement,
    /** Whether [Player] is still in the world, so a follow can stop when its target logs out. */
    private val inWorld: (Player) -> Boolean,
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    /** Where someone following [player] heads for now. */
    private fun followTile(player: Player): Tile = player.lastStepFrom ?: Tile(player.x - 1, player.z)

    /** A click on Follow: [player] turns to [target] and walks toward the tile behind them. [ctrl] flips walk and run. */
    fun click(player: Player, target: Player, ctrl: Boolean) {
        if (target === player || !canFollow(player, target)) {
            // The client may have put a flag down for the walk to them; guessed, as for a blocked walk.
            send(player, SetMapFlagV2.RESET)
            return
        }
        val follow = FollowInteraction(target)
        player.interaction = follow
        val dest = followTile(target)
        follow.routedTo = dest
        val route = movement.request(player, dest.x, dest.z, invertRun = ctrl)
        // Guessed, as for a walk: with no route to walk the client's flag would stay up.
        if (route.waypoints.isEmpty()) send(player, SetMapFlagV2.RESET)
    }

    /**
     * [player] clicked something else in the world, a loc, an NPC or a ground obj, which didn't start an interaction
     * because it had gone by the time the click was read (or the option wasn't there). A follow still ends: they clicked
     * away from it. They stop where they stand; guessed, as for a target lost mid-follow.
     */
    fun clickedElsewhere(player: Player) {
        if (player.interaction !is FollowInteraction) return
        player.interaction = null
        if (player.waypoints.isNotEmpty()) {
            player.waypoints.clear()
            send(player, SetMapFlagV2.RESET)
        }
    }

    /**
     * Notes every player's follow tile before anyone moves this tick, so who moves first in the tick doesn't change
     * where a follower heads.
     */
    fun startMovement(players: Iterable<Player>) {
        for (player in players) {
            player.followTile = followTile(player)
            player.movementStart = Tile(player.x, player.z)
            player.movementStartLevel = player.level
        }
    }

    /**
     * Runs [player]'s part of the movement phase if they are following someone: tops up their route to the target's
     * follow tile, then [move]s them. Anyone else is handed to [other].
     */
    fun process(player: Player, move: () -> Unit, other: () -> Unit) {
        val follow = player.interaction as? FollowInteraction ?: return other()
        val target = follow.target
        // Measured from where the target stood when movement began, so a target who moves first can't step out of range.
        val from = target.movementStart ?: Tile(target.x, target.z)
        val level = if (target.movementStart != null) target.movementStartLevel else target.level
        if (!canFollow(player, target, from.x, from.z, level)) {
            // Guessed: the follower stops where they are rather than walking on to the target's last follow tile.
            player.interaction = null
            if (player.waypoints.isNotEmpty()) {
                player.waypoints.clear()
                // The client may still show the flag from the walk to the target; guessed, as for a blocked walk.
                send(player, SetMapFlagV2.RESET)
            }
            move()
            return
        }
        // RSMod's rule: a new route only once the last one is down to its final waypoint. Ours also keeps a route that
        // still leads to the follow tile. A delayed player keeps theirs.
        val dest = target.followTile ?: followTile(target)
        if (!player.scripts.delayed && player.waypoints.size <= 1 && (player.waypoints.isEmpty() || follow.routedTo != dest)) {
            follow.routedTo = dest
            if (player.x != dest.x || player.z != dest.z) {
                // RSMod goes back to the orb's speed here, so a ctrl-click only changes the walk to where the target was.
                movement.request(player, dest.x, dest.z, invertRun = false)
            } else {
                player.waypoints.clear()
            }
        }
        move()
    }

    /**
     * Turns [player] to whoever they follow, or stops turning them once they don't, when that changes: [face] gets the
     * target, or null to reset. A follow ended by a click on an NPC is left alone, since the NPC's face already replaced
     * it and a reset would undo that.
     */
    fun updateFace(player: Player, face: (Player?) -> Unit) {
        val target = (player.interaction as? FollowInteraction)?.target
        if (target === player.sentFacePlayer) return
        if (target != null || player.facingNpc == null) face(target)
        player.sentFacePlayer = target
    }

    /**
     * Whether [player] can carry on following [target]: the target is still in the world, and at [targetX], [targetZ] on
     * [targetLevel] (by default where they are now), on the player's level and within [RANGE] tiles. Guessed: live stops a follow when the target teleports away or logs out, but the distance it
     * gives up at isn't known; [RANGE] is how far players see each other.
     */
    fun canFollow(player: Player, target: Player, targetX: Int = target.x, targetZ: Int = target.z, targetLevel: Int = target.level): Boolean =
        inWorld(target) && targetLevel == player.level &&
            abs(targetX - player.x) <= RANGE && abs(targetZ - player.z) <= RANGE

    companion object {
        /** The distance players see each other at, RSProt's default for player info. */
        const val RANGE = 15

        /**
         * The angle a player at [fromX], [fromZ] turns to so they look at [toX], [toZ]: 0 south, 512 west, 1024 north,
         * 1536 east, out of 2048. The client's own sum, `atan2(dx, dz) * 325.949 & 2047`. Sent with a face so a client
         * that can't see the target still turns the right way.
         */
        fun angle(fromX: Int, fromZ: Int, toX: Int, toZ: Int): Int =
            (atan2((fromX - toX).toDouble(), (fromZ - toZ).toDouble()) * 325.949).toInt() and 0x7FF
    }
}
