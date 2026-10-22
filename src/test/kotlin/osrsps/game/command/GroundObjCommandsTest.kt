package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.zone.payload.ObjAdd
import osrsps.game.ObjSpawn
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.World
import osrsps.map.CollisionMap

class GroundObjCommandsTest {
    private val types = ObjTypes.of(
        ObjType(1351, "Bronze axe", internalName = "bronze_axe"),
        ObjType(995, "Coins", stackable = true, internalName = "coins"),
    )
    private val world = World(IntArray(0), CollisionMap(), objTypes = types)
    private val player = Player(1, "test", 3222, 3218, 0)
    private val other = Player(2, "other", 3225, 3220, 0)
    private val replies = mutableListOf<String>()

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    /** How many objs [observer] is told about this cycle; payloads are encoded, so read the updates before that. */
    private fun objAdds(observer: Player): Int {
        val count = world.zones.collect(observer).sumOf { update -> (update.shared + update.own).count { it is ObjAdd } }
        world.zones.endCycle()
        return count
    }

    @Test
    fun `groundobj stays for players who come by later, and cleartile takes it away`() {
        run("groundobj bronze axe")
        run("groundobj 995 50")
        run("groundobj bronze_axe 3")
        assertEquals("Put Bronze axe (1351) x 3 on 3222, 3218", replies.last())
        assertEquals(listOf(1, 50, 1, 1, 1), world.zones.objsAt(3222, 3218, 0).map { it.count })
        assertEquals(5, objAdds(other))
        run("cleartile")
        assertEquals("Removed 5 objs from 3222, 3218", replies.last())
        other.zoneView.reset()
        assertEquals(0, objAdds(other))
    }

    @Test
    fun `objspawn makes a spawn that respawns`() {
        run("objspawn coins 10 25")
        assertEquals(listOf(ObjSpawn(995, 10, 3222, 3218, 0, 25)), world.groundObjs.spawns)
        run("objspawn bronze axe 2")
        assertEquals("Bronze axe doesn't stack, so a spawn of it is 1", replies.last())
        run("grounditems")
        assertEquals("Coins (995) x 10: public, stays, item spawn back 25 ticks after it's taken", replies.last())
    }

    @Test
    fun `grounditems shows owners and timers`() {
        run("grounditems")
        assertEquals("Nothing on 3222, 3218", replies.last())
        player.inventory.add(types[1351]!!, 1)
        world.groundObjs.drop(player, 0)
        run("grounditems")
        assertEquals("Bronze axe (1351) x 1: private to test, never public, gone in 300 ticks", replies.last())
    }

    @Test
    fun `the commands check their arguments`() {
        run("groundobj")
        run("groundobj rune pickaxe")
        run("groundobj bronze axe 200")
        run("cleartile now")
        assertTrue(replies.all { it.startsWith("Usage") || it.startsWith("No item") || it.contains("doesn't stack") }, replies.toString())
        assertEquals(4, replies.size)
        assertTrue(world.zones.objsAt(3222, 3218, 0).isEmpty())
    }
}
