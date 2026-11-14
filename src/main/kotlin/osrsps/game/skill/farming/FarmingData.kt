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
 *
 * Fruit trees ([FRUIT_TREE], step 5; the Wiki's Fruit tree patch, Disease and each fruit tree's page, read 2026-11-14)
 * grow every 160 minutes through six stages and fall ill at 18/128 in 4 of their 6 growths. Once checked they bear up
 * to [lives] fruit, one more every 40 minutes ([FRUIT_REGROW_TICKS]); [harvestValues] counts the 0 to 6 fruit drawn.
 *
 * Hops ([HOPS], step 6; the Wiki's Hops patch and Hops seeds, read 2026-11-14) grow as allotments do, every 10 minutes,
 * watered and composted the same, from four seeds (jute three, [Crop.seeds]), harvested with a spade for their lives.
 * Bushes ([BUSH], step 6; the Wiki's Bush patch and each bush's page) grow every 20 minutes from one seed, are checked
 * once grown and then bear up to [lives] berries, one more every 20 minutes ([regrowTicks]), until dug up. Guessed:
 * hops falling ill at 27/128 as allotments are taken to, bushes at 18/128 as fruit trees do (the Disease page gives
 * neither).
 */
enum class PatchKind(
    val cycleTicks: Int, val seeds: Int, val lives: Int, val harvestValues: Int, val spade: Boolean, val disease: Int,
    val composting: Boolean, val weeds: Int = 3,
) {
    ALLOTMENT(cycleTicks = 2, seeds = 3, lives = 3, harvestValues = 3, spade = true, disease = 27, composting = true),
    HERB(cycleTicks = 4, seeds = 1, lives = 3, harvestValues = 3, spade = true, disease = 27, composting = true),
    FLOWER(cycleTicks = 1, seeds = 1, lives = 1, harvestValues = 1, spade = false, disease = 27, composting = false),
    TREE(cycleTicks = 8, seeds = 1, lives = 1, harvestValues = 1, spade = true, disease = 0, composting = false),
    FRUIT_TREE(cycleTicks = 32, seeds = 1, lives = 6, harvestValues = 7, spade = true, disease = 18, composting = false),
    HOPS(cycleTicks = 2, seeds = 4, lives = 3, harvestValues = 3, spade = true, disease = 27, composting = true),
    BUSH(cycleTicks = 4, seeds = 1, lives = 4, harvestValues = 5, spade = true, disease = 18, composting = false);

    val diseaseCycleTicks: Int get() = cycleTicks * 2

    /** Planted as a sapling, checked once grown, chopped down and its stump cleared: trees and fruit trees. */
    val tree: Boolean get() = this == TREE || this == FRUIT_TREE

    /** Checked once grown for its XP, and pruned with secateurs when ill rather than cured: trees, fruit trees, bushes. */
    val checks: Boolean get() = tree || this == BUSH

    /** Bears fruit or berries once checked, [lives] at most, one more every [regrowTicks]: fruit trees and bushes. */
    val bears: Boolean get() = this == FRUIT_TREE || this == BUSH

    /** A fruit every 40 minutes on a fruit tree (the Wiki's Fruit tree patch); a berry every 20 on a bush (Bush patch: all four in "approximately 80 minutes"). */
    val regrowTicks: Int get() = if (this == BUSH) cycleTicks else FRUIT_REGROW_TICKS

    companion object {
        /** A fruit tree grows one more fruit every 40 minutes, up to six (the Wiki's Fruit tree patch). */
        const val FRUIT_REGROW_TICKS = 8
    }
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
 * [disease] is its chance in 128 to fall ill at a growth (its kind's, but each tree has its own), and its first
 * growth comes on its kind's clock, or [firstStageMillis] after planting when that isn't 0 (a yew's
 * first stage lasts 5 minutes from planting, off the usual clock: the Wiki's Growth timing). It can fall ill at
 * the growths from stage 1 to [lastDiseaseStage] (every growth but the first, but a fruit tree's 4 of 6).
 *
 * A fruit tree draws its Check-health tree at [checkValue], its stump at [stumpValue], and [harvest] + n with n fruit
 * on it; each fruit picked gives [fruitXpTenths]. A bush is drawn the same, with no stump, and each berry gives
 * [fruitXpTenths]. [seeds]: how many one planting takes, its kind's but for jute.
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
    val firstStageMillis: Long = 0,
    val lastDiseaseStage: Int = growing.size - 1,
    val checkValue: Int = -1,
    val stumpValue: Int = -1,
    val fruitXpTenths: Int = 0,
    val seeds: Int = kind.seeds,
) {
    val stages: Int get() = growing.size

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
        first: Int, diseasedFirst: Int, deadFirst: Int, disease: Int, firstStageMillis: Long = 0,
    ) = Crop(
        key, PatchKind.TREE, sapling, logs, level, plant, check, 0, 0,
        run(first, stages), emptyList(), first + stages, run(diseasedFirst, stages - 1), run(deadFirst, stages - 1),
        disease = disease, firstStageMillis = firstStageMillis,
    )

    val OAK = tree("oak", 5370, 1521, 15, 140, 4673, 4, 8, 73, 137, disease = 17)
    val WILLOW = tree("willow", 5371, 1519, 30, 250, 14565, 6, 15, 80, 144, disease = 15)
    val MAPLE = tree("maple", 5372, 1517, 45, 450, 34034, 8, 24, 89, 153, disease = 13)

    /** "Since the first growth stage of a Yew tree only lasts 5 minutes": 5 + 9 x 40 minutes (its page). */
    val YEW = tree("yew", 5373, 1515, 60, 810, 70699, 10, 35, 100, 164, disease = 11, firstStageMillis = 5 * 60_000L)
    val MAGIC = tree("magic", 5374, 1513, 75, 1455, 137683, 12, 48, 113, 177, disease = 9)

    /**
     * A fruit tree. Levels, planting, check-health and per-fruit XP from each tree's Wiki page (read 2026-11-14): six
     * stages of 160 minutes. Values from the cache's fruit tree multiloc (7962 and its siblings, RuneLite's layout):
     * growing from [first], the tree with 0 to 6 fruit after them, five diseased from [diseasedFirst] and five dead from
     * [deadFirst], then the stump and the Check-health tree. Guessed: of the 6 growths the 4 that can fall ill (the
     * Disease page gives the count) are the second to the fifth, not the first and not the last.
     */
    private fun fruitTree(
        key: String, sapling: Int, fruit: Int, level: Int, plant: Int, check: Int, fruitXp: Int, first: Int, diseasedFirst: Int, deadFirst: Int,
    ) = Crop(
        key, PatchKind.FRUIT_TREE, sapling, fruit, level, plant, check, 0, 0,
        run(first, 6), emptyList(), first + 6, run(diseasedFirst, 5), run(deadFirst, 5),
        lastDiseaseStage = 4, checkValue = deadFirst + 7, stumpValue = deadFirst + 6, fruitXpTenths = fruitXp,
    )

    val APPLE = fruitTree("apple", 5496, 1955, 27, 220, 11995, 85, 8, 21, 27)
    val BANANA = fruitTree("banana", 5497, 1963, 33, 280, 17505, 105, 35, 48, 54)
    val ORANGE = fruitTree("orange", 5498, 2108, 39, 355, 24702, 135, 72, 85, 91)
    val CURRY = fruitTree("curry", 5499, 5970, 42, 400, 29069, 150, 99, 112, 118)
    val PINEAPPLE = fruitTree("pineapple", 5500, 2114, 51, 570, 46050, 215, 136, 149, 155)
    val PAPAYA = fruitTree("papaya", 5501, 5972, 57, 720, 61466, 270, 163, 176, 182)
    val PALM = fruitTree("palm", 5502, 5974, 68, 1105, 101501, 415, 200, 213, 219)
    val DRAGONFRUIT = fruitTree("dragonfruit", 22866, 22929, 81, 1400, 173350, 700, 227, 240, 246)

    /**
     * A hop (or barley or jute). Levels, seeds, plant and harvest XP and stages from the Wiki's Hops seeds (read
     * 2026-11-14); the chance to keep a life from the Wiki's Talk:Farming "Yield rates of various crops" (Mod Easty's
     * numbers, which each seed's page cites), high 180 for all. Values from the cache's hops multiloc (8175 and its
     * siblings): growing from [first], the three grown after them, the stages from 1 on diseased from [first] +
     * [stages] + 3, watered from [wateredFirst], dead from [deadFirst]. The planting XP comes with the last crop, as an
     * allotment's (the table's footnote: "upon fully harvesting the patch, or if the patch died, upon clearing it").
     */
    private fun hops(
        key: String, seed: Int, produce: Int, level: Int, plant: Int, harvestXp: Int, low: Int, stages: Int,
        first: Int, wateredFirst: Int, deadFirst: Int, seeds: Int = PatchKind.HOPS.seeds,
    ) = Crop(
        key, PatchKind.HOPS, seed, produce, level, plant, harvestXp, low, 180,
        run(first, stages), run(wateredFirst, stages), first + stages, run(first + stages + 3, stages - 1), run(deadFirst, stages - 1),
        seeds = seeds,
    )

    val BARLEY = hops("barley", 5305, 6006, 3, 85, 95, 103, 4, 74, 202, 209)
    val HAMMERSTONE = hops("hammerstone", 5307, 5994, 4, 90, 100, 104, 4, 4, 132, 139)
    val ASGARNIAN = hops("asgarnian", 5308, 5996, 8, 109, 120, 108, 5, 14, 142, 150)
    val JUTE = hops("jute", 5306, 5931, 13, 130, 145, 113, 5, 84, 212, 220, seeds = 3)
    val YANILLIAN = hops("yanillian", 5309, 5998, 16, 145, 160, 116, 6, 26, 154, 163)
    val KRANDORIAN = hops("krandorian", 5310, 6000, 21, 175, 195, 121, 7, 40, 168, 178)
    val WILDBLOOD = hops("wildblood", 5311, 6002, 28, 230, 260, 128, 8, 56, 184, 195)

    /**
     * A bush. Levels, stages and plant, check-health and per-berry XP from the Wiki's Bush seeds (read 2026-11-14):
     * the planting XP comes with Check-health. Values from the cache's bush multiloc (7577 and its siblings): growing
     * from [first], the bush with 0 to 4 berries after them, the stages from 1 on diseased from [diseasedFirst] and
     * dead from [deadFirst], and the Check-health bush at [checkValue]. Each bush's page marks its last two diseased
     * models unused, so a bush falls ill at the growths to stage 2 to [stages] - 2, not at the last. A berry picked is
     * kept with the chance Talk:Farming gives whiteberries, 83 to 85 at level 1 and 193 to 195 at 99 (the middle
     * taken), which makes the Bush patch's minimum of four and the Farming page's "15-20 berries" at 99. Guessed: every
     * other bush's chance the same as whiteberries' (no figure for them); poison ivy never falls ill (its page: immune).
     */
    private fun bush(
        key: String, seed: Int, berries: Int, level: Int, plant: Int, check: Int, berryXp: Int, stages: Int,
        first: Int, diseasedFirst: Int, deadFirst: Int, checkValue: Int, disease: Int = PatchKind.BUSH.disease,
    ) = Crop(
        key, PatchKind.BUSH, seed, berries, level, plant, check, 84, 194,
        run(first, stages), emptyList(), first + stages, run(diseasedFirst, stages - 1), run(deadFirst, stages - 1),
        disease = disease, lastDiseaseStage = stages - 2, checkValue = checkValue, fruitXpTenths = berryXp,
    )

    val REDBERRY = bush("redberry", 5101, 1951, 10, 115, 640, 45, 5, 5, 70, 134, 250)
    val CADAVABERRY = bush("cadavaberry", 5102, 753, 22, 180, 1025, 70, 6, 15, 80, 144, 251)
    val DWELLBERRY = bush("dwellberry", 5103, 2126, 36, 315, 1775, 120, 7, 26, 91, 155, 252)
    val JANGERBERRY = bush("jangerberry", 5104, 247, 48, 505, 2845, 190, 8, 38, 103, 167, 253)
    val WHITEBERRY = bush("whiteberry", 5105, 239, 59, 780, 4375, 290, 8, 51, 116, 180, 254)
    val POISON_IVY = bush("poison_ivy", 5106, 6018, 70, 1200, 6750, 450, 8, 197, 210, 217, 255, disease = 0)

    val ALL = listOf(
        POTATO, ONION, CABBAGE, TOMATO, SWEETCORN, STRAWBERRY, WATERMELON, SNAPE_GRASS,
        GUAM, MARRENTILL, TARROMIN, HARRALANDER, RANARR, TOADFLAX, IRIT, AVANTOE, KWUARM, SNAPDRAGON, HUASCA,
        CADANTINE, LANTADYME, DWARF_WEED, TORSTOL,
        MARIGOLD, ROSEMARY, NASTURTIUM, WOAD, LIMPWURT, WHITE_LILY,
        OAK, WILLOW, MAPLE, YEW, MAGIC,
        APPLE, BANANA, ORANGE, CURRY, PINEAPPLE, PAPAYA, PALM, DRAGONFRUIT,
        BARLEY, HAMMERSTONE, ASGARNIAN, JUTE, YANILLIAN, KRANDORIAN, WILDBLOOD,
        REDBERRY, CADAVABERRY, DWELLBERRY, JANGERBERRY, WHITEBERRY, POISON_IVY,
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
 * woodcutting.json's key, for the axe rates, logs and XP). A fruit tree has no roots (0) and no Woodcutting tree.
 */
class TreeInfo(val crop: Crop, val seed: Int, val seedling: Int, val watered: Int, val roots: Int, val leaves: Int, val woodcutting: String?) {
    val sapling: Int get() = crop.seed
}

object Trees {
    val OAK = TreeInfo(Crops.OAK, 5312, 5358, 5364, 6043, 6022, "oak")
    val WILLOW = TreeInfo(Crops.WILLOW, 5313, 5359, 5365, 6045, 6024, "willow")
    val MAPLE = TreeInfo(Crops.MAPLE, 5314, 5360, 5366, 6047, 6028, "maple")
    val YEW = TreeInfo(Crops.YEW, 5315, 5361, 5367, 6049, 6026, "yew")
    val MAGIC = TreeInfo(Crops.MAGIC, 5316, 5362, 5368, 6051, 6030, "magic")

    /** Fruit trees: seeds, seedlings and watered seedlings by gameval name; pruning gives plain leaves (the trees' pages). */
    private fun fruit(crop: Crop, seed: Int, seedling: Int, watered: Int) = TreeInfo(crop, seed, seedling, watered, 0, LEAVES, null)

    const val LEAVES = 6020

    val APPLE = fruit(Crops.APPLE, 5283, 5480, 5488)
    val BANANA = fruit(Crops.BANANA, 5284, 5481, 5489)
    val ORANGE = fruit(Crops.ORANGE, 5285, 5482, 5490)
    val CURRY = fruit(Crops.CURRY, 5286, 5483, 5491)
    val PINEAPPLE = fruit(Crops.PINEAPPLE, 5287, 5484, 5492)
    val PAPAYA = fruit(Crops.PAPAYA, 5288, 5485, 5493)
    val PALM = fruit(Crops.PALM, 5289, 5486, 5494)
    val DRAGONFRUIT = fruit(Crops.DRAGONFRUIT, 22877, 22862, 22864)

    val ALL = listOf(OAK, WILLOW, MAPLE, YEW, MAGIC, APPLE, BANANA, ORANGE, CURRY, PINEAPPLE, PAPAYA, PALM, DRAGONFRUIT)

    private val byCrop = ALL.associateBy { it.crop }
    private val byWatered = ALL.associateBy { it.watered }

    fun of(crop: Crop): TreeInfo? = byCrop[crop]

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

    /**
     * The Gnome Stronghold's tree patch (`tree_patch_5`, 2435, 3414) and fruit tree patch (`fruit_tree_patch_1`, 2475,
     * 3445, drawn by `farming_transmit_b`), and the fruit tree patches of the Tree Gnome Village (`_2`, 2489, 3179),
     * Brimhaven (`_3`, 2764, 3212) and Catherby (`_4`, 2860, 3433), with their map squares from RuneLite's FarmingWorld.
     */
    val GNOME_STRONGHOLD_TREE = FarmingPatch("gnome_stronghold_tree", 19147, TRANSMIT_A, PatchKind.TREE)
    val GNOME_STRONGHOLD_FRUIT = FarmingPatch("gnome_stronghold_fruit_tree", 7962, TRANSMIT_B, PatchKind.FRUIT_TREE)
    val GNOME_VILLAGE_FRUIT = FarmingPatch("gnome_village_fruit_tree", 7963, TRANSMIT_A, PatchKind.FRUIT_TREE)
    val BRIMHAVEN_FRUIT = FarmingPatch("brimhaven_fruit_tree", 7964, TRANSMIT_A, PatchKind.FRUIT_TREE)
    val CATHERBY_FRUIT = FarmingPatch("catherby_fruit_tree", 7965, TRANSMIT_A, PatchKind.FRUIT_TREE)

    val GNOME_STRONGHOLD = FarmingArea("Gnome Stronghold", setOf(9781, 9782, 9526, 9525), listOf(GNOME_STRONGHOLD_TREE, GNOME_STRONGHOLD_FRUIT))
    val GNOME_VILLAGE = FarmingArea("Tree Gnome Village", setOf(9777), listOf(GNOME_VILLAGE_FRUIT))
    val BRIMHAVEN = FarmingArea("Brimhaven", setOf(11058, 11057), listOf(BRIMHAVEN_FRUIT))
    val CATHERBY = FarmingArea("Catherby", setOf(11317), listOf(CATHERBY_FRUIT))

    /**
     * The hops patches (`farming_hops_patch_1` to `_4`, cache placements, each a square of one-tile locs): Yanille
     * (2574, 3103), Entrana (2809, 3335), north of Lumbridge (3227, 3313) and Seers' Village (2664, 3523); and the bush
     * patches (`farming_bush_patch_1` to `_4`): west of the Champions' Guild (3181, 3357), Rimmington (2940, 3221),
     * Etceteria (2591, 3863) and south of Ardougne (2617, 3225). All drawn by `farming_transmit_a`, in the map squares
     * RuneLite's FarmingWorld gives. Aldarin's hops and the Farming Guild's bush aren't built.
     */
    val LUMBRIDGE_HOPS = FarmingPatch("lumbridge_hops", 8175, TRANSMIT_A, PatchKind.HOPS)
    val YANILLE_HOPS = FarmingPatch("yanille_hops", 8173, TRANSMIT_A, PatchKind.HOPS)
    val ENTRANA_HOPS = FarmingPatch("entrana_hops", 8174, TRANSMIT_A, PatchKind.HOPS)
    val SEERS_HOPS = FarmingPatch("seers_hops", 8176, TRANSMIT_A, PatchKind.HOPS)
    val CHAMPIONS_BUSH = FarmingPatch("champions_guild_bush", 7577, TRANSMIT_A, PatchKind.BUSH)
    val RIMMINGTON_BUSH = FarmingPatch("rimmington_bush", 7578, TRANSMIT_A, PatchKind.BUSH)
    val ETCETERIA_BUSH = FarmingPatch("etceteria_bush", 7579, TRANSMIT_A, PatchKind.BUSH)
    val ARDOUGNE_BUSH = FarmingPatch("ardougne_bush", 7580, TRANSMIT_A, PatchKind.BUSH)

    val LUMBRIDGE_HOPS_AREA = FarmingArea("Lumbridge hops", setOf(12851), listOf(LUMBRIDGE_HOPS))
    val YANILLE = FarmingArea("Yanille", setOf(10288), listOf(YANILLE_HOPS))
    val ENTRANA = FarmingArea("Entrana", setOf(11060, 11316), listOf(ENTRANA_HOPS))
    val SEERS = FarmingArea("Seers' Village", setOf(10551, 10550), listOf(SEERS_HOPS))
    val CHAMPIONS_GUILD = FarmingArea("Champions' Guild", setOf(12596), listOf(CHAMPIONS_BUSH))
    val RIMMINGTON = FarmingArea("Rimmington", setOf(11570, 11826), listOf(RIMMINGTON_BUSH))
    val ETCETERIA = FarmingArea("Etceteria", setOf(10300), listOf(ETCETERIA_BUSH))
    val ARDOUGNE = FarmingArea("Ardougne", setOf(10290, 10546), listOf(ARDOUGNE_BUSH))

    val AREAS = listOf(
        FALADOR, LUMBRIDGE, VARROCK, FALADOR_PARK, TAVERLEY, GNOME_STRONGHOLD, GNOME_VILLAGE, BRIMHAVEN, CATHERBY,
        LUMBRIDGE_HOPS_AREA, YANILLE, ENTRANA, SEERS, CHAMPIONS_GUILD, RIMMINGTON, ETCETERIA, ARDOUGNE,
    )

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
