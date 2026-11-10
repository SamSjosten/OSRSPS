package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.map.CollisionMap

class WallBeastsTest {
    private val holeType = NpcType(WallBeasts.HOLE, "Hole in the wall", ops = listOf(null, null, null, null, null), internalName = "swamp_wallbeast",
        stats = NpcStats(hitpoints = 105))
    private val world = World(
        IntArray(0), CollisionMap().apply { markLoaded(3164 shr 6, 9556 shr 6) }, npcTypes = NpcTypes.of(holeType),
    )

    @Test
    fun `a beast goes back in exactly 20 ticks after its fight's last tick, the countdown's own tick counted`() {
        val last = 100L
        // As the world runs it: the countdown set, then one taken off; back in once it reaches 0.
        val back = (last..last + 40).first { now -> world.wallBeasts.ticksLeft(now, last) - 1 <= 0 }
        assertEquals(last + WallBeasts.BACK_TICKS, back)
        // Still fighting: 20 left after this tick's.
        assertEquals(WallBeasts.BACK_TICKS, world.wallBeasts.ticksLeft(last, last) - 1)
    }
}
