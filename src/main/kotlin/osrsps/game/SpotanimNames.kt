package osrsps.game

/**
 * Spotanim ids by their gameval name, such as `bronze_arrow_travel` for 10, from the cache's gameval index (group 8).
 * Lets data files name graphics and projectiles instead of giving bare ids. Empty when the cache has no gameval index
 * (tests).
 */
class SpotanimNames(private val ids: Map<String, Int>) {
    operator fun get(name: String): Int? = ids[name]

    val isEmpty: Boolean get() = ids.isEmpty()

    companion object {
        val NONE = SpotanimNames(emptyMap())
    }
}
