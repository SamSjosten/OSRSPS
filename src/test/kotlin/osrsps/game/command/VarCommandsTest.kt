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
    fun `a var of our own goes by its name, whatever id the cache left it`() {
        // A player made with the world's types, which hold our vars after the cache's (5,000 varps, 29 varbits here).
        val player = Player(2, "named", 3222, 3218, 0, world.varTypes)
        val vars = world.customVars
        assertEquals(5000, vars.lodestonesVarp)
        assertEquals(29, vars.lodestoneVarbit(osrsps.game.content.Lodestones.LUMBRIDGE))
        world.commands.execute(player, "varp lodestones 5", replies::add)
        world.commands.execute(player, "varp Lodestones", replies::add)
        world.commands.execute(player, "varbit lodestone.varrock", replies::add)
        world.commands.execute(player, "varbit lodestone.varrock 1", replies::add)
        world.commands.execute(player, "varbit lodestone.varrock 2", replies::add)
        world.commands.execute(player, "varbit lodestone.nowhere", replies::add)
        assertEquals(
            listOf(
                "Varp lodestones: 0 -> 5 (id 5000)",
                "Varp lodestones = 5 (id 5000)",
                "Varbit lodestone.varrock (id 31, varp 5000 bits 2-2) = 1",
                "Varbit lodestone.varrock (id 31, varp 5000 bits 2-2): 1 -> 1, varp 5000 is now 5",
                "Varbit lodestone.varrock holds 0 to 1",
                "Usage: ::varbit id|name [value] ('lodestone.nowhere' is not a number or one of lodestone.al_kharid, lodestone.draynor, lodestone.edgeville, lodestone.lumbridge, lodestone.varrock)",
            ),
            replies,
        )
        assertEquals(5, player.vars[5000])
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
                "Usage: ::varp id|name [value]",
                "Usage: ::varp id|name [value] ('abc' is not a number or one of lodestones)",
                "Varps are 0 to 4999",
                "Usage: ::varp id|name [value] ('x' is not a number)",
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
