package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.map.CollisionMap

/** Wilderness step W5.4: where and when the Revenant maledictus appears. */
class MaledictusTest {
    private val boss = NpcType(Maledictus.BOSS, "Revenant maledictus", size = 5, combatLevel = 397, internalName = "wild_cave_superior", stats = NpcStats(hitpoints = 1250))
    private fun world() = World(IntArray(0), CollisionMap(), npcTypes = NpcTypes.of(boss), random = GameRandom(1))

    @Test
    fun `the Wiki's eleven places, three in the north, five in the middle and three in the south, all in the caves`() {
        assertEquals(11, Maledictus.SPOTS.size)
        assertEquals(listOf(3, 5, 3), Maledictus.Area.entries.map { area -> Maledictus.SPOTS.count { it.area == area } })
        for (spot in Maledictus.SPOTS) {
            for (dx in 0 until 5) for (dz in 0 until 5) assertTrue(RevenantCaves.inCaves(spot.tile.x + dx, spot.tile.z + dz), "${spot.tile} in the caves")
        }
    }

    @Test
    fun `revenant kills add their combat levels toward it`() {
        val world = world()
        Maledictus.revenantKilled(world, 7)
        Maledictus.revenantKilled(world, 135)
        assertNull(Maledictus.active(world), "a chance of 142 in 2,000,000")
        assertEquals(142, Maledictus.tally(world).levels)
    }

    @Test
    fun `once it appears the tally resets, it fights many at once, and none comes for 45 minutes`() {
        val world = world()
        Maledictus.revenantKilled(world, Maledictus.RATE)
        val npc = assertNotNull(Maledictus.active(world), "certain at the full rate")
        assertTrue(npc.multiTarget)
        assertTrue(Maledictus.SPOTS.any { it.tile.x == npc.x && it.tile.z == npc.z })
        val tally = Maledictus.tally(world)
        assertEquals(0, tally.levels)
        assertEquals(world.tick + Maledictus.COOLDOWN_TICKS, tally.readyTick)

        Maledictus.revenantKilled(world, Maledictus.RATE)
        assertSame(npc, Maledictus.active(world), "only one at a time")
        world.removeNpc(npc)
        Maledictus.revenantKilled(world, Maledictus.RATE)
        assertNull(Maledictus.active(world), "not during the cooldown")
        assertEquals(0, tally.levels, "kills during the cooldown don't count")
    }

    @Test
    fun `its specials come in turn, ice first`() {
        assertEquals(listOf(Maledictus.Special.ICE, Maledictus.Special.BLOOD, Maledictus.Special.SURGE), Maledictus.Special.entries)
    }

    @Test
    fun `the top damager's two dragon rolls give one looting bag at most`() {
        // Only the bag known, so only bags land: each kill's pile is its bags.
        val bag = ObjType(RevenantDrops.LOOTING_BAG, "Looting bag")
        val world = World(IntArray(0), CollisionMap(), npcTypes = NpcTypes.of(boss), objTypes = ObjTypes.of(bag), random = GameRandom(2))
        Maledictus.revenantKilled(world, Maledictus.RATE)
        val npc = assertNotNull(Maledictus.active(world))
        var some = 0
        repeat(300) {
            Maledictus.loot(world, npc, mapOf("bob" to 100))
            val bags = world.groundObjs.clear(npc.x, npc.z, npc.level)
            assertTrue(bags <= 1, "two bags from one kill")
            some += bags
        }
        // 1 in 3 a roll at combat 135: about 5 in 9 kills give one.
        assertTrue(some in 120..210, "$some")
    }
}
