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
 * A room type: the zone of the cache's house templates it is drawn from, in the basic wood style (Rimmington's), and
 * what the Wiki's Player-owned house page says it costs to build. [key] names it in a save.
 *
 * The templates are 8 by 8 tile zones from x 1856, z 7040 (map squares 29 to 32, 110 and 111); each house style takes 8
 * zone columns of one level, basic wood zones 232 to 239 on level 0 (the 242 cache: every basic wood door hotspot,
 * `poh_hotspot_doorl_rimmington` 15313, is there). Each room's zone was found by its hotspots' names
 * (`poh_crude_garden_1` in the garden, `poh_parlour_1` in the parlour, only `poh_grass` in the yard).
 */
enum class RoomType(val key: String, val templateZoneX: Int, val templateZoneZ: Int, val level: Int, val cost: Int) {
    /** The empty land round a house: grass only, no hotspots. Not a room: nothing builds it. */
    YARD("yard", 233, 880, 1, 0),
    GARDEN("garden", 232, 881, 1, 1_000),
    PARLOUR("parlour", 232, 887, 1, 1_000),
    ;

    /** The template zone's south-west tile. */
    val templateX: Int get() = templateZoneX shl 3
    val templateZ: Int get() = templateZoneZ shl 3

    companion object {
        fun byKey(key: String): RoomType? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One room of a house: its type, its cell [x], [z] in the house's grid of rooms and its [floor] ([House.GROUND] for
 * the ground floor), turned [rotation] quarters clockwise (0 for now: rotated rooms come with adding rooms, step CON2),
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
 * 3809 says "You must own a house" at 0). GUESSED: Rimmington's 1, as it is first in the estate agent's list.
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
