package osrsps.game.skill.construction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Construction steps CON3 and CON4: the hand table of hotspots and built furniture hangs together. */
class FurnitureBuildsTest {
    @Test
    fun `a rug's three spaces are siblings, built and removed together, each with its own part`() {
        assertEquals(setOf(4518, 4519, 4520), FurnitureBuilds.siblings(4519))
        val brown = FurnitureBuilds.BUILDS.getValue(5524)
        assertEquals(listOf(6759, 6760, 6761), listOf("corner", "side", "middle").map(brown::locFor))
        // A chair space stands alone, and a piece with no variants builds as itself anywhere.
        assertEquals(setOf(FurnitureBuilds.CHAIR_SPACE_1), FurnitureBuilds.siblings(FurnitureBuilds.CHAIR_SPACE_1))
        assertEquals(6752, FurnitureBuilds.BUILDS.getValue(5517).locFor("corner"))
    }

    @Test
    fun `each hotspot's place lists its pieces and each listed place has a hotspot, and built locs never double as hotspots`() {
        // Places keep the cache's numbering, which can skip one (the combat room has nothing at places 1 and 2).
        val places = FurnitureBuilds.HOTSPOTS.values.map { it.room to it.index }.toSet()
        assertEquals(FurnitureBuilds.PLACES.keys, places)
        assertTrue(FurnitureBuilds.BUILT_LOCS.none { it in FurnitureBuilds.HOTSPOTS.keys })
    }

    @Test
    fun `the crested wall decorations have no build until crests exist, and the kitchen's supplies are on built locs`() {
        assertTrue(FurnitureBuilds.NEEDS_CREST.none { it in FurnitureBuilds.BUILDS })
        assertTrue(Kitchen.SUPPLIES.keys.all { it in FurnitureBuilds.BUILT_LOCS })
    }

    @Test
    fun `a tool store racks its first spaces and leaves the rest bare, and the built row reads back from them`() {
        val spaces = listOf(15443, 15444, 15445, 15446, 15447)
        assertEquals(spaces.toSet(), FurnitureBuilds.siblings(15445))
        val second = FurnitureBuilds.BUILDS.getValue(5759)
        assertEquals(listOf(6786, 6787, -1, -1, -1), listOf("a", "b", "c", "d", "e").map(second::locFor))
        assertTrue(FurnitureBuilds.buildsOn(15444, 6787))
        assertTrue(!FurnitureBuilds.buildsOn(15443, 6787))
        assertTrue(!FurnitureBuilds.buildsOn(15445, FurnitureBuilds.NONE))
        val built = mapOf(15443 to 6786, 15444 to 6787)
        assertEquals(5759, FurnitureBuilds.builtRow(built, 15447))
        assertEquals(5758, FurnitureBuilds.builtRow(mapOf(15443 to 6786), 15443))
        assertEquals(null, FurnitureBuilds.builtRow(emptyMap(), 15443))
        // Parts of two stores together are no store.
        assertEquals(null, FurnitureBuilds.builtRow(mapOf(15444 to 6787), 15443))
        assertTrue(FurnitureBuilds.NONE !in FurnitureBuilds.BUILT_LOCS)
        // Any tool space builds any store, its racks on the first spaces; a row from another place never.
        assertTrue(FurnitureBuilds.buildsAt(15447, 5758) && FurnitureBuilds.buildsAt(15443, 5762))
        assertTrue(!FurnitureBuilds.buildsAt(15443, 5749) && FurnitureBuilds.buildsAt(15439, 5749))
    }

    @Test
    fun `a bench and its stool are built together, and every Upgrade op and tool store is on a built loc`() {
        assertEquals(setOf(15439, 15440), FurnitureBuilds.siblings(15439))
        assertEquals(5751, FurnitureBuilds.builtRow(mapOf(15439 to 6793, 15440 to 6806), 15440))
        assertTrue(FurnitureBuilds.UPGRADABLE.all { it in FurnitureBuilds.BUILT_LOCS })
        assertTrue(Workshop.TOOLS.keys.all { it in FurnitureBuilds.BUILT_LOCS })
    }

    @Test
    fun `a garden tree builds as the big tree on the big tree space, and the trees and plants give Farming XP too`() {
        val magic = FurnitureBuilds.BUILDS.getValue(5886)
        assertEquals(listOf(5127, 4537), listOf(null, "big").map(magic::locFor))
        assertTrue(FurnitureBuilds.buildsOn(15362, 4537) && FurnitureBuilds.buildsOn(15363, 5127))
        assertEquals(223, magic.farmingXp)
        assertTrue(FurnitureBuilds.PLANTED.all { FurnitureBuilds.BUILDS.getValue(it).farmingXp > 0 })
        assertTrue(FurnitureBuilds.buildsOn(House.CENTREPIECE_SPACE, House.EXIT_PORTAL))
    }

    @Test
    fun `a hall's stair space is a staircase's foot or its top, and a rug leaves it bare`() {
        assertEquals(setOf(15377, 15378, 15379, 15380), FurnitureBuilds.siblings(15377, RoomType.SKILL_HALL))
        assertEquals(setOf(15377, 15378, 15379, 15381), FurnitureBuilds.siblings(15377, RoomType.SKILL_HALL_STAIRS))
        assertEquals(setOf(15387, 15388, 15389, 15391), FurnitureBuilds.siblings(15391))
        val oak = FurnitureBuilds.BUILDS.getValue(5644)
        assertEquals(listOf(13497, 13498, FurnitureBuilds.NONE), listOf("stairs", "top", "middle").map(oak::locFor))
        assertTrue(FurnitureBuilds.buildsAt(15377, 5644, RoomType.SKILL_HALL) && FurnitureBuilds.buildsAt(15391, 5654))
        assertEquals(5644, FurnitureBuilds.builtRow(mapOf(15380 to 13497), 15380, RoomType.SKILL_HALL))
        assertEquals(5644, FurnitureBuilds.builtRow(mapOf(15381 to 13498), 15377, RoomType.SKILL_HALL_STAIRS))
        // The rug: its corners, sides and middle, the stair space bare.
        assertEquals(5525, FurnitureBuilds.builtRow(mapOf(15377 to 6764, 15378 to 6763, 15379 to 6762), 15380, RoomType.SKILL_HALL))
        assertTrue(!FurnitureBuilds.buildsOn(15380, 6764))
        assertTrue(FurnitureBuilds.STAIRCASES.all { it in FurnitureBuilds.PLACES.getValue(RoomType.QUEST_HALL to 0) })
    }

    @Test
    fun `each combat ring space piece reads back as itself, and the racks, ropes and barriers are built locs`() {
        val space = (15277..15295).toSet()
        assertEquals(space, FurnitureBuilds.siblings(15292))
        for (row in 5633..5637) {
            val build = FurnitureBuilds.BUILDS.getValue(row)
            val built = space.associateWith { build.locFor(FurnitureBuilds.HOTSPOTS.getValue(it).variant) }.filterValues { it != FurnitureBuilds.NONE }
            assertEquals(row, FurnitureBuilds.builtRow(built, 15277), "row $row")
            assertTrue(FurnitureBuilds.buildsAt(15292, row), "row $row")
        }
        // The pedestals leave the middle bare; the beam leaves the pedestals' walls bare but for the one it shares.
        assertEquals(FurnitureBuilds.NONE, FurnitureBuilds.BUILDS.getValue(5636).locFor("floor_middle"))
        assertEquals(FurnitureBuilds.NONE, FurnitureBuilds.BUILDS.getValue(5637).locFor("pedestal_wall"))
        assertTrue(CombatRoom.RACKS.keys.all { it in FurnitureBuilds.BUILT_LOCS })
        assertTrue((CombatRoom.ROPES + 13145).all { it in FurnitureBuilds.BUILT_LOCS })
        assertTrue(29336 in FurnitureBuilds.UPGRADABLE && 29337 in FurnitureBuilds.UPGRADABLE)
    }
}
