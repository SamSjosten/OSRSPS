package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.Player
import osrsps.game.World
import osrsps.map.CollisionMap

class ScriptCommandsTest {
    private val world = World(IntArray(0), CollisionMap())
    private val player = Player(1, "test", 3222, 3218, 0)
    private val replies = mutableListOf<String>()

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    // The world's tick stays at 0 here, as the clock never runs; the script phase is run by hand.
    private fun scriptPhase(n: Int = 1) = repeat(n) { world.scripts.process(player) }

    @Test
    fun `queue runs a script that says when it ran`() {
        run("queue strong")
        scriptPhase()
        assertEquals(
            listOf("Queued a strong script with delay 0 after tick 0", "strong script ran on tick 0 (queued after tick 0 with delay 0)"),
            replies,
        )
    }

    @Test
    fun `queue refuses a bad type or delay`() {
        run("queue loud")
        run("queue weak -1")
        run("queue")
        assertEquals(
            listOf(
                "Usage: ::queue weak|normal|strong|soft [delay] ('loud' is not a queue type)",
                "delay must be 0 to 1000",
                "Usage: ::queue weak|normal|strong|soft [delay]",
            ),
            replies,
        )
    }

    @Test
    fun `delay holds the player and scripts shows it`() {
        run("delay 2")
        run("delay")
        run("queue normal 1")
        run("scripts")
        assertEquals(
            listOf(
                "Delayed after tick 0 for 2 ticks",
                "Already delayed until tick 2",
                "Queued a normal script with delay 1 after tick 0",
                "Tick 0: delayed until tick 2, no modal. Queued: dev (normal, delay 1). Timers: none",
            ),
            replies,
        )
    }

    @Test
    fun `timer fires the asked number of times, then clears itself`() {
        run("timer soft 1 2")
        scriptPhase(3)
        run("scripts")
        assertEquals(
            listOf(
                "Set a soft timer every 1 tick after tick 0",
                "soft timer fired on tick 0 (1 of 2)",
                "soft timer fired on tick 0 (2 of 2)",
                "Tick 0: not delayed, no modal. Queued: none. Timers: none",
            ),
            replies,
        )
    }

    @Test
    fun `timer off clears it`() {
        run("timer normal 5")
        run("timer off")
        scriptPhase(5)
        assertEquals(listOf("Set a normal timer every 5 ticks after tick 0", "Timer cleared"), replies)
    }
}
