package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.EffectIds

private val logger = KotlinLogging.logger {}

/**
 * Lists the animation, spotanim, sound effect and jingle ids in the cache. Only the ids are read, not the types.
 * Checked against the live 241 cache on 2026-10-21: 14,514 seqs (ids up to 14,520, with gaps), 4,023 spotanims,
 * 12,163 synths and 315 jingles (ids up to 864, with gaps).
 */
object EffectIdLoader {
    private const val CONFIG_INDEX = 2
    private const val SEQ_GROUP = 12
    private const val SPOTANIM_GROUP = 13

    /** Each synth and each jingle is a group of its own index. */
    private const val SYNTH_INDEX = 4
    private const val JINGLE_INDEX = 11

    fun load(library: CacheLibrary): EffectIds {
        val config = library.index(CONFIG_INDEX)
        val seqs = config?.archive(SEQ_GROUP)?.fileIds()?.toSet()
        val spotanims = config?.archive(SPOTANIM_GROUP)?.fileIds()?.toSet()
        val synths = library.index(SYNTH_INDEX)?.archiveIds()?.toSet()
        val jingles = library.index(JINGLE_INDEX)?.archiveIds()?.toSet()
        for ((name, ids) in listOf("seq", "spotanim", "synth", "jingle").zip(listOf(seqs, spotanims, synths, jingles))) {
            if (ids == null) logger.warn { "No $name ids in the cache; allowing any" }
        }
        logger.info {
            "Loaded ${seqs?.size ?: 0} seq, ${spotanims?.size ?: 0} spotanim, ${synths?.size ?: 0} synth and " +
                "${jingles?.size ?: 0} jingle ids"
        }
        return EffectIds(seqs, spotanims, synths, jingles)
    }
}
