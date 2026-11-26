package osrsps.testclient

import osrsps.game.AllSettings
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.PlayerSave
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Cooking
import osrsps.game.skill.construction.CombatRoom
import osrsps.game.skill.construction.EstateAgent
import osrsps.game.skill.construction.Furniture
import osrsps.game.skill.construction.FurnitureData
import osrsps.game.skill.construction.FurnitureOption
import osrsps.game.skill.construction.House
import osrsps.game.skill.construction.Kitchen
import osrsps.game.skill.construction.Houses
import osrsps.game.skill.construction.HouseRoom
import osrsps.game.skill.construction.RoomType
import osrsps.game.skill.construction.Stairs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Construction steps CON1 and CON2 through the headless client: the estate agent sells the starter house, the
 * Rimmington portal takes its owner in with building mode off (walls, doors, the exit portal, no hotspots) or on (the
 * hotspots, the exit portal on its own), the exit portal and a logout put them back outside, and the house is in the
 * save; in building mode a door hotspot adds a room, turned to fit, and removes it. The house's room templates here are
 * the cache's own zones (garden 232,881, parlour 232,887, kitchen 234,887, yard 233,880), with the door hotspots the 242
 * cache places in them. The rl-client scripts test-runner/examples/construction-house.json and construction-rooms.json
 * do the same on the real map.
 */
class HeadlessHouseTest {
    private val agent = NpcType(EstateAgent.ID, "Estate agent", ops = listOf("Talk-to", null, "Relocate", "Redecorate", null), internalName = "poh_estate_agent")

    private fun hotspot(id: Int, name: String, width: Int = 1) =
        LocType(id, name, ops = listOf(null, null, null, null, House.BUILD_OP), collision = LocCollisionType(width = width, length = width, interactType = 0))

    private fun built(id: Int, name: String) =
        LocType(id, name, ops = listOf(null, null, null, null, "Remove"), collision = LocCollisionType(width = 1, length = 1, interactType = 0))

    private fun door(id: Int, op: String) = LocType(id, "Door", ops = listOf(op, null, null, null, null), collision = LocCollisionType(width = 1, length = 1))

    private val types = listOf(
        LocType(House.RIMMINGTON_PORTAL, "Portal", ops = listOf("Enter", "Home", "Build mode", "Friend's house", null), collision = LocCollisionType(width = 5, length = 2)),
        LocType(House.EXIT_PORTAL, "Portal", ops = listOf("Enter", "Lock", "Remove board advert", null, "Remove"), collision = LocCollisionType(width = 2, length = 2)),
        hotspot(House.CENTREPIECE_SPACE, "Centrepiece space", 2),
        hotspot(House.DOOR_HOTSPOT_LEFT, "Door hotspot"),
        hotspot(House.DOOR_HOTSPOT_RIGHT, "Door hotspot"),
        hotspot(CHAIR_SPACE, "Chair space"),
        hotspot(RUG_MIDDLE_SPACE, "Rug space"), hotspot(RUG_SIDE_SPACE, "Rug space"), hotspot(RUG_CORNER_SPACE, "Rug space"),
        LocType(WOODEN_LARDER, "Larder", ops = listOf("Search", null, null, null, "Remove"), collision = LocCollisionType(width = 2, length = 1)),
        LocType(BROWN_RUG_CORNER, "Rug", ops = listOf(null, null, null, null, "Remove"), collision = LocCollisionType(width = 1, length = 1, interactType = 0)),
        LocType(BROWN_RUG_SIDE, "Rug", ops = listOf(null, null, null, null, "Remove"), collision = LocCollisionType(width = 1, length = 1, interactType = 0)),
        LocType(BROWN_RUG_MIDDLE, "Rug", ops = listOf(null, null, null, null, "Remove"), collision = LocCollisionType(width = 1, length = 1, interactType = 0)),
        hotspot(BOOKCASE_SPACE, "Bookcase space"), hotspot(SCROLL_SPACE, "Bookcase space"),
        hotspot(FIREPLACE_SPACE, "Fireplace space"), hotspot(CURTAIN_SPACE, "Curtain space"),
        built(WOODEN_BOOKCASE, "Wooden bookcase"), built(WOODEN_SCROLLS, "Wooden bookcase"),
        built(CLAY_FIREPLACE, "Clay fireplace"), built(TORN_CURTAINS, "Torn curtains"),
        hotspot(TABLE_SPACE, "Table space"), hotspot(DECORATION_SPACE, "Decoration space"),
        built(WOOD_TABLE, "Table"), built(GREENMAN, "Greenman carving"),
        hotspot(STOVE_SPACE, "Stove space"), hotspot(SHELF_SPACE, "Shelf space"), hotspot(CROCKERY_SPACE, "Shelf space"), hotspot(SINK_SPACE, "Sink space"),
        built(FIREPIT, "Firepit"), built(PUMP, "Pump and drain"),
        LocType(WOODEN_SHELVES, "Shelves", ops = listOf("Search", null, null, null, "Remove"), collision = LocCollisionType(width = 1, length = 1, interactType = 0)),
        LocType(WOODEN_CROCKERY, "Shelves", ops = listOf("Search", null, null, null, "Remove"), collision = LocCollisionType(width = 1, length = 1, interactType = 0)),
        LocType(LARDER_SPACE, "Larder space", ops = listOf(null, null, null, null, House.BUILD_OP), collision = LocCollisionType(width = 2, length = 1, interactType = 0)),
        LocType(House.WALL, "null", collision = LocCollisionType(width = 1, length = 1)),
        LocType(CRUDE_CHAIR, "Chair", ops = listOf("Sit-on", null, null, null, "Remove"), collision = LocCollisionType(width = 1, length = 1)),
        hotspot(WORKBENCH_SPACE, "Workbench space"), hotspot(WORKBENCH_STOOL_SPACE, "Nothing"),
        hotspot(TOOL_SPACE_A, "Tool space"), hotspot(TOOL_SPACE_B, "Tool space"), hotspot(TOOL_SPACE_C, "Tool space"),
        built(WOODEN_WORKBENCH, "Workbench"), built(STOOL, "Stool"),
        LocType(TOOLS_1, "Tools", ops = listOf("Search", null, null, "Upgrade", "Remove"), collision = LocCollisionType(width = 1, length = 1, interactType = 0)),
        LocType(TOOLS_2, "Tools", ops = listOf("Search", null, null, "Upgrade", "Remove"), collision = LocCollisionType(width = 1, length = 1, interactType = 0)),
        hotspot(TREE_SPACE, "Tree space"), hotspot(SMALL_PLANT_SPACE, "Small Plant space 1"),
        built(DEAD_TREE, "Dead Tree"), built(PLANT, "Plant"),
        hotspot(STAIR_SPACE, "Stair Space", 2), hotspot(STAIR_TOP_SPACE, "Stair Space", 2),
        LocType(OAK_STAIRS, "Staircase", ops = listOf("Climb-up", null, null, "Remove-room", "Remove"), collision = LocCollisionType(width = 2, length = 2)),
        LocType(OAK_STAIRS_TOP, "Staircase", ops = listOf("Climb-down", null, null, "Remove-room", "Remove"), collision = LocCollisionType(width = 2, length = 2)),
        door(House.DOOR_LEFT, "Open"), door(House.DOOR_RIGHT, "Open"), door(House.DOOR_LEFT_OPEN, "Close"), door(House.DOOR_RIGHT_OPEN, "Close"),
        hotspot(RING_WALL_SPACE, "Combat ring space"), hotspot(RING_FLOOR_SPACE, "Combat ring space"), hotspot(STORAGE_SPACE, "Storage space"),
        LocType(BOXING_ROPE, "Boxing ring", ops = listOf("Climb-over", null, null, null, "Remove"), collision = LocCollisionType(width = 1, length = 1)),
        built(BOXING_MAT, "Boxing mat"),
        LocType(GLOVE_RACK, "Glove Rack", ops = listOf("Search", null, null, null, "Remove"), collision = LocCollisionType(width = 1, length = 1, interactType = 0)),
    ).associateBy { it.id }

    /** The four door hotspot pairs of a template zone at [x], [z] on the sides [sides] (0 west, 1 north, 2 east, 3 south), as the cache places them. */
    private fun doors(x: Int, z: Int, sides: List<Int>): List<ZoneLoc> = sides.flatMap { side ->
        when (side) {
            0 -> listOf(ZoneLoc(House.DOOR_HOTSPOT_LEFT, LocShape.WALL_STRAIGHT, 0, x, z + 4, 0), ZoneLoc(House.DOOR_HOTSPOT_RIGHT, LocShape.WALL_STRAIGHT, 0, x, z + 3, 0))
            1 -> listOf(ZoneLoc(House.DOOR_HOTSPOT_LEFT, LocShape.WALL_STRAIGHT, 1, x + 4, z + 7, 0), ZoneLoc(House.DOOR_HOTSPOT_RIGHT, LocShape.WALL_STRAIGHT, 1, x + 3, z + 7, 0))
            2 -> listOf(ZoneLoc(House.DOOR_HOTSPOT_LEFT, LocShape.WALL_STRAIGHT, 2, x + 7, z + 3, 0), ZoneLoc(House.DOOR_HOTSPOT_RIGHT, LocShape.WALL_STRAIGHT, 2, x + 7, z + 4, 0))
            else -> listOf(ZoneLoc(House.DOOR_HOTSPOT_LEFT, LocShape.WALL_STRAIGHT, 3, x + 3, z, 0), ZoneLoc(House.DOOR_HOTSPOT_RIGHT, LocShape.WALL_STRAIGHT, 3, x + 4, z, 0))
        }
    }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Furniture.ANY_NAILS + 1)
        objs[EstateAgent.COINS] = ObjType(EstateAgent.COINS, "Coins", stackable = true)
        objs[PLANK] = ObjType(PLANK, "Plank")
        objs[BOLT_OF_CLOTH] = ObjType(BOLT_OF_CLOTH, "Bolt of cloth")
        objs[SOFT_CLAY] = ObjType(SOFT_CLAY, "Soft clay")
        objs[TEA_LEAVES] = ObjType(TEA_LEAVES, "Tea leaves")
        objs[MILK] = ObjType(MILK, "Bucket of milk")
        objs[STEEL_NAILS] = ObjType(STEEL_NAILS, "Steel nails", stackable = true)
        objs[BRONZE_NAILS] = ObjType(BRONZE_NAILS, "Bronze nails", stackable = true)
        objs[Furniture.ANY_NAILS] = ObjType(Furniture.ANY_NAILS, "Nails")
        objs[HAMMER] = ObjType(HAMMER, "Hammer")
        objs[SAW] = ObjType(SAW, "Saw")
        objs[EstateAgent.GUIDE_BOOK] = ObjType(EstateAgent.GUIDE_BOOK, "Construction guide")
        objs[STEEL_BAR] = ObjType(STEEL_BAR, "Steel bar")
        objs[OAK_PLANK] = ObjType(OAK_PLANK, "Oak plank")
        objs[GREENMAN_ITEM] = ObjType(GREENMAN_ITEM, "Greenman carving")
        objs[KETTLE] = ObjType(KETTLE, "Kettle")
        objs[CLAY_TEAPOT] = ObjType(CLAY_TEAPOT, "Teapot")
        objs[CLAY_CUP] = ObjType(CLAY_CUP, "Empty cup")
        objs[BUCKET] = ObjType(BUCKET, "Bucket")
        objs[BUCKET_OF_WATER] = ObjType(BUCKET_OF_WATER, "Bucket of water")
        objs[CHISEL] = ObjType(CHISEL, "Chisel")
        objs[BAGGED_DEAD_TREE] = ObjType(BAGGED_DEAD_TREE, "Bagged dead tree")
        objs[BAGGED_PLANT_1] = ObjType(BAGGED_PLANT_1, "Bagged plant 1")
        objs[WATERING_CAN_8] = ObjType(WATERING_CAN_8, "Watering can(8)")
        objs[KNIFE] = ObjType(KNIFE, "Knife")
        for (gloves in listOf(CombatRoom.RED_GLOVES, CombatRoom.BLUE_GLOVES)) {
            objs[gloves] = ObjType(gloves, "Boxing gloves", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.WEAPON, wearpos2 = WearPos.SHIELD, wearpos3 = WearPos.HANDS)
        }
        for (id in listOf(Cooking.SHRIMPS.raw, Cooking.SHRIMPS.cooked, Cooking.SHRIMPS.burnt)) objs[id] = ObjType(id, "Shrimps")
        val garden = RoomType.GARDEN
        val parlour = RoomType.PARLOUR
        val kitchen = RoomType.KITCHEN
        val dining = RoomType.DINING_ROOM
        val workshop = RoomType.WORKSHOP
        val hall = RoomType.SKILL_HALL
        val hallTop = RoomType.SKILL_HALL_STAIRS
        val combat = RoomType.COMBAT_ROOM
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(garden.templateX shr 6, garden.templateZ shr 6)
                markLoaded(PORTAL_X shr 6, PORTAL_Z shr 6)
                markLoaded(AGENT_X shr 6, AGENT_Z shr 6)
            },
            objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(agent),
            npcSpawns = listOf(NpcSpawn(agent, AGENT_X, AGENT_Z, source = "test")),
            locTypes = LocTypes(types),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(House.RIMMINGTON_PORTAL, LocShape.CENTREPIECE_STRAIGHT, 1, PORTAL_X, PORTAL_Z, 0))
                add(ZoneLoc(House.CENTREPIECE_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, garden.templateX + 3, garden.templateZ + 3, 0))
                doors(garden.templateX, garden.templateZ, listOf(0, 1, 2, 3)).forEach(::add)
                // A tree space and a small plant space, one tile each (the cache's tree space is 2 by 2).
                add(ZoneLoc(TREE_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, garden.templateX + 6, garden.templateZ + 6, 0))
                add(ZoneLoc(SMALL_PLANT_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, garden.templateX + 1, garden.templateZ + 6, 0))
                add(ZoneLoc(CHAIR_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, parlour.templateX + 2, parlour.templateZ + 4, 0))
                // A small rug: a corner, a side and the middle, one tile each (the cache's rug is bigger).
                add(ZoneLoc(RUG_CORNER_SPACE, LocShape.GROUND_DECOR, 0, parlour.templateX + 1, parlour.templateZ + 1, 0))
                add(ZoneLoc(RUG_SIDE_SPACE, LocShape.GROUND_DECOR, 0, parlour.templateX + 2, parlour.templateZ + 1, 0))
                add(ZoneLoc(RUG_MIDDLE_SPACE, LocShape.GROUND_DECOR, 0, parlour.templateX + 2, parlour.templateZ + 2, 0))
                // A bookcase with its scroll rack beside it, a fireplace, and curtains on two windows of the north wall.
                add(ZoneLoc(BOOKCASE_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, parlour.templateX + 6, parlour.templateZ + 2, 0))
                add(ZoneLoc(SCROLL_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, parlour.templateX + 6, parlour.templateZ + 3, 0))
                add(ZoneLoc(FIREPLACE_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, parlour.templateX + 4, parlour.templateZ + 6, 0))
                add(ZoneLoc(CURTAIN_SPACE, LocShape.WALL_STRAIGHT, 1, parlour.templateX + 2, parlour.templateZ + 7, 0))
                add(ZoneLoc(CURTAIN_SPACE, LocShape.WALL_STRAIGHT, 1, parlour.templateX + 5, parlour.templateZ + 7, 0))
                doors(parlour.templateX, parlour.templateZ, listOf(0, 2, 3)).forEach(::add)
                // The kitchen's doors are west and south; a 2 by 1 space in its north-west shows how a turned room moves its locs.
                doors(kitchen.templateX, kitchen.templateZ, listOf(0, 3)).forEach(::add)
                // A dining room's table space and wall decoration space, one tile each.
                add(ZoneLoc(TABLE_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, dining.templateX + 3, dining.templateZ + 3, 0))
                add(ZoneLoc(DECORATION_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, dining.templateX + 1, dining.templateZ + 6, 0))
                add(ZoneLoc(LARDER_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, kitchen.templateX + 1, kitchen.templateZ + 6, 0))
                // A stove space, shelves with their crockery above them, and a sink space, one tile each.
                add(ZoneLoc(STOVE_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, kitchen.templateX + 4, kitchen.templateZ + 4, 0))
                add(ZoneLoc(SHELF_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, kitchen.templateX + 6, kitchen.templateZ + 6, 0))
                add(ZoneLoc(CROCKERY_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, kitchen.templateX + 6, kitchen.templateZ + 5, 0))
                add(ZoneLoc(SINK_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, kitchen.templateX + 6, kitchen.templateZ + 2, 0))
                // A workshop's workbench space with its stool beside it, and three tool spaces along its west wall.
                add(ZoneLoc(WORKBENCH_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, workshop.templateX + 3, workshop.templateZ + 3, 0))
                add(ZoneLoc(WORKBENCH_STOOL_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, workshop.templateX + 3, workshop.templateZ + 2, 0))
                for ((i, space) in listOf(TOOL_SPACE_A, TOOL_SPACE_B, TOOL_SPACE_C).withIndex()) {
                    add(ZoneLoc(space, LocShape.CENTREPIECE_STRAIGHT, 0, workshop.templateX + 1, workshop.templateZ + 2 + i, 0))
                }
                // A skill hall's stair space, 2 by 2 at 3,3 as the cache has it, and the same hall at a staircase's top.
                add(ZoneLoc(STAIR_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, hall.templateX + 3, hall.templateZ + 3, 0))
                add(ZoneLoc(STAIR_TOP_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, hallTop.templateX + 3, hallTop.templateZ + 3, 0))
                doors(hall.templateX, hall.templateZ, listOf(0, 1, 2, 3)).forEach(::add)
                doors(hallTop.templateX, hallTop.templateZ, listOf(0, 1, 2, 3)).forEach(::add)
                // A combat room's ring cut down to one rope, on the east side of 2,3, and the mat's middle inside it at 3,3
                // (the cache's is 6 by 6 round a 4 by 4 mat), and its storage space.
                add(ZoneLoc(RING_WALL_SPACE, LocShape.WALL_STRAIGHT, 2, combat.templateX + 2, combat.templateZ + 3, 0))
                add(ZoneLoc(RING_FLOOR_SPACE, LocShape.GROUND_DECOR, 0, combat.templateX + 3, combat.templateZ + 3, 0))
                add(ZoneLoc(STORAGE_SPACE, LocShape.CENTREPIECE_STRAIGHT, 0, combat.templateX + 5, combat.templateZ + 6, 0))
                doors(combat.templateX, combat.templateZ, listOf(0, 2, 3)).forEach(::add)
            },
            // Building mode's varbit, on a varp of its own here (the cache's layout doesn't matter to the server).
            varTypes = VarTypes(3_000, mapOf(House.BUILDING_MODE_VARBIT to VarbitType(2_000, 0, 0))),
            cycleMillis = millis,
            furnitureData = FurnitureData(
                mapOf(
                    RoomType.PARLOUR.id to List(3) { chairs } + listOf(rugs, bookcases, fireplaces, curtains),
                    RoomType.KITCHEN.id to listOf(stoves, shelves, emptyList(), emptyList(), larders, sinks),
                    RoomType.DINING_ROOM.id to listOf(tables, emptyList(), emptyList(), emptyList(), emptyList(), decorations),
                    RoomType.WORKSHOP.id to listOf(workbenches, emptyList(), toolStores),
                    RoomType.GARDEN.id to listOf(emptyList(), emptyList(), trees, emptyList(), emptyList(), smallPlants),
                    RoomType.SKILL_HALL.id to listOf(staircases),
                    RoomType.COMBAT_ROOM.id to listOf(rings, emptyList(), emptyList(), racks),
                ),
            ),
        )
    }

    /** The chair spaces' first two choices, as the cache's furniture table has them (rows 5517 and 5518). */
    private val chairs = listOf(
        FurnitureOption(CRUDE_CHAIR_ROW, "Crude wooden chair", 8309, listOf(PLANK to 2, Furniture.ANY_NAILS to 2), level = 1),
        FurnitureOption(WOODEN_CHAIR_ROW, "Wooden chair", 8310, listOf(PLANK to 3, Furniture.ANY_NAILS to 3), level = 8),
    )

    /** The larder space's first choice (row 5562). */
    private val larders = listOf(FurnitureOption(WOODEN_LARDER_ROW, "Wooden larder", 8233, listOf(PLANK to 8, Furniture.ANY_NAILS to 8), level = 9))

    /** The kitchen's stove, shelf and sink places' first choices (rows 5539, 5546 and 5565), as the cache has them. */
    private val stoves = listOf(FurnitureOption(FIREPIT_ROW, "Firepit", 8216, listOf(STEEL_BAR to 1, SOFT_CLAY to 2), level = 5))
    private val shelves = listOf(FurnitureOption(WOODEN_SHELVES_ROW, "Wooden shelves 1", 8223, listOf(PLANK to 3, Furniture.ANY_NAILS to 3), level = 6))
    private val sinks = listOf(FurnitureOption(PUMP_ROW, "Pump and drain", 8230, listOf(STEEL_BAR to 5), level = 7))

    /** The dining room's table and wall decoration places' choices (rows 5573, 5587 and 6027), as the cache has them. */
    private val tables = listOf(FurnitureOption(WOOD_TABLE_ROW, "Wood dining table", 8115, listOf(PLANK to 4, Furniture.ANY_NAILS to 4), level = 10))
    private val decorations = listOf(
        FurnitureOption(OAK_DECORATION_ROW, "Oak decoration", 8102, listOf(OAK_PLANK to 2), level = 16),
        FurnitureOption(GREENMAN_ROW, "Greenman carving", 31030, listOf(GREENMAN_ITEM to 1), level = 1),
    )

    /** The workbench space's first choice and the tool stores (rows 5749 and 5758 to 5762), as the cache has them: each store upgrades the one before. */
    private val workbenches = listOf(FurnitureOption(WOODEN_WORKBENCH_ROW, "Wooden workbench", 8375, listOf(PLANK to 5, Furniture.ANY_NAILS to 5), level = 17))
    private val toolStores = (1..5).map { tier ->
        FurnitureOption(TOOL_STORE_1_ROW + tier - 1, "Tool store $tier", 8383 + tier, listOf(OAK_PLANK to 2), level = listOf(15, 25, 35, 44, 55)[tier - 1], upgradeFrom = 1)
    }

    /** The tree space's and first small plant space's first choices (rows 5880 and 5893), as the cache has them. */
    private val trees = listOf(FurnitureOption(DEAD_TREE_ROW, "Tree", 8173, listOf(BAGGED_DEAD_TREE to 1), level = 5))
    private val smallPlants = listOf(FurnitureOption(PLANT_ROW, "Plant", 8180, listOf(BAGGED_PLANT_1 to 1), level = 1))

    /**
     * The skill hall's stair space's first staircase (row 5644) at the Stair Space page's level; half its materials (10 oak
     * planks and 4 steel bars), so twice them fit in a backpack.
     */
    private val staircases = listOf(FurnitureOption(OAK_STAIRCASE_ROW, "Oak staircase", 8249, listOf(OAK_PLANK to 5, STEEL_BAR to 2), level = 27))

    /** The rug space's first choice (row 5524). */
    private val rings = listOf(FurnitureOption(BOXING_RING_ROW, "Boxing ring", 8023, listOf(OAK_PLANK to 6, BOLT_OF_CLOTH to 4), level = 32))
    private val racks = listOf(FurnitureOption(GLOVE_RACK_ROW, "Glove rack", 8028, listOf(OAK_PLANK to 2), level = 34))
    private val rugs = listOf(FurnitureOption(BROWN_RUG_ROW, "Brown rug", 8316, listOf(BOLT_OF_CLOTH to 2), level = 2))

    /** The other parlour places' first choices (rows 5529, 5532 and 5535), with the Wiki's materials and levels. */
    private val bookcases = listOf(FurnitureOption(WOODEN_BOOKCASE_ROW, "Wooden bookcase", 8319, listOf(PLANK to 4, Furniture.ANY_NAILS to 4), level = 4))
    private val fireplaces = listOf(FurnitureOption(CLAY_FIREPLACE_ROW, "Clay fireplace", 8325, listOf(SOFT_CLAY to 3), level = 3))
    private val curtains = listOf(FurnitureOption(TORN_CURTAINS_ROW, "Torn curtains", 8322, listOf(PLANK to 3, BOLT_OF_CLOTH to 3, Furniture.ANY_NAILS to 3), level = 2))

    private fun HeadlessClient.ready() = await("the login rebuild") { position }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.chats(vararg keys: String) {
        for (key in keys) {
            awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
            continueDialogue()
        }
    }

    private fun HeadlessClient.menu(vararg keys: String) {
        val menu = awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
        assertEquals(keys.map { "[$it]" }, menu.options)
    }

    private fun HeadlessClient.awaitClosed() = await("the dialogue to close") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.slotOf(id: Int) = inventory(Inventory.INV).indexOfFirst { it?.id == id }

    private fun HeadlessClient.buyHouse() {
        tele(AGENT_X + 1, AGENT_Z)
        command("item ${EstateAgent.COINS} ${EstateAgent.HOUSE_PRICE}")
        await("the coins") { if (count(EstateAgent.COINS) == EstateAgent.HOUSE_PRICE) Unit else null }
        val npc = await("the estate agent") { npcs().firstOrNull { it.id == EstateAgent.ID } }
        npcOp(npc.index, EstateAgent.TALK_TO)
        chats("estate_agent.greeting")
        menu("estate_agent.how_get", "estate_agent.about")
        chooseOption(1)
        chats("estate_agent.how_get", "estate_agent.how_get_reply", "estate_agent.want_buy")
        menu("estate_agent.yes_please", "estate_agent.no_thanks")
        chooseOption(1)
        chats("estate_agent.yes_please")
        // The guide comes with the sale, before the closing lines that could be walked away from.
        await("the guide book") { if (count(EstateAgent.GUIDE_BOOK) == 1) Unit else null }
        chats("estate_agent.sold", "estate_agent.sold_book")
        awaitClosed()
    }

    /** Uses the portal's [op] from outside it, and waits to be in the house; returns where the player landed. */
    private fun HeadlessClient.goIn(op: Int): Coord {
        tele(OUTSIDE.x, OUTSIDE.z)
        locOp(House.RIMMINGTON_PORTAL, PORTAL_X, PORTAL_Z, op)
        return await("the house") { position?.takeIf { it.x >= 6400 && it.level == House.GROUND } }
    }

    private fun HeadlessClient.shown(id: Int, x: Int, z: Int, level: Int = House.GROUND) = locChanges().any { it.id == id && it.coord == Coord(x, z, level) }

    @Test
    fun `the estate agent sells the starter house, and the portal takes its owner in and out of it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                // No house yet: the portal says so.
                client.tele(OUTSIDE.x, OUTSIDE.z)
                client.locOp(House.RIMMINGTON_PORTAL, PORTAL_X, PORTAL_Z, House.HOME)
                client.await("the refusal") { client.messages().firstOrNull { it.text == "[construction.no_house]" } }

                client.buyHouse()
                assertEquals(0, client.count(EstateAgent.COINS))
                val house = assertNotNull(server.world.playerNamed("alice")!!.house)
                assertEquals(listOf(RoomType.GARDEN, RoomType.PARLOUR), house.rooms.map { it.type })

                val at = client.goIn(House.HOME)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val gardenX = instance.baseX + (House.STARTER_X shl 3)
                val gardenZ = instance.baseZ + (House.STARTER_Z shl 3)
                assertEquals(Coord(gardenX + House.ARRIVE_DX, gardenZ + House.ARRIVE_DZ, House.GROUND), at)
                // The client draws each cell from its template, the ground floor on level 1.
                val zones = client.await("the region rebuild") { client.regionZones() }
                assertEquals(CopiedZone(232, 881, 0, 0), zones[Triple(gardenX shr 3, gardenZ shr 3, House.GROUND)])
                assertEquals(CopiedZone(232, 887, 0, 0), zones[Triple(gardenX shr 3, (gardenZ shr 3) + 1, House.GROUND)])
                assertEquals(CopiedZone(233, 880, 0, 0), zones[Triple((gardenX shr 3) - 2, (gardenZ shr 3) - 2, House.GROUND)])
                // A level 1 builder's yard is 5 by 5 cells: none past it.
                assertNull(zones[Triple((gardenX shr 3) + 3, gardenZ shr 3, House.GROUND)])

                // Building mode off: the exit portal on the centrepiece, the garden's door hotspots and the chair space
                // gone, the parlour's south door onto the garden a door, its west and east sides walls.
                val parlourZ = gardenZ + 8
                // Each cell's locs come in their own zone update, so wait for all of them, not just the garden's (a CI
                // run once saw the parlour's arrive after the portal's).
                // Its right leaf is on the left hotspot's tile: the double door's leaves go clockwise along the wall.
                val expected = listOf(
                    Triple(House.EXIT_PORTAL, gardenX + 3, gardenZ + 3) to "the exit portal",
                    Triple(-1, gardenX + 3, gardenZ + 7) to "the garden's north door hotspot goes",
                    Triple(-1, gardenX + 2, parlourZ + 4) to "the chair space goes",
                    Triple(House.DOOR_RIGHT, gardenX + 3, parlourZ) to "the parlour's south door",
                    Triple(House.DOOR_LEFT, gardenX + 4, parlourZ) to "the parlour's south door",
                    Triple(House.WALL, gardenX, parlourZ + 4) to "the parlour's west wall",
                    Triple(House.WALL, gardenX + 7, parlourZ + 3) to "the parlour's east wall",
                )
                client.await("the house's own locs: ${expected.map { it.second }}") {
                    if (expected.all { (loc, _) -> client.shown(loc.first, loc.second, loc.third) }) Unit else null
                }

                // The parlour's door opens as a double door, the house's own.
                client.locOp(House.DOOR_LEFT, gardenX + 4, parlourZ, 1)
                client.await("both leaves open") {
                    if (client.locChanges().any { it.id == House.DOOR_LEFT_OPEN } && client.locChanges().any { it.id == House.DOOR_RIGHT_OPEN }) Unit else null
                }

                // A death here is a safe one, outside the portal she came in by.
                val alice = server.world.playerNamed("alice")!!
                val death = assertNotNull(server.world.houses.deathPlace(alice))
                assertEquals(listOf(true, true, OUTSIDE.x, OUTSIDE.z, 0), listOf(death.safe, death.keepItems, death.toX, death.toZ, death.toLevel))

                // Poisoned in the house, the exit portal puts her outside the Rimmington portal cured, and the house
                // ends once she has gone.
                client.command("poison 6")
                client.await("poisoned") { if (server.world.poison.value(alice) > 0) Unit else null }
                client.locOp(House.EXIT_PORTAL, gardenX + 3, gardenZ + 3, House.EXIT_ENTER)
                client.await("outside") { client.position?.takeIf { it == Coord(OUTSIDE.x, OUTSIDE.z, 0) } }
                client.await("cured") { if (server.world.poison.value(alice) == 0) Unit else null }
                assertNull(server.world.houses.deathPlace(alice), "outside, an ordinary death")
                client.await("the house ended") { if (server.world.instances.all.isEmpty()) Unit else null }

                // Building mode: the hotspots stay, what is built shows on them.
                val built = client.goIn(House.BUILD_MODE)
                val buildBase = assertNotNull(server.world.instances.at(built.x, built.z))
                val bx = buildBase.baseX + (House.STARTER_X shl 3)
                val bz = buildBase.baseZ + (House.STARTER_Z shl 3)
                client.await("the exit portal") { if (client.shown(House.EXIT_PORTAL, bx + 3, bz + 3)) Unit else null }
                assertTrue(client.locChanges().none { it.coord.level == House.GROUND && it.id != House.EXIT_PORTAL }, "only the exit portal changes: ${client.locChanges()}")

                // A save from inside is outside the portal, house and all, and reads back the same.
                val save = server.world.snapshot(server.world.playerNamed("alice")!!)
                assertEquals(Coord(OUTSIDE.x, OUTSIDE.z, 0), Coord(save.x, save.z, save.level))
                val read = assertNotNull(PlayerSave.fromJson(save.toJson("alice")).house)
                assertEquals(listOf("garden 2,2 {${House.CENTREPIECE_SPACE}=${House.EXIT_PORTAL}}", "parlour 2,3 {}"), read.rooms.map { "${it.type.key} ${it.x},${it.z} ${it.built}" })
            }
        }
    }

    @Test
    fun `Enter offers the menu, and a second house is never sold`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.buyHouse()
                client.tele(OUTSIDE.x, OUTSIDE.z)
                client.locOp(House.RIMMINGTON_PORTAL, PORTAL_X, PORTAL_Z, House.ENTER)
                client.menu("construction.enter_house", "construction.enter_building", "construction.enter_friend", "construction.enter_nothing")
                client.chooseOption(2)
                client.await("the house") { client.position?.takeIf { it.x >= 6400 && it.level == House.GROUND } }
                assertEquals(1, server.world.instances.all.size)

                // Owning one, the agent offers the guide book, houses and his cape, and gives no second book.
                client.tele(AGENT_X + 1, AGENT_Z)
                val npc = client.await("the estate agent") { client.npcs().firstOrNull { it.id == EstateAgent.ID } }
                client.npcOp(npc.index, EstateAgent.TALK_TO)
                client.chats("estate_agent.greeting")
                client.menu("estate_agent.guide", "estate_agent.about", "estate_agent.cape")
                client.chooseOption(1)
                client.chats("estate_agent.guide", "estate_agent.guide_has")
                client.awaitClosed()
                assertEquals(1, client.count(EstateAgent.GUIDE_BOOK))
            }
        }
    }

    @Test
    fun `building mode adds a room through a door hotspot, turned to fit, and removes it again`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                client.buyHouse()
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                // The parlour, north of the garden, and its east door hotspot; the kitchen goes in the cell past it.
                val px = instance.baseX + (House.STARTER_X shl 3)
                val pz = instance.baseZ + ((House.STARTER_Z + 1) shl 3)
                val kx = px + 8
                val kitchenZone = Triple(kx shr 3, pz shr 3, House.GROUND)
                val player = server.world.playerNamed("carol")!!

                // Level 1: the menu opens, but the kitchen wants level 5.
                client.locOp(House.DOOR_HOTSPOT_LEFT, px + 7, pz + 3, House.BUILD)
                client.await("the add-room menu") { if (House.ADD_ROOM in client.openInterfaces()) Unit else null }
                client.resumePause(House.ADD_ROOM, House.ADD_ROOM_LIST, RoomType.KITCHEN.id)
                client.await("the level refusal") { client.messages().firstOrNull { it.text == "[construction.room_level level=5]" } }
                assertEquals(2, player.house!!.rooms.size)

                // Level 5 with 5,000 coins: the kitchen's ghost, first turned as it is, its west door on the parlour.
                client.command("setlevel construction 5")
                client.command("item ${EstateAgent.COINS} ${RoomType.KITCHEN.cost}")
                client.await("the coins") { if (client.count(EstateAgent.COINS) == RoomType.KITCHEN.cost) Unit else null }
                client.locOp(House.DOOR_HOTSPOT_LEFT, px + 7, pz + 3, House.BUILD)
                client.await("the add-room menu") { if (House.ADD_ROOM in client.openInterfaces()) Unit else null }
                client.resumePause(House.ADD_ROOM, House.ADD_ROOM_LIST, RoomType.KITCHEN.id)
                client.menu("construction.rotate_clockwise", "construction.rotate_anticlockwise", "construction.build_room", "construction.cancel_room")
                client.await("the ghost") { client.regionZones()?.get(kitchenZone)?.takeIf { it == CopiedZone(234, 887, 0, 0) } }

                // Turned clockwise: its doors west and north still have one on the parlour. The larder space turns too,
                // from 1,6 to 6,5, a quarter round.
                client.chooseOption(1)
                client.await("the turned ghost") { client.regionZones()?.get(kitchenZone)?.takeIf { it == CopiedZone(234, 887, 0, 1) } }
                client.menu("construction.rotate_clockwise", "construction.rotate_anticlockwise", "construction.build_room", "construction.cancel_room")
                val larder = server.world.locs.at(kx + 6, pz + 5, House.GROUND).firstOrNull { it.id == LARDER_SPACE }
                assertEquals(1, assertNotNull(larder, "the larder space at 6,5").angle)
                // A third turn would put its doors east and north: none on the parlour, so clockwise again comes back.
                client.chooseOption(1)
                client.await("back as it is") { client.regionZones()?.get(kitchenZone)?.takeIf { it == CopiedZone(234, 887, 0, 0) } }
                client.menu("construction.rotate_clockwise", "construction.rotate_anticlockwise", "construction.build_room", "construction.cancel_room")
                client.chooseOption(2)
                client.await("turned again") { client.regionZones()?.get(kitchenZone)?.takeIf { it == CopiedZone(234, 887, 0, 1) } }
                client.menu("construction.rotate_clockwise", "construction.rotate_anticlockwise", "construction.build_room", "construction.cancel_room")

                // Built: the coins go, and the house has a kitchen turned once.
                client.chooseOption(3)
                client.awaitClosed()
                client.await("the coins gone") { if (client.count(EstateAgent.COINS) == 0) Unit else null }
                val kitchen = assertNotNull(player.house!!.room(3, 3))
                assertEquals(RoomType.KITCHEN to 1, kitchen.type to kitchen.rotation)

                // Out of building mode it is drawn turned, and the parlour's east doorway is a door onto it.
                assertEquals(1, player.vars.getVarbit(House.BUILDING_MODE_VARBIT))
                client.locOp(House.EXIT_PORTAL, px + 3, pz - 8 + 3, House.EXIT_ENTER)
                client.await("outside") { client.position?.takeIf { it == Coord(OUTSIDE.x, OUTSIDE.z, 0) } }
                // Leaving turns building mode's varbit off again (exploit review R43B-2).
                client.await("building mode off") { if (player.vars.getVarbit(House.BUILDING_MODE_VARBIT) == 0) Unit else null }
                val home = client.goIn(House.HOME)
                val again = assertNotNull(server.world.instances.at(home.x, home.z))
                val hx = again.baseX + (House.STARTER_X shl 3)
                val hz = again.baseZ + ((House.STARTER_Z + 1) shl 3)
                client.await("the kitchen drawn") { client.regionZones()?.get(Triple((hx shr 3) + 1, hz shr 3, House.GROUND))?.takeIf { it == CopiedZone(234, 887, 0, 1) } }
                client.await("the parlour's east door") { if (client.shown(House.DOOR_RIGHT, hx + 7, hz + 3)) Unit else null }

                // In building mode again, Build on that doorway offers to remove the kitchen; yes puts the yard back.
                client.locOp(House.EXIT_PORTAL, hx + 3, hz - 8 + 3, House.EXIT_ENTER)
                client.await("outside") { client.position?.takeIf { it == Coord(OUTSIDE.x, OUTSIDE.z, 0) } }
                val back = client.goIn(House.BUILD_MODE)
                val third = assertNotNull(server.world.instances.at(back.x, back.z))
                val rx = third.baseX + (House.STARTER_X shl 3)
                val rz = third.baseZ + ((House.STARTER_Z + 1) shl 3)
                client.locOp(House.DOOR_HOTSPOT_LEFT, rx + 7, rz + 3, House.BUILD)
                client.menu("construction.remove_yes", "construction.remove_no")
                client.chooseOption(1)
                client.await("the yard back") { client.regionZones()?.get(Triple((rx shr 3) + 1, rz shr 3, House.GROUND))?.takeIf { it == CopiedZone(233, 880, 0, 0) } }
                assertNull(player.house!!.room(3, 3))
                // The garden with the exit portal can't go.
                client.locOp(House.DOOR_HOTSPOT_LEFT, rx + 3, rz, House.BUILD)
                client.await("the refusal") { client.messages().firstOrNull { it.text == "[construction.remove_portal]" } }
                assertEquals(2, player.house!!.rooms.size)
            }
        }
    }

    @Test
    fun `building mode builds a chair on its space from the creation menu, and removes it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready()
                client.buyHouse()
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val cx = instance.baseX + (House.STARTER_X shl 3) + 2
                val cz = instance.baseZ + ((House.STARTER_Z + 1) shl 3) + 4
                val player = server.world.playerNamed("dave")!!
                fun menuOpen() = client.await("the creation menu") { if (Furniture.MENU in client.openInterfaces()) Unit else null }

                // No hammer or saw: the menu opens, both choices crossed out, and building is refused.
                client.locOp(CHAIR_SPACE, cx, cz, House.BUILD)
                menuOpen()
                client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                client.await("the tools refusal") { client.messages().firstOrNull { it.text == "[construction.furniture_tools]" } }
                // The menu's layout script took the chat's keys (script1406's chatdefault_stopinput); its closing gives them back.
                client.await("chatdefault_restoreinput") {
                    client.clientScripts().count { it.id == AllSettings.CHATDEFAULT_RESTOREINPUT_SCRIPT }.takeIf { it == 1 }
                }

                // With the tools and materials: each slot is drawn by the client's own script, the wooden chair crossed out
                // (level 8), and the list laid out for two.
                for (obj in listOf(HAMMER, SAW)) client.command("item $obj 1")
                client.command("item $PLANK 2")
                client.command("item $STEEL_NAILS 2")
                client.command("item $BRONZE_NAILS 3")
                client.await("the materials") { if (client.count(BRONZE_NAILS) == 3) Unit else null }
                client.locOp(CHAIR_SPACE, cx, cz, House.BUILD)
                menuOpen()
                val scripts = client.await("the menu's slots") { client.clientScripts().filter { it.id == Furniture.ENTRY_SCRIPT }.takeIf { it.size >= 4 } }
                assertEquals(listOf<Any>(1, CRUDE_CHAIR_ROW, 1, "2 x Plank|2 x Nails", 1), scripts[2].args)
                assertEquals(listOf<Any>(2, WOODEN_CHAIR_ROW, 8, "3 x Plank|3 x Nails", 0), scripts[3].args)
                assertEquals(listOf<Any>(2, 0), client.clientScripts().last { it.id == Furniture.LAYOUT_SCRIPT }.args)
                client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 2)
                client.await("the level refusal") { client.messages().firstOrNull { it.text == "[construction.furniture_level level=8]" } }

                // The crude wooden chair: the planks go, and the steel nails before the bronze; 58 XP; built and saved.
                client.locOp(CHAIR_SPACE, cx, cz, House.BUILD)
                menuOpen()
                client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                client.await("the chair") { if (client.shown(CRUDE_CHAIR, cx, cz)) Unit else null }
                assertEquals(listOf(0, 0, 3), listOf(client.count(PLANK), client.count(STEEL_NAILS), client.count(BRONZE_NAILS)))
                client.await("the XP") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == 58 } }
                assertEquals(CRUDE_CHAIR, player.house!!.room(House.STARTER_X, House.STARTER_Z + 1)!!.built[CHAIR_SPACE])

                // Out of building mode the chair is there; in it again, Remove asks, and yes puts the space back.
                client.locOp(House.EXIT_PORTAL, at.x, at.z + 1, House.EXIT_ENTER)
                client.await("outside") { client.position?.takeIf { it == Coord(OUTSIDE.x, OUTSIDE.z, 0) } }
                val home = client.goIn(House.HOME)
                val homeInstance = assertNotNull(server.world.instances.at(home.x, home.z))
                val hx = homeInstance.baseX + (House.STARTER_X shl 3) + 2
                val hz = homeInstance.baseZ + ((House.STARTER_Z + 1) shl 3) + 4
                client.await("the chair at home") { if (client.shown(CRUDE_CHAIR, hx, hz)) Unit else null }
                client.locOp(House.EXIT_PORTAL, home.x, home.z + 1, House.EXIT_ENTER)
                client.await("outside again") { client.position?.takeIf { it == Coord(OUTSIDE.x, OUTSIDE.z, 0) } }
                val back = client.goIn(House.BUILD_MODE)
                val third = assertNotNull(server.world.instances.at(back.x, back.z))
                val rx = third.baseX + (House.STARTER_X shl 3) + 2
                val rz = third.baseZ + ((House.STARTER_Z + 1) shl 3) + 4
                client.locOp(CRUDE_CHAIR, rx, rz, Furniture.REMOVE)
                client.menu("construction.remove_furniture_yes", "construction.remove_furniture_no")
                client.chooseOption(1)
                client.await("the space back") { if (client.shown(CHAIR_SPACE, rx, rz)) Unit else null }
                assertNull(player.house!!.room(House.STARTER_X, House.STARTER_Z + 1)!!.built[CHAIR_SPACE])

                // The three bronze nails left over and one steel make the next chair's two: the steel first, then one bronze,
                // as the menu counts every kind together (Codex review on #869).
                client.command("item $PLANK 2")
                client.command("item $STEEL_NAILS 1")
                client.await("more materials") { if (client.count(STEEL_NAILS) == 1 && client.count(PLANK) == 2) Unit else null }
                client.locOp(CHAIR_SPACE, rx, rz, House.BUILD)
                client.await("the creation menu again") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                client.await("the second chair") { if (client.shown(CRUDE_CHAIR, rx, rz)) Unit else null }
                assertEquals(listOf(0, 0, 2), listOf(client.count(PLANK), client.count(STEEL_NAILS), client.count(BRONZE_NAILS)))
            }
        }
    }

    @Test
    fun `a rug is built on every rug space at once, each tile its own part, and mixed nails make up a chair`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.ready()
                client.buyHouse()
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val px = instance.baseX + (House.STARTER_X shl 3)
                val pz = instance.baseZ + ((House.STARTER_Z + 1) shl 3)
                val player = server.world.playerNamed("erin")!!
                client.command("setlevel construction 2")
                for (obj in listOf(HAMMER, SAW)) client.command("item $obj 1")
                client.command("item $BOLT_OF_CLOTH 2")
                client.await("the cloth") { if (client.count(BOLT_OF_CLOTH) == 2) Unit else null }
                val xp = client.await("level 2") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.level == 2 } }.xp

                // Build on the side tile: the corner, the side and the middle each become their part of the brown rug.
                client.locOp(RUG_SIDE_SPACE, px + 2, pz + 1, House.BUILD)
                client.await("the creation menu") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                val parts = listOf(Triple(BROWN_RUG_CORNER, px + 1, pz + 1), Triple(BROWN_RUG_SIDE, px + 2, pz + 1), Triple(BROWN_RUG_MIDDLE, px + 2, pz + 2))
                client.await("the rug") { if (parts.all { (id, x, z) -> client.shown(id, x, z) }) Unit else null }
                client.await("the XP") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == xp + 30 } }
                val room = player.house!!.room(House.STARTER_X, House.STARTER_Z + 1)!!
                assertEquals(mapOf(RUG_CORNER_SPACE to BROWN_RUG_CORNER, RUG_SIDE_SPACE to BROWN_RUG_SIDE, RUG_MIDDLE_SPACE to BROWN_RUG_MIDDLE), room.built)
                assertEquals(0, client.count(BOLT_OF_CLOTH))

                // Remove on the middle takes the whole rug up.
                client.locOp(BROWN_RUG_MIDDLE, px + 2, pz + 2, Furniture.REMOVE)
                client.menu("construction.remove_furniture_yes", "construction.remove_furniture_no")
                client.chooseOption(1)
                val spaces = listOf(Triple(RUG_CORNER_SPACE, px + 1, pz + 1), Triple(RUG_SIDE_SPACE, px + 2, pz + 1), Triple(RUG_MIDDLE_SPACE, px + 2, pz + 2))
                client.await("the spaces back") { if (spaces.all { (id, x, z) -> client.shown(id, x, z) }) Unit else null }
                assertEquals(emptyMap(), room.built)

                // One steel nail and one bronze make the crude chair's two, the steel taken first.
                client.command("item $PLANK 2")
                client.command("item $STEEL_NAILS 1")
                client.command("item $BRONZE_NAILS 1")
                client.await("the materials") { if (client.count(BRONZE_NAILS) == 1 && client.count(PLANK) == 2) Unit else null }
                client.locOp(CHAIR_SPACE, px + 2, pz + 4, House.BUILD)
                client.await("the creation menu again") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                client.await("the chair") { if (client.shown(CRUDE_CHAIR, px + 2, pz + 4)) Unit else null }
                assertEquals(listOf(0, 0, 0), listOf(client.count(PLANK), client.count(STEEL_NAILS), client.count(BRONZE_NAILS)))
            }
        }
    }

    @Test
    fun `a bookcase builds with its scroll rack, a fireplace on its own, and curtains on every window at once`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("finn").use { client ->
                client.ready()
                client.buyHouse()
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val px = instance.baseX + (House.STARTER_X shl 3)
                val pz = instance.baseZ + ((House.STARTER_Z + 1) shl 3)
                val room = server.world.playerNamed("finn")!!.house!!.room(House.STARTER_X, House.STARTER_Z + 1)!!
                client.command("setlevel construction 4")
                for (obj in listOf(HAMMER, SAW)) client.command("item $obj 1")
                client.command("item $PLANK 7")
                client.command("item $STEEL_NAILS 7")
                client.command("item $BOLT_OF_CLOTH 3")
                client.command("item $SOFT_CLAY 3")
                client.await("the materials") { if (client.count(SOFT_CLAY) == 3 && client.count(BOLT_OF_CLOTH) == 3) Unit else null }
                var xp = client.await("level 4") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.level == 4 } }.xp

                fun build(space: Int, x: Int, z: Int, gain: Int, vararg shown: Triple<Int, Int, Int>) {
                    client.locOp(space, x, z, House.BUILD)
                    client.await("the creation menu") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                    client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                    client.await("$space built") { if (shown.all { (id, sx, sz) -> client.shown(id, sx, sz) }) Unit else null }
                    xp += gain
                    client.await("the XP for $space") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == xp } }
                }

                // The bookcase and, on the space beside it, its scroll rack.
                build(BOOKCASE_SPACE, px + 6, pz + 2, 115, Triple(WOODEN_BOOKCASE, px + 6, pz + 2), Triple(WOODEN_SCROLLS, px + 6, pz + 3))
                assertEquals(mapOf(BOOKCASE_SPACE to WOODEN_BOOKCASE, SCROLL_SPACE to WOODEN_SCROLLS), room.built)
                build(FIREPLACE_SPACE, px + 4, pz + 6, 30, Triple(CLAY_FIREPLACE, px + 4, pz + 6))
                // One build hangs curtains on both windows.
                build(CURTAIN_SPACE, px + 2, pz + 7, 132, Triple(TORN_CURTAINS, px + 2, pz + 7), Triple(TORN_CURTAINS, px + 5, pz + 7))
                assertEquals(TORN_CURTAINS, room.built[CURTAIN_SPACE])
                assertEquals(listOf(0, 0, 0, 0), listOf(client.count(PLANK), client.count(STEEL_NAILS), client.count(BOLT_OF_CLOTH), client.count(SOFT_CLAY)))

                // Remove on the scroll rack takes the bookcase down too; Remove on the far window takes both curtains.
                client.locOp(WOODEN_SCROLLS, px + 6, pz + 3, Furniture.REMOVE)
                client.menu("construction.remove_furniture_yes", "construction.remove_furniture_no")
                client.chooseOption(1)
                client.await("the bookcase spaces back") { if (client.shown(BOOKCASE_SPACE, px + 6, pz + 2) && client.shown(SCROLL_SPACE, px + 6, pz + 3)) Unit else null }
                client.locOp(TORN_CURTAINS, px + 5, pz + 7, Furniture.REMOVE)
                client.menu("construction.remove_furniture_yes", "construction.remove_furniture_no")
                client.chooseOption(1)
                client.await("the curtain spaces back") { if (client.shown(CURTAIN_SPACE, px + 2, pz + 7) && client.shown(CURTAIN_SPACE, px + 5, pz + 7)) Unit else null }
                assertEquals(mapOf(FIREPLACE_SPACE to CLAY_FIREPLACE), room.built)
            }
        }
    }

    @Test
    fun `a wooden larder is built in the kitchen, and Search gives its tea leaves and milk without end`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("finn").use { client ->
                client.ready()
                client.buyHouse()
                val player = server.world.playerNamed("finn")!!
                // A kitchen east of the parlour, as building mode would add it (the rooms test builds one that way).
                player.house!!.rooms += HouseRoom(RoomType.KITCHEN, House.STARTER_X + 1, House.STARTER_Z + 1)
                client.command("setlevel construction 9")
                for (obj in listOf(HAMMER, SAW)) client.command("item $obj 1")
                client.command("item $PLANK 8")
                client.command("item $STEEL_NAILS 8")
                client.await("the materials") { if (client.count(STEEL_NAILS) == 8 && client.count(PLANK) == 8) Unit else null }
                val xp = client.await("level 9") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.level == 9 } }.xp
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val lx = instance.baseX + ((House.STARTER_X + 1) shl 3) + 1
                val lz = instance.baseZ + ((House.STARTER_Z + 1) shl 3) + 6

                client.locOp(LARDER_SPACE, lx, lz, House.BUILD)
                client.await("the creation menu") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                client.await("the larder") { if (client.shown(WOODEN_LARDER, lx, lz)) Unit else null }
                client.await("the XP") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == xp + 228 } }

                // Search: the Make-X box of its supplies; two tea leaves, then a bucket of milk.
                client.locOp(WOODEN_LARDER, lx, lz, Kitchen.SEARCH)
                client.await("the supplies box") { if (Chatbox.SKILLMULTI in client.openInterfaces()) Unit else null }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 2)
                client.await("the tea leaves") { if (client.count(TEA_LEAVES) == 2) Unit else null }
                client.locOp(WOODEN_LARDER, lx, lz, Kitchen.SEARCH)
                client.await("the supplies box again") { if (Chatbox.SKILLMULTI in client.openInterfaces()) Unit else null }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 1, 1)
                client.await("the milk") { if (client.count(MILK) == 1) Unit else null }
                assertEquals(2, client.count(TEA_LEAVES))
            }
        }
    }

    @Test
    fun `a firepit cooks, a pump fills a bucket, shelves give a kettle, and the house's own supplies stay in the house`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gwen").use { client ->
                client.ready()
                client.buyHouse()
                val player = server.world.playerNamed("gwen")!!
                player.house!!.rooms += HouseRoom(RoomType.KITCHEN, House.STARTER_X + 1, House.STARTER_Z + 1)
                client.command("setlevel construction 9")
                for (obj in listOf(HAMMER, SAW)) client.command("item $obj 1")
                client.command("item $STEEL_BAR 6")
                client.command("item $SOFT_CLAY 2")
                client.command("item $PLANK 3")
                client.command("item $STEEL_NAILS 3")
                client.await("the materials") { if (client.count(STEEL_NAILS) == 3 && client.count(STEEL_BAR) == 6) Unit else null }
                val xp = client.await("level 9") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.level == 9 } }.xp
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val kx = instance.baseX + ((House.STARTER_X + 1) shl 3)
                val kz = instance.baseZ + ((House.STARTER_Z + 1) shl 3)

                fun build(space: Int, x: Int, z: Int, built: Int) {
                    client.locOp(space, x, z, House.BUILD)
                    client.await("the creation menu on $space") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                    client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                    client.await("$built on $space") { if (client.shown(built, x, z)) Unit else null }
                }
                build(STOVE_SPACE, kx + 4, kz + 4, FIREPIT)
                build(SHELF_SPACE, kx + 6, kz + 6, WOODEN_SHELVES)
                assertTrue(client.shown(WOODEN_CROCKERY, kx + 6, kz + 5), "the crockery goes up with the shelves")
                build(SINK_SPACE, kx + 6, kz + 2, PUMP)
                client.await("the XP") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == xp + 40 + 87 + 100 } }

                // The firepit cooks as a fire: one raw shrimp, cooked or burnt.
                val raw = Cooking.SHRIMPS.raw
                client.command("item $raw 1")
                client.await("the raw shrimp") { if (client.count(raw) == 1) Unit else null }
                client.locTarget(FIREPIT, kx + 4, kz + 4, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(raw), raw)
                val box = client.await("the Make-X box or the shrimp cooked") {
                    when {
                        Chatbox.SKILLMULTI in client.openInterfaces() -> true
                        client.count(raw) == 0 -> false
                        else -> null
                    }
                }
                if (box) client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.await("the shrimp off the firepit") {
                    if (client.count(raw) == 0 && client.count(Cooking.SHRIMPS.cooked) + client.count(Cooking.SHRIMPS.burnt) == 1) Unit else null
                }

                // The pump fills a bucket.
                client.command("item $BUCKET 1")
                client.await("the bucket") { if (client.count(BUCKET) == 1) Unit else null }
                client.locTarget(PUMP, kx + 6, kz + 2, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(BUCKET), BUCKET)
                client.await("the bucket of water") { if (client.count(BUCKET_OF_WATER) == 1) Unit else null }

                // The shelves' Search: their crockery in the Make-X box, the kettle first.
                client.locOp(WOODEN_SHELVES, kx + 6, kz + 6, Kitchen.SEARCH)
                client.await("the supplies box") { if (Chatbox.SKILLMULTI in client.openInterfaces()) Unit else null }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.await("the kettle") { if (client.count(KETTLE) == 1) Unit else null }

                // Leaving takes the kettle and tea leaves; milk and the bucket of water go along.
                client.command("item $TEA_LEAVES 2")
                client.command("item $MILK 1")
                client.await("the tea leaves and milk") { if (client.count(TEA_LEAVES) == 2 && client.count(MILK) == 1) Unit else null }
                // A save from inside (a shutdown, an autosave) leaves them behind too, and takes nothing from the player.
                val saved = server.world.snapshot(player).inventory.values.map { it.id }
                assertTrue(KETTLE !in saved && TEA_LEAVES !in saved && MILK in saved, "saved: $saved")
                assertEquals(listOf(1, 2), listOf(client.count(KETTLE), client.count(TEA_LEAVES)))
                client.tele(OUTSIDE.x, OUTSIDE.z)
                client.await("the house's supplies gone") { if (client.count(KETTLE) == 0 && client.count(TEA_LEAVES) == 0) Unit else null }
                assertEquals(listOf(1, 1), listOf(client.count(MILK), client.count(BUCKET_OF_WATER)))
            }
        }
    }

    @Test
    fun `a dining table builds and is saved, a crested decoration is refused for its crest, and the Greenman carving builds`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hana").use { client ->
                client.ready()
                client.buyHouse()
                val player = server.world.playerNamed("hana")!!
                player.house!!.rooms += HouseRoom(RoomType.DINING_ROOM, House.STARTER_X + 1, House.STARTER_Z + 1)
                client.command("setlevel construction 16")
                for (obj in listOf(HAMMER, SAW)) client.command("item $obj 1")
                client.command("item $PLANK 4")
                client.command("item $STEEL_NAILS 4")
                client.command("item $OAK_PLANK 2")
                client.command("item $GREENMAN_ITEM 1")
                client.await("the materials") { if (client.count(GREENMAN_ITEM) == 1 && client.count(OAK_PLANK) == 2 && client.count(STEEL_NAILS) == 4) Unit else null }
                val xp = client.await("level 16") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.level == 16 } }.xp
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val dx = instance.baseX + ((House.STARTER_X + 1) shl 3)
                val dz = instance.baseZ + ((House.STARTER_Z + 1) shl 3)
                fun menu(space: Int, x: Int, z: Int, slot: Int) {
                    client.locOp(space, x, z, House.BUILD)
                    client.await("the creation menu on $space") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                    client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, slot)
                }

                menu(TABLE_SPACE, dx + 3, dz + 3, 1)
                client.await("the table") { if (client.shown(WOOD_TABLE, dx + 3, dz + 3)) Unit else null }
                client.await("the table's XP") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == xp + 115 } }

                // The oak decoration needs a family crest: refused, its oak planks kept.
                menu(DECORATION_SPACE, dx + 1, dz + 6, 1)
                client.await("the crest refusal") { client.messages().firstOrNull { it.text == "[construction.furniture_crest]" } }
                assertEquals(2, client.count(OAK_PLANK))
                assertTrue(!client.shown(GREENMAN, dx + 1, dz + 6))

                // The Greenman carving goes up on the same space, for no XP.
                menu(DECORATION_SPACE, dx + 1, dz + 6, 2)
                client.await("the carving") { if (client.shown(GREENMAN, dx + 1, dz + 6)) Unit else null }
                client.await("the carving used") { if (client.count(GREENMAN_ITEM) == 0) Unit else null }
                assertEquals(xp + 115, client.stat(Skill.CONSTRUCTION.id)!!.xp)

                // Both are saved with the dining room and read back.
                val read = assertNotNull(PlayerSave.fromJson(server.world.snapshot(player).toJson("hana")).house)
                val room = read.rooms.single { it.type == RoomType.DINING_ROOM }
                assertEquals(mapOf(TABLE_SPACE to WOOD_TABLE, DECORATION_SPACE to GREENMAN), room.built)
            }
        }
    }

    @Test
    fun `a tool store is built, Searched and upgraded over the one before it, and a workbench builds with its stool`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ivan").use { client ->
                client.ready()
                client.buyHouse()
                val player = server.world.playerNamed("ivan")!!
                player.house!!.rooms += HouseRoom(RoomType.WORKSHOP, House.STARTER_X + 1, House.STARTER_Z + 1)
                client.command("setlevel construction 25")
                for (obj in listOf(HAMMER, SAW)) client.command("item $obj 1")
                client.command("item $OAK_PLANK 4")
                client.command("item $PLANK 5")
                client.command("item $STEEL_NAILS 5")
                client.await("the materials") { if (client.count(OAK_PLANK) == 4 && client.count(STEEL_NAILS) == 5) Unit else null }
                val xp = client.await("level 25") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.level == 25 } }.xp
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val wx = instance.baseX + ((House.STARTER_X + 1) shl 3)
                val wz = instance.baseZ + ((House.STARTER_Z + 1) shl 3)
                fun menu(id: Int, x: Int, z: Int, op: Int, slot: Int, offered: List<Int>) {
                    val before = client.clientScripts().size
                    client.locOp(id, x, z, op)
                    client.await("the creation menu on $id") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                    val shown = client.await("its slots") { client.clientScripts().drop(before).filter { it.id == Furniture.ENTRY_SCRIPT }.map { it.args[1] }.takeIf { it.size >= offered.size } }
                    assertEquals(offered, shown)
                    client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, slot)
                }

                // An empty tool space offers only the first store: the others upgrade it. Built from the second space, it racks
                // the first and leaves the second bare (Codex review on #968).
                menu(TOOL_SPACE_B, wx + 1, wz + 3, House.BUILD, 1, listOf(TOOL_STORE_1_ROW))
                client.await("the first rack") { if (client.shown(TOOLS_1, wx + 1, wz + 2)) Unit else null }
                client.await("its XP") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == xp + 120 } }
                assertTrue(!client.shown(TOOLS_2, wx + 1, wz + 3))

                // Search: the Make-X box of its tools; a chisel.
                client.locOp(TOOLS_1, wx + 1, wz + 2, Kitchen.SEARCH)
                client.await("the tools box") { if (Chatbox.SKILLMULTI in client.openInterfaces()) Unit else null }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 2, 1)
                client.await("the chisel") { if (client.count(CHISEL) == 1) Unit else null }

                // Upgrade on the rack offers the second store, which racks the second space too.
                menu(TOOLS_1, wx + 1, wz + 2, Furniture.UPGRADE, 1, listOf(TOOL_STORE_1_ROW + 1))
                client.await("the second rack") { if (client.shown(TOOLS_2, wx + 1, wz + 3)) Unit else null }
                client.await("its XP") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == xp + 240 } }
                assertEquals(0, client.count(OAK_PLANK))
                client.locOp(TOOLS_2, wx + 1, wz + 3, Kitchen.SEARCH)
                client.await("the second box") { if (Chatbox.SKILLMULTI in client.openInterfaces()) Unit else null }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + 1, 1)
                client.await("the knife") { if (client.count(KNIFE) == 1) Unit else null }

                // The bare third space offers the third store, over the second.
                menu(TOOL_SPACE_C, wx + 1, wz + 4, House.BUILD, 1, listOf(TOOL_STORE_1_ROW + 2))
                client.await("the refusal for the level") { client.messages().firstOrNull { it.text == "[construction.furniture_level level=35]" } }

                // The workbench and its stool go up together.
                menu(WORKBENCH_SPACE, wx + 3, wz + 3, House.BUILD, 1, listOf(WOODEN_WORKBENCH_ROW))
                client.await("the workbench") { if (client.shown(WOODEN_WORKBENCH, wx + 3, wz + 3)) Unit else null }
                client.await("the stool") { if (client.shown(STOOL, wx + 3, wz + 2)) Unit else null }

                val read = assertNotNull(PlayerSave.fromJson(server.world.snapshot(player).toJson("ivan")).house)
                val room = read.rooms.single { it.type == RoomType.WORKSHOP }
                assertEquals(mapOf(TOOL_SPACE_A to TOOLS_1, TOOL_SPACE_B to TOOLS_2, WORKBENCH_SPACE to WOODEN_WORKBENCH, WORKBENCH_STOOL_SPACE to STOOL), room.built)
            }
        }
    }

    @Test
    fun `a tree or plant needs a watering can but no hammer or saw, gives Farming XP too, and the only exit portal stays`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("jade").use { client ->
                client.ready()
                client.buyHouse()
                val player = server.world.playerNamed("jade")!!
                client.command("setlevel construction 5")
                client.command("item $BAGGED_DEAD_TREE 1")
                client.command("item $BAGGED_PLANT_1 1")
                client.await("the bags") { if (client.count(BAGGED_DEAD_TREE) == 1 && client.count(BAGGED_PLANT_1) == 1) Unit else null }
                val xp = client.await("level 5") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.level == 5 } }.xp
                val farming = client.await("Farming") { client.stat(Skill.FARMING.id) }.xp
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val gx = instance.baseX + (House.STARTER_X shl 3)
                val gz = instance.baseZ + (House.STARTER_Z shl 3)
                fun menu(space: Int, x: Int, z: Int) {
                    client.locOp(space, x, z, House.BUILD)
                    client.await("the creation menu on $space") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                    client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                }

                // A tree needs a watering can: refused, its bag kept.
                menu(TREE_SPACE, gx + 6, gz + 6)
                client.await("the tree refusal") { client.messages().firstOrNull { it.text == "[construction.furniture_watering_can]" } }
                assertEquals(1, client.count(BAGGED_DEAD_TREE))
                client.command("item $WATERING_CAN_8 1")
                client.await("the can") { if (client.count(WATERING_CAN_8) == 1) Unit else null }

                // No hammer or saw: with the can, a tree goes in all the same, for as much Farming XP as Construction.
                menu(TREE_SPACE, gx + 6, gz + 6)
                client.await("the tree") { if (client.shown(DEAD_TREE, gx + 6, gz + 6)) Unit else null }
                client.await("its XP") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == xp + 31 } }
                client.await("its Farming XP") { client.stat(Skill.FARMING.id)?.takeIf { it.xp == farming + 31 } }

                // A plant goes in with the same can, its water not used.
                menu(SMALL_PLANT_SPACE, gx + 1, gz + 6)
                client.await("the plant") { if (client.shown(PLANT, gx + 1, gz + 6)) Unit else null }
                assertEquals(1, client.count(WATERING_CAN_8))

                // The house's only exit portal can't be removed.
                val portal = assertNotNull(server.world.locs.at(gx + 3, gz + 3, House.GROUND).firstOrNull { it.id == House.EXIT_PORTAL })
                client.locOp(House.EXIT_PORTAL, portal.x, portal.z, Furniture.REMOVE)
                client.await("the portal refusal") { client.messages().firstOrNull { it.text == "[construction.remove_only_portal]" } }
                assertEquals(House.EXIT_PORTAL, player.house!!.rooms.first { it.type == RoomType.GARDEN }.built[House.CENTREPIECE_SPACE])
            }
        }
    }

    @Test
    fun `a staircase goes up from a hall, builds a hall at its top, and the upper floor keeps to the Wiki's rules`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("kira").use { client ->
                client.ready()
                client.buyHouse()
                val player = server.world.playerNamed("kira")!!
                val hx = House.STARTER_X + 1
                val hz = House.STARTER_Z
                player.house!!.rooms += HouseRoom(RoomType.SKILL_HALL, hx, hz)
                client.command("setlevel construction 40")
                for (obj in listOf(HAMMER, SAW)) client.command("item $obj 1")
                client.command("item $OAK_PLANK 10")
                client.command("item $STEEL_BAR 4")
                client.command("item ${EstateAgent.COINS} 15000")
                client.await("the materials") { if (client.count(OAK_PLANK) == 10 && client.count(STEEL_BAR) == 4 && client.count(EstateAgent.COINS) == 15000) Unit else null }
                val xp = client.await("level 40") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.level == 40 } }.xp
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val sx = instance.baseX + (hx shl 3) + 3
                val sz = instance.baseZ + (hz shl 3) + 3
                fun build() {
                    client.locOp(STAIR_SPACE, sx, sz, House.BUILD)
                    client.await("the creation menu") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                    client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                    client.menu("construction.stairs_up", "construction.stairs_down")
                }

                // Down waits for the dungeon; Up builds the staircase's foot.
                build()
                client.chooseOption(2)
                client.await("the refusal for Down") { client.messages().firstOrNull { it.text == "[construction.stairs_down_later]" } }
                assertEquals(10, client.count(OAK_PLANK))
                build()
                client.chooseOption(1)
                client.await("the staircase") { if (client.shown(OAK_STAIRS, sx, sz)) Unit else null }
                client.await("its XP") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == xp + 680 } }
                assertEquals(5, client.count(OAK_PLANK))

                // Climb-up with nothing above: the top room's prompt; a skill hall goes up with the staircase's top, for the
                // hall's coins and the staircase's materials again, and the player with it.
                client.locOp(OAK_STAIRS, sx, sz, 1)
                client.menu("construction.stairs_top_skill_hall", "construction.stairs_top_quest_hall", "construction.stairs_top_cancel")
                client.chooseOption(1)
                client.await("upstairs") { client.position?.takeIf { it.level == House.UPPER } }
                val top = assertNotNull(player.house!!.room(hx, hz, House.UPPER))
                assertEquals(RoomType.SKILL_HALL_STAIRS, top.type)
                assertEquals(mapOf(STAIR_TOP_SPACE to OAK_STAIRS_TOP), top.built)
                client.await("the top's XP") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == xp + 1360 } }
                assertEquals(0, client.count(OAK_PLANK))
                assertEquals(0, client.count(EstateAgent.COINS))
                assertEquals(OAK_STAIRS_TOP, server.world.locs.at(sx, sz, House.UPPER).firstOrNull { it.id == OAK_STAIRS_TOP }?.id)

                // A staircase to the other floor stays; a room upstairs needs one under it.
                client.locOp(OAK_STAIRS_TOP, sx, sz, Furniture.REMOVE)
                client.await("the top's refusal") { client.messages().firstOrNull { it.text == "[construction.stairs_connected]" } }
                client.locOp(House.DOOR_HOTSPOT_LEFT, instance.baseX + (hx shl 3) + 7, instance.baseZ + (hz shl 3) + 3, House.BUILD)
                client.await("the refusal for no support") { client.messages().firstOrNull { it.text == "[construction.room_no_support]" } }

                // Down again; the hall under the room upstairs can't be removed, nor its staircase.
                client.locOp(OAK_STAIRS_TOP, sx, sz, 1)
                client.await("downstairs") { client.position?.takeIf { it.level == House.GROUND } }
                client.locOp(OAK_STAIRS, sx, sz, Stairs.REMOVE_ROOM)
                client.await("the hall's refusal") { client.messages().firstOrNull { it.text == "[construction.remove_supporting]" } }
                client.locOp(OAK_STAIRS, sx, sz, Furniture.REMOVE)
                client.await("the foot's refusal") { client.messages().count { it.text == "[construction.stairs_connected]" }.takeIf { it == 2 } }

                // Both floors are saved.
                val read = assertNotNull(PlayerSave.fromJson(server.world.snapshot(player).toJson("kira")).house)
                assertEquals(RoomType.SKILL_HALL_STAIRS, read.room(hx, hz, House.UPPER)?.type)
                assertEquals(mapOf(STAIR_SPACE to OAK_STAIRS), assertNotNull(read.room(hx, hz)).built.toMap())

                // Up, and Remove-room on the top: the room goes and the player with it to the floor below.
                client.locOp(OAK_STAIRS, sx, sz, 1)
                client.await("upstairs again") { client.position?.takeIf { it.level == House.UPPER } }

                // A parlour's ghost upstairs, over a room below, walked into and left: the next Build takes it away and
                // whoever stands in it goes to the floor below.
                player.house!!.rooms += HouseRoom(RoomType.PARLOUR, hx + 1, hz)
                client.command("item ${EstateAgent.COINS} ${RoomType.PARLOUR.cost}")
                client.await("the parlour's coins") { if (client.count(EstateAgent.COINS) == RoomType.PARLOUR.cost) Unit else null }
                val doorX = instance.baseX + (hx shl 3) + 7
                val doorZ = instance.baseZ + (hz shl 3) + 3
                client.locOp(House.DOOR_HOTSPOT_LEFT, doorX, doorZ, House.BUILD)
                client.await("the add-room menu upstairs") { if (House.ADD_ROOM in client.openInterfaces()) Unit else null }
                client.resumePause(House.ADD_ROOM, House.ADD_ROOM_LIST, RoomType.PARLOUR.id)
                client.menu("construction.rotate_clockwise", "construction.rotate_anticlockwise", "construction.build_room", "construction.cancel_room")
                assertEquals(Houses.Cell(hx + 1, hz, House.UPPER), server.world.houses.houseAt(doorX, doorZ)?.ghost)
                client.command("tele ${doorX + 4} $doorZ ${House.UPPER}")
                client.await("in the ghost") { client.position?.takeIf { it.x == doorX + 4 && it.level == House.UPPER } }
                client.locOp(House.DOOR_HOTSPOT_LEFT, doorX, doorZ, House.BUILD)
                client.await("down from the ghost") { client.position?.takeIf { it.level == House.GROUND } }
                assertNull(player.house!!.room(hx + 1, hz, House.UPPER))
                client.locOp(OAK_STAIRS, sx, sz, 1)
                client.await("upstairs a third time") { client.position?.takeIf { it.level == House.UPPER } }
                client.locOp(OAK_STAIRS_TOP, sx, sz, Stairs.REMOVE_ROOM)
                client.menu("construction.remove_yes", "construction.remove_no")
                client.chooseOption(1)
                client.await("back down") { client.position?.takeIf { it.level == House.GROUND } }
                assertNull(player.house!!.room(hx, hz, House.UPPER))
            }
        }
    }

    @Test
    fun `a boxing ring is built over its whole space, its rope climbed both ways, and the rack's gloves stay in the house`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("lena").use { client ->
                client.ready()
                client.buyHouse()
                val player = server.world.playerNamed("lena")!!
                player.house!!.rooms += HouseRoom(RoomType.COMBAT_ROOM, House.STARTER_X + 1, House.STARTER_Z + 1)
                client.command("setlevel construction 34")
                for (obj in listOf(HAMMER, SAW)) client.command("item $obj 1")
                client.command("item $OAK_PLANK 8")
                client.command("item $BOLT_OF_CLOTH 4")
                client.await("the materials") { if (client.count(OAK_PLANK) == 8 && client.count(BOLT_OF_CLOTH) == 4) Unit else null }
                val xp = client.await("level 34") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.level == 34 } }.xp
                val at = client.goIn(House.BUILD_MODE)
                val instance = assertNotNull(server.world.instances.at(at.x, at.z))
                val cx = instance.baseX + ((House.STARTER_X + 1) shl 3)
                val cz = instance.baseZ + ((House.STARTER_Z + 1) shl 3)
                fun build(id: Int, x: Int, z: Int) {
                    client.locOp(id, x, z, House.BUILD)
                    client.await("the creation menu on $id") { if (Furniture.MENU in client.openInterfaces()) Unit else null }
                    client.resumePause(Furniture.MENU, Furniture.MENU_CONTENTS, 1)
                }

                // Built from the rope's space, the ring puts its rope there and its mat on the floor inside.
                build(RING_WALL_SPACE, cx + 2, cz + 3)
                client.await("the rope") { if (client.shown(BOXING_ROPE, cx + 2, cz + 3)) Unit else null }
                client.await("the mat") { if (client.shown(BOXING_MAT, cx + 3, cz + 3)) Unit else null }
                client.await("its XP") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == xp + 420 } }
                build(STORAGE_SPACE, cx + 5, cz + 6)
                client.await("the glove rack") { if (client.shown(GLOVE_RACK, cx + 5, cz + 6)) Unit else null }
                client.await("its XP") { client.stat(Skill.CONSTRUCTION.id)?.takeIf { it.xp == xp + 540 } }
                assertEquals(listOf(0, 0), listOf(client.count(OAK_PLANK), client.count(BOLT_OF_CLOTH)))
                val room = player.house!!.room(House.STARTER_X + 1, House.STARTER_Z + 1)!!
                // Every tile kind of the ring space takes its part, the pedestals' and the beam's own walls none.
                assertEquals(listOf(BOXING_ROPE, BOXING_MAT, GLOVE_RACK), listOf(RING_WALL_SPACE, RING_FLOOR_SPACE, STORAGE_SPACE).map { room.built[it] })
                assertEquals(BOXING_BLUE_CORNER, room.built[RING_BLUE_CORNER_SPACE])
                assertTrue(PEDESTAL_WALL_SPACE !in room.built)

                // The rack's Search: the red gloves, worn.
                client.locOp(GLOVE_RACK, cx + 5, cz + 6, Kitchen.SEARCH)
                client.await("the rack's box") { if (Chatbox.SKILLMULTI in client.openInterfaces()) Unit else null }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                val slot = client.await("the red gloves") { client.inventory(Inventory.INV).indexOfFirst { it?.id == CombatRoom.RED_GLOVES }.takeIf { it >= 0 } }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, WEAR_OP, slot, CombatRoom.RED_GLOVES)
                client.await("the gloves worn") { client.inventory(Inventory.WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == CombatRoom.RED_GLOVES } }

                // Over the rope into the ring and back out, from whichever side.
                client.command("tele ${cx + 2} ${cz + 3} ${House.GROUND}")
                client.await("beside the rope") { client.position?.takeIf { it == Coord(cx + 2, cz + 3, House.GROUND) } }
                client.locOp(BOXING_ROPE, cx + 2, cz + 3, CombatRoom.CROSS)
                client.await("in the ring") { client.position?.takeIf { it == Coord(cx + 3, cz + 3, House.GROUND) } }
                client.locOp(BOXING_ROPE, cx + 2, cz + 3, CombatRoom.CROSS)
                client.await("out of the ring") { client.position?.takeIf { it == Coord(cx + 2, cz + 3, House.GROUND) } }

                // A save from inside leaves the worn gloves behind; leaving takes them.
                assertTrue(server.world.snapshot(player).equipment.values.none { it.id == CombatRoom.RED_GLOVES })
                client.tele(OUTSIDE.x, OUTSIDE.z)
                client.await("the gloves gone") { if (client.inventory(Inventory.WORN).none { it?.id == CombatRoom.RED_GLOVES }) Unit else null }
            }
        }
    }

    private companion object {
        const val STAIR_SPACE = 15380
        const val STAIR_TOP_SPACE = 15381
        const val OAK_STAIRCASE_ROW = 5644
        const val OAK_STAIRS = 13497
        const val OAK_STAIRS_TOP = 13498
        const val TREE_SPACE = 15363
        const val SMALL_PLANT_SPACE = 15366
        const val DEAD_TREE_ROW = 5880
        const val PLANT_ROW = 5893
        const val DEAD_TREE = 4538
        const val PLANT = 5134
        const val BAGGED_DEAD_TREE = 8417
        const val BAGGED_PLANT_1 = 8431
        const val WATERING_CAN_8 = 5340
        const val WORKBENCH_SPACE = 15439
        const val WORKBENCH_STOOL_SPACE = 15440
        const val TOOL_SPACE_A = 15443
        const val TOOL_SPACE_B = 15444
        const val TOOL_SPACE_C = 15445
        const val WOODEN_WORKBENCH_ROW = 5749
        const val TOOL_STORE_1_ROW = 5758
        const val WOODEN_WORKBENCH = 6791
        const val STOOL = 6806
        const val TOOLS_1 = 6786
        const val TOOLS_2 = 6787
        const val CHISEL = 1755
        const val KNIFE = 946
        const val CHAIR_SPACE = 4515
        const val CRUDE_CHAIR = 6752
        const val CRUDE_CHAIR_ROW = 5517
        const val WOODEN_CHAIR_ROW = 5518
        const val PLANK = 960
        const val BOLT_OF_CLOTH = 8790
        const val WOODEN_LARDER_ROW = 5562
        const val WOODEN_LARDER = 13565
        const val TEA_LEAVES = 7738
        const val MILK = 1927
        const val RUG_MIDDLE_SPACE = 4518
        const val RUG_SIDE_SPACE = 4519
        const val RUG_CORNER_SPACE = 4520
        const val BROWN_RUG_ROW = 5524
        const val BROWN_RUG_CORNER = 6759
        const val BROWN_RUG_SIDE = 6760
        const val BROWN_RUG_MIDDLE = 6761
        const val BOOKCASE_SPACE = 4521
        const val SCROLL_SPACE = 4522
        const val FIREPLACE_SPACE = 4523
        const val CURTAIN_SPACE = 4524
        const val WOODEN_BOOKCASE_ROW = 5529
        const val CLAY_FIREPLACE_ROW = 5532
        const val TORN_CURTAINS_ROW = 5535
        const val WOODEN_BOOKCASE = 6768
        const val WOODEN_SCROLLS = 6771
        const val CLAY_FIREPLACE = 6780
        const val TORN_CURTAINS = 6774
        const val SOFT_CLAY = 1761
        const val STEEL_NAILS = 1539
        const val BRONZE_NAILS = 4819
        const val HAMMER = 2347
        const val SAW = 8794

        const val TABLE_SPACE = 15298
        const val DECORATION_SPACE = 15303
        const val WOOD_TABLE_ROW = 5573
        const val OAK_DECORATION_ROW = 5587
        const val GREENMAN_ROW = 6027
        const val WOOD_TABLE = 13293
        const val GREENMAN = 57012
        const val OAK_PLANK = 8778
        const val GREENMAN_ITEM = 31024
        const val STOVE_SPACE = 15398
        const val SHELF_SPACE = 15399
        const val CROCKERY_SPACE = 15400
        const val SINK_SPACE = 15404
        const val FIREPIT_ROW = 5539
        const val WOODEN_SHELVES_ROW = 5546
        const val PUMP_ROW = 5565
        const val FIREPIT = 13528
        const val WOODEN_SHELVES = 13545
        const val WOODEN_CROCKERY = 13552
        const val PUMP = 13559
        const val WEAR_OP = 3
        const val RING_WALL_SPACE = 15277
        const val RING_FLOOR_SPACE = 15292
        const val RING_BLUE_CORNER_SPACE = 15278
        const val PEDESTAL_WALL_SPACE = 15283
        const val BOXING_BLUE_CORNER = 13132
        const val STORAGE_SPACE = 15296
        const val BOXING_RING_ROW = 5633
        const val GLOVE_RACK_ROW = 5638
        const val BOXING_ROPE = 13129
        const val BOXING_MAT = 13127
        const val GLOVE_RACK = 13381
        const val STEEL_BAR = 2353
        const val KETTLE = 7688
        const val CLAY_TEAPOT = 7702
        const val CLAY_CUP = 7728
        const val BUCKET = 1925
        const val BUCKET_OF_WATER = 1929

        /** The kitchen's `poh_larder` space; its size here is the test's own. */
        const val LARDER_SPACE = 15403
        const val PORTAL_X = 2951
        const val PORTAL_Z = 3222
        val OUTSIDE = House.RIMMINGTON_OUTSIDE
        const val AGENT_X = 3240
        const val AGENT_Z = 3476
    }
}
