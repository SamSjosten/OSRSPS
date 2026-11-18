package osrsps.game.skill.construction

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
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

    /** The house's own locs that need handlers in its instance. */
    val INSTANCE_LOCS: Set<Int> = setOf(EXIT_PORTAL, DOOR_LEFT, DOOR_RIGHT, DOOR_LEFT_OPEN, DOOR_RIGHT_OPEN)
}

/**
 * Construction step CON1: the house a player buys from an estate agent ([EstateAgent]), entered through its portal in
 * Rimmington and left through its exit portal.
 *
 * A house is an instance ([osrsps.game.instance.Instances]) of [House.GRID] by [House.GRID] zones, one zone a room,
 * each a copy of the cache's room template ([RoomType]). Live draws houses this way: the cache has every room of every
 * style as a zone of the template area, its build-mode ghosts (hotspot locs, each with a Build option) in place. The
 * ground floor is on level 1 of the instance, as live keeps level 0 for the dungeon below it (inferred: the Wiki's
 * Dungeon rooms are "underground", below the ground floor). The yard round the rooms, [House.yardSize] cells square from
 * the grid's south-west corner (growing "a row to the top ... and a column to the right"), is the grass template.
 *
 * In building mode the house shows its hotspots, with what is built on them instead (the exit portal on the garden's
 * centrepiece space). Out of it every empty hotspot goes, and each door hotspot becomes a wall of the style where no
 * room lies beyond it, or a door where one does; in a garden it just goes, as gardens have no walls. GUESSED: the doors
 * start shut and open and close as any double door; a door to a garden is a door; building mode's door hotspots stay
 * even where a room lies beyond.
 *
 * Entering puts the player beside the exit portal ([House.ARRIVE_DX]); its Enter, a logout inside and the end of the
 * instance put them outside the portal they came in by ([House.RIMMINGTON_OUTSIDE]). A house is built afresh each time
 * its owner goes in, and ends once nobody is in it. GUESSED: no loading screen (live shows `poh_loading`, interface
 * 71, for a moment); Friend's house, Lock, guests, the house options and adding rooms come in later steps.
 */
class Houses(private val world: World) {
    /** A house open now: whose it is, its instance, and whether it is in building mode. */
    class Open(val owner: String, val instance: Instance, val buildingMode: Boolean)

    private val open = HashMap<String, Open>()

    /** By room type, the sides of its template with door hotspots (0 west, 1 north, 2 east, 3 south). */
    private val doorSides = HashMap<RoomType, Set<Int>>()

    /** The house [player] is in, if any. */
    fun houseAt(x: Int, z: Int): Open? {
        forgetEnded()
        val instance = world.instances.at(x, z) ?: return null
        return open.values.firstOrNull { it.instance === instance }
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
        world.doors.registerDouble(House.DOOR_LEFT, House.DOOR_LEFT_OPEN, House.DOOR_RIGHT, House.DOOR_RIGHT_OPEN)
    }

    /** Takes [ScriptContext.player] into their own house, built for [buildingMode]; a message if they have none. */
    fun enter(context: ScriptContext, buildingMode: Boolean) {
        val player = context.player
        val state = player.house ?: return world.message(player, ConstructionLines.NO_HOUSE)
        val house = build(player, state, buildingMode) ?: return world.message(player, ConstructionLines.NO_ROOM)
        val arrive = arrival(house.instance, state)
        world.teleport(player, arrive.x, arrive.z, House.GROUND)
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
                val type = room?.type ?: if (x < yard && z < yard) RoomType.YARD else continue
                parts += InstancePart(type.templateX, type.templateZ, 0, 1, 1, x, z, House.GROUND)
            }
        }
        val outside = outsideTile(state.location)
        val instance = world.instances.create(House.GRID, House.GRID, parts, outside.x, outside.z, 0) ?: return null
        for (room in state.rooms) furnish(instance, state, room, buildingMode)
        val house = Open(player.saveKey, instance, buildingMode)
        open[player.saveKey]?.let { old -> if (world.instances.all.none { it === old.instance }) open.remove(player.saveKey) }
        open[player.saveKey] = house
        setVarbit(player, House.BUILDING_MODE_VARBIT, if (buildingMode) 1 else 0)
        logger.info { "Built ${player.username}'s house (${state.rooms.size} rooms${if (buildingMode) ", building mode" else ""}) in $instance" }
        return house
    }

    /** What [room]'s hotspots show in [instance]: what is built there, and out of building mode walls, doors or nothing. */
    private fun furnish(instance: Instance, state: HouseState, room: HouseRoom, buildingMode: Boolean) {
        val baseX = instance.baseX + (room.x shl 3)
        val baseZ = instance.baseZ + (room.z shl 3)
        for (dx in 0 until 8) {
            for (dz in 0 until 8) {
                for (loc in world.locs.at(baseX + dx, baseZ + dz, House.GROUND)) {
                    if (world.locs.types[loc.id]?.ops?.getOrNull(4) != House.BUILD_OP) continue
                    val built = room.built[loc.id]
                    val shows = when {
                        built != null -> loc.copy(id = built)
                        buildingMode -> continue
                        loc.id == House.DOOR_HOTSPOT_LEFT || loc.id == House.DOOR_HOTSPOT_RIGHT -> door(state, room, loc, dx, dz)
                        else -> null
                    }
                    world.locs.setPermanent(loc.x, loc.z, loc.level, loc.layer, shows)
                }
            }
        }
    }

    /** A door hotspot out of building mode: nothing in a garden, else a door where a room lies beyond, else a wall. */
    private fun door(state: HouseState, room: HouseRoom, hotspot: ZoneLoc, dx: Int, dz: Int): ZoneLoc? {
        if (room.type == RoomType.GARDEN) return null
        val side = side(dx, dz) ?: return hotspot.copy(id = House.WALL)
        val beyond = when (side) {
            0 -> state.room(room.x - 1, room.z, room.floor)
            1 -> state.room(room.x, room.z + 1, room.floor)
            2 -> state.room(room.x + 1, room.z, room.floor)
            else -> state.room(room.x, room.z - 1, room.floor)
        }
        if (beyond == null || (side + 2) % 4 !in doorSides(beyond.type)) return hotspot.copy(id = House.WALL)
        return hotspot.copy(id = if (hotspot.id == House.DOOR_HOTSPOT_LEFT) House.DOOR_LEFT else House.DOOR_RIGHT)
    }

    /** Which side of an unrotated room a tile [dx], [dz] in it is on: 0 west, 1 north, 2 east, 3 south. */
    private fun side(dx: Int, dz: Int): Int? = when {
        dx == 0 -> 0
        dz == 7 -> 1
        dx == 7 -> 2
        dz == 0 -> 3
        else -> null
    }

    /** The sides of [type]'s template with a door hotspot on them, read from the template itself. */
    fun doorSides(type: RoomType): Set<Int> = doorSides.getOrPut(type) {
        val sides = HashSet<Int>()
        for (dx in 0 until 8) {
            for (dz in 0 until 8) {
                val here = world.locs.cached(type.templateX + dx, type.templateZ + dz, 0, 0) ?: continue
                if (here.id == House.DOOR_HOTSPOT_LEFT || here.id == House.DOOR_HOTSPOT_RIGHT) side(dx, dz)?.let(sides::add)
            }
        }
        sides
    }

    /** Where [state]'s house puts its owner on the way in: beside the exit portal, in the first garden that has one. */
    private fun arrival(instance: Instance, state: HouseState): Tile {
        val garden = state.rooms.firstOrNull { House.EXIT_PORTAL in it.built.values } ?: state.rooms.firstOrNull { it.type == RoomType.GARDEN } ?: state.rooms.first()
        return Tile(instance.baseX + (garden.x shl 3) + House.ARRIVE_DX, instance.baseZ + (garden.z shl 3) + House.ARRIVE_DZ)
    }

    private fun outside(instance: Instance): Tile = Tile(instance.exitX, instance.exitZ)

    private fun outsideTile(location: HouseLocation): Tile = when (location) {
        HouseLocation.RIMMINGTON -> House.RIMMINGTON_OUTSIDE
    }

    /** Houses whose instance has ended, as nothing tells this when one does. */
    private fun forgetEnded() {
        open.values.removeAll { house -> world.instances.all.none { it === house.instance } }
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
