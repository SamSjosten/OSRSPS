package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.Skill

class TearsOfGuthixTest {
    @Test
    fun `a tear gives min(60, 10 + floor(XP over 27) over 10) XP, as the Wiki's formula`() {
        assertEquals(100, TearsOfGuthix.tenthsPerTear(0), "10 XP at no XP")
        assertEquals(101, TearsOfGuthix.tenthsPerTear(270), "27 XP gives a tenth more")
        assertEquals(100, TearsOfGuthix.tenthsPerTear(260))
        assertEquals(600, TearsOfGuthix.tenthsPerTear(13_500 * 10), "level 30's XP and up: 60")
        assertEquals(600, TearsOfGuthix.tenthsPerTear(2_000_000_000))
    }

    @Test
    fun `the tie order is the Wiki's, every skill once`() {
        assertEquals(Skill.entries.toSet(), TearsOfGuthix.TIE_ORDER.toSet())
        assertEquals(Skill.entries.size, TearsOfGuthix.TIE_ORDER.size)
        assertEquals(listOf(Skill.ATTACK, Skill.STRENGTH, Skill.RANGED, Skill.MAGIC, Skill.DEFENCE), TearsOfGuthix.TIE_ORDER.take(5))
    }

    @Test
    fun `the walls' room holds the nine walls`() {
        for ((x, z) in TearsOfGuthix.WALLS) assertEquals(true, TearsOfGuthix.inRoom(x, z, 2))
        assertEquals(false, TearsOfGuthix.inRoom(TearsOfGuthix.END.x, TearsOfGuthix.END.z, 2), "the end tile is outside")
        assertEquals(true, TearsOfGuthix.inRoom(TearsOfGuthix.START.x, TearsOfGuthix.START.z, 2))
    }
}
