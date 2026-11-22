package osrsps.game.skill.construction

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.FrameSlot
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.Ned
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
import osrsps.game.dialogue.Chatbox
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

private val logger = KotlinLogging.logger {}

/**
 * A piece of furniture a hotspot can hold, from the cache's `furniture` table ([osrsps.cache.FurnitureLoader]): its
 * row, its name and icon (`model_obj`) in the creation menu, its materials (obj and count; `any_nails` is any nails)
 * and its Construction level. [hidden] rows are left out of the menu, as the table says.
 */
data class FurnitureOption(
    val row: Int,
    val name: String,
    val icon: Int,
    val materials: List<Pair<Int, Int>>,
    val level: Int,
    val hidden: Boolean = false,
)

/** The cache's furniture by room type id ([RoomType.id]): each hotspot's choices, in the order the room lists them. */
class FurnitureData(val rooms: Map<Int, List<List<FurnitureOption>>>) {
    /** What hotspot [index] of a [roomType] room can hold, in its menu's order (hidden ones included). */
    fun options(roomType: Int, index: Int): List<FurnitureOption> = rooms[roomType]?.getOrNull(index).orEmpty()

    companion object {
        val NONE = FurnitureData(emptyMap())
    }
}

/**
 * What the cache doesn't hold about furniture: which hotspot loc is which of its room's hotspots, and what each piece
 * becomes once built and the XP it gives. Built locs are matched to the furniture rows by the order of their names
 * (`poh_chair1` to `poh_chair7` for the seven chairs, `poh_rugcorner1` to `3` for the three plain rugs; inferred). XP is
 * the Wiki's (Chair space page; the Parlour page for the rest of the parlour).
 */
object FurnitureBuilds {
    /**
     * A hotspot loc type: the room it is in, its place in the room's hotspot list (`poh_parlour_1` is the first), and for
     * a place with more than one loc type (a rug's corners, sides and middle; the bookcase's scroll rack) which it is.
     */
    data class Hotspot(val room: RoomType, val index: Int, val variant: String? = null)

    /** What a piece of furniture (by its row) builds as, its XP, and on a [Hotspot.variant] what it builds as there. */
    data class Build(val loc: Int, val xp: Int, val variants: Map<String, Int> = emptyMap()) {
        fun locFor(variant: String?): Int = variant?.let { variants[it] } ?: loc
    }

    /** The parlour's three chair spaces (`poh_parlour_1` to `_3`, "Chair space"). */
    const val CHAIR_SPACE_1 = 4515
    const val CHAIR_SPACE_2 = 4516
    const val CHAIR_SPACE_3 = 4517

    private const val MIDDLE = "middle"
    private const val SIDE = "side"
    private const val CORNER = "corner"
    private const val SCROLLS = "scrolls"

    val HOTSPOTS: Map<Int, Hotspot> = mapOf(
        CHAIR_SPACE_1 to Hotspot(RoomType.PARLOUR, 0),
        CHAIR_SPACE_2 to Hotspot(RoomType.PARLOUR, 1),
        CHAIR_SPACE_3 to Hotspot(RoomType.PARLOUR, 2),
        // poh_parlour_4_middle, _side, _corner; poh_parlour_5 and _5_scrolls; poh_parlour_6 (fireplace); _7 (curtains).
        4518 to Hotspot(RoomType.PARLOUR, 3, MIDDLE),
        4519 to Hotspot(RoomType.PARLOUR, 3, SIDE),
        4520 to Hotspot(RoomType.PARLOUR, 3, CORNER),
        4521 to Hotspot(RoomType.PARLOUR, 4),
        4522 to Hotspot(RoomType.PARLOUR, 4, SCROLLS),
        4523 to Hotspot(RoomType.PARLOUR, 5),
        4524 to Hotspot(RoomType.PARLOUR, 6),
    )

    private fun rug(corner: Int, side: Int, middle: Int, xp: Int) = Build(middle, xp, mapOf(CORNER to corner, SIDE to side, MIDDLE to middle))

    private fun bookcase(loc: Int, scrolls: Int, xp: Int) = Build(loc, xp, mapOf(SCROLLS to scrolls))

    /** By `furniture` row. */
    val BUILDS: Map<Int, Build> = mapOf(
        // Crude wooden chair, wooden chair, rocking chair, oak chair, oak, teak and mahogany armchairs.
        5517 to Build(6752, 58),
        5518 to Build(6753, 87),
        5519 to Build(6754, 87),
        5520 to Build(6755, 120),
        5521 to Build(6756, 180),
        5522 to Build(6757, 180),
        5523 to Build(6758, 280),
        // Brown rug, rug, opulent rug (poh_rug*1 to 3), the Deadman rug (poh_deadman_rug*) and the Raging Echoes rug
        // (poh_league5_rug*).
        5524 to rug(6759, 6760, 6761, 30),
        5525 to rug(6762, 6763, 6764, 60),
        5526 to rug(6765, 6766, 6767, 360),
        5527 to rug(54114, 54115, 54116, 283),
        5528 to rug(56076, 56077, 56078, 82),
        // Wooden, oak and mahogany bookcases, each a scroll rack on the scroll space (poh_bookcase_scrolls1 to 3).
        5529 to bookcase(6768, 6771, 115),
        5530 to bookcase(6769, 6772, 180),
        5531 to bookcase(6770, 6773, 420),
        // Clay, stone and marble fireplaces (poh_fireplace_1 to 3, unlit).
        5532 to Build(6780, 30),
        5533 to Build(6782, 40),
        5534 to Build(6784, 500),
        // Torn curtains, curtains, opulent curtains (poh_curtains_1 to 3) and the Raging Echoes curtains.
        5535 to Build(6774, 132),
        5536 to Build(6775, 225),
        5537 to Build(6776, 315),
        5538 to Build(56079, 487),
    )

    /** The hotspot loc types at the same place in the room as [hotspot] (itself included): built and removed together. */
    fun siblings(hotspot: Int): Set<Int> {
        val spot = HOTSPOTS[hotspot] ?: return setOf(hotspot)
        return HOTSPOTS.filterValues { it.room == spot.room && it.index == spot.index }.keys
    }

    /** Every built loc type, each with Remove as its op 5. */
    val BUILT_LOCS: Set<Int> get() = BUILDS.values.flatMapTo(HashSet()) { it.variants.values + it.loc }

    /** Hotspots and what is built on them: the house's locs that need handlers in its instance. */
    val INSTANCE_LOCS: Set<Int> get() = HOTSPOTS.keys + BUILT_LOCS
}

/**
 * Construction step CON3: building furniture on a hotspot, and removing it, in one's own house in building mode (the
 * Wiki's Construction and Chair space pages).
 *
 * Build on a hotspot opens the furniture creation menu (`poh_furniture_creation`, interface 458): each choice the
 * hotspot's row lists (not hidden) gets a slot, its icon, level and materials drawn by the client's own script
 * (`poh_furniture_creation_entry`, 1404), and the list laid out by 1406; Build on a slot (or its key) resumes the
 * menu's contents (458:2) with the slot. The player needs the level (a boost counts), a hammer and a saw, and the
 * materials, which are used up; nails are any nails, the best first (the Wiki's Nails page). Building plays
 * `human_poh_build`, gives the piece's XP and puts it on the hotspot, saved with the house. Remove on built furniture
 * asks yes or no and takes it away, giving nothing back.
 *
 * Step CON4 (the parlour): a place in a room can hold several hotspot loc types (a rug's corners, sides and middle; the
 * bookcase and its scroll rack) and several tiles (curtains on each window): one build fills them all, each with its own
 * part, and one Remove clears them all ([FurnitureBuilds.siblings]). Left out for now: lighting a fireplace (the Wiki
 * gives no Firemaking XP for it) and the bookcase's Search (its book list).
 *
 * GUESSED: the materials text's layout (count and name, the obj types' names from the cache); the build taking 2
 * ticks; the remove prompt; removing with the same animation; the messages (held, no source yet). Left out: nail
 * bending (the Wiki says nails bend, more often with worse nails and lower levels, but gives no odds); the crystal
 * saw's +3 boost; flatpacks (with the workshop).
 */
class Furniture(private val world: World, private val houses: Houses, private val data: FurnitureData) {
    fun register() {
        for (hotspot in FurnitureBuilds.HOTSPOTS.keys) {
            world.handlers.onLoc(hotspot, House.BUILD) { op ->
                arriveDelay()
                build(this, op.loc ?: return@onLoc)
            }
        }
        for (loc in FurnitureBuilds.BUILT_LOCS) {
            world.handlers.onLoc(loc, REMOVE) { op ->
                arriveDelay()
                remove(this, op.loc ?: return@onLoc)
            }
        }
    }

    /** The choices on [hotspot] the menu shows, by slot from 1. */
    fun menu(hotspot: Int): List<FurnitureOption> {
        val (room, index) = FurnitureBuilds.HOTSPOTS[hotspot] ?: return emptyList()
        return data.options(room.id, index).filter { !it.hidden }
    }

    /** Build on hotspot [hotspot]: the creation menu, then the picked piece built if the player can. */
    private suspend fun build(context: ScriptContext, hotspot: ZoneLoc) {
        val player = context.player
        val (house, room) = houses.buildingRoom(player, hotspot.x, hotspot.z) ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        val options = menu(hotspot.id)
        if (options.isEmpty()) return world.message(player, GameLines.NOTHING_INTERESTING)
        openMenu(player, options)
        val slot = context.pauseButton(MENU, MENU_CONTENTS, 1..options.size)
        world.interfaces.closeModals(player)
        val piece = options.getOrNull(slot - 1) ?: return
        val build = FurnitureBuilds.BUILDS[piece.row] ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        if (!houses.stillHere(house, player) || world.locs[hotspot.x, hotspot.z, hotspot.level, hotspot.layer] != hotspot) return
        if (player.skills.currentLevel(Skill.CONSTRUCTION) < piece.level) return world.message(player, ConstructionLines.FURNITURE_LEVEL, "level" to piece.level)
        if (!hasTools(player)) return world.message(player, ConstructionLines.FURNITURE_TOOLS)
        val nails = nailsFor(player, piece) ?: return world.message(player, ConstructionLines.FURNITURE_MATERIALS)
        player.anim(BUILD_ANIM)
        context.delay(BUILD_TICKS)
        // Checked again after the wait: the materials may have gone, or the room or hotspot with them.
        if (!houses.stillHere(house, player) || world.locs[hotspot.x, hotspot.z, hotspot.level, hotspot.layer] != hotspot) return
        if (nailsFor(player, piece) != nails) return world.message(player, ConstructionLines.FURNITURE_MATERIALS)
        for ((obj, count) in piece.materials) if (obj != ANY_NAILS) Ned.take(player.inventory, obj, count)
        for ((kind, count) in nails) Ned.take(player.inventory, kind, count)
        player.skills.addTenths(Skill.CONSTRUCTION, build.xp * 10)
        // Every hotspot of the same place in the room takes the piece: each rug tile its corner, side or middle, every
        // window its curtains.
        for (id in FurnitureBuilds.siblings(hotspot.id)) room.built[id] = build.locFor(FurnitureBuilds.HOTSPOTS.getValue(id).variant)
        redraw(house, room, hotspot.id)
        logger.info { "${player.username} built ${piece.name} on hotspot ${hotspot.id} for ${build.xp} XP" }
    }

    /** Remove on built furniture [loc]: a prompt, then its hotspot back. */
    private suspend fun remove(context: ScriptContext, loc: ZoneLoc) {
        val player = context.player
        val (house, room) = houses.buildingRoom(player, loc.x, loc.z) ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        val hotspot = house.hotspots[Houses.slot(loc.x, loc.z, loc.layer)] ?: return
        if (room.built[hotspot.id] != loc.id) return
        var pick = 0
        world.dialogues.start(context) {
            pick = choice(ConstructionLines.REMOVE_FURNITURE_YES, ConstructionLines.REMOVE_FURNITURE_NO, title = text(ConstructionLines.REMOVE_FURNITURE_TITLE))
        }
        if (pick != 1 || !houses.stillHere(house, player) || room.built[hotspot.id] != loc.id || !world.locs.exists(loc)) return
        player.anim(BUILD_ANIM)
        for (id in FurnitureBuilds.siblings(hotspot.id)) room.built.remove(id)
        redraw(house, room, hotspot.id)
        logger.info { "${player.username} removed furniture ${loc.id} from hotspot ${hotspot.id}" }
    }

    /** Shows what is built now on every tile of [room] holding [hotspot] or one of its siblings, or the hotspot itself. */
    private fun redraw(house: Houses.Open, room: HouseRoom, hotspot: Int) {
        val ids = FurnitureBuilds.siblings(hotspot)
        val baseX = house.instance.baseX + (room.x shl 3)
        val baseZ = house.instance.baseZ + (room.z shl 3)
        for (spot in house.hotspots.values) {
            if (spot.id !in ids || spot.x - baseX !in 0..7 || spot.z - baseZ !in 0..7) continue
            val built = room.built[spot.id]
            world.locs.setPermanent(spot.x, spot.z, spot.level, spot.layer, if (built != null) spot.copy(id = built) else spot)
        }
    }

    /** Opens the creation menu with [options] in its slots, from 1, as the client's own scripts draw them. */
    private fun openMenu(player: Player, options: List<FurnitureOption>) {
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, MENU)
        world.interfaces.setEvents(player, MENU, MENU_CONTENTS, 1, MAX_SLOTS, Chatbox.EVENT_PAUSE_BUTTON)
        val level = player.skills.currentLevel(Skill.CONSTRUCTION)
        options.forEachIndexed { i, piece ->
            val can = level >= piece.level && piece.materials.all { (obj, count) -> have(player, obj) >= count }
            world.interfaces.runClientScript(player, ENTRY_SCRIPT, listOf(i + 1, piece.row, piece.level, materialsText(piece), if (can) 1 else 0))
        }
        world.interfaces.runClientScript(player, LAYOUT_SCRIPT, listOf(options.size, 0))
    }

    /** The menu's materials text: one "count x name" a line, the first half on the slot's upper line. GUESSED layout. */
    private fun materialsText(piece: FurnitureOption): String {
        val lines = piece.materials.map { (obj, count) -> "$count x ${world.objTypes[obj]?.name ?: "?"}" }
        val half = (lines.size + 1) / 2
        return lines.take(half).joinToString("<br>") + "|" + lines.drop(half).joinToString("<br>")
    }

    /** How many of material [obj] [player] has: for [ANY_NAILS], every kind of nails together. */
    private fun have(player: Player, obj: Int): Long =
        if (obj == ANY_NAILS) NAILS.sumOf { player.inventory.count(it) } else player.inventory.count(obj)

    /**
     * Whether [player] has [piece]'s materials, and which nails it takes: null when they lack any, else the nails by kind
     * and count (empty when it needs none). Nails are taken best kind first (the Wiki's Nails page: "the higher tier ones
     * are used first"), across kinds when no one kind has enough, as the menu counts every kind together (Codex review
     * on #869).
     */
    private fun nailsFor(player: Player, piece: FurnitureOption): Map<Int, Int>? {
        val nails = LinkedHashMap<Int, Int>()
        for ((obj, count) in piece.materials) {
            if (obj == ANY_NAILS) {
                var left = count.toLong()
                for (kind in NAILS) {
                    if (left == 0L) break
                    val take = minOf(left, player.inventory.count(kind))
                    if (take > 0) nails[kind] = (nails[kind] ?: 0) + take.toInt()
                    left -= take
                }
                if (left > 0) return null
            } else if (player.inventory.count(obj) < count) {
                return null
            }
        }
        return nails
    }

    /** A hammer and a saw, carried or worn (the Wiki's Construction page: the Imcando hammer and Amy's saw can be worn). */
    private fun hasTools(player: Player): Boolean = HAMMERS.any { carries(player, it) } && SAWS.any { carries(player, it) }

    private fun carries(player: Player, obj: Int) = player.inventory.count(obj) > 0 || player.equipment.count(obj) > 0

    companion object {
        /** `poh_furniture_creation`: its contents, whose slots resume with the slot from 1, and how many there can be. */
        const val MENU = 458
        const val MENU_CONTENTS = 2
        const val MAX_SLOTS = 31

        /** The slot's component: `poh_furniture_creation:01` (458:4) is slot 1 (enum 1461). */
        const val FIRST_SLOT_COMPONENT = 4

        /** `poh_furniture_creation_entry` (a slot) and script 1406 (the list's layout). */
        const val ENTRY_SCRIPT = 1404
        const val LAYOUT_SCRIPT = 1406

        /** Built furniture's op 5. */
        const val REMOVE = 5

        const val BUILD_ANIM = 3676
        const val BUILD_TICKS = 2

        /** `any_nails`, the material standing for any kind of nails. */
        const val ANY_NAILS = 32923

        /** Nails, best first: dragon, rune, adamant, mithril, black, steel, iron, bronze. */
        val NAILS = listOf(31406, 4824, 4823, 4822, 4821, 1539, 4820, 4819)

        /** `hammer`, `imcando_hammer` and its off-hand form. */
        val HAMMERS = listOf(2347, 25644, 29775)

        /** `poh_saw`, the crystal saw (`eyeglo_crystal_saw`) and Amy's saw (`wearable_saw`). */
        val SAWS = listOf(8794, 9625, 24880)
    }
}
