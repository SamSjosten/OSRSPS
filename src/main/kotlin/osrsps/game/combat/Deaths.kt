package osrsps.game.combat

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.Movement
import osrsps.game.NpcType
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.Handlers
import osrsps.game.script.Op

private val logger = KotlinLogging.logger {}

/** A player's gravestone: the [npc] standing where they died, holding what they lost in [items]. */
class Grave(
    /** [Player.saveKey] of whose it is. */
    val owner: String,
    val npc: Npc,
    val items: Inventory = Inventory(Deaths.GRAVE_INV, Deaths.GRAVE_CAPACITY),
) {
    /** Ticks until it collapses; counts down only while the owner is logged in and nobody has it open. */
    var ticksLeft: Int = Deaths.GRAVE_TICKS
        internal set
}

/**
 * Player death and gravestones (OSRS Wiki, Death and Gravestone).
 *
 * A player with no hitpoints left dies where they stand ([die]): "Oh dear, you are dead!" has already been said (see
 * [MeleeCombat]), their death animation plays, and for [DEATH_TICKS] they can do nothing. Then ([respawn]) they keep
 * their [KEPT_ITEMS] most valuable items and the rest go into a gravestone on the death tile: NPC [GRAVE] with Check
 * and Loot. They come back at Lumbridge with full hitpoints, every stat back to its level and full run energy.
 *
 * The grave stands for [GRAVE_TICKS] (15 minutes) of the owner's logged-in time; the timer stops while they have it open.
 * Loot opens the gravestone interface ([GRAVESTONE], inv [GRAVE_INV]), whose items have Take and whose button is Take-All.
 * Dying again while it stands adds to it and starts its timer again. When it collapses its contents are lost: Death's
 * Office, where they would go on live, isn't built. Nobody else can check or loot it.
 *
 * From the Wiki: the message, three items kept (more with Protect Item, fewer skulled: neither is built), valued at
 * the Grand Exchange price or the alchemy value, 15 minutes that pause while logged out or with the interface open,
 * a second death adding to the grave, and death restoring stats and run energy. Guessed and marked below: the ticks
 * the body lies before the respawn, the value order ([ObjType.cost], as there is no Grand Exchange snapshot yet; ties
 * by worn then inventory slot), that each unit of a stack counts as an item, and what Check says. The free list is
 * the interface's: its client script puts slots from varbit [VARBIT_FREE_FROM] minus one in the free list and the
 * rest in the paid one (gravestone_generic_window_set); every item here is free.
 */
class Deaths(
    private val objTypes: ObjTypes,
    /** NPC type [GRAVE]; null when the cache has no NPCs (tests), and then the lost items are simply lost. */
    private val graveType: NpcType?,
    private val currentTick: () -> Long,
    private val players: () -> Sequence<Player>,
    private val findPlayer: (saveKey: String) -> Player?,
    private val spawnNpc: (NpcSpawn) -> Npc?,
    private val removeNpc: (Npc) -> Unit,
    /** Puts a respawning player at the spawn. */
    private val teleportToSpawn: (Player) -> Unit,
    private val openModal: (Player, Int) -> Unit,
    private val closeModals: (Player) -> Unit,
    /** Sets a varbit and sends it ahead of the queue, so an interface opening now reads it. */
    private val setVarbitNow: (Player, Int, Int) -> Unit,
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
    private val message: (Player, String) -> Unit = Player::message,
    /** Examine on a grave item: the inventory's examine. */
    private val examine: (Player, ObjType, Int) -> Unit = { _, _, _ -> },
) {
    private val graves = LinkedHashMap<Npc, Grave>()

    /** The grave each player has open, by [Player.saveKey]; stale once they close it, so always checked against the modal. */
    private val viewing = HashMap<String, Grave>()

    /** Every grave standing, for tests and commands. */
    val all: Collection<Grave> get() = graves.values

    fun graveOf(player: Player): Grave? = graves.values.firstOrNull { it.owner == player.saveKey }

    fun register(handlers: Handlers) {
        handlers.onNpc(GRAVE, CHECK) { op -> check(player, op.npc!!) }
        handlers.onNpc(GRAVE, LOOT) { op -> loot(player, op.npc!!) }
        handlers.onButton(GRAVESTONE, FREEITEMS, EXAMINE_OP) { op -> examineItem(player, op.sub) }
        handlers.onButton(GRAVESTONE, FREEITEMS) { op -> take(player, op.sub) }
        handlers.onButton(GRAVESTONE, FREEBUTTON) { takeAll(player) }
    }

    /** [player] has no hitpoints left: they fall where they stand and lie there until [process] respawns them. */
    fun die(player: Player) {
        if (player.dead) return
        player.deathTick = currentTick()
        closeModals(player)
        player.interaction = null
        player.waypoints.clear()
        player.anim(DEATH_ANIM)
    }

    /** Counts the graves down (a grave raised this tick starts next tick), and respawns the players whose death animation is over. */
    fun process() {
        if (graves.isNotEmpty()) {
            val open = viewing.filter { (owner, _) -> findPlayer(owner)?.let { hasOpen(it) } == true }.values.toSet()
            for (grave in graves.values.toList()) {
                if (grave in open || findPlayer(grave.owner) == null) continue
                if (--grave.ticksLeft <= 0) collapse(grave)
            }
        }
        val tick = currentTick()
        for (player in players()) {
            if (player.dead && tick - player.deathTick >= DEATH_TICKS) respawn(player)
        }
    }

    /**
     * Puts [player] back in the world: their items sorted into kept and lost, the lost ones in their grave, their stats
     * and run energy back, and themself at the spawn. Also run for a dead player who logs out, so nobody saves dead.
     */
    fun respawn(player: Player) {
        player.deathTick = -1
        val lost = dropItems(player)
        if (lost.isNotEmpty()) bury(player, lost)
        for (skill in Skill.entries) player.skills.setCurrentLevel(skill, player.skills.baseLevel(skill))
        player.runEnergy = Movement.MAX_ENERGY
        teleportToSpawn(player)
    }

    /**
     * Takes everything but the [KEPT_ITEMS] most valuable units out of [player]'s inventory and equipment, returning what
     * came out. A stack counts a unit per item, so three coins of a stack of ten are kept and seven go. Guessed; the
     * Wiki says only that stacks "count as one item" for the kept-item count, which can't be what it means for a
     * stack of coins.
     */
    private fun dropItems(player: Player): List<InvObj> {
        class Held(val inv: Inventory, val slot: Int, val obj: InvObj, val cost: Int)
        val held = ArrayList<Held>()
        for (inv in listOf(player.equipment, player.inventory)) {
            for ((slot, obj) in inv.contents()) held += Held(inv, slot, obj, objTypes[obj.id]?.cost ?: 0)
        }
        // Stable, so ties keep worn-then-inventory slot order.
        held.sortByDescending { it.cost }
        var keep = KEPT_ITEMS
        val lost = ArrayList<InvObj>()
        for (item in held) {
            val kept = minOf(keep, item.obj.count)
            keep -= kept
            val gone = item.obj.count - kept
            if (gone == 0) continue
            item.inv.remove(item.slot, gone)
            lost += InvObj(item.obj.id, gone)
        }
        return lost
    }

    /** Puts [lost] in [player]'s grave, raising one on their tile if they have none; an existing grave starts its timer again. */
    private fun bury(player: Player, lost: List<InvObj>) {
        val grave = graveOf(player) ?: raise(player)
        if (grave == null) {
            logger.warn { "${player.username} lost ${lost.size} items with no grave to put them in" }
            return
        }
        for (obj in lost) {
            val type = objTypes[obj.id] ?: ObjType(obj.id)
            val added = grave.items.add(type, obj.count)
            // Guessed: a grave holds 120 slots; anything past that is lost.
            if (added < obj.count) logger.warn { "${player.username}'s grave is full: ${obj.count - added} of ${type.name} lost" }
        }
        grave.ticksLeft = GRAVE_TICKS
        for (viewer in players()) if (viewing[viewer.saveKey] === grave && hasOpen(viewer)) sendItems(viewer, grave)
    }

    private fun raise(player: Player): Grave? {
        val type = graveType ?: return null
        val npc = spawnNpc(NpcSpawn(type, player.x, player.z, player.level, source = "${player.username}'s death")) ?: return null
        val grave = Grave(player.saveKey, npc)
        graves[npc] = grave
        return grave
    }

    /** The grave falls: its NPC goes and its contents with it (Death's Office isn't built), and the owner is told. */
    private fun collapse(grave: Grave) {
        graves.remove(grave.npc)
        removeNpc(grave.npc)
        viewing.values.removeIf { it === grave }
        for (viewer in players()) if (viewing[viewer.saveKey] == null && hasOpen(viewer)) closeModals(viewer)
        val owner = findPlayer(grave.owner) ?: return
        if (!grave.items.isEmpty()) message(owner, GRAVE_COLLAPSED)
    }

    private fun check(player: Player, npc: Npc) {
        val grave = graves[npc] ?: return
        if (grave.owner != player.saveKey) {
            message(player, NOT_YOURS)
            return
        }
        val minutes = (grave.ticksLeft + TICKS_PER_MINUTE - 1) / TICKS_PER_MINUTE
        message(player, "Your grave will stand for $minutes more minute${if (minutes == 1) "" else "s"}.")
    }

    private fun loot(player: Player, npc: Npc) {
        val grave = graves[npc] ?: return
        if (grave.owner != player.saveKey) {
            message(player, NOT_YOURS)
            return
        }
        viewing[player.saveKey] = grave
        openModal(player, GRAVESTONE)
        setVarbitNow(player, VARBIT_FREE_FROM, 1)
        sendItems(player, grave)
        // The item slots' Take (op 1) and Examine (op 10), and the Take-All button; op bits in both event words, as
        // which word the client reads them from for script-made slots isn't known.
        send(player, IfSetEventsV2(GRAVESTONE, FREEITEMS, 0, GRAVE_CAPACITY - 1, (1 shl 1) or (1 shl 10), (1 shl 0) or (1 shl 9)))
        send(player, IfSetEventsV2(GRAVESTONE, FREEBUTTON, -1, -1, 1 shl 1, 1 shl 0))
    }

    private fun sendItems(player: Player, grave: Grave) {
        val slots = grave.items.contents().toMap()
        val count = (slots.keys.maxOrNull() ?: -1) + 1
        send(player, UpdateInvFull(GRAVE_INV, count) { slot -> slots[slot]?.let { InventoryObject(slot, it.id, it.count) } ?: InventoryObject.NULL })
    }

    /** The grave [player] has open now, or null. */
    private fun opened(player: Player): Grave? {
        val grave = viewing[player.saveKey] ?: return null
        if (!hasOpen(player) || graves[grave.npc] !== grave) return null
        return grave
    }

    private fun hasOpen(player: Player) = player.modals[FrameSlot.MAINMODAL] == GRAVESTONE

    private fun examineItem(player: Player, slot: Int) {
        val grave = opened(player) ?: return
        val obj = grave.items[slot] ?: return
        val type = objTypes[obj.id] ?: return
        examine(player, type, obj.count)
    }

    private fun take(player: Player, slot: Int) {
        val grave = opened(player) ?: return
        if (slot !in 0 until GRAVE_CAPACITY) return
        if (!takeSlot(player, grave, slot)) message(player, NO_SPACE)
        finishTaking(player, grave)
    }

    private fun takeAll(player: Player) {
        val grave = opened(player) ?: return
        for ((slot, _) in grave.items.contents()) {
            if (!takeSlot(player, grave, slot)) {
                message(player, NO_SPACE)
                break
            }
        }
        finishTaking(player, grave)
    }

    /** Moves what fits of [slot] into the inventory; false when none of it fit. */
    private fun takeSlot(player: Player, grave: Grave, slot: Int): Boolean {
        val obj = grave.items[slot] ?: return true
        val type = objTypes[obj.id] ?: ObjType(obj.id)
        val taken = player.inventory.add(type, obj.count)
        if (taken == 0) return false
        grave.items.remove(slot, taken)
        return true
    }

    /** An emptied grave goes; otherwise the viewer sees what is left. */
    private fun finishTaking(player: Player, grave: Grave) {
        if (grave.items.isEmpty()) {
            closeModals(player)
            graves.remove(grave.npc)
            removeNpc(grave.npc)
            viewing.values.removeIf { it === grave }
        } else {
            sendItems(player, grave)
        }
    }

    companion object {
        /** NPC "Grave" (cache rev 241): op1 Check, op3 Loot. 9857 is the same with an angel. */
        const val GRAVE = 9856
        const val CHECK = 1
        const val LOOT = 3

        /** Interface gravestone_generic: the free items' layer, their Take-All button. */
        const val GRAVESTONE = 672
        const val FREEITEMS = 6
        const val FREEBUTTON = 8

        /** The gravestone's inv: 120 slots. */
        const val GRAVE_INV = 525
        const val GRAVE_CAPACITY = 120

        /** The interface's free list starts at this varbit's value minus one; 1 makes every slot free. */
        const val VARBIT_FREE_FROM = 10472

        /** Examine on an item slot, as the inventory's. */
        const val EXAMINE_OP = 10

        /** human_death. */
        const val DEATH_ANIM = 836

        /** Ticks the player lies dead before the respawn. Guessed from the death animation's length. */
        const val DEATH_TICKS = 5

        /** The Wiki's three items kept on death with no Protect Item and no skull. */
        const val KEPT_ITEMS = 3

        /** 15 minutes (OSRS Wiki, Gravestone). */
        const val GRAVE_TICKS = 1500
        const val TICKS_PER_MINUTE = 100

        /** Guessed words; live's Check opens a timer interface, and a stranger's grave says something else. */
        const val NOT_YOURS = "That isn't your grave."
        const val GRAVE_COLLAPSED = "Your grave has collapsed, and what was in it is gone."
        const val NO_SPACE = "You don't have enough inventory space to take that."
    }
}
