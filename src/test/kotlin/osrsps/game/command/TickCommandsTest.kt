package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.Player
import osrsps.game.World
import osrsps.map.CollisionMap

class TickCommandsTest {
    private val world = World(IntArray(0), CollisionMap().apply { markLoaded(50, 50) })
    private val player = Player(1, "test", 3222, 3218, 0)
    private val replies = mutableListOf<String>()

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    @Test
    fun `pause, step and resume drive the clock`() {
        run("pause")
        assertTrue(world.clock.paused)
        run("step")
        assertEquals(1, world.clock.stepsLeft)
        run("step 3")
        assertEquals(4, world.clock.stepsLeft)
        run("resume")
        assertFalse(world.clock.paused)
        assertEquals(
            listOf(
                "Paused after tick 0. ::step [n] runs n ticks, ::resume starts the clock again",
                "Stepping 1 tick",
                "Stepping 3 ticks (4 to go)",
                "Resumed after tick 0",
            ),
            replies,
        )
    }

    @Test
    fun `pause while stepping stops the steps`() {
        run("pause")
        run("step 5")
        run("pause")
        run("pause")
        assertEquals(0, world.clock.stepsLeft)
        assertEquals(
            listOf(
                "Stopped stepping with 5 ticks left; paused after tick 0",
                "Already paused after tick 0",
            ),
            replies.drop(2),
        )
    }

    @Test
    fun `step and resume refuse a running clock, and step refuses bad counts`() {
        run("step")
        run("resume")
        run("pause")
        replies.removeLast()
        run("step 0")
        run("step 1001")
        run("step x")
        run("step 1 2")
        assertEquals(0, world.clock.stepsLeft)
        assertEquals(
            listOf(
                "The clock is running: ::pause first",
                "Not paused (tick 0)",
                "n must be 1 to 1000",
                "n must be 1 to 1000",
                "Usage: ::step [n] ('x' is not a number)",
                "Usage: ::step [n]",
            ),
            replies,
        )
    }

    @Test
    fun `tick says when the clock is paused`() {
        run("pause")
        run("tick")
        assertEquals("Tick 0 (paused)", replies.last())
    }

    @Test
    fun `a click outside a tick is held until the next tick's input`() {
        world.onMoveClick(player, 3225, 3218, keyCombination = 0)
        assertTrue(player.waypoints.isEmpty())
        assertEquals(1, player.heldActions.size)
        world.runHeldActions(player)
        assertFalse(player.waypoints.isEmpty())
        assertTrue(player.heldActions.isEmpty())
    }

    @Test
    fun `held clicks are capped`() {
        repeat(World.MAX_HELD_ACTIONS + 5) { world.onMoveClick(player, 3225, 3218, keyCombination = 0) }
        assertEquals(World.MAX_HELD_ACTIONS, player.heldActions.size)
    }
}
