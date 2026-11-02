package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Edgeville step 6: the real multi-way areas, read from multi-combat.tsv, with the admin's `::multi` zones on top. */
class MultiCombatTest {
    private val multi = MultiCombat.load()

    @Test
    fun `the whole of Barbarian Village is multi-way and its edges are exact`() {
        for ((x, z) in listOf(3072 to 3392, 3135 to 3455, 3082 to 3420, 3113 to 3413, 3077 to 3439)) {
            assertTrue(multi.at(0, x, z), "$x, $z")
        }
        for ((x, z) in listOf(3071 to 3420, 3136 to 3420, 3100 to 3391, 3100 to 3456)) {
            assertFalse(multi.at(0, x, z), "$x, $z")
        }
        assertFalse(multi.at(1, 3096, 3432), "the box is ground level only")
    }

    @Test
    fun `Edgeville, Lumbridge and the Monastery stay single-way, and the Stronghold's first floor is multi-way`() {
        assertFalse(multi.at(0, 3094, 3491), "Edgeville bank")
        assertFalse(multi.at(0, 3222, 3218), "Lumbridge")
        assertFalse(multi.at(0, 3052, 3490), "the Monastery")
        assertTrue(multi.at(0, 1860, 5244), "the Stronghold's entrance room")
        assertFalse(multi.at(0, 1920, 5244), "east of the first floor's map square")
    }

    @Test
    fun `an admin's zone adds to the areas and takes nothing from them`() {
        val combat = MultiCombat(MultiCombat.parse("barbarian_village\t0\t3072\t3392\t3135\t3455\n"))
        assertFalse(combat.at(0, 3222, 3218))
        combat.set(0, 3222, 3218, true)
        assertTrue(combat.at(0, 3223, 3219), "the same 8x8 zone")
        assertFalse(combat.at(0, 3230, 3218), "the next zone")
        combat.set(0, 3080, 3400, false)
        assertTrue(combat.at(0, 3080, 3400), "the file's areas stay")
    }

    @Test
    fun `the file reader skips comments and headers and refuses a bad line`() {
        val areas = MultiCombat.parse("# a comment\nname\tlevel\tmin_x\tmin_z\tmax_x\tmax_z\nbox\t1\t10\t20\t30\t40 # where from\n")
        assertEquals(listOf(MultiArea("box", 1, 10, 20, 30, 40)), areas)
        assertFailsWith<IllegalArgumentException> { MultiCombat.parse("box\t0\t10\t20\t30\n") }
        assertFailsWith<IllegalArgumentException> { MultiCombat.parse("box\t0\t30\t20\t10\t40\n") }
        assertFailsWith<IllegalArgumentException> { MultiCombat.parse("box\t4\t10\t20\t30\t40\n") }
    }
}
