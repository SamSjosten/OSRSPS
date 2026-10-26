package osrsps.game

/**
 * Animation (seq) ids by their gameval name, such as `human_death` for 836, from the cache's gameval index (group 7).
 * Lets data files name animations instead of giving bare ids. Empty when the cache has no gameval index (tests).
 */
class SeqNames(private val ids: Map<String, Int>) {
    operator fun get(name: String): Int? = ids[name]

    val isEmpty: Boolean get() = ids.isEmpty()

    companion object {
        val NONE = SeqNames(emptyMap())
    }
}
