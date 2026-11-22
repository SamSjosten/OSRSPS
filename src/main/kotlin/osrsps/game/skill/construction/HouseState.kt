package osrsps.game.skill.construction

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A room type, as the cache's `poh_room` table (dbtable 111, the 242 cache) has it: its [id] there (`room_type`, what
 * the add-room menu sends), the tile offset of its template from the template area's south-west corner
 * (`source_offset`), the Construction level and coins it takes to build, and which floors it may go on
 * (`floor_restriction`), and its button in the add-room menu. [key] names it in a save. The menu's own list (cs2 `poh_add_room_init`) offers every type but
 * the two halls with stairs, [listed] false, which come with floors (step CON5).
 *
 * The templates are 8 by 8 tile zones from x 1856, z 7040 (map squares 29 to 32, 110 and 111); each house style takes 8
 * zone columns of one level, basic wood zones 232 to 239 on level 0 (the 242 cache: every basic wood door hotspot,
 * `poh_hotspot_doorl_rimmington` 15313, is there). The table's offsets are basic wood's, and they match the rooms found
 * by their hotspots' names (`poh_crude_garden_1` in the garden, `poh_parlour_1` in the parlour, only `poh_grass` in the
 * yard).
 */
enum class RoomType(
    val key: String,
    val id: Int,
    val offsetX: Int,
    val offsetZ: Int,
    val level: Int,
    val cost: Int,
    val floors: RoomFloors = RoomFloors.ABOVE_GROUND,
    val listed: Boolean = true,
    /** Its button in the add-room menu (`button`), whose op the menu's script turns into [id]; -1 for none. */
    val button: Int = -1,
) {
    /** The empty land round a house: grass only, no hotspots. Not a room: nothing builds it, and it isn't in the table. */
    YARD("yard", -1, 8, 0, 1, 0, RoomFloors.GROUND, listed = false),
    PARLOUR("parlour", 1, 0, 56, 1, 1_000, button = 5),
    GARDEN("garden", 2, 0, 8, 1, 1_000, RoomFloors.GROUND, button = 7),
    KITCHEN("kitchen", 3, 16, 56, 5, 5_000, button = 9),
    DINING_ROOM("dining_room", 4, 32, 56, 10, 5_000, button = 11),
    BEDROOM("bedroom", 5, 48, 56, 20, 10_000, button = 13),
    GAMES_ROOM("games_room", 6, 40, 32, 30, 25_000, button = 15),
    SKILL_HALL("skill_hall", 7, 8, 48, 25, 15_000, button = 17),
    SKILL_HALL_STAIRS("skill_hall_stairs", 8, 24, 48, 25, 15_000, listed = false, button = 17),
    QUEST_HALL("quest_hall", 9, 40, 48, 35, 25_000, button = 19),
    QUEST_HALL_STAIRS("quest_hall_stairs", 10, 56, 48, 35, 25_000, listed = false, button = 19),
    CHAPEL("chapel", 11, 16, 40, 45, 50_000, button = 21),
    WORKSHOP("workshop", 12, 0, 40, 15, 10_000, button = 23),
    STUDY("study", 13, 32, 40, 40, 50_000, button = 25),
    PORTAL_CHAMBER("portal_chamber", 14, 8, 32, 50, 100_000, button = 27),
    THRONE_ROOM("throne_room", 15, 48, 40, 60, 150_000, RoomFloors.GROUND, button = 30),
    OUBLIETTE("oubliette", 16, 48, 24, 65, 150_000, RoomFloors.DUNGEON, button = 32),
    DUNGEON_CORRIDOR("dungeon_corridor", 17, 32, 24, 70, 7_500, RoomFloors.DUNGEON, button = 34),
    DUNGEON_JUNCTION("dungeon_junction", 18, 0, 24, 70, 7_500, RoomFloors.DUNGEON, button = 38),
    DUNGEON_STAIRS("dungeon_stairs", 19, 16, 24, 70, 7_500, RoomFloors.DUNGEON, button = 43),
    TREASURE_ROOM("treasure_room", 20, 56, 32, 75, 250_000, RoomFloors.DUNGEON, button = 46),
    FORMAL_GARDEN("formal_garden", 21, 16, 8, 55, 75_000, RoomFloors.GROUND, button = 48),
    COMBAT_ROOM("combat_room", 22, 24, 32, 32, 25_000, button = 50),
    COSTUME_ROOM("costume_room", 23, 48, 8, 42, 50_000, button = 52),
    MENAGERIE_INDOORS("menagerie_indoors", 24, 56, 16, 37, 30_000, button = 54),
    MENAGERIE_OUTDOORS("menagerie_outdoors", 25, 56, 0, 37, 30_000, RoomFloors.GROUND, button = 57),
    SUPERIOR_GARDEN("superior_garden", 26, 40, 0, 65, 75_000, RoomFloors.GROUND, button = 60),
    ACHIEVEMENT_GALLERY("achievement_gallery", 27, 8, 64, 80, 200_000, button = 62),
    PORTAL_NEXUS("portal_nexus", 28, 24, 64, 72, 200_000, button = 28),
    LEAGUE_HALL("league_hall", 29, 40, 64, 27, 15_000, button = 63),
    ;

    /** The template zone's south-west tile, in basic wood. */
    val templateX: Int get() = TEMPLATE_X + offsetX
    val templateZ: Int get() = TEMPLATE_Z + offsetZ

    /**
     * Whether it is out of doors (the table's `has_roof` 0: the three gardens and the outdoor menagerie), so its door
     * hotspots go to nothing out of building mode, as it has no walls.
     */
    val outdoors: Boolean get() = this == GARDEN || this == FORMAL_GARDEN || this == SUPERIOR_GARDEN || this == MENAGERIE_OUTDOORS

    companion object {
        /** The template area's south-west tile. */
        const val TEMPLATE_X = 1856
        const val TEMPLATE_Z = 7040

        fun byKey(key: String): RoomType? = entries.firstOrNull { it.key == key }

        fun byId(id: Int): RoomType? = entries.firstOrNull { it.id == id && it != YARD }
    }
}

/** The floors a room type may go on: the table's `floor_restriction`, none (any floor above ground), 1 or 2. */
enum class RoomFloors {
    ABOVE_GROUND,
    DUNGEON,
    GROUND,
}

/**
 * One room of a house: its type, its cell [x], [z] in the house's grid of rooms and its [floor] ([House.GROUND] for
 * the ground floor), turned [rotation] quarters clockwise,
 * and what is built on its hotspots: by hotspot loc type, the loc type built there.
 */
class HouseRoom(
    val type: RoomType,
    val x: Int,
    val z: Int,
    val floor: Int = House.GROUND,
    val rotation: Int = 0,
    val built: MutableMap<Int, Int> = HashMap(),
) {
    init {
        require(x in 0 until House.GRID && z in 0 until House.GRID) { "Room cell $x, $z is outside the house's grid" }
        require(floor in 0 until House.FLOORS) { "Room floor $floor is out of range" }
        require(rotation in 0..3) { "Room rotation $rotation is out of range" }
    }

    fun copy(): HouseRoom = HouseRoom(type, x, z, floor, rotation, HashMap(built))
}

/**
 * A player's house, once they have bought one from an estate agent: where its portal is and its rooms. Null on a
 * player who hasn't bought one. Saved.
 */
class HouseState(
    val location: HouseLocation = HouseLocation.RIMMINGTON,
    val rooms: MutableList<HouseRoom> = ArrayList(),
) {
    fun room(x: Int, z: Int, floor: Int = House.GROUND): HouseRoom? = rooms.firstOrNull { it.x == x && it.z == z && it.floor == floor }

    fun copy(): HouseState = HouseState(location, rooms.mapTo(ArrayList()) { it.copy() })

    companion object {
        /**
         * The house an estate agent sells: a garden with its exit portal and a parlour (the Wiki's Construction page:
         * "The house starts off with only a parlour and garden"). GUESSED: where they are in the grid, the garden in
         * the middle of the 3 by 3 a level 1 builder has and the parlour north of it, its south door on the garden;
         * the house viewer on live would show it.
         */
        fun starter(): HouseState = HouseState(
            HouseLocation.RIMMINGTON,
            arrayListOf(
                HouseRoom(RoomType.GARDEN, House.STARTER_X, House.STARTER_Z, built = hashMapOf(House.CENTREPIECE_SPACE to House.EXIT_PORTAL)),
                HouseRoom(RoomType.PARLOUR, House.STARTER_X, House.STARTER_Z + 1),
            ),
        )
    }
}

/**
 * Where a house's portal is, with the value `poh_house_location` (varbit 2187) holds for it: 0 is no house (cs2 proc
 * 3809 refuses a player who owns no house at 0). GUESSED: Rimmington's 1, as it is first in the estate agent's list.
 */
enum class HouseLocation(val key: String, val varbitValue: Int, val portal: Int) {
    RIMMINGTON("rimmington", 1, House.RIMMINGTON_PORTAL),
    ;

    companion object {
        fun byKey(key: String): HouseLocation? = entries.firstOrNull { it.key == key }
    }
}

/** A house in a save. */
object HouseSaves {
    fun toJson(house: HouseState): JsonObject = buildJsonObject {
        put("location", house.location.key)
        putJsonArray("rooms") {
            for (room in house.rooms) {
                addJsonObject {
                    put("type", room.type.key)
                    put("x", room.x)
                    put("z", room.z)
                    if (room.floor != House.GROUND) put("floor", room.floor)
                    if (room.rotation != 0) put("rotation", room.rotation)
                    if (room.built.isNotEmpty()) putJsonObject("built") {
                        for ((hotspot, loc) in room.built.toSortedMap()) put(hotspot.toString(), loc)
                    }
                }
            }
        }
    }

    /** Throws [IllegalArgumentException] for a house no server would have saved: an unknown room, two rooms in a cell. */
    fun fromJson(json: JsonObject): HouseState {
        val locationKey = json["location"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("House has no location")
        val location = HouseLocation.byKey(locationKey) ?: throw IllegalArgumentException("Unknown house location $locationKey")
        val rooms = ArrayList<HouseRoom>()
        for (element in json["rooms"]?.jsonArray.orEmpty()) {
            val entry = element.jsonObject
            val typeKey = entry["type"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("House room has no type")
            val type = RoomType.byKey(typeKey)?.takeIf { it != RoomType.YARD } ?: throw IllegalArgumentException("Unknown room type $typeKey")
            val built = HashMap<Int, Int>()
            for ((hotspot, loc) in entry["built"]?.jsonObject.orEmpty()) {
                built[hotspot.toIntOrNull() ?: throw IllegalArgumentException("Bad hotspot $hotspot")] = loc.jsonPrimitive.int
            }
            val room = HouseRoom(
                type, entry.int("x"), entry.int("z"), entry["floor"]?.jsonPrimitive?.int ?: House.GROUND,
                entry["rotation"]?.jsonPrimitive?.int ?: 0, built,
            )
            require(rooms.none { it.x == room.x && it.z == room.z && it.floor == room.floor }) { "Two rooms in cell ${room.x}, ${room.z}" }
            rooms += room
        }
        require(rooms.size <= House.MAX_ROOMS) { "A house of ${rooms.size} rooms" }
        return HouseState(location, rooms)
    }

    private fun JsonObject.int(key: String): Int = this[key]?.jsonPrimitive?.int ?: throw IllegalArgumentException("House room has no $key")
}
