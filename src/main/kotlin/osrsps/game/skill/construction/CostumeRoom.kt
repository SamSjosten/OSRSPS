package osrsps.game.skill.construction

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import osrsps.game.FrameSlot
import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/** A costume set: its key object (the one the list names) and its pieces, each with the alternates that count as it. */
class CostumeSet(val key: Int, val pieces: List<List<Int>>) {
    val objs: Set<Int> = pieces.flatten().toSet()
}

/** One of the storage window's lists (enums 3289 to 3300): its sets in the window's order. */
class CostumeList(val id: Int, val sets: List<CostumeSet>) {
    private val byObj: Map<Int, CostumeSet> = buildMap { for (set in sets.asReversed()) for (obj in set.objs) put(obj, set) }

    /** The set [obj] belongs to in this list, or null. */
    fun setOf(obj: Int): CostumeSet? = byObj[obj]
}

/** The costume room's storage lists, by enum id ([osrsps.cache.CostumeLoader]). */
class CostumeData(val lists: Map<Int, CostumeList>) {
    companion object {
        val NONE = CostumeData(emptyMap())
    }
}

object CostumeLines : Lines("construction_costume", "Costume room storage (Construction)") {
    val NOT_OWNER = line("not_owner", "Store or take in another player's costume room (the Costume room page: only the owner adds or removes; no source for the words)")
    val CANT_STORE = line("cant_store", "Storing an item this piece doesn't hold (ours; no source)")
    val FULL = line("full", "Storing a new set in a piece holding as many sets as its tier allows (the Costume room page's limits; no source for the words)")
    val CAPE_NOT_ENTITLED = line("cape_not_entitled", "Storing a cape the player couldn't wear in a cape rack (the Cape rack page's message)")
    val CAPE_RACK_FULL = line("cape_rack_full", "Storing another cape of accomplishment in a cape rack holding as many as its tier allows (the Cape rack page's limits; no source for the words)")
    val NO_SPACE = line("no_space", "Taking from storage with a full inventory (ours; no source)")
    val NOTHING_STORED = line("nothing_stored", "Set on a set with nothing stored and none of it carried (ours; no source)")
    val NOT_EMPTY = line("not_empty", "Remove on a costume room piece still holding something (the Costume room page: storage can only be removed when empty; no source for the words)")
    val OWNER_AWAY = line("owner_away", "Search in a house whose owner isn't online (ours; no source)")
}

/**
 * The costume room's storage (step CON7e): its pieces open, and Search shows the storage window (interface 675, with the
 * inventory beside it in 674), listing one of the cache's costume lists.
 *
 * Sources: the Costume room page and each piece's page (read 2026-11-28, notes in the project's
 * construction/wiki-costume-room.md), and the window's client scripts (osrs-dumps: `poh_costumes_init` 3532,
 * `poh_costumes_build`, `poh_costumes_drawitem`, `poh_costumes_draw_big`, `poh_costumes_buttons_dynamicsetup`):
 * - Storage is the owner's, one store across every piece (inv 637 `poh_costumes`, sent as the other player's inventory,
 *   which the client counts with `invother_total`). Duplicates are kept (the Wiki), as counts.
 * - Pieces and lists: magic wardrobe 3289, armour case 3290, cape rack 3292, fancy dress box 3291, toy box 3299, the
 *   treasure chest's tiers 3293 to 3298 (enum 3301's titles). Each list's sets are 44 children apart in the window
 *   (`poh_costumes_init`: a header, an arrow, a border, the name, 40 piece slots), so a set row's sub is its index * 44.
 * - How many sets each tier holds (the Wiki): wardrobe 7, 14, 21, 28, 35, 42, all; armour case 25, 50, all; fancy dress
 *   box 2, 4, all; toy box all; cape rack: capes of accomplishment none, 1, 5, 10, all, all; the treasure chest's oak
 *   tier holds beginner and easy rewards, teak adds medium, mahogany all. The window's Tiers button shows the tiers up to
 *   varp `if2`, -1 hiding it (`poh_costumes_buttons_dynamicsetup`).
 * - Only the owner stores or takes; a visitor views (the Wiki). The window's piece ops (Take) show only when the second
 *   argument is true.
 * - The closed pieces' Open swaps in the open form (the cache: closed id + 1, Search and Close); cape racks Search.
 *
 * GUESSED: a set counts toward a piece's limit once any piece of it is stored; a cape of accomplishment is a cape whose
 * wear requirement is level 99 in a skill (item-requirements.tsv), and the cape rack's limit counts only those; Set on a
 * set row takes every stored piece of it, or stores the set's carried pieces when none is stored; the inventory's op is
 * "Store"; Take takes one; a piece still holding anything can't be removed, and Upgrade keeps what it holds. Left out:
 * the Bank PIN check (the Wiki gates storing by it), Deposit mode's alternates, the window's search (client side), and
 * the room's own removal check; the boss lair jars list (3300) has no piece here (INFERRED: it is the toy box's on live).
 */
class CostumeRoom(private val world: World, private val houses: Houses, private val data: CostumeData) {
    /** The piece and list each player's window shows, until it closes. */
    private val viewing = java.util.WeakHashMap<Player, View>()

    private class View(val ownerKey: String, val piece: Int, val listId: Int)

    fun register() {
        for ((closed, piece) in PIECES) {
            if (piece.kind == Kind.CAPE_RACK) {
                world.handlers.onLoc(closed, SEARCH) { op -> arriveDelay(); open(player, op.loc?.id ?: return@onLoc) }
                continue
            }
            world.handlers.onLoc(closed, OPEN) { op ->
                arriveDelay()
                val loc = op.loc ?: return@onLoc
                if (houses.houseAt(loc.x, loc.z) == null) return@onLoc
                world.locs.replace(loc, closed + 1)
            }
            world.handlers.onLoc(closed + 1, SEARCH) { op -> arriveDelay(); open(player, closed.also { op.loc ?: return@onLoc }) }
            world.handlers.onLoc(closed + 1, CLOSE) { op ->
                arriveDelay()
                val loc = op.loc ?: return@onLoc
                if (houses.houseAt(loc.x, loc.z) == null) return@onLoc
                world.locs.replace(loc, closed)
            }
        }
        world.interfaces.onClose(MAIN) { player -> viewing.remove(player) }
    }

    /** Whether [owner]'s storage holds anything piece [loc] (a built, closed id) lists: such a piece can't be removed. */
    fun holdsAny(owner: Player, loc: Int): Boolean {
        val piece = PIECES[loc] ?: return false
        return piece.lists.any { id -> data.lists[id]?.sets?.any { set -> set.objs.any { (owner.costumes[it] ?: 0) > 0 } } == true }
    }

    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == MAIN

    /** Search on piece [loc] (its closed id): the storage window, on the piece's first list. */
    private fun open(player: Player, loc: Int, listId: Int? = null) {
        val piece = PIECES[loc] ?: return
        val house = houses.houseAt(player.x, player.z) ?: return
        val owner = ownerOf(house.owner) ?: return world.message(player, CostumeLines.OWNER_AWAY)
        val list = data.lists[listId ?: piece.lists.first()] ?: return
        world.interfaces.closeModals(player)
        viewing[player] = View(house.owner, loc, list.id)
        player.vars[TIER_VARP] = if (piece.kind == Kind.TREASURE_CHEST) piece.limit else -1
        world.interfaces.sendVarpNow(player, TIER_VARP)
        sendStore(player, owner)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, MAIN)
        world.interfaces.openModal(player, FrameSlot.SIDEMODAL, SIDE)
        val mine = house.owner == player.saveKey
        world.interfaces.runClientScript(player, INIT_SCRIPT, listOf(list.id, if (mine) 1 else 0, 1))
        val children = list.sets.size * SET_CHILDREN
        if (children > 0) player.session.queue(IfSetEventsV2(MAIN, ITEMS, 0, children - 1, 0, BankScreen.opMask(1..1) or BankScreen.opMask(10..10)))
        player.session.queue(IfSetEventsV2(MAIN, TIERS, -1, -1, 0, BankScreen.opMask(1..7)))
        player.session.queue(IfSetEventsV2(MAIN, DEPOSIT_ALL, -1, -1, 0, BankScreen.opMask(1..1)))
        world.interfaces.runClientScript(player, INTERFACE_INV_INIT_SCRIPT, listOf(SIDE shl 16 or SIDE_ITEMS, Inventory.INV, 4, 7, 0, -1, "Store"))
        player.session.queue(IfSetEventsV2(SIDE, SIDE_ITEMS, 0, Inventory.INV_CAPACITY - 1, 0, BankScreen.opMask(1..1) or BankScreen.opMask(10..10)))
    }

    private fun ownerOf(key: String): Player? = world.onlinePlayers.firstOrNull { it.saveKey == key }

    private fun sendStore(player: Player, owner: Player) {
        val stored = owner.costumes.entries.filter { it.value > 0 }.take(STORE_SIZE)
        player.session.queue(UpdateInvFull(OTHER, STORE_INV, STORE_SIZE) { slot ->
            stored.getOrNull(slot)?.let { InventoryObject(slot, it.key, it.value) } ?: InventoryObject.NULL
        })
    }

    /** A click on the window or the inventory beside it; false when it is on neither. */
    fun click(player: Player, interfaceId: Int, component: Int, sub: Int, op: Int, obj: Int): Boolean {
        if (interfaceId != MAIN && interfaceId != SIDE) return false
        if (!isOpen(player)) return true
        val view = viewing[player] ?: return true
        val piece = PIECES[view.piece] ?: return true
        val list = data.lists[view.listId] ?: return true
        if (interfaceId == MAIN && component == TIERS) {
            val tier = op - 2
            if (piece.kind == Kind.TREASURE_CHEST && tier in 0..piece.limit) open(player, view.piece, TREASURE_LISTS[tier])
            return true
        }
        if (op == EXAMINE) {
            val id = if (interfaceId == SIDE) player.inventory[sub]?.id ?: return true else obj
            world.objTypes[id]?.let { world.inventoryTab.examine(player, it, 1) }
            return true
        }
        val owner = ownerOf(view.ownerKey)
        if (owner !== player || player.house == null || houses.houseAt(player.x, player.z)?.owner != player.saveKey) {
            world.message(player, CostumeLines.NOT_OWNER)
            return true
        }
        when {
            interfaceId == SIDE && component == SIDE_ITEMS -> {
                val item = player.inventory[sub]?.takeIf { obj < 0 || it.id == obj } ?: return true
                store(player, piece, list, sub, item.id, quiet = false)
            }
            interfaceId == MAIN && component == DEPOSIT_ALL -> {
                for (slot in 0 until Inventory.INV_CAPACITY) {
                    val item = player.inventory[slot] ?: continue
                    if (list.setOf(item.id) != null) store(player, piece, list, slot, item.id, quiet = true)
                }
            }
            interfaceId == MAIN && component == ITEMS && sub % SET_CHILDREN == 0 -> setRow(player, piece, list, sub / SET_CHILDREN)
            interfaceId == MAIN && component == ITEMS -> take(player, list, if (obj >= 0) obj else pieceAt(player, list, sub) ?: return true)
        }
        sendStore(player, player)
        return true
    }

    /** Stores the item in inventory [slot] (one of [obj]) in [piece], showing why not unless [quiet]. */
    private fun store(player: Player, piece: Piece, list: CostumeList, slot: Int, obj: Int, quiet: Boolean): Boolean {
        val set = list.setOf(obj) ?: return false.also { if (!quiet) world.message(player, CostumeLines.CANT_STORE) }
        val type = world.objTypes[obj] ?: return false
        val stored = { s: CostumeSet -> s.objs.any { (player.costumes[it] ?: 0) > 0 } }
        if (piece.kind == Kind.CAPE_RACK) {
            if (!world.equipment.requirements[type].all { it.metBy(player) }) {
                if (!quiet) world.message(player, CostumeLines.CAPE_NOT_ENTITLED)
                return false
            }
            if (accomplishment(set) && !stored(set) && list.sets.count { accomplishment(it) && stored(it) } >= piece.limit) {
                if (!quiet) world.message(player, CostumeLines.CAPE_RACK_FULL)
                return false
            }
        } else if (piece.kind != Kind.TREASURE_CHEST && !stored(set) && list.sets.count(stored) >= piece.limit) {
            if (!quiet) world.message(player, CostumeLines.FULL)
            return false
        }
        val count = if (type.stackable) player.inventory[slot]?.count ?: return false else 1
        if (player.inventory[slot]?.id != obj) return false
        player.inventory.remove(slot, count)
        player.costumes[obj] = (player.costumes[obj] ?: 0) + count
        logger.info { "${player.username} stored $count of $obj in the costume room" }
        return true
    }

    /**
     * The stored piece in child [sub] of the window's list, for a click that names no object: a set's piece slots follow
     * its header, arrow, border and name (`poh_costumes_init`), one a piece (INFERRED: alternates drawn in further slots
     * aren't told apart here, so the piece's first stored form is taken).
     */
    private fun pieceAt(player: Player, list: CostumeList, sub: Int): Int? {
        val set = list.sets.getOrNull(sub / SET_CHILDREN) ?: return null
        val forms = set.pieces.getOrNull(sub % SET_CHILDREN - SET_HEADER) ?: return null
        return forms.firstOrNull { (player.costumes[it] ?: 0) > 0 }
    }

    /** Take on stored piece [obj]: one into the inventory; false when none could go. */
    private fun take(player: Player, list: CostumeList, obj: Int): Boolean {
        if ((player.costumes[obj] ?: 0) <= 0 || list.setOf(obj) == null) return false
        val type = world.objTypes[obj] ?: return false
        if (player.inventory.add(type, 1) <= 0) return false.also { world.message(player, CostumeLines.NO_SPACE) }
        val left = (player.costumes[obj] ?: 1) - 1
        if (left > 0) player.costumes[obj] = left else player.costumes.remove(obj)
        return true
    }

    /** Set on set [index]'s row: takes every stored piece, or stores the carried ones when none is stored. */
    private fun setRow(player: Player, piece: Piece, list: CostumeList, index: Int) {
        val set = list.sets.getOrNull(index) ?: return
        val stored = set.objs.filter { (player.costumes[it] ?: 0) > 0 }
        if (stored.isNotEmpty()) {
            for (obj in stored) repeat(player.costumes[obj] ?: 0) { if (!take(player, list, obj)) return }
            return
        }
        var any = false
        for (slot in 0 until Inventory.INV_CAPACITY) {
            val item = player.inventory[slot] ?: continue
            if (item.id in set.objs && store(player, piece, list, slot, item.id, quiet = any)) any = true
        }
        if (!any && set.objs.none { player.inventory.count(it) > 0 }) world.message(player, CostumeLines.NOTHING_STORED)
    }

    /** GUESSED: a cape of accomplishment wants level 99 in a skill to wear. */
    private fun accomplishment(set: CostumeSet): Boolean = set.objs.any { obj ->
        world.objTypes[obj]?.let { type -> world.equipment.requirements[type].any { it.skill != null && it.level >= 99 } } == true
    }

    enum class Kind { MAGIC_WARDROBE, ARMOUR_CASE, CAPE_RACK, FANCY_DRESS_BOX, TOY_BOX, TREASURE_CHEST }

    /**
     * A built piece: its [limit], how many sets (capes of accomplishment, for a cape rack) it holds, or for a treasure
     * chest the last of its tiers (0 beginner to 5 master); and the lists it shows.
     */
    class Piece(val kind: Kind, val limit: Int, val lists: List<Int>)

    companion object {
        const val MAIN = 675
        const val SIDE = 674
        const val ITEMS = 4
        const val TIERS = 7
        const val DEPOSIT_ALL = 10
        const val SIDE_ITEMS = 0
        const val STORE_INV = 637
        const val STORE_SIZE = 1781
        const val INIT_SCRIPT = 3532
        const val TIER_VARP = 262
        const val SET_CHILDREN = 44
        private const val SET_HEADER = 4
        const val OPEN = 1
        const val SEARCH = 1
        const val CLOSE = 2
        private const val EXAMINE = 10
        /** Any combined id under -70000 sends an inventory's other copy (as [osrsps.game.content.ClanParty]'s chest). */
        private const val OTHER = -70_002
        private const val INTERFACE_INV_INIT_SCRIPT = 149
        private const val ALL = Int.MAX_VALUE

        /** The treasure chest's lists: beginner, easy, medium, hard, elite, master. */
        val TREASURE_LISTS = listOf(3293, 3294, 3295, 3296, 3297, 3298)

        /** Each built piece, by its closed id, with its tier's limit (the Wiki) and its lists. */
        val PIECES: Map<Int, Piece> = buildMap {
            fun add(kind: Kind, ids: List<Int>, limits: List<Int>, lists: (Int) -> List<Int>) =
                ids.forEachIndexed { i, id -> put(id, Piece(kind, limits[i], lists(i))) }
            add(Kind.CAPE_RACK, (18766..18771).toList(), listOf(0, 1, 5, 10, ALL, ALL)) { listOf(3292) }
            add(Kind.FANCY_DRESS_BOX, listOf(18772, 18774, 18776), listOf(2, 4, ALL)) { listOf(3291) }
            add(Kind.ARMOUR_CASE, listOf(18778, 18780, 18782), listOf(25, 50, ALL)) { listOf(3290) }
            add(Kind.MAGIC_WARDROBE, (18784..18796 step 2).toList(), listOf(7, 14, 21, 28, 35, 42, ALL)) { listOf(3289) }
            add(Kind.TOY_BOX, listOf(18798, 18800, 18802), listOf(ALL, ALL, ALL)) { listOf(3299) }
            // The treasure chest's "limit" is its top tier: oak beginner and easy (1), teak to medium (2), mahogany all (5).
            add(Kind.TREASURE_CHEST, listOf(18804, 18806, 18808), listOf(1, 2, 5)) { i -> TREASURE_LISTS.take(listOf(2, 3, 6)[i]) }
        }

        /** The open forms, answered in a house's instance as the built pieces are ([FurnitureBuilds.INSTANCE_LOCS]). */
        val INSTANCE_LOCS: Set<Int> = PIECES.filterValues { it.kind != Kind.CAPE_RACK }.keys.map { it + 1 }.toSet()

        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            MAIN to ("poh_costumes" to mapOf(ITEMS to "items", TIERS to "tier", DEPOSIT_ALL to "depositall")),
            SIDE to ("poh_costumes_side" to mapOf(SIDE_ITEMS to "items")),
        )
    }
}
