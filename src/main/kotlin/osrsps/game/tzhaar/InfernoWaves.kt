package osrsps.game.tzhaar

import osrsps.map.Tile

/**
 * The Inferno's monsters (the Wiki's Inferno, its monster table, read 2026-11-17), by the names the Wiki's wave table
 * gives them, with the cache's NPC type of each (`inferno_nibbler`, `inferno_creature_harpie` and so on).
 */
enum class InfernoMonster(val type: Int) {
    /** Jal-Nib, which goes for the pillars ([InfernoPillars]). */
    NIBBLER(7691),

    /** Jal-MejRah. */
    BAT(7692),

    /** Jal-Ak. */
    BLOB(7693),

    /** Jal-ImKot. */
    MELEE(7697),

    /** Jal-Xil. */
    RANGER(7698),

    /** Jal-Zek. */
    MAGE(7699),

    /** JalTok-Jad. */
    JAD(7700),

    /** TzKal-Zuk. */
    ZUK(7706),
}

/**
 * The nine places the Inferno's monsters come in at, Nibblers aside. GUESSED tiles: three rows of three across the
 * arena north of the middle, clear of the pillars ([InfernoPillars.TILES]) for monsters up to JalTok-Jad's 5 by 5, until
 * a recording of the waves pins them. Each is the south-west tile of a monster coming in there, in the Inferno's own map
 * square (35_83, x 2240, z 5312 at its corner).
 */
enum class InfernoSpot(val tile: Tile) {
    NW(Tile(2260, 5353)),
    N(Tile(2268, 5353)),
    NE(Tile(2278, 5353)),
    W(Tile(2261, 5344)),
    C(Tile(2269, 5344)),
    E(Tile(2279, 5345)),
    SW(Tile(2259, 5338)),
    S(Tile(2273, 5338)),
    SE(Tile(2280, 5338)),
}

/**
 * Which monsters each of the Inferno's 69 waves brings: waves 1 to 68 as the Wiki's Inferno wave table lists them
 * (read 2026-11-17), wave 69 TzKal-Zuk. The monsters other than the Nibblers each take a different [InfernoSpot],
 * picked from [osrsps.game.World.random] (guessed: live's spawn pattern isn't on the Wiki); the Nibblers come in
 * together in the middle ([NIBBLER_TILE], the Wiki: "They spawn in the middle").
 */
object InfernoWaves {
    const val WAVES = 69

    /** The last wave with Nibblers and pillars: the pillars still standing fall as it ends. */
    const val LAST_PILLAR_WAVE = 66

    const val JAD_WAVE = 67
    const val TRIPLE_JAD_WAVE = 68
    const val ZUK_WAVE = 69

    /** Where the Nibblers come in: between the three pillars. Guessed tile. */
    val NIBBLER_TILE = Tile(2266, 5345)

    /** The Wiki's wave table, wave 1 first, as "count MONSTER" lists. */
    private val TABLE: List<String> = listOf(
        "3 NIBBLER, 1 BAT", // 1
        "3 NIBBLER, 2 BAT", // 2
        "6 NIBBLER", // 3
        "3 NIBBLER, 1 BLOB", // 4
        "3 NIBBLER, 1 BLOB, 1 BAT", // 5
        "3 NIBBLER, 1 BLOB, 2 BAT", // 6
        "3 NIBBLER, 2 BLOB", // 7
        "6 NIBBLER", // 8
        "3 NIBBLER, 1 MELEE", // 9
        "3 NIBBLER, 1 MELEE, 1 BAT", // 10
        "3 NIBBLER, 1 MELEE, 2 BAT", // 11
        "3 NIBBLER, 1 MELEE, 1 BLOB", // 12
        "3 NIBBLER, 1 MELEE, 1 BLOB, 1 BAT", // 13
        "3 NIBBLER, 1 MELEE, 1 BLOB, 2 BAT", // 14
        "3 NIBBLER, 1 MELEE, 2 BLOB", // 15
        "3 NIBBLER, 2 MELEE", // 16
        "6 NIBBLER", // 17
        "3 NIBBLER, 1 RANGER", // 18
        "3 NIBBLER, 1 RANGER, 1 BAT", // 19
        "3 NIBBLER, 1 RANGER, 2 BAT", // 20
        "3 NIBBLER, 1 RANGER, 1 BLOB", // 21
        "3 NIBBLER, 1 RANGER, 1 BLOB, 1 BAT", // 22
        "3 NIBBLER, 1 RANGER, 1 BLOB, 2 BAT", // 23
        "3 NIBBLER, 1 RANGER, 2 BLOB", // 24
        "3 NIBBLER, 1 RANGER, 1 MELEE", // 25
        "3 NIBBLER, 1 RANGER, 1 MELEE, 1 BAT", // 26
        "3 NIBBLER, 1 RANGER, 1 MELEE, 2 BAT", // 27
        "3 NIBBLER, 1 RANGER, 1 MELEE, 1 BLOB", // 28
        "3 NIBBLER, 1 RANGER, 1 MELEE, 1 BLOB, 1 BAT", // 29
        "3 NIBBLER, 1 RANGER, 1 MELEE, 1 BLOB, 2 BAT", // 30
        "3 NIBBLER, 1 RANGER, 1 MELEE, 2 BLOB", // 31
        "3 NIBBLER, 1 RANGER, 2 MELEE", // 32
        "3 NIBBLER, 2 RANGER", // 33
        "6 NIBBLER", // 34
        "3 NIBBLER, 1 MAGE", // 35
        "3 NIBBLER, 1 MAGE, 1 BAT", // 36
        "3 NIBBLER, 1 MAGE, 2 BAT", // 37
        "3 NIBBLER, 1 MAGE, 1 BLOB", // 38
        "3 NIBBLER, 1 MAGE, 1 BLOB, 1 BAT", // 39
        "3 NIBBLER, 1 MAGE, 1 BLOB, 2 BAT", // 40
        "3 NIBBLER, 1 MAGE, 2 BLOB", // 41
        "3 NIBBLER, 1 MAGE, 1 MELEE", // 42
        "3 NIBBLER, 1 MAGE, 1 MELEE, 1 BAT", // 43
        "3 NIBBLER, 1 MAGE, 1 MELEE, 2 BAT", // 44
        "3 NIBBLER, 1 MAGE, 1 MELEE, 1 BLOB", // 45
        "3 NIBBLER, 1 MAGE, 1 MELEE, 1 BLOB, 1 BAT", // 46
        "3 NIBBLER, 1 MAGE, 1 MELEE, 1 BLOB, 2 BAT", // 47
        "3 NIBBLER, 1 MAGE, 1 MELEE, 2 BLOB", // 48
        "3 NIBBLER, 1 MAGE, 2 MELEE", // 49
        "3 NIBBLER, 1 MAGE, 1 RANGER", // 50
        "3 NIBBLER, 1 MAGE, 1 RANGER, 1 BAT", // 51
        "3 NIBBLER, 1 MAGE, 1 RANGER, 2 BAT", // 52
        "3 NIBBLER, 1 MAGE, 1 RANGER, 1 BLOB", // 53
        "3 NIBBLER, 1 MAGE, 1 RANGER, 1 BLOB, 1 BAT", // 54
        "3 NIBBLER, 1 MAGE, 1 RANGER, 1 BLOB, 2 BAT", // 55
        "3 NIBBLER, 1 MAGE, 1 RANGER, 2 BLOB", // 56
        "3 NIBBLER, 1 MAGE, 1 RANGER, 1 MELEE", // 57
        "3 NIBBLER, 1 MAGE, 1 RANGER, 1 MELEE, 1 BAT", // 58
        "3 NIBBLER, 1 MAGE, 1 RANGER, 1 MELEE, 2 BAT", // 59
        "3 NIBBLER, 1 MAGE, 1 RANGER, 1 MELEE, 1 BLOB", // 60
        "3 NIBBLER, 1 MAGE, 1 RANGER, 1 MELEE, 1 BLOB, 1 BAT", // 61
        "3 NIBBLER, 1 MAGE, 1 RANGER, 1 MELEE, 1 BLOB, 2 BAT", // 62
        "3 NIBBLER, 1 MAGE, 1 RANGER, 1 MELEE, 2 BLOB", // 63
        "3 NIBBLER, 1 MAGE, 1 RANGER, 2 MELEE", // 64
        "3 NIBBLER, 1 MAGE, 2 RANGER", // 65
        "3 NIBBLER, 2 MAGE", // 66
        "1 JAD", // 67
        "3 JAD", // 68
        "1 ZUK", // 69
    )

    /** Wave [wave]'s monsters, as the Wiki lists them (Nibblers first). */
    fun monsters(wave: Int): List<InfernoMonster> {
        require(wave in 1..WAVES) { "No wave $wave" }
        return TABLE[wave - 1].split(", ").flatMap { entry ->
            val (count, name) = entry.split(" ")
            List(count.toInt()) { InfernoMonster.valueOf(name) }
        }
    }

    /**
     * TokKul for [waves] waves done, TzKal-Zuk's win aside. GUESSED: the Wiki gives only the most, 16,440 for a win
     * ([Inferno.WIN_TOKKUL]), not how a death or a walk out pays; ours pays twice the Fight Cave's for the same
     * number of waves, 2 N (N + 1).
     */
    fun tokkul(waves: Int): Int = 2 * waves * (waves + 1)
}
