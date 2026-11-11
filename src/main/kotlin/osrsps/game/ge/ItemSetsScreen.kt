package osrsps.game.ge

import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.bank.BankScreen
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/** The item sets window's messages. The Wiki quotes none, so every one's words are guessed. */
object ItemSetsLines : Lines("itemsets", "Item sets") {
    val NO_ROOM = line("no_room", "Pack or Unpack with too little room in the backpack for what comes out")
    val CONTENTS = line("contents", "Contents on a set the player hasn't every piece of: {set} the set's name, {pieces} its pieces' names", "set", "pieces")
}

/** One item set: [set] the set's item (-1 where the cache has none), [pieces] what it packs, in the cache's order. */
data class ItemSet(val index: Int, val set: Int, val pieces: List<Int>)

/** The cache's item sets, in the window's order. */
class ItemSets(val all: List<ItemSet>) {
    private val bySet = all.filter { it.set >= 0 }.associateBy { it.set }

    /** The set whose item is [obj], unnoted; null when [obj] isn't a set. */
    fun ofSet(obj: Int): ItemSet? = bySet[obj]

    val size: Int get() = all.size

    companion object {
        val NONE = ItemSets(emptyList())
    }
}

/**
 * The Grand Exchange clerks' item sets window, `itemsets` (451) with `itemsets_side` (430) beside it: Sets on a clerk,
 * or Item sets in a clerk's talk. The client draws the list from the cache's enums ([ItemSets]) and the backpack, and
 * offers Pack on a set when the backpack holds one of every piece, all unnoted or all noted, and Contents when it
 * doesn't; beside it, Unpack on a set in the backpack (osrs-dumps, rev 241: `itemsets_draw`, `itemsets_side_draw`).
 *
 * - **Pack** (`itemsets:itemlist`, slot = the set's index, op 1) takes one of each piece and gives the set: unnoted
 *   pieces make an unnoted set and, failing those, noted pieces a noted set (the Wiki's Item sets page: "Noted
 *   components produce a noted set").
 * - **Unpack** (`itemsets_side:items`, slot = the backpack slot, op 1) does the reverse for one set, unnoted or noted.
 * - **Contents** (op 1 on a set the client greys) names the pieces in a game message: guessed, as the Wiki doesn't say
 *   what it shows.
 * - Either way one set at a time, and nothing changes when what comes out doesn't fit ([ItemSetsLines.NO_ROOM]).
 * - The filter button toggles `itemsets_show_only_craftable` (varbit 17803) in the client, which sends the click; the
 *   server keeps the varbit so the choice lasts.
 *
 * Guessed: the messages, that Pack and Unpack say nothing when they work, and one set per click.
 */
class ItemSetsScreen(
    private val sets: ItemSets,
    private val objTypes: ObjTypes,
    private val openModal: (Player, FrameSlot, Int) -> Unit,
    private val closeModals: (Player) -> Unit,
    private val toggleFilter: (Player) -> Unit = {},
    private val examine: (Player, Int, Int) -> Unit = { _, _, _ -> },
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == MAIN

    fun open(player: Player) {
        closeModals(player)
        openModal(player, FrameSlot.MAINMODAL, MAIN)
        openModal(player, FrameSlot.SIDEMODAL, SIDE)
        if (sets.size > 0) send(player, IfSetEventsV2(MAIN, ITEMLIST, 0, sets.size - 1, 0, OPS))
        send(player, IfSetEventsV2(SIDE, SIDE_ITEMS, 0, Inventory.INV_CAPACITY - 1, 0, OPS))
    }

    /** A click on the window or the backpack beside it. Returns false when the click isn't on either. */
    fun click(player: Player, interfaceId: Int, component: Int, sub: Int, op: Int, obj: Int = -1): Boolean {
        if (interfaceId != MAIN && interfaceId != SIDE) return false
        if (!isOpen(player) || player.scripts.delayed) return true
        when {
            interfaceId == MAIN && component == FILTER && op == 1 -> toggleFilter(player)
            interfaceId == MAIN && component == ITEMLIST -> {
                val set = sets.all.getOrNull(sub)?.takeIf { it.set >= 0 } ?: return true
                when (op) {
                    1 -> pack(player, set)
                    10 -> examine(player, set.set, 1)
                }
            }
            interfaceId == SIDE && component == SIDE_ITEMS -> {
                if (sub !in 0 until Inventory.INV_CAPACITY) return true
                // A click sent before the slot changed is for an item no longer there.
                val held = player.inventory[sub]?.takeIf { it.id == obj } ?: return true
                when (op) {
                    1 -> unpack(player, sub, held)
                    10 -> examine(player, held.id, held.count)
                }
            }
        }
        return true
    }

    private fun pack(player: Player, set: ItemSet) {
        val inv = player.inventory
        val pieces = set.pieces.mapNotNull { objTypes[it] }
        val setType = objTypes[set.set] ?: return
        if (pieces.size != set.pieces.size || pieces.isEmpty()) return
        val noted = when {
            pieces.all { inv.count(it.id) > 0 } -> false
            pieces.all { it.certlink >= 0 && inv.count(it.certlink) > 0 } -> true
            else -> {
                message(player, ItemSetsLines.CONTENTS, "set" to setType.name, "pieces" to pieces.joinToString(", ") { it.name })
                return
            }
        }
        val take = pieces.map { if (noted) it.certlink else it.id }
        val give = if (noted) objTypes[setType.certlink] ?: return else setType
        exchange(player, take, listOf(give))
    }

    private fun unpack(player: Player, slot: Int, held: InvObj) {
        val heldType = objTypes[held.id] ?: return
        val noted = heldType.isCert
        val set = sets.ofSet(if (noted) heldType.certlink else held.id) ?: return
        val pieces = set.pieces.map { objTypes[it] ?: return }
        val give = if (noted) pieces.map { objTypes[it.certlink] ?: return } else pieces
        exchange(player, listOf(held.id), give, fromSlot = slot)
    }

    /**
     * Takes one of each of [take] (the first from [fromSlot] when given) and gives one of each of [give]; when what's
     * given doesn't all fit, puts the backpack back as it was.
     */
    private fun exchange(player: Player, take: List<Int>, give: List<ObjType>, fromSlot: Int = -1) {
        val inv = player.inventory
        val before = (0 until inv.capacity).map { inv[it] }
        for ((i, id) in take.withIndex()) {
            val slot = if (i == 0 && fromSlot >= 0 && inv[fromSlot]?.id == id) fromSlot else (0 until inv.capacity).firstOrNull { inv[it]?.id == id }
            if (slot == null || inv.remove(slot, 1) != 1) return restore(inv, before)
        }
        if (give.any { inv.add(it, 1) != 1 }) {
            restore(inv, before)
            message(player, ItemSetsLines.NO_ROOM)
        }
    }

    private fun restore(inv: Inventory, before: List<InvObj?>) {
        for ((slot, obj) in before.withIndex()) inv[slot] = obj
    }

    private fun message(player: Player, line: Line, vararg values: Pair<String, Any>) =
        send(player, MessageGame(CHATTYPE_GAMEMESSAGE, words(player).get(line, *values)))

    companion object {
        /** itemsets and itemsets_side. */
        const val MAIN = 451
        const val SIDE = 430

        /** itemsets:itemlist and itemsets:filter_button; itemsets_side:items. */
        const val ITEMLIST = 2
        const val FILTER = 7
        const val SIDE_ITEMS = 0

        /** itemsets_show_only_craftable. */
        const val FILTER_VARBIT = 17803

        private val OPS = BankScreen.opMask(1..1) or BankScreen.opMask(10..10)
        private const val CHATTYPE_GAMEMESSAGE = 0

        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            MAIN to ("itemsets" to mapOf(ITEMLIST to "itemlist", FILTER to "filter_button")),
            SIDE to ("itemsets_side" to mapOf(SIDE_ITEMS to "items")),
        )
    }
}
