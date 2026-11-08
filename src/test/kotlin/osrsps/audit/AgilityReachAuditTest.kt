package osrsps.audit

import osrsps.game.skill.AgilityData
import osrsps.game.zone.ZoneLoc
import osrsps.map.RouteFinder
import osrsps.map.RouteTarget
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Checks skills/agility.json against the real 241 map (see [RealMap]; skipped without it, as in CI). The headless tests
 * cross the obstacles on open ground, where every loc is reached from beside it; on the map a roof's edge or the gap an
 * obstacle spans can leave its loc out of reach, and the click ends in "I can't reach that!" (the Al Kharid tropical
 * tree and the Varrock wall on rl-client, 2026-11-08). Run it in the cloud after a change to agility.json.
 */
class AgilityReachAuditTest {
    /**
     * Each route's start reaches its obstacle's loc by the route finder, as a click from there would, unless the obstacle
     * is used from its starts ([osrsps.game.skill.Obstacle.fromStart]), where standing on one counts as reached.
     */
    @Test
    fun `every obstacle's loc is reached from its routes' starts`() {
        val map = RealMap.load().map
        val finder = RouteFinder(map.collision)
        val problems = ArrayList<String>()
        for (obstacle in AgilityData.load().obstacles) {
            if (obstacle.fromStart) continue
            for (route in obstacle.routes) {
                val from = route.from
                val loc = locFor(map.locs::at, obstacle.locs, from.x, from.z, from.level, route.at.ifEmpty { listOfNotNull(obstacle.at) }.map { it.x to it.z })
                if (loc == null) {
                    problems += "${obstacle.key}: no loc ${obstacle.locs} near ${from.x}, ${from.z}, ${from.level}"
                    continue
                }
                val type = map.locTypes[loc.id]
                val target = RouteTarget.loc(loc.x, loc.z, loc.shape, loc.angle, type?.width ?: 1, type?.length ?: 1, type?.accessBlock ?: 0)
                val path = finder.find(from.level, from.x, from.z, target)
                if (path.alternative) {
                    problems += "${obstacle.key}: loc ${loc.id} at ${loc.x}, ${loc.z}, ${loc.level} not reached from ${from.x}, ${from.z}, ${from.level} (stops at ${path.destination})"
                }
            }
        }
        assertEquals(emptyList(), problems)
    }

    /** The nearest loc of [ids] to [x], [z] on [level], within [RADIUS], on one of [tiles] when any are given. */
    private fun locFor(at: (Int, Int, Int) -> List<ZoneLoc>, ids: List<Int>, x: Int, z: Int, level: Int, tiles: List<Pair<Int, Int>>): ZoneLoc? {
        val found = ArrayList<ZoneLoc>()
        for (dx in -RADIUS..RADIUS) for (dz in -RADIUS..RADIUS) {
            found += at(x + dx, z + dz, level).filter { it.id in ids }
        }
        return found.filter { tiles.isEmpty() || (it.x to it.z) in tiles }.minByOrNull { maxOf(abs(it.x - x), abs(it.z - z)) }
    }

    private companion object {
        const val RADIUS = 12
    }
}
