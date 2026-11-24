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

    internal fun build(furnitureRows: Map<Int, DbRow>, roomRows: Map<Int, DbRow>, hotspotRows: Map<Int, DbRow>): FurnitureData {
        val furniture = HashMap<Int, FurnitureOption>()
        for ((row, db) in furnitureRows) {
            val levels = db.columns[LEVEL_REQUIREMENT].orEmpty().chunked(2)
            val level = levels.firstOrNull { (stat, _) -> stat == Skill.CONSTRUCTION.id }?.get(1) as? Int ?: continue
            furniture[row] = FurnitureOption(
                row = row,
                name = db.columns[NAME]?.firstOrNull() as? String ?: continue,
                icon = db.columns[MODEL_OBJ]?.firstOrNull() as? Int ?: continue,
                materials = db.columns[MATERIAL_COST].orEmpty().chunked(2).mapNotNull { pair ->
                    val obj = pair.getOrNull(0) as? Int ?: return@mapNotNull null
                    val count = pair.getOrNull(1) as? Int ?: return@mapNotNull null
                    obj to count
                },
                level = level,
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
