package osrsps.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.Skill
import osrsps.game.skill.construction.FurnitureOption

class FurnitureLoaderTest {
    // Shaped as osrs-dumps' rev 242 dump.dbrow has them: the parlour (room type 1) and its first two hotspots, the
    // armchair hotspot with two chairs and a hidden third piece, and the rug hotspot with a rug needing two stats.
    private fun furniture(name: String, obj: Int, materials: List<Int>, levels: List<Int>, hidden: Int? = null) = DbRow(
        FurnitureLoader.FURNITURE_TABLE,
        listOfNotNull(0 to listOf<Any>(obj), 1 to listOf<Any>(name), 2 to materials, 3 to levels, hidden?.let { 5 to listOf<Any>(it) }).toMap(),
    )

    private val data = FurnitureLoader.build(
        mapOf(
            5517 to furniture("Crude wooden chair", 8309, listOf(960, 2, 32923, 2), listOf(Skill.CONSTRUCTION.id, 1)),
            5518 to furniture("Wooden chair", 8310, listOf(960, 3, 32923, 3), listOf(Skill.CONSTRUCTION.id, 8)),
            5600 to furniture("Hidden chair", 8311, listOf(960, 1), listOf(Skill.CONSTRUCTION.id, 1), hidden = 1),
            5524 to furniture("Brown rug", 8316, listOf(8790, 2), listOf(Skill.CRAFTING.id, 5, Skill.CONSTRUCTION.id, 2)),
        ),
        mapOf(
            4166 to DbRow(FurnitureLoader.ROOM_TABLE, mapOf(3 to listOf(1), 7 to listOf(6030, 6030, 6031))),
            4165 to DbRow(FurnitureLoader.ROOM_TABLE, mapOf(3 to listOf(-1))),
        ),
        mapOf(
            6030 to DbRow(FurnitureLoader.HOTSPOT_TABLE, mapOf(0 to listOf(5517, 5518, 5600))),
            6031 to DbRow(FurnitureLoader.HOTSPOT_TABLE, mapOf(0 to listOf(5524))),
        ),
    )

    @Test
    fun `each room type's hotspots list their furniture in order, with names, icons, materials and Construction levels`() {
        assertEquals(setOf(1), data.rooms.keys)
        val chairs = data.options(1, 0)
        assertEquals(
            listOf(
                FurnitureOption(5517, "Crude wooden chair", 8309, listOf(960 to 2, 32923 to 2), 1),
                FurnitureOption(5518, "Wooden chair", 8310, listOf(960 to 3, 32923 to 3), 8),
                FurnitureOption(5600, "Hidden chair", 8311, listOf(960 to 1), 1, hidden = true),
            ),
            chairs,
        )
        assertEquals(chairs, data.options(1, 1))
        assertEquals(listOf(2), data.options(1, 2).map { it.level })
        assertEquals(emptyList(), data.options(1, 3))
        assertEquals(emptyList(), data.options(2, 0))
    }
}
