package osrsps.game

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.game.outgoing.inv.UpdateInvPartial
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner

private val logger = KotlinLogging.logger {}

/**
 * The inventory side panel, inventory:items (149:0): sending the player's inventory, and clicks, drags and examines
 * on its slots. The panel itself is opened with the game frame.
 */
class InventoryTab(
    private val objTypes: ObjTypes,
    private val scripts: ScriptRunner,
    private val closeModals: (Player) -> Unit,
    /** Wears the item in a slot: what Wear, Wield and Equip do when no content handler takes them. */
    private val wear: (Player, Int) -> Unit = { _, _ -> },
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    /** Lets the client send clicks, drags and "Use" on the 28 slots. Sent once the panel is open on login. */
    fun open(player: Player) {
        send(player, IfSetEventsV2(INTERFACE, ITEMS, 0, Inventory.INV_CAPACITY - 1, EVENTS_LOW, EVENTS_HIGH))
    }

    /** Sends the inventory if it changed: every slot the first time, then the changed ones. */
    fun flush(player: Player) = sendInventory(player, player.inventory, send)

    /**
     * A click on an inventory slot's option. The button op is the menu entry the client built: 2, 3, 4, 6 and 7 are
     * the item's iop1 to iop5 and 10 is Examine, as RSMod (rev 233) maps them; "Use" arrives as a target click.
     * [obj] is what the client showed in the slot; a click on anything else is dropped (inferred: the client was
     * behind).
     */
    fun click(player: Player, slot: Int, obj: Int, buttonOp: Int) {
        val held = held(player, slot, obj) ?: return
        val type = objTypes[held.id]
        if (type == null) {
            logger.warn { "${player.username} clicked unknown item ${held.id} in slot $slot" }
            return
        }
        if (buttonOp == EXAMINE_OP) {
            examine(player, type, held.count)
            return
        }
        val option = IOP_BY_BUTTON_OP[buttonOp]
        if (option == null || type.iop(option) == null) {
            logger.info { "${player.username} sent op $buttonOp on $type, which has no such option" }
            return
        }
        // Clicks while delayed do nothing, as everywhere else (ScriptRunner.trigger); this also covers unhandled options.
        if (player.scripts.delayed) return
        // Clicking an item closes any open modal, as RSMod does. It doesn't stop the player walking.
        closeModals(player)
        if (scripts.trigger(player, Op(OpTarget.OBJ, type.id, option, slot))) return
        if (type.wearable && type.iop(option) in Equipment.WEAR_OPTIONS) {
            wear(player, slot)
            return
        }
        logger.info { "${player.username} picked ${type.iop(option)} on $type in slot $slot (not handled)" }
        nothingInteresting(player)
    }

    /** The player dragged the item in [from] onto slot [to]. The inventory always swaps (the bank can insert). */
    fun drag(player: Player, from: Int, fromObj: Int, to: Int) {
        if (from == to || to !in 0 until Inventory.INV_CAPACITY) return
        if (held(player, from, fromObj) == null) return
        if (player.scripts.delayed) {
            // Inferred: a delayed player's drag is refused, with no other effect, and the client, which may have moved
            // the item already, is sent both slots again.
            player.inventory.resend(from)
            player.inventory.resend(to)
            return
        }
        closeModals(player)
        player.inventory.swap(from, to)
    }

    /** The player used one inventory item on another. Nothing handles that yet. */
    fun use(player: Player, from: Int, fromObj: Int, to: Int, toObj: Int) {
        if (held(player, from, fromObj) == null || held(player, to, toObj) == null) return
        // Ignored while delayed, like other clicks.
        if (player.scripts.delayed) return
        closeModals(player)
        logger.info { "${player.username} used $fromObj on $toObj (not handled)" }
        nothingInteresting(player)
    }

    /**
     * Examine runs client script 6003, which in the 241 cache takes (obj, count, desc, tradeable, price as a long,
     * alchemisable, high alch, low alch) and writes the examine text and the item's values to the chatbox, as RSMod
     * calls it (rev 233, without the long). The argument types are read from the 241 script's header and its call to
     * 6004.
     *
     * Guessed: the Grand Exchange price is a live market figure the server doesn't have, so the item's value stands in.
     */
    fun examine(player: Player, type: ObjType, count: Int) {
        val alchemisable = if (type.intParam(ObjType.PARAM_NO_ALCHEMY) == 0) 1 else 0
        val args = listOf(
            type.id, count, type.desc ?: "", if (type.tradeable) 1 else 0, type.cost.toLong(),
            alchemisable, type.highAlch, type.lowAlch,
        )
        send(player, RunClientScript(EXAMINE_SCRIPT, args))
    }

    private fun held(player: Player, slot: Int, obj: Int): InvObj? {
        if (slot !in 0 until Inventory.INV_CAPACITY) return null
        val held = player.inventory[slot]
        if (held == null || held.id != obj) {
            logger.debug { "${player.username} clicked slot $slot as $obj, but it holds $held" }
            return null
        }
        return held
    }

    /** Live's answer to an option or "Use" that does nothing. */
    private fun nothingInteresting(player: Player) {
        send(player, MessageGame(CHATTYPE_GAMEMESSAGE, NOTHING_INTERESTING))
    }

    companion object {
        /** inventory and inventory:items in the 241 cache's gameval index. */
        const val INTERFACE = 149
        const val ITEMS = 0

        const val EXAMINE_OP = 10
        const val EXAMINE_SCRIPT = 6003
        const val NOTHING_INTERESTING = "Nothing interesting happens."
        private const val CHATTYPE_GAMEMESSAGE = 0

        /** Button op to iop number. */
        val IOP_BY_BUTTON_OP = mapOf(2 to 1, 3 to 2, 4 to 3, 6 to 4, 7 to 5)

        /**
         * The slots' event mask, as RSMod sets it on rev 233: in the low word "Use" on ground items, NPCs, locs,
         * players, inventory items and components (bits 11 to 16), drag depth 1 (bit 17), drag target (20) and
         * targetable (21); in the high word ops 2, 3, 4, 6, 7 and 10 (op n is bit n - 1).
         */
        val EVENTS_LOW: Int = (11..16).fold(0) { mask, bit -> mask or (1 shl bit) } or (1 shl 17) or (1 shl 20) or (1 shl 21)
        val EVENTS_HIGH: Int = listOf(2, 3, 4, 6, 7, 10).fold(0) { mask, op -> mask or (1 shl (op - 1)) }
    }
}

/** Sends [inv] to [player] if it changed: every slot the first time, then the changed ones. */
internal fun sendInventory(player: Player, inv: Inventory, send: (Player, OutgoingGameMessage) -> Unit) {
    inv.flush(
        full = { slots ->
            // Only up to the last item; the client empties the inventory before filling it.
            val count = slots.indexOfLast { it != null } + 1
            send(player, UpdateInvFull(inv.type, count) { slot -> slots[slot]?.let { InventoryObject(slot, it.id, it.count) } ?: InventoryObject.NULL })
        },
        partial = { changed ->
            val bySlot = changed.toMap()
            send(player, UpdateInvPartial(inv.type, object : UpdateInvPartial.IndexedObjectProvider(changed.map { it.first }.iterator()) {
                // The partial update's encoder writes an id of -1 as an empty slot.
                override fun provide(slot: Int): Long = bySlot[slot]?.let { InventoryObject(slot, it.id, it.count) } ?: InventoryObject(slot, -1, 0)
            }))
        },
    )
}
