package osrsps.game.skill.hunter

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.skill.SuccessRoll

/** Hunter step 6a's data against the Wiki: levels, XP, the boost, and the guessed pairs matching the black warlock's chance. */
class ButterfliesTest {
    @Test
    fun `levels and XP are the Butterfly (Hunter) page's`() {
        assertEquals(
            listOf(15 to 240, 25 to 340, 35 to 440, 45 to 540, 65 to 740, 75 to 840),
            Butterflies.ALL.map { it.level to it.xpTenths },
        )
        assertEquals(listOf(10020, 10018, 10016, 10014, 28890, 28893), Butterflies.ALL.map { it.jarred })
    }

    @Test
    fun `the boost is 15 percent plus 4, rounded down`() {
        assertEquals(4, Butterflies.boost(1))
        assertEquals(10, Butterflies.boost(40))
        assertEquals(18, Butterflies.boost(99))
    }

    @Test
    fun `the guessed pairs catch as often at their level as a black warlock at 45`() {
        val warlock = Butterflies.BLACK_WARLOCK
        val target = SuccessRoll.chance(warlock.netPair.first, warlock.netPair.second, warlock.level)
        for (b in listOf(Butterflies.RUBY_HARVEST, Butterflies.SAPPHIRE_GLACIALIS, Butterflies.SNOWY_KNIGHT)) {
            val chance = SuccessRoll.chance(b.netPair.first, b.netPair.second, b.level)
            assert(kotlin.math.abs(chance - target) <= 1) { "${b.level}: $chance against $target" }
        }
        // Sunlight moths: barehanded is no better than the insect net.
        assertEquals(Butterflies.SUNLIGHT_MOTH.netPair, Butterflies.SUNLIGHT_MOTH.barePair)
    }
}
