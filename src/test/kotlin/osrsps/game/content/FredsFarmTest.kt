package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Rest of Lumbridge step 6: Fred the Farmer's talk, with no dialogue file, so every line shows its key. */
class FredsFarmTest {
    private val h = DialogueHarness()

    @Test
    fun `Fred asks what you're doing on his land, and answers both options`() {
        h.talk { with(FredsFarm) { fred() } }
        h.expect("fred_the_farmer.my_land")
        h.menu("fred_the_farmer.kill", "fred_the_farmer.lost")
        h.pick(1)
        h.expect("fred_the_farmer.kill", "fred_the_farmer.livestock")
        h.ends()

        h.talk { with(FredsFarm) { fred() } }
        h.expect("fred_the_farmer.my_land")
        h.menu("fred_the_farmer.kill", "fred_the_farmer.lost")
        h.pick(2)
        h.expect("fred_the_farmer.lost", "fred_the_farmer.road")
        h.ends()
    }

    @Test
    fun `every sheep shears into a sheep of its own colour, and the farm's NPCs and onions have examines`() {
        // Each woolly type has a sheared type of its own, and every one of them shares the sheep's examine.
        assertEquals(FredsFarm.SHEARED.size, FredsFarm.SHEARED.values.toSet().size)
        for ((woolly, sheared) in FredsFarm.SHEARED) {
            assertEquals("examine.npc_2693", Examines.npc(woolly)?.key, "sheep $woolly")
            assertEquals("examine.npc_1178", Examines.npc(sheared)?.key, "sheared sheep $sheared")
        }
        for (id in listOf(FredsFarm.FRED, 1173, 1174, 1262, 1263, 1264, 1176, 1177)) assertNotNull(Examines.npc(id), "NPC $id")
        assertEquals("examine.loc_${FredsFarm.ONION_LOC}", Examines.loc(FredsFarm.ONION_LOC)?.key)
    }
}
