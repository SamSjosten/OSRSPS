package osrsps.game.raid

import kotlin.test.Test
import kotlin.test.assertEquals

class ArchiveClockTest {
    @Test
    fun `a run's times read as the game's clock, in whole seconds of 0,6 a tick`() {
        assertEquals("0:00", ArchiveRaids.clock(0))
        assertEquals("0:00", ArchiveRaids.clock(1))
        assertEquals("0:01", ArchiveRaids.clock(2))
        // Sam's recording of 16 November 2026: the Guardian's 2:14, Ocellus's 9:46 and the raid's 22:14.
        assertEquals("2:14", ArchiveRaids.clock(224))
        assertEquals("9:46", ArchiveRaids.clock(977))
        assertEquals("22:14", ArchiveRaids.clock(2224))
        assertEquals("1:00:00", ArchiveRaids.clock(6000))
    }
}
