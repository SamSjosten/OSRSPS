package osrsps.testrunner

import com.displee.cache.CacheLibrary
import java.nio.file.Path

/**
 * The head bar types from the cache (config group 33), decoded as RuneLite's HealthBarLoader reads them: each type's
 * width, which the client divides a bar's fill by (RuneLite's NPC health scale), and how long the bar stays up.
 */
internal object HeadbarTypes {
    /**
     * A type's [width] (opcode 14, default 30) and [stickCycles], the client cycles (20 ms) a bar stays shown after
     * its last update (opcode 5, osrs-dumps' sticktime: 300 for the standard bars).
     */
    data class Type(val width: Int = DEFAULT_WIDTH, val stickCycles: Int = DEFAULT_STICK_CYCLES)

    const val DEFAULT_WIDTH = 30

    /** Guessed: the deobfuscated client's default for a type without opcode 5. Every type in osrs-dumps' headbar dump sets it. */
    const val DEFAULT_STICK_CYCLES = 70

    private const val CONFIG_INDEX = 2
    private const val HEADBAR_GROUP = 33

    fun load(cacheDir: Path): Map<Int, Type> {
        val library = CacheLibrary.create(cacheDir.toString())
        try {
            val types = HashMap<Int, Type>()
            for (file in library.index(CONFIG_INDEX)?.archive(HEADBAR_GROUP)?.files().orEmpty()) {
                types[file.id] = decode(file.data ?: continue)
            }
            return types
        } finally {
            library.close()
        }
    }

    fun decode(data: ByteArray): Type {
        var position = 0
        var width = DEFAULT_WIDTH
        var stick = DEFAULT_STICK_CYCLES
        fun u1() = data[position++].toInt() and 0xFF
        while (true) {
            when (val opcode = u1()) {
                0 -> return Type(width, stick)
                5 -> stick = (u1() shl 8) or u1()
                1, 11 -> position += 2
                2, 3, 6, 15 -> position++
                4 -> {}
                // A big smart: four bytes when the first has its top bit, else two.
                7, 8 -> position += if (data[position] < 0) 4 else 2
                14 -> width = u1()
                else -> error("Head bar opcode $opcode isn't known")
            }
        }
    }
}
