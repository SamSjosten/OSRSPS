package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import osrsps.game.NpcType

/** Rest of Lumbridge step 7: Gillie's and Millie's talks, with no dialogue file, so every line shows its key. */
class GroatsFarmAndMillTest {
    private val h = DialogueHarness()

    @Test
    fun `Gillie says who she is, how to milk a cow, or lets the player go`() {
        fun start() {
            h.talk { with(GroatsFarm) { gillie() } }
            h.expect("gillie_groats.hello")
            h.menu("gillie_groats.who", "gillie_groats.how", "gillie_groats.fine")
        }
        start()
        h.pick(1)
        h.expect("gillie_groats.name", "gillie_groats.spare", "gillie_groats.not_spare", "gillie_groats.thanks")
        h.ends()

        start()
        h.pick(2)
        h.expect(
            "gillie_groats.how_get", "gillie_groats.bucket", "gillie_groats.dairy", "gillie_groats.find",
            "gillie_groats.spot", "gillie_groats.near", "gillie_groats.milk_it",
        )
        h.ends()

        start()
        h.pick(3)
        h.expect("gillie_groats.fine")
        h.ends()
    }

    @Test
    fun `milking with no bucket gets Gillie's talk about it, under her milkmaid name`() {
        h.talk { with(GroatsFarm) { noBucket(NpcType(GroatsFarm.GILLIE, "Gillie Groats")) } }
        h.expect(
            "gillie_groats.tee_hee", "gillie_groats.how_tell", "gillie_groats.spilling", "gillie_groats.guessed",
            "gillie_groats.city", "gillie_groats.do_that",
        )
        h.ends()
    }

    @Test
    fun `Millie's first three options all end in how to mill flour`() {
        val flour = arrayOf(
            "millie_miller.making", "millie_miller.field", "millie_miller.bring", "millie_miller.any_mill", "millie_miller.hopper",
            "millie_miller.where", "millie_miller.appears", "millie_miller.thats_it", "millie_miller.great",
        )
        fun start() {
            h.talk { with(MillLaneMill) { millie() } }
            h.expect("millie_miller.welcome")
            h.menu("millie_miller.who", "millie_miller.place", "millie_miller.how", "millie_miller.fine")
        }
        start()
        h.pick(1)
        h.expect("millie_miller.who", "millie_miller.i_am", "millie_miller.good_business", "millie_miller.how", *flour)
        h.ends()

        start()
        h.pick(2)
        h.expect("millie_miller.place", "millie_miller.this_is", "millie_miller.we_take", "millie_miller.how", *flour)
        h.ends()

        start()
        h.pick(3)
        h.expect("millie_miller.how", *flour)
        h.ends()

        start()
        h.pick(4)
        h.expect("millie_miller.fine")
        h.ends()
    }

    @Test
    fun `the farm's and the mill's NPCs and locs have examines, each wheat and ladder sharing one`() {
        for (id in listOf(GroatsFarm.GILLIE, MillLaneMill.MILLIE)) assertNotNull(Examines.npc(id), "NPC $id")
        for (id in Crops.WHEAT_LOCS) assertEquals("examine.loc_313", Examines.loc(id)?.key, "wheat $id")
        for (id in GroatsFarm.DAIRY_COWS) assertEquals("examine.loc_8689", Examines.loc(id)?.key, "dairy cow $id")
        for (id in listOf(MillLaneMill.LADDER_BOTTOM, MillLaneMill.LADDER_MIDDLE, MillLaneMill.LADDER_TOP)) {
            assertEquals("examine.loc_12964", Examines.loc(id)?.key, "ladder $id")
        }
        for (id in listOf(Crops.POTATO_LOC, MillLaneMill.HOPPER, MillLaneMill.CONTROLS, 5792, 1782, 1778)) assertNotNull(Examines.loc(id), "loc $id")
    }
}
