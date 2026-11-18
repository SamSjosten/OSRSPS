package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Scenario
import osrsps.game.Scenarios
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.DeathPlace
import osrsps.game.combat.Deaths
import osrsps.game.skill.EssencePouches
import osrsps.map.CollisionMap

/**
 * Exploit review round 40: what a scenario hands out never comes out real after the way back, whoever starts it (the
 * raid tester starts one for any player). R40C-2, unclaimed raid loot; R40C-3, a grave from before the scenario; R40A-3,
 * essence in a pouch.
 */
class ScenarioItemsTest {
    private val types = ObjTypes.of(
        ObjType(379, "Lobster", internalName = "lobster"),
        ObjType(995, "Coins", stackable = true, internalName = "coins"),
        ObjType(1351, "Bronze axe", internalName = "bronze_axe"),
    )
    private val kit = Scenario.parse(
        "kit",
        """{ "position": { "x": 3222, "z": 3218 }, "inventory": [ { "item": "lobster", "count": 6 } ] }""",
    )
    private val small = EssencePouches.POUCHES.first { it.key == "small" }
    private val varTypes = VarTypes(5000, mapOf(small.countVarbit to VarbitType(4000, 0, 5), small.typeVarbit to VarbitType(4000, 6, 8)))
    private val world = World(
        IntArray(0), CollisionMap().apply { markLoaded(50, 50) }, varTypes, objTypes = types,
        npcTypes = NpcTypes.of(NpcType(Deaths.GRAVE, "Grave")), scenarios = Scenarios(listOf(kit)),
    )
    private val player = Player(1, "test", 3222, 3218, 0, varTypes)
    private val replies = mutableListOf<String>()
    private val here = DeathPlace(false, 3222, 3218, 0, 3222, 3218, 0)

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    private fun dieHere() {
        world.deaths.die(player)
        world.deaths.respawn(player, here)
    }

    @Test
    fun `unclaimed raid loot is put away for the scenario and comes back once`() {
        player.archiveChest[0] = InvObj(995, 25_000)
        run("scenario kit")
        assertTrue(player.archiveChest.isEmpty(), "nothing to claim in the scenario")
        run("reset")
        assertEquals(listOf(0 to InvObj(995, 25_000)), player.archiveChest.contents())
    }

    @Test
    fun `a scenario death leaves the grave from before it as it was`() {
        for (slot in 0 until 6) player.inventory[slot] = InvObj(1351, 1)
        dieHere()
        val grave = assertNotNull(world.deaths.graveOf(player), "a grave for the real death")
        val real = grave.items.contents().map { it.second.id to it.second.count }
        assertTrue(real.isNotEmpty() && real.all { it.first == 1351 })
        run("scenario kit")
        dieHere()
        assertEquals(real, world.deaths.graveOf(player)!!.items.contents().map { it.second.id to it.second.count }, "no lobster in it")
        run("reset")
        assertEquals(real, world.deaths.graveOf(player)!!.items.contents().map { it.second.id to it.second.count })
    }

    @Test
    fun `a scenario death with no grave from before still raises the scenario's, which goes with it`() {
        run("scenario kit")
        dieHere()
        assertTrue(world.deaths.graveOf(player)!!.scenario)
        run("reset")
        assertEquals(null, world.deaths.graveOf(player))
    }

    @Test
    fun `essence in a pouch is put away for the scenario and comes back once`() {
        player.vars.setVarbit(small.countVarbit, 3)
        player.vars.setVarbit(small.typeVarbit, 2)
        run("scenario kit")
        assertEquals(0 to 0, player.vars.getVarbit(small.countVarbit) to player.vars.getVarbit(small.typeVarbit))
        player.vars.setVarbit(small.countVarbit, 1)
        run("reset")
        assertEquals(3 to 2, player.vars.getVarbit(small.countVarbit) to player.vars.getVarbit(small.typeVarbit))
    }
}
