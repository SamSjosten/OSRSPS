package osrsps.game.skill.hunter

import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import osrsps.game.FrameSlot
import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.content.message

/** One of Asyff's fur clothing pieces: [fee] coins and [count] of the first of [furs] the player has, in that order. */
class FurPiece(val item: Int, val fee: Int, val furs: List<Int>, val count: Int = 1)

/**
 * Hunter step 9's pieces outside the shops themselves (those are shops.json's: Aleck's Hunter Emporium in Yanille, the
 * Nardah Hunter Shop, Elder Strom's Hunting Stall and Bea's Hunting Gear, with npc-spawns/hunter-shops.json):
 *
 * - The Hunter packs' Open: 100 bird snares, box traps or magic boxes (stackable, so not noted; OSRS Wiki, Item pack).
 * - Asyff's Fur Clothing (op4): the Custom Fur Clothing panel (interface hunting_customfurs, its items from inv
 *   hunting_customfurshop), each piece for its fee and furs as the Wiki's Fancy Clothes Store page lists them, a tatty fur
 *   used before a perfect one (since 15 May 2024).
 *
 * Guessed: the panel's items in the Wiki table's order in the inv, Value as the item's op1 and Buy as its op2 (the
 * panel's client script isn't in our sources), what Value and Buy say, a pack needing a free slot (or a stack of its
 * item) and opening one at a time. Left out: opening the rest of a stack of packs on its own, the Hunter Guild's Imia, the
 * shopkeepers' Talk-to, Leon's crossbows, Bea's Swap-darts.
 */
class HunterShops(private val world: World) {
    fun register() {
        for ((pack, item) in PACKS) {
            world.handlers.onObj(pack, OPEN) { op ->
                if (player.inventory[op.sub]?.id != pack) return@onObj
                val type = world.objTypes[item] ?: return@onObj
                val room = player.inventory.count(item) > 0 || player.inventory.freeSlots > 0
                if (!room) return@onObj world.message(player, HunterLines.PACK_SPACE)
                player.inventory.remove(op.sub, 1)
                player.inventory.add(type, PACK_COUNT)
            }
        }
        world.handlers.onNpc(ASYFF, FUR_CLOTHING) { open(player) }
        world.handlers.onButton(PANEL, PANEL_ITEMS) { op ->
            if (PANEL !in player.modals.values) return@onButton
            val piece = PIECES.firstOrNull { it.item == op.obj } ?: PIECES.getOrNull(op.sub) ?: return@onButton
            when (op.option) {
                VALUE -> world.message(player, HunterLines.FUR_VALUE, "fee" to piece.fee, "count" to piece.count)
                BUY -> buy(player, piece)
                EXAMINE -> world.objTypes[piece.item]?.let { world.inventoryTab.examine(player, it, 1) }
            }
        }
    }

    /** Opens the Custom Fur Clothing panel with its pieces. */
    fun open(player: Player) {
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, PANEL)
        player.session.queue(UpdateInvFull(PANEL_INV, PIECES.size) { slot -> InventoryObject(slot, PIECES[slot].item, 1) })
        world.interfaces.setEvents(player, PANEL, PANEL_ITEMS, 0, PIECES.size - 1, BankScreen.opMask(VALUE..BUY) or BankScreen.opMask(EXAMINE..EXAMINE))
    }

    /** Buys [piece]: its fee and furs, the piece in the furs' slot. */
    fun buy(player: Player, piece: FurPiece): Boolean {
        val inv = player.inventory
        val fur = piece.furs.firstOrNull { inv.count(it) >= piece.count }
        if (fur == null) {
            world.message(player, HunterLines.FUR_NEEDED, "count" to piece.count)
            return false
        }
        if (inv.count(COINS) < piece.fee) {
            world.message(player, HunterLines.FUR_COINS, "fee" to piece.fee)
            return false
        }
        var fee = piece.fee
        for ((slot, held) in inv.contents()) {
            if (fee == 0) break
            if (held.id == COINS) fee -= inv.remove(slot, fee)
        }
        var furs = piece.count
        var freed = -1
        for ((slot, held) in inv.contents()) {
            if (furs == 0) break
            if (held.id != fur) continue
            furs -= inv.remove(slot, furs)
            if (inv[slot] == null && freed < 0) freed = slot
        }
        val type = world.objTypes[piece.item] ?: return false
        if (freed >= 0) inv[freed] = InvObj(piece.item, 1) else world.groundObjs.give(player, type, 1)
        return true
    }

    companion object {
        const val OPEN = 1
        const val PACK_COUNT = 100

        /** Bird snare pack, box trap pack and magic imp box pack, and what each holds. */
        val PACKS = mapOf(12740 to 10006, 12742 to 10008, 12744 to 10025)

        const val ASYFF = 2887
        const val FUR_CLOTHING = 4
        const val PANEL = 477 // hunting_customfurs
        const val PANEL_ITEMS = 6 // choices_items
        const val PANEL_INV = 482 // hunting_customfurshop
        const val VALUE = 1
        const val BUY = 2
        const val EXAMINE = 10
        const val COINS = 995

        private const val POLAR = 10117
        private const val COMMON = 10121
        private const val FELDIP = 10119
        private const val DESERT = 10123
        private const val LARUPIA = 10095
        private const val TATTY_LARUPIA = 10093
        private const val GRAAHK = 10099
        private const val TATTY_GRAAHK = 10097
        private const val KYATT = 10103
        private const val TATTY_KYATT = 10101

        /** The Wiki's Custom Fur Clothing table, in its order. */
        val PIECES = listOf(
            FurPiece(10065, 20, listOf(POLAR), 2), FurPiece(10067, 20, listOf(POLAR), 2),
            FurPiece(10053, 20, listOf(COMMON), 2), FurPiece(10055, 20, listOf(COMMON), 2),
            FurPiece(10057, 20, listOf(FELDIP), 2), FurPiece(10059, 20, listOf(FELDIP), 2),
            FurPiece(10061, 20, listOf(DESERT), 2), FurPiece(10063, 20, listOf(DESERT), 2),
            FurPiece(10045, 500, listOf(LARUPIA)), FurPiece(10043, 100, listOf(TATTY_LARUPIA, LARUPIA)), FurPiece(10041, 100, listOf(TATTY_LARUPIA, LARUPIA)),
            FurPiece(10051, 750, listOf(GRAAHK)), FurPiece(10049, 150, listOf(TATTY_GRAAHK, GRAAHK)), FurPiece(10047, 150, listOf(TATTY_GRAAHK, GRAAHK)),
            FurPiece(10039, 1000, listOf(KYATT)), FurPiece(10037, 200, listOf(TATTY_KYATT, KYATT)), FurPiece(10035, 200, listOf(TATTY_KYATT, KYATT)),
            FurPiece(10075, 600, listOf(10115), 2), FurPiece(10069, 400, listOf(10125), 2), FurPiece(10071, 800, listOf(10127), 2),
        )
    }
}
