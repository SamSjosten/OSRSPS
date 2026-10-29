package osrsps.game.loc

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.misc.player.SetMapFlagV2
import osrsps.game.Interaction
import osrsps.game.Interactions
import osrsps.game.Inventory
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

/**
 * A loc option the player clicked and is walking to, or the item in inventory slot [slot] they used on it ([obj], -1
 * for an option).
 */
class LocInteraction internal constructor(
    val loc: ZoneLoc,
    val option: Int,
    private val owner: LocInteractions,
    val obj: Int = -1,
    val slot: Int = -1,
) : Interaction {
    val usesObj: Boolean get() = obj >= 0

    override fun tryInteract(player: Player) = owner.tryInteract(player, this)
}

/**
 * Clicks on locs: walk to the loc, then run its handler.
 *
 * A click routes the player to the nearest tile that reaches the loc (see [Reach]); [Interactions] then tries the option
 * before and after each of the player's steps. A loc with no handler for the option says "Nothing interesting
 * happens.", live's message. An item used on a loc walks the same way and then runs the [Handlers.onObjOnLoc] handler,
 * or says the same.
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
        start(player, LocInteraction(loc, option, this), invertRun)
    }

    /** The player used the item in inventory slot [slot], which the client showed as [obj], on loc [id] at [x], [z]. */
    fun useObj(player: Player, id: Int, x: Int, z: Int, slot: Int, obj: Int, invertRun: Boolean) {
        val loc = locs.find(id, x, z, player.level)
        if (loc == null) {
            logger.debug { "${player.username} used an item on loc $id at $x, $z, ${player.level}, which isn't there" }
            return
        }
        if (!holds(player, slot, obj)) {
            logger.debug { "${player.username} used slot $slot as $obj on loc $id, which it doesn't hold" }
            return
        }
        start(player, LocInteraction(loc, 0, this, obj = obj, slot = slot), invertRun)
    }

    private fun start(player: Player, interaction: LocInteraction, invertRun: Boolean) {
        scripts.interrupt(player)
        player.interaction = interaction
        val route = movement.request(player, target(interaction.loc), invertRun)
        // Guessed, as for a walk: with no route to walk the client's flag would stay up.
        if (route.waypoints.isEmpty()) clearFlag(player)
    }

    /** Whether [player]'s inventory slot [slot] holds [obj]. A slot out of range (a forged packet) holds nothing. */
    private fun holds(player: Player, slot: Int, obj: Int): Boolean =
        slot in 0 until Inventory.INV_CAPACITY && player.inventory[slot]?.id == obj

    /** Uses the loc if the player has reached it. Returns true once the interaction is over, used or gone. */
    internal fun tryInteract(player: Player, interaction: LocInteraction): Boolean {
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
        if (interaction.usesObj) {
            // Guessed: an item that left its slot on the way (dropped, or moved) is used on nothing, with no message.
            if (!holds(player, interaction.slot, interaction.obj)) return true
            val op = Op.objOnLoc(loc.id, interaction.obj, interaction.slot, loc)
            if (handlers.find(op) != null) {
                scripts.trigger(player, op)
            } else {
                logger.info { "${player.username} used $op at ${loc.x}, ${loc.z}, ${loc.level} (not handled)" }
                message(player, NOTHING_INTERESTING)
            }
            return true
        }
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

        const val NOTHING_INTERESTING = "Nothing interesting happens."
    }
}
