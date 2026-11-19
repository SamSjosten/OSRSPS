package osrsps.game.content.randomevents

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

    fun awayAt(x: Int, z: Int): Away? {
        val instance = world.instances.at(x, z) ?: return null
        return stays[instance.id]?.takeIf { it.instance === instance }
    }

    /**
     * Takes [player] to [event]'s place: the [widthZones] by [heightZones] zones from [fromX], [fromZ] (zone-aligned),
     * landing on [arrive] (real-map coordinates). Returns the stay, or null, leaving them where they are, when the
     * dynamic area has no room.
     */
    fun takeAway(player: Player, event: RandomEvent, fromX: Int, fromZ: Int, widthZones: Int, heightZones: Int, arrive: Tile): Away? {
        require(fromX % 8 == 0 && fromZ % 8 == 0) { "${event.key}'s place isn't zone-aligned" }
        // Ended stays are let go of here, as nothing else tells this when an instance ends.
        stays.values.removeAll { world.instances.all.none { instance -> instance === it.instance } }
        val instance = world.instances.create(fromX, fromZ, widthZones, heightZones, player.x, player.z, player.level) ?: return null
        val away = Away(player.saveKey, event, instance, fromX, fromZ, Tile(player.x, player.z), player.level)
        stays[instance.id] = away
        val to = away.inside(arrive.x, arrive.z)
        world.teleport(player, to.x, to.z, 0)
        return away
    }

    /** Sends [player] back from [away] to where they came from, or to Lumbridge when that tile is blocked now. */
    fun bringBack(player: Player, away: Away) {
        val (x, z, level) = if (world.canStand(away.back.x, away.back.z, away.backLevel)) Triple(away.back.x, away.back.z, away.backLevel)
        else Triple(World.SPAWN_X, World.SPAWN_Z, 0)
        world.teleport(player, x, z, level)
    }
}
