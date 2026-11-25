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
    fun `each room's places are numbered from 0 with no gaps, and built locs never double as hotspots`() {
        for ((room, spots) in FurnitureBuilds.HOTSPOTS.values.groupBy { it.room }) {
            val indices = spots.map { it.index }.toSortedSet()
            assertEquals((0 until indices.size).toSet(), indices, "the ${room.key}'s places")
        }
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
}
