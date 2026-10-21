package osrsps.game

/**
 * The animation (seq), spotanim, sound effect (synth) and jingle ids the cache has, so a mistyped id is refused on the
 * server rather than sent to the client. A null set means unknown, as in tests: then every id in range is allowed.
 */
class EffectIds(
    private val seqs: Set<Int>?,
    private val spotanims: Set<Int>?,
    private val synths: Set<Int>?,
    private val jingles: Set<Int>?,
) {
    fun seqExists(id: Int) = seqs?.contains(id) ?: (id in 0..MAX_ID)
    fun spotanimExists(id: Int) = spotanims?.contains(id) ?: (id in 0..MAX_ID)
    fun synthExists(id: Int) = synths?.contains(id) ?: (id in 0..MAX_ID)
    fun jingleExists(id: Int) = jingles?.contains(id) ?: (id in 0..MAX_ID)

    companion object {
        /** All four ids go to the client in two bytes. */
        const val MAX_ID = 65535

        val UNKNOWN = EffectIds(null, null, null, null)
    }
}
