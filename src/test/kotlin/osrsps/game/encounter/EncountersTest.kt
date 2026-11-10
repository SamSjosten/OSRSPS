package osrsps.game.encounter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.LocCollision
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

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

    @Test
    fun `a turned placed loc blocks its turned footprint, and the fight's end puts back the cache's loc and its turn`() {
        // A 2 by 1 that blocks walking but not shots (Ocellus's portals, raid step 25a), and a cache loc under it.
        val portal = LocType(PORTAL, "Portal", collision = LocCollisionType(width = 2, length = 1, interactType = 1, blocksProjectile = false))
        val table = LocType(TABLE, "Table", collision = LocCollisionType(width = 2, length = 1))
        val map = CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }
        val cached = ZoneLoc(TABLE, LocShape.CENTREPIECE_STRAIGHT, 3, 3230, 3230, 0)
        val static = StaticLocs().apply { add(cached) }
        LocCollision(map).addLoc(table.collision, cached.x, cached.z, 0, cached.shape, cached.angle)
        lateinit var placed: ZoneLoc
        val def = EncounterDef("portals", guardian, listOf(PhaseDef("open", cycle = listOf(CycleStep(0) { placed = placeLoc(PORTAL, 3230, 3230, rotation = 1) }, CycleStep(5) {}))))
        val world = World(
            IntArray(0), map, npcTypes = NpcTypes.of(guardian), locTypes = LocTypes(mapOf(PORTAL to portal, TABLE to table)), staticLocs = static,
        )
        val boss = assertNotNull(world.spawnNpc(NpcSpawn(guardian, 3222, 3218, 0, source = "test", respawns = false)))
        val fight = world.encounters.start(def, boss, { _, _ -> true })
        assertEquals(1, placed.angle)
        assertEquals(placed, world.locs[3230, 3230, 0, placed.layer], "it replaced the cache's loc on its layer")
        // Turned once, 1 wide along x and 2 long along z: it blocks 3230,3230 and 3230,3231, not 3231,3230.
        assertEquals(CollisionFlag.LOC, map[3230, 3230, 0])
        assertEquals(CollisionFlag.LOC, map[3230, 3231, 0])
        assertEquals(0, map[3231, 3230, 0])
        fight.end("test over")
        assertEquals(cached, world.locs[3230, 3230, 0, cached.layer], "the cache's loc is back, turned as it was")
        assertEquals(CollisionFlag.LOC or CollisionFlag.LOC_PROJ, map[3230, 3230, 0])
        assertEquals(CollisionFlag.LOC or CollisionFlag.LOC_PROJ, map[3230, 3231, 0])
        assertEquals(0, map[3231, 3230, 0])
    }

    private companion object {
        const val PORTAL = 62684
        const val TABLE = 1100
    }
}
