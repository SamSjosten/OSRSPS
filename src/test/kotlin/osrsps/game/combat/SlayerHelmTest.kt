package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.skill.Slayer

class SlayerHelmTest {
    @Test
    fun `masks and helmets are known by their cache names, the imbued ones by their (i)`() {
        for (name in listOf("Black mask", "Black mask (10)", "Slayer helmet", "Hydra slayer helmet", "Black slayer helmet")) assertEquals(SlayerHelm.MELEE, Slayer.helmOf(name), name)
        for (name in listOf("Black mask (i)", "Black mask (3) (i)", "Slayer helmet (i)", "Twisted slayer helmet (i)")) assertEquals(SlayerHelm.IMBUED, Slayer.helmOf(name), name)
        for (name in listOf("Black med helm", "Rune full helm", "Spiny helmet", "Nose peg", null)) assertEquals(SlayerHelm.NONE, Slayer.helmOf(name), name)
    }

    @Test
    fun `melee gets 7 sixths and the imbued mask 15% for ranged and magic, rounded down`() {
        assertEquals(1166, SlayerHelm.MELEE.melee(1000))
        assertEquals(1166, SlayerHelm.IMBUED.melee(1000))
        assertEquals(1000, SlayerHelm.NONE.melee(1000))
        assertEquals(1000, SlayerHelm.MELEE.rangedOrMagic(1000))
        assertEquals(1150, SlayerHelm.IMBUED.rangedOrMagic(1000))
        assertEquals(34, SlayerHelm.IMBUED.rangedOrMagic(30))
    }
}
