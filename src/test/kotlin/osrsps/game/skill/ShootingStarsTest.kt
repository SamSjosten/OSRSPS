package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.GameRandom
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/** Shooting stars' cycle and layers ([ShootingStars]), without players: the timings, sites, sizes and the 7 minutes. */
class ShootingStarsTest {
    private fun world(seed: Long, cycle: Boolean = true): World {
        val star = LocCollisionType(width = 2, length = 2)
        val ops = listOf("Mine", "Prospect", null, null, null)
        val types = ShootingStars.STAR_LOCS.map { LocType(it, "Crashed Star", ops, star) } + LocType(ShootingStars.LANDING, "null", collision = star)
        return World(IntArray(0), CollisionMap(), random = GameRandom(seed), locTypes = LocTypes(types.associateBy { it.id }), starCycle = cycle)
    }

    /** Runs the cycle from tick 1 until [landings] stars have landed; each landing's tick, site and size. */
    private fun landings(world: World, landings: Int): List<Triple<Long, StarSite, Int>> {
        val stars = world.shootingStars
        val out = mutableListOf<Triple<Long, StarSite, Int>>()
        stars.process(1)
        while (out.size < landings) {
            val tick = stars.nextLanding
            stars.process(tick)
            val star = stars.star!!
            out += Triple(tick, star.site, star.size)
        }
        return out
    }

    @Test
    fun `with the cycle off no star is planned`() {
        val world = world(1, cycle = false)
        world.shootingStars.process(1)
        assertEquals(-1, world.shootingStars.nextLanding)
        assertNull(world.shootingStars.star)
    }

    @Test
    fun `the first star lands 20 to 50 minutes in, then every 90 give or take 15`() {
        val stars = landings(world(7), 300)
        assertTrue(stars.first().first in 1L + ShootingStars.FIRST_MIN_TICKS..1L + ShootingStars.FIRST_MAX_TICKS, "first at ${stars.first().first}")
        for ((a, b) in stars.zipWithNext()) {
            assertTrue(b.first - a.first in ShootingStars.CYCLE_MIN_TICKS..ShootingStars.CYCLE_MAX_TICKS, "${b.first - a.first} ticks apart")
        }
    }

    @Test
    fun `a star never lands where the last one did, and every Misthalin site and size 6 to 9 comes up`() {
        val stars = landings(world(11), 400)
        for ((a, b) in stars.zipWithNext()) assertNotEquals(a.second, b.second)
        assertEquals(StarSites.load().all.map { it.key }.toSet(), stars.map { it.second.key }.toSet())
        assertEquals(setOf(6, 7, 8, 9), stars.map { it.third }.toSet())
        // 400 draws of 25/30/25/20: each well inside its expected count's spread.
        val counts = stars.groupingBy { it.third }.eachCount()
        for ((size, weight) in ShootingStars.SIZE_WEIGHTS) assertTrue(counts.getValue(size) in (weight * 4 - 45)..(weight * 4 + 45), "size $size: ${counts[size]}")
    }

    @Test
    fun `the same seed lands the same stars at the same ticks`() {
        assertEquals(landings(world(42), 20).map { Triple(it.first, it.second.key, it.third) }, landings(world(42), 20).map { Triple(it.first, it.second.key, it.third) })
    }

    @Test
    fun `the landing loc shows for 4 ticks, then the star, and a new landing takes the old star away`() {
        val world = world(3, cycle = false)
        val stars = world.shootingStars
        val site = stars.sites["draynor"]!!
        stars.land(site, 7, 100)
        assertEquals(ShootingStars.LANDING, world.locs[site.x, site.z, 0, 2]?.id)
        stars.process(103)
        assertEquals(ShootingStars.LANDING, world.locs[site.x, site.z, 0, 2]?.id)
        stars.process(104)
        assertEquals(ShootingStars.idFor(7), world.locs[site.x, site.z, 0, 2]?.id)
        val other = stars.sites["varrock_east_bank"]!!
        stars.land(other, 6, 200)
        assertNull(world.locs[site.x, site.z, 0, 2])
        assertEquals(ShootingStars.LANDING, world.locs[other.x, other.z, 0, 2]?.id)
    }

    @Test
    fun `a layer goes after 700 ticks with someone mining, and not while nobody is`() {
        val world = world(5, cycle = false)
        val stars = world.shootingStars
        val site = stars.sites["lumbridge_swamp_east"]!!
        val star = stars.land(site, 2, 0)
        for (tick in 1L..4L) stars.process(tick)
        // Nobody mining: nothing changes, however long.
        for (tick in 5L..2_000L) stars.process(tick)
        assertEquals(0, star.minedTicks)
        // Mining from tick 2_000: the miner's script runs after process, so each tick's process counts the last.
        var tick = 2_000L
        fun mineOneTick() {
            star.miners["alice"] = tick
            tick++
            stars.process(tick)
        }
        repeat(ShootingStars.LAYER_TICKS - 1) { mineOneTick() }
        assertEquals(2, star.size)
        assertEquals(99, star.percent)
        mineOneTick()
        assertEquals(1, star.size)
        assertEquals(ShootingStars.idFor(1), world.locs[site.x, site.z, 0, 2]?.id)
        repeat(ShootingStars.LAYER_TICKS) { mineOneTick() }
        assertNull(stars.star)
        assertNull(world.locs[site.x, site.z, 0, 2])
    }

    @Test
    fun `each telescope's window holds the landing, is as wide as its accuracy, and stays put while the star waits`() {
        val world = world(9, cycle = false)
        val stars = world.shootingStars
        assertNull(stars.scout(Telescope.OAK, 1))
        stars.schedule(1, 9_000) // 90 minutes
        val site = stars.nextSite!!
        for (telescope in Telescope.entries) {
            val first = stars.scout(telescope, 1)!!
            assertEquals(site.region, first.region)
            assertEquals(telescope.accuracyMinutes, first.toMinutes - first.fromMinutes)
            assertTrue(90 in first.fromMinutes..first.toMinutes, "$telescope: ${first.fromMinutes} to ${first.toMinutes}")
            // Ten minutes on, the same window ten minutes nearer.
            val later = stars.scout(telescope, 1_001)!!
            assertEquals(first.fromMinutes - 10, later.fromMinutes)
        }
        // A window never starts before now, and still holds a landing a minute away.
        stars.schedule(1, 100)
        val close = stars.scout(Telescope.OAK, 1)!!
        assertTrue(close.fromMinutes >= 0 && 1 in close.fromMinutes..close.toMinutes)
    }

    @Test
    fun `the oak's window falls anywhere around the landing, star to star`() {
        val world = world(3, cycle = false)
        val stars = world.shootingStars
        val starts = (1..200).map {
            stars.schedule(1, 6_000) // 60 minutes
            stars.scout(Telescope.OAK, 1)!!.fromMinutes
        }.toSet()
        assertEquals((36..60).toSet(), starts)
    }
}
