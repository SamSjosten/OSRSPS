package osrsps.game.skill.construction

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.FrameSlot
import osrsps.game.combat.DeathPlace
import osrsps.game.content.Ned
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.Lines
import osrsps.game.instance.Instance
import osrsps.game.instance.InstancePart
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

private val logger = KotlinLogging.logger {}

/** Construction's words that aren't an NPC's, in the dialogue file. */
object ConstructionLines : Lines("construction", "Portal (Player-owned house)") {
    val NO_HOUSE = line("no_house", "Using a house portal without owning a house (no source yet)")
    val ENTER_HOUSE = line("enter_house", "Enter on the portal: the menu's first option, your own house")
    val ENTER_BUILDING = line("enter_building", "Enter on the portal: the menu's second option, your own house in building mode")
    val ENTER_FRIEND = line("enter_friend", "Enter on the portal: the menu's third option, another player's house")
    val ENTER_NOTHING = line("enter_nothing", "Enter on the portal: the menu's last option, taking no action")
    val NO_ROOM = line("no_room", "Entering a house when the server has no room left to build one (ours, not live's)")
    val ROTATE_CLOCKWISE = line("rotate_clockwise", "Adding a room, its ghost shown: the menu's option turning it clockwise (no source yet)")
    val ROTATE_ANTICLOCKWISE = line("rotate_anticlockwise", "Adding a room: the menu's option turning it anticlockwise (no source yet)")
    val BUILD_ROOM = line("build_room", "Adding a room: the menu's option building it as shown (no source yet)")
    val CANCEL_ROOM = line("cancel_room", "Adding a room: the menu's option building nothing (no source yet)")
    val REMOVE_TITLE = line("remove_title", "Build on the door to a room: the prompt asking whether to remove it (no source yet)")
    val REMOVE_YES = line("remove_yes", "The prompt's option removing the room (no source yet)")
    val REMOVE_NO = line("remove_no", "The prompt's option keeping the room (no source yet)")
    val ROOM_LEVEL = line("room_level", "Picking a room above the player's Construction level, {level} (no source yet)", "level")
    val ROOM_COINS = line("room_coins", "Picking a room without its {cost} coins in the backpack (no source yet)", "cost")
    val ROOM_FLOOR = line("room_floor", "Picking a room for a floor it can't go on: a dungeon room above ground, or a garden or other ground floor room upstairs (no source yet)")
    val ROOM_NO_SUPPORT = line("room_no_support", "Adding a room upstairs with no room below it (the Player-owned house page: there must be one; no source for the words)")
    val REMOVE_SUPPORTING = line("remove_supporting", "Removing a ground floor room with a room above it (the Player-owned house page: it can't be; no source for the words)")
    val STAIRS_TITLE = line("stairs_title", "Building a staircase in a ground floor hall: the prompt asking which way it goes (the Hall pages: \"Up\" or \"Down\"; no source for the words)")
    val STAIRS_UP = line("stairs_up", "The staircase prompt's option for a staircase going up (the Hall pages: \"Up\")")
    val STAIRS_DOWN = line("stairs_down", "The staircase prompt's option for a staircase going down (the Hall pages: \"Down\")")
    val STAIRS_DOWN_LATER = line("stairs_down_later", "Picking Down for a staircase before the dungeon is built here, step CON10 (ours)")
    val STAIRS_TOP_TITLE = line("stairs_top_title", "Climb-up on a staircase with nothing above it, in building mode: the prompt asking which room to build at the top (the Hall pages: an interface asks; no source for the words)")
    val STAIRS_TOP_SKILL_HALL = line("stairs_top_skill_hall", "The top room prompt's option building a skill hall (no source yet)")
    val STAIRS_TOP_QUEST_HALL = line("stairs_top_quest_hall", "The top room prompt's option building a quest hall (no source yet)")
    val STAIRS_TOP_CANCEL = line("stairs_top_cancel", "The top room prompt's option building nothing (no source yet)")
    val STAIRS_NOTHING_ABOVE = line("stairs_nothing_above", "Climb-up on a staircase with nothing above it, out of building mode (no source yet)")
    val STAIRS_BLOCKED = line("stairs_blocked", "Climb-up where the room above isn't a hall with the same staircase, facing the same way (no source yet)")
    val STAIRS_NO_MATCH = line("stairs_no_match", "Building a staircase upstairs that isn't the one in the hall below, facing the same way (the Hall pages: the room above needs the same staircase; no source for the words)")
    val STAIRS_CONNECTED = line("stairs_connected", "Remove on a staircase that leads to the other floor (ours: the room upstairs goes first; no source yet)")
    val ROOM_LIMIT = line("room_limit", "Adding a room with as many as the player's level allows, {max} (no source yet)", "max")
    val ROOM_TOO_FAR = line("room_too_far", "Adding a room that would make the house wider than the player's level allows, {size} by {size} (no source yet)", "size")
    val ROOM_OUTSIDE = line("room_outside", "Adding a room past the edge of the yard (no source yet)")
    val ROOM_NO_DOOR = line("room_no_door", "Adding a room that can't be turned to have a door facing this one (no source yet)")
    val REMOVE_PORTAL = line("remove_portal", "Removing the room the house's only exit portal is in (ours: no source yet)")
    val REMOVE_ONLY_PORTAL = line("remove_only_portal", "Remove on the house's only exit portal (the Exit portal page: it can't be removed; no source for the words)")
    val FURNITURE_WATERING_CAN = line("furniture_watering_can", "Planting a bagged tree or plant without a watering can with water in it (the Garden page: one is needed; no source for the words)")
    val FURNITURE_LEVEL = line("furniture_level", "Building furniture above the player's Construction level, {level} (no source yet)", "level")
    val FURNITURE_SKILL_LEVEL = line("furniture_skill_level", "Building furniture above another skill's level it needs, {skill} {level} (the barrels' Cooking; no source yet)", "skill", "level")
    val FURNITURE_CREST = line("furniture_crest", "Building a wall decoration without a family crest (the Dining room page: they need one from Sir Renitee; no source for the words)")
    val TAKE_SUPPLIES = line("take_supplies", "The Make-X box's title on searching a larder or kitchen shelves (no source yet)")
    val FURNITURE_TOOLS = line("furniture_tools", "Building furniture without a hammer and a saw (no source yet)")
    val FURNITURE_MATERIALS = line("furniture_materials", "Building furniture without its materials (no source yet)")
    val REMOVE_FURNITURE_TITLE = line("remove_furniture_title", "Remove on built furniture: the prompt asking whether to remove it (no source yet)")
    val REMOVE_FURNITURE_YES = line("remove_furniture_yes", "The prompt's option removing it (no source yet)")
    val REMOVE_FURNITURE_NO = line("remove_furniture_no", "The prompt's option keeping it (no source yet)")
}

/**
 * The house's numbers and ids, from the 242 cache (the loc and varbit types and the maps) unless a line says otherwise.
 */
object House {
    /** The house's grid of rooms: 9 by 9 cells, the widest yard there is (level 60, the Wiki's Player-owned house). */
    const val GRID = 9

    /** Floors: the dungeon, the ground floor and the upper floor, drawn on these levels of the instance. */
    const val FLOORS = 3
    const val DUNGEON = 0
    const val GROUND = 1
    const val UPPER = 2

    /** The most rooms a house can have: 38 at level 99 (the Wiki's Player-owned house). */
    const val MAX_ROOMS = 38

    /** The starter garden's cell (see [HouseState.starter]). */
    const val STARTER_X = 2
    const val STARTER_Z = 2

    /** `poh_rimmington_portal`, 5 by 2, at 2951,3222 turned once: Enter, Home, Build mode, Friend's house. */
    const val RIMMINGTON_PORTAL = 15478
    const val ENTER = 1
    const val HOME = 2
    const val BUILD_MODE = 3

    /** Where leaving a Rimmington house puts the player: east of the portal's middle. GUESSED, the map's collision only. */
    val RIMMINGTON_OUTSIDE = Tile(2953, 3224)

    /** `poh_exit_portal`, 2 by 2: Enter, Lock, Remove board advert, and Remove in building mode. */
    const val EXIT_PORTAL = 4525
    const val EXIT_ENTER = 1

    /** The garden's centrepiece hotspot (`poh_crude_garden_1`, "Centrepiece space"), 2 by 2, at 3,3 in the garden. */
    const val CENTREPIECE_SPACE = 15361

    /** Where entering puts the player: the tile south-west below the exit portal's centrepiece. GUESSED. */
    const val ARRIVE_DX = 3
    const val ARRIVE_DZ = 2

    /** Basic wood's door hotspots (`poh_hotspot_doorl_rimmington`, `_doorr_`), a pair in the middle of each open side. */
    const val DOOR_HOTSPOT_LEFT = 15313
    const val DOOR_HOTSPOT_RIGHT = 15314

    /** Basic wood's wall (`village_wall`) and doors (`village_door_l`, `_r`, and their `_open` types). */
    const val WALL = 13098
    const val DOOR_LEFT = 13100
    const val DOOR_RIGHT = 13101
    const val DOOR_LEFT_OPEN = 13102
    const val DOOR_RIGHT_OPEN = 13103

    /** A hotspot's option: Build (op 5) on every hotspot loc type. */
    const val BUILD_OP = "Build"
    const val BUILD = 5

    /** The add-room menu (`poh_add_room`): its list, whose buttons resume with the room type's id as the slot. */
    const val ADD_ROOM = 212
    const val ADD_ROOM_LIST = 4
    val ADD_ROOM_SLOTS = 0..29

    /** `poh_building_mode` and `poh_house_location`. */
    const val BUILDING_MODE_VARBIT = 2176
    const val LOCATION_VARBIT = 2187

    /** The yard's size, in cells, by Construction level (the Wiki's Player-owned house, "Yard space"). */
    fun yardSize(level: Int): Int = when {
        level >= 60 -> 9
        level >= 45 -> 8
        level >= 30 -> 7
        level >= 15 -> 6
        else -> 5
    }

    /** The most rooms a house can have at [level] (the Wiki's Player-owned house, "Maximum number of rooms and area"). */
    fun maxRooms(level: Int): Int = when {
        level >= 99 -> 38
        level >= 96 -> 37
        level >= 26 -> 25 + minOf((level - 26) / 6, 11)
        else -> 24
    }

    /** The widest a house can be, in rooms, at [level]: the same table's "Maximum house dimensions". */
    fun maxSize(level: Int): Int = yardSize(level) - 2

    /** The house's own locs that need handlers in its instance. */
    val INSTANCE_LOCS: Set<Int> = setOf(EXIT_PORTAL, DOOR_LEFT, DOOR_RIGHT, DOOR_LEFT_OPEN, DOOR_RIGHT_OPEN, DOOR_HOTSPOT_LEFT, DOOR_HOTSPOT_RIGHT)
}

/**
 * Construction steps CON1 and CON2: the house a player buys from an estate agent ([EstateAgent]), entered through its
 * portal in Rimmington and left through its exit portal, and the rooms added to it and removed in building mode.
 *
 * A house is an instance ([osrsps.game.instance.Instances]) of [House.GRID] by [House.GRID] zones, one zone a room,
 * each a copy of the cache's room template ([RoomType]) turned as the room is ([HouseRoom.rotation]). Live draws houses
 * this way: the cache has every room of every style as a zone of the template area, its build-mode ghosts (hotspot
 * locs, each with a Build option) in place. The ground floor is on level 1 of the instance, as live keeps level 0 for
 * the dungeon below it (inferred: the Wiki's Dungeon rooms are "underground", below the ground floor). The yard round
 * the rooms, [House.yardSize] cells square from the grid's south-west corner (growing "a row to the top ... and a column
 * to the right"), is the grass template.
 *
 * In building mode the house shows its hotspots, with what is built on them instead (the exit portal on the garden's
 * centrepiece space). Out of it every empty hotspot goes, and each door hotspot becomes a wall of the style where no
 * room lies beyond it, or a door where one does; in a room out of doors it just goes, as gardens have no walls.
 * GUESSED: the doors start shut and open and close as any double door; a door to a garden is a door; building mode's
 * door hotspots stay even where a room lies beyond.
 *
 * Build on a door hotspot in building mode (the Wiki's Player-owned house, "Building rooms"): with no room beyond, the
 * add-room menu (`poh_add_room`), then the picked room's ghost beyond the door, turned to each way that puts one of its
 * doors on this doorway, until the player builds it for its coins or cancels; with a room beyond, a prompt to remove
 * it. The Wiki gives the rules: the room's level (a boost counts, Construction's room table), its coins, at most
 * [House.maxRooms] rooms and a house at most [House.maxSize] rooms across, gardens on the ground floor and dungeon rooms
 * below it. GUESSED: the ghost's menu (rotate either way, build, cancel) and the prompt's words and layout; rooms only
 * on the yard, the house's width measured over every room; the room holding the exit portal can't be removed (the
 * house would have nowhere to put its visitors); a ghost left by walking off stays until the next Build or the next
 * entry, unsaved.
 *
 * Step CON5: the upper floor is level 2 of the instance, each room there the same template a floor up (the cache's
 * template area keeps a house style's rooms on one level). Only its rooms are drawn: an empty cell upstairs is open air.
 * A room goes upstairs through a doorway there only over a room below, and a ground floor room under one can't be
 * removed (the Wiki's Player-owned house); gardens and the other ground floor rooms stay downstairs, and a hall upstairs
 * is the hall at a staircase's top ([Stairs]). Dungeon rooms wait for CON10.
 *
 * Entering puts the player beside the exit portal ([House.ARRIVE_DX]); its Enter, a logout inside and the end of the
 * instance put them outside the portal they came in by ([House.RIMMINGTON_OUTSIDE]). A house is built afresh each time
 * its owner goes in, and ends once nobody is in it. GUESSED: no loading screen (live shows `poh_loading`, interface
 * 71, for a moment); Friend's house, Lock, guests and the house options come in later steps.
 */
class Houses(private val world: World) {
    /**
     * A house open now: whose it is, its instance, whether it is in building mode, and the cell of a room's ghost shown
     * there and not yet built, if any.
     */
    class Open(val owner: String, val instance: Instance, val buildingMode: Boolean) {
        var ghost: Cell? = null

        /** By tile and layer ([slot]), the hotspot the room's template put there, whatever is built over it now. */
        val hotspots = HashMap<Long, ZoneLoc>()
    }

    /** A cell of the house's grid on one of its floors ([House.GROUND] or [House.UPPER]). */
    data class Cell(val x: Int, val z: Int, val floor: Int)

    companion object {
        /** A tile, level and layer as one key, for [Open.hotspots]. */
        fun slot(x: Int, z: Int, level: Int, layer: Int): Long =
            (x.toLong() shl 36) or (z.toLong() shl 4) or (level.toLong() shl 2) or layer.toLong()
    }

    /** By owner, their house open now; by instance, the same houses, for looking one up by where a player is. */
    private val open = HashMap<String, Open>()
    private val byInstance = HashMap<Instance, Open>()

    /** Players who were in a house at the end of the last tick, so leaving by any way is noticed ([process]). */
    private val inside = HashSet<Player>()

    /** By room type, the sides of its unturned template with door hotspots (0 west, 1 north, 2 east, 3 south). */
    private val doorSides = HashMap<RoomType, Set<Int>>()

    /** The house [player] is in, if any. */
    fun houseAt(x: Int, z: Int): Open? {
        val instance = world.instances.at(x, z) ?: return null
        // An ended instance is never found by its tiles, so a house not yet pruned can't be returned for one.
        return byInstance[instance]
    }

    fun register() {
        world.handlers.onLoc(House.RIMMINGTON_PORTAL, House.ENTER) {
            arriveDelay()
            if (player.house == null) return@onLoc world.message(player, ConstructionLines.NO_HOUSE)
            var pick = 0
            world.dialogues.start(this) {
                pick = choice(ConstructionLines.ENTER_HOUSE, ConstructionLines.ENTER_BUILDING, ConstructionLines.ENTER_FRIEND, ConstructionLines.ENTER_NOTHING)
            }
            when (pick) {
                1 -> enter(this, buildingMode = false)
                2 -> enter(this, buildingMode = true)
                // Another player's house waits for guests (step CON11).
                3 -> world.message(player, GameLines.NOTHING_INTERESTING)
            }
        }
        world.handlers.onLoc(House.RIMMINGTON_PORTAL, House.HOME) {
            arriveDelay()
            enter(this, buildingMode = false)
        }
        world.handlers.onLoc(House.RIMMINGTON_PORTAL, House.BUILD_MODE) {
            arriveDelay()
            enter(this, buildingMode = true)
        }
        world.handlers.onLoc(House.EXIT_PORTAL, House.EXIT_ENTER) {
            arriveDelay()
            val house = houseAt(player.x, player.z) ?: return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            val outside = outside(house.instance)
            world.teleport(player, outside.x, outside.z, 0)
        }
        for (hotspot in listOf(House.DOOR_HOTSPOT_LEFT, House.DOOR_HOTSPOT_RIGHT)) {
            world.handlers.onLoc(hotspot, House.BUILD) { op ->
                arriveDelay()
                doorway(this, op.loc ?: return@onLoc)
            }
        }
        world.doors.registerDouble(House.DOOR_LEFT, House.DOOR_LEFT_OPEN, House.DOOR_RIGHT, House.DOOR_RIGHT_OPEN)
    }

    /** Takes [ScriptContext.player] into their own house, built for [buildingMode]; a message if they have none. */
    fun enter(context: ScriptContext, buildingMode: Boolean) {
        val player = context.player
        val state = player.house ?: return world.message(player, ConstructionLines.NO_HOUSE)
        val house = build(player, state, buildingMode) ?: return world.message(player, ConstructionLines.NO_ROOM)
        val arrive = arrival(house.instance, state) ?: run {
            // A save with no exit portal can't load ([HouseSaves.fromJson]), so this is only a safety net (R43B-1).
            world.endInstance(house.instance)
            return world.message(player, ConstructionLines.NO_ROOM)
        }
        world.teleport(player, arrive.x, arrive.z, House.GROUND)
        inside += player
    }

    /**
     * Each tick: whoever was in a house and isn't now has left it, by its exit portal, a teleport or anything else, and
     * leaving cures poison (the Wiki's Player-owned house: "exiting the POH in any way will remove this debuff").
     */
    fun process() {
        forgetEnded()
        settleDepartures()
    }

    /**
     * Whoever has left a house since the last look is settled: also at the tick's start, before the players' input, so a
     * house-only item can't be dropped outside on the first tick out and kept (exploit review R56B-1; as the Archive's
     * supplies, R26A-1).
     */
    fun settleDepartures() {
        if (inside.isEmpty()) return
        val left = inside.filter { houseAt(it.x, it.z) == null }
        for (player in left) {
            inside -= player
            world.poison.curePoison(player)
            setVarbit(player, House.BUILDING_MODE_VARBIT, 0)
            dropHouseOnly(player)
        }
    }

    /** [player] is logging out: in a house, that is leaving it too, so the save they come back to has no poison. */
    fun leaving(player: Player) {
        if (!inside.remove(player)) return
        // Off before the save, whichever way they leave (exploit review R43B-2).
        setVarbit(player, House.BUILDING_MODE_VARBIT, 0)
        if (houseAt(player.x, player.z) != null) world.poison.curePoison(player)
        dropHouseOnly(player)
    }

    /** What the kitchen gives that can't leave the house ([Kitchen.HOUSE_ONLY]) goes as its taker does (Codex review on #906). */
    private fun dropHouseOnly(player: Player) {
        for ((slot, obj) in player.inventory.contents()) if (obj.id in Kitchen.HOUSE_ONLY) player.inventory.remove(slot)
    }

    /**
     * Where a death in a house goes: a safe one (the Wiki's Player-owned house: whoever dies in a house "will be
     * teleported to outside the house but will retain all their items and have their Hitpoints and other stats
     * restored"; Teleport to House: they do NOT lose any items), outside the portal they came in by.
     */
    fun deathPlace(player: Player): DeathPlace? {
        val house = houseAt(player.x, player.z) ?: return null
        val outside = outside(house.instance)
        return DeathPlace(keepItems = true, outside.x, outside.z, 0, outside.x, outside.z, 0, safe = true)
    }

    /**
     * [player]'s house as an instance, for [buildingMode]: a new one each time, the old one (if anybody is still in it)
     * left to end once they leave. Null when the dynamic area has no room.
     */
    fun build(player: Player, state: HouseState, buildingMode: Boolean): Open? {
        forgetEnded()
        val parts = ArrayList<InstancePart>()
        val yard = House.yardSize(player.skills.baseLevel(Skill.CONSTRUCTION))
        for (x in 0 until House.GRID) {
            for (z in 0 until House.GRID) {
                val room = state.room(x, z)
                if (room != null) {
                    parts += part(room.type, x, z, room.rotation)
                } else if (x < yard && z < yard) {
                    parts += part(RoomType.YARD, x, z, 0)
                }
                // Upstairs only the rooms: an empty cell there is open air (step CON5).
                state.room(x, z, House.UPPER)?.let { parts += part(it.type, x, z, it.rotation, House.UPPER) }
            }
        }
        val outside = outsideTile(state.location)
        val instance = world.instances.create(House.GRID, House.GRID, parts, outside.x, outside.z, 0) ?: return null
        // Open air upstairs can't be walked into (Codex review on #990).
        for (x in 0 until House.GRID) for (z in 0 until House.GRID) if (state.room(x, z, House.UPPER) == null) world.instances.clear(instance, x, z, House.UPPER)
        val house = Open(player.saveKey, instance, buildingMode)
        for (room in state.rooms) furnish(house, state, room)
        open[player.saveKey] = house
        byInstance[instance] = house
        setVarbit(player, House.BUILDING_MODE_VARBIT, if (buildingMode) 1 else 0)
        logger.info { "Built ${player.username}'s house (${state.rooms.size} rooms${if (buildingMode) ", building mode" else ""}) in $instance" }
        return house
    }

    private fun part(type: RoomType, x: Int, z: Int, rotation: Int, floor: Int = House.GROUND) =
        InstancePart(type.templateX, type.templateZ, 0, 1, 1, x, z, floor, rotation)

    /** What [room]'s hotspots show in [instance]: what is built there, and out of building mode walls, doors or nothing. */
    private fun furnish(house: Open, state: HouseState, room: HouseRoom) {
        val instance = house.instance
        val buildingMode = house.buildingMode
        val baseX = instance.baseX + (room.x shl 3)
        val baseZ = instance.baseZ + (room.z shl 3)
        for (dx in 0 until 8) {
            for (dz in 0 until 8) {
                for (loc in world.locs.at(baseX + dx, baseZ + dz, room.floor)) {
                    if (world.locs.types[loc.id]?.ops?.getOrNull(House.BUILD - 1) != House.BUILD_OP) continue
                    house.hotspots[slot(loc.x, loc.z, loc.level, loc.layer)] = loc
                    val built = room.built[loc.id]
                    val shows = when {
                        built != null -> loc.copy(id = built)
                        buildingMode -> continue
                        isDoorHotspot(loc.id) -> door(state, room, loc, dx, dz)
                        else -> null
                    }
                    world.locs.setPermanent(loc.x, loc.z, loc.level, loc.layer, shows)
                }
            }
        }
    }

    private fun isDoorHotspot(id: Int) = id == House.DOOR_HOTSPOT_LEFT || id == House.DOOR_HOTSPOT_RIGHT

    /** A door hotspot out of building mode: nothing out of doors, else a door where a room lies beyond, else a wall. */
    private fun door(state: HouseState, room: HouseRoom, hotspot: ZoneLoc, dx: Int, dz: Int): ZoneLoc? {
        if (room.type.outdoors) return null
        val side = side(dx, dz) ?: return hotspot.copy(id = House.WALL)
        val (bx, bz) = beyond(room.x, room.z, side)
        val beyond = state.room(bx, bz, room.floor)
        if (beyond == null || (side + 2) % 4 !in sides(beyond.type, beyond.rotation)) return hotspot.copy(id = House.WALL)
        // The cache's left hotspot is the door's right leaf: the double door's right leaf is the next tile clockwise along
        // the wall from its left one ([osrsps.game.loc.Doors]), and the left hotspot is the other way (Codex review on #788).
        return hotspot.copy(id = if (hotspot.id == House.DOOR_HOTSPOT_LEFT) House.DOOR_RIGHT else House.DOOR_LEFT)
    }

    /** The cell past side [side] (0 west, 1 north, 2 east, 3 south) of cell [x], [z]. */
    private fun beyond(x: Int, z: Int, side: Int): Pair<Int, Int> = when (side) {
        0 -> x - 1 to z
        1 -> x to z + 1
        2 -> x + 1 to z
        else -> x to z - 1
    }

    /** Which side of a room a tile [dx], [dz] in its zone is on, as it lies: 0 west, 1 north, 2 east, 3 south. */
    private fun side(dx: Int, dz: Int): Int? = when {
        dx == 0 -> 0
        dz == 7 -> 1
        dx == 7 -> 2
        dz == 0 -> 3
        else -> null
    }

    /** The sides of a [type] room turned [rotation] quarters clockwise with a door hotspot on them. */
    fun sides(type: RoomType, rotation: Int): Set<Int> = doorSides(type).mapTo(HashSet()) { (it + rotation) % 4 }

    /** The sides of [type]'s template with a door hotspot on them, read from the template itself. */
    fun doorSides(type: RoomType): Set<Int> = doorSides.getOrPut(type) {
        val sides = HashSet<Int>()
        for (dx in 0 until 8) {
            for (dz in 0 until 8) {
                val here = world.locs.cached(type.templateX + dx, type.templateZ + dz, 0, 0) ?: continue
                if (isDoorHotspot(here.id)) side(dx, dz)?.let(sides::add)
            }
        }
        sides
    }

    /**
     * Build on the door hotspot [hotspot]: in the player's own house in building mode, a room added past it, or the room
     * past it removed.
     */
    private suspend fun doorway(context: ScriptContext, hotspot: ZoneLoc) {
        val player = context.player
        val house = houseAt(hotspot.x, hotspot.z)
        val state = player.house
        if (house == null || state == null || house.owner != player.saveKey || !house.buildingMode) return world.message(player, GameLines.NOTHING_INTERESTING)
        clearGhost(house, state)
        val instance = house.instance
        val room = state.room((hotspot.x - instance.baseX) shr 3, (hotspot.z - instance.baseZ) shr 3, hotspot.level) ?: return
        val side = side(hotspot.x and 7, hotspot.z and 7) ?: return
        val (x, z) = beyond(room.x, room.z, side)
        val there = state.room(x, z, room.floor)
        if (there != null) remove(context, house, state, there) else add(context, house, state, Cell(x, z, room.floor), (side + 2) % 4)
    }

    /**
     * The add-room menu, then the picked room's ghost at [cell] with a door on side [facing], until it is built. Upstairs
     * (step CON5) a room needs one below it (the Wiki's Player-owned house: "Players cannot have a room above ground
     * level if there is no room below it"), and a hall there is the hall at the top of a staircase ([RoomType.upstairs]).
     */
    private suspend fun add(context: ScriptContext, house: Open, state: HouseState, cell: Cell, facing: Int) {
        val player = context.player
        val (x, z, floor) = cell
        val level = player.skills.baseLevel(Skill.CONSTRUCTION)
        val yard = House.yardSize(level)
        if (x !in 0 until yard || z !in 0 until yard) return world.message(player, ConstructionLines.ROOM_OUTSIDE)
        if (!supported(state, cell)) return world.message(player, ConstructionLines.ROOM_NO_SUPPORT)
        if (state.rooms.size >= House.maxRooms(level)) return world.message(player, ConstructionLines.ROOM_LIMIT, "max" to House.maxRooms(level))
        if (!fits(state, x, z, House.maxSize(level))) return world.message(player, ConstructionLines.ROOM_TOO_FAR, "size" to House.maxSize(level))
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, House.ADD_ROOM)
        world.interfaces.setEvents(player, House.ADD_ROOM, House.ADD_ROOM_LIST, House.ADD_ROOM_SLOTS.first, House.ADD_ROOM_SLOTS.last, Chatbox.EVENT_PAUSE_BUTTON)
        val picked = context.pauseButton(House.ADD_ROOM, House.ADD_ROOM_LIST, House.ADD_ROOM_SLOTS)
        world.interfaces.closeModals(player)
        val listed = RoomType.byId(picked)?.takeIf { it.listed } ?: return
        val type = if (floor == House.UPPER) listed.upstairs else listed
        if (!stillHere(house, player) || state.room(x, z, floor) != null || !supported(state, cell)) return
        if (player.skills.currentLevel(Skill.CONSTRUCTION) < type.level) return world.message(player, ConstructionLines.ROOM_LEVEL, "level" to type.level)
        if (player.inventory.count(EstateAgent.COINS) < type.cost) return world.message(player, ConstructionLines.ROOM_COINS, "cost" to type.cost)
        if (!type.goesOn(floor)) return world.message(player, ConstructionLines.ROOM_FLOOR)
        val turns = (0..3).filter { facing in sides(type, it) }
        if (turns.isEmpty()) return world.message(player, ConstructionLines.ROOM_NO_DOOR)
        var turn = 0
        showGhost(house, state, type, cell, turns[turn])
        while (true) {
            var pick = 0
            world.dialogues.start(context) {
                pick = choice(ConstructionLines.ROTATE_CLOCKWISE, ConstructionLines.ROTATE_ANTICLOCKWISE, ConstructionLines.BUILD_ROOM, ConstructionLines.CANCEL_ROOM)
            }
            if (!stillHere(house, player) || house.ghost != cell) return
            when (pick) {
                1 -> turn = (turn + 1) % turns.size
                2 -> turn = (turn + turns.size - 1) % turns.size
                3 -> {
                    // Checked again after the menu's waits: the coins may have gone since the room was picked, or the
                    // room below it.
                    if (state.room(x, z, floor) != null || !supported(state, cell)) return clearGhost(house, state)
                    if (player.inventory.count(EstateAgent.COINS) < type.cost) {
                        clearGhost(house, state)
                        return world.message(player, ConstructionLines.ROOM_COINS, "cost" to type.cost)
                    }
                    Ned.take(player.inventory, EstateAgent.COINS, type.cost)
                    state.rooms += HouseRoom(type, x, z, floor, turns[turn])
                    house.ghost = null
                    logger.info { "${player.username} built a ${type.key} at $x, $z on floor $floor turned ${turns[turn]} for ${type.cost} coins" }
                    return
                }
                else -> return clearGhost(house, state)
            }
            showGhost(house, state, type, cell, turns[turn])
            // GUESSED: the menu comes back a tick after the turned ghost is drawn, so the client sees it close and open.
            context.delay(1)
            if (!stillHere(house, player) || house.ghost != cell) return
        }
    }

    /** Whether a room at [cell] would stand on something: anywhere on the ground floor, upstairs over a room. */
    private fun supported(state: HouseState, cell: Cell): Boolean = cell.floor != House.UPPER || state.room(cell.x, cell.z) != null

    /** Whether a room at cell [x], [z] keeps [state]'s house within [size] rooms across both ways. */
    private fun fits(state: HouseState, x: Int, z: Int, size: Int): Boolean {
        val xs = state.rooms.map { it.x } + x
        val zs = state.rooms.map { it.z } + z
        return xs.max() - xs.min() < size && zs.max() - zs.min() < size
    }

    /**
     * The prompt to remove [room], and its removal: the yard's grass back in its cell, or open air upstairs. Not a ground
     * floor room under one upstairs (the Wiki's Player-owned house: "You cannot remove a room on the ground floor that is
     * supporting another room on the 1st floor").
     */
    internal suspend fun remove(context: ScriptContext, house: Open, state: HouseState, room: HouseRoom) {
        val player = context.player
        // Not the room with the house's only exit portal: a house needs one (the Garden page).
        if (House.EXIT_PORTAL in room.built.values && state.rooms.all { it === room || House.EXIT_PORTAL !in it.built.values }) {
            return world.message(player, ConstructionLines.REMOVE_PORTAL)
        }
        if (supports(state, room)) return world.message(player, ConstructionLines.REMOVE_SUPPORTING)
        var pick = 0
        world.dialogues.start(context) {
            pick = choice(ConstructionLines.REMOVE_YES, ConstructionLines.REMOVE_NO, title = text(ConstructionLines.REMOVE_TITLE))
        }
        if (pick != 1 || !stillHere(house, player) || state.room(room.x, room.z, room.floor) !== room || supports(state, room)) return
        state.rooms.remove(room)
        val cell = Cell(room.x, room.z, room.floor)
        lay(house, state, if (room.floor == House.GROUND) RoomType.YARD else null, cell, 0)
        if (room.floor == House.UPPER) Stairs.strand(world, house, state, cell)
        logger.info { "${player.username} removed the ${room.type.key} at ${room.x}, ${room.z} on floor ${room.floor}" }
    }

    /** Whether [room] holds up a room upstairs. */
    private fun supports(state: HouseState, room: HouseRoom): Boolean = room.floor == House.GROUND && state.room(room.x, room.z, House.UPPER) != null

    /**
     * In [player]'s own house in building mode, the house and the room built at tile [x], [z] on [level]; null anywhere
     * else, or where no room is built (the yard, open air upstairs, or a ghost).
     */
    internal fun buildingRoom(player: Player, x: Int, z: Int, level: Int): Pair<Open, HouseRoom>? {
        val house = houseAt(x, z) ?: return null
        val state = player.house ?: return null
        if (house.owner != player.saveKey || !house.buildingMode) return null
        val room = state.room((x - house.instance.baseX) shr 3, (z - house.instance.baseZ) shr 3, level) ?: return null
        return house to room
    }

    /** Whether [player] is still in [house], and it is still open, after a wait. */
    internal fun stillHere(house: Open, player: Player): Boolean =
        world.instances.all.any { it === house.instance } && house.instance.contains(player.x, player.z) && player.house != null

    /** A [type] room's ghost at [cell] turned [rotation], as building mode shows a room, in place of what was there. */
    private fun showGhost(house: Open, state: HouseState, type: RoomType, cell: Cell, rotation: Int) {
        house.ghost = cell
        lay(house, state, type, cell, rotation)
    }

    /**
     * Takes away [house]'s ghost, if it has one that wasn't built, putting the yard (or open air upstairs) back. Anyone who
     * walked into an upstairs ghost goes to the floor below, as when a room upstairs is removed.
     */
    private fun clearGhost(house: Open, state: HouseState) {
        val cell = house.ghost ?: return
        house.ghost = null
        if (state.room(cell.x, cell.z, cell.floor) != null) return
        lay(house, state, if (cell.floor == House.GROUND) RoomType.YARD else null, cell, 0)
        if (cell.floor == House.UPPER) Stairs.strand(world, house, state, cell)
    }

    /**
     * Lays a [type] room turned [rotation] over [cell] of [house], in building mode's look, or empties the cell for a null
     * [type], and redraws the house for whoever is in it: the cell's loc changes go with what was there.
     */
    internal fun lay(house: Open, state: HouseState, type: RoomType?, cell: Cell, rotation: Int) {
        val instance = house.instance
        val baseX = instance.baseX + (cell.x shl 3)
        val baseZ = instance.baseZ + (cell.z shl 3)
        world.locs.forgetArea(baseX, baseZ, baseX + 7, baseZ + 7, cell.floor)
        house.hotspots.values.removeAll { it.level == cell.floor && it.x in baseX..baseX + 7 && it.z in baseZ..baseZ + 7 }
        if (type == null) {
            world.instances.clear(instance, cell.x, cell.z, cell.floor)
        } else {
            world.instances.replace(instance, part(type, cell.x, cell.z, rotation, cell.floor))
            if (type != RoomType.YARD) furnish(house, state, state.room(cell.x, cell.z, cell.floor) ?: HouseRoom(type, cell.x, cell.z, cell.floor, rotation))
        }
        world.redrawInstance(instance)
    }

    /** Where [state]'s house puts its owner on the way in: beside the exit portal. Null for a house with none. */
    private fun arrival(instance: Instance, state: HouseState): Tile? {
        val garden = state.rooms.firstOrNull { House.EXIT_PORTAL in it.built.values && it.floor == House.GROUND } ?: return null
        return Tile(instance.baseX + (garden.x shl 3) + House.ARRIVE_DX, instance.baseZ + (garden.z shl 3) + House.ARRIVE_DZ)
    }

    private fun outside(instance: Instance): Tile = Tile(instance.exitX, instance.exitZ)

    private fun outsideTile(location: HouseLocation): Tile = when (location) {
        HouseLocation.RIMMINGTON -> House.RIMMINGTON_OUTSIDE
    }

    /** Houses whose instance has ended, as nothing tells this when one does. */
    private fun forgetEnded() {
        if (byInstance.isEmpty()) return
        // Once a tick and on each build, over a set of the live instances: never once per lookup (Codex review on #788).
        val live = world.instances.all.toHashSet()
        byInstance.keys.retainAll(live)
        open.values.removeAll { it.instance !in live }
    }

    private fun setVarbit(player: Player, id: Int, value: Int) {
        if (player.vars.types.varbit(id) != null) player.vars.setVarbit(id, value)
    }

    /** Sold a house: [player] now owns the starter house, and the house location varbit says Rimmington. */
    fun sell(player: Player) {
        val house = HouseState.starter()
        player.house = house
        setVarbit(player, House.LOCATION_VARBIT, house.location.varbitValue)
    }
}
