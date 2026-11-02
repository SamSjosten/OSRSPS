package osrsps.game.content

import osrsps.cache.VarTypeEncoder
import osrsps.cache.overlay.CacheOverlay
import osrsps.game.VarTypes
import osrsps.game.VarbitType

/**
 * Varps and varbits of our own, with ids right after the cache's (varp 5,890 and varbit 20,410 in the 241 cache),
 * served to the client through the cache overlay as config archives 16 and 14 hold them, and known to the server's
 * [VarTypes] too, so a varbit set here changes what the client draws (a loc with a varbit transform) the way the
 * cache's own do. The varp id stays the next one after the cache's, as the client sizes its varp array by that
 * archive's files; the overlay refuses an id the cache has, so a revision that takes it fails at startup, not quietly.
 * A varp of ours is saved with the player like any other.
 */
object CustomVars {
    /** The first varp past the 241 cache's (5,890). */
    const val FIRST_VARP = 5891

    /** The first varbit past the 241 cache's (20,410). */
    const val FIRST_VARBIT = 20411

    const val CONFIG_INDEX = 2
    const val VARBIT_GROUP = 14
    const val VARP_GROUP = 16

    /** The lodestones' varp: one bit per site, 1 once the player has activated it ([Lodestones]). */
    const val LODESTONES_VARP = FIRST_VARP

    /** Every varbit of ours, by id. */
    val varbits: Map<Int, VarbitType> = Lodestones.SITES.associate { it.varbit to VarbitType(LODESTONES_VARP, it.index, it.index) }

    /** Every varp of ours. */
    val varps: List<Int> = listOf(LODESTONES_VARP)

    init {
        require(varbits.keys.all { it >= FIRST_VARBIT }) { "A custom varbit is below $FIRST_VARBIT" }
        require(varbits.values.all { it.baseVar in varps }) { "A custom varbit uses a varp that isn't ours" }
    }

    /** What the overlay adds to the var type groups: a type for each varp and varbit. */
    val overlayFiles: List<CacheOverlay.File>
        get() = varps.map { CacheOverlay.File(CONFIG_INDEX, VARP_GROUP, it, VarTypeEncoder.encodeVarp()) } +
            varbits.entries.sortedBy { it.key }.map { (id, type) -> CacheOverlay.File(CONFIG_INDEX, VARBIT_GROUP, id, VarTypeEncoder.encodeVarbit(type)) }
}
