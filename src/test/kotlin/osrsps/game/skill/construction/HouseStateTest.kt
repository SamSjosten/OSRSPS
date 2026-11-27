package osrsps.game.skill.construction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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
    fun `a hand-edited save is refused without rooms on the ground floor and an exit portal, and loses what isn't built here`() {
        val garden = """{"type":"garden","x":1,"z":1,"built":{"${House.CENTREPIECE_SPACE}":${House.EXIT_PORTAL}}}"""
        read("""{"location":"rimmington","rooms":[$garden,{"type":"parlour","x":1,"z":2,"built":{"4515":6758}}]}""")
        // No rooms, no exit portal: refused. Two gardens each with an exit portal are fine (CON4e: a garden builds one).
        assertFailsWith<IllegalArgumentException> { read("""{"location":"rimmington","rooms":[]}""") }
        assertFailsWith<IllegalArgumentException> { read("""{"location":"rimmington","rooms":[{"type":"garden","x":1,"z":1}]}""") }
        assertEquals(2, read("""{"location":"rimmington","rooms":[$garden,{"type":"garden","x":2,"z":1,"built":{"${House.CENTREPIECE_SPACE}":${House.EXIT_PORTAL}}}]}""").rooms.size)
        // A garden's trees: a big tree on the big tree space, a tree on the tree space; each the other way round is dropped.
        assertEquals(mapOf(House.CENTREPIECE_SPACE to House.EXIT_PORTAL, 15362 to 4537, 15363 to 5127),
            read("""{"location":"rimmington","rooms":[{"type":"garden","x":1,"z":1,"built":{"${House.CENTREPIECE_SPACE}":${House.EXIT_PORTAL},"15362":4537,"15363":5127}}]}""").rooms.single().built)
        assertEquals(mapOf(House.CENTREPIECE_SPACE to House.EXIT_PORTAL),
            read("""{"location":"rimmington","rooms":[{"type":"garden","x":1,"z":1,"built":{"${House.CENTREPIECE_SPACE}":${House.EXIT_PORTAL},"15362":5127,"15363":4537}}]}""").rooms.single().built)
        // A room upstairs with nothing under it is refused; an exit portal anywhere but the garden's centrepiece is dropped,
        // leaving none.
        assertFailsWith<IllegalArgumentException> { read("""{"location":"rimmington","rooms":[$garden,{"type":"parlour","x":1,"z":2,"floor":2}]}""") }
        assertFailsWith<IllegalArgumentException> { read("""{"location":"rimmington","rooms":[{"type":"garden","x":1,"z":1,"built":{"4515":${House.EXIT_PORTAL}}}]}""") }
        // A chair built in the garden and a loc this server never builds are dropped, the house kept (R52B-1).
        assertEquals(mapOf(House.CENTREPIECE_SPACE to House.EXIT_PORTAL),
            read("""{"location":"rimmington","rooms":[{"type":"garden","x":1,"z":1,"built":{"${House.CENTREPIECE_SPACE}":${House.EXIT_PORTAL},"4515":6758}}]}""").rooms.single().built)
        assertEquals(emptyMap(), read("""{"location":"rimmington","rooms":[$garden,{"type":"parlour","x":1,"z":2,"built":{"4515":995}}]}""").rooms[1].built)
        // A rug's parts each on their own tile, a bookcase with its scroll rack, curtains: all fine.
        read("""{"location":"rimmington","rooms":[$garden,{"type":"parlour","x":1,"z":2,"built":{"4518":6761,"4519":6760,"4520":6759,"4521":6768,"4522":6771,"4524":6774}}]}""")
        // A chair on the rug's middle, a rug's corner on its middle, curtains on the fireplace's place, the Raging Echoes
        // curtains (not built here): each dropped.
        for (built in listOf(""""4518":6752""", """"4518":6759""", """"4523":6774""", """"4515":6761""", """"4524":56079""")) {
            assertEquals(emptyMap(), read("""{"location":"rimmington","rooms":[$garden,{"type":"parlour","x":1,"z":2,"built":{$built}}]}""").rooms[1].built, built)
        }
    }

    @Test
    fun `upstairs a room needs one under it, a hall there is a staircase's top, and the dungeon waits`() {
        val garden = """{"type":"garden","x":1,"z":1,"built":{"${House.CENTREPIECE_SPACE}":${House.EXIT_PORTAL}}}"""
        val hall = """{"type":"skill_hall","x":1,"z":2,"built":{"15380":13497}}"""
        val top = """{"type":"skill_hall_stairs","x":1,"z":2,"floor":2,"rotation":0,"built":{"15381":13498}}"""
        val house = read("""{"location":"rimmington","rooms":[$garden,$hall,$top]}""")
        assertEquals(mapOf(15381 to 13498), assertNotNull(house.room(1, 2, House.UPPER)).built.toMap())
        assertTrue(Stairs.connected(house, assertNotNull(house.room(1, 2))))
        assertEquals(house.room(1, 2, House.UPPER), Stairs.otherEnd(house, assertNotNull(house.room(1, 2))))
        // Turned another way, the top is no longer the staircase's.
        val turned = read("""{"location":"rimmington","rooms":[$garden,$hall,${top.replace("\"rotation\":0", "\"rotation\":1")}]}""")
        assertTrue(!Stairs.connected(turned, assertNotNull(turned.room(1, 2))))
        // A parlour over the garden is fine; over nothing it is refused.
        read("""{"location":"rimmington","rooms":[$garden,{"type":"parlour","x":1,"z":1,"floor":2}]}""")
        assertFailsWith<IllegalArgumentException> { read("""{"location":"rimmington","rooms":[$garden,{"type":"parlour","x":3,"z":3,"floor":2}]}""") }
        // A plain hall upstairs, a staircase's top downstairs, a garden upstairs and any room in the dungeon: refused.
        for (bad in listOf(
            """{"type":"skill_hall","x":1,"z":1,"floor":2}""",
            """{"type":"quest_hall_stairs","x":1,"z":2}""",
            """{"type":"garden","x":1,"z":1,"floor":2}""",
            """{"type":"parlour","x":1,"z":2,"floor":0}""",
        )) {
            assertFailsWith<IllegalArgumentException>(bad) { read("""{"location":"rimmington","rooms":[$garden,$bad]}""") }
        }
        // The top on the foot's stair space, or the foot on the top's: dropped.
        val swapped = read("""{"location":"rimmington","rooms":[$garden,{"type":"skill_hall","x":1,"z":2,"built":{"15381":13498}},{"type":"skill_hall_stairs","x":1,"z":2,"floor":2,"built":{"15380":13497}}]}""")
        assertTrue(swapped.rooms.all { it.built.isEmpty() || it.type == RoomType.GARDEN })
    }

    @Test
    fun `a hall upstairs is the hall at a staircase's top, and each room goes on its own floors`() {
        assertEquals(RoomType.SKILL_HALL_STAIRS, RoomType.SKILL_HALL.upstairs)
        assertEquals(RoomType.QUEST_HALL, RoomType.QUEST_HALL_STAIRS.base)
        assertEquals(RoomType.PARLOUR, RoomType.PARLOUR.upstairs)
        assertTrue(RoomType.PARLOUR.goesOn(House.GROUND) && RoomType.PARLOUR.goesOn(House.UPPER))
        assertTrue(!RoomType.GARDEN.goesOn(House.UPPER) && !RoomType.THRONE_ROOM.goesOn(House.UPPER))
        assertTrue(!RoomType.DUNGEON_CORRIDOR.goesOn(House.GROUND) && RoomType.DUNGEON_CORRIDOR.goesOn(House.DUNGEON))
        // Nothing above the upper floor (exploit review round 62, A-4).
        assertTrue(!RoomType.PARLOUR.goesOn(House.UPPER + 1) && !RoomType.SKILL_HALL_STAIRS.goesOn(House.UPPER + 1))
        assertTrue(!RoomType.SKILL_HALL_STAIRS.goesOn(House.GROUND) && !RoomType.SKILL_HALL.goesOn(House.UPPER))
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
