package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap

class MovementTest {
    private val map = CollisionMap().apply { markLoaded(3200 shr 6, 3200 shr 6) }
    private val movement = Movement(map)

    private fun player() = Player(1, "test", 3210, 3210, 0)

    @Test
    fun `walking moves one tile a tick`() {
        val player = player()
        movement.request(player, 3213, 3210, invertRun = false)
        val seen = (1..4).map { movement.process(player); player.x }
        assertEquals(listOf(3211, 3212, 3213, 3213), seen)
    }

    @Test
    fun `running moves two tiles a tick and walks the odd last tile`() {
        val player = player().apply { runEnabled = true }
        movement.request(player, 3215, 3210, invertRun = false)
        assertEquals(MoveResult.RAN, movement.process(player))
        assertEquals(MoveResult.RAN, movement.process(player))
        assertEquals(MoveResult.WALKED, movement.process(player))
        assertEquals(3215, player.x)
        assertEquals(MoveResult.NONE, movement.process(player))
    }

    @Test
    fun `ctrl-click inverts run for that route`() {
        val player = player()
        movement.request(player, 3214, 3210, invertRun = true)
        assertEquals(MoveResult.RAN, movement.process(player))
        movement.request(player, 3210, 3210, invertRun = false)
        assertEquals(MoveResult.WALKED, movement.process(player))
    }

    @Test
    fun `running drains energy at the wiki rate and standing restores it`() {
        val player = player().apply { runEnabled = true; runEnergy = 5000 }
        movement.request(player, 3220, 3210, invertRun = false)
        movement.process(player)
        // ⌊60 + 0⌋ × (1 − 1/300) = 59.8, rounded down.
        assertEquals(5000 - 59, player.runEnergy)
        player.waypoints.clear()
        movement.process(player)
        // ⌊1 / 10⌋ + 15
        assertEquals(5000 - 59 + 15, player.runEnergy)
    }

    @Test
    fun `weight speeds up the drain, up to twice at 64 kg`() {
        fun drainAt(kg: Int): Int {
            val player = player().apply { runEnabled = true; runEnergy = 5000; weight = kg }
            movement.request(player, 3220, 3210, invertRun = false)
            movement.process(player)
            return 5000 - player.runEnergy
        }
        // ⌊60 + 67 × weight / 64⌋ × (1 − 1/300), rounded down.
        assertEquals(59, drainAt(-27))
        assertEquals(92, drainAt(32))
        assertEquals(126, drainAt(64))
        assertEquals(126, drainAt(200))
    }

    @Test
    fun `run turns off when energy runs out`() {
        val player = player().apply { runEnabled = true; runEnergy = 30 }
        movement.request(player, 3220, 3210, invertRun = false)
        assertEquals(MoveResult.RAN, movement.process(player))
        assertEquals(0, player.runEnergy)
        assertFalse(player.runEnabled)
        assertEquals(MoveResult.WALKED, movement.process(player))
    }

    @Test
    fun `energy never goes above 100 percent`() {
        val player = player().apply { runEnergy = Movement.MAX_ENERGY - 5 }
        movement.process(player)
        assertEquals(Movement.MAX_ENERGY, player.runEnergy)
    }

    @Test
    fun `a step into a loc is refused`() {
        map.add(3211, 3211, 0, CollisionFlag.LOC)
        assertFalse(movement.canStep(0, 3210, 3210, 1, 1))
        assertTrue(movement.canStep(0, 3210, 3210, 1, 0))
        map.add(3211, 3210, 0, CollisionFlag.WALL_WEST)
        assertFalse(movement.canStep(0, 3210, 3210, 1, 0))
    }
}
