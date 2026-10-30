package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GrandExchangeCommandsTest {
    @Test
    fun `prices read with k, m and b, past the int range`() {
        assertEquals(500L, parsePrice("500"))
        assertEquals(10_000L, parsePrice("10k"))
        assertEquals(2_500_000L, parsePrice("2.5m"))
        assertEquals(3_000_000_000L, parsePrice("3b"))
        assertEquals(2_149_631_130_647L, parsePrice("2149631130647"))
        assertNull(parsePrice("0"))
        assertNull(parsePrice("1.5"))
        assertNull(parsePrice("0.0001k"))
        assertNull(parsePrice("lots"))
        // Past the Long range, refused rather than wrapped round to 1,000.
        assertNull(parsePrice("18446744073709551617k"))
        assertNull(parsePrice("-18446744073709551615k"))
        assertNull(parsePrice("-5k"))
        // No exponents: expanding one this size would hold the game thread.
        assertNull(parsePrice("1e100000000k"))
        assertNull(parsePrice("1E3k"))
    }
}
