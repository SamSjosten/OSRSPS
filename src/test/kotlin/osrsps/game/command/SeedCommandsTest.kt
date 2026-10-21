package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.GameRandom
import osrsps.game.Player
import osrsps.game.World
import osrsps.map.CollisionMap

class SeedCommandsTest {
    private val world = World(IntArray(0), CollisionMap(), random = GameRandom(5))
    private val player = Player(1, "test", 3222, 3218, 0)
    private val replies = mutableListOf<String>()

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    @Test
    fun `seed shows the seed and how far along it is`() {
        run("seed")
        world.random.nextInt(10)
        run("seed")
        assertEquals(listOf("Seed 5, 0 draws since it was set", "Seed 5, 1 draw since it was set"), replies)
    }

    @Test
    fun `seed n restarts the rolls for n`() {
        world.random.nextLong()
        run("seed -42")
        val expected = GameRandom(-42)
        assertEquals(List(5) { expected.nextInt(100) }, List(5) { world.random.nextInt(100) })
        assertEquals("Seed set to -42. The same input on the same ticks from here gives the same rolls", replies.single())
    }

    @Test
    fun `seed refuses anything but one whole number`() {
        run("seed 1.5")
        run("seed 1 2")
        assertEquals(5, world.random.seed)
        assertEquals(listOf("Usage: ::seed [n] ('1.5' is not a whole number)", "Usage: ::seed [n]"), replies)
    }
}
