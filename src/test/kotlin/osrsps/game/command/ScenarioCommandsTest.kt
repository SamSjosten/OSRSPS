package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.NpcType
import osrsps.game.MemorySaveStore
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.PlayerSave
import osrsps.game.Scenario
import osrsps.game.Scenarios
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.MeleeInteraction
import osrsps.game.combat.SpecialAttacks
import osrsps.game.ge.BuyLimitUse
import osrsps.game.ge.GeOffer
import osrsps.game.ge.OfferType
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.QueueType
import osrsps.game.script.TimerType
import osrsps.map.CollisionMap

class ScenarioCommandsTest {
    private val types = ObjTypes.of(
        ObjType(379, "Lobster", internalName = "lobster"),
        ObjType(995, "Coins", stackable = true, internalName = "coins"),
        ObjType(1323, "Iron scimitar", wearpos1 = WearPos.WEAPON, internalName = "iron_scimitar"),
        ObjType(1153, "Iron full helm", wearpos1 = WearPos.HEAD, wearpos2 = WearPos.HAIR, internalName = "iron_full_helm"),
        ObjType(1351, "Bronze axe", wearpos1 = WearPos.WEAPON, internalName = "bronze_axe"),
    )
    private val cows = Scenario.parse(
        "cows",
        """
        {
          "about": "Cows in iron.",
          "position": { "x": 3259, "z": 3263 },
          "skills": { "attack": 20, "hitpoints": { "level": 20, "current": 15 } },
          "run": true,
          "inventory": [ { "item": "lobster", "count": 2 }, { "item": "coins", "count": 100 } ],
          "equipment": [ { "item": "iron_scimitar" }, { "item": "iron_full_helm" } ],
          "varps": { "43": 3 }, "varbits": { "9": 5 }
        }
        """,
    )
    private val ghost = Scenario.parse("ghost", """{ "position": { "x": 3259, "z": 3263 }, "inventory": [ { "item": "ectoplasm" } ] }""")
    private val upstairs = Scenario.parse("upstairs", """{ "position": { "x": 3208, "z": 3220, "level": 2 } }""")
    private val varTypes = VarTypes(5000, mapOf(9 to VarbitType(310, 2, 4)))
    private val saves = MemorySaveStore()

    // Lumbridge's map square (50, 50) exists; nothing else does.
    private val world = World(
        IntArray(0), CollisionMap().apply { markLoaded(50, 50) }, varTypes, saves = saves, objTypes = types,
        scenarios = Scenarios(listOf(cows, ghost, upstairs)),
    )
    private val player = Player(1, "test", 3222, 3218, 0, varTypes).apply {
        skills.setLevel(Skill.COOKING, 50)
        skills.setCurrentLevel(Skill.HITPOINTS, 7)
        runEnergy = 4_000
        vars[310] = 1
        vars[1000] = 77
        inventory[5] = InvObj(1351, 1)
        equipment[WearPos.WEAPON] = InvObj(1351, 1)
    }
    private val replies = mutableListOf<String>()

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    @Test
    fun `scenario sets position, stats, items and vars in one go`() {
        run("scenario cows")
        assertEquals(listOf("Started scenario cows. Cows in iron. ::reset puts you back."), replies)
        assertEquals(listOf(3259, 3263, 0), listOf(player.x, player.z, player.level))
        assertTrue(player.teleported)
        assertEquals(20, player.skills.baseLevel(Skill.ATTACK))
        assertEquals(20, player.skills.baseLevel(Skill.HITPOINTS))
        assertEquals(15, player.hitpoints)
        // Skills the scenario doesn't name are a new account's.
        assertEquals(1, player.skills.baseLevel(Skill.COOKING))
        assertEquals(0, player.skills.tenths(Skill.COOKING))
        assertEquals(10_000, player.runEnergy)
        assertTrue(player.runEnabled)
        assertEquals(listOf(0 to InvObj(379, 1), 1 to InvObj(379, 1), 2 to InvObj(995, 100)), player.inventory.contents())
        assertEquals(listOf(WearPos.HEAD to InvObj(1153, 1), WearPos.WEAPON to InvObj(1323, 1)), player.equipment.contents())
        assertEquals(3, player.vars[43])
        assertEquals(5, player.vars.getVarbit(9))
        // Varps the scenario doesn't name are kept: bits of 310 outside varbit 9, and 1000.
        assertEquals(1 or (5 shl 2), player.vars[310])
        // But the special attack energy is full, as the run energy is.
        assertEquals(SpecialAttacks.FULL, player.vars[SpecialAttacks.VARP_ENERGY])
        assertEquals(77, player.vars[1000])
        assertEquals("cows", player.scenario)
    }

    @Test
    fun `reset puts back everything the scenario changed`() {
        player.bank[0] = InvObj(995, 500)
        player.ge[2] = GeOffer(OfferType.SELL, 1351, 3, 40)
        player.ge.limits[1351] = BuyLimitUse(1351, 1_000, 5)
        val before = PlayerSave.of(player).toJson("test")
        run("scenario cows")
        assertTrue(player.bank.isEmpty())
        assertTrue(player.ge.contents().isEmpty() && player.ge.limits.isEmpty())
        run("reset")
        assertEquals("Back to how you were before ::scenario cows.", replies.last())
        assertEquals(listOf(3222, 3218, 0), listOf(player.x, player.z, player.level))
        assertTrue(player.teleported)
        assertEquals(1, player.skills.baseLevel(Skill.ATTACK))
        assertEquals(50, player.skills.baseLevel(Skill.COOKING))
        assertEquals(7, player.hitpoints)
        assertEquals(4_000, player.runEnergy)
        assertFalse(player.runEnabled)
        assertEquals(0, player.vars[43])
        assertEquals(1, player.vars[310])
        assertEquals(0, player.vars[SpecialAttacks.VARP_ENERGY], "the energy from before")
        assertEquals(listOf(5 to InvObj(1351, 1)), player.inventory.contents())
        assertEquals(listOf(WearPos.WEAPON to InvObj(1351, 1)), player.equipment.contents())
        assertEquals(listOf(0 to InvObj(995, 500)), player.bank.contents())
        assertNull(player.scenario)
        assertNull(player.beforeScenario)
        assertEquals(before, PlayerSave.of(player).toJson("test"))
    }

    @Test
    fun `a second scenario still resets to before the first`() {
        run("scenario cows")
        run("scenario upstairs")
        assertEquals(listOf(3208, 3220, 2), listOf(player.x, player.z, player.level))
        assertTrue(player.inventory.isEmpty())
        assertEquals("upstairs", player.scenario)
        run("reset")
        assertEquals("Back to how you were before ::scenario upstairs.", replies.last())
        assertEquals(listOf(3222, 3218, 0), listOf(player.x, player.z, player.level))
        assertEquals(50, player.skills.baseLevel(Skill.COOKING))
        assertEquals(listOf(5 to InvObj(1351, 1)), player.inventory.contents())
    }

    @Test
    fun `scenario and reset drop queues, timers and delays`() {
        world.scripts.queue(player, "later", QueueType.NORMAL, 5) {}
        world.scripts.setTimer(player, "tick", 3, TimerType.SOFT) {}
        world.scripts.start(player) { delay(10) }
        assertTrue(player.scripts.delayed)
        run("scenario cows")
        assertFalse(player.scripts.delayed)
        assertTrue(player.scripts.queued.isEmpty() && player.scripts.activeTimers.isEmpty())

        world.scripts.queue(player, "later", QueueType.WEAK, 5) {}
        run("reset")
        assertTrue(player.scripts.queued.isEmpty())
    }

    @Test
    fun `scenario and reset end every fight the player is in`() {
        val man = NpcType(3106, "Man", internalName = "man")
        val npc = world.spawnNpc(NpcSpawn(man, 3225, 3218, 0, source = "test"))!!
        fun fight() {
            player.interaction = MeleeInteraction(npc, world.meleeCombat)
            npc.target = player
            player.attackedBy = npc
        }
        fight()
        run("scenario cows")
        assertNull(player.interaction)
        assertNull(npc.target)
        assertNull(player.attackedBy)
        fight()
        run("reset")
        assertNull(player.interaction)
        assertNull(npc.target)
        assertNull(player.attackedBy)
    }

    @Test
    fun `clicks held while paused don't run into the scenario or the reset`() {
        player.heldActions.addLast { player.runEnabled = false }
        run("scenario cows")
        assertTrue(player.heldActions.isEmpty())
        player.heldActions.addLast { player.inventory.clear() }
        run("reset")
        assertTrue(player.heldActions.isEmpty())
        assertEquals(listOf(5 to InvObj(1351, 1)), player.inventory.contents())
    }

    @Test
    fun `a scenario that can't start changes nothing`() {
        run("scenario ghost")
        assertEquals(listOf("Scenario ghost can't start: The cache has no item 'ectoplasm'"), replies)
        assertEquals(listOf(3222, 3218, 0), listOf(player.x, player.z, player.level))
        assertEquals(listOf(5 to InvObj(1351, 1)), player.inventory.contents())
        assertNull(player.beforeScenario)
        assertNull(player.scenario)
    }

    @Test
    fun `the listing names the scenario the player is in, and a reset only works once`() {
        run("scenario cows")
        replies.clear()
        run("scenario")
        assertEquals(
            listOf("Scenarios: cows, ghost, upstairs. You're in cows; ::reset puts you back."),
            replies,
        )
        replies.clear()
        run("reset")
        run("reset")
        assertEquals("Nothing to reset: you haven't started a ::scenario since the last ::reset.", replies.last())
    }

    @Test
    fun `listing, an unknown name and nothing to reset`() {
        run("scenario")
        run("scenario pigs")
        run("reset")
        assertEquals(
            listOf(
                "Scenarios: cows, ghost, upstairs.",
                "No scenario 'pigs'. Scenarios: cows, ghost, upstairs",
                "Nothing to reset: you haven't started a ::scenario since the last ::reset.",
            ),
            replies,
        )
    }

    @Test
    fun `a logout in a scenario keeps the way back`() {
        run("scenario cows")
        assertTrue(world.save(player))
        val loaded = world.loadPlayer(2, "test")
        assertEquals("cows", loaded.scenario)
        assertNotNull(loaded.beforeScenario)
        assertEquals(listOf(3259, 3263), listOf(loaded.x, loaded.z))
        world.commands.execute(loaded, "reset", replies::add)
        assertEquals(listOf(3222, 3218, 0), listOf(loaded.x, loaded.z, loaded.level))
        assertEquals(50, loaded.skills.baseLevel(Skill.COOKING))
        assertEquals(listOf(WearPos.WEAPON to InvObj(1351, 1)), loaded.equipment.contents())
        assertEquals(77, loaded.vars[1000])
        // Once reset, the save no longer carries a scenario.
        assertTrue(world.save(loaded))
        assertFalse("beforeScenario" in saves.saves.getValue("test"))
    }
}
