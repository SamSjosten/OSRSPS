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
    fun `an admin command refuses someone who isn't one and leaves it off their list`() {
        val admins = Admins.parse("Sam")
        val guarded = CommandRegistry(admins::isAdmin).apply {
            register("pos", "::pos", admin = false) { reply("here") }
            register("pause", "::pause", admin = true) { reply("paused") }
        }
        val sam = Player(2, "sam", 3222, 3218, 0)
        guarded.execute(player, "pause", replies::add)
        guarded.execute(player, "pos", replies::add)
        guarded.execute(player, "nope", replies::add)
        guarded.execute(sam, "pause", replies::add)
        guarded.execute(sam, "nope", replies::add)
        assertEquals(
            listOf(
                "::pause is a testing tool, so only admins can use it here.",
                "here",
                "Unknown command ::nope. Commands: ::pos",
                "paused",
                "Unknown command ::nope. Commands: ::pause, ::pos",
            ),
            replies,
        )
    }

    @Test
    fun `commands are for admins unless registered otherwise, and a dead player runs only the open ones`() {
        val guarded = CommandRegistry(Admins.parse("Sam")::isAdmin).apply {
            register("item", "::item") { reply("item") }
            register("pos", "::pos", admin = false) { reply("here") }
        }
        val sam = Player(2, "sam", 3222, 3218, 0)
        guarded.execute(player, "item", replies::add)
        sam.deathTick = 5
        guarded.execute(sam, "item", replies::add)
        guarded.execute(sam, "pos", replies::add)
        sam.deathTick = -1
        guarded.execute(sam, "item", replies::add)
        assertEquals(
            listOf("::item is a testing tool, so only admins can use it here.", "You can't use ::item while you are dead.", "here", "item"),
            replies,
        )
    }

    @Test
    fun `names are unique`() {
        assertFailsWith<IllegalArgumentException> { registry.register("Echo", "::Echo") {} }
    }
}
