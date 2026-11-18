package osrsps.game.skill.construction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Construction step CON1: the starter house and how a house is saved. */
class HouseStateTest {
    private fun read(text: String) = HouseSaves.fromJson(Json.parseToJsonElement(text).jsonObject)

    @Test
    fun `the starter house is a garden with its exit portal and a parlour north of it, in Rimmington`() {
        val house = HouseState.starter()
        assertEquals(HouseLocation.RIMMINGTON, house.location)
        val garden = house.room(House.STARTER_X, House.STARTER_Z)!!
        assertEquals(RoomType.GARDEN, garden.type)
        assertEquals(mapOf(House.CENTREPIECE_SPACE to House.EXIT_PORTAL), garden.built)
        assertEquals(RoomType.PARLOUR, house.room(House.STARTER_X, House.STARTER_Z + 1)!!.type)
    }

    @Test
    fun `a house reads back as it was saved, and a copy doesn't share its rooms`() {
        val house = HouseState.starter()
        val read = HouseSaves.fromJson(HouseSaves.toJson(house))
        assertEquals(HouseSaves.toJson(house), HouseSaves.toJson(read))
        val copy = house.copy()
        copy.rooms.first().built.clear()
        assertEquals(House.EXIT_PORTAL, house.rooms.first().built[House.CENTREPIECE_SPACE])
    }

    @Test
    fun `a save no server would write is refused`() {
        assertFailsWith<IllegalArgumentException> { read("""{"location":"atlantis","rooms":[]}""") }
        assertFailsWith<IllegalArgumentException> { read("""{"location":"rimmington","rooms":[{"type":"yard","x":0,"z":0}]}""") }
        assertFailsWith<IllegalArgumentException> { read("""{"location":"rimmington","rooms":[{"type":"garden","x":9,"z":0}]}""") }
        assertFailsWith<IllegalArgumentException> {
            read("""{"location":"rimmington","rooms":[{"type":"garden","x":1,"z":1},{"type":"parlour","x":1,"z":1}]}""")
        }
    }

    @Test
    fun `the yard grows with the level as the Wiki's table has it`() {
        assertEquals(listOf(5, 5, 6, 6, 7, 7, 8, 8, 9, 9), listOf(1, 14, 15, 29, 30, 44, 45, 59, 60, 99).map(House::yardSize))
    }

    @Test
    fun `the room limit and the house's width follow the Wiki's table`() {
        val levels = listOf(1, 15, 25, 26, 30, 32, 38, 44, 45, 50, 56, 60, 62, 68, 74, 80, 86, 92, 96, 99)
        assertEquals(listOf(24, 24, 24, 25, 25, 26, 27, 28, 28, 29, 30, 30, 31, 32, 33, 34, 35, 36, 37, 38), levels.map(House::maxRooms))
        assertEquals(listOf(3, 4, 4, 4, 5, 5, 5, 5, 6, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7), levels.map(House::maxSize))
    }

    @Test
    fun `room types are found by the cache table's ids, the yard by none`() {
        assertEquals(RoomType.KITCHEN, RoomType.byId(3))
        assertEquals(RoomType.LEAGUE_HALL, RoomType.byId(29))
        assertEquals(null, RoomType.byId(-1))
        // The kitchen's template is basic wood's zone 234, 887.
        assertEquals(234 to 887, (RoomType.KITCHEN.templateX shr 3) to (RoomType.KITCHEN.templateZ shr 3))
        assertEquals(RoomType.entries.size, RoomType.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `a turned room reads back turned`() {
        val house = HouseState.starter()
        house.rooms += HouseRoom(RoomType.KITCHEN, 3, 3, rotation = 1)
        val read = HouseSaves.fromJson(HouseSaves.toJson(house))
        assertEquals(1, read.room(3, 3)?.rotation)
    }
}
