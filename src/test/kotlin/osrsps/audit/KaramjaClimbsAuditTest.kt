package osrsps.audit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.content.BarnabyShip
import osrsps.game.content.Destination
import osrsps.game.content.KaramjaVolcano
import osrsps.game.content.MorUlRek

/**
 * The Karamja climbs teleport only from their own tile (exploit review R50B-1): on the real 242 map each type is placed
 * once, on the tile its handler expects, so no other copy is left to click. Skipped without the cache, as in CI.
 */
class KaramjaClimbsAuditTest {
    @Test
    fun `each Karamja climb is placed once, on its own tile`() {
        val loaded = RealMap.load()
        val expected = KaramjaVolcano.PLACED + MorUlRek.PLACED
        val found = HashMap<Int, MutableList<Destination>>()
        for (square in loaded.squares) for (level in 0..3) for (zx in 0 until 8) for (zz in 0 until 8) {
            loaded.map.locs.forEachInZone((square.x shl 6) + zx * 8, (square.z shl 6) + zz * 8, level) { loc ->
                if (loc.id in expected) found.getOrPut(loc.id) { mutableListOf() } += Destination(loc.x, loc.z, loc.level)
            }
        }
        for ((id, at) in expected) assertEquals<List<Destination>>(listOf(at), found[id].orEmpty(), "placements of loc $id")
    }

    @Test
    fun `Captain Barnaby's gangplanks stand where their handlers expect`() {
        val loaded = RealMap.load()
        val expected = BarnabyShip.Dock.entries.flatMap { listOf(it.plankOn to it.plankOnAt, it.plankOff to it.plankOffAt) }
        for ((id, at) in expected) {
            val there = loaded.map.locs.at(at.x, at.z, at.level).any { it.id == id }
            assertTrue(there, "gangplank $id at $at")
        }
    }
}
