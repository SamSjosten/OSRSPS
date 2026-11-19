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
}
