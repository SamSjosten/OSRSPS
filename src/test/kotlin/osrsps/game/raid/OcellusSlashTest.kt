package osrsps.game.raid

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.map.Tile

class OcellusSlashTest {
    @Test
    fun `a superior slash leaves no holes, straight or diagonal`() {
        val mid = Tile(100, 100)
        val half = 2
        for (dx in -1..1) for (dz in -1..1) {
            if (dx == 0 && dz == 0) continue
            val rows = Ocellus.slashRows(mid, half, dx, dz)
            assertEquals(Ocellus.INSOLENCE_REACH, rows.size)
            val wave = rows.flatten().toSet()
            // Diagonal: along its middle, from its first row to its last, every tile within a tile and a half across
            // (in doubled units, p along and q across) is in it.
            if (dx != 0 && dz != 0) for (p in (half + 1) * 2..(half + Ocellus.INSOLENCE_REACH) * 2) for (q in -2..2) {
                if ((p + q) % 2 != 0 || (p % 2 == 1 && abs(q) > 1)) continue
                val tile = Tile(mid.x + (dx * p - dz * q) / 2, mid.z + (dz * p + dx * q) / 2)
                assertTrue(tile in wave, "($dx, $dz): $tile")
            }
            if (dx != 0 && dz != 0) {
                // A tile whose four sides are all in the wave is in it too.
                for (tile in wave) for ((ox, oz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val n = Tile(tile.x + ox, tile.z + oz)
                    if (n in wave) continue
                    val sides = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).count { (a, b) -> Tile(n.x + a, n.z + b) in wave }
                    assertTrue(sides < 4, "($dx, $dz): a hole at $n")
                }
            } else {
                assertEquals(Ocellus.INSOLENCE_REACH * Ocellus.INSOLENCE_WIDTH, wave.size)
            }
            assertTrue(wave.all { abs(it.x - mid.x) > half || abs(it.z - mid.z) > half }, "none under him")
        }
    }
}
