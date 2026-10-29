package osrsps.game

/**
 * What a player carries, by the OSRS Wiki's Weight article (read 2026-10-29): the inventory and worn items' weights
 * added up, stackable items counting nothing whatever the cache gives them (arrows have 16 g there), and
 * weight-reducing gear counting its negative weight only while worn.
 *
 * The worn weight is in the cache: such an item has a second obj named after it with `_worn` (graceful_hood and
 * graceful_hood_worn at -3 kg, hunting_light_cape and hunting_light_cape_worn), or `worn` with no underscore for the
 * boots of lightness (ikov_bootsoflightnessworn). That obj's weight is used while the item is worn. The pufferfish,
 * which lightens from the inventory underwater, isn't in: there is no underwater.
 */
class ItemWeights(private val objTypes: ObjTypes) {
    /** The worn obj of each weight-reducing item, by the item's id. */
    private val worn: Map<Int, ObjType> by lazy {
        val map = HashMap<Int, ObjType>()
        for (id in 0 until objTypes.size) {
            val type = objTypes[id] ?: continue
            val name = type.internalName ?: continue
            if (!type.wearable || name.endsWith("worn")) continue
            val wornType = objTypes.find("${name}_worn") ?: objTypes.find("${name}worn") ?: continue
            map[id] = wornType
        }
        map
    }

    /** The weight [type] adds in the inventory, in grams. */
    fun carried(type: ObjType): Int = if (type.stackable) 0 else type.weight

    /** The weight [type] adds while worn, in grams. */
    fun worn(type: ObjType): Int = if (type.stackable) 0 else (worn[type.id] ?: type).weight

    /** [player]'s inventory and worn items, in grams. Can be below zero. */
    fun total(player: Player): Int {
        var grams = 0
        for ((_, obj) in player.inventory.contents()) grams += objTypes[obj.id]?.let(::carried) ?: 0
        for ((_, obj) in player.equipment.contents()) grams += objTypes[obj.id]?.let(::worn) ?: 0
        return grams
    }

    companion object {
        /**
         * Whole kilograms, as the client is sent them and run energy uses them. Toward zero: the Wiki's Equipment
         * Stats examples show 9.979 kg as 9 and -4.535 kg as -4. That run energy uses the same whole number is guessed.
         */
        fun kilograms(grams: Int): Int = grams / 1000
    }
}
