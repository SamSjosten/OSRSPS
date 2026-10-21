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
) {
    val varbitCount: Int get() = varbits.size

    fun varbit(id: Int): VarbitType? = varbits[id]

    companion object {
        /**
         * For tests and caches with no var configs. 6,000 varps covers every id RuneLite's VarPlayerID names for
         * revision 241 (the highest is 5,890).
         */
        val NONE = VarTypes(6_000, emptyMap())
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
