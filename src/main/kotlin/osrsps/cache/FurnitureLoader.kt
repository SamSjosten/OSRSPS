package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Skill
import osrsps.game.skill.construction.FurnitureData
import osrsps.game.skill.construction.FurnitureOption

private val logger = KotlinLogging.logger {}

/**
 * Construction's furniture from the cache's own tables, as the client's furniture creation menu and house viewer read
 * them: `poh_room` (111) lists each room type's hotspots in order, `poh_hotspot` (112) each hotspot's choices in its
 * menu's order, and `furniture` (110) each choice's name, icon, materials and levels.
 */
object FurnitureLoader {
    const val FURNITURE_TABLE = 110
    const val ROOM_TABLE = 111
    const val HOTSPOT_TABLE = 112

    // furniture's columns.
    private const val MODEL_OBJ = 0
    private const val NAME = 1
    private const val MATERIAL_COST = 2
    private const val LEVEL_REQUIREMENT = 3
    private const val HIDDEN_IN_BUILD_MENU = 5

    // poh_room's.
    private const val ROOM_TYPE = 3
    private const val ROOM_HOTSPOT = 7

    // poh_hotspot's.
    private const val BUILDDATA = 0

    fun load(library: CacheLibrary): FurnitureData =
        build(DbRows.load(library, FURNITURE_TABLE), DbRows.load(library, ROOM_TABLE), DbRows.load(library, HOTSPOT_TABLE))

    /** [values] as (int, int) pairs, or null if any value isn't an int or one is left over. */
    private fun pairs(values: List<Any?>): List<Pair<Int, Int>>? {
        if (values.size % 2 != 0) return null
        return values.chunked(2).map { (a, b) -> Pair(a as? Int ?: return null, b as? Int ?: return null) }
    }

    internal fun build(furnitureRows: Map<Int, DbRow>, roomRows: Map<Int, DbRow>, hotspotRows: Map<Int, DbRow>): FurnitureData {
        val furniture = HashMap<Int, FurnitureOption>()
        for ((row, db) in furnitureRows) {
            // Every level and material pair must read as two ints; a row with one that doesn't is left out whole, never
            // built with a requirement or a cost dropped (exploit review R50B-2).
            val levels = pairs(db.columns[LEVEL_REQUIREMENT].orEmpty())
            val materials = pairs(db.columns[MATERIAL_COST].orEmpty())
            if (levels == null || materials == null) {
                logger.warn { "Furniture row $row left out: its levels or materials don't read as pairs" }
                continue
            }
            val level = levels.firstOrNull { (stat, _) -> stat == Skill.CONSTRUCTION.id }?.second ?: continue
            furniture[row] = FurnitureOption(
                row = row,
                name = db.columns[NAME]?.firstOrNull() as? String ?: continue,
                icon = db.columns[MODEL_OBJ]?.firstOrNull() as? Int ?: continue,
                materials = materials,
                level = level,
                otherLevels = levels.filter { (stat, _) -> stat != Skill.CONSTRUCTION.id },
                hidden = (db.columns[HIDDEN_IN_BUILD_MENU]?.firstOrNull() as? Int ?: 0) != 0,
            )
        }
        val rooms = HashMap<Int, List<List<FurnitureOption>>>()
        for ((_, db) in roomRows) {
            val type = db.columns[ROOM_TYPE]?.firstOrNull() as? Int ?: continue
            if (type < 0) continue
            rooms[type] = db.columns[ROOM_HOTSPOT].orEmpty().map { hotspot ->
                hotspotRows[hotspot as? Int]?.columns?.get(BUILDDATA).orEmpty().mapNotNull { furniture[it as? Int] }
            }
        }
        logger.info { "Loaded ${furniture.size} pieces of furniture for ${rooms.size} room types" }
        return FurnitureData(rooms)
    }
}
