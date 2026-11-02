package osrsps.game.content

import osrsps.cache.LocTypeDecoder
import osrsps.cache.LocTypeEncoder
import osrsps.cache.overlay.CacheOverlay
import osrsps.game.loc.LocType

/**
 * Loc types of our own, with ids above the cache's (62,533 in the 241 cache; ours start at 65,000, and a loc id has to
 * fit the 2 bytes of the zone packets). Each is encoded as config archive 6 holds a loc type and served to the client
 * through the cache overlay, and decoded again for the server, so both see the same loc. Their models are custom
 * models in `data/models` on the server (see the README's custom models). Built for one [CustomVars], as a loc whose
 * look a varbit picks (the lodestones) names the varbit's id, which follows the loaded cache's.
 */
class CustomLocs(vars: CustomVars) {
    class Custom(val id: Int, val definition: LocTypeEncoder.Definition) {
        init {
            require(id in FIRST_ID..MAX_ID) { "A custom loc id is $FIRST_ID to $MAX_ID, not $id" }
        }

        val encoded: ByteArray by lazy { LocTypeEncoder.encode(definition) }

        /** The server's view, read back from what the client gets. */
        val type: LocType by lazy { LocTypeDecoder.decode(id, encoded) }
    }

    val all: List<Custom> = listOf(Lodestones.DORMANT_TYPE, Lodestones.ACTIVE_TYPE) + Lodestones.SITES.map { it.type(vars.lodestoneVarbit(it)) }

    init {
        require(all.map { it.id }.toSet().size == all.size) { "A custom loc id is used twice" }
    }

    val types: List<LocType> get() = all.map { it.type }

    /** What the overlay adds to the loc types group. */
    val overlayFiles: List<CacheOverlay.File> get() = all.map { CacheOverlay.File(CONFIG_INDEX, LOC_TYPE_GROUP, it.id, it.encoded) }

    companion object {
        const val FIRST_ID = 65000
        const val MAX_ID = 0xFFFF
        const val CONFIG_INDEX = 2
        const val LOC_TYPE_GROUP = 6
    }
}
