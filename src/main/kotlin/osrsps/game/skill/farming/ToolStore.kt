package osrsps.game.skill.farming

import osrsps.game.FrameSlot
import osrsps.game.Interfaces
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.bank.getVarbitIfKnown
import osrsps.game.bank.setVarbitIfKnown
import osrsps.game.content.FarmsRoads
import osrsps.game.content.message
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.ScriptContext

/**
 * Our own lines for the tool store. The Wiki gives no words for any of these, so all are guessed and held empty for
 * the Words thread.
 */
object ToolStoreLines : Lines("tool_store", "Tool Leprechaun") {
    val NO_SPACE = line("no_space", "taking an item out of the tool store with no inventory space (guessed)")
    val FULL = line("full", "storing an item the tool store already holds as many of as it can (guessed)")
    val OTHER_KIND = line("other_kind", "storing secateurs while the other kind is stored (guessed)")
    val NOTHING = line("nothing", "Deposit-all, or the store's Deposit inventory, with nothing the store holds (guessed)")
}

/**
 * The Tool Leprechauns' store, the Amazing Farming Equipment Store (farming_tools 125, with farming_tools_side 126 in
 * place of the side panel). Opened by a leprechaun's Exchange and by "Yes please." in his talk; his Deposit-all puts
 * everything it holds from the pack into it without opening it.
 *
 * What is stored lives in the varbits the client draws it from (cache 242, farming_tools_getstored): rakes, dibbers,
 * spades, secateurs and trowels as one bit plus 2 times another 6, buckets as 5 bits plus 32 times 3 plus 256 times 2,
 * compost and supercompost as 8 bits plus 256 times 2, plant cure and ultracompost in 10 bits, the watering can as its
 * place in enum 136 (empty, 1 to 8 doses, Gricoller's can) and the bottomless bucket as a type. How many each holds is
 * enum 2193: 100 of each tool, 1 watering can, 1 bottomless bucket and 1000 of the rest, as the Wiki's Tool Leprechaun
 * says. Being varps, they are saved with the player and the same at every leprechaun (his magic shed).
 *
 * The menus are farming_tools_main_update's and farming_tools_side_update's: op 1 to 4 are Remove- or Store-1, -5, -X
 * and -All in an order set by the default quantity (varbit farming_tools_selectedquantity, which the quantity buttons
 * set: 0 for 1, 1 for 5, 2 for All, 3 for X), the quantity's own first; an item the store holds one of has op 1 alone
 * (Remove or Store). Op 9 is Banknotes, taking the items out noted (the Wiki: "Right-click the item in storage to
 * withdraw as a note"), and op 10 Examine.
 *
 * Guessed: Banknotes takes the default quantity; storing takes unnoted items before noted ones; secateurs and magic
 * secateurs can't be stored together (the stored kind is one flag), nor two watering cans; Remove-X asks every time (the
 * store keeps no last amount); a store or withdrawal that doesn't all fit moves what fits; every message (see
 * [ToolStoreLines]). Not built: the filled bottomless bucket (the server has no bottomless bucket yet; the empty one is
 * stored), as nothing on this server makes one.
 */
class ToolStore(private val world: World) {
    fun register() {
        with(FarmsRoads) {
            world.handlers.onNpc(TOOL_LEPRECHAUN, TALK_TO) { op ->
                var open = false
                world.dialogues.start(this, op.npc) { open = leprechaun() }
                if (open) open(player)
            }
            world.handlers.onNpc(TOOL_LEPRECHAUN, EXCHANGE) { open(player) }
            world.handlers.onNpc(TOOL_LEPRECHAUN, DEPOSIT_ALL) { depositAll(player) }
        }
        for (tool in Tool.entries) {
            world.handlers.onButton(MAIN, tool.mainComponent) { op -> clickMain(op, tool) }
            world.handlers.onButton(SIDE, tool.sideComponent) { op -> clickSide(op, tool) }
        }
        world.handlers.onButton(MAIN, DEPOSIT_INVENTORY) { if (isOpen(player)) depositAll(player) }
        for ((component, mode) in QUANTITY_BUTTONS) {
            world.handlers.onButton(MAIN, component) { if (isOpen(player)) player.vars.setVarbitIfKnown(VARBIT_QUANTITY, mode) }
        }
    }

    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == MAIN

    /** Opens the store, with its side panel in place of the inventory, as the bank opens. */
    fun open(player: Player) {
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, MAIN)
        world.interfaces.openModal(player, FrameSlot.SIDEMODAL, SIDE)
        // The quantity buttons' op 1 is set by their client script, not the cache, so the server lets it through, as
        // the item rows' ops are already (their transmitop). Without it the client changes the default quantity alone
        // (live run on 215, 2026-11-15: the client at All, the server still at 1).
        for (component in QUANTITY_BUTTONS.keys) world.interfaces.setEvents(player, MAIN, component, -1, -1, Interfaces.IF_EVENT_OP1)
    }

    private suspend fun ScriptContext.clickMain(op: Op, tool: Tool) {
        if (!isOpen(player)) return
        if (op.option == EXAMINE) return examine(player, stored(player, tool).second)
        if (player.scripts.delayed) return
        if (op.option == BANKNOTES) {
            if (tool.capacity > 1) withdraw(player, tool, amount(op.option, player, tool) ?: return, noted = true)
            return
        }
        withdraw(player, tool, amount(op.option, player, tool) ?: return, noted = false)
    }

    private suspend fun ScriptContext.clickSide(op: Op, tool: Tool) {
        if (!isOpen(player)) return
        if (op.option == EXAMINE) return examine(player, carried(player, tool).second)
        if (player.scripts.delayed) return
        store(player, tool, amount(op.option, player, tool) ?: return)
    }

    private fun examine(player: Player, id: Int) {
        if (id < 0) return
        world.objTypes[id]?.let { world.inventoryTab.examine(player, it, 1) }
    }

    /** How many op [option] moves: 1 for the store's one-item rows, else by the default quantity; X asks. */
    private suspend fun ScriptContext.amount(option: Int, player: Player, tool: Tool): Int? {
        if (tool.capacity <= 1) return if (option == 1) 1 else null
        val order = when (player.vars.getVarbitIfKnown(VARBIT_QUANTITY)) {
            MODE_5 -> listOf(5, 1, X, ALL)
            MODE_X -> listOf(X, 1, 5, ALL)
            MODE_ALL -> listOf(ALL, 1, 5, X)
            else -> listOf(1, 5, X, ALL)
        }
        val picked = if (option == BANKNOTES) order[0] else order.getOrNull(option - 1) ?: return null
        if (picked != X) return picked
        val typed = countDialog()
        return typed.takeIf { it > 0 && isOpen(player) }
    }

    /** Takes [count] of [tool] out, noted or not, as many as there are and as fit. */
    fun withdraw(player: Player, tool: Tool, count: Int, noted: Boolean) {
        val (have, id) = stored(player, tool)
        if (have <= 0 || id < 0) return
        val type = world.objTypes[id] ?: return
        val give = if (noted) world.objTypes[type.certlink] ?: type else type
        val fits = if (give.stackable) minOf(count, have) else minOf(count, have, player.inventory.freeSlots)
        val added = if (fits > 0) player.inventory.add(give, fits) else 0
        if (added <= 0) return world.message(player, ToolStoreLines.NO_SPACE)
        setStored(player, tool, have - added)
    }

    /** Puts [count] of [tool] from the pack into the store, unnoted first, as many as there are and as it holds. */
    fun store(player: Player, tool: Tool, count: Int): Int {
        val (carried, id) = carried(player, tool)
        if (carried <= 0 || id < 0) return 0
        val (have, storedId) = stored(player, tool)
        val room = tool.capacity - have
        if (room <= 0) {
            world.message(player, ToolStoreLines.FULL)
            return 0
        }
        if (have > 0 && storedId != id) {
            world.message(player, ToolStoreLines.OTHER_KIND)
            return 0
        }
        val wanted = minOf(count, room, carried)
        val unnoted = minOf(player.inventory.count(id), wanted.toLong()).toInt()
        if (unnoted > 0) Gardeners.removeCount(player.inventory, id, unnoted)
        val note = world.objTypes[id]?.certlink ?: -1
        if (wanted > unnoted && note >= 0) Gardeners.removeCount(player.inventory, note, wanted - unnoted)
        setStored(player, tool, have + wanted, id)
        return wanted
    }

    /** Deposit-all: every item the store holds from the pack, noted or not, as many as it has room for. */
    fun depositAll(player: Player) {
        if (player.scripts.delayed) return
        var any = false
        for (tool in Tool.entries) {
            if (carried(player, tool).first <= 0) continue
            any = true
            store(player, tool, Int.MAX_VALUE)
        }
        if (!any) world.message(player, ToolStoreLines.NOTHING)
    }

    /** How many of [tool] the pack holds, noted or not, and which item (farming_tools_getcarried). */
    fun carried(player: Player, tool: Tool): Pair<Int, Int> {
        val kinds = when (tool) {
            // Magic secateurs first, then the cans from the end of enum 136 (the Gricoller's can, then the fullest).
            Tool.SECATEURS -> listOf(Farming.MAGIC_SECATEURS, Farming.SECATEURS)
            Tool.WATERING_CAN -> WATERING_CANS.reversed()
            else -> listOf(tool.obj)
        }
        for (kind in kinds) {
            val count = countWithNotes(player, kind)
            if (count > 0) return count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() to kind
        }
        return 0 to -1
    }

    private fun countWithNotes(player: Player, id: Int): Long {
        val note = world.objTypes[id]?.certlink ?: -1
        return player.inventory.count(id) + if (note >= 0) player.inventory.count(note) else 0L
    }

    companion object {
        const val MAIN = 125
        const val SIDE = 126
        const val DEPOSIT_INVENTORY = 20
        const val EXAMINE = 10
        const val BANKNOTES = 9

        /** farming_tools_selectedquantity, as storage_create_quantitybuttons numbers the buttons. */
        const val VARBIT_QUANTITY = 7792
        const val MODE_1 = 0
        const val MODE_5 = 1
        const val MODE_ALL = 2
        const val MODE_X = 3

        /** quantity_1, quantity_5, quantity_x and quantity_all, and the mode each sets. */
        val QUANTITY_BUTTONS = mapOf(4 to MODE_1, 5 to MODE_5, 6 to MODE_X, 7 to MODE_ALL)

        private const val X = -1
        private const val ALL = Int.MAX_VALUE

        /** Enum 136: the watering can varbit's values 1 to 10. */
        val WATERING_CANS = listOf(Farming.WATERING_CAN_EMPTY) + Farming.WATERING_CANS + GRICOLLERS_CAN
        const val GRICOLLERS_CAN = 13353
        const val BOTTOMLESS_BUCKET = 22994
        const val BUCKET = 1925
        const val COMPOST = 6032
        const val SUPERCOMPOST = 6034
        const val ULTRACOMPOST = 21483

        const val VARBIT_RAKE = 1435
        const val VARBIT_DIBBER = 1436
        const val VARBIT_SPADE = 1437
        const val VARBIT_SECATEURS = 1438
        const val VARBIT_WATERING_CAN = 1439
        const val VARBIT_TROWEL = 1440
        const val VARBIT_BUCKETS = 1441
        const val VARBIT_COMPOST = 1442
        const val VARBIT_SUPERCOMPOST = 1443
        const val VARBIT_MAGIC_SECATEURS = 1848
        const val VARBIT_EXTRA_BUCKETS = 4731
        const val VARBIT_ULTRACOMPOST = 5732
        const val VARBIT_EXTRA2_BUCKETS = 6265
        const val VARBIT_EXTRA_COMPOST = 6266
        const val VARBIT_EXTRA_SUPERCOMPOST = 6267
        const val VARBIT_PLANT_CURE = 6268
        const val VARBIT_BOTTOMLESS_TYPE = 7915
        const val VARBIT_EXTRA_RAKES = 8357
        const val VARBIT_EXTRA_DIBBERS = 8358
        const val VARBIT_EXTRA_SECATEURS = 8359
        const val VARBIT_EXTRA_TROWELS = 8360
        const val VARBIT_EXTRA_SPADES = 8361

        /** How many of [tool] are stored and which item they are (farming_tools_getstored); -1 for none. */
        fun stored(player: Player, tool: Tool): Pair<Int, Int> {
            val vars = player.vars
            fun v(id: Int) = vars.getVarbitIfKnown(id)
            return when (tool) {
                Tool.RAKE -> 2 * v(VARBIT_EXTRA_RAKES) + v(VARBIT_RAKE) to tool.obj
                Tool.DIBBER -> 2 * v(VARBIT_EXTRA_DIBBERS) + v(VARBIT_DIBBER) to tool.obj
                Tool.SPADE -> 2 * v(VARBIT_EXTRA_SPADES) + v(VARBIT_SPADE) to tool.obj
                Tool.SECATEURS -> 2 * v(VARBIT_EXTRA_SECATEURS) + v(VARBIT_SECATEURS) to
                    if (v(VARBIT_MAGIC_SECATEURS) == 1) Farming.MAGIC_SECATEURS else Farming.SECATEURS
                Tool.WATERING_CAN -> {
                    val can = v(VARBIT_WATERING_CAN)
                    if (can <= 0) 0 to -1 else 1 to (WATERING_CANS.getOrNull(can - 1) ?: -1)
                }
                Tool.TROWEL -> 2 * v(VARBIT_EXTRA_TROWELS) + v(VARBIT_TROWEL) to tool.obj
                Tool.PLANT_CURE -> v(VARBIT_PLANT_CURE) to tool.obj
                Tool.BOTTOMLESS_BUCKET -> (if (v(VARBIT_BOTTOMLESS_TYPE) == 1) 1 else 0) to tool.obj
                Tool.BUCKET -> 256 * v(VARBIT_EXTRA2_BUCKETS) + 32 * v(VARBIT_EXTRA_BUCKETS) + v(VARBIT_BUCKETS) to tool.obj
                Tool.COMPOST -> 256 * v(VARBIT_EXTRA_COMPOST) + v(VARBIT_COMPOST) to tool.obj
                Tool.SUPERCOMPOST -> 256 * v(VARBIT_EXTRA_SUPERCOMPOST) + v(VARBIT_SUPERCOMPOST) to tool.obj
                Tool.ULTRACOMPOST -> v(VARBIT_ULTRACOMPOST) to tool.obj
            }
        }

        /** Sets how many of [tool] are stored, [id] being which item when it matters (secateurs, the watering can). */
        fun setStored(player: Player, tool: Tool, count: Int, id: Int = stored(player, tool).second) {
            val vars = player.vars
            fun split(base: Int, extra: Int) {
                vars.setVarbitIfKnown(base, count and 1)
                vars.setVarbitIfKnown(extra, count shr 1)
            }
            when (tool) {
                Tool.RAKE -> split(VARBIT_RAKE, VARBIT_EXTRA_RAKES)
                Tool.DIBBER -> split(VARBIT_DIBBER, VARBIT_EXTRA_DIBBERS)
                Tool.SPADE -> split(VARBIT_SPADE, VARBIT_EXTRA_SPADES)
                Tool.SECATEURS -> {
                    split(VARBIT_SECATEURS, VARBIT_EXTRA_SECATEURS)
                    vars.setVarbitIfKnown(VARBIT_MAGIC_SECATEURS, if (count > 0 && id == Farming.MAGIC_SECATEURS) 1 else 0)
                }
                Tool.WATERING_CAN -> vars.setVarbitIfKnown(VARBIT_WATERING_CAN, if (count > 0) WATERING_CANS.indexOf(id) + 1 else 0)
                Tool.TROWEL -> split(VARBIT_TROWEL, VARBIT_EXTRA_TROWELS)
                Tool.PLANT_CURE -> vars.setVarbitIfKnown(VARBIT_PLANT_CURE, count)
                Tool.BOTTOMLESS_BUCKET -> vars.setVarbitIfKnown(VARBIT_BOTTOMLESS_TYPE, if (count > 0) 1 else 0)
                Tool.BUCKET -> {
                    vars.setVarbitIfKnown(VARBIT_BUCKETS, count and 31)
                    vars.setVarbitIfKnown(VARBIT_EXTRA_BUCKETS, (count shr 5) and 7)
                    vars.setVarbitIfKnown(VARBIT_EXTRA2_BUCKETS, count shr 8)
                }
                Tool.COMPOST -> {
                    vars.setVarbitIfKnown(VARBIT_COMPOST, count and 255)
                    vars.setVarbitIfKnown(VARBIT_EXTRA_COMPOST, count shr 8)
                }
                Tool.SUPERCOMPOST -> {
                    vars.setVarbitIfKnown(VARBIT_SUPERCOMPOST, count and 255)
                    vars.setVarbitIfKnown(VARBIT_EXTRA_SUPERCOMPOST, count shr 8)
                }
                Tool.ULTRACOMPOST -> vars.setVarbitIfKnown(VARBIT_ULTRACOMPOST, count)
            }
        }
    }

    /**
     * What the store holds, with its row in farming_tools and farming_tools_side and how many it holds (enum 2193).
     * [obj] is the item of the row (the empty can and plain secateurs for the rows that take more than one).
     */
    enum class Tool(val obj: Int, val mainComponent: Int, val sideComponent: Int, val capacity: Int) {
        RAKE(Farming.RAKE, 8, 1, 100),
        DIBBER(Farming.SEED_DIBBER, 9, 2, 100),
        SPADE(Farming.SPADE, 10, 3, 100),
        SECATEURS(Farming.SECATEURS, 11, 4, 100),
        WATERING_CAN(Farming.WATERING_CAN_EMPTY, 12, 5, 1),
        TROWEL(Farming.TROWEL, 13, 6, 100),
        PLANT_CURE(Farming.PLANT_CURE, 14, 7, 1000),
        BOTTOMLESS_BUCKET(ToolStore.BOTTOMLESS_BUCKET, 15, 8, 1),
        BUCKET(ToolStore.BUCKET, 16, 9, 1000),
        COMPOST(ToolStore.COMPOST, 17, 10, 1000),
        SUPERCOMPOST(ToolStore.SUPERCOMPOST, 18, 11, 1000),
        ULTRACOMPOST(ToolStore.ULTRACOMPOST, 19, 12, 1000),
    }
}
