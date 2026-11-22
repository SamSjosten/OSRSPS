package osrsps.game.content.randomevents

import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.instance.Instance
import osrsps.map.Tile

/**
 * The places the teleporting random events take their player to (the Freaky Forest, the Drill Demon's camp and the
 * rest): each a private copy of its part of the map ([Instance]) for one player, made as they go and gone once they
 * leave it, as every instance is. Logging out inside leaves by the instance's exit, which is where they were taken
 * from, so they come back there next time.
 *
 * From the OSRS Wiki's Random events page (read 2026-11-19): the player comes back where they were taken from; if
 * that tile was blocked meanwhile they go to Lumbridge instead. Guessed: Lumbridge is the usual spawn tile
 * ([World.SPAWN_X]); a tile counts as blocked when nobody could stand on it.
 */
class EventAreas(private val world: World) {
    /** One player's stay: the event, its instance, where it was copied from, and where they came from. */
    class Away(val owner: String, val event: RandomEvent, val instance: Instance, val fromX: Int, val fromZ: Int, val back: Tile, val backLevel: Int) {
        /** [x], [z] on the real map, inside this stay's instance. */
        fun inside(x: Int, z: Int) = Tile(instance.baseX + x - fromX, instance.baseZ + z - fromZ)

        /** [x], [z] inside the instance, back on the real map. */
        fun outside(x: Int, z: Int) = Tile(x - instance.baseX + fromX, z - instance.baseZ + fromZ)

        /** What the event keeps for this stay. */
        var state: Any? = null
    }

    private val stays = HashMap<Int, Away>()

    /** The stay [player] is in, if any. */
    fun awayOf(player: Player): Away? = awayAt(player.x, player.z)?.takeIf { it.owner == player.saveKey }

    /** [inventory] without the items of the place [player] is saved in, if they are in one ([RandomEvent.placeItems]). */
    fun withoutPlaceItems(player: Player, inventory: Map<Int, InvObj>): Map<Int, InvObj> {
        val items = awayOf(player)?.event?.placeItems ?: return inventory
        return if (items.isEmpty()) inventory else inventory.filterValues { it.id !in items }
    }

    fun awayAt(x: Int, z: Int): Away? {
        val instance = world.instances.at(x, z) ?: return null
        return stays[instance.id]?.takeIf { it.instance === instance }
    }

    /**
     * Takes [player] to [event]'s place: the [widthZones] by [heightZones] zones from [fromX], [fromZ] (zone-aligned),
     * landing on [arrive] (real-map coordinates). Returns the stay, or null, leaving them where they are, when the
     * dynamic area has no room or the event isn't allowed where they stand now ([RandomEvents.allowedAt]).
     */
    fun takeAway(player: Player, event: RandomEvent, fromX: Int, fromZ: Int, widthZones: Int, heightZones: Int, arrive: Tile): Away? {
        require(fromX % 8 == 0 && fromZ % 8 == 0) { "${event.key}'s place isn't zone-aligned" }
        // Checked again as they say yes: a fight or Tele Block that began during the talk keeps them here (R50).
        if (!world.randomEvents.allowedAt(player, event)) return null
        // Ended stays are let go of here, as nothing else tells this when an instance ends.
        stays.values.removeAll { world.instances.all.none { instance -> instance === it.instance } }
        val instance = world.instances.create(fromX, fromZ, widthZones, heightZones, player.x, player.z, player.level) ?: return null
        val away = Away(player.saveKey, event, instance, fromX, fromZ, Tile(player.x, player.z), player.level)
        stays[instance.id] = away
        val to = away.inside(arrive.x, arrive.z)
        world.teleport(player, to.x, to.z, 0)
        return away
    }

    /**
     * [player] is being moved to [x], [z]: out of their stay, by any way at all (the way back, a teleport spell or tablet,
     * a death), the place's own items ([RandomEvent.placeItems]) stay behind. Called by [World.teleport].
     */
    fun moving(player: Player, x: Int, z: Int) {
        val away = awayOf(player) ?: return
        if (awayAt(x, z) === away) return
        val items = away.event.placeItems
        if (items.isNotEmpty()) for (slot in 0 until player.inventory.capacity) if (player.inventory[slot]?.id in items) player.inventory.remove(slot)
    }

    /**
     * Sends [player] back from [away] to where they came from, or to Lumbridge when that tile is blocked now; the place's
     * own items stay there ([moving]).
     */
    fun bringBack(player: Player, away: Away) {
        val (x, z, level) = if (world.canStand(away.back.x, away.back.z, away.backLevel)) Triple(away.back.x, away.back.z, away.backLevel)
        else Triple(World.SPAWN_X, World.SPAWN_Z, 0)
        world.teleport(player, x, z, level)
    }
}
