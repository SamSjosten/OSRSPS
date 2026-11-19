package osrsps.game.tzhaar

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.tzhaar.FightCaveMonster.KET_ZEK
import osrsps.game.tzhaar.FightCaveMonster.TOK_XIL
import osrsps.game.tzhaar.FightCaveMonster.TZTOK_JAD
import osrsps.game.tzhaar.FightCaveMonster.TZ_KEK
import osrsps.game.tzhaar.FightCaveMonster.TZ_KIH
import osrsps.game.tzhaar.FightCaveMonster.YT_MEJKOT

/** Fight Caves step FC1: the waves and their spawn spots against the Wiki's tables. */
class FightCaveWavesTest {
    /** The Wiki's TzHaar Fight Cave wave table (read 2026-11-17), one wave a line, its monsters by level. */
    private val wiki = """
        22
        22 22
        45
        45 22
        45 22 22
        45 45
        90
        90 22
        90 22 22
        90 45
        90 45 22
        90 45 22 22
        90 45 45
        90 90
        180
        180 22
        180 22 22
        180 45
        180 45 22
        180 45 22 22
        180 45 45
        180 90
        180 90 22
        180 90 22 22
        180 90 45
        180 90 45 22
        180 90 45 22 22
        180 90 45 45
        180 90 90
        180 180
        360
        360 22
        360 22 22
        360 45
        360 45 22
        360 45 22 22
        360 45 45
        360 90
        360 90 22
        360 90 22 22
        360 90 45
        360 90 45 22
        360 90 45 22 22
        360 90 45 45
        360 90 90
        360 180
        360 180 22
        360 180 22 22
        360 180 45
        360 180 45 22
        360 180 45 22 22
        360 180 45 45
        360 180 90
        360 180 90 22
        360 180 90 22 22
        360 180 90 45
        360 180 90 45 22
        360 180 90 45 22 22
        360 180 90 45 45
        360 180 90 90
        360 180 180
        360 360
        702
    """.trimIndent().lines()

    @Test
    fun `every wave brings the Wiki's monsters`() {
        assertEquals(FightCaveWaves.WAVES, wiki.size)
        for ((i, line) in wiki.withIndex()) {
            val wave = i + 1
            assertEquals(line.split(' ').map(String::toInt), FightCaveWaves.monsters(wave).map { it.level }, "wave $wave")
        }
        assertEquals(listOf(KET_ZEK, YT_MEJKOT, TOK_XIL, TZ_KEK, TZ_KIH, TZ_KIH), FightCaveWaves.monsters(58))
        assertEquals(listOf(TZTOK_JAD), FightCaveWaves.monsters(63))
    }

    @Test
    fun `each rotation's first waves come in where the Wiki's Rotations page says`() {
        // Wave 1, wave 2's two, wave 3; the Wiki's rows in order (rotations 1 to 15).
        val rows = listOf(
            "C NW SW SW", "C S SE SE", "C SE SW SW", "NW C S S", "NW C SE SE", "NW SW SE SE", "S NW C C", "S NW C C",
            "S SE SW SW", "SE S NW NW", "SE SW C C", "SE SW S S", "SW C NW NW", "SW S NW NW", "SW SE S S",
        )
        for ((i, row) in rows.withIndex()) {
            val rotation = i + 1
            val got = (1..3).flatMap { FightCaveWaves.spots(it, rotation) }.joinToString(" ")
            assertEquals(row, got, "rotation $rotation")
        }
        // The two the first three waves can't tell apart differ at wave 4 (the Wiki's rotations 7 and 8).
        assertEquals(listOf(FightCaveSpot.S, FightCaveSpot.SE), FightCaveWaves.spots(4, 7))
        assertEquals(listOf(FightCaveSpot.SE, FightCaveSpot.SW), FightCaveWaves.spots(4, 8))
        assertEquals((0 until 15).toSet(), FightCaveWaves.ROTATION_STARTS.toSet(), "every start of the cycle is a rotation")
    }

    @Test
    fun `TzTok-Jad comes in where wave 3's monster and wave 62's second Ket-Zek did`() {
        for (rotation in 1..FightCaveWaves.ROTATIONS) {
            val jad = FightCaveWaves.spots(63, rotation).single()
            assertEquals(FightCaveWaves.spots(3, rotation).single(), jad, "rotation $rotation")
            assertEquals(listOf(KET_ZEK, KET_ZEK), FightCaveWaves.monsters(62))
            assertEquals(jad, FightCaveWaves.spots(62, rotation)[1], "rotation $rotation")
        }
    }

    @Test
    fun `a whole run pays 8,032 Tokkul and none before the first wave is done`() {
        assertEquals(0, FightCaveWaves.tokkul(0))
        assertEquals(2, FightCaveWaves.tokkul(1))
        assertEquals(6, FightCaveWaves.tokkul(2))
        assertEquals(8_032, FightCaveWaves.tokkul(FightCaveWaves.WAVES) + FightCaves.JAD_TOKKUL)
    }
}
