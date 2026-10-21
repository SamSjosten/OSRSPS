package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.Player
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.map.CollisionMap

class VarCommandsTest {
    private val types = VarTypes(5000, mapOf(28 to VarbitType(173, 0, 0), 9 to VarbitType(300, 2, 4)))
    private val world = World(IntArray(0), CollisionMap(), types)
    private val player = Player(1, "test", 3222, 3218, 0, types)
    private val replies = mutableListOf<String>()

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    @Test
    fun `varp 173 1 turns the run orb on`() {
        run("varp 173 1")
        assertTrue(player.runEnabled)
        run("varp 173")
        assertEquals(listOf("Varp 173: 0 -> 1", "Varp 173 = 1"), replies)
    }

    @Test
    fun `varbit sets its bits and shows the varp`() {
        run("varbit 9 5")
        run("varbit 9")
        assertEquals(
            listOf("Varbit 9 (varp 300 bits 2-4): 0 -> 5, varp 300 is now 20", "Varbit 9 (varp 300 bits 2-4) = 5"),
            replies,
        )
        assertEquals(20, player.vars[300])
    }

    @Test
    fun `refuses bad input and changes nothing`() {
        run("varp")
        run("varp abc")
        run("varp 5000 1")
        run("varp 1 x")
        run("varbit 1 1")
        run("varbit 9 8")
        assertEquals(
            listOf(
                "Usage: ::varp id [value]",
                "Usage: ::varp id [value] ('abc' is not a number)",
                "Varps are 0 to 4999",
                "Usage: ::varp id [value] ('x' is not a number)",
                "No varbit 1 in the cache",
                "Varbit 9 holds 0 to 7",
            ),
            replies,
        )
        val changed = mutableListOf<Int>()
        player.vars.flush { id, _ -> changed.add(id) }
        assertEquals(emptyList(), changed)
    }
}
