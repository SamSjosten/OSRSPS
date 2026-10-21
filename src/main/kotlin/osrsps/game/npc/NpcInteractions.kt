package osrsps.game.npc

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.misc.player.SetMapFlagV2
import osrsps.game.Facing
import osrsps.game.Interaction
import osrsps.game.Inventory
import osrsps.game.Movement
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner
import osrsps.map.CollisionMap
import osrsps.map.Reach
import osrsps.map.RouteTarget
import osrsps.map.Tile
import kotlin.math.abs

private val logger = KotlinLogging.logger {}

/**
 * An NPC option the player clicked, or an item they used on an NPC ([obj] and its inventory [slot]), and are walking to.
 * [sub] is the option's sub-option, -1 for none.
 */
class NpcInteraction(val npc: Npc, val option: Int, val sub: Int = -1, val obj: Int = -1, val slot: Int = -1) : Interaction {
    val usesObj: Boolean get() = obj >= 0

    /** Where the NPC stood when the player's route to it was last made. */
    internal var routedTo: Tile? = null
}

/**
 * Clicks on NPCs, and items used on them: walk to the NPC, then run the handler.
 *
 * A click turns the player to the NPC and routes them to the nearest tile beside it (see [Reach.reachedEntity]): along
 * one of its sides, not a corner, and not under it. Each tick's movement phase tries the option before the player
 * moves and again after, as for locs. While the NPC walks the player chases it: the route is made again from where
 * the NPC now stands whenever it has moved. Players reach NPCs with the same route finder as tiles, which is how the
 * client's own route finder treats them; that live re-routes every tick the NPC moves is inferred from the RS2 world
 * cycle (2004Scape), not checked against a recording.
 *
 * When the option runs, the NPC turns to the player and stays still while the player is busy with it (its handler is
 * delayed or a dialogue is open), then goes back to what it was doing. Live NPCs turn to you when you talk to them;
 * that every option does it, and the release rule, are guessed.
 *
 * Interrupted (inferred from live, where a new click replaces what you were doing): walking somewhere, clicking
 * another NPC or loc, or a teleport drops the interaction, and the NPC going away or changing level ends it quietly.
 * An option with no handler says "Nothing interesting happens.", and an NPC the route can't reach "I can't reach
 * that!" once the player stops; both are live's messages.
 */
class NpcInteractions(
    private val npcs: Npcs,
    private val npcTypes: NpcTypes,
    private val collision: CollisionMap,
    private val movement: Movement,
    private val handlers: Handlers,
    private val scripts: ScriptRunner,
    private val currentTick: () -> Long,
    /** What the player is told and shown; tests swap these for recorders, as they have no client. */
    private val message: (Player, String) -> Unit = Player::message,
    private val faceNpc: (Player, Npc?) -> Unit = ::sendFaceNpc,
    private val clearFlag: (Player) -> Unit = { it.session.queue(SetMapFlagV2.RESET) },
) {
    /**
     * The player clicked option [option] (1 to 5) of NPC [index], with sub-option [sub] (-1 for none). [invertRun] is the
     * ctrl key.
     */
    fun click(player: Player, index: Int, option: Int, sub: Int, invertRun: Boolean) {
        val npc = clickable(player, index) ?: return
        val type = shownType(npc, player)
        // A multi-NPC's options belong to the type the player's vars show; one showing nothing has none.
        if (option !in 1..Handlers.MAX_OPTION || type == null || shownOp(type, option, player) == null) {
            logger.debug { "${player.username} clicked option $option of $npc, which has no such option" }
            return
        }
        start(player, NpcInteraction(npc, option, sub), invertRun)
    }

    /** The player used the item [obj] in inventory slot [slot] on NPC [index]. */
    fun useObj(player: Player, index: Int, slot: Int, obj: Int, invertRun: Boolean) {
        val npc = clickable(player, index) ?: return
        if (!holds(player, slot, obj)) {
            logger.debug { "${player.username} used slot $slot as $obj on $npc, which it doesn't hold" }
            return
        }
        start(player, NpcInteraction(npc, 0, obj = obj, slot = slot), invertRun)
    }

    /** Whether [player]'s inventory slot [slot] holds [obj]. A slot out of range (a forged packet) holds nothing. */
    private fun holds(player: Player, slot: Int, obj: Int): Boolean =
        slot in 0 until Inventory.INV_CAPACITY && player.inventory[slot]?.id == obj

    /**
     * The NPC at [index] if the player could have clicked it: there, on their level, in view, and showing a type with a
     * menu (for a multi-NPC, the type the player's vars show).
     */
    private fun clickable(player: Player, index: Int): Npc? {
        val npc = npcs[index]
        // The client only sends NPCs it shows, so a miss is one that left before the click arrived, or a forged packet.
        if (npc == null || npc.level != player.level || abs(npc.x - player.x) > VIEW_RANGE || abs(npc.z - player.z) > VIEW_RANGE) {
            logger.debug { "${player.username} clicked NPC $index, which isn't in view" }
            return null
        }
        val type = shownType(npc, player)
        if (type == null || !type.interactable) return null
        return npc
    }

    private fun start(player: Player, interaction: NpcInteraction, invertRun: Boolean) {
        scripts.interrupt(player)
        player.interaction = interaction
        // The player turns to the NPC on the click, before walking: live's face-entity. Inferred, not from a recording.
        face(player, interaction.npc)
        if (!route(player, interaction, invertRun)) clearFlag(player)
    }

    /** Routes [player] to beside the NPC. False when there is no route to walk. */
    private fun route(player: Player, interaction: NpcInteraction, invertRun: Boolean = player.invertRun): Boolean {
        val npc = interaction.npc
        interaction.routedTo = Tile(npc.x, npc.z)
        val route = movement.request(player, RouteTarget.entity(npc.x, npc.z, npc.type.size), invertRun)
        return route.waypoints.isNotEmpty()
    }

    /**
     * Runs [player]'s part of the movement phase: their NPC interaction, if any, around [move], which walks them one
     * tick. A delayed player's interaction waits for the delay to end.
     */
    fun process(player: Player, move: () -> Unit) {
        val interaction = player.interaction as? NpcInteraction
        if (interaction == null || player.scripts.delayed || tryInteract(player, interaction)) {
            move()
            return
        }
        // Chase: the NPC moved since the route was made (in this tick's NPC phase, or an earlier one).
        val npc = interaction.npc
        if (interaction.routedTo != Tile(npc.x, npc.z)) route(player, interaction)
        move()
        if (player.interaction !== interaction || tryInteract(player, interaction)) return
        if (player.waypoints.isEmpty()) {
            player.interaction = null
            message(player, CANT_REACH)
        }
    }

    /** Runs the option if the player has reached the NPC. Returns true once the interaction is over, run or gone. */
    private fun tryInteract(player: Player, interaction: NpcInteraction): Boolean {
        val npc = interaction.npc
        if (npcs[npc.index] !== npc || npc.level != player.level) {
            // Guessed: the NPC went away (or up a ladder); the player stops turning to it and finishes their walk.
            player.interaction = null
            stopFacing(player)
            return true
        }
        if (interaction.usesObj && !holds(player, interaction.slot, interaction.obj)) {
            // Guessed: the item left the slot on the way (dropped, used up); the player gives up.
            player.interaction = null
            stopFacing(player)
            return true
        }
        val size = npc.type.size
        if (!Reach.reachedEntity(collision, player.level, player.x, player.z, 1, npc.x, npc.z, size, size)) return false
        player.interaction = null
        player.waypoints.clear()
        val type = shownType(npc, player)
        if (type == null || !type.interactable || (!interaction.usesObj && shownOp(type, interaction.option, player) == null)) {
            // A multi-NPC (or an option on a var) changed on the way, so what was clicked isn't there any more. Guessed.
            stopFacing(player)
            return true
        }
        // Sent again on arrival, in case the client dropped the turn while the player walked.
        face(player, npc)
        val op = if (interaction.usesObj) {
            Op.objOnNpc(type.id, interaction.obj, interaction.slot, npc)
        } else {
            Op(OpTarget.NPC, type.id, interaction.option, interaction.sub, npc = npc)
        }
        // An NPC already busy with another player stays with them until they're done. Guessed: live NPCs talk to
        // several players at once, but which one they face isn't checked.
        val engaged = npc.engagedWith
        if (engaged == null || engaged === player || !scripts.busy(engaged)) npcs.engage(npc, player, currentTick())
        if (handlers.find(op) != null) {
            scripts.trigger(player, op)
        } else {
            logger.info { "${player.username} used $op on $npc (not handled)" }
            message(player, NOTHING_INTERESTING)
        }
        return true
    }

    private fun face(player: Player, npc: Npc?) {
        player.facingNpc = npc
        faceNpc(player, npc)
    }

    /**
     * Stops [player] turning to the NPC they last clicked, if any: they walked off, clicked something else or teleported.
     * Inferred from live, where you stop looking at an NPC once you walk away; RSProt's walkMode 0 may cancel it in the
     * client on the first step anyway.
     */
    fun stopFacing(player: Player) {
        if (player.facingNpc != null) face(player, null)
    }

    /**
     * Lets NPCs go once the player they are busy with is done: not delayed, no dialogue or other modal open, and at
     * least a tick on from the option. Guessed; run at the start of the NPC phase so a released NPC moves that tick.
     */
    fun releaseNpcs() {
        val tick = currentTick()
        for (npc in npcs.all) {
            val player = npc.engagedWith ?: continue
            if (tick > npc.engagedTick && !scripts.busy(player)) npcs.release(npc)
        }
    }

    /** Option [option] of [type] as [player] sees it, conditional options included; null when there is none. */
    private fun shownOp(type: NpcType, option: Int, player: Player): String? =
        type.op(option) { if (it.varbit >= 0) readVar(player, -1, it.varbit) else readVar(player, it.varp, -1) }

    /** Varbit [varbit], or varp [varp] when the varbit is -1, for [player]; null for a var our cache doesn't have. */
    private fun readVar(player: Player, varp: Int, varbit: Int): Int? = try {
        when {
            varbit >= 0 -> player.vars.getVarbit(varbit)
            varp >= 0 -> player.vars[varp]
            else -> null
        }
    } catch (e: IllegalArgumentException) {
        null
    }

    /**
     * The NPC type [player] sees: a multi-NPC shows one of its types by a varbit or varp, as the client picks it. Null
     * when it shows none.
     */
    fun shownType(npc: Npc, player: Player): NpcType? {
        val type = npc.type
        if (type.multiNpcs.isEmpty()) return type
        // A var our cache doesn't have shows the default.
        val value = readVar(player, type.multiVarp, type.multiVarbit) ?: -1
        val id = if (value in 0 until type.multiNpcs.size - 1) type.multiNpcs[value] else type.multiNpcs.last()
        return if (id < 0) null else npcTypes[id]
    }

    companion object {
        /** How far away an NPC can be clicked: RSProt's NPC info sends NPCs within 15 tiles. */
        const val VIEW_RANGE = 15

        const val CANT_REACH = "I can't reach that!"
        const val NOTHING_INTERESTING = "Nothing interesting happens."

        /**
         * Turns the player to [npc], or stops turning when null. walkMode 0 is the old face-entity behaviour as RSProt
         * maps it; inferred, not checked against live.
         */
        private fun sendFaceNpc(player: Player, npc: Npc?) {
            val info = player.infos.playerInfo.avatar.extendedInfo
            if (npc == null) {
                info.resetFacing()
                return
            }
            val angle = Facing.angle(player.x, player.z, 1, npc.x, npc.z, npc.type.size) ?: 0
            info.setFaceNpc(npc.index, false, 0, angle)
        }
    }
}
