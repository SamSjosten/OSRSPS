package osrsps.game.skill.farming

/**
 * The kinds of farming patch, each with how often its crops grow: every [cycleTicks] of the 5-minute farming ticks
 * ([FarmingClock]). Cycles from the OSRS Wiki, Farming (read 2026-11-14): allotments 10 minutes. [seeds] is how many
 * seeds one planting takes (the Wiki's Allotment patch: three). [weeds] is how many weed stages an empty patch grows
 * back, one each 5-minute tick (the Wiki's Weeds: 5 minutes), and so how many times a full patch can be raked.
 */
enum class PatchKind(val cycleTicks: Int, val seeds: Int, val weeds: Int = 3) {
    ALLOTMENT(cycleTicks = 2, seeds = 3),
}

/**
 * One crop: the seed planted ([seed], obj), what it gives ([produce]), the Farming [level] to plant it, the XP for
 * planting and for each crop harvested (tenths), and its chance to keep a harvest life ([ctsLow] at level 1, [ctsHigh]
 * at 99, through [osrsps.game.skill.SuccessRoll]'s formula, which is the Wiki's Variable crop yield formula).
 *
 * The rest is how the patch's varbit draws it, read from the cache: each patch is a multiloc whose varbit value picks
 * the loc the client shows, and the shown locs' names say what each value is (cache 2026-11-11, rev 242, the allotment
 * multiloc 8550; the same layout RuneLite's Time Tracking plugin reads back). [growing] is the value for each growth
 * stage, [watered] the same stages watered, [harvest] the first of the three fully grown values, [diseased] and [dead]
 * the values for stages 1 on (a crop can't fall ill as a seed). [stages] is how many times it grows before it can be
 * harvested.
 */
class Crop(
    val key: String,
    val kind: PatchKind,
    val seed: Int,
    val produce: Int,
    val level: Int,
    val plantXpTenths: Int,
    val harvestXpTenths: Int,
    val ctsLow: Int,
    val ctsHigh: Int,
    val growing: List<Int>,
    val watered: List<Int>,
    val harvest: Int,
    val diseased: List<Int>,
    val dead: List<Int>,
) {
    val stages: Int get() = growing.size

    init {
        require(watered.isEmpty() || watered.size == stages) { "$key: ${watered.size} watered values for $stages stages" }
        require(diseased.size == stages - 1 && dead.size == stages - 1) { "$key: diseased and dead values cover stages 1 on" }
    }
}

/** The crops and their numbers. */
object Crops {
    private fun run(first: Int, count: Int) = List(count) { first + it }

    /**
     * An allotment crop. Levels and XP from each seed's Wiki page (read 2026-11-14); the chance to keep a life from the
     * same pages' "scales from x% at level 1 to y% at level 99", worked back to the 256ths the formula gives (39.8% is
     * (1 + 101)/256). [growing] values run from [first], watered ones from [wateredFirst].
     */
    private fun allotment(
        key: String, seed: Int, produce: Int, level: Int, plant: Int, harvestXp: Int, low: Int, high: Int,
        stages: Int, first: Int, wateredFirst: Int, harvest: Int, diseasedFirst: Int, deadFirst: Int,
    ) = Crop(
        key, PatchKind.ALLOTMENT, seed, produce, level, plant, harvestXp, low, high,
        run(first, stages), run(wateredFirst, stages), harvest, run(diseasedFirst, stages - 1), run(deadFirst, stages - 1),
    )

    val POTATO = allotment("potato", 5318, 1942, 1, 80, 90, 101, 180, 4, 6, 70, 10, 135, 199)
    val ONION = allotment("onion", 5319, 1957, 5, 95, 105, 105, 180, 4, 13, 77, 17, 142, 206)
    val CABBAGE = allotment("cabbage", 5324, 1965, 7, 100, 115, 107, 180, 4, 20, 84, 24, 149, 213)
    val TOMATO = allotment("tomato", 5322, 1982, 12, 125, 140, 112, 180, 4, 27, 91, 31, 156, 220)
    val SWEETCORN = allotment("sweetcorn", 5320, 5986, 20, 170, 190, 88, 180, 6, 34, 98, 40, 163, 227)
    val STRAWBERRY = allotment("strawberry", 5323, 5504, 31, 260, 290, 103, 180, 6, 43, 107, 49, 172, 236)
    val WATERMELON = allotment("watermelon", 5321, 5982, 47, 485, 545, 126, 180, 8, 52, 116, 60, 181, 245)

    /** Snape grass breaks the pattern: its watered stages sit at 128 and its ill and dead stages are split in two runs. */
    val SNAPE_GRASS = Crop(
        "snape_grass", PatchKind.ALLOTMENT, 22879, 231, 61, 820, 820, 148, 195,
        run(63, 7), run(128, 7), 138, run(196, 3) + run(202, 3), run(193, 3) + run(209, 3),
    )

    val ALL = listOf(POTATO, ONION, CABBAGE, TOMATO, SWEETCORN, STRAWBERRY, WATERMELON, SNAPE_GRASS)

    private val byKey = ALL.associateBy { it.key }
    private val bySeed = ALL.associateBy { it.seed }

    fun byKey(key: String): Crop? = byKey[key]

    fun bySeed(seed: Int): Crop? = bySeed[seed]
}

/** One patch: the loc the map places, the varbit that draws it, and its kind. [key] names it in saves. */
class FarmingPatch(val key: String, val loc: Int, val varbit: Int, val kind: PatchKind)

/**
 * Where a set of patches is drawn from: the map squares ([regions], as `(x shr 6) shl 8 or (z shr 6)`) in which the
 * server sends their varbits, and [contains] for areas that share a square. Live sends the same few varbits
 * (`farming_transmit_a` to `e`) for whichever patches are near, so the varbits only mean something with the area.
 */
class FarmingArea(val name: String, val regions: Set<Int>, val patches: List<FarmingPatch>, val contains: (x: Int, z: Int) -> Boolean = { _, _ -> true })

/**
 * Every patch built so far. The varbits are the cache's `farming_transmit_a` to `e` (4771 to 4775; the patch locs'
 * multilocs name them). Which squares send which patches is RuneLite's Time Tracking plugin's FarmingWorld, which was
 * worked out by watching live's varbits, so its farming panel reads ours as it reads live's.
 */
object FarmingPatches {
    const val TRANSMIT_A = 4771
    const val TRANSMIT_B = 4772
    const val TRANSMIT_C = 4773
    const val TRANSMIT_D = 4774
    const val TRANSMIT_E = 4775

    /** `farming_veg_patch_1` and `_2`: the allotments south of Falador, north-west (3050-3054, 3307-3312) and south-east. */
    val FALADOR_NW = FarmingPatch("falador_allotment_nw", 8550, TRANSMIT_A, PatchKind.ALLOTMENT)
    val FALADOR_SE = FarmingPatch("falador_allotment_se", 8551, TRANSMIT_B, PatchKind.ALLOTMENT)

    /** The Falador farming patches: map square 12083 north of z 3272 (Port Sarim's spirit tree patch is south of it). */
    val FALADOR = FarmingArea("Falador", setOf(12083), listOf(FALADOR_NW, FALADOR_SE)) { _, z -> z >= 3272 }

    val AREAS = listOf(FALADOR)

    val ALL: List<FarmingPatch> = AREAS.flatMap { it.patches }

    private val byKey = ALL.associateBy { it.key }

    /** Every patch loc, for registering handlers: a loc type can stand in more than one place. */
    val LOCS: Set<Int> = ALL.mapTo(HashSet()) { it.loc }

    fun byKey(key: String): FarmingPatch? = byKey[key]

    /** The patch that loc [loc] standing at [x], [z] is, or null if it isn't one we've built. */
    fun at(loc: Int, x: Int, z: Int): FarmingPatch? = areaAt(x, z)?.patches?.firstOrNull { it.loc == loc }

    /** The area whose patches [x], [z] sees, or null outside them all. */
    fun areaAt(x: Int, z: Int): FarmingArea? {
        val region = ((x shr 6) shl 8) or (z shr 6)
        return AREAS.firstOrNull { region in it.regions && it.contains(x, z) }
    }

    init {
        require(byKey.size == ALL.size) { "Two farming patches share a key" }
        require(AREAS.all { area -> area.patches.distinctBy { it.loc }.size == area.patches.size }) { "An area has two patches on one loc" }
    }
}
