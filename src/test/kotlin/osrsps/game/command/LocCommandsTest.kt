package osrsps.game.command

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

class LocCommandsTest {
    private val static = StaticLocs().apply { add(ZoneLoc(1, LocShape.WALL_STRAIGHT, 0, 3222, 3218, 0)) }
    private val types = LocTypes(
        mapOf(
            1 to LocType(1, "Wall"),
            2 to LocType(2, "Crate", collision = LocCollisionType(width = 1, length = 1)),
        ),
    )
    private val world = World(IntArray(0), CollisionMap().apply { markLoaded(50, 50) }, locTypes = types, staticLocs = static)
    private val player = Player(1, "test", 3222, 3218, 0)
    private val replies = mutableListOf<String>()

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    @AfterTest
    fun release() = world.zones.endCycle()

    @Test
    fun `lists the locs around the player`() {
        run("::locs 0")
        assertEquals(listOf("1 Wall at 3222, 3218 shape 0 angle 0"), replies)
    }

    @Test
    fun `adds a loc on the player's tile`() {
        run("::addloc 2")
        assertEquals("Added loc 2 shape 10 angle 0 on 3222, 3218", replies.last())
        assertTrue(world.locs.at(3222, 3218, 0).any { it.id == 2 })
        run("::locs 0")
        assertTrue(replies.last().endsWith("(changed)"))
    }

    @Test
    fun `refuses locs the cache doesn't have and bad shapes`() {
        run("::addloc 99")
        run("::addloc 2 23")
        assertEquals(listOf("The cache has no loc 99", "Shape must be 0 to 22"), replies)
    }

    @Test
    fun `removes the locs on the tile and puts them back`() {
        run("::delloc")
        assertEquals("Removed 1 loc from 3222, 3218", replies.last())
        assertTrue(world.locs.at(3222, 3218, 0).isEmpty())
        run("::resetlocs")
        assertEquals(1, world.locs.at(3222, 3218, 0).size)
        assertEquals(0, world.locs.changed)
    }
}
