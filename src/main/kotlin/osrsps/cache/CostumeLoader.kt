package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.skill.construction.CostumeData
import osrsps.game.skill.construction.CostumeList
import osrsps.game.skill.construction.CostumeSet

private val logger = KotlinLogging.logger {}

/**
 * Reads the costume room's storage lists from the cache's enums, as the storage window's client scripts read them
 * (osrs-dumps: `poh_costumes_init`, `poh_costumes_countmembers`, `poh_costumes_drawalternates`):
 * - each list (enums 3289 to 3300, the keys of enum 3301, which titles them) holds its sets' key objects at keys 0, 1,
 *   2 and on;
 * - enum 3077 maps a set's key to the enum of its pieces, also keyed 0, 1, 2 and on. A key with no entry is a set of one,
 *   the key itself;
 * - enum 3303 maps a piece to its one alternate (platelegs to plateskirt), and enum 3304 maps a piece to an enum of
 *   alternates (a max cape's variants). An alternate counts as that piece of the set.
 */
object CostumeLoader {
    private const val CONFIG_INDEX = 2
    private const val ENUM_GROUP = 8

    /** The lists: enum_3301's keys (it maps each to its title, which the server doesn't need). */
    val LISTS = 3289..3300
    const val PIECES_ENUM = 3077
    const val ALTERNATE_ENUM = 3303
    const val ALTERNATES_ENUM = 3304

    fun load(library: CacheLibrary): CostumeData = build { library.data(CONFIG_INDEX, ENUM_GROUP, it) }

    internal fun build(enum: (Int) -> ByteArray?): CostumeData {
        fun enumType(id: Int) = enum(id)?.let(SettingsLoader::decodeEnum)
        val pieces = enumType(PIECES_ENUM)
        val alternate = enumType(ALTERNATE_ENUM)
        val alternates = enumType(ALTERNATES_ENUM)
        fun ordered(type: SettingsLoader.EnumType): List<Int> =
            generateSequence(0) { it + 1 }.map { type.outputs[it] }.takeWhile { it != null }.filterNotNull().toList()
        fun withAlternates(piece: Int): List<Int> = buildList {
            add(piece)
            alternate?.outputs?.get(piece)?.takeIf { it >= 0 }?.let(::add)
            alternates?.outputs?.get(piece)?.let(::enumType)?.let { addAll(ordered(it)) }
        }
        val lists = LinkedHashMap<Int, CostumeList>()
        for (listId in LISTS) {
            val list = enumType(listId)
            if (list == null) {
                logger.warn { "No enum $listId (a costume list) in the cache; the costume room won't show it" }
                continue
            }
            val sets = ordered(list).map { key ->
                val members = pieces?.outputs?.get(key)?.let(::enumType)?.let(::ordered) ?: listOf(key)
                CostumeSet(key, members.map(::withAlternates))
            }
            lists[listId] = CostumeList(listId, sets)
        }
        logger.info { "Loaded ${lists.size} costume room lists (${lists.values.sumOf { it.sets.size }} sets) from the cache's enums" }
        return CostumeData(lists)
    }
}
