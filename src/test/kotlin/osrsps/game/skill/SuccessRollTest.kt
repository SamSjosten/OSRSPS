package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom

/**
 * The success formula against points read off the OSRS Wiki's charts (2026-10-29), each an exact 256th. The pairs are
 * the ones worked back from those charts for the plan: a regular tree with a bronze axe, an oak with bronze and iron, a
 * willow with bronze and iron, and shrimps on a fire.
 */
class SuccessRollTest {
    @Test
    fun `a regular tree with a bronze axe is 25,4 percent at level 1 and 78,5 at 99`() {
        assertEquals(65, SuccessRoll.chance(64, 200, 1))
        assertEquals(201, SuccessRoll.chance(64, 200, 99))
    }

    @Test
    fun `an oak is half a regular tree`() {
        assertEquals(43, SuccessRoll.chance(32, 100, 15)) // 16.8%
        assertEquals(101, SuccessRoll.chance(32, 100, 99)) // 39.5%
        assertEquals(64, SuccessRoll.chance(48, 150, 15)) // iron, 25.0%
        assertEquals(151, SuccessRoll.chance(48, 150, 99)) // iron, 59.0%
    }

    @Test
    fun `a willow matches the Wiki's level 30 points`() {
        assertEquals(27, SuccessRoll.chance(16, 50, 30)) // 10.55%
        assertEquals(51, SuccessRoll.chance(16, 50, 99)) // 19.92%
        assertEquals(40, SuccessRoll.chance(24, 75, 30)) // iron, 15.63%
    }

    @Test
    fun `shrimps on a fire stop burning at 34`() {
        assertEquals(129, SuccessRoll.chance(128, 512, 1)) // 50.39%
        assertEquals(164, SuccessRoll.chance(128, 512, 10)) // 64.06%
        assertEquals(203, SuccessRoll.chance(128, 512, 20)) // 79.30%
        assertEquals(243, SuccessRoll.chance(128, 512, 30)) // 94.92%
        assertEquals(254, SuccessRoll.chance(128, 512, 33))
        assertEquals(256, SuccessRoll.chance(128, 512, 34))
    }

    @Test
    fun `the chance is clamped to 0 to 256`() {
        assertEquals(256, SuccessRoll.chance(255, 1000, 99))
        assertEquals(256, SuccessRoll.chance(64, 200, 200))
        assertEquals(1, SuccessRoll.chance(0, 0, 50))
    }

    @Test
    fun `a sure roll always succeeds and the same seed rolls the same way`() {
        val random = GameRandom(1)
        assertTrue((1..100).all { SuccessRoll.roll(random, 128, 512, 34) })
        val a = GameRandom(42)
        val b = GameRandom(42)
        assertEquals((1..50).map { SuccessRoll.roll(a, 64, 200, 1) }, (1..50).map { SuccessRoll.roll(b, 64, 200, 1) })
    }
}
