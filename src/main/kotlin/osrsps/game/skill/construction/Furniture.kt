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
     * [only] is the one type of its room it is in, when its room's types share the rest: a hall's stair space is the
     * staircase's foot in the hall and its top in the hall at the top of a staircase ([RoomType.upstairs]).
     */
    data class Hotspot(val room: RoomType, val index: Int, val variant: String? = null, val only: RoomType? = null)

    /**
     * What a piece of furniture (by its row) builds as, its XP (and any Farming XP: the garden's trees and plants), and
     * on a [Hotspot.variant] what it builds as there.
     */
    data class Build(val loc: Int, val xp: Int, val variants: Map<String, Int> = emptyMap(), val farmingXp: Int = 0) {
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
    private const val BIG = "big"
    private const val STAIRS = "stairs"
    private const val TOP = "top"

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
        // poh_crude_garden_1 (centrepiece), _2 (big tree), _3 (tree), _4 and _5 (big plants), _6 and _7 (small plants): the
        // Rimmington style's. Left out: the tip jar space (poh_garden_8), whose jar needs platinum tokens and tipping.
        House.CENTREPIECE_SPACE to Hotspot(RoomType.GARDEN, 0),
        15362 to Hotspot(RoomType.GARDEN, 1, BIG),
        15363 to Hotspot(RoomType.GARDEN, 2),
        15364 to Hotspot(RoomType.GARDEN, 3),
        15365 to Hotspot(RoomType.GARDEN, 4),
        15366 to Hotspot(RoomType.GARDEN, 5),
        15367 to Hotspot(RoomType.GARDEN, 6),
        // poh_hall1_1_middle, _side, _corner (the rug) and _stairs_up or _stairs_top (the stair space): the skill hall's
        // first place, one rug or one staircase. The quest hall's (poh_hall2_1_*) are the same.
        15377 to Hotspot(RoomType.SKILL_HALL, 0, MIDDLE),
        15378 to Hotspot(RoomType.SKILL_HALL, 0, SIDE),
        15379 to Hotspot(RoomType.SKILL_HALL, 0, CORNER),
        15380 to Hotspot(RoomType.SKILL_HALL, 0, STAIRS, RoomType.SKILL_HALL),
        15381 to Hotspot(RoomType.SKILL_HALL, 0, TOP, RoomType.SKILL_HALL_STAIRS),
        15387 to Hotspot(RoomType.QUEST_HALL, 0, MIDDLE),
        15388 to Hotspot(RoomType.QUEST_HALL, 0, SIDE),
        15389 to Hotspot(RoomType.QUEST_HALL, 0, CORNER),
        15390 to Hotspot(RoomType.QUEST_HALL, 0, STAIRS, RoomType.QUEST_HALL),
        15391 to Hotspot(RoomType.QUEST_HALL, 0, TOP, RoomType.QUEST_HALL_STAIRS),
        // The combat room (step CON6). The combat ring space (poh_gr_1_*): one loc type a tile, named for the rings that
        // put something there, the walls round the ring (_wall_*) and the floor inside it (_floor_*). Then the storage
        // space (poh_combat_room_4), the decoration space (_5) and the combat dummy space (_6).
        15277 to Hotspot(RoomType.COMBAT_ROOM, 0, RING_WALL),
        15278 to Hotspot(RoomType.COMBAT_ROOM, 0, RING_BLUE_CORNER),
        15279 to Hotspot(RoomType.COMBAT_ROOM, 0, RING_CORNER),
        15280 to Hotspot(RoomType.COMBAT_ROOM, 0, RING_EVERY_WALL),
        15281 to Hotspot(RoomType.COMBAT_ROOM, 0, RING_RED_CORNER),
        15282 to Hotspot(RoomType.COMBAT_ROOM, 0, RING_BEAM_CORNER),
        15283 to Hotspot(RoomType.COMBAT_ROOM, 0, PEDESTAL_WALL),
        15284 to Hotspot(RoomType.COMBAT_ROOM, 0, PEDESTAL_BEAM_WALL),
        15285 to Hotspot(RoomType.COMBAT_ROOM, 0, BEAM_WALL),
        15286 to Hotspot(RoomType.COMBAT_ROOM, 0, RING_BEAM_WALL),
        15287 to Hotspot(RoomType.COMBAT_ROOM, 0, RING_PEDESTAL_WALL),
        15288 to Hotspot(RoomType.COMBAT_ROOM, 0, FLOOR_N),
        15289 to Hotspot(RoomType.COMBAT_ROOM, 0, FLOOR_NE),
        15290 to Hotspot(RoomType.COMBAT_ROOM, 0, FLOOR_NW),
        15291 to Hotspot(RoomType.COMBAT_ROOM, 0, FLOOR_SIDE),
        15292 to Hotspot(RoomType.COMBAT_ROOM, 0, FLOOR_MIDDLE),
        15293 to Hotspot(RoomType.COMBAT_ROOM, 0, FLOOR_S),
        15294 to Hotspot(RoomType.COMBAT_ROOM, 0, FLOOR_SW),
        15295 to Hotspot(RoomType.COMBAT_ROOM, 0, FLOOR_SE),
        15296 to Hotspot(RoomType.COMBAT_ROOM, 3),
        15297 to Hotspot(RoomType.COMBAT_ROOM, 4),
        29335 to Hotspot(RoomType.COMBAT_ROOM, 5),
        // The games room (step CON6e): its game space (poh_games_room_2, 2 by 2), prize chest space (_4), stone space
        // (_5), elemental balance space (_6) and ranging game space (_7), the second and fourth to seventh of its places.
        15342 to Hotspot(RoomType.GAMES_ROOM, 1),
        15343 to Hotspot(RoomType.GAMES_ROOM, 3),
        15344 to Hotspot(RoomType.GAMES_ROOM, 4),
        15345 to Hotspot(RoomType.GAMES_ROOM, 5),
        15346 to Hotspot(RoomType.GAMES_ROOM, 6),
    )

    // The combat ring space's tiles, by their loc names: poh_gr_1_wall_combat, _bluecorner, _cobat_corner, _everything,
    // _redcorner, _combat_agility_corner, _ranging, _ranging_agility, _agility, _combat_agility, _ranging_combat; and
    // _floor_n, _ne, _nw, _side, _middle, _s, _sw, _se. "Combat" is the three fighting rings, "ranging" the pedestals,
    // "agility" the balance beam.
    private const val RING_WALL = "ring_wall"
    private const val RING_BLUE_CORNER = "ring_blue_corner"
    private const val RING_CORNER = "ring_corner"
    private const val RING_EVERY_WALL = "ring_every_wall"
    private const val RING_RED_CORNER = "ring_red_corner"
    private const val RING_BEAM_CORNER = "ring_beam_corner"
    private const val PEDESTAL_WALL = "pedestal_wall"
    private const val PEDESTAL_BEAM_WALL = "pedestal_beam_wall"
    private const val BEAM_WALL = "beam_wall"
    private const val RING_BEAM_WALL = "ring_beam_wall"
    private const val RING_PEDESTAL_WALL = "ring_pedestal_wall"
    private const val FLOOR_N = "floor_n"
    private const val FLOOR_NE = "floor_ne"
    private const val FLOOR_NW = "floor_nw"
    private const val FLOOR_SIDE = "floor_side"
    private const val FLOOR_MIDDLE = "floor_middle"
    private const val FLOOR_S = "floor_s"
    private const val FLOOR_SW = "floor_sw"
    private const val FLOOR_SE = "floor_se"

    private val RING_WALLS = listOf(RING_WALL, RING_EVERY_WALL, RING_BEAM_WALL, RING_PEDESTAL_WALL)
    private val RING_CORNERS = listOf(RING_CORNER, RING_BLUE_CORNER, RING_RED_CORNER, RING_BEAM_CORNER)
    private val FLOOR_CORNERS = listOf(FLOOR_NW, FLOOR_NE, FLOOR_SW, FLOOR_SE)
    private val FLOOR_SIDES = listOf(FLOOR_N, FLOOR_S, FLOOR_SIDE)
    private val RING_SPACE = RING_WALLS + RING_CORNERS + listOf(PEDESTAL_WALL, PEDESTAL_BEAM_WALL, BEAM_WALL) + FLOOR_CORNERS + FLOOR_SIDES + FLOOR_MIDDLE

    /**
     * One of the combat ring space's pieces: [parts] by tile kind, every other tile of the space bare. The ring's
     * walls, by the hotspots' names: a fighting ring's rope goes on every "combat" wall and corner, a mat on the floor
     * (corners, sides and middle).
     */
    private fun ringSpace(xp: Int, parts: Map<String, Int>) = Build(parts.values.first(), xp, RING_SPACE.associateWith { parts[it] ?: NONE })

    /** A boxing, fencing or combat ring: [rope] on the walls, [corner] on the two plain corners, the mat on the floor. */
    private fun fightingRing(xp: Int, rope: Int, corner: Int, blue: Int, red: Int, matCorner: Int, matSide: Int, matMiddle: Int) = ringSpace(
        xp,
        RING_WALLS.associateWith { rope } + mapOf(RING_CORNER to corner, RING_BEAM_CORNER to corner, RING_BLUE_CORNER to blue, RING_RED_CORNER to red) +
            FLOOR_CORNERS.associateWith { matCorner } + FLOOR_SIDES.associateWith { matSide } + mapOf(FLOOR_MIDDLE to matMiddle),
    )

    /** A rug: its corners, sides and middle; in a hall it leaves the stair space bare. */
    private fun rug(corner: Int, side: Int, middle: Int, xp: Int) =
        Build(middle, xp, mapOf(CORNER to corner, SIDE to side, MIDDLE to middle, STAIRS to NONE, TOP to NONE))

    /**
     * A staircase: [foot] on a hall's stair space, [top] on the stair space of the hall at its top; the rug's spaces
     * round it stay bare (GUESSED: the cache puts both in one place, so a hall has a rug or a staircase).
     */
    private fun staircase(foot: Int, top: Int, xp: Int) = Build(foot, xp, mapOf(CORNER to NONE, SIDE to NONE, MIDDLE to NONE, TOP to top))

    private fun shelves(loc: Int, crockery: Int, xp: Int) = Build(loc, xp, mapOf(CROCKERY to crockery))

    private fun bookcase(loc: Int, scrolls: Int, xp: Int) = Build(loc, xp, mapOf(SCROLLS to scrolls))

    /** A garden tree: on the tree space [loc], on the big tree space [big]; as much Farming XP as Construction (the Garden page). */
    private fun tree(loc: Int, big: Int, xp: Int) = Build(loc, xp, mapOf(BIG to big), farmingXp = xp)

    private fun plant(loc: Int, xp: Int) = Build(loc, xp, farmingXp = xp)

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
        // The garden (the Garden page's XP). The exit portal, decorative rock, pond and imp statue (poh_exit_portal,
        // poh_crude_garden_centrepiece2 to 4). Left out: the dungeon entrance (5878, with the dungeon), the Greenman
        // statue (6028, a loc per rotation), the Annihilation portal (9654, a Leagues unlock), and the event pumpkin and
        // beehives (6023 to 6025) and the tip jar (5899).
        5874 to Build(House.EXIT_PORTAL, 100),
        5875 to Build(4526, 100),
        5876 to Build(4527, 100),
        5877 to Build(4528, 150),
        // Dead, nice, oak, willow, maple, yew and magic trees (poh_small_tree1_5 to 7_5, and poh_big_tree1_4 to 7_4).
        5880 to tree(4538, 4531, 31),
        5881 to tree(4539, 4532, 44),
        5882 to tree(4540, 4533, 70),
        5883 to tree(4541, 4534, 100),
        5884 to tree(5126, 4535, 122),
        5885 to tree(5121, 4536, 141),
        5886 to tree(5127, 4537, 223),
        // The plants, from bagged plants 1 to 3 (poh_plantbig1a to 2c, poh_plantbsmall1a to 2c).
        5887 to plant(5128, 31), 5888 to plant(5129, 70), 5889 to plant(5130, 100),
        5890 to plant(5131, 31), 5891 to plant(5132, 70), 5892 to plant(5133, 100),
        5893 to plant(5134, 31), 5894 to plant(5135, 70), 5895 to plant(5136, 100),
        5896 to plant(5137, 31), 5897 to plant(5138, 70), 5898 to plant(5139, 100),
        // The staircases, by the order of their locs' names (inferred): oak, teak and marble are poh_stairs_3 to 5 and
        // their poh_stairstop_3 to 5; the limestone and marble spirals are poh_spiralstairs and _2, the same loc at the
        // top. XP is the Stair Space page's.
        5644 to staircase(13497, 13498, 680),
        5647 to staircase(13499, 13500, 980),
        5650 to staircase(13503, 13503, 1040),
        5651 to staircase(13501, 13502, 3200),
        5654 to staircase(13505, 13505, 4400),
        // The combat room (the Combat room page's XP). The rings, built over the whole combat ring space; which part goes
        // on which tile is INFERRED from the hotspots' and the parts' names (poh_boxing_ringwall_white, _corner, _red,
        // _blue and poh_boxing_ring_mat_*; poh_fencing_ringwall and _ring_mat_*; poh_combat_ringwall and _mat_*): the
        // rope on the 6 by 6 ring's edge, the red and blue corners on the corner hotspots named for them, the mat on the
        // 4 by 4 floor inside.
        5633 to fightingRing(420, 13129, 13130, 13132, 13131, 13126, 13128, 13127),
        5634 to fightingRing(570, 13133, 13133, 13133, 13133, 13135, 13134, 13136),
        5635 to fightingRing(630, 13137, 13137, 13137, 13137, 13138, 13139, 13140),
        // Ranging pedestals: a ranging spot (poh_magic_circle_mat) on the north-west and south-east floor corners, each
        // walled in by the "ranging" walls' magic barriers (poh_magic_circle_wall). INFERRED likewise.
        5636 to ringSpace(720, listOf(RING_EVERY_WALL, PEDESTAL_WALL, PEDESTAL_BEAM_WALL, RING_PEDESTAL_WALL).associateWith { 13145 } + mapOf(FLOOR_NW to 13147, FLOOR_SE to 13147)),
        // The balance beam: its left end, middle and right end (poh_balancebeam_endl, _middle, _endr) along the north row
        // of the floor, railed in by the "agility" walls (poh_agility_rail). INFERRED likewise.
        BALANCE_BEAM to ringSpace(
            1000,
            mapOf(FLOOR_NW to 13143, FLOOR_N to 13142, FLOOR_NE to 13144) +
                listOf(RING_EVERY_WALL, PEDESTAL_BEAM_WALL, BEAM_WALL, RING_BEAM_WALL, RING_BEAM_CORNER).associateWith { 13141 },
        ),
        // Glove rack, weapons rack, extra weapons rack (poh_weapons_rack_gloves, _gloves+woodenstuff, +pugels).
        5638 to Build(13381, 120),
        5639 to Build(13382, 180),
        5640 to Build(13383, 440),
        // Combat dummy, undead and ornate undead (poh_combat_dummy, _undeadslayer, poh_combat_dummy_upgraded_1_undead),
        // each over the one before (the Undead and Ornate undead combat dummy pages: 660, 220 and 300 XP).
        5641 to Build(29336, 660),
        5642 to Build(29337, 220),
        5643 to Build(9355, 300),
        // The games room (the pieces' Wiki pages' XP). The game space: Jacky Jester (poh_mime_jester), the Treasure Hunt
        // Fairy House (poh_fairy_house) and the Hangman game (poh_hangman_chest).
        5618 to Build(13390, 360),
        5619 to Build(13379, 800),
        5620 to Build(13404, 1200),
        // The oak, teak and mahogany prize chests, shut (poh_prize_chest_oak_closed, _teak_, _mag_).
        5621 to Build(13385, 240),
        5622 to Build(13387, 660),
        5623 to Build(13389, 860),
        // The clay, limestone and marble attack stones (poh_tbt_clay_new, _limestone_new, _marble_new: the clay one's
        // recolour is the Clay attack stone's menu icon's; the Wiki's infoboxes have 13392 and 13393 the other way).
        5624 to Build(13393, 100),
        5625 to Build(13392, 200),
        5626 to Build(13394, 2000),
        // The elemental balances 1 to 3 (poh_elemental_orb_1 to _3).
        5627 to Build(13395, 176),
        5628 to Build(13396, 252),
        5629 to Build(13397, 356),
        // Hoop and stick, dartboard, archery target: the first of each pair (poh_stick+hoop1, poh_dartboard1,
        // poh_archery_target1). INFERRED: the stick alone is the one built, as the menu icon's model is the stick's
        // alone, and "Stick and hoop" (poh_stick+hoop2) is a hoop landed on it; the Dartboard page has the second
        // dartboard "darted".
        5630 to Build(13398, 120),
        5631 to Build(13400, 290),
        5632 to Build(13402, 600),
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
        (RoomType.GARDEN to 0) to listOf(5874, 5875, 5876, 5877, 5878, 5879, 6028, 9654),
        (RoomType.GARDEN to 1) to 5880..5886,
        (RoomType.GARDEN to 2) to 5880..5886,
        (RoomType.GARDEN to 3) to listOf(5887, 5888, 5889, 6023, 6024, 6025, 6028),
        (RoomType.GARDEN to 4) to listOf(5890, 5891, 5892, 6023, 6024, 6025, 6028),
        (RoomType.GARDEN to 5) to 5893..5895,
        (RoomType.GARDEN to 6) to 5896..5898,
        (RoomType.SKILL_HALL to 0) to listOf(5525, 5526, 5644, 5647, 5650, 5651, 5654, 5527, 5528),
        (RoomType.QUEST_HALL to 0) to listOf(5525, 5526, 5644, 5647, 5650, 5651, 5654, 5527, 5528),
        (RoomType.COMBAT_ROOM to 0) to 5633..5637,
        (RoomType.COMBAT_ROOM to 3) to 5638..5640,
        (RoomType.COMBAT_ROOM to 4) to listOf(5587, 5588, 5589, 6027),
        (RoomType.COMBAT_ROOM to 5) to 5641..5643,
        (RoomType.GAMES_ROOM to 1) to 5618..5620,
        (RoomType.GAMES_ROOM to 3) to 5621..5623,
        (RoomType.GAMES_ROOM to 4) to 5624..5626,
        (RoomType.GAMES_ROOM to 5) to 5627..5629,
        (RoomType.GAMES_ROOM to 6) to 5630..5632,
    )

    /** The balance beam's row ([CombatRoom.offBeam]). */
    const val BALANCE_BEAM = 5637

    /** The staircases' rows (the Stair Space page). */
    val STAIRCASES: Set<Int> = setOf(5644, 5647, 5650, 5651, 5654)

    /** The bagged trees and plants, planted with a watering can with water in it (the Garden page's footnotes; Codex on #979). */
    val NEEDS_WATERING_CAN: Set<Int> = (5880..5898).toSet()

    /** The garden's trees and plants, planted without a hammer and saw (the Garden page: those are for the rest). */
    val PLANTED: Set<Int> = (5880..5898).toSet()

    /** Watering cans with water, (1) to (8) (`watering_can_1` to `_8`), and Gricoller's can. */
    val WATERING_CANS: List<Int> = (5333..5340).toList() + 13353

    /** Whether [loc] is what one of the pieces [hotspot]'s place offers builds as on that hotspot. */
    fun buildsOn(hotspot: Int, loc: Int): Boolean {
        if (loc == NONE) return false
        val spot = HOTSPOTS[hotspot] ?: return false
        val rows = PLACES[spot.room to spot.index] ?: return false
        return rows.any { row -> BUILDS[row]?.locFor(spot.variant) == loc }
    }

    /**
     * Whether furniture [row] is one [hotspot]'s place offers and builds on it: every part it puts on the place's
     * hotspots is one [buildsOn] accepts there, and it puts one on at least one of them.
     */
    fun buildsAt(hotspot: Int, row: Int, type: RoomType? = null): Boolean {
        val spot = HOTSPOTS[hotspot] ?: return false
        if (PLACES[spot.room to spot.index]?.contains(row) != true) return false
        val build = BUILDS[row] ?: return false
        val parts = siblings(hotspot, type).map { it to build.locFor(HOTSPOTS.getValue(it).variant) }.filter { it.second != NONE }
        return parts.isNotEmpty() && parts.all { (id, loc) -> buildsOn(id, loc) }
    }

    /**
     * The furniture row built at [hotspot]'s place, from what [built] (a room's, by hotspot) holds on it and its siblings:
     * the one row whose every part matches, or null when nothing is built there (or what is doesn't match one row).
     */
    fun builtRow(built: Map<Int, Int>, hotspot: Int, type: RoomType? = null): Int? {
        val spot = HOTSPOTS[hotspot] ?: return null
        val ids = siblings(hotspot, type)
        if (ids.none { it in built }) return null
        return PLACES[spot.room to spot.index]?.firstOrNull { row ->
            val build = BUILDS[row] ?: return@firstOrNull false
            ids.all { id -> (built[id] ?: NONE) == build.locFor(HOTSPOTS.getValue(id).variant) }
        }
    }

    /**
     * Built locs with Upgrade as their op 4: `poh_tools1` to `4`, the steel framed bench and the bench with a vice,
     * crafting tables 1 to 3, and the combat dummy and undead combat dummy. Each opens the creation menu with what builds over it.
     */
    val UPGRADABLE: Set<Int> = setOf(6786, 6787, 6788, 6789, 6793, 6794, 6796, 6797, 6798, 29336, 29337)

    /**
     * The oak, teak and gilded wall decorations: each shows the player's family crest (poh_decor_oak_arrav and the
     * rest), which Sir Renitee gives; until crests exist they can't be built ([ConstructionLines.FURNITURE_CREST]).
     */
    val NEEDS_CREST: Set<Int> = setOf(5587, 5588, 5589)

    /**
     * The hotspot loc types at the same place in a [type] room as [hotspot] (itself included): built and removed
     * together. Without a [type], the room [hotspot] is only in, else its own room's.
     */
    fun siblings(hotspot: Int, type: RoomType? = null): Set<Int> {
        val spot = HOTSPOTS[hotspot] ?: return setOf(hotspot)
        val room = type ?: spot.only ?: spot.room
        return HOTSPOTS.filterValues { it.room == spot.room && it.index == spot.index && (it.only == null || it.only == room) }.keys
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
 * Step CON4 (the workshop): a piece whose row has `upgrade_source_relative` builds over the piece before it, by its
 * Upgrade op ([FurnitureBuilds.UPGRADABLE]); a tool store racks its first spaces ([FurnitureBuilds.NONE] leaves the rest
 * bare). The garden: trees and plants go in without a hammer and saw and give as much Farming XP as Construction, a
 * bagged tree or plant needs a watering can with water, and the house's only exit portal can't be removed (the Garden and Exit
 * portal pages). GUESSED: the can's water isn't used; planting plays the build animation.
 *
 * Step CON6a (the combat room): a ring is built over the whole combat ring space, one hotspot loc type a tile, each
 * taking the ring's part for it or going bare ([FurnitureBuilds.NONE]); which part goes where is INFERRED from the locs'
 * names. The combat dummy upgrades as the tool stores do. Its racks and ropes are [CombatRoom]'s.
 *
 * GUESSED: the materials text's layout (count and name, the obj types' names from the cache); the build taking 2
 * ticks; the remove prompt; removing with the same animation; the messages (held, no source yet). Left out: nail
 * bending (the Wiki says nails bend, more often with worse nails and lower levels, but gives no odds); the crystal
 * saw's +3 boost; flatpacks (with the workshop).
 */
class Furniture(private val world: World, private val houses: Houses, internal val data: FurnitureData) {
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
        val hotspot = house.hotspots[Houses.slot(loc.x, loc.z, loc.level, loc.layer)] ?: return
        place(context, hotspot, loc)
    }

    /**
     * Builds on [hotspot]'s place, shown on its tile as [shown] (the hotspot itself, or what is built there): the menu of
     * what can go there now, then the picked piece built over whatever is. A tool space left bare beside a tool store
     * offers the store's upgrades, as its rack's Upgrade does (GUESSED).
     */
    private suspend fun place(context: ScriptContext, hotspot: ZoneLoc, shown: ZoneLoc) {
        val player = context.player
        val (house, room) = houses.buildingRoom(player, hotspot.x, hotspot.z, hotspot.level) ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        val state = player.house ?: return
        // Only a hotspot of the room it stands in: what is built is saved, and a save checks the two match (R50B note).
        val spot = FurnitureBuilds.HOTSPOTS[hotspot.id]
        if (spot == null || spot.room != room.type.base || (spot.only != null && spot.only != room.type)) return world.message(player, GameLines.NOTHING_INTERESTING)
        if ((room.built[hotspot.id] ?: hotspot.id) != shown.id) return
        val current = FurnitureBuilds.builtRow(room.built, hotspot.id, room.type)
        if (current == null && hotspot.id in room.built) return
        val options = menu(hotspot.id, current)
        if (options.isEmpty()) return world.message(player, GameLines.NOTHING_INTERESTING)
        openMenu(player, options)
        val slot = context.pauseButton(MENU, MENU_CONTENTS, 1..options.size)
        world.interfaces.closeModals(player)
        val piece = options.getOrNull(slot - 1) ?: return
        if (piece.row in FurnitureBuilds.NEEDS_CREST) return world.message(player, ConstructionLines.FURNITURE_CREST)
        val build = FurnitureBuilds.BUILDS[piece.row] ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        // Only a piece the save check knows for this place (R52B-1): what is built must always load again. Checked across
        // the place, as a tool store leaves bare the spaces past its own (Codex review on #968).
        if (!FurnitureBuilds.buildsAt(hotspot.id, piece.row, room.type)) return world.message(player, GameLines.NOTHING_INTERESTING)
        // Step CON5: a staircase asks which way it goes; upstairs it must be the one of the hall below.
        val staircase = piece.row in FurnitureBuilds.STAIRCASES
        if (staircase && !Stairs.prepare(world, context, state, room, piece.row)) return
        if (!placeStands(house, room, hotspot, shown, current, player)) return
        if (player.skills.currentLevel(Skill.CONSTRUCTION) < piece.level) return world.message(player, ConstructionLines.FURNITURE_LEVEL, "level" to piece.level)
        lacking(player, piece)?.let { (skill, level) -> return world.message(player, ConstructionLines.FURNITURE_SKILL_LEVEL, "skill" to skill.displayName, "level" to level) }
        if (piece.row !in FurnitureBuilds.PLANTED && !hasTools(player)) return world.message(player, ConstructionLines.FURNITURE_TOOLS)
        // GUESSED: the can's water isn't used up (the page says only that one with a dose is needed).
        if (piece.row in FurnitureBuilds.NEEDS_WATERING_CAN && FurnitureBuilds.WATERING_CANS.none { player.inventory.count(it) > 0 }) {
            return world.message(player, ConstructionLines.FURNITURE_WATERING_CAN)
        }
        val nails = nailsFor(player, piece) ?: return world.message(player, ConstructionLines.FURNITURE_MATERIALS)
        player.anim(BUILD_ANIM)
        context.delay(BUILD_TICKS)
        // Checked again after the wait: the materials may have gone, or the room or hotspot with them, or the hall below.
        if (!placeStands(house, room, hotspot, shown, current, player)) return
        if (staircase && !Stairs.fits(state, room, piece.row)) return
        if (nailsFor(player, piece) != nails) return world.message(player, ConstructionLines.FURNITURE_MATERIALS)
        for ((obj, count) in piece.materials) if (obj != ANY_NAILS) Ned.take(player.inventory, obj, count)
        for ((kind, count) in nails) Ned.take(player.inventory, kind, count)
        player.skills.addTenths(Skill.CONSTRUCTION, build.xp * 10)
        if (build.farmingXp > 0) player.skills.addTenths(Skill.FARMING, build.farmingXp * 10)
        // Every hotspot of the same place in the room takes the piece: each rug tile its corner, side or middle, every
        // window its curtains.
        fill(room, hotspot.id, build)
        redraw(house, room, hotspot.id)
        // The balance beam's rails would fence in anyone on its row (round 63, R63A-1).
        if (piece.row == FurnitureBuilds.BALANCE_BEAM) world.combatRoom.offBeam(house, room)
        logger.info { "${player.username} built ${piece.name} on hotspot ${hotspot.id} for ${build.xp} XP" }
    }

    /** Puts [build] on [hotspot]'s place in [room]: each of its hotspots there takes its part, or goes bare. */
    internal fun fill(room: HouseRoom, hotspot: Int, build: FurnitureBuilds.Build) {
        for (id in FurnitureBuilds.siblings(hotspot, room.type)) {
            val loc = build.locFor(FurnitureBuilds.HOTSPOTS.getValue(id).variant)
            if (loc == FurnitureBuilds.NONE) room.built.remove(id) else room.built[id] = loc
        }
    }

    /** Whether [player] is still in [house] and [hotspot]'s place in [room] still holds [current], [shown] on its tile. */
    private fun placeStands(house: Houses.Open, room: HouseRoom, hotspot: ZoneLoc, shown: ZoneLoc, current: Int?, player: Player): Boolean =
        houses.stillHere(house, player) && world.locs[shown.x, shown.z, shown.level, shown.layer] == shown &&
            player.house?.room(room.x, room.z, room.floor) === room &&
            (room.built[hotspot.id] ?: hotspot.id) == shown.id && FurnitureBuilds.builtRow(room.built, hotspot.id, room.type) == current

    /** Remove on built furniture [loc]: a prompt, then its hotspot back. */
    private suspend fun remove(context: ScriptContext, loc: ZoneLoc) {
        val player = context.player
        val (house, room) = houses.buildingRoom(player, loc.x, loc.z, loc.level) ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        val hotspot = house.hotspots[Houses.slot(loc.x, loc.z, loc.level, loc.layer)] ?: return
        if (room.built[hotspot.id] != loc.id) return
        // A house needs an exit portal: the last one stays (the Exit portal page).
        if (loc.id == House.EXIT_PORTAL && portals(player) <= 1) return world.message(player, ConstructionLines.REMOVE_ONLY_PORTAL)
        // A staircase to the other floor stays while the room at its top does (step CON5; GUESSED), so nobody is left upstairs.
        if (connects(player, room)) return world.message(player, ConstructionLines.STAIRS_CONNECTED)
        var pick = 0
        world.dialogues.start(context) {
            pick = choice(ConstructionLines.REMOVE_FURNITURE_YES, ConstructionLines.REMOVE_FURNITURE_NO, title = text(ConstructionLines.REMOVE_FURNITURE_TITLE))
        }
        if (pick != 1 || !houses.stillHere(house, player) || room.built[hotspot.id] != loc.id || !world.locs.exists(loc)) return
        if (loc.id == House.EXIT_PORTAL && portals(player) <= 1) return
        if (connects(player, room)) return
        player.anim(BUILD_ANIM)
        // Anyone on the balance beam comes off it first (Codex review on #1018).
        if (loc.id in CombatRoom.BEAMS) world.combatRoom.offBeam(house, room)
        for (id in FurnitureBuilds.siblings(hotspot.id, room.type)) room.built.remove(id)
        redraw(house, room, hotspot.id)
        logger.info { "${player.username} removed furniture ${loc.id} from hotspot ${hotspot.id}" }
    }

    /** Whether [room]'s staircase in [player]'s house leads to the other floor. */
    private fun connects(player: Player, room: HouseRoom): Boolean = player.house?.let { Stairs.connected(it, room) } == true

    /** How many exit portals [player]'s house has. */
    private fun portals(player: Player): Int = player.house?.rooms?.sumOf { room -> room.built.values.count { it == House.EXIT_PORTAL } } ?: 0

    /** Shows what is built now on every tile of [room] holding [hotspot] or one of its siblings, or the hotspot itself. */
    private fun redraw(house: Houses.Open, room: HouseRoom, hotspot: Int) {
        val ids = FurnitureBuilds.siblings(hotspot, room.type)
        val baseX = house.instance.baseX + (room.x shl 3)
        val baseZ = house.instance.baseZ + (room.z shl 3)
        for (spot in house.hotspots.values) {
            if (spot.id !in ids || spot.level != room.floor || spot.x - baseX !in 0..7 || spot.z - baseZ !in 0..7) continue
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
    internal fun nailsFor(player: Player, piece: FurnitureOption): Map<Int, Int>? {
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
    internal fun hasTools(player: Player): Boolean = HAMMERS.any { carries(player, it) } && SAWS.any { carries(player, it) }

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
