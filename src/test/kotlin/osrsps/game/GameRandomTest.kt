package osrsps.game

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GameRandomTest {
    /** A mix of every kind of roll, so a change to any of them shows. */
    private fun rolls(random: GameRandom): List<Any> = (0 until 200).flatMap {
        listOf(random.nextInt(128), random.between(-5, 5), random.chance(1, 3), random.pick(listOf("a", "b", "c")), random.nextLong())
    }

    @Test
    fun `two runs with the same seed give the same rolls`() {
        val first = GameRandom(1234)
        val second = GameRandom(1234)
        assertEquals(rolls(first), rolls(second))
        assertEquals(first.draws, second.draws)
    }

    @Test
    fun `different seeds give different rolls`() {
        assertNotEquals(rolls(GameRandom(1234)), rolls(GameRandom(1235)))
    }

    @Test
    fun `reseeding starts the sequence again`() {
        val random = GameRandom(99)
        val expected = rolls(random)
        random.nextLong()
        random.reseed(99)
        assertEquals(0, random.draws)
        assertEquals(expected, rolls(random))
    }

    @Test
    fun `the sequence for a seed never changes`() {
        // xoshiro256** seeded with SplitMix64, values from a separate implementation of the reference C code. If this
        // fails, every recorded seed now gives different rolls.
        val zero = GameRandom(0)
        assertEquals(listOf(-7355399402456485196, -4652746763540216534, 1900383378846508768), List(3) { zero.nextLong() })
        val answer = GameRandom(42)
        assertEquals(listOf(1546998764402558742, 6990951692964543102, -5902157311460992607), List(3) { answer.nextLong() })
    }

    @Test
    fun `rolls stay in range and reach both ends`() {
        val random = GameRandom(7)
        val ints = List(10_000) { random.nextInt(6) }
        assertEquals((0..5).toSet(), ints.toSet())
        // Each face should come up about 1,667 times; a fixed seed makes this exact, so it can't flake.
        ints.groupingBy { it }.eachCount().values.forEach { assertTrue(it in 1_500..1_833, "count $it") }

        val between = List(1_000) { random.between(3, 5) }
        assertEquals(setOf(3, 4, 5), between.toSet())
        assertEquals(7, random.between(7, 7))
        val wide = List(1_000) { random.between(Int.MIN_VALUE, Int.MAX_VALUE) }
        assertTrue(wide.any { it < 0 } && wide.any { it > 0 })
    }

    @Test
    fun `chance of none never hits and chance of all always does`() {
        val random = GameRandom(7)
        repeat(1_000) {
            assertFalse(random.chance(0, 10))
            assertTrue(random.chance(10, 10))
        }
    }

    @Test
    fun `bad arguments are refused`() {
        val random = GameRandom(7)
        assertFailsWith<IllegalArgumentException> { random.nextInt(0) }
        assertFailsWith<IllegalArgumentException> { random.between(5, 4) }
        assertFailsWith<IllegalArgumentException> { random.chance(2, 1) }
        assertFailsWith<IllegalArgumentException> { random.chance(1, 0) }
        assertFailsWith<IllegalArgumentException> { random.pick(emptyList<Int>()) }
    }

    @Test
    fun `game code draws only from GameRandom`() {
        val allowed = setOf("osrsps/game/GameRandom.kt")
        val root = Path.of("src/main/kotlin")
        val offenders = Files.walk(root).use { paths ->
            paths.filter { it.extension == "kt" }
                .filter { root.relativize(it).invariantSeparatorsPathString !in allowed }
                .filter { OTHER_RANDOMS.containsMatchIn(it.readText()) }
                .map { root.relativize(it).invariantSeparatorsPathString }
                .toList()
        }
        assertEquals(emptyList(), offenders, "Draw from World.random instead, so seeded runs repeat")
    }

    private companion object {
        /** Other random sources, including the collection helpers that use Kotlin's unseeded default. */
        val OTHER_RANDOMS = Regex("""java\.util\.Random|kotlin\.random|Math\.random|ThreadLocalRandom|SplittableRandom|SecureRandom|\.random\(\)|\.shuffled\(\)|\.shuffle\(\)|nanoTime""")
    }
}
