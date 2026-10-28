package osrsps.game.combat

import osrsps.game.zone.ZoneKey

/**
 * Where combat is multi-way: many players can fight one NPC and many NPCs one player, and a Venator bow's shots bounce
 * (OSRS Wiki, Multicombat area). Everywhere else is single-way, as Lumbridge is. Nothing in Misthalin is multi-way yet,
 * so the only areas are whole zones an admin marks with `::multi`, until the content that has real ones comes; they
 * last until the server restarts. The client's multi-way icon isn't shown for them yet.
 */
class MultiCombat {
    private val zones = HashSet<ZoneKey>()

    /** Whether the tile is in a multi-way area. */
    fun at(level: Int, x: Int, z: Int): Boolean = ZoneKey.of(x, z, level) in zones

    /** Makes the zone holding the tile multi-way, or single-way again. */
    fun set(level: Int, x: Int, z: Int, multi: Boolean) {
        val zone = ZoneKey.of(x, z, level)
        if (multi) zones += zone else zones -= zone
    }
}
