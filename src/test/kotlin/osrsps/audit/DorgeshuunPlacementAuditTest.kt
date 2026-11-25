package osrsps.audit

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.content.Destination
import osrsps.game.content.DorgeshuunTunnels

/**
 * The water mill cellar's trapdoor, ladder and tunnels move a player only from their own tile (exploit review R52A-2):
 * on the real map each type is placed once, on the tile its handler expects. Skipped without the cache, as in CI.
 */
class DorgeshuunPlacementAuditTest {
    @Test
    fun `each water mill cellar climb and tunnel is placed once, on its own tile`() {
        val loaded = RealMap.load()
        val expected = DorgeshuunTunnels.PLACED
        val found = HashMap<Int, MutableList<Destination>>()
        for (square in loaded.squares) for (level in 0..3) for (zx in 0 until 8) for (zz in 0 until 8) {
            loaded.map.locs.forEachInZone((square.x shl 6) + zx * 8, (square.z shl 6) + zz * 8, level) { loc ->
                if (loc.id in expected) found.getOrPut(loc.id) { mutableListOf() } += Destination(loc.x, loc.z, loc.level)
            }
        }
        for ((id, at) in expected) assertEquals<List<Destination>>(listOf(at), found[id].orEmpty(), "placements of loc $id")
    }
}
