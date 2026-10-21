package osrsps.game

/**
 * The one random source all game logic draws from, so a run can be repeated: the same seed with the same input on the
 * same ticks gives the same rolls. The seed is set at startup (OSRSPS_SEED, or picked at random and logged) and by
 * `::seed n`.
 *
 * The generator is xoshiro256** seeded through SplitMix64, written out here rather than taken from java.util.Random or
 * kotlin.random.Random, so a seed keeps giving the same rolls across JDK and Kotlin upgrades (Kotlin only promises that
 * within one version). It is not the generator live uses; only the repeatability matters.
 *
 * Only touched on the game thread.
 */
class GameRandom(seed: Long = newSeed()) {
    var seed: Long = seed
        private set

    /** Numbers drawn since the seed was set. Two runs that have drawn the same count are at the same point. */
    var draws: Long = 0
        private set

    private var s0 = 0L
    private var s1 = 0L
    private var s2 = 0L
    private var s3 = 0L

    init {
        reseed(seed)
    }

    /** Starts the sequence for [seed] from the beginning. */
    fun reseed(seed: Long) {
        this.seed = seed
        draws = 0
        var x = seed
        fun splitMix(): Long {
            x += -0x61c8864680b583ebL
            var z = x
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
        s0 = splitMix()
        s1 = splitMix()
        s2 = splitMix()
        s3 = splitMix()
    }

    /** Any 64-bit value. */
    fun nextLong(): Long {
        draws++
        val result = (s1 * 5).rotateLeft(7) * 9
        val t = s1 shl 17
        s2 = s2 xor s0
        s3 = s3 xor s1
        s1 = s1 xor s2
        s0 = s0 xor s3
        s2 = s2 xor t
        s3 = s3.rotateLeft(45)
        return result
    }

    /** 0 to [until] - 1, each equally likely. */
    fun nextInt(until: Int): Int {
        require(until > 0) { "until must be positive, was $until" }
        return below(until.toLong()).toInt()
    }

    /** [min] to [max], both included, each equally likely. */
    fun between(min: Int, max: Int): Int {
        require(min <= max) { "min $min is above max $max" }
        return (min + below(max.toLong() - min + 1)).toInt()
    }

    /** True with probability [numerator] in [denominator], e.g. `chance(1, 128)` for a 1/128 drop. */
    fun chance(numerator: Int, denominator: Int): Boolean {
        require(denominator > 0 && numerator in 0..denominator) { "Bad chance $numerator/$denominator" }
        return nextInt(denominator) < numerator
    }

    /** One element of [list], each equally likely. */
    fun <T> pick(list: List<T>): T {
        require(list.isNotEmpty()) { "Can't pick from an empty list" }
        return list[nextInt(list.size)]
    }

    /** 0 to [bound] - 1 from the top 63 bits, redrawing the few values that would favour low numbers. */
    private fun below(bound: Long): Long {
        while (true) {
            val bits = nextLong() ushr 1
            val value = bits % bound
            // Same check as java.util.Random.nextInt(bound): overflow means bits fell in the incomplete last block.
            if (bits - value + (bound - 1) >= 0) return value
        }
    }

    companion object {
        /** A seed short enough to type into `::seed`, from the clock. Only used when none was given. */
        fun newSeed(): Long = System.nanoTime() and Int.MAX_VALUE.toLong()
    }
}
