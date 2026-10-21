package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.Player
import osrsps.game.World
import osrsps.map.CollisionMap
import osrsps.map.Tile

class DevCommandsTest {
    // Lumbridge's map square (50, 50) and the one north of it exist; nothing else does.
    private val world = World(IntArray(0), CollisionMap().apply { markLoaded(50, 50); markLoaded(50, 51) })
    private val player = Player(1, "test", 3222, 3218, 0)
    private val replies = mutableListOf<String>()

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    @Test
    fun `pos shows coordinates, level and region`() {
        run("pos")
        assertEquals(listOf("3222, 3218, 0 (region 12850, local 22, 18)"), replies)
    }

    @Test
    fun `tele moves the player and keeps the level when none is given`() {
        player.level = 1
        player.waypoints.add(Tile(3230, 3218))
        run("tele 3210 3270")
        assertEquals(listOf(3210, 3270, 1), listOf(player.x, player.z, player.level))
        assertTrue(player.waypoints.isEmpty())
        assertTrue(player.teleported)
        assertEquals(listOf("Teleported to 3210, 3270, 1"), replies)
    }

    @Test
    fun `tele takes the comma-separated form that pos prints`() {
        run("tele 3205, 3209, 2")
        assertEquals(listOf(3205, 3209, 2), listOf(player.x, player.z, player.level))
    }

    @Test
    fun `tele refuses bad input and leaves the player where they are`() {
        run("tele 3210")
        run("tele 3210 abc")
        run("tele 3210 3210 4")
        run("tele 3210 3210 0 1")
        run("tele 0 0")
        run("tele -1 3210")
        assertEquals(
            listOf(
                "Usage: ::tele x z [level]",
                "Usage: ::tele x z [level] ('abc' is not a number)",
                "Level must be 0 to 3",
                "Usage: ::tele x z [level]",
                "No map at 0, 0: the cache has no terrain there",
                // The minus sign is read as part of the number, so this is a range error rather than a usage one.
                "x and z must be 0 to 16383",
            ),
            replies,
        )
        assertEquals(listOf(3222, 3218, 0), listOf(player.x, player.z, player.level))
        assertFalse(player.teleported)
    }

    @Test
    fun `tick shows the world's tick`() {
        run("tick")
        assertEquals(listOf("Tick 0"), replies)
    }

    @Test
    fun `if checks its arguments and that the cache has the interface`() {
        val world = World(IntArray(0), CollisionMap(), interfaceIds = setOf(12))
        for (input in listOf("if", "if bank", "if 12 top", "if 13")) world.commands.execute(player, input, replies::add)
        assertEquals(
            listOf("Usage: ::if id [side]", "Usage: ::if id [side] ('bank' is not a number)", "Usage: ::if id [side]", "The cache has no interface 13"),
            replies,
        )
        assertTrue(player.modals.isEmpty())
    }
}
