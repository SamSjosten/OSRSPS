package osrsps.game.encounter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

class EncountersTest {
    private val guardian = NpcType(16659, "Archive Guardian", size = 5, internalName = "tfa_guardian", stats = NpcStats(hitpoints = 550))

    @Test
    fun `a fight that ends on its first tick leaves the list at the next`() {
        lateinit var world: World
        val vanish = EncounterDef("vanish", guardian, listOf(PhaseDef("open", cycle = listOf(CycleStep(0) { world.removeNpc(boss) }, CycleStep(3) {}))))
        world = World(IntArray(0), CollisionMap(), npcTypes = NpcTypes.of(guardian), encounterDefs = listOf(vanish))
        val boss = assertNotNull(world.spawnNpc(NpcSpawn(guardian, 3222, 3218, 0, source = "test", respawns = false)))
        val fight = world.encounters.start(vanish, boss, { _, _ -> true })
        assertTrue(fight.ended, "ended in its first tick")
        world.encounters.process()
        assertEquals(emptyList(), world.encounters.all)
    }

    @Test
    fun `a called-off fight ends before its tick, its boss taken out`() {
        val world = World(IntArray(0), CollisionMap(), npcTypes = NpcTypes.of(guardian))
        var stepped = 0
        val def = EncounterDef("steady", guardian, listOf(PhaseDef("open", cycle = listOf(CycleStep(1) { stepped++ }))))
        val boss = assertNotNull(world.spawnNpc(NpcSpawn(guardian, 3222, 3218, 0, source = "test", respawns = false)))
        var reason: String? = null
        val fight = world.encounters.start(def, boss, { _, _ -> true }, calledOff = { reason })
        world.encounters.process()
        val before = stepped
        reason = "test left"
        world.encounters.process()
        assertEquals(before, stepped, "no tick once called off")
        assertTrue(fight.ended)
        assertEquals("ends: test left", fight.lines.last().substringAfter("open: "))
        assertEquals(emptyList(), world.encounters.all)
        assertEquals(0, world.npcs.count, "its boss went with it")
    }
}
