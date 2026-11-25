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
}
