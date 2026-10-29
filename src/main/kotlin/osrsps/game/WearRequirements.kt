package osrsps.game

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * The levels needed to wear each item, from [RESOURCE]. Items it doesn't list need none.
 *
 * The levels are the OSRS Wiki's, not the cache's: the cache's requirement params (434 to 437, RSMod's statreq) are
 * right on some items and on others hold something else, such as the Crafting level that makes the item (a fire
 * battlestaff has Crafting 62 there; the Wiki has 30 Attack and 30 Magic to wield it). Quest requirements are left out,
 * as every account counts as having done every quest.
 */
class WearRequirements(private val byItem: Map<Int, List<WearRequirement>>) {
    operator fun get(type: ObjType): List<WearRequirement> = byItem[type.id].orEmpty()

    val size: Int get() = byItem.size

    companion object {
        val NONE = WearRequirements(emptyMap())

        const val RESOURCE = "/item-requirements.tsv"

        /** Reads [RESOURCE]; see [parse]. */
        fun load(objTypes: ObjTypes): WearRequirements {
            val text = WearRequirements::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text, objTypes)
        }

        /**
         * Parses a requirements file: one item a line, tab-separated, `#` starting a comment line.
         *
         * ```
         * rune_scimitar	attack=40	Rune scimitar
         * pest_void_knight_top	attack=42 strength=42 defence=42 hitpoints=42 ranged=42 magic=42 prayer=22	Void knight top
         * ```
         *
         * The item is its gameval name; the levels are skill names ([Skill] names or aliases, any case) or `combat`
         * for the combat level; the third column is the Wiki page the levels are from. A level the file gets wrong
         * fails it; an item the cache doesn't have is skipped, with a warning unless the cache has no items at all.
         */
        fun parse(text: String, objTypes: ObjTypes, fileName: String = RESOURCE): WearRequirements {
            val byItem = HashMap<Int, List<WearRequirement>>()
            val unknown = ArrayList<String>()
            text.lineSequence().forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val columns = line.split('\t')
                require(columns.size >= 2) { "$fileName:${index + 1}: expected an item and its levels" }
                val levels = columns[1].split(' ').filter { it.isNotEmpty() }.map { level(it, fileName, index + 1) }
                val type = objTypes.find(columns[0])
                if (type == null) unknown += columns[0] else byItem[type.id] = levels
            }
            if (unknown.isNotEmpty() && objTypes.size > 0) {
                logger.warn { "$fileName: ${unknown.size} items not in the cache, skipped: ${unknown.take(10).joinToString()}" }
            }
            return WearRequirements(byItem)
        }

        private fun level(text: String, fileName: String, line: Int): WearRequirement {
            val name = text.substringBefore('=')
            val level = text.substringAfter('=', "").toIntOrNull()
            require(level != null && level in 1..126) { "$fileName:$line: bad level \"$text\"" }
            if (name.equals("combat", ignoreCase = true)) return WearRequirement(null, level)
            val skill = Skill.find(name) ?: throw IllegalArgumentException("$fileName:$line: no skill \"$name\"")
            require(level <= 99) { "$fileName:$line: bad level \"$text\"" }
            return WearRequirement(skill, level)
        }
    }
}
