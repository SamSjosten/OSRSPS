package osrsps.game.tzhaar

import osrsps.map.Tile

/**
 * The TzHaar Fight Cave's monsters, weakest first: each has a [weight], and a wave's monsters weigh as much as its number
 * ("The monster weights add up to the current wave number", the Wiki's TzHaar Fight Cave/Strategies, in a skew binary
 * count of 1, 3, 7, 15, 31 and 63). [first] and [second] are the cache's two NPC types of each (`tzhaar_fightcave_swarm_
 * 1a` and `_1b` and so on).
 */
enum class FightCaveMonster(val weight: Int, val level: Int, val first: Int, val second: Int) {
    TZ_KIH(1, 22, 3116, 3117),
    TZ_KEK(3, 45, 3118, 3119),
    TOK_XIL(7, 90, 3121, 3122),
    YT_MEJKOT(15, 180, 3123, 3124),
    KET_ZEK(31, 360, 3125, 3126),
    TZTOK_JAD(63, 702, 3127, 3127),
    ;

    companion object {
        /** The heaviest first, as a wave is made up and as its monsters take the spawn spots. */
        val HEAVIEST_FIRST: List<FightCaveMonster> = entries.sortedByDescending { it.weight }
    }
}

/** The five places the cave's monsters come in at, as the Wiki's TzHaar Fight Cave/Rotations names them. */
enum class FightCaveSpot(
    /** The south-west tile of a monster coming in here, in the cave's own map square (x 2368, z 5056 at its corner). */
    val tile: Tile,
) {
    // Guessed tiles, to within a tile or two: read off the per-wave spawn maps of the fight-cave-helper Plugin Hub
    // plugin (runkev/fight-cave-helper, waves/*.jpg, 4.7 pixels to the tile, lined up on the cache's walls), each the
    // south-west tile of a 5x5 box round where the monsters are drawn. A recording of the waves would pin them.
    NW(Tile(2376, 5104)),
    C(Tile(2393, 5087)),
    SE(Tile(2415, 5078)),
    SW(Tile(2380, 5070)),
    S(Tile(2404, 5069)),
}

/**
 * Which monsters each wave brings and where they come in. Waves 1 to 62 weigh their own number, made up greedily from
 * the heaviest monster down (Tz-Kek and Tz-Kih may come twice), which gives the Wiki's table of all 63 waves; wave 63
 * is TzTok-Jad alone, his healers coming later in the fight.
 *
 * Spots follow one cycle of fifteen ([CYCLE], the Wiki's TzHaar Fight Cave: "SE, SW, C, NW, SW, SE, S, NW, C, SE, SW,
 * S, NW, C, S"): each wave the heaviest monster takes the next spot of the cycle and the others the spots after it. A
 * rotation is where in the cycle wave 1 starts; [ROTATION_STARTS] numbers them as the Wiki's Rotations page does, each
 * one checked against that page's first waves. So TzTok-Jad comes in where wave 3's monster did, and where wave 62's
 * second Ket-Zek did, as the Wiki says.
 */
object FightCaveWaves {
    const val WAVES = 63
    const val ROTATIONS = 15

    val CYCLE: List<FightCaveSpot> = listOf(
        FightCaveSpot.SE, FightCaveSpot.SW, FightCaveSpot.C, FightCaveSpot.NW, FightCaveSpot.SW,
        FightCaveSpot.SE, FightCaveSpot.S, FightCaveSpot.NW, FightCaveSpot.C, FightCaveSpot.SE,
        FightCaveSpot.SW, FightCaveSpot.S, FightCaveSpot.NW, FightCaveSpot.C, FightCaveSpot.S,
    )

    /**
     * Where in [CYCLE] wave 1 of each rotation (1 to 15, the Wiki's numbering) starts. Rotations 7 and 8 share their
     * first three waves, as the Wiki says; this has 7 as "S SE" on wave 4 and 8 as "SE SW", which the cycle gives one
     * way round only.
     */
    val ROTATION_STARTS: List<Int> = listOf(2, 13, 8, 12, 7, 3, 11, 6, 14, 5, 0, 9, 1, 10, 4)

    /** Wave [wave]'s monsters, the heaviest first. */
    fun monsters(wave: Int): List<FightCaveMonster> {
        require(wave in 1..WAVES) { "No wave $wave" }
        if (wave == WAVES) return listOf(FightCaveMonster.TZTOK_JAD)
        val out = ArrayList<FightCaveMonster>()
        var left = wave
        for (monster in FightCaveMonster.HEAVIEST_FIRST) {
            if (monster == FightCaveMonster.TZTOK_JAD) continue
            while (left >= monster.weight) {
                out += monster
                left -= monster.weight
            }
        }
        return out
    }

    /** The spots of wave [wave]'s monsters in [rotation] (1 to 15), in the order [monsters] gives them. */
    fun spots(wave: Int, rotation: Int): List<FightCaveSpot> {
        require(rotation in 1..ROTATIONS) { "No rotation $rotation" }
        val start = ROTATION_STARTS[rotation - 1] + wave - 1
        return monsters(wave).indices.map { CYCLE[(start + it) % CYCLE.size] }
    }

    /** Tokkul for [waves] waves done, Jad's own not counted: 2 + 4 + 6 + ... (the Wiki's TzHaar Fight Cave). */
    fun tokkul(waves: Int): Int = waves * (waves + 1)
}
