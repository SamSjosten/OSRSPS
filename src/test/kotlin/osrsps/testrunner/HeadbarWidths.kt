package osrsps.testrunner

import com.displee.cache.CacheLibrary
import java.nio.file.Path

/**
 * Each head bar type's width from the cache (config group 33), which the client divides a bar's fill by: RuneLite's
 * NPC health scale. Decoded as RuneLite's HealthBarLoader reads it; a type without opcode 14 keeps the default 30.
 */
internal object HeadbarWidths {
    const val DEFAULT = 30
    private const val CONFIG_INDEX = 2
    private const val HEADBAR_GROUP = 33

    fun load(cacheDir: Path): Map<Int, Int> {
        val library = CacheLibrary.create(cacheDir.toString())
        try {
            val widths = HashMap<Int, Int>()
            for (file in library.index(CONFIG_INDEX)?.archive(HEADBAR_GROUP)?.files().orEmpty()) {
                widths[file.id] = decode(file.data ?: continue)
            }
            return widths
        } finally {
            library.close()
        }
    }

    fun decode(data: ByteArray): Int {
        var position = 0
        var width = DEFAULT
        fun u1() = data[position++].toInt() and 0xFF
        while (true) {
            when (val opcode = u1()) {
                0 -> return width
                1, 5, 11 -> position += 2
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
