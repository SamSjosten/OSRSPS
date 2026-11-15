package osrsps.game.skill.hunter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.skill.SuccessRoll

/** Hunter step 2's data against the Wiki: levels, XP, Mod Ash's catch chances and the struggling locs by side. */
class BoxTrapsTest {
    private fun creature(npc: Int) = BoxTraps.CREATURES.first { it.npc == npc }

    @Test
    fun `levels and XP are the Box trap page's`() {
        assertEquals(listOf(27 to 1152, 53 to 1984, 63 to 2650), BoxTraps.CREATURES.map { it.level to it.xpTenths })
    }

    @Test
    fun `chinchompas are caught at Mod Ash's chances`() {
        val grey = creature(BoxTraps.CHINCHOMPA)
        val red = creature(BoxTraps.CARNIVOROUS_CHINCHOMPA)
        // "Approximately 145/255 beginning at level 53, and 268/255 at level 99" (greys); "115/255 at 63" (reds).
        assertTrue(SuccessRoll.chance(grey.low, grey.high, 53) in 143..147)
        assertEquals(256, SuccessRoll.chance(grey.low, grey.high, 99))
        assertTrue(SuccessRoll.chance(red.low, red.high, 63) in 113..118)
        assertTrue(SuccessRoll.chance(red.low, red.high, 99) in 227..231)
    }

    @Test
    fun `a creature struggles on the side it stepped in from`() {
        val grey = creature(BoxTraps.CHINCHOMPA)
        assertEquals(listOf(9386, 9387, 9388, 9389), listOf(grey.catchingLoc(0, 1), grey.catchingLoc(1, 0), grey.catchingLoc(0, -1), grey.catchingLoc(-1, 0)))
        // A diagonal step counts as its north or south side; a bird snare has one struggling loc whatever the side.
        assertEquals(9386, grey.catchingLoc(1, 1))
        assertEquals(9349, BirdSnares.BIRDS.first().catchingLoc(1, 0))
        assertTrue(BoxTraps.CREATURES.flatMap { it.catchingFrom + it.full }.all(BoxTraps.KIND::owns))
    }
}
