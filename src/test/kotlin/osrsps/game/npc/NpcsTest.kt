package osrsps.game.npc

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.Tile

class NpcsTest {
    private val man = NpcType(1, "Man")
    private val cow = NpcType(2, "Cow", size = 2)

    private fun map() = CollisionMap().apply { markLoaded(3200 shr 6, 3200 shr 6) }

    private fun spawn(type: NpcType = man, x: Int = 3230, z: Int = 3230, wander: Int = 5, patrol: List<PatrolStop> = emptyList()) =
        NpcSpawn(type, x, z, wander = wander, patrol = patrol, source = "test")

    /** Where every NPC stands after each of [ticks] ticks. */
    private fun paths(seed: Long, ticks: Int, map: CollisionMap = map(), vararg spawns: NpcSpawn): List<List<Tile>> {
        val npcs = Npcs(map, GameRandom(seed))
        spawns.forEach { npcs.add(it) }
        return (1..ticks).map {
            npcs.process()
            npcs.all.map { Tile(it.x, it.z) }.toList()
        }
    }

    @Test
    fun `the same seed gives the same paths`() {
        val spawns = arrayOf(spawn(x = 3220), spawn(x = 3240), spawn(cow, x = 3230, z = 3245))
        val first = paths(42, 500, map(), *spawns)
        assertEquals(first, paths(42, 500, map(), *spawns))
        assertNotEquals(first, paths(43, 500, map(), *spawns))
        // And they did wander.
        assertTrue(first.map { it[0] }.distinct().size > 10)
    }

    @Test
    fun `a wandering NPC stays within its range of the spawn`() {
        for (tick in paths(7, 2_000, map(), spawn(wander = 3))) {
            val (x, z) = tick.single()
            assertTrue(abs(x - 3230) <= 3 && abs(z - 3230) <= 3, "at $x, $z")
        }
    }

    @Test
    fun `an NPC with no wander range never moves and never rolls`() {
        val random = GameRandom(1)
        val npcs = Npcs(map(), random)
        val npc = npcs.add(spawn(wander = 0))!!
        repeat(100) { npcs.process() }
        assertEquals(Tile(3230, 3230), Tile(npc.x, npc.z))
        assertEquals(0, random.draws)
    }

    @Test
    fun `NPCs walk one tile a tick`() {
        val npcs = Npcs(map(), GameRandom(1))
        val npc = npcs.add(spawn(wander = 0))!!
        npc.destination = Tile(3233, 3230)
        val xs = (1..4).map { npcs.process(); npc.x }
        assertEquals(listOf(3231, 3232, 3233, 3233), xs)
        assertNull(npc.destination)
    }

    @Test
    fun `a frozen NPC stands for the freeze's ticks, can't be frozen again until 5 ticks after, and drops where it was going`() {
        val npcs = Npcs(map(), GameRandom(1))
        val npc = npcs.add(spawn(wander = 0))!!
        npc.destination = Tile(3240, 3230)
        assertTrue(npcs.freeze(npc, 3))
        assertNull(npc.destination)
        assertFalse(npcs.freeze(npc, 3))
        npc.destination = Tile(3240, 3230)
        // A tick as the world has it: the count-down, then spells landing, then movement.
        npcs.process()
        repeat(2) {
            npcs.countDownFreezes()
            assertFalse(npcs.freeze(npc, 3))
            npcs.process()
        }
        assertEquals(3230, npc.x)
        // Free from the next tick, and immune for each of the 5 ticks it moves in.
        repeat(Npcs.FREEZE_IMMUNITY_TICKS) {
            npcs.countDownFreezes()
            assertFalse(npcs.freeze(npc, 3), "frozen again in moving tick ${it + 1}")
            npcs.process()
        }
        assertEquals(3235, npc.x)
        npcs.countDownFreezes()
        assertTrue(npcs.freeze(npc, 3))
    }

    @Test
    fun `naive steps go diagonally first, then straight`() {
        val npcs = Npcs(map(), GameRandom(1))
        val npc = npcs.add(spawn(wander = 0))!!
        npc.destination = Tile(3233, 3231)
        val seen = (1..3).map { npcs.process(); Tile(npc.x, npc.z) }
        assertEquals(listOf(Tile(3231, 3231), Tile(3232, 3231), Tile(3233, 3231)), seen)
    }

    @Test
    fun `a blocked diagonal falls back to a straight step, and a dead end gives up`() {
        val map = map()
        // A wall along the north side of the row the NPC walks on.
        for (x in 3228..3236) {
            map.add(x, 3230, 0, CollisionFlag.WALL_NORTH)
            map.add(x, 3231, 0, CollisionFlag.WALL_SOUTH)
        }
        val npcs = Npcs(map, GameRandom(1))
        val npc = npcs.add(spawn(wander = 0))!!
        npc.destination = Tile(3232, 3233)
        npcs.process()
        npcs.process()
        assertEquals(Tile(3232, 3230), Tile(npc.x, npc.z))
        // Now straight north is blocked too: it gives up.
        npcs.process()
        assertEquals(Tile(3232, 3230), Tile(npc.x, npc.z))
        assertNull(npc.destination)
    }

    @Test
    fun `NPCs never step onto blocked tiles`() {
        val map = map()
        for (x in 3226..3234) for (z in 3226..3234) if (x % 3 == 0 && z % 3 == 0) map.add(x, z, 0, CollisionFlag.LOC)
        for (tick in paths(3, 2_000, map, spawn(x = 3230, z = 3231, wander = 4), spawn(cow, x = 3227, z = 3229, wander = 4))) {
            for ((i, tile) in tick.withIndex()) {
                val size = if (i == 0) 1 else 2
                for (x in tile.x until tile.x + size) for (z in tile.z until tile.z + size) {
                    assertEquals(0, map[x, z, 0] and CollisionFlag.BLOCK_WALK, "NPC $i on $x, $z")
                }
            }
        }
    }

    @Test
    fun `a two-tile NPC can't straddle a fence`() {
        val map = map()
        // A fence between x 3229 and 3230, from z 3220 to 3240.
        for (z in 3220..3240) {
            map.add(3229, z, 0, CollisionFlag.WALL_EAST)
            map.add(3230, z, 0, CollisionFlag.WALL_WEST)
        }
        for (tick in paths(5, 2_000, map, spawn(cow, x = 3230, z = 3230, wander = 5))) {
            assertTrue(tick.single().x >= 3230, "the cow crossed or straddled the fence at ${tick.single()}")
        }
        // A wall across the middle of where it would step is caught too.
        val npcs = Npcs(map(), GameRandom(1))
        val npc = npcs.add(spawn(cow, wander = 0))!!
        val open = map()
        assertTrue(Npcs(open, GameRandom(1)).canStep(npc, -1, 0))
        val walled = map().apply { add(3229, 3230, 0, CollisionFlag.WALL_NORTH); add(3229, 3231, 0, CollisionFlag.WALL_SOUTH) }
        assertTrue(!Npcs(walled, GameRandom(1)).canStep(npc, -1, 0))
        assertTrue(npcs.canStep(npc, 1, 1))
    }

    @Test
    fun `NPCs don't walk into each other`() {
        val npcs = Npcs(map(), GameRandom(1))
        val a = npcs.add(spawn(wander = 0))!!
        val b = npcs.add(spawn(cow, x = 3231, z = 3230, wander = 0))!!
        a.destination = Tile(3235, 3230)
        npcs.process()
        assertEquals(Tile(3230, 3230), Tile(a.x, a.z))
        npcs.remove(b)
        a.destination = Tile(3235, 3230)
        npcs.process()
        assertEquals(Tile(3231, 3230), Tile(a.x, a.z))
    }

    @Test
    fun `a patrol walks its tiles in turn, over and over`() {
        val npcs = Npcs(map(), GameRandom(1))
        val patrol = listOf(PatrolStop(3232, 3230), PatrolStop(3232, 3232), PatrolStop(3230, 3230))
        val npc = npcs.add(spawn(wander = 5, patrol = patrol))!!
        val seen = (1..12).map { npcs.process(); Tile(npc.x, npc.z) }
        assertEquals(
            listOf(
                Tile(3231, 3230), Tile(3232, 3230),
                Tile(3232, 3231), Tile(3232, 3232),
                Tile(3231, 3231), Tile(3230, 3230),
                Tile(3231, 3230), Tile(3232, 3230),
                Tile(3232, 3231), Tile(3232, 3232),
                Tile(3231, 3231), Tile(3230, 3230),
            ),
            seen,
        )
    }

    @Test
    fun `a patrol stop with a wait holds the NPC there that many ticks`() {
        val npcs = Npcs(map(), GameRandom(1))
        val patrol = listOf(PatrolStop(3230, 3230), PatrolStop(3232, 3230, wait = 3))
        val npc = npcs.add(spawn(patrol = patrol))!!
        val seen = (1..9).map { npcs.process(); Tile(npc.x, npc.z) }
        assertEquals(
            listOf(
                Tile(3230, 3230), Tile(3231, 3230), Tile(3232, 3230),
                // Arrived on tick 3: stands on ticks 4 to 6, steps off on 7.
                Tile(3232, 3230), Tile(3232, 3230), Tile(3232, 3230),
                Tile(3231, 3230), Tile(3230, 3230), Tile(3231, 3230),
            ),
            seen,
        )
    }

    @Test
    fun `an indoors NPC keeps under the roof and an outdoors one out from under it`() {
        // A roof over x 3228 to 3232; open sky either side.
        val map = map().apply { for (x in 3228..3232) for (z in 3220..3240) add(x, z, 0, CollisionFlag.ROOF) }
        val npcs = Npcs(map, GameRandom(3))
        val inside = npcs.add(NpcSpawn(man, 3230, 3230, wander = 6, restrict = MoveRestrict.INDOORS, source = "test"))!!
        val outside = npcs.add(NpcSpawn(man, 3226, 3230, wander = 6, restrict = MoveRestrict.OUTDOORS, source = "test"))!!
        val insideXs = HashSet<Int>()
        val outsideXs = HashSet<Int>()
        repeat(2_000) {
            npcs.process()
            insideXs += inside.x
            outsideXs += outside.x
        }
        assertEquals((3228..3232).toSet(), insideXs)
        assertEquals((3220..3227).toSet(), outsideXs)
    }

    @Test
    fun `near finds NPCs around a tile on its level`() {
        val npcs = Npcs(map(), GameRandom(1))
        npcs.add(spawn(x = 3230))
        npcs.add(spawn(x = 3250))
        npcs.add(NpcSpawn(man, 3231, 3230, level = 1, source = "test"))
        assertEquals(listOf(3230), npcs.near(3232, 3230, 0, 5).map { it.x })
        assertEquals(3, npcs.count)
    }

    @Test
    fun `a following NPC walks after the player and stops beside them`() {
        val npcs = Npcs(map(), GameRandom(1))
        val npc = npcs.add(spawn(x = 3230, z = 3230, wander = 5))!!
        val player = Player(1, "test", 3235, 3233, 0)
        npcs.follow(npc, player)
        assertSame(player, npc.facing)
        repeat(10) { npcs.process() }
        // Diagonally to 3233, 3233, then straight: stops at 3234, beside the player, not on them.
        assertEquals(Tile(3234, 3233), Tile(npc.x, npc.z))
        // The player walks off; it follows.
        player.x = 3240
        repeat(10) { npcs.process() }
        assertEquals(Tile(3239, 3233), Tile(npc.x, npc.z))
        npcs.stopFollowing(npc)
        assertNull(npc.facing)
    }

    @Test
    fun `a following NPC at the player's corner slides to their side, and steps out from under them`() {
        val npcs = Npcs(map(), GameRandom(1))
        val npc = npcs.add(spawn(x = 3231, z = 3231, wander = 0))!!
        val player = Player(1, "test", 3230, 3230, 0)
        npcs.follow(npc, player)
        npcs.process()
        // The diagonal step would land on the player, so it takes the x step.
        assertEquals(Tile(3230, 3231), Tile(npc.x, npc.z))
        player.z = 3231
        npcs.process()
        // Under the player now: it steps out west first.
        assertEquals(Tile(3229, 3231), Tile(npc.x, npc.z))
    }

    @Test
    fun `a big following NPC on top of the player steps out over several ticks`() {
        val npcs = Npcs(map(), GameRandom(1))
        val npc = npcs.add(spawn(NpcType(3, "Giant", size = 3), x = 3230, z = 3230, wander = 0))!!
        // The player stands on the middle tile of the 3x3 NPC: no one step clears them.
        val player = Player(1, "test", 3231, 3231, 0)
        npcs.follow(npc, player)
        npcs.process()
        assertEquals(Tile(3229, 3230), Tile(npc.x, npc.z))
        npcs.process()
        assertEquals(Tile(3228, 3230), Tile(npc.x, npc.z))
        // Beside the player now, so it stays.
        npcs.process()
        assertEquals(Tile(3228, 3230), Tile(npc.x, npc.z))
    }

    @Test
    fun `a busy NPC stands still and is let go`() {
        val npcs = Npcs(map(), GameRandom(1))
        val npc = npcs.add(spawn(wander = 5))!!
        val player = Player(1, "test", 3231, 3230, 0)
        npcs.engage(npc, player, 0)
        repeat(200) { npcs.process() }
        assertEquals(Tile(3230, 3230), Tile(npc.x, npc.z))
        assertSame(player, npc.facing)
        npcs.forget(player)
        assertNull(npc.engagedWith)
        assertNull(npc.facing)
    }

    @Test
    fun `a busy NPC told to follow someone else keeps facing its player until let go`() {
        val npcs = Npcs(map(), GameRandom(1))
        val npc = npcs.add(spawn(wander = 0))!!
        val talker = Player(1, "talker", 3231, 3230, 0)
        val other = Player(2, "other", 3229, 3230, 0)
        npcs.engage(npc, talker, 0)
        npcs.follow(npc, other)
        assertSame(talker, npc.facing)
        npcs.release(npc)
        assertSame(other, npc.facing)
    }

    @Test
    fun `a following NPC gives up on a player who goes far away`() {
        val npcs = Npcs(map(), GameRandom(1))
        val npc = npcs.add(spawn(wander = 0))!!
        val player = Player(1, "test", 3231, 3230, 0)
        npcs.follow(npc, player)
        player.x = 3230 + Npcs.FOLLOW_RANGE + 1
        npcs.process()
        assertNull(npc.following)
    }
}
