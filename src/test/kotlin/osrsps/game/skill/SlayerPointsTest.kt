package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals

/** The OSRS Wiki's Slayer reward point table (read 2026-11-15), Vannaka's row: 8, 40, 120, 200, 280, 400. */
class SlayerPointsTest {
    @Test
    fun `no points for the first four tasks`() {
        for (streak in 1..4) assertEquals(0, Slayer.pointsFor(SlayerMaster.VANNAKA.points, streak))
    }

    @Test
    fun `Vannaka's row`() {
        val base = SlayerMaster.VANNAKA.points
        assertEquals(8, Slayer.pointsFor(base, 5))
        assertEquals(8, Slayer.pointsFor(base, 11))
        assertEquals(40, Slayer.pointsFor(base, 10))
        assertEquals(120, Slayer.pointsFor(base, 50))
        assertEquals(200, Slayer.pointsFor(base, 100))
        assertEquals(280, Slayer.pointsFor(base, 250))
        assertEquals(400, Slayer.pointsFor(base, 1000))
        // 300 is a 100th, not a 250th; 500 is both, and the bigger wins.
        assertEquals(200, Slayer.pointsFor(base, 300))
        assertEquals(280, Slayer.pointsFor(base, 500))
    }

    @Test
    fun `Aya and Spria pay nothing, Konar 18 a task`() {
        assertEquals(0, Slayer.pointsFor(SlayerMaster.AYA.points, 10))
        assertEquals(0, Slayer.pointsFor(SlayerMaster.SPRIA.points, 1000))
        assertEquals(18, Slayer.pointsFor(SlayerMaster.KONAR.points, 7))
        assertEquals(900, Slayer.pointsFor(SlayerMaster.KONAR.points, 2000))
    }
}
