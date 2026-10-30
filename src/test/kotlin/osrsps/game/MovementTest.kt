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

    private fun settingsPlayer(mode: Int) = Player(1, "test", 3210, 3210, 0, VarTypes(6_000, AllSettings.VARBITS)).apply {
        vars.setVarbit(Movement.VARBIT_RUN_INVERT_MODE, mode)
    }

    /** What one ctrl-click route does to a player walking ([run] false) or running, under [mode]. */
    private fun ctrlClick(mode: Int, run: Boolean): MoveResult {
        val player = settingsPlayer(mode).apply { runEnabled = run }
        movement.request(player, 3214, 3210, invertRun = true)
        return movement.process(player)
    }

    @Test
    fun `the ctrl-click setting decides which way ctrl flips the run mode`() {
        assertEquals(MoveResult.WALKED, ctrlClick(Movement.RUN_INVERT_NEVER, run = false))
        assertEquals(MoveResult.RAN, ctrlClick(Movement.RUN_INVERT_NEVER, run = true))
        assertEquals(MoveResult.RAN, ctrlClick(Movement.RUN_INVERT_WALK_TO_RUN, run = false))
        assertEquals(MoveResult.RAN, ctrlClick(Movement.RUN_INVERT_WALK_TO_RUN, run = true))
        assertEquals(MoveResult.WALKED, ctrlClick(Movement.RUN_INVERT_RUN_TO_WALK, run = false))
        assertEquals(MoveResult.WALKED, ctrlClick(Movement.RUN_INVERT_RUN_TO_WALK, run = true))
        assertEquals(MoveResult.RAN, ctrlClick(Movement.RUN_INVERT_ALWAYS, run = false))
        assertEquals(MoveResult.WALKED, ctrlClick(Movement.RUN_INVERT_ALWAYS, run = true))
    }

    @Test
    fun `run that ran out comes back on at the energy threshold, and only then`() {
        val player = settingsPlayer(Movement.RUN_INVERT_ALWAYS).apply { runEnabled = true; runEnergy = 50 }
        player.vars.setVarbit(Movement.VARBIT_RUN_AUTOENABLE, 1)
        movement.request(player, 3220, 3210, invertRun = false)
        movement.process(player)
        assertEquals(0, player.runEnergy)
        assertFalse(player.runEnabled)
        player.waypoints.clear()
        // 1% is 100 units; standing restores 15 a tick, so 7 ticks reach 105.
        repeat(6) { movement.process(player) }
        assertFalse(player.runEnabled, "on before the threshold")
        movement.process(player)
        assertTrue(player.runEnabled)
    }

    @Test
    fun `run turned off by hand stays off, and 0 never turns it back on`() {
        val byHand = settingsPlayer(Movement.RUN_INVERT_ALWAYS).apply { runEnergy = 0 }
        byHand.vars.setVarbit(Movement.VARBIT_RUN_AUTOENABLE, 1)
        repeat(20) { movement.process(byHand) }
        assertFalse(byHand.runEnabled)

        val off = settingsPlayer(Movement.RUN_INVERT_ALWAYS).apply { runEnabled = true; runEnergy = 50 }
        movement.request(off, 3220, 3210, invertRun = false)
        movement.process(off)
        off.waypoints.clear()
        repeat(700) { movement.process(off) }
        assertEquals(Movement.MAX_ENERGY, off.runEnergy)
        assertFalse(off.runEnabled)
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
        fun drainAt(grams: Int): Int {
            val player = player().apply { runEnabled = true; runEnergy = 5000; weightGrams = grams }
            movement.request(player, 3220, 3210, invertRun = false)
            movement.process(player)
            return 5000 - player.runEnergy
        }
        // ⌊(60 + 67 × kg / 64) × (1 − 1/300)⌋, the fractions of a kilogram counting.
        assertEquals(59, drainAt(-27_605))
        assertEquals(69, drainAt(9_000))
        assertEquals(70, drainAt(9_979), "a rune platebody: 70.25, not 9 kg's 69.2")
        assertEquals(93, drainAt(32_000))
        assertEquals(126, drainAt(64_000))
        assertEquals(126, drainAt(200_000))
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
    fun `run turned on with no energy goes off, and that standing tick still restores energy`() {
        val player = player().apply { runEnabled = true; runEnergy = 0 }
        assertEquals(MoveResult.NONE, movement.process(player))
        assertFalse(player.runEnabled)
        assertTrue(player.runRanOut)
        assertEquals(15, player.runEnergy)
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
