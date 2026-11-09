package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape
import osrsps.map.Tile

/** Misthalin piece 7 step 1: which tool the shed gives, which bank the raft goes to, and where the shed's door is. */
class LumbridgeSwampTest {
    @Test
    fun `the shed's tools give the one not held, a choice with neither, and nothing with both`() {
        assertEquals(LumbridgeSwamp.SPADE, LumbridgeSwamp.toolFor(hasRake = true, hasSpade = false))
        assertEquals(LumbridgeSwamp.RAKE, LumbridgeSwamp.toolFor(hasRake = false, hasSpade = true))
        assertNull(LumbridgeSwamp.toolFor(hasRake = false, hasSpade = false))
        assertNull(LumbridgeSwamp.toolFor(hasRake = true, hasSpade = true))
    }

    @Test
    fun `the raft goes from either bank's shortcut tile to the other, and from nowhere else`() {
        assertEquals(Tile(3259, 3179), LumbridgeSwamp.raftLanding(Tile(3246, 3179)))
        assertEquals(Tile(3246, 3179), LumbridgeSwamp.raftLanding(Tile(3259, 3179)))
        assertNull(LumbridgeSwamp.raftLanding(Tile(3247, 3179)))
        assertNull(LumbridgeSwamp.raftLanding(Tile(3252, 3179)))
    }

    @Test
    fun `only the swamp's shed door is the shed's, and its own tile is inside`() {
        assertTrue(LumbridgeSwamp.isShedDoor(ZoneLoc(LumbridgeSwamp.SHED_DOOR, LocShape.WALL_STRAIGHT, 0, 3202, 3169, 0)))
        assertFalse(LumbridgeSwamp.isShedDoor(ZoneLoc(LumbridgeSwamp.SHED_DOOR, LocShape.WALL_STRAIGHT, 0, 2452, 4473, 0)))
        assertTrue(LumbridgeSwamp.insideShed(3202))
        assertFalse(LumbridgeSwamp.insideShed(3201))
    }
}
