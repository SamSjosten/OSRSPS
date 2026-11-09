package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LumbridgeSwampCavesTest {
    @Test
    fun `the dark covers the rope's tile, both sides of each stepping stone and all the gas, on level 0 only`() = with(LumbridgeSwampCaves) {
        for ((x, z) in listOf(3168 to 9572, 3204 to 9572, 3208 to 9572, 3221 to 9556, 3221 to 9552)) assertTrue(isDark(x, z, 0), "$x, $z")
        assertFalse(isDark(3168, 9572, 1))
        // The cellar and the H.A.M. Hideout are lit: neither joins the caves on the map.
        assertFalse(isDark(3209, 9616, 0))
        assertFalse(isDark(3150, 9640, 0))
        assertEquals(2011 + 159, DARK.size)
        assertTrue(GAS.isNotEmpty() && DARK.containsAll(GAS))
        assertTrue(isGas(3160, 9590, 0) && isGas(3208, 9556, 0))
        assertFalse(isGas(3188, 9565, 0))
    }

    @Test
    fun `rows read as runs, the escape range and the gas damage`() = with(LumbridgeSwampCaves) {
        assertEquals(5, parse("# a comment\n9572 3168-3170 3172-3173\n").size)
        assertTrue(nearRope(3172, 9569) && !nearRope(3173, 9572))
        assertEquals(1, gasDamage(3))
        assertEquals(2, gasDamage(10))
        assertEquals(24, gasDamage(99))
    }
}
