package osrsps.game.content

import osrsps.cache.VarTypeEncoder
import osrsps.cache.overlay.CacheOverlay
import osrsps.game.VarTypes
import osrsps.game.VarbitType

/**
 * Varps and varbits of our own, with ids right after the loaded cache's ([after]): the first varp is the cache's varp
 * count and the first varbit its varbit count, whatever build of the revision the cache is (OpenRS2's 241 builds differ
 * in how far their varps go: 5,890 in one, 5,995 in another). They are served to the client through the cache overlay
 * as config archives 16 and 14 hold them, and known to the server's [VarTypes] too, so a varbit set here changes what
 * the client draws (a loc with a varbit transform) the way the cache's own do. The varp id is the next one after the
 * cache's, as the client sizes its varp array by that archive's files; the overlay refuses an id the cache has, so a
 * build that reaches ours would fail at startup, not quietly, which deriving the ids from the cache prevents. A varp
 * of ours is saved with the player like any other, so a cache with more varps than the last one moves the save's old
 * value out of the way (nothing is read back from it).
 *
 * Content and tests refer to the vars by name ([lodestonesVarp], [lodestoneVarbit], [lodestonePreviousVarbit], [varp]
 * and [varbit]), never by number: `::varp lodestones` and `::varbit lodestone.varrock` for the runner scripts and the chatbox.
 */
class CustomVars(
    /** The first varp of ours: the cache's varp count. */
    val firstVarp: Int,
    /** The first varbit of ours: the cache's varbit count. */
    val firstVarbit: Int,
) {
    init {
        require(firstVarp >= 0 && firstVarbit >= 0) { "Custom var ids start at $firstVarp and $firstVarbit" }
    }

    /** The lodestones' varp: one bit per site, 1 once the player has activated it ([Lodestones]). */
    val lodestonesVarp: Int = firstVarp

    /** The varbit that is bit [Lodestones.Site.index] of the lodestones' varp: 1 once the player has activated the site. */
    fun lodestoneVarbit(site: Lodestones.Site): Int = firstVarbit + site.index

    /**
     * The varbit holding the stone the player last teleported to (its site index plus one; 0 for none), for the map's
     * Previous destination: bits [Lodestones.PREVIOUS_LOW_BIT] to [Lodestones.PREVIOUS_HIGH_BIT] of the lodestones' varp.
     */
    val lodestonePreviousVarbit: Int = firstVarbit + Lodestones.SITES.size

    /** Every varbit of ours, by id. */
    val varbits: Map<Int, VarbitType> = Lodestones.SITES.associate { lodestoneVarbit(it) to VarbitType(lodestonesVarp, it.index, it.index) } +
        (lodestonePreviousVarbit to VarbitType(lodestonesVarp, Lodestones.PREVIOUS_LOW_BIT, Lodestones.PREVIOUS_HIGH_BIT))

    /** Every varp of ours. */
    val varps: List<Int> = listOf(lodestonesVarp)

    /** The varps by name, for `::varp name`: [LODESTONES]. */
    val varpNames: Map<String, Int> = mapOf(LODESTONES to lodestonesVarp)

    /** The varbits by name, for `::varbit name`: each site's line key (`lodestone.lumbridge`, `lodestone.al_kharid`) and [LODESTONE_PREVIOUS]. */
    val varbitNames: Map<String, Int> = Lodestones.SITES.associate { it.name.key to lodestoneVarbit(it) } + (LODESTONE_PREVIOUS to lodestonePreviousVarbit)

    /** The varp called [name], in any case, or null. */
    fun varp(name: String): Int? = varpNames[name.lowercase()]

    /** The varbit called [name], in any case, or null. */
    fun varbit(name: String): Int? = varbitNames[name.lowercase()]

    /** What the overlay adds to the var type groups: a type for each varp and varbit. */
    val overlayFiles: List<CacheOverlay.File>
        get() = varps.map { CacheOverlay.File(CONFIG_INDEX, VARP_GROUP, it, VarTypeEncoder.encodeVarp()) } +
            varbits.entries.sortedBy { it.key }.map { (id, type) -> CacheOverlay.File(CONFIG_INDEX, VARBIT_GROUP, id, VarTypeEncoder.encodeVarbit(type)) }

    override fun toString(): String = "varp $firstVarp, varbits $firstVarbit to ${firstVarbit + varbits.size - 1}"

    companion object {
        const val CONFIG_INDEX = 2
        const val VARBIT_GROUP = 14
        const val VARP_GROUP = 16

        /** The lodestones' varp's name, and the previous destination varbit's. */
        const val LODESTONES = "lodestones"
        const val LODESTONE_PREVIOUS = "lodestone.previous"

        /** Our vars with ids right after [cache]'s, the loaded cache's own types (before ours are added to them). */
        fun after(cache: VarTypes): CustomVars = CustomVars(cache.varpCount, cache.varbitCount)
    }
}
