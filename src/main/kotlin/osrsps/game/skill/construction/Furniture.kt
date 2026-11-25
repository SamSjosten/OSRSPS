package osrsps.game.skill.construction

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.AllSettings
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
 * and its Construction level, and any other skill's level it needs (by stat id: the barrels' Cooking). [hidden] rows are
 * left out of the menu, as the table says. [upgradeFrom] (`upgrade_source_relative`) makes it an upgrade: it is built
 * over the piece that many places before it in its hotspot's list, or on an empty hotspot when that is before the first
 * (the client's own check, script5921).
 */
data class FurnitureOption(
    val row: Int,
    val name: String,
    val icon: Int,
    val materials: List<Pair<Int, Int>>,
    val level: Int,
    val hidden: Boolean = false,
    val otherLevels: List<Pair<Int, Int>> = emptyList(),
    val upgradeFrom: Int? = null,
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
    private const val CROCKERY = "crockery"
    private const val STOOL = "stool"

    /** The workshop's five tool spaces (`poh_workshop_3a` to `_3e`), in order. */
    private val TOOL_SPACES = listOf("a", "b", "c", "d", "e")

    /** A [Build.variants] value for a hotspot the piece leaves as it is: still a hotspot, nothing built on it. */
    const val NONE = -1

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
        // poh_kitchen_1 (stove), _2 and _2_crockery (shelves), _3 (barrel), _4 (pet bed), _5 (larder), _6 (sink),
        // _7 (table), _8 (spice rack).
        15398 to Hotspot(RoomType.KITCHEN, 0),
        15399 to Hotspot(RoomType.KITCHEN, 1),
        15400 to Hotspot(RoomType.KITCHEN, 1, CROCKERY),
        15401 to Hotspot(RoomType.KITCHEN, 2),
        15402 to Hotspot(RoomType.KITCHEN, 3),
        15403 to Hotspot(RoomType.KITCHEN, 4),
        15404 to Hotspot(RoomType.KITCHEN, 5),
        15405 to Hotspot(RoomType.KITCHEN, 6),
        37620 to Hotspot(RoomType.KITCHEN, 7),
        // poh_dining_room_1 (table), _2 and _3 (seating), _4 (fireplace), _5 (curtains), _6 (decoration), _7 (bell-pull).
        15298 to Hotspot(RoomType.DINING_ROOM, 0),
        15299 to Hotspot(RoomType.DINING_ROOM, 1),
        15300 to Hotspot(RoomType.DINING_ROOM, 2),
        15301 to Hotspot(RoomType.DINING_ROOM, 3),
        15302 to Hotspot(RoomType.DINING_ROOM, 4),
        15303 to Hotspot(RoomType.DINING_ROOM, 5),
        15304 to Hotspot(RoomType.DINING_ROOM, 6),
        // poh_workshop_1 (workbench) and its stool, _2 (clockmaking) and its stool, _3a to _3e (tools), _4 (repair) and
        // its stool, _5 (heraldry).
        15439 to Hotspot(RoomType.WORKSHOP, 0),
        15440 to Hotspot(RoomType.WORKSHOP, 0, STOOL),
        15441 to Hotspot(RoomType.WORKSHOP, 1),
        15442 to Hotspot(RoomType.WORKSHOP, 1, STOOL),
        15443 to Hotspot(RoomType.WORKSHOP, 2, "a"),
        15444 to Hotspot(RoomType.WORKSHOP, 2, "b"),
        15445 to Hotspot(RoomType.WORKSHOP, 2, "c"),
        15446 to Hotspot(RoomType.WORKSHOP, 2, "d"),
        15447 to Hotspot(RoomType.WORKSHOP, 2, "e"),
        15448 to Hotspot(RoomType.WORKSHOP, 3),
        15449 to Hotspot(RoomType.WORKSHOP, 3, STOOL),
        15450 to Hotspot(RoomType.WORKSHOP, 4),
    )

    private fun rug(corner: Int, side: Int, middle: Int, xp: Int) = Build(middle, xp, mapOf(CORNER to corner, SIDE to side, MIDDLE to middle))

    private fun shelves(loc: Int, crockery: Int, xp: Int) = Build(loc, xp, mapOf(CROCKERY to crockery))

    private fun bookcase(loc: Int, scrolls: Int, xp: Int) = Build(loc, xp, mapOf(SCROLLS to scrolls))

    private fun withStool(loc: Int, stool: Int, xp: Int) = Build(loc, xp, mapOf(STOOL to stool))

    /** Tool store [tier]: a rack of tools on each of the first [tier] tool spaces (`poh_tools1` to its own), the rest bare. */
    private fun toolStore(tier: Int) = Build(TOOLS_1 + tier - 1, 120, TOOL_SPACES.withIndex().associate { (i, space) -> space to if (i < tier) TOOLS_1 + i else NONE })

    /** `poh_tools1`; `poh_tools2` to `5` follow it. */
    const val TOOLS_1 = 6786

    /** `poh_stool_1` and `_2`. */
    private const val STOOL_1 = 6806
    private const val STOOL_2 = 6807

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
        // Brown rug, rug, opulent rug (poh_rug*1 to 3).
        5524 to rug(6759, 6760, 6761, 30),
        5525 to rug(6762, 6763, 6764, 60),
        5526 to rug(6765, 6766, 6767, 360),
        // Left out (exploit review R52B-2): the Deadman rug (5527, poh_deadman_rug*, 54114-54116) and the Raging Echoes
        // rug (5528, poh_league5_rug*, 56076-56078) and curtains (5538, 56079), which live unlocks in Deadman and Leagues.
        // Wooden, oak and mahogany bookcases, each a scroll rack on the scroll space (poh_bookcase_scrolls1 to 3).
        5529 to bookcase(6768, 6771, 115),
        5530 to bookcase(6769, 6772, 180),
        5531 to bookcase(6770, 6773, 420),
        // Clay, stone and marble fireplaces (poh_fireplace_1 to 3, unlit).
        5532 to Build(6780, 30),
        5533 to Build(6782, 40),
        5534 to Build(6784, 500),
        // Torn curtains, curtains, opulent curtains (poh_curtains_1 to 3).
        5535 to Build(6774, 132),
        5536 to Build(6775, 225),
        5537 to Build(6776, 315),
        // The kitchen (the Kitchen page's XP). Stoves: firepit, with hook, with pot, small and large ovens, steel and
        // fancy ranges (poh_stove_1 to 7, without their kettle or pot).
        5539 to Build(13528, 40),
        5540 to Build(13529, 60),
        5541 to Build(13531, 80),
        5542 to Build(13533, 80),
        5543 to Build(13536, 100),
        5544 to Build(13539, 120),
        5545 to Build(13542, 160),
        // Wooden shelves 1 to 3, oak 1 and 2, teak 1 and 2 (poh_kitchen_shelves_1 to 7, each with its crockery).
        5546 to shelves(13545, 13552, 87),
        5547 to shelves(13546, 13553, 147),
        5548 to shelves(13547, 13554, 147),
        5549 to shelves(13548, 13555, 240),
        5550 to shelves(13549, 13556, 240),
        5551 to shelves(13550, 13557, 330),
        5552 to shelves(13551, 13558, 930),
        // Beer, cider, Asgarnian ale, Greenman's ale, Dragon bitter and Chef's Delight barrels (poh_barrel_1 to 6).
        5553 to Build(13568, 87),
        5554 to Build(13569, 91),
        5555 to Build(13570, 184),
        5556 to Build(13571, 184),
        5557 to Build(13572, 224),
        5558 to Build(13573, 224),
        // Cat blanket, cat basket, cushioned basket (poh_pet_1 to 3).
        5559 to Build(13574, 15),
        5560 to Build(13575, 58),
        5561 to Build(13576, 58),
        // Wooden, oak and teak larders (poh_larder_1 to 3).
        5562 to Build(13565, 228),
        5563 to Build(13566, 480),
        5564 to Build(13567, 750),
        // Pump and drain, pump and tub, sink (poh_sink_1 to 3) and the gold sink (poh_sink_4).
        5565 to Build(13559, 100),
        5566 to Build(13561, 200),
        5567 to Build(13563, 300),
        5568 to Build(26458, 11144),
        // Wooden, oak and teak kitchen tables (poh_kitchentable_1 to 3).
        5569 to Build(13577, 87),
        5570 to Build(13578, 180),
        5571 to Build(13579, 270),
        // The spice rack (poh_kitchen_rack_1).
        5572 to Build(37621, 374),
        // The dining room (the Dining room page's XP; its fireplace and curtains are the parlour's rows). Wood, oak, carved
        // oak, teak, carved teak, mahogany and opulent tables (poh_diningtable_1 to 7).
        5573 to Build(13293, 115),
        5574 to Build(13294, 240),
        5575 to Build(13295, 360),
        5576 to Build(13296, 360),
        5577 to Build(13297, 600),
        5578 to Build(13298, 840),
        5579 to Build(13299, 3100),
        // Wooden, oak, carved oak, teak, carved teak, mahogany and gilded benches (poh_diningchairs_1 to 7).
        5580 to Build(13300, 115),
        5581 to Build(13301, 240),
        5582 to Build(13302, 240),
        5583 to Build(13303, 360),
        5584 to Build(13304, 360),
        5585 to Build(13305, 560),
        5586 to Build(13306, 1760),
        // Rope bell-pull, bell-pull, posh bell-pull (poh_bellpull_1 to 3).
        5590 to Build(13307, 64),
        5591 to Build(13308, 120),
        5592 to Build(13309, 420),
        // The Greenman carving (poh_greenman_wall_decoration); 0 XP, as the page has it.
        6027 to Build(57012, 0),
        // The workshop (the Workshop page's XP). Wooden, oak and steel framed workbenches and the benches with a vice and a
        // lathe (poh_workbench_1 to 5); crafting tables 1 to 4 (poh_clockmaking_1 to 4, the clockmaker's bench); tool
        // stores 1 to 5; the repair bench, whetstone and armour stand (poh_repair_1 to 3); the pluming stand, shield easel
        // and banner easel (poh_repair_4 to 6). GUESSED: which stool goes with which bench (the first for the workbench
        // and the repair bench, the second for the clockmaker's).
        5749 to withStool(6791, STOOL_1, 143),
        5750 to withStool(6792, STOOL_1, 300),
        5751 to withStool(6793, STOOL_1, 440),
        5752 to withStool(6794, STOOL_1, 140),
        5753 to withStool(6795, STOOL_1, 140),
        5754 to withStool(6796, STOOL_2, 240),
        5755 to withStool(6797, STOOL_2, 1),
        5756 to withStool(6798, STOOL_2, 2),
        5757 to withStool(6799, STOOL_2, 120),
        5758 to toolStore(1),
        5759 to toolStore(2),
        5760 to toolStore(3),
        5761 to toolStore(4),
        5762 to toolStore(5),
        5763 to withStool(6800, STOOL_1, 120),
        5764 to withStool(6801, STOOL_1, 260),
        5765 to withStool(6802, STOOL_1, 500),
        5766 to Build(6803, 120),
        5767 to Build(6804, 240),
        5768 to Build(6805, 510),
    )

    /**
     * The furniture rows each place in a room offers, as the cache's `poh_hotspot` table lists them, so a save can be
     * checked without the cache: a piece is only ever built on its own place.
     */
    val PLACES: Map<Pair<RoomType, Int>, Iterable<Int>> = mapOf(
        (RoomType.PARLOUR to 0) to 5517..5523,
        (RoomType.PARLOUR to 1) to 5517..5523,
        (RoomType.PARLOUR to 2) to 5517..5523,
        (RoomType.PARLOUR to 3) to 5524..5528,
        (RoomType.PARLOUR to 4) to 5529..5531,
        (RoomType.PARLOUR to 5) to 5532..5534,
        (RoomType.PARLOUR to 6) to 5535..5538,
        (RoomType.KITCHEN to 0) to 5539..5545,
        (RoomType.KITCHEN to 1) to 5546..5552,
        (RoomType.KITCHEN to 2) to 5553..5558,
        (RoomType.KITCHEN to 3) to 5559..5561,
        (RoomType.KITCHEN to 4) to 5562..5564,
        (RoomType.KITCHEN to 5) to 5565..5568,
        (RoomType.KITCHEN to 6) to 5569..5571,
        (RoomType.KITCHEN to 7) to 5572..5572,
        (RoomType.DINING_ROOM to 0) to 5573..5579,
        (RoomType.DINING_ROOM to 1) to 5580..5586,
        (RoomType.DINING_ROOM to 2) to 5580..5586,
        (RoomType.DINING_ROOM to 3) to 5532..5534,
        (RoomType.DINING_ROOM to 4) to 5535..5538,
        (RoomType.DINING_ROOM to 5) to listOf(5587, 5588, 5589, 6027),
        (RoomType.DINING_ROOM to 6) to 5590..5592,
        (RoomType.WORKSHOP to 0) to 5749..5753,
        (RoomType.WORKSHOP to 1) to 5754..5757,
        (RoomType.WORKSHOP to 2) to 5758..5762,
        (RoomType.WORKSHOP to 3) to 5763..5765,
        (RoomType.WORKSHOP to 4) to 5766..5768,
    )

    /** Whether [loc] is what one of the pieces [hotspot]'s place offers builds as on that hotspot. */
    fun buildsOn(hotspot: Int, loc: Int): Boolean {
        if (loc == NONE) return false
        val spot = HOTSPOTS[hotspot] ?: return false
        val rows = PLACES[spot.room to spot.index] ?: return false
        return rows.any { row -> BUILDS[row]?.locFor(spot.variant) == loc }
    }

    /**
     * The furniture row built at [hotspot]'s place, from what [built] (a room's, by hotspot) holds on it and its siblings:
     * the one row whose every part matches, or null when nothing is built there (or what is doesn't match one row).
     */
    fun builtRow(built: Map<Int, Int>, hotspot: Int): Int? {
        val spot = HOTSPOTS[hotspot] ?: return null
        val ids = siblings(hotspot)
        if (ids.none { it in built }) return null
        return PLACES[spot.room to spot.index]?.firstOrNull { row ->
            val build = BUILDS[row] ?: return@firstOrNull false
            ids.all { id -> (built[id] ?: NONE) == build.locFor(HOTSPOTS.getValue(id).variant) }
        }
    }

    /**
     * Built locs with Upgrade as their op 4: `poh_tools1` to `4`, the steel framed bench and the bench with a vice, and
     * crafting tables 1 to 3. Each opens the creation menu with what builds over it.
     */
    val UPGRADABLE: Set<Int> = setOf(6786, 6787, 6788, 6789, 6793, 6794, 6796, 6797, 6798)

    /**
     * The oak, teak and gilded wall decorations: each shows the player's family crest (poh_decor_oak_arrav and the
     * rest), which Sir Renitee gives; until crests exist they can't be built ([ConstructionLines.FURNITURE_CREST]).
     */
    val NEEDS_CREST: Set<Int> = setOf(5587, 5588, 5589)

    /** The hotspot loc types at the same place in the room as [hotspot] (itself included): built and removed together. */
    fun siblings(hotspot: Int): Set<Int> {
        val spot = HOTSPOTS[hotspot] ?: return setOf(hotspot)
        return HOTSPOTS.filterValues { it.room == spot.room && it.index == spot.index }.keys
    }

    /** Every built loc type, each with Remove as its op 5. */
    val BUILT_LOCS: Set<Int> get() = BUILDS.values.flatMapTo(HashSet()) { it.variants.values + it.loc } - NONE

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
        for (loc in FurnitureBuilds.UPGRADABLE) {
            world.handlers.onLoc(loc, UPGRADE) { op ->
                arriveDelay()
                upgrade(this, op.loc ?: return@onLoc)
            }
        }
        // The menu's init (script1406) takes the chat's keys away (chatdefault_stopinput) and nothing in the client gives
        // them back, so the server does when it closes, as for the canoe map. Without it the chat ignores every key after
        // a build (nightly 2026-11-23: construction-furniture stuck at its next command).
        world.interfaces.onClose(MENU) { player -> world.interfaces.runClientScript(player, AllSettings.CHATDEFAULT_RESTOREINPUT_SCRIPT, emptyList()) }
    }

    /**
     * The choices on [hotspot] the menu shows, by slot from 1, with [built] (a furniture row) at its place: with nothing
     * there, every piece that isn't an upgrade (or upgrades an empty space); over a piece, the pieces that upgrade it.
     */
    fun menu(hotspot: Int, built: Int? = null): List<FurnitureOption> {
        val (room, index) = FurnitureBuilds.HOTSPOTS[hotspot] ?: return emptyList()
        val all = data.options(room.id, index)
        val at = if (built == null) -1 else all.indexOfFirst { it.row == built }.takeIf { it >= 0 } ?: return emptyList()
        return all.filterIndexed { i, piece ->
            !piece.hidden && if (piece.upgradeFrom == null) built == null else i - piece.upgradeFrom == at
        }
    }

    /** Build on hotspot [hotspot]: the creation menu, then the picked piece built if the player can. */
    private suspend fun build(context: ScriptContext, hotspot: ZoneLoc) = place(context, hotspot, hotspot)

    /**
     * Upgrade on built furniture [loc]: the creation menu with what builds over it, then the picked piece built in its
     * place, as a build on its hotspot (the Wiki's Workshop page: each higher piece needs the one before it, which it
     * replaces).
     */
    private suspend fun upgrade(context: ScriptContext, loc: ZoneLoc) {
        val house = houses.houseAt(loc.x, loc.z) ?: return world.message(context.player, GameLines.NOTHING_INTERESTING)
        val hotspot = house.hotspots[Houses.slot(loc.x, loc.z, loc.layer)] ?: return
        place(context, hotspot, loc)
    }

    /**
     * Builds on [hotspot]'s place, shown on its tile as [shown] (the hotspot itself, or what is built there): the menu of
     * what can go there now, then the picked piece built over whatever is. A tool space left bare beside a tool store
     * offers the store's upgrades, as its rack's Upgrade does (GUESSED).
     */
    private suspend fun place(context: ScriptContext, hotspot: ZoneLoc, shown: ZoneLoc) {
        val player = context.player
        val (house, room) = houses.buildingRoom(player, hotspot.x, hotspot.z) ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        // Only a hotspot of the room it stands in: what is built is saved, and a save checks the two match (R50B note).
        if (FurnitureBuilds.HOTSPOTS[hotspot.id]?.room != room.type) return world.message(player, GameLines.NOTHING_INTERESTING)
        if ((room.built[hotspot.id] ?: hotspot.id) != shown.id) return
        val current = FurnitureBuilds.builtRow(room.built, hotspot.id)
        if (current == null && hotspot.id in room.built) return
        val options = menu(hotspot.id, current)
        if (options.isEmpty()) return world.message(player, GameLines.NOTHING_INTERESTING)
        openMenu(player, options)
        val slot = context.pauseButton(MENU, MENU_CONTENTS, 1..options.size)
        world.interfaces.closeModals(player)
        val piece = options.getOrNull(slot - 1) ?: return
        if (piece.row in FurnitureBuilds.NEEDS_CREST) return world.message(player, ConstructionLines.FURNITURE_CREST)
        val build = FurnitureBuilds.BUILDS[piece.row] ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        // Only a piece the save check knows for this place (R52B-1): what is built must always load again.
        if (!FurnitureBuilds.buildsOn(hotspot.id, build.locFor(FurnitureBuilds.HOTSPOTS[hotspot.id]?.variant))) return world.message(player, GameLines.NOTHING_INTERESTING)
        if (!placeStands(house, room, hotspot, shown, current, player)) return
        if (player.skills.currentLevel(Skill.CONSTRUCTION) < piece.level) return world.message(player, ConstructionLines.FURNITURE_LEVEL, "level" to piece.level)
        lacking(player, piece)?.let { (skill, level) -> return world.message(player, ConstructionLines.FURNITURE_SKILL_LEVEL, "skill" to skill.displayName, "level" to level) }
        if (!hasTools(player)) return world.message(player, ConstructionLines.FURNITURE_TOOLS)
        val nails = nailsFor(player, piece) ?: return world.message(player, ConstructionLines.FURNITURE_MATERIALS)
        player.anim(BUILD_ANIM)
        context.delay(BUILD_TICKS)
        // Checked again after the wait: the materials may have gone, or the room or hotspot with them.
        if (!placeStands(house, room, hotspot, shown, current, player)) return
        if (nailsFor(player, piece) != nails) return world.message(player, ConstructionLines.FURNITURE_MATERIALS)
        for ((obj, count) in piece.materials) if (obj != ANY_NAILS) Ned.take(player.inventory, obj, count)
        for ((kind, count) in nails) Ned.take(player.inventory, kind, count)
        player.skills.addTenths(Skill.CONSTRUCTION, build.xp * 10)
        // Every hotspot of the same place in the room takes the piece: each rug tile its corner, side or middle, every
        // window its curtains.
        for (id in FurnitureBuilds.siblings(hotspot.id)) {
            val loc = build.locFor(FurnitureBuilds.HOTSPOTS.getValue(id).variant)
            if (loc == FurnitureBuilds.NONE) room.built.remove(id) else room.built[id] = loc
        }
        redraw(house, room, hotspot.id)
        logger.info { "${player.username} built ${piece.name} on hotspot ${hotspot.id} for ${build.xp} XP" }
    }

    /** Whether [player] is still in [house] and [hotspot]'s place in [room] still holds [current], [shown] on its tile. */
    private fun placeStands(house: Houses.Open, room: HouseRoom, hotspot: ZoneLoc, shown: ZoneLoc, current: Int?, player: Player): Boolean =
        houses.stillHere(house, player) && world.locs[shown.x, shown.z, shown.level, shown.layer] == shown &&
            (room.built[hotspot.id] ?: hotspot.id) == shown.id && FurnitureBuilds.builtRow(room.built, hotspot.id) == current

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
            val can = level >= piece.level && lacking(player, piece) == null && piece.materials.all { (obj, count) -> have(player, obj) >= count }
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

    /** The first other skill [piece] needs that [player] is below (a boost counts), with its level; null when none. */
    private fun lacking(player: Player, piece: FurnitureOption): Pair<Skill, Int>? {
        for ((stat, level) in piece.otherLevels) {
            val skill = Skill.entries.firstOrNull { it.id == stat } ?: continue
            if (player.skills.currentLevel(skill) < level) return skill to level
        }
        return null
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

        /** Built furniture's op 5, and an upgradable piece's op 4. */
        const val REMOVE = 5
        const val UPGRADE = 4

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
