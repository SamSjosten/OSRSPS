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
        assertTrue(admins.lists("iron_man"))
        assertFalse(admins.lists("test"))
    }

    @Test
    fun `unset or blank makes everyone an admin`() {
        for (list in listOf(null, "", "  ")) {
            val admins = Admins.parse(list)
            assertTrue(admins.everyone)
            assertTrue(admins.isAdmin(player("anyone")))
            // Nobody is listed, so every name can still be claimed.
            assertFalse(admins.lists("anyone"))
        }
    }

    @Test
    fun `a list with no usable name makes nobody an admin`() {
        val admins = Admins.parse(" , ,")
        assertFalse(admins.everyone)
        assertFalse(admins.isAdmin(player("sam")))
    }

    @Test
    fun `only the commands that read or only show the player something are open to everyone`() {
        val world = osrsps.game.World(IntArray(0), osrsps.map.CollisionMap())
        val open = world.commands.all.filter { !it.admin }.map { it.name }.toSet()
        assertEquals(
            setOf("pos", "keys", "buttons", "tick", "locs", "npcs", "respawns", "los", "combat", "fight", "hp", "drops", "scripts", "sound", "jingle", "prayers", "stats"),
            open,
        )
    }
}
