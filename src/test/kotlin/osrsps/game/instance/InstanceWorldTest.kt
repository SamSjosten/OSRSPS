package osrsps.game.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.ObjType
import osrsps.game.MemorySaveStore
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Player
import osrsps.game.PlayerSave
import osrsps.game.World
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocShape

/** What the world does with instances: ending one with everything in it, and never saving anyone in one. */
class InstanceWorldTest {
    private val man = NpcType(3106, "Man", internalName = "man")
    private val saves = MemorySaveStore()
    private val world = World(IntArray(0), CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, saves = saves, npcTypes = NpcTypes.of(man))
    private val instance = assertNotNull(world.instances.create(3216, 3216, 2, 2, 3222, 3218, 0))
    private val inside = Pair(instance.baseX + 3, instance.baseZ + 3)

    @Test
    fun `an ended instance takes its NPCs, ground items and loc changes with it`() {
        val npc = assertNotNull(world.spawnNpc(NpcSpawn(man, inside.first, inside.second, source = "test")))
        val outsider = assertNotNull(world.spawnNpc(NpcSpawn(man, 3222, 3220, source = "test")))
        world.groundObjs.addPublic(ObjType(1351, "Bronze axe"), 1, inside.first, inside.second, 0)
        world.locs.add(ZoneLoc(1, LocShape.CENTREPIECE_STRAIGHT, 0, inside.first + 1, inside.second, 0))
        assertEquals(1, world.locs.changed)

        world.endInstance(instance)
        assertNull(world.npcs[npc.index], "its man")
        assertEquals(outsider, world.npcs[outsider.index], "the world's man stays")
        assertEquals(emptyList(), world.zones.objsAt(inside.first, inside.second, 0))
        assertEquals(0, world.locs.changed)
        assertNull(world.instances.at(inside.first, inside.second))
        // A respawn into it has nowhere to go.
        assertNull(world.spawnNpc(NpcSpawn(man, inside.first, inside.second, source = "test")))
    }

    @Test
    fun `a player in an instance is saved at its exit, and one saved where an instance was comes back at the spawn`() {
        val player = Player(1, "alice", inside.first, inside.second, 0)
        val save = world.snapshot(player)
        assertEquals(Triple(3222, 3218, 0), Triple(save.x, save.z, save.level))
        // A save from inside one, as saves were before they moved players out, once the instance has gone.
        saves.write(player.saveKey, PlayerSave.of(player).toJson(player.username))
        world.endInstance(instance)
        val loaded = world.loadPlayer(1, "alice")
        assertEquals(Triple(World.SPAWN_X, World.SPAWN_Z, World.SPAWN_LEVEL), Triple(loaded.x, loaded.z, loaded.level))
        assertTrue(!world.instances.inDynamicArea(loaded.x, loaded.z))
    }
}
