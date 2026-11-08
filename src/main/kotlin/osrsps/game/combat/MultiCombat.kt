package osrsps.game.combat

import osrsps.game.zone.ZoneKey

/** A box of tiles where combat is multi-way: [minX] to [maxX] and [minZ] to [maxZ], inclusive, on [level]. */
data class MultiArea(val name: String, val level: Int, val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int) {
    init {
        require(level in 0..3) { "$name: levels are 0 to 3, not $level" }
        require(minX <= maxX && minZ <= maxZ) { "$name: the box's south-west corner must not be north or east of its north-east one" }
    }

    fun contains(level: Int, x: Int, z: Int): Boolean = level == this.level && x in minX..maxX && z in minZ..maxZ
}

/**
 * Where combat is multi-way: many players can fight one NPC and many NPCs one player, and a Venator bow's shots bounce
 * (OSRS Wiki, Multicombat area). Everywhere else is single-way, as Lumbridge is. The real areas are [areas], read from
 * `src/main/resources/multi-combat.tsv`; an admin can also mark whole zones with `::multi`, which last until the server
 * restarts. The client shows its crossed-swords icon from [VARBIT_INDICATOR], which the world sets each tick.
 */
class MultiCombat(val areas: List<MultiArea> = emptyList()) {
    private val zones = HashSet<ZoneKey>()

    /** The areas by map square (64 by 64 tiles), so a lookup reads only the few near the tile. */
    private val bySquare: Map<Int, List<MultiArea>> = buildMap<Int, MutableList<MultiArea>> {
        for (area in areas) {
            for (sx in (area.minX shr 6)..(area.maxX shr 6)) {
                for (sz in (area.minZ shr 6)..(area.maxZ shr 6)) getOrPut(square(sx, sz)) { ArrayList() } += area
            }
        }
    }

    /** Whether the tile is in a multi-way area. */
    fun at(level: Int, x: Int, z: Int): Boolean =
        ZoneKey.of(x, z, level) in zones || bySquare[square(x shr 6, z shr 6)]?.any { it.contains(level, x, z) } == true || dynamic(level, x, z)

    /**
     * Whether a tile of an instance is multi-way, as a raid's copy is all through; the world sets it. The file's boxes
     * can't say, since an instance is laid wherever there is room.
     */
    var dynamic: (level: Int, x: Int, z: Int) -> Boolean = { _, _, _ -> false }

    /** Makes the zone holding the tile multi-way, or single-way again. Only the admin zones change, not [areas]. */
    fun set(level: Int, x: Int, z: Int, multi: Boolean) {
        val zone = ZoneKey.of(x, z, level)
        if (multi) zones += zone else zones -= zone
    }

    companion object {
        /** multiway_indicator: 1 shows the client's crossed swords over the minimap, 0 hides them. */
        const val VARBIT_INDICATOR = 4605

        const val RESOURCE = "/multi-combat.tsv"
        private const val COLUMNS = 6

        private fun square(sx: Int, sz: Int) = (sx shl 8) or sz

        fun load(): MultiCombat {
            val text = MultiCombat::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return MultiCombat(parse(text))
        }

        /**
         * Reads an areas file: tab-separated, one box a line, `name level min_x min_z max_x max_z` (inclusive tiles).
         * Everything after a `#` is a comment; a line whose first column is `name` is a header.
         */
        fun parse(text: String): List<MultiArea> {
            val areas = ArrayList<MultiArea>()
            for ((index, raw) in text.lines().withIndex()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val where = "${RESOURCE.drop(1)} line ${index + 1}"
                val columns = line.split('\t').map(String::trim).filter(String::isNotEmpty)
                if (columns.first().equals("name", ignoreCase = true)) continue
                require(columns.size == COLUMNS) { "$where: expected $COLUMNS tab-separated columns (name level min_x min_z max_x max_z), got ${columns.size}" }
                val n = columns.drop(1).map { requireNotNull(it.toIntOrNull()) { "$where: '$it' is not a number" } }
                areas += MultiArea(columns[0], n[0], n[1], n[2], n[3], n[4])
            }
            return areas
        }
    }
}
