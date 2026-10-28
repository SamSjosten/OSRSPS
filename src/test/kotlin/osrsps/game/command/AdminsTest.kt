package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.Player

class AdminsTest {
    private fun player(name: String) = Player(1, name, 3222, 3218, 0)

    @Test
    fun `names match the way saves do`() {
        val admins = Admins.parse(" Sam , test2,Iron Man")
        assertTrue(admins.isAdmin(player("sam")))
        assertTrue(admins.isAdmin(player("TEST2")))
        assertTrue(admins.isAdmin(player("iron_man")))
        assertFalse(admins.isAdmin(player("test")))
        assertEquals("iron_man, sam, test2", admins.toString())
    }

    @Test
    fun `unset or blank makes everyone an admin`() {
        for (list in listOf(null, "", "  ")) {
            val admins = Admins.parse(list)
            assertTrue(admins.everyone)
            assertTrue(admins.isAdmin(player("anyone")))
        }
    }

    @Test
    fun `a list with no usable name makes nobody an admin`() {
        val admins = Admins.parse(" , ,")
        assertFalse(admins.everyone)
        assertFalse(admins.isAdmin(player("sam")))
    }

    @Test
    fun `the world-changing commands are the admin ones`() {
        val world = osrsps.game.World(IntArray(0), osrsps.map.CollisionMap())
        val adminOnly = world.commands.all.filter { it.admin }.map { it.name }.toSet()
        assertEquals(
            setOf(
                "pause", "step", "resume", "seed", "addloc", "delloc", "resetlocs", "gfx", "proj", "areasound",
                "dialogue", "groundobj", "objspawn", "cleartile", "npc", "follow", "delnpc", "hit", "kill", "god", "heal", "multi", "blowpipe",
            ),
            adminOnly,
        )
    }
}
