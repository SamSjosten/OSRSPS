package osrsps.game.skill.farming

/**
 * The kinds of farming patch, each with how often its crops grow: every [cycleTicks] of the 5-minute farming ticks
 * ([FarmingClock]). Cycles from the OSRS Wiki, Farming (read 2026-11-14): allotments 10 minutes, herbs 20, flowers 5.
 * [seeds] is how many seeds one planting takes (the Wiki's Allotment patch: three; herbs and flowers one). [weeds] is
 * how many weed stages an empty patch grows back, one each 5-minute tick (the Wiki's Weeds: 5 minutes), and so how
 * many times a full patch can be raked. [lives] is how many harvest lives a crop has when it finishes growing (three;
 * a flower is picked once), and [harvestValues] how many varbit values draw it grown. [spade]: harvested with a spade
 * (allotments and herbs, the Wiki's Herb patch: "a spade to harvest herbs"); flowers are picked by hand.
 *
 * [disease] is the chance in 128 that a growing crop falls ill instead of growing, each growth past its first (the
 * Wiki's Disease page: herbs 27/128). A diseased crop dies at its next tick of the next slower clock ([diseaseCycleTicks],
 * twice the cycle for these three: the same page, "one tier slower"). [composting]: compost adds harvest lives.
 * Guessed: allotments and flowers falling ill as often as herbs (the Wiki gives no figure for them).
 *
 * Trees ([TREE], step 4; the Wiki's Tree patch and each tree's page, read 2026-11-14) grow every 40 minutes, are planted
 * as one sapling with a spade, aren't watered and have no harvest lives: once grown they are checked, chopped to a
 * stump and the stump dug up. Their chance to fall ill is each tree's own ([Crop.disease]).
 */
enum class PatchKind(
    val cycleTicks: Int, val seeds: Int, val lives: Int, val harvestValues: Int, val spade: Boolean, val disease: Int,
    val composting: Boolean, val weeds: Int = 3,
) {
    ALLOTMENT(cycleTicks = 2, seeds = 3, lives = 3, harvestValues = 3, spade = true, disease = 27, composting = true),
    HERB(cycleTicks = 4, seeds = 1, lives = 3, harvestValues = 3, spade = true, disease = 27, composting = true),
    FLOWER(cycleTicks = 1, seeds = 1, lives = 1, harvestValues = 1, spade = false, disease = 27, composting = false),
    TREE(cycleTicks = 8, seeds = 1, lives = 1, harvestValues = 1, spade = true, disease = 0, composting = false);

    val diseaseCycleTicks: Int get() = cycleTicks * 2
}

/**
 * Compost on a patch (OSRS Wiki, Compost, Supercompost, Ultracompost and Disease, read 2026-11-14): each bucket's XP, the
 * harvest lives it adds to allotments and herbs (Mod Ash: "4 for standard compost, 5 for super compost, 6 for the
 * ultra one"), and the disease chance it leaves, in tenths: compost halves it, supercompost takes 80% off and
 * ultracompost 90%, rounded up to a 128th (the Disease page's table: herbs 27, 14, 6, 3).
 */
object Compost {
    const val NONE = 0
    const val COMPOST = 1
    const val SUPER = 2
    const val ULTRA = 3

    const val BUCKET = 6032
    const val SUPER_BUCKET = 6034
    const val ULTRA_BUCKET = 21483
    const val EMPTY_BUCKET = 1925

    /** The compost level each bucket gives. */
    val BUCKETS = mapOf(BUCKET to COMPOST, SUPER_BUCKET to SUPER, ULTRA_BUCKET to ULTRA)

    /** Farming XP for putting each on a patch, tenths: 18, 26 and 36. */
    fun xpTenths(level: Int): Int = when (level) {
        COMPOST -> 180
        SUPER -> 260
        ULTRA -> 360
        else -> 0
    }

    /** Tenths of the disease chance left: none, compost, supercompost, ultracompost. */
    private val LEFT_TENTHS = intArrayOf(10, 5, 2, 1)

    /**
     * The chance in 128 that a crop of [kind] falls ill at a growth, with [compost] in the soil and [watered] since its
     * last growth. Guessed: watering halving it again (the Wiki: "a modest reduction", no figure).
     */
    fun diseaseChance(kind: PatchKind, compost: Int, watered: Boolean): Int = diseaseChance(kind.disease, compost, watered)

    /** The same for a crop whose untreated chance is [base] in 128 (the Disease page's table: maple 13, 7, 3, 2). */
    fun diseaseChance(base: Int, compost: Int, watered: Boolean): Int {
        var chance = ceilDiv(base * LEFT_TENTHS[compost.coerceIn(NONE, ULTRA)], 10)
        if (watered) chance = ceilDiv(chance, 2)
        return chance
    }

    private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b
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
 *
 * A flower ([PatchKind.FLOWER]) is picked once for [yield] items ([levelBonus]: plus 0 to a tenth of the Farming level
 * more, limpwurt's) and has no chance to keep a life.
 *
 * [disease] is its chance in 128 to fall ill at a growth (its kind's, but each tree has its own), and [firstCycleTicks]
 * how often its first growth comes (its kind's cycle, but a yew's first stage lasts 5 minutes).
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
    val yield: Int = 1,
    val levelBonus: Boolean = false,
    val disease: Int = kind.disease,
    val firstCycleTicks: Int = kind.cycleTicks,
) {
    val stages: Int get() = growing.size

    /** How often the growth from [stage] comes. */
    fun cycleAt(stage: Int): Int = if (stage == 0) firstCycleTicks else kind.cycleTicks

    init {
        require(watered.isEmpty() || watered.size == stages) { "$key: ${watered.size} watered values for $stages stages" }
        // Flowers draw a dead plant for the grown stage too: four dead values to three diseased.
        require(diseased.size == stages - 1 && dead.size in stages - 1..stages) { "$key: diseased and dead values cover stages 1 on" }
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

    /**
     * A herb. Levels and XP from the Wiki's Herb patch table (read 2026-11-14); the chance to keep a life from each
     * herb's seed page, low to high 80 for all. Herbs can't be watered. Values from the cache's herb multiloc 8150: four
     * growing from [first], the three grown after them, three diseased from [diseasedFirst]; every herb dies to the same
     * three values, 170 to 172.
     */
    private fun herb(key: String, seed: Int, grimy: Int, level: Int, plant: Int, harvestXp: Int, low: Int, first: Int, diseasedFirst: Int) = Crop(
        key, PatchKind.HERB, seed, grimy, level, plant, harvestXp, low, 80,
        run(first, 4), emptyList(), first + 4, run(diseasedFirst, 3), run(170, 3),
    )

    val GUAM = herb("guam", 5291, 199, 9, 110, 125, 25, 4, 128)
    val MARRENTILL = herb("marrentill", 5292, 201, 14, 135, 150, 28, 11, 131)
    val TARROMIN = herb("tarromin", 5293, 203, 19, 160, 180, 31, 18, 134)
    val HARRALANDER = herb("harralander", 5294, 205, 26, 215, 240, 36, 25, 137)
    val RANARR = herb("ranarr", 5295, 207, 32, 270, 305, 39, 32, 140)
    val TOADFLAX = herb("toadflax", 5296, 3049, 38, 340, 385, 43, 39, 143)
    val IRIT = herb("irit", 5297, 209, 44, 430, 485, 46, 46, 146)
    val AVANTOE = herb("avantoe", 5298, 211, 50, 545, 615, 50, 53, 149)
    val KWUARM = herb("kwuarm", 5299, 213, 56, 690, 780, 54, 68, 152)
    val SNAPDRAGON = herb("snapdragon", 5300, 3051, 62, 875, 985, 57, 75, 155)

    /** Guessed: the Wiki gives huasca no chance to keep a life; 58 sits between snapdragon's and cadantine's, as its level does. */
    val HUASCA = herb("huasca", 30088, 30094, 65, 865, 1100, 58, 60, 173)
    val CADANTINE = herb("cadantine", 5301, 215, 67, 1065, 1200, 60, 82, 158)
    val LANTADYME = herb("lantadyme", 5302, 2485, 73, 1345, 1515, 64, 89, 161)
    val DWARF_WEED = herb("dwarf_weed", 5303, 217, 79, 1705, 1920, 67, 96, 164)
    val TORSTOL = herb("torstol", 5304, 219, 85, 1995, 2245, 71, 103, 167)

    /**
     * A flower. Levels, XP and yields from the Wiki's Flower patch (read 2026-11-14): picked once, plant and harvest XP
     * together then. Values from the cache's flower multiloc 7847: four growing from [first] and grown at the fifth,
     * watered ones from [wateredFirst], three diseased and four dead.
     */
    private fun flower(
        key: String, seed: Int, produce: Int, level: Int, plant: Int, harvestXp: Int,
        first: Int, wateredFirst: Int, diseasedFirst: Int, deadFirst: Int, yield: Int = 1, levelBonus: Boolean = false,
    ) = Crop(
        key, PatchKind.FLOWER, seed, produce, level, plant, harvestXp, 0, 0,
        run(first, 4), run(wateredFirst, 4), first + 4, run(diseasedFirst, 3), run(deadFirst, 4), yield, levelBonus,
    )

    val MARIGOLD = flower("marigold", 5096, 6010, 2, 85, 470, 8, 72, 137, 201)
    val ROSEMARY = flower("rosemary", 5097, 6014, 11, 120, 665, 13, 77, 142, 206)
    val NASTURTIUM = flower("nasturtium", 5098, 6012, 24, 195, 1110, 18, 82, 147, 211)
    val WOAD = flower("woad", 5099, 1793, 25, 205, 1155, 23, 87, 152, 216, yield = 3)

    /** Three roots and a random 0 to level/10 more (the Limpwurt seed page, Mod Ash: "between 0 and (stat - 1)"). */
    val LIMPWURT = flower("limpwurt", 5100, 225, 26, 215, 1200, 28, 92, 157, 221, yield = 3, levelBonus = true)
    val WHITE_LILY = flower("white_lily", 22887, 22932, 58, 420, 2500, 37, 101, 166, 230)

    /**
     * A tree. Levels, planting and check-health XP, cycles and stages from each tree's Wiki page (read 2026-11-14): the
     * sapling is what is planted ([Crop.seed]) and logs what it gives. Values from the cache's tree multiloc (8391 and
     * its siblings, the layout RuneLite reads): growing from [first], the check-health tree at [first] + stages, then
     * the tree to chop and its stump; ill and dead values from [diseasedFirst] and [deadFirst]. Chances to fall ill from
     * the Disease page: maple 13 and magic 9. Guessed: oak 17, willow 15 and yew 11, on the same line by stages (the
     * Wiki gives none).
     */
    private fun tree(
        key: String, sapling: Int, logs: Int, level: Int, plant: Int, check: Int, stages: Int,
        first: Int, diseasedFirst: Int, deadFirst: Int, disease: Int, firstCycle: Int = PatchKind.TREE.cycleTicks,
    ) = Crop(
        key, PatchKind.TREE, sapling, logs, level, plant, check, 0, 0,
        run(first, stages), emptyList(), first + stages, run(diseasedFirst, stages - 1), run(deadFirst, stages - 1),
        disease = disease, firstCycleTicks = firstCycle,
    )

    val OAK = tree("oak", 5370, 1521, 15, 140, 4673, 4, 8, 73, 137, disease = 17)
    val WILLOW = tree("willow", 5371, 1519, 30, 250, 14565, 6, 15, 80, 144, disease = 15)
    val MAPLE = tree("maple", 5372, 1517, 45, 450, 34034, 8, 24, 89, 153, disease = 13)

    /** "Since the first growth stage of a Yew tree only lasts 5 minutes": 5 + 9 x 40 minutes (its page). */
    val YEW = tree("yew", 5373, 1515, 60, 810, 70699, 10, 35, 100, 164, disease = 11, firstCycle = 1)
    val MAGIC = tree("magic", 5374, 1513, 75, 1455, 137683, 12, 48, 113, 177, disease = 9)

    val ALL = listOf(
        POTATO, ONION, CABBAGE, TOMATO, SWEETCORN, STRAWBERRY, WATERMELON, SNAPE_GRASS,
        GUAM, MARRENTILL, TARROMIN, HARRALANDER, RANARR, TOADFLAX, IRIT, AVANTOE, KWUARM, SNAPDRAGON, HUASCA,
        CADANTINE, LANTADYME, DWARF_WEED, TORSTOL,
        MARIGOLD, ROSEMARY, NASTURTIUM, WOAD, LIMPWURT, WHITE_LILY,
        OAK, WILLOW, MAPLE, YEW, MAGIC,
    )

    private val byKey = ALL.associateBy { it.key }
    private val bySeed = ALL.associateBy { it.seed }

    /**
     * Which flower, fully grown in the flower patch beside them, keeps each allotment crop from falling ill (the Wiki's
     * Allotment patch seeds table); a white lily keeps every crop well. Sweetcorn's scarecrow isn't built.
     */
    val PROTECTED_BY: Map<Crop, Crop> = mapOf(
        POTATO to MARIGOLD, ONION to MARIGOLD, TOMATO to MARIGOLD, CABBAGE to ROSEMARY, WATERMELON to NASTURTIUM,
    )

    /** Whether [flower] grown beside [crop] keeps it well. */
    fun protects(flower: Crop, crop: Crop): Boolean = flower == WHITE_LILY || PROTECTED_BY[crop] == flower

    fun byKey(key: String): Crop? = byKey[key]

    fun bySeed(seed: Int): Crop? = bySeed[seed]
}

/**
 * What goes with each tree besides its patch (the Wiki's Seedling, Sapling, Roots and Leaves pages; ids by gameval
 * name): the [seed] planted in a filled plant pot to make the [seedling], the [watered] seedling that grows into the
 * crop's sapling, the [roots] dug from its stump, the [leaves] pruning gives and its Woodcutting tree ([woodcutting],
 * woodcutting.json's key, for the axe rates, logs and XP).
 */
class TreeInfo(val crop: Crop, val seed: Int, val seedling: Int, val watered: Int, val roots: Int, val leaves: Int, val woodcutting: String) {
    val sapling: Int get() = crop.seed
}

object Trees {
    val OAK = TreeInfo(Crops.OAK, 5312, 5358, 5364, 6043, 6022, "oak")
    val WILLOW = TreeInfo(Crops.WILLOW, 5313, 5359, 5365, 6045, 6024, "willow")
    val MAPLE = TreeInfo(Crops.MAPLE, 5314, 5360, 5366, 6047, 6028, "maple")
    val YEW = TreeInfo(Crops.YEW, 5315, 5361, 5367, 6049, 6026, "yew")
    val MAGIC = TreeInfo(Crops.MAGIC, 5316, 5362, 5368, 6051, 6030, "magic")

    val ALL = listOf(OAK, WILLOW, MAPLE, YEW, MAGIC)

    private val byCrop = ALL.associateBy { it.crop }
    private val bySeed = ALL.associateBy { it.seed }
    private val bySeedling = ALL.associateBy { it.seedling }
    private val byWatered = ALL.associateBy { it.watered }

    fun of(crop: Crop): TreeInfo? = byCrop[crop]

    fun bySeed(seed: Int): TreeInfo? = bySeed[seed]

    fun bySeedling(id: Int): TreeInfo? = bySeedling[id]

    fun byWatered(id: Int): TreeInfo? = byWatered[id]

    /** An empty plant pot, and one filled with soil (plantpot_empty, plantpot_compost). */
    const val POT_EMPTY = 5350
    const val POT_FILLED = 5354

    /**
     * Roots dug from a stump at Farming [level]: one at the tree's level and one more every 8 levels, four at most (the
     * maple's page: 1 at 45, 2 at 53, 3 at 61, 4 at 69; the yew's the same from 60).
     */
    fun roots(crop: Crop, level: Int): Int = (1 + (level - crop.level).coerceAtLeast(0) / 8).coerceAtMost(4)
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

    /** `farming_flower_patch_1` (3054, 3307) and `farming_herb_patch_1` (3058, 3311), between and beside the allotments. */
    val FALADOR_FLOWER = FarmingPatch("falador_flower", 7847, TRANSMIT_C, PatchKind.FLOWER)
    val FALADOR_HERB = FarmingPatch("falador_herb", 8150, TRANSMIT_D, PatchKind.HERB)

    /** The Falador farming patches: map square 12083 north of z 3272 (Port Sarim's spirit tree patch is south of it). */
    val FALADOR = FarmingArea("Falador", setOf(12083), listOf(FALADOR_NW, FALADOR_SE, FALADOR_FLOWER, FALADOR_HERB)) { _, z -> z >= 3272 }

    /**
     * The tree patches (`tree_patch_1` to `_4`, cache placements): Falador park (3003, 3372), Taverley (2935, 3437),
     * Varrock palace (3228, 3458) and Lumbridge castle (3192, 3230), each drawn by `farming_transmit_a` in its two map
     * squares (RuneLite's FarmingWorld).
     */
    val LUMBRIDGE_TREE = FarmingPatch("lumbridge_tree", 8391, TRANSMIT_A, PatchKind.TREE)
    val VARROCK_TREE = FarmingPatch("varrock_tree", 8390, TRANSMIT_A, PatchKind.TREE)
    val FALADOR_TREE = FarmingPatch("falador_tree", 8389, TRANSMIT_A, PatchKind.TREE)
    val TAVERLEY_TREE = FarmingPatch("taverley_tree", 8388, TRANSMIT_A, PatchKind.TREE)

    val LUMBRIDGE = FarmingArea("Lumbridge", setOf(12594, 12850), listOf(LUMBRIDGE_TREE))
    val VARROCK = FarmingArea("Varrock", setOf(12854, 12853), listOf(VARROCK_TREE))
    val FALADOR_PARK = FarmingArea("Falador park", setOf(11828, 12084), listOf(FALADOR_TREE))
    val TAVERLEY = FarmingArea("Taverley", setOf(11573, 11829), listOf(TAVERLEY_TREE))

    val AREAS = listOf(FALADOR, LUMBRIDGE, VARROCK, FALADOR_PARK, TAVERLEY)

    val ALL: List<FarmingPatch> = AREAS.flatMap { it.patches }

    private val byKey = ALL.associateBy { it.key }

    /** Every patch loc, for registering handlers: a loc type can stand in more than one place. */
    val LOCS: Set<Int> = ALL.mapTo(HashSet()) { it.loc }

    fun byKey(key: String): FarmingPatch? = byKey[key]

    private val areaByPatch: Map<String, FarmingArea> = AREAS.flatMap { area -> area.patches.map { it.key to area } }.toMap()

    /** The area patch [key] is in. */
    fun areaOf(key: String): FarmingArea? = areaByPatch[key]

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
        require(AREAS.flatMap { it.regions }.let { it.size == it.toSet().size }) { "Two farming areas share a map square" }
    }
}
