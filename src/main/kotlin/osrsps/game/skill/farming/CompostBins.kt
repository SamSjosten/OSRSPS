package osrsps.game.skill.farming

/**
 * One compost bin: the loc the map places (a multiloc) and the `farming_transmit` varbit that draws it, [key] naming
 * it in saves. A bin is drawn with its area's patches ([FarmingArea.bins]).
 */
class CompostBin(val key: String, val loc: Int, val varbit: Int)

/**
 * What a compost bin makes and what goes in it (the OSRS Wiki's Compost Bin, Compost, Supercompost, Ultracompost and
 * Rotten tomato, read 2026-11-14). Fifteen un-noted items fill a bin; all supercompostable makes supercompost, all
 * tomatoes rotten tomatoes, anything else compost. A closed bin rots through [ROT_STAGES] growths of the 40-minute
 * clock ([ROT_CYCLE_TICKS], RuneLite's Time Tracking: compost, supercompost and rotten tomatoes all 40 minutes, three
 * stages; the Wiki's Compost: "60 minutes"). Opened, it is taken a bucket at a time for [takeXpTenths]: 4.5, 8.5 and
 * 10 XP (the Wiki's Recipe boxes), rotten tomatoes by hand. One dose of compost potion turns an opened bin of compost
 * into supercompost, and [ASH_PER_BIN] volcanic ash a bin of supercompost into ultracompost.
 */
object Compostables {
    const val NONE = 0
    const val COMPOST = 1
    const val SUPER = 2
    const val TOMATO = 3
    const val ULTRA = 4

    const val CAPACITY = 15
    const val ROT_CYCLE_TICKS = 8
    const val ROT_STAGES = 2
    const val ASH_PER_BIN = 25

    const val TOMATO_ITEM = 1982
    const val ROTTEN_TOMATO = 2518
    const val VOLCANIC_ASH = 21622

    /** Compost potion(4) to (1) (supercompost_potion_4 to _1), each dose going down by 2 ids; the vial after the last. */
    val COMPOST_POTIONS = listOf(6470, 6472, 6474, 6476)
    const val VIAL = 229

    /**
     * The Wiki's Compost Bin table, compostable column (ids by gameval name): allotment crops but watermelons (and a
     * watermelon slice), the flowers but white lilies, guam to irit (grimy and clean), the hops and fibres, redberries,
     * cadava berries and dwellberries, apples, bananas, oranges and curry leaves, weeds, kebabs, potato cacti, seaweed
     * and giant seaweed. Tomatoes are here too, and make rotten tomatoes when they are all there is.
     */
    val COMPOSTABLE: Set<Int> = setOf(
        1942, 1957, 1965, TOMATO_ITEM, 5986, 5504, 5984, 6010, 6014, 6012, 1793, 225,
        199, 201, 203, 205, 207, 209, 249, 251, 253, 255, 257, 259,
        6006, 5994, 5996, 5931, 5998, 6000, 6002, 31460, 1951, 753, 2126, 1955, 1963, 2108, 5970,
        6055, 1971, 3138, 401, 21504,
    )

    /**
     * The supercompostable column: watermelons, snape grass, white lilies, toadflax and the herbs above it (grimy and
     * clean), jangerberries, white berries, poison ivy berries, pineapples, papayas, coconuts and their halves and
     * shells, dragonfruit, every tree's roots, mushrooms, calquat fruit and white tree fruit. Guessed: huasca (newer
     * than the table; at level 65 it fits the page's rule, "requires 47 Farming or higher").
     */
    val SUPERCOMPOSTABLE: Set<Int> = setOf(
        5982, 231, 22932, 3049, 211, 213, 3051, 215, 2485, 217, 219, 30094,
        2998, 261, 263, 3000, 265, 2481, 267, 269, 30097,
        247, 239, 6018, 2114, 5972, 5974, 5976, 5978, 22929, 6043, 6045, 6047, 6049, 6051, 6053, 6004, 5980, 6469,
    )

    /** The bucket of each kind, filled from an empty one; rotten tomatoes come without a bucket. */
    fun product(kind: Int): Int = when (kind) {
        COMPOST -> Compost.BUCKET
        SUPER -> Compost.SUPER_BUCKET
        ULTRA -> Compost.ULTRA_BUCKET
        else -> ROTTEN_TOMATO
    }

    /** XP for each bucket taken, in tenths. Guessed: none for a rotten tomato (the Wiki's Recipe gives none). */
    fun takeXpTenths(kind: Int): Int = when (kind) {
        COMPOST -> 45
        SUPER -> 85
        ULTRA -> 100
        else -> 0
    }

    /** The kind a bin of [kind] becomes with [item] put in: a mix of super and plain is plain, tomatoes and more plain. */
    fun mix(kind: Int, count: Int, item: Int): Int {
        val itemKind = when (item) {
            TOMATO_ITEM -> TOMATO
            in SUPERCOMPOSTABLE -> SUPER
            else -> COMPOST
        }
        return if (count == 0 || kind == itemKind) itemKind else COMPOST
    }
}

/**
 * A player's compost bin: empty, filling with [count] items of [kind], closed and rotting ([rot] growths in), ready
 * and still closed, or open with [count] buckets left. Saved with the player.
 */
class BinState(var kind: Int = Compostables.NONE, var count: Int = 0, var closed: Boolean = false, var rot: Int = 0, var open: Boolean = false) {
    val empty: Boolean get() = count == 0 && !closed && !open
    val full: Boolean get() = !closed && !open && count >= Compostables.CAPACITY
    val ready: Boolean get() = closed && rot >= Compostables.ROT_STAGES

    fun clear() {
        kind = Compostables.NONE
        count = 0
        closed = false
        rot = 0
        open = false
    }

    /**
     * The bin's varbit value (the cache's compost bin multiloc 7836 and RuneLite's Time Tracking): 0 empty, 1 to 15
     * compostable items, 16 to 30 compost left to take, 31 and 32 rotting, 94 ready; supercompost 33 to 47, 48 to 62,
     * 95 and 96, 126; tomatoes 129 to 143, rotten tomatoes 144 to 158, 159 and 160, 222; ultracompost 176 to 190.
     */
    fun value(): Int {
        if (empty || kind == Compostables.NONE) return 0
        val base = when (kind) {
            Compostables.SUPER -> 32
            Compostables.TOMATO -> 128
            Compostables.ULTRA -> 160
            else -> 0
        }
        if (open) return base + Compostables.CAPACITY + count
        if (closed && ready) return when (kind) {
            Compostables.SUPER -> 126
            Compostables.TOMATO -> 222
            else -> 94
        }
        if (closed) return when (kind) {
            Compostables.SUPER -> 95
            Compostables.TOMATO -> 159
            else -> 31
        } + rot
        return base + count
    }

    fun copy() = BinState(kind, count, closed, rot, open)
}
