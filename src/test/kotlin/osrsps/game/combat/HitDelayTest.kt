package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals

class HitDelayTest {
    private fun delays(delay: HitDelay) = (1..10).map(delay::ticks)

    @Test
    fun `the Wiki's delays by distance`() {
        assertEquals(List(10) { 0 }, delays(HitDelay.MELEE))
        // 1 + (3 + d) / 6: 1 tick up to 2 tiles, 2 from 3 to 8, 3 from 9.
        assertEquals(listOf(1, 1, 2, 2, 2, 2, 2, 2, 3, 3), delays(HitDelay.BOW))
        // 1 + d / 6: 1 up to 5 tiles, 2 from 6.
        assertEquals(listOf(1, 1, 1, 1, 1, 2, 2, 2, 2, 2), delays(HitDelay.THROWN))
        // 1 + (1 + d) / 3: 1 at 1 tile, 2 from 2 to 4, 3 from 5 to 7, 4 from 8.
        assertEquals(listOf(1, 2, 2, 2, 3, 3, 3, 4, 4, 4), delays(HitDelay.MAGIC))
    }
}
