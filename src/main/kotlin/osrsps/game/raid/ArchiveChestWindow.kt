package osrsps.game.raid

import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.bank.BankScreen
import osrsps.game.combat.Drop
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Dialogues
import osrsps.game.dialogue.Line
import osrsps.game.script.ScriptRunner
import osrsps.game.sendInventory

/**
 * The window a chest of the reward vault opens (raid step 29b): `tfa_chests` (973) over the player's unclaimed loot,
 * [Player.archiveChest], which the client draws from inv `tfa_chests` (1029, six slots).
 *
 * - The layout is the cache's (osrs-dumps, `interface/tfa_chests.if3`): `items` (973:11), the layer the server fills by
 *   `interface_inv_init` (149); `bank` (973:5, op 6 Bank-all), `inventory` (973:7, op 1 Take-all) and `discard`
 *   (973:9, op 6 Discard-all). "A loot interface then opens from which items can be banked, taken into the inventory,
 *   or destroyed" (the Wiki's chest page).
 * - Guessed: the grid as two columns of three (it fits the layer's 92 by 119 and the inv's six slots); an item's
 *   options, Take, Take-5, Take-10 and Take-All (the tsps capture notes' own estimate, "as the Tombs of Amascut's");
 *   that Discard-all asks first (tsps capture notes) with a Yes and a No; that Bank-all says what a deposit box does
 *   when the bank is full.
 * - Taken to the inventory, an item that doesn't stack comes noted when it has a note; banked, it goes in as the item
 *   (tsps capture notes: "bulky drops come noted; banked, they're unnoted"). The Wiki's table marks none as noted.
 * - "If a player leaves the room before claiming their loot, it will be held in the loot wheelbarrow in the lobby"
 *   (the Wiki): the loot is the player's, saved with them, and the lobby's Loot Wheelbarrow (`tfa_rewards_chest_lobby`,
 *   a multiloc on `tfa_has_claimable_loot`: Search only while there is any) opens the same window. Guessed: that it is
 *   the same window, and that the varbit is set whenever any loot waits, in the vault too.
 */
class ArchiveChestWindow(
    private val objTypes: ObjTypes,
    private val openModal: (Player, FrameSlot, Int) -> Unit,
    private val closeModals: (Player) -> Unit,
    private val setVarbit: (Player, Int, Int) -> Unit,
    private val bankScreen: BankScreen,
    private val scripts: ScriptRunner,
    /** The world's dialogues, made after this is. */
    private val dialogues: () -> Dialogues,
    private val examine: (Player, Int, Int) -> Unit = { _, _, _ -> },
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == ID

    /** Whether [player] has loot of an earlier run still to claim. */
    fun waiting(player: Player): Boolean = !player.archiveChest.isEmpty()

    /** A run's end: [drops] are [player]'s loot to claim, each a stack of its own whatever the item. */
    fun fill(player: Player, drops: List<Drop>) {
        player.archiveChest.clear()
        for ((slot, drop) in drops.take(SLOTS).withIndex()) player.archiveChest[slot] = InvObj(drop.type.id, drop.count)
        showClaimable(player)
    }

    /** `tfa_has_claimable_loot`, which shows the lobby's Loot Wheelbarrow open: 1 while any loot waits. */
    fun showClaimable(player: Player) = setVarbit(player, CLAIMABLE, if (waiting(player)) 1 else 0)

    fun open(player: Player) {
        closeModals(player)
        openModal(player, FrameSlot.MAINMODAL, ID)
        send(player, RunClientScript(INTERFACE_INV_INIT_SCRIPT, listOf(ID shl 16 or ITEMS, INV, COLUMNS, ROWS, 0, -1, "Take", "Take-5", "Take-10", "Take-All", "")))
        send(player, IfSetEventsV2(ID, ITEMS, 0, SLOTS - 1, 0, BankScreen.opMask(1..4) or BankScreen.opMask(10..10)))
        send(player, IfSetEventsV2(ID, BANK, -1, -1, 0, BankScreen.opMask(BANK_OP..BANK_OP)))
        send(player, IfSetEventsV2(ID, INVENTORY, -1, -1, 0, BankScreen.opMask(INVENTORY_OP..INVENTORY_OP)))
        send(player, IfSetEventsV2(ID, DISCARD, -1, -1, 0, BankScreen.opMask(DISCARD_OP..DISCARD_OP)))
        player.archiveChest.resendAll()
    }

    /** Sends the loot while the window is open. Run at the end of every cycle. */
    fun flush(player: Player) {
        if (isOpen(player)) sendInventory(player, player.archiveChest, send)
    }

    /** A click on the window. Returns false when the click isn't on it. */
    fun click(player: Player, interfaceId: Int, component: Int, sub: Int, op: Int, obj: Int = -1): Boolean {
        if (interfaceId != ID) return false
        if (!isOpen(player) || player.scripts.delayed) return true
        when (component) {
            ITEMS -> clickItem(player, sub, op, obj)
            INVENTORY -> if (op == INVENTORY_OP) takeAll(player)
            BANK -> if (op == BANK_OP) {
                bankScreen.depositAll(player, player.archiveChest)
                showClaimable(player)
            }
            DISCARD -> if (op == DISCARD_OP && waiting(player)) {
                scripts.start(player) {
                    dialogues().start(this) {
                        val yes = choice(ArchiveVaultLines.DISCARD_YES, ArchiveVaultLines.DISCARD_NO, title = text(ArchiveVaultLines.DISCARD_ASK)) == 1
                        if (yes) {
                            player.archiveChest.clear()
                            showClaimable(player)
                        }
                    }
                }
            }
        }
        return true
    }

    private fun clickItem(player: Player, slot: Int, op: Int, clicked: Int) {
        if (slot !in 0 until SLOTS) return
        // A click sent before the slot changed is for an item no longer there.
        val obj = player.archiveChest[slot]?.takeIf { it.id == clicked } ?: return
        when (op) {
            10 -> examine(player, obj.id, obj.count)
            1 -> take(player, slot, 1)
            2 -> take(player, slot, 5)
            3 -> take(player, slot, 10)
            4 -> take(player, slot, Int.MAX_VALUE)
        }
    }

    /** Up to [count] of [slot]'s item into the inventory: as many as fit. Returns whether all of them went. */
    private fun take(player: Player, slot: Int, count: Int, quiet: Boolean = false): Boolean {
        val obj = player.archiveChest[slot] ?: return true
        val item = objTypes[obj.id] ?: return true
        val type = if (item.stackable) item else noteOf(item) ?: item
        val wanted = minOf(count, obj.count)
        val taken = player.inventory.add(type, wanted)
        if (taken > 0) player.archiveChest.remove(slot, taken)
        if (taken < wanted && !quiet) message(player, ArchiveVaultLines.NO_ROOM)
        showClaimable(player)
        return taken == wanted
    }

    private fun noteOf(type: ObjType): ObjType? = objTypes[type.certlink]?.takeIf { it.isCert && it.certlink == type.id }

    private fun takeAll(player: Player) {
        var all = true
        for ((slot, _) in player.archiveChest.contents()) all = take(player, slot, Int.MAX_VALUE, quiet = true) && all
        if (!all) message(player, ArchiveVaultLines.NO_ROOM)
    }

    private fun message(player: Player, line: Line) = send(player, MessageGame(CHATTYPE_GAMEMESSAGE, words(player)[line]))

    companion object {
        /** tfa_chests, the interface, and its inv of the same name. */
        const val ID = 973
        const val INV = 1029
        const val SLOTS = 6

        /** tfa_chests:bank (Bank-all, op 6), :inventory (Take-all, op 1), :discard (Discard-all, op 6) and :items. */
        const val BANK = 5
        const val INVENTORY = 7
        const val DISCARD = 9
        const val ITEMS = 11
        const val BANK_OP = 6
        const val INVENTORY_OP = 1
        const val DISCARD_OP = 6

        /** The grid interface_inv_init draws in :items. Guessed. */
        const val COLUMNS = 2
        const val ROWS = 3
        const val INTERFACE_INV_INIT_SCRIPT = 149

        /** tfa_has_claimable_loot. */
        const val CLAIMABLE = 16416

        /** tfa_rewards_chest_lobby, the lobby's Loot Wheelbarrow at 4136,5551, and what it shows with loot waiting: Search. */
        const val WHEELBARROW = 63299
        const val WHEELBARROW_OPEN = 63301
        const val SEARCH = 1

        private const val CHATTYPE_GAMEMESSAGE = 0
    }
}
