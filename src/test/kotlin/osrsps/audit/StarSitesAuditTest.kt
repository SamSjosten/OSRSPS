package osrsps.audit

import osrsps.game.skill.ShootingStars
import osrsps.game.skill.StarSites
import osrsps.map.CollisionFlag
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Checks every shooting star site ([StarSites]) against the real map (see [RealMap]; skipped without it, as in CI): the
 * star's 2x2 tiles are open ground with no loc of the star's layer on them, so landing replaces nothing and blocks no
 * path that wasn't open. Prints the ground around each site: `#` blocked, `L` a ground loc, `.` open, `S` the star.
 */
class StarSitesAuditTest {
    @Test
    fun `every star site is open ground the star fits on`() {
        val map = RealMap.load().map
        val bad = mutableListOf<String>()
        for (site in StarSites.load().all) {
            val star = (0 until ShootingStars.SIZE).flatMap { dx -> (0 until ShootingStars.SIZE).map { dz -> (site.x + dx) to (site.z + dz) } }
            fun blocked(x: Int, z: Int) = map.collision[x, z, 0] and CollisionFlag.BLOCK_WALK != 0
            fun groundLoc(x: Int, z: Int) = map.locs.at(x, z, 0, GROUND_LAYER) != null
            println("${site.key} at ${site.x}, ${site.z}:")
            for (z in site.z + 4 downTo site.z - 3) {
                println((site.x - 3..site.x + 4).joinToString("") { x ->
                    when {
                        (x to z) in star && (blocked(x, z) || groundLoc(x, z)) -> "X"
                        (x to z) in star -> "S"
                        groundLoc(x, z) -> "L"
                        blocked(x, z) -> "#"
                        else -> "."
                    }
                })
            }
            for ((x, z) in star) {
                if (blocked(x, z)) bad += "${site.key}: $x, $z is blocked"
                if (groundLoc(x, z)) bad += "${site.key}: $x, $z has ${map.locs.at(x, z, 0, GROUND_LAYER)}"
            }
        }
        assertEquals(emptyList<String>(), bad)
    }

    private companion object {
        /** Ground locs: the layer a star's loc goes in. */
        const val GROUND_LAYER = 2
    }
}
