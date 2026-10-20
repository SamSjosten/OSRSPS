package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import osrsps.game.Player

class CommandRegistryTest {
    private val player = Player(1, "test", 3222, 3218, 0)
    private val replies = mutableListOf<String>()
    private val registry = CommandRegistry().apply {
        register("echo", "::echo words") { reply(args.joinToString("|")) }
        register("fails", "::fails") { fail("Nope") }
        register("throws", "::throws") { error("boom") }
    }

    private fun run(input: String) = registry.execute(player, input, replies::add)

    @Test
    fun `splits the arguments on whitespace`() {
        run("echo  a b\tc ")
        assertEquals(listOf("a|b|c"), replies)
    }

    @Test
    fun `matches names in any case, with or without the colons`() {
        run("ECHO x")
        run("::Echo y")
        assertEquals(listOf("x", "y"), replies)
    }

    @Test
    fun `lists the commands when the name is unknown`() {
        run("nope 1")
        assertEquals(listOf("Unknown command ::nope. Commands: ::echo, ::fails, ::throws"), replies)
    }

    @Test
    fun `an empty command does nothing`() {
        run("  ")
        assertEquals(emptyList(), replies)
    }

    @Test
    fun `a failure is told to the player`() {
        run("fails")
        assertEquals(listOf("Nope"), replies)
    }

    @Test
    fun `an exception is caught and reported`() {
        run("throws")
        assertEquals(listOf("::throws failed: java.lang.IllegalStateException: boom"), replies)
    }

    @Test
    fun `names are unique`() {
        assertFailsWith<IllegalArgumentException> { registry.register("Echo", "::Echo") {} }
    }
}
