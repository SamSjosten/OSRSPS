package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.ge.ItemSet
import osrsps.game.ge.ItemSets

private val logger = KotlinLogging.logger {}

/**
 * Reads the Grand Exchange's item sets from the cache's enums, as the item sets window's client scripts read them
 * (osrs-dumps, rev 241: `itemsets_build_sets` and `itemsets_draw`): enum 1033 lists each set's enum by index, from 0
 * until the first missing one; a set's enum holds the set at key -1 and its pieces at keys 0, 1, 2 and on until the
 * first missing one. (Enum 1034, from a set to its enum, is the same list the other way round.)
 */
object ItemSetLoader {
    private const val CONFIG_INDEX = 2
    private const val ENUM_GROUP = 8

    /** enum_1033: the sets, by index. */
    const val SETS_ENUM = 1033

    fun load(library: CacheLibrary): ItemSets = build { library.data(CONFIG_INDEX, ENUM_GROUP, it) }

    internal fun build(enum: (Int) -> ByteArray?): ItemSets {
        fun enumType(id: Int) = enum(id)?.let(SettingsLoader::decodeEnum)
        val list = enumType(SETS_ENUM)
        if (list == null) {
            logger.warn { "No enum $SETS_ENUM (item sets) in the cache; the Grand Exchange's item sets window will list nothing" }
            return ItemSets(emptyList())
        }
        val sets = ArrayList<ItemSet>()
        var index = 0
        while (true) {
            val setEnum = list.outputs[index]?.let(::enumType) ?: break
            val set = setEnum.outputs[-1]
            val pieces = generateSequence(0) { it + 1 }.map { setEnum.outputs[it] }.takeWhile { it != null }.filterNotNull().toList()
            // The window draws an entry with no set as hidden, with nothing to click.
            sets += ItemSet(index, set ?: -1, pieces)
            index++
        }
        logger.info { "Loaded ${sets.count { it.set >= 0 }} item sets from the cache's enum $SETS_ENUM" }
        return ItemSets(sets)
    }
}
