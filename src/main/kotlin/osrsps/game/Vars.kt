package osrsps.game

import java.util.BitSet

/** A varbit: bits [startBit] to [endBit], inclusive, of varp [baseVar]. */
data class VarbitType(val baseVar: Int, val startBit: Int, val endBit: Int) {
    init {
        require(startBit in 0..31 && endBit in startBit..31) { "Bad varbit bits $startBit to $endBit" }
    }

    /** The largest value the varbit holds. The client's own masks are `(2 shl (end - start)) - 1`, -1 for all 32 bits. */
    val mask: Int get() = (2 shl (endBit - startBit)) - 1
}

/** The varps and varbits the cache defines. */
class VarTypes(
    /** Varps are numbered 0 until this; the client sizes its varp array by the same count. */
    val varpCount: Int,
    private val varbits: Map<Int, VarbitType>,
    /**
     * Varbits are numbered 0 until this: the cache's highest varbit file id plus one, which counts one that didn't
     * decode. Our own vars ([osrsps.game.content.CustomVars]) take the ids from here and [varpCount] on.
     */
    val varbitCount: Int = (varbits.keys.maxOrNull() ?: -1) + 1,
    /**
     * Varps of our own by name ([osrsps.game.content.CustomVars]), all at or past [cacheVarpCount]. A save keeps them
     * by name ([PlayerSave]), as their ids follow the cache the server loaded and another cache gives them others.
     */
    val namedVarps: Map<String, Int> = emptyMap(),
    /** The cache's own varp count: a saved varp numbered from here on is one of ours under an old id, not the cache's. */
    val cacheVarpCount: Int = varpCount,
) {
    init {
        require(varbits.keys.all { it < varbitCount }) { "A varbit id is past the varbit count $varbitCount" }
        require(cacheVarpCount in 0..varpCount) { "The cache's varp count $cacheVarpCount is past the varp count $varpCount" }
        require(namedVarps.values.all { it in cacheVarpCount until varpCount }) { "A named varp isn't after the cache's ($cacheVarpCount)" }
        require(namedVarps.values.toSet().size == namedVarps.size) { "Two names share a varp" }
    }

    private val varpNames: Map<Int, String> = namedVarps.entries.associate { (name, id) -> id to name }

    fun varbit(id: Int): VarbitType? = varbits[id]

    /** The name [varp] goes by in a save, or null for one of the cache's. */
    fun varpName(varp: Int): String? = varpNames[varp]

    /**
     * These plus [extra] varbits, whose ids must be new, and [named] varps of our own, whose names must be new, with
     * the varp and varbit counts grown to cover them: for the vars of our own ([osrsps.game.content.CustomVars]),
     * which the client gets through the cache overlay. The cache's own varp count stays what it was.
     */
    fun with(extra: Map<Int, VarbitType>, named: Map<String, Int> = emptyMap()): VarTypes {
        if (extra.isEmpty() && named.isEmpty()) return this
        val merged = HashMap(varbits)
        for ((id, type) in extra) require(merged.put(id, type) == null) { "Varbit $id is already in the cache" }
        for (name in named.keys) require(name !in namedVarps) { "Varp '$name' is already named" }
        val varps = maxOf(varpCount, (extra.values.maxOfOrNull { it.baseVar } ?: -1) + 1, (named.values.maxOrNull() ?: -1) + 1)
        return VarTypes(varps, merged, maxOf(varbitCount, (extra.keys.maxOrNull() ?: -1) + 1), namedVarps + named, cacheVarpCount)
    }

    companion object {
        /**
         * For tests and caches with no var configs. 6,000 varps and 21,000 varbits cover every id RuneLite's
         * VarPlayerID and VarbitID name for revision 241 (the highest are 5,890 and 20,410), so vars of our own
         * ([osrsps.game.content.CustomVars]) land past the real ones here too.
         */
        val NONE = VarTypes(6_000, emptyMap(), 21_000)
    }
}

/**
 * One player's varps. Varbits are slices of them, so setting a varbit changes its base varp.
 * Changed varps are sent once per tick by [flush], with the value they hold at that point.
 */
class PlayerVars(val types: VarTypes) {
    private val values = IntArray(types.varpCount)
    private val changed = LinkedHashSet<Int>()

    /** Varps flushed at least once, which are the ones the client may hold a value other than 0 for. */
    private val sent = BitSet(types.varpCount)

    operator fun get(varp: Int): Int = values[checkVarp(varp)]

    operator fun set(varp: Int, value: Int) {
        checkVarp(varp)
        if (values[varp] == value) return
        values[varp] = value
        changed.add(varp)
    }

    fun getVarbit(id: Int): Int {
        val type = varbitType(id)
        return (values[checkVarp(type.baseVar)] ushr type.startBit) and type.mask
    }

    fun setVarbit(id: Int, value: Int) {
        val type = varbitType(id)
        // The client sets an out-of-range varbit to 0; failing here shows the bug in our content instead.
        require(type.mask == -1 || value in 0..type.mask) { "Varbit $id holds 0 to ${type.mask}, not $value" }
        val shiftedMask = type.mask shl type.startBit
        val varp = checkVarp(type.baseVar)
        set(varp, (values[varp] and shiftedMask.inv()) or ((value shl type.startBit) and shiftedMask))
    }

    /** Hands each varp changed since the last flush, and its value now, to [send]. */
    fun flush(send: (varp: Int, value: Int) -> Unit) {
        for (varp in changed) {
            send(varp, values[varp])
            sent.set(varp)
        }
        changed.clear()
    }

    /** Hands each varp that isn't 0 to [action], lowest id first. */
    fun forEachNonZero(action: (varp: Int, value: Int) -> Unit) {
        for (varp in values.indices) {
            if (values[varp] != 0) action(varp, values[varp])
        }
    }

    /** Makes the next [flush] send [varp] even if it hasn't changed: the client changed its own copy. */
    fun resend(varp: Int) {
        changed.add(checkVarp(varp))
    }

    /**
     * Makes the next [flush] send every varp the client may have a stale value for: those that aren't 0, and those
     * sent before, which covers a varp that went back to 0 while the client wasn't listening.
     */
    fun resendAll() {
        for (varp in values.indices) {
            if (values[varp] != 0 || sent[varp]) changed.add(varp)
        }
    }

    private fun varbitType(id: Int): VarbitType = types.varbit(id) ?: throw IllegalArgumentException("No varbit $id in the cache")

    private fun checkVarp(varp: Int): Int {
        require(varp in 0 until types.varpCount) { "Varps are 0 to ${types.varpCount - 1}, not $varp" }
        return varp
    }
}
